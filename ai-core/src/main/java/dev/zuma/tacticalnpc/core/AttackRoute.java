package dev.zuma.tacticalnpc.core;

import java.util.SplittableRandom;
import java.util.UUID;

/** Local free routes from immutable observations only; navigation remains adapter-owned. */
public final class AttackRoute {
    private UUID owner,subject;
    private SplittableRandom random;
    private Vector3 waypoint,observed;
    private int phase=-1,failures;
    private long revision,pauseUntil,committedAt,memoryTick;
    private double heading,side;
    private boolean recovering,finished;
    public long revision(){return revision;}
    public boolean exploring(){return phase==0;}
    public void reset(){if(phase!=-1)revision++;phase=-1;waypoint=null;observed=null;subject=null;recovering=false;finished=false;pauseUntil=0;failures=0;}
    public CombatDecision plan(CombatSnapshot s,CombatDecision combat,RouteRecovery.Status route) {
        int next=!s.targetKnown()?0:!s.targetVisible() && combat.intent()==TacticalIntent.SEARCH?2:combat.intent()==TacticalIntent.ADVANCE?1:-1;
        // Preserve emergency retreat, cover, weapon maintenance and in-range combat policy.
        if(s.targetKnown() && (combat.firePermitted() || combat.intent()==TacticalIntent.RETREAT || combat.intent()==TacticalIntent.TAKE_COVER || combat.intent()==TacticalIntent.BOLT || combat.intent()==TacticalIntent.RELOAD))next=-1;
        if(!combat.firePermitted() && combat.intent()==TacticalIntent.REPOSITION && (phase==1 || phase==2) && route!=RouteRecovery.Status.IDLE)next=s.targetVisible()?1:2;
        if(next<0){reset();return combat;}
        long tick=s.self().frame().tick();Vector3 self=s.self().position();
        if(!s.self().frame().owner().equals(owner)) {
            reset();owner=s.self().frame().owner();random=new SplittableRandom(owner.getLeastSignificantBits()^Long.rotateLeft(owner.getMostSignificantBits(),23));heading=random.nextDouble()*Math.PI*2;
        }
        UUID contact=next==0?null:s.observation().subject();
        if(next!=phase || !java.util.Objects.equals(contact,subject) || next==2 && (memoryTick!=s.observation().tick() || !s.observation().position().equals(observed))) {
            reset();phase=next;subject=contact;observed=next==0?null:s.observation().position();memoryTick=next==2?s.observation().tick():0;revision++;
        }
        if(waypoint!=null) {
            recovering|=route==RouteRecovery.Status.RECOVERING;
            boolean failed=route==RouteRecovery.Status.EXHAUSTED || recovering && route==RouteRecovery.Status.ACCEPTED;
            if(failed || horizontalSquared(self,waypoint)<=1.21) {
                waypoint=null;recovering=false;revision++;
                if(failed){failures++;pauseUntil=tick+(failures>=3?160:40);if(phase==2)finished=true;heading+=Math.PI/2;}
                else {failures=0;pauseUntil=tick+(phase==0?20+random.nextInt(21):0);if(phase==2)finished=true;}
            }
        }
        // Only fresh visual displacement can replace a held approach, at most once per 20 ticks.
        if(phase==1 && waypoint!=null && !recovering && tick-committedAt>=20 && horizontalSquared(observed,s.observation().position())>=16) {
            waypoint=null;revision++;
        }
        if(waypoint==null && !finished && tick>=pauseUntil) {
            if(phase==0) {
                heading+=(random.nextDouble()-.5)*1.2;
                waypoint=new Vector3(Math.floor(self.x()+Math.cos(heading)*12)+.5,self.y(),Math.floor(self.z()+Math.sin(heading)*12)+.5);
            } else if(phase==2) waypoint=observed;
            else {
                observed=s.observation().position();var base=combat.movement().destination();
                if(combat.intent()!=TacticalIntent.ADVANCE || base==null) { // Recovery still uses the observed weapon standoff.
                    base=new CombatDecision(TacticalIntent.ADVANCE,false,"held approach").bind(s).movement().destination();
                }
                var toward=observed.minus(self);double length=Math.hypot(toward.x(),toward.z());side=random.nextBoolean()?2:-2;
                waypoint=length<.01?base:base.plus(new Vector3(-toward.z()*side/length,0,toward.x()*side/length));
            }
            committedAt=tick;revision++;
        }
        var move=waypoint==null?MovementCommand.hold():MovementCommand.navigate(waypoint,phase==0?MovementCommand.Locomotion.WALK:MovementCommand.Locomotion.RUN);
        var intent=phase==0?TacticalIntent.PATROL:phase==1?TacticalIntent.ADVANCE:TacticalIntent.SEARCH;
        var weapon=combat.weaponAction()==ActionArbiter.WeaponAction.SHOOT?ActionArbiter.WeaponAction.NONE:combat.weaponAction();
        return new CombatDecision(intent,false,phase==0?"free local exploration":phase==1?"committed weapon approach":"search frozen observation",weapon,move,combat.aim(),combat.frame(),combat.expires());
    }
    private static double horizontalSquared(Vector3 a,Vector3 b){double x=a.x()-b.x(),z=a.z()-b.z();return x*x+z*z;}
}
