package dev.zuma.tacticalnpc;

import com.mojang.authlib.GameProfile;
import dev.zuma.tacticalnpc.compat.SbwGrenades;
import dev.zuma.tacticalnpc.config.ServerConfig;
import dev.zuma.tacticalnpc.entity.NpcEntities;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native SBW checks are selected only in the opted-in dependency environment. */
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class SbwGrenadeTests {
    private static final Map<UUID,Probe> PROBES=new HashMap<>();
    private static final class Probe {
        int joined, detonated, companionBlasts;
        Vec3 impact;
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void joined(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide || !SbwGrenades.marked(event.getEntity()))return;
        var source=(Projectile)event.getEntity();
        var owner=source.getOwner();
        var probe=owner==null?null:PROBES.get(owner.getUUID());
        if(probe!=null)probe.joined++;
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void detonated(ExplosionEvent.Detonate event) {
        var source=event.getExplosion().getDirectSourceEntity();
        if(!SbwGrenades.marked(source)) {
            if(source!=null && event.getExplosion().getClass()==net.minecraft.world.level.Explosion.class) {
                var probe=PROBES.get(source.getUUID());
                if(probe!=null)probe.companionBlasts++;
            }
            return;
        }
        var owner=((Projectile)source).getOwner();
        var probe=owner==null?null:PROBES.get(owner.getUUID());
        if(probe!=null){
            probe.detonated++;
            probe.impact=event.getExplosion().getPosition();
        }
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="sbw-01-rgo",timeoutTicks=1400)
    public static void rgoOverWallAt32Blocks(GameTestHelper h) {
        ScenarioIsolation.begin(h);
        run(h,NpcEntities.NPC.get().create(h.getLevel()),10);
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="sbw-03-short",timeoutTicks=310)
    public static void rgoOverWallAt16Blocks(GameTestHelper h) {
        ScenarioIsolation.begin(h);
        run(h,NpcEntities.NPC.get().create(h.getLevel()),1,16);
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="sbw-04-medium",timeoutTicks=310)
    public static void rgoOverWallAt24Blocks(GameTestHelper h) {
        ScenarioIsolation.begin(h);
        run(h,NpcEntities.NPC.get().create(h.getLevel()),1,24);
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="sbw-05-mobile",timeoutTicks=310)
    public static void mobileArmedNpcBacksUpAndThrowsAtAirborneSighting(GameTestHelper h) {
        ScenarioIsolation.begin(h);
        run(h,NpcEntities.NPC.get().create(h.getLevel()),1,9,true);
    }

    public static void run(GameTestHelper h, PathfinderMob npc,int rounds) {
        run(h,npc,rounds,32);
    }

    private static void run(GameTestHelper h, PathfinderMob npc,int rounds,int distance) {
        run(h,npc,rounds,distance,false);
    }

    private static void run(GameTestHelper h, PathfinderMob npc,int rounds,int distance,boolean mobile) {
        h.assertTrue(SbwGrenades.available(),"verified SBW RGO adapter enabled");
        ServerConfig.GRENADE_STOCK.set(rounds);
        ServerConfig.GRENADE_COOLDOWN.set(20);
        ServerConfig.DEBUG.set(true);
        var launch=h.absoluteVec(new Vec3(6.5,2,20.5));
        var remembered=h.absoluteVec(new Vec3(6.5+distance,mobile?3.5:2,20.5));
        var hidden=h.absoluteVec(new Vec3(22.5+distance,2,20.5));
        int wallX=distance==32?21:6+distance/2;
        for(int x=0;x<=58;x++)for(int z=15;z<=28;z++){
            h.setBlock(new BlockPos(x,1,z),Blocks.BEDROCK);
            for(int y=2;y<=6;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        npc.setUUID(new UUID(904, distance));
        npc.getRandom().setSeed(904L+distance);
        npc.moveTo(launch.x,launch.y,launch.z,-90,0);
        npc.setYHeadRot(-90);
        npc.setYBodyRot(-90);
        if(mobile && npc instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity own){
            own.setOrder(dev.zuma.tacticalnpc.core.OrderState.Kind.ASSAULT,64);
            var stack=dev.zuma.tacticalnpc.compat.GunSupport.create(new net.minecraft.resources.ResourceLocation("tacz:glock_17"));
            var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
            gun.setCurrentAmmoCount(stack,17);
            gun.setBulletInBarrel(stack,true);
            npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,stack);
        } else npc.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        npc.addTag(RuntimeRegistry.TAG);
        var team=h.getLevel().getScoreboard().addPlayerTeam("rgo-allies");
        h.getLevel().getScoreboard().addPlayerToTeam(npc.getScoreboardName(),team);
        h.getLevel().addFreshEntity(npc);
        var target=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"sbw-target"));
        target.setGameMode(GameType.SURVIVAL);
        target.setInvulnerable(true);
        target.setNoGravity(mobile);
        target.moveTo(remembered.x,remembered.y,remembered.z);
        h.getLevel().addNewPlayer(target);
        var probe=new Probe();
        PROBES.clear();
        PROBES.put(npc.getUUID(),probe);
        class Scene {
            int completed, accurate;
            long blockedAt;
            boolean blocked;
            boolean backedUp;
            String grenadeState;
            PathfinderMob ally;
        }
        var scene=new Scene();
        h.onEachTick(()->{
            h.assertTrue(npc.isAlive(),"launch position became unsafe: feet="+h.getLevel().getBlockState(npc.blockPosition())+
                    " head="+h.getLevel().getBlockState(npc.blockPosition().above()));
            var goal=RuntimeRegistry.active(npc);
            if(goal==null)return;
            var known=goal.observation();
            if(!scene.blocked && h.getTick()>=scene.blockedAt && known!=null && known.visible() && known.subject().equals(target.getUUID())) {
                h.assertTrue(probe.joined==scene.completed,"no RGO while visual contact is clear");
                wall(h,wallX,mobile);
                target.moveTo(hidden.x,hidden.y,hidden.z);
                scene.blockedAt=h.getTick();
                scene.blocked=true;
            }
            if(scene.blocked && probe.joined>scene.completed && scene.completed==0 && scene.ally==null) {
                var ally=NpcEntities.NPC.get().create(h.getLevel());
                var location=remembered.add(0,0,4);
                ally.moveTo(location.x,location.y,location.z);
                h.getLevel().getScoreboard().addPlayerToTeam(ally.getScoreboardName(),team);
                h.getLevel().addFreshEntity(ally);
                scene.ally=ally;
            }
            var grenadeState=goal.grenadeDiagnostics();
            if(mobile && !grenadeState.equals(scene.grenadeState)) {
                TacticalNpcAiMod.LOGGER.info("SBW_MOBILE tick={} {} position={}",h.getTick(),grenadeState,npc.position());
                scene.grenadeState=grenadeState;
            }
            if(scene.blocked && grenadeState.contains("moving clear of blast area") && npc.position().x<launch.x-1)scene.backedUp=true;
            if(probe.detonated>scene.completed) {
                scene.completed++;
                double error=probe.impact.subtract(remembered).horizontalDistance();
                if(error<=3)scene.accurate++;
                TacticalNpcAiMod.LOGGER.info("SBW_RGO distance={} trial={} error={} plannedArea={} nativeDetonation={}",distance,scene.completed,error,remembered,probe.impact);
                h.assertTrue(probe.joined==scene.completed,"one entity join per occlusion");
                h.assertTrue(probe.companionBlasts==(SbwGrenades.legacyImpact()?scene.completed:0),"native companion blast count");
                h.assertTrue(SbwGrenades.stock(npc)==rounds-scene.completed,"one RGO debited per successful launch");
                if(scene.ally!=null){
                    h.assertTrue(scene.ally.getHealth()==scene.ally.getMaxHealth(),"SBW blast excludes an ally entering after launch");
                    scene.ally.discard();scene.ally=null;
                }
                if(scene.completed==rounds) {
                    h.assertTrue(!mobile || scene.backedUp,"mobile NPC first made room for safe blast distance");
                    h.assertTrue(scene.accurate>=Math.ceil(rounds*.9),distance+"-block accuracy: "+scene.accurate+"/"+rounds);
                    PROBES.remove(npc.getUUID());
                    npc.discard();target.discard();
                    ServerConfig.GRENADE_STOCK.set(ServerConfig.GRENADE_STOCK.getDefault());
                    ServerConfig.GRENADE_COOLDOWN.set(ServerConfig.GRENADE_COOLDOWN.getDefault());
                    h.succeed();return;
                }
                scene.blocked=false;
                // Native blast changes terrain and can leave velocity behind. Reconstruct
                // the same firing lane before measuring the next sample in the series.
                for(int x=0;x<=58;x++)for(int z=18;z<=22;z++){
                    h.setBlock(new BlockPos(x,1,z),Blocks.BEDROCK);
                    for(int y=2;y<=8;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
                }
                npc.moveTo(launch.x,launch.y,launch.z,-90,0);
                npc.setYHeadRot(-90);
                npc.setYBodyRot(-90);
                npc.setDeltaMovement(Vec3.ZERO);
                target.moveTo(remembered.x,remembered.y,remembered.z);
                target.setDeltaMovement(Vec3.ZERO);
                // Wait out the inter-throw cooldown in clear sight before closing the next wall.
                scene.blockedAt=h.getTick()+20;
            }
            if(!scene.blocked && h.getTick()>scene.blockedAt+40)
                h.fail("no visual sighting in reopened lane; target="+target.position()+" alive="+target.isAlive()+
                        " owner="+npc.position()+" yaw="+npc.getYHeadRot()+" "+goal.fireDiagnostics());
            if(scene.blocked && h.getTick()>scene.blockedAt+100)
                h.fail("RGO missing after occlusion; "+goal.grenadeDiagnostics()+" joined="+probe.joined+" detonation="+probe.detonated+
                        " observation="+known+" position="+npc.position()+" "+goal.fireDiagnostics());
        });
        h.runAtTickTime(rounds==1?290:1380,()->h.assertTrue(npc.isRemoved(),"RGO scenario did not finish, rounds="+scene.completed));
    }

    private static void wall(GameTestHelper h,int x,boolean wide){
        int left=wide?15:18,right=wide?28:22;
        for(int y=2;y<=6;y++)for(int z=left;z<=right;z++)h.setBlock(new BlockPos(x,y,z),Blocks.OBSIDIAN);
    }
}
