package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.OrderState;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Same deterministic ordinary-dedicated fixture against frozen before/after production sources. */
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class CombatUpgradeProfile {
    private record Phase(String name,int count,int distance,int ticks){}
    private static final List<Phase> PHASES=List.of(new Phase("range64",1,64,240),new Phase("range96",1,96,240),new Phase("range128",1,128,240),
            new Phase("running64",1,64,320),new Phase("suppression",1,24,320),new Phase("flank1v4",4,32,500),new Phase("load16",16,24,320),new Phase("load30",30,24,320),new Phase("load50",50,24,320));
    private static final List<TacticalNpcEntity> NPCS=new ArrayList<>();
    private static final List<Target> TARGETS=new ArrayList<>();
    private static final List<AiTelemetry.Sample> SAMPLES=new ArrayList<>();
    private static final Set<UUID> SHOOTERS=new HashSet<>(),HITS=new HashSet<>();
    private static MinecraftServer server;private static ServerLevel level;private static int phase=-1,age,rounds,hits,firstShot,firstHit,detected,suppressed,areaRounds,flankTicks;
    private static java.time.Instant measuredStart;
    private static long started;private static double lateral,flankDisplacement;
    private static final java.util.Map<UUID,net.minecraft.world.phys.Vec3> STARTS=new HashMap<>();
    private static final Set<String> ARRIVALS=new HashSet<>();
    private static final class Target extends net.minecraftforge.common.util.FakePlayer {
        Target(int index){super(level,new com.mojang.authlib.GameProfile(new UUID(945,index),"upgrade-"+index));}
        @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
            if(source.getDirectEntity() instanceof com.tacz.guns.entity.EntityKineticBullet bullet && source.getEntity()!=null && SHOOTERS.contains(source.getEntity().getUUID()) && HITS.add(bullet.getUUID())){hits++;if(firstHit<0)firstHit=age;}
            return false;
        }
    }
    @SubscribeEvent public static void start(net.minecraftforge.event.server.ServerStartedEvent e) throws Exception {
        if(!Boolean.getBoolean("tactical.profile.upgrade"))return;
        server=e.getServer();level=server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(6000);
        // Fixture tickets only. Production searches must never add chunk tickets.
        for(int x=62;x<=72;x++)for(int z=62;z<=101;z++)level.setChunkForced(x,z,true);
        for(int x=1000;x<=1150;x++)for(int z=1000;z<=1605;z++)level.setBlock(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState(),2);
        Files.writeString(Path.of("upgrade.csv"),"phase,npc,samples,detected_tick,first_projectile_tick,first_hit_tick,rounds,hits,post_wall_rounds,area_rounds,shooters,flank_ticks,flank_arrivals,flank_displacement,lateral_max,tick_p50_ms,tick_p95_ms,ai_p95_ms,rays_mean,paths_mean,tps\n");
        Files.writeString(Path.of("upgrade-phases.csv"),"phase,start,end\n");
        Files.writeString(Path.of("upgrade-samples.csv"),"phase,tick,server_ns,ai_ns,rays,paths,active\n");
        TacticalNpcAiMod.LOGGER.info("UPGRADE_ENV seed={} java={} cpu={} mods={} simulationDistance={} forcedFixtureChunks=true",
                server.getWorldData().worldGenOptions().seed(),System.getProperty("java.runtime.version"),System.getenv("PROCESSOR_IDENTIFIER"),
                net.minecraftforge.fml.ModList.get().getMods().stream().map(m->m.getModId()+":"+m.getVersion()).toList(),server.getPlayerList().getSimulationDistance());
        next();
    }
    private static void wall(boolean enabled){for(int y=101;y<=105;y++)for(int z=1004;z<=1013;z++)level.setBlock(new BlockPos(1030,y,z),(enabled?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}
    private static void next() throws Exception {
        NPCS.forEach(Entity::discard);TARGETS.forEach(Entity::discard);NPCS.clear();TARGETS.clear();SHOOTERS.clear();HITS.clear();SAMPLES.clear();STARTS.clear();ARRIVALS.clear();wall(false);
        if(++phase==PHASES.size()){
            for(int x=62;x<=72;x++)for(int z=62;z<=101;z++)level.setChunkForced(x,z,false);
            TacticalNpcAiMod.LOGGER.info("UPGRADE_COMPLETE phases={}",PHASES.size());server.halt(false);return;
        }
        age=rounds=hits=suppressed=areaRounds=flankTicks=0;flankDisplacement=0;firstShot=firstHit=detected=-1;lateral=0;
        var p=PHASES.get(phase);boolean squad=p.count==4;
        for(int i=0;i<p.count;i++){
            var n=(TacticalNpcEntity)RuntimeRegistry.create(level).entity();n.setUUID(new UUID(944,i+1));n.getRandom().setSeed(8675309+i);
            n.moveTo(1008.5,101,1008.5+(squad?(i-1.5)*3:i*12),-90,0);n.setYHeadRot(-90);n.setYBodyRot(-90);
            n.setFaction("upgrade-squad");n.setOrder(squad?OrderState.Kind.ASSAULT:OrderState.Kind.HOLD_POSITION,squad?64:6);
            if(!squad)n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
            var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,30);gun.setBulletInBarrel(stack,true);
            n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.runtime().onEquip(stack);level.addFreshEntity(n);NPCS.add(n);STARTS.put(n.getUUID(),n.position());
        }
        for(int i=0;i<(squad?1:p.count);i++){
            var t=new Target(i);t.setGameMode(GameType.SURVIVAL);t.moveTo(1008.5+p.distance,101,1008.5+i*12);level.addNewPlayer(t);TARGETS.add(t);
        }
        started=System.nanoTime();
        TacticalNpcAiMod.LOGGER.info("UPGRADE_PHASE {} count={} distance={}",p.name,p.count,p.distance);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ServerTickEvent e) throws Exception {
        if(server==null || phase>=PHASES.size())return;var p=PHASES.get(phase);
        if(e.phase==TickEvent.Phase.START){
            if(age==40)NPCS.forEach(n->n.addTag(RuntimeRegistry.TAG));
            if(p.name.equals("running64"))TARGETS.get(0).setPos(1072.5,101,1008.5+Math.sin(age*.035)*8);
            if((p.name.equals("suppression") || p.name.equals("flank1v4")) && age==90)wall(true);
            return;
        }
        age++;if(age==40)measuredStart=java.time.Instant.now();
        if(age>=40){
            var s=AiTelemetry.latest(level);if(s!=null)SAMPLES.add(s);
            for(var n:NPCS){
                if(n.controller()!=null && n.controller().failed())throw new IllegalStateException("Controller failed");
                if(detected<0 && n.controller()!=null && n.controller().targetId()!=null)detected=age;
                lateral=Math.max(lateral,Math.abs(n.getZ()-1008.5));
                var d=n.controller()==null?null:n.controller().decision();
                if(d!=null && d.intent()==dev.zuma.tacticalnpc.core.TacticalIntent.FLANK){
                    flankTicks++;flankDisplacement=Math.max(flankDisplacement,n.position().distanceTo(STARTS.get(n.getUUID())));
                    var destination=d.movement().destination();
                    if(destination!=null && n.position().distanceToSqr(new net.minecraft.world.phys.Vec3(destination.x(),destination.y(),destination.z()))<4)ARRIVALS.add(n.getUUID()+":"+destination);
                }
            }
        }
        if(age>=p.ticks){
            double seconds=(System.nanoTime()-started)/1e9;
            Files.writeString(Path.of("upgrade-phases.csv"),p.name+","+measuredStart+","+java.time.Instant.now()+"\n",StandardOpenOption.APPEND);
            String row=String.format(Locale.ROOT,"%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.3f,%.3f,%.4f,%.4f,%.4f,%.2f,%.3f,%.2f%n",p.name,p.count,SAMPLES.size(),detected,firstShot,firstHit,rounds,hits,suppressed,areaRounds,SHOOTERS.size(),flankTicks,ARRIVALS.size(),flankDisplacement,lateral,
                    percentile(AiTelemetry.Sample::serverNanos,.5)/1e6,percentile(AiTelemetry.Sample::serverNanos,.95)/1e6,percentile(AiTelemetry.Sample::aiNanos,.95)/1e6,
                    SAMPLES.stream().mapToInt(AiTelemetry.Sample::rays).average().orElse(0),SAMPLES.stream().mapToInt(AiTelemetry.Sample::paths).average().orElse(0),age/seconds);
            Files.writeString(Path.of("upgrade.csv"),row,StandardOpenOption.APPEND);
            StringBuilder raw=new StringBuilder();for(var s:SAMPLES)raw.append(p.name).append(',').append(s.tick()).append(',').append(s.serverNanos()).append(',').append(s.aiNanos()).append(',').append(s.rays()).append(',').append(s.paths()).append(',').append(s.active()).append('\n');
            Files.writeString(Path.of("upgrade-samples.csv"),raw,StandardOpenOption.APPEND);
            TacticalNpcAiMod.LOGGER.info("UPGRADE_RESULT {} diagnostics={}",row.strip(),AiTelemetry.diagnostics(level));next();
        }
    }
    private static double percentile(java.util.function.ToLongFunction<AiTelemetry.Sample> f,double q){var a=SAMPLES.stream().mapToLong(f).sorted().toArray();return a.length==0?0:a[(int)((a.length-1)*q)];}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void bullet(net.minecraftforge.event.entity.EntityJoinLevelEvent e){
        if(server==null || e.isCanceled() || !(e.getEntity() instanceof com.tacz.guns.entity.EntityKineticBullet b) || b.getOwner()==null || NPCS.stream().noneMatch(n->n.getUUID().equals(b.getOwner().getUUID())))return;
        rounds++;SHOOTERS.add(b.getOwner().getUUID());if(firstShot<0)firstShot=age;
        var controller=RuntimeRegistry.active((LivingEntity)b.getOwner());
        if(controller!=null && controller.decision()!=null && controller.decision().aim().mode()==dev.zuma.tacticalnpc.core.AimCommand.Mode.SUPPRESS_AREA)areaRounds++;
        if(age>=90 && (PHASES.get(phase).name.equals("suppression") || PHASES.get(phase).name.equals("flank1v4")))suppressed++;
    }
}
