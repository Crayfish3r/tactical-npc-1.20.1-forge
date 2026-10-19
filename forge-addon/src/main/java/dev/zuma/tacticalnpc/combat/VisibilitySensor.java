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
        var budget=AiWorkBudget.of(level);
        if(!(active?budget.activeRay(npc.getUUID()):budget.ray(npc.getUUID()))) return Los.DEFERRED;
        return npc.level().clip(new ClipContext(npc.getEyePosition(), point,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc)).getType() == HitResult.Type.MISS?Los.CLEAR:Los.BLOCKED;
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
        if(!(npc.level() instanceof ServerLevel level) || !AiWorkBudget.of(level).candidate(npc.getUUID()))return Nearby.DEFERRED;
        var players=new ArrayList<Player>(1);
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
        Vec3 start = npc.getEyePosition();
        var controller=RuntimeRegistry.active(npc);
        if(controller==null) return true;
        var weapon=controller.weaponState();
        if(!weapon.supported()) return true;
        double spread=weapon.spreadDegrees();
        if (!Double.isFinite(spread) || spread < 0 || spread > 30) return true;
        // Check beyond the target too. A probabilistic native spread has no absolute cone guarantee;
        // the server damage-event guard is the final protection for allied living entities.
        Vec3 direction = end.subtract(start).normalize();
        end = start.add(direction.scale(128));
        double slope = Math.tan(Math.toRadians(Math.min(75, spread * 3 + 1)));
        double maxRadius = 0.6 + slope * 128;
        List<LivingEntity> nearby = new ArrayList<>();
        npc.level().getEntities(EntityTypeTest.forClass(LivingEntity.class), new AABB(start, end).inflate(maxRadius),
                e -> e != npc && e != target && e.isAlive(), nearby, 32);
        if (nearby.size() == 32) return true; // Saturated work budget fails closed.
        for (LivingEntity e : nearby) {
            double along = Math.max(0, Math.min(128, e.getBoundingBox().getCenter().subtract(start).dot(direction)));
            AABB box = e.getBoundingBox().inflate(0.6 + slope * along);
            if (box.contains(start) || box.clip(start, end).isPresent()) return true;
        }
        return false;
    }
}
