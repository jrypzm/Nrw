# yrs. music

An offline music player for Android 8 or newer, in the yrs. look: pure black, white words, one colour borrowed from the cover.

It plays the songs saved on your phone. It has no internet permission and keeps nothing anywhere but the phone.

- **Songs, Albums, Playlists.** Three words at the top, nothing else. Songs is home; pin up to six albums or playlists above it.
- **The player.** A square cover that grows out of the playing strip on a spring, a thin line for time, three symbols, and words for Shuffle, Lyrics, Up next and Repeat. The file's format and quality sit at the bottom.
- **Lyrics.** Synced lyrics from the song's own tag or from a file you choose, the current line in white.
- **Up next.** Drag to reorder, swipe to remove, Clear to empty.
- **Loved.** Tap the heart; the song lands in Loved.
- **Settings.** Only a few, all real: folders, short files, colour from the cover, lyrics size.

Download: [yrs-music.apk](https://github.com/jrypzm/yrs/releases/download/music/yrs-music.apk). It installs next to Lotus without replacing it.

On Linux there's [yrs. music for Linux](../music-linux/), a separate app in the same look: [yrs-music-x86_64.AppImage](https://github.com/jrypzm/yrs/releases/download/music-linux/yrs-music-x86_64.AppImage).

## Built on Lotus

yrs. music is a modified version of [Lotus](https://github.com/dn0ne/lotus) by dn0ne. The playback engine, library reading, playlists and lyrics storage come from Lotus; the interface is new. Changes from Lotus:

- a new interface (`app/src/main/java/com/dn0ne/player/ui`), new icon and name;
- no network code: online lyrics and MusicBrainz lookups are removed, and the internet permission with them;
- shuffle and repeat can be set separately, Play next lands after the current song, and the library re-reads as soon as folders change.

Lotus and yrs. music are free software under the GNU General Public License, version 3. See [LICENSE.md](LICENSE.md).

## Build

```
cd music
gradle assembleRelease      # app/build/outputs/apk/release/yrs-music-release.apk
gradle testDebugUnitTest    # renders every screen into app/build/screens
```
