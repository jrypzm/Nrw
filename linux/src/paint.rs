//! Drawing without a window: the wallpaper, and the thousands of dots in Weeks, Days and History, drawn once into an
//! image instead of shape by shape every frame.

use crate::life;
use crate::store::Settings;
use ab_glyph::{Font, FontRef, PxScale, ScaleFont};
use tiny_skia::{Color, FillRule, Paint, PathBuilder, Pixmap, Transform};

const THIN: &[u8] = include_bytes!("../assets/fonts/interdisplay_extralight.ttf");
const SEMI: &[u8] = include_bytes!("../assets/fonts/interdisplay_semibold.ttf");
const BODY: &[u8] = include_bytes!("../assets/fonts/inter_medium.ttf");

fn paint(rgb: u8, a: f32) -> Paint<'static> {
    let mut p = Paint::default();
    p.set_color(Color::from_rgba8(rgb, rgb, rgb, (a.clamp(0.0, 1.0) * 255.0) as u8));
    p.anti_alias = true;
    p
}

pub fn circle(pm: &mut Pixmap, x: f32, y: f32, r: f32, p: &Paint) {
    if let Some(path) = PathBuilder::from_circle(x, y, r.max(0.3)) { pm.fill_path(&path, p, FillRule::Winding, Transform::identity(), None); }
}

/// A dot partly filled from the bottom.
fn partial(pm: &mut Pixmap, x: f32, y: f32, r: f32, f: f32, lit: &Paint, base: &Paint) {
    circle(pm, x, y, r, base);
    let top = y + r - 2.0 * r * f.clamp(0.0, 1.0);
    let Some(rect) = tiny_skia::Rect::from_ltrb(x - r - 1.0, top, x + r + 1.0, y + r + 1.0) else { return };
    let mut mask = tiny_skia::Mask::new(pm.width(), pm.height()).unwrap();
    mask.fill_path(&PathBuilder::from_rect(rect), FillRule::Winding, true, Transform::identity());
    if let Some(path) = PathBuilder::from_circle(x, y, r) { pm.fill_path(&path, lit, FillRule::Winding, Transform::identity(), Some(&mask)); }
}

/// Text in the given font, with tracking in em. [x, y] is the baseline start (or centre / end for align 1 / 2).
pub fn text(pm: &mut Pixmap, font: &[u8], s: &str, x: f32, y: f32, size: f32, rgb: u8, a: f32, track: f32, align: u8) -> f32 {
    let Ok(f) = FontRef::try_from_slice(font) else { return 0.0 };
    let sf = f.as_scaled(PxScale::from(size));
    let mut w = 0.0; let mut prev = None;
    for ch in s.chars() { let g = sf.glyph_id(ch); if let Some(p) = prev { w += sf.kern(p, g); } w += sf.h_advance(g) + track * size; prev = Some(g); }
    w -= track * size;
    let mut pen = x - match align { 1 => w / 2.0, 2 => w, _ => 0.0 };
    let (pw, ph) = (pm.width() as i32, pm.height() as i32);
    let data = pm.data_mut();
    prev = None;
    for ch in s.chars() {
        let id = sf.glyph_id(ch);
        if let Some(p) = prev { pen += sf.kern(p, id); }
        let glyph = id.with_scale_and_position(PxScale::from(size), ab_glyph::point(pen, y));
        if let Some(o) = f.outline_glyph(glyph) {
            let bb = o.px_bounds();
            o.draw(|gx, gy, cov| {
                let px = bb.min.x as i32 + gx as i32; let py = bb.min.y as i32 + gy as i32;
                if px < 0 || py < 0 || px >= pw || py >= ph { return; }
                let i = ((py * pw + px) * 4) as usize;
                let k = (cov * a).clamp(0.0, 1.0);
                // premultiplied "over"
                for c in 0..3 { data[i + c] = (rgb as f32 * k + data[i + c] as f32 * (1.0 - k)).round() as u8; }
                data[i + 3] = (255.0 * k + data[i + 3] as f32 * (1.0 - k)).round() as u8;
            });
        }
        pen += sf.h_advance(id) + track * size;
        prev = Some(id);
    }
    w
}

/// Every dot of a big grid as one image: lived dots lit, the rest dark. The dot you're in is drawn live by the app.
pub fn big_grid(w: u32, h: u32, total: usize, cols: usize, done: usize, ratio: f32, lit: u8) -> Pixmap {
    let mut pm = Pixmap::new(w.max(1), h.max(1)).unwrap();
    let rows = total.div_ceil(cols);
    let cell = (w as f32 / cols as f32).min(h as f32 / rows as f32);
    let r = cell * ratio / 2.0;
    let (pl, pe) = (paint(lit, 1.0), paint(0x23, 1.0));
    for i in 0..total {
        if i == done { continue; }
        let x = (i % cols) as f32 * cell + cell / 2.0;
        let y = (i / cols) as f32 * cell + cell / 2.0;
        circle(&mut pm, x, y, r, if i < done { &pl } else { &pe });
    }
    pm
}

/// The wallpaper: your years as dots, with the number, in one of three styles.
pub fn wallpaper(s: &Settings, w: u32, h: u32, style: u8) -> Pixmap {
    let mut pm = Pixmap::new(w.max(320), h.max(240)).unwrap();
    pm.fill(Color::BLACK);
    let (W, H) = (pm.width() as f32, pm.height() as f32);
    let Some(b) = s.birth() else { return pm };
    let a = life::age(b).min(s.span as f64);
    let lived = a.floor() as usize;
    let sun = if style == 2 { life::sun_level() } else { 1.0 };
    let lit = (110.0 + 145.0 * sun).round() as u8;
    let (pl, pe, pp) = (paint(lit, 1.0), paint(0x23, 1.0), paint(0x3a, 1.0));
    let u = H / 1440.0;
    let cols = 10usize; let rows = (s.span as usize).div_ceil(cols);
    let left = life::years_left(s);
    let head = format!("{}", life::whole(if s.lived { a } else { left }));
    let word = if s.lived { "years lived" } else { "years left" };
    let sub = format!("Age {} · {} year life", lived, s.span);
    let draw_grid = |pm: &mut Pixmap, x0: f32, y0: f32, cell: f32| {
        let r = cell * 0.3;
        for i in 0..s.span as usize {
            let x = x0 + (i % cols) as f32 * cell + cell / 2.0; let y = y0 + (i / cols) as f32 * cell + cell / 2.0;
            if i < lived { circle(pm, x, y, r, &pl) } else if i == lived { partial(pm, x, y, r, (a - lived as f64) as f32, &pl, &pp) } else { circle(pm, x, y, r, &pe) }
        }
    };
    if style == 1 {
        // the number on the left, the grid on the right
        let cell = (H * 0.62 / rows as f32).min(W * 0.4 / cols as f32);
        let gw = cell * cols as f32; let gh = cell * rows as f32;
        let gx = W * 0.56; let gy = (H - gh) / 2.0;
        draw_grid(&mut pm, gx, gy, cell);
        let nx = W * 0.12; let size = (H * 0.34).min(W * 0.2);
        text(&mut pm, THIN, &head, nx, gy + gh * 0.52, size, 255, 1.0, -0.055, 0);
        text(&mut pm, SEMI, word, nx + size * 0.04, gy + gh * 0.52 + 92.0 * u, 64.0 * u, 255, 1.0, -0.03, 0);
        text(&mut pm, BODY, &sub, nx + size * 0.045, gy + gh * 0.52 + 150.0 * u, 30.0 * u, 0x8e, 1.0, 0.0, 0);
        let _ = gw;
    } else {
        // the grid, centred a little high, with a quiet caption under it
        let cell = (H * 0.58 / rows as f32).min(W * 0.5 / cols as f32);
        let gw = cell * cols as f32; let gh = cell * rows as f32;
        let gx = (W - gw) / 2.0; let gy = (H - gh) / 2.0 - 40.0 * u;
        draw_grid(&mut pm, gx, gy, cell);
        let line = format!("{head} {word}");
        text(&mut pm, SEMI, &line, W / 2.0, gy + gh + 86.0 * u, 40.0 * u, 255, if style == 2 { 0.55 + 0.45 * sun } else { 1.0 }, -0.02, 1);
        text(&mut pm, BODY, &sub, W / 2.0, gy + gh + 132.0 * u, 26.0 * u, 0x8e, 1.0, 0.0, 1);
    }
    pm
}
