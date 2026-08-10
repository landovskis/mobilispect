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

#[component]
pub fn IntersectionPage(props: &IntersectionPageProps) -> Html {
    let remix_id = props.remix_id;
    let cross_section_id = props.cross_section_id;

    let bus_gate = use_state(|| None::<String>);
    let turn_conflict = use_state(|| None::<String>);
    let error = use_state(|| None::<String>);
    let loaded = use_state(|| false);

    {
        let bus_gate = bus_gate.clone();
        let turn_conflict = turn_conflict.clone();
        let error = error.clone();
        let loaded = loaded.clone();
        use_effect_with(cross_section_id, move |cross_section_id| {
            let cross_section_id = *cross_section_id;
            let bus_gate = bus_gate.clone();
            let turn_conflict = turn_conflict.clone();
            let error = error.clone();
            let loaded = loaded.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::get_intersection_treatment(cross_section_id).await {
                    Ok(fetched) => {
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

    // Two <select> `change` events can never land in the same browser task
    // the way a text-input blur and a button click can (see
    // `pages/corridor.rs`'s write-queue comment for that hazard) -- a native
    // <select>'s dropdown is modal, so persisting each change immediately
    // from `use_state`'s latest value, with no write-queue/live-ref
    // machinery, can't drop a same-tick sibling edit here.
    let on_bus_gate_change = {
        let bus_gate = bus_gate.clone();
        let turn_conflict = turn_conflict.clone();
        let error = error.clone();
        Callback::from(move |e: Event| {
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let new_bus_gate = to_option(value);
            bus_gate.set(new_bus_gate.clone());
            let turn_conflict_value = (*turn_conflict).clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                if let Err(e) = api::set_intersection_treatment(
                    cross_section_id,
                    new_bus_gate,
                    turn_conflict_value,
                )
                .await
                {
                    error.set(Some(e));
                }
            });
        })
    };

    let on_turn_conflict_change = {
        let bus_gate = bus_gate.clone();
        let turn_conflict = turn_conflict.clone();
        let error = error.clone();
        Callback::from(move |e: Event| {
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let new_turn_conflict = to_option(value);
            turn_conflict.set(new_turn_conflict.clone());
            let bus_gate_value = (*bus_gate).clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                if let Err(e) = api::set_intersection_treatment(
                    cross_section_id,
                    bus_gate_value,
                    new_turn_conflict,
                )
                .await
                {
                    error.set(Some(e));
                }
            });
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
                    <select class="field" id="bus-gate" onchange={on_bus_gate_change}>
                        { for BUS_GATES.iter().map(|(value, label)| html! {
                            <option value={*value} selected={is_selected(&bus_gate, value)}>{ *label }</option>
                        }) }
                    </select>

                    <label class="field-label" for="turn-conflict" style="margin-top:0.75rem;">{ "Turn-conflict type" }</label>
                    <select class="field" id="turn-conflict" onchange={on_turn_conflict_change}>
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
