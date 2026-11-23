package dev.zuma.tacticalnpc.core;

import java.util.UUID;

/** An occlusion episode is anchored to a personal visual sample. */
public final class GrenadeContact {
    public record Permit(UUID subject, Vector3 feet, Frame issued, long expires) {
        public boolean valid(Frame now) {
            return issued.owner().equals(now.owner()) && issued.dimension().equals(now.dimension()) &&
                    issued.generation()==now.generation() && now.tick()>=issued.tick() && now.tick()<=expires;
        }
    }

    private UUID subject;
    private Vector3 feet;
    private long visualTick, visualExpires;
    private Permit pending;
    private boolean spent;

    public UUID subject(){return subject;}
    public Vector3 lastFeet(){return feet;}
    public boolean recent(long tick,int window){
        long expires=pending==null?Math.min(visualExpires,visualTick+window):pending.expires();
        return subject!=null && !spent && tick<=expires;
    }

    public void seen(UUID subject, Vector3 feet, long tick, long expires) {
        this.subject=subject;
        this.feet=feet;
        visualTick=tick;
        visualExpires=expires;
        pending=null;
        spent=false;
    }

    public Permit permit(UUID checked, boolean occluded, Frame now, int window, int repositionTicks) {
        if(!recent(now.tick(),window) || !subject.equals(checked) || !occluded)return null;
        if(pending==null){
            long expires=Math.min(visualExpires,Math.max(visualTick+window,now.tick()+repositionTicks));
            pending=new Permit(subject,feet,now,expires);
        }
        return pending.valid(now)?pending:null;
    }

    public void spend(){spent=true;}
    public void clear(){subject=null;feet=null;pending=null;spent=false;}
}
