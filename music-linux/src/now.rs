//! What's playing: the bar along the bottom, the full view the cover grows into,
//! lyrics and the queue beside it.

use crate::app::{App, Side};
use crate::icons::I;
use crate::library::Size;
use crate::lyrics::Lyrics;
use crate::pages::TOP;
use crate::store::Repeat;
use crate::theme::*;
use egui::{pos2, vec2, Color32, CornerRadius, CursorIcon, Id, Rect, Sense, TextureHandle, Ui, UiBuilder};

fn lerp(a: f32, b: f32, t: f32) -> f32 {
    a + (b - a) * t
}

fn lerp_rect(a: Rect, b: Rect, t: f32) -> Rect {
    Rect::from_min_max(pos2(lerp(a.left(), b.left(), t), lerp(a.top(), b.top(), t)), pos2(lerp(a.right(), b.right(), t), lerp(a.bottom(), b.bottom(), t)))
}

/// A soft round light, made once, tinted with the cover's colour behind the player.
pub fn glow(ctx: &egui::Context) -> TextureHandle {
    let id = Id::new("glow-texture");
    if let Some(t) = ctx.data(|d| d.get_temp::<TextureHandle>(id)) { return t; }
    let n = 256usize;
    let mut px = Vec::with_capacity(n * n);
    for y in 0..n {
        for x in 0..n {
            let dx = (x as f32 + 0.5) / n as f32 * 2.0 - 1.0;
            let dy = (y as f32 + 0.5) / n as f32 * 2.0 - 1.0;
            let d = (dx * dx + dy * dy).sqrt();
            let a = (1.0 - d).clamp(0.0, 1.0).powf(2.2);
            px.push(Color32::from_white_alpha((a * 255.0) as u8));
        }
    }
    let t = ctx.load_texture("glow", egui::ColorImage { size: [n, n], pixels: px }, egui::TextureOptions::LINEAR);
    ctx.data_mut(|d| d.insert_temp(id, t.clone()));
    t
}

impl App {
    /// The thin line of time. It thickens under the pointer; drag or click to move.
    pub fn timeline(&mut self, ui: &mut Ui, r: Rect, id: &str, accent: Color32) {
        let resp = ui.interact(r.expand2(vec2(0.0, 8.0)), Id::new(id), Sense::click_and_drag());
        let dur = self.player.current().map(|t| self.lib.tracks[t].secs).unwrap_or(0.0).max(0.1);
        let held = Id::new((id, "held"));
        let mut p = (self.player.pos / dur).clamp(0.0, 1.0);
        if let Some(pt) = resp.interact_pointer_pos() {
            if resp.dragged() || resp.is_pointer_button_down_on() {
                p = ((pt.x - r.left()) / r.width()).clamp(0.0, 1.0);
                ui.ctx().data_mut(|d| d.insert_temp(held, p));
            }
        }
        if resp.drag_stopped() || resp.clicked() {
            if let Some(v) = ui.ctx().data(|d| d.get_temp::<f32>(held)) { self.player.seek(v * dur); }
            ui.ctx().data_mut(|d| d.remove::<f32>(held));
        } else if let Some(v) = ui.ctx().data(|d| d.get_temp::<f32>(held)) { if resp.dragged() { p = v } }
        let k = ui.ctx().animate_bool_with_time(Id::new((id, "k")), resp.hovered() || resp.dragged(), 0.15);
        let h = lerp(2.0, 5.0, k);
        let line_r = Rect::from_center_size(r.center(), vec2(r.width(), h));
        let cr = CornerRadius::same((h / 2.0).round() as u8);
        ui.painter().rect_filled(line_r, cr, RAIL);
        ui.painter().rect_filled(Rect::from_min_size(line_r.min, vec2((line_r.width() * p).max(h), h)), cr, accent);
        if k > 0.01 { ui.painter().circle_filled(pos2(line_r.left() + line_r.width() * p, line_r.center().y), 6.0 * k, WHITE); }
        if resp.hovered() { ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
        if let Some(h) = resp.hover_pos() {
            if !resp.dragged() {
                let t = ((h.x - r.left()) / r.width()).clamp(0.0, 1.0) * dur;
                resp.on_hover_text_at_pointer(clock(t));
            }
        }
    }

    fn controls(&mut self, ui: &mut Ui, c: egui::Pos2, big: f32, side: f32, gap: f32, id: &str) {
        if self.glyph(ui, Rect::from_center_size(pos2(c.x - gap, c.y), vec2(side + 14.0, side + 14.0)), I::Prev, side, WHITE, (id, "prev")).clicked() { self.player.prev(&self.lib); }
        let icon = if self.player.playing { I::Pause } else { I::Play };
        if self.glyph(ui, Rect::from_center_size(c, vec2(big + 14.0, big + 14.0)), icon, big, WHITE, (id, "pp")).clicked() { self.player.toggle(&self.lib); }
        if self.glyph(ui, Rect::from_center_size(pos2(c.x + gap, c.y), vec2(side + 14.0, side + 14.0)), I::Next, side, WHITE, (id, "next")).clicked() { self.player.next(&self.lib); }
    }

    // ------------------------------------------------------------ the bar

    pub fn draw_bar(&mut self, ui: &mut Ui, r: Rect, t: f32) {
        let Some(cur) = self.player.current() else { return };
        let tr = self.lib.tracks[cur].clone();
        let accent = self.accent;
        let p = ui.painter();
        p.rect_filled(r, CornerRadius::ZERO, BLACK);
        p.hline(r.x_range(), r.top(), egui::Stroke::new(1.0_f32, HAIR.gamma_multiply(1.0 - t)));
        let cy = r.center().y;
        // left: cover, title, heart (the cover is drawn by the player when it is open)
        let art = Rect::from_min_size(pos2(r.left() + 24.0, cy - 26.0), vec2(52.0, 52.0));
        if t <= 0.0 { self.cover(ui, &tr.album_key, &tr.album, art, 7.0, Size::M); }
        let left_alpha = 1.0 - (t * 3.0).min(1.0);
        let mid_w = (r.width() * 0.4).clamp(380.0, 560.0);
        let mid = Rect::from_center_size(pos2(r.center().x, cy), vec2(mid_w, r.height()));
        if left_alpha > 0.0 {
            let tw = mid.left() - art.right() - 70.0;
            let g = line(ui, &tr.title, font(F::Medium, 15.0), WHITE.gamma_multiply(left_alpha), tw, -0.15);
            let title_r = Rect::from_min_size(pos2(art.right() + 14.0, cy - 19.0), g.size());
            ui.painter().galley(title_r.min, g, WHITE);
            let g = line(ui, &tr.artist, font(F::Regular, 13.0), MUTE.gamma_multiply(left_alpha), tw, 0.0);
            ui.painter().galley(pos2(art.right() + 14.0, cy + 3.0), g, WHITE);
            let open = ui.interact(Rect::from_min_max(art.min, pos2(art.right() + 14.0 + tw.min(title_r.width().max(120.0)), art.bottom())), Id::new("bar-open"), Sense::click());
            if open.hovered() { ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
            if open.clicked() { self.now = true; }
            let hx = (art.right() + 14.0 + title_r.width().max(ui.fonts(|f| f.layout_no_wrap(tr.artist.clone(), font(F::Regular, 13.0), WHITE).size().x)).min(tw)) + 22.0;
            let loved = self.loved(cur);
            if self.glyph(ui, Rect::from_center_size(pos2(hx, cy), vec2(30.0, 30.0)), if loved { I::HeartFull } else { I::Heart }, 20.0, if loved { accent } else { FAINT }, "bar-love").clicked() { self.toggle_love(cur); }
        }
        // middle: Shuffle, the three symbols, Repeat; time below
        let cc = pos2(mid.center().x, cy - 12.0);
        self.controls(ui, cc, 30.0, 24.0, 64.0, "bar");
        let sh_on = self.player.shuffle;
        let sh = line(ui, "Shuffle", font(F::Semi, 13.0), WHITE, f32::INFINITY, 0.0);
        if self.word(ui, pos2(cc.x - 132.0 - sh.size().x, cc.y - sh.size().y / 2.0), "Shuffle", font(F::Semi, 13.0), if sh_on { accent } else { FAINT }, if sh_on { accent } else { MUTE }, "bar-shuffle").clicked() { let s = !self.player.shuffle; self.player.set_shuffle(s); self.dirty(); }
        let rep = match self.player.repeat { Repeat::One => "Repeat one", _ => "Repeat" };
        let rep_on = self.player.repeat != Repeat::Off;
        let g = line(ui, rep, font(F::Semi, 13.0), WHITE, f32::INFINITY, 0.0);
        if self.word(ui, pos2(cc.x + 132.0, cc.y - g.size().y / 2.0), rep, font(F::Semi, 13.0), if rep_on { accent } else { FAINT }, if rep_on { accent } else { MUTE }, "bar-repeat").clicked() { self.player.cycle_repeat(); self.dirty(); }
        let ty = cy + 22.0;
        let g = line(ui, &clock(self.player.pos), font(F::Medium, 11.5), WHITE, 60.0, 0.0);
        ui.painter().galley(pos2(mid.left() + 40.0 - g.size().x, ty - g.size().y / 2.0), g, WHITE);
        let g = line(ui, &format!("−{}", clock(tr.secs - self.player.pos)), font(F::Medium, 11.5), DIM, 60.0, 0.0);
        ui.painter().galley(pos2(mid.right() - 40.0, ty - g.size().y / 2.0), g, WHITE);
        self.timeline(ui, Rect::from_min_max(pos2(mid.left() + 50.0, ty - 1.0), pos2(mid.right() - 50.0, ty + 1.0)), "bar-time", accent);
        // right: quality, Lyrics, Up next, volume
        let mut x = r.right() - 24.0;
        let vol_w = 84.0;
        let vr = Rect::from_min_max(pos2(x - vol_w, cy - 1.0), pos2(x, cy + 1.0));
        self.volume_line(ui, vr);
        x -= vol_w + 10.0;
        let vi = if self.player.muted || self.player.volume <= 0.001 { I::Mute } else { I::Volume };
        if self.glyph(ui, Rect::from_center_size(pos2(x - 10.0, cy), vec2(26.0, 26.0)), vi, 18.0, DIM, "bar-mute").clicked() { self.player.toggle_mute(); self.dirty(); }
        x -= 44.0;
        for (side, label) in [(Side::Next, "Up next"), (Side::Lyrics, "Lyrics")] {
            let on = self.now && self.side == side;
            let g = line(ui, label, font(F::Semi, 13.0), WHITE, f32::INFINITY, 0.0);
            x -= g.size().x;
            if x < mid.right() + 10.0 { break; }
            if self.word(ui, pos2(x, cy - g.size().y / 2.0), label, font(F::Semi, 13.0), if on { accent } else { FAINT }, if on { accent } else { MUTE }, ("bar-side", label)).clicked() {
                self.toggle_side(side);
            }
            x -= 22.0;
        }
        let q = tr.quality();
        let g = meta(ui, &q, DIM, 160.0);
        if x - g.size().x > mid.right() + 10.0 { ui.painter().galley(pos2(x - g.size().x, cy - g.size().y / 2.0), g, DIM); }
    }

    pub fn volume_line(&mut self, ui: &mut Ui, r: Rect) {
        let resp = ui.interact(r.expand2(vec2(4.0, 10.0)), Id::new("volume"), Sense::click_and_drag());
        if let Some(p) = resp.interact_pointer_pos() {
            if resp.dragged() || resp.clicked() || resp.is_pointer_button_down_on() {
                self.player.muted = false;
                self.player.set_volume(((p.x - r.left()) / r.width()).clamp(0.0, 1.0));
                self.dirty();
            }
        }
        let scroll = if resp.hovered() { ui.ctx().input(|i| i.smooth_scroll_delta.y) } else { 0.0 };
        if scroll != 0.0 { let v = self.player.volume + scroll / 400.0; self.player.set_volume(v); self.dirty(); }
        let k = ui.ctx().animate_bool_with_time(Id::new("vol-k"), resp.hovered() || resp.dragged(), 0.15);
        let h = lerp(2.0, 4.0, k);
        let lr = Rect::from_center_size(r.center(), vec2(r.width(), h));
        let v = if self.player.muted { 0.0 } else { self.player.volume };
        let cr = CornerRadius::same((h / 2.0).round() as u8);
        ui.painter().rect_filled(lr, cr, RAIL);
        ui.painter().rect_filled(Rect::from_min_size(lr.min, vec2(lr.width() * v, h)), cr, Color32::from_rgb(220, 220, 224));
        if resp.hovered() { ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
    }

    // ------------------------------------------------------------ the player

    pub fn draw_now(&mut self, ui: &mut Ui, full: Rect, bar_h: f32, t: f32) {
        let Some(cur) = self.player.current() else { return };
        let tr = self.lib.tracks[cur].clone();
        let accent = self.accent;
        let area = Rect::from_min_max(full.min, pos2(full.right(), full.bottom() - bar_h));
        let ctx = ui.ctx().clone();
        // the sheet: black, a light from the cover behind
        // the sheet goes dark quickly, so the travelling cover never floats over a busy page
        ui.painter().rect_filled(area, CornerRadius::ZERO, BLACK.gamma_multiply((t * 2.4).min(1.0)));
        let g = glow(&ctx);
        let pad = (area.width() * 0.06).clamp(40.0, 96.0);
        let side = (area.height() - TOP - 140.0).min(area.width() * 0.4).min(560.0).max(200.0);
        let big = Rect::from_min_size(pos2(area.left() + pad, area.top() + TOP + 30.0), vec2(side, side));
        let light = Rect::from_center_size(big.center(), big.size() * 2.4);
        ui.painter().image(g.id(), light, Rect::from_min_max(pos2(0.0, 0.0), pos2(1.0, 1.0)), accent.gamma_multiply(0.13 * t));
        // a click on the empty sheet must not reach the library under it
        let _ = ui.interact(area, Id::new("now-sheet"), Sense::click());
        // the cover travels from the bar to here
        let small = Rect::from_min_size(pos2(full.left() + 24.0, full.bottom() - bar_h / 2.0 - 26.0), vec2(52.0, 52.0));
        let rect = lerp_rect(small, big, t);
        let radius = lerp(7.0, 14.0, t);
        if t > 0.6 {
            // a soft shadow under the big cover
            ui.painter().add(egui::epaint::Shadow { offset: [0, 30], blur: 70, spread: 0, color: Color32::from_black_alpha((150.0 * t) as u8) }.as_shape(rect, CornerRadius::same(radius as u8)));
        }
        let (key, album) = (tr.album_key.clone(), tr.album.clone());
        let size = if t > 0.15 { Size::L } else { Size::M };
        self.cover(ui, &key, &album, rect, radius, size);
        let a = ((t - 0.55) / 0.45).clamp(0.0, 1.0);
        if a <= 0.0 { return; }
        // top: close, where it plays from, the window
        let top = Rect::from_min_max(area.min, pos2(area.right(), area.top() + TOP));
        self.drag_area(ui, top);
        if self.glyph(ui, Rect::from_center_size(pos2(top.left() + 34.0, top.center().y), vec2(36.0, 36.0)), I::Down, 24.0, MUTE.gamma_multiply(a), "now-close").clicked() { self.now = false; }
        if !self.player.from.is_empty() {
            let m = meta(ui, &format!("Playing from {}", self.player.from), DIM.gamma_multiply(a), area.width() * 0.5);
            ui.painter().galley(pos2(top.left() + 62.0, top.center().y - m.size().y / 2.0), m, WHITE);
        }
        self.window_buttons(ui, area.right(), top.center().y, true);
        // under the cover: title, artist, the heart
        let ty = big.bottom() + 26.0;
        let g = line(ui, &tr.title, font(F::Display, 30.0), WHITE.gamma_multiply(a), side - 50.0, -0.9);
        ui.painter().galley(pos2(big.left(), ty), g, WHITE);
        let g = line(ui, &tr.artist, font(F::Medium, 18.0), MUTE.gamma_multiply(a), side - 50.0, -0.1);
        let ar = Rect::from_min_size(pos2(big.left(), ty + 42.0), g.size());
        ui.painter().galley(ar.min, g, WHITE);
        let artist_click = ui.interact(ar, Id::new("now-album"), Sense::click());
        if artist_click.hovered() { ctx.set_cursor_icon(CursorIcon::PointingHand); }
        if artist_click.clicked() { self.go(crate::app::View::Album(tr.album_key.clone())); }
        let loved = self.loved(cur);
        if self.glyph(ui, Rect::from_center_size(pos2(big.right() - 14.0, ty + 22.0), vec2(36.0, 36.0)), if loved { I::HeartFull } else { I::Heart }, 26.0, if loved { accent } else { MUTE }.gamma_multiply(a), "now-love").clicked() { self.toggle_love(cur); }
        // beside it: lyrics, or what plays next
        let col = Rect::from_min_max(pos2(big.right() + pad.max(64.0), area.top() + TOP + 10.0), pos2(area.right() - pad, area.bottom() - 20.0));
        if col.width() < 200.0 { return; }
        match self.side {
            Side::Lyrics => self.draw_lyrics(ui, col, a, accent),
            Side::Next => self.draw_next(ui, col, a),
        }
    }

    pub fn draw_lyrics(&mut self, ui: &mut Ui, col: Rect, a: f32, accent: Color32) {
        self.ensure_lyrics();
        let pos = self.player.pos;
        let size = if col.width() > 560.0 { 34.0 } else { 28.0 };
        let gap = size * 0.62;
        let lyrics = self.lyrics.as_ref().and_then(|(_, l)| l.as_ref());
        match lyrics {
            None => {
                let g = line(ui, "No lyrics", font(F::Display, 30.0), WHITE.gamma_multiply(a), col.width(), -0.9);
                ui.painter().galley(pos2(col.left(), col.top() + 60.0), g, WHITE);
                let g = para(ui, "Put a .lrc file beside the song with the same name, or choose one. Lyrics saved inside the song show up on their own.", font(F::Regular, 16.0), MUTE.gamma_multiply(a), col.width().min(460.0), 24.0);
                let h = g.size().y;
                ui.painter().galley(pos2(col.left(), col.top() + 108.0), g, WHITE);
                if self.word(ui, pos2(col.left(), col.top() + 128.0 + h), "Choose a file", font(F::Semi, 15.0), accent.gamma_multiply(a), WHITE, "lyr-pick").clicked() { self.pick_lyrics(); }
            }
            Some(Lyrics::Plain(lines)) => {
                let text = lines.join("\n");
                ui.scope_builder(UiBuilder::new().max_rect(col), |ui| {
                    egui::ScrollArea::vertical().id_salt("plain-lyrics").auto_shrink([false, false]).show(ui, |ui| {
                        let g = para(ui, &text, font(F::Display, size * 0.72), Color32::from_rgb(209, 209, 214).gamma_multiply(a), col.width() - 8.0, size * 0.72 * 1.35);
                        let (r, _) = ui.allocate_exact_size(g.size() + vec2(0.0, 40.0), Sense::hover());
                        ui.painter().galley(r.min + vec2(0.0, 20.0), g, WHITE);
                    });
                });
            }
            Some(Lyrics::Synced(lines)) => {
                let lines = lines.clone();
                let now = lines.iter().rposition(|(t, _)| *t <= pos + 0.25).unwrap_or(0);
                // lay out every line once for its height
                let galleys: Vec<_> = lines.iter().map(|(_, s)| para(ui, if s.is_empty() { "♪" } else { s }, font(F::Display, size), WHITE, col.width(), size * 1.25)).collect();
                let mut ys = vec![];
                let mut y = 0.0;
                for g in &galleys { ys.push(y); y += g.size().y + gap; }
                // the current line rests a third of the way down, and the page glides there
                let target = ys.get(now).copied().unwrap_or(0.0) - col.height() * 0.32;
                let dt = ui.ctx().input(|i| i.stable_dt).min(0.05);
                let next = self.lyric_scroll + (target - self.lyric_scroll) * (1.0 - (-dt * 7.0).exp());
                if (next - target).abs() > 0.5 { ui.ctx().request_repaint(); self.lyric_scroll = next } else { self.lyric_scroll = target }
                let clip = ui.painter().with_clip_rect(col);
                for (i, g) in galleys.iter().enumerate() {
                    let yy = col.top() + ys[i] - self.lyric_scroll;
                    if yy > col.bottom() || yy + g.size().y < col.top() { continue; }
                    let c = if i == now { WHITE } else if i == now + 1 { DIM } else if i + 1 == now { Color32::from_rgb(52, 52, 55) } else { GHOST };
                    let lr = Rect::from_min_size(pos2(col.left(), yy), g.size());
                    let resp = ui.interact(lr.intersect(col), Id::new(("lyr", i)), Sense::click());
                    let c = if resp.hovered() && i != now { mix(c, WHITE, 0.4) } else { c };
                    // fade lines out toward the edges of the column
                    let edge = ((yy - col.top()) / 60.0).min((col.bottom() - yy - g.size().y) / 80.0).clamp(0.0, 1.0);
                    let g2 = para(ui, if lines[i].1.is_empty() { "♪" } else { &lines[i].1 }, font(F::Display, size), c.gamma_multiply(a * edge.max(0.15)), col.width(), size * 1.25);
                    clip.galley(lr.min, g2, WHITE);
                    if resp.hovered() { ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
                    if resp.clicked() { self.player.seek(lines[i].0); }
                }
            }
        }
    }

    pub fn draw_next(&mut self, ui: &mut Ui, col: Rect, a: f32) {
        let up = self.player.upcoming();
        let g = line(ui, "Up next", font(F::Display, 30.0), WHITE.gamma_multiply(a), col.width(), -0.9);
        ui.painter().galley(col.min + vec2(0.0, 18.0), g, WHITE);
        let secs: f64 = up.iter().map(|(_, t)| self.lib.tracks[*t].secs as f64).sum();
        let m = meta(ui, &if up.is_empty() { "Nothing after this song".to_string() } else { format!("{} · {}{}", songs(up.len()), span(secs), if self.player.shuffle { " · shuffled" } else { "" }) }, DIM.gamma_multiply(a), col.width());
        ui.painter().galley(col.min + vec2(0.0, 60.0), m, WHITE);
        if !up.is_empty() {
            let cl = line(ui, "Clear", font(F::Semi, 14.0), MUTE, f32::INFINITY, 0.0);
            if self.word(ui, pos2(col.right() - cl.size().x, col.top() + 28.0), "Clear", font(F::Semi, 14.0), MUTE.gamma_multiply(a), WHITE, "clear-next").clicked() { self.player.clear_next(); }
        }
        let list = Rect::from_min_max(pos2(col.left() - 12.0, col.top() + 90.0), col.max);
        let row = 58.0;
        let mut drop_to: Option<usize> = None;
        ui.scope_builder(UiBuilder::new().max_rect(list), |ui| {
            egui::ScrollArea::vertical().id_salt("upnext").auto_shrink([false, false]).show_rows(ui, row, up.len(), |ui, range| {
                for k in range {
                    let (qi, t) = up[k];
                    let (rect, resp) = ui.allocate_exact_size(vec2(list.width(), row), Sense::click_and_drag());
                    let dragging = self.drag_from == Some(qi);
                    if resp.hovered() || dragging { ui.painter().rect_filled(rect, CornerRadius::same(10), if dragging { Color32::from_rgb(18, 18, 20) } else { LIFT }); }
                    let tr = self.lib.tracks[t].clone();
                    let cy = rect.center().y;
                    self.cover(ui, &tr.album_key, &tr.album, Rect::from_min_size(pos2(rect.left() + 12.0, cy - 20.0), vec2(40.0, 40.0)), 6.0, Size::S);
                    let tw = rect.width() - 150.0;
                    ui.painter().galley(pos2(rect.left() + 66.0, cy - 18.0), line(ui, &tr.title, font(F::Medium, 15.0), WHITE.gamma_multiply(a), tw, -0.15), WHITE);
                    ui.painter().galley(pos2(rect.left() + 66.0, cy + 2.0), line(ui, &tr.artist, font(F::Regular, 13.0), MUTE.gamma_multiply(a), tw, 0.0), WHITE);
                    if resp.hovered() || dragging {
                        self.icons.draw(ui, I::Grip, Rect::from_center_size(pos2(rect.right() - 24.0, cy), vec2(24.0, 24.0)), 20.0, DIM);
                        ui.ctx().set_cursor_icon(if dragging { CursorIcon::Grabbing } else { CursorIcon::Grab });
                    } else {
                        let g = line(ui, &clock(tr.secs), font(F::Medium, 13.0), DIM.gamma_multiply(a), 60.0, 0.0);
                        ui.painter().galley(pos2(rect.right() - 12.0 - g.size().x, cy - g.size().y / 2.0), g, WHITE);
                    }
                    if resp.drag_started() { self.drag_from = Some(qi); }
                    if self.drag_from.is_some() {
                        if let Some(p) = ui.ctx().pointer_hover_pos() {
                            if rect.contains(p) && self.drag_from != Some(qi) {
                                ui.painter().hline(rect.x_range(), if p.y < cy { rect.top() } else { rect.bottom() }, egui::Stroke::new(2.0_f32, self.accent));
                                if ui.ctx().input(|i| i.pointer.any_released()) { drop_to = Some(qi); }
                            }
                        }
                    }
                    if resp.double_clicked() { self.jump_to(qi); }
                    resp.context_menu(|ui| {
                        if ui.button("Play now").clicked() { self.jump_to(qi); ui.close_menu(); }
                        if ui.button("Remove from the queue").clicked() { self.player.remove(&self.lib, qi); ui.close_menu(); }
                    });
                }
            });
        });
        if ui.ctx().input(|i| i.pointer.any_released()) {
            if let (Some(from), Some(to)) = (self.drag_from, drop_to) { self.player.move_item(from, to); }
            self.drag_from = None;
        }
    }

    fn jump_to(&mut self, qi: usize) {
        if qi < self.player.queue.len() {
            let list = self.player.queue.clone();
            let from = self.player.from.clone();
            let shuffle = self.player.shuffle;
            self.player.shuffle = false;
            self.player.play_list(&self.lib, list, qi, from);
            self.player.shuffle = shuffle;
        }
    }

    pub fn pick_lyrics(&mut self) {
        let Some(t) = self.player.current() else { return };
        let song = self.lib.tracks[t].path.clone();
        std::thread::spawn(move || {
            if let Some(f) = rfd::FileDialog::new().set_title("Choose lyrics").add_filter("Lyrics", &["lrc", "txt"]).pick_file() {
                if let Ok(text) = std::fs::read_to_string(&f) {
                    let p = crate::lyrics::saved(&song);
                    let _ = std::fs::create_dir_all(p.parent().unwrap());
                    let _ = std::fs::write(p, text);
                }
            }
        });
        // look again shortly; the picker writes beside our data
        self.lyrics = None;
    }
}
