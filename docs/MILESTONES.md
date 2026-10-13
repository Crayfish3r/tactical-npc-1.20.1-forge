# Roadmap with acceptance criteria

| Milestone | Deliverable | Release gate |
|---|---|---|
| M0: audit | lock versions, class signatures, legal checks, adapter strategy | Verified against exact 2.1.0 binary/source; no guessed internals |
| M1: vertical slice | one NPC acquires **visible** player, aims via legit TaCZ API, shoots, bolts, reloads | dedicated server test; no double shots/duplication; no through-wall target |
| M2: PvP brain | aim smoothing, velocity prediction (if ballistic API confirmed), strafe, cover, last known | player line-of-sight and friend corridor tests; humanized response |
| M3: tactics | flank, cover reservations, squad role handoff, reaction on teammate death | 1v4 scenario passes; no path oscillation/stuck spam |
| M4: production | saved config, telemetry, GameTests, profiler, low-frequency far LOD | 30/50 NPC load scenarios; no severe p95 tick regression; no chunk leaks |
| M5: optional extras | difficulty profiles, weapon classes, PvE mission director | config + backward compatibility documented |

## Quality metrics (initial project targets, not measured claims)
- 0 shoots without valid line-of-fire / friendly-fire gating.
- 0 duplicate controller firings in the same TaCZ cooldown window.
- target 20 TPS with 30 active combat NPCs on defined reference server hardware.
- target AI compute <= 2ms/tick p95 after profiling, adjust based on test hardware.
- cover retry backoff after unreachable/missing cover.
- Aim/reaction distributions: configurable, not perfectly instantaneous.
- 1v1 / 1v4 / 30+ NPC test arena video and logs before a PvE release.
