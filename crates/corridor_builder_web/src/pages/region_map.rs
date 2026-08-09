use wasm_bindgen::prelude::*;
use yew::prelude::*;
use yew_router::prelude::*;

use crate::api;
use crate::app::Route;
use crate::feature_support::webgl_is_supported;
use crate::maplibre::Map;

#[derive(Properties, PartialEq)]
pub struct RegionMapPageProps {
    pub remix_id: i64,
}

#[component]
pub fn RegionMapPage(props: &RegionMapPageProps) -> Html {
    let remix_id = props.remix_id;
    let navigator = use_navigator().expect("BrowserRouter provides a Navigator");
    let error = use_state(|| None::<String>);
    let webgl_ok = use_state(webgl_is_supported);
    let show_add_menu = use_state(|| false);
    let on_open_add_menu = {
        let show_add_menu = show_add_menu.clone();
        Callback::from(move |_: MouseEvent| show_add_menu.set(true))
    };
    let on_choose_manual_trace = {
        let navigator = navigator.clone();
        Callback::from(move |_: MouseEvent| {
            navigator.push(&Route::ManualTrace { remix_id });
        })
    };

    {
        let error = error.clone();
        let webgl_ok = *webgl_ok;
        let navigator = navigator.clone();
        use_effect_with(remix_id, move |remix_id| {
            let remix_id = *remix_id;
            if webgl_ok {
                let error = error.clone();
                wasm_bindgen_futures::spawn_local(async move {
                    let mount_error = error.clone();
                    if let Err(e) = mount_map(remix_id, navigator, mount_error).await {
                        error.set(Some(e));
                    }
                });
            }
            || ()
        });
    }

    html! {
        <div class="builder-region-map">
            if !*webgl_ok {
                <div class="alert alert--warn">
                    <p>{ "Your browser doesn't support WebGL, which the region map requires." }</p>
                </div>
            } else if let Some(err) = &*error {
                <div class="alert alert--err">
                    <p>{ err }</p>
                    <Link<Route> classes="chip" to={Route::Landing}>{ "Back to builder" }</Link<Route>>
                </div>
            }
            <div id="map" style="width: 100%; height: 100vh;"></div>
            <div style="position:absolute; top:16px; right:16px; z-index:10;">
                if *show_add_menu {
                    <div class="setup-card" style="padding:1rem;">
                        <button class="btn btn-primary" style="display:block; width:100%; margin-bottom:0.5rem;" onclick={on_choose_manual_trace}>{ "Manual trace" }</button>
                        <button class="btn" style="display:block; width:100%;" disabled=true title="Coming soon">{ "Import from OSM" }</button>
                    </div>
                } else {
                    <button class="btn btn-primary" onclick={on_open_add_menu}>{ "Add corridor" }</button>
                }
            </div>
        </div>
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

fn corridor_line_layer() -> serde_json::Value {
    serde_json::json!({
        "id": "corridor-lines",
        "type": "line",
        "source": "corridors",
        "filter": ["==", ["get", "feature_type"], "corridor"],
        "paint": {
            "line-color": ["case", ["get", "highlighted"], "#C8463A", "#1D4E89"],
            "line-width": ["case", ["get", "highlighted"], 4, 2]
        }
    })
}

fn corridor_intersection_layer() -> serde_json::Value {
    serde_json::json!({
        "id": "corridor-intersections",
        "type": "circle",
        "source": "corridors",
        "filter": ["==", ["get", "feature_type"], "intersection"],
        "paint": {
            "circle-radius": 6,
            "circle-color": "#3D3935"
        }
    })
}

async fn mount_map(
    remix_id: i64,
    navigator: Navigator,
    error: UseStateHandle<Option<String>>,
) -> Result<(), String> {
    let remix = api::get_remix(remix_id).await?;
    let corridors = api::get_remix_corridors(remix_id).await?;

    let options = to_js_value(&serde_json::json!({
        "container": "map",
        "style": osm_raster_style(),
    }))?;
    let map = Map::new(&options);

    let bbox = remix.region.bbox;
    let load_map = map.clone();
    let load_navigator = navigator.clone();
    let onload = Closure::wrap(Box::new(move |_event: JsValue| {
        if let Err(e) = finish_map_setup(&load_map, &bbox, &corridors, &load_navigator, remix_id) {
            error.set(Some(e));
        }
    }) as Box<dyn FnMut(JsValue)>);
    map.on("load", &onload);
    onload.forget();

    Ok(())
}

/// Runs once the map's style has finished loading — MapLibre throws if
/// addSource/addLayer are called before this, so this must not run any
/// earlier than the "load" event.
fn finish_map_setup(
    map: &Map,
    bbox: &api::BoundingBox,
    corridors: &serde_json::Value,
    navigator: &Navigator,
    remix_id: i64,
) -> Result<(), String> {
    let bounds = to_js_value(&serde_json::json!([
        [bbox.min_lon, bbox.min_lat],
        [bbox.max_lon, bbox.max_lat]
    ]))?;
    // `animate: false` snaps the camera to the bounds immediately rather
    // than MapLibre's default eased/animated transition. Without this, the
    // map is exposed via `window.__corridorBuilderMap` (and the click
    // listener registered) while the camera is still mid-animation; a
    // pixel computed via `map.project(...)` right after exposure goes
    // stale by the time a click physically lands, since the camera keeps
    // moving in between — `queryRenderedFeatures` then finds nothing at
    // that now-wrong pixel. Confirmed via a standalone repro: the camera
    // only reaches the correct center/zoom immediately, with zero drift,
    // once `animate: false` is set.
    map.fit_bounds(
        &bounds,
        &to_js_value(&serde_json::json!({ "animate": false }))?,
    );

    let source = to_js_value(&serde_json::json!({
        "type": "geojson",
        "data": corridors,
    }))?;
    map.add_source("corridors", &source);
    map.add_layer(&to_js_value(&corridor_line_layer())?);
    map.add_layer(&to_js_value(&corridor_intersection_layer())?);

    let click_map = map.clone();
    let click_navigator = navigator.clone();
    let onclick = Closure::wrap(Box::new(move |event: JsValue| {
        handle_map_click(&click_map, &event, &click_navigator, remix_id);
    }) as Box<dyn FnMut(JsValue)>);
    map.on("click", &onclick);
    onclick.forget();

    expose_map_for_e2e_tests(map);

    Ok(())
}

/// A single map-wide click handler (not two layer-scoped ones — see
/// `maplibre.rs`'s `on` binding doc comment for why): checks
/// `corridor-intersections` first, then `corridor-lines`, and acts on
/// whichever is hit first at the click point. Intersections must win at a
/// corridor's endpoints, since the line passes directly through them too.
fn handle_map_click(map: &Map, event: &JsValue, navigator: &Navigator, remix_id: i64) {
    let Ok(point) = js_sys::Reflect::get(event, &"point".into()) else {
        return;
    };

    for layer_id in ["corridor-intersections", "corridor-lines"] {
        let options = js_sys::Object::new();
        let layers = js_sys::Array::of1(&layer_id.into());
        js_sys::Reflect::set(&options, &"layers".into(), &layers).unwrap();

        let features = map.query_rendered_features(&point, &options);
        if features.length() == 0 {
            continue;
        }

        let feature = features.get(0);
        let Ok(properties) = js_sys::Reflect::get(&feature, &"properties".into()) else {
            continue;
        };

        match layer_id {
            "corridor-intersections" => {
                if let Some(cross_section_id) =
                    js_sys::Reflect::get(&properties, &"cross_section_id".into())
                        .ok()
                        .and_then(|v| v.as_f64())
                {
                    navigator.push(&Route::Intersection {
                        remix_id,
                        cross_section_id: cross_section_id as i64,
                    });
                }
            }
            "corridor-lines" => {
                if let Some(corridor_id) = js_sys::Reflect::get(&properties, &"corridor_id".into())
                    .ok()
                    .and_then(|v| v.as_f64())
                {
                    navigator.push(&Route::Corridor {
                        remix_id,
                        corridor_id: corridor_id as i64,
                    });
                }
            }
            _ => {}
        }
        return;
    }
}

/// Stashes the map instance on `window.__corridorBuilderMap` so Playwright
/// E2E tests can compute exact click pixel coordinates via `map.project()`
/// instead of guessing — see `e2e/tests/builder-click-routing.spec.ts`.
fn expose_map_for_e2e_tests(map: &Map) {
    if let Some(window) = web_sys::window() {
        let _ = js_sys::Reflect::set(&window, &"__corridorBuilderMap".into(), map);
    }
}
