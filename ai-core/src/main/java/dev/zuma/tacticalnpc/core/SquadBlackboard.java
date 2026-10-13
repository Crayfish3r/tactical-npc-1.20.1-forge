package dev.zuma.tacticalnpc.core;

import java.util.*;

/** One level owns one board. No entities, world references or live target lookups. */
public final class SquadBlackboard {
    public enum Role { ANCHOR, FLANKER, SUPPRESSOR }
    private static final class Member {
        Vector3 position;long heartbeat,lastReport=Long.MIN_VALUE/2;
        Report pending,delivered;Role role=Role.SUPPRESSOR;
    }
    private record Report(Observation observation,Vector3 sender,long delivery) {}
    private record Claim(UUID owner,Vector3 point,long expires) {}
    private static final class Group {
        final Map<UUID,Member> members=new HashMap<>();
        final List<Claim> claims=new ArrayList<>();long rolesUntil;
    }
    private final Map<UUID,Group> groups=new HashMap<>();
    private long nextCleanup;
    public void heartbeat(UUID squad,UUID owner,Vector3 position,long tick) {
        cleanup(tick);
        if(squad==null || !groups.containsKey(squad) && groups.size()>=128) return;
        var g=groups.computeIfAbsent(squad,k->new Group());
        if(!g.members.containsKey(owner) && g.members.size()>=64) return;
        var m=g.members.computeIfAbsent(owner,k->new Member());m.position=position;m.heartbeat=tick;
        if(tick>=g.rolesUntil) {
            var ids=g.members.keySet().stream().sorted().toList();
            for(int i=0;i<ids.size();i++)g.members.get(ids.get(i)).role=i==0?Role.ANCHOR:i==1?Role.FLANKER:Role.SUPPRESSOR;
            g.rolesUntil=tick+80;
        }
    }
    public void report(UUID squad,UUID owner,Observation observed,long tick) {
        var g=groups.get(squad);var m=g==null?null:g.members.get(owner);
        if(m==null || observed==null || !observed.visible() || observed.source()!=Observation.Source.VISION || tick-m.lastReport<10) return;
        deliver(m,tick);m.pending=new Report(observed,m.position,tick+10);m.lastReport=tick;
    }
    public Observation receive(UUID squad,UUID owner,Vector3 position,long tick) {
        cleanup(tick);var g=groups.get(squad);if(g==null) return null;
        Report best=null;
        for(var e:g.members.entrySet()) {
            if(e.getKey().equals(owner)) continue;
            deliver(e.getValue(),tick);var r=e.getValue().delivered;
            if(r==null || tick-r.observation.tick()>80 || tick>r.observation.expires() || position.minus(r.sender).lengthSquared()>48*48)continue;
            if(best==null || best.observation.tick()<r.observation.tick()) best=r;
        }
        if(best==null)return null;var o=best.observation;
        return new Observation(o.subject(),o.position(),new Vector3(0,0,0),o.tick(),Math.min(o.expires(),o.tick()+80),Observation.Source.SQUAD,o.confidence()*.75,o.uncertainty()+2,false);
    }
    private static void deliver(Member m,long tick){if(m.pending!=null && tick>=m.pending.delivery){m.delivered=m.pending;m.pending=null;}}
    public Role role(UUID squad,UUID owner){var g=groups.get(squad);var m=g==null?null:g.members.get(owner);return m==null?Role.SUPPRESSOR:m.role;}
    public boolean available(UUID squad,UUID owner,Vector3 point,long tick) {
        var g=groups.get(squad);if(g==null)return true;
        return g.claims.stream().noneMatch(c->c.expires>=tick && !c.owner.equals(owner) && c.point.minus(point).lengthSquared()<4);
    }
    public boolean claim(UUID squad,UUID owner,Vector3 point,long tick) {
        var g=groups.get(squad);if(g==null || !g.members.containsKey(owner) || !available(squad,owner,point,tick))return false;
        g.claims.removeIf(c->c.owner.equals(owner) || c.expires<tick);
        if(g.claims.size()>=64)return false;g.claims.add(new Claim(owner,point,tick+80));return true;
    }
    public void releaseClaims(UUID owner){for(var g:groups.values())g.claims.removeIf(c->c.owner.equals(owner));}
    public void release(UUID owner) {
        for(var g:groups.values())if(g.members.remove(owner)!=null)g.rolesUntil=0;
        releaseClaims(owner);groups.values().removeIf(g->g.members.isEmpty());
    }
    public int members(UUID squad){var g=groups.get(squad);return g==null?0:g.members.size();}
    private void cleanup(long tick) {
        if(tick<nextCleanup)return;nextCleanup=tick+20;
        for(var g:groups.values()) {
            var stale=g.members.entrySet().stream().filter(e->tick-e.getValue().heartbeat>80).map(Map.Entry::getKey).toList();
            for(var id:stale) {g.members.remove(id);g.claims.removeIf(c->c.owner.equals(id));g.rolesUntil=0;}
            g.claims.removeIf(c->c.expires<tick);
        }
        groups.values().removeIf(g->g.members.isEmpty());
    }
}
