//! The app: state, the window, the keyboard, and the frame loop.

use crate::audio::Audio;
use crate::covers::Covers;
use crate::icons::{Icons, I};
use crate::library::{self, Library, Scan};
use crate::lyrics::{self, Lyrics};
use crate::mpris::{Ask, Mpris};
use crate::player::Player;
use crate::store::{List, Store};
use crate::theme::*;
use egui::{pos2, vec2, Color32, CursorIcon, Id, Key, Rect, Response, Sense, Ui, ViewportCommand};
use std::path::PathBuf;
use std::sync::mpsc::{channel, Receiver};

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Tab {
    Songs,
    Albums,
    Lists,
}

#[derive(Clone, PartialEq)]
pub enum View {
    Home,
    Album(String),
    /// None is Loved.
    List(Option<usize>),
    Settings,
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Side {
    Lyrics,
    Next,
}

pub enum Dialog {
    NewList { name: String, add: Vec<usize> },
    Rename { i: usize, name: String },
    Delete { i: usize },
}

pub struct App {
    pub lib: Library,
    pub scanning: Option<(usize, usize)>,
    scan_rx: Receiver<Scan>,
    pub player: Player,
    pub store: Store,
    pub icons: Icons,
    pub covers: Covers,
    mpris: Mpris,
    pub tab: Tab,
    pub view: View,
    pub history: Vec<View>,
    pub search: String,
    pub focus_search: bool,
    pub now: bool,
    pub side: Side,
    /// What the player beside the list shows instead of the cover.
    pub panel: Option<Side>,
    /// Which shape the window has right now.
    pub shape: crate::layout::Shape,
    pub selected: Option<usize>,
    pub toast: Option<(String, f64)>,
    pub lyrics: Option<(PathBuf, Option<Lyrics>)>,
    pub accent: Color32,
    pub dialog: Option<Dialog>,
    pub sorted: (u64, (u8, bool), Vec<usize>),
    pub found: (String, u64, Vec<usize>, Vec<usize>),
    pub generation: u64,
    folder_rx: Option<Receiver<Option<PathBuf>>>,
    open_args: Vec<PathBuf>,
    restored: bool,
    save_at: Option<f64>,
    last_track: Option<usize>,
    pub drag_from: Option<usize>,
    last_size: Option<[f32; 2]>,
    pub lyric_scroll: f32,
}

impl App {
    pub fn new(cc: &eframe::CreationContext, args: Vec<PathBuf>) -> Self {
        install_fonts(&cc.egui_ctx);
        install_style(&cc.egui_ctx);
        let ctx = cc.egui_ctx.clone();
        let wake = move || ctx.request_repaint();
        let store = Store::load();
        let mut player = Player::new(Audio::start(wake.clone()));
        player.shuffle = store.shuffle;
        player.repeat = store.repeat;
        player.muted = store.muted;
        player.set_volume(store.volume);
        let (tx, scan_rx) = channel();
        let folders = store.folders.clone();
        let scanning = if folders.is_empty() { None } else { library::scan(folders, tx, wake.clone()); Some((0, 0)) };
        // keep the receiver alive even with nothing to scan yet
        let mpris = Mpris::start(wake);
        App {
            lib: Library::default(),
            scanning,
            scan_rx,
            player,
            store,
            icons: Icons::default(),
            covers: Covers::new(&cc.egui_ctx),
            mpris,
            tab: Tab::Songs,
            view: View::Home,
            history: vec![],
            search: String::new(),
            focus_search: false,
            now: false,
            side: Side::Lyrics,
            panel: None,
            shape: crate::layout::Shape::Wide,
            selected: None,
            toast: None,
            lyrics: None,
            accent: ACCENT,
            dialog: None,
            sorted: (u64::MAX, (0, true), vec![]),
            found: (String::new(), u64::MAX, vec![], vec![]),
            generation: 0,
            folder_rx: None,
            open_args: args,
            restored: false,
            save_at: None,
            last_track: None,
            drag_from: None,
            last_size: None,
            lyric_scroll: 0.0,
        }
    }

    // ------------------------------------------------------------ library

    pub fn rescan(&mut self, ctx: &egui::Context) {
        if self.store.folders.is_empty() { return; }
        let (tx, rx) = channel();
        self.scan_rx = rx;
        let c = ctx.clone();
        library::scan(self.store.folders.clone(), tx, move || c.request_repaint());
        self.scanning = Some((0, 0));
    }

    pub fn add_folder(&mut self, ctx: &egui::Context, p: PathBuf) {
        if !self.store.folders.iter().any(|f| p.starts_with(f)) {
            self.store.folders.retain(|f| !f.starts_with(&p));
            self.store.folders.push(p);
            self.store.welcomed = true;
            self.dirty();
            self.rescan(ctx);
        }
    }

    pub fn pick_folder(&mut self) {
        let (tx, rx) = channel();
        std::thread::spawn(move || {
            let r = rfd::FileDialog::new().set_title("Choose a music folder").set_directory(library::home()).pick_folder();
            let _ = tx.send(r);
        });
        self.folder_rx = Some(rx);
    }

    fn take_scan(&mut self, ctx: &egui::Context) {
        while let Ok(m) = self.scan_rx.try_recv() {
            match m {
                Scan::Progress(a, b) => self.scanning = Some((a, b)),
                Scan::Covers => { self.covers.reset(); ctx.request_repaint(); }
                Scan::Done(tracks) => {
                    // indexes change with the rebuild: hold the queue as paths meanwhile
                    let saved = self.player.as_paths(&self.lib);
                    let mut tracks = tracks;
                    for a in std::mem::take(&mut self.open_args) {
                        if a.is_file() && !tracks.iter().any(|t| t.path == a) {
                            if let Some(t) = library::read_one(&a) { tracks.push(t); }
                        }
                    }
                    self.lib = Library::build(tracks);
                    self.generation += 1;
                    self.scanning = None;
                    if !self.restored && self.player.queue.is_empty() {
                        self.restored = true;
                        let q: Vec<usize> = self.store.queue.iter().filter_map(|p| self.lib.by_path.get(p).copied()).collect();
                        if !q.is_empty() && self.store.at < q.len() {
                            self.player.restore(&self.lib, q, self.store.at, self.store.pos);
                            self.player.from = self.store.from.clone();
                        }
                    } else {
                        // something was already playing (perhaps started before this scan
                        // finished): keep it, as the same songs
                        self.restored = true;
                        self.player.remap(&self.lib, saved);
                    }
                    // the song didn't change, only its number did
                    self.last_track = self.player.current();
                    ctx.request_repaint();
                }
            }
        }
    }

    // ------------------------------------------------------------ helpers

    pub fn dirty(&mut self) {
        self.save_at.get_or_insert(0.0);
    }

    pub fn say(&mut self, ctx: &egui::Context, s: impl Into<String>) {
        self.toast = Some((s.into(), ctx.input(|i| i.time)));
    }

    pub fn go(&mut self, v: View) {
        if self.view != v {
            self.history.push(std::mem::replace(&mut self.view, v));
        }
        self.now = false;
        self.search.clear();
    }

    pub fn back(&mut self) {
        if let Some(v) = self.history.pop() { self.view = v } else { self.view = View::Home }
    }

    pub fn loved(&self, t: usize) -> bool {
        self.store.loved.contains(&self.lib.tracks[t].path)
    }

    pub fn toggle_love(&mut self, t: usize) {
        let p = self.lib.tracks[t].path.clone();
        if let Some(i) = self.store.loved.iter().position(|x| *x == p) { self.store.loved.remove(i); } else { self.store.loved.insert(0, p); }
        self.dirty();
    }

    pub fn list_songs(&self, which: Option<usize>) -> Vec<usize> {
        let paths: &[PathBuf] = match which { None => &self.store.loved, Some(i) => self.store.lists.get(i).map(|l| l.songs.as_slice()).unwrap_or(&[]) };
        paths.iter().filter_map(|p| self.lib.by_path.get(p).copied()).collect()
    }

    pub fn add_to_list(&mut self, ctx: &egui::Context, i: usize, songs: &[usize]) {
        let paths: Vec<PathBuf> = songs.iter().map(|&t| self.lib.tracks[t].path.clone()).collect();
        if let Some(l) = self.store.lists.get_mut(i) {
            let mut n = 0;
            for p in paths { if !l.songs.contains(&p) { l.songs.push(p); n += 1; } }
            let name = l.name.clone();
            self.dirty();
            self.say(ctx, if n == 0 { format!("Already in {name}") } else { format!("Added to {name}") });
        }
    }

    pub fn new_list(&mut self, name: &str, songs: &[usize]) -> Option<usize> {
        let name = name.trim();
        if name.is_empty() || self.store.lists.iter().any(|l| l.name.eq_ignore_ascii_case(name)) || name.eq_ignore_ascii_case("loved") { return None; }
        self.store.lists.push(List { name: name.to_string(), songs: songs.iter().map(|&t| self.lib.tracks[t].path.clone()).collect() });
        self.dirty();
        Some(self.store.lists.len() - 1)
    }

    /// Plain text that reacts to the pointer like a button.
    pub fn word(&mut self, ui: &mut Ui, at: egui::Pos2, text: &str, f: egui::FontId, color: Color32, hover: Color32, id: impl std::hash::Hash) -> Response {
        let g = line(ui, text, f.clone(), color, f32::INFINITY, 0.0);
        let rect = Rect::from_min_size(at, g.size()).expand2(vec2(4.0, 6.0));
        let r = ui.interact(rect, Id::new(id), Sense::click());
        let c = if r.hovered() { hover } else { color };
        let g = line(ui, text, f, c, f32::INFINITY, 0.0);
        ui.painter().galley(at, g, c);
        if r.hovered() { ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
        r
    }

    /// An icon that reacts to the pointer.
    pub fn glyph(&mut self, ui: &mut Ui, rect: Rect, i: I, size: f32, color: Color32, id: impl std::hash::Hash) -> Response {
        let r = ui.interact(rect, Id::new(id), Sense::click());
        let c = if r.hovered() && color != WHITE { mix(color, WHITE, 0.6) } else { color };
        let shrink = if r.is_pointer_button_down_on() { 0.9 } else { 1.0 };
        self.icons.draw(ui, i, rect, size * shrink, c);
        if r.hovered() { ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
        r
    }

    /// The cover, or a quiet tile with one letter while there is none.
    pub fn cover(&mut self, ui: &Ui, key: &str, title: &str, rect: Rect, radius: f32, size: library::Size) {
        let p = ui.painter();
        let r = egui::CornerRadius::same(radius.round() as u8);
        match self.covers.get(key, size) {
            Some(t) => {
                p.add(egui::epaint::RectShape::filled(rect, r, WHITE).with_texture(t.id(), Rect::from_min_max(pos2(0.0, 0.0), pos2(1.0, 1.0))));
            }
            None => {
                p.rect_filled(rect, r, TILE);
                let letter: String = title.chars().find(|c| c.is_alphanumeric()).map(|c| c.to_uppercase().collect()).unwrap_or_else(|| "♪".into());
                let g = line(ui, &letter, font(F::Display, rect.height() * 0.42), Color32::from_rgb(58, 58, 60), f32::INFINITY, 0.0);
                p.galley(rect.center() - g.size() / 2.0, g, WHITE);
            }
        }
    }

    // ------------------------------------------------------------ the window

    /// Our window has no frame, so its edges are drawn as thin grips that sit above
    /// everything else: pressing one resizes the window and never reaches a song below.
    fn window_edges(&mut self, ctx: &egui::Context) {
        if self.store.system_frame { return; }
        let maximized = ctx.input(|i| i.viewport().maximized.unwrap_or(false));
        if maximized { return; }
        let r = ctx.screen_rect();
        let e = 7.0;
        let c = 18.0;
        use egui::ResizeDirection as D;
        let zones = [
            (Rect::from_min_max(r.min, pos2(r.left() + c, r.top() + c)), D::NorthWest, CursorIcon::ResizeNorthWest),
            (Rect::from_min_max(pos2(r.right() - c, r.top()), pos2(r.right(), r.top() + c)), D::NorthEast, CursorIcon::ResizeNorthEast),
            (Rect::from_min_max(pos2(r.left(), r.bottom() - c), pos2(r.left() + c, r.bottom())), D::SouthWest, CursorIcon::ResizeSouthWest),
            (Rect::from_min_max(pos2(r.right() - c, r.bottom() - c), r.max), D::SouthEast, CursorIcon::ResizeSouthEast),
            (Rect::from_min_max(pos2(r.left(), r.top() + c), pos2(r.left() + e, r.bottom() - c)), D::West, CursorIcon::ResizeWest),
            (Rect::from_min_max(pos2(r.right() - e, r.top() + c), pos2(r.right(), r.bottom() - c)), D::East, CursorIcon::ResizeEast),
            (Rect::from_min_max(pos2(r.left() + c, r.top()), pos2(r.right() - c, r.top() + 4.0)), D::North, CursorIcon::ResizeNorth),
            (Rect::from_min_max(pos2(r.left() + c, r.bottom() - e), pos2(r.right() - c, r.bottom())), D::South, CursorIcon::ResizeSouth),
        ];
        for (i, (zone, dir, cursor)) in zones.into_iter().enumerate() {
            egui::Area::new(Id::new(("edge", i))).order(egui::Order::Foreground).fixed_pos(zone.min).show(ctx, |ui| {
                let (_, resp) = ui.allocate_exact_size(zone.size(), Sense::drag());
                if resp.hovered() || resp.dragged() { ctx.set_cursor_icon(cursor); }
                if resp.drag_started() || resp.is_pointer_button_down_on() && ctx.input(|i| i.pointer.primary_pressed()) {
                    ctx.send_viewport_cmd(ViewportCommand::BeginResize(dir));
                }
            });
        }
    }

    /// The title bar: drag to move, double-click to fill the screen.
    pub fn drag_area(&mut self, ui: &mut Ui, rect: Rect) {
        let r = ui.interact(rect, Id::new("titlebar"), Sense::click_and_drag());
        if r.drag_started() { ui.ctx().send_viewport_cmd(ViewportCommand::StartDrag); }
        if r.double_clicked() {
            let m = ui.ctx().input(|i| i.viewport().maximized.unwrap_or(false));
            ui.ctx().send_viewport_cmd(ViewportCommand::Maximized(!m));
        }
    }

    /// Close, fill the screen, tuck away, and the three dots. `full` adds the window
    /// buttons; small layouts keep only close and the dots.
    pub fn window_buttons(&mut self, ui: &mut Ui, right: f32, cy: f32, full: bool) {
        let mut x = right - 10.0;
        let own = !self.store.system_frame;
        let mut slot = |w: f32| { x -= w; Rect::from_center_size(pos2(x + w / 2.0, cy), vec2(w, 32.0)) };
        if own {
            if self.glyph(ui, slot(32.0), I::Close, 17.0, DIM, "w-close").clicked() { ui.ctx().send_viewport_cmd(ViewportCommand::Close); }
            if full {
                let m = ui.ctx().input(|i| i.viewport().maximized.unwrap_or(false));
                if self.glyph(ui, slot(32.0), I::Max, 17.0, DIM, "w-max").clicked() { ui.ctx().send_viewport_cmd(ViewportCommand::Maximized(!m)); }
                if self.glyph(ui, slot(32.0), I::Min, 17.0, DIM, "w-min").clicked() { ui.ctx().send_viewport_cmd(ViewportCommand::Minimized(true)); }
            }
            slot(4.0);
        }
        let r = self.glyph(ui, slot(32.0), I::More, 19.0, DIM, "w-more");
        let popup = Id::new("more-menu");
        if r.clicked() { ui.memory_mut(|m| m.toggle_popup(popup)); }
        egui::popup::popup_below_widget(ui, popup, &r, egui::PopupCloseBehavior::CloseOnClickOutside, |ui| {
            ui.set_min_width(230.0);
            if ui.button("Search").clicked() { self.focus_search = true; ui.memory_mut(|m| m.close_popup()); }
            if ui.button("Look for new music").clicked() { let c = ui.ctx().clone(); self.rescan(&c); ui.memory_mut(|m| m.close_popup()); }
            if ui.button("Settings").clicked() { self.go(View::Settings); ui.memory_mut(|m| m.close_popup()); }
            ui.separator();
            ui.label(meta(ui, "Player", DIM, 200.0));
            let side = self.store.side;
            let accent = self.accent;
            let pick = |ui: &mut Ui, on: bool, text: &str| ui.button(egui::RichText::new(text.to_string()).color(if on { accent } else { WHITE }));
            if pick(ui, side, "On the right").clicked() { self.store.side = true; self.dirty(); ui.memory_mut(|m| m.close_popup()); }
            if pick(ui, !side, "At the bottom").clicked() { self.store.side = false; self.dirty(); ui.memory_mut(|m| m.close_popup()); }
        });
    }

    // ------------------------------------------------------------ keys and the desktop

    fn keys(&mut self, ctx: &egui::Context) {
        let typing = ctx.wants_keyboard_input();
        let ev = ctx.input(|i| i.events.clone());
        let mods = ctx.input(|i| i.modifiers);
        for e in ev {
            let egui::Event::Key { key, pressed: true, .. } = e else { continue };
            if key == Key::Escape {
                if self.dialog.is_some() { self.dialog = None }
                else if !self.search.is_empty() { self.search.clear(); ctx.memory_mut(|m| m.surrender_focus(Id::new("search"))); }
                else if self.now { self.now = false }
                else if self.view != View::Home { self.back() }
                continue;
            }
            if mods.command && key == Key::F { self.focus_search = true; continue; }
            if typing { continue; }
            match key {
                Key::Space => {
                    if self.player.current().is_none() {
                        // nothing queued yet: start what's on screen, or everything
                        let list = if self.sorted.2.is_empty() { (0..self.lib.tracks.len()).collect() } else { self.sorted.2.clone() };
                        let start = self.selected.and_then(|t| list.iter().position(|&x| x == t)).unwrap_or(0);
                        self.player.play_list(&self.lib, list, start, "Songs".into());
                    } else { self.player.toggle(&self.lib) }
                }
                Key::ArrowRight if mods.command => self.player.next(&self.lib),
                Key::ArrowLeft if mods.command => self.player.prev(&self.lib),
                Key::ArrowRight => { let p = self.player.pos + 5.0; self.player.seek(p) }
                Key::ArrowLeft => { let p = self.player.pos - 5.0; self.player.seek(p) }
                Key::ArrowUp => { let v = self.player.volume + 0.05; self.player.set_volume(v); self.dirty() }
                Key::ArrowDown => { let v = self.player.volume - 0.05; self.player.set_volume(v); self.dirty() }
                Key::Slash => self.focus_search = true,
                Key::L => self.toggle_side(Side::Lyrics),
                Key::Q if mods.command => ctx.send_viewport_cmd(ViewportCommand::Close),
                Key::Enter => if let Some(t) = self.selected { self.play_from_view(t) },
                _ => {}
            }
        }
        // a folder dropped on the window joins the library; a song dropped on it plays
        let dropped = ctx.input(|i| i.raw.dropped_files.clone());
        for f in dropped {
            if let Some(p) = f.path {
                if p.is_dir() { self.add_folder(ctx, p) }
                else if let Some(&t) = self.lib.by_path.get(&p) { self.player.play_list(&self.lib, vec![t], 0, String::new()) }
            }
        }
    }

    /// Lyrics or Up next: inside the player beside the list, or in the full player otherwise.
    pub fn toggle_side(&mut self, side: Side) {
        if self.shape == crate::layout::Shape::Wide && self.store.side && !self.now {
            self.panel = if self.panel == Some(side) { None } else { Some(side) };
        } else if self.now && self.side == side {
            self.now = false;
        } else {
            self.now = true;
            self.side = side;
        }
    }

    /// Play `t` within whatever list is on screen.
    pub fn play_from_view(&mut self, t: usize) {
        let (list, from) = if !self.search.is_empty() {
            (self.found.2.clone(), format!("Search: {}", self.search))
        } else {
            match self.view.clone() {
                View::Album(k) => (self.lib.by_album.get(&k).map(|&a| self.lib.albums[a].tracks.clone()).unwrap_or_default(), self.lib.album_of(t).map(|a| a.title.clone()).unwrap_or_default()),
                View::List(w) => (self.list_songs(w), w.and_then(|i| self.store.lists.get(i)).map(|l| l.name.clone()).unwrap_or_else(|| "Loved".into())),
                _ => (self.sorted.2.clone(), "Songs".into()),
            }
        };
        let start = list.iter().position(|&x| x == t).unwrap_or(0);
        self.player.play_list(&self.lib, list, start, from);
    }

    fn desktop(&mut self, ctx: &egui::Context) {
        while let Ok(a) = self.mpris.rx.try_recv() {
            match a {
                Ask::Toggle => self.player.toggle(&self.lib),
                Ask::Play => self.player.play(&self.lib),
                Ask::Pause => self.player.pause(&self.lib),
                Ask::Next => self.player.next(&self.lib),
                Ask::Prev => self.player.prev(&self.lib),
                Ask::Seek(s) => self.player.seek(s),
                Ask::SeekBy(d) => { let p = self.player.pos + d; self.player.seek(p) }
                Ask::Raise => { ctx.send_viewport_cmd(ViewportCommand::Minimized(false)); ctx.send_viewport_cmd(ViewportCommand::Focus) }
                Ask::Quit => ctx.send_viewport_cmd(ViewportCommand::Close),
            }
        }
        match self.player.current() {
            Some(t) => {
                let tr = self.lib.tracks[t].clone();
                let cover = library::cover_path(&tr.album_key, library::Size::L);
                let url = cover.exists().then(|| format!("file://{}", cover.display()));
                self.mpris.song(&tr.title, &tr.artist, &tr.album, tr.secs, url);
                let changed = self.last_track != Some(t);
                self.mpris.status(self.player.playing, self.player.pos, changed);
                if changed {
                    self.last_track = Some(t);
                    self.lyrics = None;
                    self.lyric_scroll = 0.0;
                    self.dirty();
                    ctx.send_viewport_cmd(ViewportCommand::Title(format!("{} · {}", tr.title, tr.artist)));
                }
            }
            None => {
                if self.last_track.is_some() { self.last_track = None; self.mpris.stopped(); ctx.send_viewport_cmd(ViewportCommand::Title("yrs. music".into())); }
            }
        }
    }

    /// Lyrics are looked up once per song, off the main thread's hot path.
    pub fn ensure_lyrics(&mut self) {
        let Some(t) = self.player.current() else { return };
        let p = &self.lib.tracks[t].path;
        let have = self.lyrics.as_ref().map(|(q, l)| q == p && (l.is_some() || !lyrics::saved(p).exists())).unwrap_or(false);
        if have { return; }
        self.lyrics = Some((p.clone(), lyrics::find(p)));
    }

    fn persist(&mut self, ctx: &egui::Context) {
        let now = ctx.input(|i| i.time);
        if let Some(t) = self.save_at {
            if t == 0.0 { self.save_at = Some(now + 1.5); }
            else if now > t { self.save_now(); self.save_at = None; }
            else { ctx.request_repaint_after(std::time::Duration::from_secs_f64(t - now)); }
        }
    }

    pub fn save_now(&mut self) {
        if let Some(sz) = self.last_size { self.store.size = Some(sz); }
        self.store.volume = self.player.volume;
        self.store.muted = self.player.muted;
        self.store.shuffle = self.player.shuffle;
        self.store.repeat = self.player.repeat;
        if self.restored || self.store.folders.is_empty() {
            self.store.queue = self.player.queue.iter().map(|&t| self.lib.tracks[t].path.clone()).collect();
            self.store.at = self.player.at;
            self.store.pos = self.player.pos;
            self.store.from = self.player.from.clone();
        }
        self.store.save();
    }

    fn animate_accent(&mut self, ctx: &egui::Context) {
        let target = match (self.store.cover_colour, self.player.current()) {
            (true, Some(t)) => { let k = self.lib.tracks[t].album_key.clone(); self.covers.accent(&k).unwrap_or(ACCENT) }
            _ => ACCENT,
        };
        if target != self.accent {
            let dt = ctx.input(|i| i.stable_dt).min(0.05);
            let next = mix(self.accent, target, 1.0 - (-dt * 6.0).exp());
            self.accent = if next == self.accent { target } else { next };
            ctx.request_repaint();
        }
    }
}

impl eframe::App for App {
    fn clear_color(&self, _: &egui::Visuals) -> [f32; 4] {
        [0.0, 0.0, 0.0, 1.0]
    }

    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.covers.pump(ctx);
        self.take_scan(ctx);
        if let Some(rx) = &self.folder_rx {
            if let Ok(r) = rx.try_recv() { self.folder_rx = None; if let Some(p) = r { self.add_folder(ctx, p) } }
        }
        self.player.tick(&self.lib);
        if let Some(e) = self.player.error.take() { self.say(ctx, e); }
        self.desktop(ctx);
        self.keys(ctx);
        self.animate_accent(ctx);
        self.window_edges(ctx);

        let maximized = ctx.input(|i| i.viewport().maximized.unwrap_or(false));
        if !maximized {
            let sz = ctx.input(|i| i.screen_rect().size());
            let sz = [sz.x, sz.y];
            if self.last_size != Some(sz) { self.last_size = Some(sz); self.dirty(); }
        }
        let frame = egui::Frame::NONE.fill(BLACK);
        egui::CentralPanel::default().frame(frame).show(ctx, |ui| {
            let full = ui.max_rect();
            if self.lib.tracks.is_empty() && self.scanning.is_none() { self.draw_welcome(ui, full); }
            else { self.draw_main(ui, full); }
            self.draw_toast(ui, full);
        });
        self.draw_dialog(ctx);
        if self.player.playing { ctx.request_repaint_after(std::time::Duration::from_millis(if self.now { 120 } else { 250 })); }
        if self.scanning.is_some() || self.folder_rx.is_some() { ctx.request_repaint_after(std::time::Duration::from_millis(300)); }
        self.persist(ctx);
    }
}

impl Drop for App {
    fn drop(&mut self) {
        self.save_now();
    }
}
