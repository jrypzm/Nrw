# yrs.

Your life, in dots. Pure black, white dots for years lived, dim dots for years left; the year you're in fills as it passes.

- Website: `index.html` at the repo root (GitHub Pages) → https://jrypzm.github.io/yrs./
- iPhone app: `web/` → https://jrypzm.github.io/yrs./web/ — open in Safari, Share → **Add to Home Screen**.
  It runs full screen and offline, uses the iPhone's own SF Pro, and keeps everything in `localStorage` on the device.
- Android app: `android/` (below).

## iPhone web app (`web/`)

A single-file port of the Android app (`web/index.html`, no build step):

- Onboarding: birthday wheels, lifespan, a reveal of your grid.
- Five pages you swipe between: Life (pinch or tap to zoom History → Years → Months → Weeks → Days), This decade,
  This year (a line a day, "on this day", countdowns), People (time left together) and the Ledger.
- The headline counts in the zoom's own unit; on Years, tap it to count in days, weeks, summers, weekends or full moons;
  hold it to feel the seconds. Left/Lived and % toggles; Classic, Large and Live number styles.
- Hold a dot and drag to scrub through time; tap a year to name it or seal a letter to it (opens on that birthday);
  a review sheet after each birthday.
- Lock-screen wallpapers (Grid, Daylight, Hourglass, Stars) exported at the phone's resolution, and a share card.
- Launch screens for current iPhones in `web/splash/`, icons in `web/icons/`, offline cache in `web/sw.js`.

## Android app

Native Kotlin app in `android/` (Jetpack Compose, Glance widgets, live wallpaper, WorkManager).
GitHub Actions (`.github/workflows/android.yml`) builds and signs it on every push that touches `android/`,
and publishes the APK as a GitHub Release (`years-1.0.N`). The signing key `android/app/years-release.jks`
is a personal sideload key kept in the repo so each build installs over the previous one.
