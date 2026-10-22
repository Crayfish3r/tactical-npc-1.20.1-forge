---
name: forge-ai-profiling
description: Validate and profile Forge combat AI with risk-scoped tests and explicitly identified comparable before-after or third-party baselines.
---

# forge-ai-profiling

Inputs: comparison question, frozen baseline/current sources, pinned dependencies, host/JVM and fixture manifests. Apply [project rules](../../../AGENTS.md) and [testing profiles](../../../docs/TESTING-PROFILES.md).

## Procedure
1. Match checks to the change: instructions need links/frontmatter/diff validation; core needs relevant Java suites; gameplay needs affected GameTests; lifecycle/packaging needs ordinary dedicated checks. Use FULL/release/load matrices for cross-cutting risks and explicit user requirements, not after every documentation edit.
2. Identify the experiment before measuring: repository AI before/after compares frozen project revisions; original NPCs versus custom AI is a separate third-party experiment requiring its pinned adapter/artifacts. Do not substitute one baseline for the other or reuse historical numbers as a fresh baseline.
3. Preserve source/dependency/fixture hashes and comparable seed, map, weapons, config/profile, JVM, warmup, sample window and entity activation/ticking. Run benchmark JVMs sequentially without concurrent builds/tests. Test-only finite tickets must be disclosed separately from production search.
4. Measure p50/p95 MSPT and AI cost, server cadence, query/ray/path work, allocations/GC where relevant, first shot/hit, actual native rounds/pellets, hits, shooters and active/ticking NPC counts. Requested/spawned actors and trigger calls are not proof of work. Use real projectile joins/hits and actual arrivals.
5. Choose cohort sizes/scenarios for the risk; the established release/load matrix includes 1/4/16/30/50 and human 1v1/1v4 when requested. Add entity unload/reload, chunk boundaries, low-TPS, leak or soak checks where the changed behavior needs them.
6. Never credit lower firing, fewer active actors, missing arrivals or slower engagement as optimization. If useful work differs, report the difference and costs without claiming an equivalent-work speedup. JFR sampling is an estimate; separate fixture, AI, native weapon and projectile costs.
7. Add load shedding/caching or budgets only for an observed bottleneck, preserving behavior and the project safety contracts. Read tuning/caps from their source owners, not a second numeric profile here.

## Verify
Retain raw logs/CSV/JFR, manifests, commands and success/failure counts. NO-SOURCE/dry-run/partial execution are not PASS. Compare measured scope honestly and state remaining compatibility/human production-JAR checks; do not infer 20 TPS, fairness or performance wins from test names or role labels.
