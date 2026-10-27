package dev.zuma.tacticalnpc.core;

/**
 * Deterministic combat policy. Cover, order constraints and suppression are separate pure overlays.
 */
public final class CombatBrain {
    private final int memoryTicks;
    private final double effectiveRange;

    public CombatBrain(int memoryTicks, double effectiveRange) {
        if (memoryTicks < 0 || !(effectiveRange > 0.0) || !Double.isFinite(effectiveRange))
            throw new IllegalArgumentException("Invalid tuning");
        this.memoryTicks = memoryTicks;
        this.effectiveRange = effectiveRange;
    }

    public CombatDecision decide(CombatSnapshot s) {
        return policy(s).bind(s);
    }

    private CombatDecision policy(CombatSnapshot s) {
        var operation = s.magazineRounds() == 0 && s.reserveAmmoAvailable() ? ActionArbiter.WeaponAction.RELOAD :
                s.boltNeeded() ? ActionArbiter.WeaponAction.BOLT : ActionArbiter.WeaponAction.NONE;
        if (!s.targetKnown()) return new CombatDecision(TacticalIntent.PATROL, false, "no observed target", operation);
        boolean returnFire=s.targetVisible() && s.magazineRounds()>0 && !s.boltNeeded() &&
                s.targetDistanceBlocks()<=effectiveRange && s.lineOfFireClear() && !s.allyInFireLine();
        var emergencyWeapon=returnFire?ActionArbiter.WeaponAction.SHOOT:operation;
        if (s.healthRatio() <= 0.25)
            return new CombatDecision(TacticalIntent.RETREAT, returnFire, "critical health", emergencyWeapon);
        if (s.pathBlocked()) return new CombatDecision(TacticalIntent.REPOSITION, returnFire, "blocked route", emergencyWeapon);
        if (!s.targetVisible()) {
            return s.lastSeenTicksAgo() <= memoryTicks
                ? new CombatDecision(TacticalIntent.SEARCH, false, "search last-known location", operation)
                : new CombatDecision(TacticalIntent.PATROL, false, "sighting expired", operation);
        }
        if (s.magazineRounds() == 0) {
            return s.reserveAmmoAvailable()
                ? new CombatDecision(s.coverAvailable() ? TacticalIntent.TAKE_COVER : s.targetDistanceBlocks()<s.weapon().profile().minimum()?TacticalIntent.RETREAT:TacticalIntent.ENGAGE,
                        false, "magazine empty", operation)
                : new CombatDecision(TacticalIntent.RETREAT, false, "no available ammunition");
        }
        if (s.boltNeeded()) return new CombatDecision(TacticalIntent.ENGAGE, false, "weapon action required", operation);
        if (s.incomingFire() && s.coverAvailable())
            return new CombatDecision(TacticalIntent.TAKE_COVER, false, "incoming fire");
        if(s.targetDistanceBlocks()<s.weapon().profile().minimum()) {
            boolean safe=s.targetDistanceBlocks()<=effectiveRange && s.lineOfFireClear() && !s.allyInFireLine();
            return new CombatDecision(TacticalIntent.RETREAT,safe,"below preferred weapon distance");
        }
        if (s.flankRouteAvailable() && s.targetDistanceBlocks() > 12.0 &&
                s.targetDistanceBlocks() < effectiveRange * 0.8)
            return new CombatDecision(TacticalIntent.FLANK, false, "safe flank route");
        if (s.targetDistanceBlocks() > effectiveRange)
            return new CombatDecision(TacticalIntent.ADVANCE, false, "out of effective range");
        // Intent survives a native cooldown/volley pause. Fresh arbiter/native gates own readiness.
        boolean safeFire = s.lineOfFireClear() && !s.allyInFireLine();
        return new CombatDecision(TacticalIntent.ENGAGE, safeFire,
                safeFire ? "clear and ready" : "weapon fire gated");
    }
}
