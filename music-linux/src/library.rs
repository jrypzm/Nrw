//! The library: songs read from your folders, in place. Tags are read once and kept
//! in a small cache, keyed by each file's modification time, so opening is instant.

use lofty::file::{AudioFile, TaggedFileExt};
use lofty::picture::PictureType;
use lofty::tag::{Accessor, ItemKey};
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::sync::mpsc::Sender;
use std::time::UNIX_EPOCH;

pub const AUDIO: &[&str] = &["flac", "mp3", "m4a", "mp4", "aac", "ogg", "oga", "wav", "aif", "aiff"];

#[derive(Clone, Serialize, Deserialize, Debug)]
pub struct Track {
    pub path: PathBuf,
    pub mtime: u64,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: String,
    pub year: Option<u32>,
    pub track: Option<u32>,
    pub disc: Option<u32>,
    pub secs: f32,
    pub format: String,
    pub bits: Option<u8>,
    pub rate: Option<u32>,
    pub kbps: Option<u32>,
    #[serde(default)]
    pub album_key: String,
    /// Bytes on disk, for sorting by size.
    #[serde(default)]
    pub size: u64,
}

impl Track {
    pub fn lossless(&self) -> bool {
        matches!(self.format.as_str(), "FLAC" | "WAV" | "AIFF" | "ALAC")
    }

    /// "FLAC · 24/96" or "MP3 · 320 KBPS".
    pub fn quality(&self) -> String {
        let mut s = self.format.clone();
        if self.lossless() {
            if let (Some(b), Some(r)) = (self.bits, self.rate) {
                let k = r as f32 / 1000.0;
                let khz = if (k - k.round()).abs() < 0.05 { format!("{}", k.round() as u32) } else { format!("{k:.1}") };
                s += &format!(" · {b}/{khz}");
            }
        } else if let Some(k) = self.kbps {
            s += &format!(" · {k} KBPS");
        }
        s
    }
}

#[derive(Clone, Debug)]
pub struct Album {
    pub key: String,
    pub title: String,
    pub artist: String,
    pub year: Option<u32>,
    pub tracks: Vec<usize>,
}

#[derive(Default)]
pub struct Library {
    pub tracks: Vec<Track>,
    pub albums: Vec<Album>,
    pub by_path: HashMap<PathBuf, usize>,
    pub by_album: HashMap<String, usize>,
}

impl Library {
    pub fn build(mut tracks: Vec<Track>) -> Self {
        tracks.sort_by(|a, b| sort_key(&a.title).cmp(&sort_key(&b.title)).then(a.artist.cmp(&b.artist)));
        let mut albums: Vec<Album> = vec![];
        let mut by_album = HashMap::new();
        for (i, t) in tracks.iter().enumerate() {
            let a = *by_album.entry(t.album_key.clone()).or_insert_with(|| {
                albums.push(Album { key: t.album_key.clone(), title: t.album.clone(), artist: t.album_artist.clone(), year: t.year, tracks: vec![] });
                albums.len() - 1
            });
            albums[a].tracks.push(i);
            if albums[a].year.is_none() { albums[a].year = t.year; }
        }
        for a in &mut albums {
            a.tracks.sort_by_key(|&i| (tracks[i].disc.unwrap_or(1), tracks[i].track.unwrap_or(u32::MAX), sort_key(&tracks[i].title)));
            let artists: HashSet<&str> = a.tracks.iter().map(|&i| tracks[i].artist.as_str()).collect();
            if a.artist.is_empty() { a.artist = if artists.len() > 1 { "Various artists".into() } else { tracks[a.tracks[0]].artist.clone() }; }
        }
        albums.sort_by(|a, b| sort_key(&a.title).cmp(&sort_key(&b.title)));
        let by_album = albums.iter().enumerate().map(|(i, a)| (a.key.clone(), i)).collect();
        let by_path = tracks.iter().enumerate().map(|(i, t)| (t.path.clone(), i)).collect();
        Library { tracks, albums, by_path, by_album }
    }

    pub fn album_of(&self, t: usize) -> Option<&Album> {
        self.by_album.get(&self.tracks[t].album_key).map(|&a| &self.albums[a])
    }
}

pub fn sort_key(s: &str) -> String {
    let l = s.trim().to_lowercase();
    l.strip_prefix("the ").map(str::to_string).unwrap_or(l)
}

pub fn hash(s: &str) -> String {
    // FNV-1a: stable across runs, good enough for file names
    let mut h: u64 = 0xcbf29ce484222325;
    for b in s.as_bytes() { h ^= *b as u64; h = h.wrapping_mul(0x100000001b3); }
    format!("{h:016x}")
}

pub fn data_dir() -> PathBuf {
    let base = std::env::var_os("XDG_DATA_HOME").map(PathBuf::from).unwrap_or_else(|| home().join(".local/share"));
    base.join("yrs-music")
}

pub fn cache_dir() -> PathBuf {
    let base = std::env::var_os("XDG_CACHE_HOME").map(PathBuf::from).unwrap_or_else(|| home().join(".cache"));
    base.join("yrs-music")
}

pub fn home() -> PathBuf {
    std::env::var_os("HOME").map(PathBuf::from).unwrap_or_else(|| PathBuf::from("/"))
}

pub fn music_dir() -> PathBuf {
    // honour the XDG user folder if it was renamed or translated
    let dirs = home().join(".config/user-dirs.dirs");
    if let Ok(s) = std::fs::read_to_string(dirs) {
        for l in s.lines() {
            if let Some(v) = l.strip_prefix("XDG_MUSIC_DIR=") {
                let v = v.trim_matches('"').replace("$HOME", &home().to_string_lossy());
                return PathBuf::from(v);
            }
        }
    }
    home().join("Music")
}

pub fn cover_path(key: &str, size: Size) -> PathBuf {
    cache_dir().join("covers").join(format!("{}-{}.png", hash(key), size.px()))
}

#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
pub enum Size {
    S,
    M,
    L,
}

impl Size {
    pub fn px(self) -> u32 {
        match self { Size::S => 112, Size::M => 360, Size::L => 900 }
    }
}

pub enum Scan {
    Progress(usize, usize),
    Done(Vec<Track>),
    /// New cover pictures were written; reload any shown so far.
    Covers,
}

fn is_audio(p: &Path) -> bool {
    p.extension().and_then(|e| e.to_str()).map(|e| AUDIO.contains(&e.to_lowercase().as_str())).unwrap_or(false)
}

fn mtime(p: &Path) -> u64 {
    std::fs::metadata(p).and_then(|m| m.modified()).ok().and_then(|t| t.duration_since(UNIX_EPOCH).ok()).map(|d| d.as_secs()).unwrap_or(0)
}

fn bytes(p: &Path) -> u64 {
    std::fs::metadata(p).map(|m| m.len()).unwrap_or(0)
}

pub fn read_one(path: &Path) -> Option<Track> {
    let file = lofty::read_from_path(path).ok()?;
    let props = file.properties();
    let tag = file.primary_tag().or_else(|| file.first_tag());
    let stem = path.file_stem().map(|s| s.to_string_lossy().to_string()).unwrap_or_default();
    let ext = path.extension().map(|e| e.to_string_lossy().to_uppercase()).unwrap_or_default();
    let get = |f: &dyn Fn(&lofty::tag::Tag) -> Option<String>| tag.and_then(f).map(|s| s.trim().to_string()).filter(|s| !s.is_empty());
    let title = get(&|t| t.title().map(|s| s.to_string())).unwrap_or(stem);
    let artist = get(&|t| t.artist().map(|s| s.to_string())).unwrap_or_else(|| "Unknown artist".into());
    let album_tag = get(&|t| t.album().map(|s| s.to_string()));
    let album_artist = get(&|t| t.get_string(&ItemKey::AlbumArtist).map(|s| s.to_string())).unwrap_or_default();
    let folder = path.parent().map(|p| p.to_string_lossy().to_string()).unwrap_or_default();
    let album = album_tag.clone().unwrap_or_else(|| path.parent().and_then(|p| p.file_name()).map(|s| s.to_string_lossy().to_string()).unwrap_or_else(|| "Unknown album".into()));
    // the same album title in two folders by two artists stays two albums
    let album_key = match &album_tag {
        Some(a) => format!("{}\u{1}{}", a.to_lowercase(), if album_artist.is_empty() { folder.to_lowercase() } else { album_artist.to_lowercase() }),
        None => format!("dir\u{1}{}", folder.to_lowercase()),
    };
    let format = match ext.as_str() {
        "M4A" | "MP4" => if format!("{:?}", file.file_type()).contains("Mp4") && props.bit_depth().is_some() { "ALAC".into() } else { "AAC".into() },
        "OGA" | "OGG" => "OGG".into(),
        "AIF" => "AIFF".into(),
        e => e.to_string(),
    };
    Some(Track {
        path: path.to_path_buf(),
        mtime: mtime(path),
        title,
        artist,
        album,
        album_artist,
        year: tag.and_then(|t| t.year()),
        track: tag.and_then(|t| t.track()),
        disc: tag.and_then(|t| t.disk()),
        secs: props.duration().as_secs_f32(),
        format,
        bits: props.bit_depth(),
        rate: props.sample_rate(),
        kbps: props.audio_bitrate(),
        album_key,
        size: bytes(path),
    })
}

/// Walk the folders, reuse what the cache already knows, read the rest on every core.
pub fn scan(folders: Vec<PathBuf>, tx: Sender<Scan>, wake: impl Fn() + Send + 'static) {
    std::thread::spawn(move || {
        let cache_file = data_dir().join("library.json");
        let old: Vec<Track> = std::fs::read(&cache_file).ok().and_then(|b| serde_json::from_slice(&b).ok()).unwrap_or_default();
        let old: HashMap<PathBuf, Track> = old.into_iter().map(|t| (t.path.clone(), t)).collect();
        let mut files = vec![];
        let mut seen = HashSet::new();
        for f in &folders {
            for e in walkdir::WalkDir::new(f).follow_links(true).into_iter().filter_map(Result::ok) {
                if e.file_type().is_file() && is_audio(e.path()) && seen.insert(e.path().to_path_buf()) {
                    files.push(e.path().to_path_buf());
                }
            }
        }
        let total = files.len();
        let (keep, todo): (Vec<_>, Vec<_>) = files.into_iter().partition(|p| old.get(p).map(|t| t.mtime == mtime(p) && !t.album_key.is_empty()).unwrap_or(false));
        // older caches have no size; a stat is cheap
        let mut tracks: Vec<Track> = keep.iter().filter_map(|p| old.get(p).cloned()).map(|mut t| { if t.size == 0 { t.size = bytes(&t.path); } t }).collect();
        let _ = tx.send(Scan::Progress(tracks.len(), total));
        wake();
        let threads = std::thread::available_parallelism().map(|n| n.get()).unwrap_or(4).min(8);
        let chunk = todo.len().div_ceil(threads).max(1);
        let done = std::sync::Arc::new(std::sync::atomic::AtomicUsize::new(tracks.len()));
        let results: Vec<Track> = std::thread::scope(|s| {
            let hs: Vec<_> = todo
                .chunks(chunk)
                .map(|c| {
                    let done = done.clone();
                    let tx = tx.clone();
                    s.spawn(move || {
                        let mut out = vec![];
                        for p in c {
                            if let Some(t) = read_one(p) { out.push(t); }
                            let n = done.fetch_add(1, std::sync::atomic::Ordering::Relaxed) + 1;
                            if n % 50 == 0 { let _ = tx.send(Scan::Progress(n, total)); }
                        }
                        out
                    })
                })
                .collect();
            hs.into_iter().flat_map(|h| h.join().unwrap_or_default()).collect()
        });
        tracks.extend(results);
        let _ = std::fs::create_dir_all(data_dir());
        if let Ok(b) = serde_json::to_vec(&tracks) { let _ = std::fs::write(&cache_file, b); }
        // songs first, so the library opens now; covers follow and fade in
        let _ = tx.send(Scan::Done(tracks.clone()));
        wake();
        if make_covers(&tracks) { let _ = tx.send(Scan::Covers); wake(); }
    });
}

/// One set of cover sizes per album: from the first song with a picture, or a cover image in the folder.
fn make_covers(tracks: &[Track]) -> bool {
    let _ = std::fs::create_dir_all(cache_dir().join("covers"));
    let mut by_album: HashMap<&str, Vec<&Track>> = HashMap::new();
    for t in tracks { by_album.entry(&t.album_key).or_default().push(t); }
    let jobs: Vec<(&str, Vec<&Track>)> = by_album.into_iter().filter(|(k, _)| !cover_path(k, Size::S).exists() && !cover_path(k, Size::S).with_extension("none").exists()).collect();
    if jobs.is_empty() { return false; }
    let threads = std::thread::available_parallelism().map(|n| n.get()).unwrap_or(4).min(6);
    let chunk = jobs.len().div_ceil(threads).max(1);
    std::thread::scope(|s| {
        for c in jobs.chunks(chunk) {
            s.spawn(move || {
                for (key, ts) in c {
                    let img = ts.iter().take(4).find_map(|t| embedded(&t.path)).or_else(|| folder_image(&ts[0].path));
                    match img {
                        Some(img) => {
                            for size in [Size::S, Size::M, Size::L] {
                                // never blow a small picture up past its own size (but keep the small one)
                                let px = size.px().min(img.width().min(img.height()).max(Size::S.px()));
                                let sq = square(&img, px);
                                let _ = sq.save(cover_path(key, size));
                            }
                        }
                        None => { let _ = std::fs::write(cover_path(key, Size::S).with_extension("none"), b""); }
                    }
                }
            });
        }
    });
    true
}

fn square(img: &image::DynamicImage, px: u32) -> image::RgbaImage {
    let (w, h) = (img.width(), img.height());
    let side = w.min(h);
    let cropped = img.crop_imm((w - side) / 2, (h - side) / 2, side, side);
    cropped.resize_exact(px, px, image::imageops::FilterType::Lanczos3).to_rgba8()
}

fn embedded(path: &Path) -> Option<image::DynamicImage> {
    let file = lofty::read_from_path(path).ok()?;
    let pics: Vec<_> = file.tags().iter().flat_map(|t| t.pictures().iter()).collect();
    let pic = pics.iter().find(|p| p.pic_type() == PictureType::CoverFront).or(pics.first())?;
    image::load_from_memory(pic.data()).ok()
}

fn folder_image(path: &Path) -> Option<image::DynamicImage> {
    let dir = path.parent()?;
    let mut best: Option<(i32, PathBuf)> = None;
    for e in std::fs::read_dir(dir).ok()?.flatten() {
        let p = e.path();
        let ext = p.extension().and_then(|e| e.to_str()).map(|e| e.to_lowercase()).unwrap_or_default();
        if !matches!(ext.as_str(), "jpg" | "jpeg" | "png") { continue; }
        let name = p.file_stem().map(|s| s.to_string_lossy().to_lowercase()).unwrap_or_default();
        let score = match name.as_str() { "cover" => 0, "folder" => 1, "front" => 2, "album" => 3, _ if name.contains("cover") => 4, _ => 9 };
        if best.as_ref().map(|b| score < b.0).unwrap_or(true) { best = Some((score, p)); }
    }
    image::open(best?.1).ok()
}

/// Lyrics saved inside the song.
pub fn tag_lyrics(path: &Path) -> Option<String> {
    let file = lofty::read_from_path(path).ok()?;
    file.tags().iter().find_map(|t| t.get_string(&ItemKey::Lyrics).map(|s| s.to_string())).filter(|s| !s.trim().is_empty())
}
