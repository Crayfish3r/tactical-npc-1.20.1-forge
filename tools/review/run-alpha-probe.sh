#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
{ find ai-core/src/main/java ai-core/src/test/java -name '*.java' -print0; printf '%s\0' tools/review/AlphaReviewProbe.java; } | xargs -0 javac --release 17 -encoding UTF-8 -d "$out"
java -cp "$out" AlphaReviewProbe
