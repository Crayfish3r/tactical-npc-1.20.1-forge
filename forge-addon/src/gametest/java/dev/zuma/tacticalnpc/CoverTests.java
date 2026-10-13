package dev.zuma.tacticalnpc;
import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder(TacticalNpcAiMod.ID) @PrefixGameTestTemplate(false)
public final class CoverTests {
    @GameTest(template="wide_arena",batch="cover-01",timeoutTicks=320)
    public static void moveReloadAndPeek(GameTestHelper h) {
        dev.zuma.tacticalnpc.config.ServerConfig.DEBUG.set(true);
        ScenarioIsolation.begin(h);dev.zuma.tacticalnpc.config.ServerConfig.STRAFE.set(0.0);var n=NpcEntities.NPC.get().create(h.getLevel());Vec3 origin=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(origin.x,origin.y,origin.z,-90,0);n.setYHeadRot(-90);
        n.setOrder(dev.zuma.tacticalnpc.core.OrderState.Kind.ASSAULT,24);
        var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,30);gun.setBulletInBarrel(stack,true);n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);
        for(int y=1;y<6;y++)for(int z=8;z<=13;z++)h.setBlock(new BlockPos(6,y,z),Blocks.STONE);
        // Keep the cover/peek position outside the AR's ten-block retreat distance.
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"cover-test"));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);Vec3 at=h.absoluteVec(new Vec3(23.5,2,7.5));p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);
        boolean[] coveredReload={false},peek={false},peekFire={false},emergency={false};long[] maxShotsDuringReload={0},emergencyAt={-1};int[] lastBullets={0};
        h.runAtTickTime(40,()->{gun.setCurrentAmmoCount(stack,0);gun.setBulletInBarrel(stack,false);});
        h.onEachTick(()->{
            if(n.controller()==null || n.controller().decision()==null) return;
            boolean busy=n.runtime().operator().getSynReloadState().getStateType().isReloading();
            boolean hidden=h.getLevel().clip(new net.minecraft.world.level.ClipContext(n.getEyePosition(),new VisibilitySensor().aimPoint(p),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,n)).getType()!=net.minecraft.world.phys.HitResult.Type.MISS;
            if(busy && hidden && n.position().distanceTo(origin)>1) coveredReload[0]=true;
            if(n.controller().decision().intent()==dev.zuma.tacticalnpc.core.TacticalIntent.PEEK) {
                peek[0]=true;if(ProjectileProbe.count(n.getUUID())>lastBullets[0])peekFire[0]=true;
            }
            if(peekFire[0] && emergencyAt[0]<0) {n.setHealth(4);emergencyAt[0]=h.getLevel().getGameTime();}
            if(emergencyAt[0]>=0 && h.getLevel().getGameTime()>emergencyAt[0]+1) {
                h.assertTrue(n.controller().decision().intent()==dev.zuma.tacticalnpc.core.TacticalIntent.RETREAT,"critical HP interrupts real PEEK on next controller tick");
                h.assertTrue(ProjectileProbe.count(n.getUUID())==lastBullets[0],"emergency cancels subsequent native rounds");emergency[0]=true;
            }
            lastBullets[0]=ProjectileProbe.count(n.getUUID());
            if(busy) { if(maxShotsDuringReload[0]==0) maxShotsDuringReload[0]=n.controller().shots()+1;else h.assertTrue(n.controller().shots()+1==maxShotsDuringReload[0],"no accepted shots while native reload busy"); }
            else maxShotsDuringReload[0]=0;
        });
        h.runAtTickTime(270,()->{
            h.assertTrue(coveredReload[0],"NPC actually moved behind body/eye cover during native reload: position="+n.position()+" state="+n.controller().decision());
            h.assertTrue(peek[0] && peekFire[0],"NPC leaves hold for a checked peek and creates actual projectiles during peek");
            h.assertTrue(emergency[0],"real peek emergency interruption exercised");
            p.discard();n.discard();h.succeed();
        });
    }
    @GameTest(template="arena",batch="cover-02",timeoutTicks=80)
    public static void hazardsAndBudgets(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());Vec3 pos=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(pos.x,pos.y,pos.z);h.getLevel().addFreshEntity(n);
        h.runAtTickTime(5,()->{
            var sensor=new CoverSensor(n.runtime());h.setBlock(new BlockPos(5,1,5),Blocks.MAGMA_BLOCK);
            h.assertTrue(sensor.safeStanding(h.absoluteVec(new Vec3(5.5,2,5.5)))==null,"hazard floor rejected");
            var budget=AiWorkBudget.of(h.getLevel());int rays=0;while(budget.ray(n.getUUID())) rays++;
            h.assertTrue(rays<=16,"per-NPC ray work is bounded");
            h.assertTrue(budget.activeRay(n.getUUID()),"cover budget cannot consume the active LOS reserve");
            h.assertTrue(budget.path(n.getUUID()) && !budget.path(n.getUUID()),"path requests coalesced per owner");
            n.discard();h.succeed();
        });
    }
}
