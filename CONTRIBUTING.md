# Contributing to yrs.

Thank you for wanting to make yrs. better. It's made by one person, so every report, idea and fix really counts.

## Ideas and bugs

The best way to help is an issue. Every one is read.

- **[Report a bug](https://github.com/jrypzm/yrs/issues/new?template=bug.yml)**: which app, what happened, what you expected, and your device.
- **[Suggest an idea](https://github.com/jrypzm/yrs/issues/new?template=idea.yml)**: what it would let you do or feel.
- **Security or privacy problems**: please report them privately, see [SECURITY.md](SECURITY.md).

## Pull requests

Pull requests are welcome. For anything bigger than a small fix, open an issue first so we can agree on it before you spend the time.

1. Fork the repository and make a branch.
2. Work inside one app's folder; each one builds on its own (see below).
3. Run that app's tests, and look at the screens they render if you changed anything visible.
4. Open the pull request with a short note on what changed and why, and a screenshot for visual changes.

### Building and testing

| App | Folder | Build | Test |
|---|---|---|---|
| yrs. for Android | `android/` | `gradle assembleRelease` | `gradle :app:recordPaparazziDebug -Pshots` |
| yrs. for Linux | `linux/` | `./build-appimage.sh` | `cargo run --release` |
| yrs. in the browser | `web/` | none | open it in a browser |
| yrs. home | `home/` | `gradle assembleRelease` | `gradle testDebugUnitTest` |
| yrs. music | `music/` | `gradle assembleRelease` | `gradle testDebugUnitTest` |

The Android apps need JDK 17 and Gradle 8.11 or newer. Screen tests write PNGs to `app/build/screens` (or Paparazzi's snapshot folder), so you can see every page without a phone.

## What fits yrs.

- **Calm.** True black, white words, at most one colour. No badges, gradients or bright buttons.
- **Honest.** Numbers are exact, settings do what they say, and nothing pretends.
- **Private.** Nothing leaves the device. No network, accounts or analytics, and no new permissions without a very good reason.
- **Small.** One idea per screen. If it needs an explanation, it isn't finished.
- **Words over icons.** Plain language, written like a person talks.

## Licences

By contributing you agree that your work is released under the licence of the folder you changed:

- `music/` is **GPL-3.0**, because yrs. music is made from [Lotus](https://github.com/dn0ne/lotus) by dn0ne. Mark files you change there with a short note.
- Everything else is **MIT**. You're also free to take that code and build something of your own from it; keeping the licence notice is all it asks.
