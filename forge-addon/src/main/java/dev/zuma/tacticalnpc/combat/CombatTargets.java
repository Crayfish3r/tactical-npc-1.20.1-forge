package dev.zuma.tacticalnpc.combat;

import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;

/** Type/alliance/lifecycle checks never read a hidden subject's position or velocity. */
public final class CombatTargets {
    public static boolean supported(LivingEntity entity) {
        if(entity instanceof Player)return true;
        var runtime=entity==null?null:RuntimeRegistry.adapt(entity);
        return runtime!=null && runtime.entity()==entity;
    }
    public static LivingEntity resolve(ServerLevel level,UUID id) {
        var entity=id==null?null:level.getEntity(id);
        return entity instanceof LivingEntity living && living.level()==level && !living.isRemoved()?living:null;
    }
    public static boolean eligible(PathfinderMob owner,LivingEntity target) {
        if(target==null || target==owner || target.level()!=owner.level() || !target.isAlive() || target.isRemoved() || !supported(target))return false;
        if(target instanceof Player player && (player.isCreative() || player.isSpectator()))return false;
        var runtime=RuntimeRegistry.adapt(owner);
        return !(runtime==null?owner.isAlliedTo(target):runtime.allied(target));
    }
    private CombatTargets(){}
}
