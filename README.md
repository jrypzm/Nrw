# Years

A minimal web app (PWA) that shows how many years you have left, as a grid of dots.
Pure black (OLED) background, white dots for years lived, dim dots for years remaining.
The current year fills up as it passes.

- **Three views**: swipe sideways (or tap the page dots) for Life, This decade, and This year of life.
- **Number styles**: Classic, Large (thin hero number) and Live (ticking decimals).
- **Touch**: drag across dots to travel through time; tap a year, then tap its label to name it (milestones get a ring).
- **Feel**: Daylight dims the dots at night; a ripple runs through your years each time you open the app; birthday greeting.
- **Lock screen wallpaper**: export Quiet grid, Daylight, Hourglass or Constellation at your screen's resolution.

Edit `tools/index.src.html`, then run `python3 tools/build.py` to regenerate `index.html`.

- Open `index.html` (or host the folder on any static host, e.g. GitHub Pages).
- On your phone: browser menu → **Add to Home Screen** to use it full-screen and offline.
- Birthday and lifespan (default 80) are stored only on the device (`localStorage`).

## Next: native app (planned)
- Bundle SF Pro (Display Semibold/Bold, Text Regular/Medium/Semibold) inside the app build.
  For the web version, `tools/embed_fonts.py` can inline them into `index.html` once the font files are supplied.
- Home-screen / lock-screen widget showing the years grid.
