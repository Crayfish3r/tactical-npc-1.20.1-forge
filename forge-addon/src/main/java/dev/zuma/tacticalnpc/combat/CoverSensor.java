package dev.zuma.tacticalnpc.combat;

import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Incremental local geometry, at most six candidates and one path per update. */
public final class CoverSensor {
    private final NpcRuntimeAdapter runtime;
    private CoverOption selected;
    private long nextUpdate,nextPath;
    private long coverUntil,peekUntil,revision;
    private boolean deferred;
    public boolean deferred(){return deferred;}
    public long revision(){return revision;}
    private int cursor,peekCursor;
    private Vec3 lastThreat;
    private final Map<BlockPos,Long> blacklist=new HashMap<>();
    public CoverSensor(NpcRuntimeAdapter runtime) {this.runtime=runtime;nextUpdate=runtime.entity().level().getGameTime()+Math.floorMod(runtime.entity().getUUID().hashCode(),20);}
    public void reject(Vector3 point,long tick) {
        var key=BlockPos.containing(point.x(),point.y(),point.z());if(blacklist.containsKey(key)) return;
        blacklist.put(key,tick+160);
        if(selected!=null && (selected.position().minus(point).lengthSquared()<2 || selected.peek()!=null && selected.peek().minus(point).lengthSquared()<2)) {
            blacklist.put(BlockPos.containing(selected.position().x(),selected.position().y(),selected.position().z()),tick+160);
            selected=null;SquadRuntime.of((ServerLevel)runtime.entity().level()).releaseClaims(runtime.entity().getUUID());
        }
        nextUpdate=tick+20;
    }
    public void reset(){selected=null;lastThreat=null;coverUntil=peekUntil=0;deferred=false;revision++;blacklist.clear();SquadRuntime.of((ServerLevel)runtime.entity().level()).releaseClaims(runtime.entity().getUUID());}
    public List<CoverOption> sample(Observation observation,long tick,boolean reposition) {
        runtime.requireServer();var npc=runtime.entity();var level=(ServerLevel)npc.level();
        deferred=false;
        blacklist.entrySet().removeIf(e->e.getValue()<tick);
        if(observation==null){if(selected!=null || lastThreat!=null)reset();return List.of();}
        if(selected!=null && !reserve(selected.position(),tick))selected=null;
        Vec3 threat=vec(observation.position());
        if(lastThreat!=null && lastThreat.distanceToSqr(threat)>1){coverUntil=peekUntil=0;revision++;}
        lastThreat=threat;
        if(reposition && selected!=null) reject(selected.position(),tick);
        if(selected!=null) {
            var budget=AiWorkBudget.of(level);Vec3 cover=vec(selected.position());
            if(!withinOrder(cover) || !sameStanding(cover)){selected=null;revision++;return List.of();}
            if(tick>=coverUntil) {
                if(!budget.canRays(npc.getUUID(),6)){deferred=true;return List.of();}
                if(!protectedAt(cover,threat)){selected=null;revision++;SquadRuntime.of(level).releaseClaims(npc.getUUID());return List.of();}
                coverUntil=tick+20;
            }
            Vec3 peek=selected.peek()==null?null:vec(selected.peek());
            if(peek!=null && (!withinOrder(peek) || !sameStanding(peek))){peek=null;peekUntil=0;revision++;}
            if(tick>=peekUntil){
                if(peek!=null){
                    if(!budget.canRays(npc.getUUID(),3)){deferred=true;return List.of(new CoverOption(selected.position(),null,coverUntil));}
                    if(!peekOpen(peek,cover,threat)){peek=null;revision++;}
                }
                if(peek==null)peek=findPeek(cover,threat);
                peekUntil=deferred?tick:tick+4;
            }
            var checked=new CoverOption(selected.position(),peek==null?null:vector(peek),coverUntil);
            if(!java.util.Objects.equals(checked.peek(),selected.peek()))revision++;
            selected=checked;
            return List.of(new CoverOption(selected.position(),deferred?null:selected.peek(),coverUntil));
        }
        if(tick<nextUpdate)return List.of();
        nextUpdate=tick+20;
        var budget=AiWorkBudget.of(level);
        double base=Math.atan2(threat.z-npc.getZ(),threat.x-npc.getX());
        for(int count=0;count<6;count++) {
            if(!budget.candidate(npc.getUUID())) {nextUpdate=tick+1;break;}
            int index=cursor++%24,sector=index%8;
            if(runtime.squadId()!=null && SquadRuntime.of(level).role(runtime.squadId(),npc.getUUID())==SquadBlackboard.Role.FLANKER)sector=new int[]{2,6,1,7,3,5,0,4}[sector];
            double radius=3+(index/8)*2,angle=base+sector*Math.PI/4;
            Vec3 point=safeStanding(npc.position().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius));
            if(point==null || !withinOrder(point) || !available(vector(point),tick) || blacklist.containsKey(BlockPos.containing(point)))continue;
            if(!budget.canRays(npc.getUUID(),6)){cursor--;nextUpdate=tick+1;break;}
            if(!protectedAt(point,threat))continue;
            if(tick<nextPath || !budget.path(npc.getUUID())) {cursor--;nextUpdate=Math.max(tick+1,nextPath);break;}
            nextPath=tick+20;var path=npc.getNavigation().createPath(BlockPos.containing(point),0);
            if(path==null || !path.canReach() || !withinOrderPath(path)) {blacklist.put(BlockPos.containing(point),tick+160);continue;}
            Vec3 peek=findPeek(point,threat);
            if(!reserve(vector(point),tick))continue;
            coverUntil=tick+20;peekUntil=deferred?tick:tick+4;revision++;
            selected=new CoverOption(vector(point),peek==null?null:vector(peek),coverUntil);
            if(dev.zuma.tacticalnpc.config.ServerConfig.DEBUG.get()) dev.zuma.tacticalnpc.TacticalNpcAiMod.LOGGER.info("NPC {} checked cover {} peek {}",npc.getUUID(),point,peek);
            break;
        }
        return selected==null?List.of():List.of(selected);
    }
    public Vec3 safeStanding(Vec3 guess) {
        var npc=runtime.entity();var level=npc.level();BlockPos center=BlockPos.containing(guess);
        for(int dy:new int[]{0,-1,1}) {
            BlockPos feet=center.offset(0,dy,0),below=feet.below();
            if(!level.hasChunkAt(feet) || !level.hasChunkAt(feet.offset(1,0,1)) || !level.hasChunkAt(feet.offset(-1,0,-1))) continue;
            var ground=level.getBlockState(below);var shape=ground.getCollisionShape(level,below);
            if(shape.isEmpty() || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty() || !level.getFluidState(below).isEmpty() ||
                    ground.is(Blocks.MAGMA_BLOCK) || ground.is(Blocks.CACTUS) || ground.is(Blocks.CAMPFIRE) || ground.is(Blocks.SOUL_CAMPFIRE) ||
                    level.getBlockState(feet).is(Blocks.FIRE) || level.getBlockState(feet).is(Blocks.POWDER_SNOW)) continue;
            double y=below.getY()+shape.max(net.minecraft.core.Direction.Axis.Y);
            Vec3 at=new Vec3(feet.getX()+.5,y,feet.getZ()+.5);
            double width=npc.getBbWidth()/2+.1;
            if(level.noCollision(npc,new AABB(at.x-width,at.y+.01,at.z-width,at.x+width,at.y+npc.getBbHeight(),at.z+width))) return at;
        }
        return null;
    }
    private boolean protectedAt(Vec3 feet,Vec3 threat) {
        Vec3 direction=threat.subtract(feet).normalize(),side=new Vec3(-direction.z,0,direction.x).normalize();
        // Include body width and vanilla navigation's arrival tolerance, not just the center ray.
        for(double offset:new double[]{0,-1.2,1.2}) {
            Vec3 at=feet.add(side.scale(offset));
            if(!Boolean.FALSE.equals(clear(at.add(0,runtime.entity().getEyeHeight(),0),threat)) || !Boolean.FALSE.equals(clear(at.add(0,.8,0),threat))) return false;
        }
        return true;
    }
    private Vec3 findPeek(Vec3 point,Vec3 threat) {
        var npc=runtime.entity();var budget=AiWorkBudget.of((ServerLevel)npc.level());
        Vec3 side=new Vec3(-(threat.z-point.z),0,threat.x-point.x).normalize();
        // Continue across budgeted updates instead of starving the farther usable exits forever.
        for(int count=0;count<6;count++) {
            if(!budget.candidate(npc.getUUID()) || !budget.canRays(npc.getUUID(),3)){deferred=true;return null;}
            int index=Math.floorMod(peekCursor++,6);double distance=new double[]{1.7,3.2,4.5}[index/2];int sign=index%2==0?-1:1;
            Vec3 test=safeStanding(point.add(side.scale(sign*distance)));
            if(test==null || !withinOrder(test) || blacklist.containsKey(BlockPos.containing(test))) continue;
            if(peekOpen(test,point,threat))return test;
        }
        return null;
    }
    private boolean sameStanding(Vec3 point){var checked=safeStanding(point);return checked!=null && checked.distanceToSqr(point)<.01;}
    private boolean peekOpen(Vec3 test,Vec3 cover,Vec3 threat){
        var npc=runtime.entity();Vec3 side=new Vec3(-(threat.z-cover.z),0,threat.x-cover.x).normalize();
        for(double offset:new double[]{0,-1.2,1.2}){
            Boolean result=clear(test.add(side.scale(offset)).add(0,npc.getEyeHeight(),0),threat);
            if(result==null){deferred=true;return false;}if(!result)return false;
        }
        return true;
    }
    private Boolean clear(Vec3 start,Vec3 end) {
        var npc=runtime.entity();if(!AiWorkBudget.of((ServerLevel)npc.level()).ray(npc.getUUID())) return null;
        return npc.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,npc)).getType()==HitResult.Type.MISS;
    }
    public boolean withinOrder(Vec3 point) {
        var order=runtime.orderState();
        if(order.kind()==OrderState.Kind.DEFEND_AREA) return territory().contains(vector(point));
        double radius=order.kind()==OrderState.Kind.HOLD_POSITION?Math.min(6,order.radius()):order.radius();
        return order.kind()==OrderState.Kind.ASSAULT || vector(point).minus(order.anchor()).lengthSquared()<=radius*radius;
    }
    public DefendTerritory territory() {
        var order=runtime.orderState();
        return order.kind()==OrderState.Kind.DEFEND_AREA?new DefendTerritory(order.anchor(),order.radius(),DefendTerritory.clearance(runtime.entity().getBbWidth())):null;
    }
    /** Validate actual vanilla node coordinates, including detours with interior endpoints. */
    public boolean withinOrderPath(net.minecraft.world.level.pathfinder.Path path) {
        var boundary=territory();if(boundary==null)return true;
        if(path.getNodeCount()>128)return false;
        var npc=runtime.entity();Vector3 previous=vector(npc.position());
        for(int i=path.getNextNodeIndex();i<path.getNodeCount();i++) {
            Vec3 point=path.getEntityPosAtNode(npc,i);
            // The initial cell represents the displaced starting position, not a requested step.
            if(i==0 && BlockPos.containing(npc.position()).equals(BlockPos.containing(point)))continue;
            // PathNavigation advances an already-arrived waypoint before issuing its next move.
            // Its arrival reference is node + (width + 1)/2, not getEntityPosAtNode's center.
            var node=path.getNode(i);float width=npc.getBbWidth(),tolerance=width>.75f?width/2:.75f-width/2;
            double offset=(width+1)/2.0;
            if(i==path.getNextNodeIndex() && Math.abs(npc.getX()-node.x-offset)<=tolerance &&
                    Math.abs(npc.getZ()-node.z-offset)<=tolerance && Math.abs(npc.getY()-node.y)<1)continue;
            if(!boundary.permitsStep(previous,vector(point)))return false;
            previous=vector(point);
        }
        return true;
    }
    private boolean available(Vector3 point,long tick){return runtime.squadId()==null || SquadRuntime.of((ServerLevel)runtime.entity().level()).available(runtime.squadId(),runtime.entity().getUUID(),point,tick);}
    private boolean reserve(Vector3 point,long tick){return runtime.squadId()==null || SquadRuntime.of((ServerLevel)runtime.entity().level()).claim(runtime.squadId(),runtime.entity().getUUID(),point,tick);}
    private static Vector3 vector(Vec3 v){return new Vector3(v.x,v.y,v.z);}private static Vec3 vec(Vector3 v){return new Vec3(v.x(),v.y(),v.z());}
}
