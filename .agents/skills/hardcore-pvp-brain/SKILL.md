---
name: hardcore-pvp-brain
description: Implement fair but extremely challenging PvP-style NPC combat decisions: perception, reaction latency, velocity prediction, strafing and utility tactics.
---

# hardcore-pvp-brain

## Inputs
`ai-core` code, perception contracts, balance profile and combat logs.

## Procedure
1. Keep `ai-core` independent of Forge and deterministic given a snapshot.
2. Reject knowledge from unseen targets except aged last-seen/heard/hit/squad report with confidence decay.
3. Use `TacticalIntent` utility decisions with state commitment and emergency interrupts; no per-tick oscillation.
4. Separate movement, aiming and firing. Bound turn rate and human-like response; cap speed prediction; no perfect headshots.
5. Introduce weapon-class profiles (SMG/AR/DMR/sniper/shotgun) only after observing real TaCZ gun metadata.
6. Compare win rate, accuracy by range, TTK and cover use on consistent maps; tune challenge without increasing base damage arbitrarily.

## Verify
Run `bash ai-core/run-tests.sh` and combat GameTests; no invisible-target perfect tracking; deterministic test seeds; record fairness settings.
