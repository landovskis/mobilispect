//! WebGL availability check for MapLibre GL JS graceful degradation.
//! Scoped to what this shell's map actually depends on: WebGL — see
//! e2e/tests/builder-graceful-degradation.spec.ts for the corresponding
//! E2E coverage.

use wasm_bindgen::JsCast;
use web_sys::HtmlCanvasElement;

/// True if the browser can create a WebGL rendering context, which
/// MapLibre GL JS requires. Checked by creating a throwaway canvas rather
/// than touching the real map canvas, so it's safe to call before the map
/// exists.
pub fn webgl_is_supported() -> bool {
    let Some(window) = web_sys::window() else {
        return false;
    };
    let Some(document) = window.document() else {
        return false;
    };
    let Ok(element) = document.create_element("canvas") else {
        return false;
    };
    let Ok(canvas) = element.dyn_into::<HtmlCanvasElement>() else {
        return false;
    };
    canvas.get_context("webgl").ok().flatten().is_some()
}
