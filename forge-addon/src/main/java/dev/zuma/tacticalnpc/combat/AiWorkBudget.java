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
    // Grant small query quanta. Previous denials reserve next-tick capacity regardless of tick order.
    // Pending identities expire and are capped; no entities/world references are retained here.
    private static final class QueryLane {
        final int limit,quantum,ownerLimit;
        int reserved;
        final Map<UUID,Integer> left=new HashMap<>(),spent=new HashMap<>();
        final Map<UUID,Long> pending=new LinkedHashMap<>();
        final Set<UUID> priority=new HashSet<>();
        QueryLane(int limit,int quantum,int ownerLimit){this.limit=limit;this.quantum=quantum;this.ownerLimit=ownerLimit;}
        void reset(long tick){
            reserved=0;left.clear();spent.clear();priority.clear();pending.entrySet().removeIf(e->tick-e.getValue()>200);
            for(var id:pending.keySet()){if(priority.size()>=limit/quantum)break;priority.add(id);}
        }
        boolean take(UUID id,long tick) {
            int used=spent.getOrDefault(id,0);if(used>=ownerLimit)return false;
            int available=left.getOrDefault(id,0);
            if(available==0) {
                int protectedSlots=quantum*(priority.size()-(priority.contains(id)?1:0));
                if(reserved+quantum+protectedSlots>limit){if(pending.size()<1024)pending.putIfAbsent(id,tick);return false;}
                reserved+=quantum;available=quantum;pending.remove(id);priority.remove(id);
            }
            left.put(id,available-1);spent.put(id,used+1);return true;
        }
        void forget(UUID id){pending.remove(id);priority.remove(id);left.remove(id);spent.remove(id);}
    }
    // Four 32-block safety sections replace one former full corridor query (stage 11).
    private final QueryLane contactQueries=new QueryLane(1024,16,32),optionalQueries=new QueryLane(32,1,2);
    private final QueryLane routeNodes=new QueryLane(512,64,64);
    private int queries,queryDenials,worldQueries;
    private long maximumDeferredAge;
    private static final class RayWait {
        final long since;long last;
        RayWait(long tick){since=last=tick;}
    }
    private final Map<UUID,RayWait> activeWait=new LinkedHashMap<>(),optionalWait=new LinkedHashMap<>();
    private final Map<UUID,Integer> activeCredit=new HashMap<>(),optionalCredit=new HashMap<>();
    private int reservedActive,reservedOptional;
    public static AiWorkBudget of(ServerLevel level) {
        var b=LEVELS.computeIfAbsent(level,k->new AiWorkBudget());
        if(b.tick!=level.getGameTime()) {
            b.tick=level.getGameTime();Arrays.fill(b.used,0);b.owners.clear();b.queries=0;b.queryDenials=0;b.worldQueries=0;
            b.contactQueries.reset(b.tick);b.optionalQueries.reset(b.tick);
            b.routeNodes.reset(b.tick);
            b.reservedActive=b.prepareRays(b.activeWait,b.activeCredit,4,64);
            b.reservedOptional=b.prepareRays(b.optionalWait,b.optionalCredit,6,21);
            b.nextPaths.entrySet().removeIf(e->e.getValue()+200<b.tick);
        }return b;
    }
    private int prepareRays(Map<UUID,RayWait> pending,Map<UUID,Integer> credit,int quantum,int count){
        credit.clear();pending.entrySet().removeIf(e->e.getValue().last<tick-8);
        for(var id:pending.keySet()){if(credit.size()>=count)break;credit.put(id,quantum);}
        return credit.size()*quantum;
    }
    private void waitRay(UUID owner,boolean active){
        var pending=active?activeWait:optionalWait;var waiting=pending.get(owner);
        if(waiting!=null){waiting.last=tick;maximumDeferredAge=Math.max(maximumDeferredAge,Math.min(32767,tick-waiting.since));}
        else if(pending.size()<1024)pending.put(owner,new RayWait(tick));
    }
    private boolean takeRay(UUID owner,boolean active){
        var local=owners.get(owner);int total=local==null?0:local[0],optional=local==null?0:local[0]-local[3];
        if(total>=16 || !active && optional>=12)return false;
        var credit=active?activeCredit:optionalCredit;int left=credit.getOrDefault(owner,0);
        int protectedAll=reservedActive+reservedOptional-(left>0?1:0);
        int protectedOptional=reservedOptional-(!active && left>0?1:0);
        if(used[0]+1+protectedAll>384 || !active && used[0]-used[3]+1+protectedOptional>128){waitRay(owner,active);return false;}
        if(left>0){credit.put(owner,left-1);if(active)reservedActive--;else reservedOptional--;}
        var pending=active?activeWait:optionalWait;var waiting=pending.remove(owner);
        if(waiting!=null)maximumDeferredAge=Math.max(maximumDeferredAge,Math.min(32767,tick-waiting.since));
        int[] counts=owners.computeIfAbsent(owner,k->new int[4]);used[0]++;counts[0]++;
        if(active){used[3]++;counts[3]++;}return true;
    }
    /** Must precede the query/list page. A denial never proves absence, path failure or CLEAR. */
    public boolean query(UUID owner,boolean active) {
        var lane=active?contactQueries:optionalQueries;Long since=lane.pending.get(owner);
        if(since!=null)maximumDeferredAge=Math.max(maximumDeferredAge,Math.min(32767,tick-since));
        if(!lane.take(owner,tick)){queryDenials++;return false;}queries++;return true;
    }
    public int queries(){return queries;}public int queryDenials(){return queryDenials;}
    public void worldQuery(){worldQueries++;}
    public int worldQueries(){return worldQueries;}
    public long maximumDeferredAge(){return maximumDeferredAge;}
    public void forget(UUID owner){
        contactQueries.forget(owner);optionalQueries.forget(owner);nextPaths.remove(owner);owners.remove(owner);
        routeNodes.forget(owner);
        activeWait.remove(owner);optionalWait.remove(owner);
        reservedActive-=activeCredit.getOrDefault(owner,0);reservedOptional-=optionalCredit.getOrDefault(owner,0);activeCredit.remove(owner);optionalCredit.remove(owner);
    }
    public String queryDiagnostics(){return "queries="+queries+",denials="+queryDenials+",deferredAgeMax="+maximumDeferredAge+",pending="+(contactQueries.pending.size()+optionalQueries.pending.size());}
    private boolean take(UUID owner,int type,int levelLimit,int ownerLimit) {
        int[] local=owners.computeIfAbsent(owner,k->new int[4]);
        if(used[type]>=levelLimit || local[type]>=ownerLimit) return false;
        used[type]++;local[type]++;return true;
    }
    // Cover/acquisition cannot consume the active-contact/native-round reserve.
    public boolean ray(UUID owner) { return takeRay(owner,false); }
    public boolean activeRay(UUID owner) {
        return takeRay(owner,true);
    }
    public boolean canRays(UUID owner,int count) {
        var local=owners.get(owner);int localOptional=local==null?0:local[0]-local[3],localTotal=local==null?0:local[0];
        if(localOptional+count>12 || localTotal+count>16)return false;
        int own=Math.min(count,optionalCredit.getOrDefault(owner,0));
        boolean available=used[0]-used[3]+count+reservedOptional-own<=128 && used[0]+count+reservedActive+reservedOptional-own<=384;
        if(!available)waitRay(owner,false);return available;
    }
    public boolean candidate(UUID owner) { return take(owner,1,128,6); }
    public boolean routeNode(UUID owner){return routeNodes.take(owner,tick);}
    public int routeNodes(){return routeNodes.spent.values().stream().mapToInt(Integer::intValue).sum();}
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
