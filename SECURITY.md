# Security

The yrs. apps keep everything on your device. yrs., yrs. home and yrs. music for Android have no internet permission. The web app stores its data only in your browser, and the Linux apps only in your home folder.

## Reporting a problem

If you find a security or privacy problem in any of the apps or the website, please don't open a public issue. Use **[private vulnerability reporting](https://github.com/jrypzm/yrs/security/advisories/new)** instead, with:

- which app and version (shown in its Settings, or the release you downloaded),
- the steps to reproduce it,
- what you think the impact is.

You'll get a reply as soon as possible, and credit in the release notes if you'd like it.

## Supported versions

Only the newest build of each app receives fixes:

| App | Newest build |
|---|---|
| yrs. for Android | [latest release](https://github.com/jrypzm/yrs/releases/latest) |
| yrs. for Linux | [`linux` release](https://github.com/jrypzm/yrs/releases/tag/linux) |
| yrs. home | [`home` release](https://github.com/jrypzm/yrs/releases/tag/home) |
| yrs. music | [`music` release](https://github.com/jrypzm/yrs/releases/tag/music) |
| yrs. music for Linux | [`music-linux` release](https://github.com/jrypzm/yrs/releases/tag/music-linux) |
| Web app and website | [jrypzm.github.io/yrs](https://jrypzm.github.io/yrs/) |

## Release signing

Every APK is built and signed by GitHub Actions from this repository. The signing keys live in the repository, so builds need no secrets and anyone can reproduce them. That also means a signature only shows an APK was built from code like this, not who built it: install only from the release links above.
