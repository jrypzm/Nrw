//! Covers load off the main thread, at the size they are shown, and arrive as textures.
//! The large one also yields the one colour the player borrows.

use crate::library::{cover_path, Size};
use egui::{Color32, ColorImage, Context, TextureHandle, TextureOptions};
use std::collections::{HashMap, HashSet};
use std::sync::mpsc::{channel, Receiver, Sender};

pub struct Covers {
    tex: HashMap<(String, Size), Option<TextureHandle>>,
    pending: HashSet<(String, Size)>,
    pub accents: HashMap<String, Color32>,
    tx: Sender<(String, Size)>,
    rx: Receiver<(String, Size, Option<ColorImage>, Option<Color32>)>,
}

impl Covers {
    pub fn new(ctx: &Context) -> Self {
        let (tx, jobs) = channel::<(String, Size)>();
        let (done, rx) = channel();
        let ctx = ctx.clone();
        let jobs = std::sync::Arc::new(std::sync::Mutex::new(jobs));
        for _ in 0..3 {
            let jobs = jobs.clone();
            let done = done.clone();
            let ctx = ctx.clone();
            std::thread::spawn(move || loop {
                let job = { let j = jobs.lock().unwrap(); j.recv() };
                let Ok((key, size)) = job else { return };
                let img = image::open(cover_path(&key, size)).ok().map(|i| i.to_rgba8());
                let accent = if size == Size::S { img.as_ref().map(pick) } else { None };
                let ci = img.map(|i| ColorImage::from_rgba_unmultiplied([i.width() as usize, i.height() as usize], i.as_raw()));
                let _ = done.send((key, size, ci, accent));
                ctx.request_repaint();
            });
        }
        Covers { tex: HashMap::new(), pending: HashSet::new(), accents: HashMap::new(), tx, rx }
    }

    /// Take in what the loaders finished. Call once per frame.
    pub fn pump(&mut self, ctx: &Context) {
        while let Ok((key, size, img, accent)) = self.rx.try_recv() {
            self.pending.remove(&(key.clone(), size));
            if let Some(a) = accent { self.accents.insert(key.clone(), a); }
            let t = img.map(|i| ctx.load_texture(format!("cover-{key}-{size:?}"), i, TextureOptions { magnification: egui::TextureFilter::Linear, minification: egui::TextureFilter::Linear, mipmap_mode: Some(egui::TextureFilter::Linear), ..Default::default() }));
            self.tex.insert((key, size), t);
        }
    }

    /// The texture if ready; otherwise ask for it (and maybe a smaller one meanwhile).
    pub fn get(&mut self, key: &str, size: Size) -> Option<TextureHandle> {
        let k = (key.to_string(), size);
        if let Some(t) = self.tex.get(&k) { return t.clone(); }
        if self.pending.insert(k.clone()) { let _ = self.tx.send(k); }
        if size != Size::S { if let Some(Some(t)) = self.tex.get(&(key.to_string(), Size::S)) { return Some(t.clone()); } }
        None
    }

    pub fn accent(&mut self, key: &str) -> Option<Color32> {
        if let Some(a) = self.accents.get(key) { return Some(*a); }
        let _ = self.get(key, Size::S);
        None
    }

    /// Forget everything, after a rescan made new covers.
    pub fn reset(&mut self) {
        self.tex.clear();
        self.pending.clear();
        self.accents.clear();
    }
}

/// Weigh every pixel by how alive it is, take the strongest hue, then make it sit well on black.
pub fn pick(img: &image::RgbaImage) -> Color32 {
    let mut bins = [0f64; 24];
    let mut sums = [[0f64; 3]; 24];
    let step = ((img.width() * img.height()) as usize / 4000).max(1);
    for (i, p) in img.pixels().enumerate() {
        if i % step != 0 { continue; }
        let (h, s, v) = hsv(p[0], p[1], p[2]);
        if s < 0.18 || v < 0.22 { continue; }
        let b = ((h / 15.0) as usize).min(23);
        let w = (s * v) as f64;
        bins[b] += w;
        sums[b][0] += h as f64 * w;
        sums[b][1] += s as f64 * w;
    }
    let total: f64 = bins.iter().sum();
    let (best, wt) = bins.iter().enumerate().fold((0, 0.0), |a, (i, &w)| if w > a.1 { (i, w) } else { a });
    if wt <= 0.0 || wt < total * 0.05 || wt < 3.0 { return crate::theme::ACCENT; }
    let h = (sums[best][0] / wt) as f32;
    let s = ((sums[best][1] / wt) as f32).clamp(0.32, 0.62);
    rgb(h, s, 0.95)
}

fn hsv(r: u8, g: u8, b: u8) -> (f32, f32, f32) {
    let (r, g, b) = (r as f32 / 255.0, g as f32 / 255.0, b as f32 / 255.0);
    let max = r.max(g).max(b);
    let min = r.min(g).min(b);
    let d = max - min;
    let h = if d == 0.0 { 0.0 } else if max == r { 60.0 * (((g - b) / d) % 6.0) } else if max == g { 60.0 * ((b - r) / d + 2.0) } else { 60.0 * ((r - g) / d + 4.0) };
    let h = if h < 0.0 { h + 360.0 } else { h };
    (h, if max == 0.0 { 0.0 } else { d / max }, max)
}

fn rgb(h: f32, s: f32, v: f32) -> Color32 {
    let c = v * s;
    let x = c * (1.0 - ((h / 60.0) % 2.0 - 1.0).abs());
    let m = v - c;
    let (r, g, b) = match (h / 60.0) as u32 { 0 => (c, x, 0.0), 1 => (x, c, 0.0), 2 => (0.0, c, x), 3 => (0.0, x, c), 4 => (x, 0.0, c), _ => (c, 0.0, x) };
    Color32::from_rgb(((r + m) * 255.0) as u8, ((g + m) * 255.0) as u8, ((b + m) * 255.0) as u8)
}
