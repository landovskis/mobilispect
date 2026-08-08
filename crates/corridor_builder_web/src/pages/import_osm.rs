use std::cell::RefCell;
use std::collections::HashSet;
use std::rc::Rc;

use wasm_bindgen::prelude::*;
use yew::prelude::*;
use yew_router::prelude::*;

use crate::api;
use crate::app::Route;
use crate::maplibre::Map;

#[derive(Properties, PartialEq)]
pub struct ImportOsmPageProps {
    pub remix_id: i64,
}

/// Minimum zoom level (roughly a few city blocks across) required before
/// "Load streets" is enabled — keeps every Overpass query small. See the
/// design spec's WASM UI Layer section.
const MIN_LOAD_STREETS_ZOOM: f64 = 15.0;

#[component]
pub fn ImportOsmPage(props: &ImportOsmPageProps) -> Html {
    let remix_id = props.remix_id;
    let navigator = use_navigator().expect("BrowserRouter provides a Navigator");

    // `map_ref`/`ways_ref`/`selected_ref` are `Rc<RefCell<...>>` (via
    // `use_mut_ref`), not `UseStateHandle`s, deliberately: the map's native
    // click listener below is a `wasm_bindgen::Closure` registered once (via
    // `map.on(...)` + `.forget()`) and reused for the page's whole lifetime,
    // not recreated on every Yew render the way a `Callback` is. A
    // `UseStateHandle` captured into a Closure like that only ever sees the
    // value from the render where the Closure was created — this is the
    // exact hazard the manual-trace page's point counter hit (see
    // `pages/manual_trace.rs`'s comment on `click_point_count`), fixed there
    // by reading from a live, non-snapshotted source instead of an old
    // `UseStateHandle` dereference. `RefCell::borrow()` always returns the
    // current value, so the click closure reads live state through these
    // three instead. `selection_count`/`name_value` below remain
    // `UseStateHandle`s because the click closure only ever *writes* fresh,
    // freshly-computed values into them (`.set(...)`) -- writing a computed
    // value has no staleness hazard, only *reading* a handle to compute one
    // does.
    let map_ref = use_mut_ref(|| None::<Map>);
    let ways_ref = use_mut_ref(|| None::<Vec<api::OsmWayResponse>>);
    let selected_ref = use_mut_ref(HashSet::<i64>::new);

    let selection_count = use_state(|| 0usize);
    let name_value = use_state(String::new);
    let zoom_ok = use_state(|| false);
    let error = use_state(|| None::<String>);

    // Mounts the map exactly once, on first render: creates it, tracks zoom,
    // and registers the ways-layer click listener up front (querying
    // `queryRenderedFeatures` against a layer that doesn't exist yet, before
    // "Load streets" is clicked, simply returns no results -- registering
    // early is harmless and avoids ever needing to re-register).
    {
        let map_ref = map_ref.clone();
        let ways_ref = ways_ref.clone();
        let selected_ref = selected_ref.clone();
        let selection_count = selection_count.clone();
        let name_value = name_value.clone();
        let zoom_ok = zoom_ok.clone();
        use_effect_with((), move |()| {
            let options = to_js_value(&serde_json::json!({
                "container": "import-map",
                "style": osm_raster_style(),
                "center": [-73.5795, 45.5005],
                "zoom": 16,
            }));
            if let Ok(options) = options {
                let map = Map::new(&options);
                *map_ref.borrow_mut() = Some(map.clone());
                zoom_ok.set(map.get_zoom() >= MIN_LOAD_STREETS_ZOOM);

                let zoom_watch_map = map.clone();
                let zoom_watch_flag = zoom_ok.clone();
                let onzoom = Closure::wrap(Box::new(move |_event: JsValue| {
                    zoom_watch_flag.set(zoom_watch_map.get_zoom() >= MIN_LOAD_STREETS_ZOOM);
                }) as Box<dyn FnMut(JsValue)>);
                map.on("zoomend", &onzoom);
                map.on("moveend", &onzoom);
                onzoom.forget();

                let click_map = map.clone();
                let click_ways_ref = ways_ref.clone();
                let click_selected_ref = selected_ref.clone();
                let click_selection_count = selection_count.clone();
                let click_name_value = name_value.clone();
                let onclick = Closure::wrap(Box::new(move |event: JsValue| {
                    handle_way_click(
                        &click_map,
                        &event,
                        &click_ways_ref,
                        &click_selected_ref,
                        &click_selection_count,
                        &click_name_value,
                    );
                }) as Box<dyn FnMut(JsValue)>);
                map.on("click", &onclick);
                onclick.forget();

                crate::maplibre::expose_map_for_e2e_tests(&map);
            }
            || ()
        });
    }

    let on_load_streets = {
        let map_ref = map_ref.clone();
        let ways_ref = ways_ref.clone();
        let error = error.clone();
        Callback::from(move |_: MouseEvent| {
            let Some(map) = map_ref.borrow().clone() else {
                return;
            };
            let bounds = map.get_bounds();
            let (min_lat, min_lon, max_lat, max_lon) = (
                bounds.get_south(),
                bounds.get_west(),
                bounds.get_north(),
                bounds.get_east(),
            );
            let map_ref = map_ref.clone();
            let ways_ref = ways_ref.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::search_streets(remix_id, min_lat, min_lon, max_lat, max_lon).await {
                    Ok(fetched) => {
                        if let Some(map) = map_ref.borrow().clone() {
                            render_ways_layer(&map, &fetched);
                            fit_bounds_to_ways(&map, &fetched);
                        }
                        *ways_ref.borrow_mut() = Some(fetched);
                    }
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    let on_name_input = {
        let name_value = name_value.clone();
        Callback::from(move |e: InputEvent| {
            let value = e
                .target_dyn_into::<web_sys::HtmlInputElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            name_value.set(value);
        })
    };

    let on_import = {
        let ways_ref = ways_ref.clone();
        let selected_ref = selected_ref.clone();
        let name_value = name_value.clone();
        let error = error.clone();
        let navigator = navigator.clone();
        Callback::from(move |_: MouseEvent| {
            let Some(all_ways) = ways_ref.borrow().clone() else {
                return;
            };
            let selected_ids = selected_ref.borrow().clone();
            let selected: Vec<api::OsmWayResponse> = all_ways
                .into_iter()
                .filter(|w| selected_ids.contains(&w.osm_way_id))
                .collect();
            if selected.is_empty() {
                return;
            }
            let name = (*name_value).clone();
            let error = error.clone();
            let navigator = navigator.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::import_corridor(remix_id, name, selected).await {
                    Ok(response) => navigator.push(&Route::Corridor {
                        remix_id,
                        corridor_id: response.id,
                    }),
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    let has_selection = *selection_count > 0;

    html! {
        <div class="builder-region-map">
            <div id="import-map" style="width: 100%; height: 100vh;"></div>
            <div class="setup-card" style="position:absolute; top:16px; right:16px; z-index:10; width:320px;">
                <h1 class="setup-title">{ "Import from OpenStreetMap" }</h1>
                if let Some(err) = &*error {
                    <div class="alert alert--err">{ err }</div>
                }
                if !*zoom_ok {
                    <p>{ "Zoom in to load streets." }</p>
                } else {
                    <button class="btn btn-primary" style="width:100%;" onclick={on_load_streets}>{ "Load streets" }</button>
                }
                if has_selection {
                    <div style="margin-top:1rem;">
                        <label class="field-label" for="import-name">{ "Corridor name" }</label>
                        <input class="field" id="import-name" type="text" value={(*name_value).clone()} oninput={on_name_input} />
                        <button class="btn btn-primary" style="width:100%;margin-top:1rem;" onclick={on_import}>{ "Import" }</button>
                    </div>
                }
            </div>
        </div>
    }
}

/// Registered once via `map.on("click", ...)` and reused for every native
/// click event thereafter -- see the state-management comment at the top of
/// `ImportOsmPage` for why `ways_ref`/`selected_ref` are read live via
/// `RefCell` here rather than through a captured `UseStateHandle`.
fn handle_way_click(
    map: &Map,
    event: &JsValue,
    ways_ref: &Rc<RefCell<Option<Vec<api::OsmWayResponse>>>>,
    selected_ref: &Rc<RefCell<HashSet<i64>>>,
    selection_count: &UseStateHandle<usize>,
    name_value: &UseStateHandle<String>,
) {
    let Some(clicked_id) = extract_clicked_way_id(map, event) else {
        return;
    };

    let next_selected = {
        let mut selected = selected_ref.borrow_mut();
        if !selected.remove(&clicked_id) {
            selected.insert(clicked_id);
        }
        selected.clone()
    };

    restyle_ways_layer(map, &next_selected);
    let was_first_selection = next_selected.len() == 1;
    selection_count.set(next_selected.len());

    // Suggest a name the moment the first way gets selected -- an explicit,
    // simple starting point the analyst can freely edit afterward; further
    // selection changes don't fight the analyst's own typing. See the
    // design spec's WASM UI Layer section.
    if was_first_selection && let Some(ways) = &*ways_ref.borrow() {
        let names: HashSet<&str> = ways
            .iter()
            .filter(|w| next_selected.contains(&w.osm_way_id))
            .filter_map(|w| w.tags.get("name").map(|s| s.as_str()))
            .collect();
        if names.len() == 1 {
            let name = *names.iter().next().unwrap();
            name_value.set(name.to_string());
        }
    }
}

fn extract_clicked_way_id(map: &Map, event: &JsValue) -> Option<i64> {
    let point = js_sys::Reflect::get(event, &"point".into()).ok()?;
    let options = js_sys::Object::new();
    let layers = js_sys::Array::of1(&"osm-ways".into());
    js_sys::Reflect::set(&options, &"layers".into(), &layers).ok()?;

    let features = map.query_rendered_features(&point, &options);
    if features.length() == 0 {
        return None;
    }
    let feature = features.get(0);
    let properties = js_sys::Reflect::get(&feature, &"properties".into()).ok()?;
    js_sys::Reflect::get(&properties, &"osm_way_id".into())
        .ok()
        .and_then(|v| v.as_f64())
        .map(|v| v as i64)
}

fn render_ways_layer(map: &Map, ways: &[api::OsmWayResponse]) {
    let features: Vec<serde_json::Value> = ways
        .iter()
        .map(|way| {
            serde_json::json!({
                "type": "Feature",
                "properties": { "osm_way_id": way.osm_way_id },
                "geometry": {
                    "type": "LineString",
                    "coordinates": way.points.iter().map(|p| [p.lon, p.lat]).collect::<Vec<_>>(),
                },
            })
        })
        .collect();
    let collection = serde_json::json!({ "type": "FeatureCollection", "features": features });

    if let Ok(source) = to_js_value(&serde_json::json!({ "type": "geojson", "data": collection })) {
        map.add_source("osm-ways", &source);
    }
    if let Ok(layer) = to_js_value(&osm_ways_layer()) {
        map.add_layer(&layer);
    }
}

/// Re-centers the camera to fit all of `ways`' geometry. Without this, a
/// fetched way outside the map's *current* on-screen viewport (e.g. beyond
/// the visible height/width at the page's default center/zoom) would render
/// on the map but never be reachable by a click — the analyst would have to
/// manually pan/zoom to find it. `animate: false` (matching
/// `region_map.rs`'s `finish_map_setup`, which has the same requirement for
/// the same reason) snaps the camera immediately rather than easing into
/// place, so a click landing right after this call — including in the E2E
/// spec, which computes click pixels via `map.project()` — sees the final
/// camera position, not a mid-animation one.
fn fit_bounds_to_ways(map: &Map, ways: &[api::OsmWayResponse]) {
    let mut min_lat = f64::MAX;
    let mut min_lon = f64::MAX;
    let mut max_lat = f64::MIN;
    let mut max_lon = f64::MIN;
    for way in ways {
        for point in &way.points {
            min_lat = min_lat.min(point.lat);
            max_lat = max_lat.max(point.lat);
            min_lon = min_lon.min(point.lon);
            max_lon = max_lon.max(point.lon);
        }
    }
    if min_lat > max_lat || min_lon > max_lon {
        return;
    }
    let Ok(bounds) = to_js_value(&serde_json::json!([[min_lon, min_lat], [max_lon, max_lat]]))
    else {
        return;
    };
    let Ok(options) = to_js_value(&serde_json::json!({ "padding": 40, "animate": false })) else {
        return;
    };
    map.fit_bounds(&bounds, &options);
}

fn restyle_ways_layer(map: &Map, selected_ids: &HashSet<i64>) {
    let selected: Vec<i64> = selected_ids.iter().copied().collect();
    if let Ok(expression) = to_js_value(&serde_json::json!([
        "case",
        ["in", ["get", "osm_way_id"], ["literal", selected]],
        "#C8463A",
        "#1D4E89"
    ])) {
        map.set_paint_property("osm-ways", "line-color", &expression);
    }
}

fn osm_ways_layer() -> serde_json::Value {
    serde_json::json!({
        "id": "osm-ways",
        "type": "line",
        "source": "osm-ways",
        "paint": {
            "line-color": "#1D4E89",
            "line-width": 3
        }
    })
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
