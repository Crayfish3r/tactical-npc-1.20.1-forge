package dev.zuma.tacticalnpc.combat;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
/** Bounded in-memory samples. Timing includes goal and native fire safety without nested double counting. */
public final class AiTelemetry {
    public enum Component { GOAL, NATIVE_MAINTENANCE, HEARING, NATIVE_SAFETY }
    public enum Gate { NO_CONTACT, LOS_BLOCKED, LOS_DEFERRED, REACTION, TURN, WEAPON_BUSY, COOLDOWN, ALLY, POLICY, NATIVE_DECLINED }
    public enum Route { FAILED, ALTERNATIVE, ARRIVED, EXHAUSTED }
    private static final class Counters {
        long unfairHints;
        final long[] gates=new long[Gate.values().length],routes=new long[Route.values().length];
        final long[][] latency=new long[2][129];
        final long[] sectors=new long[VisibilitySensor.Sector.values().length];
    }
    public record Sample(long tick,long serverNanos,long aiNanos,int rays,int candidates,int paths,int active,long heapBytes,long goalNanos,long maintenanceNanos,long hearingNanos,long nativeSafetyNanos){}
    private static final class LevelData {long ai;final long[] components=new long[Component.values().length];Sample latest;final long[] history=new long[256];int cursor,count;final Counters counters=new Counters();}
    private static final Map<ServerLevel,LevelData> LEVELS=new WeakHashMap<>();
    private static int depth;
    private long serverStart;
    public static long begin(ServerLevel level){return ++depth==1?System.nanoTime():0;}
    public static void end(ServerLevel level,long start){if(start!=0)LEVELS.computeIfAbsent(level,k->new LevelData()).ai+=System.nanoTime()-start;depth--;}
    public static Sample latest(ServerLevel level){var d=LEVELS.get(level);return d==null?null:d.latest;}
    public static void component(ServerLevel level,Component component,long nanos){LEVELS.computeIfAbsent(level,k->new LevelData()).components[component.ordinal()]+=nanos;}
    public static void gate(ServerLevel level,Gate gate) {LEVELS.computeIfAbsent(level,k->new LevelData()).counters.gates[gate.ordinal()]++;}
    public static void sector(ServerLevel level,VisibilitySensor.Sector result){LEVELS.computeIfAbsent(level,k->new LevelData()).counters.sectors[result.ordinal()]++;}
    public static void route(ServerLevel level,Route route) {LEVELS.computeIfAbsent(level,k->new LevelData()).counters.routes[route.ordinal()]++;}
    public static void contactShot(ServerLevel level,boolean reacquire,long ticks) {LEVELS.computeIfAbsent(level,k->new LevelData()).counters.latency[reacquire?1:0][(int)Math.min(128,Math.max(0,ticks))]++;}
    public static void unfairHint(ServerLevel level){LEVELS.computeIfAbsent(level,k->new LevelData()).counters.unfairHints++;}
    private static String latency(long[] histogram) {
        long n=0,sum=0;for(int i=0;i<histogram.length;i++){n+=histogram[i];sum+=i*histogram[i];}
        return n==0?"n=0":"n="+n+",meanTicks="+(double)sum/n+",overflow="+histogram[128];
    }
    public static String diagnostics(ServerLevel level) {
        var c=LEVELS.computeIfAbsent(level,k->new LevelData()).counters;
        return "firstProjectile{"+latency(c.latency[0])+"} reacquireProjectile{"+latency(c.latency[1])+"} unfairHints="+c.unfairHints+" gates="+java.util.stream.IntStream.range(0,c.gates.length).filter(i->c.gates[i]>0).mapToObj(i->Gate.values()[i]+":"+c.gates[i]).toList()+" routes[failed,alternative,arrived,exhausted]="+java.util.Arrays.toString(c.routes)+" "+AiWorkBudget.of(level).queryDiagnostics()+" sectors[CLEAR,OCCUPIED,SATURATED,DEFERRED,UNSUPPORTED]="+java.util.Arrays.toString(c.sectors);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase==TickEvent.Phase.START){serverStart=System.nanoTime();return;}
        long elapsed=System.nanoTime()-serverStart;
        for(var level:event.getServer().getAllLevels()) {
            var d=LEVELS.computeIfAbsent(level,k->new LevelData());var b=AiWorkBudget.of(level);
            int active=dev.zuma.tacticalnpc.runtime.RuntimeRegistry.activeIn(level).size();
            d.latest=new Sample(level.getGameTime(),elapsed,d.ai,b.rays(),b.candidates(),b.paths(),active,Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory(),d.components[0],d.components[1],d.components[2],d.components[3]);
            Arrays.fill(d.components,0);
            d.history[d.cursor++%256]=d.ai;d.count=Math.min(256,d.count+1);d.ai=0;
            if(active>0 && level.getGameTime()%200==0 && dev.zuma.tacticalnpc.config.ServerConfig.TELEMETRY.get()) {
                long[] values=Arrays.copyOf(d.history,d.count);Arrays.sort(values);
                dev.zuma.tacticalnpc.TacticalNpcAiMod.LOGGER.info("AI sample level={} active={} ai_p95_ms={} rays={} candidates={} paths={}",level.dimension().location(),active,values[(int)((values.length-1)*.95)]/1e6,b.rays(),b.candidates(),b.paths());
                dev.zuma.tacticalnpc.TacticalNpcAiMod.LOGGER.info("AI alpha {}",diagnostics(level));
            }
        }
    }
    @SubscribeEvent public void unload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level){LEVELS.remove(level);SquadRuntime.remove(level);AiWorkBudget.remove(level);GunHearing.remove(level);}
    }
}
