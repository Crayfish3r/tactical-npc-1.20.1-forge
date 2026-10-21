package dev.zuma.tacticalnpc;

import com.tacz.guns.api.item.IGun;
import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.config.ServerConfig;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Ordinary dedicated profiling fixture, portable to the frozen pre-09 source. Never production. */
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class BatchProfile {
    private record Phase(String name,int count,int repeat,int ticks){}
    private record Row(long tick,long server,long ai,long rays,long paths,long heap,long queries,long denials,long deferred,long hearing,long maintenance,long goal,long safety,long worldQueries){}
    private static final List<Phase> PHASES=new ArrayList<>();
    private static final List<TacticalNpcEntity> ACTORS=new ArrayList<>();
    private static final List<Entity> TARGETS=new ArrayList<>(),CROWD=new ArrayList<>();
    private static final List<Row> ROWS=new ArrayList<>();
    private static final Set<UUID> OWNERS=new HashSet<>(),SHOT_OWNERS=new HashSet<>(),FIRED=new HashSet<>(),VISIBLE=new HashSet<>(),EPISODE_SHOT=new HashSet<>();
    private static final Map<UUID,Long> VISUAL_AT=new HashMap<>();
    private static final Map<UUID,UUID> SERVICES=new HashMap<>();
    private static final Map<UUID,net.minecraft.world.item.ItemStack> SAVED_STACKS=new HashMap<>();
    private static final Map<Class<?>,Map<String,java.lang.reflect.Method>> METRICS=new HashMap<>();
    private static final List<Long> WALL_LATENCY=new ArrayList<>(),VISUAL_LATENCY=new ArrayList<>(),REACQUIRE_LATENCY=new ArrayList<>();
    private static MinecraftServer server;private static ServerLevel level;
    private static int phase=-1,age,bullets;private static long wallTick,wallStart,cpuStart,gcStart,gcTimeStart,sampleFirst,sampleLast;private static String started;
    private static boolean wall;private static long overloadTick=Long.MIN_VALUE;
    private static final class Target extends net.minecraftforge.common.util.FakePlayer {
        Target(ServerLevel l,UUID id,String name){super(l,new com.mojang.authlib.GameProfile(id,name));setGameMode(GameType.SURVIVAL);}
        @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource s,float amount){return false;}
    }
    @SubscribeEvent public static void started(net.minecraftforge.event.server.ServerStartedEvent e) throws Exception {
        if(!Boolean.getBoolean("tactical.profile.batch"))return;
        server=e.getServer();level=server.overworld();
        if(net.minecraftforge.fml.ModList.get().isLoaded("tacznpcs"))throw new IllegalStateException("Batch before/after must use identical standalone modlist");
        ServerConfig.DEBUG.set(false);ServerConfig.TELEMETRY.set(false);
        ServerConfig.PERCEPTION.set(1);ServerConfig.REACTION.set(3);ServerConfig.REACQUIRE.set(1);ServerConfig.ACQUISITION.set(3);ServerConfig.TURN.set(17.0);ServerConfig.SUPPRESSION.set(true);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(6000);
        for(int x=62;x<=65;x++)for(int z=62;z<=102;z++)level.setChunkForced(x,z,true);
        for(int x=1000;x<1044;x++)for(int z=1000;z<1620;z++)level.setBlock(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState(),2);
        for(int repeat=1;repeat<=2;repeat++)for(int count:new int[]{1,4,16,30,50})for(String name:List.of("neutral","idle","patrol","combat","crowded","npc_only","dynamic","overload"))
            PHASES.add(new Phase(name,count,repeat,name.equals("dynamic")?200:120));
        for(int repeat=1;repeat<=2;repeat++)PHASES.add(new Phase("soak",50,repeat,2400));
        PHASES.addAll(0,List.of(new Phase("unload",30,1,500),new Phase("unload",30,2,500)));
        PHASES.add(0,new Phase("warmup",4,0,200));
        Files.writeString(Path.of("batch-summary.csv"),"phase,npc_actors,total_npcs,repeat,samples,start_utc,end_utc,wall_s,tps,tick_p50_ms,tick_p95_ms,tick_p99_ms,ai_p50_ms,ai_p95_ms,ai_p99_ms,rays_mean,paths_mean,queries_mean,denials_mean,deferred_age_max,hearing_ms_mean,adapter_maintenance_ms_mean,heap_p95_mb,cpu_core_percent,gc_count,gc_ms,projectiles,wall_first_p50_ticks,wall_first_p95_ticks,visual_first_p50_ticks,visual_first_p95_ticks,reacquire_p95_ticks,goal_scope_ms_mean,native_safety_ms_mean,owners_with_projectiles,world_queries_mean\n");
        Files.writeString(Path.of("batch-samples.csv"),"phase,count,repeat,tick,server_ns,ai_ns,rays,paths,heap,queries,denials,deferred_age,hearing_ns,adapter_maintenance_ns,goal_ns,native_safety_ns,world_queries\n");
        var metadata=new LinkedHashMap<String,Object>();metadata.put("seed",server.getWorldData().worldGenOptions().seed());metadata.put("java",System.getProperty("java.runtime.version"));metadata.put("jvm",java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());metadata.put("cpu",System.getenv("PROCESSOR_IDENTIFIER"));metadata.put("processors",Runtime.getRuntime().availableProcessors());metadata.put("mods",net.minecraftforge.fml.ModList.get().getMods().stream().map(m->m.getModId()+":"+m.getVersion()).toList());metadata.put("warmupTicks",80);metadata.put("weapon","tacz:ak47; native finite magazine30+chamber1 reserve120 (soak100000); 280 tick native warmup; deterministic actor UUID/RNG; DEFEND6");
        Files.writeString(Path.of("batch-environment.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(metadata));next();
    }
    private static void next() throws Exception {
        // A phase boundary owns its remaining projectiles too; they cannot attack the next scene.
        var rounds=new ArrayList<Entity>();level.getAllEntities().forEach(e->{if(e instanceof com.tacz.guns.entity.EntityKineticBullet)rounds.add(e);});rounds.forEach(Entity::discard);
        for(var list:List.of(ACTORS,TARGETS,CROWD))list.forEach(Entity::discard);
        ACTORS.clear();TARGETS.clear();CROWD.clear();OWNERS.clear();SHOT_OWNERS.clear();ROWS.clear();VISIBLE.clear();VISUAL_AT.clear();FIRED.clear();EPISODE_SHOT.clear();WALL_LATENCY.clear();VISUAL_LATENCY.clear();REACQUIRE_LATENCY.clear();
        AiWorkBudget.remove(level);
        SERVICES.clear();SAVED_STACKS.clear();
        if(++phase>=PHASES.size()){
            TacticalNpcAiMod.LOGGER.info("BATCH_PROFILE_COMPLETE phases={}",PHASES.size());
            for(int x=62;x<=65;x++)for(int z=62;z<=102;z++)level.setChunkForced(x,z,false);server.halt(false);return;
        }
        var p=PHASES.get(phase);age=0;bullets=0;wall=false;sampleFirst=sampleLast=0;
        for(int i=0;i<p.count;i++){
            var n=NpcEntities.NPC.get().create(level);n.setUUID(new UUID(14,p.repeat*10000L+i));n.getRandom().setSeed(140000+i);
            n.moveTo(1006.5,101,1008.5+i*12,-90,0);n.setYHeadRot(-90);n.setOrder(p.name.equals("patrol")?OrderState.Kind.PATROL:p.name.equals("idle")?OrderState.Kind.HOLD_POSITION:OrderState.Kind.DEFEND_AREA,p.name.equals("unload")?128:p.name.equals("patrol")?8:6);
            if(p.name.equals("npc_only")){
                boolean blue=i%2==1;n.moveTo(blue?1028.5:1006.5,101,1008.5+(i/2)*12,blue?90:-90,0);n.setYHeadRot(blue?90:-90);
                n.setFaction(blue?"blue":"red");n.setInvulnerable(true);n.setOrder(OrderState.Kind.DEFEND_AREA,6);
            }
            if(p.name.equals("unload")){n.setFaction("batch14");n.setSquad(new UUID(14,i/4));SERVICES.put(n.getUUID(),n.serviceId());}
            if(p.name.equals("neutral"))n.enterNeutral();
            var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,30);gun.setBulletInBarrel(stack,true);n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.runtime().onEquip(stack);n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,p.name.equals("soak")?100000:120);
            n.addTag(RuntimeRegistry.TAG);level.addFreshEntity(n);ACTORS.add(n);OWNERS.add(n.getUUID());
            if(!List.of("neutral","idle","patrol","unload","npc_only").contains(p.name)){
                Entity target;
                var t=new Target(level,new UUID(141,p.repeat*10000L+i),"batch-"+i);t.moveTo(1028.5,101,1008.5+i*12);level.addNewPlayer(t);target=t;
                TARGETS.add(target);
            }
        }
        if(p.name.equals("crowded"))for(int i=0;i<32;i++){var a=new ArmorStand(level,1009+(i%4)*.2,101,1028+(i/4)*.2);a.setNoGravity(true);level.addFreshEntity(a);CROWD.add(a);}
        if(p.name.equals("combat")||p.name.equals("dynamic")||p.name.equals("soak"))setWall(true);
        TacticalNpcAiMod.LOGGER.info("BATCH_PHASE phase={} count={} repeat={} samples={}",p.name,p.count,p.repeat,p.ticks);
    }
    private static void setWall(boolean solid){
        for(int y=101;y<=104;y++)for(int z=1000;z<=Math.min(1619,1020+ACTORS.size()*12);z++)level.setBlock(new BlockPos(1016,y,z),(solid?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);wall=solid;
        if(!solid)wallTick=level.getGameTime();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ServerTickEvent e) throws Exception {
        if(server==null || phase>=PHASES.size())return;var p=PHASES.get(phase);
        if(e.phase==TickEvent.Phase.START){
            if(p.name.equals("unload")){
                if(age==100){for(var n:ACTORS)SAVED_STACKS.put(n.getUUID(),n.getMainHandItem().copy());for(int x=62;x<=65;x++)for(int z=62;z<=102;z++)level.setChunkForced(x,z,false);}
                if(age==400){
                    if(OWNERS.stream().anyMatch(id->level.getEntity(id)!=null) || ACTORS.stream().anyMatch(n->n.controller()!=null || RuntimeRegistry.active(n)!=null))throw new IllegalStateException("Real chunk unload retained NPC/controller");
                    TacticalNpcAiMod.LOGGER.info("BATCH_CHUNK_UNLOAD_PASS actors={}",OWNERS.size());
                }
                if(age==420)for(int x=62;x<=65;x++)for(int z=62;z<=102;z++)level.setChunkForced(x,z,true);
                if(age==490){
                    ACTORS.clear();for(var id:OWNERS){var entity=level.getEntity(id);if(!(entity instanceof TacticalNpcEntity n) || !n.serviceId().equals(SERVICES.get(id)) || n.runtime().squadId()==null || !n.faction().equals("batch14") || n.runtime().orderState().radius()!=128 || !net.minecraft.world.item.ItemStack.matches(n.getMainHandItem(),SAVED_STACKS.get(id)))throw new IllegalStateException("Real chunk reload lost identity/native stack/faction/squad/radius128: "+id);ACTORS.add(n);}
                    TacticalNpcAiMod.LOGGER.info("BATCH_CHUNK_RELOAD_PASS actors={}",ACTORS.size());
                }
            }
            if(age==80){if(wall){if(!VISIBLE.isEmpty() || !SHOT_OWNERS.isEmpty())throw new IllegalStateException("Hidden warmup leaked a visual contact/native shot: "+p);setWall(false);VISUAL_AT.clear();VISIBLE.clear();}wallTick=level.getGameTime();wallStart=System.nanoTime();cpuStart=cpu();gcStart=gc(false);gcTimeStart=gc(true);bullets=0;started=java.time.Instant.now().toString();}
            if(p.name.equals("dynamic") && age>80 && age%40==0)setWall(!wall);
            return;
        }
        for(var n:ACTORS){var o=n.controller()==null?null:n.controller().observation();boolean visible=o!=null && o.visible();
            if(visible && !VISIBLE.contains(n.getUUID())){VISUAL_AT.put(n.getUUID(),level.getGameTime());EPISODE_SHOT.remove(n.getUUID());}
            if(visible)VISIBLE.add(n.getUUID());else VISIBLE.remove(n.getUUID());
        }
        if(age>=80){var s=AiTelemetry.latest(level);if(s!=null){long now=System.nanoTime();if(ROWS.isEmpty())sampleFirst=now;sampleLast=now;ROWS.add(new Row(s.tick(),server.tickTimes[Math.floorMod(server.getTickCount(),server.tickTimes.length)],s.aiNanos(),s.rays(),s.paths(),s.heapBytes(),metric(AiWorkBudget.of(level),"queries"),metric(AiWorkBudget.of(level),"queryDenials"),metric(AiWorkBudget.of(level),"maximumDeferredAge"),metric(s,"hearingNanos"),metric(s,"maintenanceNanos"),metric(s,"goalNanos"),metric(s,"nativeSafetyNanos"),metric(AiWorkBudget.of(level),"worldQueries")));}}
        if(++age>=80+p.ticks){finish(p);setWall(false);next();}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void overload(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e){
        if(server==null || phase>=PHASES.size() || !PHASES.get(phase).name.equals("overload") || !OWNERS.contains(e.getEntity().getUUID()) || e.getEntity().level()!=level || overloadTick==level.getGameTime())return;
        overloadTick=level.getGameTime();for(int i=0;i<20;i++){var id=new UUID(1499,i);while(AiWorkBudget.of(level).activeRay(id)){} }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void bullet(net.minecraftforge.event.entity.EntityJoinLevelEvent e){
        if(server==null || e.getLevel().isClientSide || e.isCanceled() || !(e.getEntity() instanceof com.tacz.guns.entity.EntityKineticBullet b) || b.getOwner()==null || !OWNERS.contains(b.getOwner().getUUID()))return;
        if(age<80){SHOT_OWNERS.add(b.getOwner().getUUID());return;}
        bullets++;UUID id=b.getOwner().getUUID();SHOT_OWNERS.add(id);long tick=level.getGameTime();
        if(!List.of("combat","dynamic","soak").contains(PHASES.get(phase).name))return;
        if(FIRED.add(id)){WALL_LATENCY.add(tick-wallTick);Long visual=VISUAL_AT.get(id);if(visual!=null)VISUAL_LATENCY.add(tick-visual);EPISODE_SHOT.add(id);}
        else if(EPISODE_SHOT.add(id)){Long visual=VISUAL_AT.get(id);if(visual!=null)REACQUIRE_LATENCY.add(tick-visual);}
    }
    private static long metric(Object value,String name){
        var methods=METRICS.computeIfAbsent(value.getClass(),k->new HashMap<>());
        if(!methods.containsKey(name)){try{methods.put(name,value.getClass().getMethod(name));}catch(NoSuchMethodException e){methods.put(name,null);}}
        var method=methods.get(name);if(method==null)return -1;
        try{return ((Number)method.invoke(value)).longValue();}catch(ReflectiveOperationException e){throw new IllegalStateException("Measured counter failed: "+name,e);}
    }
    private static long cpu(){return ProcessHandle.current().info().totalCpuDuration().orElse(java.time.Duration.ZERO).toNanos();}
    private static long gc(boolean time){return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(g->Math.max(0,time?g.getCollectionTime():g.getCollectionCount())).sum();}
    private static double q(java.util.function.ToLongFunction<Row> f,double q){var a=ROWS.stream().mapToLong(f).sorted().toArray();return a.length==0?-1:a[(int)((a.length-1)*q)];}
    private static double mean(java.util.function.ToLongFunction<Row> f){return ROWS.stream().mapToLong(f).average().orElse(-1);}
    private static long latency(List<Long> values,double q){var a=values.stream().mapToLong(Long::longValue).sorted().toArray();return a.length==0?-1:a[(int)((a.length-1)*q)];}
    private static void finish(Phase p) throws Exception {
        if(ACTORS.size()!=p.count || ACTORS.stream().anyMatch(n->!n.isAlive() || n.isRemoved() || n.controller()==null || n.controller().failed()))throw new IllegalStateException("Profile cohort not fully alive/active at phase end: "+p);
        if(p.name.equals("warmup")){if(bullets==0)throw new IllegalStateException("Native warmup fired no rounds");TacticalNpcAiMod.LOGGER.info("BATCH_NATIVE_WARMUP_PASS projectiles={}",bullets);return;}
        double seconds=(System.nanoTime()-wallStart)/1e9;
        var values=List.of(p.name,p.count,p.count,p.repeat,ROWS.size(),started,java.time.Instant.now(),seconds,(ROWS.size()-1)*1e9/(sampleLast-sampleFirst),
            q(Row::server,.5)/1e6,q(Row::server,.95)/1e6,q(Row::server,.99)/1e6,q(Row::ai,.5)/1e6,q(Row::ai,.95)/1e6,q(Row::ai,.99)/1e6,
            mean(Row::rays),mean(Row::paths),mean(Row::queries),mean(Row::denials),q(Row::deferred,1),mean(Row::hearing)/1e6,mean(Row::maintenance)/1e6,q(Row::heap,.95)/1048576,(cpu()-cpuStart)/1e9/seconds*100,gc(false)-gcStart,gc(true)-gcTimeStart,bullets,
            latency(WALL_LATENCY,.5),latency(WALL_LATENCY,.95),latency(VISUAL_LATENCY,.5),latency(VISUAL_LATENCY,.95),latency(REACQUIRE_LATENCY,.95),mean(Row::goal)/1e6,mean(Row::safety)/1e6,SHOT_OWNERS.size(),mean(Row::worldQueries));
        Files.writeString(Path.of("batch-summary.csv"),values.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(","))+"\n",StandardOpenOption.APPEND);
        var raw=new StringBuilder();for(var r:ROWS)raw.append(p.name).append(',').append(p.count).append(',').append(p.repeat).append(',').append(r.tick).append(',').append(r.server).append(',').append(r.ai).append(',').append(r.rays).append(',').append(r.paths).append(',').append(r.heap).append(',').append(r.queries).append(',').append(r.denials).append(',').append(r.deferred).append(',').append(r.hearing).append(',').append(r.maintenance).append(',').append(r.goal).append(',').append(r.safety).append(',').append(r.worldQueries).append('\n');
        Files.writeString(Path.of("batch-samples.csv"),raw,StandardOpenOption.APPEND);
        TacticalNpcAiMod.LOGGER.info("BATCH_RESULT phase={} count={} repeat={} samples={} projectiles={} ai_p95_ms={} tick_p95_ms={}",p.name,p.count,p.repeat,ROWS.size(),bullets,q(Row::ai,.95)/1e6,q(Row::server,.95)/1e6);
    }
}
