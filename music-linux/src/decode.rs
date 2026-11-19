//! Turns a file into sound, for rodio to play.
//!
//! The decoding is done here rather than by rodio, for three reasons:
//!  - the file is handed to the decoder as a file, so it knows its size; m4a needs that to
//!    open at all, and FLAC needs it to jump to a point in the song;
//!  - Opus (and Opus or Vorbis in WebM) is decoded too, with a pure-Rust Opus decoder;
//!  - nothing a broken or unusual file does can take the music down. A bad packet is skipped,
//!    a file that stops making sense simply ends, and even a panic inside a decoder is caught.

use rodio::source::SeekError;
use rodio::Source;
use std::fs::File;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::Path;
use std::sync::OnceLock;
use std::time::Duration;
use symphonia::core::codecs::audio::{AudioDecoder, AudioDecoderOptions};
use symphonia::core::codecs::registry::CodecRegistry;
use symphonia::core::common::Tier;
use symphonia::core::errors::Error;
use symphonia::core::formats::probe::Hint;
use symphonia::core::formats::{FormatOptions, FormatReader, SeekMode, SeekTo, TrackType};
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::units::{Time, TimeBase};

/// Decode errors in a row after which a file is taken to be broken from there on.
const GIVE_UP_AFTER: u32 = 64;

fn codecs() -> &'static CodecRegistry {
    static REG: OnceLock<CodecRegistry> = OnceLock::new();
    REG.get_or_init(|| {
        let mut r = CodecRegistry::new();
        symphonia::default::register_enabled_codecs(&mut r);
        r.register_audio_decoder_at_tier::<symphonia_opus::OpusDecoder>(Tier::Fallback);
        r
    })
}

pub struct Song {
    path: std::path::PathBuf,
    format: Box<dyn FormatReader>,
    decoder: Box<dyn AudioDecoder>,
    track: u32,
    time_base: Option<TimeBase>,
    /// Decoded samples, interleaved, waiting to be played.
    buf: Vec<f32>,
    at: usize,
    channels: u16,
    rate: u32,
    total: Option<Duration>,
    /// After a seek: drop audio until this timestamp, so the jump lands where asked.
    skip_to: Option<i64>,
    done: bool,
}

fn name_of(path: &Path) -> String {
    path.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default()
}

/// Opens a song and decodes its first sound, so a file that can't play says so now
/// instead of silently doing nothing.
pub fn open(path: &Path) -> Result<Song, String> {
    let name = name_of(path);
    match catch_unwind(AssertUnwindSafe(|| open_inner(path))) {
        Ok(Ok(s)) => Ok(s),
        Ok(Err(e)) => Err(format!("Can't play {name}: {e}")),
        Err(_) => Err(format!("Can't play {name}: the file confused the decoder")),
    }
}

fn open_inner(path: &Path) -> Result<Song, String> {
    let mut song = build(path)?;
    song.refill();
    if song.buf.is_empty() {
        return Err("no sound could be decoded".into());
    }
    Ok(song)
}

/// The reader and decoder for a file, positioned at its start, nothing decoded yet.
fn build(path: &Path) -> Result<Song, String> {
    let file = File::open(path).map_err(|e| e.to_string())?;
    let mss = MediaSourceStream::new(Box::new(file), Default::default());
    let mut hint = Hint::new();
    if let Some(ext) = path.extension().and_then(|e| e.to_str()) {
        hint.with_extension(&ext.to_lowercase());
    }
    let format = symphonia::default::get_probe()
        .probe(&hint, mss, FormatOptions::default(), MetadataOptions::default())
        .map_err(|e| short(&e))?;
    let track = format
        .default_track(TrackType::Audio)
        .or_else(|| format.tracks().iter().find(|t| t.codec_params.as_ref().and_then(|c| c.audio()).is_some()))
        .ok_or("no sound in this file")?;
    let params = track.codec_params.as_ref().and_then(|c| c.audio()).ok_or("no sound in this file")?.clone();
    let decoder = codecs().make_audio_decoder(&params, &AudioDecoderOptions::default()).map_err(|e| short(&e))?;
    let (id, time_base) = (track.id, track.time_base);
    let rate = params.sample_rate.unwrap_or(44_100);
    let total = match (track.num_frames, track.duration, time_base) {
        (Some(n), _, _) if rate > 0 => Some(Duration::from_secs_f64(n as f64 / rate as f64)),
        (_, Some(d), Some(tb)) => tb.calc_time(symphonia::core::units::Timestamp::new(d.get() as i64)).map(|t| Duration::from_secs_f64(t.as_secs_f64().max(0.0))),
        _ => None,
    };
    Ok(Song { path: path.to_path_buf(), format, decoder, track: id, time_base, buf: Vec::new(), at: 0, channels: 2, rate, total, skip_to: None, done: false })
}

fn short(e: &Error) -> String {
    match e {
        Error::Unsupported(_) => "this kind of file isn't supported".into(),
        Error::IoError(_) => "the file ended early or couldn't be read".into(),
        other => other.to_string(),
    }
}

impl Song {
    /// Decodes the next block of sound into `buf`. Never panics; at the end, or when the
    /// file stops making sense, marks the song done.
    fn refill(&mut self) {
        self.buf.clear();
        self.at = 0;
        if self.done {
            return;
        }
        if catch_unwind(AssertUnwindSafe(|| self.refill_inner())).is_err() {
            self.buf.clear();
            self.done = true;
        }
    }

    fn refill_inner(&mut self) {
        let mut errors = 0u32;
        loop {
            let packet = match self.format.next_packet() {
                Ok(Some(p)) => p,
                Ok(None) => { self.done = true; return; }
                Err(Error::ResetRequired) => { self.decoder.reset(); continue; }
                Err(Error::DecodeError(_)) => {
                    errors += 1;
                    if errors > GIVE_UP_AFTER { self.done = true; return; }
                    continue;
                }
                Err(_) => { self.done = true; return; }
            };
            if packet.track_id != self.track {
                continue;
            }
            let pts = packet.pts.get();
            let time_base = self.time_base;
            match self.decoder.decode(&packet) {
                Ok(audio) => {
                    let frames = audio.frames();
                    if frames == 0 {
                        continue;
                    }
                    let spec = audio.spec();
                    let channels = spec.channels().count().max(1);
                    // after a seek, drop what comes before the spot asked for. Timestamps count in
                    // the track's time base (samples for most files, milliseconds in WebM/MKV),
                    // so they are turned into frames before comparing.
                    let mut drop = 0usize;
                    if let Some(t) = self.skip_to {
                        let need = Self::ts_to_frames(time_base, t - pts, spec.rate());
                        if need >= frames as i64 { continue; }
                        if need > 0 { drop = need as usize; }
                        self.skip_to = None;
                    }
                    self.rate = spec.rate();
                    self.channels = channels as u16;
                    audio.copy_to_vec_interleaved(&mut self.buf);
                    if drop > 0 {
                        let n = (drop * channels).min(self.buf.len());
                        self.buf.drain(..n);
                    }
                    if self.buf.is_empty() {
                        continue;
                    }
                    return;
                }
                Err(Error::DecodeError(_)) => {
                    errors += 1;
                    if errors > GIVE_UP_AFTER { self.done = true; return; }
                }
                Err(Error::ResetRequired) => self.decoder.reset(),
                Err(_) => { self.done = true; return; }
            }
        }
    }

    /// A span of timestamps, in frames at `rate`.
    fn ts_to_frames(time_base: Option<TimeBase>, ts: i64, rate: u32) -> i64 {
        match time_base {
            Some(tb) => {
                let secs = ts as f64 * tb.numer.get() as f64 / tb.denom.get() as f64;
                (secs * rate as f64).round() as i64
            }
            None => ts,
        }
    }

    fn seek_inner(&mut self, pos: Duration) -> Result<(), String> {
        let time = Time::try_from_secs_f64(pos.as_secs_f64()).ok_or("bad time")?;
        let seeked = self
            .format
            .seek(SeekMode::Accurate, SeekTo::Time { time, track_id: Some(self.track) })
            .map_err(|e| e.to_string())?;
        let need = seeked.required_ts.get();
        if seeked.actual_ts.get() > need {
            // The reader landed after the spot asked for (WebM in symphonia 0.6.1 does this,
            // and can't be trusted to go back either). Start the file afresh and decode
            // forward to the exact spot instead.
            let fresh = build(&self.path)?;
            self.format = fresh.format;
            self.decoder = fresh.decoder;
        } else {
            self.decoder.reset();
        }
        self.skip_to = Some(need);
        self.done = false;
        self.refill();
        Ok(())
    }
}

impl Iterator for Song {
    type Item = f32;

    fn next(&mut self) -> Option<f32> {
        if self.at >= self.buf.len() {
            self.refill();
            if self.buf.is_empty() {
                return None;
            }
        }
        let s = self.buf[self.at];
        self.at += 1;
        if self.at >= self.buf.len() && !self.done {
            // have the next block ready, so rodio always knows where this one ends
            self.refill();
        }
        Some(if s.is_finite() { s.clamp(-1.0, 1.0) } else { 0.0 })
    }
}

impl Source for Song {
    fn current_frame_len(&self) -> Option<usize> {
        // the sound may change rate or channels from one block to the next;
        // rodio asks again at the end of each block
        let left = self.buf.len().saturating_sub(self.at);
        if left == 0 && self.done { Some(0) } else { Some(left.max(1)) }
    }

    fn channels(&self) -> u16 {
        self.channels
    }

    fn sample_rate(&self) -> u32 {
        self.rate.max(1)
    }

    fn total_duration(&self) -> Option<Duration> {
        self.total
    }

    fn try_seek(&mut self, pos: Duration) -> Result<(), SeekError> {
        match catch_unwind(AssertUnwindSafe(|| self.seek_inner(pos))) {
            Ok(Ok(())) => Ok(()),
            Ok(Err(e)) => Err(SeekError::Other(Box::new(std::io::Error::other(e)))),
            Err(_) => {
                self.buf.clear();
                self.done = true;
                Err(SeekError::NotSupported { underlying_source: "song" })
            }
        }
    }
}

/// A file's length and sample rate, found by reading through its packets without decoding them;
/// for files whose tags the library can't read (WebM, MKV), so they still get listed.
pub fn length(path: &Path) -> Option<(f32, u32)> {
    catch_unwind(AssertUnwindSafe(|| {
        let mut s = build(path).ok()?;
        let rate = s.rate;
        if let Some(t) = s.total { return Some((t.as_secs_f32(), rate)); }
        let tb = s.time_base?;
        let mut end = 0i64;
        while let Ok(Some(p)) = s.format.next_packet() {
            if p.track_id == s.track { end = end.max(p.pts.get() + p.dur.get() as i64); }
        }
        let secs = end as f64 * tb.numer.get() as f64 / tb.denom.get() as f64;
        Some((secs.max(0.0) as f32, rate))
    }))
    .ok()
    .flatten()
}
