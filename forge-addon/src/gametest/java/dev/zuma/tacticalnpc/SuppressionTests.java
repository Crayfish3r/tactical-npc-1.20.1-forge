package dev.zuma.tacticalnpc;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
@PrefixGameTestTemplate(false)
public final class SuppressionTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="suppress-01",timeoutTicks=220)
    public static void knownOpenPointWithoutHiddenTracking(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());Vec3 at=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,30);gun.setBulletInBarrel(stack,true);n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"suppression-test"));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);p.moveTo(at.x+8,at.y,at.z);h.getLevel().addNewPlayer(p);
        int[] bullets={0},after={0};dev.zuma.tacticalnpc.core.Vector3[] known={null};
        h.runAtTickTime(35,()->{
            h.assertTrue(n.controller().observation()!=null && n.controller().observation().visible(),"visual track precedes suppression");known[0]=n.controller().observation().position();bullets[0]=ProjectileProbe.count(n.getUUID());
            for(int y=1;y<7;y++)for(int x=1;x<15;x++)h.setBlock(new BlockPos(x,y,10),Blocks.STONE);
            p.setPos(at.x+8,at.y,at.z+6);
        });
        h.runAtTickTime(70,()->{
            var memory=n.controller().observation();after[0]=ProjectileProbe.count(n.getUUID());
            h.assertTrue(memory!=null && !memory.visible() && memory.position().equals(known[0]),"aim region stays at old open point");
            h.assertTrue(after[0]>bullets[0] && after[0]-bullets[0]<=6,"limited real native suppression projectiles: "+(after[0]-bullets[0]));
            p.setPos(at.x+10,at.y,at.z+5);
        });
        h.runAtTickTime(100,()->after[0]=ProjectileProbe.count(n.getUUID()));
        h.runAtTickTime(145,()->{h.assertTrue(ProjectileProbe.count(n.getUUID())==after[0],"expired fire permit stops shooting while general memory may remain");p.discard();n.discard();h.succeed();});
    }
}
