"""mpv icons, painted pixel by pixel in the styles of the references.

  python3 paint.py <glow|spray|graphite|mark|aura> <out.png> [size]
"""
import math
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy.ndimage import distance_transform_edt, gaussian_filter

N = int(sys.argv[3]) if len(sys.argv) > 3 else 1024
K = N / 1024.0
rng = np.random.default_rng(5)
Y, X = np.mgrid[0:N, 0:N].astype(np.float32) / K


def hexc(h):
    return np.array([int(h[i:i + 2], 16) / 255 for i in (1, 3, 5)], np.float32)


def sstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def ramp(t, stops):
    t = np.clip(t, 0, 1)
    out = np.zeros(t.shape + (3,), np.float32)
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        m = (t >= p0) & (t <= p1)
        u = ((t - p0) / max(p1 - p0, 1e-6))[..., None]
        out[m] = (hexc(c0) * (1 - u) + hexc(c1) * u)[m]
    return out


def over(img, col, a):
    a = np.clip(a, 0, 1)[..., None]
    return img * (1 - a) + (col if col.ndim == 3 else col[None, None, :]) * a


# ---------- shapes as signed distances (negative inside) ----------
def circle(cx, cy, r):
    return np.sqrt((X - cx) ** 2 + (Y - cy) ** 2) - r


def polygon_sdf(pts, round_=0):
    """signed distance to a polygon, rounded by `round_` (shrink first, then grow)"""
    im = Image.new('L', (N * 4, N * 4), 0)
    ImageDraw.Draw(im).polygon([(x * K * 4, y * K * 4) for x, y in pts], fill=255)
    m = np.asarray(im.resize((N, N), Image.LANCZOS), np.float32) / 255 > .5
    d = (distance_transform_edt(~m) - distance_transform_edt(m)) / K
    return d - 0 if not round_ else d


def play(cx=528, cy=512, r=118, rnd=26):
    """mpv's play triangle, pointing right, corners rounded"""
    rr = r - rnd
    pts = [(cx + rr * math.cos(math.radians(a)), cy + rr * math.sin(math.radians(a))) for a in (0, 120, 240)]
    return polygon_sdf(pts) - rnd


def aa(sdf, w=1.0):
    return np.clip(.5 - sdf * K / w, 0, 1)


def squircle(R=440, n=4.6):
    u = np.abs(X - 512) / R; v = np.abs(Y - 512) / R
    return np.clip((1 - (u ** n + v ** n)) / (1.2 / (R * K) * n) + .5, 0, 1)


def finish(img, out, lip_top='#6a6572', lip_bottom='#2a2730'):
    T = squircle(); Tin = squircle(R=432)
    lip = np.clip(T - Tin, 0, 1)
    img = over(img, ramp(sstep(80, 520, Y), [(0, lip_top), (1, lip_bottom)]), .8 * lip)
    shadow = gaussian_filter(np.roll(squircle(R=420), int(40 * K), 0), 24 * K) * .7
    alpha = np.clip(T + shadow * (1 - T), 0, 1)
    rgb = (np.clip(img, 0, 1) * T[..., None]) / np.maximum(alpha, 1e-4)[..., None]
    Image.fromarray((np.dstack([np.clip(rgb, 0, 1), alpha]) * 255 + .5).astype(np.uint8), 'RGBA').save(out)


def grain(img, amount=.045, only_light=True):
    n = rng.normal(0, 1, X.shape).astype(np.float32)
    w = np.clip(img.mean(-1) * 1.4, 0, 1) if only_light else 1
    return np.clip(img + (n * amount * w)[..., None], 0, 1)


def dither(a, strength=1.0):
    u = rng.random(a.shape, np.float32)
    return np.clip(a + (u - .5) * strength * 4 * a * (1 - a), 0, 1)


def sunken(img, sdf, core, rim_col, shadow_col, shadow_amt=.38):
    """a glossy hollow: soft shadow beside it, a lit lip on its upper left"""
    a = aa(sdf)
    sh = gaussian_filter(aa(np.roll(np.roll(sdf, int(16 * K), 0), int(10 * K), 1)), 16 * K)
    img = over(img, hexc(shadow_col), shadow_amt * sh)
    depth = np.clip(-sdf / 60, 0, 1)
    col = ramp(depth, [(0, rim_col), (.35, core[1]), (1, core[0])])
    lip = np.clip(1 + sdf / 8, 0, 1) * sstep(560, 380, Y + X * .3)
    col = col + (lip * .25)[..., None]
    return over(img, col, a)


def puff(sdf, height=40):
    """light on a soft raised shape: brighter top left, shaded bottom right"""
    h = np.clip(-sdf / height, 0, 1)
    h = np.sqrt(h)
    gy, gx = np.gradient(gaussian_filter(h, 6 * K))
    nz = 1.0
    nx, ny = -gx * 60 * K, -gy * 60 * K
    l = np.array([-.5, -.65, .57]); l = l / np.linalg.norm(l)
    norm = np.sqrt(nx ** 2 + ny ** 2 + nz ** 2)
    diff = np.clip((nx * l[0] + ny * l[1] + nz * l[2]) / norm, 0, 1)
    spec = np.clip((nx * 0 + ny * -.3 + nz * .95) / norm, 0, 1) ** 40
    return diff, spec


DISC = (512, 512, 300)


def glow(out):
    """the Ghostty reference: a disc made of light, crisp on top, melting into violet below, grain in the glow"""
    tile = ramp(sstep(60, 990, Y), [(0, '#0a0710'), (1, '#170922')])
    s = circle(*DISC)
    inside = np.clip(-s, 0, None); o = np.clip(s, 0, None)
    low = sstep(300, 860, Y); left = sstep(640, 220, X)
    sigma = 2.2 + 16 * low + 60 * low * left ** 1.3
    A = 1 / (1 + np.exp(np.clip(s / sigma * 2.4, -30, 30)))
    depth = np.clip(sstep(420, 880, Y) * .8 + sstep(620, 200, X) * .6 - .12, 0, 1)
    body = ramp(depth, [(0, '#fff8fe'), (.32, '#fff8fe'), (.5, '#fbe1fb'), (.7, '#f5a3f3'), (1, '#a873e6')])
    g = np.exp(-(((X - 700) / 170) ** 2 + ((Y - 760) / 170) ** 2))[..., None]
    body = body * (1 - .5 * g) + hexc('#e64fe4') * .5 * g
    band = (np.exp(-inside / 13) * (s < 0))[..., None] * sstep(560, 300, Y)[..., None]
    body = body * (1 - .7 * band) + hexc('#f5a3f3') * .7 * band
    right = sstep(560, 800, X); lower = sstep(380, 820, Y)
    glow_ = np.zeros_like(tile); amt = np.zeros(X.shape, np.float32)
    for col, w in (('#e64fe4', np.exp(-o / 24) * right * lower * .95),
                   ('#5f6fe8', np.exp(-((o - 44) / 30) ** 2) * right * lower * .9),
                   ('#5f6fe8', np.exp(-o / 5) * sstep(500, 260, Y) * .5),
                   ('#a873e6', np.exp(-o / 120) * sstep(700, 200, X) * sstep(380, 900, Y) * .95),
                   ('#a873e6', np.exp(-o / 50) * sstep(760, 260, X) * sstep(440, 860, Y) * .9),
                   ('#5b22b8', sstep(720, 1010, Y) * .8 * (1 - right * .4)),
                   ('#e64fe4', np.exp(-(((X - 880) / 160) ** 2 + ((Y - 940) / 200) ** 2)) * .5)):
        w = np.clip(w, 0, 1); glow_ += hexc(col) * w[..., None]; amt += w
    thin = 4 * np.clip(amt, 0, 1) * (1 - np.clip(amt, 0, 1))
    u = rng.random(X.shape, np.float32)
    glow_ *= (np.clip(amt + (u - .5) * 1.1 * thin, 0, None) / np.maximum(amt, 1e-4))[..., None]
    img = 1 - (1 - tile) * np.exp(-glow_ * 1.6)
    img = over(img, body, dither(A, .9) * sstep(2.5, 12, sigma) + A * (1 - sstep(2.5, 12, sigma)))
    img = sunken(img, play(), ('#120310', '#1e061b'), '#45163f', '#8d5f99')
    finish(grain(img), out)


def spray(out):
    """the Kaonashi reference: a grainy glowing tile, a disc of sprayed black ink, a bright white play shape"""
    blooms = np.zeros(X.shape + (3,), np.float32)
    base = hexc('#d9b8ff')
    img = np.ones(X.shape + (3,), np.float32) * base
    for (cx, cy, r, col, a) in ((860, 160, 330, '#fdf6ff', .9), (160, 880, 300, '#9b5cff', .9), (880, 880, 300, '#f3dcff', .8), (180, 170, 260, '#c39aff', .8), (720, 760, 220, '#ff9fe0', .45)):
        w = np.exp(-(((X - cx) / r) ** 2 + ((Y - cy) / r) ** 2) * 1.6)
        img = img * (1 - a * w[..., None]) + hexc(col) * (a * w)[..., None]
    img = grain(img, .05, False)
    s = circle(*DISC)
    # ink: solid inside, breaking into spray toward the lower right
    dirf = sstep(560, 1040, X * .6 + Y * .6)
    soft = 2 + 46 * dirf ** 1.4
    A = 1 / (1 + np.exp(np.clip(s / soft * 2.2, -30, 30)))
    fade = 1 - .6 * sstep(640, 1060, X * .55 + Y * .65)
    A = A * fade
    Ad = dither(A, 1.4)
    ink = np.ones_like(img) * hexc('#141016')
    specks = (rng.random(X.shape) > .985) & (Ad > .6)
    img = over(img, ink, Ad)
    img = over(img, hexc('#bdb6c4'), specks * .8)
    # the white play shape, softly shaded like the reference's mask
    t = play(536, 500, 132, 30)
    diff, spec = puff(t, 30)
    white = ramp(diff, [(0, '#c9c5cf'), (.6, '#f1eff3'), (1, '#ffffff')]) + spec[..., None] * .3
    img = over(img, hexc('#000000'), .25 * gaussian_filter(aa(np.roll(t, int(14 * K), 0)), 12 * K))
    img = over(img, white, aa(t))
    finish(img, out, '#f4eefa', '#b9a6d0')


def graphite(out):
    """the mirror-app reference: black glass, a sunken well, a frosted milky play shape catching warm and cool light"""
    img = ramp(sstep(60, 990, Y), [(0, '#3a3a40'), (.3, '#18181b'), (1, '#060607')])
    well = circle(512, 512, 286)
    # the well: darker, lit lip on its lower right, shadowed upper left
    wa = aa(well)
    img = over(img, ramp(sstep(220, 800, Y), [(0, '#050506'), (1, '#121215')]), wa)
    lip = np.exp(-np.abs(well) / 3) * sstep(300, 720, X * .5 + Y * .5)
    img = img + (lip * .35)[..., None]
    inner_sh = np.exp(-np.clip(-well, 0, None) / 30) * wa * sstep(720, 300, X * .5 + Y * .5)
    img = img * (1 - .5 * inner_sh[..., None])
    t = play(530, 512, 150, 34)
    # warm and cool light on its edges
    o = np.clip(t, 0, None)
    warm = np.exp(-((o - 6) / 9) ** 2) * sstep(480, 640, X)
    cool = np.exp(-((o - 6) / 9) ** 2) * sstep(560, 400, X)
    img = img + (hexc('#ff8a3d') * (warm * .8)[..., None]) + (hexc('#5aa8ff') * (cool * .8)[..., None])
    img = over(img, hexc('#000000'), .55 * gaussian_filter(aa(np.roll(t, int(26 * K), 0)), 22 * K))
    diff, spec = puff(t, 46)
    milk = ramp(diff * .9 + .1 * sstep(700, 300, Y), [(0, '#7f838e'), (.55, '#c3c6cf'), (1, '#f6f7fa')]) + spec[..., None] * .5
    img = over(img, milk, aa(t))
    finish(grain(img, .03), out, '#7a7a82', '#2a2a2e')


def mark(out):
    """the premium flat sheet: mpv's purple, a white ring, a white disc with the play shape cut out of it"""
    img = ramp(sstep(70, 960, Y), [(0, '#7b4687'), (1, '#4c2354')])
    lift = np.exp(-(((X - 512) / 360) ** 2 + ((Y - 430) / 360) ** 2))
    img = img + (hexc('#ffffff') * (lift * .07)[..., None])
    T = squircle(); rim = np.clip(squircle(R=436) - squircle(R=424), 0, 1)
    img = over(img, hexc('#c9a3d6'), .55 * gaussian_filter(rim, 3 * K))
    ring = np.abs(circle(512, 512, 296)) - 26
    disc = circle(512, 512, 214)
    t = play(530, 512, 120, 22)
    shape = np.minimum(ring, np.maximum(disc, -t))
    img = over(img, hexc('#2a0f30'), .35 * gaussian_filter(aa(np.roll(shape, int(12 * K), 0)), 10 * K))
    img = over(img, ramp(sstep(200, 820, Y), [(0, '#ffffff'), (1, '#ece6ef')]), aa(shape))
    finish(img, out, '#a679b3', '#3b1a42')


def aura(out):
    """the gradient pack: a living mesh of mpv purple, magenta and amber; a soft glossy white disc"""
    img = np.ones(X.shape + (3,), np.float32) * hexc('#2a0b3a')
    for (cx, cy, r, col, a) in ((240, 230, 330, '#ff5fa8', .95), (820, 260, 320, '#7a3cff', .95), (760, 860, 360, '#ff9a3c', .85), (220, 860, 300, '#5b1cc8', .9), (512, 520, 230, '#ff7fd0', .5)):
        w = np.exp(-(((X - cx) / r) ** 2 + ((Y - cy) / r) ** 2) * 1.4)
        img = img * (1 - a * w[..., None]) + hexc(col) * (a * w)[..., None]
    img = grain(img, .05, False)
    d = circle(512, 512, 232)
    img = over(img, hexc('#2a0b3a'), .45 * gaussian_filter(aa(np.roll(d, int(30 * K), 0)), 30 * K))
    diff, spec = puff(d, 120)
    disc = ramp(diff, [(0, '#e6dbee'), (.6, '#fbf7fd'), (1, '#ffffff')]) + spec[..., None] * .25
    img = over(img, disc, aa(d))
    t = play(530, 512, 112, 22)
    tcol = ramp(sstep(400, 640, Y), [(0, '#a24bd6'), (1, '#6a1fa8')])
    img = over(img, hexc('#3a0a50'), .25 * gaussian_filter(aa(np.roll(t, int(8 * K), 0)), 8 * K))
    img = over(img, tcol, aa(t))
    finish(img, out, '#d7a6e8', '#4a1a5c')


if __name__ == '__main__' and not sys.argv[1].startswith('c-'):
    {'glow': glow, 'spray': spray, 'graphite': graphite, 'mark': mark, 'aura': aura}[sys.argv[1]](sys.argv[2])


# ---------- ceramic: the white pebble tile with a black window ----------
def tile_height(bevel=46):
    T = squircle()
    d = distance_transform_edt(T > .5) / K
    u = np.clip(d / bevel, 0, 1)
    return np.sqrt(1 - (1 - u) ** 2) * bevel * .9, T


def light(h, L=(.55, -.62, .56), shin=34, blur=2):
    hb = gaussian_filter(h, blur * K)
    gy, gx = np.gradient(hb * K)
    nx, ny, nz = -gx, -gy, np.ones_like(gx)
    n = np.sqrt(nx ** 2 + ny ** 2 + nz ** 2); nx, ny, nz = nx / n, ny / n, nz / n
    l = np.array(L, np.float32); l /= np.linalg.norm(l)
    diff = np.clip(nx * l[0] + ny * l[1] + nz * l[2], 0, 1)
    hv = l + np.array([0, 0, 1], np.float32); hv /= np.linalg.norm(hv)
    spec = np.clip(nx * hv[0] + ny * hv[1] + nz * hv[2], 0, 1) ** shin
    return diff, spec


def half_moon(cx, cy, r):
    """an eye like the reference: round on top, flat underneath"""
    c = circle(cx, cy, r)
    # the underside curves up a little, like a smile, as in the reference
    return np.maximum(c, -circle(cx, cy + r * 2.6, r * 2.75))


def ceramic(out, window, eyes=(), dark=False, inner=None):
    h, T = tile_height()
    diff, spec = light(h, blur=7)
    base = ramp(sstep(1300, 300, X * .7 + (1024 - Y) * .7 * -1 + 1024), [(0, '#ffffff'), (1, '#ffffff')])
    # albedo: bright towards the top right, greyer towards the bottom left
    t = np.clip(((1024 - X) * .5 + Y * .5 - 250) / 650, 0, 1)
    alb = ramp(t, [(0, '#2a2a2e'), (1, '#0a0a0c')]) if dark else ramp(t, [(0, '#ffffff'), (.6, '#ececee'), (1, '#c9c9cd')])
    img = alb * (.62 + .45 * diff[..., None]) + (spec * (.5 if dark else .35))[..., None]
    # a broad soft sheen on the face, top right
    sheen = np.exp(-(((X - 700) / 260) ** 2 + ((Y - 300) / 200) ** 2))
    img = img + (sheen * (.1 if dark else .09))[..., None]
    # the thin bright lip where the bevel meets the outside
    edge = distance_transform_edt(T > .5) / K
    lip = np.exp(-edge / 2.5) * sstep(900, 200, Y + (1024 - X) * .2)
    img = img + (lip * (.35 if dark else .25))[..., None]
    # the window: cut through, its edge a crisp ridge of light
    w = window
    ridge = np.exp(-np.abs(w) / 1.8) * (.55 + .45 * sstep(700, 300, Y))
    hole = hexc('#f4f4f6') if dark else hexc('#000000')
    img = over(img, hole if inner is None else inner, aa(w))
    img = img + (ridge * (.12 if dark else .5))[..., None] * (1 - aa(w))[..., None]
    for e in eyes:
        ea = aa(e)
        ed = np.clip(-e / 26, 0, 1)
        ecol = ramp(1 - ed + sstep(-30, 30, Y - (np.where(ea > 0, Y, 0).max() if False else 0)), [(0, '#ffffff'), (1, '#ffffff')])
        # glossy: grey along the top curve, bright below
        top = np.exp(-np.clip(-e, 0, None) / 10)
        ecol = ramp(top, [(0, '#ffffff'), (.6, '#d9d9dd'), (1, '#9c9ca2')]) if not dark else ramp(top, [(0, '#050506'), (.6, '#1a1a1e'), (1, '#55555c')])
        img = over(img, ecol, ea)
    img = np.clip(img, 0, 1)
    shadow = gaussian_filter(np.roll(squircle(R=420), int(46 * K), 0), 30 * K) * .75
    alpha = np.clip(T + shadow * (1 - T), 0, 1)
    rgb = (img * T[..., None]) / np.maximum(alpha, 1e-4)[..., None]
    Image.fromarray((np.dstack([np.clip(rgb, 0, 1), alpha]) * 255 + .5).astype(np.uint8), 'RGBA').save(out)


def big_play(cx=548, cy=512, r=300, rnd=70):
    return play(cx, cy, r, rnd)


def face_eyes():
    return (half_moon(438, 590, 52), half_moon(574, 590, 52))


def c_face(out):      # the play shape as the character, two half-moon eyes
    ceramic(out, big_play(), face_eyes())


def c_disc(out):      # a round black window, a glossy white play shape floating in it
    win = circle(512, 512, 262)
    ceramic(out, np.maximum(win, -play(530, 512, 128, 30)))


def c_ring(out):      # mpv's ring: a black ring and a black play shape, cut through the ceramic
    ring = np.abs(circle(512, 512, 270)) - 30
    ceramic(out, np.minimum(ring, play(532, 512, 140, 32)))


def c_night(out):     # the face in black ceramic, the window lit white
    ceramic(out, big_play(), face_eyes(), dark=True)


for name, fn in (('c-face', c_face), ('c-disc', c_disc), ('c-ring', c_ring), ('c-night', c_night)):
    if len(sys.argv) > 1 and sys.argv[1] == name:
        fn(sys.argv[2]); sys.exit(0)
