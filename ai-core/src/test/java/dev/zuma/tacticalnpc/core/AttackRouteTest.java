package dev.zuma.tacticalnpc.core;

import java.util.List;
import java.util.UUID;

public final class AttackRouteTest {
    private static int checks;
    private static final UUID OWNER=new UUID(0,6),TARGET=new UUID(0,7);
    private static final Vector3 ZERO=new Vector3(0,2,0);
    private static final OrderState ORDER=new OrderState(OrderState.Kind.ASSAULT,ZERO,4);
    private static CombatSnapshot snapshot(long tick,Vector3 self,Observation observation,boolean blocked) {
        return new CombatSnapshot(new SelfState(new Frame(OWNER,"test",tick,1),self,1,true,false),observation,
            new WeaponState("test",30,true,false,false,true,true,20,"AUTO"),new TerrainState(List.of(),List.of(),blocked,true,false),new TacticalState(TacticalIntent.PATROL,0,0,1));
    }
    private static CombatDecision plan(OrderPlanner p,CombatSnapshot s,RouteRecovery.Status route) {
        return p.plan(s,new CombatBrain(120,20).decide(s),ORDER,.6,route);
    }
    private static Observation observation(long tick,Vector3 point,boolean visible) {
        return new Observation(TARGET,point,ZERO,tick,500,Observation.Source.VISION,1,0,visible);
    }
    public static void main(String[] args) {
        var p=new OrderPlanner();var s=snapshot(0,ZERO,null,false);var d=plan(p,s,RouteRecovery.Status.IDLE);
        check(d.movement().mode()==MovementCommand.Mode.NAVIGATE,"attack without contact must move, not ASSAULT HOLD");
        var first=d.movement().destination();check(first.minus(ZERO).length()>4,"attack has no order radius leash");
        for(int tick=1;tick<1000;tick+=4)check(first.equals(plan(p,snapshot(tick,ZERO,null,false),RouteRecovery.Status.IDLE).movement().destination()),"budget deferral retains leg");
        d=plan(p,snapshot(1000,first,null,false),RouteRecovery.Status.IDLE);check(d.movement().mode()==MovementCommand.Mode.HOLD,"arrival pauses");
        d=plan(p,snapshot(1041,first,null,false),RouteRecovery.Status.IDLE);check(d.movement().mode()==MovementCommand.Mode.NAVIGATE && !first.equals(d.movement().destination()),"next local leg from current position");
        d=plan(p,snapshot(1042,first,null,true),RouteRecovery.Status.RECOVERING);var blocked=d.movement().destination();
        check(blocked!=null,"recovery retains idle primary");
        d=plan(p,snapshot(1103,first,null,true),RouteRecovery.Status.EXHAUSTED);check(d.movement().mode()==MovementCommand.Mode.HOLD,"exhausted idle route backs off");
        d=plan(p,snapshot(1144,first,null,false),RouteRecovery.Status.IDLE);check(d.movement().mode()==MovementCommand.Mode.NAVIGATE && !blocked.equals(d.movement().destination()),"different route after backoff");
        p=new OrderPlanner();var point=new Vector3(50,3,0);s=snapshot(10,ZERO,observation(10,point,true),false);d=plan(p,s,RouteRecovery.Status.IDLE);
        check(d.intent()==TacticalIntent.ADVANCE && d.movement().sprint(),"visible distant contact uses existing RUN");var approach=d.movement().destination();
        check(approach.x()>20 && Math.abs(approach.z())<=2.01,"useful weapon distance with bounded side offset");
        d=plan(p,snapshot(14,ZERO,observation(14,point.plus(new Vector3(1,0,0)),true),false),RouteRecovery.Status.IDLE);
        check(approach.equals(d.movement().destination()),"small visual changes do not replace committed route every plan");
        var frozen=observation(18,new Vector3(35,3,4),false);s=snapshot(20,ZERO,frozen,false);d=plan(p,s,RouteRecovery.Status.IDLE);
        check(d.intent()==TacticalIntent.SEARCH && d.movement().destination().equals(frozen.position()),"lost LOS interrupts approach for frozen legitimate point");
        for(int tick=21;tick<100;tick++)check(plan(p,snapshot(tick,ZERO,frozen,false),RouteRecovery.Status.IDLE).movement().destination().equals(frozen.position()),"hidden memory never extrapolates");
        d=plan(p,snapshot(100,frozen.position(),frozen,false),RouteRecovery.Status.IDLE);check(d.movement().mode()==MovementCommand.Mode.HOLD,"arrived memory searches by looking, no live hidden chase");
        d=plan(p,snapshot(501,frozen.position(),frozen,false),RouteRecovery.Status.IDLE);check(d.intent()==TacticalIntent.PATROL && d.movement().mode()==MovementCommand.Mode.NAVIGATE,"expired memory resumes free search");
        p.resetPatrol();check(!p.patrolling(),"lifecycle releases free route");
        p=new OrderPlanner();d=plan(p,snapshot(20,ZERO,frozen,false),RouteRecovery.Status.IDLE);
        plan(p,snapshot(21,ZERO,frozen,true),RouteRecovery.Status.RECOVERING);
        d=plan(p,snapshot(82,ZERO,frozen,true),RouteRecovery.Status.EXHAUSTED);check(d.movement().mode()==MovementCommand.Mode.HOLD,"unreachable memory exhausts once");
        d=plan(p,snapshot(400,ZERO,frozen,false),RouteRecovery.Status.IDLE);check(d.movement().mode()==MovementCommand.Mode.HOLD,"same hidden memory cannot restart path spam");
        for(var intent:new TacticalIntent[]{TacticalIntent.HOLD,TacticalIntent.PEEK,TacticalIntent.TAKE_COVER,TacticalIntent.RETREAT,TacticalIntent.BOLT,TacticalIntent.RELOAD}) {
            s=snapshot(100,ZERO,frozen,false);var cover=new CombatDecision(intent,false,"existing combat cycle").bind(s);
            check(p.plan(s,cover,ORDER,.6,RouteRecovery.Status.IDLE).equals(cover),"free search preserves hidden-contact "+intent);
        }
        var q=new OrderPlanner();var r=new OrderPlanner();
        check(plan(q,snapshot(0,ZERO,null,false),RouteRecovery.Status.IDLE).movement().equals(plan(r,snapshot(0,ZERO,null,false),RouteRecovery.Status.IDLE).movement()),"owner-seeded repeatable routes");
        q=new OrderPlanner();plan(q,snapshot(10,ZERO,observation(10,point,true),false),RouteRecovery.Status.IDLE);
        d=plan(q,snapshot(20,ZERO,frozen,true),RouteRecovery.Status.RECOVERING);
        check(d.intent()==TacticalIntent.SEARCH && d.movement().destination().equals(frozen.position()),"LOS loss during approach recovery switches to frozen search");
        System.out.println("PASS: "+checks+" attack route checks");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
}
