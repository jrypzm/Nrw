//! What yrs. music remembers between runs, as one small JSON file.

use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq, Default, Debug)]
pub enum Repeat {
    #[default]
    Off,
    All,
    One,
}

#[derive(Serialize, Deserialize, Clone, Default)]
pub struct List {
    pub name: String,
    pub songs: Vec<PathBuf>,
}

#[derive(Serialize, Deserialize, Clone)]
#[serde(default)]
pub struct Store {
    pub folders: Vec<PathBuf>,
    pub volume: f32,
    pub muted: bool,
    pub shuffle: bool,
    pub repeat: Repeat,
    pub cover_colour: bool,
    pub loved: Vec<PathBuf>,
    pub lists: Vec<List>,
    pub queue: Vec<PathBuf>,
    pub at: usize,
    pub pos: f32,
    /// Songs order: 0 name, 1 size, 2 length, 3 date changed; and whether the first way round.
    pub order: (u8, bool),
    /// The player beside the list (true) or along the bottom.
    pub side: bool,
    /// Width of the player beside the list.
    pub split: f32,
    /// Use the desktop's own window frame instead of ours.
    pub system_frame: bool,
    pub size: Option<[f32; 2]>,
    pub welcomed: bool,
    pub from: String,
}

impl Default for Store {
    fn default() -> Self {
        Store { folders: vec![], volume: 0.8, muted: false, shuffle: false, repeat: Repeat::All, cover_colour: true, loved: vec![], lists: vec![], queue: vec![], at: 0, pos: 0.0, order: (0, true), side: true, split: 420.0, system_frame: false, size: None, welcomed: false, from: String::new() }
    }
}

fn file() -> PathBuf {
    crate::library::data_dir().join("settings.json")
}

impl Store {
    pub fn load() -> Self {
        std::fs::read(file()).ok().and_then(|b| serde_json::from_slice(&b).ok()).unwrap_or_default()
    }

    pub fn save(&self) {
        let _ = std::fs::create_dir_all(crate::library::data_dir());
        if let Ok(b) = serde_json::to_vec_pretty(self) {
            // write then rename, so a crash never leaves half a file
            let tmp = file().with_extension("tmp");
            if std::fs::write(&tmp, b).is_ok() { let _ = std::fs::rename(tmp, file()); }
        }
    }
}
