#!/bin/sh
# yrs. folders: black glass folders in Nemo, each with its own name on the front.
#   install:  curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-folders/install-yrs-folders.sh | sh
#   undo:     curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-folders/install-yrs-folders.sh | sh -s -- --undo
set -eu

DATA=${XDG_DATA_HOME:-$HOME/.local/share}
CACHE=${XDG_CACHE_HOME:-$HOME/.cache}/yrs-folders
EXT=$DATA/nemo-python/extensions
KEEP=$DATA/yrs-folders

restart_nemo() {
  if command -v nemo >/dev/null 2>&1 && pgrep -x nemo >/dev/null 2>&1; then
    nemo -q >/dev/null 2>&1 || true
  fi
}

if [ "${1:-}" = "--undo" ]; then
  rm -f "$EXT/yrs-folders.py"
  rm -rf "$EXT/__pycache__/yrs-folders"*
  restart_nemo
  if [ -f "$KEEP/tagged" ] && command -v gio >/dev/null 2>&1; then
    sort -u "$KEEP/tagged" | while IFS= read -r dir; do
      [ -d "$dir" ] || continue
      case "$(gio info -a metadata::custom-icon "$dir" 2>/dev/null)" in
        *"/yrs-folders/"*) gio set -t unset "$dir" metadata::custom-icon 2>/dev/null || true ;;
      esac
    done
  fi
  rm -rf "$KEEP" "$CACHE"
  echo "Your folders are back to your theme's icons."
  echo "If any still show the glass folder, close every Nemo window and open it again."
  exit 0
fi

if ! command -v nemo >/dev/null 2>&1; then
  echo "Nemo, the Linux Mint file manager, isn't installed." >&2
  exit 1
fi
if [ ! -e /usr/lib/x86_64-linux-gnu/nemo/extensions-3.0/libnemo-python.so ] \
   && ! ls /usr/lib/*/nemo/extensions-3.0/libnemo-python.so >/dev/null 2>&1 \
   && ! ls /usr/lib/nemo/extensions-3.0/libnemo-python.so >/dev/null 2>&1; then
  echo "This needs Nemo's Python plugin support. Install it, then run this again:" >&2
  echo "  sudo apt install nemo-python" >&2
  exit 1
fi

mkdir -p "$EXT" "$KEEP"
cat > "$EXT/yrs-folders.py" <<'PY'
# yrs. folders: a black glass folder for every folder in Nemo, with the
# folder's own name and how many things are inside written on the front.
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
VERSION = "2"

# The folder on a 256 grid: the back with its tab, the pages, the glass front.
BACK = ("M 30 70 Q 30 52 48 52 L 92 52 Q 104 52 112 60 L 120 68 Q 126 74 136 74 "
        "L 210 74 Q 228 74 228 92 L 228 196 Q 228 212 212 212 L 44 212 Q 28 212 28 196 Z")
FRONT = ("M 24 122 Q 24 104 42 104 L 214 104 Q 232 104 232 122 "
         "L 232 200 Q 232 218 214 218 L 42 218 Q 24 218 24 200 Z")
RIM = "M 25.5 122 Q 25.5 105.5 42 105.5 L 214 105.5 Q 230.5 105.5 230.5 122"
PAGES = """
<g transform="rotate(-12 107 170)"><rect x="86" y="74" width="42" height="84" rx="8" fill="url(#paper)" stroke="#000" stroke-opacity=".10" stroke-width="1"/></g>
<g transform="rotate(-5 125 170)"><rect x="104" y="64" width="42" height="92" rx="8" fill="url(#paper)" stroke="#000" stroke-opacity=".10" stroke-width="1"/></g>
<g transform="rotate(1 143 170)"><rect x="122" y="60" width="42" height="94" rx="8" fill="url(#paper)" stroke="#000" stroke-opacity=".10" stroke-width="1"/></g>
<g transform="rotate(7 161 170)"><rect x="140" y="64" width="42" height="90" rx="8" fill="url(#paper)" stroke="#000" stroke-opacity=".10" stroke-width="1"/></g>
<g transform="rotate(13 177 170)"><rect x="156" y="74" width="42" height="82" rx="8" fill="url(#paper)" stroke="#000" stroke-opacity=".10" stroke-width="1"/></g>
"""


def fit(name):
    """Pick a size the name fits at, shortening it if it is long."""
    for size, room in ((28, 11), (23, 14), (19, 17)):
        if len(name) <= room:
            return size, name
    return 19, name[:16].rstrip() + "…"


def count_label(n):
    if n is None:
        return ""
    if n == 0:
        return "Empty"
    return "1 item" if n == 1 else f"{n:,} items"


def svg(name, n):
    size, shown = fit(name)
    label = count_label(n)
    sub = (f'<text x="40" y="200" font-family="Inter, Ubuntu, sans-serif" font-size="16" '
           f'font-weight="500" fill="#fff" fill-opacity=".55">{escape(label)}</text>') if label else ""
    y = 176 if label else 190
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
<defs>
 <linearGradient id="back" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3c3c40"/><stop offset=".22" stop-color="#1c1c1f"/><stop offset="1" stop-color="#111113"/></linearGradient>
 <linearGradient id="paper" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff"/><stop offset="1" stop-color="#e6e6e9"/></linearGradient>
 <linearGradient id="glass" x1="0" y1="0" x2=".15" y2="1"><stop offset="0" stop-color="#9a9aa0" stop-opacity=".42"/><stop offset=".45" stop-color="#5a5a60" stop-opacity=".55"/><stop offset="1" stop-color="#1a1a1c" stop-opacity=".9"/></linearGradient>
 <linearGradient id="edge" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity=".6"/><stop offset=".5" stop-color="#fff" stop-opacity=".25"/><stop offset="1" stop-color="#fff" stop-opacity=".55"/></linearGradient>
 <clipPath id="front"><path d="{FRONT}"/></clipPath>
 <filter id="frost" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="7"/></filter>
 <filter id="shade" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="9"/></filter>
</defs>
<ellipse cx="128" cy="222" rx="96" ry="10" fill="#000" opacity=".45" filter="url(#shade)"/>
<path d="{BACK}" fill="url(#back)"/>
<path d="M 31.5 70 Q 31.5 53.5 48 53.5 L 92 53.5 Q 103.5 53.5 111 61" fill="none" stroke="#fff" stroke-opacity=".18" stroke-width="2"/>
<g id="pages">{PAGES}</g>
<g clip-path="url(#front)"><g filter="url(#frost)" opacity=".9">{PAGES}</g></g>
<path d="{FRONT}" fill="url(#glass)"/>
<path d="{RIM}" fill="none" stroke="url(#edge)" stroke-width="2.5"/>
<text x="40" y="{y}" font-family="Inter, Ubuntu, sans-serif" font-size="{size}" font-weight="600" fill="#fff" letter-spacing="-.2">{escape(shown)}</text>
{sub}
</svg>
"""


def count(path):
    try:
        with os.scandir(path) as it:
            n = 0
            for e in it:
                if not e.name.startswith("."):
                    n += 1
                    if n >= 99999:
                        break
            return n
    except OSError:
        return None


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
    n = count(path)
    digest = hashlib.sha1(f"{VERSION}\0{name}\0{n}".encode()).hexdigest()[:16]
    target = os.path.join(CACHE, digest + ".svg")
    if not os.path.exists(target):
        os.makedirs(CACHE, exist_ok=True)
        tmp = target + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            f.write(svg(name, n))
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
    """Dresses each folder Nemo shows and watches it, so the count stays true."""

    WATCH_AT_MOST = 400

    def __init__(self):
        super().__init__()
        self.watching = {}  # path -> (monitor, Nemo file)
        self.pending = {}   # path -> timeout id

    def update_file_info(self, file):
        if file.get_uri_scheme() != "file" or not file.is_directory():
            return
        path = file.get_location().get_path()
        if not path:
            return
        try:
            dress(path)
            self.watch(path, file)
        except Exception:  # never take Nemo down with us
            pass

    def watch(self, path, file):
        if path in self.watching:
            monitor, _ = self.watching.pop(path)
            self.watching[path] = (monitor, file)  # most recent last
            return
        if len(self.watching) >= self.WATCH_AT_MOST:
            oldest = next(iter(self.watching))
            self.watching.pop(oldest)[0].cancel()
        monitor = Gio.File.new_for_path(path).monitor_directory(Gio.FileMonitorFlags.NONE, None)
        monitor.connect("changed", self.changed, path)
        self.watching[path] = (monitor, file)

    def changed(self, monitor, child, other, event, path):
        if event not in (Gio.FileMonitorEvent.CREATED, Gio.FileMonitorEvent.DELETED,
                         Gio.FileMonitorEvent.MOVED_IN, Gio.FileMonitorEvent.MOVED_OUT,
                         Gio.FileMonitorEvent.RENAMED):
            return
        if path not in self.pending:
            self.pending[path] = GLib.timeout_add(700, self.refresh, path)

    def refresh(self, path):
        self.pending.pop(path, None)
        entry = self.watching.get(path)
        if entry and os.path.isdir(path):
            try:
                dress(path)
                entry[1].invalidate_extension_info()
            except Exception:
                pass
        return False
PY
restart_nemo

echo "Your folders now show the yrs. glass folder with their name on the front."
echo "Open Files. To read the names, make the icons bigger with Ctrl and + (or the slider at the bottom right)."
echo "To undo: curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-folders/install-yrs-folders.sh | sh -s -- --undo"
