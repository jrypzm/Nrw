# yrs. for Linux

Your life, in dots, on the desktop. One AppImage of about 5 MB for Fedora, Ubuntu, Arch and other current desktops, on Wayland or X11.

**[Download yrs-x86_64.AppImage](https://github.com/jrypzm/yrs/releases/download/linux/yrs-x86_64.AppImage)**

1. Make it executable: right-click it, Properties, Permissions, **Is executable**. Or `chmod +x yrs-x86_64.AppImage`.
2. Open it. yrs. adds itself to your app launcher with its icon, so you can pin it to the taskbar.
3. On KDE Plasma it can also set your wallpaper and lock screen, sit in the tray and start at login.

## Use

```
yrs               open the window
yrs --line        open straight to today's line
yrs --background  no window: the tray, the daily wallpaper and the Monday note (started at login)
```

## Inside

Rust and [egui](https://github.com/emilk/egui), drawn by hand. It starts instantly and idles at zero CPU. The tray, notifications and the Plasma wallpaper go through standard desktop services, and everything is stored in your home folder.

```
src/
  main.rs         entry point and command line
  app.rs, ui.rs   the window and its pages
  life.rs         the calendar maths
  paint.rs        the dots and type
  store.rs        settings and data on disk
  system.rs       tray, notifications, wallpaper, autostart
```

## Build

Rust stable:

```
cd linux
cargo run --release        # run it
./build-appimage.sh        # dist/yrs-x86_64.AppImage
```

CI ([`linux.yml`](../.github/workflows/linux.yml)) builds the AppImage and publishes it to the `linux` release.
