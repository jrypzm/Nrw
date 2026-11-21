#!/bin/sh
# Runs the simulator build on a simulated iPhone SE (the iPhone 6s screen), with a few songs,
# takes screenshots of the list and the player, and measures memory and CPU while it plays.
set -eu
cd "$(dirname "$0")/build"
RT=$(xcrun simctl list runtimes -j | python3 -c "import json,sys; r=[x for x in json.load(sys.stdin)['runtimes'] if x['platform']=='iOS' and x['isAvailable']]; print(r[-1]['identifier'])")
DT=$(xcrun simctl list devicetypes -j | python3 -c "import json,sys; d=[x['identifier'] for x in json.load(sys.stdin)['devicetypes'] if 'iPhone-SE' in x['identifier']]; print(d[-1])")
echo "runtime $RT, device $DT"
UDID=$(xcrun simctl create yrs "$DT" "$RT")
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

# memory and CPU of the app while it plays, with the player open
PID=$(pgrep -f "yrsmusic.app/yrsmusic" | head -n 1)
{
    echo "yrs. music on a simulated iPhone, playing:"
    for n in 1 2 3 4 5; do ps -o rss=,%cpu= -p "$PID" | awk '{printf "  memory %.1f MB, CPU %s%%\n", $1/1024, $2}'; sleep 2; done
} | tee usage.txt
xcrun simctl shutdown "$UDID" || true
