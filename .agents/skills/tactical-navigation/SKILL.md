---
name: tactical-navigation
description: Implement strafe, cover, peeking, movement hysteresis and flank routes for Forge 1.20.1 armed NPCs while keeping pathfinding costs bounded.
---

# tactical-navigation

## Inputs
Verified entity navigation type; world geometry, cover scoring requirements, perf budgets.

## Procedure
1. Separate intent from nav commands; never issue a fresh `moveTo()` each tick.
2. Sample bounded cover candidates in active area and score blocking, peeking, approach cost, reservation collisions and hazard.
3. Validate path reachability before committing; reserve cover by bot/squad + TTL; release on death/timeout.
4. Implement cooldown on strafe direction changes and stuck detector with backoff. Preserve walk/shoot animation support.
5. Reevaluate on clear stimuli (damage, target move, loss of line of fire) and cap raycast/path requests.
6. Handle unloaded chunks, lava, holes, doors and existing map obstacles conservatively; no block griefing by default.

## Verify
Corners/half slabs/stairs/doors, blocked flanks, ally overlap, active chunk boundaries, stuck retry rate, CPU budgets.
