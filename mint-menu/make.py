#!/usr/bin/env python3
"""Draws the yrs. menu icon (the full stop) at each size the panel uses, and writes
install-yrs-menu.sh with every icon inside it.

The icon is symbolic: one colour, made only of fills, so Cinnamon can recolour it to
suit any panel theme (GTK recolours fills, not strokes, so the ring is a filled band).
Each size is drawn on its own pixel grid: flat edges on whole pixels, thinner rings
at small sizes so every size looks equally bold.
"""
from pathlib import Path

HERE = Path(__file__).parent

# size: (centre, ring outer radius, ring width, lit-half radius)
SPEC = {
    16: (8, 7.0, 1.5, 3.75),
    22: (11, 9.0, 1.75, 5.0),
    24: (12, 10.0, 2.0, 5.5),
    32: (16, 13.5, 2.5, 7.5),
    48: (24, 20.0, 3.5, 11.0),
}


def ring(m, ro, ri):
    # an annulus as one even-odd path: the outer circle, then the inner one
    c = lambda r: f"M{m - r} {m}a{r} {r} 0 1 0 {2 * r} 0a{r} {r} 0 1 0 {-2 * r} 0z"
    return f'<path fill-rule="evenodd" d="{c(ro)}{c(ri)}"/>'


def svg(n):
    m, ro, w, f = SPEC[n]
    ri = ro - w
    upper = f'<path opacity="0.3" d="M{m - f} {m}h{2 * f}a{f} {f} 0 0 0 {-2 * f} 0z"/>'
    lower = f'<path d="M{m - f} {m}h{2 * f}a{f} {f} 0 0 1 {-2 * f} 0z"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{n}" height="{n}" viewBox="0 0 {n} {n}">'
            f'<g fill="#bebebe">{ring(m, ro, ri)}{upper}{lower}</g></svg>\n')


def main():
    icons = {}
    for n in SPEC:
        icons[f"{n}x{n}"] = svg(n)
    icons["scalable"] = svg(24)
    for d, s in icons.items():
        (HERE / "icons" / f"yrs-menu-{d}-symbolic.svg").write_text(s)
    blocks = "\n".join(
        f"put {d} <<'SVG'\n{s.rstrip()}\nSVG" for d, s in icons.items()
    )
    script = (HERE / "install.sh.in").read_text().replace("@ICONS@", blocks)
    out = HERE / "install-yrs-menu.sh"
    out.write_text(script)
    out.chmod(0o755)
    print(f"wrote {len(icons)} icons and {out.name}")


if __name__ == "__main__":
    main()
