# Pure black Linux Mint

Every grey Mint draws, in #000000 with white text: app windows, Settings and the other utilities, title bars, the desktop's right-click menu, the panel, its menus and popups.

## Install

```
curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-black/install-yrs-black.sh | sh
```

Apps that were already open turn black when you open them again; log out and in to see everything at once.

## Undo

```
curl -fsSL https://github.com/jrypzm/yrs/releases/download/mint-black/install-yrs-black.sh | sh -s -- --undo
```

## What it does

- Copies your current theme to `~/.themes/<your theme>-Black`. If you use a light Mint theme it starts from its dark version, so text stays light on black.
- Adds one block of colours at the end of the copy: GTK 3 and GTK 4 apps (windows, dialogs, sidebars, lists, menus, popovers, buttons, fields), the title bars (Mint's window borders read their colours from the theme) and the Cinnamon panel, menus, popups and notifications.
- The newest GTK 4 apps ignore themes, so the same colours go into `~/.config/gtk-4.0/gtk.css`; a file of your own there is kept and comes back on undo.
- Switches Mint to the copy (applications, window borders, desktop) and asks apps for their dark style. What you had before is saved in `~/.local/share/yrs-black`, and undo puts it back exactly.
- No root, nothing downloaded, your original themes are not touched. The desktop keeps its wallpaper.

Apps that draw themselves (browsers, Electron apps such as VS Code or Discord, LibreOffice's documents) keep their own colours.
