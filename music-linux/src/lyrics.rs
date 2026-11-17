//! Lyrics from this computer only: a .lrc file beside the song, or the song's own tag.

use std::path::{Path, PathBuf};

pub enum Lyrics {
    Synced(Vec<(f32, String)>),
    Plain(Vec<String>),
}

/// Where lyrics chosen for a song are kept (the song's folder stays untouched).
pub fn saved(song: &Path) -> PathBuf {
    crate::library::data_dir().join("lyrics").join(format!("{}.lrc", crate::library::hash(&song.to_string_lossy())))
}

pub fn find(song: &Path) -> Option<Lyrics> {
    if let Ok(s) = std::fs::read_to_string(saved(song)) {
        if let Some(l) = parse(&s) { return Some(l); }
    }
    let base = song.with_extension("");
    for ext in ["lrc", "LRC", "txt"] {
        let p = base.with_extension(ext);
        if let Ok(s) = std::fs::read_to_string(&p) {
            if let Some(l) = parse(&s) { return Some(l); }
        }
    }
    crate::library::tag_lyrics(song).and_then(|s| parse(&s))
}

/// `[mm:ss.xx] words`, several stamps per line allowed; anything else is plain text.
pub fn parse(text: &str) -> Option<Lyrics> {
    let mut synced = vec![];
    let mut plain = vec![];
    for raw in text.replace('\r', "").lines() {
        let mut rest = raw.trim();
        let mut stamps = vec![];
        while let Some(stripped) = rest.strip_prefix('[') {
            let Some(end) = stripped.find(']') else { break };
            let inside = &stripped[..end];
            if let Some(t) = stamp(inside) { stamps.push(t); } else if stamps.is_empty() && inside.contains(':') {
                // a header such as [ar:Artist]; skip the whole line
                stamps.clear();
                rest = "";
                break;
            }
            rest = stripped[end + 1..].trim_start();
        }
        if stamps.is_empty() {
            if !rest.is_empty() || !plain.is_empty() { plain.push(rest.to_string()); }
        } else {
            for t in stamps { synced.push((t, rest.trim().to_string())); }
        }
    }
    if !synced.is_empty() {
        synced.sort_by(|a, b| a.0.total_cmp(&b.0));
        return Some(Lyrics::Synced(synced));
    }
    while plain.last().map(|l| l.is_empty()).unwrap_or(false) { plain.pop(); }
    if plain.is_empty() { None } else { Some(Lyrics::Plain(plain)) }
}

fn stamp(s: &str) -> Option<f32> {
    let (m, rest) = s.split_once(':')?;
    let m: f32 = m.trim().parse().ok()?;
    let sec: f32 = rest.trim().replace(':', ".").parse().ok()?;
    Some(m * 60.0 + sec)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn synced_and_headers() {
        let l = parse("[ar:Mara Vell]\n[00:01.50]first\n[00:04.00][00:10.00]twice\nnot timed").unwrap();
        match l {
            Lyrics::Synced(v) => {
                assert_eq!(v.len(), 3);
                assert!((v[0].0 - 1.5).abs() < 0.01);
                assert_eq!(v[2].1, "twice");
            }
            _ => panic!("expected synced"),
        }
    }
    #[test]
    fn plain_text() {
        match parse("one\ntwo\n\n").unwrap() { Lyrics::Plain(v) => assert_eq!(v, vec!["one", "two"]), _ => panic!() }
    }
}
