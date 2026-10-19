package dev.zuma.tacticalnpc.core;

import java.util.SplittableRandom;
import java.util.UUID;

/** Ephemeral idle route state owned by the existing order planner, not a second controller. */
public final class DefendPatrol {
    private OrderState order;
    private UUID owner;
    private SplittableRandom random;
    private Vector3 waypoint,rejected;
    private long pauseUntil,rejectedUntil,revision;
    private double heading;
    private boolean active,recovering;
    public long revision(){return revision;}
    public boolean active(){return active;}
    public void suspend() {
        if(active){active=false;waypoint=null;pauseUntil=0;recovering=false;revision++;}
    }
    public void reset(){suspend();order=null;owner=null;random=null;rejected=null;}
    public MovementCommand plan(CombatSnapshot s,OrderState next,DefendTerritory territory,RouteRecovery.Status route) {
        long tick=s.self().frame().tick();Vector3 self=s.self().position();
        if(!next.equals(order) || !s.self().frame().owner().equals(owner)) {
            reset();order=next;owner=s.self().frame().owner();
            random=new SplittableRandom(owner.getLeastSignificantBits()^Long.rotateLeft(owner.getMostSignificantBits(),17));
            heading=random.nextDouble()*Math.PI*2;
        }
        if(!active){active=true;revision++;}
        if(waypoint!=null) {
            recovering|=route==RouteRecovery.Status.RECOVERING;
            boolean failed=route==RouteRecovery.Status.EXHAUSTED || recovering && route==RouteRecovery.Status.ACCEPTED;
            double x=self.x()-waypoint.x(),z=self.z()-waypoint.z();
            if(failed || x*x+z*z<=1.21) {
                if(failed){rejected=waypoint;rejectedUntil=tick+160;}
                waypoint=null;recovering=false;pauseUntil=tick+20+random.nextInt(21);revision++;
            }
        }
        if(waypoint==null && tick>=pauseUntil) {
            double leg=Math.min(12,(next.radius()-territory.clearance())*.75);
            heading+=(random.nextDouble()-.5)*.7;
            for(int attempt=0;attempt<8;attempt++) {
                double angle=attempt==0?heading:Math.atan2(next.anchor().z()-self.z(),next.anchor().x()-self.x())+(random.nextDouble()-.5)*Math.PI;
                double x=self.x()+Math.cos(angle)*leg,z=self.z()+Math.sin(angle)*leg;
                double dx=x-next.anchor().x(),dz=z-next.anchor().z(),distance=Math.sqrt(dx*dx+dz*dz),limit=next.radius()-territory.clearance()-.5;
                if(distance>limit){x=next.anchor().x()+dx*limit/distance;z=next.anchor().z()+dz*limit/distance;}
                var candidate=new Vector3(Math.floor(x)+.5,self.y(),Math.floor(z)+.5);
                dx=candidate.x()-self.x();dz=candidate.z()-self.z();
                if(!territory.contains(candidate) || dx*dx+dz*dz<2.25 || rejected!=null && tick<rejectedUntil && candidate.minus(rejected).lengthSquared()<1)continue;
                waypoint=candidate;heading=angle;revision++;break;
            }
            if(waypoint==null)pauseUntil=tick+20+random.nextInt(21);
        }
        // No timeout on a budget-deferred primary: without failure evidence keep the same waypoint.
        return waypoint==null?MovementCommand.hold():MovementCommand.navigate(waypoint);
    }
}
