//! Hand-written wasm-bindgen bindings for the subset of the MapLibre GL JS
//! API this app needs. Deliberately narrow rather than depending on an
//! unofficial third-party wrapper crate — see the design spec's
//! Architecture section.

use wasm_bindgen::prelude::*;

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = maplibregl)]
    #[derive(Clone)]
    pub type Map;

    #[wasm_bindgen(constructor, js_namespace = maplibregl)]
    pub fn new(options: &JsValue) -> Map;

    #[wasm_bindgen(method, js_name = addSource)]
    pub fn add_source(this: &Map, id: &str, source: &JsValue);

    #[wasm_bindgen(method, js_name = addLayer)]
    pub fn add_layer(this: &Map, layer: &JsValue);

    #[wasm_bindgen(method, js_name = fitBounds)]
    pub fn fit_bounds(this: &Map, bounds: &JsValue, options: &JsValue);

    /// Map-wide click listener (not layer-scoped) — see `handle_map_click`
    /// in `pages/region_map.rs` for why: a corridor's line passes directly
    /// through its own endpoints, so a layer-scoped listener per layer
    /// would fire twice for the same click and race on which navigation
    /// wins. A single listener plus `query_rendered_features` lets us pick
    /// one winner deliberately (intersections take priority).
    #[wasm_bindgen(method)]
    pub fn on(this: &Map, event_type: &str, callback: &Closure<dyn FnMut(JsValue)>);

    #[wasm_bindgen(method, js_name = queryRenderedFeatures)]
    pub fn query_rendered_features(this: &Map, point: &JsValue, options: &JsValue)
    -> js_sys::Array;
}
