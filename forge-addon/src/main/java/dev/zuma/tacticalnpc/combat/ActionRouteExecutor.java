package dev.zuma.tacticalnpc.combat;

import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.Vec3;
import static dev.zuma.tacticalnpc.combat.ActionGeometry.*;
import static dev.zuma.tacticalnpc.core.ActionRoute.*;

/** Owned exclusively by MovementExecutor. Physical movement remains native MoveControl/travel. */
final class ActionRouteExecutor {
    private final NpcRuntimeAdapter runtime;private final ActionGeometry g;
    private ActionRoutePlanner search;private ActionRoute route;private Vector3 goal;
    private int index,walkEnd;private long lastTick=-1,started,nextPath,lastProgress;
    private Vec3 checkpoint,pathPoint;private Clock clock=new Clock();
    private Long reserved;private boolean entering,cancelAfterTransition;
    private Status status=Status.SUCCESS;private String reason="idle",lastFailure="none";
    private int completed,opened,waited,failed,climbed;
    ActionRouteExecutor(NpcRuntimeAdapter runtime){this.runtime=runtime;g=new ActionGeometry(runtime);}
    boolean active(){return search!=null || route!=null;}
    Status status(){return status;}Vector3 goal(){return goal;}
    String diagnostics(){return status+":"+reason+" step="+index+" completed="+completed+" opened="+opened+" climbed="+climbed+" waiting="+waited+" failed="+failed+" lastFailure="+lastFailure;}
    void start(Vec3 destination,long tick){
        stop("replaced");goal=vector(destination);search=new ActionRoutePlanner(runtime,destination);started=tick;status=Status.WAITING_BUDGET;reason="local search";
    }
    void cancelSafely(){
        // A grounded actor may still occupy the door throat. Retain ownership until
        // the started crossing is physically clear, just as for a ladder landing.
        if(route!=null && index<route.transitions().size() &&
                (climb(route.transitions().get(index)) || entering && route.transitions().get(index).kind()==Kind.OPEN_DOOR) &&
                !transitionArrived(route.transitions().get(index)))cancelAfterTransition=true;
        else stop("cancelled");
    }
    private static boolean climb(Transition t){return t.kind()==Kind.CLIMB_UP || t.kind()==Kind.CLIMB_DOWN;}
    void tick(long tick,double speed){
        runtime.requireServer();if(!active() || tick==lastTick)return;lastTick=tick;
        var n=runtime.entity();n.setSprinting(runtime.operator().getProcessedSprintStatus(false));
        if(!n.isAlive() || n.isRemoved()){stop("removed");return;}
        if(search!=null){
            n.getNavigation().stop();zeroInput();search.advance();status=Status.WAITING_BUDGET;
            if(tick-started>100){failure("search deadline");return;}
            if(!search.done())return;
            route=search.result();search=null;
            if(route==null){failure("no loaded local action route");return;}
            index=0;resetStep(tick);reason="route ready";
        }
        var step=route.transitions().get(index);
        if(reserved!=null && !TransitionResources.of(g.level).acquire(reserved,n.getUUID(),tick)){failure("lost lease");return;}
        // Reserve before entering the approach cell, so queued NPCs do not occupy the throat.
        Transition narrow=null;
        for(int i=index;i<route.transitions().size();i++){
            var s=route.transitions().get(i);if(s.kind()!=Kind.WALK){narrow=s;break;}
        }
        if(narrow!=null && n.position().distanceToSqr(vec(narrow.approach()))<9){
            long key=BlockPos.containing(vec(narrow.resource())).asLong();
            if(!TransitionResources.of(g.level).acquire(key,n.getUUID(),tick)){waiting(Status.WAITING_RESOURCE,"narrow transition occupied",tick,step);return;}
            reserved=key;
        }
        status=Status.RUNNING;
        if(!g.permits(n.position(),vec(step.exit()))){failure("order boundary changed");return;}
        if(step.kind()==Kind.WALK){
            walkEnd=index;while(walkEnd+1<route.transitions().size() && route.transitions().get(walkEnd+1).kind()==Kind.WALK)walkEnd++;
            var exit=vec(route.transitions().get(walkEnd).exit());
            if(arrived(exit)){index=walkEnd;complete(tick);return;}
            walk(exit,tick,speed,step);return;
        }
        if(reserved==null){
            long key=BlockPos.containing(vec(step.resource())).asLong();
            if(!TransitionResources.of(g.level).acquire(key,n.getUUID(),tick)){waiting(Status.WAITING_RESOURCE,"narrow transition occupied",tick,step);return;}
            reserved=key;
        }
        if(!entering && n.position().distanceToSqr(vec(step.approach()))>.24){walk(vec(step.approach()),tick,speed,step);return;}
        entering=true;n.getNavigation().stop();pathPoint=null;
        if(step.kind()==Kind.OPEN_DOOR){
            var p=BlockPos.containing(vec(step.resource()));
            if(!RouteInteractions.VANILLA.supports(g.level,p)){failure("door removed or unsupported");return;}
            var floor=g.level.getBlockState(p.below());
            if(!floor.isFaceSturdy(g.level,p.below(),net.minecraft.core.Direction.UP) || !g.level.getFluidState(p.below()).isEmpty() || floor.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)){
                failure("door support changed");return;
            }
            if(!g.level.getBlockState(p).getValue(DoorBlock.OPEN)){
                if(!RouteInteractions.VANILLA.open(n,p)){failure("door interaction denied");return;}opened++;
            }
            if(!g.level.getBlockState(p.above()).getValue(DoorBlock.OPEN) || !g.clear(vec(step.entry())) || !g.clear(vec(step.exit()))){failure("open door passage obstructed");return;}
            if(transitionArrived(step)){complete(tick);return;}
            steer(vec(step.exit()),speed);progress(tick,step);return;
        }
        var ladder=g.ladder(BlockPos.containing(vec(step.resource())));
        if(ladder==null){failure("ladder discontinuous or obstructed");return;}
        var exit=vec(step.exit());
        double requiredTop=step.kind()==Kind.CLIMB_UP?exit.y-1:step.entry().y()-1;
        if(ladder.top().getY()<requiredTop || ladder.bottom().getY()!=BlockPos.containing(vec(step.resource())).getY()){failure("ladder extent changed");return;}
        if(g.standing(BlockPos.containing(exit))==null){failure("ladder landing obstructed");return;}
        if(arrived(exit)){complete(tick);return;}
        if(step.kind()==Kind.CLIMB_UP){
            if(n.getY()>=exit.y-.08)steer(exit,speed);
            else{
                steer(new Vec3(ladder.bottomPoint().x,n.getY(),ladder.bottomPoint().z),speed);
                if(n.onClimbable())n.getJumpControl().jump();
            }
        }else{
            // Gravity and vanilla ladder descent clamp own Y. Do not jump or inject velocity.
            if(n.getY()<=exit.y+.12)steer(exit,speed);
            else steer(new Vec3(ladder.bottomPoint().x,n.getY(),ladder.bottomPoint().z),speed);
        }
        progress(tick,step);
    }
    private void walk(Vec3 exit,long tick,double speed,Transition step){
        var n=runtime.entity();
        if(g.standing(BlockPos.containing(exit))==null){failure("walk support or clearance changed");return;}
        if(pathPoint==null || pathPoint.distanceToSqr(exit)>.01 || n.getNavigation().isDone()){
            if(n.position().distanceToSqr(exit)<2.25 && clearWalkSegment(n.position(),exit)){
                steer(exit,speed);progress(tick,step);return;
            }
            if(tick<nextPath || !AiWorkBudget.of(g.level).path(n.getUUID(),4)){waiting(Status.WAITING_BUDGET,"native path budget",tick,step);return;}
            nextPath=tick+4;var path=n.getNavigation().createPath(BlockPos.containing(exit),0);
            if(path==null || !path.canReach() || path.getNodeCount()>128 || !g.terrain.withinOrderPath(path)){failure("walk native path rejected");return;}
            for(int i=0;i<path.getNodeCount();i++)if(g.standing(path.getNodePos(i))==null){failure("walk native path crosses unsafe node");return;}
            n.getNavigation().moveTo(path,speed);pathPoint=exit;
        }else n.getNavigation().setSpeedModifier(speed);
        progress(tick,step);
    }
    private void steer(Vec3 p,double speed){runtime.entity().getMoveControl().setWantedPosition(p.x,p.y,p.z,speed);}
    private boolean clearWalkSegment(Vec3 from,Vec3 to){
        int count=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.2));
        if(count>8)return false;
        for(int i=0;i<=count;i++)if(!g.clear(from.lerp(to,(double)i/count)))return false;
        return true;
    }
    private boolean arrived(Vec3 p){var n=runtime.entity();return n.onGround() && Math.abs(n.getY()-p.y)<.12 && n.position().subtract(p).horizontalDistance()<.32 && g.standing(BlockPos.containing(p))!=null;}
    private boolean transitionArrived(Transition step){
        if(!arrived(vec(step.exit())))return false;
        if(step.kind()!=Kind.OPEN_DOOR)return true;
        var p=BlockPos.containing(vec(step.resource()));var box=runtime.entity().getBoundingBox();
        if(step.exit().x()>step.entry().x())return box.minX>p.getX()+1;
        if(step.exit().x()<step.entry().x())return box.maxX<p.getX();
        if(step.exit().z()>step.entry().z())return box.minZ>p.getZ()+1;
        return box.maxZ<p.getZ();
    }
    private void progress(long tick,Transition step){
        reason=step.kind().toString();
        if(checkpoint==null || runtime.entity().position().distanceToSqr(checkpoint)>.025){checkpoint=runtime.entity().position();lastProgress=tick;}
        if(clock.advance(tick,Status.RUNNING,step.timeoutTicks()))failure("transition deadline: "+step.kind());
        else if(tick-lastProgress>60)failure("no physical progress: "+step.kind());
    }
    private void waiting(Status wait,String why,long tick,Transition step){
        runtime.entity().getNavigation().stop();zeroInput();pathPoint=null;status=wait;reason=why;waited++;
        checkpoint=runtime.entity().position();lastProgress=tick;
        if(clock.advance(tick,wait,step.timeoutTicks()))failure("waiting deadline: "+why);
    }
    private void complete(long tick){
        if(climb(route.transitions().get(index)))climbed++;
        completed++;if(route.transitions().get(index).kind()!=Kind.WALK)release();
        index++;runtime.entity().getNavigation().stop();zeroInput();
        if(cancelAfterTransition || index>=route.transitions().size()){route=null;release();status=Status.SUCCESS;reason=cancelAfterTransition?"safely cancelled at transition exit":"arrived";cancelAfterTransition=false;}
        else resetStep(tick);
    }
    private void resetStep(long tick){entering=false;clock=new Clock();checkpoint=runtime.entity().position();lastProgress=tick;pathPoint=null;}
    private void failure(String why){failed++;lastFailure=why;stop(why);status=Status.FAILURE;}
    void stop(String why){search=null;route=null;cancelAfterTransition=false;entering=false;pathPoint=null;release();runtime.entity().getNavigation().stop();zeroInput();status=Status.SUCCESS;reason=why;}
    private void release(){if(reserved!=null){TransitionResources.of(g.level).release(reserved,runtime.entity().getUUID());reserved=null;}TransitionResources.of(g.level).forget(runtime.entity().getUUID());}
    private void zeroInput(){var n=runtime.entity();n.getMoveControl().strafe(0,0);n.setXxa(0);n.setZza(0);n.setJumping(false);}
}
