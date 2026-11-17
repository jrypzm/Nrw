//! The queue on top of the sound: what plays, in which order, and what comes next.

use crate::audio::{Audio, Cmd};
use crate::library::Library;
use crate::store::Repeat;
use rand::seq::SliceRandom;

pub struct Player {
    pub audio: Audio,
    /// Songs in the order they will play (already shuffled when shuffle is on).
    pub queue: Vec<usize>,
    /// The same songs in the order they were chosen, to return to when shuffle goes off.
    base: Vec<usize>,
    pub at: usize,
    pub playing: bool,
    pub pos: f32,
    pub shuffle: bool,
    pub repeat: Repeat,
    pub volume: f32,
    pub muted: bool,
    pub from: String,
    pub error: Option<String>,
    pub no_device: bool,
    handed: Option<usize>,
    dirty: bool,
    seen: (u64, u64),
    loaded: bool,
    /// A seek made while paused lands only once sound flows; show it meanwhile.
    held: Option<f32>,
    told: Option<String>,
}

impl Player {
    pub fn new(audio: Audio) -> Self {
        Player { audio, queue: vec![], base: vec![], at: 0, playing: false, pos: 0.0, shuffle: false, repeat: Repeat::All, volume: 0.8, muted: false, from: String::new(), error: None, no_device: false, handed: None, dirty: false, seen: (0, 0), loaded: false, held: None, told: None }
    }

    pub fn current(&self) -> Option<usize> {
        self.queue.get(self.at).copied()
    }

    pub fn set_volume(&mut self, v: f32) {
        self.volume = v.clamp(0.0, 1.0);
        self.audio.send(Cmd::Volume(if self.muted { 0.0 } else { self.volume }));
    }

    pub fn toggle_mute(&mut self) {
        self.muted = !self.muted;
        self.set_volume(self.volume);
    }

    fn load(&mut self, lib: &Library, at: f32, play: bool) {
        self.handed = None;
        self.dirty = false;
        if let Some(t) = self.current() {
            self.audio.send(Cmd::Load { path: lib.tracks[t].path.clone(), at, play });
            self.pos = at;
            self.held = if play || at < 0.5 { None } else { Some(at) };
            self.playing = play;
            self.loaded = true;
        }
    }

    /// Play `list`, starting with the song at `start`.
    pub fn play_list(&mut self, lib: &Library, list: Vec<usize>, start: usize, from: String) {
        if list.is_empty() { return; }
        let start = start.min(list.len() - 1);
        self.base = list.clone();
        self.from = from;
        if self.shuffle {
            let first = list[start];
            let mut rest: Vec<usize> = list.into_iter().enumerate().filter(|(i, _)| *i != start).map(|(_, t)| t).collect();
            rest.shuffle(&mut rand::thread_rng());
            self.queue = std::iter::once(first).chain(rest).collect();
            self.at = 0;
        } else {
            self.queue = list;
            self.at = start;
        }
        self.load(lib, 0.0, true);
    }

    /// Play everything in `list`, shuffled or in order.
    pub fn play_all(&mut self, lib: &Library, list: Vec<usize>, shuffle: bool, from: String) {
        if list.is_empty() { return; }
        self.shuffle = shuffle;
        let start = if shuffle { rand::random::<usize>() % list.len() } else { 0 };
        self.play_list(lib, list, start, from);
    }

    /// Put back a queue from last time, paused where it was.
    pub fn restore(&mut self, lib: &Library, queue: Vec<usize>, at: usize, pos: f32) {
        if queue.is_empty() { return; }
        self.base = queue.clone();
        self.queue = queue;
        self.at = at.min(self.queue.len() - 1);
        self.load(lib, pos, false);
    }

    pub fn toggle(&mut self, lib: &Library) {
        if !self.loaded { if self.current().is_some() { let p = self.pos; self.load(lib, p, true); } return; }
        if self.playing { self.audio.send(Cmd::Pause); self.playing = false } else { self.audio.send(Cmd::Play); self.playing = true }
    }

    pub fn play(&mut self, lib: &Library) { if !self.playing { self.toggle(lib) } }
    pub fn pause(&mut self, lib: &Library) { if self.playing { self.toggle(lib) } }

    fn following(&self) -> Option<usize> {
        if self.queue.is_empty() { return None; }
        match self.repeat {
            Repeat::One => Some(self.at),
            _ if self.at + 1 < self.queue.len() => Some(self.at + 1),
            Repeat::All => Some(0),
            Repeat::Off => None,
        }
    }

    pub fn next(&mut self, lib: &Library) {
        if self.queue.is_empty() { return; }
        // a press of Next moves on even when one song repeats
        self.at = if self.at + 1 < self.queue.len() { self.at + 1 } else { 0 };
        let play = self.playing || self.at != 0 || self.repeat == Repeat::All;
        self.load(lib, 0.0, play);
    }

    pub fn prev(&mut self, lib: &Library) {
        if self.queue.is_empty() { return; }
        if self.pos > 3.0 { self.seek(0.0); return; }
        self.at = if self.at > 0 { self.at - 1 } else if self.repeat == Repeat::All { self.queue.len() - 1 } else { 0 };
        let play = self.playing;
        self.load(lib, 0.0, play);
    }

    pub fn seek(&mut self, to: f32) {
        // the next song may already be queued behind this one; it still follows
        self.pos = to.max(0.0);
        if !self.playing { self.held = Some(self.pos); }
        self.audio.send(Cmd::Seek(self.pos));
    }

    pub fn set_shuffle(&mut self, on: bool) {
        self.shuffle = on;
        let Some(cur) = self.current() else { return };
        if on {
            let mut rest: Vec<usize> = self.queue.iter().enumerate().filter(|(i, _)| *i != self.at).map(|(_, &t)| t).collect();
            rest.shuffle(&mut rand::thread_rng());
            self.queue = std::iter::once(cur).chain(rest).collect();
            self.at = 0;
        } else if !self.base.is_empty() {
            self.queue = self.base.clone();
            self.at = self.queue.iter().position(|&t| t == cur).unwrap_or(0);
        }
        self.edited();
    }

    pub fn cycle_repeat(&mut self) {
        self.repeat = match self.repeat { Repeat::Off => Repeat::All, Repeat::All => Repeat::One, Repeat::One => Repeat::Off };
        self.edited();
    }

    fn edited(&mut self) {
        if self.handed.is_some() { self.dirty = true; }
    }

    pub fn play_next(&mut self, lib: &Library, songs: &[usize]) {
        if self.queue.is_empty() { self.play_list(lib, songs.to_vec(), 0, String::new()); return; }
        for (k, &t) in songs.iter().enumerate() {
            self.queue.insert(self.at + 1 + k, t);
            self.base.push(t);
        }
        self.edited();
    }

    pub fn add(&mut self, lib: &Library, songs: &[usize]) {
        if self.queue.is_empty() { self.play_list(lib, songs.to_vec(), 0, String::new()); return; }
        self.queue.extend_from_slice(songs);
        self.base.extend_from_slice(songs);
        self.edited();
    }

    pub fn remove(&mut self, lib: &Library, i: usize) {
        if i >= self.queue.len() { return; }
        if i == self.at {
            self.queue.remove(i);
            if self.queue.is_empty() { self.stop(); return; }
            if self.at >= self.queue.len() { self.at = 0; }
            let play = self.playing;
            self.load(lib, 0.0, play);
            return;
        }
        self.queue.remove(i);
        if i < self.at { self.at -= 1; }
        self.edited();
    }

    pub fn move_item(&mut self, from: usize, to: usize) {
        if from >= self.queue.len() || to >= self.queue.len() || from == to { return; }
        let t = self.queue.remove(from);
        self.queue.insert(to, t);
        if from == self.at { self.at = to } else if from < self.at && to >= self.at { self.at -= 1 } else if from > self.at && to <= self.at { self.at += 1 }
        self.edited();
    }

    pub fn clear_next(&mut self) {
        self.queue.truncate(self.at + 1);
        self.edited();
    }

    pub fn stop(&mut self) {
        self.audio.send(Cmd::Stop);
        self.queue.clear();
        self.base.clear();
        self.at = 0;
        self.playing = false;
        self.pos = 0.0;
        self.loaded = false;
        self.handed = None;
    }

    /// Follow the sound thread: position, gapless hand-overs, the end of the queue.
    pub fn tick(&mut self, lib: &Library) {
        let s = self.audio.snapshot();
        self.no_device = s.no_device;
        // say each problem once, not every frame it persists
        if s.error.is_some() && s.error != self.told { self.error = s.error.clone(); self.told = s.error.clone(); }
        if s.advanced != self.seen.0 {
            self.seen.0 = s.advanced;
            if let Some(h) = self.handed.take() {
                if self.dirty {
                    // the queue changed after the hand-over: start the song that should follow now
                    self.dirty = false;
                    if let Some(n) = self.following() { self.at = n; self.load(lib, 0.0, true); }
                    return;
                }
                self.at = h;
            }
        }
        if s.ended != self.seen.1 {
            self.seen.1 = s.ended;
            self.handed = None;
            if s.error.is_some() && self.queue.len() > 1 {
                // a file that won't play is skipped, not stuck on
                if let Some(n) = self.following().filter(|&n| n != self.at) { self.at = n; self.load(lib, 0.0, true); }
                return;
            }
            match self.following() {
                Some(n) => { self.at = n; self.load(lib, 0.0, true); }
                None => { self.loaded = false; self.playing = false; self.pos = 0.0; }
            }
            return;
        }
        if self.loaded { self.playing = s.playing; }
        match self.held {
            Some(h) if !s.playing => self.pos = h,
            Some(h) if (s.pos - h).abs() < 1.5 || s.pos > h => { self.held = None; self.pos = s.pos }
            Some(h) => self.pos = h,
            None => self.pos = s.pos,
        }
        // hand the next song over a few seconds early, so it follows without a gap
        if self.playing && self.handed.is_none() {
            if let Some(cur) = self.current() {
                let left = lib.tracks[cur].secs - self.pos;
                if left < 8.0 && left > 0.0 {
                    if let Some(n) = self.following() {
                        self.audio.send(Cmd::Then(lib.tracks[self.queue[n]].path.clone()));
                        self.handed = Some(n);
                    }
                }
            }
        }
    }

    /// The songs still to come, with their places in the queue.
    pub fn upcoming(&self) -> Vec<(usize, usize)> {
        if self.queue.is_empty() { return vec![]; }
        ((self.at + 1)..self.queue.len()).map(|i| (i, self.queue[i])).collect()
    }
}
