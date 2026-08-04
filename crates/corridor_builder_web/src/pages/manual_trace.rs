use wasm_bindgen::prelude::*;
use yew::prelude::*;
use yew_router::prelude::*;

use crate::api;
use crate::app::Route;
use crate::maplibre::Map;

#[derive(Properties, PartialEq)]
pub struct ManualTracePageProps {
    pub remix_id: i64,
}

#[component]
pub fn ManualTracePage(props: &ManualTracePageProps) -> Html {
    let remix_id = props.remix_id;
    let navigator = use_navigator().expect("BrowserRouter provides a Navigator");
    // Split into two states deliberately: `corridor_id` only ever transitions
    // None -> Some once (when tracing starts), while `point_count` changes on
    // every click. Keeping them separate means the map-mounting effect below
    // can depend on `corridor_id` alone — if a single combined enum carried
    // both, using it as the effect's dependency would remount a brand new
    // MapLibre map (and stack a duplicate click listener) on every point
    // clicked, since the whole enum value changes each time.
    let corridor_id = use_state(|| None::<i64>);
    let point_count = use_state(|| 0usize);
    let error = use_state(|| None::<String>);
    let name_input = use_node_ref();

    let on_start_tracing = {
        let name_input = name_input.clone();
        let corridor_id = corridor_id.clone();
        let error = error.clone();
        Callback::from(move |_: MouseEvent| {
            let name_input = name_input.clone();
            let corridor_id = corridor_id.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                let name = name_input
                    .cast::<web_sys::HtmlInputElement>()
                    .map(|el| el.value())
                    .unwrap_or_default();
                if name.trim().is_empty() {
                    error.set(Some("name must not be blank".to_string()));
                    return;
                }
                match api::start_manual_corridor(remix_id, name).await {
                    Ok(response) => corridor_id.set(Some(response.id)),
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    // Mounts the trace map exactly once, the moment `corridor_id` goes from
    // `None` to `Some(id)`. See the field comment above for why this depends
    // on `corridor_id` specifically, not a combined state enum.
    {
        let point_count = point_count.clone();
        let error = error.clone();
        use_effect_with(*corridor_id, move |corridor_id: &Option<i64>| {
            if let Some(id) = *corridor_id {
                mount_trace_map(id, point_count, error);
            }
            || ()
        });
    }

    let on_finish = {
        let corridor_id = corridor_id.clone();
        let error = error.clone();
        let navigator = navigator.clone();
        Callback::from(move |_: MouseEvent| {
            let Some(id) = *corridor_id else {
                return;
            };
            let error = error.clone();
            let navigator = navigator.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::finish_manual_corridor(id).await {
                    Ok(_) => navigator.push(&Route::Corridor {
                        remix_id,
                        corridor_id: id,
                    }),
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    html! {
        <div class="setup-wrap">
            <div class="setup-card">
                <h1 class="setup-title">{ "Trace a corridor" }</h1>
                if let Some(err) = &*error {
                    <div class="alert alert--err">{ err }</div>
                }
                if corridor_id.is_none() {
                    <div>
                        <label class="field-label" for="trace-name">{ "Corridor name" }</label>
                        <input class="field" id="trace-name" type="text" ref={name_input.clone()} />
                        <button class="btn btn-primary" style="width:100%;margin-top:1rem;" onclick={on_start_tracing}>{ "Start tracing" }</button>
                    </div>
                } else {
                    <div>
                        <p>{ format!("Click the map to place points ({} placed so far, minimum 2).", *point_count) }</p>
                        <button class="btn btn-primary" style="width:100%;margin-top:1rem;" onclick={on_finish}>{ "Finish trace" }</button>
                    </div>
                }
            </div>
            <div id="trace-map" style="width: 100%; height: 100vh; margin-top:1rem;"></div>
        </div>
    }
}

fn mount_trace_map(
    corridor_id: i64,
    point_count: UseStateHandle<usize>,
    error: UseStateHandle<Option<String>>,
) {
    let options = to_js_value(&serde_json::json!({
        "container": "trace-map",
        "style": osm_raster_style(),
        "center": [-73.6, 45.5],
        "zoom": 13,
    }));
    let Ok(options) = options else {
        error.set(Some("failed to build map options".to_string()));
        return;
    };
    let map = Map::new(&options);

    let click_point_count = point_count.clone();
    let click_error = error.clone();
    let onclick = Closure::wrap(Box::new(move |event: JsValue| {
        let Ok(lng_lat) = js_sys::Reflect::get(&event, &"lngLat".into()) else {
            return;
        };
        let Some(lon) = js_sys::Reflect::get(&lng_lat, &"lng".into()).ok().and_then(|v| v.as_f64()) else {
            return;
        };
        let Some(lat) = js_sys::Reflect::get(&lng_lat, &"lat".into()).ok().and_then(|v| v.as_f64()) else {
            return;
        };
        let click_point_count = click_point_count.clone();
        let click_error = click_error.clone();
        wasm_bindgen_futures::spawn_local(async move {
            match api::add_manual_point(corridor_id, lat, lon).await {
                Ok(_) => click_point_count.set(*click_point_count + 1),
                Err(e) => click_error.set(Some(e)),
            }
        });
    }) as Box<dyn FnMut(JsValue)>);
    map.on("click", &onclick);
    onclick.forget();
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
