package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.Observation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder(TacticalNpcAiMod.ID) @PrefixGameTestTemplate(false)
public final class PerceptionTests {
    @GameTest(template="arena",batch="perception-01",timeoutTicks=260)
    public static void hiddenPositionFrozenAndExpires(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());
        Vec3 origin=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(origin.x,origin.y,origin.z,-90,0);n.setYHeadRot(-90);
        n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        n.setItemSlot(EquipmentSlot.MAINHAND,GunSupport.create(new ResourceLocation("tacz:ak47")));n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"memory-test"));
        p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);Vec3 at=h.absoluteVec(new Vec3(11.5,2,7.5));p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);
        dev.zuma.tacticalnpc.core.Vector3[] known={null};long[] seen={0};
        h.runAtTickTime(35,()->{
            var observation=n.controller().observation();h.assertTrue(observation!=null && observation.visible(),"perception registers visual track");known[0]=observation.position();seen[0]=observation.tick();
            for(int y=2;y<7;y++)for(int z=0;z<16;z++)h.setBlock(new BlockPos(7,y,z),Blocks.STONE);
            p.setPos(at.x+2,at.y,at.z+3);
        });
        h.runAtTickTime(55,()->{
            Observation memory=n.controller().observation();
            h.assertTrue(memory!=null && !memory.visible() && memory.position().equals(known[0]) && memory.tick()==seen[0],"moving occluded player never refreshes known coordinates");
            h.assertTrue(memory.uncertainty()>0 && memory.velocity().lengthSquared()==0,"stale track is uncertain and not predicted");
            p.setPos(at.x+3,at.y,at.z-3);
        });
        h.runAtTickTime(235,()->{h.assertTrue(n.controller().observation()==null,"visual memory expires");p.discard();n.discard();h.succeed();});
    }
    @GameTest(template="arena",batch="perception-02",timeoutTicks=100)
    public static void hearingIsRegionAndFovIsSeparate(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());Vec3 at=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(at.x,at.y,at.z,90,0);n.setYHeadRot(90);
        n.setItemSlot(EquipmentSlot.MAINHAND,GunSupport.create(new ResourceLocation("tacz:ak47")));n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"heard-test"));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);p.moveTo(at.x+8,at.y,at.z);h.getLevel().addNewPlayer(p);
        for(int y=2;y<7;y++)for(int z=0;z<16;z++)h.setBlock(new BlockPos(7,y,z),Blocks.STONE);
        h.runAtTickTime(10,()->{
            h.assertTrue(n.controller().targetId()==null,"wall and FOV prevent vision");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.common.GunFireEvent(p,p.getMainHandItem(),net.minecraftforge.fml.LogicalSide.SERVER));
            var heard=n.controller().observation();h.assertTrue(heard!=null && heard.source()==Observation.Source.HEARING && !heard.visible() && heard.uncertainty()>=8,"server gun event reports approximate region");
            h.assertFalse(new dev.zuma.tacticalnpc.combat.VisibilitySensor().inFov(n,p),"FOV is an independent gate");
        });
        h.runAtTickTime(80,()->{h.assertTrue(n.controller().observation()==null && n.controller().shots()==0,"sound expires without acquiring hidden target");p.discard();n.discard();h.succeed();});
    }
}
