mod api;
mod app;
mod feature_support;
mod maplibre;
mod pages;

fn main() {
    yew::Renderer::<app::App>::new().render();
}
