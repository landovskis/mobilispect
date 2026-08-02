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
        <div class="builder-placeholder">
            <p>{ "Corridor editor coming soon." }</p>
            <Link<Route> to={Route::RegionMap { remix_id: props.remix_id }}>{ "Back to map" }</Link<Route>>
        </div>
    }
}
