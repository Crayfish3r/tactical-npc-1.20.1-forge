---
name: forge-ai-profiling
description: Benchmark Forge server combat AI, add GameTests, profiler instrumentation, tick budgets and load-shedding for 1.20.1 TaCZ NPCs.
---

# forge-ai-profiling

## Inputs
Pinned test modpack, dedicated server CPU/RAM, representative maps, test logs.

## Procedure
1. Run `bash ai-core/run-tests.sh` and code review before in-game test.
2. Implement Forge GameTests documented at docs.minecraftforge.net/en/1.20.x/misc/gametest for LOS, cooldown, reload, ally, lost target, unload and squads.
3. Measure with Spark or Java Flight Recorder on dedicated server; report world seed, entity count, AI timings p50/p95, TPS, ray traces and path requests.
4. Load 1/4/16/30/50 NPCs and compare baseline original mod against enhanced mod under identical world/gear conditions.
5. Add LOD, capped sensors/covers, coalesced path updates, command TTL and safe feature flags only on observed bottlenecks.
6. Track memory leaks, unload, reconnect, low TPS effects, mod compatibility and crashes.

## Verify
Share commands, raw metrics, p95 trend, screenshots/logs, exact mod list and failures. Never claim 20 TPS or performance gains without measurement.
