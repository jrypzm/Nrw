//! yrs. music for Linux: your music, offline.
//!
//!   yrs-music             open the window
//!   yrs-music FILE...     open and play these songs

mod app;
mod audio;
mod covers;
mod decode;
mod icons;
mod layout;
mod library;
mod lyrics;
mod mpris;
mod now;
mod pages;
mod player;
mod store;
mod theme;

fn icon() -> Option<egui::IconData> {
    let img = image::load_from_memory(include_bytes!("../assets/icons/yrs-music-256.png")).ok()?.to_rgba8();
    Some(egui::IconData { width: img.width(), height: img.height(), rgba: img.into_raw() })
}

fn main() -> eframe::Result<()> {
    let args: Vec<std::path::PathBuf> = std::env::args_os().skip(1).map(std::path::PathBuf::from).filter(|p| p.exists()).collect();
    let st = store::Store::load();
    let size = st.size.unwrap_or([1280.0, 800.0]);
    // any size is welcome: the layout reshapes itself down to a strip or just the cover
    let mut vp = egui::ViewportBuilder::default()
        .with_title("yrs. music")
        .with_app_id("yrs-music")
        .with_decorations(st.system_frame)
        .with_resizable(true)
        .with_inner_size([size[0].max(220.0), size[1].max(96.0)])
        .with_min_inner_size([200.0, 90.0])
        .with_drag_and_drop(true);
    if let Some(i) = icon() { vp = vp.with_icon(i); }
    let opts = eframe::NativeOptions { viewport: vp, vsync: true, multisampling: 0, ..Default::default() };
    eframe::run_native("yrs. music", opts, Box::new(move |cc| Ok(Box::new(app::App::new(cc, args)))))
}
