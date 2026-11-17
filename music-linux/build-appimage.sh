#!/bin/bash
# Builds dist/yrs-music-x86_64.AppImage: one file that runs on Fedora, Ubuntu, Arch and other current desktops.
set -euo pipefail
cd "$(dirname "$0")"
cargo build --release
rm -rf dist/AppDir && mkdir -p dist/AppDir/usr/bin dist/AppDir/usr/share/applications
cp target/release/yrs-music dist/AppDir/usr/bin/yrs-music
for n in 16 22 24 32 48 64 128 256 512; do
  mkdir -p dist/AppDir/usr/share/icons/hicolor/${n}x${n}/apps
  cp assets/icons/yrs-music-$n.png dist/AppDir/usr/share/icons/hicolor/${n}x${n}/apps/yrs-music.png
done
cp assets/icons/yrs-music-256.png dist/AppDir/yrs-music.png
cp assets/icons/yrs-music-256.png dist/AppDir/.DirIcon
cat > dist/AppDir/yrs-music.desktop <<D
[Desktop Entry]
Type=Application
Name=yrs. music
GenericName=Music player
Comment=Your music, offline.
Exec=yrs-music %F
Icon=yrs-music
Terminal=false
Categories=AudioVideo;Audio;Player;
MimeType=audio/flac;audio/x-flac;audio/mpeg;audio/mp3;audio/mp4;audio/x-m4a;audio/aac;audio/ogg;audio/x-vorbis+ogg;audio/wav;audio/x-wav;audio/aiff;audio/x-aiff;
Keywords=music;player;audio;flac;mp3;offline;
StartupWMClass=yrs-music
D
cp dist/AppDir/yrs-music.desktop dist/AppDir/usr/share/applications/
cat > dist/AppDir/AppRun <<'R'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/yrs-music" "$@"
R
chmod +x dist/AppDir/AppRun
TOOL=${APPIMAGETOOL:-}
if [ -z "$TOOL" ]; then
  TOOL=dist/appimagetool
  [ -x "$TOOL" ] || { curl -sSL -o "$TOOL" https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage; chmod +x "$TOOL"; }
fi
ARCH=x86_64 "$TOOL" --appimage-extract-and-run --no-appstream dist/AppDir dist/yrs-music-x86_64.AppImage
ls -la dist/yrs-music-x86_64.AppImage
