package dev.zuma.tacticalnpc.core;

import java.util.*;

/** Bounded FIFO leases, identities only. A level owns one table; no world references. */
public final class TransitionReservations {
    private record Lease(UUID owner,long until) {}
    private final Map<Long,Lease> leases=new HashMap<>();
    private final Map<Long,LinkedHashMap<UUID,Long>> queues=new HashMap<>();
    public boolean acquire(long key,UUID owner,long tick) {
        prune(tick);
        var lease=leases.get(key);
        if(lease!=null && lease.owner.equals(owner)){leases.put(key,new Lease(owner,tick+40));return true;}
        var queue=queues.get(key);
        if(queue==null){if(queues.size()>=256)return false;queue=new LinkedHashMap<>();queues.put(key,queue);}
        if(queue.containsKey(owner) || queue.size()<64)queue.put(owner,tick+60);
        if(lease!=null || queue.isEmpty() || !queue.keySet().iterator().next().equals(owner))return false;
        queue.remove(owner);leases.put(key,new Lease(owner,tick+40));return true;
    }
    public void release(long key,UUID owner){var lease=leases.get(key);if(lease!=null && lease.owner.equals(owner))leases.remove(key);var q=queues.get(key);if(q!=null)q.remove(owner);}
    public void forget(UUID owner){leases.entrySet().removeIf(e->e.getValue().owner.equals(owner));queues.values().forEach(q->q.remove(owner));}
    public void prune(long tick){leases.entrySet().removeIf(e->e.getValue().until<tick);queues.values().forEach(q->q.entrySet().removeIf(e->e.getValue()<tick));queues.entrySet().removeIf(e->e.getValue().isEmpty() && !leases.containsKey(e.getKey()));}
    public int size(){return leases.size();}
}
