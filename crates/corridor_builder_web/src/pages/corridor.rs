use yew::prelude::*;
use yew_router::prelude::*;

use crate::app::Route;

#[derive(Properties, PartialEq)]
pub struct CorridorPageProps {
    pub remix_id: i64,
    pub corridor_id: i64,
}

/// Placeholder — the segment editor itself is a follow-up spec (the WASM
/// rework of REQ-001-007). See the design spec's "Out of Scope".
#[component]
pub fn CorridorPage(props: &CorridorPageProps) -> Html {
    html! {
        <div class="setup-wrap">
            <div class="setup-card">
                <p>{ "Corridor editor coming soon." }</p>
                <div style="margin-top:1rem;">
                    <Link<Route> classes="chip" to={Route::RegionMap { remix_id: props.remix_id }}>{ "Back to map" }</Link<Route>>
                </div>
            </div>
        </div>
    }
}
