package dev.zuma.tacticalnpc.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    public boolean inFov(PathfinderMob npc,Player target) {
        if(target==null) return false;
        Vec3 d=target.position().subtract(npc.position());
        double yaw=Math.toDegrees(Math.atan2(d.z,d.x))-90;
        return Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw-npc.getYHeadRot()))<=dev.zuma.tacticalnpc.config.ServerConfig.FOV.get()/2;
    }
    public boolean valid(PathfinderMob npc, Player player, double range) {
        return player != null && player.level() == npc.level() && player.isAlive() && !player.isRemoved() &&
                !player.isCreative() && !player.isSpectator() && !player.isInvisible() &&
                npc.distanceToSqr(player) <= range * range &&
                !(RuntimeRegistry.adapt(npc)==null ? npc.isAlliedTo(player) : RuntimeRegistry.adapt(npc).allied(player));
    }

    public Vec3 aimPoint(Player target) {
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

    /** At most sixteen nearby candidates and four visibility rays per idle scan. */
    public Player acquire(PathfinderMob npc, double range) {
        List<Player> candidates = new ArrayList<>();
        npc.level().getEntities(EntityTypeTest.forClass(Player.class), npc.getBoundingBox().inflate(range),
                p -> valid(npc, p, range), candidates, 16);
        candidates.sort(Comparator.comparingDouble(npc::distanceToSqr));
        for (int i = 0; i < Math.min(4, candidates.size()); i++) {
            if(!AiWorkBudget.of((net.minecraft.server.level.ServerLevel)npc.level()).candidate(npc.getUUID())) break;
            Player p = candidates.get(i);
            if (inFov(npc,p) && clear(npc, aimPoint(p))) return p;
        }
        return null;
    }

    /** Conservative: any other living entity in the corridor blocks a shot, including non-allies. */
    public boolean occupied(PathfinderMob npc, Player target, Vec3 end) {
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
