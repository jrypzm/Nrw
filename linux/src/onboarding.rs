//! First launch, in four calm steps: welcome → birthday → lifespan → your life fills in.

use crate::app::App;
use crate::life;
use crate::store::Est;
use crate::ui::*;
use chrono::{Datelike, NaiveDate};
use egui::{Align2, Color32, Id, Pos2, Rect, Ui, Vec2};

pub struct Ob { pub step: usize, pub date: NaiveDate, pub span: u32, pub est: Option<Est>, pub at: f64 }
impl Default for Ob { fn default() -> Self { Ob { step: 0, date: NaiveDate::from_ymd_opt(1995, 6, 15).unwrap(), span: 80, est: None, at: 0.0 } } }
impl Ob { pub fn birth(&self) -> NaiveDate { self.date } }

/// The name, with its period filling up to a life two-thirds lived.
pub fn wordmark(ui: &Ui, pos: Pos2, size: f32, fill: f32) {
    let p = ui.painter();
    let r = text(p, pos, Align2::LEFT_BOTTOM, "yrs", F::DisplayBold, size, FG, -0.065);
    let d = size * 0.24;
    let c = Pos2::new(r.right() + d * 0.15 + d / 2.0, r.bottom() - size * 0.235 - d / 2.0);
    partial_dot(p, c, d / 2.0, fill, FG, PARTIAL);
}

pub fn show(app: &mut App, ui: &mut Ui, full: Rect, now: f64) {
    if app.ob.at == 0.0 { app.ob.at = now; }
    let ob_t = now - app.ob.at;
    let k = if app.still { 1.0 } else { ease_out((ob_t / 0.55) as f32) };
    if k < 1.0 { ui.ctx().request_repaint(); }
    let col_w = 560.0f32.min(full.width() - 80.0);
    let col = Rect::from_center_size(full.center() + Vec2::new(0.0, (1.0 - k) * 18.0), Vec2::new(col_w, (full.height() - 80.0).min(720.0)));
    let mut child = ui.new_child(egui::UiBuilder::new().max_rect(col).id_salt(("ob", app.ob.step)));
    child.multiply_opacity(k);
    let ui = &mut child;
    let p = ui.painter().clone();
    let step = app.ob.step;
    // progress and back
    if step > 0 {
        for i in 1..=3 {
            let w = tweenf(ui.ctx(), Id::new(("obp", i)), if i == step { 24.0 } else { 7.0 }, 0.35);
            let x = col.center().x - 34.0 + (i as f32 - 1.0) * 30.0;
            p.rect_filled(Rect::from_center_size(Pos2::new(x, col.top() + 10.0), Vec2::new(w, 7.0)), egui::CornerRadius::same(4), if i <= step { FG } else { FAINT });
        }
        if round(ui, Pos2::new(col.left() + 18.0, col.top() + 10.0), 18.0, Id::new("obback"), |p, c, col| chevron(p, c, col, true)) { app.ob.step -= 1; app.ob.at = now; }
    }
    let btn = Rect::from_min_size(Pos2::new(col.left(), col.bottom() - 56.0), Vec2::new(col_w, 56.0));
    let label = match step { 0 => "Begin", 3 => "Enter yrs.", _ => "Continue" };
    let enter = ui.ctx().input(|i| i.key_pressed(egui::Key::Enter)) && app.ov.is_none();
    match step {
        0 => {
            let fill = if app.still { 0.62 } else { 0.62 * ease_in_out(((ob_t - 0.25) / 1.6) as f32) };
            if ob_t < 2.0 { ui.ctx().request_repaint(); }
            wordmark(ui, Pos2::new(col.left() - 6.0, col.top() + 300.0), 148.0, fill);
            let tk = if app.still { 1.0 } else { ease_out(((ob_t - 1.4) / 0.7) as f32) };
            let y = col.top() + 340.0 + (1.0 - tk) * 14.0;
            let g = galley(ui.ctx(), "Your life,\nin dots.", F::DisplayBold, 48.0, alpha(FG, tk), -0.03, f32::INFINITY, Some(52.0));
            p.galley(Pos2::new(col.left(), y), g, FG);
            para(&p, Pos2::new(col.left(), y + 128.0), "Every year you've lived, and every year still ahead. Seen at a glance, so you spend them well.", F::Medium, 17.0, alpha(MUTE, tk), col_w - 40.0, 1.5);
            text(&p, Pos2::new(col.center().x, btn.bottom() + 26.0), Align2::CENTER_CENTER, "Private by design. Nothing leaves this computer.", F::Body, 12.5, DIM, 0.0);
        }
        1 => {
            let g = galley(ui.ctx(), "When were\nyou born?", F::DisplayBold, 48.0, FG, -0.03, f32::INFINITY, Some(52.0));
            p.galley(Pos2::new(col.left(), col.top() + 70.0), g, FG);
            text(&p, Pos2::new(col.left(), col.top() + 192.0), Align2::LEFT_TOP, "Only you will ever see this. Nothing leaves this computer.", F::Medium, 16.0, MUTE, 0.0);
            let r = Rect::from_min_size(Pos2::new(col.left(), col.top() + 250.0), Vec2::new(col_w, 220.0));
            if let Some(d) = date_wheels(ui, r, app.ob.date, 1920, life::today().year(), Id::new("obw")) { app.ob.date = d.min(life::today()); }
            let days = (life::today() - app.ob.date).num_days();
            text(&p, Pos2::new(col.left(), r.bottom() + 34.0), Align2::LEFT_CENTER, &if days <= 0 { "Welcome to the world.".to_string() } else { format!("You've been alive for {} days.", life::group(days)) }, F::Medium, 17.0, MUTE, 0.0);
            text(&p, Pos2::new(col.left(), r.bottom() + 62.0), Align2::LEFT_CENTER, "Scroll or drag the wheels.", F::Body, 13.0, DIM, 0.0);
        }
        2 => {
            let g = galley(ui.ctx(), "How long\nis a life?", F::DisplayBold, 48.0, FG, -0.03, f32::INFINITY, Some(52.0));
            p.galley(Pos2::new(col.left(), col.top() + 70.0), g, FG);
            text(&p, Pos2::new(col.left(), col.top() + 192.0), Align2::LEFT_TOP, "80 years is a fair average. You can refine it later.", F::Medium, 16.0, MUTE, 0.0);
            let n = text(&p, Pos2::new(col.left() - 6.0, col.top() + 236.0), Align2::LEFT_TOP, &app.ob.span.to_string(), F::Thin, 120.0, FG, -0.05);
            text(&p, Pos2::new(n.right() + 8.0, n.bottom() - 30.0), Align2::LEFT_BOTTOM, "years", F::Medium, 18.0, MUTE, 0.0);
            let plus = |p: &egui::Painter, c: Pos2, col: Color32| { p.line_segment([c + Vec2::new(-6.0, 0.0), c + Vec2::new(6.0, 0.0)], egui::Stroke::new(1.8, col)); p.line_segment([c + Vec2::new(0.0, -6.0), c + Vec2::new(0.0, 6.0)], egui::Stroke::new(1.8, col)); };
            let minus = |p: &egui::Painter, c: Pos2, col: Color32| { p.line_segment([c + Vec2::new(-6.0, 0.0), c + Vec2::new(6.0, 0.0)], egui::Stroke::new(1.8, col)); };
            if round(ui, Pos2::new(col.right() - 24.0, n.center().y), 24.0, Id::new("ob+"), plus) { app.ob.span = (app.ob.span + 1).min(120); }
            if round(ui, Pos2::new(col.right() - 84.0, n.center().y), 24.0, Id::new("ob-"), minus) { app.ob.span = app.ob.span.saturating_sub(1).max(40); }
            let cw = (col_w - 32.0) / 5.0;
            for (i, v) in [70u32, 75, 80, 85, 90].iter().enumerate() {
                let r = Rect::from_min_size(Pos2::new(col.left() + i as f32 * (cw + 8.0), n.bottom() + 12.0), Vec2::new(cw, 40.0));
                if pill(ui, r, &v.to_string(), app.ob.span == *v, Id::new(("chip", i))) { app.ob.span = *v; }
            }
            let er = Rect::from_min_size(Pos2::new(col.left(), n.bottom() + 70.0), Vec2::new(250.0, 42.0));
            if pill(ui, er, "Estimate it from your life  ›", false, Id::new("obest")) { crate::sheets::open_estimate(app, true, now); }
            let a = life::age_at(app.ob.date, life::now()).min(app.ob.span as f64);
            let gy = n.bottom() + 136.0;
            let gw = (btn.top() - 50.0 - gy) / ((app.ob.span as f32 / 10.0).ceil()) * 10.0;
            let gw = gw.min(col_w * 0.7);
            mini_grid(ui, Pos2::new(col.left(), gy), gw, app.ob.span as usize, a);
            let end = life::birthday_in(app.ob.date, app.ob.date.year() + app.ob.span as i32);
            text(&p, Pos2::new(col.left() + gw + 24.0, gy + 8.0), Align2::LEFT_TOP, &format!("{} years", life::whole(app.ob.span as f64 - a)), F::Display, 22.0, FG, -0.02);
            text(&p, Pos2::new(col.left() + gw + 24.0, gy + 38.0), Align2::LEFT_TOP, &format!("{} days left", life::group((end - life::today()).num_days().max(0))), F::Medium, 15.0, MUTE, 0.0);
        }
        _ => {
            let g = galley(ui.ctx(), "This is\nyour life.", F::DisplayBold, 48.0, FG, -0.03, f32::INFINITY, Some(52.0));
            p.galley(Pos2::new(col.left(), col.top() + 70.0), g, FG);
            text(&p, Pos2::new(col.left(), col.top() + 192.0), Align2::LEFT_TOP, "Each dot is one year.", F::Medium, 16.0, MUTE, 0.0);
            let a = life::age_at(app.ob.date, life::now()).min(app.ob.span as f64);
            let dur = (0.9 + a * 0.045).min(3.8);
            let t = if app.still { 1.0 } else { (((ob_t - 0.35) / dur) as f32).clamp(0.0, 1.0) };
            if t < 1.0 { ui.ctx().request_repaint(); }
            let gy = col.top() + 240.0;
            let rows = (app.ob.span as f32 / 10.0).ceil();
            let gw = ((btn.top() - 110.0 - gy) / rows * 10.0).min(col_w * 0.75);
            mini_grid(ui, Pos2::new(col.left(), gy), gw, app.ob.span as usize, a * ease_in_out(t) as f64);
            let dk = if app.still { 1.0 } else { ease_out(((ob_t - 0.35 - dur) / 0.6) as f32) };
            let lived = a.floor() as i32;
            text(&p, Pos2::new(col.left(), gy + gw / 10.0 * rows + 26.0 + (1.0 - dk) * 10.0), Align2::LEFT_TOP, &format!("{lived} {} lived. {} to go.", if lived == 1 { "year" } else { "years" }, life::whole(app.ob.span as f64 - a)), F::Display, 24.0, alpha(FG, dk), -0.02);
            text(&p, Pos2::new(col.left(), gy + gw / 10.0 * rows + 60.0 + (1.0 - dk) * 10.0), Align2::LEFT_TOP, "Make the next one count.", F::Medium, 16.0, alpha(MUTE, dk), 0.0);
        }
    }
    if primary(ui, btn, label, Id::new("obgo")) || enter {
        if step < 3 { app.ob.step += 1; app.ob.at = now; }
        else {
            app.s.birth = Some(app.ob.date.to_string());
            app.s.span = app.ob.span;
            app.s.reviewed_age = life::age(app.ob.date) as i32;
            if app.ob.est.is_some() { app.s.est = app.ob.est.clone(); }
            crate::store::save(&app.s);
        }
    }
    let _ = Color32::BLACK;
}

fn mini_grid(ui: &Ui, o: Pos2, w: f32, n: usize, a: f64) {
    let cell = w / 10.0;
    let p = ui.painter();
    let whole = a.floor() as usize;
    for i in 0..n {
        let c = Pos2::new(o.x + (i % 10) as f32 * cell + cell / 2.0, o.y + (i / 10) as f32 * cell + cell / 2.0);
        if i < whole { p.circle_filled(c, cell * 0.3, FG); } else if i == whole { partial_dot(p, c, cell * 0.3, (a - whole as f64) as f32, FG, PARTIAL); } else { p.circle_filled(c, cell * 0.3, EMPTY); }
    }
}
