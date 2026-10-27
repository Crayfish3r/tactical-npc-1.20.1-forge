package dev.zuma.tacticalnpc.core;

public final class CoreSmokeTest {
    private static int passed;

    public static void main(String[] args) {
        CombatBrain brain = new CombatBrain(100, 45);
        CombatSnapshot normal = snapshot(true, true, 10, 20, 1, 25, true,
                false, false, false, false, true, false, false, true);
        assertDecision(brain.decide(normal), TacticalIntent.ENGAGE, true);
        assertDecision(brain.decide(snapshot(false,false, 101,20,1,25,true,false,false,false,false,false,false,false,true)), TacticalIntent.PATROL, false);
        assertDecision(brain.decide(snapshot(true,false, 10,20,1,25,true,false,false,false,false,false,false,false,true)), TacticalIntent.SEARCH, false);
        assertDecision(brain.decide(snapshot(true,true, 10,20,1,0,true,false,false,false,false,true,false,false,true)), TacticalIntent.ENGAGE, false);
        assertDecision(brain.decide(snapshot(true,true, 10,20,1,25,true,false,false,false,false,true,true,false,true)), TacticalIntent.ENGAGE, false);
        assertDecision(brain.decide(snapshot(true,true, 10,20,0.2,25,true,true,true,false,false,true,false,false,true)), TacticalIntent.RETREAT, true);
        assertDecision(brain.decide(snapshot(true,true, 10,20,1,25,true,true,true,false,false,true,false,false,true)), TacticalIntent.TAKE_COVER, false);
        assertDecision(brain.decide(snapshot(true,true, 10,20,1,25,true,false,false,true,false,true,false,false,true)), TacticalIntent.FLANK, false);
        assertDecision(brain.decide(snapshot(true,true, 10,20,1,25,true,false,false,false,true,true,false,false,true)), TacticalIntent.REPOSITION, true);
        assertDecision(brain.decide(snapshot(true,true, 10,20,1,25,true,false,false,false,false,true,false,true,true)), TacticalIntent.ENGAGE, false);
        assertDecision(brain.decide(snapshot(true,true, 10,20,1,25,true,false,false,false,false,true,false,false,false)), TacticalIntent.ENGAGE, true);
        Vector3 lead = AimLead.predict(new Vector3(0,0,0),new Vector3(10,0,0),new Vector3(0,0,1), 2,10);
        check(lead.z() > 0.0 && lead.z() < 10.0, "lead target forward");
        check(AimLead.predict(new Vector3(0,0,0),new Vector3(10,0,0),new Vector3(0,0,1),-1,10).z() == 0, "unknown ballistics no lead");
        ActionArbiter arbiter = new ActionArbiter();
        CombatDecision fire = brain.decide(normal);
        check(arbiter.arbitrate(fire, new ActionArbiter.Safety(true,true,true,true,true,true,false,false,true)) == ActionArbiter.WeaponAction.SHOOT, "ready and visible can shoot");
        check(arbiter.arbitrate(fire, new ActionArbiter.Safety(true,true,false,true,true,true,false,false,true)) == ActionArbiter.WeaponAction.NONE, "stale fire decision cannot shoot hidden target");
        check(arbiter.arbitrate(fire, new ActionArbiter.Safety(true,false,true,true,true,true,false,false,true)) == ActionArbiter.WeaponAction.NONE, "dead target rejects previous decision");
        check(arbiter.arbitrate(fire, new ActionArbiter.Safety(true,true,true,false,true,true,false,false,true)) == ActionArbiter.WeaponAction.NONE, "reaction delay gates fire");
        check(arbiter.arbitrate(fire, new ActionArbiter.Safety(true,true,true,true,false,true,false,false,true)) == ActionArbiter.WeaponAction.NONE, "turning gates fire");
        check(arbiter.arbitrate(fire, new ActionArbiter.Safety(true,true,true,true,true,false,false,false,true)) == ActionArbiter.WeaponAction.NONE, "new obstruction gates fire");
        check(arbiter.arbitrate(fire, new ActionArbiter.Safety(true,true,true,true,true,true,true,false,true)) == ActionArbiter.WeaponAction.NONE, "crossing ally gates fire");
        check(arbiter.arbitrate(fire, new ActionArbiter.Safety(true,true,true,true,true,true,false,true,true)) == ActionArbiter.WeaponAction.NONE, "reload gates fire");
        check(arbiter.arbitrate(fire, new ActionArbiter.Safety(false,true,true,true,true,true,false,false,true)) == ActionArbiter.WeaponAction.NONE, "dead shooter gates fire");
        check(arbiter.arbitrate(fire, new ActionArbiter.Safety(true,true,true,true,true,true,false,false,false)) == ActionArbiter.WeaponAction.NONE, "cooldown gates fire");
        check(arbiter.arbitrate(new CombatDecision(TacticalIntent.RELOAD,false,"empty"), new ActionArbiter.Safety(true,true,true,true,true,true,false,false,true)) == ActionArbiter.WeaponAction.RELOAD, "reload wins over fire");
        var noTargetEmpty = brain.decide(snapshot(false,false,0,0,1,0,true,false,false,false,false,false,false,false,true));
        check(noTargetEmpty.intent() == TacticalIntent.PATROL && noTargetEmpty.weaponAction() == ActionArbiter.WeaponAction.RELOAD, "idle movement and reload independent");
        check(arbiter.arbitrate(noTargetEmpty, new ActionArbiter.Safety(true,false,false,false,false,false,false,false,true)) == ActionArbiter.WeaponAction.RELOAD, "reload behind wall without target");
        check(brain.decide(snapshot(true,false,5,20,1,4,true,false,false,false,false,false,false,true,true)).weaponAction() == ActionArbiter.WeaponAction.BOLT, "hidden target does not block bolt");
        check(brain.decide(snapshot(false,false,0,0,1,0,false,false,false,false,false,false,false,false,true)).weaponAction() == ActionArbiter.WeaponAction.NONE, "no reserve does not request impossible reload");
        check(arbiter.arbitrate(noTargetEmpty, new ActionArbiter.Safety(true,false,false,false,false,false,false,true,true)) == ActionArbiter.WeaponAction.NONE, "active reload not restarted");
        var coverReload = brain.decide(snapshot(true,true,0,20,1,0,true,false,true,false,false,true,false,false,true));
        check(coverReload.movement().mode()==MovementCommand.Mode.NAVIGATE && coverReload.weaponAction()==ActionArbiter.WeaponAction.RELOAD, "move to checked cover while reloading");
        var safe = new ActionArbiter.Safety(true,true,true,true,true,true,false,false,true);
        Frame current = normal.self().frame();
        check(arbiter.commands(fire,current,safe).valid(), "same runtime owns command bundle");
        check(!arbiter.commands(fire,new Frame(current.owner(),current.dimension(),current.tick(),current.generation()+1),safe).valid(), "target/unload generation rejects old movement and fire");
        check(!arbiter.commands(fire,new Frame(current.owner(),"other-dimension",current.tick(),current.generation()),safe).valid(), "dimension-scoped commands");
        check(!arbiter.commands(fire,new Frame(current.owner(),current.dimension(),current.tick()+9,current.generation()),safe).valid(), "expired TTL rejects bundle");
        check(!arbiter.commands(fire,new Frame(new java.util.UUID(0,3),current.dimension(),current.tick(),current.generation()),safe).valid(), "another NPC cannot reuse command");
        ObservationMemory memory=new ObservationMemory();
        var seen=new Observation(new java.util.UUID(0,9),new Vector3(12,2,3),new Vector3(.2,0,0),100,200,Observation.Source.VISION,1,0,true);
        memory.record(seen);
        var hidden=memory.sample(120,false,new Vector3(0,0,0),64);
        check(hidden.position().equals(seen.position()) && hidden.velocity().lengthSquared()==0,"hidden memory freezes position and velocity");
        check(!hidden.visible() && hidden.confidence()<1 && hidden.uncertainty()>0,"hidden uncertainty grows and confidence decays");
        check(memory.sample(201,false,new Vector3(0,0,0),64)==null,"expired observation forgotten");
        memory.record(seen);check(memory.sample(110,false,new Vector3(100,0,0),32)==null,"memory distance independent from detection");
        var option=new CoverOption(new Vector3(3,0,0),new Vector3(3,0,2),1200);
        var empty=snapshot(true,true,0,20,1,0,true,false,true,false,false,true,false,false,true);
        var sheltered=new CombatSnapshot(empty.self(),empty.observation(),empty.weapon(),new TerrainState(java.util.List.of(option.position()),java.util.List.of(),false,true,false,java.util.List.of(option)),TacticalState.idle());
        var coverPlan=new MovementPlanner().plan(sheltered,brain.decide(sheltered));
        check(coverPlan.intent()==TacticalIntent.TAKE_COVER && coverPlan.movement().destination().equals(option.position()) && coverPlan.weaponAction()==ActionArbiter.WeaponAction.NONE,"visible threat delays new reload until checked cover");
        var stationary=new MovementPlanner().plan(sheltered,brain.decide(sheltered),false);
        check(stationary.movement().mode()==MovementCommand.Mode.HOLD && stationary.weaponAction()==ActionArbiter.WeaponAction.RELOAD,"WAIT reloads at anchor despite a selected remote cover");
        check(arbiter.commands(stationary,sheltered.self().frame(),safe).weapon()==ActionArbiter.WeaponAction.RELOAD,"stationary native reload reaches arbitration");
        var loaded=new CombatSnapshot(sheltered.self(),sheltered.observation(),normal.weapon(),sheltered.terrain(),new TacticalState(TacticalIntent.TAKE_COVER,950,980,1));
        var stationaryLoaded=new MovementPlanner().plan(loaded,brain.decide(loaded),false);
        check(stationaryLoaded.firePermitted() && stationaryLoaded.movement().mode()==MovementCommand.Mode.HOLD,"completed WAIT reload does not inherit an unfulfilled cover commitment");
        var atCover=new CombatSnapshot(new SelfState(empty.self().frame(),option.position(),1,true,false),empty.observation(),empty.weapon(),sheltered.terrain(),new TacticalState(TacticalIntent.TAKE_COVER,950,980,1));
        check(new MovementPlanner().plan(atCover,brain.decide(atCover)).intent()==TacticalIntent.HOLD,"arrival in cover holds position through reload");
        var hiddenSnapshot=snapshot(true,false,10,20,1,25,true,false,false,false,false,true,false,false,true);
        var permit=new SuppressionPermit(hiddenSnapshot.observation(),true,true);
        var area=new SuppressionPlanner().plan(hiddenSnapshot,brain.decide(hiddenSnapshot),permit);
        var areaSafety=new ActionArbiter.Safety(true,false,false,false,true,true,false,false,true);
        check(area.aim().mode()==AimCommand.Mode.SUPPRESS_AREA && arbiter.commands(area,hiddenSnapshot.self().frame(),areaSafety,permit).weapon()==ActionArbiter.WeaponAction.SHOOT,"explicit fresh-memory suppression can fire without visible target");
        check(arbiter.commands(area,hiddenSnapshot.self().frame(),areaSafety).weapon()==ActionArbiter.WeaponAction.NONE,"suppression requires separate permit");
        check(arbiter.commands(area,hiddenSnapshot.self().frame(),new ActionArbiter.Safety(true,false,false,false,true,false,false,false,true),permit).weapon()==ActionArbiter.WeaponAction.NONE,"suppression never bypasses a wall");
        check(!new SuppressionPermit(hiddenSnapshot.observation(),false,true).valid(1000),"spent suppression quota rejects fire");
        check(!permit.valid(1041),"suppression memory is younger than general search memory");
        var smg=profileSnapshot(normal,WeaponProfile.Kind.SMG,30);
        var sniper=profileSnapshot(normal,WeaponProfile.Kind.SNIPER,30);
        var shotgun=profileSnapshot(normal,WeaponProfile.Kind.SHOTGUN,40);
        check(new CombatBrain(160,smg.weapon().effectiveRange()).decide(smg).intent()==TacticalIntent.ADVANCE,"SMG closes beyond its working distance");
        check(new CombatBrain(160,sniper.weapon().effectiveRange()).decide(sniper).movement().mode()==MovementCommand.Mode.HOLD,"sniper holds an appropriate distant firing position");
        check(!new CombatBrain(160,shotgun.weapon().effectiveRange()).decide(shotgun).firePermitted(),"shotgun cannot shoot a forty-block target");
        var advancing=new CombatBrain(160,22).decide(smg);
        var orders=new OrderPlanner();var anchor=normal.self().position();
        check(orders.plan(smg,advancing,new OrderState(OrderState.Kind.HOLD_POSITION,anchor,24)).movement().mode()==MovementCommand.Mode.HOLD,"hold order does not chase distant target");
        check(orders.plan(smg,advancing,new OrderState(OrderState.Kind.ASSAULT,anchor,24)).movement().mode()==MovementCommand.Mode.NAVIGATE,"assault advances to weapon standoff");
        var displaced=new CombatSnapshot(new SelfState(normal.self().frame(),new Vector3(12,0,0),1,true,false),normal.observation(),normal.weapon(),normal.terrain(),normal.tactical());
        var returning=orders.plan(displaced,brain.decide(displaced),new OrderState(OrderState.Kind.DEFEND_AREA,anchor,8));
        check(returning.movement().destination().equals(anchor) && !returning.firePermitted(),"defender returns inside leash without chasing fire");
        var idle=snapshot(false,false,0,0,1,0,true,false,false,false,false,false,false,false,true);
        var patrol=orders.plan(idle,brain.decide(idle),new OrderState(OrderState.Kind.PATROL,anchor,16));
        check(patrol.movement().mode()==MovementCommand.Mode.NAVIGATE && patrol.weaponAction()==ActionArbiter.WeaponAction.RELOAD,"patrol movement and native maintenance are independent");
        var board=new SquadBlackboard();var group=new java.util.UUID(0,41);var a=new java.util.UUID(0,42);var b=new java.util.UUID(0,43);
        board.heartbeat(group,a,anchor,100);board.heartbeat(group,b,anchor,100);board.report(group,a,seen,100);
        check(board.receive(group,b,anchor,109)==null,"radio reports have real delivery latency");
        var report=board.receive(group,b,anchor,110);
        check(report!=null && report.source()==Observation.Source.SQUAD && report.tick()==100 && report.position().equals(seen.position()) && !report.visible(),"squad report preserves original observation age and coordinates");
        check(board.receive(group,b,new Vector3(80,0,0),110)==null,"reports have a local communication radius");
        check(board.claim(group,a,new Vector3(3,0,3),110) && !board.claim(group,b,new Vector3(3.5,0,3),110),"cover reservation excludes another body with TTL");
        board.release(a);check(board.claim(group,b,new Vector3(3,0,3),111),"death or unload releases reservations immediately");
        board.heartbeat(group,b,anchor,112);check(board.role(group,b)==SquadBlackboard.Role.ANCHOR,"leader loss reassigns a live anchor");
        check(new SquadBlackboard().receive(group,b,anchor,112)==null,"another dimension has no shared reports");
        check(board.receive(group,b,anchor,210)==null && board.members(group)==0,"stale member and report expire without entity references");
        System.out.println("PASS: " + passed + " policy checks");
    }
    private static CombatSnapshot profileSnapshot(CombatSnapshot source,WeaponProfile.Kind kind,double distance) {
        var profile=WeaponProfile.of(kind);var old=source.observation();
        var seen=new Observation(old.subject(),new Vector3(distance,0,0),old.velocity(),old.tick(),old.expires(),old.source(),old.confidence(),old.uncertainty(),old.visible());
        return new CombatSnapshot(source.self(),seen,new WeaponState("test",25,true,false,false,true,true,profile.maximum(),"SEMI",profile,1),source.terrain(),source.tactical());
    }

    private static CombatSnapshot snapshot(boolean known, boolean visible, int last, double dist, double hp,
            int rounds, boolean reserve, boolean incoming, boolean cover, boolean flank, boolean stuck,
            boolean lof, boolean ally, boolean bolt, boolean ready) {
        var zero = new Vector3(0,0,0);
        return new CombatSnapshot(new SelfState(new Frame(new java.util.UUID(0,1),"test",1000,1),zero,hp,true,incoming),
                known ? new Observation(new java.util.UUID(0,2),new Vector3(dist,0,0),zero,1000-last,1200,Observation.Source.VISION,1,0,visible) : null,
                new WeaponState("test",rounds,reserve,bolt,false,ready,true,45,"SEMI"),
                new TerrainState(cover ? java.util.List.of(zero) : java.util.List.of(),flank ? java.util.List.of(zero) : java.util.List.of(),stuck,lof,ally),TacticalState.idle());
    }
    private static void assertDecision(CombatDecision actual, TacticalIntent expected, boolean fire) {
        check(actual.intent() == expected, "intent expected " + expected + " got " + actual.intent());
        check(actual.firePermitted() == fire, "fire permission expected " + fire + " got " + actual.firePermitted());
    }
    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        passed++;
    }
}
