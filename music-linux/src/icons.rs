//! The glyphs from the design, drawn from the same SVG paths and kept as white
//! textures, so any colour can be laid over them.

use egui::{Color32, Context, Rect, TextureHandle, TextureOptions, Ui};
use std::collections::HashMap;

#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
pub enum I {
    More,
    Down,
    Back,
    Search,
    Min,
    Max,
    Close,
    Heart,
    HeartFull,
    Play,
    Pause,
    Next,
    Prev,
    Volume,
    Mute,
    Grip,
    Sort,
}

fn tri(x: f32, y: f32, w: f32, h: f32) -> String {
    let r = 1.6;
    format!(
        "M{x} {}c0 -{r} 1.15 -{} 2.05 -1.05l{} {}c.85 .55 .85 1.85 0 2.4L{} {}C{} {} {x} {} {x} {}z",
        y + r * 1.3, r * 1.5, w - 2.6, h / 2.0 - 1.3, x + 2.05, y + h + r * 0.25, x + 1.15, y + h + 0.75, y + h, y + h - r * 1.3
    )
}

fn svg(i: I) -> String {
    const HEART: &str = "M12 19s-7-4.2-7-9.3A3.9 3.9 0 0 1 12 7.5a3.9 3.9 0 0 1 7 2.2C19 14.8 12 19 12 19z";
    let s = |d: &str| format!(r##"<path d="{d}" fill="none" stroke="#fff" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>"##);
    let k = |d: &str| format!(r##"<path d="{d}" fill="#fff"/>"##);
    let body = match i {
        I::More => r##"<circle cx="5.5" cy="12" r="1.5" fill="#fff"/><circle cx="12" cy="12" r="1.5" fill="#fff"/><circle cx="18.5" cy="12" r="1.5" fill="#fff"/>"##.to_string(),
        I::Down => s("M6.5 9.5l5.5 5.5 5.5-5.5"),
        I::Back => s("M14.5 6l-6 6 6 6"),
        I::Search => s("M10.5 16.5a6 6 0 1 0 0-12a6 6 0 1 0 0 12z") + &s("M15 15l4.5 4.5"),
        I::Min => s("M7 12h10"),
        I::Max => r##"<rect x="7" y="7" width="10" height="10" rx="2" fill="none" stroke="#fff" stroke-width="1.6"/>"##.to_string(),
        I::Close => s("M7.5 7.5l9 9M16.5 7.5l-9 9"),
        I::Heart => s(HEART),
        I::HeartFull => k(HEART) + &s(HEART),
        I::Play => k(&tri(6.4, 4.3, 14.2, 15.4)),
        I::Pause => r##"<rect x="6.2" y="4.5" width="3.8" height="15" rx="1.2" fill="#fff"/><rect x="14" y="4.5" width="3.8" height="15" rx="1.2" fill="#fff"/>"##.to_string(),
        I::Next => k(&tri(3.2, 6.3, 9.4, 11.4)) + &k(&tri(11.9, 6.3, 9.4, 11.4)),
        I::Prev => format!(r##"<g transform="translate(24 0) scale(-1 1)">{}{}</g>"##, k(&tri(3.2, 6.3, 9.4, 11.4)), k(&tri(11.9, 6.3, 9.4, 11.4))),
        I::Volume => k("M4 9.5h3.2L11.5 6v12l-4.3-3.5H4z") + &s("M15 9.2a4 4 0 0 1 0 5.6M17.6 7a7 7 0 0 1 0 10"),
        I::Mute => k("M4 9.5h3.2L11.5 6v12l-4.3-3.5H4z") + &s("M15.5 9.5l5 5M20.5 9.5l-5 5"),
        I::Grip => s("M6 9.5h12M6 14.5h12"),
        I::Sort => s("M8 5v14M5 16l3 3 3-3M16 19V5M13 8l3-3 3 3"),
    };
    format!(r##"<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">{body}</svg>"##)
}

#[derive(Default)]
pub struct Icons {
    cache: HashMap<(I, u32), TextureHandle>,
}

impl Icons {
    fn get(&mut self, ctx: &Context, i: I, px: u32) -> TextureHandle {
        self.cache
            .entry((i, px))
            .or_insert_with(|| {
                let tree = resvg::usvg::Tree::from_str(&svg(i), &resvg::usvg::Options::default()).expect("icon svg");
                let mut map = resvg::tiny_skia::Pixmap::new(px, px).unwrap();
                let k = px as f32 / 24.0;
                resvg::render(&tree, resvg::tiny_skia::Transform::from_scale(k, k), &mut map.as_mut());
                // tiny-skia stores premultiplied colour: exactly what egui expects
                let img = egui::ColorImage { size: [px as usize, px as usize], pixels: map.pixels().iter().map(|p| Color32::from_rgba_premultiplied(p.red(), p.green(), p.blue(), p.alpha())).collect() };
                ctx.load_texture(format!("icon-{i:?}-{px}"), img, TextureOptions::LINEAR)
            })
            .clone()
    }

    /// Draw an icon centred in `rect` at `size` points, in `color`.
    pub fn draw(&mut self, ui: &Ui, i: I, rect: Rect, size: f32, color: Color32) {
        let ppp = ui.ctx().pixels_per_point();
        let px = (size * ppp).round().max(1.0) as u32;
        let tex = self.get(ui.ctx(), i, px);
        let r = Rect::from_center_size(rect.center(), egui::vec2(size, size));
        ui.painter().image(tex.id(), r, Rect::from_min_max(egui::pos2(0.0, 0.0), egui::pos2(1.0, 1.0)), color);
    }
}
