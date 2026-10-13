package dev.zuma.tacticalnpc.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Destination-specific backoff and a finite local recovery episode, independent of Minecraft. */
public final class RouteRecovery {
    public enum Status { IDLE, RECOVERING, ACCEPTED, EXHAUSTED }
    private record Failure(long until,int count) {}
    private final Map<Vector3,Failure> failures=new LinkedHashMap<>();
    private Vector3 origin,goal;
    private int attempt;
    private long started,finished;
    private Status status=Status.IDLE;
    public boolean rejected(Vector3 point,long tick) {
        return failures.entrySet().stream().anyMatch(e->e.getKey().minus(point).lengthSquared()<1 && tick<e.getValue().until());
    }
    public void fail(Vector3 point,long tick) {
        var old=failures.entrySet().stream().filter(e->e.getKey().minus(point).lengthSquared()<1).findFirst().orElse(null);
        int count=old==null?1:Math.min(3,old.getValue().count()+1);
        if(old!=null) failures.remove(old.getKey());
        if(failures.size()>=16) failures.remove(failures.keySet().iterator().next());
        failures.put(point,new Failure(tick+(20L<<Math.min(2,count-1)),count));
        if(status!=Status.RECOVERING) {started=tick;attempt=0;status=Status.RECOVERING;}
    }
    public Vector3 alternative(Vector3 self,Vector3 requested,long tick) {
        if(status==Status.EXHAUSTED && tick-finished<20) return null;
        if(status==Status.EXHAUSTED || status==Status.IDLE || status==Status.ACCEPTED) {
            origin=self;goal=requested;attempt=0;started=tick;status=Status.RECOVERING;
        }
        if(origin==null) {origin=self;goal=requested;}
        if(tick-started>=60 || attempt>=3) {status=Status.EXHAUSTED;finished=tick;return null;}
        Vector3 toward=goal.minus(origin);toward=new Vector3(toward.x(),0,toward.z());
        if(toward.lengthSquared()<.01) toward=new Vector3(1,0,0);
        toward=toward.times(1/toward.length());Vector3 side=new Vector3(-toward.z(),0,toward.x());
        while(attempt<3) {
            int index=attempt++;
            Vector3 point=index==2?origin.plus(toward.times(2)):origin.plus(toward.times(1.5)).plus(side.times(index==0?3:-3));
            if(!rejected(point,tick)) return point;
        }
        status=Status.EXHAUSTED;finished=tick;return null;
    }
    public void accepted() {status=Status.ACCEPTED;origin=null;goal=null;}
    public boolean expire(long tick) {
        if(status==Status.RECOVERING && tick-started>=60) {status=Status.EXHAUSTED;finished=tick;return true;}
        return false;
    }
    public Status status() {return status;}
    public void cancelEpisode() {status=Status.IDLE;origin=null;goal=null;attempt=0;}
    public void clear() {failures.clear();status=Status.IDLE;origin=null;goal=null;}
}
