---
name: tacz-weapon-control
description: Implement native server TaCZ aiming firing ammo reload and bolt control with distinct visual and known-area permissions.
---

# tacz-weapon-control

Inputs: pinned TaCZ API, runtime support and current [configuration](../../../forge-addon/src/main/java/dev/zuma/tacticalnpc/config/ServerConfig.java). Apply [project rules](../../../AGENTS.md), including the implemented Hard/Unfair boundary.

## Procedure
1. Audit changed IGunOperator/IGun calls, gun events and metadata using [tacz-npc-audit](../tacz-npc-audit/SKILL.md); do not infer native signatures from an old mod branch.
2. Keep detection, preferred weapon distance, damage falloff and ballistic maximum reach separate. Read verified data through [TaczGunController](../../../forge-addon/src/main/java/dev/zuma/tacticalnpc/compat/TaczGunController.java) and core WeaponProfile/Ballistics; unknown data needs a conservative documented fallback.
3. DIRECT needs fresh visual LOS, reaction/aim validity and native weapon readiness. SUPPRESS_AREA uses a separate observation/point permit checked by [SuppressionPermit](../../../ai-core/src/main/java/dev/zuma/tacticalnpc/core/SuppressionPermit.java) and [TacticalCombatGoal](../../../forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/TacticalCombatGoal.java). Take duration, burst, pause and actual-round budgets from effective ServerConfig values; do not copy historical numeric limits here. Confidence, uncertainty and sector validity are enforced by their checking code.
4. Count authenticated native rounds through [SuppressionCycle](../../../ai-core/src/main/java/dev/zuma/tacticalnpc/core/SuppressionCycle.java), not trigger requests or pellets as extra cartridges. Hidden reports/hints must not refill an exhausted contact budget. Select another checked sector or a bounded reposition when geometry blocks the point.
5. Route operations through the native operator and handle its results without retry spam or per-tick reload/bolt cancellation. Preserve ammo policy; the independent Unfair reserve exception is not a bypass of magazine consumption or reload.
6. Predict only from confirmed visual velocity and verified ballistic units. Revalidate obstacles, allies, ammo/mode and command identity/TTL for every native round in either profile; hidden hints carry no target velocity. The shared NPC block policy in AGENTS.md governs AI barrier permission for visual, barrel and suppression rays; opaque obstacles still block. Projectile penetration remains entirely with the existing mods.

## Verify
Before running, identify changed files, behavior and the minimum native GameTests for affected modes, reload, bolt, ammo exhaustion, stale commands, hidden contact and ally crossing; add relevant core suites when core/contracts change. Exercise actual native projectile joins/events. Follow [testing profiles](../../../docs/TESTING-PROFILES.md) for adapter/shared-runtime, dedicated save/restart, external gun-pack and FULL risks.

Diagnose FAIL before retrying the failing test and necessary related regressions; explain each additional run. Reuse applicable final-input results, including shared fixtures/helpers, resources, config, dependencies and runtime, for one final STAGE assessment. Preserve original failures and disclose multiple-run coverage; a selective PASS is not a new green FULL. Distinguish measured behavior from human balance acceptance.
