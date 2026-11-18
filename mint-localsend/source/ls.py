"""LocalSend icons, painted in the styles of the references.
  python3 ls.py <sleepy|ceramic|glow|mark> <out.png> [size]
"""
import math
import os
import sys

ARGS = sys.argv[1:]
sys.argv = [sys.argv[0], 'c-none', '/dev/null'] + ARGS[2:3]
HERE = os.path.dirname(os.path.abspath(__file__))
exec(open(os.path.join(HERE, 'paint.py')).read())   # shared painting tools: shapes, light, ceramic, tile

R_RING, W_SEG, DOT = 300, 54, 166


def segments(n=8, span=26.0, R=R_RING, w=W_SEG, offset=0.0, rr=None):
    """LocalSend's dashed ring: n rounded arc segments"""
    rr = w / 2 if rr is None else rr
    r = np.sqrt((X - 512) ** 2 + (Y - 512) ** 2)
    ang = np.degrees(np.arctan2(Y - 512, X - 512))
    d = np.full(X.shape, 1e9, np.float32)
    for i in range(n):
        c = offset + i * 360 / n
        da = np.abs(((ang - c + 180) % 360) - 180)
        qx = np.abs(r - R) - w / 2 + rr
        qy = (da - span / 2) * math.pi / 180 * R + rr
        dd = np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2) + np.minimum(np.maximum(qx, qy), 0) - rr
        d = np.minimum(d, dd)
    return d


def dot():
    return circle(512, 512, DOT)


def lid_eye(cx, cy, r, tilt=0.0):
    c = circle(cx, cy, r)
    t = math.radians(tilt)
    return np.maximum(c, ((X - cx) * math.sin(t) - (Y - cy) * math.cos(t)) - r * .3)


# ---------- 1. sleepy: the pink-creature style, in LocalSend teal ----------
def sleepy(out):
    P = {'top': '#a8f5e6', 'bottom': '#0fb5a0', 'rim': '#e6fffb', 'shade': '#06786a', 'tint': '#dff8f3'}
    T = squircle()
    edge = distance_transform_edt(T > .5) / K
    img = ramp(sstep(90, 960, Y), [(0, P['top']), (1, P['bottom'])])
    rim = np.exp(-edge / 10) * (.35 + .65 * sstep(900, 100, X * .4 + Y * .8))
    img = img + (hexc('#ffffff') - img) * (rim * .75)[..., None]
    img = img + (hexc('#ffffff') - img) * (np.exp(-edge / 2.2) * .5)[..., None]
    body = np.minimum(dot(), segments())
    holes = np.minimum(lid_eye(458, 526, 52, -10), lid_eye(570, 522, 52, 0))
    halo = np.exp(-np.clip(body, 0, None) / 5) * (body > 0)
    img = img * (1 - .25 * halo[..., None]) + hexc(P['shade']) * (.25 * halo)[..., None]
    img = over(img, hexc(P['shade']), .18 * gaussian_filter(aa(np.roll(body, int(10 * K), 0)), 10 * K))
    milk = ramp(sstep(220, 820, Y), [(0, '#ffffff'), (.5, '#fbfffe'), (1, P['tint'])])
    shape = np.maximum(body, -holes)
    img = over(img, milk, aa(shape))
    lids = np.exp(-np.clip(-holes, 0, None) / 6) * aa(holes) * sstep(560, 480, Y)
    img = img * (1 - .25 * lids[..., None])
    lip = np.exp(-np.clip(holes, 0, None) / 2.5) * (holes > 0) * aa(body)
    img = img + (hexc('#ffffff') - img) * (lip * .45)[..., None]
    finish(np.clip(img, 0, 1), out, P['rim'], P['bottom'])


# ---------- 2. ceramic: the white pebble with black windows; the dot has sleepy half-moon eyes ----------
def ceramic_ls(out):
    win = np.minimum(dot(), segments())
    eyes = (half_moon(456, 548, 46), half_moon(568, 548, 46))
    ceramic(out, win, eyes)


# ---------- 3. glow: the Ghostty style; the dot is light, the ring a ring of light, grain in the glow ----------
def glow_ls(out):
    tile = ramp(sstep(60, 990, Y), [(0, '#06100f'), (1, '#0a1d1c')])
    s = np.minimum(dot(), segments())
    inside = np.clip(-s, 0, None); o = np.clip(s, 0, None)
    low = sstep(300, 900, Y); left = sstep(640, 220, X)
    sigma = 2 + 4 * low + 10 * low * left
    A = 1 / (1 + np.exp(np.clip(s / sigma * 2.4, -30, 30)))
    depth = np.clip(sstep(420, 900, Y) * .8 + sstep(620, 200, X) * .5 - .1, 0, 1)
    body = ramp(depth, [(0, '#f6fffd'), (.35, '#f6fffd'), (.6, '#c9fbf2'), (.8, '#5fe6d2'), (1, '#1fa8a0')])
    glow_ = np.zeros_like(tile); amt = np.zeros(X.shape, np.float32)
    right = sstep(520, 820, X)
    for col, w in (('#2fe6c8', np.exp(-o / 26) * .85),
                   ('#3f8dff', np.exp(-((o - 40) / 26) ** 2) * right * .7),
                   ('#0f8f86', np.exp(-o / 60) * .55),
                   ('#0a5f66', sstep(760, 1010, Y) * .35)):
        w = np.clip(w, 0, 1); glow_ += hexc(col) * w[..., None]; amt += w
    thin = 4 * np.clip(amt, 0, 1) * (1 - np.clip(amt, 0, 1))
    u = rng.random(X.shape, np.float32)
    glow_ *= (np.clip(amt + (u - .5) * 1.1 * thin, 0, None) / np.maximum(amt, 1e-4))[..., None]
    img = 1 - (1 - tile) * np.exp(-glow_ * 1.5)
    img = over(img, body, dither(A, .8))
    eyes = np.minimum(lid_eye(458, 526, 52, -10), lid_eye(570, 522, 52, 0))
    img = sunken(img, eyes, ('#03201d', '#06302b'), '#1a5a52', '#5fae9f', .3)
    finish(grain(img), out, '#4a6a66', '#16302d')


# ---------- 4. mark: the premium flat sheet; deep teal, crisp white ring and dot ----------
def mark_ls(out):
    img = ramp(sstep(70, 960, Y), [(0, '#14a394'), (1, '#05605a')])
    lift = np.exp(-(((X - 512) / 360) ** 2 + ((Y - 430) / 360) ** 2))
    img = img + (hexc('#ffffff') * (lift * .07)[..., None])
    rim = np.clip(squircle(R=436) - squircle(R=424), 0, 1)
    img = over(img, hexc('#9ff0e3'), .55 * gaussian_filter(rim, 3 * K))
    shape = np.minimum(dot(), segments())
    img = over(img, hexc('#03332f'), .3 * gaussian_filter(aa(np.roll(shape, int(12 * K), 0)), 10 * K))
    img = over(img, ramp(sstep(200, 820, Y), [(0, '#ffffff'), (1, '#e8f6f3')]), aa(shape))
    finish(img, out, '#7fe0d3', '#043c38')


{'sleepy': sleepy, 'ceramic': ceramic_ls, 'glow': glow_ls, 'mark': mark_ls}[ARGS[0]](ARGS[1])
