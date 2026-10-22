package dev.zuma.tacticalnpc.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.PathfinderMob;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class VisibilitySensor {
    public enum Los { CLEAR, BLOCKED, DEFERRED }
    private static final int ACQUISITION_PAGE=16, ACQUISITION_RAYS=4;
    // Per-controller progress only: no Entity/world references or hidden coordinates retained.
    private final List<UUID> acquisitionPage=new ArrayList<>(ACQUISITION_PAGE);
    private UUID acquisitionOwner;
    private ResourceKey<Level> acquisitionDimension;
    private int nextCandidate,pageCursor,activationCursor;
    private boolean activationSeen;
    private boolean acquisitionDeferred;
    public enum Sector { CLEAR, OCCUPIED, SATURATED, DEFERRED, UNSUPPORTED }
    private Sector lastSector=Sector.UNSUPPORTED;
    private int sectorCursor;
    public Sector lastSector(){return lastSector;}
    private boolean sector(PathfinderMob npc,Sector result){lastSector=result;AiTelemetry.sector((ServerLevel)npc.level(),result);return result!=Sector.CLEAR;}
    public boolean inFov(PathfinderMob npc,LivingEntity target) {
        if(target==null) return false;
        Vec3 d=target.position().subtract(npc.position());
        double yaw=Math.toDegrees(Math.atan2(d.z,d.x))-90;
        return Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw-npc.getYHeadRot()))<=dev.zuma.tacticalnpc.config.ServerConfig.FOV.get()/2;
    }
    public boolean valid(PathfinderMob npc, LivingEntity target, double range) {
        return CombatTargets.eligible(npc,target) && !target.isInvisible() && npc.distanceToSqr(target)<=range*range;
    }

    public Vec3 aimPoint(LivingEntity target) {
        return target.position().add(0, target.getBbHeight() * 0.65, 0);
    }

    public boolean clear(PathfinderMob npc, Vec3 point) {
        return los(npc,point,false)==Los.CLEAR;
    }
    public Los los(PathfinderMob npc,Vec3 point,boolean active) {
        if(!(npc.level() instanceof net.minecraft.server.level.ServerLevel level)) return Los.DEFERRED;
        // ClipContext may ask for chunks. Preflight its entire bounded segment without tickets/I/O.
        if(!loadedSegment(level,npc.getEyePosition(),point))return Los.DEFERRED;
        var budget=AiWorkBudget.of(level);
        if(!(active?budget.activeRay(npc.getUUID()):budget.ray(npc.getUUID()))) return Los.DEFERRED;
        return npc.level().clip(new ClipContext(npc.getEyePosition(), point,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc)).getType() == HitResult.Type.MISS?Los.CLEAR:Los.BLOCKED;
    }
    /** Chunk-grid DDA, including corner neighbours: fixed-distance samples can miss a thin corner. */
    public static boolean loadedSegment(ServerLevel level,Vec3 start,Vec3 end){
        double distance=start.distanceTo(end);if(!Double.isFinite(distance) || distance>512)return false;
        int x=net.minecraft.util.Mth.floor(start.x/16),z=net.minecraft.util.Mth.floor(start.z/16);
        int ex=net.minecraft.util.Mth.floor(end.x/16),ez=net.minecraft.util.Mth.floor(end.z/16);
        double dx=end.x-start.x,dz=end.z-start.z;int sx=Double.compare(dx,0),sz=Double.compare(dz,0);
        double tx=sx==0?Double.POSITIVE_INFINITY:((sx>0?(x+1)*16.0:x*16.0)-start.x)/dx;
        double tz=sz==0?Double.POSITIVE_INFINITY:((sz>0?(z+1)*16.0:z*16.0)-start.z)/dz;
        double stepx=sx==0?Double.POSITIVE_INFINITY:16/Math.abs(dx),stepz=sz==0?Double.POSITIVE_INFINITY:16/Math.abs(dz);
        for(int i=0;i<130;i++){
            if(!level.hasChunk(x,z))return false;if(x==ex && z==ez)return true;
            if(Math.abs(tx-tz)<1e-10 && (!level.hasChunk(x+sx,z) || !level.hasChunk(x,z+sz)))return false;
            if(tx<=tz){x+=sx;tx+=stepx;}else{z+=sz;tz+=stepz;}
        }
        return false;
    }

    public boolean acquisitionDeferred() { return acquisitionDeferred; }
    public void resetAcquisition() {
        acquisitionPage.clear();nextCandidate=0;pageCursor=0;acquisitionDeferred=false;activationCursor=0;activationSeen=false;
        acquisitionOwner=null;acquisitionDimension=null;
    }

    /**
     * At most sixteen cheap checks per page, then four budgeted LOS checks per scan.
     * Index the existing player list and lifecycle-maintained supported NPC UUID list.
     * Only this small page is sorted; no world scan/copy or permanently capped spatial query.
     * For a stable list of N players + loaded supported NPCs, fixed eligibility/FOV and four rays per
     * call, a fresh cycle reaches a visible candidate within 4*ceil(N/16) calls, unless an
     * earlier candidate already succeeds (empty pages take one). Allow two extra pages for
     * arbitrary progress after a list change. DEFERRED has no bound
     * until budget becomes available; it never advances the untested page entry.
     */
    public LivingEntity acquire(PathfinderMob npc, double range) {
        acquisitionDeferred=false;
        if(!(npc.level() instanceof ServerLevel level)) {acquisitionDeferred=true;return null;}
        if(!npc.getUUID().equals(acquisitionOwner) || !level.dimension().equals(acquisitionDimension)) {
            resetAcquisition();acquisitionOwner=npc.getUUID();acquisitionDimension=level.dimension();
        }
        if(pageCursor>=acquisitionPage.size()) {
            if(!AiWorkBudget.of(level).query(npc.getUUID(),false)){acquisitionDeferred=true;return null;}
            acquisitionPage.clear();pageCursor=0;
            var players=level.players();
            int count=players.size()+RuntimeRegistry.loadedNpcCount(level);
            if(count==0) {nextCandidate=0;return null;}
            nextCandidate=Math.floorMod(nextCandidate,count);
            int end=Math.min(count,nextCandidate+ACQUISITION_PAGE);
            List<LivingEntity> candidates=new ArrayList<>(ACQUISITION_PAGE);
            for(int i=nextCandidate;i<end;i++) {
                LivingEntity p=i<players.size()?players.get(i):RuntimeRegistry.loadedNpcAt(level,i-players.size());
                if(valid(npc,p,range) && inFov(npc,p)) candidates.add(p);
            }
            nextCandidate=end==count?0:end;
            candidates.sort(Comparator.comparingDouble(npc::distanceToSqr));
            for(LivingEntity p:candidates) acquisitionPage.add(p.getUUID());
        }
        var budget=AiWorkBudget.of(level);
        for(int rays=0;pageCursor<acquisitionPage.size() && rays<ACQUISITION_RAYS;) {
            var p=CombatTargets.resolve(level,acquisitionPage.get(pageCursor));
            // Page membership grants no visibility; eligibility, FOV and LOS are fresh.
            if(!valid(npc,p,range) || !inFov(npc,p)) {pageCursor++;continue;}
            if(!budget.candidate(npc.getUUID())) {acquisitionDeferred=true;return null;}
            Los result=los(npc,aimPoint(p),false);
            if(result==Los.DEFERRED) {acquisitionDeferred=true;return null;}
            pageCursor++;rays++;
            if(result==Los.CLEAR) return p;
        }
        return null;
    }

    public enum Nearby { PRESENT, ABSENT, DEFERRED }
    /** Proximity grants activation only, never an observation. One budget token, <=16 NPC checks. */
    public Nearby activation(PathfinderMob npc,double range) {
        if(!(npc.level() instanceof ServerLevel level) || !AiWorkBudget.of(level).query(npc.getUUID(),false) || !AiWorkBudget.of(level).candidate(npc.getUUID()))return Nearby.DEFERRED;
        var players=new ArrayList<Player>(1);
        AiWorkBudget.of(level).worldQuery();
        level.getEntities(EntityTypeTest.forClass(Player.class),npc.getBoundingBox().inflate(range),p->CombatTargets.eligible(npc,p),players,1);
        if(!players.isEmpty())return Nearby.PRESENT;
        int count=RuntimeRegistry.loadedNpcCount(level);activationCursor=count==0?0:Math.floorMod(activationCursor,count);
        int end=Math.min(count,activationCursor+ACQUISITION_PAGE);
        for(int i=activationCursor;i<end;i++) {
            var candidate=RuntimeRegistry.loadedNpcAt(level,i);
            if(CombatTargets.eligible(npc,candidate) && npc.distanceToSqr(candidate)<=range*range)activationSeen=true;
        }
        activationCursor=end>=count?0:end;
        var result=activationSeen?Nearby.PRESENT:activationCursor==0?Nearby.ABSENT:Nearby.DEFERRED;
        if(activationCursor==0)activationSeen=false;
        return result;
    }

    /** Conservative: any other living entity in the corridor blocks a shot, including non-allies. */
    public boolean occupied(PathfinderMob npc, LivingEntity target, Vec3 end) {
        var controller=RuntimeRegistry.active(npc);
        if(controller==null) return true;
        return occupied(npc,target,end,controller.weaponState());
    }
    public boolean occupied(PathfinderMob npc,LivingEntity target,Vec3 end,dev.zuma.tacticalnpc.core.WeaponState weapon) {
        Vec3 start=npc.getEyePosition();
        if(!weapon.supported()) return sector(npc,Sector.UNSUPPORTED);
        double spread=weapon.spreadDegrees();
        if (!Double.isFinite(spread) || spread < 0 || spread > 30 || end.distanceToSqr(start)<1e-8) return sector(npc,Sector.UNSUPPORTED);
        // Check beyond the target too. A probabilistic native spread has no absolute cone guarantee;
        // the server damage-event guard is the final protection for allied living entities.
        Vec3 direction = end.subtract(start).normalize();
        // Cover the requested firing depth plus continuation, not the bullet's entire lifetime.
        // The native damage guard protects allies further downrange even after the owner unloads.
        double safetyRange=Math.max(128,Math.min(512,end.distanceTo(start)+8));
        end = start.add(direction.scale(safetyRange));
        double slope = Math.tan(Math.toRadians(Math.min(75, spread * 3 + 1)));
        var level=(ServerLevel)npc.level();var budget=AiWorkBudget.of(level);int first=sectorCursor;
        for(int section=0;section<4;section++) {
            int index=(first+section)%4;double low=index*safetyRange/4,high=low+safetyRange/4;
            if(!budget.query(npc.getUUID(),true)){sectorCursor=index;return sector(npc,Sector.DEFERRED);}
            List<LivingEntity> nearby=new ArrayList<>(32);int[] scanned={0};boolean[] saturated={false};
            AABB region=new AABB(start.add(direction.scale(low)),start.add(direction.scale(high))).inflate(.6+slope*high);
            budget.worldQuery();
            level.getEntities(EntityTypeTest.forClass(LivingEntity.class),region,e->{
                if(e==npc || e==target || !e.isAlive())return false;
                // Bound predicate work too: after 64 checks, fill at most 32 sentinels then stop.
                if(++scanned[0]>64){saturated[0]=true;return true;}
                return coneIntersects(e.getBoundingBox(),start,direction,low,high,slope);
            },nearby,32);
            sectorCursor=(index+1)%4;
            if(saturated[0] || nearby.size()==32)return sector(npc,Sector.SATURATED);
            if(!nearby.isEmpty())return sector(npc,Sector.OCCUPIED);
        }
        // Every section above was checked now; no CLEAR geometry carries across ticks/rounds.
        return sector(npc,Sector.CLEAR);
    }
    /** Exact slab intersection with the conservative linearly expanding L-infinity envelope. */
    public static boolean coneIntersects(AABB box,Vec3 start,Vec3 direction,double low,double high,double slope) {
        double[] origin={start.x,start.y,start.z},delta={direction.x,direction.y,direction.z};
        double[] min={box.minX,box.minY,box.minZ},max={box.maxX,box.maxY,box.maxZ};
        for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++){
            double a=(side==0?delta[axis]:-delta[axis])-slope;
            double b=side==0?max[axis]+.6-origin[axis]:origin[axis]-min[axis]+.6;
            if(Math.abs(a)<1e-12){if(b<0)return false;}
            else if(a>0)high=Math.min(high,b/a);else low=Math.max(low,b/a);
            if(low>high)return false;
        }
        return true;
    }
}
