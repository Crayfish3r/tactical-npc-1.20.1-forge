# Technical architecture: Tactical NPC AI PvP Edition

Target: Java 17; Forge 1.20.1; TaCZ; separate compatibility adapter for `[TACZ] NPCs` 2.1.0.

Status note, 2026-10-11: this document also contains design intentions. Current implementation is the independent PathfinderMob with native TaCZ LivingEntity lifecycle and a separate optional NPCs artifact. Runtime boundaries, pure cover/order/suppression overlays and per-level delayed SquadBlackboard exist; exact executed checks are in [IMPLEMENTATION-STATUS](IMPLEMENTATION-STATUS.md). Scheduler/LOD, advanced flanks and full release performance targets below are not automatically implemented or proven by this design. No NPCs source fork or copied renderer is required for the independent entity.

## 0. Key decision
Implement **a separate add-on** with a replaceable `NpcRuntimeAdapter` first. If 2.1.0 has no safe hook to override its fight behavior without conflicts, fork its MIT-licensed source (or negotiate access to its current code) instead of brittle global Mixin injections. Legacy Corrinedev and craftorio implementations are references, **not** proof of the 2.1.0 internals.

## 1. Runtime dataflow
```
Forge server tick 20 TPS
  -> NpcAdapter.selectAffectedEntities() [verified registry ID/type]
  -> SensorScheduler [LoS / hearing / hit / target velocity / ammo / cover]
  -> CombatSnapshot immutable [per bot; never omniscient]
  -> TacticalPlanner [intent every 4-8 ticks or interrupts]
  -> MovementPlanner [cover / strafe / flank / advance]
  -> FireControl [reaction delay / turn rate / friendly checks]
  -> CommandArbiter [exactly one active movement + fire ownership]
  -> TaCZ/NPC adapter [request navigation, aim, shoot, bolt, reload]
  -> Debug telemetry [sampled, rate-limited]
```
SquadBlackboard is server-per-level, key = squad UUID, with expiry, no global dimensions leak.

## 2. Data types / contracts
- `NpcRuntimeAdapter`: stable interface to target entity pose/target, navigation, health, weapon, lifecycle, state and ownership. It is implementation-specific to audited 2.1.0 classes.
- `GunController`: `getWeaponSnapshot`, `requestAim`, `tryShoot`, `tryBolt`, `tryReload`, `getEffectiveRange`. Use actual TaCZ API; never hand-spawn bullets.
- `SensorSnapshot`: entity id and dimension, world tick, immutable position/velocity, observed target id, line of sight, last seen location/tick, acoustic event.
- `CombatBlackboard`: target last-known data, confidence decay, last goal, suppression, squad role, cover reservations, cooldowns.
- `TacticalIntent`: PATROL / SEARCH / ENGAGE / TAKE_COVER / FLANK / ADVANCE / REPOSITION / BOLT / RELOAD / RETREAT.
- `ActionArbiter`: movement owner, weapon owner, command TTL, stop/reset on death/dimension/unload, token for stale async results.

## 3. Fair PvP-style combat
- Target acquisition: field of view and validated LoS; visibility checks target body/head as allowed; consider fog/blocks; don't cheat through chunks.
- Hearing: subscribe to validated server-side weapon/gunshot/hurt events if available; propagate approximate event location, distance falloff, sound age and confidence; don't infer precise shooter through wall.
- Reaction: configurable 4-9 ticks (200-450 ms) from valid stimulus for difficult bots. Aim turn limit ~8-18 degrees/tick, with small correlated noise and fatigue; values are *starting points*, tune in GameTests/PvP simulations.
- Movement prediction: estimate target velocity using past observed positions; solve basic ballistic interception only with correctly converted TaCZ projectile speed/gravity; cap prediction to fresh observations. If ballistic data unknown, prefer direct aim over guessing.
- Line of fire: reject allies or protected/faction entities in predicted fire corridor; verify LOS; avoid friendly fire from penetrating/explosive projectiles.
- Movement: strafe during ENGAGE; change direction with cooldown; prefer lateral movement; do not jitter every tick; cover/peeking only when actual block geometry supports it.
- Reload: only call TaCZ APIs respecting ammo reserve and cooldown. Retreat / cover before reloading when feasible. Do not cancel/restart reload each tick.
- Surprise / evasion: break predictable strafe habits after several shots; aim reset if target lost.
- Anti-exploit: do not spin 180 instantly, snipe exact coordinates after losing target, aimbot through cover, or perfectly track invisible players.

## 4. Layered planning
- Reactive safety (every server tick if entity active): stop fire on loss of LoS/friendly crossing/death; process TaCZ fire cooldown as exposed.
- Combat intent planner (every 4-8 ticks + interrupts): utility score by tactical state, previous commitment/hysteresis.
- Perception scans (2-4 ticks in combat, 10-20 idle): visibility and recent target; rely on bounded sensors rather than searching all world entities.
- Cover candidates (every 10-20 ticks, or on threat/geometry change): sample solid blocks in capped radius, validate nav + exposure, reserve a slot per squad to prevent overlap.
- Path recalculation (change-driven, minimum 15-30 ticks absent emergency): do not issue `moveTo` on every tick.
- Squad roles (every 10-20 ticks): suppressor, flanker, anchor, rusher; role ownership TTL and expiry.

These are initial schedules, not performance guarantees.

## 5. Cover scoring
Candidate utility = w1 * blockedEnemyFire + w2 * canPeek + w3 * routeReachable + w4 * flankPotential
                  - w5 * pathCost - w6 * explosiveHazard - w7 * teammateReservation - w8 * exposureTime.
Use up to K bounded candidate checks per bot and tick budget for ray traces. Never scan arbitrary terrain volumes. No destructive building/breaking by default.

## 6. Gun integration
TaCZ `IGunOperator` (1.20.1 branch) exposes `shoot(Supplier<Float> pitch, Supplier<Float> yaw)`, `reload()`, `bolt()`, `aim()` status and cooldowns; the legacy Corrinedev mod's `TaczShootAttack` uses these actions. An actual 2.1.0 adapter must verify entity support and required methods first. The policy decides *when and why*, never bypasses TaCZ's gun mechanics.

## 7. Conflict and lifecycle control
Only one combat controller is active per NPC. Base NPC task must be cancelled / disabled / replaced when the custom behavior takes over. If target mod doesn't expose a safe replacement point, prefer a source fork rather than concurrently ticking a replacement through LivingTickEvent. Clear all state on despawn/death/chunk unload/teleport/dimension transfer. Server authoritative; visual packet sync is optional and minimal.

## 8. Performance goals
Use Spark or Java Flight Recorder on a dedicated Forge test server. Track p95 tick duration and AI CPU time (goal: <= 2 ms/tick for tactical code with 30 active NPCs on chosen hardware, **target, not measured claim**), ray traces/s, cover scans/s, navigation rebuilds/s and memory sizes. Degrade gracefully with LOD (far NPC: sparse sensors; distant squads: coarse simulation or vanilla fallback). TPS and human fairness beat flashy tactics.

## 9. First release scope
1v1: deterministic perception and fire gating, meaningful strafe, legitimate reload and no ally shooting. 1v4: roles, shared last-known info with latency, cover reservation. Production: no hardcoded internal class/method names without audited evidence; GameTests + server soak + profiler.
