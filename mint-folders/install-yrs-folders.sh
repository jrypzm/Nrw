#!/bin/sh
# yrs. folders: a hundred material folders for Nemo, a different one for every folder.
#   install:  curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-folders/install-yrs-folders.sh | sh
#   undo:     curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-folders/install-yrs-folders.sh | sh -s -- --undo
set -eu

PACK_URL=${YRS_FOLDERS_PACK:-https://github.com/jrypzm/yrs/releases/download/mint-folders/yrs-folders-designs.tar.gz}
PACK_SHA=c41532c2d645d8aaf34666aebedf265cb4157717f8d81fa1d1ce1bf5543ae830
DATA=${XDG_DATA_HOME:-$HOME/.local/share}
CACHE=${XDG_CACHE_HOME:-$HOME/.cache}/yrs-folders
EXT=$DATA/nemo-python/extensions
KEEP=$DATA/yrs-folders

restart_nemo() {
  if command -v nemo >/dev/null 2>&1 && pgrep -x nemo >/dev/null 2>&1; then
    nemo -q >/dev/null 2>&1 || true
  fi
  # the desktop is drawn by its own program; start it again so it loads the plugin too
  if command -v nemo-desktop >/dev/null 2>&1 && pgrep -x nemo-desktop >/dev/null 2>&1; then
    nemo-desktop -q >/dev/null 2>&1 || true
    sleep 1
    (setsid nemo-desktop >/dev/null 2>&1 &) || true
  fi
}
set_zoom() {
  for try in 1 2 3; do
    gsettings set org.nemo.icon-view default-zoom-level "$1" 2>/dev/null || true
    sleep 1
    [ "$(gsettings get org.nemo.icon-view default-zoom-level 2>/dev/null)" = "'$1'" ] && break
  done
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
  if [ -f "$KEEP/zoom" ] && command -v gsettings >/dev/null 2>&1; then
    set_zoom "$(cat "$KEEP/zoom")"
  fi
  rm -rf "$KEEP" "$CACHE"
  echo "Your folders are back to your theme's icons."
  echo "If any still show a yrs. folder, close every Nemo window and open it again."
  exit 0
fi

if ! command -v nemo >/dev/null 2>&1; then
  echo "Nemo, the Linux Mint file manager, isn't installed." >&2
  exit 1
fi
found=""
for so in /usr/lib/*/nemo/extensions-3.0/libnemo-python.so /usr/lib/nemo/extensions-3.0/libnemo-python.so /usr/lib64/nemo/extensions-3.0/libnemo-python.so; do
  [ -e "$so" ] && found=1
done
if [ -z "$found" ]; then
  echo "This needs Nemo's Python plugin support. Install it, then run this again:" >&2
  echo "  sudo apt install nemo-python" >&2
  exit 1
fi

echo "Fetching the hundred folders..."
mkdir -p "$KEEP"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
curl -fsSL "$PACK_URL" -o "$tmp/pack.tar.gz"
got=$(sha256sum "$tmp/pack.tar.gz" | cut -d' ' -f1)
if [ "$got" != "$PACK_SHA" ]; then
  echo "The download didn't match what was published. Nothing was changed; try again in a minute." >&2
  exit 1
fi
mkdir -p "$tmp/designs"
tar -xzf "$tmp/pack.tar.gz" -C "$tmp/designs"
rm -rf "$KEEP/designs"
mv "$tmp/designs" "$KEEP/designs"
rm -rf "$CACHE"   # pictures from an older version

# Nemo keeps your icon size. An earlier version set it to "large": put yours back.
if [ -f "$KEEP/zoom" ] && command -v gsettings >/dev/null 2>&1; then
  set_zoom "$(cat "$KEEP/zoom")"
  rm -f "$KEEP/zoom"
fi

mkdir -p "$EXT"
cat > "$EXT/yrs-folders.py" <<'PY'
# yrs. folders: one hundred material folders for Nemo. Denim, leather,
# marble, felt, puffer, wood, see-through plastic, metal and more. Every
# folder gets its own, and folders side by side never share one.
#
# Nemo shows a folder's "custom icon" when one is set in its metadata. For
# each folder Nemo shows, this picks a design and sets it. Folders that
# already have an icon you chose yourself are left alone.

import hashlib
import json
import os

import gi

gi.require_version("Nemo", "3.0")
from gi.repository import GLib, GObject, Gio, Nemo  # noqa: E402

KEY = "metadata::custom-icon"
HOME = os.path.join(GLib.get_user_data_dir(), "yrs-folders")
DESIGNS = os.path.join(HOME, "designs")
TAGGED = os.path.join(HOME, "tagged")
CHOSEN = os.path.join(HOME, "chosen.json")


def ours(uri):
    return "/yrs-folders/" in uri


class Book:
    """Which design each folder has, grouped by the folder it sits in."""

    def __init__(self):
        try:
            self.designs = sorted(f for f in os.listdir(DESIGNS) if f.endswith(".png"))
        except OSError:
            self.designs = []
        try:
            with open(CHOSEN, encoding="utf-8") as f:
                self.by_parent = json.load(f)
        except (OSError, ValueError):
            self.by_parent = {}
        self.saving = 0

    def pick(self, path):
        parent, name = os.path.split(path.rstrip("/"))
        here = self.by_parent.setdefault(parent, {})
        have = here.get(name)
        if have in self.designs:
            return have
        taken = set(here.values())
        # a fixed order for this folder, so the choice does not depend on luck
        order = sorted(self.designs, key=lambda d: hashlib.sha1(f"{path}\0{d}".encode()).digest())
        choice = next((d for d in order if d not in taken), order[0])
        here[name] = choice
        if not self.saving:
            self.saving = GLib.timeout_add(1500, self.save)
        return choice

    def save(self):
        self.saving = 0
        try:
            os.makedirs(HOME, exist_ok=True)
            tmp = CHOSEN + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(self.by_parent, f)
            os.replace(tmp, CHOSEN)
        except OSError:
            pass
        return False


def remember(path):
    try:
        os.makedirs(HOME, exist_ok=True)
        with open(TAGGED, "a", encoding="utf-8") as f:
            f.write(path + "\n")
    except OSError:
        pass


class YrsFolders(GObject.GObject, Nemo.InfoProvider):
    def __init__(self):
        super().__init__()
        self.book = Book()

    def update_file_info(self, file):
        if not self.book.designs:
            return
        if file.get_uri_scheme() != "file" or not file.is_directory():
            return
        path = file.get_location().get_path()
        if not path or path == "/":
            return
        try:
            self.dress(path)
        except Exception:  # never take Nemo down with us
            pass

    def dress(self, path):
        loc = Gio.File.new_for_path(path)
        try:
            info = loc.query_info(KEY, Gio.FileQueryInfoFlags.NONE, None)
        except GLib.Error:
            return
        current = info.get_attribute_string(KEY)
        if current and not ours(current):
            return  # an icon you chose yourself
        uri = Gio.File.new_for_path(os.path.join(DESIGNS, self.book.pick(path))).get_uri()
        if current == uri:
            return
        try:
            loc.set_attribute_string(KEY, uri, Gio.FileQueryInfoFlags.NONE, None)
        except GLib.Error:
            return
        if not current:
            remember(path)
PY
restart_nemo

echo "Done. Open Files: every folder has its own design now."
echo "If a folder still shows the old icon, press F5 in that window to refresh it."
echo "To undo: curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-folders/install-yrs-folders.sh | sh -s -- --undo"
