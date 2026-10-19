package dev.zuma.tacticalnpc.core;

import java.util.List;
import java.util.UUID;

public final class DefendPatrolTest {
    private static final UUID OWNER=new UUID(0,5);
    private static final Vector3 ANCHOR=new Vector3(20.5,2,20.5);
    private static int checks;
    private static CombatSnapshot snapshot(long tick,Vector3 self,Observation observation) {
        return new CombatSnapshot(new SelfState(new Frame(OWNER,"test",tick,1),self,1,true,false),observation,
            new WeaponState("test",20,true,false,false,true,true,36,"AUTO"),new TerrainState(List.of(),List.of(),false,true,false),new TacticalState(TacticalIntent.PATROL,0,0,1));
    }
    private static CombatDecision idle(CombatSnapshot s) {return new CombatDecision(TacticalIntent.PATROL,false,"no contact").bind(s);}
    public static void main(String[] args) {
        var s=snapshot(100,ANCHOR,null);var planner=new OrderPlanner();var order=new OrderState(OrderState.Kind.DEFEND_AREA,ANCHOR,32);
        var d=planner.plan(s,idle(s),order);
        check(d.movement().mode()==MovementCommand.Mode.NAVIGATE && !d.movement().destination().equals(ANCHOR),"idle defend must walk to an internal patrol waypoint instead of holding anchor forever");
        var waypoint=d.movement().destination();long revision=planner.patrolRevision();
        for(int tick=101;tick<1101;tick+=10) {
            s=snapshot(tick,ANCHOR,null);d=planner.plan(s,idle(s),order);
            check(d.movement().destination().equals(waypoint) && planner.patrolRevision()==revision,"budget-deferred/no progress keeps one waypoint at "+tick);
        }
        s=snapshot(1101,waypoint,null);d=planner.plan(s,idle(s),order);
        check(d.movement().mode()==MovementCommand.Mode.HOLD,"arrival pauses");
        s=snapshot(1110,waypoint,null);d=planner.plan(s,idle(s),order);
        check(d.movement().mode()==MovementCommand.Mode.HOLD,"pause lasts at least 20 ticks");
        s=snapshot(1142,waypoint,null);d=planner.plan(s,idle(s),order);
        check(d.movement().mode()==MovementCommand.Mode.NAVIGATE && !waypoint.equals(d.movement().destination()),"new leg after a bounded pause");
        waypoint=d.movement().destination();
        s=snapshot(1143,ANCHOR,null);d=planner.plan(s,idle(s),order,.6,RouteRecovery.Status.RECOVERING);
        check(waypoint.equals(d.movement().destination()),"bounded recovery keeps original route commitment");
        s=snapshot(1205,ANCHOR,null);d=planner.plan(s,idle(s),order,.6,RouteRecovery.Status.EXHAUSTED);
        check(d.movement().mode()==MovementCommand.Mode.HOLD,"exhaustion backs off before selecting another point");
        s=snapshot(1215,ANCHOR,null);d=planner.plan(s,idle(s),order,.6,RouteRecovery.Status.EXHAUSTED);
        check(d.movement().mode()==MovementCommand.Mode.HOLD,"failed point backoff is not a per-tick retry");
        s=snapshot(1246,ANCHOR,null);d=planner.plan(s,idle(s),order);
        check(d.movement().mode()==MovementCommand.Mode.NAVIGATE && !waypoint.equals(d.movement().destination()),"unreachable waypoint replaced after backoff");
        var memory=new Observation(new UUID(0,9),new Vector3(25,2,20),ANCHOR,1246,1400,Observation.Source.VISION,1,0,false);
        s=snapshot(1250,ANCHOR,memory);var searching=new CombatDecision(TacticalIntent.SEARCH,false,"legitimate memory").bind(s);
        d=planner.plan(s,searching,order);
        check(!planner.patrolling() && d.intent()==TacticalIntent.SEARCH && d.movement().destination().equals(memory.position()),"search on retained memory interrupts patrol");
        s=snapshot(1401,ANCHOR,null);d=planner.plan(s,idle(s),order);
        check(planner.patrolling() && d.intent()==TacticalIntent.PATROL && d.movement().mode()==MovementCommand.Mode.NAVIGATE,"patrol resumes when contact/memory ends");
        planner.resetPatrol();check(!planner.patrolling(),"mode/lifecycle reset releases ephemeral route state");
        for(int radius:new int[]{4,32,128}) {
            var bounds=new DefendTerritory(ANCHOR,radius,DefendTerritory.clearance(.6));
            var p=new OrderPlanner();var q=new OrderPlanner();var o=new OrderState(OrderState.Kind.DEFEND_AREA,ANCHOR,radius);Vector3 position=ANCHOR;
            for(int leg=0;leg<20;leg++) {
                long tick=2000+leg*100;s=snapshot(tick,position,null);d=p.plan(s,idle(s),o);var same=q.plan(s,idle(s),o);
                check(d.movement().equals(same.movement()),"route randomization fixed by owner/leg "+radius+"/"+leg);
                check(d.movement().mode()==MovementCommand.Mode.NAVIGATE && bounds.contains(d.movement().destination()),"internal leg "+radius+"/"+leg);
                if(leg==0 && radius>=32)check(d.movement().destination().minus(ANCHOR).length()>10,"large defend is not legacy PATROL's ten-block ring");
                position=d.movement().destination();s=snapshot(tick+20,position,null);p.plan(s,idle(s),o);q.plan(s,idle(s),o);
            }
        }
        var old=new OrderState(OrderState.Kind.PATROL,ANCHOR,32);
        s=snapshot(2000,ANCHOR,null);d=planner.plan(s,idle(s),old);
        check(!planner.patrolling() && Math.abs(d.movement().destination().minus(ANCHOR).length()-10)<1e-7,"standalone PATROL still has its separate old cardinal route");
        var loaded=new OrderPlanner();s=snapshot(3000,ANCHOR.plus(new Vector3(8,0,0)),null);d=loaded.plan(s,idle(s),order);
        check(d.movement().mode()==MovementCommand.Mode.NAVIGATE && new DefendTerritory(ANCHOR,32,DefendTerritory.clearance(.6)).contains(d.movement().destination()),"new planner after load rebuilds patrol from persisted order/anchor/radius");
        System.out.println("PASS: "+checks+" defend patrol checks");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
}
