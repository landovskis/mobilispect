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

    /// Current visible geographic bounds — used to build the Overpass bbox
    /// query when the analyst clicks "Load streets" (see
    /// `pages/import_osm.rs`).
    #[wasm_bindgen(method, js_name = getBounds)]
    pub fn get_bounds(this: &Map) -> LngLatBounds;

    /// Current zoom level — used to gate the "Load streets" button.
    #[wasm_bindgen(method, js_name = getZoom)]
    pub fn get_zoom(this: &Map) -> f64;

    /// Updates one paint property of an already-added layer — used to
    /// re-color selected OSM ways as the analyst clicks them (see
    /// `pages/import_osm.rs`), without re-adding the layer.
    #[wasm_bindgen(method, js_name = setPaintProperty)]
    pub fn set_paint_property(this: &Map, layer_id: &str, name: &str, value: &JsValue);

    #[wasm_bindgen(js_namespace = maplibregl)]
    pub type LngLatBounds;

    #[wasm_bindgen(method, js_name = getWest)]
    pub fn get_west(this: &LngLatBounds) -> f64;

    #[wasm_bindgen(method, js_name = getSouth)]
    pub fn get_south(this: &LngLatBounds) -> f64;

    #[wasm_bindgen(method, js_name = getEast)]
    pub fn get_east(this: &LngLatBounds) -> f64;

    #[wasm_bindgen(method, js_name = getNorth)]
    pub fn get_north(this: &LngLatBounds) -> f64;
}

/// Stashes `map` on `window.__corridorBuilderMap` so Playwright E2E tests can
/// compute exact click pixel coordinates via `map.project(...)` instead of
/// guessing. Shared across every page that mounts its own MapLibre map
/// instance (`pages/region_map.rs`, `pages/manual_trace.rs`,
/// `pages/import_osm.rs`).
pub fn expose_map_for_e2e_tests(map: &Map) {
    if let Some(window) = web_sys::window() {
        let _ = js_sys::Reflect::set(&window, &"__corridorBuilderMap".into(), map);
    }
}
