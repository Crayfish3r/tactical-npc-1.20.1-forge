package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import dev.zuma.tacticalnpc.runtime.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Focused ordinary dedicated release check for the new commands, never production. */
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class TeamCommandProfile {
    private static final int COUNT=Integer.getInteger("tactical.profile.team.count",15);
    private static final boolean LANES=COUNT==15 && !Boolean.getBoolean("tactical.profile.team.crowded");
    private static MinecraftServer server;private static ServerLevel level;private static int age,rounds;private static boolean spawning;
    private static final List<TacticalNpcEntity> ACTORS=new ArrayList<>();
    private static final Set<UUID> OWNERS=new HashSet<>();
    private static final List<long[]> ROWS=new ArrayList<>();
    private static long first,last;private static String started;
    @SubscribeEvent public static void start(net.minecraftforge.event.server.ServerStartedEvent event) throws Exception{
        if(!Boolean.getBoolean("tactical.profile.team"))return;
        server=event.getServer();level=server.overworld();
        dev.zuma.tacticalnpc.config.ServerConfig.DEBUG.set(false);dev.zuma.tacticalnpc.config.ServerConfig.TELEMETRY.set(false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(6000);
        for(int x=62;x<=67;x++)for(int z=62;z<=(LANES?70:67);z++)level.setChunkForced(x,z,true);
        for(int x=1000;x<=1070;x++)for(int z=1000;z<=(LANES?1110:1070);z++)level.setBlock(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState(),2);
        var source=server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
        spawning=true;
        int red=server.getCommands().getDispatcher().execute("spawn npc team profile-red count "+COUNT,source.withPosition(new Vec3(1012.5,101,1032.5)).withRotation(new Vec2(0,-90)));
        int blue=server.getCommands().getDispatcher().execute("spawn npc team profile-blue count "+COUNT,source.withPosition(new Vec3(1044.5,101,1032.5)).withRotation(new Vec2(0,90)));
        spawning=false;
        if(red!=COUNT || blue!=COUNT)throw new IllegalStateException("NPC spawn commands did not create exact requested cohorts");
        if(ACTORS.size()!=COUNT*2)throw new IllegalStateException("Unexpected native joined profile population: "+ACTORS.size());
        ACTORS.forEach(n->{n.setInvulnerable(true);n.addTag("team-profile");});
        // Separate the ordinary 30-NPC measurement from the crowded command formation.
        // Native weapon, ammo, turn, LOS and occupied-sector rules remain fully enabled.
        if(LANES)for(String team:List.of("profile-red","profile-blue")){
            var cohort=ACTORS.stream().filter(n->team.equals(n.faction())).toList();
            for(int i=0;i<cohort.size();i++){var n=cohort.get(i);float yaw=team.equals("profile-red")?-90:90;n.moveTo(team.equals("profile-red")?1012.5:1044.5,101,1008.5+i*6,yaw,0);n.setYHeadRot(yaw);n.setYBodyRot(yaw);}
        }
        var env=new LinkedHashMap<String,Object>();env.put("java",System.getProperty("java.runtime.version"));env.put("cpu",System.getenv("PROCESSOR_IDENTIFIER"));env.put("processors",Runtime.getRuntime().availableProcessors());env.put("seed",server.getWorldData().worldGenOptions().seed());env.put("jvm",java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());env.put("mods",net.minecraftforge.fml.ModList.get().getMods().stream().map(m->m.getModId()+":"+m.getVersion()).toList());env.put("weapon","native default AK47/INFINITE reserve, empty initial magazine, natural reload; cohort invulnerable, not human PvP");env.put("warmup",200);
        env.put("aiConfig",Map.of("perception",dev.zuma.tacticalnpc.config.ServerConfig.PERCEPTION.get(),"reaction",dev.zuma.tacticalnpc.config.ServerConfig.REACTION.get(),"turn",dev.zuma.tacticalnpc.config.ServerConfig.TURN.get(),"memory",dev.zuma.tacticalnpc.config.ServerConfig.MEMORY.get(),"suppression",dev.zuma.tacticalnpc.config.ServerConfig.SUPPRESSION.get()));
        env.put("geometry",LANES?"30 NPCs in parallel lanes 6 blocks apart; fixture-only relocation after real spawn commands":"unaltered positions from short spawn commands, "+COUNT+" per team");
        Files.writeString(Path.of("team-environment.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(env));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void bullet(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(server!=null && spawning && event.getLevel()==level && !event.isCanceled() && event.getEntity() instanceof TacticalNpcEntity n){ACTORS.add(n);return;}
        if(server==null || event.isCanceled() || event.getLevel().isClientSide || age<200 || !(event.getEntity() instanceof com.tacz.guns.entity.EntityKineticBullet b) || !(b.getOwner() instanceof TacticalNpcEntity n) || !n.getTags().contains("team-profile"))return;
        rounds++;OWNERS.add(n.getUUID());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ServerTickEvent event) throws Exception{
        if(server==null || event.phase!=TickEvent.Phase.END)return;
        if(age++<200)return;
        long now=System.nanoTime();if(ROWS.isEmpty()){first=now;started=java.time.Instant.now().toString();}last=now;
        var sample=AiTelemetry.latest(level);if(sample==null)throw new IllegalStateException("Missing actual AI sample");var budget=AiWorkBudget.of(level);
        ROWS.add(new long[]{sample.serverNanos(),sample.aiNanos(),sample.rays(),sample.paths(),budget.worldQueries(),budget.queryDenials(),budget.maximumDeferredAge(),sample.heapBytes(),sample.goalNanos(),sample.maintenanceNanos(),sample.hearingNanos(),sample.nativeSafetyNanos()});
        if(ROWS.size()<600)return;
        boolean active=ACTORS.stream().allMatch(n->n.isAlive() && !n.isRemoved() && !n.neutral() && RuntimeRegistry.active(n)!=null && !n.controller().failed());
        long red=ACTORS.stream().filter(n->n.faction().equals("profile-red") && OWNERS.contains(n.getUUID())).count(),blue=ACTORS.stream().filter(n->n.faction().equals("profile-blue") && OWNERS.contains(n.getUUID())).count();
        var result=new LinkedHashMap<String,Object>();result.put("status",active && rounds>0 && red>0 && blue>0?"PASS":"FAIL");result.put("actors",ACTORS.size());result.put("samples",ROWS.size());result.put("projectiles",rounds);result.put("redOwnersWithProjectiles",red);result.put("blueOwnersWithProjectiles",blue);result.put("tps",(ROWS.size()-1)*1e9/(last-first));
        for(int i=0;i<2;i++){String label=i==0?"tick":"ai";result.put(label+"P50Ms",quantile(i,.5)/1e6);result.put(label+"P95Ms",quantile(i,.95)/1e6);result.put(label+"P99Ms",quantile(i,.99)/1e6);}
        for(int i=2;i<12;i++){final int column=i;result.put(new String[]{"raysMean","pathsMean","worldQueriesMean","queryDenialsMean","deferredAgeMean","heapMeanBytes","goalMeanNanos","adapterMaintenanceMeanNanos","hearingMeanNanos","nativeSafetyMeanNanos"}[i-2],ROWS.stream().mapToLong(r->r[column]).average().orElse(-1));}
        result.put("deferredAgeMax",quantile(6,1));result.put("diagnostics",AiTelemetry.diagnostics(level));result.put("scope","one focused new-team scene, not a before/after speed comparison; component scopes overlap");
        Files.writeString(Path.of("team-profile.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
        Files.writeString(Path.of("batch-summary.csv"),"phase,npc_actors,repeat,start_utc,end_utc\nteam-commands,30,1,"+started+","+java.time.Instant.now()+"\n");
        var raw=new StringBuilder("tick_ns,ai_ns,rays,paths,world_queries,query_denials,deferred_age,heap_bytes,goal_ns,maintenance_ns,hearing_ns,native_safety_ns\n");for(var row:ROWS)raw.append(Arrays.stream(row).mapToObj(Long::toString).collect(java.util.stream.Collectors.joining(","))).append('\n');Files.writeString(Path.of("team-samples.csv"),raw);
        TacticalNpcAiMod.LOGGER.info("TEAM_PROFILE_COMPLETE status={} actors={} projectiles={}",result.get("status"),ACTORS.size(),rounds);
        ACTORS.forEach(TacticalNpcEntity::discard);for(int x=62;x<=67;x++)for(int z=62;z<=(LANES?70:67);z++)level.setChunkForced(x,z,false);server.halt(false);server=null;
    }
    private static long quantile(int column,double fraction){var values=ROWS.stream().mapToLong(r->r[column]).sorted().toArray();return values[(int)((values.length-1)*fraction)];}
}
