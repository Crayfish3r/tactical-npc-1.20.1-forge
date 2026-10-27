package dev.zuma.tacticalnpc.core;

import java.util.List;
import java.util.UUID;

/** Temporal regressions for alpha-1. PASS requires desired behaviour, never the old defects. */
public final class AlphaRegressionTest {
    private static final Vector3 ZERO=new Vector3(0,0,0);
    private static final UUID OWNER=new UUID(0,1),TARGET=new UUID(0,2);
    private static final CombatBrain BRAIN=new CombatBrain(160,36);
    private static int checks;
    private static CombatSnapshot sample(long tick,double health,boolean blocked,boolean ready,TacticalIntent prior,long since,CoverOption cover) {
        return new CombatSnapshot(new SelfState(new Frame(OWNER,"alpha",tick,1),ZERO,health,true,false),
            new Observation(TARGET,new Vector3(20,1,0),ZERO,tick,tick+160,Observation.Source.VISION,1,0,true),
            new WeaponState("test",25,true,false,false,ready,true,36,"AUTO"),
            new TerrainState(cover==null?List.of():List.of(cover.position()),List.of(),blocked,true,false,cover==null?List.of():List.of(cover)),new TacticalState(prior,since,since+20,1));
    }
    public static void main(String[] args) {
        var planner=new MovementPlanner();var cover=new CoverOption(ZERO,new Vector3(0,0,3),2000);
        for(var prior:List.of(TacticalIntent.PEEK,TacticalIntent.HOLD,TacticalIntent.TAKE_COVER,TacticalIntent.REPOSITION)) {
            var healthy=sample(999,1,false,true,prior,990,cover);planner.plan(healthy,BRAIN.decide(healthy));
            var critical=sample(1000,.1,false,true,prior,990,cover);var d=planner.plan(critical,BRAIN.decide(critical));
            check(d.intent()==TacticalIntent.RETREAT && d.movement().destination().equals(cover.position()) && d.firePermitted(),"R1 emergency interrupts "+prior+" while permitting safe covering fire");
        }
        var peek=sample(1100,1,false,true,TacticalIntent.PEEK,1098,cover);planner.plan(peek,BRAIN.decide(peek));
        for(var weapon:List.of(new WeaponState("test",0,true,false,false,true,true,36,"AUTO"),new WeaponState("test",25,true,true,false,true,true,36,"AUTO"))) {
            var urgent=new CombatSnapshot(peek.self(),peek.observation(),weapon,peek.terrain(),peek.tactical());var d=planner.plan(urgent,BRAIN.decide(urgent));
            check(d.intent()!=TacticalIntent.PEEK && !d.firePermitted() && d.weaponAction()==(weapon.boltNeeded()?ActionArbiter.WeaponAction.BOLT:ActionArbiter.WeaponAction.RELOAD),"urgent reload/bolt interrupts an active peek");
        }
        var blocked=sample(1000,1,true,true,TacticalIntent.ADVANCE,990,null);
        var fallback=planner.plan(blocked,BRAIN.decide(blocked));
        check(fallback.intent()==TacticalIntent.REPOSITION && fallback.movement().mode()==MovementCommand.Mode.NAVIGATE && fallback.movement().destination()!=null,"R2 no-cover recovery actually moves");
        var routes=new RouteRecovery();var bad=new Vector3(8,0,0);routes.fail(bad,100);
        check(routes.rejected(bad,101) && !routes.rejected(new Vector3(0,0,3),101),"backoff is destination-specific");
        var a=routes.alternative(ZERO,bad,104);check(a!=null && !a.equals(bad),"first alternative before 160 ticks");routes.fail(a,105);
        var b=routes.alternative(ZERO,bad,108);check(b!=null && !b.equals(a),"second bounded alternative");routes.fail(b,109);
        var c=routes.alternative(ZERO,bad,112);check(c!=null && !c.equals(b),"third bounded alternative");routes.fail(c,113);
        check(routes.alternative(ZERO,bad,116)==null && routes.status()==RouteRecovery.Status.EXHAUSTED,"bounded attempts end honestly");
        check(routes.alternative(ZERO,bad,117)==null,"no immediate restart after exhaustion");
        routes.cancelEpisode();check(routes.status()==RouteRecovery.Status.IDLE && routes.rejected(c,117),"visual interruption cancels episode but preserves failed-point backoff");
        routes.clear();routes.fail(bad,200);check(routes.alternative(ZERO,bad,204)!=null,"new recovery");check(routes.expire(260) && routes.status()==RouteRecovery.Status.EXHAUSTED,"explicit timeout even if path budget is deferred");
        routes.clear();routes.fail(bad,300);check(routes.alternative(ZERO,bad,304)!=null,"success episode starts");routes.accepted();check(routes.status()==RouteRecovery.Status.ACCEPTED,"reachable alternative terminates recovery");
        var noPeek=new CoverOption(ZERO,null,2000);
        for(int age=0;age<=48;age++) {
            var held=sample(1000+age,1,false,true,TacticalIntent.HOLD,1000,noPeek);var d=planner.plan(held,BRAIN.decide(held));
            check(age<48?d.intent()==TacticalIntent.HOLD:d.intent()==TacticalIntent.REPOSITION && d.movement().mode()==MovementCommand.Mode.NAVIGATE,"R5 unusable cover bounded at age "+age);
        }
        var cached=BRAIN.decide(sample(1000,1,false,false,TacticalIntent.ENGAGE,990,null));var arbiter=new ActionArbiter();
        for(int offset=0;offset<4;offset++) {
            var safety=new ActionArbiter.Safety(true,true,true,true,true,true,false,false,offset>0);
            check(arbiter.commands(cached,new Frame(OWNER,"alpha",1000+offset,1),safety).weapon()==(offset==0?ActionArbiter.WeaponAction.NONE:ActionArbiter.WeaponAction.SHOOT),"R4 fresh cooldown without tactical replan "+offset);
        }
        check(arbiter.commands(cached,new Frame(OWNER,"alpha",1001,1),new ActionArbiter.Safety(true,true,false,true,true,false,false,false,true)).weapon()==ActionArbiter.WeaponAction.NONE,"cached intent cannot shoot a hidden target");
        var contact=new ContactReadiness();
        check(!contact.visual(TARGET,100,1,3,1,60),"first visual sample starts perception");
        check(contact.visual(TARGET,101,1,3,1,60) && !contact.ready(103),"perception and reaction remain distinct");
        check(contact.ready(104),"first ready after four ticks");contact.blocked();
        check(!contact.ready(105) && TARGET.equals(contact.subject()),"corner blocks fire immediately but retains UUID");
        check(contact.visual(TARGET,110,1,3,1,60) && !contact.ready(110) && contact.reacquiring(),"reacquire does not repeat first perception");
        check(contact.ready(111),"reacquire one tick is faster than first four ticks");
        contact.deferred();check(!contact.ready(112) && TARGET.equals(contact.subject()),"DEFERRED fails fire closed but retains identity");
        check(contact.visual(TARGET,113,1,3,1,60) && contact.ready(113),"DEFERRED does not restart readiness");
        contact.blocked();check(!contact.visual(TARGET,180,1,3,1,60),"stale UUID needs first-contact perception");
        check(contact.visual(TARGET,181,1,3,1,60) && !contact.ready(181) && !contact.reacquiring(),"stale contact repeats reaction");
        var other=new UUID(0,3);check(!contact.visual(other,182,1,3,1,60),"new UUID never inherits readiness");
        for(int i=1;i<4;i++) {
            var fresh=new Observation(TARGET,new Vector3(20,1,i),ZERO,1000+i,1200,Observation.Source.VISION,1,0,true);
            var tracking=cached.track(fresh,TARGET,1000+i);check(tracking.aim().point().equals(fresh.position()) && tracking.frame().equals(cached.frame()),"aim updates between tactic ticks "+i);
            var hidden=new Observation(TARGET,new Vector3(90,1,i),ZERO,1000+i,1200,Observation.Source.VISION,1,0,false);
            check(cached.track(hidden,TARGET,1000+i).aim().equals(cached.aim()),"hidden point never enters DIRECT tracking");
            check(cached.track(fresh,other,1000+i).aim().equals(cached.aim()),"different UUID cannot refresh old DIRECT aim");
        }
        check(MovementCommand.navigate(bad,MovementCommand.Locomotion.RUN).sprint() && MovementCommand.navigate(bad,MovementCommand.Locomotion.RUN).speed()>MovementCommand.navigate(bad).speed(),"pursuit gait faster than patrol; not a blocks/s claim");
        check(!MovementCommand.navigate(bad,MovementCommand.Locomotion.PEEK).sprint(),"peek never sprints");
        System.out.println("PASS: "+checks+" alpha temporal regression checks (R1/R2/R4/R5, contact, tracking, recovery, locomotion)");
    }
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);checks++;}
}
