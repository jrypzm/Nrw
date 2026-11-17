//! The window's shape decides the layout. Wide: the list and the player side by side
//! (or the player along the bottom). Narrow: the list with a strip below. Smaller still:
//! the mini player, a thin strip, or just the cover. There is no separate mode to switch;
//! resizing the window is the switch.

use crate::app::{App, Side};
use crate::icons::I;
use crate::library::Size;
use crate::pages::{BAR, TOP};
use crate::store::Repeat;
use crate::theme::*;
use egui::{pos2, vec2, Color32, CornerRadius, CursorIcon, Id, Rect, Sense, Ui, UiBuilder};

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Shape {
    Wide,
    Narrow,
    Mini,
    Strip,
    Tiny,
}

pub fn shape_of(w: f32, h: f32) -> Shape {
    if w < 300.0 || (h < 300.0 && w < 420.0) { return Shape::Tiny; }
    if h < 300.0 || (h < 430.0 && w >= 560.0) { return Shape::Strip; }
    if w < 470.0 && h < 640.0 { return Shape::Mini; }
    if w < 720.0 { return Shape::Narrow; }
    Shape::Wide
}

pub const STRIP_H: f32 = 84.0;

impl App {
    pub fn draw_main(&mut self, ui: &mut Ui, full: Rect) {
        let shape = shape_of(full.width(), full.height());
        if shape != self.shape {
            // the full player only belongs to the wide window with the bar below
            if shape != Shape::Wide && shape != Shape::Narrow { self.now = false; }
            self.shape = shape;
        }
        match shape {
            Shape::Tiny => self.draw_tiny(ui, full),
            Shape::Strip => self.draw_strip(ui, full),
            Shape::Mini => self.draw_mini(ui, full),
            Shape::Narrow => self.draw_narrow(ui, full),
            Shape::Wide => if self.store.side { self.draw_wide_side(ui, full) } else { self.draw_wide_bottom(ui, full) },
        }
    }

    // ------------------------------------------------------------ wide

    fn draw_wide_side(&mut self, ui: &mut Ui, full: Rect) {
        self.now = false;
        let has = self.player.current().is_some();
        let max = (full.width() * 0.55).max(320.0);
        let sp = if has { self.store.split.clamp(320.0, max) } else { 0.0 };
        let left = Rect::from_min_max(full.min, pos2(full.right() - sp, full.bottom()));
        self.library_area(ui, left, true);
        if !has { return; }
        let right = Rect::from_min_max(pos2(left.right(), full.top()), full.max);
        // the line between them: drag to give either side more room
        let handle = Rect::from_center_size(pos2(left.right(), full.center().y), vec2(9.0, full.height()));
        let resp = ui.interact(handle, Id::new("split"), Sense::drag());
        if resp.hovered() || resp.dragged() { ui.ctx().set_cursor_icon(CursorIcon::ResizeHorizontal); }
        if resp.dragged() {
            if let Some(p) = resp.interact_pointer_pos() { self.store.split = (full.right() - p.x).clamp(320.0, max); self.dirty(); }
        }
        let k = ui.ctx().animate_bool_with_time(Id::new("split-k"), resp.hovered() || resp.dragged(), 0.15);
        ui.painter().vline(left.right(), full.y_range(), egui::Stroke::new(1.0_f32, mix(HAIR, GHOST, k)));
        let inner = Rect::from_min_max(pos2(right.left() + 1.0, right.top()), right.max);
        ui.scope_builder(UiBuilder::new().max_rect(inner), |ui| {
            ui.set_clip_rect(inner);
            self.panel_view(ui, inner, false);
        });
    }

    fn draw_wide_bottom(&mut self, ui: &mut Ui, full: Rect) {
        let ctx = ui.ctx().clone();
        let has = self.player.current().is_some();
        let t = ctx.animate_bool_with_time_and_easing(Id::new("now-open"), self.now && has, 0.48, egui::emath::easing::cubic_in_out);
        let bar_h = if has { BAR } else { 0.0 };
        if t < 1.0 {
            ui.scope_builder(UiBuilder::new().max_rect(full), |ui| {
                if t > 0.0 { ui.multiply_opacity(1.0 - t * 0.7); }
                self.library_area(ui, Rect::from_min_max(full.min, pos2(full.right(), full.bottom() - bar_h)), true);
            });
        }
        if t > 0.0 { self.draw_now(ui, full, bar_h, t); }
        if has { self.draw_bar(ui, Rect::from_min_max(pos2(full.left(), full.bottom() - BAR), full.max), t); }
    }

    // ------------------------------------------------------------ narrow

    fn draw_narrow(&mut self, ui: &mut Ui, full: Rect) {
        let has = self.player.current().is_some();
        if self.now && has {
            // the player takes the window; a chevron brings the list back
            let top = Rect::from_min_max(full.min, pos2(full.right(), full.top() + 48.0));
            self.drag_area(ui, top);
            if self.glyph(ui, Rect::from_center_size(pos2(top.left() + 30.0, top.center().y), vec2(36.0, 36.0)), I::Down, 24.0, MUTE, "narrow-close").clicked() { self.now = false; }
            self.window_buttons(ui, full.right(), top.center().y, true);
            let body = Rect::from_min_max(pos2(full.left(), top.bottom()), full.max);
            self.panel_view(ui, body, true);
            return;
        }
        let strip = if has { STRIP_H } else { 0.0 };
        self.library_area(ui, Rect::from_min_max(full.min, pos2(full.right(), full.bottom() - strip)), false);
        if has { self.compact_bar(ui, Rect::from_min_max(pos2(full.left(), full.bottom() - STRIP_H), full.max)); }
    }

    /// The phone-like strip: cover, names, three symbols; a hairline of time above.
    fn compact_bar(&mut self, ui: &mut Ui, r: Rect) {
        let Some(cur) = self.player.current() else { return };
        let tr = self.lib.tracks[cur].clone();
        let accent = self.accent;
        ui.painter().rect_filled(r, CornerRadius::ZERO, BLACK);
        ui.painter().hline(r.x_range(), r.top(), egui::Stroke::new(1.0_f32, HAIR));
        let p = (self.player.pos / tr.secs.max(0.1)).clamp(0.0, 1.0);
        ui.painter().hline(r.left()..=r.left() + r.width() * p, r.top(), egui::Stroke::new(1.0_f32, accent));
        let cy = r.center().y;
        let art = Rect::from_min_size(pos2(r.left() + 18.0, cy - 24.0), vec2(48.0, 48.0));
        self.cover(ui, &tr.album_key, &tr.album, art, 7.0, Size::S);
        let ctl_w = 150.0;
        let tw = r.width() - 18.0 - 48.0 - 12.0 - ctl_w - 10.0;
        ui.painter().galley(pos2(art.right() + 12.0, cy - 19.0), line(ui, &tr.title, font(F::Medium, 15.0), WHITE, tw, -0.15), WHITE);
        ui.painter().galley(pos2(art.right() + 12.0, cy + 2.0), line(ui, &tr.artist, font(F::Regular, 13.0), MUTE, tw, 0.0), WHITE);
        let open = ui.interact(Rect::from_min_max(art.min, pos2(art.right() + 12.0 + tw, art.bottom())), Id::new("compact-open"), Sense::click());
        if open.hovered() { ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
        if open.clicked() { self.now = true; }
        self.symbols(ui, pos2(r.right() - 14.0 - ctl_w / 2.0, cy), 28.0, 22.0, 48.0, "compact");
    }

    // ------------------------------------------------------------ the player panel

    /// Previous, play or pause, next, centred on `c`.
    pub fn symbols(&mut self, ui: &mut Ui, c: egui::Pos2, big: f32, side: f32, gap: f32, id: &str) {
        if self.glyph(ui, Rect::from_center_size(pos2(c.x - gap, c.y), vec2(side + 14.0, side + 14.0)), I::Prev, side, WHITE, (id, "prev")).clicked() { self.player.prev(&self.lib); }
        let icon = if self.player.playing { I::Pause } else { I::Play };
        if self.glyph(ui, Rect::from_center_size(c, vec2(big + 14.0, big + 14.0)), icon, big, WHITE, (id, "pp")).clicked() { self.player.toggle(&self.lib); }
        if self.glyph(ui, Rect::from_center_size(pos2(c.x + gap, c.y), vec2(side + 14.0, side + 14.0)), I::Next, side, WHITE, (id, "next")).clicked() { self.player.next(&self.lib); }
    }

    /// Shuffle · Lyrics · Up next · Repeat, spread across `w`.
    fn panel_words(&mut self, ui: &mut Ui, x: f32, y: f32, w: f32) {
        let accent = self.accent;
        let rep = match self.player.repeat { Repeat::One => "Repeat one", _ => "Repeat" };
        let items: [(&str, bool, u8); 4] = [
            ("Shuffle", self.player.shuffle, 0),
            ("Lyrics", self.panel == Some(Side::Lyrics), 1),
            ("Up next", self.panel == Some(Side::Next), 2),
            (rep, self.player.repeat != Repeat::Off, 3),
        ];
        let widths: Vec<f32> = items.iter().map(|(t, ..)| line(ui, t, font(F::Semi, 13.0), WHITE, f32::INFINITY, 0.0).size().x).collect();
        let gap = ((w - widths.iter().sum::<f32>()) / 3.0).max(10.0);
        let mut xx = x;
        for (i, (label, on, k)) in items.iter().enumerate() {
            let c = if *on { accent } else { FAINT };
            if self.word(ui, pos2(xx, y), label, font(F::Semi, 13.0), c, if *on { accent } else { MUTE }, ("pw", *k)).clicked() {
                match k {
                    0 => { let s = !self.player.shuffle; self.player.set_shuffle(s); self.dirty(); }
                    1 => self.panel = if self.panel == Some(Side::Lyrics) { None } else { Some(Side::Lyrics) },
                    2 => self.panel = if self.panel == Some(Side::Next) { None } else { Some(Side::Next) },
                    _ => { self.player.cycle_repeat(); self.dirty(); }
                }
            }
            xx += widths[i] + gap;
        }
    }

    fn quality_volume(&mut self, ui: &mut Ui, x: f32, y: f32, w: f32) {
        let Some(cur) = self.player.current() else { return };
        let q = self.lib.tracks[cur].quality();
        let g = meta(ui, &q, DIM, w - 120.0);
        ui.painter().galley(pos2(x, y - g.size().y / 2.0), g, DIM);
        let vr = Rect::from_min_max(pos2(x + w - 72.0, y - 1.0), pos2(x + w, y + 1.0));
        self.volume_line(ui, vr);
        let vi = if self.player.muted || self.player.volume <= 0.001 { I::Mute } else { I::Volume };
        if self.glyph(ui, Rect::from_center_size(pos2(x + w - 90.0, y), vec2(24.0, 24.0)), vi, 16.0, DIM, ("pmute", x as i32)).clicked() { self.player.toggle_mute(); self.dirty(); }
    }

    /// The player in any box: the big cover with everything under it, or, while lyrics
    /// or the queue are open, a small cover at the top and the words in its place.
    pub fn panel_view(&mut self, ui: &mut Ui, r: Rect, _full_window: bool) {
        let Some(cur) = self.player.current() else { return };
        let tr = self.lib.tracks[cur].clone();
        let accent = self.accent;
        let pad = (r.width() * 0.07).clamp(20.0, 40.0);
        let inner_w = r.width() - pad * 2.0;
        let glow = crate::now::glow(ui.ctx());
        if let Some(side) = self.panel {
            let head = Rect::from_min_size(pos2(r.left() + pad, r.top() + 22.0), vec2(64.0, 64.0));
            ui.painter().image(glow.id(), Rect::from_center_size(head.center() + vec2(inner_w * 0.3, 40.0), vec2(inner_w * 1.6, inner_w * 1.2)), Rect::from_min_max(pos2(0.0, 0.0), pos2(1.0, 1.0)), accent.gamma_multiply(0.10));
            self.cover(ui, &tr.album_key, &tr.album, head, 9.0, Size::S);
            ui.painter().galley(pos2(head.right() + 14.0, head.top() + 12.0), line(ui, &tr.title, font(F::Display, 19.0), WHITE, inner_w - 80.0, -0.4), WHITE);
            ui.painter().galley(pos2(head.right() + 14.0, head.top() + 38.0), line(ui, &tr.artist, font(F::Medium, 14.0), MUTE, inner_w - 80.0, 0.0), WHITE);
            let back = ui.interact(head, Id::new("panel-head"), Sense::click());
            if back.hovered() { ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
            if back.clicked() { self.panel = None; }
            let controls_h = 180.0;
            let col = Rect::from_min_max(pos2(r.left() + pad, head.bottom() + 18.0), pos2(r.right() - pad, r.bottom() - controls_h));
            match side { Side::Lyrics => self.draw_lyrics(ui, col, 1.0, accent), Side::Next => self.draw_next(ui, col, 1.0) }
            self.panel_controls(ui, r.left() + pad, r.bottom() - controls_h + 18.0, inner_w);
            return;
        }
        // cover mode: one block, centred in the height
        let block_extra = 300.0;
        let cover = inner_w.min(r.height() - block_extra - 40.0).min(560.0);
        if cover < 150.0 {
            self.compact_panel(ui, r);
            return;
        }
        let y0 = r.top() + ((r.height() - cover - block_extra) / 2.0).max(20.0);
        let cx = r.center().x;
        let art = Rect::from_min_size(pos2(cx - cover / 2.0, y0), vec2(cover, cover));
        ui.painter().image(glow.id(), Rect::from_center_size(art.center(), art.size() * 2.3), Rect::from_min_max(pos2(0.0, 0.0), pos2(1.0, 1.0)), accent.gamma_multiply(0.12));
        ui.painter().add(egui::epaint::Shadow { offset: [0, 26], blur: 60, spread: 0, color: Color32::from_black_alpha(150) }.as_shape(art, CornerRadius::same(14)));
        self.cover(ui, &tr.album_key, &tr.album, art, 14.0, Size::L);
        let x = art.left();
        let w = cover.max(inner_w.min(cover + 40.0));
        let x = x - (w - cover) / 2.0;
        let ty = art.bottom() + 22.0;
        ui.painter().galley(pos2(x, ty), line(ui, &tr.title, font(F::Display, 26.0), WHITE, w - 44.0, -0.8), WHITE);
        let ar = line(ui, &tr.artist, font(F::Medium, 16.0), MUTE, w - 44.0, -0.1);
        let arr = Rect::from_min_size(pos2(x, ty + 36.0), ar.size());
        ui.painter().galley(arr.min, ar, WHITE);
        let go = ui.interact(arr, Id::new("panel-artist"), Sense::click());
        if go.hovered() { ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
        if go.clicked() { self.go(crate::app::View::Album(tr.album_key.clone())); }
        let loved = self.loved(cur);
        if self.glyph(ui, Rect::from_center_size(pos2(x + w - 14.0, ty + 20.0), vec2(34.0, 34.0)), if loved { I::HeartFull } else { I::Heart }, 24.0, if loved { accent } else { MUTE }, "panel-love").clicked() { self.toggle_love(cur); }
        self.panel_controls(ui, x, ty + 80.0, w);
    }

    /// The time line, the symbols, the words, quality and volume: shared by every panel.
    fn panel_controls(&mut self, ui: &mut Ui, x: f32, y: f32, w: f32) {
        let accent = self.accent;
        let Some(cur) = self.player.current() else { return };
        let secs = self.lib.tracks[cur].secs;
        self.timeline(ui, Rect::from_min_max(pos2(x, y), pos2(x + w, y + 2.0)), "panel-time", accent);
        ui.painter().galley(pos2(x, y + 10.0), line(ui, &clock(self.player.pos), font(F::Medium, 12.0), WHITE, 80.0, 0.0), WHITE);
        let g = line(ui, &format!("−{}", clock(secs - self.player.pos)), font(F::Medium, 12.0), DIM, 80.0, 0.0);
        ui.painter().galley(pos2(x + w - g.size().x, y + 10.0), g, WHITE);
        self.symbols(ui, pos2(x + w / 2.0, y + 66.0), 40.0, 28.0, 82.0, "panel");
        self.panel_words(ui, x, y + 112.0, w);
        self.quality_volume(ui, x, y + 152.0, w);
    }

    /// When there is little height: cover, then names with the symbols beside them, then time.
    fn compact_panel(&mut self, ui: &mut Ui, r: Rect) {
        let Some(cur) = self.player.current() else { return };
        let tr = self.lib.tracks[cur].clone();
        let accent = self.accent;
        let side = (r.width() - 36.0).min(r.height() - 100.0).max(60.0);
        let art = Rect::from_min_size(pos2(r.center().x - side / 2.0, r.top() + 6.0), vec2(side, side));
        let glow = crate::now::glow(ui.ctx());
        ui.painter().image(glow.id(), Rect::from_center_size(art.center(), art.size() * 2.2), Rect::from_min_max(pos2(0.0, 0.0), pos2(1.0, 1.0)), accent.gamma_multiply(0.12));
        let drag = ui.interact(art, Id::new("mini-art"), Sense::click_and_drag());
        if drag.drag_started() { ui.ctx().send_viewport_cmd(egui::ViewportCommand::StartDrag); }
        self.cover(ui, &tr.album_key, &tr.album, art, 12.0, Size::L);
        let w = art.width().max(r.width() - 36.0);
        let x = r.center().x - w / 2.0;
        let y = art.bottom() + 16.0;
        let tw = w - 130.0;
        ui.painter().galley(pos2(x, y), line(ui, &tr.title, font(F::Semi, 16.0), WHITE, tw, -0.15), WHITE);
        ui.painter().galley(pos2(x, y + 22.0), line(ui, &tr.artist, font(F::Regular, 13.0), MUTE, tw, 0.0), WHITE);
        self.symbols(ui, pos2(x + w - 52.0, y + 18.0), 28.0, 20.0, 40.0, "mini");
        self.timeline(ui, Rect::from_min_max(pos2(x, y + 56.0), pos2(x + w, y + 58.0)), "mini-time", accent);
    }

    // ------------------------------------------------------------ small windows

    fn nothing_yet(&mut self, ui: &mut Ui, r: Rect) {
        let g = para(ui, "Nothing playing. Make the window bigger to choose music.", font(F::Regular, 14.0), MUTE, (r.width() - 40.0).max(80.0), 20.0);
        ui.painter().galley(r.center() - g.size() / 2.0, g, WHITE);
    }

    pub fn draw_mini(&mut self, ui: &mut Ui, full: Rect) {
        let top = Rect::from_min_max(full.min, pos2(full.right(), full.top() + 44.0));
        self.drag_area(ui, top);
        self.logo(ui, Rect::from_center_size(pos2(full.left() + 28.0, top.center().y), vec2(22.0, 22.0)));
        self.window_buttons(ui, full.right(), top.center().y, false);
        if self.player.current().is_none() { self.nothing_yet(ui, full); return; }
        let body = Rect::from_min_max(pos2(full.left(), top.bottom()), full.max);
        if self.panel.is_some() { self.panel_view(ui, body, true); } else { self.compact_panel(ui, body); }
    }

    fn draw_strip(&mut self, ui: &mut Ui, full: Rect) {
        self.drag_area(ui, full);
        let Some(cur) = self.player.current() else {
            self.logo(ui, Rect::from_center_size(pos2(full.left() + 30.0, full.center().y), vec2(26.0, 26.0)));
            self.window_buttons(ui, full.right(), full.top() + 22.0, false);
            self.nothing_yet(ui, full);
            return;
        };
        let tr = self.lib.tracks[cur].clone();
        let accent = self.accent;
        let side = (full.height() - 28.0).clamp(40.0, 120.0);
        let cy = full.center().y;
        let art = Rect::from_min_size(pos2(full.left() + 14.0, cy - side / 2.0), vec2(side, side));
        self.cover(ui, &tr.album_key, &tr.album, art, 9.0, Size::M);
        let ctl = 150.0;
        let x = art.right() + 16.0;
        let w = (full.right() - 52.0 - ctl - x).max(60.0);
        ui.painter().galley(pos2(x, cy - 26.0), line(ui, &tr.title, font(F::Semi, 15.0), WHITE, w, -0.15), WHITE);
        ui.painter().galley(pos2(x, cy - 5.0), line(ui, &tr.artist, font(F::Regular, 13.0), MUTE, w, 0.0), WHITE);
        self.timeline(ui, Rect::from_min_max(pos2(x, cy + 19.0), pos2(x + w, cy + 21.0)), "strip-time", accent);
        self.symbols(ui, pos2(full.right() - 46.0 - ctl / 2.0, cy), 30.0, 22.0, 46.0, "strip");
        // close and the dots, stacked at the edge
        let bx = full.right() - 22.0;
        if !self.store.system_frame && self.glyph(ui, Rect::from_center_size(pos2(bx, cy - 14.0), vec2(28.0, 26.0)), I::Close, 15.0, DIM, "strip-close").clicked() { ui.ctx().send_viewport_cmd(egui::ViewportCommand::Close); }
        let m = self.glyph(ui, Rect::from_center_size(pos2(bx, cy + 14.0), vec2(28.0, 26.0)), I::More, 15.0, DIM, "strip-more");
        if m.clicked() { self.panel = None; }
    }

    fn draw_tiny(&mut self, ui: &mut Ui, full: Rect) {
        let Some(cur) = self.player.current() else {
            self.drag_area(ui, full);
            self.logo(ui, Rect::from_center_size(full.center(), vec2(44.0, 44.0)));
            return;
        };
        let tr = self.lib.tracks[cur].clone();
        let accent = self.accent;
        let drag = ui.interact(full, Id::new("tiny-drag"), Sense::click_and_drag());
        if drag.drag_started() { ui.ctx().send_viewport_cmd(egui::ViewportCommand::StartDrag); }
        self.cover(ui, &tr.album_key, &tr.album, full, 0.0, Size::M);
        // a soft dark fall over the lower half, so the symbols read on any cover
        let fall = Rect::from_min_max(pos2(full.left(), full.top() + full.height() * 0.35), full.max);
        gradient(ui, fall, Color32::from_black_alpha(0), Color32::from_black_alpha(225));
        let hovered = ui.ctx().input(|i| i.pointer.hover_pos()).map(|p| full.contains(p)).unwrap_or(false);
        let k = ui.ctx().animate_bool_with_time(Id::new("tiny-hover"), hovered, 0.2);
        let y = full.bottom() - 26.0;
        ui.painter().galley(pos2(full.left() + 14.0, y - 38.0), line(ui, &tr.title, font(F::Semi, 14.0), WHITE, full.width() - 28.0, -0.1), WHITE);
        self.symbols(ui, pos2(full.center().x, y), 26.0, 20.0, (full.width() * 0.3).min(60.0), "tiny");
        let p = (self.player.pos / tr.secs.max(0.1)).clamp(0.0, 1.0);
        ui.painter().rect_filled(Rect::from_min_size(pos2(full.left(), full.bottom() - 2.0), vec2(full.width() * p, 2.0)), CornerRadius::ZERO, accent);
        if k > 0.01 && !self.store.system_frame {
            if self.glyph(ui, Rect::from_center_size(pos2(full.right() - 18.0, full.top() + 18.0), vec2(28.0, 28.0)), I::Close, 15.0, WHITE.gamma_multiply(k), "tiny-close").clicked() { ui.ctx().send_viewport_cmd(egui::ViewportCommand::Close); }
        }
    }

    /// The library side: the top bar and whichever page is open.
    pub fn library_area(&mut self, ui: &mut Ui, area: Rect, wide: bool) {
        let content = Rect::from_min_max(pos2(area.left(), area.top() + TOP), area.max);
        if self.lib.tracks.is_empty() {
            self.top_bar(ui, Rect::from_min_max(area.min, pos2(area.right(), area.top() + TOP)), wide);
            self.reading(ui, area);
            return;
        }
        self.top_bar(ui, Rect::from_min_max(area.min, pos2(area.right(), area.top() + TOP)), wide);
        ui.scope_builder(UiBuilder::new().max_rect(content), |ui| {
            ui.set_clip_rect(content);
            self.page(ui, content);
        });
    }
}

/// A vertical gradient, top colour to bottom colour.
pub fn gradient(ui: &Ui, r: Rect, top: Color32, bottom: Color32) {
    let mut m = egui::Mesh::default();
    m.colored_vertex(r.left_top(), top);
    m.colored_vertex(r.right_top(), top);
    m.colored_vertex(r.left_bottom(), bottom);
    m.colored_vertex(r.right_bottom(), bottom);
    m.add_triangle(0, 1, 2);
    m.add_triangle(1, 3, 2);
    ui.painter().add(egui::Shape::mesh(m));
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn shapes_follow_the_window() {
        assert_eq!(shape_of(1280.0, 800.0), Shape::Wide);
        assert_eq!(shape_of(900.0, 620.0), Shape::Wide);
        assert_eq!(shape_of(420.0, 760.0), Shape::Narrow);
        assert_eq!(shape_of(340.0, 440.0), Shape::Mini);
        assert_eq!(shape_of(560.0, 104.0), Shape::Strip);
        assert_eq!(shape_of(900.0, 380.0), Shape::Strip);
        assert_eq!(shape_of(220.0, 220.0), Shape::Tiny);
    }
}
