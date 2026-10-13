---
name: tacz-weapon-control
description: Implement TaCZ gun firing, aiming, recoil, ammo, reloading and bolt behavior for NPCs using genuine server APIs and fair PvP constraints.
---

# tacz-weapon-control

## Inputs
Pinned TaCZ JAR/API, audited NPC entity runtime methods, GameTest harness.

## Procedure
1. Inspect exact TaCZ 1.20.1 `IGunOperator`, weapon item/ammo and relevant gun events. Use no guessed shoot/reload signatures.
2. Create `GunSnapshot` containing magazine, reserve, mode, cooldown, bolt state, weapon range/ballistic data when verifiable.
3. DIRECT requires a fresh visual target + clear line of fire + ally sector safety + reaction/aim condition + native server cooldown. SUPPRESS_AREA requires a separate fresh legitimate Observation permit (age <=40 ticks, confidence >=0.35, uncertainty <=12), at most six native rounds per continuous hidden contact and pauses >=10 ticks between requests. Never read the hidden player position for aiming. Both modes revalidate geometry, ammo properties and allies for every native round; blocked known-point rays prohibit firing, including deliberate wallbang.
4. Route shooting through verified TaCZ operator. Handle `ShootResult` (success/need bolt/etc.) without retry spam.
5. Reload when empty + reserve; do not interrupt reload each tick; state arbitration controls order.
6. Prediction is optional and requires correct speed units and target velocity from *observed* samples. Never bypass TaCZ spread, weapon damage, cooldown or projectile creation.

## Verify
Semi-auto/full-auto, reload once, needs-bolt, zero ammo, friend crossing, target hidden, external gun addon and server sound/event. Capture logs and GameTests.

