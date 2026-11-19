//! The sound. One thread owns the output and a rodio sink. The next song is handed
//! over a few seconds early and queued behind the current one, so albums play
//! without a gap.
//!
//! The thread looks after itself: if anything inside it panics, it starts again with a fresh
//! output, and if the sound stops moving while it should be playing (headphones unplugged,
//! the sound server restarted) it reconnects and carries on from the same place.

use crate::decode;
use rodio::{OutputStream, OutputStreamHandle, Sink};
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::PathBuf;
use std::sync::mpsc::{channel, Receiver, RecvTimeoutError, Sender};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

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
        std::thread::Builder::new()
            .name("audio".into())
            .spawn(move || {
                // if the sound thread ever panics, start it again rather than go quiet for good
                let mut keep = Keep::default();
                loop {
                    match catch_unwind(AssertUnwindSafe(|| run(&rx, &st, &wake, &mut keep))) {
                        Ok(()) => return, // the app is closing
                        Err(_) => {
                            if let Ok(mut s) = st.lock() { s.error = Some("The sound stopped unexpectedly and was restarted.".into()); }
                            wake();
                            std::thread::sleep(Duration::from_millis(300));
                        }
                    }
                }
            })
            .expect("audio thread");
        Audio { tx, state }
    }

    pub fn send(&self, c: Cmd) {
        let _ = self.tx.send(c);
    }

    pub fn snapshot(&self) -> State {
        self.state.lock().map(|s| s.clone()).unwrap_or_else(|p| p.into_inner().clone())
    }
}

/// What the thread needs to pick up where it was after a restart.
#[derive(Default)]
struct Keep {
    current: Option<PathBuf>,
    queued: Vec<PathBuf>,
    volume: Option<f32>,
    playing: bool,
    pos: f32,
}

struct Out {
    _stream: OutputStream,
    handle: OutputStreamHandle,
    sink: Sink,
}

fn connect(volume: Option<f32>) -> Option<Out> {
    let (stream, handle) = OutputStream::try_default().ok()?;
    let sink = Sink::try_new(&handle).ok()?;
    sink.pause();
    if let Some(v) = volume { sink.set_volume(v); }
    Some(Out { _stream: stream, handle, sink })
}

fn set(st: &Arc<Mutex<State>>, f: impl FnOnce(&mut State)) {
    match st.lock() {
        Ok(mut s) => f(&mut s),
        Err(p) => f(&mut p.into_inner()),
    }
}

fn run(rx: &Receiver<Cmd>, st: &Arc<Mutex<State>>, wake: &impl Fn(), keep: &mut Keep) {
    let mut out = loop {
        match connect(keep.volume) {
            Some(o) => break o,
            None => {
                set(st, |s| { s.no_device = true; s.error = Some("No sound output found. Check your sound settings; yrs. music keeps trying.".into()); });
                wake();
                // keep answering, and try the output again now and then
                match rx.recv_timeout(Duration::from_secs(2)) {
                    Err(RecvTimeoutError::Disconnected) => return,
                    _ => continue,
                }
            }
        }
    };
    set(st, |s| { if s.no_device { s.no_device = false; s.error = None; } });

    // back from a restart: put the song back where it was
    let mut loaded = false;
    if let Some(p) = keep.current.clone() {
        loaded = resume(&out, keep, &p);
    }
    let mut last_len = out.sink.len();
    let mut last_pos = out.sink.get_pos();
    let mut still_since: Option<Instant> = None;

    loop {
        let msg = rx.recv_timeout(Duration::from_millis(80));
        let mut changed = false;
        match msg {
            Ok(Cmd::Load { path, at, play }) => {
                out.sink.stop();
                out.sink.clear();
                keep.queued.clear();
                match decode::open(&path) {
                    Ok(mut song) => {
                        if at > 0.5 { let _ = rodio::Source::try_seek(&mut song, Duration::from_secs_f32(at)); }
                        out.sink.append(song);
                        if play { out.sink.play() } else { out.sink.pause() }
                        loaded = true;
                        keep.current = Some(path);
                        keep.playing = play;
                        keep.pos = at;
                        set(st, |s| { s.error = None; s.pos = at; });
                    }
                    Err(e) => {
                        loaded = false;
                        keep.current = None;
                        set(st, |s| { s.error = Some(e); s.ended += 1; });
                    }
                }
                last_len = out.sink.len();
                changed = true;
            }
            Ok(Cmd::Then(path)) => {
                // a song that won't open isn't queued; when the current one ends,
                // the player moves on and reports it then
                if let Ok(song) = decode::open(&path) {
                    out.sink.append(song);
                    keep.queued.push(path);
                }
                last_len = out.sink.len();
            }
            Ok(Cmd::Play) => { if loaded { out.sink.play(); keep.playing = true; changed = true; } }
            Ok(Cmd::Pause) => { out.sink.pause(); keep.playing = false; changed = true; }
            Ok(Cmd::Seek(at)) => {
                let at = at.max(0.0);
                if out.sink.try_seek(Duration::from_secs_f32(at)).is_err() && loaded {
                    // the file couldn't jump there itself: open it again at that spot
                    if let Some(p) = keep.current.clone() {
                        keep.pos = at;
                        let was = keep.playing;
                        out.sink.stop();
                        out.sink.clear();
                        loaded = resume(&out, keep, &p);
                        keep.playing = was;
                        if was { out.sink.play() }
                        last_len = out.sink.len();
                    }
                }
                keep.pos = at;
                still_since = None;
                changed = true;
            }
            Ok(Cmd::Volume(v)) => {
                let v = v.clamp(0.0, 1.0).powf(1.6);
                keep.volume = Some(v);
                out.sink.set_volume(v);
            }
            Ok(Cmd::Stop) => {
                out.sink.stop();
                out.sink.clear();
                loaded = false;
                keep.current = None;
                keep.queued.clear();
                keep.playing = false;
                changed = true;
            }
            Err(RecvTimeoutError::Timeout) => {}
            Err(RecvTimeoutError::Disconnected) => return,
        }

        let len = out.sink.len();
        if loaded && len < last_len && len > 0 {
            // moved on to the song queued behind
            if !keep.queued.is_empty() { keep.current = Some(keep.queued.remove(0)); }
            set(st, |s| s.advanced += 1);
            changed = true;
        }
        if loaded && len == 0 && last_len > 0 {
            set(st, |s| s.ended += 1);
            loaded = false;
            keep.current = None;
            keep.queued.clear();
            changed = true;
        }
        last_len = len;

        let pos = out.sink.get_pos();
        let playing = loaded && !out.sink.is_paused() && len > 0;
        if loaded { keep.pos = pos.as_secs_f32(); }
        set(st, |s| { s.pos = pos.as_secs_f32(); s.playing = playing; });

        // playing, but the sound isn't moving: the output has gone away. Reconnect.
        if playing && pos == last_pos {
            let since = *still_since.get_or_insert_with(Instant::now);
            if since.elapsed() > Duration::from_secs(4) {
                still_since = None;
                if let Some(fresh) = connect(keep.volume) {
                    out = fresh;
                    if let Some(p) = keep.current.clone() {
                        loaded = resume(&out, keep, &p);
                    }
                    last_len = out.sink.len();
                    changed = true;
                }
            }
        } else {
            still_since = None;
        }
        last_pos = pos;

        if changed { wake(); }
    }
}

/// Puts `path` back on a sink at the kept position, with anything that was queued behind it.
fn resume(out: &Out, keep: &mut Keep, path: &PathBuf) -> bool {
    let _ = &out.handle;
    match decode::open(path) {
        Ok(mut song) => {
            if keep.pos > 0.5 { let _ = rodio::Source::try_seek(&mut song, Duration::from_secs_f32(keep.pos)); }
            out.sink.append(song);
            let queued = std::mem::take(&mut keep.queued);
            for q in queued {
                if let Ok(s) = decode::open(&q) { out.sink.append(s); keep.queued.push(q); }
            }
            if keep.playing { out.sink.play() } else { out.sink.pause() }
            true
        }
        Err(_) => false,
    }
}
