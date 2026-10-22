#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
if [ "$#" -eq 0 ]; then
    set -- policy temporal territory patrol attack combat
fi
declare -A suites=(
    [policy]=CoreSmokeTest [temporal]=AlphaRegressionTest
    [territory]=DefendTerritoryTest [patrol]=DefendPatrolTest [attack]=AttackRouteTest
    [combat]=CombatUpgradeTest
)
declare -A seen=()
for suite in "$@"; do
    if [[ -z "${suites[$suite]:-}" || -n "${seen[$suite]:-}" ]]; then
        echo "Unknown or duplicate core suite: $suite (policy temporal territory patrol attack combat)" >&2
        exit 2
    fi
    seen[$suite]=1
done
find src/main/java src/test/java -name '*.java' -print0 | xargs -0 javac --release 17 -encoding UTF-8 -d "$out"
for suite in "$@"; do
    java -cp "$out" "dev.zuma.tacticalnpc.core.${suites[$suite]}"
done
