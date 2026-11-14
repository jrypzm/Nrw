#!/usr/bin/env python3
"""Build index.html from tools/index.src.html by inserting the SF Pro @font-face rules.
Run from anywhere: python3 tools/build.py  (then optionally tools/embed_fonts.py)"""
import pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
faces = []
for cut, weights in [("Display", [(200, "Thin"), (300, "Light"), (400, "Regular"), (500, "Medium"), (600, "Semibold"), (700, "Bold")]),
                     ("Text", [(400, "Regular"), (500, "Medium"), (600, "Semibold"), (700, "Bold")])]:
    for w, n in weights:
        faces.append(f'''  @font-face {{ font-family: "SFP {cut}"; font-weight: {w}; font-display: swap;
    src: local("SF Pro {cut} {n}"), local("SFPro{cut}-{n}"), url("fonts/SF-Pro-{cut}-{n}.otf") format("opentype"); }}''')
src = (ROOT / "tools/index.src.html").read_text()
(ROOT / "index.html").write_text(src.replace("/*FACES*/", "\n".join(faces)))
print("built index.html")
