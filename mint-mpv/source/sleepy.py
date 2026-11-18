"""mpv in the style of the pink sleepy-creature reference.
  python3 sleepy.py <disc|playbody|pink> <out.png> [size]
"""
import sys
ARGS = sys.argv[1:]
sys.argv = [sys.argv[0], 'none', '/dev/null'] + ARGS[2:3]
import math
import numpy as np
exec(open(__file__.replace('sleepy.py', 'paint.py')).read().split("if __name__ == '__main__'")[0])


PALS = {
    'purple': {'top': '#d9b6ff', 'bottom': '#7d3fe0', 'rim': '#f3e6ff', 'shade': '#5a22b0', 'tint': '#efe2ff'},
    'pink':   {'top': '#fba6ee', 'bottom': '#ff55dd', 'rim': '#ffe6fb', 'shade': '#c22aa8', 'tint': '#fde3f7'},
}


def lid_eye(cx, cy, r, tilt=0.0):
    """a sleepy eye: the lower half of a circle under a straight lid, the lid tilted for attitude"""
    c = circle(cx, cy, r)
    t = math.radians(tilt)
    lid = ((X - cx) * math.sin(t) - (Y - cy) * math.cos(t)) - r * .3   # positive above the lid: cut away
    return np.maximum(c, lid)


def spikes(pts):
    return polygon_sdf(pts) - 10


def creature_disc():
    head = circle(512, 560, 262)
    tuft = np.minimum(np.minimum(spikes([(340, 420), (384, 256), (452, 360)]), spikes([(420, 340), (534, 216), (580, 360)])), spikes([(552, 340), (628, 268), (648, 380)]))
    fangs = np.minimum(spikes([(428, 800), (468, 800), (446, 848)]), spikes([(560, 800), (600, 800), (580, 848)]))
    body = np.minimum(np.minimum(head, tuft), fangs)
    eyes = [lid_eye(428, 590, 80, -10), lid_eye(608, 586, 80, 0)]
    mouth = play(524, 726, 38, 11)
    return body, eyes + [mouth]


def creature_play():
    body = play(560, 548, 330, 80)
    tuft = np.minimum(spikes([(360, 320), (392, 206), (446, 292)]), spikes([(380, 290), (478, 190), (520, 330)]))
    body = np.minimum(body, tuft)
    eyes = [lid_eye(420, 590, 66, -10), lid_eye(570, 584, 66, 0)]
    return body, eyes


def sleepy(out, which, pal):
    P = PALS[pal]
    T = squircle()
    edge = distance_transform_edt(T > .5) / K
    img = ramp(sstep(90, 960, Y), [(0, P['top']), (1, P['bottom'])])
    # gloss: light gathered along the inside of the rim, brightest top left
    rim = np.exp(-edge / 10) * (.35 + .65 * sstep(900, 100, X * .4 + Y * .8))
    img = img + (hexc('#ffffff') - img) * (rim * .75)[..., None]
    img = img + (hexc('#ffffff') - img) * (np.exp(-edge / 2.2) * .5)[..., None]
    body, holes = creature_disc() if which != 'playbody' else creature_play()
    # a soft darker halo around the creature, as if it sits a hair above the tile
    halo = np.exp(-np.clip(body, 0, None) / 5) * (body > 0)
    img = img * (1 - .25 * halo[..., None]) + hexc(P['shade']) * (.25 * halo)[..., None]
    sh = gaussian_filter(aa(np.roll(body, int(10 * K), 0)), 10 * K)
    img = over(img, hexc(P['shade']), .18 * sh)
    # the milky body: white, a pink tint low down, a bright line just inside its edge
    milk = ramp(sstep(300, 820, Y + (X - 512) * .15), [(0, '#ffffff'), (.5, '#fff9fe'), (1, P['tint'])])
    inner = np.exp(-np.clip(-body, 0, None) / 3)
    milk = milk + (inner * .08)[..., None]
    for hh in holes:
        # the cut-through eyes: the tile shows inside, a shadow under the lid, a lit lower lip
        pass
    cut = np.full(X.shape, 1e9, np.float32)
    for hh in holes:
        cut = np.minimum(cut, hh)
    shape = np.maximum(body, -cut)
    img = over(img, milk, aa(shape))
    lidshadow = np.exp(-np.clip(-cut, 0, None) / 6) * aa(cut) * sstep(620, 520, Y)
    img = img * (1 - .25 * lidshadow[..., None])
    lip = np.exp(-np.clip(cut, 0, None) / 2.5) * (cut > 0) * aa(body)
    img = img + (hexc('#ffffff') - img) * (lip * .45)[..., None]
    finish(np.clip(img, 0, 1), out, P['rim'], P['bottom'])


which = ARGS[0]
sleepy(ARGS[1], which, 'pink' if which == 'pink' else 'purple')
