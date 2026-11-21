#!/bin/sh
# Runs the simulator build on a simulated iPhone SE (the iPhone 6s screen), with a few songs,
# takes screenshots of the list and the player, and measures memory and CPU while it plays.
set -eu
cd "$(dirname "$0")/build"
RT=$(xcrun simctl list runtimes -j | python3 -c "import json,sys; r=[x for x in json.load(sys.stdin)['runtimes'] if x['platform']=='iOS' and x['isAvailable']]; print(r[-1]['identifier'])")
# the iPhone 6s screen size if this runtime has it (iPhone SE), else any iPhone it supports
UDID=""
for DT in $(xcrun simctl list devicetypes -j | python3 -c "
import json,sys
ids=[x['identifier'] for x in json.load(sys.stdin)['devicetypes'] if '.iPhone-' in x['identifier']]
se=[i for i in ids if 'iPhone-SE' in i]
print(' '.join(list(reversed(se)) + [i for i in reversed(ids) if i not in se]))"); do
    if UDID=$(xcrun simctl create yrs "$DT" "$RT" 2>/dev/null); then echo "runtime $RT, device $DT"; break; fi
done
[ -n "$UDID" ] || { echo "no simulator device fits $RT"; exit 1; }
xcrun simctl boot "$UDID"
xcrun simctl bootstatus "$UDID" -b
xcrun simctl install "$UDID" sim/yrsmusic.app
DATA=$(xcrun simctl get_app_container "$UDID" com.yrs.music data)

# a few songs: the Mac reads a line, saved in the formats people have
mkdir -p "$DATA/Documents/Night Drive"
i=0
for name in "Afterglow" "Blue Hour" "Coastline" "Driftwood" "Echoes" "Fireflies" "Golden" "Harbor Lights" "Inland" "July"; do
    say -r 90 -o tmp.aiff "$name. The quiet hour, the long road home, the lights along the water." 
    case $((i % 3)) in
        0) afconvert -f m4af -d aac tmp.aiff "$DATA/Documents/Night Drive/$name.m4a" ;;
        1) afconvert -f m4af -d alac tmp.aiff "$DATA/Documents/$name.m4a" ;;
        2) afconvert -f WAVE -d LEI16 tmp.aiff "$DATA/Documents/$name.wav" ;;
    esac
    i=$((i + 1))
done
rm -f tmp.aiff
printf 'not a song' > "$DATA/Documents/Broken.mp3"

xcrun simctl status_bar "$UDID" override --time 9:41 --batteryState charged --batteryLevel 100 || true
xcrun simctl launch "$UDID" com.yrs.music
sleep 5
xcrun simctl io "$UDID" screenshot list.png
xcrun simctl terminate "$UDID" com.yrs.music
xcrun simctl launch "$UDID" com.yrs.music -demo-player
sleep 6
xcrun simctl io "$UDID" screenshot player.png

# memory and CPU while it plays: with the player open, and with nothing on screen.
# "phys_footprint" is how iOS counts an app's memory (what it judges when memory is short);
# the simulator's plain process size also counts shared system libraries, so it isn't used.
measure() {
    PID=$(pgrep -f "yrsmusic.app/yrsmusic" | head -n 1)
    echo "$1"
    footprint "$PID" 2>/dev/null | grep -iE "phys_footprint|^ *Footprint" | head -n 2 | sed 's/^/  /'
    for n in 1 2 3 4 5 6; do sleep 2; ps -o %cpu= -p "$PID" | awk '{printf "  CPU %s%%\n", $1}'; done
}
{
    echo "yrs. music on a simulated iPhone ($DT, $RT)"
    echo "app size: $(du -k yrs-music.ipa | cut -f1) KB .ipa, $(du -k Payload/yrsmusic.app/yrsmusic | cut -f1) KB program"
    measure "playing, player open:"
    xcrun simctl terminate "$UDID" com.yrs.music
    xcrun simctl launch "$UDID" com.yrs.music -demo-play
    sleep 6
    measure "playing, nothing open (the phone in your pocket):"
} | tee usage.txt
xcrun simctl shutdown "$UDID" || true
