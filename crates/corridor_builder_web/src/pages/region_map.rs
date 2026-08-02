use yew::prelude::*;

#[derive(Properties, PartialEq)]
pub struct RegionMapPageProps {
    pub remix_id: i64,
}

/// Stub — replaced with the real MapLibre-backed map in Task 10 of
/// docs/superpowers/plans/2026-08-02-corridor-builder-wasm-shell.md.
#[component]
pub fn RegionMapPage(props: &RegionMapPageProps) -> Html {
    html! {
        <div class="builder-region-map">
            <p>{ format!("Region map for remix {} coming soon.", props.remix_id) }</p>
        </div>
    }
}
