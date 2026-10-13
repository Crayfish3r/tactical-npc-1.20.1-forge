package dev.zuma.tacticalnpc.core;

import java.util.UUID;

/** Server-owned bounded identity/readiness state; carries no hidden coordinates. */
public final class ContactReadiness {
    private UUID subject,candidate;
    private long candidateSince,lastVisual=Long.MIN_VALUE,readyAt=Long.MAX_VALUE,episodeStart;
    private boolean visible,occluded,reacquiring;
    public boolean visual(UUID id,long tick,int perception,int reaction,int reacquire,int recentTicks) {
        if(subject!=null && subject.equals(id) && tick-lastVisual<=recentTicks) {
            if(occluded) {readyAt=tick+reacquire;episodeStart=tick;reacquiring=true;}
            visible=true;occluded=false;lastVisual=tick;return true;
        }
        if(subject!=null) clear();
        if(!id.equals(candidate)) {candidate=id;candidateSince=tick;}
        if(tick-candidateSince<perception) {visible=false;return false;}
        subject=id;lastVisual=tick;readyAt=tick+reaction;episodeStart=candidateSince;
        visible=true;occluded=false;reacquiring=false;return true;
    }
    public void blocked() {visible=false;occluded=true;candidate=null;}
    public void deferred() {visible=false;}
    public boolean ready(long tick) {return visible && tick>=readyAt;}
    public UUID subject() {return subject;}
    public boolean visible() {return visible;}
    public boolean reacquiring() {return reacquiring;}
    public long episodeStart() {return episodeStart;}
    public void clear() {subject=null;candidate=null;visible=false;occluded=false;readyAt=Long.MAX_VALUE;lastVisual=Long.MIN_VALUE;}
}
