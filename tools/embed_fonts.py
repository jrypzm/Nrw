#!/usr/bin/env python3
"""Embed SF Pro into index.html as small base64 WOFF2 data URIs.

Usage: python3 tools/embed_fonts.py [fonts_dir]   (default: ./fonts)
Each fonts/SF-Pro-{Display,Text}-{Regular,Medium,Semibold,Bold}.otf found is
subset to Latin + the punctuation the app uses, converted to WOFF2 and inlined,
replacing its url("fonts/...") reference. Missing files are skipped.
Needs: pip install fonttools brotli
"""
import base64, io, re, sys, pathlib
from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
html_path = ROOT / "web" / "index.html"
fonts_dir = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "web" / "fonts"
UNICODES = "U+0020-007E,U+00A0-00FF,U+2010-2027,U+2030,U+2032-2033,U+2039-203A,U+2212"

html = html_path.read_text()
done = 0
for m in sorted(set(re.findall(r'url\("(?:fonts/|data:font/woff2;base64,[^"]*#)(SF-Pro-[A-Za-z]+-[A-Za-z]+)\.otf"\)', html))):
    src = next((p for ext in (".otf", ".ttf") if (p := fonts_dir / f"{m}{ext}").exists()), None)
    if not src:
        print(f"skip  {m} (not found)"); continue
    font = TTFont(src)
    opts = subset.Options(); opts.flavor = "woff2"; opts.layout_features = ["*"]; opts.name_IDs = ["*"]
    sub = subset.Subsetter(opts); sub.populate(unicodes=subset.parse_unicodes(UNICODES)); sub.subset(font)
    buf = io.BytesIO(); font.flavor = "woff2"; font.save(buf)
    data = base64.b64encode(buf.getvalue()).decode()
    # keep the original name after '#' so the script can be re-run
    new = f'url("data:font/woff2;base64,{data}#{m}.otf") format("woff2")'
    html = re.sub(r'url\("(?:fonts/|data:font/woff2;base64,[^"]*#)' + re.escape(m) + r'\.otf"\) format\("[a-z0-9]+"\)', lambda _: new, html)
    print(f"embed {m}: {len(buf.getvalue())//1024} KB"); done += 1
html_path.write_text(html)
print(f"{done} font(s) embedded into {html_path.name} ({html_path.stat().st_size//1024} KB)")
