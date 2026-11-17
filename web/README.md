# yrs. in the browser

The same yrs., the same design and the same maths, in any browser: **[jrypzm.github.io/yrs/web](https://jrypzm.github.io/yrs/web/)**

Enter your birthday and that's it. Add it to your Home Screen from your browser's Share or menu button and it opens full screen and works offline. On iPhone this is the way to have yrs.

Browsers can't add widgets or change your wallpaper on their own, so here you save the lock screen once from Settings. Everything you enter stays in your browser's storage; nothing is sent anywhere.

## Inside

```
index.html            the whole app: vanilla JavaScript and canvas, no build step
manifest.webmanifest  install name, colours and icons
sw.js                 the service worker that keeps it working offline
icons/, splash/       app icons and iPhone launch screens
```

To work on it, serve the repository root with any static server (for example `python3 -m http.server`) and open `/web/`.
