//! The look: black, white words, one colour borrowed from the cover. Inter everywhere.

use egui::{text::LayoutJob, Color32, FontData, FontDefinitions, FontFamily, FontId, Galley, Stroke, TextFormat, Ui};
use std::sync::Arc;

pub const BLACK: Color32 = Color32::from_rgb(0, 0, 0);
pub const WHITE: Color32 = Color32::from_rgb(255, 255, 255);
pub const MUTE: Color32 = Color32::from_rgb(142, 142, 147);
pub const DIM: Color32 = Color32::from_rgb(99, 99, 102);
pub const FAINT: Color32 = Color32::from_rgb(72, 72, 74);
pub const GHOST: Color32 = Color32::from_rgb(44, 44, 46);
pub const RAIL: Color32 = Color32::from_rgb(38, 38, 40);
pub const EDGE: Color32 = Color32::from_rgb(35, 35, 38);
pub const HAIR: Color32 = Color32::from_rgb(22, 22, 24);
pub const TILE: Color32 = Color32::from_rgb(17, 17, 19);
pub const LIFT: Color32 = Color32::from_rgb(12, 12, 14);
pub const ACCENT: Color32 = Color32::from_rgb(242, 162, 122);

#[derive(Clone, Copy)]
pub enum F {
    Regular,
    Medium,
    Semi,
    Display,
}

pub fn font(f: F, size: f32) -> FontId {
    let name = match f {
        F::Regular => "regular",
        F::Medium => "medium",
        F::Semi => "semi",
        F::Display => "display",
    };
    FontId::new(size, FontFamily::Name(name.into()))
}

pub fn install_fonts(ctx: &egui::Context) {
    let mut defs = FontDefinitions::default();
    let add = |defs: &mut FontDefinitions, key: &str, bytes: &'static [u8]| {
        defs.font_data.insert(key.into(), Arc::new(FontData::from_static(bytes)));
        let mut list = vec![key.to_string()];
        // keep egui's own fonts behind ours for symbols Inter lacks
        list.extend(defs.families.get(&FontFamily::Proportional).cloned().unwrap_or_default());
        defs.families.insert(FontFamily::Name(key.into()), list);
    };
    add(&mut defs, "regular", include_bytes!("../assets/fonts/inter_regular.ttf"));
    add(&mut defs, "medium", include_bytes!("../assets/fonts/inter_medium.ttf"));
    add(&mut defs, "semi", include_bytes!("../assets/fonts/inter_semibold.ttf"));
    add(&mut defs, "display", include_bytes!("../assets/fonts/interdisplay_bold.ttf"));
    defs.families.get_mut(&FontFamily::Proportional).unwrap().insert(0, "regular".into());
    ctx.set_fonts(defs);
}

/// Menus and text fields in the same black.
pub fn install_style(ctx: &egui::Context) {
    let mut v = egui::Visuals::dark();
    v.panel_fill = BLACK;
    v.window_fill = BLACK;
    v.extreme_bg_color = BLACK;
    v.window_stroke = Stroke::new(1.0_f32, EDGE);
    v.window_corner_radius = egui::CornerRadius::same(14);
    v.menu_corner_radius = egui::CornerRadius::same(12);
    v.window_shadow = egui::epaint::Shadow { offset: [0, 18], blur: 50, spread: 0, color: Color32::from_black_alpha(220) };
    v.popup_shadow = v.window_shadow;
    v.selection.bg_fill = Color32::from_rgb(58, 58, 62);
    v.selection.stroke = Stroke::new(1.0_f32, WHITE);
    v.text_cursor.stroke = Stroke::new(1.5_f32, WHITE);
    for w in [&mut v.widgets.inactive, &mut v.widgets.hovered, &mut v.widgets.active, &mut v.widgets.open, &mut v.widgets.noninteractive] {
        w.bg_stroke = Stroke::NONE;
        w.corner_radius = egui::CornerRadius::same(8);
    }
    v.widgets.inactive.weak_bg_fill = Color32::TRANSPARENT;
    v.widgets.inactive.bg_fill = Color32::TRANSPARENT;
    v.widgets.hovered.weak_bg_fill = Color32::from_rgb(24, 24, 26);
    v.widgets.hovered.bg_fill = Color32::from_rgb(24, 24, 26);
    v.widgets.active.weak_bg_fill = Color32::from_rgb(30, 30, 33);
    v.widgets.open.weak_bg_fill = Color32::from_rgb(24, 24, 26);
    v.widgets.inactive.fg_stroke = Stroke::new(1.0_f32, WHITE);
    v.widgets.hovered.fg_stroke = Stroke::new(1.0_f32, WHITE);
    v.widgets.noninteractive.fg_stroke = Stroke::new(1.0_f32, MUTE);
    ctx.set_visuals(v);
    ctx.style_mut(|s| {
        s.spacing.menu_margin = egui::Margin::same(8);
        s.spacing.button_padding = egui::vec2(12.0, 8.0);
        s.spacing.item_spacing = egui::vec2(8.0, 2.0);
        s.spacing.scroll = egui::style::ScrollStyle::floating();
        s.spacing.scroll.floating_allocated_width = 0.0;
        s.text_styles.insert(egui::TextStyle::Button, font(F::Medium, 14.0));
        s.text_styles.insert(egui::TextStyle::Body, font(F::Regular, 14.0));
        s.interaction.selectable_labels = false;
        s.animation_time = 0.18;
    });
}

/// Lay out one line of text. `width` truncates with an ellipsis; `spacing` tracks the letters.
pub fn line(ui: &Ui, text: &str, f: FontId, color: Color32, width: f32, spacing: f32) -> Arc<Galley> {
    let mut job = LayoutJob::single_section(text.to_owned(), TextFormat { font_id: f, color, extra_letter_spacing: spacing, ..Default::default() });
    job.wrap = egui::text::TextWrapping { max_width: width, max_rows: 1, break_anywhere: true, overflow_character: Some('…') };
    ui.fonts(|fo| fo.layout_job(job))
}

/// Several lines, wrapped.
pub fn para(ui: &Ui, text: &str, f: FontId, color: Color32, width: f32, line_height: f32) -> Arc<Galley> {
    let mut job = LayoutJob::single_section(text.to_owned(), TextFormat { font_id: f, color, line_height: Some(line_height), ..Default::default() });
    job.wrap.max_width = width;
    ui.fonts(|fo| fo.layout_job(job))
}

/// Small caps-style labels: uppercase, tracked.
pub fn meta(ui: &Ui, text: &str, color: Color32, width: f32) -> Arc<Galley> {
    line(ui, &text.to_uppercase(), font(F::Semi, 11.0), color, width, 1.3)
}

pub fn mix(a: Color32, b: Color32, t: f32) -> Color32 {
    let t = t.clamp(0.0, 1.0);
    let l = |x: u8, y: u8| (x as f32 + (y as f32 - x as f32) * t).round() as u8;
    Color32::from_rgb(l(a.r(), b.r()), l(a.g(), b.g()), l(a.b(), b.b()))
}

pub fn clock(secs: f32) -> String {
    let s = secs.max(0.0) as u64;
    if s >= 3600 { format!("{}:{:02}:{:02}", s / 3600, s / 60 % 60, s % 60) } else { format!("{}:{:02}", s / 60, s % 60) }
}

pub fn count(n: usize) -> String {
    let s = n.to_string();
    let mut out = String::new();
    for (i, c) in s.chars().enumerate() {
        if i > 0 && (s.len() - i) % 3 == 0 { out.push(','); }
        out.push(c);
    }
    out
}

pub fn songs(n: usize) -> String {
    if n == 1 { "1 song".into() } else { format!("{} songs", count(n)) }
}

pub fn span(secs: f64) -> String {
    let m = (secs / 60.0).round() as u64;
    if m >= 24 * 60 { format!("{} days, {} h", m / 1440, m / 60 % 24) } else if m >= 60 { format!("{} h {} min", m / 60, m % 60) } else { format!("{m} min") }
}
