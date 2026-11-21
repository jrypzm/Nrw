#!/var/jb/bin/bash
# yrs. slim: a debloat for jailbroken iPhones on iOS 15 (made for the iPhone 6s, iOS 15.7.6).
# Works on Dopamine and palera1n, rootless or rootful. Needs root and launchctl (Procursus).
# Run it with bash (not fish):   bash yrs-slim.sh
#
# By default it turns off every Apple background service in the groups below and keeps them off
# after reboots, until you undo it. AssistiveTouch, calls, Wi-Fi, the App Store, Find My and the
# rest of what the phone needs are never touched. Nothing is deleted.
#
#   bash yrs-slim.sh                    everything off, for good (until --undo)
#   bash yrs-slim.sh --keep tips,siri   everything off except those groups
#   bash yrs-slim.sh --safe             only the groups you'd never notice
#   bash yrs-slim.sh --temp             only until the next reboot
#   bash yrs-slim.sh --undo             turn everything back on (then reboot)
#   bash yrs-slim.sh --list             show what each group stops, change nothing
set -u

# ---------- the groups ----------

# Always stopped: none of these does anything you'd notice.
CRASH="com.apple.ReportCrash com.apple.ReportCrash.Jetsam com.apple.ReportMemoryException com.apple.ReportSystemMemory
com.apple.OTACrashCopier com.apple.ProxiedCrashCopier com.apple.ProxiedCrashCopier.ProxyingDevice com.apple.crash_mover
com.apple.crashreportcopymobile com.apple.DumpBasebandCrash com.apple.DumpPanic com.apple.coresymbolicationd
com.apple.analyticsd com.apple.wifianalyticsd com.apple.rtcreportingd com.apple.aslmanager com.apple.syslogd
com.apple.logd_reporter com.apple.logd_reporter.report_statistics com.apple.signpost.signpost_reporter
com.apple.pluginkit.pkreporter com.apple.hangreporter com.apple.hangtracerd com.apple.spindump com.apple.tailspind"
DIAG="com.apple.diagnosticd com.apple.diagnosticextensionsd com.apple.diagnosticservicesd com.apple.diagnosticspushd
com.apple.symptomsd-diag com.apple.sysdiagnose com.apple.sysdiagnose_helper com.apple.sysdiagnose.darwinos"
UPDATE="com.apple.mobile.softwareupdated com.apple.softwareupdateservicesd com.apple.OTATaskingAgent com.apple.mobile.NRDUpdated"
TRACKING="com.apple.UsageTrackingAgent com.apple.followupd com.apple.atc.atwakeup com.apple.racoon
com.apple.wapic com.apple.wifi.wapic"
TIPS="com.apple.tipsd"
SAFE_GROUPS="CRASH DIAG UPDATE TRACKING TIPS"

# Also off unless you pass --safe. Each line says what stops working.
SIRI="com.apple.assistantd com.apple.assistant_service com.apple.voiced"
SPOTLIGHT="com.apple.searchd com.apple.corespotlightservice com.apple.spotlight.IndexAgent com.apple.spotlightknowledged com.apple.spotlightknowledged.updater"
SMART="com.apple.suggestd com.apple.parsecd com.apple.knowledge-agent com.apple.duetexpertd com.apple.photoanalysisd com.apple.mediaanalysisd"
SCREENTIME="com.apple.ScreenTimeAgent com.apple.familycircled com.apple.familynotification com.apple.asktod"
GAMECENTER="com.apple.gamed"
HEALTH="com.apple.healthd"
WALLET="com.apple.passd"
HOME="com.apple.homed"
WATCH="com.apple.nanotimekitcompaniond"
PRINT="com.apple.printd"
HOTSPOT="com.apple.MobileInternetSharing"
MAX_GROUPS="SIRI SPOTLIGHT SMART SCREENTIME GAMECENTER HEALTH WALLET HOME WATCH PRINT HOTSPOT"

about() {
    case $1 in
        CRASH) echo "crash reports, analytics, logging uploads (nothing you use)" ;;
        DIAG) echo "diagnostics and sysdiagnose (nothing you use)" ;;
        UPDATE) echo "software update: no update downloads or nags, so no accidental update that loses the jailbreak" ;;
        TIPS) echo "Tips notifications (the Tips app itself, and a TrollStore helper installed in it, still open)" ;;
        TRACKING) echo "usage tracking, 'finish setting up' badges, VPN (IPsec racoon), China WAPI Wi-Fi" ;;
        SIRI) echo "Siri and Dictation stop working" ;;
        SPOTLIGHT) echo "swipe-down search stops finding things" ;;
        SMART) echo "Siri Suggestions, app suggestions, People and Memories in Photos stop updating" ;;
        SCREENTIME) echo "Screen Time and Family Sharing stop" ;;
        GAMECENTER) echo "Game Center sign-in and achievements stop" ;;
        HEALTH) echo "Health app and step counting stop" ;;
        WALLET) echo "Wallet and Apple Pay stop" ;;
        HOME) echo "Home app (HomeKit) stops" ;;
        WATCH) echo "Apple Watch faces stop syncing" ;;
        PRINT) echo "AirPrint stops" ;;
        HOTSPOT) echo "Personal Hotspot stops" ;;
    esac
}

# Never touched, whatever is passed: the phone needs these (or they protect it).
NEVER=" com.apple.SpringBoard com.apple.backboardd com.apple.logd com.apple.trustd com.apple.watchdogd com.apple.thermalmonitord
com.apple.locationd com.apple.securityd com.apple.lsd com.apple.cfprefsd.xpc.daemon com.apple.configd com.apple.mDNSResponder
com.apple.wifid com.apple.CommCenter com.apple.runningboardd com.apple.dasd com.apple.mediaserverd com.apple.mobile.installd
com.apple.itunesstored com.apple.appstored com.apple.apsd com.apple.keybagd com.apple.fseventsd com.apple.itunescloudd
com.apple.mobileactivationd com.apple.assistivetouchd com.apple.imagent com.apple.identityservicesd "

# ---------- options ----------

MODE=stop; MAX=1; PERSIST=1; KEEP=""
while [ $# -gt 0 ]; do
    case $1 in
        --max) MAX=1 ;;
        --persist) PERSIST=1 ;;
        --safe) MAX=0 ;;
        --temp) PERSIST=0 ;;
        --undo) MODE=undo ;;
        --list) MODE=list ;;
        --keep) shift; KEEP=$(echo "${1:-}" | tr 'a-z,' 'A-Z ') ;;
        --keep=*) KEEP=$(echo "${1#--keep=}" | tr 'a-z,' 'A-Z ') ;;
        -h|--help) sed -n '2,17p' "$0" 2>/dev/null || echo "see the top of the script"; exit 0 ;;
        *) echo "unknown option: $1 (try --help)"; exit 1 ;;
    esac
    shift
done

SETS="$SAFE_GROUPS"
if [ $MAX = 1 ] || [ $MODE != stop ]; then SETS="$SAFE_GROUPS $MAX_GROUPS"; fi
NEVER=" $(echo $NEVER) "

if [ $MODE = list ]; then
    for g in $SETS; do
        case " $SAFE_GROUPS " in *" $g "*) w="always" ;; *) w="most  " ;; esac
        echo "[$w] $(echo $g | tr 'A-Z' 'a-z'): $(about $g)"
        eval "echo \"\$$g\"" | tr ' ' '\n' | sed '/^$/d; s/^/          /'
    done
    exit 0
fi

# ---------- checks ----------

[ "$(id -u)" = 0 ] || { echo "Run it as root: sudo bash yrs-slim.sh   (the default sudo password on a fresh jailbreak is often 'alpine' unless you set one)"; exit 1; }
PATH="/var/jb/usr/bin:/var/jb/bin:/var/jb/usr/sbin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"; export PATH
command -v launchctl >/dev/null 2>&1 || { echo "launchctl not found. Install it from Sileo/Zebra (package: launchctl, from Procursus)."; exit 1; }
V=$(sw_vers -productVersion 2>/dev/null || sysctl -n kern.osproductversion 2>/dev/null || echo "?")
case $V in 15.*|"?") ;; *) echo "This is for iOS 15; this phone runs iOS $V. Stopping to be safe."; exit 1 ;; esac
MOBILE=$(id -u mobile 2>/dev/null || echo 501)

kb_of() {   # pid -> resident memory in KB
    [ -n "$1" ] && [ "$1" != "-" ] && [ "$1" != 0 ] && ps -o rss= -p "$1" 2>/dev/null | tr -d ' '
}
pid_of() {  # label -> pid if running, in the system domain or the phone user's
    launchctl list 2>/dev/null | awk -v l="$1" '$3==l && $1!="-" {print $1; exit}'
}
free_mb() {
    p=$(sysctl -n vm.page_free_count 2>/dev/null); s=$(sysctl -n hw.pagesize 2>/dev/null)
    [ -n "$p" ] && [ -n "$s" ] && echo $((p * s / 1048576))
}

# ---------- do it ----------

stopped=0; freed=0; absent=0
before=$(free_mb)
for g in $SETS; do
    case " $KEEP " in *" $g "*) echo "keeping $(echo $g | tr 'A-Z' 'a-z')"; continue ;; esac
    [ $MODE = stop ] && echo "• $(echo $g | tr 'A-Z' 'a-z'): $(about $g)"
    for l in $(eval "echo \$$g"); do
        case "$NEVER" in *" $l "*) echo "  skipping $l (protected)"; continue ;; esac
        if [ $MODE = undo ]; then
            launchctl enable "system/$l" 2>/dev/null
            launchctl enable "user/$MOBILE/$l" 2>/dev/null
            launchctl enable "gui/$MOBILE/$l" 2>/dev/null
            continue
        fi
        pid=$(pid_of "$l"); kb=$(kb_of "$pid")
        ok=0
        for d in system "user/$MOBILE" "gui/$MOBILE"; do
            [ $PERSIST = 1 ] && launchctl disable "$d/$l" 2>/dev/null
            launchctl bootout "$d/$l" >/dev/null 2>&1 && ok=1
        done
        if [ $ok = 1 ]; then
            stopped=$((stopped + 1)); freed=$((freed + ${kb:-0}))
            echo "  stopped $l${kb:+ (${kb} KB)}"
        else
            absent=$((absent + 1))
        fi
    done
done

if [ $MODE = undo ]; then
    echo
    echo "Everything is turned back on for future boots. Reboot the phone now to start them again."
    exit 0
fi

sleep 2
after=$(free_mb)
echo
echo "Stopped $stopped services ($absent were already off or not on this iOS)."
[ $freed -gt 0 ] && echo "Memory they were using: about $((freed / 1024)) MB."
[ -n "$before" ] && [ -n "$after" ] && echo "Free memory: $before MB before, $after MB now."
if [ $PERSIST = 1 ]; then
    echo "They stay off after reboots too. To bring them back: bash yrs-slim.sh --undo, then reboot."
else
    echo "Until the next reboot. Run it again after you re-jailbreak (leave out --temp to make it stick)."
fi
