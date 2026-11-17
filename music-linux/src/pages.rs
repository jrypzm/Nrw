//! The library pages: Songs, Albums, Playlists, an album, a playlist, search, settings, first open.

use crate::app::{App, Dialog, Tab, View};
use crate::icons::I;
use crate::library::{sort_key, Size};
use crate::theme::*;
use egui::{pos2, vec2, Color32, CornerRadius, CursorIcon, Id, Rect, Sense, Ui, UiBuilder};

pub const TOP: f32 = 56.0;
pub const BAR: f32 = 88.0;
const ROW: f32 = 56.0;

impl App {
    pub fn draw_main(&mut self, ui: &mut Ui, full: Rect) {
        let ctx = ui.ctx().clone();
        let has = self.player.current().is_some();
        let t = ctx.animate_bool_with_time_and_easing(Id::new("now-open"), self.now && has, 0.48, egui::emath::easing::cubic_in_out);
        let bar_h = if has { BAR } else { 0.0 };
        let content = Rect::from_min_max(pos2(full.left(), full.top() + TOP), pos2(full.right(), full.bottom() - bar_h));
        if t < 1.0 && self.lib.tracks.is_empty() {
            // the very first read of a library: say so, calmly
            self.top_bar(ui, Rect::from_min_max(full.min, pos2(full.right(), full.top() + TOP)));
            let (a, b) = self.scanning.unwrap_or((0, 0));
            let x = full.left() + (full.width() * 0.09).max(48.0);
            let g = line(ui, "Reading your music", font(F::Display, 34.0), WHITE, full.width() - x * 2.0, -1.0);
            ui.painter().galley(pos2(x, full.top() + full.height() * 0.34), g, WHITE);
            let s = if b == 0 { "Looking through your folders…".to_string() } else { format!("{} of {} songs", count(a), count(b)) };
            let g = line(ui, &s, font(F::Regular, 17.0), MUTE, full.width() - x * 2.0, 0.0);
            ui.painter().galley(pos2(x, full.top() + full.height() * 0.34 + 54.0), g, WHITE);
            let w = (full.width() - x * 2.0).min(420.0);
            let y = full.top() + full.height() * 0.34 + 96.0;
            ui.painter().rect_filled(Rect::from_min_size(pos2(x, y), vec2(w, 2.0)), CornerRadius::same(1), RAIL);
            let k = if b == 0 { 0.06 } else { a as f32 / b as f32 };
            ui.painter().rect_filled(Rect::from_min_size(pos2(x, y), vec2(w * k.clamp(0.02, 1.0), 2.0)), CornerRadius::same(1), self.accent);
            return;
        }
        if t < 1.0 {
            self.top_bar(ui, Rect::from_min_max(full.min, pos2(full.right(), full.top() + TOP)));
            ui.scope_builder(UiBuilder::new().max_rect(content), |ui| {
                // the library steps back as the player rises over it
                if t > 0.0 { ui.multiply_opacity(1.0 - t * 0.7); }
                ui.set_clip_rect(content);
                if !self.search.is_empty() { self.page_search(ui, content) } else {
                    match self.view.clone() {
                        View::Home => match self.tab { Tab::Songs => self.page_songs(ui, content), Tab::Albums => self.page_albums(ui, content), Tab::Lists => self.page_lists(ui, content) },
                        View::Album(k) => self.page_album(ui, content, &k),
                        View::List(w) => self.page_list(ui, content, w),
                        View::Settings => self.page_settings(ui, content),
                    }
                }
            });
        }
        if t > 0.0 { self.draw_now(ui, full, bar_h, t); }
        if has { self.draw_bar(ui, Rect::from_min_max(pos2(full.left(), full.bottom() - BAR), full.max), t); }
    }

    fn top_bar(&mut self, ui: &mut Ui, r: Rect) {
        self.drag_area(ui, r);
        let cy = r.center().y;
        // logo and name
        let logo = Rect::from_center_size(pos2(r.left() + 37.0, cy), vec2(26.0, 26.0));
        self.logo(ui, logo);
        let g = line(ui, "yrs.", font(F::Display, 17.0), WHITE, f32::INFINITY, -0.5);
        ui.painter().galley(pos2(logo.right() + 9.0, cy - g.size().y / 2.0), g, WHITE);
        // search
        let sr = Rect::from_min_size(pos2(r.left() + 140.0, cy - 17.0), vec2(300.0_f32.min(r.width() - 360.0).max(160.0), 34.0));
        ui.painter().hline(sr.x_range(), sr.bottom(), egui::Stroke::new(1.0_f32, HAIR));
        self.icons.draw(ui, I::Search, Rect::from_min_size(sr.min, vec2(20.0, 34.0)), 18.0, FAINT);
        let edit = egui::TextEdit::singleline(&mut self.search)
            .id(Id::new("search"))
            .frame(false)
            .hint_text(egui::RichText::new("Search songs, albums, artists").color(FAINT).font(font(F::Regular, 14.0)))
            .font(font(F::Regular, 14.0))
            .text_color(WHITE)
            .desired_width(sr.width() - 56.0)
            .vertical_align(egui::Align::Center);
        let er = ui.put(Rect::from_min_max(pos2(sr.left() + 28.0, sr.top()), pos2(sr.right() - 28.0, sr.bottom())), edit);
        if self.focus_search { er.request_focus(); self.focus_search = false; }
        if !self.search.is_empty() {
            if self.glyph(ui, Rect::from_center_size(pos2(sr.right() - 12.0, cy), vec2(24.0, 24.0)), I::Close, 14.0, DIM, "search-clear").clicked() { self.search.clear(); }
        } else if !er.has_focus() {
            let k = line(ui, "/", font(F::Medium, 11.0), FAINT, f32::INFINITY, 0.0);
            let kr = Rect::from_center_size(pos2(sr.right() - 12.0, cy), vec2(18.0, 17.0));
            ui.painter().rect_stroke(kr, CornerRadius::same(5), egui::Stroke::new(1.0_f32, Color32::from_rgb(34, 34, 36)), egui::StrokeKind::Inside);
            ui.painter().galley(kr.center() - k.size() / 2.0, k, FAINT);
        }
        if let Some((a, b)) = self.scanning {
            let s = if b == 0 { "Looking for music…".to_string() } else { format!("Reading {} of {}", count(a), count(b)) };
            let g = line(ui, &s, font(F::Medium, 12.0), DIM, 300.0, 0.0);
            ui.painter().galley(pos2(sr.right() + 24.0, cy - g.size().y / 2.0), g, DIM);
        }
        self.window_buttons(ui, r.right(), cy, true);
    }

    pub fn logo(&mut self, ui: &Ui, r: Rect) {
        let p = ui.painter();
        let u = r.width() / 100.0;
        p.rect_stroke(r.shrink(u), CornerRadius::same((24.0 * u) as u8), egui::Stroke::new(2.0 * u, GHOST), egui::StrokeKind::Inside);
        let m = line(ui, "m", font(F::Display, 64.0 * u), WHITE, f32::INFINITY, -3.0 * u);
        // Inter's ascent is 0.97 em: this puts the baseline where the icon has it
        p.galley(pos2(r.left() + 13.0 * u, r.top() + 14.0 * u), m, WHITE);
        let c = pos2(r.left() + 83.5 * u, r.top() + 71.5 * u);
        p.circle_filled(c, 5.6 * u, Color32::from_rgb(58, 58, 60));
        let half: Vec<egui::Pos2> = (0..=16).map(|i| { let a = std::f32::consts::PI * i as f32 / 16.0; pos2(c.x + 5.6 * u * a.cos(), c.y + 5.6 * u * a.sin()) }).collect();
        p.add(egui::Shape::convex_polygon(half, WHITE, egui::Stroke::NONE));
    }

    /// The three words, and whatever sits on the right of the page header.
    fn words(&mut self, ui: &mut Ui, area: Rect) {
        let mut x = area.left() + 24.0;
        let y = area.top() + 16.0;
        for (tab, name) in [(Tab::Songs, "Songs"), (Tab::Albums, "Albums"), (Tab::Lists, "Playlists")] {
            let on = self.tab == tab && self.view == View::Home;
            let k = ui.ctx().animate_bool_with_time(Id::new(("word", name)), on, 0.22);
            let g = line(ui, name, font(F::Display, 30.0), WHITE, f32::INFINITY, -0.9);
            let rect = Rect::from_min_size(pos2(x, y), g.size());
            let r = ui.interact(rect.expand(4.0), Id::new(("tab", name)), Sense::click());
            let c = if r.hovered() && !on { mix(GHOST, MUTE, 0.5) } else { mix(GHOST, WHITE, k) };
            ui.painter().galley(rect.min, line(ui, name, font(F::Display, 30.0), c, f32::INFINITY, -0.9), c);
            if r.hovered() { ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
            if r.clicked() { self.tab = tab; self.view = View::Home; self.history.clear(); }
            x = rect.right() + 24.0;
        }
    }

    // ------------------------------------------------------------ Songs

    fn sorted(&mut self) -> Vec<usize> {
        if self.sorted.0 != self.generation || self.sorted.1 != self.store.sort {
            let mut v: Vec<usize> = (0..self.lib.tracks.len()).collect();
            let tr = &self.lib.tracks;
            match self.store.sort.0 {
                1 => v.sort_by(|&a, &b| sort_key(&tr[a].artist).cmp(&sort_key(&tr[b].artist)).then(sort_key(&tr[a].album).cmp(&sort_key(&tr[b].album))).then(tr[a].track.cmp(&tr[b].track))),
                2 => v.sort_by(|&a, &b| sort_key(&tr[a].album).cmp(&sort_key(&tr[b].album)).then(tr[a].disc.cmp(&tr[b].disc)).then(tr[a].track.cmp(&tr[b].track))),
                3 => v.sort_by(|&a, &b| tr[a].secs.total_cmp(&tr[b].secs)),
                4 => v.sort_by(|&a, &b| tr[b].mtime.cmp(&tr[a].mtime)),
                _ => {}
            }
            if !self.store.sort.1 { v.reverse(); }
            self.sorted = (self.generation, self.store.sort, v);
        }
        self.sorted.2.clone()
    }

    fn page_songs(&mut self, ui: &mut Ui, area: Rect) {
        self.words(ui, area);
        let list = self.sorted();
        let total = self.lib.total_secs();
        let right = area.right() - 24.0;
        let y = area.top() + 30.0;
        let sh = line(ui, "Shuffle", font(F::Semi, 14.0), MUTE, f32::INFINITY, 0.0);
        let shx = right - sh.size().x;
        if self.word(ui, pos2(shx, y), "Shuffle", font(F::Semi, 14.0), MUTE, WHITE, "songs-shuffle").clicked() { self.player.play_all(&self.lib, list.clone(), true, "Songs".into()); }
        let pl = line(ui, "Play", font(F::Semi, 14.0), WHITE, f32::INFINITY, 0.0);
        let plx = shx - 22.0 - pl.size().x;
        if self.word(ui, pos2(plx, y), "Play", font(F::Semi, 14.0), WHITE, WHITE, "songs-play").clicked() { self.player.play_all(&self.lib, list.clone(), false, "Songs".into()); }
        let m = meta(ui, &format!("{} · {}", songs(list.len()), span(total)), DIM, 400.0);
        ui.painter().galley(pos2(plx - 28.0 - m.size().x, y + 2.0), m, DIM);
        let table = Rect::from_min_max(pos2(area.left(), area.top() + 74.0), area.max);
        self.song_table(ui, table, &list, "songs", true);
    }

    /// The table of songs used by Songs and search: number, cover, title, artist, album, time.
    fn song_table(&mut self, ui: &mut Ui, area: Rect, list: &[usize], id: &str, sortable: bool) {
        let w = area.width() - 48.0;
        let (cn, ct) = (44.0, 64.0);
        let rest = w - cn - ct - 16.0 * 4.0;
        let (c1, c2) = (rest * 0.42, rest * 0.29);
        let x0 = area.left() + 24.0;
        let cols = [x0, x0 + cn + 16.0, x0 + cn + 32.0 + c1, x0 + cn + 48.0 + c1 + c2, area.right() - 24.0];
        // header
        let hy = area.top();
        let heads = [("#", 99u8), ("Title", 0), ("Artist", 1), ("Album", 2), ("Time", 3)];
        for (i, (h, key)) in heads.iter().enumerate() {
            let on = sortable && self.store.sort.0 == *key;
            let label = if on { format!("{h} {}", if self.store.sort.1 { "↑" } else { "↓" }) } else { h.to_string() };
            let g = meta(ui, &label, if on { WHITE } else { DIM }, 200.0);
            let at = if i == 0 || i == 4 { pos2(cols[i] + if i == 0 { cn } else { 0.0 } - g.size().x, hy + 11.0) } else { pos2(cols[i], hy + 11.0) };
            let r = Rect::from_min_size(at, g.size()).expand(6.0);
            ui.painter().galley(at, g, WHITE);
            if sortable && *key != 99 {
                let resp = ui.interact(r, Id::new((id, "head", i)), Sense::click());
                if resp.hovered() { ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
                if resp.clicked() { self.store.sort = if self.store.sort.0 == *key { (*key, !self.store.sort.1) } else { (*key, true) }; self.dirty(); }
            }
        }
        ui.painter().hline(area.x_range(), hy + 34.0, egui::Stroke::new(1.0_f32, HAIR));
        let body = Rect::from_min_max(pos2(area.left(), hy + 35.0), area.max);
        let current = self.player.current();
        let accent = self.accent;
        ui.scope_builder(UiBuilder::new().max_rect(body), |ui| {
            egui::ScrollArea::vertical().id_salt(id).auto_shrink([false, false]).show_rows(ui, ROW, list.len(), |ui, range| {
                for i in range {
                    let t = list[i];
                    let (rect, resp) = ui.allocate_exact_size(vec2(body.width(), ROW), Sense::click());
                    let playing = current == Some(t);
                    let sel = self.selected == Some(t);
                    if resp.hovered() || sel { ui.painter().rect_filled(rect, CornerRadius::ZERO, if sel { Color32::from_rgb(16, 16, 18) } else { LIFT }); }
                    let tr = self.lib.tracks[t].clone();
                    let cy = rect.center().y;
                    if playing {
                        let icon = if self.player.playing { I::Play } else { I::Pause };
                        self.icons.draw(ui, icon, Rect::from_center_size(pos2(cols[0] + cn - 6.0, cy), vec2(14.0, 14.0)), 13.0, accent);
                    } else {
                        let g = line(ui, &(i + 1).to_string(), font(F::Medium, 13.0), DIM, cn, 0.0);
                        ui.painter().galley(pos2(cols[0] + cn - g.size().x, cy - g.size().y / 2.0), g, WHITE);
                    }
                    self.cover(ui, &tr.album_key, &tr.album, Rect::from_min_size(pos2(cols[1], cy - 20.0), vec2(40.0, 40.0)), 6.0, Size::S);
                    let g = line(ui, &tr.title, font(F::Medium, 15.0), WHITE, c1 - 56.0, -0.15);
                    ui.painter().galley(pos2(cols[1] + 54.0, cy - g.size().y / 2.0), g, WHITE);
                    let g = line(ui, &tr.artist, font(F::Regular, 14.0), MUTE, c2 - 8.0, 0.0);
                    ui.painter().galley(pos2(cols[2], cy - g.size().y / 2.0), g, WHITE);
                    let g = line(ui, &tr.album, font(F::Regular, 14.0), MUTE, cols[4] - cols[3] - ct - 16.0, 0.0);
                    ui.painter().galley(pos2(cols[3], cy - g.size().y / 2.0), g, WHITE);
                    let g = line(ui, &clock(tr.secs), font(F::Medium, 13.0), if playing { accent } else { DIM }, ct, 0.0);
                    ui.painter().galley(pos2(cols[4] - g.size().x, cy - g.size().y / 2.0), g, WHITE);
                    if resp.clicked() { self.selected = Some(t); }
                    if resp.double_clicked() { self.play_from_view(t); }
                    resp.context_menu(|ui| self.song_menu(ui, t, None));
                }
            });
        });
    }

    /// The right-click menu for a song.
    pub fn song_menu(&mut self, ui: &mut Ui, t: usize, in_list: Option<Option<usize>>) {
        ui.set_min_width(220.0);
        let ctx = ui.ctx().clone();
        if ui.button("Play").clicked() { self.play_from_view(t); ui.close_menu(); }
        if ui.button("Play next").clicked() { self.player.play_next(&self.lib, &[t]); self.say(&ctx, "Plays next"); ui.close_menu(); }
        if ui.button("Add to queue").clicked() { self.player.add(&self.lib, &[t]); self.say(&ctx, "Added to the queue"); ui.close_menu(); }
        ui.menu_button("Add to playlist", |ui| {
            ui.set_min_width(200.0);
            if ui.button("New playlist…").clicked() { self.dialog = Some(Dialog::NewList { name: String::new(), add: vec![t] }); ui.close_menu(); }
            for i in 0..self.store.lists.len() {
                let name = self.store.lists[i].name.clone();
                if ui.button(name).clicked() { self.add_to_list(&ctx, i, &[t]); ui.close_menu(); }
            }
        });
        ui.separator();
        if ui.button(if self.loved(t) { "Remove from Loved" } else { "Love" }).clicked() { self.toggle_love(t); ui.close_menu(); }
        if ui.button("Go to album").clicked() { let k = self.lib.tracks[t].album_key.clone(); self.go(View::Album(k)); ui.close_menu(); }
        if ui.button("Show in folder").clicked() {
            if let Some(d) = self.lib.tracks[t].path.parent() { let _ = std::process::Command::new("xdg-open").arg(d).spawn(); }
            ui.close_menu();
        }
        if let Some(w) = in_list {
            ui.separator();
            let label = if w.is_none() { "Remove from Loved".to_string() } else { format!("Remove from {}", w.and_then(|i| self.store.lists.get(i)).map(|l| l.name.clone()).unwrap_or_default()) };
            if w.is_some() && ui.button(label).clicked() {
                let p = self.lib.tracks[t].path.clone();
                if let Some(l) = w.and_then(|i| self.store.lists.get_mut(i)) { l.songs.retain(|x| *x != p); }
                self.dirty();
                ui.close_menu();
            }
        }
    }

    // ------------------------------------------------------------ Albums

    fn page_albums(&mut self, ui: &mut Ui, area: Rect) {
        self.words(ui, area);
        let m = meta(ui, &format!("{} albums", count(self.lib.albums.len())), DIM, 300.0);
        ui.painter().galley(pos2(area.right() - 24.0 - m.size().x, area.top() + 32.0), m, DIM);
        let grid = Rect::from_min_max(pos2(area.left(), area.top() + 74.0), area.max);
        let keys: Vec<usize> = (0..self.lib.albums.len()).collect();
        self.album_grid(ui, grid, &keys, "albums");
    }

    fn album_grid(&mut self, ui: &mut Ui, area: Rect, albums: &[usize], id: &str) {
        let gap = 22.0;
        let w = area.width() - 48.0;
        let cols = ((w + gap) / (190.0 + gap)).floor().max(2.0) as usize;
        let tile = (w - gap * (cols as f32 - 1.0)) / cols as f32;
        let row_h = tile + 62.0;
        let rows = albums.len().div_ceil(cols);
        ui.scope_builder(UiBuilder::new().max_rect(area), |ui| {
            egui::ScrollArea::vertical().id_salt(id).auto_shrink([false, false]).show_viewport(ui, |ui, view| {
                let (full, _) = ui.allocate_exact_size(vec2(area.width(), rows as f32 * row_h + 40.0), Sense::hover());
                let first = (view.top() / row_h).floor().max(0.0) as usize;
                let last = ((view.bottom() / row_h).ceil() as usize + 1).min(rows);
                for r in first..last {
                    for c in 0..cols {
                        let k = r * cols + c;
                        let Some(&a) = albums.get(k) else { break };
                        let al = self.lib.albums[a].clone();
                        let x = full.left() + 24.0 + c as f32 * (tile + gap);
                        let y = full.top() + r as f32 * row_h + 4.0;
                        let cr = Rect::from_min_size(pos2(x, y), vec2(tile, tile));
                        let resp = ui.interact(Rect::from_min_size(cr.min, vec2(tile, tile + 52.0)), Id::new((id, a)), Sense::click());
                        let lift = ui.ctx().animate_bool_with_time(Id::new((id, "lift", a)), resp.hovered(), 0.18);
                        let cr2 = cr.translate(vec2(0.0, -3.0 * lift));
                        self.cover(ui, &al.key, &al.title, cr2, 10.0, Size::M);
                        let g = line(ui, &al.title, font(F::Medium, 14.0), WHITE, tile, -0.1);
                        ui.painter().galley(pos2(x, cr.bottom() + 10.0), g, WHITE);
                        let g = line(ui, &al.artist, font(F::Regular, 13.0), MUTE, tile, 0.0);
                        ui.painter().galley(pos2(x, cr.bottom() + 30.0), g, WHITE);
                        if resp.hovered() { ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
                        if resp.clicked() { self.go(View::Album(al.key.clone())); }
                        if resp.double_clicked() { self.player.play_list(&self.lib, al.tracks.clone(), 0, al.title.clone()); }
                        resp.context_menu(|ui| {
                            let ctx = ui.ctx().clone();
                            if ui.button("Play").clicked() { self.player.play_list(&self.lib, al.tracks.clone(), 0, al.title.clone()); ui.close_menu(); }
                            if ui.button("Shuffle").clicked() { self.player.play_all(&self.lib, al.tracks.clone(), true, al.title.clone()); ui.close_menu(); }
                            if ui.button("Play next").clicked() { self.player.play_next(&self.lib, &al.tracks); self.say(&ctx, "Plays next"); ui.close_menu(); }
                            if ui.button("Add to queue").clicked() { self.player.add(&self.lib, &al.tracks); self.say(&ctx, "Added to the queue"); ui.close_menu(); }
                        });
                    }
                }
            });
        });
    }

    // ------------------------------------------------------------ an album, a playlist

    fn back_link(&mut self, ui: &mut Ui, area: Rect, label: &str) {
        let r = Rect::from_min_size(pos2(area.left() + 12.0, area.top() + 6.0), vec2(140.0, 30.0));
        let resp = ui.interact(r, Id::new("back"), Sense::click());
        let c = if resp.hovered() { WHITE } else { MUTE };
        self.icons.draw(ui, I::Back, Rect::from_min_size(r.min, vec2(26.0, 30.0)), 22.0, c);
        let g = line(ui, label, font(F::Medium, 14.0), c, 120.0, 0.0);
        ui.painter().galley(pos2(r.left() + 28.0, r.center().y - g.size().y / 2.0), g, c);
        if resp.hovered() { ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
        if resp.clicked() { self.back(); }
    }

    fn page_album(&mut self, ui: &mut Ui, area: Rect, key: &str) {
        let back = match self.history.last() { Some(View::List(_)) => "Playlist", Some(View::Settings) => "Settings", _ => if self.tab == Tab::Songs { "Songs" } else { "Albums" } };
        self.back_link(ui, area, back);
        let Some(&a) = self.lib.by_album.get(key) else {
            let g = line(ui, "This album is no longer in your folders.", font(F::Display, 26.0), WHITE, area.width() - 48.0, -0.6);
            ui.painter().galley(pos2(area.left() + 24.0, area.top() + 70.0), g, WHITE);
            return;
        };
        let al = self.lib.albums[a].clone();
        let side = (area.height() - 260.0).min(320.0).min(area.width() * 0.32).max(160.0);
        let x = area.left() + 24.0;
        let y = area.top() + 56.0;
        self.cover(ui, &al.key, &al.title, Rect::from_min_size(pos2(x, y), vec2(side, side)), 12.0, Size::L);
        let mut cy = y + side + 24.0;
        let g = para(ui, &al.title, font(F::Display, 30.0), WHITE, side, 34.0);
        let h = g.size().y;
        ui.painter().galley(pos2(x, cy), g, WHITE);
        cy += h + 4.0;
        let g = line(ui, &al.artist, font(F::Medium, 17.0), WHITE, side, -0.1);
        ui.painter().galley(pos2(x, cy), g, WHITE);
        cy += 30.0;
        let first = &self.lib.tracks[al.tracks[0]];
        let mut bits = vec!["Album".to_string()];
        if let Some(y) = al.year { bits.push(y.to_string()); }
        if al.tracks.iter().all(|&t| self.lib.tracks[t].lossless()) { bits.push(first.quality()); }
        let g = meta(ui, &bits.join(" · "), DIM, side);
        ui.painter().galley(pos2(x, cy), g, DIM);
        cy += 34.0;
        if self.word(ui, pos2(x, cy), "Play", font(F::Semi, 15.0), WHITE, WHITE, "al-play").clicked() { self.player.play_all(&self.lib, al.tracks.clone(), false, al.title.clone()); }
        if self.word(ui, pos2(x + 62.0, cy), "Shuffle", font(F::Semi, 15.0), MUTE, WHITE, "al-shuf").clicked() { self.player.play_all(&self.lib, al.tracks.clone(), true, al.title.clone()); }
        let secs: f64 = al.tracks.iter().map(|&t| self.lib.tracks[t].secs as f64).sum();
        let foot = format!("{}{}, {}", al.year.map(|y| format!("{y} · ")).unwrap_or_default(), songs(al.tracks.len()), span(secs));
        let right = Rect::from_min_max(pos2(x + side + 48.0, y - 6.0), area.max);
        self.track_list(ui, right, &al.tracks, "album-rows", true, None, Some(foot));
    }

    /// Numbered rows (an album) or rows with covers (a playlist).
    fn track_list(&mut self, ui: &mut Ui, area: Rect, list: &[usize], id: &str, numbered: bool, in_list: Option<Option<usize>>, foot: Option<String>) {
        let current = self.player.current();
        let accent = self.accent;
        let h = if numbered { 50.0 } else { ROW };
        ui.scope_builder(UiBuilder::new().max_rect(area), |ui| {
            egui::ScrollArea::vertical().id_salt(id).auto_shrink([false, false]).show(ui, |ui| {
                for (i, &t) in list.iter().enumerate() {
                    let (rect, resp) = ui.allocate_exact_size(vec2(area.width(), h), Sense::click());
                    if resp.hovered() { ui.painter().rect_filled(rect, CornerRadius::same(8), LIFT); }
                    let tr = self.lib.tracks[t].clone();
                    let playing = current == Some(t);
                    let cy = rect.center().y;
                    let mut x = rect.left() + 16.0;
                    if numbered {
                        let n = tr.track.map(|n| n.to_string()).unwrap_or_else(|| (i + 1).to_string());
                        let g = line(ui, &n, font(F::Medium, 14.0), if playing { accent } else { DIM }, 34.0, 0.0);
                        ui.painter().galley(pos2(x + 30.0 - g.size().x, cy - g.size().y / 2.0), g, WHITE);
                        x += 50.0;
                    } else {
                        self.cover(ui, &tr.album_key, &tr.album, Rect::from_min_size(pos2(x, cy - 20.0), vec2(40.0, 40.0)), 6.0, Size::S);
                        x += 56.0;
                    }
                    let tw = rect.right() - 90.0 - x;
                    if numbered {
                        let g = line(ui, &tr.title, font(F::Medium, 15.0), WHITE, tw, -0.15);
                        ui.painter().galley(pos2(x, cy - g.size().y / 2.0), g, WHITE);
                    } else {
                        let g = line(ui, &tr.title, font(F::Medium, 15.0), WHITE, tw, -0.15);
                        ui.painter().galley(pos2(x, cy - 18.0), g, WHITE);
                        let g = line(ui, &tr.artist, font(F::Regular, 13.0), MUTE, tw, 0.0);
                        ui.painter().galley(pos2(x, cy + 2.0), g, WHITE);
                    }
                    let g = line(ui, &clock(tr.secs), font(F::Medium, 13.0), if playing { accent } else { DIM }, 70.0, 0.0);
                    ui.painter().galley(pos2(rect.right() - 24.0 - g.size().x, cy - g.size().y / 2.0), g, WHITE);
                    if resp.double_clicked() || (numbered && resp.clicked() && false) { self.play_from_view(t); }
                    if resp.clicked() { self.selected = Some(t); }
                    resp.context_menu(|ui| self.song_menu(ui, t, in_list));
                }
                if let Some(f) = &foot {
                    ui.add_space(14.0);
                    let g = line(ui, f, font(F::Regular, 12.5), DIM, area.width() - 80.0, 0.0);
                    let (r, _) = ui.allocate_exact_size(vec2(area.width(), 40.0), Sense::hover());
                    ui.painter().galley(pos2(r.left() + 66.0, r.top()), g, DIM);
                }
                if list.is_empty() {
                    let (r, _) = ui.allocate_exact_size(vec2(area.width(), 80.0), Sense::hover());
                    let hint = if in_list == Some(None) { "Click the heart beside the playing song and it lands here." } else { "Nothing here yet. Right-click any song and choose Add to playlist." };
                    let g = line(ui, hint, font(F::Regular, 15.0), MUTE, area.width() - 40.0, 0.0);
                    ui.painter().galley(pos2(r.left() + 16.0, r.top() + 20.0), g, WHITE);
                }
            });
        });
    }

    // ------------------------------------------------------------ Playlists

    fn page_lists(&mut self, ui: &mut Ui, area: Rect) {
        self.words(ui, area);
        let n = self.store.lists.len();
        let label = "New playlist";
        let g = line(ui, label, font(F::Semi, 14.0), WHITE, f32::INFINITY, 0.0);
        if self.word(ui, pos2(area.right() - 24.0 - g.size().x, area.top() + 30.0), label, font(F::Semi, 14.0), WHITE, WHITE, "new-list").clicked() {
            self.dialog = Some(Dialog::NewList { name: String::new(), add: vec![] });
        }
        let m = meta(ui, &format!("{} playlists", count(n)), DIM, 300.0);
        ui.painter().galley(pos2(area.right() - 24.0 - g.size().x - 28.0 - m.size().x, area.top() + 32.0), m, DIM);
        let body = Rect::from_min_max(pos2(area.left(), area.top() + 76.0), area.max);
        let accent = self.accent;
        ui.scope_builder(UiBuilder::new().max_rect(body), |ui| {
            egui::ScrollArea::vertical().id_salt("lists").auto_shrink([false, false]).show(ui, |ui| {
                for k in 0..=n {
                    let which = if k == 0 { None } else { Some(k - 1) };
                    let songs_in = self.list_songs(which);
                    let name = which.map(|i| self.store.lists[i].name.clone()).unwrap_or_else(|| "Loved".into());
                    let (rect, resp) = ui.allocate_exact_size(vec2(body.width(), 76.0), Sense::click());
                    if resp.hovered() { ui.painter().rect_filled(rect.shrink2(vec2(12.0, 0.0)), CornerRadius::same(10), LIFT); ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
                    let art = Rect::from_min_size(pos2(rect.left() + 24.0, rect.center().y - 28.0), vec2(56.0, 56.0));
                    if which.is_none() {
                        ui.painter().rect_stroke(art, CornerRadius::same(10), egui::Stroke::new(1.0_f32, GHOST), egui::StrokeKind::Inside);
                        self.icons.draw(ui, I::HeartFull, art, 22.0, accent);
                    } else { self.mosaic(ui, &songs_in, &name, art, 10.0); }
                    let g = line(ui, &name, font(F::Medium, 16.0), WHITE, rect.width() - 200.0, -0.15);
                    ui.painter().galley(pos2(art.right() + 16.0, rect.center().y - 20.0), g, WHITE);
                    let secs: f64 = songs_in.iter().map(|&t| self.lib.tracks[t].secs as f64).sum();
                    let sub = if songs_in.is_empty() { if which.is_none() { "Songs you love".into() } else { "Empty".into() } } else { format!("{} · {}", songs(songs_in.len()), span(secs)) };
                    let g = line(ui, &sub, font(F::Regular, 14.0), MUTE, rect.width() - 200.0, 0.0);
                    ui.painter().galley(pos2(art.right() + 16.0, rect.center().y + 3.0), g, WHITE);
                    if resp.clicked() { self.go(View::List(which)); }
                    if let Some(i) = which {
                        resp.context_menu(|ui| {
                            if ui.button("Play").clicked() { self.player.play_list(&self.lib, songs_in.clone(), 0, name.clone()); ui.close_menu(); }
                            if ui.button("Rename…").clicked() { self.dialog = Some(Dialog::Rename { i, name: name.clone() }); ui.close_menu(); }
                            if ui.button("Delete…").clicked() { self.dialog = Some(Dialog::Delete { i }); ui.close_menu(); }
                        });
                    }
                }
                if n == 0 {
                    let (r, _) = ui.allocate_exact_size(vec2(body.width(), 60.0), Sense::hover());
                    let g = line(ui, "Make one for a mood, a drive or a person. Right-click any song to add it.", font(F::Regular, 13.5), DIM, body.width() - 48.0, 0.0);
                    ui.painter().galley(pos2(r.left() + 24.0, r.top() + 18.0), g, WHITE);
                }
            });
        });
    }

    /// Four covers in a square, or one when there aren't four albums.
    pub fn mosaic(&mut self, ui: &Ui, list: &[usize], name: &str, r: Rect, radius: f32) {
        let mut keys: Vec<(String, String)> = vec![];
        for &t in list {
            let tr = &self.lib.tracks[t];
            if !keys.iter().any(|k| k.0 == tr.album_key) { keys.push((tr.album_key.clone(), tr.album.clone())); }
            if keys.len() == 4 { break; }
        }
        if keys.len() < 4 {
            match keys.first() { Some((k, a)) => self.cover(ui, k, a, r, radius, Size::M), None => self.cover(ui, "", name, r, radius, Size::S) }
            return;
        }
        let h = r.width() / 2.0;
        let clip = ui.painter().with_clip_rect(r);
        for (i, (k, a)) in keys.iter().enumerate() {
            let q = Rect::from_min_size(pos2(r.left() + h * (i % 2) as f32, r.top() + h * (i / 2) as f32), vec2(h, h));
            match self.covers.get(k, Size::S) {
                Some(t) => { clip.image(t.id(), q, Rect::from_min_max(pos2(0.0, 0.0), pos2(1.0, 1.0)), WHITE); }
                None => { clip.rect_filled(q, CornerRadius::ZERO, TILE); let _ = a; }
            }
        }
        // round the outer corners by painting the black around them
        ui.painter().rect_stroke(r.expand(radius * 0.5), CornerRadius::same((radius * 1.5) as u8), egui::Stroke::new(radius, BLACK), egui::StrokeKind::Inside);
    }

    fn page_list(&mut self, ui: &mut Ui, area: Rect, which: Option<usize>) {
        self.back_link(ui, area, "Playlists");
        if let Some(i) = which { if i >= self.store.lists.len() { self.back(); return; } }
        let list = self.list_songs(which);
        let name = which.map(|i| self.store.lists[i].name.clone()).unwrap_or_else(|| "Loved".into());
        let side = (area.height() - 230.0).min(280.0).min(area.width() * 0.3).max(150.0);
        let x = area.left() + 24.0;
        let y = area.top() + 56.0;
        let art = Rect::from_min_size(pos2(x, y), vec2(side, side));
        if which.is_none() {
            ui.painter().rect_filled(art, CornerRadius::same(12), TILE);
            let a = self.accent;
            self.icons.draw(ui, I::HeartFull, art, side * 0.28, a);
        } else { self.mosaic(ui, &list, &name, art, 12.0); }
        let g = para(ui, &name, font(F::Display, 30.0), WHITE, side, 34.0);
        let gh = g.size().y;
        ui.painter().galley(pos2(x, y + side + 24.0), g, WHITE);
        let secs: f64 = list.iter().map(|&t| self.lib.tracks[t].secs as f64).sum();
        let g = meta(ui, &if list.is_empty() { "No songs yet".into() } else { format!("{} · {}", songs(list.len()), span(secs)) }, DIM, side);
        ui.painter().galley(pos2(x, y + side + 32.0 + gh), g, DIM);
        let by = y + side + 64.0 + gh;
        if !list.is_empty() {
            if self.word(ui, pos2(x, by), "Play", font(F::Semi, 15.0), WHITE, WHITE, "pl-play").clicked() { self.player.play_all(&self.lib, list.clone(), false, name.clone()); }
            if self.word(ui, pos2(x + 62.0, by), "Shuffle", font(F::Semi, 15.0), MUTE, WHITE, "pl-shuf").clicked() { self.player.play_all(&self.lib, list.clone(), true, name.clone()); }
        }
        let right = Rect::from_min_max(pos2(x + side + 48.0, y - 6.0), area.max);
        self.track_list(ui, right, &list, "list-rows", false, Some(which), None);
    }

    // ------------------------------------------------------------ search

    fn page_search(&mut self, ui: &mut Ui, area: Rect) {
        let q = self.search.trim().to_lowercase();
        if self.found.0 != q || self.found.1 != self.generation {
            let words: Vec<&str> = q.split_whitespace().collect();
            let hit = |s: &str| { let s = s.to_lowercase(); words.iter().all(|w| s.contains(w)) };
            let tr = &self.lib.tracks;
            let songs_found: Vec<usize> = (0..tr.len()).filter(|&i| hit(&format!("{} {} {} {}", tr[i].title, tr[i].artist, tr[i].album, tr[i].album_artist))).collect();
            let albums: Vec<usize> = (0..self.lib.albums.len()).filter(|&a| hit(&format!("{} {}", self.lib.albums[a].title, self.lib.albums[a].artist))).take(12).collect();
            self.found = (q.clone(), self.generation, songs_found, albums);
        }
        let (_, _, s, a) = self.found.clone();
        let mut y = area.top() + 16.0;
        let g = line(ui, &format!("“{}”", self.search.trim()), font(F::Display, 30.0), WHITE, area.width() - 48.0, -0.9);
        ui.painter().galley(pos2(area.left() + 24.0, y), g, WHITE);
        y += 58.0;
        if s.is_empty() && a.is_empty() {
            let g = line(ui, "Nothing matches. Try part of a title, an artist or an album.", font(F::Regular, 15.0), MUTE, area.width() - 48.0, 0.0);
            ui.painter().galley(pos2(area.left() + 24.0, y), g, WHITE);
            return;
        }
        if !a.is_empty() {
            let m = meta(ui, "Albums", DIM, 200.0);
            ui.painter().galley(pos2(area.left() + 24.0, y), m, DIM);
            y += 24.0;
            let tile = 132.0;
            let mut x = area.left() + 24.0;
            for &al in &a {
                if x + tile > area.right() - 24.0 { break; }
                let alb = self.lib.albums[al].clone();
                let r = Rect::from_min_size(pos2(x, y), vec2(tile, tile));
                let resp = ui.interact(Rect::from_min_size(r.min, vec2(tile, tile + 40.0)), Id::new(("s-al", al)), Sense::click());
                self.cover(ui, &alb.key, &alb.title, r, 9.0, Size::M);
                let g = line(ui, &alb.title, font(F::Medium, 13.0), WHITE, tile, 0.0);
                ui.painter().galley(pos2(x, r.bottom() + 8.0), g, WHITE);
                let g = line(ui, &alb.artist, font(F::Regular, 12.0), MUTE, tile, 0.0);
                ui.painter().galley(pos2(x, r.bottom() + 25.0), g, WHITE);
                if resp.hovered() { ui.ctx().set_cursor_icon(CursorIcon::PointingHand); }
                if resp.clicked() { self.go(View::Album(alb.key.clone())); }
                x += tile + 18.0;
            }
            y += tile + 56.0;
        }
        if !s.is_empty() {
            self.song_table(ui, Rect::from_min_max(pos2(area.left(), y), area.max), &s, "search-rows", false);
        }
    }

    // ------------------------------------------------------------ settings

    fn page_settings(&mut self, ui: &mut Ui, area: Rect) {
        self.back_link(ui, area, "Back");
        let ctx = ui.ctx().clone();
        let x = area.left() + 24.0;
        let w = (area.width() - 48.0).min(640.0);
        ui.scope_builder(UiBuilder::new().max_rect(Rect::from_min_max(pos2(area.left(), area.top() + 44.0), area.max)), |ui| {
            egui::ScrollArea::vertical().id_salt("settings").auto_shrink([false, false]).show(ui, |ui| {
                let (r, _) = ui.allocate_exact_size(vec2(area.width(), 60.0), Sense::hover());
                ui.painter().galley(pos2(x, r.top() + 8.0), line(ui, "Settings", font(F::Display, 30.0), WHITE, w, -0.9), WHITE);
                let cap = |ui: &mut Ui, s: &str| {
                    let (r, _) = ui.allocate_exact_size(vec2(area.width(), 40.0), Sense::hover());
                    ui.painter().galley(pos2(x, r.bottom() - 16.0), meta(ui, s, DIM, w), DIM);
                };
                cap(ui, "Folders");
                let mut remove = None;
                for (i, f) in self.store.folders.clone().iter().enumerate() {
                    let (r, _) = ui.allocate_exact_size(vec2(area.width(), 54.0), Sense::hover());
                    let home = crate::library::home();
                    let shown = f.strip_prefix(&home).map(|p| format!("~/{}", p.display())).unwrap_or_else(|_| f.display().to_string());
                    ui.painter().galley(pos2(x, r.center().y - 10.0), line(ui, &shown, font(F::Regular, 16.0), WHITE, w - 90.0, 0.0), WHITE);
                    if self.word(ui, pos2(x + w - 58.0, r.center().y - 9.0), "Remove", font(F::Medium, 14.0), FAINT, WHITE, ("rm", i)).clicked() { remove = Some(i); }
                    ui.painter().hline(x..=x + w, r.bottom(), egui::Stroke::new(1.0_f32, Color32::from_rgb(20, 20, 22)));
                }
                if let Some(i) = remove { self.store.folders.remove(i); self.dirty(); self.rescan(&ctx); if self.store.folders.is_empty() { self.lib = Default::default(); self.generation += 1; } }
                let (r, _) = ui.allocate_exact_size(vec2(area.width(), 50.0), Sense::hover());
                if self.word(ui, pos2(x, r.top() + 16.0), "Add a folder", font(F::Semi, 15.0), self.accent, WHITE, "add-folder").clicked() { self.pick_folder(); }
                if self.word(ui, pos2(x + 130.0, r.top() + 16.0), "Look for new music", font(F::Semi, 15.0), MUTE, WHITE, "rescan").clicked() { self.rescan(&ctx); }

                cap(ui, "Looks");
                let (r, _) = ui.allocate_exact_size(vec2(area.width(), 62.0), Sense::hover());
                ui.painter().galley(pos2(x, r.top() + 12.0), line(ui, "Colour from the cover", font(F::Regular, 16.0), WHITE, w, 0.0), WHITE);
                ui.painter().galley(pos2(x, r.top() + 34.0), line(ui, "One tone from the artwork for the time line and what's on", font(F::Regular, 13.0), MUTE, w - 80.0, 0.0), WHITE);
                let on = self.store.cover_colour;
                if self.word(ui, pos2(x + w - 30.0, r.top() + 20.0), if on { "On" } else { "Off" }, font(F::Medium, 16.0), if on { WHITE } else { FAINT }, WHITE, "cc").clicked() { self.store.cover_colour = !on; self.dirty(); }

                cap(ui, "Keys");
                for (k, what) in [("Space", "Play or pause"), ("← →", "Back or ahead five seconds"), ("Ctrl ← →", "Previous or next song"), ("↑ ↓", "Volume"), ("/", "Search"), ("L", "Lyrics"), ("M", "Mini player"), ("Esc", "Close or go back")] {
                    let (r, _) = ui.allocate_exact_size(vec2(area.width(), 34.0), Sense::hover());
                    ui.painter().galley(pos2(x, r.top() + 8.0), line(ui, k, font(F::Medium, 14.0), WHITE, 110.0, 0.0), WHITE);
                    ui.painter().galley(pos2(x + 120.0, r.top() + 8.0), line(ui, what, font(F::Regular, 14.0), MUTE, w - 120.0, 0.0), WHITE);
                }
                let (r, _) = ui.allocate_exact_size(vec2(area.width(), 110.0), Sense::hover());
                let g = para(ui, &format!("yrs. music reads your files where they are. Nothing leaves this computer.\nVersion {}", env!("CARGO_PKG_VERSION")), font(F::Regular, 13.0), DIM, w, 20.0);
                ui.painter().galley(pos2(x, r.top() + 30.0), g, DIM);
            });
        });
    }

    // ------------------------------------------------------------ first open

    pub fn draw_welcome(&mut self, ui: &mut Ui, full: Rect) {
        self.drag_area(ui, Rect::from_min_max(full.min, pos2(full.right(), full.top() + TOP)));
        let logo = Rect::from_center_size(pos2(full.left() + 37.0, full.top() + TOP / 2.0), vec2(26.0, 26.0));
        self.logo(ui, logo);
        let g = line(ui, "yrs.", font(F::Display, 17.0), WHITE, f32::INFINITY, -0.5);
        ui.painter().galley(pos2(logo.right() + 9.0, logo.center().y - g.size().y / 2.0), g, WHITE);
        self.window_buttons(ui, full.right(), full.top() + TOP / 2.0, false);
        let x = full.left() + (full.width() * 0.09).max(48.0);
        let w = (full.width() - x * 2.0).min(620.0);
        let mut y = full.top() + full.height() * 0.3;
        let g = para(ui, "Drop a folder of music here.", font(F::Display, 40.0), WHITE, w, 44.0);
        let h = g.size().y;
        ui.painter().galley(pos2(x, y), g, WHITE);
        y += h + 16.0;
        let g = para(ui, "Or choose one. yrs. music reads it in place, keeps nothing online, and finds new songs when you add them.", font(F::Regular, 17.0), MUTE, w, 26.0);
        let h = g.size().y;
        ui.painter().galley(pos2(x, y), g, WHITE);
        y += h + 30.0;
        let a = self.accent;
        if self.word(ui, pos2(x, y), "Choose a folder", font(F::Semi, 16.0), a, WHITE, "w-choose").clicked() { self.pick_folder(); }
        let music = crate::library::music_dir();
        if music.is_dir() {
            let ctx = ui.ctx().clone();
            let home = crate::library::home();
            let shown = music.strip_prefix(&home).map(|p| format!("Use ~/{}", p.display())).unwrap_or_else(|_| format!("Use {}", music.display()));
            if self.word(ui, pos2(x + 160.0, y), &shown, font(F::Semi, 16.0), MUTE, WHITE, "w-music").clicked() { self.add_folder(&ctx, music); }
        }
        let by = full.bottom() - 44.0;
        ui.painter().hline(x..=full.right() - x, by, egui::Stroke::new(1.0_f32, HAIR));
        ui.painter().galley(pos2(x, by + 18.0), meta(ui, "Space play · ← → seek · / search · M mini player", DIM, w + 200.0), DIM);
        // a folder hovering over the window
        if ui.ctx().input(|i| !i.raw.hovered_files.is_empty()) {
            ui.painter().rect_stroke(full.shrink(14.0), CornerRadius::same(16), egui::Stroke::new(1.5_f32, a), egui::StrokeKind::Inside);
        }
    }

    // ------------------------------------------------------------ toast and dialogs

    pub fn draw_toast(&mut self, ui: &mut Ui, full: Rect) {
        let Some((text, at)) = self.toast.clone() else { return };
        let now = ui.ctx().input(|i| i.time);
        let age = (now - at) as f32;
        if age > 2.8 { self.toast = None; return; }
        let alpha = (age / 0.15).min(1.0).min((2.8 - age) / 0.35).clamp(0.0, 1.0);
        let g = line(ui, &text, font(F::Medium, 14.0), WHITE, 600.0, 0.0);
        let base = if self.player.current().is_some() { full.bottom() - BAR - 18.0 } else { full.bottom() - 30.0 };
        let r = Rect::from_center_size(pos2(full.center().x, base - 20.0 + (1.0 - alpha) * 8.0), g.size() + vec2(32.0, 22.0));
        let p = ui.painter();
        p.rect_filled(r, CornerRadius::same(12), Color32::from_rgba_unmultiplied(11, 11, 12, (alpha * 255.0) as u8));
        p.rect_stroke(r, CornerRadius::same(12), egui::Stroke::new(1.0_f32, EDGE.gamma_multiply(alpha)), egui::StrokeKind::Inside);
        p.galley(r.center() - g.size() / 2.0, g, WHITE.gamma_multiply(alpha));
        ui.ctx().request_repaint_after(std::time::Duration::from_millis(30));
    }

    pub fn draw_dialog(&mut self, ctx: &egui::Context) {
        let Some(d) = self.dialog.take() else { return };
        let mut keep = true;
        let mut next: Option<Dialog> = None;
        let accent = self.accent;
        let shown = egui::Modal::new(Id::new("dialog")).backdrop_color(Color32::from_black_alpha(170)).frame(egui::Frame::NONE.fill(BLACK).stroke(egui::Stroke::new(1.0_f32, EDGE)).corner_radius(16).inner_margin(26)).show(ctx, |ui| {
            ui.set_width(380.0);
            match d {
                Dialog::NewList { mut name, add } => {
                    ui.label(egui::RichText::new("New playlist").font(font(F::Display, 24.0)).color(WHITE));
                    ui.add_space(14.0);
                    let r = ui.add(egui::TextEdit::singleline(&mut name).hint_text("Name").font(font(F::Regular, 17.0)).frame(false).desired_width(f32::INFINITY));
                    r.request_focus();
                    ui.painter().hline(r.rect.x_range(), r.rect.bottom() + 4.0, egui::Stroke::new(1.0_f32, GHOST));
                    ui.add_space(20.0);
                    let enter = ui.input(|i| i.key_pressed(egui::Key::Enter));
                    ui.horizontal(|ui| {
                        if ui.add(egui::Button::new(egui::RichText::new("Cancel").color(MUTE))).clicked() { keep = false; }
                        ui.add_space(ui.available_width() - 70.0);
                        if ui.add(egui::Button::new(egui::RichText::new("Save").color(accent).font(font(F::Semi, 14.0)))).clicked() || enter {
                            match self.new_list(&name, &add) {
                                Some(_) => { keep = false; let c = ui.ctx().clone(); self.say(&c, if add.is_empty() { format!("Made {}", name.trim()) } else { format!("Added to {}", name.trim()) }); }
                                None => { let c = ui.ctx().clone(); self.say(&c, "Pick a name that isn't taken"); }
                            }
                        }
                    });
                    if keep { next = Some(Dialog::NewList { name, add }); }
                }
                Dialog::Rename { i, mut name } => {
                    ui.label(egui::RichText::new("Rename playlist").font(font(F::Display, 24.0)).color(WHITE));
                    ui.add_space(14.0);
                    let r = ui.add(egui::TextEdit::singleline(&mut name).font(font(F::Regular, 17.0)).frame(false).desired_width(f32::INFINITY));
                    r.request_focus();
                    ui.painter().hline(r.rect.x_range(), r.rect.bottom() + 4.0, egui::Stroke::new(1.0_f32, GHOST));
                    ui.add_space(20.0);
                    let enter = ui.input(|i| i.key_pressed(egui::Key::Enter));
                    ui.horizontal(|ui| {
                        if ui.button(egui::RichText::new("Cancel").color(MUTE)).clicked() { keep = false; }
                        ui.add_space(ui.available_width() - 70.0);
                        if ui.button(egui::RichText::new("Save").color(accent).font(font(F::Semi, 14.0))).clicked() || enter {
                            let clean = name.trim().to_string();
                            let taken = self.store.lists.iter().enumerate().any(|(j, l)| j != i && l.name.eq_ignore_ascii_case(&clean));
                            if !clean.is_empty() && !taken { if let Some(l) = self.store.lists.get_mut(i) { l.name = clean; } self.dirty(); keep = false; }
                        }
                    });
                    if keep { next = Some(Dialog::Rename { i, name }); }
                }
                Dialog::Delete { i } => {
                    let name = self.store.lists.get(i).map(|l| l.name.clone()).unwrap_or_default();
                    ui.label(egui::RichText::new(format!("Delete “{name}”?")).font(font(F::Display, 24.0)).color(WHITE));
                    ui.add_space(8.0);
                    ui.label(egui::RichText::new("The songs stay on your computer.").color(MUTE).font(font(F::Regular, 15.0)));
                    ui.add_space(20.0);
                    ui.horizontal(|ui| {
                        if ui.button(egui::RichText::new("Cancel").color(MUTE)).clicked() { keep = false; }
                        ui.add_space(ui.available_width() - 80.0);
                        if ui.button(egui::RichText::new("Delete").color(Color32::from_rgb(255, 138, 128)).font(font(F::Semi, 14.0))).clicked() {
                            if i < self.store.lists.len() { self.store.lists.remove(i); }
                            if let View::List(Some(j)) = self.view { if j == i { self.back() } else if j > i { self.view = View::List(Some(j - 1)) } }
                            self.dirty();
                            keep = false;
                        }
                    });
                    if keep { next = Some(Dialog::Delete { i }); }
                }
            }
        });
        if shown.should_close() { keep = false; }
        if keep { self.dialog = next; }
    }
}
