use yew::prelude::*;
use yew_router::prelude::*;

use crate::app::Route;

#[derive(Properties, PartialEq)]
pub struct IntersectionPageProps {
    pub remix_id: i64,
    pub cross_section_id: i64,
}

/// Placeholder — the intersection editor itself is a follow-up spec. See
/// the design spec's "Out of Scope".
#[component]
pub fn IntersectionPage(props: &IntersectionPageProps) -> Html {
    html! {
        <div class="builder-placeholder">
            <p>{ "Intersection editor coming soon." }</p>
            <Link<Route> to={Route::RegionMap { remix_id: props.remix_id }}>{ "Back to map" }</Link<Route>>
        </div>
    }
}
