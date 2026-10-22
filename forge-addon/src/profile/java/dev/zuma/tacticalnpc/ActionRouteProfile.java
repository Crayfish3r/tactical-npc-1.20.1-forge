package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import java.util.*;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Ordinary dedicated, same source against frozen baseline/current. One movement owner per actor. */
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class ActionRouteProfile {
    private static MinecraftServer server;private static ServerLevel level;
    private static int phase=-1,age,warmup,rounds,hits,firstShot,firstHit;
    private static final int[] COUNTS={1,4,16,30,50};
    private static final List<TacticalNpcEntity> NPCS=new ArrayList<>();
    private static final List<MovementExecutor> MOVES=new ArrayList<>();
    private static final List<Target> TARGETS=new ArrayList<>();
    private static final Map<UUID,Integer> ARRIVED=new HashMap<>(),TICKS=new HashMap<>();
    private static final Map<UUID,Integer> LAST_TICKS=new HashMap<>();
    private static final Map<UUID,Long> DRIVEN=new HashMap<>();
    private record Activity(int alive,int ticking,int drivers,int combat){}
    private static final List<Activity> ACTIVITY=new ArrayList<>();
    private static final Set<UUID> SHOOTERS=new HashSet<>(),HITS=new HashSet<>();
    private static final List<AiTelemetry.Sample> SAMPLES=new ArrayList<>();
    private static long started;private static java.time.Instant begin;
    private static final class Target extends net.minecraftforge.common.util.FakePlayer {
        Target(int i){super(level,new com.mojang.authlib.GameProfile(new UUID(1782,i),"actions-"+i));}
        @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
            if(source.getDirectEntity() instanceof com.tacz.guns.entity.EntityKineticBullet bullet && source.getEntity()!=null && SHOOTERS.contains(source.getEntity().getUUID()) && HITS.add(bullet.getUUID())){hits++;if(firstHit<0)firstHit=age-40;}return false;
        }
    }
    @SubscribeEvent public static void start(net.minecraftforge.event.server.ServerStartedEvent e)throws Exception{
        if(!Boolean.getBoolean("tactical.profile.actions"))return;
        server=e.getServer();level=server.overworld();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,server);
        dev.zuma.tacticalnpc.config.ServerConfig.DEBUG.set(false);dev.zuma.tacticalnpc.config.ServerConfig.TELEMETRY.set(false);
        for(int x=62;x<=65;x++)for(int z=62;z<=71;z++)level.setChunkForced(x,z,true);
        for(int x=1000;x<=1040;x++)for(int z=1000;z<=1140;z++){
            level.setBlock(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=101;y<=113;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        for(int group=0;group<13;group++){
            int z=1004+group*10;
            for(int x=1000;x<=1015;x++)for(int y=101;y<=111;y++)for(int side:new int[]{-3,3})level.setBlock(new BlockPos(x,y,z+side),Blocks.STONE.defaultBlockState(),2);
            for(int y=101;y<=111;y++)for(int dz=-2;dz<=2;dz++)if(dz!=0 || y>102)level.setBlock(new BlockPos(1005,y,z+dz),Blocks.STONE.defaultBlockState(),2);
            var door=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST);
            level.setBlock(new BlockPos(1005,101,z),door,3);level.setBlock(new BlockPos(1005,102,z),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),3);
            for(int x=1008;x<=1030;x++)for(int dz=-2;dz<=2;dz++)level.setBlock(new BlockPos(x,106,z+dz),Blocks.STONE.defaultBlockState(),2);
            for(int y=101;y<=106;y++){
                level.setBlock(new BlockPos(1008,y,z),Blocks.STONE.defaultBlockState(),2);
                level.setBlock(new BlockPos(1007,y,z),Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.WEST),3);
            }
        }
        Files.writeString(Path.of("actions.csv"),"npc,groups,samples,arrivals,arrival_p50_ticks,arrival_max_ticks,rounds,hits,shooters,first_projectile_tick,first_hit_tick,tick_p50_ms,tick_p95_ms,ai_p95_ms,paths_mean,alive_min,ticking_min,route_drivers_mean,combat_owners_mean\n");
        Files.writeString(Path.of("actions-samples.csv"),"phase,tick,server_ns,ai_ns,paths,registered_combat_goals,alive,ticking,route_drivers,combat_owners\n");Files.writeString(Path.of("actions-phases.csv"),"phase,start,end\n");
        Files.writeString(Path.of("actions-actors.csv"),"npc_count,index,arrival_tick,x,y,z,diagnostics\n");
        TacticalNpcAiMod.LOGGER.info("ACTIONS_ENV seed={} java={} cpu={} mods={}",server.getWorldData().worldGenOptions().seed(),System.getProperty("java.runtime.version"),System.getenv("PROCESSOR_IDENTIFIER"),net.minecraftforge.fml.ModList.get().getMods().stream().map(m->m.getModId()+":"+m.getVersion()).toList());
        next();
    }
    private static void next()throws Exception{
        NPCS.forEach(Entity::discard);TARGETS.forEach(Entity::discard);NPCS.clear();MOVES.clear();TARGETS.clear();ARRIVED.clear();TICKS.clear();LAST_TICKS.clear();DRIVEN.clear();ACTIVITY.clear();SHOOTERS.clear();HITS.clear();SAMPLES.clear();
        if(++phase==COUNTS.length){for(int x=62;x<=65;x++)for(int z=62;z<=71;z++)level.setChunkForced(x,z,false);TacticalNpcAiMod.LOGGER.info("ACTIONS_COMPLETE phases=5");server.halt(false);return;}
        age=warmup=rounds=hits=0;firstShot=firstHit=-1;
        for(int i=0;i<COUNTS[phase];i++){
            int group=i/4,j=i%4,z=1004+group*10;
            var n=(TacticalNpcEntity)RuntimeRegistry.create(level).entity();n.setUUID(new UUID(1781,i+1));n.getRandom().setSeed(8675309+i);
            n.moveTo(1001.5+(j%2),101,z-.65+(j/2)*1.3,-90,0);n.setYHeadRot(-90);n.setFaction("route-"+group);n.setOrder(OrderState.Kind.ASSAULT,64);
            var stack=GunSupport.create(new net.minecraft.resources.ResourceLocation("tacz:glock_17"));var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,17);gun.setBulletInBarrel(stack,true);
            n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.runtime().onEquip(stack);level.addFreshEntity(n);NPCS.add(n);MOVES.add(new MovementExecutor(n.runtime()));
            if(j==0){var t=new Target(group);t.setGameMode(GameType.SURVIVAL);t.moveTo(1025.5,107,z+.5);level.addNewPlayer(t);TARGETS.add(t);}
            var p=new BlockPos(1005,101,z);var s=level.getBlockState(p);((DoorBlock)s.getBlock()).setOpen(null,level,s,p,false);
        }
        started=System.nanoTime();TacticalNpcAiMod.LOGGER.info("ACTIONS_PHASE npc={} groups={}",COUNTS[phase],(COUNTS[phase]+3)/4);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void drive(TickEvent.LevelTickEvent e){
        if(server==null || phase>=COUNTS.length || e.phase!=TickEvent.Phase.END || e.level!=level || age<40)return;
        for(int i=0;i<NPCS.size();i++){
            var n=NPCS.get(i);if(ARRIVED.containsKey(n.getUUID()) || TICKS.getOrDefault(n.getUUID(),-1)==n.tickCount)continue;TICKS.put(n.getUUID(),n.tickCount);
            var exit=new Vec3(1011.5,107,1002.5+(i/4)*10+i%4);
            DRIVEN.put(n.getUUID(),level.getGameTime());
            long before=AiTelemetry.begin(level);
            try{MOVES.get(i).apply(MovementCommand.navigate(new Vector3(exit.x,exit.y,exit.z)),level.getGameTime());}finally{AiTelemetry.end(level,before);}
            if(n.onGround() && n.position().distanceTo(exit)<.7 && complete(MOVES.get(i))){
                ARRIVED.put(n.getUUID(),age-40);MOVES.get(i).stop(level.getGameTime());
                n.setOrder(OrderState.Kind.HOLD_POSITION,6);n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);n.addTag(RuntimeRegistry.TAG);
                TacticalNpcAiMod.LOGGER.info("ACTIONS_ARRIVAL npc={} tick={}",i,age-40);
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ServerTickEvent e)throws Exception{
        if(server==null || phase>=COUNTS.length)return;
        if(e.phase==TickEvent.Phase.START)return;
        int alive=0,ticking=0,drivers=0,combat=0;
        for(var n:NPCS){
            var previous=LAST_TICKS.put(n.getUUID(),n.tickCount);
            if(n.isAlive() && !n.isRemoved())alive++;
            if(previous!=null && n.tickCount>previous && level.isPositionEntityTicking(n.blockPosition())){
                ticking++;
                if(DRIVEN.getOrDefault(n.getUUID(),-1L)==level.getGameTime())drivers++;
                if(n.controller()!=null)combat++;
            }
        }
        if(age<40){
            if(++warmup>240)throw new IllegalStateException("Action fixture failed entity-ticking readiness: "+ticking+"/"+NPCS.size());
            if(age==39 && (alive!=NPCS.size() || ticking!=NPCS.size() || NPCS.stream().anyMatch(n->n.tickCount<5)))return;
        }
        age++;if(age==40)begin=java.time.Instant.now();
        if(age>=40){var s=AiTelemetry.latest(level);if(s!=null){SAMPLES.add(s);ACTIVITY.add(new Activity(alive,ticking,drivers,combat));}}
        if(age<1000)return;
        double p50=ARRIVED.values().stream().mapToInt(Integer::intValue).sorted().skip(Math.max(0,(ARRIVED.size()-1)/2)).findFirst().orElse(-1);
        String row=String.format(Locale.ROOT,"%d,%d,%d,%d,%.0f,%d,%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.3f,%d,%d,%.3f,%.3f%n",COUNTS[phase],(COUNTS[phase]+3)/4,SAMPLES.size(),ARRIVED.size(),p50,ARRIVED.values().stream().mapToInt(Integer::intValue).max().orElse(-1),rounds,hits,SHOOTERS.size(),firstShot,firstHit,percentile(false,.5),percentile(false,.95),percentile(true,.95),SAMPLES.stream().mapToInt(AiTelemetry.Sample::paths).average().orElse(0),ACTIVITY.stream().mapToInt(Activity::alive).min().orElse(0),ACTIVITY.stream().mapToInt(Activity::ticking).min().orElse(0),ACTIVITY.stream().mapToInt(Activity::drivers).average().orElse(0),ACTIVITY.stream().mapToInt(Activity::combat).average().orElse(0));
        Files.writeString(Path.of("actions.csv"),row,StandardOpenOption.APPEND);
        Files.writeString(Path.of("actions-phases.csv"),"route"+COUNTS[phase]+","+begin+","+java.time.Instant.now()+"\n",StandardOpenOption.APPEND);
        var raw=new StringBuilder();for(int sample=0;sample<SAMPLES.size();sample++){var s=SAMPLES.get(sample);var a=ACTIVITY.get(sample);raw.append("route").append(COUNTS[phase]).append(',').append(s.tick()).append(',').append(s.serverNanos()).append(',').append(s.aiNanos()).append(',').append(s.paths()).append(',').append(s.active()).append(',').append(a.alive()).append(',').append(a.ticking()).append(',').append(a.drivers()).append(',').append(a.combat()).append('\n');}Files.writeString(Path.of("actions-samples.csv"),raw,StandardOpenOption.APPEND);
        for(int i=0;i<NPCS.size();i++){
            var n=NPCS.get(i);var m=MOVES.get(i);String d="native recovery="+m.recoveryStatus();
            try{d=(String)m.getClass().getMethod("actionDiagnostics").invoke(m);}catch(NoSuchMethodException expected){}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
            Files.writeString(Path.of("actions-actors.csv"),COUNTS[phase]+","+i+","+ARRIVED.getOrDefault(n.getUUID(),-1)+","+n.getX()+","+n.getY()+","+n.getZ()+",\""+d.replace("\"","\"\"")+"\"\n",StandardOpenOption.APPEND);
        }
        TacticalNpcAiMod.LOGGER.info("ACTIONS_RESULT {}",row.strip());next();
    }
    private static boolean complete(MovementExecutor m){try{return !(Boolean)m.getClass().getMethod("actionActive").invoke(m);}catch(NoSuchMethodException expected){return true;}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}}
    private static double percentile(boolean ai,double q){var a=SAMPLES.stream().mapToLong(s->ai?s.aiNanos():s.serverNanos()).sorted().toArray();return a.length==0?0:a[(int)((a.length-1)*q)]/1e6;}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void bullet(net.minecraftforge.event.entity.EntityJoinLevelEvent e){
        if(server==null || e.isCanceled() || !(e.getEntity() instanceof com.tacz.guns.entity.EntityKineticBullet b) || b.getOwner()==null || NPCS.stream().noneMatch(n->n.getUUID().equals(b.getOwner().getUUID())))return;
        rounds++;SHOOTERS.add(b.getOwner().getUUID());if(firstShot<0)firstShot=age-40;
    }
}
