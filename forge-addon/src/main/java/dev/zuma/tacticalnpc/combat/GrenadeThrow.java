package dev.zuma.tacticalnpc.combat;

import dev.zuma.tacticalnpc.TacticalNpcAiMod;
import dev.zuma.tacticalnpc.compat.SbwGrenades;
import dev.zuma.tacticalnpc.config.ServerConfig;
import dev.zuma.tacticalnpc.core.Frame;
import dev.zuma.tacticalnpc.core.GrenadeContact;
import dev.zuma.tacticalnpc.core.MovementCommand;
import dev.zuma.tacticalnpc.core.OrderState;
import dev.zuma.tacticalnpc.core.Vector3;
import dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-only RGO preparation and dry trajectory planning for the audited SBW build. */
final class GrenadeThrow {
    private static final int ARCS_PER_TICK=6, MAX_ZONES=128, MAX_PLAN_TICKS=20;
    private static final int REPOSITION_TICKS=56;
    private static final double BODY_MARGIN=1, MAX_BLAST=24;
    private static final Vec3[] OFFSETS={Vec3.ZERO,new Vec3(1.5,0,0),new Vec3(-1.5,0,0),new Vec3(0,0,1.5),new Vec3(0,0,-1.5)};
    private record Claim(UUID squad,Vec3 point,double radius,long expires) {}
    private static final Map<ServerLevel,ArrayList<Claim>> CLAIMS=new WeakHashMap<>();

    private final NpcRuntimeAdapter runtime;
    private final ActionGeometry geometry;
    private final GrenadeContact contact=new GrenadeContact();
    private UUID subject;
    private Vec3 origin, launchPosition, landingTarget;
    private long readyAt, nextThrow;
    private int flight, offset;
    private String status="no personal sighting";
    private int released;
    private boolean abandoned;

    GrenadeThrow(NpcRuntimeAdapter runtime){this.runtime=runtime;geometry=new ActionGeometry(runtime);}

    void seen(LivingEntity target,long tick,long expires) {
        stopPreparation();
        status="target visible";
        abandoned=false;
        if(SbwGrenades.available())contact.seen(target.getUUID(),ActionGeometry.vector(target.position()),tick,expires);
    }

    void clear(){contact.clear();stopPreparation();}
    boolean preparing(){return subject!=null;}
    private void stopPreparation(){subject=null;origin=null;launchPosition=null;landingTarget=null;}
    Vector3 lastFeet(){return contact.lastFeet();}
    boolean tracks(UUID subject,long tick){return SbwGrenades.available() && contact.recent(tick,ServerConfig.GRENADE_WINDOW.get()) && contact.subject().equals(subject);}

    String diagnostics(){return (SbwGrenades.available()?"stock="+SbwGrenades.stock(runtime.entity()):"SBW unavailable")+" released="+released+" status="+status;}

    private MovementCommand decline(String reason){status=reason;stopPreparation();return null;}
    private MovementCommand waitHere(String reason){status=reason;return MovementCommand.hold();}

    /** A non-null command owns movement and hands for this tick. A sight deferral cannot launch. */
    MovementCommand tick(Frame frame, UUID checked, boolean occluded, boolean sightClear,
                         boolean availableHands, boolean allowReposition, boolean blockedRoute) {
        if(!SbwGrenades.available())return decline("SBW unavailable");
        var npc=runtime.entity();
        if(SbwGrenades.stock(npc)==0)return decline("empty stock");
        if(frame.tick()<nextThrow)return decline("cooldown");
        if(!tracks(checked,frame.tick()))return decline("no recent personal contact");
        if(abandoned)return decline("no safe launch for this contact");
        if(sightClear)return decline("visual contact restored");
        // An issued preparation may finish moving while turned away from the target.
        // A fresh confirmation of loss is still required at the actual release point.
        if(!occluded && subject==null){status="waiting for confirmed loss of sight";return null;}
        if(!CombatTargets.eligible(npc,CombatTargets.resolve((ServerLevel)npc.level(),checked)))return decline("target no longer eligible");
        if(!availableHands || !npc.onGround()){status="hands or footing unavailable";return null;}
        var from=npc.getEyePosition().subtract(0,.1,0);
        var feet=ActionGeometry.vec(contact.lastFeet());
        double radius=SbwGrenades.dangerRadius();
        if(radius<=0 || radius>MAX_BLAST)return decline("SBW blast radius outside supported bounds");
        if(from.subtract(feet).horizontalDistance()>ServerConfig.GRENADE_RANGE.get())return decline("remembered point out of range");
        boolean tooClose=npc.distanceToSqr(feet)<=Math.pow(radius+BODY_MARGIN,2);
        if(tooClose) {
            if(!allowReposition || blockedRoute)return decline("too close to blast area");
            if(launchPosition==null)launchPosition=findLaunchPosition(feet,radius);
            if(launchPosition==null)return decline("no reachable safer launch position");
        }
        var permit=contact.permit(checked,occluded || subject!=null,frame,ServerConfig.GRENADE_WINDOW.get(),tooClose?REPOSITION_TICKS:0);
        if(permit==null)return decline("expired or replaced contact");
        if(!permit.subject().equals(subject)) {
            subject=permit.subject();
            readyAt=frame.tick()+ServerConfig.GRENADE_PREPARE.get();
        }
        if(tooClose) {
            status="moving clear of blast area";
            return MovementCommand.navigate(ActionGeometry.vector(launchPosition),MovementCommand.Locomotion.RELOCATION);
        }
        if(launchPosition!=null){launchPosition=null;readyAt=frame.tick()+2;}
        if(frame.tick()<readyAt)return waitHere("preparing RGO");
        if(frame.tick()>=readyAt+MAX_PLAN_TICKS){abandoned=true;return decline("no safe arc before deadline");}
        if(!occluded)return waitHere("turning toward last sighting");
        if(origin==null || origin.distanceToSqr(from)>.01) {
            origin=from;
            flight=Math.max(1,(int)Math.ceil(from.subtract(feet).horizontalDistance()/SbwGrenades.MAX_SPEED)-1);
            offset=0;
            landingTarget=null;
        }

        var level=(ServerLevel)npc.level();
        var budget=AiWorkBudget.of(level);
        boolean legacyImpact=SbwGrenades.legacyImpact();
        for(int i=0;i<ARCS_PER_TICK && offset<OFFSETS.length;i++) {
            int duration=flight;
            if(landingTarget==null){
                if(!budget.grenadeSegment(npc.getUUID()))return waitHere("waiting for trajectory budget");
                landingTarget=landingSurface(level,npc,feet.add(OFFSETS[offset]));
                if(landingTarget==null){offset++;continue;}
            }
            double gravity=SbwGrenades.gravity();
            Vec3 displacement=landingTarget.subtract(from);
            int horizontalTicks=legacyImpact?Math.max(1,duration-1):duration;
            Vec3 velocity=new Vec3(displacement.x/horizontalTicks,displacement.y/duration+gravity*(duration-1)/2,displacement.z/horizontalTicks);
            if(velocity.lengthSqr()>SbwGrenades.MAX_SPEED*SbwGrenades.MAX_SPEED) { advance(feet,from);continue; }
            var trace=trace(level,npc,from,velocity,duration,gravity,budget,legacyImpact);
            if(trace.deferred())return waitHere("waiting for trajectory budget");
            if(trace.impact()==null || trace.impact().subtract(feet).horizontalDistanceSqr()>9 ||
                    Math.abs(trace.impact().y-feet.y)>4) {advance(feet,from);continue;}
            if(!budget.query(npc.getUUID(),false))return waitHere("waiting for blast safety query");
            Vec3 hit=trace.impact();
            if(!safeArea(level,hit,radius,frame.tick())) {advance(feet,from);continue;}
            boolean launched=SbwGrenades.release(npc,from,velocity);
            if(launched) {
                contact.spend();
                released++;
                nextThrow=frame.tick()+ServerConfig.GRENADE_COOLDOWN.get();
                recordClaim(level,hit,radius,frame.tick()+duration+8);
                if(ServerConfig.DEBUG.get())TacticalNpcAiMod.LOGGER.info("NPC {} released RGO at {} from {} remaining={}",npc.getUUID(),hit,from,SbwGrenades.stock(npc));
            } else {
                nextThrow=frame.tick()+8;
            }
            status=launched?"RGO released":"SBW rejected launch";
            stopPreparation();
            return MovementCommand.hold();
        }
        if(offset<OFFSETS.length)return waitHere("checking dry arc and blast safety");
        abandoned=true;
        return decline("no safe arc for remembered position");
    }

    private Vec3 findLaunchPosition(Vec3 feet,double radius) {
        var npc=runtime.entity();
        var away=npc.position().subtract(feet).multiply(1,0,1);
        if(away.horizontalDistanceSqr()<.25)return null;
        Vec3 back=away.normalize(),side=new Vec3(-back.z,0,back.x);
        double distance=radius+BODY_MARGIN+1.5-away.horizontalDistance();
        if(distance>8)return null;
        for(int lateral:new int[]{0,-2,2}) {
            Vec3 candidate=geometry.terrain.safeStanding(npc.position().add(back.scale(Math.max(2,distance))).add(side.scale(lateral)));
            if(candidate!=null && candidate.distanceToSqr(feet)>Math.pow(radius+BODY_MARGIN+1,2) &&
                    geometry.permits(npc.position(),candidate))return candidate;
        }
        return null;
    }

    private static Vec3 landingSurface(ServerLevel level,LivingEntity owner,Vec3 vicinity) {
        Vec3 high=vicinity.add(0,.25,0),low=vicinity.subtract(0,3,0);
        if(!VisibilitySensor.loadedSegment(level,high,low))return null;
        var ground=level.clip(new ClipContext(high,low,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,owner));
        if(ground.getType()==HitResult.Type.MISS || ground.getDirection()!=Direction.UP || !level.getFluidState(ground.getBlockPos()).isEmpty())return null;
        return ground.getLocation().subtract(0,.1,0);
    }

    private void advance(Vec3 feet,Vec3 from){
        if(++flight>SbwGrenades.LIFE){
            offset++;
            landingTarget=null;
            flight=Math.max(1,(int)Math.ceil(from.subtract(feet).horizontalDistance()/SbwGrenades.MAX_SPEED)-1);
        }
    }

    private record Trace(Vec3 impact,boolean deferred) {}
    private static final Trace REJECTED=new Trace(null,false), DEFERRED=new Trace(null,true);

    private static Trace trace(ServerLevel level,LivingEntity owner,Vec3 point,Vec3 velocity,int duration,double gravity,AiWorkBudget budget,boolean legacyImpact) {
        for(int t=0;t<duration;t++) {
            Vec3 next=point.add(velocity);
            if(next.y>=level.getMaxBuildHeight() || next.y<=level.getMinBuildHeight() || !VisibilitySensor.loadedSegment(level,point,next))return REJECTED;
            if(!budget.grenadeSegment(owner.getUUID()))return DEFERRED;
            var hit=level.clip(new ClipContext(point,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,owner));
            if(hit.getType()!=HitResult.Type.MISS) {
                return hit.getDirection()==Direction.UP && level.getFluidState(hit.getBlockPos()).isEmpty()?
                        new Trace(legacyImpact?point:hit.getLocation(),false):REJECTED;
            }
            point=next;
            // SBW 0.8.9 undoes vanilla's dry drag using a float reciprocal.
            if(legacyImpact)velocity=velocity.scale((double).99f*(1.0f/.99f));
            velocity=velocity.subtract(0,gravity,0);
        }
        return REJECTED;
    }

    private boolean safeArea(ServerLevel level,Vec3 hit,double radius,long tick) {
        double limit=radius+BODY_MARGIN;
        var npc=runtime.entity();
        if(npc.distanceToSqr(hit)<=limit*limit || !level.hasChunkAt(BlockPos.containing(hit)))return false;
        var squad=runtime.squadId();
        var claims=CLAIMS.get(level);
        if(squad!=null && claims!=null) {
            claims.removeIf(c->tick>c.expires());
            if(claims.size()>=MAX_ZONES)return false;
            for(var claim:claims) {
                double separation=Math.max(claim.radius(),radius);
                if(squad.equals(claim.squad()) && claim.point().distanceToSqr(hit)<=separation*separation)return false;
            }
        }
        return level.getEntitiesOfClass(LivingEntity.class,new AABB(hit,hit).inflate(limit),
                e->e!=npc && e.isAlive() && !CombatTargets.eligible(npc,e)).isEmpty();
    }

    private void recordClaim(ServerLevel level,Vec3 hit,double radius,long expires){
        var squad=runtime.squadId();
        if(squad!=null)CLAIMS.computeIfAbsent(level,key->new ArrayList<>()).add(new Claim(squad,hit,radius,expires));
    }
}
