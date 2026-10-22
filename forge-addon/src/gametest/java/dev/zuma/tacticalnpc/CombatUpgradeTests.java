package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.config.ServerConfig;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@PrefixGameTestTemplate(false)
public final class CombatUpgradeTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="upgrade-range64",timeoutTicks=140)
    public static void detection64(GameTestHelper h){range(h,64);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="upgrade-range96",timeoutTicks=140)
    public static void detection96(GameTestHelper h){range(h,96);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="upgrade-range128",timeoutTicks=140)
    public static void detection128(GameTestHelper h){range(h,128);}
    private static void range(GameTestHelper h,int range){
        ScenarioIsolation.begin(h);ServerConfig.normalizeHard();
        var tickets=new java.util.ArrayList<ChunkPos>();
        for(int x=624;x<=634;x++)for(int z=624;z<=626;z++)if(h.getLevel().setChunkForced(x,z,true))tickets.add(new ChunkPos(x,z));
        // Remote lane prevents a long sight ray crossing another GameTest's structure/wall.
        for(int x=10000;x<=10140;x++)for(int z=9997;z<=10004;z++){
            h.getLevel().setBlock(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=101;y<=105;y++)h.getLevel().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        var n=npc(h);n.moveTo(10000.5,101,10000.5,-90,0);n.setYHeadRot(-90);n.setYBodyRot(-90);n.setOrder(OrderState.Kind.HOLD_POSITION,6);
        var p=player(h,new Vec3(3.5,2,7.5));p.setPos(10000.5+range,101,10000.5);
        // doMobSpawning=false does not suppress animals serialized by initial chunk generation.
        for(int tick:new int[]{15,30})h.runAtTickTime(tick,()->h.getLevel().getEntitiesOfClass(LivingEntity.class,n.getBoundingBox().inflate(512)).stream().filter(e->e!=n && e!=p).forEach(Entity::discard));
        h.runAtTickTime(110,()->{
            h.assertTrue(n.tickCount>80 && n.controller()!=null && !n.controller().failed(),"dedicated controller actually ticks at "+range);
            h.assertTrue(n.controller().observation()!=null && n.controller().observation().subject().equals(p.getUUID()),"player acquired at "+range+": "+n.controller().decision()+" npc="+n.position()+" player="+p.position()+" valid="+new VisibilitySensor().valid(n,p,128)+" los="+new VisibilitySensor().los(n,new VisibilitySensor().aimPoint(p),true));
            var w=n.controller().weaponState();
            h.assertTrue(w.effectiveRange()>128 && w.profile().maximum()<64 && w.damageFalloff()<w.effectiveRange(),"preferred/falloff/native reach are separate");
            h.assertTrue(ProjectileProbe.count(n.getUUID())>0,"real native projectiles at "+range+": "+n.controller().decision()+" "+AiTelemetry.diagnostics(h.getLevel())+" living="+h.getLevel().getEntitiesOfClass(LivingEntity.class,n.getBoundingBox().inflate(512)).stream().map(e->e.getType()+":"+e.getUUID()+":"+e.position()+":self="+(e==n)+":target="+(e==p)).toList());
            n.discard();p.discard();tickets.forEach(c->h.getLevel().setChunkForced(c.x,c.z,false));h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="upgrade-hints",timeoutTicks=140)
    public static void legacyUnfairNormalizesToHardAndPreservesAmmo(GameTestHelper h){
        ScenarioIsolation.begin(h);ServerConfig.DEBUG.set(true);ServerConfig.TELEMETRY.set(true);
        ServerConfig.DIFFICULTY.set(ServerConfig.Difficulty.UNFAIR);ServerConfig.HEARING_RANGE.set(192.0);ServerConfig.REACTION.set(20);ServerConfig.TURN.set(2.0);
        var n=npc(h);n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,17);
        h.assertTrue(ServerConfig.normalizeHard() && !ServerConfig.normalizeHard() && !ServerConfig.unfair() && ServerConfig.DIFFICULTY.get()==ServerConfig.Difficulty.HARD && ServerConfig.HEARING_RANGE.get()==128 && ServerConfig.reaction()==3 && ServerConfig.TURN.get()==17,"migration resets every old combat preset and is idempotent");
        h.assertTrue(ServerConfig.DEBUG.get() && ServerConfig.TELEMETRY.get() && n.needCheckAmmo() && n.ammoPolicy()==TacticalNpcEntity.AmmoPolicy.FINITE,"diagnostics and finite policy preserved");
        ServerConfig.DEBUG.set(false);ServerConfig.TELEMETRY.set(false);
        var p=player(h,new Vec3(12.5,2,7.5));for(int y=1;y<=7;y++)for(int z=0;z<=15;z++)h.setBlock(new BlockPos(8,y,z),Blocks.STONE);
        h.runAtTickTime(110,()->{h.assertTrue(n.controller().observation()==null && n.needCheckAmmo() && ProjectileProbe.count(n.getUUID())==0,"old UNFAIR grants no hints, infinite override or wall fire");n.discard();p.discard();h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="upgrade-nochunk",timeoutTicks=30)
    public static void unloadedLosDoesNotLoadChunks(GameTestHelper h){
        ScenarioIsolation.begin(h);var n=npc(h);var sensor=new VisibilitySensor();
        h.runAtTickTime(10,()->{
            var forced=new java.util.HashSet<>(h.getLevel().getForcedChunks());var start=n.getEyePosition();
            // Beyond the fixture's forced area, but inside the bounded 512-block ray contract.
            var point=start.add(400,0,0);var chunk=new ChunkPos(BlockPos.containing(point));
            h.assertTrue(!h.getLevel().hasChunk(chunk.x,chunk.z),"fixture endpoint starts unloaded");
            h.assertTrue(sensor.los(n,point,true)==VisibilitySensor.Los.DEFERRED,"unloaded terrain defers LOS");
            h.assertTrue(!h.getLevel().hasChunk(chunk.x,chunk.z) && forced.equals(h.getLevel().getForcedChunks()),"search/ray adds no loaded or forced chunk");n.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="upgrade-hint-refresh",timeoutTicks=150)
    public static void legacyUnfairCannotRefreshHiddenPosition(GameTestHelper h){
        ScenarioIsolation.begin(h);ServerConfig.DIFFICULTY.set(ServerConfig.Difficulty.UNFAIR);
        var n=npc(h);var p=player(h,new Vec3(12.5,2,7.5));Vector3[] visual={null};
        h.runAtTickTime(30,()->{
            var o=n.controller().observation();h.assertTrue(o!=null && o.visible() && o.source()==Observation.Source.VISION,"ordinary visual track precedes concealment");visual[0]=o.position();
            for(int y=1;y<=7;y++)for(int z=0;z<=15;z++)h.setBlock(new BlockPos(8,y,z),Blocks.STONE);
            var at=h.absoluteVec(new Vec3(12.5,2,13.5));p.setPos(at.x,at.y,at.z);
        });
        h.runAtTickTime(110,()->{
            var o=n.controller().observation();h.assertTrue(o==null || o.source()!=Observation.Source.UNFAIR_HINT && !o.visible() && o.position().equals(visual[0]),"hidden position cannot refresh from legacy config");
            ServerConfig.DIFFICULTY.set(ServerConfig.Difficulty.HARD);n.discard();p.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="upgrade-native-hearing128",timeoutTicks=420)
    public static void genuineNativeShotIsAudibleAt128(GameTestHelper h){
        nativeHearing(h,128);
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="upgrade-native-hearing192",timeoutTicks=420)
    public static void testOnlyNativeHearingAt192RetainsMemory(GameTestHelper h){
        nativeHearing(h,192);
    }
    private static void nativeHearing(GameTestHelper h,int range){
        ScenarioIsolation.begin(h);ServerConfig.normalizeHard();
        // Test-only acoustic experiment, not a gameplay profile.
        ServerConfig.HEARING_RANGE.set((double)range);
        h.assertTrue(ServerConfig.memoryRange()>=ServerConfig.hearingRange()+16,"contact memory covers hearing with hysteresis");
        var tickets=new java.util.ArrayList<ChunkPos>();
        int low=10008-range-8;
        for(int x=Math.floorDiv(low,16)-1;x<=628;x++)for(int z=624;z<=626;z++)if(h.getLevel().setChunkForced(x,z,true))tickets.add(new ChunkPos(x,z));
        for(int x=low;x<=10040;x++)for(int z=9998;z<=10002;z++){
            h.getLevel().setBlock(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=101;y<=110;y++)h.getLevel().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        var shooter=npc(h);shooter.moveTo(10008.5,101,10000.5,-90,0);shooter.setYHeadRot(-90);shooter.setYBodyRot(-90);shooter.setFaction("hearing-source");shooter.setOrder(OrderState.Kind.HOLD_POSITION,6);
        var listener=npc(h);listener.setPos(10008.5-range,101,10000.5);listener.setNoAi(true);listener.setInvisible(true);listener.setFaction("hearing-listener");
        // Static acoustic measurement actors must not be knocked off the exact
        // distance or driven into a critical-health retreat by unrelated damage.
        // Native firing, cooldown, actual projectile and HEARING checks stay enabled.
        shooter.setInvulnerable(true);listener.setInvulnerable(true);
        var p=player(h,new Vec3(12.5,2,7.5));p.setPos(10024.5,101,10000.5);boolean[] heard={false};
        for(int tick:new int[]{15,30})h.runAtTickTime(tick,()->h.getLevel().getEntitiesOfClass(LivingEntity.class,shooter.getBoundingBox().inflate(512)).stream().filter(e->e!=shooter && e!=listener && e!=p).forEach(Entity::discard));
        int[] ready={-1};
        h.onEachTick(()->{
            var o=listener.controller()==null?null:listener.controller().observation();
            if(o!=null && o.source()==Observation.Source.HEARING && o.subject().equals(shooter.getUUID()))heard[0]=true;
            // These actors are relocated outside the template into fresh remote chunks.
            // Measure the original 140-tick combat window in actual entity ticks after
            // activation, rather than consuming it while those chunks become ticking.
            if(ready[0]<0){
                if(shooter.tickCount>=5 && listener.tickCount>=5 && h.getLevel().isPositionEntityTicking(shooter.blockPosition()) && h.getLevel().isPositionEntityTicking(listener.blockPosition()) && h.getLevel().isPositionEntityTicking(p.blockPosition()))ready[0]=shooter.tickCount;
                return;
            }
            if(shooter.tickCount-ready[0]<140)return;
            h.assertTrue(shooter.distanceToSqr(listener)==range*range && ProjectileProbe.count(shooter.getUUID())>0 && heard[0],"native joined projectile delivers genuine regional hearing at"+range+" without visual observation: distanceSq="+shooter.distanceToSqr(listener)+" rounds="+ProjectileProbe.count(shooter.getUUID())+" heard="+heard[0]+" shooter="+shooter.position()+" listener="+listener.position()+" entityTicks="+shooter.tickCount+"/"+listener.tickCount+" ticking="+h.getLevel().isPositionEntityTicking(shooter.blockPosition())+"/"+h.getLevel().isPositionEntityTicking(listener.blockPosition())+" decision="+(shooter.controller()==null?null:shooter.controller().decision())+" listenerObservation="+(listener.controller()==null?null:listener.controller().observation())+" hearing="+java.util.Arrays.toString(dev.zuma.tacticalnpc.combat.GunHearing.measurements(h.getLevel())));
            shooter.discard();listener.discard();p.discard();ServerConfig.HEARING_RANGE.set(128.0);tickets.forEach(c->h.getLevel().setChunkForced(c.x,c.z,false));ServerConfig.DIFFICULTY.set(ServerConfig.Difficulty.HARD);h.succeed();
        });
        h.runAtTickTime(410,()->h.fail("native hearing fixture did not complete its 140 active ticks: ready="+ready[0]+" entityTicks="+shooter.tickCount+"/"+listener.tickCount));
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="upgrade-safe-retarget",timeoutTicks=230)
    public static void unsafeVisibleContactDoesNotMonopolizeFirstPage(GameTestHelper h){
        ScenarioIsolation.begin(h);ServerConfig.DEBUG.set(true);
        // Measure unsafe-contact acquisition, independent of fall/damage/critical-HP
        // interruptions. Both target pages stand in the same explicit flat room.
        for(int x=1;x<=36;x++)for(int z=29;z<=38;z++){
            h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            for(int y=2;y<=8;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        var n=npc(h);n.setInvulnerable(true);var at=h.absoluteVec(new Vec3(3.5,2,32.5));n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.setYBodyRot(-90);n.setOrder(OrderState.Kind.HOLD_POSITION,6);
        var players=new java.util.ArrayList<net.minecraft.server.level.ServerPlayer>();
        for(int i=0;i<16;i++)players.add(player(h,new Vec3(30.5,2,32.5+i*.15)));
        var near=player(h,new Vec3(14.5,2,32.5));players.add(near);
        h.runAtTickTime(20,()->{h.assertTrue(n.getHealth()/n.getMaxHealth()>.4,"retarget fixture starts outside critical-health policy");h.assertTrue(n.controller().observation()!=null && !near.getUUID().equals(n.controller().observation().subject()),"first sensor page initially contains only distant visible contacts");});
        h.runAtTickTime(190,()->{
            h.assertTrue(near.getUUID().equals(n.controller().targetId()),"sustained unsafe corridor resumes bounded page cursor and selects the closer genuinely visible target: hp="+n.getHealth()+"/"+n.getMaxHealth()+" npc="+n.position()+" near="+near.position()+" nearAlive="+near.isAlive()+" ticks="+n.tickCount+" target="+n.controller().targetId()+" decision="+n.controller().decision()+" observation="+n.controller().observation()+" "+retargetDiagnostics(n.controller()));
            players.forEach(Entity::discard);n.discard();h.succeed();
        });
    }
    private static String retargetDiagnostics(TacticalCombatGoal goal){
        try{
            var text=new StringBuilder();
            for(String name:new String[]{"blockedFireTicks","nextOpportunityScan","nextTargetChange","nextScan","candidateId","activeLos"}){var f=TacticalCombatGoal.class.getDeclaredField(name);f.setAccessible(true);text.append(name).append('=').append(f.get(goal)).append(' ');}
            var f=TacticalCombatGoal.class.getDeclaredField("sensor");f.setAccessible(true);var sensor=(VisibilitySensor)f.get(goal);text.append("sector=").append(sensor.lastSector());
            return text.toString();
        }catch(ReflectiveOperationException e){return e.toString();}
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="upgrade-finite-reload",timeoutTicks=330)
    public static void legacyUnfairCannotOverrideFiniteReserve(GameTestHelper h){
        ScenarioIsolation.begin(h);ServerConfig.DIFFICULTY.set(ServerConfig.Difficulty.UNFAIR);
        var n=npc(h);n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,0);
        var gun=IGun.getIGunOrNull(n.getMainHandItem());gun.setCurrentAmmoCount(n.getMainHandItem(),1);gun.setBulletInBarrel(n.getMainHandItem(),true);
        var p=player(h,new Vec3(14.5,2,7.5));
        h.runAtTickTime(120,()->{h.assertTrue(n.needCheckAmmo() && n.controller().reloads()==0 && ProjectileProbe.count(n.getUUID())<=2,"legacy UNFAIR never bypasses saved finite reserve0");ServerConfig.normalizeHard();});
        h.runAtTickTime(300,()->{
            var state=n.controller().weaponState();
            h.assertTrue(n.needCheckAmmo() && n.ammoPolicy()==TacticalNpcEntity.AmmoPolicy.FINITE && !state.reserve() && state.rounds()==0 && n.controller().reloads()==0,"migration leaves finite policy/native exhaustion unchanged");
            n.discard();p.discard();h.succeed();
        });
    }
    private static TacticalNpcEntity npc(GameTestHelper h){
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.setYBodyRot(-90);
        n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,30);gun.setBulletInBarrel(stack,true);n.setItemSlot(EquipmentSlot.MAINHAND,stack);
        n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);return n;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,Vec3 local){
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"upgrade-test"));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);var at=h.absoluteVec(local);p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);return p;
    }
}
