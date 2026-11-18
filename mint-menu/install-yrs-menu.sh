#!/bin/sh
# The yrs. menu icon for Linux Mint (Cinnamon): the yrs. full stop on your panel.
#
#   sh install-yrs-menu.sh             put the icon on your menu
#   sh install-yrs-menu.sh --undo      bring back the icon you had before
#
# What it does, all inside your home folder:
#   - copies the icon into ~/.local/share/icons (sizes 16 to 48, and scalable)
#   - remembers the menu icon you have now, then sets Cinnamon's menu icon to it
#   - if a menu uses its own custom icon, changes that one too (after a backup)
# No root, nothing downloaded, nothing outside your home folder is touched.
set -eu

NAME=yrs-menu-symbolic
ICONS="${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor"
KEEP="${XDG_DATA_HOME:-$HOME/.local/share}/yrs-menu"
SPICES="${XDG_CONFIG_HOME:-$HOME/.config}/cinnamon/spices/menu@cinnamon.org"

say() { printf '%s\n' "$*"; }
die() { say "yrs. menu: $*" >&2; exit 1; }

command -v gsettings >/dev/null 2>&1 || die "gsettings isn't available. Is this Cinnamon?"
gsettings get org.cinnamon app-menu-icon-name >/dev/null 2>&1 || die "this desktop isn't Cinnamon (Linux Mint's default desktop)."

# point menus that use their own icon at $1; keep a copy of each settings file first
custom_menus() {
    [ -d "$SPICES" ] || return 0
    command -v python3 >/dev/null 2>&1 || return 0
    for f in "$SPICES"/*.json; do
        [ -f "$f" ] || continue
        python3 - "$f" "$1" "$KEEP" <<'PY'
import json, os, shutil, sys
path, want, keep = sys.argv[1], sys.argv[2], sys.argv[3]
with open(path) as fh:
    data = json.load(fh)
custom = data.get("menu-custom", {}).get("value")
icon = data.get("menu-icon")
if not custom or not isinstance(icon, dict):
    sys.exit(0)
backup = os.path.join(keep, "spices-" + os.path.basename(path))
if want == "--restore":
    if os.path.exists(backup):
        shutil.copyfile(backup, path)
        os.remove(backup)
    sys.exit(0)
os.makedirs(keep, exist_ok=True)
if not os.path.exists(backup):
    shutil.copyfile(path, backup)
icon["value"] = want
tmp = path + ".tmp"
with open(tmp, "w") as fh:
    json.dump(data, fh, indent=4)
os.replace(tmp, path)
PY
    done
}

if [ "${1:-}" = "--undo" ] || [ "${1:-}" = "--uninstall" ]; then
    if [ -f "$KEEP/previous" ]; then
        gsettings set org.cinnamon app-menu-icon-name "$(cat "$KEEP/previous")"
    else
        gsettings reset org.cinnamon app-menu-icon-name
    fi
    custom_menus --restore
    find "$ICONS" -name "$NAME.svg" -path "*/apps/*" -delete 2>/dev/null || true
    rm -rf "$KEEP"
    say "Your menu icon is back to how it was."
    exit 0
fi

put() {
    mkdir -p "$ICONS/$1/apps"
    cat > "$ICONS/$1/apps/$NAME.svg"
}

put 16x16 <<'SVG'
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"><g fill="#bebebe"><path fill-rule="evenodd" d="M1.0 8a7.0 7.0 0 1 0 14.0 0a7.0 7.0 0 1 0 -14.0 0zM2.5 8a5.5 5.5 0 1 0 11.0 0a5.5 5.5 0 1 0 -11.0 0z"/><path opacity="0.3" d="M4.25 8h7.5a3.75 3.75 0 0 0 -7.5 0z"/><path d="M4.25 8h7.5a3.75 3.75 0 0 1 -7.5 0z"/></g></svg>
SVG
put 22x22 <<'SVG'
<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 22 22"><g fill="#bebebe"><path fill-rule="evenodd" d="M2.0 11a9.0 9.0 0 1 0 18.0 0a9.0 9.0 0 1 0 -18.0 0zM3.75 11a7.25 7.25 0 1 0 14.5 0a7.25 7.25 0 1 0 -14.5 0z"/><path opacity="0.3" d="M6.0 11h10.0a5.0 5.0 0 0 0 -10.0 0z"/><path d="M6.0 11h10.0a5.0 5.0 0 0 1 -10.0 0z"/></g></svg>
SVG
put 24x24 <<'SVG'
<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><g fill="#bebebe"><path fill-rule="evenodd" d="M2.0 12a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0zM4.0 12a8.0 8.0 0 1 0 16.0 0a8.0 8.0 0 1 0 -16.0 0z"/><path opacity="0.3" d="M6.5 12h11.0a5.5 5.5 0 0 0 -11.0 0z"/><path d="M6.5 12h11.0a5.5 5.5 0 0 1 -11.0 0z"/></g></svg>
SVG
put 32x32 <<'SVG'
<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32"><g fill="#bebebe"><path fill-rule="evenodd" d="M2.5 16a13.5 13.5 0 1 0 27.0 0a13.5 13.5 0 1 0 -27.0 0zM5.0 16a11.0 11.0 0 1 0 22.0 0a11.0 11.0 0 1 0 -22.0 0z"/><path opacity="0.3" d="M8.5 16h15.0a7.5 7.5 0 0 0 -15.0 0z"/><path d="M8.5 16h15.0a7.5 7.5 0 0 1 -15.0 0z"/></g></svg>
SVG
put 48x48 <<'SVG'
<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48"><g fill="#bebebe"><path fill-rule="evenodd" d="M4.0 24a20.0 20.0 0 1 0 40.0 0a20.0 20.0 0 1 0 -40.0 0zM7.5 24a16.5 16.5 0 1 0 33.0 0a16.5 16.5 0 1 0 -33.0 0z"/><path opacity="0.3" d="M13.0 24h22.0a11.0 11.0 0 0 0 -22.0 0z"/><path d="M13.0 24h22.0a11.0 11.0 0 0 1 -22.0 0z"/></g></svg>
SVG
put scalable <<'SVG'
<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><g fill="#bebebe"><path fill-rule="evenodd" d="M2.0 12a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0zM4.0 12a8.0 8.0 0 1 0 16.0 0a8.0 8.0 0 1 0 -16.0 0z"/><path opacity="0.3" d="M6.5 12h11.0a5.5 5.5 0 0 0 -11.0 0z"/><path d="M6.5 12h11.0a5.5 5.5 0 0 1 -11.0 0z"/></g></svg>
SVG

# the icon folder changed: let the desktop notice
touch "$ICONS"

mkdir -p "$KEEP"
now=$(gsettings get org.cinnamon app-menu-icon-name | sed "s/^'//; s/'\$//")
if [ "$now" != "$NAME" ] && [ ! -f "$KEEP/previous" ]; then
    printf '%s' "$now" > "$KEEP/previous"
fi
gsettings set org.cinnamon app-menu-icon-name "$NAME"
custom_menus "$NAME"

say "The yrs. full stop is on your menu."
say "If the old icon is still showing, press Ctrl+Alt+Esc to refresh Cinnamon."
say "To undo: sh install-yrs-menu.sh --undo"
