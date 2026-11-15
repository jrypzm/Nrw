//! Everything you enter, kept in ~/.local/share/yrs on this computer. Nothing is ever sent anywhere.

use chrono::NaiveDate;
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::path::PathBuf;

#[derive(Serialize, Deserialize, Clone, PartialEq, Debug)]
pub struct Person { pub name: String, pub born: i32, pub per: u32 }
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug)]
pub struct Event { pub name: String, pub date: String }
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug)]
pub struct Letter { pub age: i32, pub text: String, pub written: String, #[serde(default)] pub opened: bool }

/// Answers to Estimate my lifespan.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
pub struct Est { pub country: String, pub sex: String, pub picks: BTreeMap<String, usize> }

#[derive(Serialize, Deserialize, Clone, PartialEq, Debug)]
#[serde(default)]
pub struct Settings {
    pub birth: Option<String>,
    pub span: u32,
    pub lived: bool,
    pub pct: bool,
    /// 0 Classic, 1 Large, 2 Live
    pub style: u8,
    pub sun: bool,
    pub ripple: bool,
    pub unit: usize,
    pub page: usize,
    pub zoom: usize,
    pub names: BTreeMap<i32, String>,
    pub people: Vec<Person>,
    pub events: Vec<Event>,
    pub letters: Vec<Letter>,
    pub reviewed_age: i32,
    pub est: Option<Est>,
    pub sleep: f32,
    pub work: f32,
    pub retire: f32,
    pub commute: f32,
    pub chores: f32,
    pub phone: f32,
    // the desktop
    pub wall: u8,
    pub auto_wall: bool,
    pub auto_lock: bool,
    pub tray: bool,
    pub autostart: bool,
    pub monday: bool,
    pub tour_done: bool,
    /// What the wallpaper last showed, so it's only redrawn when something changes.
    pub wall_key: String,
    /// The screen in pixels, for drawing the wallpaper at the right size.
    pub screen: (u32, u32),
    pub last_note: String,
}

impl Default for Settings {
    fn default() -> Self {
        Settings {
            birth: None, span: 80, lived: false, pct: false, style: 0, sun: false, ripple: true, unit: 0, page: 0, zoom: 1,
            names: BTreeMap::new(), people: vec![], events: vec![], letters: vec![], reviewed_age: -1, est: None,
            sleep: 8.0, work: 40.0, retire: 65.0, commute: 45.0, chores: 2.5, phone: 4.0,
            wall: 1, auto_wall: false, auto_lock: false, tray: true, autostart: false, monday: false, tour_done: false,
            wall_key: String::new(), screen: (2560, 1440), last_note: String::new(),
        }
    }
}

impl Settings {
    pub fn birth(&self) -> Option<NaiveDate> { self.birth.as_deref().and_then(|b| NaiveDate::parse_from_str(b, "%Y-%m-%d").ok()) }
}

pub fn dir() -> PathBuf {
    let base = std::env::var_os("XDG_DATA_HOME").map(PathBuf::from).filter(|p| p.is_absolute())
        .unwrap_or_else(|| PathBuf::from(std::env::var_os("HOME").unwrap_or_default()).join(".local/share"));
    base.join("yrs")
}

fn read<T: for<'de> Deserialize<'de> + Default>(name: &str) -> T {
    std::fs::read_to_string(dir().join(name)).ok().and_then(|s| serde_json::from_str(&s).ok()).unwrap_or_default()
}
/// Written to a temporary file first, then moved into place, so a crash can never leave half a file.
fn write<T: Serialize>(name: &str, v: &T) {
    let d = dir();
    let _ = std::fs::create_dir_all(&d);
    if let Ok(s) = serde_json::to_string_pretty(v) {
        let tmp = d.join(format!(".{name}.tmp"));
        if std::fs::write(&tmp, s).is_ok() { let _ = std::fs::rename(tmp, d.join(name)); }
    }
}

pub fn load() -> Settings { read("settings.json") }
pub fn save(s: &Settings) { write("settings.json", s) }
/// One line a day, keyed by ISO date.
pub fn load_lines() -> BTreeMap<String, String> { read("lines.json") }
pub fn save_lines(l: &BTreeMap<String, String>) { write("lines.json", l) }
