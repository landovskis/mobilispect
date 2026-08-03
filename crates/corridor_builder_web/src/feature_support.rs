//! WebGL availability check for MapLibre GL JS graceful degradation.
//! Mirrors the feature-detection pattern already established for the
//! (separate, canvas-based) corridor segment editor's REQ-007 — see
//! e2e/tests/feature-detection.spec.ts — scoped here to what this shell's
//! map actually depends on: WebGL, not canvas-2D/Pointer Events.

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
