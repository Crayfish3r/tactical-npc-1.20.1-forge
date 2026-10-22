package dev.zuma.tacticalnpc;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.entity.NpcEntities;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import com.tacz.guns.api.item.IGun;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
@PrefixGameTestTemplate(false)
public final class ActivationTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="wide_arena",batch="activation-01",timeoutTicks=240)
    public static void dormantMaintenanceAndHonestWake(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(3.5,2,20.5));n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        n.setItemSlot(EquipmentSlot.MAINHAND,GunSupport.create(new ResourceLocation("tacz","ak47")));n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"dormant-test"));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);var far=h.absoluteVec(new Vec3(164.5,2,20.5));p.moveTo(far.x,far.y,far.z);h.getLevel().addNewPlayer(p);
        h.runAtTickTime(145,()->{
            h.assertTrue(n.controller().observation()==null && n.controller().shots()==0,"activation grants no remote player knowledge");
            h.assertTrue(n.controller().reloads()==1 && IGun.getIGunOrNull(n.getMainHandItem()).getCurrentAmmoCount(n.getMainHandItem())>0,"dormant runtime completes native maintenance");
            var close=h.absoluteVec(new Vec3(24.5,2,20.5));p.moveTo(close.x,close.y,close.z);n.setYRot(-90);n.setYHeadRot(-90);n.setYBodyRot(-90);
        });
        h.runAtTickTime(210,()->{h.assertTrue(p.getUUID().equals(n.controller().targetId()) && n.controller().shots()>0 && ProjectileProbe.count(n.getUUID())>0,"near player is acquired through ordinary vision and native fire after wake delay");n.discard();p.discard();h.succeed();});
    }
}
