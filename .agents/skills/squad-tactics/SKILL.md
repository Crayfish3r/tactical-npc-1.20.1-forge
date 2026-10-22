---
name: squad-tactics
description: Develop per-level squad coordination with delayed visual reports stable implemented roles and bounded reservations.
---

# squad-tactics

Inputs: current native-team membership, observation events and per-level [SquadBlackboard](../../../ai-core/src/main/java/dev/zuma/tacticalnpc/core/SquadBlackboard.java). Apply [project rules](../../../AGENTS.md).

## Procedure
1. Verify membership through the runtime/team adapter; do not create a parallel faction source. Store immutable coordinates/UUIDs with expiry, never live enemy entities in the board.
2. Preserve the current visual-report contract: the publisher accepts confirmed VISION and the receiver gets delayed nonvisual SQUAD knowledge with original observation age. Personal hearing/damage are not automatically implemented report sources.
3. Read effective delivery delay/radius at the [TacticalCombatGoal](../../../forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/TacticalCombatGoal.java) call site and enforce board TTL/validity in core. Authorized Unfair accelerates this existing delivery; UNFAIR_HINT is never promoted or forwarded as a visual report.
4. Use the implemented ANCHOR, FLANKER and SUPPRESSOR roles. Keep stable assignments and evaluate coordination utility/progress before changing a role; new roles require implementation and tests, not just documentation.
5. Prevent duplicate cover claims and bound narrow-passage reservations by level, owner and expiry. Release claims on invalidation/removal/unload; waiting for a lease is distinct from physical stuckness.
6. Preserve per-round ally geometry and native safety. Coordinate suppression and actual flank routes using permitted knowledge, without instant shared hidden coordinates.

## Verify
Use scoped delayed-report/expiry, profile-switch, leader-death, dimension and reservation GameTests. Add actual coordinated combat and multi-group load when this behavior/performance is affected or requested. Follow [testing profiles](../../../docs/TESTING-PROFILES.md); report actual active actors, native rounds and arrivals rather than role labels alone.
