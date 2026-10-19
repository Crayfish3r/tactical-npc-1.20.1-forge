package dev.zuma.tacticalnpc.core;
/** Orders constrain movement independently from the current combat/weapon state. */
public final class OrderPlanner {
    private final DefendPatrol patrol=new DefendPatrol();
    private final AttackRoute attack=new AttackRoute();
    public long patrolRevision(){return patrol.revision()+attack.revision();}
    public boolean patrolling(){return patrol.active() || attack.exploring();}
    public void resetPatrol(){patrol.reset();attack.reset();}
    public CombatDecision plan(CombatSnapshot s,CombatDecision combat,OrderState order) {
        return plan(s,combat,order,.6);
    }
    public CombatDecision plan(CombatSnapshot s,CombatDecision combat,OrderState order,double bodyWidth) {
        return plan(s,combat,order,bodyWidth,RouteRecovery.Status.IDLE);
    }
    public CombatDecision plan(CombatSnapshot s,CombatDecision combat,OrderState order,double bodyWidth,RouteRecovery.Status route) {
        if(order.kind()!=OrderState.Kind.DEFEND_AREA || s.targetKnown())patrol.suspend();
        if(order.kind()==OrderState.Kind.ASSAULT) return attack.plan(s,combat,route);
        attack.reset();
        var territory=order.kind()==OrderState.Kind.DEFEND_AREA?new DefendTerritory(order.anchor(),order.radius(),DefendTerritory.clearance(bodyWidth)):null;
        double radius=order.kind()==OrderState.Kind.HOLD_POSITION?Math.min(6,order.radius()):order.radius();
        MovementCommand move=combat.movement();TacticalIntent intent=combat.intent();boolean fire=combat.firePermitted();
        if(territory!=null?!territory.contains(s.self().position()):s.self().position().minus(order.anchor()).lengthSquared()>radius*radius) {
            patrol.suspend();
            move=MovementCommand.navigate(order.anchor(),MovementCommand.Locomotion.RETREAT);intent=TacticalIntent.RETREAT;fire=false;
        } else if(!s.targetKnown()) {
            if(order.kind()==OrderState.Kind.PATROL) {
                int leg=(int)Math.floorMod(s.self().frame().tick()/160+s.self().frame().owner().hashCode(),4);
                double r=Math.min(radius*.6,10);Vector3 offset=switch(leg){case 0->new Vector3(r,0,0);case 1->new Vector3(0,0,r);case 2->new Vector3(-r,0,0);default->new Vector3(0,0,-r);};
                move=MovementCommand.navigate(order.anchor().plus(offset));
            } else if(territory!=null) {
                move=patrol.plan(s,order,territory,route);intent=TacticalIntent.PATROL;fire=false;
            } else move=s.self().position().minus(order.anchor()).lengthSquared()>1?MovementCommand.navigate(order.anchor()):MovementCommand.hold();
        } else if(order.kind()==OrderState.Kind.HOLD_POSITION && (intent==TacticalIntent.ADVANCE || intent==TacticalIntent.SEARCH)) move=MovementCommand.hold();
        if(move.destination()!=null && (territory!=null?!territory.contains(move.destination()):move.destination().minus(order.anchor()).lengthSquared()>radius*radius)) move=MovementCommand.hold();
        var weapon=!fire && combat.weaponAction()==ActionArbiter.WeaponAction.SHOOT?ActionArbiter.WeaponAction.NONE:combat.weaponAction();
        return new CombatDecision(intent,fire,combat.reason(),weapon,move,combat.aim(),combat.frame(),combat.expires());
    }
}
