# Suggested Codex tasks (run sequentially)

## Task 0 — audit target mod, no edits
`Use $tacz-npc-audit. Inspect the exact Forge 1.20.1 TACZ NPCs 2.1.0 JAR and pinned TaCZ JAR. Fill docs/INTEGRATION-AUDIT.md with evidence, signatures, and a clear decision: safe addon hook vs fork. Do not implement any mixin yet.`

## Task 1 — implement tested adapter
`Use $forge-compat-hook. Implement the minimal Forge add-on and version-gated NpcRuntimeAdapter for verified 2.1.0 classes. One owner for movement/weapon. Fail closed and add smoke checks. Compile after each slice.`

## Task 2 — legitimate TaCZ shooting
`Use $tacz-weapon-control. Add server-side FireControl using verified TaCZ operator methods. Respect ammo, aim, cooldown, reload, bolt, target LoS and ally corridor. Add GameTests and dedicated-server manual test script.`

## Task 3 — fair hardcore 1v1
`Use $hardcore-pvp-brain. Integrate ai-core policy with legit perception and aim smoothing. Add deterministic logs to compare 1v1 bots. No omniscient information.`

## Task 4 — cover + flanking
`Use $tactical-navigation. Add bounded cover sampling, path cooldown/hysteresis, failed-cover backoff and strafe changes. Verify no stuck path loops.`

## Task 5 — squad pressure
`Use $squad-tactics. Add server-level shared blackboard per squad, delayed alerts, suppression and flanker reservations. Demonstrate 1v4.`

## Task 6 — testing, budget, release
`Use $forge-ai-profiling. Add Forge GameTests, server telemetry and load benchmark. Report 1,4,16,30,50 NPCs with p95 tick. Do not claim results not measured.`
