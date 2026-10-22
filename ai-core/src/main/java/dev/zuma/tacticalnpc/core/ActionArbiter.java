package dev.zuma.tacticalnpc.core;

/** Final safety gate. Inputs must be refreshed on the server at application time. */
public final class ActionArbiter {
    public enum WeaponAction { NONE, SHOOT, RELOAD, BOLT }
    public record Safety(boolean ownerAlive, boolean targetValid, boolean visible,
                         boolean reactionComplete, boolean aimAligned, boolean clear,
                         boolean allyCrossing, boolean weaponBusy, boolean cooldownReady) {}
    public record Commands(MovementCommand movement, AimCommand aim, WeaponAction weapon, boolean valid) {}

    public Commands commands(CombatDecision d, Frame now, Safety safety) {
        return commands(d,now,safety,SuppressionPermit.none());
    }
    public Commands commands(CombatDecision d,Frame now,Safety safety,SuppressionPermit suppression) {
        Frame then = d.frame();
        boolean valid = then != null && then.owner().equals(now.owner()) && then.dimension().equals(now.dimension()) &&
                then.generation() == now.generation() && then.tick() <= now.tick() && now.tick() <= d.expires() && safety.ownerAlive();
        if (!valid) return new Commands(MovementCommand.hold(),AimCommand.none(),WeaponAction.NONE,false);
        return new Commands(d.movement(),d.aim(),arbitrate(d,safety,suppression,now.tick()),true);
    }

    public WeaponAction arbitrate(CombatDecision decision, Safety s) {
        return arbitrate(decision,s,SuppressionPermit.none(),decision.frame()==null?0:decision.frame().tick());
    }
    private WeaponAction arbitrate(CombatDecision decision,Safety s,SuppressionPermit suppression,long tick) {
        if (!s.ownerAlive() || s.weaponBusy()) return WeaponAction.NONE;
        if (decision.weaponAction() == WeaponAction.RELOAD) return s.cooldownReady() ? WeaponAction.RELOAD : WeaponAction.NONE;
        if (decision.weaponAction() == WeaponAction.BOLT) return WeaponAction.BOLT;
        boolean direct=decision.aim().mode()==AimCommand.Mode.DIRECT && s.targetValid() && s.visible() && s.reactionComplete();
        boolean area=decision.aim().mode()==AimCommand.Mode.SUPPRESS_AREA && suppression.valid(tick) &&
                decision.aim().point().equals(suppression.point());
        return decision.firePermitted() && (direct || area) && s.aimAligned() && s.clear() &&
                !s.allyCrossing() && s.cooldownReady() ? WeaponAction.SHOOT : WeaponAction.NONE;
    }
}
