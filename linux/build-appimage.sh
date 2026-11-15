#!/bin/bash
# Builds dist/yrs-x86_64.AppImage: one file that runs on Fedora and other modern Linux desktops.
set -euo pipefail
cd "$(dirname "$0")"
cargo build --release
rm -rf dist/AppDir && mkdir -p dist/AppDir/usr/bin dist/AppDir/usr/share/applications
cp target/release/yrs dist/AppDir/usr/bin/yrs
for n in 16 22 24 32 48 64 128 256 512; do
  mkdir -p dist/AppDir/usr/share/icons/hicolor/${n}x${n}/apps
  cp assets/icons/yrs-$n.png dist/AppDir/usr/share/icons/hicolor/${n}x${n}/apps/yrs.png
done
cp assets/icons/yrs-256.png dist/AppDir/yrs.png
cp assets/icons/yrs-256.png dist/AppDir/.DirIcon
cat > dist/AppDir/yrs.desktop <<D
[Desktop Entry]
Type=Application
Name=yrs.
GenericName=Life in dots
Comment=Your life, in dots. Every year you've lived, and every year still ahead.
Exec=yrs
Icon=yrs
Terminal=false
Categories=Utility;
StartupWMClass=yrs
D
cp dist/AppDir/yrs.desktop dist/AppDir/usr/share/applications/
cat > dist/AppDir/AppRun <<'R'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/yrs" "$@"
R
chmod +x dist/AppDir/AppRun
TOOL=${APPIMAGETOOL:-}
if [ -z "$TOOL" ]; then
  TOOL=dist/appimagetool
  [ -x "$TOOL" ] || { curl -sSL -o "$TOOL" https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage; chmod +x "$TOOL"; }
fi
ARCH=x86_64 "$TOOL" --appimage-extract-and-run --no-appstream dist/AppDir dist/yrs-x86_64.AppImage
ls -la dist/yrs-x86_64.AppImage
