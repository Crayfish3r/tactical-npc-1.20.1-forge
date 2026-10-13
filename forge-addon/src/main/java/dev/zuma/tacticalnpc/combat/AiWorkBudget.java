package dev.zuma.tacticalnpc.combat;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
/** Level and per-owner limits. No world/entity references survive level unload. */
public final class AiWorkBudget {
    private static final Map<ServerLevel,AiWorkBudget> LEVELS=new WeakHashMap<>();
    private long tick=-1;
    private final int[] used=new int[4];
    private final Map<UUID,int[]> owners=new HashMap<>();
    private final Map<UUID,Long> nextPaths=new HashMap<>();
    public static AiWorkBudget of(ServerLevel level) {
        var b=LEVELS.computeIfAbsent(level,k->new AiWorkBudget());
        if(b.tick!=level.getGameTime()) {b.tick=level.getGameTime();Arrays.fill(b.used,0);b.owners.clear();b.nextPaths.entrySet().removeIf(e->e.getValue()+200<b.tick);}return b;
    }
    private boolean take(UUID owner,int type,int levelLimit,int ownerLimit) {
        int[] local=owners.computeIfAbsent(owner,k->new int[4]);
        if(used[type]>=levelLimit || local[type]>=ownerLimit) return false;
        used[type]++;local[type]++;return true;
    }
    // Cover/acquisition cannot consume the active-contact/native-round reserve.
    public boolean ray(UUID owner) { return canRays(owner,1) && take(owner,0,384,16); }
    public boolean activeRay(UUID owner) {
        if(!take(owner,0,384,16)) return false;
        used[3]++;owners.get(owner)[3]++;return true;
    }
    public boolean canRays(UUID owner,int count) {var local=owners.get(owner);return used[0]-used[3]+count<=128 && (local==null?0:local[0]-local[3])+count<=12 && used[0]+count<=384 && (local==null?0:local[0])+count<=16;}
    public boolean candidate(UUID owner) { return take(owner,1,128,6); }
    public boolean path(UUID owner) {
        return path(owner,20);
    }
    public boolean path(UUID owner,int interval) {
        if(tick<nextPaths.getOrDefault(owner,0L) || !take(owner,2,8,1)) return false;
        nextPaths.put(owner,tick+Math.max(4,interval));return true;
    }
    public void pathFailed(UUID owner) {nextPaths.computeIfPresent(owner,(k,next)->Math.min(next,tick+4));}
    public int rays() {return used[0];}public int candidates(){return used[1];}public int paths(){return used[2];}
    public static void remove(ServerLevel level){LEVELS.remove(level);}
}
