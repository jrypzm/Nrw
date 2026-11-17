//! The sound. One thread owns the output and a rodio sink. The next song is handed
//! over a few seconds early and queued behind the current one, so albums play
//! without a gap.

use rodio::{Decoder, OutputStream, Sink};
use std::fs::File;
use std::io::BufReader;
use std::path::PathBuf;
use std::sync::mpsc::{channel, Receiver, Sender};
use std::sync::{Arc, Mutex};
use std::time::Duration;

pub enum Cmd {
    /// Start this song now, from `at` seconds.
    Load { path: PathBuf, at: f32, play: bool },
    /// Queue this song to follow the current one without a gap.
    Then(PathBuf),
    Play,
    Pause,
    Seek(f32),
    Volume(f32),
    Stop,
}

#[derive(Default, Clone)]
pub struct State {
    pub pos: f32,
    pub playing: bool,
    /// Bumped each time the sink moves on to a song queued with `Then`.
    pub advanced: u64,
    /// Bumped when the last queued song finished and nothing followed.
    pub ended: u64,
    pub error: Option<String>,
    pub no_device: bool,
}

pub struct Audio {
    tx: Sender<Cmd>,
    pub state: Arc<Mutex<State>>,
}

impl Audio {
    pub fn start(wake: impl Fn() + Send + 'static) -> Self {
        let (tx, rx) = channel();
        let state = Arc::new(Mutex::new(State::default()));
        let st = state.clone();
        std::thread::Builder::new().name("audio".into()).spawn(move || run(rx, st, wake)).expect("audio thread");
        Audio { tx, state }
    }

    pub fn send(&self, c: Cmd) {
        let _ = self.tx.send(c);
    }

    pub fn snapshot(&self) -> State {
        self.state.lock().map(|s| s.clone()).unwrap_or_default()
    }
}

fn open(path: &PathBuf) -> Result<Decoder<BufReader<File>>, String> {
    let name = path.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default();
    let f = File::open(path).map_err(|e| format!("Can't open {name}: {e}"))?;
    Decoder::new(BufReader::with_capacity(256 * 1024, f)).map_err(|e| format!("Can't play {name}: {e}"))
}

fn run(rx: Receiver<Cmd>, st: Arc<Mutex<State>>, wake: impl Fn()) {
    let out = OutputStream::try_default();
    let (_stream, handle) = match out {
        Ok(o) => o,
        Err(e) => {
            if let Ok(mut s) = st.lock() { s.no_device = true; s.error = Some("No sound output found. Check your sound settings, then reopen yrs. music.".into()); let _ = e; }
            wake();
            // keep answering so the app stays usable
            while rx.recv().is_ok() {}
            return;
        }
    };
    let sink = match Sink::try_new(&handle) {
        Ok(s) => s,
        Err(e) => {
            if let Ok(mut s) = st.lock() { s.no_device = true; s.error = Some("The sound output couldn't be opened. Check your sound settings, then reopen yrs. music.".into()); let _ = e; }
            wake();
            while rx.recv().is_ok() {}
            return;
        }
    };
    sink.pause();
    let mut loaded = false;
    let mut last_len = 0usize;
    loop {
        let msg = rx.recv_timeout(Duration::from_millis(80));
        let mut changed = false;
        match msg {
            Ok(Cmd::Load { path, at, play }) => {
                sink.stop();
                sink.clear();
                match open(&path) {
                    Ok(src) => {
                        sink.append(src);
                        if at > 0.5 { let _ = sink.try_seek(Duration::from_secs_f32(at)); }
                        if play { sink.play() } else { sink.pause() }
                        loaded = true;
                        if let Ok(mut s) = st.lock() { s.error = None; s.pos = at; }
                    }
                    Err(e) => {
                        loaded = false;
                        if let Ok(mut s) = st.lock() { s.error = Some(e); s.ended += 1; }
                    }
                }
                last_len = sink.len();
                changed = true;
            }
            Ok(Cmd::Then(path)) => {
                if let Ok(src) = open(&path) { sink.append(src); }
                last_len = sink.len();
            }
            Ok(Cmd::Play) => { if loaded { sink.play(); changed = true; } }
            Ok(Cmd::Pause) => { sink.pause(); changed = true; }
            Ok(Cmd::Seek(at)) => { let _ = sink.try_seek(Duration::from_secs_f32(at.max(0.0))); changed = true; }
            Ok(Cmd::Volume(v)) => sink.set_volume(v.clamp(0.0, 1.0).powf(1.6)),
            Ok(Cmd::Stop) => { sink.stop(); sink.clear(); loaded = false; changed = true; }
            Err(std::sync::mpsc::RecvTimeoutError::Timeout) => {}
            Err(_) => return,
        }
        let len = sink.len();
        if let Ok(mut s) = st.lock() {
            if loaded && len < last_len && len > 0 { s.advanced += 1; changed = true; }
            if loaded && len == 0 && last_len > 0 { s.ended += 1; loaded = false; changed = true; }
            s.pos = sink.get_pos().as_secs_f32();
            s.playing = loaded && !sink.is_paused() && len > 0;
        }
        last_len = len;
        if changed { wake(); }
    }
}
