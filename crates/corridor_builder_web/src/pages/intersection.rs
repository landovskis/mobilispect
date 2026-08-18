use std::cell::RefCell;
use std::collections::VecDeque;
use std::rc::Rc;

use yew::prelude::*;
use yew_router::prelude::*;

use crate::api;
use crate::app::Route;

#[derive(Properties, PartialEq)]
pub struct IntersectionPageProps {
    pub remix_id: i64,
    pub cross_section_id: i64,
}

const BUS_GATES: &[(&str, &str)] = &[
    ("", "None"),
    ("signal_controlled", "Signal controlled"),
    ("yield_controlled", "Yield controlled"),
];

const TURN_CONFLICTS: &[(&str, &str)] = &[
    ("", "None"),
    (
        "indirect_left_via_alternative",
        "Indirect left via alternative",
    ),
    (
        "indirect_left_within_intersection",
        "Indirect left within intersection",
    ),
    ("right_in_right_out", "Right-in / right-out"),
    ("dead_end_lateral_street", "Dead-end lateral street"),
];

const BUS_STOPS: &[(&str, &str)] = &[
    ("", "None"),
    ("bus_bulb", "Bus bulb"),
    ("signal_protected_platform", "Signal-protected platform"),
];

/// `true` when `current` (a `None`/`Some(String)` field's live value) matches
/// this `<option>`'s raw `value` attribute -- `value == ""` stands for
/// `current == None` throughout this page, matching each `<select>`'s own
/// "None" option.
fn is_selected(current: &Option<String>, value: &str) -> bool {
    match current {
        Some(c) => c == value,
        None => value.is_empty(),
    }
}

fn to_option(value: String) -> Option<String> {
    if value.is_empty() { None } else { Some(value) }
}

/// One queued write: the full `(bus_gate, turn_conflict, bus_stop)` triple to
/// PUT to `/api/intersections/:id`. The endpoint replaces all three fields at
/// once (a whole-record PUT, not per-field), so every queued write must carry
/// a complete, self-contained snapshot of all three -- never a value re-read
/// from render state, which could be stale by the time this write is
/// actually sent.
type PendingWrite = (Option<String>, Option<String>, Option<String>);

/// What this page currently knows about its `cross_section_id`. Resolving
/// `cross_section_id -> intersection_id` is a separate network round trip
/// (`api::get_cross_section`) from loading the `Intersection` itself, so this
/// tracks that resolution's outcome distinctly from a plain loading flag:
/// a cross-section that simply isn't an endpoint of any intersection isn't
/// an error, just nothing to show here.
#[derive(Debug, Clone, PartialEq)]
enum LoadState {
    Loading,
    NotAnIntersection,
    Error(String),
    Loaded,
}

#[component]
pub fn IntersectionPage(props: &IntersectionPageProps) -> Html {
    let remix_id = props.remix_id;
    let cross_section_id = props.cross_section_id;

    let load_state = use_state(|| LoadState::Loading);
    let intersection_id = use_state(|| None::<i64>);
    let bus_gate = use_state(|| None::<String>);
    let turn_conflict = use_state(|| None::<String>);
    let bus_stop = use_state(|| None::<String>);
    let turn_movements = use_state(Vec::<api::TurnMovementResponse>::new);
    let turn_movement_error = use_state(|| None::<String>);

    // The live, always-current `(bus_gate, turn_conflict, bus_stop)` triple.
    // Because the endpoint is a combined whole-record PUT, a rapid edit of
    // one field immediately followed by an edit of another must compose on
    // top of *this* -- the just-mutated triple -- not on top of whatever
    // `bus_gate`/`turn_conflict`/`bus_stop` happened to hold at the page's
    // last render. Mutated synchronously by the `on_*_change` handlers below,
    // and seeded from the server's fetched state on load so it never starts
    // out of sync with DB truth.
    let live_triple = use_mut_ref(|| (None::<String>, None::<String>, None::<String>));

    // Pending writes are drained by a single sequential worker, mirroring
    // `pages/corridor.rs`'s `write_queue` for the lane-fields endpoint (also
    // a combined multi-field whole-record PUT). This exists because firing
    // all three selects' PUTs independently gives no guarantee the network
    // round-trips *complete* in the order the analyst made the edits: a
    // request carrying a now-stale snapshot of another field can finish
    // after a later request and silently overwrite it in the database, with
    // no visible sign until a reload. Serializing the writes -- always
    // awaiting one before sending the next -- removes the possibility
    // entirely: the server receives, and finishes, them strictly in the
    // order they were queued.
    let write_queue = use_mut_ref(VecDeque::<PendingWrite>::new);
    let write_worker_running = use_mut_ref(|| false);

    // `<option selected={...}>` is a plain HTML *attribute* in Yew's VDOM
    // diffing, and native `<select>`/`<option>` elements stop honoring
    // attribute-driven selection changes once the option has been "dirtied"
    // by ANY prior interaction -- per the HTML spec, a dirtied option's
    // selectedness no longer resyncs from `selected` attribute mutations.
    // These `NodeRef`s and their `use_effect_with`s below force the fix
    // imperatively: setting the DOM `.value` PROPERTY directly (not the
    // `selected` attribute) always takes effect regardless of dirtiness.
    let bus_gate_ref = use_node_ref();
    let turn_conflict_ref = use_node_ref();
    let bus_stop_ref = use_node_ref();

    {
        let bus_gate_ref = bus_gate_ref.clone();
        use_effect_with((*bus_gate).clone(), move |value| {
            if let Some(select) = bus_gate_ref.cast::<web_sys::HtmlSelectElement>() {
                select.set_value(value.as_deref().unwrap_or(""));
            }
            || ()
        });
    }
    {
        let turn_conflict_ref = turn_conflict_ref.clone();
        use_effect_with((*turn_conflict).clone(), move |value| {
            if let Some(select) = turn_conflict_ref.cast::<web_sys::HtmlSelectElement>() {
                select.set_value(value.as_deref().unwrap_or(""));
            }
            || ()
        });
    }
    {
        let bus_stop_ref = bus_stop_ref.clone();
        use_effect_with((*bus_stop).clone(), move |value| {
            if let Some(select) = bus_stop_ref.cast::<web_sys::HtmlSelectElement>() {
                select.set_value(value.as_deref().unwrap_or(""));
            }
            || ()
        });
    }

    // Resolves `cross_section_id -> intersection_id` first (this route only
    // carries `cross_section_id`), then loads the intersection and its turn
    // movements.
    {
        let load_state = load_state.clone();
        let intersection_id = intersection_id.clone();
        let bus_gate = bus_gate.clone();
        let turn_conflict = turn_conflict.clone();
        let bus_stop = bus_stop.clone();
        let turn_movements = turn_movements.clone();
        let live_triple = live_triple.clone();
        use_effect_with(cross_section_id, move |cross_section_id| {
            let cross_section_id = *cross_section_id;
            let load_state = load_state.clone();
            let intersection_id = intersection_id.clone();
            let bus_gate = bus_gate.clone();
            let turn_conflict = turn_conflict.clone();
            let bus_stop = bus_stop.clone();
            let turn_movements = turn_movements.clone();
            let live_triple = live_triple.clone();
            load_state.set(LoadState::Loading);
            wasm_bindgen_futures::spawn_local(async move {
                let cs = match api::get_cross_section(cross_section_id).await {
                    Ok(cs) => cs,
                    Err(e) => {
                        load_state.set(LoadState::Error(e));
                        return;
                    }
                };
                let Some(iid) = cs.intersection_id else {
                    load_state.set(LoadState::NotAnIntersection);
                    return;
                };
                intersection_id.set(Some(iid));

                let fetched = match api::get_intersection(iid).await {
                    Ok(fetched) => fetched,
                    Err(e) => {
                        load_state.set(LoadState::Error(e));
                        return;
                    }
                };
                *live_triple.borrow_mut() = (
                    fetched.bus_gate.clone(),
                    fetched.turn_conflict.clone(),
                    fetched.bus_stop.clone(),
                );
                bus_gate.set(fetched.bus_gate);
                turn_conflict.set(fetched.turn_conflict);
                bus_stop.set(fetched.bus_stop);

                match api::list_turn_movements(iid).await {
                    Ok(movements) => turn_movements.set(movements),
                    Err(e) => {
                        load_state.set(LoadState::Error(e));
                        return;
                    }
                }

                load_state.set(LoadState::Loaded);
            });
            || ()
        });
    }

    let on_bus_gate_change = {
        let bus_gate = bus_gate.clone();
        let turn_conflict = turn_conflict.clone();
        let bus_stop = bus_stop.clone();
        let load_state = load_state.clone();
        let live_triple = live_triple.clone();
        let write_queue = write_queue.clone();
        let write_worker_running = write_worker_running.clone();
        let intersection_id = intersection_id.clone();
        Callback::from(move |e: Event| {
            let Some(iid) = *intersection_id else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let new_bus_gate = to_option(value);
            let triple = {
                let mut live = live_triple.borrow_mut();
                live.0 = new_bus_gate.clone();
                live.clone()
            };
            bus_gate.set(new_bus_gate);
            enqueue_write(
                &write_queue,
                &write_worker_running,
                iid,
                &bus_gate,
                &turn_conflict,
                &bus_stop,
                &load_state,
                triple,
            );
        })
    };

    let on_turn_conflict_change = {
        let bus_gate = bus_gate.clone();
        let turn_conflict = turn_conflict.clone();
        let bus_stop = bus_stop.clone();
        let load_state = load_state.clone();
        let live_triple = live_triple.clone();
        let write_queue = write_queue.clone();
        let write_worker_running = write_worker_running.clone();
        let intersection_id = intersection_id.clone();
        Callback::from(move |e: Event| {
            let Some(iid) = *intersection_id else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let new_turn_conflict = to_option(value);
            let triple = {
                let mut live = live_triple.borrow_mut();
                live.1 = new_turn_conflict.clone();
                live.clone()
            };
            turn_conflict.set(new_turn_conflict);
            enqueue_write(
                &write_queue,
                &write_worker_running,
                iid,
                &bus_gate,
                &turn_conflict,
                &bus_stop,
                &load_state,
                triple,
            );
        })
    };

    let on_bus_stop_change = {
        let bus_gate = bus_gate.clone();
        let turn_conflict = turn_conflict.clone();
        let bus_stop = bus_stop.clone();
        let load_state = load_state.clone();
        let live_triple = live_triple.clone();
        let write_queue = write_queue.clone();
        let write_worker_running = write_worker_running.clone();
        let intersection_id = intersection_id.clone();
        Callback::from(move |e: Event| {
            let Some(iid) = *intersection_id else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let new_bus_stop = to_option(value);
            let triple = {
                let mut live = live_triple.borrow_mut();
                live.2 = new_bus_stop.clone();
                live.clone()
            };
            bus_stop.set(new_bus_stop);
            enqueue_write(
                &write_queue,
                &write_worker_running,
                iid,
                &bus_gate,
                &turn_conflict,
                &bus_stop,
                &load_state,
                triple,
            );
        })
    };

    let from_lane_input = use_state(String::new);
    let to_lane_input = use_state(String::new);

    let on_from_lane_input = {
        let from_lane_input = from_lane_input.clone();
        Callback::from(move |e: InputEvent| {
            let value = e
                .target_dyn_into::<web_sys::HtmlInputElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            from_lane_input.set(value);
        })
    };
    let on_to_lane_input = {
        let to_lane_input = to_lane_input.clone();
        Callback::from(move |e: InputEvent| {
            let value = e
                .target_dyn_into::<web_sys::HtmlInputElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            to_lane_input.set(value);
        })
    };

    let on_add_turn_movement = {
        let intersection_id = intersection_id.clone();
        let from_lane_input = from_lane_input.clone();
        let to_lane_input = to_lane_input.clone();
        let turn_movements = turn_movements.clone();
        let turn_movement_error = turn_movement_error.clone();
        Callback::from(move |_: MouseEvent| {
            let Some(iid) = *intersection_id else {
                return;
            };
            let (Ok(from_lane_id), Ok(to_lane_id)) =
                (from_lane_input.parse::<i64>(), to_lane_input.parse::<i64>())
            else {
                turn_movement_error.set(Some(
                    "From lane ID and To lane ID must both be numbers".to_string(),
                ));
                return;
            };
            let turn_movements = turn_movements.clone();
            let turn_movement_error = turn_movement_error.clone();
            let from_lane_input = from_lane_input.clone();
            let to_lane_input = to_lane_input.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::set_turn_movement(iid, from_lane_id, to_lane_id).await {
                    Ok(()) => {
                        let mut next = (*turn_movements).clone();
                        next.retain(|m| {
                            !(m.from_lane_id == from_lane_id && m.to_lane_id == to_lane_id)
                        });
                        next.push(api::TurnMovementResponse {
                            from_lane_id,
                            to_lane_id,
                            source: "manual".to_string(),
                        });
                        turn_movements.set(next);
                        turn_movement_error.set(None);
                        from_lane_input.set(String::new());
                        to_lane_input.set(String::new());
                    }
                    Err(e) => turn_movement_error.set(Some(e)),
                }
            });
        })
    };

    let make_on_remove_turn_movement = {
        let intersection_id = intersection_id.clone();
        let turn_movements = turn_movements.clone();
        let turn_movement_error = turn_movement_error.clone();
        move |from_lane_id: i64, to_lane_id: i64| {
            let intersection_id = intersection_id.clone();
            let turn_movements = turn_movements.clone();
            let turn_movement_error = turn_movement_error.clone();
            Callback::from(move |_: MouseEvent| {
                let Some(iid) = *intersection_id else {
                    return;
                };
                let turn_movements = turn_movements.clone();
                let turn_movement_error = turn_movement_error.clone();
                wasm_bindgen_futures::spawn_local(async move {
                    match api::delete_turn_movement(iid, from_lane_id, to_lane_id).await {
                        Ok(()) => {
                            let mut next = (*turn_movements).clone();
                            next.retain(|m| {
                                !(m.from_lane_id == from_lane_id && m.to_lane_id == to_lane_id)
                            });
                            turn_movements.set(next);
                            turn_movement_error.set(None);
                        }
                        Err(e) => turn_movement_error.set(Some(e)),
                    }
                });
            })
        }
    };

    html! {
        <div class="setup-wrap">
            <div class="setup-card">
                { match &*load_state {
                    LoadState::Loading => html! { <p>{ "Loading intersection…" }</p> },
                    LoadState::Error(err) => html! {
                        <>
                            <div class="alert alert--err">{ err }</div>
                            <div style="margin-top:1rem;">
                                <Link<Route> classes="chip" to={Route::RegionMap { remix_id }}>{ "Back to map" }</Link<Route>>
                            </div>
                        </>
                    },
                    LoadState::NotAnIntersection => html! {
                        <>
                            <div class="alert alert--warn">
                                { "This cross-section isn't an intersection endpoint, so it has no intersection treatment to edit." }
                            </div>
                            <div style="margin-top:1rem;">
                                <Link<Route> classes="chip" to={Route::RegionMap { remix_id }}>{ "Back to map" }</Link<Route>>
                            </div>
                        </>
                    },
                    LoadState::Loaded => html! {
                        <>
                            <label class="field-label" for="bus-gate">{ "Bus gate" }</label>
                            <select class="field" id="bus-gate" ref={bus_gate_ref} onchange={on_bus_gate_change}>
                                { for BUS_GATES.iter().map(|(value, label)| html! {
                                    <option value={*value} selected={is_selected(&bus_gate, value)}>{ *label }</option>
                                }) }
                            </select>

                            <label class="field-label" for="turn-conflict" style="margin-top:0.75rem;">{ "Turn-conflict type" }</label>
                            <select class="field" id="turn-conflict" ref={turn_conflict_ref} onchange={on_turn_conflict_change}>
                                { for TURN_CONFLICTS.iter().map(|(value, label)| html! {
                                    <option value={*value} selected={is_selected(&turn_conflict, value)}>{ *label }</option>
                                }) }
                            </select>

                            <label class="field-label" for="bus-stop" style="margin-top:0.75rem;">{ "Bus stop" }</label>
                            <select class="field" id="bus-stop" ref={bus_stop_ref} onchange={on_bus_stop_change}>
                                { for BUS_STOPS.iter().map(|(value, label)| html! {
                                    <option value={*value} selected={is_selected(&bus_stop, value)}>{ *label }</option>
                                }) }
                            </select>

                            <h2 style="margin-top:1.5rem;">{ "Turn movements" }</h2>
                            if let Some(err) = &*turn_movement_error {
                                <div class="alert alert--err">{ err }</div>
                            }
                            <ul style="list-style:none;padding:0;margin:0.5rem 0;">
                                { for turn_movements.iter().map(|m| {
                                    let (badge_bg, badge_fg) = if m.source == "manual" {
                                        ("var(--b-ox-bg)", "var(--b-ox-fg)")
                                    } else {
                                        ("var(--b-neu-bg)", "var(--b-neu-fg)")
                                    };
                                    let badge_label = if m.source == "manual" { "Manual" } else { "Inferred" };
                                    let on_remove = make_on_remove_turn_movement(m.from_lane_id, m.to_lane_id);
                                    html! {
                                        <li style="display:flex;align-items:center;gap:0.5rem;padding:0.4rem 0;border-bottom:1px solid var(--border-light);">
                                            <span>{ format!("Lane {} → Lane {}", m.from_lane_id, m.to_lane_id) }</span>
                                            <span class="badge" style={format!("background:{badge_bg};color:{badge_fg};")}>{ badge_label }</span>
                                            <button class="chip" style="margin-left:auto;" onclick={on_remove}>{ "Remove" }</button>
                                        </li>
                                    }
                                }) }
                            </ul>

                            <label class="field-label" for="from-lane-id">{ "From lane ID" }</label>
                            <input class="field" id="from-lane-id" type="number" value={(*from_lane_input).clone()} oninput={on_from_lane_input} />
                            <label class="field-label" for="to-lane-id" style="margin-top:0.5rem;">{ "To lane ID" }</label>
                            <input class="field" id="to-lane-id" type="number" value={(*to_lane_input).clone()} oninput={on_to_lane_input} />
                            <div style="margin-top:0.5rem;">
                                <button class="chip" onclick={on_add_turn_movement}>{ "Add turn movement" }</button>
                            </div>

                            <div style="margin-top:1rem;">
                                <Link<Route> classes="chip" to={Route::RegionMap { remix_id }}>{ "Back to map" }</Link<Route>>
                            </div>
                        </>
                    },
                } }
            </div>
        </div>
    }
}

/// Queues `write` and, unless a drain loop is already running, starts one.
///
/// Mirrors `pages/corridor.rs`'s `enqueue_write` -- see this page's
/// `write_queue` comment above for why the writes must be serialized rather
/// than fired independently.
#[allow(clippy::too_many_arguments)]
fn enqueue_write(
    queue: &Rc<RefCell<VecDeque<PendingWrite>>>,
    running: &Rc<RefCell<bool>>,
    intersection_id: i64,
    bus_gate: &UseStateHandle<Option<String>>,
    turn_conflict: &UseStateHandle<Option<String>>,
    bus_stop: &UseStateHandle<Option<String>>,
    load_state: &UseStateHandle<LoadState>,
    write: PendingWrite,
) {
    queue.borrow_mut().push_back(write);
    if *running.borrow() {
        return;
    }
    *running.borrow_mut() = true;

    let queue = queue.clone();
    let running = running.clone();
    let bus_gate = bus_gate.clone();
    let turn_conflict = turn_conflict.clone();
    let bus_stop = bus_stop.clone();
    let load_state = load_state.clone();
    wasm_bindgen_futures::spawn_local(async move {
        loop {
            let next = queue.borrow_mut().pop_front();
            let Some((new_bus_gate, new_turn_conflict, new_bus_stop)) = next else {
                *running.borrow_mut() = false;
                break;
            };
            let outcome = api::set_intersection_treatment(
                intersection_id,
                new_bus_gate,
                new_turn_conflict,
                new_bus_stop,
            )
            .await;
            // Clearing the flag BEFORE publishing matters, matching
            // `corridor.rs`'s write-queue comment: `bus_gate.set`/etc. can
            // re-render synchronously, and on the last response of a burst
            // nothing must still look like a write is in flight.
            let idle = queue.borrow().is_empty();
            if idle {
                *running.borrow_mut() = false;
            }
            match outcome {
                Ok(confirmed) => {
                    // Only the LAST response in a burst is published --
                    // publishing every intermediate response would flicker
                    // the selects through each momentarily-stale confirmed
                    // value before settling on the final one.
                    if idle {
                        bus_gate.set(confirmed.bus_gate);
                        turn_conflict.set(confirmed.turn_conflict);
                        bus_stop.set(confirmed.bus_stop);
                    }
                }
                Err(e) => load_state.set(LoadState::Error(e)),
            }
            if idle {
                break;
            }
        }
    });
}
