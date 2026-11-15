//! Sheets: a card that rises over a dimmed page. Esc or a click outside closes it.

use crate::app::{take, App, Ov};
use crate::data::EST_Q;
use crate::life;
use crate::store::{Est, Event, Letter, Person};
use crate::system;
use crate::ui::*;
use chrono::{Datelike, NaiveDate};
use egui::{Align2, Color32, Context, CornerRadius, Id, Pos2, Rect, Sense, Stroke, StrokeKind, Ui, Vec2};

pub fn show(app: &mut App, ctx: &Context, now: f64) {
    let sr = ctx.screen_rect();
    let t = if app.still { 1.0 } else { ((now - app_ov_at(app)) / 0.32) as f32 };
    if t < 1.0 { ctx.request_repaint(); }
    let k = ease_out(t);
    let mut close = false;
    egui::Area::new(Id::new("sheet-layer")).order(egui::Order::Foreground).fixed_pos(sr.min).show(ctx, |ui| {
        ui.set_min_size(sr.size());
        let scrim = ui.interact(sr, Id::new("scrim"), Sense::click());
        ui.painter().rect_filled(sr, if crate::chrome::maximized(ctx) { 0 } else { crate::chrome::RADIUS }, Color32::from_black_alpha((170.0 * k) as u8));
        let wide = matches!(app.ov, Some(Ov::Settings { .. }) | Some(Ov::Estimate { .. }));
        let w = (if wide { 600.0 } else { 540.0f32 }).min(sr.width() - 48.0);
        // as tall as what's inside, up to nearly the whole window
        let kind = sheet_kind(app);
        let content = ui.ctx().data(|d| d.get_temp::<f32>(Id::new(("sheeth", kind)))).unwrap_or(if wide { 860.0 } else { 420.0 });
        let h = (sr.height() - 72.0).min(if wide { 860.0 } else { 760.0 }).min(content + 48.0).max(260.0);
        let panel = Rect::from_center_size(sr.center() + Vec2::new(0.0, (1.0 - k) * 26.0), Vec2::new(w, h));
        let p = ui.painter();
        p.rect_filled(panel.translate(Vec2::new(0.0, 18.0)).expand(10.0), CornerRadius::same(40), Color32::from_black_alpha((110.0 * k) as u8));
        p.rect_filled(panel, CornerRadius::same(30), alpha(SHEET, k));
        p.rect_stroke(panel, CornerRadius::same(30), Stroke::new(1.0, alpha(HAIR2, k)), StrokeKind::Inside);
        let block = ui.interact(panel, Id::new("panel"), Sense::click());
        let _ = block;
        if round(ui, Pos2::new(panel.right() - 34.0, panel.top() + 34.0), 17.0, Id::new("close"), cross) { close = true; }
        let inner = Rect::from_min_max(panel.min + Vec2::new(36.0, 32.0), panel.max - Vec2::new(28.0, 8.0));
        let mut c = ui.new_child(egui::UiBuilder::new().max_rect(inner).id_salt("sheet"));
        c.multiply_opacity(k);
        let out = egui::ScrollArea::vertical().id_salt(("sheetscroll", kind)).auto_shrink([false, false]).show(&mut c, |ui| {
            ui.spacing_mut().item_spacing = Vec2::ZERO;
            let w = inner.width() - 12.0;
            ui.set_width(w);
            let top = ui.cursor().top();
            if body(app, ui, w, now) { close = true; }
            ui.add_space(20.0);
            ui.cursor().top() - top
        });
        let used = out.inner;
        if (used - content).abs() > 1.0 { ui.ctx().data_mut(|d| d.insert_temp(Id::new(("sheeth", kind)), used)); ui.ctx().request_repaint(); }
        if scrim.clicked() && !panel.contains(scrim.interact_pointer_pos().unwrap_or(panel.center())) { close = true; }
    });
    if close { closing(app); app.ov = None; }
}

fn app_ov_at(app: &App) -> f64 { app.ov_at_pub() }
fn sheet_kind(app: &App) -> u8 { match app.ov { Some(Ov::Settings { .. }) => 0, Some(Ov::Milestone { .. }) => 1, Some(Ov::Line { .. }) => 2, Some(Ov::Person { .. }) => 3, Some(Ov::Event { .. }) => 4, Some(Ov::Story { .. }) => 5, Some(Ov::Estimate { .. }) => 6, Some(Ov::Letter(_)) => 7, Some(Ov::Review) => 8, Some(Ov::About) => 9, None => 10 } }

/// What closing keeps: answers to the estimate, a read letter, a reviewed year.
pub fn closing(app: &mut App) {
    match &app.ov {
        Some(Ov::Estimate { e, onboarding: false, .. }) => app.s.est = Some(e.clone()),
        Some(Ov::Estimate { e, onboarding: true, .. }) => app.ob.est = Some(e.clone()),
        Some(Ov::Letter(i)) => { if let Some(l) = app.s.letters.get_mut(*i) { l.opened = true; } }
        Some(Ov::Review) => { if let Some(b) = app.s.birth() { app.s.reviewed_age = life::age(b) as i32; } }
        _ => {}
    }
}

fn title(ui: &mut Ui, w: f32, t: &str, lead: Option<&str>) {
    let r = take(ui, w - 50.0, 40.0);
    text(ui.painter(), r.left_center(), Align2::LEFT_CENTER, t, F::Display, 28.0, FG, -0.025);
    if let Some(l) = lead {
        let h = para_height(ui.ctx(), l, F::Medium, 15.5, w - 40.0, 1.45);
        let r = take(ui, w, h + 8.0);
        para(ui.painter(), r.min + Vec2::new(0.0, 4.0), l, F::Medium, 15.5, MUTE, w - 40.0, 1.45);
    }
}
fn caps(ui: &mut Ui, w: f32, t: &str) { ui.add_space(26.0); let r = take(ui, w, 24.0); cap(ui.painter(), r.min + Vec2::new(2.0, 0.0), t); }
fn note(ui: &mut Ui, w: f32, t: &str) { let h = para_height(ui.ctx(), t, F::Body, 13.0, w - 8.0, 1.5); let r = take(ui, w, h + 12.0); para(ui.painter(), r.min + Vec2::new(4.0, 8.0), t, F::Body, 13.0, MUTE, w - 8.0, 1.5); }
fn button(ui: &mut Ui, w: f32, label: &str, id: &str) -> bool { ui.add_space(22.0); let r = take(ui, w, 52.0); primary(ui, r, label, Id::new(id)) }
fn quiet_btn(ui: &mut Ui, w: f32, label: &str, id: &str) -> bool { let r = take(ui, w, 46.0); quiet(ui, r, label, Id::new(id)) }
fn group(ui: &mut Ui, w: f32, n: usize, tall: bool) -> (Rect, f32) {
    let rh = if tall { 62.0 } else { 52.0 };
    let r = take(ui, w, rh * n as f32);
    let p = ui.painter();
    p.rect_filled(r, CornerRadius::same(18), SURFACE);
    (r, rh)
}
fn nth(r: Rect, rh: f32, i: usize) -> Rect { Rect::from_min_size(Pos2::new(r.left(), r.top() + rh * i as f32), Vec2::new(r.width(), rh)) }

/// Returns true to close.
fn body(app: &mut App, ui: &mut Ui, w: f32, now: f64) -> bool {
    let mut ov = app.ov.take();
    let close = match ov.as_mut() {
        Some(Ov::Settings { birth_open }) => settings(app, ui, w, birth_open, now),
        Some(Ov::Milestone { year, name, letter }) => milestone(app, ui, w, *year, name, letter, now),
        Some(Ov::Line { date, text }) => line(app, ui, w, *date, text, now),
        Some(Ov::Person { idx, name, born, per }) => person(app, ui, w, *idx, name, born, per),
        Some(Ov::Event { idx, name, date }) => event(app, ui, w, *idx, name, date),
        Some(Ov::Story { i, fact, seen }) => story(app, ui, w, i, fact, seen),
        Some(Ov::Estimate { e, step, query, h1, h2, h3, imperial, onboarding }) => estimate(app, ui, w, e, step, query, [h1, h2, h3], imperial, *onboarding, now),
        Some(Ov::Letter(i)) => letter(app, ui, w, *i),
        Some(Ov::Review) => review(app, ui, w),
        Some(Ov::About) => about(ui, w),
        None => true,
    };
    // a sheet may have opened another one; keep that
    if app.ov.is_none() { app.ov = ov; }
    close
}

// ------------------------------------------------------------------ settings

fn settings(app: &mut App, ui: &mut Ui, w: f32, birth_open: &mut bool, now: f64) -> bool {
    title(ui, w, "Settings", None);
    let b = app.s.birth().unwrap_or_else(life::today);
    caps(ui, w, "You");
    let (g, rh) = group(ui, w, 3, false);
    let p = ui.painter().clone();
    if row(ui, nth(g, rh, 0), "Birthday", None, Id::new("s-b"), true).clicked() { *birth_open = !*birth_open; }
    text(&p, Pos2::new(g.right() - 20.0, nth(g, rh, 0).center().y), Align2::RIGHT_CENTER, &life::date_str(b), F::Body, 15.0, if *birth_open { FG } else { MUTE }, 0.0);
    row(ui, nth(g, rh, 1), "Lifespan", None, Id::new("s-l"), false);
    let d = stepper(ui, Pos2::new(g.right() - 12.0, nth(g, rh, 1).center().y), app.s.span as i64, Id::new("span"));
    if d != 0 { app.s.span = (app.s.span as i64 + d).clamp(40, 120) as u32; }
    if row(ui, nth(g, rh, 2), "Estimate my lifespan", None, Id::new("s-e"), false).clicked() { open_estimate(app, false, now); }
    text(&p, Pos2::new(g.right() - 40.0, nth(g, rh, 2).center().y), Align2::RIGHT_CENTER, if app.s.est.is_some() { "Answered" } else { "Ten questions" }, F::Body, 14.0, MUTE, 0.0);
    chevron(&p, Pos2::new(g.right() - 20.0, nth(g, rh, 2).center().y), DIM, false);
    if *birth_open {
        ui.add_space(10.0);
        let r = take(ui, w, 210.0);
        if let Some(d) = date_wheels(ui, r, b, 1920, life::today().year(), Id::new("bw")) {
            let d = d.min(life::today());
            app.s.birth = Some(d.to_string());
            // a new birthday shouldn't make old letters and reviews pop up
            app.s.reviewed_age = life::age(d) as i32;
        }
    }

    caps(ui, w, "Number");
    let r = take(ui, w, 44.0);
    if let Some(i) = segmented(ui, r, &["Classic", "Large", "Live"], app.s.style as usize, Id::new("style")) { app.s.style = i as u8; }

    caps(ui, w, "Feel");
    let (g, rh) = group(ui, w, 1, true);
    row(ui, nth(g, rh, 0), "Daylight", Some("Dots glow by day and dim at night"), Id::new("s-sun"), true);
    if toggle(ui, Pos2::new(g.right() - 40.0, nth(g, rh, 0).center().y), app.s.sun, Id::new("t-sun")) { app.s.sun = !app.s.sun; }

    caps(ui, w, "Wallpaper and lock screen");
    let pw = (w - 24.0) / 3.0;
    let r = take(ui, w, pw * 10.0 / 16.0 + 30.0);
    for (i, name) in system::WALLS.iter().enumerate() {
        let cell = Rect::from_min_size(Pos2::new(r.left() + i as f32 * (pw + 12.0), r.top()), Vec2::new(pw, pw * 10.0 / 16.0));
        let s2 = app.s.clone();
        let ppp = ui.ctx().pixels_per_point();
        let (tw, th) = ((cell.width() * ppp) as u32, (cell.height() * ppp) as u32);
        let key = format!("{}|{}|{}|{:?}|{}", tw, i, life::today(), s2.birth, s2.span);
        let tid = app.texture_pub(ui.ctx(), &format!("wall{i}"), key, || crate::paint::wallpaper(&s2, tw, th, i as u8));
        let resp = hit(ui, cell, Id::new(("wall", i)));
        let p = ui.painter();
        p.image(tid, cell, Rect::from_min_max(Pos2::ZERO, Pos2::new(1.0, 1.0)), Color32::WHITE);
        let on = app.s.wall as usize == i;
        p.rect_stroke(cell, CornerRadius::same(12), Stroke::new(if on { 2.0 } else { 1.0 }, if on { FG } else if resp.hovered() { SURFACE3 } else { HAIR2 }), StrokeKind::Outside);
        text(p, Pos2::new(cell.center().x, cell.bottom() + 16.0), Align2::CENTER_CENTER, name, F::Medium, 13.0, if on { FG } else { MUTE }, 0.0);
        if resp.clicked() { app.s.wall = i as u8; }
    }
    ui.add_space(12.0);
    let r = take(ui, w, 46.0);
    let half = (w - 10.0) / 2.0;
    let set = |app: &mut App, desk: bool, lock: bool| {
        match system::apply_wallpaper(&app.s, desk, lock, app.s.screen) { Ok(()) => app.toast(if desk { "Wallpaper set" } else { "Lock screen set" }, now), Err(e) => app.toast(&e, now) }
    };
    if pill(ui, Rect::from_min_size(r.min, Vec2::new(half, 46.0)), "Set as wallpaper", false, Id::new("setw")) { set(app, true, false); }
    if pill(ui, Rect::from_min_size(r.min + Vec2::new(half + 10.0, 0.0), Vec2::new(half, 46.0)), "Set as lock screen", false, Id::new("setl")) { set(app, false, true); }
    ui.add_space(12.0);
    let (g, rh) = group(ui, w, 2, true);
    row(ui, nth(g, rh, 0), "Keep my wallpaper up to date", Some("Redrawn when a year or a day turns over"), Id::new("s-aw"), true);
    if toggle(ui, Pos2::new(g.right() - 40.0, nth(g, rh, 0).center().y), app.s.auto_wall, Id::new("t-aw")) { app.s.auto_wall = !app.s.auto_wall; if app.s.auto_wall { set(app, true, false); system::ensure_background(&app.s); } }
    row(ui, nth(g, rh, 1), "Keep my lock screen up to date", Some("The same, on the lock screen"), Id::new("s-al"), false);
    if toggle(ui, Pos2::new(g.right() - 40.0, nth(g, rh, 1).center().y), app.s.auto_lock, Id::new("t-al")) { app.s.auto_lock = !app.s.auto_lock; if app.s.auto_lock { set(app, false, true); system::ensure_background(&app.s); } }

    caps(ui, w, "On your desktop");
    let (g, rh) = group(ui, w, 4, true);
    row(ui, nth(g, rh, 0), "Show in the system tray", Some("Your years left, one click away"), Id::new("s-tray"), true);
    if toggle(ui, Pos2::new(g.right() - 40.0, nth(g, rh, 0).center().y), app.s.tray, Id::new("t-tray")) {
        app.s.tray = !app.s.tray; crate::store::save(&app.s);
        system::stop_background(); system::ensure_background(&app.s);
    }
    row(ui, nth(g, rh, 1), "Start at login", Some("Quietly, in the tray, so your wallpaper stays true"), Id::new("s-auto"), false);
    if toggle(ui, Pos2::new(g.right() - 40.0, nth(g, rh, 1).center().y), app.s.autostart, Id::new("t-auto")) { app.s.autostart = !app.s.autostart; system::set_autostart(app.s.autostart); }
    row(ui, nth(g, rh, 2), "Monday note", Some("“You're in week 1,630 of your life.” Once a week"), Id::new("s-mon"), false);
    if toggle(ui, Pos2::new(g.right() - 40.0, nth(g, rh, 2).center().y), app.s.monday, Id::new("t-mon")) { app.s.monday = !app.s.monday; crate::store::save(&app.s); system::ensure_background(&app.s); }
    let inst = system::launcher_installed();
    row(ui, nth(g, rh, 3), "Show in the app launcher", Some("With its icon, so you can pin it to the taskbar"), Id::new("s-launch"), false);
    if toggle(ui, Pos2::new(g.right() - 40.0, nth(g, rh, 3).center().y), inst, Id::new("t-launch")) { if inst { system::remove_launcher() } else { system::install_launcher() } }

    caps(ui, w, "Keyboard");
    let keys = [("1 – 5", "Pages"), ("← →", "Previous and next page"), ("+  −", "Zoom the Life grid"), ("U", "Count another way"), ("L", "Left or lived"), ("P", "Percentage"), ("T", "Write today's line"), ("S", "Settings"), ("Esc", "Close")];
    let (g, rh) = group(ui, w, keys.len(), false);
    for (i, (k, d)) in keys.iter().enumerate() {
        let r = nth(g, rh, i);
        let p = ui.painter();
        if i > 0 { p.line_segment([Pos2::new(r.left() + 18.0, r.top()), Pos2::new(r.right(), r.top())], Stroke::new(1.0, HAIR2)); }
        text(p, Pos2::new(r.left() + 18.0, r.center().y), Align2::LEFT_CENTER, d, F::Body, 15.0, FG, 0.0);
        let kw = width_of(ui.ctx(), k, F::Semi, 12.5, 0.02) + 18.0;
        let kr = Rect::from_center_size(Pos2::new(r.right() - 18.0 - kw / 2.0, r.center().y), Vec2::new(kw, 26.0));
        p.rect_filled(kr, CornerRadius::same(7), SURFACE2);
        text(p, kr.center(), Align2::CENTER_CENTER, k, F::Semi, 12.5, FG, 0.02);
    }

    caps(ui, w, "About");
    let (g, rh) = group(ui, w, 1, true);
    if row(ui, nth(g, rh, 0), "About yrs.", Some("A one-person, open source project"), Id::new("s-about"), true).clicked() { app.open(Ov::About, now); }
    chevron(ui.painter(), Pos2::new(g.right() - 20.0, g.center().y), DIM, false);
    note(ui, w, "Everything stays on this computer, in ~/.local/share/yrs. Nothing is ever sent anywhere.");
    button(ui, w, "Done", "s-done")
}

pub fn open_estimate(app: &mut App, onboarding: bool, now: f64) {
    let saved = if onboarding { app.ob.est.clone() } else { app.s.est.clone() };
    let first = saved.is_some() && !onboarding;
    let e = saved.unwrap_or_else(|| Est { country: life::guess_country(), sex: String::new(), picks: Default::default() });
    let imperial = std::env::var("LANG").map(|l| l.contains("_US")).unwrap_or(false);
    app.open(Ov::Estimate { e, step: if first { EST_Q.len() + 1 } else { 0 }, query: String::new(), h1: String::new(), h2: String::new(), h3: String::new(), imperial, onboarding }, now);
}

// ------------------------------------------------------------------ a year: name it, write to it, see the world

fn milestone(app: &mut App, ui: &mut Ui, w: f32, year: i32, name: &mut String, letter: &mut String, _now: f64) -> bool {
    let b = app.s.birth().unwrap();
    let lived = life::age(b) as i32;
    let past = year <= lived;
    title(ui, w, &format!("Age {year} · {}", b.year() + year), Some(if past { "What happened this year?" } else { "What do you want this year to hold?" }));
    ui.add_space(14.0);
    let r = take(ui, w, 52.0);
    let resp = field(ui, r, name, if past { "Moved to a new city" } else { "See the northern lights" }, false, Id::new(("mname", year)), 40);
    if !ui.ctx().memory(|m| m.focused().is_some()) && name.is_empty() { resp.request_focus(); }
    note(ui, w, "Named years get a thin ring.");
    ui.add_space(18.0);
    let key = format!("year{year}");
    let lines = app.lines.clone();
    let list = life::year_facts(&app.s, year, &lines);
    year_card(app, ui, w, &key, if past { "That year" } else { "That year, ahead" }, list);
    if !past {
        let sealed = app.s.letters.iter().any(|l| l.age == year && !l.opened);
        caps(ui, w, &format!("A letter to yourself at {year}"));
        if sealed { note(ui, w, "A letter is sealed for this year. Writing a new one replaces it."); ui.add_space(8.0); }
        let r = take(ui, w, 150.0);
        field(ui, r, letter, &format!("Dear me at {year}…"), true, Id::new(("mletter", year)), 2000);
        note(ui, w, "It stays sealed until your birthday that year. Not even you can read it before then.");
    }
    let enter = ui.ctx().input(|i| i.key_pressed(egui::Key::Enter) && !i.modifiers.shift) && !ui.ctx().memory(|m| m.focused() == Some(Id::new(("mletter", year))));
    if button(ui, w, "Save", "m-save") || enter {
        let n = name.trim().chars().take(40).collect::<String>();
        if n.is_empty() { app.s.names.remove(&year); } else { app.s.names.insert(year, n); }
        let l = letter.trim().to_string();
        if !past && !l.is_empty() {
            app.s.letters.retain(|x| x.age != year || x.opened);
            app.s.letters.push(Letter { age: year, text: l, written: life::today().to_string(), opened: false });
        }
        return true;
    }
    if app.s.names.contains_key(&year) && quiet_btn(ui, w, "Remove name", "m-del") { app.s.names.remove(&year); return true; }
    false
}

fn year_card(app: &mut App, ui: &mut Ui, w: f32, key: &str, caption: &str, list: Vec<String>) {
    let f = app.fact_pub(key, &list);
    let th = para_height(ui.ctx(), &f, F::Medium, 17.0, w - 44.0, 1.42);
    let r = take(ui, w, th + 92.0);
    let resp = hit(ui, r, Id::new(("ycard", key)));
    let p = ui.painter();
    card(p, r, 22, if resp.hovered() { CARD_HI } else { CARD });
    cap(p, r.min + Vec2::new(22.0, 22.0), caption);
    para(p, r.min + Vec2::new(22.0, 48.0), &f, F::Medium, 17.0, FG, w - 44.0, 1.42);
    text(p, Pos2::new(r.left() + 22.0, r.bottom() - 22.0), Align2::LEFT_BOTTOM, "Click for another", F::Body, 12.5, if resp.hovered() { MUTE } else { DIM }, 0.0);
    if resp.clicked() { app.next_fact_pub(key, list); }
}

fn line(app: &mut App, ui: &mut Ui, w: f32, date: NaiveDate, txt: &mut String, _now: f64) -> bool {
    let b = app.s.birth().unwrap();
    title(ui, w, &format!("Day {}", life::group(life::day_of_life(b, date))), Some(&format!("{} {}", life::WEEKDAYS[date.weekday().num_days_from_monday() as usize], life::date_str(date))));
    ui.add_space(14.0);
    let r = take(ui, w, 110.0);
    let id = Id::new(("line", date));
    let resp = field(ui, r, txt, "One line. What made today today?", true, id, 140);
    if ui.ctx().memory(|m| m.focused().is_none()) { resp.request_focus(); }
    note(ui, w, &format!("{}/140 · One line is enough. In ten years you'll be glad you wrote it.", txt.chars().count()));
    let enter = ui.ctx().input(|i| i.key_pressed(egui::Key::Enter) && !i.modifiers.shift);
    if button(ui, w, "Save", "l-save") || enter {
        let v = txt.trim().replace('\n', " ");
        if v.is_empty() { app.lines.remove(&date.to_string()); } else { app.lines.insert(date.to_string(), v); }
        crate::store::save_lines(&app.lines);
        return true;
    }
    false
}

fn person(app: &mut App, ui: &mut Ui, w: f32, idx: Option<usize>, name: &mut String, born: &mut i32, per: &mut u32) -> bool {
    title(ui, w, if idx.is_none() { "Add someone" } else { "Someone you love" }, None);
    ui.add_space(14.0);
    let r = take(ui, w, 52.0);
    let resp = field(ui, r, name, "Mum", false, Id::new("pname"), 24);
    if idx.is_none() && ui.ctx().memory(|m| m.focused().is_none()) && name.is_empty() { resp.request_focus(); }
    caps(ui, w, "Born in");
    let r = take(ui, w, 170.0);
    let years: Vec<String> = (1900..=life::today().year()).map(|y| y.to_string()).collect();
    let p = ui.painter();
    p.rect_filled(Rect::from_center_size(r.center(), Vec2::new(w, 42.0)), CornerRadius::same(14), SURFACE);
    if let Some(i) = wheel(ui, Rect::from_center_size(r.center(), Vec2::new(160.0, 170.0)), &years, (*born - 1900).max(0) as usize, Id::new("pyear")) { *born = 1900 + i as i32; }
    caps(ui, w, "You see each other");
    let (g, rh) = group(ui, w, life::OFTEN.len(), false);
    for (i, (l, v)) in life::OFTEN.iter().enumerate() {
        if row(ui, nth(g, rh, i), l, None, Id::new(("often", i)), i == 0).clicked() { *per = *v; }
        if *per == *v { let c = Pos2::new(g.right() - 26.0, nth(g, rh, i).center().y); ui.painter().line_segment([c + Vec2::new(-6.0, 0.0), c + Vec2::new(-2.0, 4.0)], Stroke::new(2.0, FG)); ui.painter().line_segment([c + Vec2::new(-2.0, 4.0), c + Vec2::new(6.0, -5.0)], Stroke::new(2.0, FG)); }
    }
    if button(ui, w, "Save", "p-save") {
        let n = name.trim().chars().take(24).collect::<String>();
        if n.is_empty() { return true; }
        let pp = Person { name: n, born: *born, per: *per };
        match idx { Some(i) if i < app.s.people.len() => app.s.people[i] = pp, _ => app.s.people.push(pp) }
        return true;
    }
    if let Some(i) = idx { if quiet_btn(ui, w, "Remove", "p-del") { if i < app.s.people.len() { app.s.people.remove(i); } return true; } }
    false
}

fn event(app: &mut App, ui: &mut Ui, w: f32, idx: Option<usize>, name: &mut String, date: &mut NaiveDate) -> bool {
    title(ui, w, if idx.is_none() { "Add a countdown" } else { "Countdown" }, None);
    ui.add_space(14.0);
    let r = take(ui, w, 52.0);
    let resp = field(ui, r, name, "Trip to Japan", false, Id::new("ename"), 30);
    if idx.is_none() && ui.ctx().memory(|m| m.focused().is_none()) && name.is_empty() { resp.request_focus(); }
    caps(ui, w, "On");
    let t = life::today();
    let r = take(ui, w, 210.0);
    if let Some(d) = date_wheels(ui, r, *date, t.year().min(date.year()), t.year() + 30, Id::new("ew")) { *date = d.max(t.min(*date)); }
    let days = (*date - t).num_days();
    note(ui, w, &if days > 0 { format!("In {} {}.", life::group(days), if days == 1 { "day" } else { "days" }) } else if days == 0 { "Today.".into() } else { "Already passed.".into() });
    if button(ui, w, "Save", "e-save") {
        let n = name.trim().chars().take(30).collect::<String>();
        if n.is_empty() { return true; }
        let e = Event { name: n, date: date.to_string() };
        match idx { Some(i) if i < app.s.events.len() => app.s.events[i] = e, _ => app.s.events.push(e) }
        return true;
    }
    if let Some(i) = idx { if quiet_btn(ui, w, "Remove", "e-del") { if i < app.s.events.len() { app.s.events.remove(i); } return true; } }
    false
}

// ------------------------------------------------------------------ a story for every history dot

fn story(app: &mut App, ui: &mut Ui, w: f32, i: &mut usize, fact: &mut String, seen: &mut Vec<String>) -> bool {
    let b = app.s.birth().unwrap();
    let last = life::LIFETIMES - 1;
    let mine = *i == last;
    let y0 = life::life_start(b.year(), *i);
    let list = life::life_facts(&app.s, *i);
    let r = take(ui, w, 22.0);
    cap(ui.painter(), r.min, if mine { "Your life" } else { life::era_name(y0) });
    title(ui, w, &if mine { format!("You, since {}", b.year()) } else { format!("About {} years ago", life::group((life::today().year() - y0) as i64)) },
        Some(&if mine { "Things that are true about the life you’re living".to_string() } else { format!("{} to {} · life {} of {}", life::year_label(y0), life::year_label(y0 + 80), life::group(*i as i64 + 1), life::group(life::LIFETIMES as i64)) }));
    ui.add_space(20.0);
    let th = para_height(ui.ctx(), fact, F::Medium, 23.0, w - 56.0, 1.38);
    let r = take(ui, w, (th + 60.0).max(220.0));
    card(ui.painter(), r, 26, CARD);
    let at = ui.ctx().data(|d| d.get_temp::<f64>(Id::new("storyat"))).unwrap_or(-10.0);
    let k = if app.still { 1.0 } else { ease_out(((ui.ctx().input(|x| x.time) - at) / 0.55) as f32) };
    if k < 1.0 { ui.ctx().request_repaint(); }
    para(ui.painter(), Pos2::new(r.left() + 28.0, r.center().y - th / 2.0 + (1.0 - k) * 12.0), fact, F::Medium, 23.0, alpha(FG, k), w - 56.0, 1.38);
    let n = seen.len().min(list.len());
    note(ui, w, &format!("{n} of {} things this {} can tell you{}", list.len(), if mine { "part of the grid" } else { "life" }, if n >= list.len() { ". They’ll come round again." } else { "" }));
    let mut another = button(ui, w, "Tell me another", "st-more");
    if ui.ctx().input(|x| x.key_pressed(egui::Key::Space) || x.key_pressed(egui::Key::Enter)) { another = true; }
    if another {
        let fresh: Vec<String> = list.iter().filter(|f| !seen.contains(f)).cloned().collect();
        if fresh.is_empty() { seen.clear(); }
        *fact = life::pick(if fresh.is_empty() { &list } else { &fresh }, fact);
        seen.push(fact.clone());
        let t = ui.ctx().input(|x| x.time);
        ui.ctx().data_mut(|d| d.insert_temp(Id::new("storyat"), t));
    }
    let r = take(ui, w, 46.0);
    let half = w / 2.0;
    let mut mv: i32 = 0;
    if *i > 0 && (quiet(ui, Rect::from_min_size(r.min, Vec2::new(half, 46.0)), "‹  Older life", Id::new("older")) || ui.ctx().input(|x| x.key_pressed(egui::Key::ArrowLeft))) { mv = -1; }
    if !mine && (quiet(ui, Rect::from_min_size(r.min + Vec2::new(half, 0.0), Vec2::new(half, 46.0)), "Newer life  ›", Id::new("newer")) || ui.ctx().input(|x| x.key_pressed(egui::Key::ArrowRight))) { mv = 1; }
    if mv != 0 {
        *i = (*i as i32 + mv).clamp(0, last as i32) as usize;
        let l = life::life_facts(&app.s, *i);
        *fact = life::pick(&l, ""); seen.clear(); seen.push(fact.clone());
        let t = ui.ctx().input(|x| x.time);
        ui.ctx().data_mut(|d| d.insert_temp(Id::new("storyat"), t));
    }
    false
}

// ------------------------------------------------------------------ estimate my lifespan

fn sex_word(s: &str) -> &'static str { match s { "f" => "women", "m" => "men", _ => "people" } }

#[allow(clippy::too_many_arguments)]
fn estimate(app: &mut App, ui: &mut Ui, w: f32, e: &mut Est, step: &mut usize, query: &mut String, h: [&mut String; 3], imperial: &mut bool, onboarding: bool, _now: f64) -> bool {
    let b = if onboarding { app.ob.birth() } else { app.s.birth().unwrap() };
    let total = EST_Q.len() + 2;
    let est = life::estimate(e, b);
    // the top: progress, and the estimate so far
    let r = take(ui, w - 50.0, 72.0);
    let p = ui.painter().clone();
    for k in 0..total {
        let x = r.left() + k as f32 * 15.0 + 4.0;
        let on = k <= *step;
        let rr = tweenf(ui.ctx(), Id::new(("pd", k)), if k == *step { 4.5 } else { 3.0 }, 0.25);
        p.circle_filled(Pos2::new(x, r.bottom() - 14.0), rr, if on { FG } else { SURFACE3 });
    }
    let v = tween(ui.ctx(), Id::new("estv"), est.years, 0.7, None);
    let a = text(&p, Pos2::new(r.right(), r.top() + 2.0), Align2::RIGHT_TOP, &format!("{v:.1}"), F::Thin, 48.0, FG, -0.045);
    text(&p, Pos2::new(r.right(), a.bottom() + 2.0), Align2::RIGHT_TOP, if *step == total - 1 { "years, your estimate" } else { "years, so far" }, F::Body, 12.0, MUTE, 0.0);
    ui.add_space(14.0);
    let qsize = 30.0;
    let q_title = |ui: &mut Ui, t: &str| { let hh = para_height(ui.ctx(), t, F::DisplayBold, qsize, w - 20.0, 1.1); let r = take(ui, w, hh + 4.0); para(ui.painter(), r.min, t, F::DisplayBold, qsize, FG, w - 20.0, 1.1); };
    if *step == 0 {
        q_title(ui, "Let’s start with you.");
        note(ui, w, "Where you live and your sex set the starting point: the average for people like you, from the UN.");
        caps(ui, w, "Where you live");
        let r = take(ui, w, 52.0);
        field(ui, r, query, "Search countries", false, Id::new("country"), 40);
        ui.add_space(8.0);
        let q = query.trim().to_lowercase();
        let hits: Vec<&crate::data::Country> = if q.is_empty() { vec![life::country(&e.country)] } else {
            let mut v: Vec<_> = crate::data::COUNTRIES.iter().filter(|c| c.iso != "WLD" && c.name.to_lowercase().contains(&q)).collect();
            v.sort_by_key(|c| c.name.to_lowercase().find(&q).unwrap_or(99)); v.truncate(6);
            if "world average".contains(&q) { v.push(life::country("WLD")); }
            v
        };
        if hits.is_empty() { note(ui, w, "No country by that name."); } else {
            let (g, rh) = group(ui, w, hits.len(), false);
            for (k, c) in hits.iter().enumerate() {
                if row(ui, nth(g, rh, k), if c.iso == "WLD" { "World average" } else { c.name }, None, Id::new(("c", c.iso)), k == 0).clicked() { e.country = c.iso.into(); query.clear(); }
                if c.iso == e.country { text(ui.painter(), Pos2::new(g.right() - 22.0, nth(g, rh, k).center().y), Align2::RIGHT_CENTER, "✓", F::Semi, 15.0, FG, 0.0); }
            }
        }
        caps(ui, w, "Sex");
        let r = take(ui, w, 44.0);
        let sel = match e.sex.as_str() { "f" => 0, "m" => 1, _ => 2 };
        if let Some(i) = segmented(ui, r, &["Woman", "Man", "Rather not say"], sel, Id::new("sex")) { e.sex = ["f", "m", ""][i].into(); }
        note(ui, w, &format!("Born today, {} in {} live {:.1} years on average. Women outlive men almost everywhere on Earth.", sex_word(&e.sex), life::place_name(&e.country), life::base_le(&e.country, &e.sex)));
        if button(ui, w, "Continue", "e-c0") { *step = 1; }
        return false;
    }
    if *step < total - 1 {
        let qi = *step - 1;
        let q = &EST_Q[qi];
        q_title(ui, q.title);
        if q.calc {
            ui.add_space(16.0);
            let r = take(ui, w, 40.0);
            if let Some(i) = segmented(ui, r, &["cm · kg", "ft · lb"], *imperial as usize, Id::new("units")) { *imperial = i == 1; for x in h.iter() { let _ = x; } }
            ui.add_space(8.0);
            let r = take(ui, w, 50.0);
            let [h1, h2, h3] = h;
            if *imperial {
                let cw = (w - 16.0) / 3.0;
                field(ui, Rect::from_min_size(r.min, Vec2::new(cw, 50.0)), h1, "ft", false, Id::new("h1"), 4);
                field(ui, Rect::from_min_size(r.min + Vec2::new(cw + 8.0, 0.0), Vec2::new(cw, 50.0)), h2, "in", false, Id::new("h2"), 4);
                field(ui, Rect::from_min_size(r.min + Vec2::new(2.0 * (cw + 8.0), 0.0), Vec2::new(cw, 50.0)), h3, "lb", false, Id::new("h3"), 5);
            } else {
                let cw = (w - 8.0) / 2.0;
                field(ui, Rect::from_min_size(r.min, Vec2::new(cw, 50.0)), h1, "Height, cm", false, Id::new("h1"), 5);
                field(ui, Rect::from_min_size(r.min + Vec2::new(cw + 8.0, 0.0), Vec2::new(cw, 50.0)), h3, "Weight, kg", false, Id::new("h3"), 5);
            }
            let num = |s: &String| s.trim().parse::<f64>().unwrap_or(0.0);
            let m = if *imperial { (num(h1) * 12.0 + num(h2)) * 0.0254 } else { num(h1) / 100.0 };
            let kg = if *imperial { num(h3) * 0.45359237 } else { num(h3) };
            if (0.9..=2.6).contains(&m) && (25.0..=350.0).contains(&kg) {
                let bmi = kg / (m * m);
                let i = if bmi < 18.5 { 0 } else if bmi < 25.0 { 1 } else if bmi < 30.0 { 2 } else if bmi < 35.0 { 3 } else if bmi < 40.0 { 4 } else { 5 };
                e.picks.insert(q.key.into(), i);
                note(ui, w, &format!("Your BMI is {bmi:.1} · {}", q.options[i].sub));
            } else { note(ui, w, "Or work it out: enter your height and weight."); }
        }
        ui.add_space(18.0);
        let picked = e.picks.get(q.key).copied();
        for (i, o) in q.options.iter().enumerate() {
            let r = take(ui, w, 60.0);
            let resp = hit(ui, r, Id::new(("opt", qi, i)));
            let on = picked == Some(i);
            let k = tweenf(ui.ctx(), Id::new(("optk", qi, i)), if on { 1.0 } else { 0.0 }, 0.2);
            let hv = tweenf(ui.ctx(), Id::new(("opth", qi, i)), if resp.hovered() { 1.0 } else { 0.0 }, 0.15);
            let p = ui.painter();
            let rr = r.shrink2(Vec2::new(0.0, 4.0));
            p.rect_filled(rr, CornerRadius::same(16), lerp_c(lerp_c(SURFACE, SURFACE2, hv), FG, k));
            let fg = lerp_c(FG, Color32::BLACK, k);
            let kx = rr.left() + 18.0;
            let kr = Rect::from_center_size(Pos2::new(kx + 10.0, rr.center().y), Vec2::new(20.0, 20.0));
            p.rect_stroke(kr, CornerRadius::same(6), Stroke::new(1.0, lerp_c(HAIR2, gray(0xd1), k)), StrokeKind::Inside);
            text(p, kr.center(), Align2::CENTER_CENTER, &(i + 1).to_string(), F::Medium, 11.5, lerp_c(MUTE, gray(0x6e), k), 0.0);
            if o.sub.is_empty() { text(p, Pos2::new(kx + 32.0, rr.center().y), Align2::LEFT_CENTER, o.label, F::Body, 16.0, fg, -0.01); }
            else { text(p, Pos2::new(kx + 32.0, rr.center().y - 9.0), Align2::LEFT_CENTER, o.label, F::Body, 16.0, fg, -0.01); text(p, Pos2::new(kx + 32.0, rr.center().y + 11.0), Align2::LEFT_CENTER, o.sub, F::Body, 12.5, lerp_c(MUTE, gray(0x6e), k), 0.0); }
            let c = Pos2::new(rr.right() - 26.0, rr.center().y);
            p.circle_stroke(c, 10.0, Stroke::new(1.5 + 5.5 * k, lerp_c(DIM, Color32::BLACK, k)));
            let key = ui.ctx().input(|x| x.events.iter().any(|ev| matches!(ev, egui::Event::Text(t) if t == &(i + 1).to_string())));
            if resp.clicked() || (key && !ui.ctx().wants_keyboard_input()) {
                e.picks.insert(q.key.into(), i);
                ui.ctx().data_mut(|d| d.insert_temp(Id::new("advance"), (ui.ctx().input(|x| x.time), *step)));
            }
        }
        // a pick moves on by itself after a moment
        if let Some((t, st)) = ui.ctx().data(|d| d.get_temp::<(f64, usize)>(Id::new("advance"))) {
            let el = ui.ctx().input(|x| x.time) - t;
            if st != *step || el > 3.0 { ui.ctx().data_mut(|d| d.remove::<(f64, usize)>(Id::new("advance"))); }
            else if el > 0.42 { *step += 1; ui.ctx().data_mut(|d| d.remove::<(f64, usize)>(Id::new("advance"))); }
            else { ui.ctx().request_repaint(); }
        }
        ui.add_space(14.0);
        let hh = para_height(ui.ctx(), q.why, F::Body, 14.0, w - 40.0, 1.5) + para_height(ui.ctx(), q.source, F::Body, 12.0, w - 40.0, 1.4);
        let r = take(ui, w, hh + 46.0);
        let p = ui.painter();
        p.rect_filled(r, CornerRadius::same(18), SURFACE);
        let y = para(p, r.min + Vec2::new(20.0, 16.0), q.why, F::Body, 14.0, MUTE, w - 40.0, 1.5);
        para(p, r.min + Vec2::new(20.0, 26.0 + y), q.source, F::Body, 12.0, DIM, w - 40.0, 1.4);
        let picked = e.picks.contains_key(q.key);
        if button(ui, w, if picked { "Continue" } else { "Skip this one" }, "e-next") || (ui.ctx().input(|x| x.key_pressed(egui::Key::Enter)) && !ui.ctx().wants_keyboard_input()) { *step += 1; }
        let r = take(ui, w, 46.0);
        if quiet(ui, Rect::from_min_size(r.min, Vec2::new(w / 2.0, 46.0)), "Back", Id::new("e-back")) { *step -= 1; }
        if picked && quiet(ui, Rect::from_min_size(r.min + Vec2::new(w / 2.0, 0.0), Vec2::new(w / 2.0, 46.0)), "Clear", Id::new("e-clear")) { e.picks.remove(q.key); }
        return false;
    }
    // the result
    let yrs = est.years.round() as i32;
    let diff = est.years - est.typical;
    let answered = EST_Q.iter().filter(|q| e.picks.contains_key(q.key)).count();
    q_title(ui, &format!("You could live to about {yrs}."));
    note(ui, w, &format!("That’s {} for {} your age in {}{}", if diff.abs() < 0.3 { "right on the average".to_string() } else { format!("{:.1} years {} than the average", diff.abs(), if diff > 0.0 { "more" } else { "less" }) }, sex_word(&e.sex), life::place_name(&e.country), if answered < EST_Q.len() { format!(", from {answered} of {} answers.", EST_Q.len()) } else { ".".into() }));
    ui.add_space(18.0);
    let gw = (w * 0.7).min(360.0);
    let cell = gw / 10.0;
    let r = take(ui, w, cell * 11.0);
    let o = Pos2::new(r.center().x - gw / 2.0, r.top());
    let p = ui.painter().clone();
    let p = &p;
    let ag = est.age_now;
    for i in 0..110 {
        let c = Pos2::new(o.x + (i % 10) as f32 * cell + cell / 2.0, o.y + (i / 10) as f32 * cell + cell / 2.0);
        let rr = cell * 0.29;
        if (i as f64) < ag.floor() { p.circle_filled(c, rr, FG); }
        else if i == ag.floor() as usize { partial_dot(p, c, rr, (ag - ag.floor()) as f32, FG, PARTIAL); }
        else { p.circle_filled(c, rr, alpha(FG, (0.07 + 0.55 * est.chance(i as f64 + 1.0).powf(1.5)) as f32)); }
    }
    let r = take(ui, w, 30.0);
    text(p, r.center(), Align2::CENTER_CENTER, "White: lived    ·    Brighter: more likely to reach", F::Body, 12.0, MUTE, 0.0);
    caps(ui, w, "The range");
    let r = take(ui, w, 118.0);
    let cw = (w - 16.0) / 3.0;
    for (k, (age, t)) in [(est.q25, "1 in 4 people like you die before this age"), (est.q50, "Half live past this"), (est.q75, "1 in 4 live past this")].iter().enumerate() {
        let c = Rect::from_min_size(Pos2::new(r.left() + k as f32 * (cw + 8.0), r.top()), Vec2::new(cw, 118.0));
        let mid = k == 1;
        let p = ui.painter();
        p.rect_filled(c, CornerRadius::same(18), if mid { FG } else { SURFACE });
        text(p, c.min + Vec2::new(16.0, 12.0), Align2::LEFT_TOP, &format!("{}", age.round() as i32), F::Light, 38.0, if mid { Color32::BLACK } else { FG }, -0.04);
        para(p, c.min + Vec2::new(16.0, 62.0), t, F::Body, 12.0, if mid { gray(0x6e) } else { MUTE }, cw - 28.0, 1.3);
    }
    caps(ui, w, "Your chances of reaching");
    for (k, a) in [80.0, 90.0, 100.0].iter().enumerate() {
        let pr = est.chance(*a);
        let r = take(ui, w, 36.0);
        let p = ui.painter();
        text(p, r.left_center(), Align2::LEFT_CENTER, &format!("{}", *a as i32), F::Body, 15.0, MUTE, 0.0);
        let bar = Rect::from_min_max(Pos2::new(r.left() + 46.0, r.center().y - 4.0), Pos2::new(r.right() - 72.0, r.center().y + 4.0));
        p.rect_filled(bar, CornerRadius::same(4), SURFACE2);
        let f = tweenf(ui.ctx(), Id::new(("ch", k)), pr as f32, 1.1).max(0.015);
        p.rect_filled(Rect::from_min_size(bar.min, Vec2::new(bar.width() * f, bar.height())), CornerRadius::same(4), FG);
        let pc = if pr >= 0.995 { "99%+".to_string() } else if pr < 0.01 { "<1%".into() } else { format!("{}%", (pr * 100.0).round()) };
        text(p, r.right_center(), Align2::RIGHT_CENTER, &pc, F::Semi, 15.0, FG, 0.0);
    }
    if !est.contrib.is_empty() {
        caps(ui, w, "What moved it");
        let (g, rh) = group(ui, w, est.contrib.len(), true);
        for (k, (qi, y)) in est.contrib.iter().enumerate() {
            let q = &EST_Q[*qi];
            let name = match q.key { "smoke" => "Smoking", "move" => "Activity", "bmi" => "Body weight", "drink" => "Alcohol", "diet" => "Diet", "sleep" => "Sleep", "social" => "People around you", _ => "Diabetes" };
            let pick = e.picks.get(q.key).map(|&i| q.options[i].label).unwrap_or("");
            row(ui, nth(g, rh, k), name, Some(pick), Id::new(("ct", k)), k == 0);
            text(ui.painter(), Pos2::new(g.right() - 20.0, nth(g, rh, k).center().y), Align2::RIGHT_CENTER, &format!("{}{:.1} yrs", if *y > 0.0 { "+" } else { "−" }, y.abs()), F::Medium, 15.0, if *y > 0.0 { FG } else { MUTE }, 0.0);
        }
    }
    ui.add_space(18.0);
    let tip = "Did you know? In 1900, a newborn could expect to live about 32 years, worldwide. Today it’s about 73. Most of the gain came from children surviving: clean water, vaccines and antibiotics.";
    let hh = para_height(ui.ctx(), tip, F::Body, 14.0, w - 40.0, 1.5);
    let r = take(ui, w, hh + 32.0);
    ui.painter().rect_filled(r, CornerRadius::same(18), SURFACE);
    para(ui.painter(), r.min + Vec2::new(20.0, 16.0), tip, F::Body, 14.0, MUTE, w - 40.0, 1.5);
    if button(ui, w, &format!("Use {yrs} years"), "e-use") {
        let y = yrs.clamp(40, 120) as u32;
        if onboarding { app.ob.span = y; app.ob.est = Some(e.clone()); } else { app.s.span = y; app.s.est = Some(e.clone()); app.toast(&format!("Lifespan set to {y}"), ui.ctx().input(|x| x.time)); }
        return true;
    }
    if quiet_btn(ui, w, "Change answers", "e-redo") { *step = 1; }
    note(ui, w, &format!("How this works: your starting point is life expectancy at birth for your sex and country (UN World Population Prospects 2024), turned into a mortality curve where the risk of dying roughly doubles every 8 years of age. Each answer scales that yearly risk by a ratio from large studies, re-centred so an average answer changes nothing. Habits cluster in the same people, so the biggest effect counts in full and the rest count less, calibrated so five healthy habits add about 12 to 14 years at 50 (Li et al., Circulation 2018). Because you’re {} already, it counts only the years from now. It uses today’s death rates, so the real number is likely a little higher. A population average to reflect on, not a prediction, and not medical advice.", est.age_now as i32));
    false
}

// ------------------------------------------------------------------ moments

fn letter(app: &mut App, ui: &mut Ui, w: f32, i: usize) -> bool {
    let Some(l) = app.s.letters.get(i).cloned() else { return true };
    let wd = NaiveDate::parse_from_str(&l.written, "%Y-%m-%d").map(life::date_str).unwrap_or_default();
    title(ui, w, "A letter from you", Some(&format!("Written {wd}, for you at {}", l.age)));
    ui.add_space(18.0);
    let hh = para_height(ui.ctx(), &l.text, F::Body, 18.0, w - 10.0, 1.55);
    let r = take(ui, w, hh + 6.0);
    para(ui.painter(), r.min, &l.text, F::Body, 18.0, FG, w - 10.0, 1.55);
    if button(ui, w, "Keep it", "lt-keep") { if let Some(x) = app.s.letters.get_mut(i) { x.opened = true; } return true; }
    false
}

fn review(app: &mut App, ui: &mut Ui, w: f32) -> bool {
    let b = app.s.birth().unwrap();
    let lived = life::age(b) as i32;
    let end = life::last_birthday(b, life::today());
    let start = life::birthday_in(b, end.year() - 1);
    let lines: Vec<(&String, &String)> = app.lines.iter().filter(|(k, _)| NaiveDate::parse_from_str(k, "%Y-%m-%d").map(|d| d >= start && d < end).unwrap_or(false)).collect();
    title(ui, w, &format!("Age {}, complete", lived - 1), Some(&format!("{}–{} · one more white dot", start.year(), end.year())));
    ui.add_space(14.0);
    let named = app.s.names.get(&(lived - 1)).cloned();
    let (g, rh) = group(ui, w, if named.is_some() { 3 } else { 2 }, false);
    row(ui, nth(g, rh, 0), "Days lived", None, Id::new("rv0"), true);
    text(ui.painter(), Pos2::new(g.right() - 20.0, nth(g, rh, 0).center().y), Align2::RIGHT_CENTER, &life::group((end - start).num_days()), F::Body, 15.0, MUTE, 0.0);
    row(ui, nth(g, rh, 1), "Lines written", None, Id::new("rv1"), false);
    text(ui.painter(), Pos2::new(g.right() - 20.0, nth(g, rh, 1).center().y), Align2::RIGHT_CENTER, &lines.len().to_string(), F::Body, 15.0, MUTE, 0.0);
    if let Some(n) = &named { row(ui, nth(g, rh, 2), "You called it", None, Id::new("rv2"), false); text(ui.painter(), Pos2::new(g.right() - 20.0, nth(g, rh, 2).center().y), Align2::RIGHT_CENTER, n, F::Body, 15.0, MUTE, 0.0); }
    if let Some((_, f)) = lines.first() { caps(ui, w, "First line of the year"); note(ui, w, &format!("“{f}”")); }
    if lines.len() > 1 { caps(ui, w, "Last line of the year"); note(ui, w, &format!("“{}”", lines.last().unwrap().1)); }
    if button(ui, w, &format!("Begin year {}", lived + 1), "rv-go") { app.s.reviewed_age = lived; return true; }
    false
}

fn about(ui: &mut Ui, w: f32) -> bool {
    let r = take(ui, w, 72.0);
    text(ui.painter(), r.left_center(), Align2::LEFT_CENTER, "yrs.", F::DisplayBold, 60.0, FG, -0.06);
    note(ui, w, "A small app that shows your life as dots, so you spend the rest of it on purpose.");
    let items = [("One person", "yrs. is an independent project, made by one person in spare time. No company, no investors, no ads."), ("Free", "No price, no subscription, nothing locked."), ("Open source, MIT license", "Read every line of the code. Build it yourself, change anything, or make your own version."), ("Private", "No account and no tracking. Your birthday, names, letters and lines stay on this computer.")];
    for (t, d) in items { caps(ui, w, t); note(ui, w, d); }
    caps(ui, w, "Links");
    let links = [("Website", "https://jrypzm.github.io/yrs/"), ("Source code", "https://github.com/jrypzm/yrs"), ("Ideas and bugs", "https://github.com/jrypzm/yrs/issues/new/choose")];
    let (g, rh) = group(ui, w, links.len(), false);
    for (i, (l, u)) in links.iter().enumerate() {
        if row(ui, nth(g, rh, i), l, None, Id::new(("link", i)), i == 0).clicked() { let _ = std::process::Command::new("xdg-open").arg(u).spawn(); }
        chevron(ui.painter(), Pos2::new(g.right() - 20.0, nth(g, rh, i).center().y), DIM, false);
    }
    note(ui, w, &format!("yrs. {} · Make the next one count.", env!("CARGO_PKG_VERSION")));
    button(ui, w, "Done", "a-done")
}
