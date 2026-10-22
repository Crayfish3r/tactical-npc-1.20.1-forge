# Repository rules for Codex / AI agents

Goal: build hard, fair, **server-authoritative** independent PvP-style NPCs for Minecraft 1.20.1 / Forge 47.4.20 / Java 17 / TaCZ 1.1.8-hotfix2. `[TACZ] NPCs` 2.1.0 is a transitional optional adapter and comparison baseline. Preserve its equipment/rendering/spawn systems in that adapter; the independent entity must not require its classes or resources. Minimal vanilla rendering is sufficient.

## Hard constraints
1. Treat `[TACZ] NPCs 2.1.0` as the compatibility target, **not** the older `tacz_npc` branch. Never hardcode an internal class, mod ID or mixin target until confirmed from the pinned binary/source. Keep evidence in `docs/INTEGRATION-AUDIT.md`.
2. Design: `perception -> immutable snapshot -> decision -> action arbitration -> Forge/TaCZ adapter`. The Java core must not import `net.minecraft`, `net.minecraftforge`, `com.tacz`, or the existing NPC mod.
3. Only the Minecraft server thread may mutate entities, inventories, navigation, brain memories, or world state. Workers may process immutable snapshots only; results require generation/target validity checks before applying.
4. Never fire a TaCZ weapon directly by spawning a bullet if an authenticated weapon API is available. The TaCZ adapter owns cooldowns, shot, reload, bolt, gun mode, ammo, and friendly-fire gating.
5. In Hard, never give NPCs knowledge of occluded players without a legitimate last-seen, hearing, damage, or squad-report observation. Never aim at coordinates not known to the NPC. Explicit user-authorized Unfair alone may receive periodic hidden-position hints: store them separately as UNFAIR_HINT, never as VISION, never with hidden velocity, never relay them as a visual squad report. Disabling Unfair clears its hints.
6. One controller owns tactical movement/shoot requests when the custom AI is enabled. Prevent duplicate firing, duplicate goal scheduling, and competition with base NPC AI. Fall back safely if the adapter fails.
7. No per-tick full-world entity scans, unbounded raycasts, unbounded pathfinding or synchronous I/O. Use work budgets, cache invalidation, entity activation radius, level-aware cleanup.
8. Avoid Mixins unless an event, extension interface, source-level fork, or official API is insufficient. Every Mixin needs a documented target signature, version gate, fail-soft behavior and dedicated-server validation.
9. Hard maintains distinct human-like limits on perception delay, reaction delay, turn rate, accuracy and ability to predict movement. Explicit user-authorized Unfair may boost hearing, reaction and squad-report speed and use infinite reserve with ordinary native reload. Both modes retain native TaCZ cooldowns, physical obstacles and friendly-fire protection; DIRECT always requires current visual LOS. Unfair hints only authorize separate known-area search/suppression, not visual tracking.
10. Preserve original third-party licenses, attribution and resources when forking. Treat modpack redistribution separately from permission to reuse code.

## Workflow
- Read relevant `.agents/skills/<name>/SKILL.md` before starting changes.
- Make small vertical slices; compile / run tests after each. Do not claim `runServer` or GameTests passed unless run.
- Run `bash ai-core/run-tests.sh` for pure Java changes.
- After the Forge adapter exists, test `gradlew runServer` and GameTests on the **pinned** dependency versions. Run dedicated-server startup, 1v1, 1v4 and >=30 NPC profiling before release.
- State exact versions, file paths, test results and unresolved compatibility risks in every PR.
