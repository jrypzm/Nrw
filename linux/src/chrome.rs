//! The window itself: yrs. draws its own title bar, so it looks like the rest of the app.
//! Drag the top bar to move, double-click it to maximise, pull any edge or corner to resize.

use crate::ui::*;
use egui::{Color32, Context, CornerRadius, CursorIcon, Id, Pos2, Rect, ResizeDirection, Sense, Stroke, StrokeKind, Ui, Vec2, ViewportCommand};

/// Rounded corners while the window floats; none when it fills the screen.
pub const RADIUS: u8 = 14;

pub fn maximized(ctx: &Context) -> bool { ctx.input(|i| i.viewport().maximized.unwrap_or(false) || i.viewport().fullscreen.unwrap_or(false)) }

/// The window's own background: black, with rounded corners and a faint edge when it isn't maximised.
pub fn background(ctx: &Context) {
    let r = ctx.screen_rect();
    let p = ctx.layer_painter(egui::LayerId::background());
    if maximized(ctx) { p.rect_filled(r, 0.0, BG); return; }
    p.rect_filled(r, CornerRadius::same(RADIUS), BG);
    p.rect_stroke(r, CornerRadius::same(RADIUS), Stroke::new(1.0, HAIR2), StrokeKind::Inside);
}

/// The top strip moves the window. Call it before the controls on it, so they stay clickable.
pub fn drag_strip(ui: &Ui, r: Rect) {
    let resp = ui.interact(r, Id::new("window-drag"), Sense::click_and_drag());
    let ctx = ui.ctx();
    if resp.double_clicked() { ctx.send_viewport_cmd(ViewportCommand::Maximized(!maximized(ctx))); }
    else if resp.drag_started() { ctx.send_viewport_cmd(ViewportCommand::StartDrag); }
}

/// Resize from any edge or corner, like any other window.
pub fn edges(ctx: &Context) {
    if maximized(ctx) { return; }
    let Some(pos) = ctx.input(|i| i.pointer.hover_pos()) else { return };
    let r = ctx.screen_rect();
    let (e, c) = (6.0, 16.0);
    let (l, rt, t, b) = (pos.x - r.left() < e, r.right() - pos.x < e, pos.y - r.top() < e, r.bottom() - pos.y < e);
    let (lc, rc, tc, bc) = (pos.x - r.left() < c, r.right() - pos.x < c, pos.y - r.top() < c, r.bottom() - pos.y < c);
    let dir = if (t && lc) || (l && tc) { Some((ResizeDirection::NorthWest, CursorIcon::ResizeNorthWest)) }
        else if (t && rc) || (rt && tc) { Some((ResizeDirection::NorthEast, CursorIcon::ResizeNorthEast)) }
        else if (b && lc) || (l && bc) { Some((ResizeDirection::SouthWest, CursorIcon::ResizeSouthWest)) }
        else if (b && rc) || (rt && bc) { Some((ResizeDirection::SouthEast, CursorIcon::ResizeSouthEast)) }
        else if l { Some((ResizeDirection::West, CursorIcon::ResizeWest)) }
        else if rt { Some((ResizeDirection::East, CursorIcon::ResizeEast)) }
        else if t { Some((ResizeDirection::North, CursorIcon::ResizeNorth)) }
        else if b { Some((ResizeDirection::South, CursorIcon::ResizeSouth)) }
        else { None };
    if let Some((d, icon)) = dir {
        ctx.set_cursor_icon(icon);
        if ctx.input(|i| i.pointer.primary_pressed()) { ctx.send_viewport_cmd(ViewportCommand::BeginResize(d)); }
    }
}

/// Minimise, maximise and close, top right, in the app's own style. Drawn above everything, sheets included.
pub fn controls(ctx: &Context, opacity: f32) {
    let sr = ctx.screen_rect();
    let y = sr.top() + 40.0;
    egui::Area::new(Id::new("window-controls")).order(egui::Order::Foreground).fixed_pos(Pos2::new(sr.right() - 126.0, y - 18.0)).show(ctx, |ui| {
        ui.multiply_opacity(opacity);
        ui.set_min_size(Vec2::new(120.0, 36.0));
        let max = maximized(ctx);
        let xs = [sr.right() - 104.0, sr.right() - 70.0, sr.right() - 36.0];
        for (k, x) in xs.iter().enumerate() {
            let c = Pos2::new(*x, y);
            let rect = Rect::from_center_size(c, Vec2::splat(28.0));
            let resp = ui.interact(rect, Id::new(("winbtn", k)), Sense::click());
            let h = tweenf(ctx, Id::new(("winbtn-h", k)), if resp.hovered() { 1.0 } else { 0.0 }, 0.14);
            let close = k == 2;
            let p = ui.painter();
            // close turns white on hover; the others lift a little
            let fill = if close { lerp_c(GLASS, FG, h) } else { lerp_c(GLASS, SURFACE2, h) };
            let ink = if close { lerp_c(MUTE, Color32::BLACK, h) } else { lerp_c(MUTE, FG, h) };
            p.circle_filled(c, 14.0, fill);
            p.circle_stroke(c, 13.5, Stroke::new(1.0, alpha(HAIR2, 1.0 - h * if close { 1.0 } else { 0.0 })));
            let s = Stroke::new(1.4, ink);
            match k {
                0 => { p.line_segment([c + Vec2::new(-4.5, 0.0), c + Vec2::new(4.5, 0.0)], s); }
                1 => {
                    if max {
                        // two squares: restore
                        p.rect_stroke(Rect::from_center_size(c + Vec2::new(-1.2, 1.2), Vec2::splat(7.0)), CornerRadius::same(2), s, StrokeKind::Middle);
                        p.line_segment([c + Vec2::new(-1.2, -3.8), c + Vec2::new(3.8, -3.8)], s);
                        p.line_segment([c + Vec2::new(3.8, -3.8), c + Vec2::new(3.8, 1.2)], s);
                    } else {
                        p.rect_stroke(Rect::from_center_size(c, Vec2::splat(8.5)), CornerRadius::same(2), s, StrokeKind::Middle);
                    }
                }
                _ => { let d = 4.0; p.line_segment([c + Vec2::new(-d, -d), c + Vec2::new(d, d)], s); p.line_segment([c + Vec2::new(-d, d), c + Vec2::new(d, -d)], s); }
            }
            if resp.clicked() {
                match k {
                    0 => ctx.send_viewport_cmd(ViewportCommand::Minimized(true)),
                    1 => ctx.send_viewport_cmd(ViewportCommand::Maximized(!max)),
                    _ => ctx.send_viewport_cmd(ViewportCommand::Close),
                }
            }
            if resp.hovered() { ctx.set_cursor_icon(CursorIcon::Default); }
        }
    });
}
