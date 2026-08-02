mod api;
mod app;
mod pages;

fn main() {
    yew::Renderer::<app::App>::new().render();
}
