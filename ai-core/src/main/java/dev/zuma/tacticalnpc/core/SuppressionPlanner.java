package dev.zuma.tacticalnpc.core;
/** Limited fire at a previously observed open point, never deliberate wall penetration. */
public final class SuppressionPlanner {
    public CombatDecision plan(CombatSnapshot s,CombatDecision base,SuppressionPermit permit) {
        if(!permit.valid(s.self().frame().tick()) || s.targetVisible() || s.weapon().busy() || s.weapon().rounds()==0 ||
                !s.weapon().supported() || s.targetDistanceBlocks()>s.weapon().effectiveRange() || !s.terrain().lineOfFireClear() || s.terrain().occupied() ||
                base.intent()==TacticalIntent.TAKE_COVER || base.intent()==TacticalIntent.RETREAT || base.intent()==TacticalIntent.REPOSITION || base.intent()==TacticalIntent.FLANK ||
                base.weaponAction()==ActionArbiter.WeaponAction.BOLT || base.weaponAction()==ActionArbiter.WeaponAction.RELOAD) return base;
        return new CombatDecision(base.intent(),true,"sustained suppression of known region",ActionArbiter.WeaponAction.SHOOT,
                base.intent()==TacticalIntent.SEARCH?MovementCommand.hold():base.movement(),new AimCommand(AimCommand.Mode.SUPPRESS_AREA,permit.point()),base.frame(),base.expires());
    }
}
