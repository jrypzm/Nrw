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

- Puts the icon in `~/.local/share/icons/hicolor` under the names Zim uses, `org.zim_wiki.Zim` and the older `zim`, at ten sizes from 16 to 512 pixels. Icons in your home folder are found before the system's, so this works for the apt version and the Flatpak without touching them.
- If a launcher of yours in `~/.local/share/applications` starts Zim with another icon, it is pointed at the new one.
- Anything it replaces is copied to `~/.local/share/yrs-zim` first, and undo puts it back.
- No root, nothing downloaded, Zim itself is not changed.

Zim and its quill belong to the Zim project; this is an unofficial fan icon.

## Build

```
python3 make.py
```

draws the icon from [`source/icon.html`](source/icon.html) in Chromium (Node and Playwright), scales it to every size in `icons/`, and packs them into `install-yrs-zim.sh`. `python3 make.py --pack` packs `icons/` as it is.
