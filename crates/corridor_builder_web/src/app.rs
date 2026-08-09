use yew::prelude::*;
use yew_router::prelude::*;

use crate::pages::corridor::CorridorPage;
use crate::pages::intersection::IntersectionPage;
use crate::pages::landing::LandingPage;
use crate::pages::manual_trace::ManualTracePage;
use crate::pages::region_map::RegionMapPage;

#[derive(Clone, Routable, PartialEq, Debug)]
pub enum Route {
    #[at("/builder")]
    Landing,
    #[at("/builder/remix/:remix_id")]
    RegionMap { remix_id: i64 },
    #[at("/builder/remix/:remix_id/trace")]
    ManualTrace { remix_id: i64 },
    #[at("/builder/remix/:remix_id/intersection/:cross_section_id")]
    Intersection {
        remix_id: i64,
        cross_section_id: i64,
    },
    #[at("/builder/remix/:remix_id/corridor/:corridor_id")]
    Corridor { remix_id: i64, corridor_id: i64 },
    #[not_found]
    #[at("/builder/404")]
    NotFound,
}

fn switch(route: Route) -> Html {
    match route {
        Route::Landing => html! { <LandingPage /> },
        Route::RegionMap { remix_id } => html! { <RegionMapPage {remix_id} /> },
        Route::ManualTrace { remix_id } => html! { <ManualTracePage {remix_id} /> },
        Route::Intersection {
            remix_id,
            cross_section_id,
        } => html! { <IntersectionPage {remix_id} {cross_section_id} /> },
        Route::Corridor {
            remix_id,
            corridor_id,
        } => html! { <CorridorPage {remix_id} {corridor_id} /> },
        Route::NotFound => html! { <p>{ "Not found" }</p> },
    }
}

#[component]
pub fn App() -> Html {
    html! {
        <BrowserRouter>
            <Switch<Route> render={switch} />
        </BrowserRouter>
    }
}
