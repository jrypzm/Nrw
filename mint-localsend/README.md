# A LocalSend icon for Linux Mint

A new icon for [LocalSend](https://localsend.org): its dot as a sleepy milky creature, half-lidded eyes cut right through, inside LocalSend's dashed ring, on a candy-teal tile with a glossy rim.

![The new LocalSend icon](preview.png)

## Install

```
curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-localsend/install-yrs-localsend.sh | sh
```

If you still see the old icon, press <kbd>Ctrl</kbd>+<kbd>Alt</kbd>+<kbd>Esc</kbd> to refresh Cinnamon, or log out and in.

## Undo

```
curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-localsend/install-yrs-localsend.sh | sh -s -- --undo
```

## What it does

- Puts the icon in `~/.local/share/icons/hicolor` as `yrs-localsend`, at ten sizes from 16 to 512 pixels: a name of its own, so no icon theme can override it.
- Gives every LocalSend launcher on the computer a personal copy in `~/.local/share/applications`, identical except for the icon: the Flatpak's `org.localsend.localsend_app.desktop`, the .deb's and the Snap's. Yours is used before theirs.
- If a launcher of your own starts LocalSend with another icon, it is pointed at the new one too.
- Anything it replaces is copied to `~/.local/share/yrs-localsend` first, and undo puts it back.
- No root, nothing downloaded, LocalSend itself is not changed.

LocalSend and its logo belong to the LocalSend project; this is an unofficial fan icon.

## Build

```
python3 make.py
```

paints the icon with [`source/ls.py`](source/ls.py) and the shared tools in [`source/paint.py`](source/paint.py) (numpy, scipy and Pillow), every shape drawn as a signed distance so its edges stay crisp at any size, then scales it to every size in `icons/` and packs them into `install-yrs-localsend.sh`. `python3 make.py --pack` packs `icons/` as it is.
