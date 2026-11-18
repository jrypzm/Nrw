"""Paints the glowing Ghostty creature pixel by pixel, the way the reference is lit.

Every pixel's colour comes from where it sits relative to the creature's edge:
crisp along the top, melting into violet low on the left, splitting into a
magenta and blue prism off the right, with grain dithered into every falloff.

  python3 paint.py <design> <palette> <out.png> [size]
"""
import math
import re
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy.ndimage import distance_transform_edt, gaussian_filter

N = int(sys.argv[4]) if len(sys.argv) > 4 else 1024
K = N / 1024.0
rng = np.random.default_rng(7)
Y, X = np.mgrid[0:N, 0:N].astype(np.float32) / K   # coordinates on the 1024 design grid


def hexc(h):
    return np.array([int(h[i:i + 2], 16) / 255 for i in (1, 3, 5)], np.float32)


def sstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def ramp(t, stops):
    """colour ramp: stops = [(pos, '#hex'), ...]"""
    t = np.clip(t, 0, 1)
    out = np.zeros(t.shape + (3,), np.float32)
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        m = (t >= p0) & (t <= p1)
        u = ((t - p0) / max(p1 - p0, 1e-6))[..., None]
        out[m] = (hexc(c0) * (1 - u) + hexc(c1) * u)[m]
    return out


# ---------- shapes ----------
def path_points(d, rot=0.0, cx=512, cy=600):
    toks = re.findall(r'[MLCZ]|-?\d+\.?\d*', d)
    pts, cur, i = [], (0, 0), 0
    while i < len(toks):
        t = toks[i]
        if t in 'ML':
            cur = (float(toks[i + 1]), float(toks[i + 2])); pts.append(cur); i += 3
        elif t == 'C':
            p1 = (float(toks[i + 1]), float(toks[i + 2])); p2 = (float(toks[i + 3]), float(toks[i + 4])); p3 = (float(toks[i + 5]), float(toks[i + 6]))
            for k in range(1, 41):
                s = k / 40
                pts.append(tuple((1 - s) ** 3 * a + 3 * (1 - s) ** 2 * s * b + 3 * (1 - s) * s * s * c + s ** 3 * e for a, b, c, e in zip(cur, p1, p2, p3)))
            cur = p3; i += 7
        else:
            i += 1
    if rot:
        r = math.radians(rot); c, s = math.cos(r), math.sin(r)
        pts = [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for x, y in pts]
    return pts


def mask_of(pts, ss=4):
    im = Image.new('L', (N * ss, N * ss), 0)
    ImageDraw.Draw(im).polygon([(x * K * ss, y * K * ss) for x, y in pts], fill=255)
    return np.asarray(im.resize((N, N), Image.LANCZOS), np.float32) / 255


def squircle_mask(R=440, n=4.6, cx=512, cy=512):
    u = np.abs(X - cx) / R; v = np.abs(Y - cy) / R
    f = u ** n + v ** n
    edge = 1.2 / (R * K) * n   # about a pixel of antialiasing
    return np.clip((1 - f) / edge + .5, 0, 1)


HAUNT = 'M 198 363 L 337 301 C 362 362 420 380 472 376 C 545 370 592 318 597 232 L 707 190 C 762 380 822 560 848 737 C 862 830 866 920 866 1040 L 250 1040 C 268 900 292 780 296 660 C 298 570 276 505 236 452 C 224 420 212 392 198 363 Z'
GHOST = 'M 236 1040 L 236 520 C 236 320 372 214 532 214 C 700 214 820 330 826 520 C 832 700 850 880 870 1040 Z'

PAL = {
    #        tile top   tile bottom white      blush      pink       magenta    prism      violet     deep
    'violet': ['#0a0710', '#170922', '#fff8fe', '#fbe1fb', '#f5a3f3', '#e64fe4', '#5f6fe8', '#a873e6', '#5b22b8'],
    'cyan':   ['#05080e', '#081424', '#f8fdff', '#dff2ff', '#a2d6ff', '#4f8dff', '#36e2ff', '#5a8de8', '#1d3fb8'],
}


def paint(design, pal, out):
    t0, t1, white, blush, pink, mag, prism, violet, deep = PAL[pal]
    shape = HAUNT if design in ('haunt', 'pure', 'hprompt') else GHOST
    tilt = -6 if design in ('ghost', 'prompt') else 0
    M = mask_of(path_points(shape, tilt))
    inside = distance_transform_edt(M > .5) / K
    outside = distance_transform_edt(M <= .5) / K
    s = outside - inside                                  # signed distance, + outside

    # how soft the edge is here: crisp along the top, melting low and to the left
    low = sstep(330, 900, Y)
    left = sstep(640, 200, X)
    sigma = 2.2 + 16 * low + 70 * low * left ** 1.3
    A = 1 / (1 + np.exp(np.clip(s / sigma * 2.4, -30, 30)))   # body opacity

    # colour inside: white, melting through blush and pink to violet toward the lower left
    depth = np.clip(sstep(420, 1000, Y) * .8 + sstep(620, 160, X) * .65 - .12, 0, 1)
    body = ramp(depth, [(0, white), (.32, white), (.5, blush), (.7, pink), (1, violet)])
    # magenta pooling low on the right
    g = np.exp(-(((X - 860) / 190) ** 2 + ((Y - 990) / 230) ** 2))[..., None]
    body = body * (1 - .55 * g) + hexc(mag) * .55 * g
    # a thin pink band just inside the top edge
    topness = sstep(560, 330, Y)[..., None]
    band = (np.exp(-np.clip(inside, 0, None) / 13) * (M > .5))[..., None] * topness
    body = body * (1 - .75 * band) + hexc(pink) * .75 * band

    # ---------- the light thrown into the tile ----------
    tile = ramp(sstep(60, 990, Y), [(0, t0), (1, t1)])
    o = np.clip(outside, 0, None)
    right = sstep(560, 820, X)
    lower = sstep(400, 860, Y)
    glow = np.zeros_like(tile); amt = np.zeros(X.shape, np.float32)

    def add(col, w):
        nonlocal glow, amt
        w = np.clip(w, 0, 1)
        glow = glow + hexc(col) * w[..., None]; amt = amt + w

    add(mag, np.exp(-o / 24) * right * lower * .95)                            # magenta right next to the edge
    add(prism, np.exp(-((o - 44) / 30) ** 2) * right * lower * .9)            # then a blue prism band
    add(prism, np.exp(-o / 5) * sstep(520, 300, Y) * .5)                       # a faint blue halo along the top
    add(violet, np.exp(-o / 140) * sstep(700, 200, X) * sstep(420, 900, Y) * .95)
    add(violet, np.exp(-o / 50) * sstep(760, 260, X) * sstep(470, 860, Y) * .9)   # straight out of the body, no gap   # violet pouring out low left
    add(deep, sstep(700, 1010, Y) * .8 * (1 - right * .4))                         # violet pooling at the bottom
    add(mag, np.exp(-(((X - 900) / 160) ** 2 + ((Y - 960) / 200) ** 2)) * .5)       # magenta glow low right

    # grain: the light breaks into noise as it thins (dithered, not just sprinkled)
    u = rng.random(X.shape, np.float32)
    thin = 4 * np.clip(amt, 0, 1) * (1 - np.clip(amt, 0, 1))
    amt_d = np.clip(amt + (u - .5) * 1.1 * thin, 0, None)
    scale = (amt_d / np.maximum(amt, 1e-4))[..., None]
    glow = glow * scale
    lit = 1 - np.exp(-glow * 1.6)                                               # light adds up softly
    img = 1 - (1 - tile) * (1 - lit)                                            # screen onto the tile

    # the body over it, its soft edges dithered too
    u2 = rng.random(X.shape, np.float32)
    A_d = np.clip(A + (u2 - .5) * .9 * 4 * A * (1 - A) * sstep(2.5, 12, sigma), 0, 1)
    img = img * (1 - A_d[..., None]) + body * A_d[..., None]

    # ---------- the face ----------
    def ellipse_sdf(cx, cy, rx, ry, rot):
        r = math.radians(rot); c, s_ = math.cos(r), math.sin(r)
        x = (X - cx) * c + (Y - cy) * s_; y = -(X - cx) * s_ + (Y - cy) * c
        q = np.sqrt((x / rx) ** 2 + (y / ry) ** 2)
        return (q - 1) * min(rx, ry), x / rx, y / ry

    def shade_mark(sdf, nx, ny):
        nonlocal img
        a = np.clip(.5 - sdf * K, 0, 1)                                         # antialiased
        sh = gaussian_filter(np.clip(.5 - (np.roll(np.roll(sdf, int(16 * K), 0), int(10 * K), 1)) * K, 0, 1), 16 * K)
        img = img * (1 - .38 * sh[..., None]) + hexc('#5f7fa8' if pal == 'cyan' else '#8d5f99') * .38 * sh[..., None]
        rim = np.clip(1 + sdf / 7, 0, 1) * a                                     # the lip of the hollow
        lightdir = np.clip(-(nx * .6 + ny * .8), 0, 1)
        core = ramp(np.clip(np.sqrt(nx ** 2 + ny ** 2), 0, 1), [(0, '#040a18'), (.75, '#081430'), (1, '#1d3a66')] if pal == 'cyan' else [(0, '#120310'), (.75, '#1e061b'), (1, '#45163f')])
        core = core + (rim * lightdir * .28)[..., None]
        img = img * (1 - a[..., None]) + core * a[..., None]

    def rrect_sdf(x0, y0, x1, y1, r):
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2; hx, hy = (x1 - x0) / 2 - r, (y1 - y0) / 2 - r
        qx = np.abs(X - cx) - hx; qy = np.abs(Y - cy) - hy
        d = np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2) + np.minimum(np.maximum(qx, qy), 0) - r
        return d, (X - cx) / (hx + r), (Y - cy) / (hy + r)

    def stroke_sdf(pts, w):
        d = np.full(X.shape, 1e9, np.float32)
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            px, py = X - ax, Y - ay; vx, vy = bx - ax, by - ay
            t = np.clip((px * vx + py * vy) / (vx * vx + vy * vy), 0, 1)
            d = np.minimum(d, np.sqrt((px - t * vx) ** 2 + (py - t * vy) ** 2))
        return d - w / 2

    if design in ('haunt', 'pure', 'cyan'):
        for (cx, cy, rx, ry) in ((470, 552, 73, 121), (664, 496, 62, 108)):
            sdf, nx, ny = ellipse_sdf(cx, cy, rx, ry, -12)
            shade_mark(sdf, nx, ny)
        if design != 'pure':
            sdf, nx, ny = rrect_sdf(516, 722, 646, 766, 22)
            shade_mark(sdf, nx * .4, ny * .4)
    elif design == 'ghost':
        for (cx, cy, rx, ry) in ((452, 520, 66, 112), (642, 500, 60, 104)):
            sdf, nx, ny = ellipse_sdf(cx, cy, rx, ry, -14)
            shade_mark(sdf, nx, ny)
        sdf, nx, ny = rrect_sdf(486, 692, 616, 736, 22)
        shade_mark(sdf, nx * .4, ny * .4)
    elif design == 'hprompt':
        # the >_ prompt on the creature, leaning with it like the eyes did
        r = math.radians(-10)
        rot = lambda x, y: (560 + (x - 560) * math.cos(r) - (y - 560) * math.sin(r), 560 + (x - 560) * math.sin(r) + (y - 560) * math.cos(r))
        for pts in ([(420, 470), (520, 552), (420, 634)], [(590, 634), (730, 634)]):
            sdf = stroke_sdf([rot(*p) for p in pts], 62)
            shade_mark(sdf, np.zeros_like(X), np.zeros_like(X) - .3)
    elif design == 'prompt':
        r = math.radians(-6)
        rot = lambda x, y: (512 + (x - 512) * math.cos(r) - (y - 600) * math.sin(r), 600 + (x - 512) * math.sin(r) + (y - 600) * math.cos(r))
        for pts in ([(400, 482), (492, 560), (400, 638)], [(560, 638), (690, 638)]):
            sdf = stroke_sdf([rot(*p) for p in pts], 58)
            shade_mark(sdf, np.zeros_like(X), np.zeros_like(X) - .3)

    # fine film grain riding on the light only
    n = rng.normal(0, 1, X.shape).astype(np.float32)
    lum = img.mean(-1)
    img = np.clip(img + (n * .045 * np.clip(lum * 1.4, 0, 1))[..., None], 0, 1)

    # ---------- the tile ----------
    T = squircle_mask()
    Tin = squircle_mask(R=432)
    lip = np.clip(T - Tin, 0, 1)
    lipcol = ramp(sstep(80, 500, Y), [(0, '#5d5966'), (1, '#2a2730')])
    img = img * (1 - .8 * lip[..., None]) + lipcol * .8 * lip[..., None]

    shadow = gaussian_filter(np.roll(squircle_mask(R=420), int(40 * K), 0), 24 * K) * .7
    alpha = np.clip(T + shadow * (1 - T), 0, 1)
    rgb = np.where(T[..., None] > 0, img, 0)
    out_rgb = (rgb * T[..., None]) / np.maximum(alpha, 1e-4)[..., None]
    arr = np.dstack([np.clip(out_rgb, 0, 1), alpha])
    Image.fromarray((arr * 255 + .5).astype(np.uint8), 'RGBA').save(out)


if __name__ == '__main__':
    paint(sys.argv[1], sys.argv[2], sys.argv[3])
