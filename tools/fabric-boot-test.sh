#!/usr/bin/env bash
# tools/fabric-boot-test.sh [mod-jar]
#
# Grows Arboretum trees in a real Fabric 26.3 dedicated server and checks them, then restarts
# and checks again:
#
#   1. a fresh world with no arboretum.json: the default config must be created;
#   2. savanna chunks are force-loaded, so world generation runs the tree features;
#   3. one baobab is grown in each rotation with /arboretum place (the same path);
#   4. random ticks are sped up, so leaves with a wrong distance would decay, then
#      /arboretum verify checks every recorded tree's blocks against the world;
#   5. restart: the config must be read, not recreated, and every tree verified again.
#
# The server (launcher, libraries, Fabric API) is cached in ~/.cache/arboretum/fabric-26.3;
# each run starts from a fresh world. KEEP=1 keeps the world. Needs java 25, curl, python3.
set -euo pipefail

mc=26.3
loader=0.19.5
installer=1.1.2
fabric_api=0.162.0+26.3
seed=1234567
here=$(cd "$(dirname "$0")/.." && pwd)
jar=$(realpath "${1:-$(ls "$here"/arboretum-fabric/build/libs/arboretum-fabric-*.jar | grep -v sources | head -1)}")
java=${JAVA:-java}
port=${PORT:-25591}
dir=${ARBORETUM_SERVER_DIR:-$HOME/.cache/arboretum/fabric-$mc}
world=arboretum-boot-world

mkdir -p "$dir/mods"
cd "$dir"
[ -f server.jar ] || curl -fsSL -o server.jar \
    "https://meta.fabricmc.net/v2/versions/loader/$mc/$loader/$installer/server/jar"
[ -f "mods/fabric-api-$fabric_api.jar" ] || curl -fsSL -o "mods/fabric-api-$fabric_api.jar" \
    "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$fabric_api/fabric-api-$fabric_api.jar"
rm -f mods/arboretum-*.jar
cp "$jar" mods/
rm -rf "$world"
echo "eula=true" > eula.txt
cat > server.properties <<PROPS
server-port=$port
online-mode=false
level-name=$world
level-seed=$seed
view-distance=4
simulation-distance=4
spawn-protection=0
max-tick-time=-1
PROPS

server=
cleanup() {
    local status=$?
    if [ -n "$server" ]; then kill "$server" 2>/dev/null || true; wait "$server" 2>/dev/null || true; fi
    exec 3>&- 2>/dev/null || true
    if [ -z "${KEEP:-}" ]; then rm -rf "$dir/$world"; fi
    exit "$status"
}
trap cleanup EXIT

problems=()
log=server.log

start() {
    rm -f console "$log"
    mkfifo console
    exec 3<>console
    "$java" -Xmx3G -Darboretum.debug=true -jar server.jar nogui < console > "$log" 2>&1 &
    server=$!
    wait_for 'Done \(' 900 || { problems+=("the server did not start"); return 1; }
}

stop() {
    echo "stop" >&3
    for _ in $(seq 1 90); do kill -0 "$server" 2>/dev/null || break; sleep 1; done
    kill "$server" 2>/dev/null || true
    wait "$server" 2>/dev/null || true
    server=
    exec 3>&-
}

wait_for() {  # wait_for <regex> <seconds> [from-line]
    local deadline=$((SECONDS + $2))
    until tail -n "+${3:-1}" "$log" | grep -qE "$1"; do
        if ! kill -0 "$server" 2>/dev/null || (( SECONDS > deadline )); then return 1; fi
        sleep 1
    done
}

# run <command> <reply-regex>: send a console command, echo and return the reply line
run() {
    local from=$(($(wc -l < "$log") + 1))
    echo "$1" >&3
    wait_for "$2" 120 "$from" || { echo "(no reply to: $1)" >&2; return 1; }
    tail -n "+$from" "$log" | grep -E "$2" | tail -1
}

echo "== Fabric $mc, loader $loader, Fabric API $fabric_api, $("$java" -version 2>&1 | head -1)"
echo "== run 1: fresh world"
start
grep -q "Created the world's Arboretum config" "$log" || problems+=("run 1 did not create the default config")

savanna=$(run "locate biome minecraft:savanna" "nearest minecraft:savanna|Could not find") || true
echo "  $savanna"
read -r sx sz < <(echo "$savanna" | python3 -c "import re,sys;m=re.search(r'\[(-?\d+), [^,]+, (-?\d+)\]',sys.stdin.read());print(m.group(1),m.group(2)) if m else print('')")
if [ -z "${sx:-}" ]; then problems+=("no savanna near spawn for seed $seed"); exit 1; fi

# 15x15 chunks around the savanna: loaded to full status, so their features run. The seed is
# fixed, so what grows must be the same every run, whatever order the chunks generate in.
grow_savanna() {
    run "forceload add $((sx - 112)) $((sz - 112)) $((sx + 112)) $((sz + 112))" "Marked|chunks" > /dev/null
    # Settled when three readings 3 s apart agree: chunks keep generating for a while after the
    # forceload, and stopping on a plateau would compare a half-grown world with a full one.
    local previous=-1 total steady=0
    for _ in $(seq 1 80); do
        sleep 3
        grown=$(run "arboretum stats" "Arboretum: grown") || true
        total=$(echo "$grown" | grep -oE 'grown [0-9]+' | grep -oE '[0-9]+')
        if [ "$total" = "$previous" ] && [ "$total" -gt 0 ]; then steady=$((steady + 1)); else steady=0; fi
        (( steady >= 2 )) && break
        previous=$total
    done
}
grow_savanna
echo "  natural: ${grown#*: }"
natural1=$(run "arboretum list" "Arboretum trees:")
echo "$grown" | grep -q "arboretum:baobab" || problems+=("world generation grew no baobab")
echo "$grown" | grep -q "arboretum:jacaranda" || problems+=("world generation grew no jacaranda")

# One baobab per rotation, each on its own grass pad high above the terrain, through the same
# grower world generation uses.
for r in 0 1 2 3; do
    x=$((sx - 90 + 45 * r)); z=$((sz - 60))
    run "fill $((x - 12)) 149 $((z - 12)) $((x + 12)) 149 $((z + 12)) grass_block" "filled|Successfully" > /dev/null
    out=$(run "execute positioned $x 150 $z run arboretum place baobab $r" "Arboretum placed|refused at") || true
    echo "  rotation $r: ${out##*: }"
    echo "$out" | grep -q "Arboretum placed" || problems+=("rotation $r was not placed: ${out##*: }")
done
stats=$(run "arboretum stats" "Arboretum: grown")
echo "  ${stats#*: }"

run "gamerule random_tick_speed 1000" "Gamerule|gamerule|random" > /dev/null || run "gamerule randomTickSpeed 1000" "Gamerule|random" > /dev/null || true
sleep 20
run "gamerule random_tick_speed 3" "Gamerule|gamerule|random" > /dev/null || run "gamerule randomTickSpeed 3" "Gamerule|random" > /dev/null || true
verify1=$(run "arboretum verify" "Arboretum verify")
echo "  ${verify1#*: }"
echo "$verify1" | grep -q " 0 wrong" || problems+=("run 1: trees don't match the world: $verify1")
stop

[ -f "$world/arboretum.json" ] || problems+=("arboretum.json was not written")
echo "== run 2: restart"
start
grep -q "Read the world's Arboretum config" "$log" || problems+=("run 2 did not read the saved config")
grep -q "Created the world's Arboretum config" "$log" && problems+=("run 2 recreated the config")
sleep 10
verify2=$(run "arboretum verify" "Arboretum verify")
echo "  ${verify2#*: }"
echo "$verify2" | grep -q " 0 wrong" || problems+=("run 2: trees don't match the world: $verify2")
echo "$verify2" | grep -q "^.*verify: 0 trees" && problems+=("run 2 had no trees to verify")
stop

echo "== run 3: a fresh world from the same seed"
rm -rf "$world"
start
grow_savanna
natural3=$(run "arboretum list" "Arboretum trees:")
stop
echo "  run 1: ${natural1#*trees: }"
echo "  run 3: ${natural3#*trees: }"
[ "${natural1#*trees: }" = "${natural3#*trees: }" ] || problems+=("the same seed grew different trees")

grep -nE "ERROR|Exception" "$log" | grep -iE "arboretum|codes\.castled" | head -5 | sed 's/^/  /' || true
grep -qiE "(ERROR|Exception).*(arboretum|codes\.castled)|at codes\.castled\." "$log" && problems+=("an error mentions Arboretum")

if (( ${#problems[@]} )); then
    printf 'FAIL: %s\n' "${problems[@]}"
    echo "---- Arboretum log lines ----"
    grep -E "Arboretum|arboretum" "$log" | tail -30
    exit 1
fi
echo "PASS: trees grow in a real $mc world and survive a restart"
