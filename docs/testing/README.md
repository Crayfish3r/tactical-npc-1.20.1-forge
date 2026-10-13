# Test evidence in the source repository

This directory contains compact test summaries, artifact verification hashes, configuration samples and profiling CSV reports. Raw `.log` and `.jfr` files remain local and are ignored by Git; historical documentation links to those files describe the original local runs. Build output, test worlds and backup trees are also excluded.

The last alpha-1 acceptance run was **2026-10-12**:

| Check | Result |
|---|---|
| `bash ai-core/run-tests.sh` | 72 policy + 94 temporal checks PASS |
| `bash tools/review/run-alpha-probe.sh` | 94 temporal checks PASS; defects are no longer expected |
| `runGameTestServer --offline -PcombatTests` | 29/29 required tests PASS, without NPCs |
| `runGameTestServer --offline -PcombatTests -PlegacyCompatibility` | 38/38 required tests PASS |
| `compileJava compileCompatJava build --offline --rerun-tasks` | BUILD SUCCESSFUL, 12 tasks executed |
| `python -B tools/verify-production-artifacts.py` | PASS; see `alpha1-production-artifacts-2026-10-12.json` |
| Ordinary `runServer --offline` | DedicatedServer Done, Hard/diagnostics commands, clean stop |

These are historical results, not a new run performed during the Git import. Reproduction commands, pinned versions, measurement conditions, failed attempts and remaining manual acceptance work are described in [alpha-1 release notes](../ALPHA-1-RELEASE-NOTES-2026-10-11.md). The source import does not assert full alpha readiness.
