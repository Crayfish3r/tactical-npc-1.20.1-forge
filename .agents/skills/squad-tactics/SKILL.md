---
name: squad-tactics
description: Create squad-based PvP NPC coordination: delayed sightings, suppressor/flanker roles, cover claims and fair information sharing.
---

# squad-tactics

## Inputs
Squad membership mapping, target knowledge events, visibility/hearing policies, server level.

## Procedure
1. Store per-level squad blackboard keyed by squad UUID; include target last-known location, confidence and TTL; never static leak between dimensions.
2. Propagate only genuine spotted/heard/hit events. Apply configurable radio/speech delay and radius, not instant global omniscience.
3. Assign roles (anchor, flanker, suppressor, rusher) with cooldown and maximum simultaneous flankers.
4. Prevent duplicate cover points; expire reservations on removal/target switch/unload.
5. Respect friendly-fire geometry and explosions, use coordination to generate difficult but counterplay-friendly encounters.

## Verify
1v4 coordinated combat, communication behind walls, squad death/respawn, dimension separation, 30 NPC mass load and no stale coordinates.
