package dev.zuma.tacticalnpc.core;

/** Pure local cover cycle. Navigation/reachability belongs to the server adapter. */
public final class MovementPlanner {
    public CombatDecision plan(CombatSnapshot s,CombatDecision combat) {
        long tick=s.self().frame().tick(), age=tick-s.tactical().since();
        // Emergency decisions precede every old cover commitment, including a previous PEEK.
        if(combat.intent()==TacticalIntent.RETREAT || s.self().damaged() && combat.intent()==TacticalIntent.TAKE_COVER)
            return combat;
        if(combat.weaponAction()==ActionArbiter.WeaponAction.BOLT) return combat;
        if(!s.targetKnown() || s.terrain().covers().isEmpty()) return combat;
        CoverOption cover=s.terrain().covers().get(0);
        if(tick>cover.expires()) return combat;
        TacticalIntent prior=s.tactical().intent(), next=null;
        Vector3 destination=cover.position();
        boolean atCover=s.self().position().minus(destination).lengthSquared()<.8;
        boolean maintenance=s.weapon().rounds()==0;
        if(maintenance && prior==TacticalIntent.PEEK) next=atCover?TacticalIntent.HOLD:TacticalIntent.TAKE_COVER;
        else if(prior==TacticalIntent.TAKE_COVER || prior==TacticalIntent.RETREAT || prior==TacticalIntent.REPOSITION)
            next=atCover?TacticalIntent.HOLD:prior;
        else if(prior==TacticalIntent.HOLD) {
            if(cover.peek()==null && age>=48)
                return new CombatDecision(TacticalIntent.REPOSITION,false,"unusable peek timeout",combat.weaponAction()==ActionArbiter.WeaponAction.SHOOT?ActionArbiter.WeaponAction.NONE:combat.weaponAction()).bind(s);
            if(maintenance || s.weapon().busy() || age<24 || cover.peek()==null) next=TacticalIntent.HOLD;
            else { next=TacticalIntent.PEEK;destination=cover.peek(); }
        } else if(prior==TacticalIntent.PEEK) {
            if(cover.peek()==null || maintenance || s.self().damaged() || age>=60) next=TacticalIntent.REPOSITION;
            else { next=TacticalIntent.PEEK;destination=cover.peek(); }
        } else if(maintenance || s.self().damaged() || s.self().healthRatio()<.35 || prior==TacticalIntent.ENGAGE && age>=120)
            next=TacticalIntent.TAKE_COVER;
        if(next==null) return combat;
        MovementCommand move=next==TacticalIntent.HOLD || destination==null?MovementCommand.hold():MovementCommand.navigate(destination,
                next==TacticalIntent.PEEK?MovementCommand.Locomotion.PEEK:MovementCommand.Locomotion.RELOCATION);
        boolean fire=next==TacticalIntent.PEEK && combat.firePermitted();
        var weapon=fire?ActionArbiter.WeaponAction.SHOOT:combat.weaponAction()==ActionArbiter.WeaponAction.SHOOT?ActionArbiter.WeaponAction.NONE:combat.weaponAction();
        if(weapon==ActionArbiter.WeaponAction.RELOAD && !atCover && !s.weapon().busy()) weapon=ActionArbiter.WeaponAction.NONE;
        return new CombatDecision(next,fire,"local cover cycle",weapon,move,combat.aim(),combat.frame(),combat.expires());
    }
}
