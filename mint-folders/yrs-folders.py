# yrs. folders: a black glass folder for every folder in Nemo, with the
# folder's own name written big on the front.
#
# Nemo shows a folder's "custom icon" when one is set in its metadata.
# For each folder Nemo shows, this draws a small SVG with that folder's
# name and sets it as the custom icon. Folders that already have a custom
# icon you picked yourself are left alone.

import hashlib
import os
from xml.sax.saxutils import escape

import gi

gi.require_version("Nemo", "3.0")
from gi.repository import GLib, GObject, Gio, Nemo  # noqa: E402

KEY = "metadata::custom-icon"
CACHE = os.path.join(GLib.get_user_cache_dir(), "yrs-folders")
TAGGED = os.path.join(GLib.get_user_data_dir(), "yrs-folders", "tagged")
MINE = Gio.File.new_for_path(CACHE).get_uri() + "/"
VERSION = "3"

# The folder on a 256 grid, filling it edge to edge: the back with its tab,
# the pages, and the frosted front that carries the name.
BACK = ("M 12 46 Q 12 26 32 26 L 84 26 Q 98 26 107 35 L 114 42 Q 121 49 132 49 "
        "L 226 49 Q 246 49 246 69 L 246 214 Q 246 232 228 232 L 30 232 Q 12 232 12 214 Z")
FRONT = ("M 6 104 Q 6 84 26 84 L 230 84 Q 250 84 250 104 "
         "L 250 220 Q 250 240 230 240 L 26 240 Q 6 240 6 220 Z")
RIM = "M 7.5 104 Q 7.5 85.5 26 85.5 L 230 85.5 Q 248.5 85.5 248.5 104"
PAGES = "\n".join(
    f'<g transform="rotate({r} {x + 23} 150)"><rect x="{x}" y="{y}" width="46" height="{h}" rx="8" '
    f'fill="url(#paper)" stroke="#000" stroke-opacity=".10"/></g>'
    for r, x, y, h in ((-12, 70, 40, 80), (-5, 92, 32, 88), (1, 114, 28, 90), (7, 136, 32, 88), (13, 156, 40, 80)))

ROOM = 214      # how wide the name may be, on the 256 grid
EM = 0.6        # a generous average width of one character, in em


def lines_for(name):
    """One big line if it fits, else two lines, else two lines cut short."""
    size = min(46, ROOM / (EM * max(len(name), 1)))
    if size >= 28:
        return round(size), [name]
    words, top = name.split(), ""
    if len(words) > 1:
        # split at the space nearest the middle
        best = min(range(1, len(words)), key=lambda i: abs(len(" ".join(words[:i])) - len(name) / 2))
        top, bottom = " ".join(words[:best]), " ".join(words[best:])
    else:
        half = (len(name) + 1) // 2
        top, bottom = name[:half], name[half:]
    size = min(34, ROOM / (EM * max(len(top), len(bottom))))
    if size < 26:
        size = 26
        most = int(ROOM / (EM * size))
        top = top if len(top) <= most else top[:most - 1].rstrip() + "…"
        bottom = bottom if len(bottom) <= most else bottom[:most - 1].rstrip() + "…"
    return round(size), [top, bottom]


def svg(name):
    size, lines = lines_for(name)
    if len(lines) == 1:
        ys = [208]
    else:
        ys = [190, 190 + round(size * 1.08)]
    text = "".join(
        f'<text x="24" y="{y}" font-family="Inter, Ubuntu, sans-serif" font-size="{size}" '
        f'font-weight="700" fill="#fff" letter-spacing="{-0.02 * size:.2f}">{escape(line)}</text>'
        for y, line in zip(ys, lines))
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
<defs>
 <linearGradient id="back" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3c3c40"/><stop offset=".22" stop-color="#1c1c1f"/><stop offset="1" stop-color="#111113"/></linearGradient>
 <linearGradient id="paper" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff"/><stop offset="1" stop-color="#e6e6e9"/></linearGradient>
 <linearGradient id="glass" x1="0" y1="0" x2=".15" y2="1"><stop offset="0" stop-color="#9a9aa0" stop-opacity=".45"/><stop offset=".4" stop-color="#4a4a50" stop-opacity=".75"/><stop offset="1" stop-color="#151517" stop-opacity=".95"/></linearGradient>
 <linearGradient id="edge" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity=".65"/><stop offset=".5" stop-color="#fff" stop-opacity=".25"/><stop offset="1" stop-color="#fff" stop-opacity=".6"/></linearGradient>
 <clipPath id="front"><path d="{FRONT}"/></clipPath>
 <filter id="frost" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="7"/></filter>
</defs>
<path d="{BACK}" fill="url(#back)"/>
<path d="M 13.5 46 Q 13.5 27.5 32 27.5 L 84 27.5 Q 97.5 27.5 106 36" fill="none" stroke="#fff" stroke-opacity=".2" stroke-width="2"/>
<g>{PAGES}</g>
<g clip-path="url(#front)"><g filter="url(#frost)" opacity=".85">{PAGES}</g></g>
<path d="{FRONT}" fill="url(#glass)"/>
<path d="{RIM}" fill="none" stroke="url(#edge)" stroke-width="2.5"/>
{text}
</svg>
"""


def remember(path):
    os.makedirs(os.path.dirname(TAGGED), exist_ok=True)
    try:
        with open(TAGGED, "a", encoding="utf-8") as f:
            f.write(path + "\n")
    except OSError:
        pass


def dress(path):
    """Give one folder its icon. Safe to call again and again."""
    loc = Gio.File.new_for_path(path)
    try:
        info = loc.query_info(KEY, Gio.FileQueryInfoFlags.NONE, None)
    except GLib.Error:
        return
    current = info.get_attribute_string(KEY)
    if current and not current.startswith(MINE):
        return  # an icon you chose yourself
    name = os.path.basename(path.rstrip("/")) or "/"
    if path == GLib.get_home_dir():
        name = "Home"
    digest = hashlib.sha1(f"{VERSION}\0{name}".encode()).hexdigest()[:16]
    target = os.path.join(CACHE, digest + ".svg")
    if not os.path.exists(target):
        os.makedirs(CACHE, exist_ok=True)
        tmp = target + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            f.write(svg(name))
        os.replace(tmp, target)
    uri = Gio.File.new_for_path(target).get_uri()
    if current == uri:
        return
    try:
        loc.set_attribute_string(KEY, uri, Gio.FileQueryInfoFlags.NONE, None)
    except GLib.Error:
        return
    if not current:
        remember(path)


class YrsFolders(GObject.GObject, Nemo.InfoProvider):
    def update_file_info(self, file):
        if file.get_uri_scheme() != "file" or not file.is_directory():
            return
        path = file.get_location().get_path()
        if path:
            try:
                dress(path)
            except Exception:  # never take Nemo down with us
                pass
