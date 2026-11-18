# yrs. folders for Linux Mint

A hundred folders made of things: denim, leather, marble with gold, felt with a sleepy face, puffer jacket, wood, see-through plastic, brushed metal, corduroy, cork, terrazzo, carbon, velvet, scribbled paper, foil, concrete, tartan, camo and glitter. Every folder in Nemo gets its own, and folders side by side never share one.

![The hundred yrs. folders, and Nemo using them](preview.png)

## Install

```
curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-folders/install-yrs-folders.sh | sh
```

It also sets Nemo's icons to the "large" size so you can see the materials (undo puts your size back). A folder you zoomed yourself keeps its zoom: press <kbd>Ctrl</kbd>+<kbd>0</kbd> there to reset it.

If it says Nemo's Python support is missing, run `sudo apt install nemo-python` and try again.

## Undo

```
curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-folders/install-yrs-folders.sh | sh -s -- --undo
```

## How it works

- The installer downloads the hundred pictures (4.4 MB), checks them against the checksum written inside it, and puts them in `~/.local/share/yrs-folders/designs`.
- A small Nemo plugin, [`yrs-folders.py`](yrs-folders.py), gives each folder Nemo shows a design and sets it as that folder's custom icon (the same thing as Properties → choose an icon). Which folder has which design is kept in `~/.local/share/yrs-folders/chosen.json`, so a folder keeps its look.
- Folders that sit together get different designs, until a place holds more than a hundred folders.
- A folder you already gave your own icon is left alone. Undo removes the plugin and clears only the icons it set.
- No root, and after the install nothing reaches the internet.

The designs show in Nemo's icon view and on the desktop. The sidebar and file dialogs of other apps keep your theme's icons.

## Build

```
python3 make.py
```

draws every design from [`designs/draw.js`](designs/draw.js) in Chromium (Node and Playwright), scales them to 256 pixels into `designs/png`, packs them, and writes `install-yrs-folders.sh` with the pack's checksum. `python3 make.py --pack` packs `designs/png` as it is, without drawing.
