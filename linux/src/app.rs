//! The window: a top bar with the page switcher, five pages, and the sheets that open over them.

use crate::life::{self, Grid, Kind};
use crate::store::{self, Est, Settings};
use crate::ui::*;
use crate::{paint, system};
use chrono::{Datelike, NaiveDate};
use egui::{Align2, Color32, Context, CornerRadius, Id, Key, Pos2, Rect, Sense, Stroke, StrokeKind, Ui, Vec2};
use std::collections::{BTreeMap, HashMap};

/// When the page starts rising in after launch, in seconds.
const INTRO_PAGE: f64 = 1.25;
/// The whole opening, in seconds.
const INTRO: f64 = 2.35;

pub const PAGES: [&str; 5] = ["Life", "Decade", "Year", "People", "Time"];

/// What's open over the page.
pub enum Ov {
    Settings { birth_open: bool },
    Milestone { year: i32, name: String, letter: String },
    Line { date: NaiveDate, text: String },
    Person { idx: Option<usize>, name: String, born: i32, per: u32 },
    Event { idx: Option<usize>, name: String, date: NaiveDate },
    Story { i: usize, fact: String, seen: Vec<String> },
    Estimate { e: Est, step: usize, query: String, h1: String, h2: String, h3: String, imperial: bool, onboarding: bool },
    Letter(usize),
    Review,
    About,
}

pub struct App {
    pub s: Settings,
    pub lines: BTreeMap<String, String>,
    saved: Settings,
    dirty_at: Option<f64>,
    pub ov: Option<Ov>,
    ov_at: f64,
    page_at: f64,
    page_from: usize,
    pub ob: crate::onboarding::Ob,
    facts: HashMap<String, String>,
    tex: HashMap<String, (String, egui::TextureHandle)>,
    toast: Option<(String, f64)>,
    /// screenshots: no motion
    pub still: bool,
    started: f64,
    /// the opening: the name in the middle, gliding up into the corner as the page rises in
    intro: bool,
    /// where the dot of the year you're in sat last frame, for the opening to land on
    cur_dot: Option<(Pos2, f32)>,
}

impl App {
    pub fn new(cc: &eframe::CreationContext, line: bool) -> App {
        install_fonts(&cc.egui_ctx);
        let mut st = egui::Style::default();
        st.visuals = egui::Visuals::dark();
        st.visuals.panel_fill = BG;
        st.visuals.window_fill = SHEET;
        st.visuals.extreme_bg_color = SURFACE;
        st.visuals.selection.bg_fill = gray(0x48);
        st.visuals.selection.stroke = Stroke::new(1.0, FG);
        st.visuals.text_cursor.stroke = Stroke::new(2.0, FG);
        st.spacing.item_spacing = Vec2::ZERO;
        st.spacing.scroll = egui::style::ScrollStyle::floating();
        st.spacing.scroll.bar_width = 6.0;
        st.visuals.widgets.inactive.bg_fill = SURFACE3;
        st.visuals.widgets.hovered.bg_fill = gray(0x63);
        st.visuals.widgets.active.bg_fill = gray(0x8e);
        st.animation_time = 0.18;
        cc.egui_ctx.set_style(st);
        let s = store::load();
        let still = std::env::var_os("YRS_STILL").is_some();
        if !still {
            // a second launch leaves a note; this thread spots it and wakes the window
            let ctx = cc.egui_ctx.clone();
            std::thread::spawn(move || loop {
                std::thread::sleep(std::time::Duration::from_millis(400));
                if store::dir().join("raise").exists() { ctx.request_repaint(); }
            });
        }
        let mut app = App {
            saved: s.clone(), lines: store::load_lines(), s, dirty_at: None, ov: None, ov_at: 0.0, page_at: -10.0, page_from: 0,
            ob: Default::default(), facts: HashMap::new(), tex: HashMap::new(), toast: None, still, started: 0.0, intro: !still && !line, cur_dot: None,
        };
        if line && app.s.birth.is_some() { app.open(Ov::Line { date: life::today(), text: app.lines.get(&life::today().to_string()).cloned().unwrap_or_default() }, 0.0); }
        app.moments();
        // for screenshots: YRS_OPEN=settings | about | story:2000 | year:12 | estimate:N | line | person
        if let Ok(o) = std::env::var("YRS_OPEN") {
            let (k, v) = o.split_once(':').unwrap_or((&o, ""));
            let n: i64 = v.parse().unwrap_or(0);
            match k {
                "ob" => app.ob.step = n as usize,
                "settings" => app.open(Ov::Settings { birth_open: v == "birth" }, 0.0),
                "about" => app.open(Ov::About, 0.0),
                "story" => app.open_story(n as usize, 0.0),
                "year" => app.open_year(n as i32, 0.0),
                "line" => app.open(Ov::Line { date: life::today(), text: String::new() }, 0.0),
                "person" => app.open(Ov::Person { idx: Some(0), name: "Mum".into(), born: 1966, per: 4 }, 0.0),
                "estimate" => { crate::sheets::open_estimate(&mut app, false, 0.0); if let Some(Ov::Estimate { step, .. }) = &mut app.ov { *step = n as usize; } }
                _ => {}
            }
        }
        app
    }

    pub fn open(&mut self, o: Ov, now: f64) { self.ov = Some(o); self.ov_at = now; }
    pub fn toast(&mut self, t: &str, now: f64) { self.toast = Some((t.into(), now)); }

    /// A letter you sealed for this age, or the year you just finished.
    fn moments(&mut self) {
        let Some(b) = self.s.birth() else { return };
        let lived = life::age(b) as i32;
        if let Some(i) = self.s.letters.iter().position(|l| !l.opened && l.age <= lived) { self.ov = Some(Ov::Letter(i)); return; }
        let since = (life::today() - life::last_birthday(b, life::today())).num_days();
        if self.s.reviewed_age >= 0 && self.s.reviewed_age < lived && since <= 30 { self.ov = Some(Ov::Review); }
    }

    fn fact(&mut self, key: &str, list: impl FnOnce() -> Vec<String>) -> String {
        if let Some(f) = self.facts.get(key) { return f.clone(); }
        let f = life::pick(&list(), "");
        self.facts.insert(key.into(), f.clone());
        f
    }
    fn next_fact(&mut self, key: &str, list: Vec<String>) { let last = self.facts.get(key).cloned().unwrap_or_default(); self.facts.insert(key.into(), life::pick(&list, &last)); }

    /// An image cached until [key] changes.
    fn texture(&mut self, ctx: &Context, name: &str, key: String, make: impl FnOnce() -> tiny_skia::Pixmap) -> egui::TextureId {
        let now = ctx.input(|i| i.time);
        if let Some((k, t)) = self.tex.get(name) {
            if *k == key { return t.id(); }
            let built = ctx.data(|d| d.get_temp::<f64>(Id::new(("texbuilt", name)))).unwrap_or(-10.0);
            if now - built < 0.3 { ctx.request_repaint_after(std::time::Duration::from_millis(320)); return t.id(); }
        }
        ctx.data_mut(|d| d.insert_temp(Id::new(("texbuilt", name)), now));
        let pm = make();
        let img = egui::ColorImage::from_rgba_premultiplied([pm.width() as usize, pm.height() as usize], pm.data());
        let t = ctx.load_texture(name, img, egui::TextureOptions::LINEAR);
        let id = t.id();
        self.tex.insert(name.into(), (key, t));
        id
    }

    pub fn ov_at_pub(&self) -> f64 { self.ov_at }
    pub fn texture_pub(&mut self, ctx: &Context, name: &str, key: String, make: impl FnOnce() -> tiny_skia::Pixmap) -> egui::TextureId { self.texture(ctx, name, key, make) }
    pub fn fact_pub(&mut self, key: &str, list: &[String]) -> String { let l = list.to_vec(); self.fact(key, || l) }
    pub fn next_fact_pub(&mut self, key: &str, list: Vec<String>) { self.next_fact(key, list) }

    fn go(&mut self, page: usize, now: f64) {
        let page = page.min(PAGES.len() - 1);
        if page != self.s.page { self.page_from = self.s.page; self.s.page = page; self.page_at = now; }
    }
}

impl eframe::App for App {
    fn clear_color(&self, _v: &egui::Visuals) -> [f32; 4] { [0.0, 0.0, 0.0, 0.0] }

    fn update(&mut self, ctx: &Context, _frame: &mut eframe::Frame) {
        let now = ctx.input(|i| i.time);
        if self.started == 0.0 {
            self.started = now.max(1e-6);
            // the page waits for the opening, then rises in
            if self.intro && self.s.birth.is_some() && self.ov.is_none() { self.page_at = self.started + INTRO_PAGE; } else { self.intro = false; }
        }
        if !self.still {
            if let Some(what) = system::take_raise() {
                ctx.send_viewport_cmd(egui::ViewportCommand::Minimized(false));
                ctx.send_viewport_cmd(egui::ViewportCommand::Focus);
                ctx.send_viewport_cmd(egui::ViewportCommand::RequestUserAttention(egui::UserAttentionType::Informational));
                if what == "line" && self.s.birth.is_some() && self.ov.is_none() {
                    let t = life::today();
                    self.open(Ov::Line { date: t, text: self.lines.get(&t.to_string()).cloned().unwrap_or_default() }, now);
                }
            }
        }
        crate::chrome::edges(ctx);
        crate::chrome::background(ctx);
        // remember the screen size for wallpapers
        if let Some(m) = ctx.input(|i| i.viewport().monitor_size) {
            let ppp = ctx.pixels_per_point();
            let px = ((m.x * ppp).round() as u32, (m.y * ppp).round() as u32);
            if px.0 > 300 && px != self.s.screen { self.s.screen = px; }
        }
        let pad = if crate::chrome::maximized(ctx) { 0.0 } else { crate::chrome::SHADOW };
        egui::CentralPanel::default().frame(egui::Frame::NONE.inner_margin(egui::Margin::same(pad as i8))).show(ctx, |ui| {
            crate::chrome::drag_strip(ui, Rect::from_min_size(ui.max_rect().min, Vec2::new(ui.max_rect().width(), 80.0)));
            let full = ui.max_rect();
            if self.s.birth.is_none() { crate::onboarding::show(self, ui, full, now); } else { self.home(ui, full, now); }
        });
        if self.ov.is_some() { crate::sheets::show(self, ctx, now); }
        if self.intro { self.intro_overlay(ctx, now); }
        let ck = if self.intro { ease_out(((now - self.started - 1.9) / 0.5) as f32) } else { 1.0 };
        crate::chrome::controls(ctx, ck);
        self.draw_toast(ctx, now);
        if self.ov.is_none() && self.s.birth.is_some() { self.keys(ctx, now); }
        else if ctx.input(|i| i.key_pressed(Key::Escape)) && self.ov.is_some() { crate::sheets::closing(self); self.ov = None; }

        // save shortly after things stop changing, never mid-drag
        if self.s != self.saved {
            if self.dirty_at.is_none() { self.dirty_at = Some(now); }
            if now - self.dirty_at.unwrap() > 0.4 && !ctx.input(|i| i.pointer.any_down()) {
                let wall_changed = system::wall_key(&self.s) != system::wall_key(&self.saved);
                store::save(&self.s);
                self.saved = self.s.clone(); self.dirty_at = None;
                if wall_changed && (self.s.auto_wall || self.s.auto_lock) {
                    let s = self.s.clone();
                    std::thread::spawn(move || { let _ = system::apply_wallpaper(&s, s.auto_wall, s.auto_lock, s.screen); });
                }
            } else { ctx.request_repaint_after(std::time::Duration::from_millis(450)); }
        }
        // the Live number ticks; otherwise wake every so often for the clock
        if self.s.style == 2 && self.ov.is_none() && self.s.page < 3 { ctx.request_repaint_after(std::time::Duration::from_millis(60)); }
        else { ctx.request_repaint_after(std::time::Duration::from_secs(20)); }
    }

    fn on_exit(&mut self, _gl: Option<&eframe::glow::Context>) {
        store::save(&self.s); store::save_lines(&self.lines);
        if !self.still { let _ = std::fs::remove_file(store::dir().join("window.pid")); }
    }
}

impl App {
    fn keys(&mut self, ctx: &Context, now: f64) {
        if ctx.wants_keyboard_input() { return; }
        let pressed = |k: Key| ctx.input(|i| i.key_pressed(k));
        for (i, k) in [Key::Num1, Key::Num2, Key::Num3, Key::Num4, Key::Num5].into_iter().enumerate() { if pressed(k) { self.go(i, now); } }
        if pressed(Key::ArrowRight) { self.go(self.s.page + 1, now); }
        if pressed(Key::ArrowLeft) && self.s.page > 0 { self.go(self.s.page - 1, now); }
        if self.s.page == 0 && (pressed(Key::Plus) || pressed(Key::Equals)) && self.s.zoom < 4 { self.s.zoom += 1; }
        if self.s.page == 0 && pressed(Key::Minus) && self.s.zoom > 0 { self.s.zoom -= 1; }
        if self.s.page < 3 && pressed(Key::L) { self.s.lived = !self.s.lived; }
        if self.s.page < 3 && pressed(Key::P) { self.s.pct = !self.s.pct; }
        if self.s.page == 0 && pressed(Key::U) && self.s.zoom == 1 && !self.s.pct { self.s.unit = (self.s.unit + 1) % life::MEASURES.len(); }
        if pressed(Key::S) || pressed(Key::Comma) { self.open(Ov::Settings { birth_open: false }, now); }
        if pressed(Key::T) { let t = life::today(); self.open(Ov::Line { date: t, text: self.lines.get(&t.to_string()).cloned().unwrap_or_default() }, now); }
    }

    /// The opening: the name rises in and its dot fills to how much of your life you've lived; the letters
    /// dissolve; the dot flies to the year you're in; and your life opens outward from it in a widening circle.
    fn intro_overlay(&mut self, ctx: &Context, now: f64) {
        let ms = ((now - self.started) * 1000.0) as f32;
        if ms > (INTRO * 1000.0) as f32 { self.intro = false; return; }
        ctx.request_repaint();
        let seg = |a: f32, b: f32| ((ms - a) / (b - a)).clamp(0.0, 1.0);
        let rise = ease_out(seg(0.0, 700.0));
        let fill = ease_in_out(seg(250.0, 1150.0));
        let dissolve = ease_out(seg(1100.0, 1500.0));
        let fly = ease_in_out(seg(1350.0, 1950.0));
        let open = ease_in_out(seg(1780.0, 2350.0));
        let sr = crate::chrome::content(ctx);
        let p = ctx.layer_painter(egui::LayerId::new(egui::Order::Foreground, Id::new("intro"))).with_clip_rect(crate::chrome::content(ctx));
        let b = self.s.birth().unwrap();
        let age = life::age(b);
        let lived = (age / self.s.span as f64).clamp(0.0, 1.0) as f32;
        let year_fill = (age - age.floor()) as f32;
        // the name, centred
        let size = 132.0;
        let g = galley(ctx, "yrs", F::DisplayBold, size, FG, -0.065, f32::INFINITY, None);
        let d = size * 0.24;
        let total = g.size().x + d * 1.15;
        let origin = Pos2::new(sr.center().x - total / 2.0, sr.center().y - g.size().y / 2.0 - 10.0);
        let home = Pos2::new(origin.x + g.size().x + d * 0.15 + d / 2.0, origin.y + g.size().y * 0.80 - d / 2.0);
        let (dest, dest_r) = self.cur_dot.filter(|_| self.s.page == 0 && self.s.zoom == 1).unwrap_or((sr.center(), d / 2.0));
        let arc = (fly * std::f32::consts::PI).sin() * sr.height().min(sr.width()) * 0.08;
        let pos = Pos2::new(home.x + (dest.x - home.x) * fly - arc * 0.6, home.y + (dest.y - home.y) * fly - arc + (1.0 - rise) * 26.0 * (1.0 - fly));
        let r = (d / 2.0) * (1.0 + 0.35 * ease_out(seg(1100.0, 1400.0)) * (1.0 - fly)) + (dest_r - d / 2.0) * fly;
        // black everywhere, except a circle opening from where the dot landed
        let corner = [sr.left_top(), sr.right_top(), sr.left_bottom(), sr.right_bottom()].iter().map(|c| c.distance(pos)).fold(0.0f32, f32::max) + 4.0;
        let hole = if open > 0.0 { r + (corner - r) * open } else { 0.0 };
        if hole <= 0.5 { p.rect_filled(sr, if crate::chrome::maximized(ctx) { 0 } else { crate::chrome::RADIUS }, BG); }
        else if hole < corner { p.circle_stroke(pos, (hole + corner) / 2.0, Stroke::new(corner - hole, BG)); }
        // the letters rise in, then dissolve and drift
        let a = rise * (1.0 - dissolve);
        if a > 0.0 {
            let g = galley(ctx, "yrs", F::DisplayBold, size, alpha(FG, a), -0.065, f32::INFINITY, None);
            p.galley(origin + Vec2::new(-dissolve * 28.0, (1.0 - rise) * 26.0 - dissolve * 6.0), g, FG);
        }
        // a soft glow while it flies
        if fly > 0.0 && fly < 1.0 { p.circle_filled(pos, r * 2.6, alpha(FG, 0.08 * (fly * std::f32::consts::PI).sin())); }
        // the dot fills to your life so far, then, landing, to how far you are through this year
        let f = lived * fill + (year_fill - lived * fill) * fly;
        let fade = 1.0 - ease_out(seg(2150.0, 2350.0));
        if rise > 0.0 { partial_dot(&p, pos, r, f, alpha(FG, rise * fade), alpha(PARTIAL, rise * fade)); }
    }

    fn draw_toast(&mut self, ctx: &Context, now: f64) {
        let Some((t, at)) = self.toast.clone() else { return };
        let age = now - at;
        if age > 2.6 { self.toast = None; return; }
        ctx.request_repaint();
        let k = (ease_out((age / 0.3) as f32) * (1.0 - ease_out(((age - 2.2) / 0.4).max(0.0) as f32))).clamp(0.0, 1.0);
        let p = ctx.layer_painter(egui::LayerId::new(egui::Order::Tooltip, Id::new("toast")));
        let sr = crate::chrome::content(ctx);
        let w = width_of(ctx, &t, F::Medium, 14.0, 0.0) + 36.0;
        let r = Rect::from_center_size(Pos2::new(sr.center().x, sr.bottom() - 52.0 + (1.0 - k) * 14.0), Vec2::new(w, 40.0));
        p.rect_filled(r, CornerRadius::same(20), alpha(SURFACE2, k));
        text(&p, r.center(), Align2::CENTER_CENTER, &t, F::Medium, 14.0, alpha(FG, k), 0.0);
    }

    // ================================================================== the home screen

    fn home(&mut self, ui: &mut Ui, full: Rect, now: f64) {
        // top bar: the name, the pages, settings
        let bar_y = full.top() + 40.0;
        let logo_end = Pos2::new(full.left() + 40.0, bar_y);
        let p = ui.painter().clone();
        text(&p, logo_end, Align2::LEFT_CENTER, "yrs.", F::DisplayBold, 22.0, FG, -0.05);
        let bw = (full.width() - 440.0).clamp(380.0, 540.0);
        let bar = Rect::from_center_size(Pos2::new(full.center().x, bar_y), Vec2::new(bw, 46.0));
        p.rect_filled(bar, CornerRadius::same(23), GLASS);
        p.rect_stroke(bar, CornerRadius::same(23), Stroke::new(1.0, HAIR), StrokeKind::Inside);
        let inner = bar.shrink(4.0);
        let w = inner.width() / PAGES.len() as f32;
        let x = tweenf(ui.ctx(), Id::new("pagebar"), self.s.page as f32, 0.38);
        p.rect_filled(Rect::from_min_size(Pos2::new(inner.left() + w * x, inner.top()), Vec2::new(w, inner.height())), CornerRadius::same(19), SURFACE2);
        for (i, l) in PAGES.iter().enumerate() {
            let cell = Rect::from_min_size(Pos2::new(inner.left() + w * i as f32, inner.top()), Vec2::new(w, inner.height()));
            let r = hit(ui, cell, Id::new(("page", i)));
            let on = i == self.s.page;
            text(&p, cell.center(), Align2::CENTER_CENTER, l, if on { F::Semi } else { F::Medium }, 14.0, if on { FG } else if r.hovered() { gray(0xc7) } else { MUTE }, 0.0);
            if r.clicked() { self.go(i, now); }
        }
        // settings sits just left of the window controls, with a hairline between
        p.line_segment([Pos2::new(full.right() - 136.0, bar_y - 12.0), Pos2::new(full.right() - 136.0, bar_y + 12.0)], Stroke::new(1.0, HAIR2));
        if round(ui, Pos2::new(full.right() - 172.0, bar_y), 20.0, Id::new("more"), dots3) { self.open(Ov::Settings { birth_open: false }, now); }

        // the page slides in from the side it came from
        let content = Rect::from_min_max(Pos2::new(full.left() + 64.0, full.top() + 104.0), Pos2::new(full.right() - 64.0, full.bottom() - 36.0));
        let t = if self.still { 1.0 } else { ((now - self.page_at) / 0.45) as f32 };
        if t < 1.0 { ui.ctx().request_repaint(); }
        let k = ease_out(t);
        let dir = if self.s.page >= self.page_from { 1.0 } else { -1.0 };
        let rect = content.translate(Vec2::new((1.0 - k) * 28.0 * dir, 0.0));
        let mut child = ui.new_child(egui::UiBuilder::new().max_rect(rect).id_salt(("page", self.s.page)));
        child.multiply_opacity(k.max(0.0));
        match self.s.page {
            0 => self.dots_page(&mut child, rect, Kind::Life, now),
            1 => self.dots_page(&mut child, rect, Kind::Decade, now),
            2 => self.dots_page(&mut child, rect, Kind::Year, now),
            3 => self.people_page(&mut child, rect, now),
            _ => self.time_page(&mut child, rect, now),
        }
    }

    /// The left column of a page scrolls on its own if it has to.
    fn column(ui: &mut Ui, r: Rect, salt: &str, f: impl FnOnce(&mut Ui, f32)) {
        let mut c = ui.new_child(egui::UiBuilder::new().max_rect(r).id_salt(salt));
        egui::ScrollArea::vertical().id_salt(salt).auto_shrink([false, false]).show(&mut c, |ui| {
            ui.spacing_mut().item_spacing = Vec2::ZERO;
            let w = r.width() - 8.0;
            ui.set_width(w);
            f(ui, w);
            ui.add_space(24.0);
        });
    }

    fn header(&mut self, ui: &mut Ui, w: f32, g: &Grid, now: f64) {
        let cur_unit = self.s.unit;
        let s = &self.s;
        let hl = life::headline(s, g);
        let caption = match g.kind { Kind::Life => if g.unit == "lifetime" { "All of history".to_string() } else { "Your life".into() }, Kind::Decade => g.context.clone().unwrap_or_default(), Kind::Year => "This year of your life".into() };
        let r = take(ui, w, 22.0);
        cap(ui.painter(), r.min, &caption);
        let target = if s.pct { hl.pct } else { hl.value };
        let key = egui::util::hash((s.unit, s.lived, s.pct, g.kind as u8, g.unit, s.span, s.style));
        let v = rolling(ui.ctx(), Id::new(("num", g.kind as u8)), target, key, self.still);
        let txt = if s.style == 2 { if s.pct { format!("{:.7}", hl.pct) } else { life::live(hl.value) } } else if s.pct { format!("{}", v.floor() as i64) } else { life::whole(v) };
        let max = if s.style == 1 { 230.0 } else if s.style == 2 { 74.0 } else { 176.0 };
        let size = (w / ((txt.len() + if s.pct { 1 } else { 0 }) as f32 * 0.58)).min(max).max(40.0);
        let nr = take(ui, w, size * 1.02);
        let p = ui.painter().clone();
        if s.style == 2 {
            let dot = txt.find('.').unwrap_or(txt.len());
            let a = text(&p, Pos2::new(nr.left() - size * 0.04, nr.center().y), Align2::LEFT_CENTER, &txt[..dot], F::Thin, size, FG, -0.04);
            text(&p, Pos2::new(a.right(), nr.center().y), Align2::LEFT_CENTER, &format!("{}{}", &txt[dot..], if s.pct { "%" } else { "" }), F::Thin, size, DIM, -0.04);
        } else {
            let a = text(&p, Pos2::new(nr.left() - size * 0.05, nr.center().y), Align2::LEFT_CENTER, &txt, F::Thin, size, FG, -0.055);
            if s.pct { text(&p, Pos2::new(a.right() + 4.0, a.top() + size * 0.22), Align2::LEFT_TOP, "%", F::Light, size * 0.4, MUTE, 0.0); }
        }
        // a click on the number counts another way, like on the phone
        let can_cycle = g.kind == Kind::Life && g.unit == "year" && !s.pct;
        if can_cycle && hit(ui, nr.with_max_x(nr.left() + width_of(ui.ctx(), &txt, F::Thin, size, -0.055)), Id::new("numclick")).clicked() { self.s.unit = (self.s.unit + 1) % life::MEASURES.len(); }
        let s = &self.s;
        let word = if g.kind == Kind::Life && !s.lived && !s.pct && hl.value <= 0.0 { "Every year is a gift".to_string() } else if s.pct { hl.word.to_string() } else { format!("{} {}", hl.noun, hl.word) };
        let r = take(ui, w, 44.0);
        text(&p, r.left_center(), Align2::LEFT_CENTER, &word, F::Display, 34.0, FG, -0.03);
        let b = s.birth().unwrap();
        let sub = if g.kind == Kind::Life && life::is_birthday(b) { format!("Happy birthday · you're {} today", life::age(b) as i32) }
            else if g.kind == Kind::Life && g.unit == "year" { format!("{} · {}% through this year", g.sub, ((life::age(b) % 1.0) * 100.0).floor()) }
            else if g.kind == Kind::Year { format!("{} · {}", g.context.clone().unwrap_or_default(), g.sub) } else { g.sub.clone() };
        let r = take(ui, w, 30.0);
        text(&p, r.left_center() + Vec2::new(0.0, 2.0), Align2::LEFT_CENTER, &sub, F::Medium, 16.0, MUTE, -0.01);
        if can_cycle {
            ui.add_space(14.0);
            let r = take(ui, w, 30.0);
            let mut x = r.left();
            for (i, m) in life::MEASURES.iter().enumerate() {
                let on = i == cur_unit % life::MEASURES.len();
                let label = { let mut c = m.many.chars(); c.next().map(|f| f.to_uppercase().collect::<String>() + c.as_str()).unwrap_or_default() };
                let lw = width_of(ui.ctx(), &label, if on { F::Semi } else { F::Medium }, 14.5, 0.0);
                if x + lw > r.right() { break; }
                let cell = Rect::from_min_size(Pos2::new(x, r.top()), Vec2::new(lw, 30.0));
                let resp = hit(ui, cell, Id::new(("unit", i)));
                text(&p, cell.left_center(), Align2::LEFT_CENTER, &label, if on { F::Semi } else { F::Medium }, 14.5, if on { FG } else if resp.hovered() { gray(0xc7) } else { DIM }, 0.0);
                if resp.clicked() { self.s.unit = i; }
                x += lw + 20.0;
            }
        }
        ui.add_space(20.0);
        let r = take(ui, w, 44.0);
        if let Some(i) = segmented(ui, Rect::from_min_size(r.min, Vec2::new(170.0, 44.0)), &["Left", "Lived"], self.s.lived as usize, Id::new("ll")) { self.s.lived = i == 1; }
        let pr = Rect::from_min_size(r.min + Vec2::new(180.0, 0.0), Vec2::new(44.0, 44.0));
        if pill(ui, pr, "%", self.s.pct, Id::new("pct")) { self.s.pct = !self.s.pct; }
        let _ = now;
    }

    /// Life, Decade and Year: the words on the left, the dots on the right.
    fn dots_page(&mut self, ui: &mut Ui, r: Rect, kind: Kind, now: f64) {
        let g = Grid::new(&self.s, kind);
        let lw = (r.width() * 0.38).clamp(340.0, 500.0);
        let left = Rect::from_min_size(r.min, Vec2::new(lw, r.height()));
        let right = Rect::from_min_max(Pos2::new(r.left() + lw + 64.0, r.top()), r.max);
        let mut clicked = None;
        let zoom_h = if kind == Kind::Life { 76.0 } else { 0.0 };
        let gr = Rect::from_min_max(right.min, Pos2::new(right.right(), right.bottom() - zoom_h));
        if let Some(i) = self.grid(ui, gr, &g, now) { clicked = Some(i); }
        if kind == Kind::Life { self.zoom_ruler(ui, Rect::from_min_max(Pos2::new(right.left(), right.bottom() - 56.0), right.max)); }
        let page = format!("left{}", kind as u8);
        let mut actions: Vec<Box<dyn FnOnce(&mut App)>> = vec![];
        {
            let this = &mut *self;
            App::column(ui, left, &page, |ui, w| {
                this.header(ui, w, &g, now);
                match kind {
                    Kind::Life => this.life_foot(ui, w, now),
                    Kind::Decade => this.decade_foot(ui, w),
                    Kind::Year => this.year_foot(ui, w, now, &mut actions),
                }
            });
        }
        for a in actions { a(self); }
        if let Some(i) = clicked {
            if let Some(y) = g.year_of(i) { self.open_year(y, now); }
            else if g.unit == "lifetime" { self.open_story(i, now); }
            else if g.unit == "day" {
                let d = g.day(i);
                if d <= life::today() { self.open(Ov::Line { date: d, text: self.lines.get(&d.to_string()).cloned().unwrap_or_default() }, now); }
            }
        }
    }

    pub fn open_year(&mut self, y: i32, now: f64) {
        let name = self.s.names.get(&y).cloned().unwrap_or_default();
        self.open(Ov::Milestone { year: y, name, letter: String::new() }, now);
        let lines = self.lines.clone();
        let list = life::year_facts(&self.s, y, &lines);
        let lead = list.first().filter(|f| f.starts_with("In ")).cloned().unwrap_or_else(|| life::pick(&list, ""));
        self.facts.insert(format!("year{y}"), lead);
    }
    pub fn open_story(&mut self, i: usize, now: f64) {
        let list = life::life_facts(&self.s, i);
        let f = life::pick(&list, "");
        self.open(Ov::Story { i, fact: f.clone(), seen: vec![f] }, now);
    }

    /// The dots. Hover for a label; click to open. Big grids are drawn once into an image.
    fn grid(&mut self, ui: &mut Ui, area: Rect, g: &Grid, now: f64) -> Option<usize> {
        let rows = g.total.div_ceil(g.cols);
        let cell = (area.width() / g.cols as f32).min(area.height() / rows as f32).min(if g.total <= 12 { 150.0 } else { 90.0 });
        let size = Vec2::new(cell * g.cols as f32, cell * rows as f32);
        // the Life grid lines up with the number; the small Decade and Year grids sit a little above centre
        let y = if g.total <= 12 { (area.height() - size.y) * 0.36 } else { ((area.height() - size.y) / 2.0).min(if g.total > 200 { 0.0 } else { 40.0 }) };
        let o = Pos2::new(area.left() + (area.width() - size.x) / 2.0, area.top() + y.max(0.0));
        let box_ = Rect::from_min_size(o, size);
        let big = g.total > 200;
        let ratio = if big { 0.66 } else if g.cols == 10 { 0.6 } else if g.cols == 5 { 0.46 } else { 0.5 };
        let r0 = cell * ratio / 2.0;
        let done = g.done.floor() as usize;
        let lit = if self.s.sun { gray((110.0 + 145.0 * life::sun_level()) as u8) } else { FG };
        let center = |i: usize| Pos2::new(o.x + (i % g.cols) as f32 * cell + cell / 2.0, o.y + (i / g.cols) as f32 * cell + cell / 2.0);
        let resp = ui.interact(box_, Id::new(("grid", g.kind as u8, g.zoom)), Sense::click());
        let hover = resp.hover_pos().filter(|_| !self.intro).and_then(|pos| {
            let c = ((pos.x - o.x) / cell).floor(); let rr = ((pos.y - o.y) / cell).floor();
            if c < 0.0 || rr < 0.0 || c as usize >= g.cols { return None; }
            let i = rr as usize * g.cols + c as usize;
            (i < g.total).then_some(i)
        });
        let p = ui.painter().clone();
        let entered = if self.still { 10.0 } else { now - self.page_at.max(self.started) };
        if big {
            let ppp = ui.ctx().pixels_per_point();
            let (pw, ph) = ((size.x * ppp).round() as u32, (size.y * ppp).round() as u32);
            let key = format!("{}|{}|{}|{}x{}|{}", g.total, g.cols, done, pw, ph, lit.r());
            let tid = self.texture(ui.ctx(), &format!("grid{}", g.zoom), key, || paint::big_grid(pw, ph, g.total, g.cols, done, ratio, lit.r()));
            let a = if self.still { 1.0 } else { ease_out((entered / 0.5) as f32) };
            p.image(tid, box_, Rect::from_min_max(Pos2::ZERO, Pos2::new(1.0, 1.0)), alpha(Color32::WHITE, a));
        } else {
            let sealed: Vec<i32> = self.s.letters.iter().filter(|l| !l.opened).map(|l| l.age).collect();
            for i in 0..g.total {
                let c = center(i);
                // each dot grows in, one after another
                let t = ((entered as f32 - i as f32 * 0.012) / 0.5).clamp(0.0, 1.0);
                let mut k = 0.35 + 0.65 * ease_out(t);
                let hk = tweenf(ui.ctx(), Id::new(("h", g.kind as u8, i)), if hover == Some(i) { 1.0 } else { 0.0 }, 0.16);
                k *= 1.0 + 0.32 * hk;
                let r = r0 * k;
                let fade = ease_out(t);
                if i < done { p.circle_filled(c, r, alpha(lit, fade)); }
                else if i == done && g.done < g.total as f64 { partial_dot(&p, c, r, (g.done - done as f64) as f32, lit, PARTIAL); }
                else { p.circle_filled(c, r, alpha(lerp_c(EMPTY, SURFACE3, hk), fade)); }
                if let Some(y) = g.year_of(i) { if self.s.names.contains_key(&y) || sealed.contains(&y) { p.circle_stroke(c, r0 * 1.42, Stroke::new(1.5, alpha(FG, 0.5 * fade))); } }
            }
            if entered < 2.0 { ui.ctx().request_repaint(); }
        }
        if g.kind == Kind::Life && g.unit == "year" && done < g.total { self.cur_dot = Some((center(done), r0)); }
        if big && done < g.total {
            // the dot you're in, drawn live and a little larger so it can be found
            let c = center(done);
            partial_dot(&p, c, (r0 * 1.9).max(3.0), (g.done - done as f64) as f32, lit, PARTIAL);
        }
        if let Some(i) = hover {
            ui.ctx().set_cursor_icon(egui::CursorIcon::PointingHand);
            let c = center(i);
            if big { p.circle_filled(c, (r0 * 2.4).max(4.0), FG); }
            let y = g.year_of(i);
            let mut title = g.label(i);
            if i == done && g.done < g.total as f64 { title += " · now"; }
            let name = y.and_then(|y| self.s.names.get(&y).cloned()).or_else(|| y.filter(|y| self.s.letters.iter().any(|l| !l.opened && l.age == *y)).map(|_| "Sealed letter".to_string()))
                .or_else(|| (g.unit == "day").then(|| self.lines.get(&g.day(i).to_string()).cloned()).flatten());
            let hint = if y.is_some() { Some(if self.s.names.contains_key(&y.unwrap()) { "Click to open" } else if i <= done { "Click to name this year" } else { "Click to plan this year" }) }
                else if g.unit == "lifetime" { Some("Click for its story") } else if g.unit == "day" && g.day(i) <= life::today() { Some("Click to write its line") } else { None };
            self.tip(ui, c, r0 * 1.35, name.as_deref(), &title, hint, box_);
        }
        resp.clicked().then_some(hover).flatten()
    }

    fn tip(&self, ui: &Ui, c: Pos2, above: f32, name: Option<&str>, label: &str, hint: Option<&str>, bounds: Rect) {
        let ctx = ui.ctx();
        let p = ctx.layer_painter(egui::LayerId::new(egui::Order::Tooltip, Id::new("tip")));
        let lines: Vec<(String, F, f32, Color32)> = [name.map(|n| (n.to_string(), F::Semi, 14.0, Color32::BLACK)), Some((label.to_string(), if name.is_some() { F::Body } else { F::Semi }, if name.is_some() { 12.5 } else { 13.5 }, if name.is_some() { gray(0x6e) } else { Color32::BLACK })), hint.map(|h| (h.to_string(), F::Body, 12.0, gray(0x6e)))]
            .into_iter().flatten().collect();
        let w = lines.iter().map(|l| width_of(ctx, &l.0, l.1, l.2, 0.0)).fold(0.0f32, f32::max) + 28.0;
        let h = lines.iter().map(|l| l.2 * 1.35).sum::<f32>() + 16.0;
        let mut r = Rect::from_min_size(Pos2::new(c.x - w / 2.0, c.y - above - 10.0 - h), Vec2::new(w, h));
        if r.top() < bounds.top() - 60.0 { r = r.translate(Vec2::new(0.0, h + above * 2.0 + 20.0)); }
        let sr = crate::chrome::content(ctx);
        if r.left() < sr.left() + 8.0 { r = r.translate(Vec2::new(sr.left() + 8.0 - r.left(), 0.0)); }
        if r.right() > sr.right() - 8.0 { r = r.translate(Vec2::new(sr.right() - 8.0 - r.right(), 0.0)); }
        p.rect_filled(r.translate(Vec2::new(0.0, 4.0)).expand(2.0), CornerRadius::same(16), Color32::from_black_alpha(90));
        p.rect_filled(r, CornerRadius::same(14), FG);
        let mut y = r.top() + 8.0;
        for (t, f, s, col) in &lines { text(&p, Pos2::new(r.center().x, y), Align2::CENTER_TOP, t, *f, *s, *col, 0.0); y += s * 1.35; }
    }

    fn zoom_ruler(&mut self, ui: &Ui, r: Rect) {
        let p = ui.painter();
        let n = life::ZOOMS.len();
        let w = (r.width()).min(560.0);
        let r = Rect::from_center_size(r.center(), Vec2::new(w, r.height()));
        let cell = w / n as f32;
        let x = tweenf(ui.ctx(), Id::new("zoompos"), self.s.zoom as f32, 0.4);
        let ty = r.bottom() - 10.0;
        p.line_segment([Pos2::new(r.left() + cell / 2.0, ty), Pos2::new(r.right() - cell / 2.0, ty)], Stroke::new(1.5, HAIR2));
        for i in 0..n {
            let cx = r.left() + cell * (i as f32 + 0.5);
            p.circle_filled(Pos2::new(cx, ty), 2.5, HAIR2);
            let cellr = Rect::from_center_size(Pos2::new(cx, r.top() + 16.0), Vec2::new(cell, 32.0));
            let resp = hit(ui, cellr, Id::new(("zoom", i)));
            let on = i == self.s.zoom;
            text(p, cellr.center(), Align2::CENTER_CENTER, life::ZOOMS[i], if on { F::Semi } else { F::Medium }, 13.5, if on { FG } else if resp.hovered() { gray(0xc7) } else { DIM }, 0.0);
            if resp.clicked() { self.s.zoom = i; }
        }
        p.circle_filled(Pos2::new(r.left() + cell * (x + 0.5), ty), 6.0, FG);
    }

    fn stats(ui: &mut Ui, w: f32, items: &[(&str, String, Option<String>)]) {
        let r = take(ui, w, 58.0);
        let cw = w / items.len() as f32;
        let p = ui.painter();
        for (k, (c, v, rest)) in items.iter().enumerate() {
            let x = r.left() + cw * k as f32;
            cap(p, Pos2::new(x, r.top()), c);
            let a = text(p, Pos2::new(x, r.top() + 24.0), Align2::LEFT_TOP, v, F::Medium, 15.5, FG, -0.01);
            if let Some(rest) = rest {
                let g = galley(ui.ctx(), &format!(" · {rest}"), F::Medium, 15.5, MUTE, -0.01, (x + cw - 12.0 - a.right()).max(10.0), None);
                if g.rows.len() == 1 { p.galley(Pos2::new(a.right(), a.top()), g, MUTE); }
            }
        }
    }

    /// A card that shows one fact; click for another.
    fn fact_card(&mut self, ui: &mut Ui, w: f32, key: &str, caption: &str, list: impl Fn(&App) -> Vec<String>, big: bool) {
        let f = { let l = list(self); self.fact(key, || l) };
        let pad = 22.0;
        let size = if big { 21.0 } else { 17.0 };
        let th = para_height(ui.ctx(), &f, F::Medium, size, w - pad * 2.0, 1.42);
        let r = take(ui, w, th + 92.0);
        let resp = hit(ui, r, Id::new(("fact", key)));
        let h = tweenf(ui.ctx(), Id::new(("facth", key)), if resp.hovered() { 1.0 } else { 0.0 }, 0.2);
        let p = ui.painter().clone();
        p.rect_filled(r, CornerRadius::same(22), lerp_c(CARD, CARD_HI, h));
        p.rect_stroke(r, CornerRadius::same(22), Stroke::new(1.0, lerp_c(HAIR, HAIR2, h)), StrokeKind::Inside);
        cap(&p, r.min + Vec2::new(pad, pad), caption);
        // the new fact rises in
        let at = ui.ctx().data(|d| d.get_temp::<f64>(Id::new(("factat", key)))).unwrap_or(-10.0);
        let k = if self.still { 1.0 } else { ease_out(((ui.ctx().input(|i| i.time) - at) / 0.5) as f32) };
        if k < 1.0 { ui.ctx().request_repaint(); }
        para(&p, r.min + Vec2::new(pad, pad + 26.0 + (1.0 - k) * 10.0), &f, F::Medium, size, alpha(FG, k), w - pad * 2.0, 1.42);
        text(&p, Pos2::new(r.left() + pad, r.bottom() - pad), Align2::LEFT_BOTTOM, "Click for another", F::Body, 12.5, if resp.hovered() { MUTE } else { DIM }, 0.0);
        if resp.clicked() {
            let l = list(self);
            self.next_fact(key, l);
            let t = ui.ctx().input(|i| i.time);
            ui.ctx().data_mut(|d| d.insert_temp(Id::new(("factat", key)), t));
        }
    }

    fn life_foot(&mut self, ui: &mut Ui, w: f32, _now: f64) {
        let b = self.s.birth().unwrap();
        let t = life::today();
        let lived = life::age(b) as i32;
        let next = life::next_birthday(b, t);
        let to = (next - t).num_days();
        let line = self.lines.get(&t.to_string()).cloned();
        ui.add_space(40.0);
        App::stats(ui, w, &[
            ("Today", format!("Day {}", life::group(life::day_of_life(b, t))), line.map(|l| format!("“{l}”"))),
            ("Next birthday", if life::is_birthday(b) { "Today".into() } else { format!("in {} {}", life::group(to), if to == 1 { "day" } else { "days" }) }, Some(format!("turning {}", if life::is_birthday(b) { lived } else { lived + 1 }))),
        ]);
        ui.add_space(26.0);
        let history = self.s.zoom == 0;
        if history { self.fact_card(ui, w, "history", "From history", |a| life::life_facts(&a.s, rand::random::<usize>() % (life::LIFETIMES - 1)), false); }
        else { self.fact_card(ui, w, "you", "Did you know", |a| life::you_facts(&a.s), false); }
    }

    fn decade_foot(&mut self, ui: &mut Ui, w: f32) {
        let b = self.s.birth().unwrap();
        let lived = life::age(b) as i32;
        let st = lived / 10 * 10;
        // whole years left in the decade, the same count as the number above
        let togo = ((st + 10) as f64 - life::age(b)).floor().max(0.0) as i32;
        let named = self.s.names.keys().filter(|y| **y >= st && **y < st + 10).count();
        ui.add_space(40.0);
        App::stats(ui, w, &[
            (if st == 0 { "Your first decade" } else { "This decade" }, format!("{togo} {} to go", if togo == 1 { "year" } else { "years" }), Some(format!("ending at {}", st + 10))),
            ("Named years", if named == 0 { "None yet".into() } else { named.to_string() }, Some("click a year".into())),
        ]);
        ui.add_space(26.0);
        let key = format!("decade{st}");
        if !self.facts.contains_key(&key) {
            let lines = self.lines.clone();
            let lead = (st..st + 10).filter_map(|y| life::year_facts(&self.s, y, &lines).into_iter().find(|f| f.starts_with("In "))).collect::<Vec<_>>();
            let f = life::pick(&lead, "");
            if !f.is_empty() { self.facts.insert(key.clone(), f); }
        }
        let cap_s = if st == 0 { "In your first decade".to_string() } else { format!("In your {st}s") };
        self.fact_card(ui, w, &key, &cap_s, move |a| { let lines = a.lines.clone(); life::year_facts(&a.s, st + (rand::random::<i32>().rem_euclid(10)), &lines) }, false);
    }

    fn year_foot(&mut self, ui: &mut Ui, w: f32, now: f64, actions: &mut Vec<Box<dyn FnOnce(&mut App)>>) {
        let b = self.s.birth().unwrap();
        let t = life::today();
        let line = self.lines.get(&t.to_string()).cloned();
        ui.add_space(36.0);
        // today's line
        let body = line.clone().unwrap_or_else(|| "Write one line about today. Years from now it will be the only thing left of it.".into());
        let size = if line.is_some() { 20.0 } else { 16.5 };
        let th = para_height(ui.ctx(), &body, F::Medium, size, w - 44.0, 1.42);
        let r = take(ui, w, th + 90.0);
        let resp = hit(ui, r, Id::new("todaycard"));
        let p = ui.painter().clone();
        card(&p, r, 22, if resp.hovered() { CARD_HI } else { CARD });
        cap(&p, r.min + Vec2::new(22.0, 22.0), "Today");
        text(&p, Pos2::new(r.right() - 22.0, r.top() + 22.0), Align2::RIGHT_TOP, &format!("Day {}", life::group(life::day_of_life(b, t))), F::Medium, 12.5, MUTE, 0.0);
        para(&p, r.min + Vec2::new(22.0, 48.0), &body, F::Medium, size, if line.is_some() { FG } else { MUTE }, w - 44.0, 1.42);
        text(&p, Pos2::new(r.left() + 22.0, r.bottom() - 20.0), Align2::LEFT_BOTTOM, if line.is_some() { "Click to edit" } else { "Click to write  ·  T" }, F::Body, 12.5, if resp.hovered() { FG } else { DIM }, 0.0);
        if resp.clicked() { let text = line.unwrap_or_default(); actions.push(Box::new(move |a| a.open(Ov::Line { date: t, text }, now))); }
        // a year ago today
        if let Some((n, l)) = (1..=30).find_map(|y| t.with_year(t.year() - y).and_then(|d| self.lines.get(&d.to_string()).map(|l| (y, l.clone())))) {
            ui.add_space(12.0);
            let th = para_height(ui.ctx(), &format!("“{l}”"), F::Body, 16.0, w - 44.0, 1.45);
            let r = take(ui, w, th + 70.0);
            card(&p, r, 22, CARD);
            cap(&p, r.min + Vec2::new(22.0, 22.0), &if n == 1 { "A year ago today".to_string() } else { format!("{n} years ago today") });
            para(&p, r.min + Vec2::new(22.0, 46.0), &format!("“{l}”"), F::Body, 16.0, FG, w - 44.0, 1.45);
        }
        // what's ahead
        ui.add_space(30.0);
        let r = take(ui, w, 26.0);
        cap(&p, r.min, "Ahead");
        let mut up: Vec<(usize, NaiveDate, String)> = self.s.events.iter().enumerate().filter_map(|(i, e)| NaiveDate::parse_from_str(&e.date, "%Y-%m-%d").ok().filter(|d| *d >= t).map(|d| (i, d, e.name.clone()))).collect();
        up.sort_by_key(|x| x.1);
        let rows = up.len() + 1;
        let g = take(ui, w, rows as f32 * 58.0);
        card(&p, g, 20, CARD);
        for (k, (i, d, name)) in up.iter().enumerate() {
            let rr = Rect::from_min_size(Pos2::new(g.left(), g.top() + k as f32 * 58.0), Vec2::new(w, 58.0));
            let resp = row(ui, rr, name, Some(&life::date_str(*d)), Id::new(("ev", *i)), k == 0);
            let days = (*d - t).num_days();
            text(&p, Pos2::new(rr.right() - 20.0, rr.center().y), Align2::RIGHT_CENTER, &if days == 0 { "Today".into() } else { format!("{} {}", life::group(days), if days == 1 { "day" } else { "days" }) }, F::Medium, 15.0, MUTE, 0.0);
            if resp.clicked() { let (i, d, name) = (*i, *d, name.clone()); actions.push(Box::new(move |a| a.open(Ov::Event { idx: Some(i), name, date: d }, now))); }
        }
        let rr = Rect::from_min_size(Pos2::new(g.left(), g.top() + up.len() as f32 * 58.0), Vec2::new(w, 58.0));
        if row(ui, rr, "Add a countdown", None, Id::new("evnew"), up.is_empty()).clicked() { actions.push(Box::new(move |a| a.open(Ov::Event { idx: None, name: String::new(), date: life::add_months(t, 1) }, now))); }
        chevron(&p, Pos2::new(rr.right() - 22.0, rr.center().y), DIM, false);
    }

    // ================================================================== people

    fn people_page(&mut self, ui: &mut Ui, r: Rect, now: f64) {
        let lw = (r.width() * 0.34).clamp(320.0, 440.0);
        let left = Rect::from_min_size(r.min, Vec2::new(lw, r.height()));
        let right = Rect::from_min_max(Pos2::new(r.left() + lw + 64.0, r.top()), r.max);
        let p = ui.painter().clone();
        cap(&p, left.min, "People");
        let g1 = galley(ui.ctx(), "The time you have", F::Display, 46.0, FG, -0.04, f32::INFINITY, None);
        let g2 = galley(ui.ctx(), "left together.", F::Display, 46.0, DIM, -0.04, f32::INFINITY, None);
        p.galley(left.min + Vec2::new(-2.0, 30.0), g1, FG);
        p.galley(left.min + Vec2::new(-2.0, 82.0), g2, DIM);
        let h = para(&p, left.min + Vec2::new(0.0, 160.0), "Most of the time you'll ever spend with your parents, you've already spent. Add the people you love and see how many more times you'll be together, at the rate you see them now.", F::Body, 16.0, MUTE, lw, 1.55);
        if primary(ui, Rect::from_min_size(left.min + Vec2::new(0.0, 160.0 + h + 32.0), Vec2::new(180.0, 50.0)), "Add someone", Id::new("addp")) {
            self.open(Ov::Person { idx: None, name: String::new(), born: 1970, per: 4 }, now);
        }
        if self.s.people.is_empty() {
            text(&p, right.center(), Align2::CENTER_CENTER, "No one here yet.", F::Medium, 16.0, DIM, 0.0);
            return;
        }
        let two = right.width() > 720.0;
        let cw = if two { (right.width() - 16.0) / 2.0 } else { right.width() };
        let people = self.s.people.clone();
        let mut open = None;
        let mut c = ui.new_child(egui::UiBuilder::new().max_rect(right).id_salt("people"));
        egui::ScrollArea::vertical().id_salt("peoplescroll").auto_shrink([false, false]).show(&mut c, |ui| {
            ui.spacing_mut().item_spacing = Vec2::ZERO;
            let top = ui.cursor().top();
            let mut heights = [0.0f32; 2];
            for (i, person) in people.iter().enumerate() {
                let col = if two { if heights[0] <= heights[1] { 0 } else { 1 } } else { 0 };
                let t = life::together(&self.s, person);
                let n = t.visits.min(140) as usize;
                let rows = n.div_ceil(20);
                let dot = (cw - 48.0) / 20.0;
                let h = 196.0 + rows as f32 * dot + if t.visits > 140 { 24.0 } else { 0.0 };
                let rr = Rect::from_min_size(Pos2::new(right.left() + col as f32 * (cw + 16.0), top + heights[col]), Vec2::new(cw, h));
                heights[col] += h + 16.0;
                let resp = hit(ui, rr, Id::new(("person", i)));
                let p = ui.painter();
                card(p, rr, 24, if resp.hovered() { CARD_HI } else { CARD });
                text(p, rr.min + Vec2::new(24.0, 26.0), Align2::LEFT_TOP, &person.name, F::Semi, 17.0, FG, -0.01);
                let often = life::OFTEN.iter().find(|o| o.1 == person.per).map(|o| o.0).unwrap_or("");
                text(p, Pos2::new(rr.right() - 24.0, rr.top() + 28.0), Align2::RIGHT_TOP, often, F::Body, 13.0, MUTE, 0.0);
                let v = rolling(ui.ctx(), Id::new(("pv", i)), t.visits as f64, egui::util::hash((person.per, person.born, self.s.span)), self.still);
                text(p, rr.min + Vec2::new(20.0, 54.0), Align2::LEFT_TOP, &life::whole(v), F::Thin, 72.0, FG, -0.05);
                let unit = if person.per == 365 { if t.visits == 1 { "day" } else { "days" } } else if t.visits == 1 { "visit" } else { "visits" };
                text(p, rr.min + Vec2::new(24.0, 138.0), Align2::LEFT_TOP, &format!("more {unit} together"), F::Semi, 16.0, FG, -0.01);
                text(p, rr.min + Vec2::new(24.0, 162.0), Align2::LEFT_TOP, &format!("About {} summers left together · {} is {}", life::whole(t.years), person.name, t.their_age), F::Body, 13.0, MUTE, 0.0);
                for k in 0..n { p.circle_filled(Pos2::new(rr.left() + 24.0 + (k % 20) as f32 * dot + dot / 2.0, rr.top() + 190.0 + (k / 20) as f32 * dot + dot / 2.0), dot * 0.28, FG); }
                if t.visits > 140 { text(p, Pos2::new(rr.left() + 24.0, rr.bottom() - 22.0), Align2::LEFT_CENTER, &format!("+ {} more", life::group(t.visits - 140)), F::Body, 12.5, MUTE, 0.0); }
                if resp.clicked() { open = Some(i); }
            }
            ui.allocate_space(Vec2::new(right.width(), heights[0].max(heights[1]) + 24.0));
        });
        if let Some(i) = open { let pp = &self.s.people[i]; self.open(Ov::Person { idx: Some(i), name: pp.name.clone(), born: pp.born, per: pp.per }, now); }
    }

    // ================================================================== where your years go

    fn time_page(&mut self, ui: &mut Ui, r: Rect, _now: f64) {
        let lw = (r.width() * 0.38).clamp(340.0, 480.0);
        let left = Rect::from_min_size(r.min, Vec2::new(lw, r.height()));
        let right = Rect::from_min_max(Pos2::new(r.left() + lw + 64.0, r.top()), r.max);
        let sl = life::ledger(&self.s);
        let left_y = life::years_left(&self.s);
        let free = sl.last().map(|x| x.years).unwrap_or(0.0);
        // the dots: one per year left, coloured by where it goes
        let total = (left_y.round() as usize).max(1);
        let mut counts: Vec<usize> = sl.iter().map(|x| (x.years / left_y.max(0.01) * total as f64) as usize).collect();
        let mut rest = total as i64 - counts.iter().sum::<usize>() as i64;
        let mut fr: Vec<(usize, f64)> = sl.iter().enumerate().map(|(i, x)| (i, x.years / left_y.max(0.01) * total as f64 % 1.0)).collect();
        fr.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap());
        for (i, _) in fr { if rest > 0 { counts[i] += 1; rest -= 1; } }
        let colours: Vec<u8> = counts.iter().enumerate().flat_map(|(i, n)| std::iter::repeat_n(sl[i].shade, *n)).collect();
        let rows = colours.len().div_ceil(10).max(1);
        let cell = (right.width() / 10.0).min((right.height() - 20.0) / rows as f32).min(64.0);
        let o = Pos2::new(right.left() + (right.width() - cell * 10.0) / 2.0, right.top() + 10.0);
        let p = ui.painter().clone();
        for (i, sh) in colours.iter().enumerate() {
            let c = Pos2::new(o.x + (i % 10) as f32 * cell + cell / 2.0, o.y + (i / 10) as f32 * cell + cell / 2.0);
            p.circle_filled(c, cell * 0.3, gray(*sh));
        }
        App::column(ui, left, "time", |ui, w| {
            let pr = take(ui, w, 22.0);
            let p = ui.painter().clone();
            cap(&p, pr.min, "Where your years go");
            let v = rolling(ui.ctx(), Id::new("free"), free, 1, self.still);
            let t = life::one(v);
            let size = (w / (t.len() as f32 * 0.58)).min(176.0);
            let nr = take(ui, w, size * 1.02);
            text(&p, Pos2::new(nr.left() - size * 0.05, nr.center().y), Align2::LEFT_CENTER, &t, F::Thin, size, FG, -0.055);
            let r = take(ui, w, 44.0);
            text(&p, r.left_center(), Align2::LEFT_CENTER, "years truly yours", F::Display, 34.0, FG, -0.03);
            let r = take(ui, w, 30.0);
            text(&p, r.left_center(), Align2::LEFT_CENTER, &format!("of the {} you have left", life::whole(left_y)), F::Medium, 16.0, MUTE, -0.01);
            ui.add_space(28.0);
            for (i, x) in sl.iter().enumerate() {
                let r = take(ui, w, 42.0);
                if i > 0 { p.line_segment([r.left_top(), r.right_top()], Stroke::new(1.0, HAIR)); }
                p.circle_filled(Pos2::new(r.left() + 5.0, r.center().y), 5.0, gray(x.shade));
                let mine = i == sl.len() - 1;
                text(&p, Pos2::new(r.left() + 22.0, r.center().y), Align2::LEFT_CENTER, x.label, F::Body, 15.5, if mine { FG } else { MUTE }, 0.0);
                text(&p, Pos2::new(r.right(), r.center().y), Align2::RIGHT_CENTER, &format!("{} yrs", life::one(x.years)), if mine { F::Semi } else { F::Body }, 15.5, FG, 0.0);
            }
            ui.add_space(26.0);
            let knobs: [(&str, f32, f32, f32, &str); 6] = [("Sleep", 4.0, 11.0, 0.25, "h a night"), ("Work", 0.0, 80.0, 1.0, "h a week"), ("Retire at", 40.0, 85.0, 1.0, ""), ("Commute", 0.0, 180.0, 5.0, "min a day"), ("Eating, chores, getting ready", 0.0, 8.0, 0.25, "h a day"), ("Screens, outside work", 0.0, 14.0, 0.25, "h a day")];
            let h = 74.0 + knobs.len() as f32 * 62.0 + 40.0;
            let cr = take(ui, w, h);
            card(&p, cr, 22, CARD);
            cap(&p, cr.min + Vec2::new(22.0, 22.0), "Your days");
            text(&p, cr.min + Vec2::new(22.0, 44.0), Align2::LEFT_TOP, "Move these to match your life. Everything above follows.", F::Body, 13.0, MUTE, 0.0);
            let s = &mut self.s;
            let vals: [&mut f32; 6] = [&mut s.sleep, &mut s.work, &mut s.retire, &mut s.commute, &mut s.chores, &mut s.phone];
            for (k, ((label, min, max, step, unit), v)) in knobs.iter().zip(vals).enumerate() {
                let y = cr.top() + 80.0 + k as f32 * 62.0;
                text(&p, Pos2::new(cr.left() + 22.0, y), Align2::LEFT_TOP, label, F::Body, 13.5, MUTE, 0.0);
                let shown = if *v == v.floor() { format!("{}", *v as i32) } else { format!("{}", ((*v * 100.0).round() / 100.0)) };
                let val = if *label == "Retire at" { format!("age {shown}") } else if *v == 0.0 && *label == "Work" { "Not working".into() } else if *v == 0.0 && *label == "Commute" { "None".into() } else { format!("{shown} {unit}") };
                text(&p, Pos2::new(cr.right() - 22.0, y), Align2::RIGHT_TOP, &val, F::Medium, 13.5, FG, 0.0);
                slider(ui, Rect::from_min_size(Pos2::new(cr.left() + 14.0, y + 20.0), Vec2::new(w - 28.0, 32.0)), v, *min, *max, *step, Id::new(("knob", k)));
            }
            if quiet(ui, Rect::from_min_size(Pos2::new(cr.left(), cr.bottom() - 44.0), Vec2::new(w, 40.0)), "Reset to typical", Id::new("reset")) {
                let d = Settings::default();
                let s = &mut self.s;
                s.sleep = d.sleep; s.work = d.work; s.retire = d.retire; s.commute = d.commute; s.chores = d.chores; s.phone = d.phone;
            }
        });
    }
}

/// Takes the next [h] pixels of a column.
pub fn take(ui: &mut Ui, w: f32, h: f32) -> Rect { ui.allocate_exact_size(Vec2::new(w, h), Sense::hover()).0 }
