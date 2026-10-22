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

@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class PerceptionTests {
    private static java.util.UUID traced;
    private static String death="none";
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent event){
        if(event.getEntity().getUUID().equals(traced))death="tick="+event.getEntity().level().getGameTime()+",source="+event.getSource().getMsgId()+",attacker="+event.getSource().getEntity()+",at="+event.getEntity().position();
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="perception-r2-four",timeoutTicks=40)
    public static void acquisitionContinuesPastFourBlocked(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=acquisitionNpc(h,false);
        var players=acquisitionPlayers(h,4,true);var open=players.get(4);
        var sensor=new dev.zuma.tacticalnpc.combat.VisibilitySensor();
        h.runAtTickTime(10,()->{
            h.assertTrue(sensor.inFov(n,open) && sensor.clear(n,sensor.aimPoint(open)),"fifth player really has fresh open LOS in FOV");
            int rays=dev.zuma.tacticalnpc.combat.AiWorkBudget.of(h.getLevel()).rays();
            h.assertTrue(sensor.acquire(n,40)==null,"first four really occluded");
            h.assertTrue(dev.zuma.tacticalnpc.combat.AiWorkBudget.of(h.getLevel()).rays()-rays==4,"four acquisition LOS rays per scan");
        });
        h.runAtTickTime(11,()->{
            h.assertTrue(sensor.acquire(n,40)==open,"R2: fifth visible player must be inspected on the next scan");
            acquisitionFinish(h,n,players);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="perception-r2-fov",timeoutTicks=40)
    public static void acquisitionFiltersFovBeforeLos(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=acquisitionNpc(h,false);
        var players=acquisitionPlayers(h,4,false);var open=players.get(4);
        for(int i=0;i<4;i++) {var at=h.absoluteVec(new Vec3(1.5+i*.2,2,7.5));players.get(i).moveTo(at.x,at.y,at.z);}
        var sensor=new dev.zuma.tacticalnpc.combat.VisibilitySensor();
        h.runAtTickTime(10,()->{
            var budget=dev.zuma.tacticalnpc.combat.AiWorkBudget.of(h.getLevel());int rays=budget.rays(),candidates=budget.candidates();
            h.assertTrue(sensor.acquire(n,40)==open,"near players outside FOV must not occupy the four LOS slots");
            h.assertTrue(budget.rays()-rays==1 && budget.candidates()-candidates==1,"only eligible FOV candidate consumes expensive budget");
            acquisitionFinish(h,n,players);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="perception-r2-cap",timeoutTicks=50)
    public static void acquisitionContinuesBeyondSixteen(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=acquisitionNpc(h,false);
        var players=acquisitionPlayers(h,33,true);var open=players.get(33);
        var sensor=new dev.zuma.tacticalnpc.combat.VisibilitySensor();boolean[] found={false};
        h.runAtTickTime(9,()->{
            var capped=new java.util.ArrayList<net.minecraft.world.entity.player.Player>();
            h.getLevel().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.player.Player.class),
                n.getBoundingBox().inflate(40),p->sensor.valid(n,p,40),capped,16);
            h.assertTrue(capped.size()==16 && !capped.contains(open),"fixture puts the real open player outside the original capped query");
        });
        for(int i=0;i<12;i++) h.runAtTickTime(10+i,()->{
            if(found[0])return;
            int rays=dev.zuma.tacticalnpc.combat.AiWorkBudget.of(h.getLevel()).rays();
            var acquired=sensor.acquire(n,40);
            h.assertTrue(acquired==null || acquired==open,"never acquire an occluded decoy");
            h.assertTrue(dev.zuma.tacticalnpc.combat.AiWorkBudget.of(h.getLevel()).rays()-rays<=4,"LOS cap retained beyond first page");
            found[0]=acquired==open;
        });
        h.runAtTickTime(23,()->{h.assertTrue(found[0],"R2: stable 34-player scene reaches the visible player beyond the 16-player cap");acquisitionFinish(h,n,players);});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="perception-r2-budget",timeoutTicks=40)
    public static void acquisitionDeferredKeepsCursor(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=acquisitionNpc(h,false);
        var players=acquisitionPlayers(h,4,true);var open=players.get(4);
        var sensor=new dev.zuma.tacticalnpc.combat.VisibilitySensor();
        h.runAtTickTime(10,()->{
            h.assertTrue(sensor.acquire(n,40)==null,"consume four blocked candidates before deferral");
            var budget=dev.zuma.tacticalnpc.combat.AiWorkBudget.of(h.getLevel());while(budget.ray(n.getUUID())){}
            h.assertTrue(sensor.los(n,sensor.aimPoint(open),false)==dev.zuma.tacticalnpc.combat.VisibilitySensor.Los.DEFERRED,"real LOS budget exhausted");
            h.assertTrue(sensor.acquire(n,40)==null,"no visibility inferred when budget is deferred");
            h.assertTrue(sensor.acquisitionDeferred(),"budget exhaustion is explicitly DEFERRED, not absence");
        });
        h.runAtTickTime(11,()->{h.assertTrue(sensor.acquire(n,40)==open,"DEFERRED must retry the untested fifth candidate, without restarting or skipping it");acquisitionFinish(h,n,players);});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="perception-r2-runtime",timeoutTicks=60)
    public static void controllerAcquiresFifthWithCadence(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=acquisitionNpc(h,true);
        var players=acquisitionPlayers(h,4,true);var open=players.get(4);
        // The bounded cursor guarantee assumes fixed FOV; idle sweep has separate coverage.
        h.onEachTick(()->{n.setYRot(-90);n.setYBodyRot(-90);n.setYHeadRot(-90);});
        dev.zuma.tacticalnpc.core.Vector3[] known={null};int[] projectiles={0};
        h.runAtTickTime(25,()->{
            h.assertTrue(n.controller()!=null && open.getUUID().equals(n.controller().targetId()),"armed real controller finds fifth player through staggered acquisition/perception: target="+(n.controller()==null?null:n.controller().targetId())+" contact="+(n.controller()==null?null:n.controller().contactId())+" observation="+(n.controller()==null?null:n.controller().observation())+" entityTicks="+n.tickCount+" ticking="+h.getLevel().isPositionEntityTicking(n.blockPosition()));
            h.assertTrue(n.controller().observation()!=null && n.controller().observation().visible() && open.getUUID().equals(n.controller().observation().subject()),"only real visible target becomes an observation");
            known[0]=n.controller().observation().position();projectiles[0]=ProjectileProbe.count(n.getUUID());
            for(int y=2;y<7;y++)for(int z=0;z<16;z++)h.setBlock(new BlockPos(7,y,z),Blocks.STONE);
        });
        for(int i=26;i<30;i++)h.runAtTickTime(i,()->{
            var budget=dev.zuma.tacticalnpc.combat.AiWorkBudget.of(h.getLevel());
            for(int owner=0;owner<11;owner++){var id=java.util.UUID.randomUUID();while(budget.ray(id)){} }
            n.controller().tick();
            var memory=n.controller().observation();
            h.assertTrue(memory!=null && !memory.visible() && memory.position().equals(known[0]) && open.getUUID().equals(n.controller().contactId()),"blocked contact and acquisition budget deferral retain frozen legitimate memory");
            h.assertTrue(ProjectileProbe.count(n.getUUID())==projectiles[0] && !n.controller().allowNativeFire(),"neither a wall nor DEFERRED permits a native round");
        });
        h.runAtTickTime(31,()->acquisitionFinish(h,n,players));
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="perception-r2-pending",timeoutTicks=70)
    public static void acquisitionRetainsVisiblePendingPerception(GameTestHelper h) {
        ScenarioIsolation.begin(h);
        dev.zuma.tacticalnpc.config.ServerConfig.PERCEPTION.set(8);
        dev.zuma.tacticalnpc.config.ServerConfig.ACQUISITION.set(2);
        var n=acquisitionNpc(h,true);var players=acquisitionPlayers(h,17,true);var open=players.get(17);
        // Isolate acquisition/readiness from the existing idle sweep and weapon aim.
        h.onEachTick(()->{n.setYRot(-90);n.setYBodyRot(-90);n.setYHeadRot(-90);});
        h.runAtTickTime(45,()->{
            h.assertTrue(n.controller()!=null && open.getUUID().equals(n.controller().targetId()),"page continuation must not replace a fresh visible candidate still awaiting perception=8 with cadence=2: target="+(n.controller()==null?null:n.controller().targetId())+" contact="+(n.controller()==null?null:n.controller().contactId())+" observation="+(n.controller()==null?null:n.controller().observation())+" entityTicks="+n.tickCount+" ticking="+h.getLevel().isPositionEntityTicking(n.blockPosition())+" players="+h.getLevel().players().size()+" "+dev.zuma.tacticalnpc.combat.AiTelemetry.diagnostics(h.getLevel()));
            h.assertTrue(open.getUUID().equals(n.controller().contactId()),"same identity reaches ContactReadiness without changing perception delay");
            acquisitionFinish(h,n,players);
        });
    }
    private static TacticalNpcEntity acquisitionNpc(GameTestHelper h,boolean controlled) {
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(3.5,2,7.5));
        n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.setYBodyRot(-90);n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        if(controlled) {n.setItemSlot(EquipmentSlot.MAINHAND,GunSupport.create(new ResourceLocation("tacz:ak47")));n.addTag(RuntimeRegistry.TAG);}
        h.getLevel().addFreshEntity(n);return n;
    }
    private static java.util.List<net.minecraftforge.common.util.FakePlayer> acquisitionPlayers(GameTestHelper h,int closed,boolean wall) {
        var players=new java.util.ArrayList<net.minecraftforge.common.util.FakePlayer>();
        for(int i=0;i<=closed;i++) {
            var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"r2-"+i));
            p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);
            var at=h.absoluteVec(i==closed?new Vec3(13.5,2,1.5):new Vec3(8.5+i%4*.4,2,7.5));
            p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);players.add(p);
        }
        if(wall)for(int y=2;y<7;y++)for(int z=6;z<10;z++)h.setBlock(new BlockPos(7,y,z),Blocks.STONE);
        return players;
    }
    private static void acquisitionFinish(GameTestHelper h,TacticalNpcEntity n,java.util.List<net.minecraftforge.common.util.FakePlayer> players) {
        players.forEach(net.minecraft.world.entity.Entity::discard);n.discard();h.succeed();
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="perception-01",timeoutTicks=260)
    public static void hiddenPositionFrozenAndExpires(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());
        traced=n.getUUID();death="none";
        Vec3 origin=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(origin.x,origin.y,origin.z,-90,0);n.setYHeadRot(-90);
        n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        n.setItemSlot(EquipmentSlot.MAINHAND,GunSupport.create(new ResourceLocation("tacz:ak47")));n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"memory-test"));
        p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);Vec3 at=h.absoluteVec(new Vec3(11.5,2,7.5));p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);
        dev.zuma.tacticalnpc.core.Vector3[] known={null};long[] seen={0};
        h.runAtTickTime(35,()->{
            h.assertTrue(n.controller()!=null,"memory fixture controller missing at35: removed="+n.isRemoved()+" reason="+n.getRemovalReason()+" health="+n.getHealth()+" ticking="+h.getLevel().isPositionEntityTicking(n.blockPosition()));
            var observation=n.controller().observation();h.assertTrue(observation!=null && observation.visible(),"perception registers visual track");known[0]=observation.position();seen[0]=observation.tick();
            for(int y=2;y<7;y++)for(int z=0;z<16;z++)h.setBlock(new BlockPos(7,y,z),Blocks.STONE);
            p.setPos(at.x+2,at.y,at.z+3);
        });
        h.runAtTickTime(55,()->{
            h.assertTrue(n.controller()!=null,"memory fixture controller missing at55: removed="+n.isRemoved()+" reason="+n.getRemovalReason()+" health="+n.getHealth()+" ticking="+h.getLevel().isPositionEntityTicking(n.blockPosition()));
            Observation memory=n.controller().observation();
            h.assertTrue(memory!=null && !memory.visible() && memory.position().equals(known[0]) && memory.tick()==seen[0],"moving occluded player never refreshes known coordinates");
            h.assertTrue(memory.uncertainty()>0 && memory.velocity().lengthSquared()==0,"stale track is uncertain and not predicted");
            p.setPos(at.x+3,at.y,at.z-3);
        });
        h.runAtTickTime(235,()->{h.assertTrue(n.controller()!=null,"memory fixture controller missing at235: removed="+n.isRemoved()+" reason="+n.getRemovalReason()+" health="+n.getHealth()+" ticking="+h.getLevel().isPositionEntityTicking(n.blockPosition())+" death="+death);h.assertTrue(n.controller().observation()==null,"visual memory expires");p.discard();n.discard();h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="perception-02",timeoutTicks=100)
    public static void hearingIsRegionAndFovIsSeparate(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());Vec3 at=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(at.x,at.y,at.z,90,0);n.setYHeadRot(90);
        n.setItemSlot(EquipmentSlot.MAINHAND,GunSupport.create(new ResourceLocation("tacz:ak47")));n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"heard-test"));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);p.moveTo(at.x+8,at.y,at.z);h.getLevel().addNewPlayer(p);
        for(int y=2;y<7;y++)for(int z=0;z<16;z++)h.setBlock(new BlockPos(7,y,z),Blocks.STONE);
        h.runAtTickTime(10,()->{
            h.assertTrue(n.controller().targetId()==null,"wall and FOV prevent vision");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.common.GunFireEvent(p,p.getMainHandItem(),net.minecraftforge.fml.LogicalSide.SERVER));
            h.assertTrue(n.controller().observation()==null,"empty/fake GunFireEvent without native projectile cannot fabricate hearing");
            h.assertTrue(n.controller().hear(p),"direct receipt tests hearing eligibility separately from native delivery fixture");
            var heard=n.controller().observation();h.assertTrue(heard!=null && heard.source()==Observation.Source.HEARING && !heard.visible() && heard.uncertainty()>=8,"delivered sound reports approximate region");
            h.assertFalse(new dev.zuma.tacticalnpc.combat.VisibilitySensor().inFov(n,p),"FOV is an independent gate");
        });
        h.runAtTickTime(80,()->{h.assertTrue(n.controller().observation()==null && n.controller().shots()==0,"sound expires without acquiring hidden target");p.discard();n.discard();h.succeed();});
    }
}
