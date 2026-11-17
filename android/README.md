<picture>
  <source media="(prefers-color-scheme: dark)" srcset="../.github/assets/hero-dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="../.github/assets/hero-light.svg">
  <img alt="yrs. — Your life, in dots. Every year you've lived, and every year still ahead." src="../.github/assets/hero-dark.svg" width="100%">
</picture>

# yrs. for Android

Every year you've lived and every year still ahead, one dot each. Android 10 or newer, no internet permission.

**[Download yrs.apk](https://github.com/jrypzm/yrs/releases/latest/download/yrs.apk)** · also on [Linux](../linux/) and [in your browser](https://jrypzm.github.io/yrs/web/)

<p align="center"><img alt="Every year, one dot. White for the years behind you, dark for the years ahead; the one you're in fills a little every day. Count in years, weeks, days, summers, weekends or full moons, or watch it tick live." src="../.github/assets/tile-life.png" width="100%"></p>
<p align="center"><img alt="From all of history to a single day: pinch through History, Years, Months, Weeks and Days. The headline always counts in what you see." src="../.github/assets/tile-zoom.png" width="100%"></p>
<p align="center"><img alt="Write to the you of later: name a year, seal a letter that opens on that birthday, and write one line a day." src="../.github/assets/tile-letters.png" width="100%"></p>
<p align="center"><img alt="Know what's left, of everything: visits left with the people you love, and where your remaining years go." src="../.github/assets/tile-people.png" width="100%"></p>
<p align="center"><img alt="See it before anything else: four lock screens, Grid, Daylight, Hourglass and Stars, redrawn every day on Android." src="../.github/assets/tile-lock.png" width="100%"></p>
<p align="center"><img alt="Seven widgets, one idea each: Life, Number, Decade, This year, Days, Today and People." src="../.github/assets/tile-widgets.png" width="100%"></p>
<p align="center"><img alt="No account. No cloud. Nothing leaves your phone. The Android app has no internet permission." src="../.github/assets/tile-privacy.png" width="100%"></p>

## How it counts

Age is calendar-exact, down to 29 February birthdays. A year holds 52.1775 weeks and a day is a real calendar day, so every zoom level agrees with every other. A heading always counts in the unit it shows.

## Inside

Kotlin and Jetpack Compose, Glance for the widgets, WorkManager to redraw the lock screen each day, and a live wallpaper service. Everything is stored on the phone.

```
app/src/main/java/app/years/
  MainActivity.kt         entry point
  Home.kt, Life.kt, Grid.kt   the main screen and the dots
  Pages.kt, Sheets.kt     Decade, Year, People, Time and their sheets
  Widgets.kt              the seven Glance widgets
  Render.kt, Work.kt      lock screen images and the daily redraw
  YearsWallpaperService.kt   the live wallpaper
  Onboarding.kt, Tour.kt  first open
```

## Build

JDK 17 and Gradle 8.11:

```
cd android
gradle assembleRelease                          # app/build/outputs/apk/release/
gradle :app:recordPaparazziDebug -Pshots        # render every screen with Paparazzi
```

CI ([`android.yml`](../.github/workflows/android.yml)) builds and signs the APK, publishes it as the latest release, and attaches the rendered screens.
