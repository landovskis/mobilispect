use web_sys::{HtmlInputElement, HtmlSelectElement};
use yew::prelude::*;
use yew_router::prelude::*;

use crate::api;
use crate::app::Route;

#[derive(Clone, PartialEq)]
enum Mode {
    Choose,
    Create,
    Open,
}

#[component]
pub fn LandingPage() -> Html {
    let mode = use_state(|| Mode::Choose);
    let regions = use_state(Vec::<api::Region>::new);
    let load_error = use_state(|| None::<String>);
    let name_input = use_node_ref();
    let region_select = use_node_ref();
    let create_error = use_state(|| None::<String>);
    let open_region_select = use_node_ref();
    let remixes = use_state(Vec::<api::RemixSummary>::new);
    let remixes_error = use_state(|| None::<String>);
    let navigator = use_navigator().expect("BrowserRouter provides a Navigator");

    {
        let regions = regions.clone();
        let load_error = load_error.clone();
        use_effect_with((), move |_| {
            wasm_bindgen_futures::spawn_local(async move {
                match api::list_regions().await {
                    Ok(fetched) => regions.set(fetched),
                    Err(err) => load_error.set(Some(err)),
                }
            });
            || ()
        });
    }

    let on_choose_create = {
        let mode = mode.clone();
        Callback::from(move |_: MouseEvent| mode.set(Mode::Create))
    };
    let on_choose_open = {
        let mode = mode.clone();
        Callback::from(move |_: MouseEvent| mode.set(Mode::Open))
    };

    let on_submit_create = {
        let name_input = name_input.clone();
        let region_select = region_select.clone();
        let create_error = create_error.clone();
        let navigator = navigator.clone();
        Callback::from(move |_: MouseEvent| {
            let name_input = name_input.clone();
            let region_select = region_select.clone();
            let create_error = create_error.clone();
            let navigator = navigator.clone();
            wasm_bindgen_futures::spawn_local(async move {
                let name = name_input
                    .cast::<HtmlInputElement>()
                    .map(|el| el.value())
                    .unwrap_or_default();
                let region_id = region_select
                    .cast::<HtmlSelectElement>()
                    .and_then(|el| el.value().parse::<i64>().ok());

                if name.trim().is_empty() {
                    create_error.set(Some("name must not be blank".to_string()));
                    return;
                }
                let Some(region_id) = region_id else {
                    create_error.set(Some("select a metro region".to_string()));
                    return;
                };

                match api::create_remix(name, region_id).await {
                    Ok(response) => navigator.push(&Route::RegionMap {
                        remix_id: response.id,
                    }),
                    Err(err) => create_error.set(Some(err)),
                }
            });
        })
    };

    let on_pick_open_region = {
        let open_region_select = open_region_select.clone();
        let remixes = remixes.clone();
        let remixes_error = remixes_error.clone();
        Callback::from(move |_: Event| {
            let open_region_select = open_region_select.clone();
            let remixes = remixes.clone();
            let remixes_error = remixes_error.clone();
            let region_id = open_region_select
                .cast::<HtmlSelectElement>()
                .and_then(|el| el.value().parse::<i64>().ok());
            if let Some(region_id) = region_id {
                wasm_bindgen_futures::spawn_local(async move {
                    match api::list_region_remixes(region_id).await {
                        Ok(fetched) => remixes.set(fetched),
                        Err(err) => remixes_error.set(Some(err)),
                    }
                });
            }
        })
    };

    html! {
        <div class="builder-landing">
            <h1>{ "Corridor Builder" }</h1>
            if let Some(err) = &*load_error {
                <p class="error">{ err }</p>
            }
            {
                match &*mode {
                    Mode::Choose => html! {
                        <div>
                            <button onclick={on_choose_create}>{ "Create remix" }</button>
                            <button onclick={on_choose_open}>{ "Open remix" }</button>
                        </div>
                    },
                    Mode::Create => html! {
                        <div>
                            <label for="create-region">{ "Metro region" }</label>
                            <select id="create-region" ref={region_select.clone()}>
                                { for regions.iter().map(|r| html! {
                                    <option value={r.id.to_string()}>{ &r.name }</option>
                                }) }
                            </select>
                            <label for="create-name">{ "Remix name" }</label>
                            <input id="create-name" type="text" ref={name_input.clone()} />
                            <button onclick={on_submit_create}>{ "Create" }</button>
                            if let Some(err) = &*create_error {
                                <p class="error">{ err }</p>
                            }
                        </div>
                    },
                    Mode::Open => html! {
                        <div>
                            <label for="open-region">{ "Metro region" }</label>
                            <select id="open-region" ref={open_region_select.clone()} onchange={on_pick_open_region}>
                                <option value="" selected=true disabled=true>{ "Select a region" }</option>
                                { for regions.iter().map(|r| html! {
                                    <option value={r.id.to_string()}>{ &r.name }</option>
                                }) }
                            </select>
                            if let Some(err) = &*remixes_error {
                                <p class="error">{ err }</p>
                            }
                            <ul>
                                { for remixes.iter().map(|r| {
                                    let remix_id = r.id;
                                    html! {
                                        <li>
                                            <Link<Route> to={Route::RegionMap { remix_id }}>{ &r.name }</Link<Route>>
                                        </li>
                                    }
                                }) }
                            </ul>
                        </div>
                    },
                }
            }
        </div>
    }
}
