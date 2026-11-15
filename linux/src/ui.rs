//! The look of yrs.: black, white and greys; thin, very large numbers; cards that are barely there.
//! Every control is drawn here by hand, so the app looks the same on every desktop.

use egui::{text::LayoutJob, Align2, Color32, Context, CornerRadius, FontFamily, FontId, Galley, Id, Painter, Pos2, Rect, Response, Sense, Stroke, StrokeKind, TextFormat, Ui, Vec2};
use std::sync::Arc;

// ------------------------------------------------------------------ colours

pub const BG: Color32 = Color32::BLACK;
pub const FG: Color32 = Color32::WHITE;
pub const MUTE: Color32 = Color32::from_rgb(0x8e, 0x8e, 0x93);
pub const DIM: Color32 = Color32::from_rgb(0x63, 0x63, 0x66);
pub const FAINT: Color32 = Color32::from_rgb(0x48, 0x48, 0x4a);
pub const EMPTY: Color32 = Color32::from_rgb(0x23, 0x23, 0x23);
pub const PARTIAL: Color32 = Color32::from_rgb(0x3a, 0x3a, 0x3c);
pub const CARD: Color32 = Color32::from_rgb(0x0c, 0x0c, 0x0d);
pub const CARD_HI: Color32 = Color32::from_rgb(0x12, 0x12, 0x14);
pub const HAIR: Color32 = Color32::from_rgb(0x1e, 0x1e, 0x20);
pub const HAIR2: Color32 = Color32::from_rgb(0x2c, 0x2c, 0x2e);
pub const GLASS: Color32 = Color32::from_rgb(0x12, 0x12, 0x14);
pub const SURFACE: Color32 = Color32::from_rgb(0x1c, 0x1c, 0x1e);
pub const SURFACE2: Color32 = Color32::from_rgb(0x2c, 0x2c, 0x2e);
pub const SURFACE3: Color32 = Color32::from_rgb(0x3a, 0x3a, 0x3c);
pub const SHEET: Color32 = Color32::from_rgb(0x14, 0x14, 0x16);

pub fn gray(v: u8) -> Color32 { Color32::from_rgb(v, v, v) }
pub fn alpha(c: Color32, a: f32) -> Color32 { c.gamma_multiply(a.clamp(0.0, 1.0)) }

// ------------------------------------------------------------------ type

#[derive(Clone, Copy)]
pub enum F { Thin, Light, Display, DisplayBold, Body, Medium, Semi }

impl F {
    fn family(self) -> FontFamily {
        FontFamily::Name(match self { F::Thin => "thin", F::Light => "light", F::Display => "display", F::DisplayBold => "displaybold", F::Body => "body", F::Medium => "medium", F::Semi => "semi" }.into())
    }
    pub fn at(self, size: f32) -> FontId { FontId::new(size, self.family()) }
}

pub fn install_fonts(ctx: &Context) {
    let mut f = egui::FontDefinitions::default();
    let add = |f: &mut egui::FontDefinitions, k: &str, b: &'static [u8]| { f.font_data.insert(k.into(), Arc::new(egui::FontData::from_static(b))); };
    add(&mut f, "thin", include_bytes!("../assets/fonts/interdisplay_extralight.ttf"));
    add(&mut f, "light", include_bytes!("../assets/fonts/interdisplay_light.ttf"));
    add(&mut f, "display", include_bytes!("../assets/fonts/interdisplay_semibold.ttf"));
    add(&mut f, "displaybold", include_bytes!("../assets/fonts/interdisplay_bold.ttf"));
    add(&mut f, "body", include_bytes!("../assets/fonts/inter_regular.ttf"));
    add(&mut f, "medium", include_bytes!("../assets/fonts/inter_medium.ttf"));
    add(&mut f, "semi", include_bytes!("../assets/fonts/inter_semibold.ttf"));
    // egui's own fonts stay at the end of every family for the odd symbol Inter lacks
    let fallback: Vec<String> = f.families.get(&FontFamily::Proportional).cloned().unwrap_or_default();
    for k in ["thin", "light", "display", "displaybold", "body", "medium", "semi"] {
        let mut v = vec![k.to_string()]; v.extend(fallback.iter().cloned());
        f.families.insert(FontFamily::Name(k.into()), v);
    }
    let mut p = vec!["body".to_string()]; p.extend(fallback);
    f.families.insert(FontFamily::Proportional, p);
    ctx.set_fonts(f);
}

/// A run of text with tracking (letter spacing in em) and an optional width to wrap at.
pub fn galley(ctx: &Context, text: &str, f: F, size: f32, color: Color32, track: f32, wrap: f32, line: Option<f32>) -> Arc<Galley> {
    let mut job = LayoutJob::default();
    job.append(text, 0.0, TextFormat { font_id: f.at(size), color, extra_letter_spacing: track * size, line_height: line, ..Default::default() });
    job.wrap.max_width = wrap;
    ctx.fonts(|fo| fo.layout_job(job))
}

/// Draws text at a point, anchored by [align]. Returns its rectangle.
pub fn text(p: &Painter, pos: Pos2, align: Align2, s: &str, f: F, size: f32, color: Color32, track: f32) -> Rect {
    let g = galley(p.ctx(), s, f, size, color, track, f32::INFINITY, None);
    let r = align.anchor_size(pos, g.size());
    p.galley(r.min, g, color);
    r
}

/// Wrapped text from the top left. Returns the height used.
pub fn para(p: &Painter, pos: Pos2, s: &str, f: F, size: f32, color: Color32, width: f32, line: f32) -> f32 {
    let g = galley(p.ctx(), s, f, size, color, -0.005, width, Some(size * line));
    let h = g.size().y;
    p.galley(pos, g, color);
    h
}
pub fn para_height(ctx: &Context, s: &str, f: F, size: f32, width: f32, line: f32) -> f32 { galley(ctx, s, f, size, FG, -0.005, width, Some(size * line)).size().y }
pub fn width_of(ctx: &Context, s: &str, f: F, size: f32, track: f32) -> f32 { galley(ctx, s, f, size, FG, track, f32::INFINITY, None).size().x }

/// The caption above a section: small, uppercase, widely spaced.
pub fn cap(p: &Painter, pos: Pos2, s: &str) -> Rect { text(p, pos, Align2::LEFT_TOP, &s.to_uppercase(), F::Semi, 11.5, DIM, 0.1) }

// ------------------------------------------------------------------ motion

pub fn ease_out(t: f32) -> f32 { 1.0 - (1.0 - t.clamp(0.0, 1.0)).powi(4) }
pub fn ease_in_out(t: f32) -> f32 { let t = t.clamp(0.0, 1.0); if t < 0.5 { 4.0 * t * t * t } else { 1.0 - (-2.0 * t + 2.0).powi(3) / 2.0 } }

#[derive(Clone, Copy)]
struct Tw { from: f64, to: f64, start: f64, dur: f64 }

/// A value that glides to its target with an ease-out curve. The first time it starts at [first], or at the target.
pub fn tween(ctx: &Context, id: Id, target: f64, dur: f64, first: Option<f64>) -> f64 {
    let now = ctx.input(|i| i.time);
    let mut tw = ctx.data_mut(|d| d.get_temp::<Tw>(id)).unwrap_or(Tw { from: first.unwrap_or(target), to: target, start: now, dur });
    let cur = |tw: &Tw| { let t = ((now - tw.start) / tw.dur.max(1e-3)) as f32; tw.from + (tw.to - tw.from) * ease_out(t) as f64 };
    if (tw.to - target).abs() > 1e-9 { tw = Tw { from: cur(&tw), to: target, start: now, dur }; }
    let v = cur(&tw);
    ctx.data_mut(|d| d.insert_temp(id, tw));
    if now - tw.start < tw.dur { ctx.request_repaint(); }
    if now - tw.start >= tw.dur { target } else { v }
}
pub fn tweenf(ctx: &Context, id: Id, target: f32, dur: f64) -> f32 { tween(ctx, id, target as f64, dur, None) as f32 }

/// A rolling number: it glides from where it was to [target] whenever [key] changes, and follows it live otherwise.
pub fn rolling(ctx: &Context, id: Id, target: f64, key: u64, still: bool) -> f64 {
    if still { return target; }
    let now = ctx.input(|i| i.time);
    #[derive(Clone, Copy)] struct R { key: u64, from: f64, start: f64 }
    let r = ctx.data_mut(|d| d.get_temp::<R>(id));
    let r = match r {
        None => R { key, from: 0.0, start: now },
        Some(r) if r.key != key => { let p = ((now - r.start) / 0.9).min(1.0) as f32; let shown = if p >= 1.0 { f64::NAN } else { r.from + (target - r.from) * ease_out(p) as f64 }; R { key, from: if shown.is_nan() { ctx.data(|d| d.get_temp::<f64>(id.with("last"))).unwrap_or(target) } else { shown }, start: now } }
        Some(r) => r,
    };
    ctx.data_mut(|d| d.insert_temp(id, r));
    let p = ((now - r.start) / 0.9) as f32;
    let v = if p >= 1.0 { target } else { ctx.request_repaint(); r.from + (target - r.from) * ease_out(p) as f64 };
    ctx.data_mut(|d| d.insert_temp(id.with("last"), v));
    v
}

// ------------------------------------------------------------------ surfaces

pub fn card(p: &Painter, r: Rect, radius: u8, fill: Color32) {
    p.rect_filled(r, CornerRadius::same(radius), fill);
    p.rect_stroke(r, CornerRadius::same(radius), Stroke::new(1.0, HAIR), StrokeKind::Inside);
}

pub fn hit(ui: &Ui, r: Rect, id: Id) -> Response {
    let resp = ui.interact(r, id, Sense::click());
    if resp.hovered() { ui.ctx().set_cursor_icon(egui::CursorIcon::PointingHand); }
    resp
}

/// The main white button.
pub fn primary(ui: &Ui, r: Rect, label: &str, id: Id) -> bool {
    let resp = hit(ui, r, id);
    let k = tweenf(ui.ctx(), id.with("h"), if resp.is_pointer_button_down_on() { 2.0 } else if resp.hovered() { 1.0 } else { 0.0 }, 0.15);
    let fill = if k > 1.0 { gray(209) } else { gray((255.0 - 23.0 * k) as u8) };
    let p = ui.painter();
    p.rect_filled(r, CornerRadius::same((r.height() / 2.0) as u8), fill);
    text(p, r.center(), Align2::CENTER_CENTER, label, F::Semi, 15.5, Color32::BLACK, -0.01);
    resp.clicked()
}

/// A quiet text button.
pub fn quiet(ui: &Ui, r: Rect, label: &str, id: Id) -> bool {
    let resp = hit(ui, r, id);
    let c = if resp.hovered() { FG } else { MUTE };
    text(ui.painter(), r.center(), Align2::CENTER_CENTER, label, F::Medium, 14.5, c, 0.0);
    resp.clicked()
}

/// A capsule button with a hairline, white when on.
pub fn pill(ui: &Ui, r: Rect, label: &str, on: bool, id: Id) -> bool {
    let resp = hit(ui, r, id);
    let k = tweenf(ui.ctx(), id.with("on"), if on { 1.0 } else { 0.0 }, 0.25);
    let h = tweenf(ui.ctx(), id.with("h"), if resp.hovered() { 1.0 } else { 0.0 }, 0.15);
    let p = ui.painter();
    let base = lerp_c(GLASS, SURFACE2, h * 0.6);
    p.rect_filled(r, CornerRadius::same((r.height() / 2.0) as u8), lerp_c(base, FG, k));
    if k < 0.99 { p.rect_stroke(r, CornerRadius::same((r.height() / 2.0) as u8), Stroke::new(1.0, alpha(HAIR2, 1.0 - k)), StrokeKind::Inside); }
    text(p, r.center(), Align2::CENTER_CENTER, label, F::Semi, 14.0, lerp_c(FG, Color32::BLACK, k), 0.0);
    resp.clicked()
}

pub fn lerp_c(a: Color32, b: Color32, t: f32) -> Color32 {
    let t = t.clamp(0.0, 1.0);
    let l = |x: u8, y: u8| (x as f32 + (y as f32 - x as f32) * t).round() as u8;
    Color32::from_rgba_unmultiplied(l(a.r(), b.r()), l(a.g(), b.g()), l(a.b(), b.b()), l(a.a(), b.a()))
}

/// Two or more options in a capsule, with a thumb that slides to the chosen one. Returns the index clicked.
pub fn segmented(ui: &Ui, r: Rect, labels: &[&str], sel: usize, id: Id) -> Option<usize> {
    let p = ui.painter();
    let rad = CornerRadius::same((r.height() / 2.0) as u8);
    p.rect_filled(r, rad, GLASS);
    p.rect_stroke(r, rad, Stroke::new(1.0, HAIR), StrokeKind::Inside);
    let inner = r.shrink(4.0);
    let w = inner.width() / labels.len() as f32;
    let x = tweenf(ui.ctx(), id.with("x"), sel as f32, 0.35);
    let thumb = Rect::from_min_size(Pos2::new(inner.left() + w * x, inner.top()), Vec2::new(w, inner.height()));
    p.rect_filled(thumb, CornerRadius::same((inner.height() / 2.0) as u8), SURFACE2);
    let mut out = None;
    for (i, l) in labels.iter().enumerate() {
        let cell = Rect::from_min_size(Pos2::new(inner.left() + w * i as f32, inner.top()), Vec2::new(w, inner.height()));
        let resp = hit(ui, cell, id.with(i));
        let c = if i == sel { FG } else if resp.hovered() { gray(0xc7) } else { MUTE };
        text(ui.painter(), cell.center(), Align2::CENTER_CENTER, l, if i == sel { F::Semi } else { F::Medium }, 14.0, c, 0.0);
        if resp.clicked() && i != sel { out = Some(i); }
    }
    out
}

/// A thin track with a white thumb. Drag or click anywhere on it.
pub fn slider(ui: &Ui, r: Rect, v: &mut f32, min: f32, max: f32, step: f32, id: Id) -> bool {
    let resp = ui.interact(r, id, Sense::click_and_drag());
    if resp.hovered() || resp.dragged() { ui.ctx().set_cursor_icon(egui::CursorIcon::PointingHand); }
    let half = 12.0;
    let track = r.width() - 2.0 * half;
    let mut changed = false;
    if let Some(pos) = resp.interact_pointer_pos() {
        if resp.dragged() || resp.clicked() || resp.is_pointer_button_down_on() {
            let f = ((pos.x - r.left() - half) / track).clamp(0.0, 1.0);
            let nv = ((min + (max - min) * f) / step).round() * step;
            let nv = nv.clamp(min, max);
            if (nv - *v).abs() > 1e-4 { *v = nv; changed = true; }
        }
    }
    let f = ((*v - min) / (max - min)).clamp(0.0, 1.0);
    let y = r.center().y;
    let p = ui.painter();
    let tr = Rect::from_min_max(Pos2::new(r.left() + half, y - 2.0), Pos2::new(r.right() - half, y + 2.0));
    p.rect_filled(tr, CornerRadius::same(2), SURFACE3);
    p.rect_filled(Rect::from_min_max(tr.min, Pos2::new(tr.left() + track * f, tr.bottom())), CornerRadius::same(2), alpha(FG, 0.55));
    let cx = r.left() + half + track * f;
    let grow = tweenf(ui.ctx(), id.with("g"), if resp.dragged() || resp.hovered() { 1.0 } else { 0.0 }, 0.18);
    p.circle_filled(Pos2::new(cx, y + 1.5), half + 1.5, Color32::from_black_alpha(90));
    p.circle_filled(Pos2::new(cx, y), half - 1.0 + grow * 1.5, FG);
    changed
}

/// A round button (⋯, ‹, ›, ×) with a hairline.
pub fn round(ui: &Ui, c: Pos2, r: f32, id: Id, draw: impl FnOnce(&Painter, Pos2, Color32)) -> bool {
    let rect = Rect::from_center_size(c, Vec2::splat(r * 2.0));
    let resp = hit(ui, rect, id);
    let h = tweenf(ui.ctx(), id.with("h"), if resp.hovered() { 1.0 } else { 0.0 }, 0.15);
    let p = ui.painter();
    p.circle_filled(c, r, lerp_c(GLASS, SURFACE2, h));
    p.circle_stroke(c, r - 0.5, Stroke::new(1.0, HAIR2));
    draw(p, c, FG);
    resp.clicked()
}
pub fn dots3(p: &Painter, c: Pos2, col: Color32) { for k in [-6.0, 0.0, 6.0] { p.circle_filled(c + Vec2::new(k, 0.0), 1.9, col); } }
pub fn cross(p: &Painter, c: Pos2, col: Color32) { let s = 5.0; p.line_segment([c + Vec2::new(-s, -s), c + Vec2::new(s, s)], Stroke::new(1.6, col)); p.line_segment([c + Vec2::new(-s, s), c + Vec2::new(s, -s)], Stroke::new(1.6, col)); }
pub fn chevron(p: &Painter, c: Pos2, col: Color32, left: bool) {
    let d = if left { 1.0 } else { -1.0 };
    p.line_segment([c + Vec2::new(2.5 * d, -5.0), c + Vec2::new(-2.5 * d, 0.0)], Stroke::new(1.6, col));
    p.line_segment([c + Vec2::new(-2.5 * d, 0.0), c + Vec2::new(2.5 * d, 5.0)], Stroke::new(1.6, col));
}

/// A dot that is partly filled from the bottom, like the year you're in.
pub fn partial_dot(p: &Painter, c: Pos2, r: f32, f: f32, lit: Color32, base: Color32) {
    p.circle_filled(c, r, base);
    let f = f.clamp(0.0, 1.0);
    if f <= 0.0 { return; }
    // the filled part is the circle, clipped to the band below the waterline
    let top = c.y + r - 2.0 * r * f;
    let clip = Rect::from_min_max(Pos2::new(c.x - r - 1.0, top), Pos2::new(c.x + r + 1.0, c.y + r + 1.0));
    p.with_clip_rect(clip.intersect(p.clip_rect())).circle_filled(c, r, lit);
}

/// A text field drawn as a soft dark capsule.
pub fn field(ui: &mut Ui, r: Rect, value: &mut String, hint: &str, multiline: bool, id: Id, max: usize) -> Response {
    let p = ui.painter().clone();
    let focused = ui.ctx().memory(|m| m.has_focus(id));
    p.rect_filled(r, CornerRadius::same(16), SURFACE);
    p.rect_stroke(r, CornerRadius::same(16), Stroke::new(1.0, if focused { SURFACE3 } else { HAIR }), StrokeKind::Inside);
    let inner = r.shrink2(Vec2::new(16.0, 13.0));
    let mut child = ui.new_child(egui::UiBuilder::new().max_rect(inner));
    let te = if multiline { egui::TextEdit::multiline(value) } else { egui::TextEdit::singleline(value) };
    let out = child.add(te.id(id).frame(false).font(F::Body.at(16.0)).text_color(FG).desired_width(inner.width()).desired_rows(if multiline { ((inner.height() - 4.0) / 22.0).max(1.0) as usize } else { 1 })
        .hint_text(egui::RichText::new(hint).color(DIM).font(F::Body.at(16.0))).char_limit(max).margin(Vec2::ZERO));
    out
}

/// An on/off switch.
pub fn toggle(ui: &Ui, c: Pos2, on: bool, id: Id) -> bool {
    let r = Rect::from_center_size(c, Vec2::new(46.0, 28.0));
    let resp = hit(ui, r, id);
    let k = tweenf(ui.ctx(), id.with("k"), if on { 1.0 } else { 0.0 }, 0.28);
    let p = ui.painter();
    p.rect_filled(r, CornerRadius::same(14), lerp_c(SURFACE3, FG, k));
    p.circle_filled(Pos2::new(r.left() + 14.0 + 18.0 * k, c.y), 11.0, lerp_c(FG, Color32::BLACK, k));
    resp.clicked()
}

/// − value +
pub fn stepper(ui: &Ui, right: Pos2, value: i64, id: Id) -> i64 {
    let mut d = 0;
    if round(ui, right + Vec2::new(-16.0, 0.0), 16.0, id.with("+"), |p, c, col| { p.line_segment([c + Vec2::new(-5.0, 0.0), c + Vec2::new(5.0, 0.0)], Stroke::new(1.6, col)); p.line_segment([c + Vec2::new(0.0, -5.0), c + Vec2::new(0.0, 5.0)], Stroke::new(1.6, col)); }) { d = 1; }
    text(ui.painter(), right + Vec2::new(-58.0, 0.0), Align2::CENTER_CENTER, &value.to_string(), F::Medium, 16.0, FG, 0.0);
    if round(ui, right + Vec2::new(-100.0, 0.0), 16.0, id.with("-"), |p, c, col| { p.line_segment([c + Vec2::new(-5.0, 0.0), c + Vec2::new(5.0, 0.0)], Stroke::new(1.6, col)); }) { d = -1; }
    d
}

/// One spinning column of a picker. Scroll over it, drag it, or click a value. Returns the new index if it changed.
pub fn wheel(ui: &Ui, r: Rect, items: &[String], index: usize, id: Id) -> Option<usize> {
    let row = 40.0;
    let resp = ui.interact(r, id, Sense::click_and_drag());
    let mut target = index as f32;
    let mut out = None;
    if resp.hovered() {
        ui.ctx().set_cursor_icon(egui::CursorIcon::ResizeVertical);
        let dy = ui.ctx().input(|i| i.raw_scroll_delta.y);
        if dy != 0.0 {
            let acc = ui.ctx().data(|d| d.get_temp::<f32>(id.with("acc"))).unwrap_or(0.0) + dy;
            let steps = (acc / 40.0).trunc();
            ui.ctx().data_mut(|d| d.insert_temp(id.with("acc"), acc - steps * 40.0));
            if steps != 0.0 { target = (index as f32 - steps).clamp(0.0, items.len() as f32 - 1.0); }
        }
    }
    if resp.dragged() {
        let acc = ui.ctx().data(|d| d.get_temp::<f32>(id.with("drag"))).unwrap_or(0.0) + resp.drag_delta().y;
        let steps = (acc / row).trunc();
        ui.ctx().data_mut(|d| d.insert_temp(id.with("drag"), acc - steps * row));
        if steps != 0.0 { target = (index as f32 - steps).clamp(0.0, items.len() as f32 - 1.0); }
    }
    if resp.drag_stopped() { ui.ctx().data_mut(|d| d.insert_temp(id.with("drag"), 0.0f32)); }
    if resp.clicked() {
        if let Some(pos) = resp.interact_pointer_pos() {
            let k = ((pos.y - r.center().y) / row).round() as i32;
            target = (index as i32 + k).clamp(0, items.len() as i32 - 1) as f32;
        }
    }
    if target as usize != index { out = Some(target as usize); }
    let shown = tweenf(ui.ctx(), id.with("pos"), out.unwrap_or(index) as f32, 0.22);
    let p = ui.painter().with_clip_rect(r);
    let lo = (shown - 3.0).floor().max(0.0) as usize;
    let hi = ((shown + 3.0).ceil() as usize).min(items.len().saturating_sub(1));
    for i in lo..=hi {
        let dy = (i as f32 - shown) * row;
        let d = (i as f32 - shown).abs();
        let a = (1.0 - d * 0.36).max(0.08);
        let (f, size) = if d < 0.5 { (F::Display, 21.0) } else { (F::Light, 19.0) };
        text(&p, Pos2::new(r.center().x, r.center().y + dy), Align2::CENTER_CENTER, &items[i], f, size, alpha(FG, a), -0.01);
    }
    out
}

/// Day, month and year wheels over a soft band. Returns the new date if it changed.
pub fn date_wheels(ui: &Ui, r: Rect, date: chrono::NaiveDate, from: i32, to: i32, id: Id) -> Option<chrono::NaiveDate> {
    use chrono::Datelike;
    let p = ui.painter();
    p.rect_filled(Rect::from_center_size(r.center(), Vec2::new(r.width(), 42.0)), CornerRadius::same(14), SURFACE);
    let days_in = |y: i32, m: u32| chrono::NaiveDate::from_ymd_opt(y, m, 1).and_then(|d| d.checked_add_months(chrono::Months::new(1))).map(|n| (n - chrono::NaiveDate::from_ymd_opt(y, m, 1).unwrap()).num_days() as u32).unwrap_or(31);
    let dim = days_in(date.year(), date.month());
    let days: Vec<String> = (1..=dim).map(|d| d.to_string()).collect();
    let months: Vec<String> = crate::life::MONTHS.iter().map(|m| m.to_string()).collect();
    let years: Vec<String> = (from..=to).map(|y| y.to_string()).collect();
    let w = r.width();
    let c1 = Rect::from_min_size(r.min, Vec2::new(w * 0.22, r.height()));
    let c2 = Rect::from_min_size(Pos2::new(r.left() + w * 0.22, r.top()), Vec2::new(w * 0.46, r.height()));
    let c3 = Rect::from_min_size(Pos2::new(r.left() + w * 0.68, r.top()), Vec2::new(w * 0.32, r.height()));
    let (mut y, mut m, mut d) = (date.year(), date.month(), date.day());
    if let Some(i) = wheel(ui, c1, &days, (d - 1) as usize, id.with("d")) { d = i as u32 + 1; }
    if let Some(i) = wheel(ui, c2, &months, (m - 1) as usize, id.with("m")) { m = i as u32 + 1; }
    if let Some(i) = wheel(ui, c3, &years, (y - from).clamp(0, to - from) as usize, id.with("y")) { y = from + i as i32; }
    let d = d.min(days_in(y, m));
    let nd = chrono::NaiveDate::from_ymd_opt(y, m, d)?;
    if nd != date { Some(nd) } else { None }
}

/// A list of rows inside a rounded dark group. Returns the clicked row.
pub fn row(ui: &Ui, r: Rect, title: &str, sub: Option<&str>, id: Id, first: bool) -> Response {
    let resp = hit(ui, r, id);
    let p = ui.painter();
    if resp.hovered() { p.rect_filled(r.shrink(1.0), CornerRadius::same(12), alpha(FG, 0.04)); }
    if !first { p.line_segment([Pos2::new(r.left() + 18.0, r.top()), Pos2::new(r.right(), r.top())], Stroke::new(1.0, HAIR2)); }
    let x = r.left() + 18.0;
    if let Some(sub) = sub {
        text(p, Pos2::new(x, r.center().y - 10.0), Align2::LEFT_CENTER, title, F::Body, 15.5, FG, -0.01);
        text(p, Pos2::new(x, r.center().y + 11.0), Align2::LEFT_CENTER, sub, F::Body, 12.5, MUTE, 0.0);
    } else {
        text(p, Pos2::new(x, r.center().y), Align2::LEFT_CENTER, title, F::Body, 15.5, FG, -0.01);
    }
    resp
}
