#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
find src/main/java src/test/java -name '*.java' -print0 | xargs -0 javac --release 17 -encoding UTF-8 -d "$out"
java -cp "$out" dev.zuma.tacticalnpc.core.CoreSmokeTest
java -cp "$out" dev.zuma.tacticalnpc.core.AlphaRegressionTest
