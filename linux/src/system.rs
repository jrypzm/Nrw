//! Living on the desktop: the app launcher entry, starting at login, notifications, the wallpaper and lock screen
//! on KDE Plasma, and the tray. Everything happens on this computer.

use crate::life;
use crate::store::{self, Settings};
use std::path::PathBuf;
use std::process::Command;

pub const ICON_256: &[u8] = include_bytes!("../assets/icons/yrs-256.png");
pub const ICON_64: &[u8] = include_bytes!("../assets/icons/yrs-64.png");
const ICONS: &[(u32, &[u8])] = &[
    (16, include_bytes!("../assets/icons/yrs-16.png")), (22, include_bytes!("../assets/icons/yrs-22.png")), (24, include_bytes!("../assets/icons/yrs-24.png")),
    (32, include_bytes!("../assets/icons/yrs-32.png")), (48, include_bytes!("../assets/icons/yrs-48.png")), (64, include_bytes!("../assets/icons/yrs-64.png")),
    (128, include_bytes!("../assets/icons/yrs-128.png")), (256, include_bytes!("../assets/icons/yrs-256.png")), (512, include_bytes!("../assets/icons/yrs-512.png")),
];

fn home() -> PathBuf { PathBuf::from(std::env::var_os("HOME").unwrap_or_default()) }
fn data_home() -> PathBuf { std::env::var_os("XDG_DATA_HOME").map(PathBuf::from).filter(|p| p.is_absolute()).unwrap_or_else(|| home().join(".local/share")) }
fn config_home() -> PathBuf { std::env::var_os("XDG_CONFIG_HOME").map(PathBuf::from).filter(|p| p.is_absolute()).unwrap_or_else(|| home().join(".config")) }

/// The file to start: the AppImage itself when running from one, else this program.
pub fn exe() -> PathBuf { std::env::var_os("APPIMAGE").map(PathBuf::from).or_else(|| std::env::current_exe().ok()).unwrap_or_else(|| "yrs".into()) }

fn quote(p: &std::path::Path) -> String { format!("\"{}\"", p.display().to_string().replace('"', "\\\"")) }

fn desktop_entry(args: &str, extra: &str) -> String {
    format!("[Desktop Entry]\nType=Application\nName=yrs.\nGenericName=Life in dots\nComment=Your life, in dots. Every year you've lived, and every year still ahead.\nExec={}{args}\nIcon=yrs\nTerminal=false\nCategories=Utility;\nKeywords=life;time;years;calendar;countdown;\nStartupWMClass=yrs\nStartupNotify=true\n{extra}",
        quote(&exe()))
}

/// Puts yrs. in the app launcher with its icon, so it shows properly in the taskbar and can be pinned.
/// Run on every start, so the entry follows the AppImage if it is moved.
pub fn install_launcher() {
    let apps = data_home().join("applications");
    let _ = std::fs::create_dir_all(&apps);
    let entry = desktop_entry(" %U", "Actions=line;\n\n[Desktop Action line]\nName=Write today's line\nExec=") + &format!("{} --line\n", quote(&exe()));
    let path = apps.join("yrs.desktop");
    let changed = std::fs::read_to_string(&path).map(|s| s != entry).unwrap_or(true);
    for (n, bytes) in ICONS {
        let d = data_home().join(format!("icons/hicolor/{n}x{n}/apps"));
        let _ = std::fs::create_dir_all(&d);
        let f = d.join("yrs.png");
        if std::fs::metadata(&f).map(|m| m.len() as usize != bytes.len()).unwrap_or(true) { let _ = std::fs::write(f, bytes); }
    }
    if changed {
        let _ = std::fs::write(&path, entry);
        // ask Plasma to notice the new entry and icon
        let _ = Command::new("kbuildsycoca6").arg("--noincremental").stdout(std::process::Stdio::null()).stderr(std::process::Stdio::null()).spawn();
    }
}

pub fn launcher_installed() -> bool { data_home().join("applications/yrs.desktop").exists() }
pub fn remove_launcher() {
    let _ = std::fs::remove_file(data_home().join("applications/yrs.desktop"));
    for (n, _) in ICONS { let _ = std::fs::remove_file(data_home().join(format!("icons/hicolor/{n}x{n}/apps/yrs.png"))); }
}

/// Start quietly in the tray at login.
pub fn set_autostart(on: bool) {
    let d = config_home().join("autostart");
    let f = d.join("yrs.desktop");
    if on { let _ = std::fs::create_dir_all(&d); let _ = std::fs::write(f, desktop_entry(" --background", "X-KDE-autostart-after=panel\n")); } else { let _ = std::fs::remove_file(f); }
}

pub fn notify(title: &str, body: &str) {
    let _ = notify_rust::Notification::new().appname("yrs.").summary(title).body(body).icon("yrs").show();
}

// ------------------------------------------------------------------ wallpaper and lock screen

pub const WALLS: [&str; 3] = ["Grid", "Number", "Daylight"];

/// Draws the wallpaper, saves it, and hands it to Plasma for the desktop, the lock screen, or both.
pub fn apply_wallpaper(s: &Settings, desktop: bool, lock: bool, size: (u32, u32)) -> Result<(), String> {
    let png = crate::paint::wallpaper(s, size.0, size.1, s.wall).encode_png().map_err(|e| e.to_string())?;
    let d = store::dir();
    let _ = std::fs::create_dir_all(&d);
    // a new name each time, so Plasma sees a change and repaints
    for old in std::fs::read_dir(&d).into_iter().flatten().flatten() {
        if old.file_name().to_string_lossy().starts_with("wallpaper-") { let _ = std::fs::remove_file(old.path()); }
    }
    let f = d.join(format!("wallpaper-{}.png", life::now().format("%Y%m%d%H%M%S")));
    std::fs::write(&f, png).map_err(|e| e.to_string())?;
    if desktop {
        let ok = Command::new("plasma-apply-wallpaperimage").arg(&f).output().map(|o| o.status.success()).unwrap_or(false);
        if !ok { return Err("Couldn't reach Plasma to set the wallpaper".into()); }
    }
    if lock {
        let url = format!("file://{}", f.display());
        for key in ["Image", "PreviewImage"] {
            let _ = Command::new("kwriteconfig6").args(["--file", "kscreenlockerrc", "--group", "Greeter", "--group", "Wallpaper", "--group", "org.kde.image", "--group", "General", "--key", key, &url]).output();
        }
        let _ = Command::new("kwriteconfig6").args(["--file", "kscreenlockerrc", "--group", "Greeter", "--key", "WallpaperPlugin", "org.kde.image"]).output();
    }
    let mut st = store::load();
    st.wall_key = wall_key(s);
    store::save(&st);
    Ok(())
}

/// Changes only when something on the wallpaper would.
pub fn wall_key(s: &Settings) -> String {
    let hour = if s.wall == 2 { life::now().format("%H").to_string() } else { String::new() };
    format!("{}|{hour}|{}|{}|{:?}|{}", life::today(), s.span, s.wall, s.birth, s.lived)
}

// ------------------------------------------------------------------ the background: tray, daily wallpaper, notes

struct Tray { tip: String, sub: String, icon: Vec<ksni::Icon> }

impl ksni::Tray for Tray {
    fn id(&self) -> String { "yrs".into() }
    fn title(&self) -> String { "yrs.".into() }
    fn icon_name(&self) -> String { "yrs".into() }
    fn icon_pixmap(&self) -> Vec<ksni::Icon> { self.icon.clone() }
    fn category(&self) -> ksni::Category { ksni::Category::ApplicationStatus }
    fn tool_tip(&self) -> ksni::ToolTip { ksni::ToolTip { title: self.tip.clone(), description: self.sub.clone(), icon_name: "yrs".into(), icon_pixmap: vec![] } }
    fn activate(&mut self, _x: i32, _y: i32) { open_window(None); }
    fn menu(&self) -> Vec<ksni::MenuItem<Self>> {
        use ksni::menu::StandardItem;
        vec![
            StandardItem { label: self.tip.clone(), enabled: false, ..Default::default() }.into(),
            ksni::MenuItem::Separator,
            StandardItem { label: "Open yrs.".into(), activate: Box::new(|_: &mut Self| open_window(None)), ..Default::default() }.into(),
            StandardItem { label: "Write today's line".into(), activate: Box::new(|_: &mut Self| open_window(Some("--line"))), ..Default::default() }.into(),
            ksni::MenuItem::Separator,
            StandardItem { label: "Quit".into(), activate: Box::new(|_: &mut Self| std::process::exit(0)), ..Default::default() }.into(),
        ]
    }
}

fn open_window(arg: Option<&str>) {
    let mut c = Command::new(exe());
    if let Some(a) = arg { c.arg(a); }
    let _ = c.spawn();
}

fn tray_icon() -> Vec<ksni::Icon> {
    [ICON_64].iter().filter_map(|b| tiny_skia::Pixmap::decode_png(b).ok()).map(|p| {
        let mut data = Vec::with_capacity(p.data().len());
        for px in p.pixels() { let c = px.demultiply(); data.extend_from_slice(&[c.alpha(), c.red(), c.green(), c.blue()]); }
        ksni::Icon { width: p.width() as i32, height: p.height() as i32, data }
    }).collect()
}

fn tip(s: &Settings) -> (String, String) {
    match s.birth() {
        None => ("yrs.".into(), "Open yrs. to begin".into()),
        Some(b) => {
            let left = life::years_left(s);
            (format!("{} years left", life::whole(left)), format!("Day {} of your life · {} days to go", life::group(life::day_of_life(b, life::today())), life::whole(life::days_left_exact(s))))
        }
    }
}

/// Runs without a window: the tray, the daily wallpaper and lock screen, and the Monday note. Started at login.
pub fn background() {
    // one background at a time
    let lock = store::dir().join("background.pid");
    if let Ok(pid) = std::fs::read_to_string(&lock) {
        if PathBuf::from(format!("/proc/{}", pid.trim())).exists() && pid.trim() != std::process::id().to_string() { return; }
    }
    let _ = std::fs::create_dir_all(store::dir());
    let _ = std::fs::write(&lock, std::process::id().to_string());

    let s = store::load();
    let (t, d) = tip(&s);
    use ksni::blocking::TrayMethods;
    let handle = if s.tray { Tray { tip: t, sub: d, icon: tray_icon() }.spawn().ok() } else { None };
    let mut last_note = String::new();
    loop {
        let s = store::load();
        if let Some(h) = &handle { let (t, d) = tip(&s); h.update(|tr| { tr.tip = t; tr.sub = d; }); }
        if s.birth().is_some() && (s.auto_wall || s.auto_lock) && s.wall_key != wall_key(&s) {
            let _ = apply_wallpaper(&s, s.auto_wall, s.auto_lock, s.screen);
        }
        // Monday at 9, once
        let n = life::now();
        use chrono::{Datelike, Timelike};
        let key = n.format("%Y-%m-%d").to_string();
        if s.monday && n.weekday() == chrono::Weekday::Mon && n.hour() >= 9 && last_note != key && s.last_note != key {
            if let Some(b) = s.birth() {
                let week = (life::today() - b).num_days() / 7 + 1;
                notify("A new week", &format!("You're in week {} of your life. {} weeks left. Make this one count.", life::group(week), life::whole(life::days_left_exact(&s) / 7.0)));
                let mut st = store::load(); st.last_note = key.clone(); store::save(&st);
            }
            last_note = key;
        }
        if !s.tray && !s.auto_wall && !s.auto_lock && !s.monday { let _ = std::fs::remove_file(&lock); return; }
        std::thread::sleep(std::time::Duration::from_secs(60));
    }
}

/// Starts the background helper if anything needs it and it isn't running.
pub fn ensure_background(s: &Settings) {
    if !(s.tray || s.auto_wall || s.auto_lock || s.monday) { return; }
    let lock = store::dir().join("background.pid");
    let running = std::fs::read_to_string(&lock).map(|p| PathBuf::from(format!("/proc/{}", p.trim())).exists()).unwrap_or(false);
    if !running { let _ = Command::new(exe()).arg("--background").spawn(); }
}

pub fn stop_background() {
    if let Ok(pid) = std::fs::read_to_string(store::dir().join("background.pid")) {
        let _ = Command::new("kill").arg(pid.trim()).output();
        let _ = std::fs::remove_file(store::dir().join("background.pid"));
    }
}
