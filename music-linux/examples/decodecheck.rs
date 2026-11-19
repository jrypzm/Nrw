//! Runs every file through the app's own decoder the way the player does: open, play it all,
//! jump to the middle. Reports crashes, hangs and failures, so no song can stop the music.
//!   cargo run --release --example decodecheck -- <files...>
#[path = "../src/decode.rs"]
mod decode;

use rodio::Source;
use std::sync::mpsc::channel;
use std::time::{Duration, Instant};

fn check(path: String) -> String {
    let p = std::path::Path::new(&path);
    let t = Instant::now();
    let mut d = match decode::open(p) { Ok(d) => d, Err(e) => return format!("refused: {e}") };
    let (rate, ch, total) = (d.sample_rate(), d.channels(), d.total_duration());
    let mut n = 0u64;
    for _ in d.by_ref() { n += 1; }
    let secs = n as f64 / (rate as f64 * ch as f64);
    let mut d = decode::open(p).unwrap();
    let seek = match d.try_seek(Duration::from_secs(5)) { Ok(()) => "seek ok".to_string(), Err(e) => format!("seek FAILED: {e}") };
    let after: usize = d.by_ref().take(44100).count();
    let mut d2 = decode::open(p).unwrap();
    let _ = d2.try_seek(Duration::from_secs(5));
    let left = d2.count() as f64 / (rate as f64 * ch as f64);
    format!("ok  {rate} Hz x{ch}, plays {secs:.1}s (length {:?}), {seek}, after jumping to 5s {left:.1}s remain, {after} samples, {:?}", total.map(|t| (t.as_secs_f32() * 10.0).round() / 10.0), t.elapsed())
}

fn main() {
    for path in std::env::args().skip(1) {
        let (tx, rx) = channel();
        let p = path.clone();
        std::thread::spawn(move || {
            let r = std::panic::catch_unwind(|| check(p.clone()));
            let _ = tx.send(match r { Ok(s) => s, Err(_) => "CRASH".into() });
        });
        let res = rx.recv_timeout(Duration::from_secs(20)).unwrap_or_else(|_| "HANG (no answer in 20 s)".into());
        println!("{:<22} {}", std::path::Path::new(&path).file_name().unwrap().to_string_lossy(), res);
    }
}
