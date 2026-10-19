package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@PrefixGameTestTemplate(false)
public final class OptionalTargetTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="targets-optional-01",timeoutTicks=180)
    public static void independentTargetsVerifiedLegacyRuntime(GameTestHelper h) {
        ScenarioIsolation.begin(h);var own=own(h,2.5);var legacy=RuntimeRegistry.createLegacy(h.getLevel());var target=legacy.entity();var at=h.absoluteVec(new Vec3(13.5,2,7.5));target.moveTo(at.x,at.y,at.z);target.setNoAi(true);target.setInvulnerable(true);h.getLevel().addFreshEntity(target);
        int bullets=ProjectileProbe.count(own.getUUID());
        h.runAtTickTime(140,()->{
            h.assertTrue(CombatTargets.supported(target) && RuntimeRegistry.adapt(target)!=null && RuntimeRegistry.loadedNpcCount(h.getLevel())==2,"exact pinned optional runtime registered without main importing its class");
            h.assertTrue(target.getUUID().equals(own.controller().contactId()) && own.controller().shots()>0 && ProjectileProbe.count(own.getUUID())>bullets,"independent NPC genuinely sees and shoots legacy NPC through native TaCZ");
            target.discard();h.assertTrue(own.controller().observation()==null && own.controller().contactId()==null && !own.controller().allowNativeFire(),"optional target leave invalidates contact and native gate");own.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="targets-optional-02",timeoutTicks=180)
    public static void legacyControllerUsesSameNpcPolicyAndNativeGate(GameTestHelper h) {
        ScenarioIsolation.begin(h);var target=own(h,13.5);target.removeTag(RuntimeRegistry.TAG);var legacy=RuntimeRegistry.createLegacy(h.getLevel());var shooter=legacy.entity();var at=h.absoluteVec(new Vec3(2.5,2,7.5));shooter.moveTo(at.x,at.y,at.z,-90,0);shooter.setYHeadRot(-90);shooter.setInvulnerable(true);shooter.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,30);gun.setBulletInBarrel(stack,true);shooter.setItemSlot(EquipmentSlot.MAINHAND,stack);shooter.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(shooter);
        boolean allied=legacy.allied(target);int bullets=ProjectileProbe.count(shooter.getUUID());
        h.runAtTickTime(140,()->{
            var controller=RuntimeRegistry.active(shooter);h.assertTrue(controller!=null && !controller.failed(),"one optional controller owns native requests");
            if(allied)h.assertTrue(controller.observation()==null && controller.shots()==0 && !new VisibilitySensor().valid(shooter,target,40),"original legacy alliance preserved");
            else h.assertTrue(target.getUUID().equals(controller.contactId()) && controller.shots()>0 && ProjectileProbe.count(shooter.getUUID())>bullets,"legacy controller resolves an independent NPC and fires native projectiles");
            TacticalNpcAiMod.LOGGER.info("STAGE02 optional reverse native alliance={}",allied);target.discard();shooter.discard();h.succeed();
        });
    }
    private static TacticalNpcEntity own(GameTestHelper h,double x) {
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(x,2,7.5));n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.setOnGround(true);n.setFaction("red");n.setInvulnerable(true);n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,30);gun.setBulletInBarrel(stack,true);n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);return n;
    }
}
