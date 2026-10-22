---
name: tactical-navigation
description: Develop bounded tactical navigation and specify action-route doors and ladders with native execution and narrow-passage coordination.
---

# tactical-navigation

Inputs: current movement owner, native navigation/physics, loaded geometry and tactical goals. Apply [project rules](../../../AGENTS.md); audit only APIs used by the affected runtime.

## Procedure
1. Inspect [MovementExecutor](../../../forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/MovementExecutor.java), terrain/order checks and bounded recovery. Extend the independent runtime without requiring NPCs classes; an optional adapter needs its own relevant API evidence.
2. Separate bounded route search/replanning from per-tick execution. Rebuild native paths on meaningful invalidation/stimuli with budgets/backoff; refreshing MoveControl inputs, progress checks and safety for the selected transition each active tick is allowed and necessary. Do not equate that with a new createPath/moveTo path search every tick.
3. Keep tactical decisions responsible for the goal and one movement owner responsible for execution. Reuse valid paths/claims, honor order bounds, and invalidate generation/target/order/TTL safely. Routine combat refreshes must not restart an action.
4. Bound candidates, rays and native/custom search globally and per owner using the [work budget](../../../forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/AiWorkBudget.java) and owning search/recovery code. Use only loaded geometry; never add production chunk tickets, scan terrain without a bound or force relocation through obstacles.

## Next-stage requirements — not a claim of shipped capability
At the instruction-alignment checkpoint, doors/ladders and action-route drafts require implementation review and acceptance in the navigation stage. Local files or a design report alone do not prove completion.

- Model WALK, OPEN_DOOR, CLIMB_UP and CLIMB_DOWN as immutable core transitions with approach/entry/exit points, preconditions, completion and deadlines. Forge owns world objects/interactions. Distinguish RUNNING, WAITING_RESOURCE, WAITING_BUDGET, SUCCESS and FAILURE.
- Keep ordinary onGround/safeStanding/safePath rules for walking; give ladder/interactions explicit admissibility. Intermediate ladder nodes may lack floor. Success requires actual world state and a stable physical exit, not elapsed time or approximate height.
- Wooden vanilla doors need both halves, facing/hinge, native server opening, applicable Forge vetoes and collision confirmation. Recheck a reclosed door. Closed iron/unsupported mod doors remain obstacles with bounded alternatives and an interaction-adapter boundary; automatic closing is outside this stage.
- Vanilla LadderBlock needs consistent facing/continuity, body clearance, approach/entry, native ascent/descent and a supported landing. Broken ladders, blocked exits or no progress produce a diagnostic failure and bounded recovery, never teleport/noClip.
- Use bounded per-level FIFO/expiry reservations for narrow passages, released on completion, safe cancellation, death/removal/unload. Waiting must not consume the physical-stuck timer. Define safe exit behavior for order/target changes and emergencies; retain ownership while an actor is still inside a started crossing.
- Navigation may consume only permitted observations, with UNFAIR_HINT separately identified. Preserve native weapon upkeep, obstacles and ally protection during traversal.

## Verify
Require real GameTests for doors from both sides/reclosing; ladder up/down/grounded exit; destruction/blocked landing; four actual sequential arrivals; safe cancellation/cleanup; and actual native combat after traversal. Remove alternate ground routes from ladder fixtures. Establish finite fixture chunk/entity-ticking readiness separately from the active action deadline. Use scoped checks after each slice and the requested final FULL/dedicated/multi-group profiling from [testing profiles](../../../docs/TESTING-PROFILES.md). Record failures and human production-JAR acceptance separately.
