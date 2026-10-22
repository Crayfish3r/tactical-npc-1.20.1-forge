package dev.zuma.tacticalnpc.core;

import java.util.UUID;

/** Counts actual native rounds, never requested trigger pulls. Refreshing hidden reports cannot refill it. */
public final class SuppressionCycle {
    private UUID subject;private long start=-1,resume,recordedToken=-1;private int rounds,burst;
    public void observe(Observation known,long tick){
        if(known==null){reset();return;}
        if(known.visible() || !known.subject().equals(subject)) {subject=known.subject();start=tick;rounds=burst=0;resume=0;}
    }
    public boolean active(long tick,int duration,int budget){return start>=0 && tick-start<=duration && rounds<budget;}
    public boolean ready(long tick,int duration,int budget){return active(tick,duration,budget) && tick>=resume;}
    public void round(long tick,int burstLimit,int pause){rounds++;if(++burst>=burstLimit){burst=0;resume=tick+pause;}}
    public void nativeProjectile(long token,long tick,int burstLimit,int pause){
        if(token==recordedToken)return;recordedToken=token;round(tick,burstLimit,pause);
    }
    public int rounds(){return rounds;}
    public void reset(){subject=null;start=-1;rounds=burst=0;resume=0;recordedToken=-1;}
}
