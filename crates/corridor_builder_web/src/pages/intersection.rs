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

/// `true` when `current` (a `None`/`Some(String)` field's live value) matches
/// this `<option>`'s raw `value` attribute -- `value == ""` stands for
/// `current == None` throughout this page, matching the `<select>`'s own
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

/// One queued write: the full `(bus_gate, turn_conflict)` pair to PUT to
/// `/api/cross-sections/:id/intersection-treatment`. The endpoint replaces
/// both fields at once (a whole-record PUT, not per-field), so every queued
/// write must carry a complete, self-contained snapshot of both fields --
/// never a value re-read from render state, which could be stale by the time
/// this write is actually sent.
type PendingWrite = (Option<String>, Option<String>);

#[component]
pub fn IntersectionPage(props: &IntersectionPageProps) -> Html {
    let remix_id = props.remix_id;
    let cross_section_id = props.cross_section_id;

    let bus_gate = use_state(|| None::<String>);
    let turn_conflict = use_state(|| None::<String>);
    let error = use_state(|| None::<String>);
    let loaded = use_state(|| false);

    // The live, always-current `(bus_gate, turn_conflict)` pair. Because the
    // endpoint is a combined whole-record PUT, a rapid edit of one field
    // immediately followed by an edit of the other must compose on top of
    // *this* -- the just-mutated pair -- not on top of whatever `bus_gate`/
    // `turn_conflict` happened to hold at the page's last render. Mutated
    // synchronously by `on_bus_gate_change`/`on_turn_conflict_change` below,
    // and seeded from the server's fetched state on mount so it never starts
    // out of sync with DB truth.
    let live_pair = use_mut_ref(|| (None::<String>, None::<String>));

    // Pending writes are drained by a single sequential worker, mirroring
    // `pages/corridor.rs`'s `write_queue` for the lane-fields endpoint
    // (also a combined multi-field whole-record PUT). This exists because
    // firing both selects' PUTs independently gives no guarantee the two
    // network round-trips *complete* in the order the analyst made the
    // edits: a `bus_gate` request that carries a now-stale snapshot of
    // `turn_conflict` can finish after a later `turn_conflict` request and
    // silently overwrite it in the database, with no visible sign until a
    // reload. (This is a server-round-trip completion-order hazard, distinct
    // from -- and not addressed by -- the fact that two native `<select>`
    // `change` events can't land in the same browser task the way a
    // text-input blur and a button click can; that only rules out a
    // *same-tick* client-side race, not this one.) Serializing the writes --
    // always awaiting one before sending the next -- removes the possibility
    // entirely: the server receives, and finishes, them strictly in the
    // order they were queued.
    let write_queue = use_mut_ref(VecDeque::<PendingWrite>::new);
    let write_worker_running = use_mut_ref(|| false);

    // `<option selected={...}>` is a plain HTML *attribute* in Yew's VDOM
    // diffing, and native `<select>`/`<option>` elements stop honoring
    // attribute-driven selection changes once the option has been "dirtied"
    // by ANY prior interaction (a real click, or `<select>.value = ...`
    // script assignment) -- per the HTML spec, a dirtied option's
    // selectedness no longer resyncs from `selected` attribute mutations.
    // Concretely: once an analyst has picked anything in one of these
    // selects, a later re-render driven by this page's write-queue
    // (`bus_gate.set`/`turn_conflict.set` applying a confirmed -- possibly
    // momentarily stale, mid-burst -- server response) can silently fail to
    // visually update the `<select>`, even though the underlying Rust state
    // (and, moments later, the database) is correct. These two `NodeRef`s
    // and their `use_effect_with`s below force the fix imperatively: setting
    // the DOM `.value` PROPERTY directly (not the `selected` attribute)
    // always takes effect regardless of dirtiness, keeping what the analyst
    // sees in sync with confirmed state at every step, not just after a
    // reload.
    let bus_gate_ref = use_node_ref();
    let turn_conflict_ref = use_node_ref();

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
        let bus_gate = bus_gate.clone();
        let turn_conflict = turn_conflict.clone();
        let error = error.clone();
        let loaded = loaded.clone();
        let live_pair = live_pair.clone();
        use_effect_with(cross_section_id, move |cross_section_id| {
            let cross_section_id = *cross_section_id;
            let bus_gate = bus_gate.clone();
            let turn_conflict = turn_conflict.clone();
            let error = error.clone();
            let loaded = loaded.clone();
            let live_pair = live_pair.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::get_intersection_treatment(cross_section_id).await {
                    Ok(fetched) => {
                        *live_pair.borrow_mut() =
                            (fetched.bus_gate.clone(), fetched.turn_conflict.clone());
                        bus_gate.set(fetched.bus_gate);
                        turn_conflict.set(fetched.turn_conflict);
                        loaded.set(true);
                    }
                    Err(e) => error.set(Some(e)),
                }
            });
            || ()
        });
    }

    let on_bus_gate_change = {
        let bus_gate = bus_gate.clone();
        let turn_conflict = turn_conflict.clone();
        let error = error.clone();
        let live_pair = live_pair.clone();
        let write_queue = write_queue.clone();
        let write_worker_running = write_worker_running.clone();
        Callback::from(move |e: Event| {
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let new_bus_gate = to_option(value);
            let pair = {
                let mut live = live_pair.borrow_mut();
                live.0 = new_bus_gate.clone();
                live.clone()
            };
            bus_gate.set(new_bus_gate);
            enqueue_write(
                &write_queue,
                &write_worker_running,
                cross_section_id,
                &bus_gate,
                &turn_conflict,
                &error,
                pair,
            );
        })
    };

    let on_turn_conflict_change = {
        let bus_gate = bus_gate.clone();
        let turn_conflict = turn_conflict.clone();
        let error = error.clone();
        let live_pair = live_pair.clone();
        let write_queue = write_queue.clone();
        let write_worker_running = write_worker_running.clone();
        Callback::from(move |e: Event| {
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let new_turn_conflict = to_option(value);
            let pair = {
                let mut live = live_pair.borrow_mut();
                live.1 = new_turn_conflict.clone();
                live.clone()
            };
            turn_conflict.set(new_turn_conflict);
            enqueue_write(
                &write_queue,
                &write_worker_running,
                cross_section_id,
                &bus_gate,
                &turn_conflict,
                &error,
                pair,
            );
        })
    };

    html! {
        <div class="setup-wrap">
            <div class="setup-card">
                if let Some(err) = &*error {
                    <div class="alert alert--err">{ err }</div>
                }
                if *loaded {
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
                } else {
                    <p>{ "Loading…" }</p>
                }
                <div style="margin-top:1rem;">
                    <Link<Route> classes="chip" to={Route::RegionMap { remix_id }}>{ "Back to map" }</Link<Route>>
                </div>
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
    cross_section_id: i64,
    bus_gate: &UseStateHandle<Option<String>>,
    turn_conflict: &UseStateHandle<Option<String>>,
    error: &UseStateHandle<Option<String>>,
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
    let error = error.clone();
    wasm_bindgen_futures::spawn_local(async move {
        loop {
            let next = queue.borrow_mut().pop_front();
            let Some((new_bus_gate, new_turn_conflict)) = next else {
                *running.borrow_mut() = false;
                break;
            };
            let outcome =
                api::set_intersection_treatment(cross_section_id, new_bus_gate, new_turn_conflict)
                    .await;
            // Clearing the flag BEFORE publishing matters, matching
            // `corridor.rs`'s write-queue comment: `bus_gate.set`/
            // `turn_conflict.set` can re-render synchronously, and on the
            // last response of a burst nothing must still look like a write
            // is in flight.
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
                    }
                }
                Err(e) => error.set(Some(e)),
            }
            if idle {
                break;
            }
        }
    });
}
