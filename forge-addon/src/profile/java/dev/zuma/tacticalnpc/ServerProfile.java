package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.combat.AiTelemetry;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.OrderState;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Synthetic benchmark on an ordinary dedicated server, never packaged in production. */
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class ServerProfile {
    private record Phase(String name,int count,boolean active,int samples){}
    private static final List<Phase> phases=new ArrayList<>();
    private static final List<PathfinderMob> actors=new ArrayList<>();
    private static final List<ServerPlayer> targets=new ArrayList<>();
    private static final Set<UUID> owners=new HashSet<>();
    private static final Map<UUID,UUID> serviceIds=new HashMap<>();
    private static final List<AiTelemetry.Sample> samples=new ArrayList<>();
    private static int phaseIndex=-1,age,bullets;
    private static long wallStart,cpuStart;
    private static MinecraftServer server;
    private static ServerLevel level;
    private static boolean legacy;
    /** Selectable survival dummy: damage is ignored without the vanilla invulnerable flag. */
    private static final class ProfileTarget extends net.minecraftforge.common.util.FakePlayer {
        ProfileTarget(ServerLevel level,com.mojang.authlib.GameProfile profile){super(level,profile);}
        @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){return false;}
    }
    private static final Path csv=Path.of("profile-summary.csv");
    @SubscribeEvent public static void started(net.minecraftforge.event.server.ServerStartedEvent event) throws Exception {
        if(Boolean.getBoolean("tactical.profile.batch") || Boolean.getBoolean("tactical.profile.team"))return;
        server=event.getServer();level=server.overworld();legacy=Boolean.getBoolean("tactical.profile.legacy");
        if(!legacy && net.minecraftforge.fml.ModList.get().isLoaded("tacznpcs"))throw new IllegalStateException("Standalone profile must not contain NPCs");
        dev.zuma.tacticalnpc.config.ServerConfig.DEBUG.set(false);dev.zuma.tacticalnpc.config.ServerConfig.TELEMETRY.set(false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(6000);
        for(int cx=62;cx<=65;cx++)for(int cz=62;cz<=95;cz++)level.setChunkForced(cx,cz,true);
        for(int x=1000;x<1044;x++)for(int z=1000;z<1520;z++)level.setBlock(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState(),2);
        for(int count:new int[]{1,4,16,30,50}) {
            phases.add(new Phase("baseline",count,false,240));phases.add(new Phase("combat",count,true,240));
        }
        if(!legacy) {
            phases.add(new Phase("dormant",50,true,240));
            phases.add(new Phase("dynamic",30,true,320));phases.add(new Phase("injected-low-tps",30,true,160));
            phases.add(new Phase("unload-reload",30,true,500));
            phases.add(new Phase("short-soak",50,true,2400));
        }
        if(Boolean.getBoolean("tactical.profile.quick")) phases.removeIf(p->!p.name.equals("combat") || p.count!=30 && p.count!=50);
        Files.writeString(csv,"mode,phase,npc,samples,wall_s,tps,tick_p50_ms,tick_p95_ms,ai_p50_ms,ai_p95_ms,rays_mean,candidates_mean,paths_mean,active_mean,heap_p50_mb,heap_p95_mb,cpu_core_percent,native_projectiles\n");
        Files.writeString(Path.of("profile-samples.csv"),"phase,npc,tick,server_ns,ai_ns,rays,candidates,paths,active,heap_bytes\n");
        TacticalNpcAiMod.LOGGER.info("PROFILE_START legacy={} seed={} mods={} map=44x520 stone platform y100; one synthetic invulnerable target/lane; warmup=80ticks",legacy,server.getWorldData().worldGenOptions().seed(),net.minecraftforge.fml.ModList.get().getMods().stream().map(m->m.getModId()+":"+m.getVersion()).toList());
        next();
    }
    private static void next() throws Exception {
        actors.forEach(Entity::discard);targets.forEach(Entity::discard);actors.clear();targets.clear();owners.clear();serviceIds.clear();samples.clear();
        if(++phaseIndex>=phases.size()) {
            TacticalNpcAiMod.LOGGER.info("PROFILE_COMPLETE phases={} standalone={} summary={} jfr=profile.jfr",phases.size(),!legacy,csv.toAbsolutePath());
            for(int cx=62;cx<=65;cx++)for(int cz=62;cz<=95;cz++)level.setChunkForced(cx,cz,false);
            server.halt(false);return;
        }
        var phase=phases.get(phaseIndex);age=0;bullets=0;
        for(int i=0;i<phase.count;i++) {
            var runtime=legacy?RuntimeRegistry.createLegacy(level):RuntimeRegistry.create(level);var n=runtime.entity();
            n.moveTo(1006.5,101,1008.5+i*10,-90,0);n.setYHeadRot(-90);n.setPersistenceRequired();
            var gun=GunSupport.create(new ResourceLocation("tacz","ak47"));var api=IGun.getIGunOrNull(gun);api.setCurrentAmmoCount(gun,30);api.setBulletInBarrel(gun,true);
            n.setItemSlot(EquipmentSlot.MAINHAND,gun);runtime.onEquip(gun);
            if(n instanceof TacticalNpcEntity own){own.setOrder(OrderState.Kind.DEFEND_AREA,6);own.setSquad(new UUID(40,i/4));serviceIds.put(own.getUUID(),own.serviceId());}
            if(phase.active && !legacy)n.addTag(RuntimeRegistry.TAG);level.addFreshEntity(n);actors.add(n);owners.add(n.getUUID());
            var p=new ProfileTarget(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"bench-"+i));p.setGameMode(phase.active?GameType.SURVIVAL:GameType.CREATIVE);p.moveTo(1028.5,101,1008.5+i*10);level.addNewPlayer(p);targets.add(p);
            if(phase.name.equals("dormant"))p.moveTo(1128.5,101,1008.5+i*10);
        }
        TacticalNpcAiMod.LOGGER.info("PROFILE_PHASE mode={} phase={} npc={} sample_ticks={}",legacy?"original-npcs":"independent",phase.name,phase.count,phase.samples);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ServerTickEvent event) throws Exception {
        if(server==null || phaseIndex>=phases.size())return;
        var p=phases.get(phaseIndex);
        if(event.phase==TickEvent.Phase.START) {
            if(p.name.equals("injected-low-tps") && age>=80)Thread.sleep(100);
            if(p.name.equals("unload-reload")) {
                if(age==0){targets.forEach(Entity::discard);targets.clear();for(int cx=62;cx<=65;cx++)for(int cz=62;cz<=95;cz++)level.setChunkForced(cx,cz,false);}
                if(age==400){
                    long remaining=owners.stream().filter(id->level.getEntity(id)!=null).count();
                    if(remaining!=0)throw new IllegalStateException("Profile chunks did not unload: remaining="+remaining);
                    if(actors.stream().anyMatch(n->RuntimeRegistry.active(n)!=null))throw new IllegalStateException("Unloaded actor retained tactical runtime");
                    TacticalNpcAiMod.LOGGER.info("PROFILE_CHUNK_UNLOAD_PASS actors={}",owners.size());
                }
                if(age==420)for(int cx=62;cx<=65;cx++)for(int cz=62;cz<=95;cz++)level.setChunkForced(cx,cz,true);
                if(age==490){actors.clear();for(var id:owners){var restored=level.getEntity(id);if(!(restored instanceof TacticalNpcEntity npc))throw new IllegalStateException("Profile NPC missing after chunk load: "+id);
                    if(!npc.serviceId().equals(serviceIds.get(id)) || !npc.order().equals("DEFEND_AREA") || npc.runtime().squadId()==null || !IGun.getIGunOrNull(npc.getMainHandItem()).getGunId(npc.getMainHandItem()).equals(new ResourceLocation("tacz","ak47")))throw new IllegalStateException("Profile NBT fields lost after reload: "+id);
                    actors.add(npc);}
                    TacticalNpcAiMod.LOGGER.info("PROFILE_CHUNK_RELOAD_PASS actors={}",actors.size());}
            }
            if(p.name.equals("dynamic") && age%40==0) {
                boolean wall=age%80==0;
                for(int i=0;i<actors.size();i++)for(int y=101;y<=104;y++)for(int z=1006+i*10;z<=1011+i*10;z++)level.setBlock(new BlockPos(1016,y,z),(wall?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
            }
            return;
        }
        if(++age==80){wallStart=System.nanoTime();cpuStart=cpu();bullets=0;}
        if(legacy && p.active && age%80==0) diagnoseLegacy();
        if(age>80) {var sample=AiTelemetry.latest(level);if(sample!=null)samples.add(sample);}
        if(age>=80+p.samples) {finish(p);next();}
    }
    private static long cpu(){return ProcessHandle.current().info().totalCpuDuration().orElse(java.time.Duration.ZERO).toNanos();}
    /** Read-only diagnostics of public fields confirmed in the pinned 2.1.0 binary. */
    private static void diagnoseLegacy() throws ReflectiveOperationException {
        if(actors.isEmpty()) return;
        var n=actors.get(0);var goal=n.getClass().getField("fireAtNearestTargetGoal").get(n);
        var api=IGun.getIGunOrNull(n.getMainHandItem());
        var operator=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(n);
        TacticalNpcAiMod.LOGGER.info("PROFILE_LEGACY_DIAGNOSTIC age={} target={} distance={} los={} gun={} rounds={} state={} result={} see={} delay={} reload={} position={}",
            age,n.getTarget()==null?"none":n.getTarget().getUUID(),n.distanceTo(targets.get(0)),n.getSensing().hasLineOfSight(targets.get(0)),
            api==null?"none":api.getGunId(n.getMainHandItem()),api==null?-1:api.getCurrentAmmoCount(n.getMainHandItem()),
            goal.getClass().getField("gunState").get(goal),goal.getClass().getField("lastResult").get(goal),
            goal.getClass().getField("seeTime").get(goal),goal.getClass().getField("attackDelay").get(goal),operator.getDataHolder().reloadStateType,n.position());
    }
    private static void finish(Phase p) throws Exception {
        double wall=(System.nanoTime()-wallStart)/1e9;
        String row=String.format(Locale.ROOT,"%s,%s,%d,%d,%.3f,%.2f,%.4f,%.4f,%.4f,%.4f,%.2f,%.2f,%.3f,%.2f,%.1f,%.1f,%.1f,%d%n",legacy?"original-npcs":"independent",p.name,p.count,samples.size(),wall,samples.size()/wall,
            percentile(AiTelemetry.Sample::serverNanos,.5)/1e6,percentile(AiTelemetry.Sample::serverNanos,.95)/1e6,
            percentile(AiTelemetry.Sample::aiNanos,.5)/1e6,percentile(AiTelemetry.Sample::aiNanos,.95)/1e6,
            mean(AiTelemetry.Sample::rays),mean(AiTelemetry.Sample::candidates),mean(AiTelemetry.Sample::paths),mean(AiTelemetry.Sample::active),
            percentile(AiTelemetry.Sample::heapBytes,.5)/1048576,percentile(AiTelemetry.Sample::heapBytes,.95)/1048576,(cpu()-cpuStart)/1e9/wall*100,bullets);
        Files.writeString(csv,row,StandardOpenOption.APPEND);TacticalNpcAiMod.LOGGER.info("PROFILE_RESULT {}",row.strip());
        var raw=new StringBuilder();for(var s:samples)raw.append(p.name).append(',').append(p.count).append(',').append(s.tick()).append(',').append(s.serverNanos()).append(',').append(s.aiNanos()).append(',').append(s.rays()).append(',').append(s.candidates()).append(',').append(s.paths()).append(',').append(s.active()).append(',').append(s.heapBytes()).append('\n');
        Files.writeString(Path.of("profile-samples.csv"),raw,StandardOpenOption.APPEND);
    }
    private static double percentile(java.util.function.ToLongFunction<AiTelemetry.Sample> f,double q){long[] a=samples.stream().mapToLong(f).sorted().toArray();return a.length==0?0:a[(int)((a.length-1)*q)];}
    private static double mean(java.util.function.ToLongFunction<AiTelemetry.Sample> f){return samples.stream().mapToLong(f).average().orElse(0);}
    @SubscribeEvent public static void bullet(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide && event.getEntity() instanceof com.tacz.guns.entity.EntityKineticBullet b && b.getOwner()!=null && owners.contains(b.getOwner().getUUID()))bullets++;
    }
}
