#!/bin/sh
# Runs the simulator build on a simulated iPhone SE (the iPhone 6s screen), takes a screenshot,
# and measures the app's memory and CPU next to an empty app.
set -eu
cd "$(dirname "$0")/build"
RT=$(xcrun simctl list runtimes -j | python3 -c "import json,sys; r=[x for x in json.load(sys.stdin)['runtimes'] if x['platform']=='iOS' and x['isAvailable']]; print(r[-1]['identifier'])")
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
xcrun simctl install "$UDID" sim/yrspins.app
xcrun simctl install "$UDID" sim/empty.app
xcrun simctl status_bar "$UDID" override --time 9:41 --batteryState discharging --batteryLevel 64 || true

pid_of() { pgrep -f "$1.app/$1" | head -n 1; }
secs() { ps -o time= -p "$1" | awk -F: '{ if (NF==3) print $1*3600+$2*60+$3; else print $1*60+$2 }'; }
measure() {   # label program
    sleep 7
    PID=$(pid_of "$2")
    printf '%-40s' "$1"
    footprint "$PID" 2>/dev/null | awk '/phys_footprint:/ {printf "memory %s %s", $2, $3}'
    a=$(secs "$PID"); sleep 10; b=$(secs "$PID")
    echo "   CPU $(echo "scale=2; ($b - $a) * 10" | bc)% of one core over 10 s"
}
{
    echo "yrs. pins on a simulated iPhone ($DT, $RT)"
    echo "app size: $(du -k yrs-pins.ipa | cut -f1) KB .ipa, $(du -k Payload/yrspins.app/yrspins | cut -f1) KB program"
    echo
    xcrun simctl launch "$UDID" com.yrs.empty >/dev/null
    measure "an empty app (the floor iOS sets):" empty
    xcrun simctl terminate "$UDID" com.yrs.empty
    for st in "home|" "search|search:wallpaper" "pin|pin:cats" "recipes|search:recipes"; do
        name=${st%%|*}; demo=${st#*|}
        if [ -n "$demo" ]; then xcrun simctl launch "$UDID" com.yrs.pins -demo "$demo" >/dev/null; else xcrun simctl launch "$UDID" com.yrs.pins >/dev/null; fi
        sleep 6
        xcrun simctl io "$UDID" screenshot "$name.png" >/dev/null
        measure "$name:" yrspins
        xcrun simctl terminate "$UDID" com.yrs.pins
    done
} | tee usage.txt
xcrun simctl shutdown "$UDID" || true
