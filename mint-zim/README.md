# Violet Quill: a Zim icon for Linux Mint

A new icon for [Zim Desktop Wiki](https://zim-wiki.org/): Zim's quill in sprayed black ink that breaks into grain at its tip, a white nib, on a grainy violet glow.

![The Violet Quill icon](preview.png)

## Install

```
curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-zim/install-yrs-zim.sh | sh
```

If you still see the old icon, press <kbd>Ctrl</kbd>+<kbd>Alt</kbd>+<kbd>Esc</kbd> to refresh Cinnamon, or log out and in.

## Undo

```
curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-zim/install-yrs-zim.sh | sh -s -- --undo
```

## What it does

- Puts the icon in `~/.local/share/icons/hicolor` as `yrs-zim`, at ten sizes from 16 to 512 pixels. It needs a name of its own: Mint's icon theme (Mint-Y) has its own Zim icon, and a theme's icon wins over one with the same name anywhere else.
- Gives Zim's launcher (`org.zim_wiki.Zim.desktop` from apt or Flatpak, or the older `zim.desktop`) a personal copy in `~/.local/share/applications`, identical except for the icon. Yours is used before the system's and the Flatpak's.
- If a launcher of your own starts Zim with another icon, it is pointed at the new one too.
- Anything it replaces is copied to `~/.local/share/yrs-zim` first, and undo puts it back.
- No root, nothing downloaded, Zim itself is not changed.

Zim and its quill belong to the Zim project; this is an unofficial fan icon.

## Build

```
python3 make.py
```

draws the icon from [`source/icon.html`](source/icon.html) in Chromium (Node and Playwright), scales it to every size in `icons/`, and packs them into `install-yrs-zim.sh`. `python3 make.py --pack` packs `icons/` as it is.
