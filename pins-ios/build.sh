#!/bin/sh
# Builds build/yrs-pins.ipa for iPhone (iOS 15 and later, so the iPhone 6s too).
# Needs a Mac with Xcode; GitHub Actions does this on every push (.github/workflows/pins-ios.yml).
#   sh build.sh               the .ipa
#   sh build.sh --simulator   also an app for the iOS simulator (for screenshots)
set -eu
cd "$(dirname "$0")"
rm -rf build
mkdir -p build

app() {   # sdk target dir [sources name id]
    src="${4:-Sources/*.swift}"; name="${5:-yrspins}"; id="${6:-com.yrs.pins}"
    a="$3/$name.app"
    mkdir -p "$a"
    # -Osize: the smallest code; no libraries, so the app is just this
    xcrun -sdk "$1" swiftc -target "$2" -Osize -wmo -parse-as-library $src -o "$a/$name"
    xcrun strip -x "$a/$name" 2>/dev/null || true
    cp Info.plist "$a/Info.plist"
    plutil -replace CFBundleExecutable -string "$name" "$a/Info.plist"
    plutil -replace CFBundleIdentifier -string "$id" "$a/Info.plist"
    plutil -insert DTPlatformName -string "$1" "$a/Info.plist"
    plutil -insert DTSDKName -string "$1$(xcrun -sdk "$1" --show-sdk-version)" "$a/Info.plist"
    plutil -insert DTPlatformVersion -string "$(xcrun -sdk "$1" --show-sdk-version)" "$a/Info.plist"
    plutil -convert binary1 "$a/Info.plist"
    cp Icons/*.png "$a/"
    printf 'APPL????' > "$a/PkgInfo"
    # signed ad hoc: sideloading tools sign it again with your Apple ID; TrollStore takes it as is
    codesign -f -s - --timestamp=none "$a"
}

app iphoneos arm64-apple-ios15.0 build/Payload
(cd build && zip -qry yrs-pins.ipa Payload)
echo "build/yrs-pins.ipa: $(du -k build/yrs-pins.ipa | cut -f1) KB"

if [ "${1:-}" = "--simulator" ]; then
    app iphonesimulator "$(uname -m)-apple-ios15.0-simulator" build/sim
    # an empty app, to measure what iOS itself costs every app with a screen
    app iphonesimulator "$(uname -m)-apple-ios15.0-simulator" build/sim Baseline/Empty.swift empty com.yrs.empty
fi
