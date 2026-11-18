#!/bin/sh
# Pure black Linux Mint: every app window, Settings, title bars, the desktop right-click menu,
# the panel and its menus in #000000, with white text.
#
#   sh install-yrs-black.sh            make it all black
#   sh install-yrs-black.sh --undo     put back the themes you had before
#
# What it does, all inside your home folder:
#   - copies your current theme to ~/.themes/<your theme>-Black (starting from its dark version,
#     so text stays readable) and adds one block of black colours at the end of it:
#     GTK 3 and 4 apps, title bars (the window borders read their colours from it),
#     the Cinnamon panel, menus and popups
#   - covers the newest GTK 4 apps too, in ~/.config/gtk-4.0/gtk.css (yours is kept if you had one)
#   - switches Mint to the copy and remembers exactly what you had, for --undo
# No root, nothing downloaded, your original themes are not touched.
set -eu

DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
CONF="${XDG_CONFIG_HOME:-$HOME/.config}"
KEEP="$DATA/yrs-black"
SUFFIX="-Black"

say() { printf '%s\n' "$*"; }
get() { gsettings get "$1" "$2" 2>/dev/null | sed "s/^'//; s/'\$//" || true; }
set_() { gsettings set "$1" "$2" "$3" 2>/dev/null || true; }
find_theme() {   # a theme directory by name, yours first
    for d in "$HOME/.themes/$1" "$DATA/themes/$1" "/usr/share/themes/$1"; do
        [ -d "$d" ] && { printf '%s' "$d"; return 0; }
    done
    return 1
}

command -v gsettings >/dev/null 2>&1 || { say "This needs gsettings (Linux Mint with Cinnamon)." >&2; exit 1; }

if [ "${1:-}" = "--undo" ] || [ "${1:-}" = "--uninstall" ]; then
    [ -f "$KEEP/previous" ] || { say "Nothing to undo."; exit 0; }
    while IFS='|' read -r schema key value; do
        [ -n "$schema" ] && set_ "$schema" "$key" "$value"
    done < "$KEEP/previous"
    [ -f "$KEEP/made" ] && rm -rf "$(cat "$KEEP/made")"
    rm -f "$CONF/gtk-4.0/gtk.css"
    [ -f "$KEEP/gtk4.css" ] && cp "$KEEP/gtk4.css" "$CONF/gtk-4.0/gtk.css"
    rm -rf "$KEEP"
    say "Your themes are back to how they were."
    say "Apps that were already open change when you open them again."
    exit 0
fi

GTK=$(get org.cinnamon.desktop.interface gtk-theme)
WM=$(get org.cinnamon.desktop.wm.preferences theme)
CIN=$(get org.cinnamon.theme name)

# what you had before, kept once (running this again doesn't lose it)
mkdir -p "$KEEP"
if [ ! -f "$KEEP/previous" ]; then
    # an earlier "black panel" command kept the Cinnamon theme it replaced: use that one
    if [ -f "$DATA/yrs-panel/previous" ]; then CIN_WAS=$(cat "$DATA/yrs-panel/previous"); else CIN_WAS=$CIN; fi
    {
        printf 'org.cinnamon.desktop.interface|gtk-theme|%s\n' "$GTK"
        printf 'org.cinnamon.desktop.wm.preferences|theme|%s\n' "$WM"
        printf 'org.cinnamon.theme|name|%s\n' "$CIN_WAS"
        printf 'org.gnome.desktop.interface|gtk-theme|%s\n' "$(get org.gnome.desktop.interface gtk-theme)"
        printf 'org.gnome.desktop.interface|color-scheme|%s\n' "$(get org.gnome.desktop.interface color-scheme)"
    } > "$KEEP/previous"
    [ -f "$CONF/gtk-4.0/gtk.css" ] && cp "$CONF/gtk-4.0/gtk.css" "$KEEP/gtk4.css"
fi
BASE=$(sed -n 's/^org.cinnamon.desktop.interface|gtk-theme|//p' "$KEEP/previous")
BASE=${BASE%"$SUFFIX"}
CINBASE=$(sed -n 's/^org.cinnamon.theme|name|//p' "$KEEP/previous")
CINBASE=${CINBASE%"$SUFFIX"}

# start from the dark version of your theme, so text stays light on black
case "$BASE" in
    *Dark*|*dark*) ;;
    Mint-Y*|Mint-L*|Mint-X*)
        DARK=$(printf '%s' "$BASE" | sed 's/^\(Mint-[YLX]\)/\1-Dark/')
        find_theme "$DARK" >/dev/null && BASE=$DARK ;;
    Adwaita) BASE=Adwaita-dark ;;
esac
SRC=$(find_theme "$BASE") || { say "I couldn't find your theme \"$BASE\"." >&2; exit 1; }

NAME="$BASE$SUFFIX"
DEST="$HOME/.themes/$NAME"
rm -rf "$DEST"
mkdir -p "$HOME/.themes"
cp -rL "$SRC" "$DEST"
printf '%s' "$DEST" > "$KEEP/made"

# the Cinnamon part: from your Cinnamon theme (or Cinnamon's own default)
if [ -z "$CINBASE" ] || [ "$CINBASE" = cinnamon ]; then
    CSRC=/usr/share/cinnamon/theme
elif CDIR=$(find_theme "$CINBASE") && [ -d "$CDIR/cinnamon" ]; then
    CSRC="$CDIR/cinnamon"
else
    CSRC="$SRC/cinnamon"
fi
if [ -d "$CSRC" ]; then rm -rf "$DEST/cinnamon"; cp -rL "$CSRC" "$DEST/cinnamon"; fi

[ -f "$DEST/index.theme" ] && sed -i -e "s/^Name=.*/Name=$NAME/" -e "s/^GtkTheme=.*/GtkTheme=$NAME/" -e "s/^MetacityTheme=.*/MetacityTheme=$NAME/" "$DEST/index.theme"

GTK_BLACK='
/* yrs. black: everything #000000 */
@define-color theme_bg_color #000000;
@define-color theme_base_color #000000;
@define-color theme_unfocused_bg_color #000000;
@define-color theme_unfocused_base_color #000000;
@define-color bg_color #000000;
@define-color base_color #000000;
@define-color insensitive_bg_color #000000;
@define-color insensitive_base_color #000000;
@define-color content_view_bg #000000;
@define-color theme_fg_color #ffffff;
@define-color theme_text_color #ffffff;
@define-color fg_color #ffffff;
@define-color text_color #ffffff;
@define-color borders #222222;
@define-color unfocused_borders #1a1a1a;
@define-color wm_bg #000000;
@define-color wm_bg_unfocused #000000;
@define-color wm_highlight #000000;
@define-color wm_border #000000;
@define-color wm_border_unfocused #000000;
@define-color wm_title #ffffff;
@define-color wm_title_unfocused #8c8c8c;
@define-color wm_button_hover_bg #262626;
@define-color wm_button_active_bg #333333;

window, window.background, .background, dialog, messagedialog, .dialog-vbox,
headerbar, headerbar:backdrop, .titlebar, .titlebar:backdrop, decoration,
menubar, menu, .menu, .context-menu, menu menuitem, popover, popover.background, popover > contents, popover > arrow,
notebook, notebook > header, notebook > stack, notebook > header > tabs > tab, stack, scrolledwindow, viewport,
treeview.view, treeview.view header button, iconview, .view, list, row, textview, textview text,
.sidebar, placessidebar, placessidebar list, .navigation-sidebar, paned > separator,
toolbar, .toolbar, .primary-toolbar, statusbar, actionbar, infobar, frame > border, tooltip, tooltip.background,
.csd.popup decoration, .nemo-window .sidebar, .nemo-window notebook {
  background-color: #000000;
  background-image: none;
  color: #ffffff;
  box-shadow: none;
}
row:selected, .view:selected, treeview.view:selected, iconview:selected, placessidebar row:selected {
  background-color: #2b2b2b;
  color: #ffffff;
}
row:hover, menu menuitem:hover, .menu menuitem:hover, .context-menu menuitem:hover, modelbutton:hover {
  background-color: #1c1c1c;
  color: #ffffff;
}
button, entry, spinbutton, combobox button, .linked > button, switch, scale trough, progressbar trough {
  background-color: #000000;
  background-image: none;
  border-color: #333333;
  color: #ffffff;
  box-shadow: none;
}
button:hover, entry:focus { background-color: #141414; border-color: #4a4a4a; }
button:active, button:checked { background-color: #262626; }
switch:checked, progressbar progress, scale highlight { background-color: #e8e8e8; color: #000000; }
switch slider { background-color: #ffffff; }
/* the desktop keeps its wallpaper */
.nemo-desktop, .nemo-desktop.view, .nemo-desktop-window, window.nemo-desktop-window { background-color: transparent; }
'
for f in "$DEST/gtk-3.0/gtk.css" "$DEST/gtk-3.0/gtk-dark.css" "$DEST/gtk-4.0/gtk.css" "$DEST/gtk-4.0/gtk-dark.css"; do
    [ -f "$f" ] && printf '%s\n' "$GTK_BLACK" >> "$f"
done
[ -d "$DEST/gtk-3.0" ] && [ ! -f "$DEST/gtk-3.0/gtk.css" ] && printf '%s\n' "$GTK_BLACK" > "$DEST/gtk-3.0/gtk.css"

if [ -f "$DEST/cinnamon/cinnamon.css" ]; then
    cat >> "$DEST/cinnamon/cinnamon.css" <<'CSS'

/* yrs. black: panel, menus and popups #000000 */
.panel-bottom, .panel-top, .panel-left, .panel-right, #panelBottom, #panelTop, #panelLeft, #panelRight {
  background-color: #000000; background-image: none; background-gradient-direction: none; }
.menu, .popup-menu, .popup-menu-content, .popup-combo-menu, .popup-sub-menu, .menu-context-menu,
.menu-favorites-box, .menu-categories-box, .menu-applications-outer-box, .menu-places-box,
.switcher-list, .info-osd, .osd-window, .workspace-osd, .notification, .notification-with-image,
.modal-dialog, .run-dialog, .calendar, .tooltip, .desklet {
  background-color: #000000; background-image: none; background-gradient-direction: none;
  color: #ffffff; border-color: #222222; }
.popup-menu-boxpointer, .menu-boxpointer { -arrow-background-color: #000000; -arrow-border-color: #222222; }
.popup-menu-item:active, .popup-menu-item:hover, .menu-application-button-selected, .menu-category-button-selected {
  background-color: #222222; color: #ffffff; }
CSS
fi

# the newest GTK 4 apps (libadwaita) ignore themes; they read their colours from here
mkdir -p "$CONF/gtk-4.0"
{
    [ -f "$KEEP/gtk4.css" ] && cat "$KEEP/gtk4.css"
    cat <<'CSS'

/* yrs. black */
@define-color window_bg_color #000000;
@define-color window_fg_color #ffffff;
@define-color view_bg_color #000000;
@define-color view_fg_color #ffffff;
@define-color headerbar_bg_color #000000;
@define-color headerbar_backdrop_color #000000;
@define-color headerbar_fg_color #ffffff;
@define-color sidebar_bg_color #000000;
@define-color sidebar_backdrop_color #000000;
@define-color secondary_sidebar_bg_color #000000;
@define-color card_bg_color #0d0d0d;
@define-color dialog_bg_color #000000;
@define-color popover_bg_color #000000;
@define-color thumbnail_bg_color #0d0d0d;
CSS
} > "$CONF/gtk-4.0/gtk.css"

set_ org.cinnamon.desktop.interface gtk-theme "$NAME"
set_ org.cinnamon.desktop.wm.preferences theme "$NAME"
set_ org.cinnamon.theme name "$NAME"
set_ org.gnome.desktop.interface gtk-theme "$NAME"
set_ org.gnome.desktop.interface color-scheme prefer-dark
rm -rf "$DATA/yrs-panel"

say "Everything is black now: apps, Settings, title bars, menus and the panel."
say "Apps that were already open turn black when you open them again (or log out and in)."
say "To undo: curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-black/install-yrs-black.sh | sh -s -- --undo"
