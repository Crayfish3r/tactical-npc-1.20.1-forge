package dev.zuma.tacticalnpc;

import com.tacz.guns.api.item.IGun;
import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.*;

@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class TargetTypeTests {
    private static UUID capture;
    private static com.tacz.guns.entity.EntityKineticBullet nativeBullet;
    @SubscribeEvent public static void projectile(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if(event.getEntity() instanceof com.tacz.guns.entity.EntityKineticBullet bullet && bullet.getOwner()!=null && bullet.getOwner().getUUID().equals(capture))nativeBullet=bullet;
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="targets-01-duel",timeoutTicks=180)
    public static void opposingNpcsFireNativeRoundsWithoutPlayers(GameTestHelper h) {
        begin(h);var a=npc(h,2.5,7.5,"red",true);var b=npc(h,13.5,7.5,"blue",true);b.setYRot(90);b.setYHeadRot(90);capture=a.getUUID();
        int beforeA=ProjectileProbe.count(a.getUUID()),beforeB=ProjectileProbe.count(b.getUUID());
        h.runAtTickTime(140,()->{
            noPlayers(h);h.assertTrue(a.controller().shots()>0 && b.controller().shots()>0 && ProjectileProbe.count(a.getUUID())>beforeA && ProjectileProbe.count(b.getUUID())>beforeB,"both supported NPCs fire real native projectiles without any player");
            h.assertTrue(b.getUUID().equals(a.controller().contactId()) && a.getUUID().equals(b.controller().contactId()),"generic UUID contact resolution both ways");
            h.assertTrue(nativeBullet!=null,"capture a real native projectile, never construct a substitute");
            var zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(7,2,2));zombie.setNoAi(true);
            h.assertTrue(damageEvent(h,a,zombie),"unsupported victim protected even on native pre-damage event");
            h.assertFalse(damageEvent(h,a,b),"hostile supported NPC damage remains permitted");
            b.setFaction("red");h.assertTrue(damageEvent(h,a,b),"same faction protected without changing the source faction");
            h.assertTrue(a.faction().equals("red"),"no automatic faction conversion");finish(h);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="targets-02-allies",timeoutTicks=180)
    public static void defaultFactionAndScoreboardAlliesNeverAcquire(GameTestHelper h) {
        begin(h);var a=npc(h,2.5,7.5,"hostile",true);var b=npc(h,13.5,7.5,"hostile",true);
        var scoreboard=h.getLevel().getScoreboard();var team=scoreboard.addPlayerTeam("t"+a.getUUID().toString().substring(0,8));
        h.runAtTickTime(60,()->{
            h.assertTrue(a.controller().observation()==null && b.controller().observation()==null && a.controller().shots()==0 && b.controller().shots()==0,"equal default factions remain allied");
            a.setFaction("red");b.setFaction("blue");scoreboard.addPlayerToTeam(a.getScoreboardName(),team);scoreboard.addPlayerToTeam(b.getScoreboardName(),team);
        });
        h.runAtTickTime(140,()->{h.assertTrue(!new VisibilitySensor().valid(a,b,40) && a.controller().observation()==null && a.controller().shots()==0 && b.controller().shots()==0,"scoreboard alliance wins over different factions");scoreboard.removePlayerTeam(team);finish(h);});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="targets-03-types",timeoutTicks=140)
    public static void ordinaryMobsAnimalsArmorStandsAndSelfAreExcluded(GameTestHelper h) {
        begin(h);var a=npc(h,2.5,7.5,"red",true);var zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(10,2,5));zombie.setNoAi(true);zombie.setInvulnerable(true);
        var cow=h.spawn(EntityType.COW,new BlockPos(11,2,7));cow.setNoAi(true);var stand=h.spawn(EntityType.ARMOR_STAND,new BlockPos(10,2,9));
        h.runAtTickTime(100,()->{
            var sensor=new VisibilitySensor();for(var target:List.of(a,zombie,cow,stand))h.assertFalse(sensor.valid(a,target,40),"closed type filter rejects "+target.getType());
            var player=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"excluded-player"));
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);h.assertFalse(CombatTargets.eligible(a,player),"creative player excluded");
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);h.assertFalse(CombatTargets.eligible(a,player),"spectator player excluded");
            noPlayers(h);h.assertTrue(a.controller().observation()==null && a.controller().shots()==0,"excluded nearby living entities never become AI contacts");finish(h);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="targets-04-page",timeoutTicks=55)
    public static void npcPageBeyondSixteenAndDeferredKeepProgress(GameTestHelper h) {
        begin(h);var a=npc(h,3.5,7.5,"red",false);for(int i=0;i<20;i++)npc(h,9.5+i*.01,2.5,"blue",false);var open=npc(h,13.5,7.5,"blue",false);
        for(int x=0;x<=14;x++)for(int y=2;y<=6;y++)h.setBlock(new BlockPos(x,y,4),Blocks.STONE);
        var sensor=new VisibilitySensor();boolean[] found={false};
        h.runAtTickTime(9,()->{
            h.assertTrue(RuntimeRegistry.loadedNpcCount(h.getLevel())>=22,"loaded NPC index includes all 22 identities");
            h.assertTrue(sensor.valid(a,open,40),"open NPC genuinely eligible");h.assertTrue(sensor.inFov(a,open),"open NPC in FOV: yaw="+a.getYHeadRot());
            h.assertTrue(sensor.clear(a,sensor.aimPoint(open)),"open NPC genuinely has LOS beyond the first sixteen indexed entries");
        });
        h.runAtTickTime(10,()->h.assertTrue(sensor.acquire(a,40)==null,"first page really occluded"));
        h.runAtTickTime(11,()->{var budget=AiWorkBudget.of(h.getLevel());while(budget.ray(a.getUUID())){}h.assertTrue(sensor.acquire(a,40)==null && sensor.acquisitionDeferred(),"LOS exhaustion is DEFERRED, not absence");});
        for(int tick=12;tick<=23;tick++)h.runAtTickTime(tick,()->{
            if(found[0])return;int rays=AiWorkBudget.of(h.getLevel()).rays();var target=sensor.acquire(a,40);
            h.assertTrue(target==null || target==open,"never acquire an occluded NPC");h.assertTrue(AiWorkBudget.of(h.getLevel()).rays()-rays<=4,"four-LOS budget retained");found[0]=target==open;
        });
        h.runAtTickTime(25,()->{h.assertTrue(found[0],"NPC beyond cap is reached after deferred cursor continuation");finish(h);});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="targets-05-memory",timeoutTicks=130)
    public static void occludedNpcResolutionNeverRefreshesHiddenCoordinates(GameTestHelper h) {
        begin(h);var a=npc(h,2.5,7.5,"red",true);var b=npc(h,13.5,7.5,"blue",false);Observation[] frozen={null};
        h.runAtTickTime(40,()->{h.assertTrue(a.controller().observation()!=null && a.controller().observation().visible(),"NPC genuinely observed before wall");frozen[0]=a.controller().observation();wall(h);var at=h.absoluteVec(new Vec3(13.5,2,3.5));b.moveTo(at.x,at.y,at.z);});
        // At tick 100 teardown kills the subject. GameTest's runnable map has no callback order;
        // the memory assertion is meaningful only before that deliberately invalidating teardown.
        h.onEachTick(()->{if(h.getTick()<=42 || h.getTick()>=100 || frozen[0]==null || a.controller()==null)return;var memory=a.controller().observation();h.assertTrue(memory!=null && !memory.visible() && memory.position().equals(frozen[0].position()) && memory.tick()==frozen[0].tick() && memory.velocity().lengthSquared()==0,"UUID validity cannot reveal hidden NPC position or velocity: tick="+h.getTick()+" frozen="+frozen[0]+" actual="+memory+" phase="+a.standPhase());h.assertFalse(a.controller().allowNativeFire(),"wall still gates every native round");});
        h.runAtTickTime(100,()->finish(h));
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="targets-06-death",timeoutTicks=150)
    public static void npcDeathClearsContactAndQueuedNativeFire(GameTestHelper h){lifecycle(h,0);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="targets-07-unload",timeoutTicks=150)
    public static void npcUnloadClearsContactAndLoadedIndex(GameTestHelper h){lifecycle(h,1);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="targets-08-dimension",timeoutTicks=150)
    public static void npcDimensionTransferCannotResolveAcrossLevels(GameTestHelper h){lifecycle(h,2);}
    private static void lifecycle(GameTestHelper h,int change) {
        begin(h);var a=npc(h,2.5,7.5,"red",true);var b=npc(h,13.5,7.5,"blue",false);long[] shots={0},generation={0};Entity[] moved={null};
        h.runAtTickTime(55,()->{
            h.assertTrue(a.controller()!=null,"lifecycle source must retain controller; change="+change+" alive="+a.isAlive()+" removed="+a.isRemoved()+" tags="+a.getTags()+" ticks="+a.tickCount+" ticking="+h.getLevel().isPositionEntityTicking(a.blockPosition())+" position="+a.position());
            h.assertTrue(b.getUUID().equals(a.controller().contactId()) && a.controller().shots()>0,"real NPC combat before lifecycle change");generation[0]=a.controller().generation();
            if(change==0){b.setInvulnerable(false);b.hurt(h.getLevel().damageSources().genericKill(),1000);}
            else if(change==1)b.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            else {var nether=h.getLevel().getServer().getLevel(Level.NETHER);moved[0]=b.changeDimension(nether,new net.minecraftforge.common.util.ITeleporter(){
                @Override public net.minecraft.world.level.portal.PortalInfo getPortalInfo(Entity e,ServerLevel dest,java.util.function.Function<ServerLevel,net.minecraft.world.level.portal.PortalInfo> fallback){return new net.minecraft.world.level.portal.PortalInfo(new Vec3(8,110,8),Vec3.ZERO,0,0);}
            });h.assertTrue(moved[0]!=null && moved[0].level()==nether,"real cross-dimension transfer");}
            h.assertTrue(a.controller()!=null,"lifecycle source controller lost during target transfer; alive="+a.isAlive()+" removed="+a.isRemoved()+" tags="+a.getTags()+" position="+a.position());
            h.assertTrue(a.controller().generation()!=generation[0] && a.controller().contactId()==null && a.controller().targetId()==null && a.controller().observation()==null && !a.controller().allowNativeFire(),"lifecycle invalidates target/generation/memory/native gate immediately");shots[0]=a.controller().shots();
            if(change!=0)h.assertTrue(CombatTargets.resolve(h.getLevel(),b.getUUID())==null && RuntimeRegistry.loadedNpcCount(h.getLevel())==1,"old level index and resolver exclude departed NPC");
        });
        h.runAtTickTime(110,()->{h.assertTrue(a.controller().shots()==shots[0] && a.controller().observation()==null,"no stale post-lifecycle fire");if(moved[0]!=null)moved[0].discard();finish(h);});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="targets-09-activation",timeoutTicks=100)
    public static void hiddenNpcWakesLodWithoutGrantingObservation(GameTestHelper h) {
        begin(h);var a=npc(h,2.5,7.5,"red",true);var b=npc(h,13.5,7.5,"blue",false);wall(h);var frames=new HashSet<Long>();
        h.onEachTick(()->{if(h.getTick()>20 && h.getTick()<45 && a.controller()!=null && a.controller().decision()!=null)frames.add(a.controller().decision().frame().tick());});
        h.runAtTickTime(60,()->{
            var sensor=new VisibilitySensor();noPlayers(h);h.assertTrue(sensor.activation(a,64)==VisibilitySensor.Nearby.PRESENT,"supported NPC proximity wakes without players");
            h.assertTrue(frames.size()>=4 && a.controller().observation()==null && a.controller().shots()==0,"active cadence without hidden target knowledge: frames="+frames.size());
            var budget=AiWorkBudget.of(h.getLevel());while(budget.candidate(a.getUUID())){}h.assertTrue(sensor.activation(a,64)==VisibilitySensor.Nearby.DEFERRED,"activation budget deferral is explicit");finish(h);
        });
    }
    private static boolean damageEvent(GameTestHelper h,TacticalNpcEntity owner,LivingEntity victim) {
        var source=h.getLevel().damageSources().generic();var id=new ResourceLocation("tacz:ak47");
        return net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre(nativeBullet,victim,owner,id,id,9,org.apache.commons.lang3.tuple.Pair.of(source,source),false,1,net.minecraftforge.fml.LogicalSide.SERVER));
    }
    private static void begin(GameTestHelper h){ScenarioIsolation.begin(h);capture=null;nativeBullet=null;}
    private static void finish(GameTestHelper h){capture=null;nativeBullet=null;h.killAllEntities();h.succeed();}
    private static void noPlayers(GameTestHelper h){h.assertTrue(h.getLevel().players().stream().noneMatch(p->p.isAlive() && !p.isRemoved()),"fixture has no players");}
    private static void wall(GameTestHelper h){for(int z=0;z<16;z++)for(int y=2;y<=6;y++)h.setBlock(new BlockPos(7,y,z),Blocks.STONE);}
    private static TacticalNpcEntity npc(GameTestHelper h,double x,double z,String faction,boolean controlled) {
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(x,2,z));n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.setYBodyRot(-90);n.setOnGround(true);n.setFaction(faction);n.setInvulnerable(true);n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        if(controlled){var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,30);gun.setBulletInBarrel(stack,true);n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.addTag(RuntimeRegistry.TAG);}
        h.getLevel().addFreshEntity(n);return n;
    }
}
