//! The desktop's own music controls: media keys, the GNOME and KDE panels, the lock screen.

use souvlaki::{MediaControlEvent, MediaControls, MediaMetadata, MediaPlayback, MediaPosition, PlatformConfig, SeekDirection};
use std::sync::mpsc::{channel, Receiver};
use std::time::Duration;

pub enum Ask {
    Toggle,
    Play,
    Pause,
    Next,
    Prev,
    Seek(f32),
    SeekBy(f32),
    Raise,
    Quit,
}

pub struct Mpris {
    controls: Option<MediaControls>,
    pub rx: Receiver<Ask>,
    last: (String, bool),
}

impl Mpris {
    pub fn start(wake: impl Fn() + Send + Sync + 'static) -> Self {
        let (tx, rx) = channel();
        let cfg = PlatformConfig { display_name: "yrs. music", dbus_name: "yrs_music", hwnd: None };
        // without a session bus there is nobody to talk to (and the library would panic trying)
        let runtime_bus = std::env::var_os("XDG_RUNTIME_DIR").map(|d| std::path::Path::new(&d).join("bus").exists()).unwrap_or(false);
        let has_bus = std::env::var_os("DBUS_SESSION_BUS_ADDRESS").is_some() || runtime_bus;
        let controls = if !has_bus { None } else { MediaControls::new(cfg).ok() }.and_then(|mut c| {
            let ok = c
                .attach(move |e| {
                    let a = match e {
                        MediaControlEvent::Toggle => Ask::Toggle,
                        MediaControlEvent::Play => Ask::Play,
                        MediaControlEvent::Pause | MediaControlEvent::Stop => Ask::Pause,
                        MediaControlEvent::Next => Ask::Next,
                        MediaControlEvent::Previous => Ask::Prev,
                        MediaControlEvent::Seek(d) => Ask::SeekBy(if d == SeekDirection::Forward { 10.0 } else { -10.0 }),
                        MediaControlEvent::SeekBy(d, by) => Ask::SeekBy(by.as_secs_f32() * if d == SeekDirection::Forward { 1.0 } else { -1.0 }),
                        MediaControlEvent::SetPosition(MediaPosition(p)) => Ask::Seek(p.as_secs_f32()),
                        MediaControlEvent::Raise => Ask::Raise,
                        MediaControlEvent::Quit => Ask::Quit,
                        _ => return,
                    };
                    let _ = tx.send(a);
                    wake();
                })
                .is_ok();
            ok.then_some(c)
        });
        Mpris { controls, rx, last: (String::new(), false) }
    }

    pub fn song(&mut self, title: &str, artist: &str, album: &str, secs: f32, cover: Option<String>) {
        let key = format!("{title}\u{1}{artist}\u{1}{album}");
        if self.last.0 == key { return; }
        self.last.0 = key;
        if let Some(c) = &mut self.controls {
            let _ = c.set_metadata(MediaMetadata { title: Some(title), artist: Some(artist), album: Some(album), cover_url: cover.as_deref(), duration: Some(Duration::from_secs_f32(secs.max(0.0))) });
        }
    }

    pub fn status(&mut self, playing: bool, pos: f32, force: bool) {
        if self.last.1 == playing && !force { return; }
        self.last.1 = playing;
        if let Some(c) = &mut self.controls {
            let progress = Some(MediaPosition(Duration::from_secs_f32(pos.max(0.0))));
            let _ = c.set_playback(if playing { MediaPlayback::Playing { progress } } else { MediaPlayback::Paused { progress } });
        }
    }

    pub fn stopped(&mut self) {
        self.last = (String::new(), false);
        if let Some(c) = &mut self.controls { let _ = c.set_playback(MediaPlayback::Stopped); }
    }
}
