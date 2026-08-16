use std::cell::RefCell;
use std::collections::VecDeque;
use std::rc::Rc;

use wasm_bindgen::prelude::*;
use yew::prelude::*;
use yew_router::prelude::*;

use crate::api;
use crate::app::Route;
use crate::maplibre::Map;

#[derive(Properties, PartialEq)]
pub struct CorridorPageProps {
    pub remix_id: i64,
    pub corridor_id: i64,
}

#[component]
pub fn CorridorPage(props: &CorridorPageProps) -> Html {
    let remix_id = props.remix_id;
    let corridor_id = props.corridor_id;

    // `selected_cross_section_id` is set from the mini-map's long-lived native
    // click Closure (registered once via `map.on("click", ...)` + `.forget()`),
    // but only ever *written*, never read-then-computed-from inside that
    // closure -- so a plain `UseStateHandle` is safe here, unlike
    // `pages/import_osm.rs`'s selection state (which the click closure both
    // reads AND writes, and so needs `Rc<RefCell<...>>` -- see that file's
    // state-management comment for the full explanation of the hazard this
    // page doesn't have).
    let selected_cross_section_id = use_state(|| None::<i64>);
    let cross_sections = use_state(Vec::<api::CrossSectionSummary>::new);
    let lanes = use_state(Vec::<api::LaneResponse>::new);
    let selected_lane_id = use_state(|| None::<i64>);
    let error = use_state(|| None::<String>);

    // `access_rules_ref`/`lane_fields_ref` are `Rc<RefCell<...>>` (via
    // `use_mut_ref`), not `UseStateHandle`s, for the same reason
    // `pages/import_osm.rs`'s `ways_ref`/`selected_ref` are (see that file's
    // state-management comment): every edit control here does a
    // read-modify-write of the lane's *whole* server-side record (the
    // access-rule endpoint is a whole-list replace, the lane endpoint a
    // whole-record PATCH), and reading the "before" half of that from a value
    // derived from the `lanes` state means reading whatever that state was at
    // the last render -- not what earlier events in the same tick already
    // changed. Concretely: typing in one rule's "Allowed modes" field and then
    // clicking another rule's remove button fires `blur` and then `click`
    // before Yew re-renders anything, so both handlers would read the same
    // pre-edit snapshot and the two resulting PUTs would each omit the other's
    // change, silently discarding whichever landed first.
    //
    // These two refs are the live, always-current picture of the selected
    // lane's editable fields. Each handler mutates them synchronously and
    // sends the *mutated* value, so back-to-back events compose (WASM/JS is
    // single-threaded and a DOM handler runs to completion before the next one
    // starts, so the second handler's `borrow_mut()` always observes the
    // first's mutation). They are resynced from server-confirmed state by the
    // `use_effect_with` below. Rendering still comes from `lanes` /
    // `selected_lane` -- Yew needs reactive state to re-render, and the refs
    // only decide what gets *sent*.
    let access_rules_ref = use_mut_ref(Vec::<api::AccessRuleValue>::new);
    let lane_fields_ref = use_mut_ref(|| (String::new(), 0.0_f64, String::new()));
    let synced_lane_id = use_mut_ref(|| None::<i64>);

    // Pending writes are drained by a single sequential worker so that two
    // near-simultaneous edits reach the server in the order the analyst made
    // them. Firing both concurrently would leave the final database state up
    // to whichever request the server happened to finish last -- the same data
    // loss, just moved from the client to the network.
    let write_queue = use_mut_ref(VecDeque::<PendingWrite>::new);
    let write_worker_running = use_mut_ref(|| false);

    // Mounts the mini-map once, fetches the corridor's cross-sections, and
    // renders them as a clickable point layer.
    {
        let cross_sections = cross_sections.clone();
        let selected_cross_section_id = selected_cross_section_id.clone();
        let error = error.clone();
        use_effect_with((), move |()| {
            let options = to_js_value(&serde_json::json!({
                "container": "corridor-map",
                "style": osm_raster_style(),
                "center": [-73.6, 45.5],
                "zoom": 15,
            }));
            if let Ok(options) = options {
                let map = Map::new(&options);
                crate::maplibre::expose_map_for_e2e_tests(&map);

                let load_map = map.clone();
                let load_cross_sections = cross_sections.clone();
                let load_error = error.clone();
                wasm_bindgen_futures::spawn_local(async move {
                    match api::list_cross_sections(corridor_id).await {
                        Ok(fetched) => {
                            render_cross_sections_layer(&load_map, &fetched);
                            load_cross_sections.set(fetched);
                        }
                        Err(e) => load_error.set(Some(e)),
                    }
                });

                let click_map = map.clone();
                let click_selected = selected_cross_section_id.clone();
                let onclick = Closure::wrap(Box::new(move |event: JsValue| {
                    if let Some(id) = extract_clicked_cross_section_id(&click_map, &event) {
                        click_selected.set(Some(id));
                    }
                }) as Box<dyn FnMut(JsValue)>);
                map.on("click", &onclick);
                onclick.forget();
            }
            || ()
        });
    }

    // Fetches the selected cross-section's lanes whenever the selection changes.
    {
        let lanes = lanes.clone();
        let selected_lane_id = selected_lane_id.clone();
        let error = error.clone();
        use_effect_with(*selected_cross_section_id, move |selected_id| {
            if let Some(cross_section_id) = *selected_id {
                let lanes = lanes.clone();
                let selected_lane_id = selected_lane_id.clone();
                let error = error.clone();
                selected_lane_id.set(None);
                wasm_bindgen_futures::spawn_local(async move {
                    match api::list_lanes(cross_section_id).await {
                        Ok(fetched) => lanes.set(fetched),
                        Err(e) => error.set(Some(e)),
                    }
                });
            }
            || ()
        });
    }

    let selected_cross_section = cross_sections
        .iter()
        .find(|cs| Some(cs.id) == *selected_cross_section_id)
        .cloned();

    let on_label_blur = {
        let cross_sections = cross_sections.clone();
        let selected_cross_section_id = selected_cross_section_id.clone();
        let error = error.clone();
        Callback::from(move |e: FocusEvent| {
            let Some(cross_section_id) = *selected_cross_section_id else {
                return;
            };
            let Some(current) = cross_sections
                .iter()
                .find(|cs| cs.id == cross_section_id)
                .cloned()
            else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlInputElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let cross_sections = cross_sections.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::update_cross_section_label(
                    corridor_id,
                    cross_section_id,
                    Some(value),
                    current.version,
                )
                .await
                {
                    Ok(updated) => {
                        let mut next: Vec<api::CrossSectionSummary> = (*cross_sections).clone();
                        if let Some(entry) = next.iter_mut().find(|cs| cs.id == updated.id) {
                            *entry = updated;
                        }
                        cross_sections.set(next);
                    }
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    let selected_lane = lanes
        .iter()
        .find(|l| Some(l.id) == *selected_lane_id)
        .cloned();

    // Resyncs the live-edit refs from the rendered (server-confirmed) lane.
    {
        let access_rules_ref = access_rules_ref.clone();
        let lane_fields_ref = lane_fields_ref.clone();
        let synced_lane_id = synced_lane_id.clone();
        let write_worker_running = write_worker_running.clone();
        use_effect_with(selected_lane.clone(), move |lane| {
            let lane_id = lane.as_ref().map(|l| l.id);
            let same_lane = lane_id == *synced_lane_id.borrow();
            // While writes for the SAME lane are still in flight the refs hold
            // edits the server hasn't acknowledged yet, and an earlier
            // response carries a lane that predates them -- adopting it would
            // reintroduce exactly the loss this fix removes. The worker clears
            // its "running" flag *before* publishing the last response, so
            // there is always a final, unskipped run of this effect that
            // adopts the server's authoritative state.
            // Switching to a different lane (or to none) always resyncs: the
            // refs describe whichever lane the panel is editing, and any write
            // still queued for the previous lane already carries its own
            // fully-formed payload.
            if !(same_lane && *write_worker_running.borrow()) {
                match lane {
                    Some(lane) => {
                        *access_rules_ref.borrow_mut() = lane.access_rules.clone();
                        *lane_fields_ref.borrow_mut() = (
                            lane.lane_type.clone(),
                            lane.width_meters,
                            lane.direction.clone(),
                        );
                    }
                    None => {
                        access_rules_ref.borrow_mut().clear();
                        *lane_fields_ref.borrow_mut() = (String::new(), 0.0, String::new());
                    }
                }
                *synced_lane_id.borrow_mut() = lane_id;
            }
            || ()
        });
    }

    // Applies one lane-attribute change to the live `lane_fields_ref` and
    // queues a PATCH carrying the mutated triple -- never a triple rebuilt
    // from the render's `selected_lane`.
    let stage_lane_field = {
        let lane_fields_ref = lane_fields_ref.clone();
        let write_queue = write_queue.clone();
        let write_worker_running = write_worker_running.clone();
        let lanes = lanes.clone();
        let error = error.clone();
        Callback::from(move |(lane_id, change): (i64, LaneFieldChange)| {
            let (lane_type, width_meters, direction) = {
                let mut fields = lane_fields_ref.borrow_mut();
                match change {
                    LaneFieldChange::Type(value) => fields.0 = value,
                    LaneFieldChange::Width(value) => fields.1 = value,
                    LaneFieldChange::Direction(value) => fields.2 = value,
                }
                fields.clone()
            };
            enqueue_write(
                &write_queue,
                &write_worker_running,
                &lanes,
                &error,
                PendingWrite::LaneFields {
                    lane_id,
                    lane_type,
                    width_meters,
                    direction,
                },
            );
        })
    };

    let on_width_blur = {
        let selected_lane_id = selected_lane_id.clone();
        let stage_lane_field = stage_lane_field.clone();
        Callback::from(move |e: FocusEvent| {
            let Some(lane_id) = *selected_lane_id else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlInputElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let Ok(width_meters) = value.parse::<f64>() else {
                return;
            };
            stage_lane_field.emit((lane_id, LaneFieldChange::Width(width_meters)));
        })
    };

    let on_type_change = {
        let selected_lane_id = selected_lane_id.clone();
        let stage_lane_field = stage_lane_field.clone();
        Callback::from(move |e: Event| {
            let Some(lane_id) = *selected_lane_id else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            stage_lane_field.emit((lane_id, LaneFieldChange::Type(value)));
        })
    };

    let on_direction_change = {
        let selected_lane_id = selected_lane_id.clone();
        let stage_lane_field = stage_lane_field.clone();
        Callback::from(move |e: Event| {
            let Some(lane_id) = *selected_lane_id else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            stage_lane_field.emit((lane_id, LaneFieldChange::Direction(value)));
        })
    };

    let on_insert_lane = {
        let lanes = lanes.clone();
        let selected_cross_section_id = selected_cross_section_id.clone();
        let error = error.clone();
        Callback::from(move |(before, after): (Option<f64>, Option<f64>)| {
            let Some(cross_section_id) = *selected_cross_section_id else {
                return;
            };
            let lanes = lanes.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::insert_lane(
                    cross_section_id,
                    "travel".to_string(),
                    3.0,
                    "forward".to_string(),
                    before,
                    after,
                )
                .await
                {
                    Ok(_) => match api::list_lanes(cross_section_id).await {
                        Ok(fetched) => lanes.set(fetched),
                        Err(e) => error.set(Some(e)),
                    },
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    let on_remove_lane = {
        let lanes = lanes.clone();
        let selected_lane_id = selected_lane_id.clone();
        let error = error.clone();
        Callback::from(move |lane_id: i64| {
            let lanes = lanes.clone();
            let selected_lane_id = selected_lane_id.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::delete_lane(lane_id).await {
                    Ok(()) => {
                        let next: Vec<api::LaneResponse> =
                            lanes.iter().filter(|l| l.id != lane_id).cloned().collect();
                        lanes.set(next);
                        selected_lane_id.set(None);
                    }
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    // Every access-rule control (add/remove a rule, edit one of its fields)
    // persists the lane's full rule list via `set_access_rules` -- access rules
    // have no per-rule `id` in the domain model, so whole-list replace
    // (matching the repository/API layer's own shape) is simpler than tracking
    // per-rule identity in the UI. Because the whole list is replaced, the list
    // that gets sent must be the live one from `access_rules_ref` with this
    // edit applied on top, never one rebuilt from the render's `selected_lane`.
    let stage_access_rule_edit = {
        let access_rules_ref = access_rules_ref.clone();
        let write_queue = write_queue.clone();
        let write_worker_running = write_worker_running.clone();
        let lanes = lanes.clone();
        let error = error.clone();
        Callback::from(move |(lane_id, edit): (i64, AccessRuleEdit)| {
            let rules = {
                let mut live = access_rules_ref.borrow_mut();
                apply_access_rule_edit(&mut live, edit);
                normalized_for_persist(&live)
            };
            enqueue_write(
                &write_queue,
                &write_worker_running,
                &lanes,
                &error,
                PendingWrite::AccessRules { lane_id, rules },
            );
        })
    };

    let on_add_time_window = {
        let selected_lane_id = selected_lane_id.clone();
        let stage_access_rule_edit = stage_access_rule_edit.clone();
        Callback::from(move |_: MouseEvent| {
            if let Some(lane_id) = *selected_lane_id {
                stage_access_rule_edit.emit((lane_id, AccessRuleEdit::AddTimeWindow));
            }
        })
    };

    let on_remove_access_rule = {
        let selected_lane_id = selected_lane_id.clone();
        let stage_access_rule_edit = stage_access_rule_edit.clone();
        Callback::from(move |rule_index: usize| {
            if let Some(lane_id) = *selected_lane_id {
                stage_access_rule_edit.emit((lane_id, AccessRuleEdit::Remove(rule_index)));
            }
        })
    };

    let on_rule_days_blur = {
        let selected_lane_id = selected_lane_id.clone();
        let stage_access_rule_edit = stage_access_rule_edit.clone();
        Callback::from(move |(rule_index, value): (usize, String)| {
            if let Some(lane_id) = *selected_lane_id {
                stage_access_rule_edit.emit((lane_id, AccessRuleEdit::Days(rule_index, value)));
            }
        })
    };

    let on_rule_start_time_blur = {
        let selected_lane_id = selected_lane_id.clone();
        let stage_access_rule_edit = stage_access_rule_edit.clone();
        Callback::from(move |(rule_index, value): (usize, String)| {
            if let Some(lane_id) = *selected_lane_id {
                stage_access_rule_edit
                    .emit((lane_id, AccessRuleEdit::StartTime(rule_index, value)));
            }
        })
    };

    let on_rule_end_time_blur = {
        let selected_lane_id = selected_lane_id.clone();
        let stage_access_rule_edit = stage_access_rule_edit.clone();
        Callback::from(move |(rule_index, value): (usize, String)| {
            if let Some(lane_id) = *selected_lane_id {
                stage_access_rule_edit.emit((lane_id, AccessRuleEdit::EndTime(rule_index, value)));
            }
        })
    };

    let on_rule_modes_blur = {
        let selected_lane_id = selected_lane_id.clone();
        let stage_access_rule_edit = stage_access_rule_edit.clone();
        Callback::from(move |(rule_index, value): (usize, String)| {
            if let Some(lane_id) = *selected_lane_id {
                stage_access_rule_edit.emit((lane_id, AccessRuleEdit::Modes(rule_index, value)));
            }
        })
    };

    html! {
        <div class="builder-region-map">
            <div id="corridor-map" style="width: 100%; height: 100vh;"></div>
            <div class="setup-card" style="position:absolute; top:16px; right:16px; z-index:10; width:360px; max-height: calc(100vh - 32px); overflow-y: auto;">
                if let Some(err) = &*error {
                    <div class="alert alert--err">{ err }</div>
                }
                if let Some(cs) = &selected_cross_section {
                    <label class="field-label" for="cross-section-label">{ "Cross-section label" }</label>
                    <input class="field" id="cross-section-label" type="text" value={cs.label.clone().unwrap_or_default()} onblur={on_label_blur} />

                    if let Some(intersection_id) = cs.intersection_id {
                        <div style="margin-top:0.75rem;">
                            <Link<Route> classes="chip" to={Route::Intersection { remix_id, cross_section_id: cs.id }}>
                                { format!("Edit intersection #{intersection_id}") }
                            </Link<Route>>
                        </div>
                    }

                    <div class="xs-diagram" style="margin-top:1rem;">
                        { insert_button("Add lane at start", None, lanes.first().map(|l| l.position), on_insert_lane.clone()) }
                        { for lanes.iter().enumerate().map(|(i, lane)| {
                            let lane_id = lane.id;
                            let next_position = lanes.get(i + 1).map(|l| l.position);
                            let onclick = {
                                let selected_lane_id = selected_lane_id.clone();
                                Callback::from(move |_: MouseEvent| selected_lane_id.set(Some(lane_id)))
                            };
                            html! {
                                <>
                                    <div
                                        class="xs-lane"
                                        onclick={onclick}
                                        style={format!("flex: {} 0 auto; background:{};", lane.width_meters, lane_color(&lane.lane_type))}
                                    >
                                        { lane_type_label(&lane.lane_type) }
                                        <span class="w">{ format!("{}m", lane.width_meters) }</span>
                                    </div>
                                    { insert_button(&format!("Add lane after {}", lane_type_label(&lane.lane_type)), Some(lane.position), next_position, on_insert_lane.clone()) }
                                </>
                            }
                        }) }
                    </div>

                    if let Some(lane) = &selected_lane {
                        <div style="margin-top:1rem;">
                            <label class="field-label" for="lane-width">{ "Width (meters)" }</label>
                            <input class="field" id="lane-width" type="text" value={lane.width_meters.to_string()} onblur={on_width_blur} />

                            <label class="field-label" for="lane-type" style="margin-top:0.75rem;">{ "Lane type" }</label>
                            <select class="field" id="lane-type" onchange={on_type_change}>
                                { for LANE_TYPES.iter().map(|(value, label)| html! {
                                    <option value={*value} selected={lane.lane_type == *value}>{ *label }</option>
                                }) }
                            </select>

                            <label class="field-label" for="lane-direction" style="margin-top:0.75rem;">{ "Direction" }</label>
                            <select class="field" id="lane-direction" onchange={on_direction_change}>
                                { for LANE_DIRECTIONS.iter().map(|(value, label)| html! {
                                    <option value={*value} selected={lane.direction == *value}>{ *label }</option>
                                }) }
                            </select>

                            <p class="field-label" style="margin-top:0.75rem;">{ "Access rules" }</p>
                            { for lane.access_rules.iter().enumerate().map(|(i, rule)| {
                                let days = rule.time_window.as_ref().map(|w| w.days.clone()).unwrap_or_default();
                                let start_time = rule.time_window.as_ref().map(|w| w.start_time.clone()).unwrap_or_default();
                                let end_time = rule.time_window.as_ref().map(|w| w.end_time.clone()).unwrap_or_default();
                                let modes = rule.allowed_modes.join(",");

                                let days_onblur = { let f = on_rule_days_blur.clone(); on_field_blur(move |v| f.emit((i, v))) };
                                let start_onblur = { let f = on_rule_start_time_blur.clone(); on_field_blur(move |v| f.emit((i, v))) };
                                let end_onblur = { let f = on_rule_end_time_blur.clone(); on_field_blur(move |v| f.emit((i, v))) };
                                let modes_onblur = { let f = on_rule_modes_blur.clone(); on_field_blur(move |v| f.emit((i, v))) };
                                let remove_onclick = {
                                    let on_remove_access_rule = on_remove_access_rule.clone();
                                    Callback::from(move |_: MouseEvent| on_remove_access_rule.emit(i))
                                };

                                html! {
                                    <div class="access-rule">
                                        <input class="field" aria-label="Days" placeholder="days (blank = always)" value={days} onblur={days_onblur} />
                                        <input class="field" aria-label="Start time" placeholder="HH:MM" value={start_time} onblur={start_onblur} />
                                        <input class="field" aria-label="End time" placeholder="HH:MM" value={end_time} onblur={end_onblur} />
                                        <input class="field" aria-label="Allowed modes" placeholder="car,transit,..." value={modes} onblur={modes_onblur} />
                                        <button class="btn" aria-label="Remove access rule" onclick={remove_onclick}>{ "✕" }</button>
                                    </div>
                                }
                            }) }
                            <button class="btn" onclick={on_add_time_window}>{ "+ Add time window" }</button>

                            <div style="margin-top:0.75rem;">
                                <button class="btn" aria-label="Remove lane" onclick={{
                                    let lane_id = lane.id;
                                    let on_remove_lane = on_remove_lane.clone();
                                    Callback::from(move |_: MouseEvent| on_remove_lane.emit(lane_id))
                                }}>{ "Remove lane" }</button>
                            </div>
                        </div>
                    }
                } else {
                    <p>{ "Click a point on the map to select a cross-section." }</p>
                }
                <div style="margin-top:1rem;">
                    <Link<Route> classes="chip" to={Route::RegionMap { remix_id }}>{ "Back to map" }</Link<Route>>
                </div>
            </div>
        </div>
    }
}

const LANE_TYPES: &[(&str, &str)] = &[
    ("travel", "Travel"),
    ("turn", "Turn"),
    ("transit", "Transit"),
    ("queue_jump", "Queue Jump"),
    ("cycle_lane", "Cycle Lane"),
    ("cycle_track", "Cycle Track"),
    ("parking", "Parking"),
    ("sidewalk", "Sidewalk"),
    ("median", "Median"),
    ("buffer", "Buffer"),
];

const LANE_DIRECTIONS: &[(&str, &str)] = &[
    ("forward", "Forward"),
    ("backward", "Backward"),
    ("both", "Both"),
    ("none", "None"),
];

fn lane_type_label(lane_type: &str) -> &'static str {
    LANE_TYPES
        .iter()
        .find(|(value, _)| *value == lane_type)
        .map(|(_, label)| *label)
        .unwrap_or("Lane")
}

/// Colors match the cross-section diagram mockup approved during the original
/// corridor-design brainstorming (Travel/Sidewalk/Parking/CycleLane/Median);
/// the remaining five types use the Lumina design system's existing palette
/// (`DESIGN.md`) rather than inventing new colors.
fn lane_color(lane_type: &str) -> &'static str {
    match lane_type {
        "travel" => "#1D4E89",      // oxford-500
        "turn" => "#163A67",        // oxford-600
        "transit" => "#C8463A",     // cinnabar-500
        "queue_jump" => "#A83530",  // cinnabar-600
        "cycle_lane" => "#3D9A6B",  // sage
        "cycle_track" => "#2E7A54", // sage, darker
        "parking" => "#6b6b8f",
        "sidewalk" => "#9a9a9a",
        "median" => "#C8A050",
        "buffer" => "#C8C4BC", // cream-400
        _ => "#888480",
    }
}

/// Which of the selected lane's three editable attributes one event changed.
/// Applied to the live `lane_fields_ref` triple, which then supplies the other
/// two -- so an edit never carries a stale copy of its neighbors.
enum LaneFieldChange {
    Type(String),
    Width(f64),
    Direction(String),
}

/// One user-level edit to a lane's access-rule list, applied in place to the
/// live list by `apply_access_rule_edit`.
enum AccessRuleEdit {
    AddTimeWindow,
    Remove(usize),
    Days(usize, String),
    StartTime(usize, String),
    EndTime(usize, String),
    Modes(usize, String),
}

/// Pure: applies one edit to `rules` in place.
///
/// Out-of-range indices are ignored rather than panicking. A rule index comes
/// from the last render, and a burst of edits (two removals clicked before the
/// first response lands, say) can shrink the live list below it; the render
/// catches up as soon as the writes are acknowledged.
fn apply_access_rule_edit(rules: &mut Vec<api::AccessRuleValue>, edit: AccessRuleEdit) {
    match edit {
        AccessRuleEdit::AddTimeWindow => rules.push(api::AccessRuleValue {
            time_window: Some(api::TimeWindowValue {
                days: "weekdays".to_string(),
                start_time: "07:00".to_string(),
                end_time: "09:00".to_string(),
            }),
            allowed_modes: vec![],
        }),
        AccessRuleEdit::Remove(index) => {
            if index < rules.len() {
                rules.remove(index);
            }
        }
        AccessRuleEdit::Days(index, value) => {
            set_time_window_field(rules, index, |w| w.days = value);
        }
        AccessRuleEdit::StartTime(index, value) => {
            set_time_window_field(rules, index, |w| w.start_time = value);
        }
        AccessRuleEdit::EndTime(index, value) => {
            set_time_window_field(rules, index, |w| w.end_time = value);
        }
        AccessRuleEdit::Modes(index, value) => {
            if let Some(rule) = rules.get_mut(index) {
                rule.allowed_modes = value
                    .split(',')
                    .map(|s| s.trim().to_string())
                    .filter(|s| !s.is_empty())
                    .collect();
            }
        }
    }
}

/// Sets one field of one access rule's `TimeWindowValue`, defaulting to an
/// empty window if the rule had none yet (normalized back to `None` by
/// `normalized_for_persist` if `days` ends up blank).
fn set_time_window_field(
    rules: &mut [api::AccessRuleValue],
    index: usize,
    set_field: impl FnOnce(&mut api::TimeWindowValue),
) {
    let Some(rule) = rules.get_mut(index) else {
        return;
    };
    let mut window = rule.time_window.clone().unwrap_or(api::TimeWindowValue {
        days: String::new(),
        start_time: String::new(),
        end_time: String::new(),
    });
    set_field(&mut window);
    rule.time_window = Some(window);
}

/// Pure: an edited `days` field of "" means "always active" -- normalize back
/// to `time_window: None` before sending, rather than sending a half-filled
/// time window. The live ref keeps the un-normalized form; it converges once
/// the server's response is adopted.
fn normalized_for_persist(rules: &[api::AccessRuleValue]) -> Vec<api::AccessRuleValue> {
    rules
        .iter()
        .map(|rule| api::AccessRuleValue {
            time_window: rule
                .time_window
                .clone()
                .filter(|w| !w.days.trim().is_empty()),
            allowed_modes: rule.allowed_modes.clone(),
        })
        .collect()
}

/// One queued server write. Both variants replace their target wholesale
/// (`PATCH /api/lanes/:id`, `PUT /api/lanes/:id/access-rules`), so each carries
/// a complete, self-contained payload captured at the moment the edit happened
/// -- nothing is re-read from a ref at send time, which is what makes it safe
/// for a write to sit in the queue while the analyst keeps editing.
enum PendingWrite {
    LaneFields {
        lane_id: i64,
        lane_type: String,
        width_meters: f64,
        direction: String,
    },
    AccessRules {
        lane_id: i64,
        rules: Vec<api::AccessRuleValue>,
    },
}

/// The server's confirmed state after one `PendingWrite`.
enum WriteOutcome {
    Lane(api::LaneResponse),
    AccessRules {
        lane_id: i64,
        rules: Vec<api::AccessRuleValue>,
    },
}

/// Queues `write` and, unless a drain loop is already running, starts one.
///
/// The queue exists so writes reach the server strictly in the order the
/// analyst made them. Two overlapping whole-record writes fired concurrently
/// would leave the final database row up to whichever request the server
/// happened to finish last -- so even with each payload correctly carrying the
/// union of the edits so far, an out-of-order arrival could still drop the
/// newer one.
fn enqueue_write(
    queue: &Rc<RefCell<VecDeque<PendingWrite>>>,
    running: &Rc<RefCell<bool>>,
    lanes: &UseStateHandle<Vec<api::LaneResponse>>,
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
    let lanes = lanes.clone();
    let error = error.clone();
    wasm_bindgen_futures::spawn_local(async move {
        // Working copy of the lane list, seeded from the render that queued the
        // first write of this burst and updated in place as each response
        // lands. A `UseStateHandle` captured into a future always dereferences
        // to its creating render's value, so re-cloning `*lanes` per response
        // would make every response after the first overwrite its predecessor
        // with an ever-staler base.
        let mut working: Vec<api::LaneResponse> = (*lanes).clone();
        loop {
            let next = queue.borrow_mut().pop_front();
            let Some(write) = next else {
                *running.borrow_mut() = false;
                break;
            };
            let outcome = send_write(write).await;
            // Clearing the flag BEFORE publishing matters: `lanes.set` can
            // re-render (and so run the resync effect) synchronously, and on
            // the last response of a burst that effect must see an idle queue
            // so it adopts the server's authoritative state into the refs.
            let idle = queue.borrow().is_empty();
            if idle {
                *running.borrow_mut() = false;
            }
            match outcome {
                Ok(outcome) => {
                    apply_write_outcome(&mut working, outcome);
                    lanes.set(working.clone());
                }
                Err(e) => error.set(Some(e)),
            }
            if idle {
                break;
            }
        }
    });
}

async fn send_write(write: PendingWrite) -> Result<WriteOutcome, String> {
    match write {
        PendingWrite::LaneFields {
            lane_id,
            lane_type,
            width_meters,
            direction,
        } => api::update_lane(lane_id, lane_type, width_meters, direction)
            .await
            .map(WriteOutcome::Lane),
        PendingWrite::AccessRules { lane_id, rules } => api::set_access_rules(lane_id, rules)
            .await
            .map(|rules| WriteOutcome::AccessRules { lane_id, rules }),
    }
}

/// Pure: merges one server response into the lane list backing the diagram.
fn apply_write_outcome(lanes: &mut [api::LaneResponse], outcome: WriteOutcome) {
    match outcome {
        WriteOutcome::Lane(updated) => {
            if let Some(entry) = lanes.iter_mut().find(|l| l.id == updated.id) {
                *entry = updated;
            }
        }
        WriteOutcome::AccessRules { lane_id, rules } => {
            if let Some(entry) = lanes.iter_mut().find(|l| l.id == lane_id) {
                entry.access_rules = rules;
            }
        }
    }
}

/// Renders one "+" gap-insert control. `before`/`after` are the flanking
/// lanes' positions (`None` at either end of the sequence) -- passed straight
/// through to `insert_lane`, which resolves them via `assign_position` on the
/// server.
fn insert_button(
    label: &str,
    before: Option<f64>,
    after: Option<f64>,
    on_insert_lane: Callback<(Option<f64>, Option<f64>)>,
) -> Html {
    let onclick = Callback::from(move |_: MouseEvent| on_insert_lane.emit((before, after)));
    html! {
        <button class="xs-add" aria-label={label.to_string()} onclick={onclick}>{ "+" }</button>
    }
}

/// Wraps a `String -> ()` closure as an `onblur` handler that reads the
/// blurred `<input>`'s value. Used for the access-rule fields, where every
/// field shares the same "read the input, call back with (index, value)"
/// shape.
fn on_field_blur(f: impl Fn(String) + 'static) -> Callback<FocusEvent> {
    Callback::from(move |e: FocusEvent| {
        let value = e
            .target_dyn_into::<web_sys::HtmlInputElement>()
            .map(|el| el.value())
            .unwrap_or_default();
        f(value);
    })
}

fn extract_clicked_cross_section_id(map: &Map, event: &JsValue) -> Option<i64> {
    let point = js_sys::Reflect::get(event, &"point".into()).ok()?;
    let options = js_sys::Object::new();
    let layers = js_sys::Array::of1(&"cross-section-points".into());
    js_sys::Reflect::set(&options, &"layers".into(), &layers).ok()?;

    let features = map.query_rendered_features(&point, &options);
    if features.length() == 0 {
        return None;
    }
    let feature = features.get(0);
    let properties = js_sys::Reflect::get(&feature, &"properties".into()).ok()?;
    js_sys::Reflect::get(&properties, &"cross_section_id".into())
        .ok()
        .and_then(|v| v.as_f64())
        .map(|v| v as i64)
}

fn render_cross_sections_layer(map: &Map, cross_sections: &[api::CrossSectionSummary]) {
    let features: Vec<serde_json::Value> = cross_sections
        .iter()
        .map(|cs| {
            serde_json::json!({
                "type": "Feature",
                "properties": { "cross_section_id": cs.id },
                "geometry": { "type": "Point", "coordinates": [cs.lon, cs.lat] },
            })
        })
        .collect();
    let collection = serde_json::json!({ "type": "FeatureCollection", "features": features });

    if let Ok(source) = to_js_value(&serde_json::json!({ "type": "geojson", "data": collection })) {
        map.add_source("cross-section-points", &source);
    }
    if let Ok(layer) = to_js_value(&serde_json::json!({
        "id": "cross-section-points",
        "type": "circle",
        "source": "cross-section-points",
        "paint": { "circle-radius": 8, "circle-color": "#C8463A" }
    })) {
        map.add_layer(&layer);
    }
}

fn to_js_value<T: serde::Serialize>(value: &T) -> Result<JsValue, String> {
    let json = serde_json::to_string(value).map_err(|e| e.to_string())?;
    js_sys::JSON::parse(&json).map_err(|e| format!("{e:?}"))
}

fn osm_raster_style() -> serde_json::Value {
    serde_json::json!({
        "version": 8,
        "sources": {
            "osm": {
                "type": "raster",
                "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                "tileSize": 256,
                "attribution": "© OpenStreetMap contributors"
            }
        },
        "layers": [
            { "id": "osm-tiles", "type": "raster", "source": "osm" }
        ]
    })
}
