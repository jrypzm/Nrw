# A VSCodium icon for Linux Mint

A new icon for [VSCodium](https://vscodium.com): its coral, traced from the official logo, in crisp white on a bold blue tile with a thin rim of light.

![The new VSCodium icon](preview.png)

## Install

```
curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-vscodium/install-yrs-vscodium.sh | sh
```

If you still see the old icon, press <kbd>Ctrl</kbd>+<kbd>Alt</kbd>+<kbd>Esc</kbd> to refresh Cinnamon, or log out and in.

## Undo

```
curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-vscodium/install-yrs-vscodium.sh | sh -s -- --undo
```

## What it does

- Puts the icon in `~/.local/share/icons/hicolor` as `yrs-vscodium`, at ten sizes from 16 to 512 pixels: a name of its own, so no icon theme can override it.
- Gives VSCodium's launcher a personal copy in `~/.local/share/applications`, identical except for the icon: `codium.desktop` (.deb), `com.vscodium.codium.desktop` (Flatpak) and the Snap's `codium_codium.desktop`. Mint's icon theme has its own VSCodium icon, which is why the new one has a name of its own. Yours is used before theirs.
- If a launcher of your own starts VSCodium with another icon, it is pointed at the new one too.
- Anything it replaces is copied to `~/.local/share/yrs-vscodium` first, and undo puts it back.
- No root, nothing downloaded, VSCodium itself is not changed.

VSCodium and its logo belong to the VSCodium project; this is an unofficial fan icon.

## Build

```
python3 make.py
```

paints the icon with [`source/vsc.py`](source/vsc.py) from the official [`source/logo.svg`](source/logo.svg) and the shared tools in [`source/paint.py`](source/paint.py) (numpy, scipy and Pillow), every shape drawn as a signed distance so its edges stay crisp at any size, then scales it to every size in `icons/` and packs them into `install-yrs-vscodium.sh`. `python3 make.py --pack` packs `icons/` as it is.
