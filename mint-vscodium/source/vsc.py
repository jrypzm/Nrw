"""VSCodium icons in the styles of the references.
  python3 vsc.py <sleepy|glow|ceramic|mark> <out.png> [size]
"""
import math, os, subprocess, sys, tempfile
ARGS = sys.argv[1:]
sys.argv = [sys.argv[0], 'c-none', '/dev/null'] + ARGS[2:3]
HERE = os.path.dirname(os.path.abspath(__file__))
exec(open(os.path.join(HERE, 'paint.py')).read())


def coral(cx=512, cy=500, size=560):
    """VSCodium's coral, from its official logo, as a signed distance"""
    px = int(size * K * 4)
    with tempfile.TemporaryDirectory() as t:
        f = os.path.join(t, 'm.png')
        subprocess.run(['rsvg-convert', '-w', str(px), '-h', str(px), os.path.join(HERE, 'logo.svg'), '-o', f], check=True)
        m = Image.open(f).getchannel('A')
    canvas = Image.new('L', (N * 4, N * 4), 0)
    canvas.paste(m, (int((cx - size / 2) * K * 4), int((cy - size / 2) * K * 4)))
    a = np.asarray(canvas.resize((N, N), Image.LANCZOS), np.float32) / 255 > .5
    return (distance_transform_edt(~a) - distance_transform_edt(a)) / K


def lid_eye(cx, cy, r, tilt=0.0):
    c = circle(cx, cy, r); t = math.radians(tilt)
    return np.maximum(c, ((X - cx) * math.sin(t) - (Y - cy) * math.cos(t)) - r * .3)


def smin(a, b, k=40):
    h = np.clip(.5 + .5 * (b - a) / k, 0, 1)
    return b * (1 - h) + a * h - k * h * (1 - h)


def sleepy(out):
    P = {'top': '#ffc7a6', 'bottom': '#ff6a3d', 'rim': '#fff0e8', 'shade': '#b8401c', 'tint': '#ffe9dc'}
    T = squircle(); edge = distance_transform_edt(T > .5) / K
    img = ramp(sstep(90, 960, Y), [(0, P['top']), (1, P['bottom'])])
    rim = np.exp(-edge / 10) * (.35 + .65 * sstep(900, 100, X * .4 + Y * .8))
    img = img + (hexc('#ffffff') - img) * (rim * .75)[..., None]
    img = img + (hexc('#ffffff') - img) * (np.exp(-edge / 2.2) * .5)[..., None]
    # a round sleepy body with the coral growing out of its head like antlers
    antlers = coral(512, 392, 460)
    head = circle(512, 676, 222)
    body = smin(antlers, head, 30)
    fangs = np.minimum(polygon_sdf([(446, 880), (484, 880), (465, 924)]) - 8, polygon_sdf([(540, 880), (578, 880), (559, 924)]) - 8)
    body = np.minimum(body, fangs)
    holes = np.minimum(lid_eye(444, 694, 64, -10), lid_eye(584, 690, 64, 0))
    halo = np.exp(-np.clip(body, 0, None) / 5) * (body > 0)
    img = img * (1 - .25 * halo[..., None]) + hexc(P['shade']) * (.25 * halo)[..., None]
    img = over(img, hexc(P['shade']), .18 * gaussian_filter(aa(np.roll(body, int(10 * K), 0)), 10 * K))
    milk = ramp(sstep(200, 880, Y), [(0, '#ffffff'), (.5, '#fffbf9'), (1, P['tint'])])
    img = over(img, milk, aa(np.maximum(body, -holes)))
    lids = np.exp(-np.clip(-holes, 0, None) / 6) * aa(holes) * sstep(720, 640, Y)
    img = img * (1 - .25 * lids[..., None])
    lip = np.exp(-np.clip(holes, 0, None) / 2.5) * (holes > 0) * aa(body)
    img = img + (hexc('#ffffff') - img) * (lip * .45)[..., None]
    finish(np.clip(img, 0, 1), out, P['rim'], P['bottom'])


def glow(out):
    tile = ramp(sstep(60, 990, Y), [(0, '#05080f'), (1, '#0a1324')])
    s = coral(512, 512, 600)
    o = np.clip(s, 0, None)
    low = sstep(300, 900, Y); left = sstep(640, 220, X)
    sigma = 2 + 5 * low + 12 * low * left
    A = 1 / (1 + np.exp(np.clip(s / sigma * 2.4, -30, 30)))
    depth = np.clip(sstep(380, 920, Y) * .85 + sstep(620, 200, X) * .4 - .1, 0, 1)
    body = ramp(depth, [(0, '#f5f9ff'), (.35, '#f5f9ff'), (.6, '#cfe3ff'), (.82, '#62a0ea'), (1, '#2a5fd0')])
    glow_ = np.zeros_like(tile); amt = np.zeros(X.shape, np.float32)
    right = sstep(520, 820, X)
    for col, w in (('#62a0ea', np.exp(-o / 26) * .9), ('#8f6cff', np.exp(-((o - 34) / 24) ** 2) * right * .6),
                   ('#1a5fb4', np.exp(-o / 70) * .6), ('#0d2d66', sstep(760, 1010, Y) * .4)):
        w = np.clip(w, 0, 1); glow_ += hexc(col) * w[..., None]; amt += w
    thin = 4 * np.clip(amt, 0, 1) * (1 - np.clip(amt, 0, 1))
    u = rng.random(X.shape, np.float32)
    glow_ *= (np.clip(amt + (u - .5) * 1.1 * thin, 0, None) / np.maximum(amt, 1e-4))[..., None]
    img = 1 - (1 - tile) * np.exp(-glow_ * 1.5)
    img = over(img, body, dither(A, .8))
    finish(grain(img), out, '#4a5a72', '#141c2c')


def ceramic_v(out):
    ceramic(out, coral(512, 512, 560))


def mark(out):
    img = ramp(sstep(70, 960, Y), [(0, '#4f8fe6'), (1, '#173f8f')])
    lift = np.exp(-(((X - 512) / 360) ** 2 + ((Y - 430) / 360) ** 2))
    img = img + (hexc('#ffffff') * (lift * .08)[..., None])
    rim = np.clip(squircle(R=436) - squircle(R=424), 0, 1)
    img = over(img, hexc('#b9d4ff'), .55 * gaussian_filter(rim, 3 * K))
    shape = coral(512, 512, 560)
    img = over(img, hexc('#0b2350'), .3 * gaussian_filter(aa(np.roll(shape, int(12 * K), 0)), 10 * K))
    img = over(img, ramp(sstep(200, 820, Y), [(0, '#ffffff'), (1, '#e7efff')]), aa(shape))
    finish(img, out, '#a9c8ff', '#0e2a60')


{'sleepy': sleepy, 'glow': glow, 'ceramic': ceramic_v, 'mark': mark}[ARGS[0]](ARGS[1])
