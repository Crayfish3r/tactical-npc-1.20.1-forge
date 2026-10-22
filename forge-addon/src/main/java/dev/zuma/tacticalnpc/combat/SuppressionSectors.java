package dev.zuma.tacticalnpc.combat;

import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter;
import net.minecraft.world.phys.Vec3;

/** Known-contact offsets only. Two rays/update, rotating blocked exits; never follows hidden entities. */
public final class SuppressionSectors {
    private Vector3 selected;private long until,blockedSince=-1,nextSample;private int cursor;
    private java.util.UUID subject;
    public Vector3 sample(NpcRuntimeAdapter runtime,VisibilitySensor sensor,Observation known,long tick){
        if(known==null || known.visible()){reset();return known==null?null:known.position();}
        if(!known.subject().equals(subject)){reset();subject=known.subject();}
        if(selected!=null && tick<until && selected.minus(known.position()).lengthSquared()<=64)return selected;
        if(tick<nextSample)return null;nextSample=tick+4;
        var n=runtime.entity();Vector3 delta=known.position().minus(new Vector3(n.getX(),n.getY(),n.getZ()));
        Vector3 side=new Vector3(-delta.z(),0,delta.x());if(side.lengthSquared()<.01)side=new Vector3(1,0,0);side=side.times(1/side.length());
        selected=null;
        for(int i=0;i<2;i++){
            int index=Math.floorMod(cursor++,7);double offset=new double[]{0,-2,2,-4,4,-6,6}[index];
            Vector3 candidate=known.position().plus(side.times(offset));
            var result=sensor.los(n,new Vec3(candidate.x(),candidate.y(),candidate.z()),true);
            if(result==VisibilitySensor.Los.DEFERRED){cursor--;return null;}
            if(result==VisibilitySensor.Los.CLEAR){selected=candidate;until=tick+12;blockedSince=-1;return selected;}
        }
        if(blockedSince<0)blockedSince=tick;return null;
    }
    public boolean reposition(long tick){return blockedSince>=0 && tick-blockedSince>=16;}
    public void reset(){selected=null;subject=null;until=nextSample=0;blockedSince=-1;cursor=0;}
}
