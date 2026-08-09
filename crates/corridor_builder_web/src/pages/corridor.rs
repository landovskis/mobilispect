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

    let on_width_blur = {
        let lanes = lanes.clone();
        let selected_lane_id = selected_lane_id.clone();
        let error = error.clone();
        Callback::from(move |e: FocusEvent| {
            let Some(lane_id) = *selected_lane_id else {
                return;
            };
            let Some(current) = lanes.iter().find(|l| l.id == lane_id).cloned() else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlInputElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let Ok(width_meters) = value.parse::<f64>() else {
                return;
            };
            persist_lane_update(
                lanes.clone(),
                error.clone(),
                lane_id,
                current.lane_type.clone(),
                width_meters,
                current.direction.clone(),
            );
        })
    };

    let on_type_change = {
        let lanes = lanes.clone();
        let selected_lane_id = selected_lane_id.clone();
        let error = error.clone();
        Callback::from(move |e: Event| {
            let Some(lane_id) = *selected_lane_id else {
                return;
            };
            let Some(current) = lanes.iter().find(|l| l.id == lane_id).cloned() else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            persist_lane_update(
                lanes.clone(),
                error.clone(),
                lane_id,
                value,
                current.width_meters,
                current.direction.clone(),
            );
        })
    };

    let on_direction_change = {
        let lanes = lanes.clone();
        let selected_lane_id = selected_lane_id.clone();
        let error = error.clone();
        Callback::from(move |e: Event| {
            let Some(lane_id) = *selected_lane_id else {
                return;
            };
            let Some(current) = lanes.iter().find(|l| l.id == lane_id).cloned() else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            persist_lane_update(
                lanes.clone(),
                error.clone(),
                lane_id,
                current.lane_type.clone(),
                current.width_meters,
                value,
            );
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
    // rebuilds the lane's full rule list client-side and immediately persists
    // it via `set_access_rules` -- access rules have no per-rule `id` in the
    // domain model, so whole-list replace (matching the repository/API
    // layer's own shape) is simpler than tracking per-rule identity in the UI.
    let persist_access_rules = {
        let lanes = lanes.clone();
        let error = error.clone();
        Callback::from(move |(lane_id, rules): (i64, Vec<api::AccessRuleValue>)| {
            // An edited `days` field of "" means "always active" -- normalize
            // back to `time_window: None` before sending, rather than sending
            // a half-filled time window.
            let normalized: Vec<api::AccessRuleValue> = rules
                .into_iter()
                .map(|rule| api::AccessRuleValue {
                    time_window: rule.time_window.filter(|w| !w.days.trim().is_empty()),
                    allowed_modes: rule.allowed_modes,
                })
                .collect();
            let lanes = lanes.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::set_access_rules(lane_id, normalized).await {
                    Ok(updated_rules) => {
                        let mut next: Vec<api::LaneResponse> = (*lanes).clone();
                        if let Some(entry) = next.iter_mut().find(|l| l.id == lane_id) {
                            entry.access_rules = updated_rules;
                        }
                        lanes.set(next);
                    }
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    let on_add_time_window = {
        let selected_lane = selected_lane.clone();
        let persist_access_rules = persist_access_rules.clone();
        Callback::from(move |_: MouseEvent| {
            let Some(lane) = &selected_lane else {
                return;
            };
            let mut rules = lane.access_rules.clone();
            rules.push(api::AccessRuleValue {
                time_window: Some(api::TimeWindowValue {
                    days: "weekdays".to_string(),
                    start_time: "07:00".to_string(),
                    end_time: "09:00".to_string(),
                }),
                allowed_modes: vec![],
            });
            persist_access_rules.emit((lane.id, rules));
        })
    };

    let on_remove_access_rule = {
        let selected_lane = selected_lane.clone();
        let persist_access_rules = persist_access_rules.clone();
        Callback::from(move |rule_index: usize| {
            let Some(lane) = &selected_lane else {
                return;
            };
            let rules: Vec<api::AccessRuleValue> = lane
                .access_rules
                .iter()
                .enumerate()
                .filter(|(i, _)| *i != rule_index)
                .map(|(_, r)| r.clone())
                .collect();
            persist_access_rules.emit((lane.id, rules));
        })
    };

    let on_rule_days_blur = {
        let selected_lane = selected_lane.clone();
        let persist_access_rules = persist_access_rules.clone();
        Callback::from(move |(rule_index, value): (usize, String)| {
            let Some(lane) = &selected_lane else {
                return;
            };
            let rules = with_rule_time_window_field(lane, rule_index, value, |w, v| w.days = v);
            persist_access_rules.emit((lane.id, rules));
        })
    };

    let on_rule_start_time_blur = {
        let selected_lane = selected_lane.clone();
        let persist_access_rules = persist_access_rules.clone();
        Callback::from(move |(rule_index, value): (usize, String)| {
            let Some(lane) = &selected_lane else {
                return;
            };
            let rules =
                with_rule_time_window_field(lane, rule_index, value, |w, v| w.start_time = v);
            persist_access_rules.emit((lane.id, rules));
        })
    };

    let on_rule_end_time_blur = {
        let selected_lane = selected_lane.clone();
        let persist_access_rules = persist_access_rules.clone();
        Callback::from(move |(rule_index, value): (usize, String)| {
            let Some(lane) = &selected_lane else {
                return;
            };
            let rules = with_rule_time_window_field(lane, rule_index, value, |w, v| w.end_time = v);
            persist_access_rules.emit((lane.id, rules));
        })
    };

    let on_rule_modes_blur = {
        let selected_lane = selected_lane.clone();
        let persist_access_rules = persist_access_rules.clone();
        Callback::from(move |(rule_index, value): (usize, String)| {
            let Some(lane) = &selected_lane else {
                return;
            };
            let mut rules = lane.access_rules.clone();
            if let Some(rule) = rules.get_mut(rule_index) {
                rule.allowed_modes = value
                    .split(',')
                    .map(|s| s.trim().to_string())
                    .filter(|s| !s.is_empty())
                    .collect();
            }
            persist_access_rules.emit((lane.id, rules));
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

/// Shared tail of `on_width_blur`/`on_type_change`/`on_direction_change`:
/// each of those callbacks reads the currently-selected lane's current
/// `(lane_type, width_meters, direction)` and computes one changed field
/// itself (parsing/reading the triggering event is per-field and stays in
/// the caller), then hands all three fields here to persist via
/// `update_lane` and merge the result back into `lanes`.
fn persist_lane_update(
    lanes: UseStateHandle<Vec<api::LaneResponse>>,
    error: UseStateHandle<Option<String>>,
    lane_id: i64,
    lane_type: String,
    width_meters: f64,
    direction: String,
) {
    wasm_bindgen_futures::spawn_local(async move {
        match api::update_lane(lane_id, lane_type, width_meters, direction).await {
            Ok(updated) => {
                let mut next: Vec<api::LaneResponse> = (*lanes).clone();
                if let Some(entry) = next.iter_mut().find(|l| l.id == updated.id) {
                    *entry = updated;
                }
                lanes.set(next);
            }
            Err(e) => error.set(Some(e)),
        }
    });
}

/// Shared body of `on_rule_days_blur`/`on_rule_start_time_blur`/
/// `on_rule_end_time_blur`: each edits one field of one access rule's
/// `TimeWindowValue` (defaulting to an empty window if the rule had none
/// yet -- normalized back to `None` later by `persist_access_rules` if
/// `days` ends up blank) and returns the lane's full rebuilt rule list,
/// ready for the caller to hand to `persist_access_rules.emit(...)`.
fn with_rule_time_window_field(
    lane: &api::LaneResponse,
    rule_index: usize,
    value: String,
    set_field: impl Fn(&mut api::TimeWindowValue, String),
) -> Vec<api::AccessRuleValue> {
    let mut rules = lane.access_rules.clone();
    if let Some(rule) = rules.get_mut(rule_index) {
        let mut window = rule.time_window.clone().unwrap_or(api::TimeWindowValue {
            days: String::new(),
            start_time: String::new(),
            end_time: String::new(),
        });
        set_field(&mut window, value);
        rule.time_window = Some(window);
    }
    rules
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
