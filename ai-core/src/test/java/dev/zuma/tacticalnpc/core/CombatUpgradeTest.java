package dev.zuma.tacticalnpc.core;

import java.util.*;

public final class CombatUpgradeTest {
    private static int checks;
    private static final Vector3 ZERO=new Vector3(0,0,0);
    private static Observation seen(long tick,boolean visible){return new Observation(new UUID(0,2),new Vector3(96,0,0),new Vector3(0,0,.25),tick,tick+320,Observation.Source.VISION,1,0,visible);}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    public static void main(String[] args){
        var ball=new Ballistics(20,.01,.01,100);
        check(ball.valid() && ball.maximumRange()>128,"native life/speed reach exceeds preferred distance and falloff");
        check(!Ballistics.UNKNOWN.valid(),"no invented speed");
        var o=seen(10,true);var aim=ball.aim(ZERO,o,12);
        check(aim.z()>1 && aim.y()>0,"observed lateral motion plus native drop lead");
        check(ball.aim(ZERO,seen(10,false),12).equals(o.position()),"hidden velocity never extrapolated");
        check(new Ballistics(Double.NaN,0,0,20).maximumRange()==16,"invalid metadata conservative fallback");
        // Integrate the audited native recurrence and find closest moving-target approach.
        Vector3 velocity=aim.times(ball.speed()/aim.length()),position=ZERO;double closest=1000;
        for(int i=0;i<20;i++){
            var next=position.plus(velocity);
            for(int k=0;k<=100;k++){
                double f=k/100.0;var target=o.position().plus(o.velocity().times(i+f));
                closest=Math.min(closest,position.plus(next.minus(position).times(f)).minus(target).length());
            }
            position=next;velocity=velocity.times(1-ball.friction()).plus(new Vector3(0,-ball.gravity(),0));
        }
        check(closest<.15,"ballistic intercept verified by independent discrete projectile integration: "+closest);
        var weapon=new WeaponState("rifle",30,true,false,false,true,true,ball.maximumRange(),"AUTO",WeaponProfile.of(WeaponProfile.Kind.AR),1,ball,40);
        var frame=new Frame(new UUID(0,1),"test",10,1);
        var snapshot=new CombatSnapshot(new SelfState(frame,ZERO,1,true,false),o,weapon,new TerrainState(List.of(),List.of(),false,true,false),TacticalState.idle());
        var decision=new CombatBrain(320,weapon.effectiveRange()).decide(snapshot);
        check(decision.firePermitted() && decision.intent()==TacticalIntent.ENGAGE,"96-block shot independent of 36-block preference and 40-block falloff");
        var close=new Observation(o.subject(),new Vector3(5,0,0),ZERO,10,330,Observation.Source.VISION,1,0,true);
        var closeSnapshot=new CombatSnapshot(snapshot.self(),close,weapon,snapshot.terrain(),snapshot.tactical());
        var retreat=new CombatBrain(320,weapon.effectiveRange()).decide(closeSnapshot);
        check(retreat.intent()==TacticalIntent.RETREAT && retreat.movement().destination().x()<0 && !retreat.movement().sprint(),"weapon standoff retreats along a bounded native path away from known contact while retaining native fire arbitration");
        var cycle=new SuppressionCycle();cycle.observe(o,10);cycle.observe(seen(10,false),11);
        for(int i=0;i<8;i++)cycle.round(12+i,8,12);
        check(cycle.rounds()==8 && !cycle.ready(20,160,48),"actual burst quota pauses fire");
        check(cycle.ready(31,160,48),"burst resumes after configured pause");
        cycle.observe(seen(32,false),32);
        check(cycle.rounds()==8,"fresh hidden report cannot refill spent rounds");
        check(!cycle.ready(171,160,48),"duration expires independent of refreshed reports");
        for(int i=8;i<48;i++)cycle.round(40,100,12);
        check(!cycle.ready(80,160,48),"total hidden-round budget enforced");
        cycle.observe(seen(81,true),81);check(cycle.ready(82,160,48) && cycle.rounds()==0,"genuine visual reacquire starts a new contact budget");
        var pellets=new SuppressionCycle();pellets.observe(o,10);
        for(int i=0;i<10;i++)pellets.nativeProjectile(1,11,8,12);
        check(pellets.rounds()==1,"ten native pellets from one cartridge count one spent round");
        pellets.nativeProjectile(2,11,8,12);check(pellets.rounds()==2,"two native cartridges in one server tick are distinct");
        var hidden=seen(10,false);var permit=new SuppressionPermit(hidden,true,true,hidden.position().plus(new Vector3(0,0,6)),160);
        check(permit.valid(100),"extended pressure authorized by fresh bounded known-area permit");
        check(!new SuppressionPermit(hidden,true,true,hidden.position().plus(new Vector3(0,0,9)),160).valid(20),"sector cannot leave registered contact region");
        var hiddenSnapshot=new CombatSnapshot(snapshot.self(),hidden,weapon,snapshot.terrain(),snapshot.tactical());
        var utility=new TacticalUtility();
        check(utility.flank(hiddenSnapshot,SquadBlackboard.Role.FLANKER,true),"occluded target favors flank utility");
        check(utility.flank(snapshot,SquadBlackboard.Role.FLANKER,true),"brief LOS restoration does not oscillate committed flank");
        var damaged=new CombatSnapshot(new SelfState(frame,ZERO,.2,true,true),hidden,weapon,snapshot.terrain(),snapshot.tactical());
        check(!utility.flank(damaged,SquadBlackboard.Role.FLANKER,true),"health emergency interrupts commitment");
        check(TacticalUtility.preferTarget(10000,1000) && !TacticalUtility.preferTarget(10000,9000),"target utility requires a meaningful distance advantage, preventing target oscillation");
        check(TacticalUtility.preferTarget(720,576),"an unsafe adjacent-lane target cannot veto its ten-percent-closer clear contact forever");
        var board=new SquadBlackboard();var group=new UUID(0,3);var sender=new UUID(0,4);var receiver=new UUID(0,5);
        board.heartbeat(group,sender,ZERO,10);board.heartbeat(group,receiver,ZERO,10);board.report(group,sender,o,10,2);
        check(board.receive(group,receiver,new Vector3(90,0,0),11,128)==null,"Unfair radio retains a nonzero delay");
        check(board.receive(group,receiver,new Vector3(90,0,0),12,128)!=null,"configured fast local report delivered");
        var hint=new Observation(o.subject(),o.position(),ZERO,20,80,Observation.Source.UNFAIR_HINT,.7,5,false);
        board.report(group,sender,hint,20,2);
        check(board.receive(group,receiver,ZERO,22,128).source()==Observation.Source.SQUAD && board.receive(group,receiver,ZERO,22,128).tick()==10,"hint never relayed as visual report");
        System.out.println("PASS: "+checks+" combat upgrade checks");
    }
}
