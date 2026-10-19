package dev.zuma.tacticalnpc.core;

import java.util.List;
import java.util.UUID;

/** Stage 04: territory is horizontal and never changes weapon/observation range. */
public final class DefendTerritoryTest {
    private static int checks;
    public static void main(String[] args) {
        var anchor=new Vector3(0,0,0);
        var self=new Vector3(0,10,0);
        var frame=new Frame(new UUID(0,1),"test",100,1);
        var observation=new Observation(new UUID(0,2),new Vector3(20,10,0),anchor,100,200,Observation.Source.VISION,1,0,true);
        var snapshot=new CombatSnapshot(new SelfState(frame,self,1,true,false),observation,
            new WeaponState("test",20,true,false,false,true,true,36,"AUTO"),new TerrainState(List.of(),List.of(),false,true,false),new TacticalState(TacticalIntent.ENGAGE,90,120,1));
        var combat=new CombatDecision(TacticalIntent.ENGAGE,true,"visible external target",ActionArbiter.WeaponAction.SHOOT,MovementCommand.hold(),new AimCommand(AimCommand.Mode.DIRECT,observation.position()),frame,108);
        var decision=new OrderPlanner().plan(snapshot,combat,new OrderState(OrderState.Kind.DEFEND_AREA,anchor,4));
        check(decision.firePermitted() && decision.movement().mode()==MovementCommand.Mode.HOLD,"height alone must not force retreat or block shooting an external target");
        for(double radius:new double[]{4,64,128}) {
            check(OrderState.validRadius(OrderState.Kind.DEFEND_AREA,radius),"valid defend radius "+radius);
            var territory=new DefendTerritory(anchor,radius,DefendTerritory.clearance(.6));
            check(territory.contains(new Vector3(0,500,0)),"height independent "+radius);
            check(!territory.contains(new Vector3(radius,0,0)),"body/arrival margin "+radius);
            check(!territory.permitsStep(anchor,new Vector3(radius+1,0,0)),"inside cannot exit "+radius);
            check(territory.permitsStep(new Vector3(radius+12,0,0),new Vector3(radius+4,100,0)),"outside return segment allowed "+radius);
            check(!territory.permitsStep(new Vector3(radius+4,0,0),new Vector3(radius+5,0,0)),"return cannot walk farther outside "+radius);
            check(!territory.permitsStep(new Vector3(radius+4,0,0),new Vector3(-radius-1,0,0)),"return cannot cross through and exit opposite edge "+radius);
            check(!territory.permitsStep(anchor,new Vector3(radius-1,0,0)),"arrival tolerance reserved "+radius);
        }
        for(double radius:new double[]{3,129,Double.NaN,Double.POSITIVE_INFINITY}) {
            check(!OrderState.validRadius(OrderState.Kind.DEFEND_AREA,radius),"reject invalid defend "+radius);
            try {new OrderState(OrderState.Kind.DEFEND_AREA,anchor,radius);throw new AssertionError("invalid runtime order "+radius);}
            catch(IllegalArgumentException expected){checks++;}
        }
        for(var kind:OrderState.Kind.values())if(kind!=OrderState.Kind.DEFEND_AREA)
            check(!OrderState.validRadius(kind,128) && OrderState.validRadius(kind,64),"legacy maximum unchanged: "+kind);
        var outsideMove=new CombatDecision(combat.intent(),true,combat.reason(),combat.weaponAction(),MovementCommand.navigate(new Vector3(8,10,0)),combat.aim(),frame,108);
        decision=new OrderPlanner().plan(snapshot,outsideMove,new OrderState(OrderState.Kind.DEFEND_AREA,anchor,4));
        check(decision.movement().mode()==MovementCommand.Mode.HOLD && decision.firePermitted(),"external pursuit blocked without blocking fire");
        var displaced=new CombatSnapshot(new SelfState(frame,new Vector3(140,30,0),1,true,false),observation,snapshot.weapon(),snapshot.terrain(),snapshot.tactical());
        decision=new OrderPlanner().plan(displaced,combat,new OrderState(OrderState.Kind.DEFEND_AREA,anchor,128));
        check(decision.movement().destination().equals(anchor) && decision.movement().locomotion()==MovementCommand.Locomotion.RETREAT,"displaced return requested, adapter segments it");
        System.out.println("PASS: "+checks+" defend territory checks");
    }
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);checks++;}
}
