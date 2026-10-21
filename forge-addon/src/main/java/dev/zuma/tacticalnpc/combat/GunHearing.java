package dev.zuma.tacticalnpc.combat;
import dev.zuma.tacticalnpc.config.ServerConfig;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.entity.EntityTypeTest;

/** Actual native-round delivery only. Local bounded query, no global active-NPC iteration. */
public final class GunHearing {
    private record Pair(UUID source,UUID listener){}
    private static final class State {
        final Map<UUID,Long> last=new LinkedHashMap<>();
        final Map<Pair,Long> recent=new LinkedHashMap<>();
        long events,coalesced,queries,denials,considered,delivered,nanos;
    }
    private static final Map<ServerLevel,State> LEVELS=new WeakHashMap<>();
    public static long[] measurements(ServerLevel level){var s=LEVELS.computeIfAbsent(level,k->new State());return new long[]{s.events,s.coalesced,s.queries,s.denials,s.considered,s.delivered,s.nanos};}
    public static void remove(ServerLevel level){LEVELS.remove(level);}
    public static void forget(ServerLevel level,UUID id){var s=LEVELS.get(level);if(s!=null){s.last.remove(id);s.recent.keySet().removeIf(p->p.source.equals(id)||p.listener.equals(id));}}
    public static int deliver(LivingEntity source) {
        if(!(source.level() instanceof ServerLevel level) || !source.isAlive() || !CombatTargets.supported(source))return 0;
        var s=LEVELS.computeIfAbsent(level,k->new State());long started=System.nanoTime();
        try {
            long tick=level.getGameTime();s.events++;
            if(s.last.getOrDefault(source.getUUID(),Long.MIN_VALUE)==tick){s.coalesced++;return 0;}
            s.last.put(source.getUUID(),tick);trim(s.last,1024);
            if(!AiWorkBudget.of(level).query(source.getUUID(),false)){s.denials++;return 0;}
            s.queries++;double range=ServerConfig.HEARING_RANGE.get();var listeners=new ArrayList<PathfinderMob>(16);int[] checked={0};boolean[] saturated={false};
            AiWorkBudget.of(level).worldQuery();
            level.getEntities(EntityTypeTest.forClass(PathfinderMob.class),source.getBoundingBox().inflate(range),n->{
                s.considered++;
                if(++checked[0]>96){saturated[0]=true;return true;}
                if(n==source || !n.isAlive() || n.distanceToSqr(source)>range*range || RuntimeRegistry.active(n)==null ||
                        n instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity own && own.neutral() || !CombatTargets.eligible(n,source))return false;
                return s.recent.getOrDefault(new Pair(source.getUUID(),n.getUUID()),Long.MIN_VALUE)<=tick;
            },listeners,16);
            if(saturated[0]){s.denials++;return 0;}
            int delivered=0;
            for(var n:listeners){var goal=RuntimeRegistry.active(n);if(goal!=null && goal.hear(source)){
                s.recent.put(new Pair(source.getUUID(),n.getUUID()),tick+10);delivered++;
            }}
            trim(s.recent,4096);s.delivered+=delivered;return delivered;
        } finally{long elapsed=System.nanoTime()-started;s.nanos+=elapsed;AiTelemetry.component(level,AiTelemetry.Component.HEARING,elapsed);}
    }
    private static <K,V> void trim(Map<K,V> map,int cap){while(map.size()>cap){var it=map.keySet().iterator();it.next();it.remove();}}
    private GunHearing(){}
}
