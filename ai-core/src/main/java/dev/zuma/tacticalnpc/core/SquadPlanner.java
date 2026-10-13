package dev.zuma.tacticalnpc.core;
/** Minimal local role difference. Flanker cover geometry is selected by the bounded server sensor. */
public final class SquadPlanner {
    public CombatDecision plan(CombatSnapshot s,CombatDecision d,SquadBlackboard.Role role) {
        if(role!=SquadBlackboard.Role.ANCHOR || d.intent()!=TacticalIntent.PEEK || s.tactical().intent()!=TacticalIntent.HOLD || s.self().frame().tick()-s.tactical().since()>=48)return d;
        var weapon=d.weaponAction()==ActionArbiter.WeaponAction.SHOOT?ActionArbiter.WeaponAction.NONE:d.weaponAction();
        return new CombatDecision(TacticalIntent.HOLD,false,"anchor holds cover longer",weapon,MovementCommand.hold(),d.aim(),d.frame(),d.expires());
    }
}
