# Volt: a Helium icon for Linux Mint

A new icon for the [Helium](https://github.com/imputnet/helium) browser: acid lime, Helium's own star in black, each arm notched at its root so a small star of light opens in the centre.

![The Volt icon](preview.png)

## Install

```
curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-helium/install-yrs-helium.sh | sh
```

If you still see the old icon, press <kbd>Ctrl</kbd>+<kbd>Alt</kbd>+<kbd>Esc</kbd> to refresh Cinnamon, or log out and in.

## Undo

```
curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-helium/install-yrs-helium.sh | sh -s -- --undo
```

## What it does

- Puts the icon in `~/.local/share/icons/hicolor` as `helium`, the name Helium's own packages use, at ten sizes from 16 to 512 pixels. Icons in your home folder are found before the system's, so this works for the .deb, .rpm and tarball installs without touching them.
- If you added Helium to your menu from an AppImage, its launcher in `~/.local/share/applications` is pointed at the new icon too.
- Anything it replaces is copied to `~/.local/share/yrs-helium` first, and undo puts it back.
- No root, nothing downloaded, Helium itself is not changed.

While Helium is open, its window may still carry the icon built into the browser; the menu, panel launcher and Alt+Tab use this one.

Helium and its star belong to imput; this is an unofficial fan icon.

## Build

```
python3 make.py
```

draws the icon from [`source/icon.html`](source/icon.html) in Chromium (Node and Playwright), scales it to every size in `icons/`, and packs them into `install-yrs-helium.sh`. `python3 make.py --pack` packs `icons/` as it is.
