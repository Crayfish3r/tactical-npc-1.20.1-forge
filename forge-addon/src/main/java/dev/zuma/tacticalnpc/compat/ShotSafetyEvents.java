package dev.zuma.tacticalnpc.compat;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import dev.zuma.tacticalnpc.combat.TacticalCombatGoal;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

public final class ShotSafetyEvents {
    @SubscribeEvent public void death(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel level){
            dev.zuma.tacticalnpc.combat.TransitionResources.forget(level,event.getEntity().getUUID());
            for(var goal:RuntimeRegistry.activeIn(level)) goal.forget(event.getEntity().getUUID());
        }
    }
    @SubscribeEvent public void starting(net.minecraftforge.event.server.ServerStartingEvent event) { GunSupport.verifyResources(event.getServer().getResourceManager()); }
    @SubscribeEvent public void reload(net.minecraftforge.event.OnDatapackSyncEvent event) {
        if(event.getPlayer()==null) GunSupport.verifyResources(event.getPlayerList().getServer().getResourceManager());
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void fire(GunFireEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        TacticalCombatGoal goal = RuntimeRegistry.active(event.getShooter());
        if (goal != null) {
            if(!goal.allowNativeFire())event.setCanceled(true);
            else goal.authorizeNativeRound();
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void hurt(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        // Remember the projectile's ownership even after tag removal: a bullet in flight is still ours.
        if (event.getBullet() == null || !event.getBullet().getTags().contains("tactical_projectile")) return;
        // TaCZ legitimately supplies null when Projectile.getOwner cannot resolve an unloaded owner.
        // Without a faction authority, reject damage from our orphan rather than lose ally protection.
        var runtime=event.getAttacker()==null?null:RuntimeRegistry.adapt(event.getAttacker());
        if(runtime==null) {event.setCanceled(true);return;}
        if (!(event.getHurtEntity() instanceof LivingEntity victim) ||
                !dev.zuma.tacticalnpc.combat.CombatTargets.eligible(runtime.entity(),victim)) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void projectile(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || event.isCanceled()) return;
        if(event.getEntity() instanceof com.tacz.guns.entity.EntityKineticBullet round && round.getOwner() instanceof LivingEntity source)
            dev.zuma.tacticalnpc.combat.GunHearing.deliver(source);
        if (event.getEntity() instanceof com.tacz.guns.entity.EntityKineticBullet bullet &&
                bullet.getOwner() instanceof LivingEntity npc && RuntimeRegistry.active(npc) != null) {
            bullet.addTag("tactical_projectile");RuntimeRegistry.active(npc).recordProjectile(event.getLevel().getGameTime());
        }
    }
}
