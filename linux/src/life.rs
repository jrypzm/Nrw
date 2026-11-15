//! The maths of a life: the same rules as the Android and web apps.

use crate::data::{COUNTRIES, ERA, EST_Q, EVENTS};
use crate::store::Settings;
use chrono::{Datelike, Duration, Local, NaiveDate, NaiveDateTime, Timelike};

// ================================================================== dates

pub fn today() -> NaiveDate { Local::now().date_naive() }
pub fn now() -> NaiveDateTime { Local::now().naive_local() }

/// Your birthday in a given year. 29 February falls on 28 February in other years.
pub fn birthday_in(b: NaiveDate, y: i32) -> NaiveDate {
    NaiveDate::from_ymd_opt(y, b.month(), b.day()).unwrap_or_else(|| NaiveDate::from_ymd_opt(y, 2, 28).unwrap())
}

pub fn whole_years(b: NaiveDate, t: NaiveDate) -> i32 {
    let mut y = t.year() - b.year();
    if birthday_in(b, t.year()) > t { y -= 1; }
    y.max(0)
}

/// Exact age: whole years, plus how far you are between your last birthday and the next.
pub fn age_at(b: NaiveDate, n: NaiveDateTime) -> f64 {
    let t = n.date();
    if t <= b { return 0.0; }
    let y = whole_years(b, t);
    let last = birthday_in(b, b.year() + y).and_hms_opt(0, 0, 0).unwrap();
    let next = birthday_in(b, b.year() + y + 1).and_hms_opt(0, 0, 0).unwrap();
    let f = (n - last).num_milliseconds() as f64 / ((next - last).num_milliseconds().max(1)) as f64;
    y as f64 + f.clamp(0.0, 0.999_999)
}
pub fn age(b: NaiveDate) -> f64 { age_at(b, now()) }

pub fn is_birthday(b: NaiveDate) -> bool { let t = today(); t > b && birthday_in(b, t.year()) == t }
pub fn last_birthday(b: NaiveDate, t: NaiveDate) -> NaiveDate { birthday_in(b, b.year() + whole_years(b, t)) }
pub fn next_birthday(b: NaiveDate, t: NaiveDate) -> NaiveDate { birthday_in(b, last_birthday(b, t).year() + 1) }
pub fn day_of_life(b: NaiveDate, t: NaiveDate) -> i64 { (t - b).num_days() + 1 }
pub fn days_lived(b: NaiveDate) -> f64 { ((now() - b.and_hms_opt(0, 0, 0).unwrap()).num_milliseconds() as f64 / 86_400_000.0).max(0.0) }
pub fn end_of(s: &Settings) -> NaiveDate { s.birth().map(|b| birthday_in(b, b.year() + s.span as i32)).unwrap_or_else(today) }
pub fn days_left_exact(s: &Settings) -> f64 { ((end_of(s).and_hms_opt(0, 0, 0).unwrap() - now()).num_milliseconds() as f64 / 86_400_000.0).max(0.0) }
pub fn years_left(s: &Settings) -> f64 { s.birth().map(|b| (s.span as f64 - age(b)).max(0.0)).unwrap_or(0.0) }

pub const MON: [&str; 12] = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
pub const MONTHS: [&str; 12] = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
pub const WEEKDAYS: [&str; 7] = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
pub fn mon(d: NaiveDate) -> &'static str { MON[d.month0() as usize] }
pub fn date_str(d: NaiveDate) -> String { format!("{} {} {}", d.day(), mon(d), d.year()) }

// ================================================================== numbers

/// "1,234": always grouped with commas.
pub fn whole(v: f64) -> String { group(v.max(0.0).floor() as i64) }
pub fn group(n: i64) -> String {
    let s = n.abs().to_string();
    let mut out = String::new();
    for (i, c) in s.chars().enumerate() { if i > 0 && (s.len() - i) % 3 == 0 { out.push(','); } out.push(c); }
    if n < 0 { format!("-{out}") } else { out }
}
pub fn one(v: f64) -> String { format!("{:.1}", v) }
pub fn live(v: f64) -> String { let w = v.floor(); format!("{}{}", group(w as i64), &format!("{:.8}", v - w)[1..]) }

// ================================================================== the grids

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Kind { Life, Decade, Year }

/// One page's worth of dots.
pub struct Grid {
    pub kind: Kind,
    pub total: usize,
    pub done: f64,
    pub cols: usize,
    pub unit: &'static str,
    pub context: Option<String>,
    pub sub: String,
    /// The calendar year of a dot, when a dot is a year of your life.
    pub year_index: Option<i32>,
    /// zoom level for Life
    pub zoom: usize,
    b: NaiveDate,
    start: i32,
}

pub const ZOOMS: [&str; 5] = ["History", "Years", "Months", "Weeks", "Days"];
/// About 300,000 years of our species, as 80-year lives laid end to end.
pub const LIFETIMES: usize = 3750;

impl Grid {
    pub fn new(s: &Settings, kind: Kind) -> Grid {
        let b = s.birth().unwrap_or_else(|| today() - Duration::days(365 * 30));
        let a = age(b);
        let lived = a.floor() as i32;
        let span = s.span as f64;
        let base = |total, done, cols, unit, sub: String| Grid { kind, total, done, cols, unit, context: None, sub, year_index: None, zoom: s.zoom, b, start: 0 };
        match kind {
            Kind::Decade => {
                let st = lived / 10 * 10;
                let mut g = base(10, (a - st as f64).clamp(0.0, 10.0), 5, "year", format!("Ages {st}–{}", st + 9));
                g.context = Some(if st == 0 { "Your first decade".into() } else { format!("Your {st}s") });
                g.start = st; g.year_index = Some(st);
                g
            }
            Kind::Year => {
                let t = today();
                let lb = last_birthday(b, t);
                let mut m = 0;
                while m < 11 && add_months(lb, m + 1) <= t { m += 1; }
                let st = add_months(lb, m).and_hms_opt(0, 0, 0).unwrap();
                let en = if m == 11 { birthday_in(b, lb.year() + 1) } else { add_months(lb, m + 1) }.and_hms_opt(0, 0, 0).unwrap();
                let f = ((now() - st).num_milliseconds() as f64 / (en - st).num_milliseconds().max(1) as f64).clamp(0.0, 1.0);
                let next = birthday_in(b, lb.year() + 1);
                let mut g = base(12, (m as f64 + f).min(12.0), 6, "month", format!("Turning {} on {} {}", lived + 1, next.day(), mon(next)));
                g.context = Some(format!("Age {lived}"));
                g
            }
            Kind::Life => {
                let sub = format!("Age {lived} · {} year life", s.span);
                match s.zoom.min(4) {
                    0 => base(LIFETIMES, LIFETIMES as f64 - 1.0 + (a / span).clamp(0.0, 1.0), 75, "lifetime", "Yours is the last one, filling now".into()),
                    2 => base(s.span as usize * 12, (a * 12.0).clamp(0.0, span * 12.0), 36, "month", sub),
                    // the number of week dots is rounded, so the weeks lived are capped at that same whole number
                    3 => base((span * 52.1775).round() as usize, (a * 52.1775).clamp(0.0, (span * 52.1775).round()), 52, "week", sub),
                    4 => {
                        let total = (end_of(s) - b).num_days().max(1) as usize;
                        let done = ((today() - b).num_days() as f64 + now().num_seconds_from_midnight() as f64 / 86400.0).clamp(0.0, total as f64);
                        base(total, done, 146, "day", sub)
                    }
                    _ => { let mut g = base(s.span as usize, a.clamp(0.0, span), 10, "year", sub); g.year_index = Some(0); g }
                }
            }
        }
    }

    /// The age a dot stands for, when dots are years.
    pub fn year_of(&self, i: usize) -> Option<i32> { self.year_index.map(|s| s + i as i32) }

    pub fn label(&self, i: usize) -> String {
        let b = self.b;
        match (self.kind, self.unit) {
            (Kind::Decade, _) => format!("Age {} · {}", self.start + i as i32, b.year() + self.start + i as i32),
            (Kind::Year, _) => { let d = add_months(last_birthday(b, today()), i as u32); format!("{} {}", MONTHS[d.month0() as usize], d.year()) }
            (_, "lifetime") => if i == LIFETIMES - 1 { "Your life".into() } else { format!("About {} years ago", group(((LIFETIMES - 1 - i) * 80) as i64)) },
            (_, "month") => { let d = add_months(b, i as u32); format!("{} {} · age {}", mon(d), d.year(), i / 12) }
            (_, "week") => { let d = b + Duration::weeks(i as i64); format!("Week of {}", date_str(d)) }
            (_, "day") => { let d = b + Duration::days(i as i64); format!("{} {}", &WEEKDAYS[d.weekday().num_days_from_monday() as usize][..3], date_str(d)) }
            _ => format!("Age {i} · {}", b.year() + i as i32),
        }
    }
    pub fn day(&self, i: usize) -> NaiveDate { self.b + Duration::days(i as i64) }
}

pub fn add_months(d: NaiveDate, m: u32) -> NaiveDate { d.checked_add_months(chrono::Months::new(m)).unwrap_or(d) }

/// Ways to count what's left.
pub struct Measure { pub one: &'static str, pub many: &'static str, pub per_year: f64 }
pub const MEASURES: [Measure; 6] = [
    Measure { one: "year", many: "years", per_year: 1.0 },
    Measure { one: "day", many: "days", per_year: 365.2425 },
    Measure { one: "week", many: "weeks", per_year: 52.1775 },
    Measure { one: "summer", many: "summers", per_year: 1.0 },
    Measure { one: "weekend", many: "weekends", per_year: 52.1775 },
    Measure { one: "full moon", many: "full moons", per_year: 365.2425 / 29.530589 },
];

/// What the big number says.
pub struct Headline { pub value: f64, pub noun: String, pub word: &'static str, pub pct: f64 }

pub fn headline(s: &Settings, g: &Grid) -> Headline {
    if g.unit == "lifetime" {
        let f = (g.done - (g.total as f64 - 1.0)) * 100.0;
        return Headline { value: g.total as f64, noun: "lives".into(), word: "of human history", pct: if s.lived { f } else { 100.0 - f } };
    }
    let left = (g.total as f64 - g.done).max(0.0);
    let (one, many, per) = match (g.kind, g.unit) {
        (Kind::Life, "month") => ("month", "months", 12.0),
        (Kind::Life, "week") => ("week", "weeks", 52.1775),
        (Kind::Life, "day") => ("day", "days", 365.2425),
        (Kind::Life, _) => { let m = &MEASURES[s.unit % MEASURES.len()]; (m.one, m.many, m.per_year) }
        (_, u) => (u, if u == "year" { "years" } else { "months" }, 1.0),
    };
    let base = if g.kind == Kind::Life {
        let lived = s.birth().map(|b| age(b).min(s.span as f64)).unwrap_or(0.0);
        if s.lived { lived } else { (s.span as f64 - lived).max(0.0) }
    } else if s.lived { g.done } else { left };
    let v = if g.kind == Kind::Life && one == "day" && s.birth().is_some() {
        if s.lived { days_lived(s.birth().unwrap()) } else { days_left_exact(s) }
    } else { base * per };
    let word = if !s.lived { "left" } else if g.kind == Kind::Year { "in" } else { "lived" };
    let pct = if g.kind == Kind::Life { base / s.span as f64 * 100.0 } else { base / g.total as f64 * 100.0 };
    Headline { value: v, noun: if v.floor() == 1.0 { one.into() } else { many.into() }, word, pct }
}

/// 0 at night, 1 at midday.
pub fn sun_level() -> f32 { let n = Local::now(); let h = n.hour() as f32 + n.minute() as f32 / 60.0; ((h - 6.0) / 12.0 * std::f32::consts::PI).sin().max(0.0) }

// ================================================================== people

pub struct Together { pub visits: i64, pub years: f64, pub their_age: i32 }

pub fn together(s: &Settings, p: &crate::store::Person) -> Together {
    let their = today().year() - p.born;
    let their_left = (s.span as f64 - their as f64).max(if their < 105 { 2.0f64.max((100.0 - their as f64) / 3.0) } else { 1.0 });
    let shared = years_left(s).min(their_left);
    Together { visits: (shared * p.per as f64).floor() as i64, years: shared, their_age: their }
}
pub const OFTEN: [(&str, u32); 5] = [("We live together", 365), ("Every week", 52), ("Every month", 12), ("A few times a year", 4), ("Once a year", 1)];

// ================================================================== where your years go

pub struct Slice { pub label: &'static str, pub years: f64, pub shade: u8 }

/// Work counts 48 weeks a year until you retire; commuting, five days a week.
pub fn ledger(s: &Settings) -> Vec<Slice> {
    let left = years_left(s);
    let a = s.birth().map(age).unwrap_or(30.0);
    let wy = (s.retire as f64 - a).min(left).max(0.0);
    let wk = 48.0 / 52.1775;
    let mut v = vec![
        Slice { label: "Asleep", years: left * s.sleep as f64 / 24.0, shade: 0x2c },
        Slice { label: "Working", years: wy * s.work as f64 / 168.0 * wk, shade: 0x48 },
        Slice { label: "Commuting", years: wy * s.commute as f64 / 60.0 * 5.0 / 168.0 * wk, shade: 0x5a },
        Slice { label: "Eating and chores", years: left * s.chores as f64 / 24.0, shade: 0x3a },
        Slice { label: "On your screen", years: left * s.phone as f64 / 24.0, shade: 0x8e },
    ];
    let used: f64 = v.iter().map(|x| x.years).sum();
    if used > left && used > 0.0 { for x in v.iter_mut() { x.years *= left / used; } }
    let used: f64 = v.iter().map(|x| x.years).sum();
    v.push(Slice { label: "Truly yours", years: (left - used).max(0.0), shade: 0xff });
    v
}

// ================================================================== estimate my lifespan
// A Gompertz–Makeham curve calibrated to UN life expectancy for your country and sex; each answer scales the yearly
// risk by a published hazard ratio, re-centred on how common it is; clustered habits are discounted to match Li et al.

pub fn country(iso: &str) -> &'static crate::data::Country { COUNTRIES.iter().find(|c| c.iso == iso).unwrap_or_else(|| COUNTRIES.iter().find(|c| c.iso == "WLD").unwrap()) }
pub fn place_name(iso: &str) -> &'static str { let c = country(iso); if c.iso == "WLD" { "the world" } else { c.name } }
pub fn base_le(iso: &str, sex: &str) -> f64 { let c = country(iso); match sex { "f" => c.women, "m" => c.men, _ => (c.women + c.men) / 2.0 } }

/// Your country from the system language (en_GB → GBR), or the world average.
pub fn guess_country() -> String {
    let lang = std::env::var("LC_ALL").ok().filter(|v| !v.is_empty()).or_else(|| std::env::var("LANG").ok()).unwrap_or_default();
    let region = lang.split(['_', '-']).nth(1).map(|r| r.chars().take(2).collect::<String>().to_uppercase()).unwrap_or_default();
    ISO2.iter().find(|(a, _)| *a == region).map(|(_, b)| b.to_string()).filter(|b| COUNTRIES.iter().any(|c| c.iso == b)).unwrap_or_else(|| "WLD".into())
}
const ISO2: &[(&str, &str)] = &[("US", "USA"), ("GB", "GBR"), ("DE", "DEU"), ("FR", "FRA"), ("IN", "IND"), ("AE", "ARE"), ("CA", "CAN"), ("AU", "AUS"), ("NZ", "NZL"), ("IE", "IRL"), ("ES", "ESP"), ("IT", "ITA"), ("NL", "NLD"), ("BE", "BEL"), ("CH", "CHE"), ("AT", "AUT"), ("SE", "SWE"), ("NO", "NOR"), ("DK", "DNK"), ("FI", "FIN"), ("PL", "POL"), ("PT", "PRT"), ("BR", "BRA"), ("MX", "MEX"), ("AR", "ARG"), ("JP", "JPN"), ("KR", "KOR"), ("CN", "CHN"), ("SG", "SGP"), ("PK", "PAK"), ("BD", "BGD"), ("SA", "SAU"), ("EG", "EGY"), ("ZA", "ZAF"), ("NG", "NGA"), ("KE", "KEN"), ("TR", "TUR"), ("RU", "RUS"), ("UA", "UKR"), ("ID", "IDN"), ("MY", "MYS"), ("PH", "PHL"), ("TH", "THA"), ("VN", "VNM"), ("IL", "ISR"), ("GR", "GRC"), ("CZ", "CZE"), ("HU", "HUN"), ("RO", "ROU"), ("CL", "CHL"), ("CO", "COL"), ("PE", "PER"), ("QA", "QAT"), ("KW", "KWT"), ("OM", "OMN"), ("BH", "BHR"), ("LK", "LKA"), ("NP", "NPL")];

const GB: f64 = 0.09;
const GL: f64 = 0.0004;
const CW: [f64; 8] = [1.0, 0.35, 0.25, 0.18, 0.14, 0.12, 0.1, 0.1];
fn child_deaths(le: f64) -> f64 { (-2.254 - 0.1256 * (le - 54.0)).exp().min(0.3) }
fn curve(a: f64, hr: f64, from: f64) -> Vec<f64> {
    let mut out = vec![1.0]; let (mut s, mut t) = (1.0f64, from);
    while t < 125.0 && s > 1e-6 { s *= (-hr * (GL + a * (GB * (t + 0.125)).exp()) * 0.25).exp(); out.push(s); t += 0.25; }
    out
}
fn rest_of(c: &[f64]) -> f64 { c.windows(2).map(|w| (w[0] + w[1]) / 2.0 * 0.25).sum() }
fn calibrate(le0: f64) -> f64 {
    let q5 = child_deaths(le0); let (mut lo, mut hi) = (1e-7f64, 1e-2f64);
    for _ in 0..50 { let mid = (lo * hi).sqrt(); let e = q5 * 0.5 + (1.0 - q5) * (5.0 + rest_of(&curve(mid, 1.0, 5.0))); if e > le0 { lo = mid } else { hi = mid } }
    (lo * hi).sqrt()
}
fn rel_hr(qi: usize, i: usize) -> f64 { let q = &EST_Q[qi]; q.options[i].hr / q.options.iter().map(|o| o.hr * o.share).sum::<f64>() }
fn combine(ls: &[f64]) -> f64 { let mut v = ls.to_vec(); v.sort_by(|a, b| b.abs().partial_cmp(&a.abs()).unwrap()); v.iter().enumerate().map(|(i, l)| l * CW.get(i).copied().unwrap_or(0.1)).sum::<f64>().exp() }

pub struct Estimate { pub years: f64, pub typical: f64, pub q25: f64, pub q50: f64, pub q75: f64, pub age_now: f64, curve: Vec<f64>, pub contrib: Vec<(usize, f64)> }
impl Estimate {
    pub fn chance(&self, y: f64) -> f64 { if y <= self.age_now { 1.0 } else { self.curve.get(((y - self.age_now) * 4.0).round() as usize).copied().unwrap_or(0.0) } }
}

pub fn estimate(e: &crate::store::Est, b: NaiveDate) -> Estimate {
    let age_now = age(b);
    let le0 = base_le(&e.country, &e.sex);
    let a = calibrate((le0 * 10.0).round() / 10.0);
    let logs: Vec<(usize, f64)> = EST_Q.iter().enumerate().filter_map(|(qi, q)| e.picks.get(q.key).filter(|&&i| i < q.options.len()).map(|&i| (qi, rel_hr(qi, i).ln()))).collect();
    let run = |ls: &[f64]| { let c = curve(a, combine(ls), age_now); let e = age_now + rest_of(&c); (c, e) };
    let (c, years) = run(&logs.iter().map(|x| x.1).collect::<Vec<_>>());
    let at = |p: f64| c.iter().position(|&s| s <= p).map(|i| age_now + i as f64 * 0.25).unwrap_or(125.0);
    let mut contrib: Vec<(usize, f64)> = logs.iter().map(|&(qi, _)| (qi, years - run(&logs.iter().filter(|x| x.0 != qi).map(|x| x.1).collect::<Vec<_>>()).1)).filter(|x| x.1.abs() >= 0.1).collect();
    contrib.sort_by(|x, y| y.1.partial_cmp(&x.1).unwrap());
    Estimate { years, typical: run(&[]).1, q25: at(0.75), q50: at(0.5), q75: at(0.25), age_now, curve: c, contrib }
}

// ================================================================== facts

pub fn year_label(y: i32) -> String { if y < 0 { format!("{} BCE", group(-y as i64)) } else if y < 1000 { format!("{y} CE") } else { y.to_string() } }

pub fn era_name(y0: i32) -> &'static str {
    match y0 {
        i32::MIN..=-115001 => "The Middle Stone Age", -115000..=-26001 => "The last ice age", -26000..=-19001 => "The coldest part of the ice age",
        -19000..=-11701 => "The ice age ends", -11700..=-3201 => "The first farmers", -3200..=-1201 => "The Bronze Age", -1200..=499 => "The ancient world",
        500..=1449 => "The Middle Ages", 1450..=1799 => "The early modern world", _ => "The modern world",
    }
}
pub fn life_start(birth_year: i32, i: usize) -> i32 { birth_year - ((LIFETIMES - 1 - i) * 80) as i32 }

/// What a history dot can say: its era, and facts worked out for its exact distance from you.
pub fn life_facts(s: &Settings, i: usize) -> Vec<String> {
    let Some(b) = s.birth() else { return vec![] };
    if i == LIFETIMES - 1 { return you_facts(s); }
    let y0 = life_start(b.year(), i); let y1 = y0 + 80;
    let ago = (today().year() - y0) as f64;
    let between = LIFETIMES - 1 - i;
    let mut v: Vec<String> = ERA.iter().filter(|(a, z, _)| y0 <= *z && y1 >= *a).map(|x| x.2.to_string()).collect();
    v.push(format!("This life began about {} years ago, around {}.", whole(ago), year_label(y0)));
    v.push(format!("That’s about {} generations back. One of your direct ancestors was alive in this lifetime, or you wouldn’t be here.", whole(ago / 25.0)));
    v.push(format!("{} whole lifetimes, end to end, separate this one from yours.", group(between as i64)));
    v.push(format!("If each lifetime took one second, you’d travel back to this one in {}.", if between < 60 { format!("{between} seconds") } else { format!("{} minutes {} seconds", between / 60, between % 60) }));
    v.push(format!("Since then, Earth has circled the Sun about {} times, and spun about {} times.", whole(ago), whole(ago * 365.2425)));
    v.push(format!("The Moon drifts away from Earth by about 3.8 cm a year, so it was about {} metres closer then.", whole(ago * 0.038)));
    let far = if ago > 100000.0 { ", farther than the whole width of the Milky Way" } else if ago > 26000.0 { ", past the centre of our galaxy" } else if ago > 4000.0 { ", farther than most stars you can see with your eyes" } else { "" };
    v.push(format!("Light that left Earth when this life began is now {} light-years away{far}.", whole(ago)));
    if y1 < -10000 { v.push(format!("This was about {} years before anyone, anywhere, planted a crop.", group((-10000 - y0) as i64))); }
    if y1 < -3200 { v.push(format!("It was about {} years before the first writing.", group((-3200 - y0) as i64))); }
    v.push(format!("By the start of this life, {:.1}% of the time since our species appeared had already passed.", i as f64 * 100.0 / LIFETIMES as f64));
    v
}

const POP: [(i32, f64); 13] = [(1900, 1.65), (1927, 2.0), (1950, 2.5), (1960, 3.0), (1974, 4.0), (1987, 5.0), (1999, 6.0), (2011, 7.0), (2022, 8.0), (2030, 8.5), (2050, 9.7), (2084, 10.3), (2100, 10.2)];
fn pop_in(y: i32) -> f64 {
    if y <= POP[0].0 { return POP[0].1; }
    for w in POP.windows(2) { if y <= w[1].0 { return w[0].1 + (w[1].1 - w[0].1) * (y - w[0].0) as f64 / (w[1].0 - w[0].0) as f64; } }
    POP[12].1
}
pub fn event(y: i32) -> Option<&'static str> { EVENTS.iter().find(|e| e.0 == y).map(|e| e.1) }

/// The world in a year of your life, and you in it. The event, if there is one, comes first.
pub fn year_facts(s: &Settings, yi: i32, lines: &std::collections::BTreeMap<String, String>) -> Vec<String> {
    let Some(b) = s.birth() else { return vec![] };
    let y = b.year() + yi; let t = today(); let lived = age(b) as i32;
    let mut v = vec![];
    if let Some(e) = event(y) { v.push(format!("In {y}: {e}")); }
    if let Some(e) = event(y + 1) { if y + 1 <= t.year() { v.push(format!("In {}, as you turned {}: {e}", y + 1, yi + 1)); } }
    v.push(if yi <= lived { format!("The world had about {:.1} billion people in {y}.", pop_in(y)) } else { format!("The world is projected to have about {:.1} billion people in {y}.", pop_in(y)) });
    let from = birthday_in(b, y); let to = birthday_in(b, y + 1);
    if from <= t { let ago = t.year() - y; v.push(format!("That was {}: about {} days back.", match ago { 0 => "this year".into(), 1 => "last year".into(), n => format!("{n} years ago") }, group((t - from).num_days()))); }
    else { v.push(format!("It starts in about {} days. You’ll be {yi} on your birthday that year.", group((from - t).num_days()))); }
    let days = (to - from).num_days();
    v.push(format!("This year of your life {} {days} days{}.", if yi <= lived { "had" } else { "will have" }, if days == 366 { ", thanks to a 29 February" } else { "" }));
    let n = lines.keys().filter(|k| NaiveDate::parse_from_str(k, "%Y-%m-%d").map(|d| d >= from && d < to).unwrap_or(false)).count();
    if n > 0 { v.push(format!("You wrote {n} {} in this year of your life.", if n == 1 { "line" } else { "lines" })); }
    v.push(format!("It’s year {} of the {} on your grid{}.", yi + 1, s.span, if yi < lived { "" } else if yi == lived { ", and it’s happening now" } else { "" }));
    v
}

/// Fun facts about you, from your birthday. All approximate, all averages.
pub fn you_facts(s: &Settings) -> Vec<String> {
    let Some(b) = s.birth() else { return vec![] };
    let t = today(); let days = days_lived(b); let yrs = days / 365.2425; let by = b.year();
    let olympics = (1896..=t.year()).step_by(4).filter(|y| ![1916, 1940, 1944].contains(y)).map(|y| if y == 2020 { 2021 } else { y }).filter(|&y| y > by || (y == by && b.month() < 8)).count();
    let cups = (1930..=t.year()).step_by(4).filter(|y| ![1942, 1946].contains(y)).filter(|&y| y > by).count();
    let leaps = (by..=t.year()).filter_map(|y| NaiveDate::from_ymd_opt(y, 2, 29)).filter(|&d| d > b && d <= t).count();
    let next = [10000.0, 15000.0, 20000.0, 25000.0, 30000.0].into_iter().find(|&n| n > days + 1.0);
    let mut v = vec![
        format!("Your heart has beaten roughly {} million times, at about 75 beats a minute.", whole(days * 24.0 * 60.0 * 75.0 / 1e6)),
        format!("You’ve taken roughly {} million breaths.", whole(days * 24.0 * 60.0 * 15.0 / 1e6)),
        format!("You’ve spent about {:.1} years asleep.", yrs / 3.0),
        format!("You’ve seen about {} full moons.", whole(days / 29.5306)),
        format!("You were born on a {}.", WEEKDAYS[b.weekday().num_days_from_monday() as usize]),
        format!("You’ve lived through {leaps} leap {}.", if leaps == 1 { "day" } else { "days" }),
        format!("{olympics} Summer Olympics have been held in your lifetime."),
        format!("{cups} FIFA World Cups have been played since you were born."),
        format!("Riding on Earth, you’ve travelled about {} billion kilometres around the Sun.", whole(yrs * 0.94)),
        format!("With the whole solar system, you’ve moved about {} billion kilometres around the centre of the galaxy.", whole(yrs * 7.26)),
        match next { Some(n) => { let d = b + Duration::days(n as i64 - 1); format!("Your {}th day of life is on {}.", whole(n), date_str(d)) } None => "You’ve passed your 30,000th day of life.".into() },
        format!("On Mars you’d be {:.1} years old. On Jupiter, {:.1}.", days / 686.98, days / 4332.6),
        if by < 1986 { "Halley’s Comet came by in 1986, during your lifetime. Its next visit is in 2061.".into() } else { format!("Halley’s Comet returns in 2061. You’ll be {}.", 2061 - by) },
        format!("Your scalp hair grows about 15 cm a year: around {} metres so far, laid end to end.", whole(yrs * 0.15)),
        format!("People speak about 16,000 words a day on average: something like {} million words for you so far.", whole((days - 730.0).max(0.0) * 16000.0 / 1e6)),
    ];
    if 2050 > by { v.push(format!("You’ll be {} when 2050 begins.", 2050 - by)); }
    v
}

/// A fact from the list, never the one shown just before.
pub fn pick(list: &[String], last: &str) -> String {
    use rand::seq::SliceRandom;
    if list.len() < 2 { return list.first().cloned().unwrap_or_default(); }
    // a few tries for a different one; never an endless search if they all read the same
    for _ in 0..24 { let f = list.choose(&mut rand::thread_rng()).unwrap(); if f != last { return f.clone(); } }
    list[0].clone()
}
