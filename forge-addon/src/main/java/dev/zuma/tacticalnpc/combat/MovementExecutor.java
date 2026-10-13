package dev.zuma.tacticalnpc.combat;

import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Sole navigation owner. One primary and at most three alternatives per 60-tick recovery. */
public final class MovementExecutor {
    private final NpcRuntimeAdapter runtime;
    private final CoverSensor terrain;
    private final RouteRecovery recovery=new RouteRecovery();
    private MovementCommand.Mode mode;
    private Vec3 destination,checkpoint;
    private Vector3 failedPoint,alternative;
    private long nextPath,checkpointTick;
    private MovementCommand.Locomotion gait;
    public MovementExecutor(NpcRuntimeAdapter runtime) {this.runtime=runtime;terrain=new CoverSensor(runtime);}
    public boolean blocked(long tick) {return recovery.status()==RouteRecovery.Status.RECOVERING || recovery.status()==RouteRecovery.Status.EXHAUSTED;}
    public Vector3 failedDestination(long tick) {return failedPoint!=null && recovery.rejected(failedPoint,tick)?failedPoint:null;}
    public RouteRecovery.Status recoveryStatus() {return recovery.status();}
    public void contactRestored() {
        runtime.requireServer();recovery.cancelEpisode();alternative=null;destination=null;runtime.entity().getNavigation().stop();
    }
    public void apply(MovementCommand command,long tick) {
        runtime.requireServer();var npc=runtime.entity();var level=(ServerLevel)npc.level();
        if(command.locomotion()==MovementCommand.Locomotion.RETREAT && gait!=command.locomotion()) {
            recovery.clear();alternative=null;destination=null;
            // The replacement path may be budget-deferred; never keep walking the old peek meanwhile.
            npc.getNavigation().stop();checkpoint=npc.position();checkpointTick=tick;nextPath=tick;
        }
        gait=command.locomotion();
        if(recovery.expire(tick)) {npc.getNavigation().stop();destination=null;alternative=null;AiTelemetry.route(level,AiTelemetry.Route.EXHAUSTED);}
        boolean run=command.sprint() && npc.onGround() && !npc.horizontalCollision && destination!=null && !npc.getNavigation().isDone();
        // Slow for the last two metres; release native sprint before combat/ADS.
        if(destination!=null && npc.position().distanceToSqr(destination)<4) run=false;
        npc.setSprinting(runtime.operator().getProcessedSprintStatus(run));
        if(mode!=command.mode()) {
            npc.getNavigation().stop();npc.getMoveControl().strafe(0,0);mode=command.mode();destination=null;checkpoint=npc.position();checkpointTick=tick;
        }
        if(mode==MovementCommand.Mode.STRAFE) {
            npc.getMoveControl().strafe((float)command.forward(),(float)(command.sideways()*dev.zuma.tacticalnpc.config.ServerConfig.STRAFE.get()));
            return;
        }
        if(mode!=MovementCommand.Mode.NAVIGATE || command.destination()==null || !npc.onGround()) return;
        Vector3 requested=command.destination();
        if(recovery.status()==RouteRecovery.Status.RECOVERING || recovery.status()==RouteRecovery.Status.EXHAUSTED || recovery.rejected(requested,tick)) {
            if(alternative==null) {
                var before=recovery.status();alternative=recovery.alternative(vector(npc.position()),requested,tick);
                if(before!=RouteRecovery.Status.EXHAUSTED && recovery.status()==RouteRecovery.Status.EXHAUSTED) AiTelemetry.route(level,AiTelemetry.Route.EXHAUSTED);
            }
            if(alternative==null) {npc.getNavigation().stop();npc.setSprinting(false);return;}
            requested=alternative;
        }
        Vec3 point=terrain.safeStanding(vec(requested));
        if(point==null || !terrain.withinOrder(point)) {fail(requested,tick);return;}
        if(npc.position().distanceToSqr(point)<.5) {
            npc.setSprinting(false);
            if(alternative!=null) {AiTelemetry.route(level,AiTelemetry.Route.ARRIVED);recovery.accepted();alternative=null;failedPoint=null;}
            return;
        }
        if(tick-checkpointTick>=20) {
            if(checkpoint!=null && npc.position().distanceToSqr(checkpoint)<.15 && !npc.getNavigation().isDone()) {fail(vector(point),tick);return;}
            checkpoint=npc.position();checkpointTick=tick;
        }
        if(tick<nextPath || destination!=null && destination.distanceToSqr(point)<=1 && !npc.getNavigation().isDone()) {
            npc.getNavigation().setSpeedModifier(command.speed());return;
        }
        boolean recovering=alternative!=null;
        if(!AiWorkBudget.of(level).path(npc.getUUID(),recovering?4:20)) return; // Deferred is not failure.
        nextPath=tick+(recovering?4:20);
        var path=npc.getNavigation().createPath(net.minecraft.core.BlockPos.containing(point),0);
        if(path!=null && path.canReach() && safePath(path)) {
            npc.getNavigation().moveTo(path,command.speed());destination=point;
            if(recovering) AiTelemetry.route(level,AiTelemetry.Route.ALTERNATIVE);
        } else fail(vector(point),tick);
    }
    private void fail(Vector3 point,long tick) {
        runtime.entity().getNavigation().stop();runtime.entity().setSprinting(false);destination=null;failedPoint=point;alternative=null;
        recovery.fail(point,tick);nextPath=tick+4;
        AiWorkBudget.of((ServerLevel)runtime.entity().level()).pathFailed(runtime.entity().getUUID());
        AiTelemetry.route((ServerLevel)runtime.entity().level(),AiTelemetry.Route.FAILED);
    }
    public void stop(long tick) {apply(MovementCommand.hold(),tick);recovery.clear();alternative=null;failedPoint=null;}
    private boolean safePath(net.minecraft.world.level.pathfinder.Path path) {
        if(path.getNodeCount()>128) return false;
        for(int i=0;i<path.getNodeCount();i++) {var node=path.getNode(i);if(terrain.safeStanding(new Vec3(node.x+.5,node.y,node.z+.5))==null) return false;}
        return true;
    }
    private static Vector3 vector(Vec3 v) {return new Vector3(v.x,v.y,v.z);}
    private static Vec3 vec(Vector3 v) {return new Vec3(v.x(),v.y(),v.z());}
}
