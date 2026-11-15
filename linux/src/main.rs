//! yrs. for the Linux desktop: your life, in dots.
//!
//!   yrs               open the window
//!   yrs --line        open straight to today's line
//!   yrs --background  no window: the tray, the daily wallpaper and the Monday note (started at login)

mod app;
mod chrome;
mod data;
mod life;
mod onboarding;
mod paint;
mod sheets;
mod store;
mod system;
mod ui;

fn main() -> eframe::Result {
    let args: Vec<String> = std::env::args().collect();
    if args.iter().any(|a| a == "--background") { system::background(); return Ok(()); }
    if let Some(i) = args.iter().position(|a| a == "--wallpaper") {
        // yrs --wallpaper out.png [style]: draw the wallpaper to a file, for checking by eye
        let s = store::load();
        let style = args.get(i + 2).and_then(|v| v.parse().ok()).unwrap_or(s.wall);
        let _ = paint::wallpaper(&s, 2560, 1440, style).save_png(args.get(i + 1).cloned().unwrap_or_else(|| "wallpaper.png".into()));
        return Ok(());
    }
    let line = args.iter().any(|a| a == "--line");

    let s = store::load();
    if std::env::var_os("YRS_STILL").is_none() {
        // the launcher entry gives the window its icon in the taskbar, from the very first launch;
        // it follows this copy if the AppImage is moved
        system::install_launcher();
        if s.birth.is_some() { system::ensure_background(&s); }
    }

    let icon = tiny_skia::Pixmap::decode_png(system::ICON_256).ok().map(|p| {
        let data = p.pixels().iter().flat_map(|px| { let c = px.demultiply(); [c.red(), c.green(), c.blue(), c.alpha()] }).collect();
        std::sync::Arc::new(egui::IconData { rgba: data, width: p.width(), height: p.height() })
    });
    let mut vp = egui::ViewportBuilder::default()
        .with_title("yrs.")
        .with_app_id("yrs")
        .with_inner_size([1280.0, 820.0])
        .with_min_inner_size([960.0, 640.0])
        // yrs. draws its own title bar and rounded corners
        .with_decorations(false)
        .with_transparent(true)
        .with_resizable(true);
    if let Some(i) = icon { vp = vp.with_icon(i); }
    if let Some((w, h)) = std::env::var("YRS_SIZE").ok().and_then(|v| v.split_once('x').map(|(a, b)| (a.parse().unwrap_or(1280.0), b.parse().unwrap_or(820.0)))) {
        vp = vp.with_inner_size([w, h]).with_position([0.0, 0.0]);
    }
    let opts = eframe::NativeOptions { viewport: vp, renderer: eframe::Renderer::Glow, vsync: true, ..Default::default() };
    eframe::run_native("yrs", opts, Box::new(move |cc| Ok(Box::new(app::App::new(cc, line)))))
}
