package dev.zuma.tacticalnpc.core;
/** Orders constrain movement independently from the current combat/weapon state. */
public final class OrderPlanner {
    public CombatDecision plan(CombatSnapshot s,CombatDecision combat,OrderState order) {
        if(order.kind()==OrderState.Kind.ASSAULT) return combat;
        double radius=order.kind()==OrderState.Kind.HOLD_POSITION?Math.min(6,order.radius()):order.radius();
        MovementCommand move=combat.movement();TacticalIntent intent=combat.intent();boolean fire=combat.firePermitted();
        if(s.self().position().minus(order.anchor()).lengthSquared()>radius*radius) {
            move=MovementCommand.navigate(order.anchor(),MovementCommand.Locomotion.RETREAT);intent=TacticalIntent.RETREAT;fire=false;
        } else if(!s.targetKnown()) {
            if(order.kind()==OrderState.Kind.PATROL) {
                int leg=(int)Math.floorMod(s.self().frame().tick()/160+s.self().frame().owner().hashCode(),4);
                double r=Math.min(radius*.6,10);Vector3 offset=switch(leg){case 0->new Vector3(r,0,0);case 1->new Vector3(0,0,r);case 2->new Vector3(-r,0,0);default->new Vector3(0,0,-r);};
                move=MovementCommand.navigate(order.anchor().plus(offset));
            } else move=s.self().position().minus(order.anchor()).lengthSquared()>1?MovementCommand.navigate(order.anchor()):MovementCommand.hold();
        } else if(order.kind()==OrderState.Kind.HOLD_POSITION && (intent==TacticalIntent.ADVANCE || intent==TacticalIntent.SEARCH)) move=MovementCommand.hold();
        if(move.destination()!=null && move.destination().minus(order.anchor()).lengthSquared()>radius*radius) move=MovementCommand.hold();
        var weapon=!fire && combat.weaponAction()==ActionArbiter.WeaponAction.SHOOT?ActionArbiter.WeaponAction.NONE:combat.weaponAction();
        return new CombatDecision(intent,fire,combat.reason(),weapon,move,combat.aim(),combat.frame(),combat.expires());
    }
}
