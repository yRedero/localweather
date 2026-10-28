#!/usr/bin/env bash
# Walks a production copy (prodscene.py --no-scene) left and right with real key presses, like a player, saving F2
# frames, then scores row flicker in a screen region (tools/rowscore.py). Unlike the dev strafe scene this includes
# view bobbing, which is what showed Voxy's banding (see tools/README.md).
#
#   tools/live/walktest.sh <profile dir> <world dir> <run dir> <name> [prodscene options...]
#   e.g. walktest.sh "$APPDATA/ModrinthApp/profiles/X" ".../saves/3" runs/a bob_on --size 2560x1440 --bob true
#
# Set ROWSCORE_REGION="x0 y0 x1 y1" for the area to score (default: tools/rowscore.py's).
set -e
L="$(cd "$(dirname "$0")" && pwd)"
profile=$1; world=$2; run=$3; name=$4; shift 4
mkdir -p "$run"
rm -f "$run/launch.log"
python -I "$L/prodscene.py" "$profile" "$world" "$run" --no-scene "$@" > "$run/$name.out" 2>&1 &
for i in $(seq 1 180); do
    grep -qE "Local weather active|joined the game|logged in with entity id" "$run/launch.log" 2>/dev/null && break
    sleep 2
done
sleep 25  # chunks and LOD terrain settle
game=$(cygpath -w "$(cd "$run" && pwd)/game" | sed 's/\\/\\\\/g')
pid=$(powershell.exe -NoProfile -Command "(Get-CimInstance Win32_Process -Filter \"name='javaw.exe' or name='java.exe'\" | Where-Object { \$_.CommandLine -like '*$(basename "$(cd "$run" && pwd)")*game*' -and \$_.CommandLine -match 'KnotClient' }).ProcessId" | tr -d '\r' | head -1)
[ -n "$pid" ] || { echo "game process not found"; exit 1; }
stamp=$(date +%s)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$L/mcdrive.ps1")" -ProcessId "$pid" -Steps "wait:500","tap:F1","wait:800" | tr -d '\r'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$L/strafe.ps1")" -ProcessId "$pid" | tr -d '\r'
sleep 3
powershell.exe -NoProfile -Command "Stop-Process -Id $pid -Force"
out="$run/$name"
rm -rf "$out"; mkdir -p "$out"
python -I - "$run/game/screenshots" "$out" "$stamp" <<'EOF'
import glob, os, shutil, sys
src, out, stamp = sys.argv[1], sys.argv[2], float(sys.argv[3])
fs = sorted((f for f in glob.glob(src + '/*.png') if os.path.getmtime(f) > stamp and 'strafe_' not in f), key=os.path.getmtime)
for i, f in enumerate(fs):
    shutil.copy(f, os.path.join(out, 'strafe_%03d.png' % i))
print(len(fs), 'frames')
EOF
python -I "$L/../rowscore.py" "$out" $ROWSCORE_REGION
