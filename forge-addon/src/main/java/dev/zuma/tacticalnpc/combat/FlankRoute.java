package dev.zuma.tacticalnpc.combat;

import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Committed lateral -> advance -> inward legs. Executor validates bounded native paths and loaded terrain. */
public final class FlankRoute {
    private final CoverSensor terrain;private List<Vector3> route=List.of();private int leg,side=1;
    private long expires,retry;private UUID subject;private Vector3 threat;
    public FlankRoute(NpcRuntimeAdapter runtime){terrain=new CoverSensor(runtime);side=(runtime.entity().getUUID().hashCode()&1)==0?1:-1;}
    public Vector3 sample(NpcRuntimeAdapter runtime,CombatSnapshot s,boolean flanker,boolean failed){
        long tick=s.self().frame().tick();
        if(!flanker || !s.targetKnown()){reset();return null;}
        if(failed){route=List.of();retry=tick+60;side=-side;return null;}
        if(!route.isEmpty() && (tick>expires || !s.observation().subject().equals(subject) || threat.minus(s.observation().position()).lengthSquared()>144)){route=List.of();retry=tick+20;}
        while(leg<route.size() && route.get(leg).minus(s.self().position()).lengthSquared()<4)leg++;
        if(!route.isEmpty() && leg>=route.size()){route=List.of();retry=tick+80;return null;}
        if(route.isEmpty()){
            if(tick<retry || s.targetDistanceBlocks()<10)return null;
            var n=runtime.entity();var budget=AiWorkBudget.of((ServerLevel)n.level());
            if(!budget.candidate(n.getUUID()))return null;
            Vector3 from=s.self().position(),delta=s.observation().position().minus(from);delta=new Vector3(delta.x(),0,delta.z());
            double distance=delta.length();if(distance<1)return null;Vector3 forward=delta.times(1/distance),lateral=new Vector3(-forward.z(),0,forward.x()).times(side);
            double advance=Math.min(24,distance*.7),width=Math.min(14,Math.max(8,distance*.35));
            List<Vector3> points=new ArrayList<>();
            for(Vector3 guess:List.of(from.plus(lateral.times(width)),from.plus(lateral.times(width)).plus(forward.times(advance)),from.plus(lateral.times(width*.5)).plus(forward.times(advance)))){
                var point=terrain.safeStanding(new Vec3(guess.x(),guess.y(),guess.z()));
                if(point==null || !terrain.withinOrder(point)){retry=tick+40;side=-side;return null;}
                points.add(new Vector3(point.x,point.y,point.z));
            }
            route=List.copyOf(points);leg=0;expires=tick+240;subject=s.observation().subject();threat=s.observation().position();
        }
        var point=route.get(leg);
        if(runtime.squadId()!=null && !SquadRuntime.of((ServerLevel)runtime.entity().level()).claim(runtime.squadId(),runtime.entity().getUUID(),point,tick)){
            route=List.of();retry=tick+40;side=-side;return null;
        }
        return point;
    }
    public void reset(){route=List.of();leg=0;subject=null;threat=null;retry=0;}
}
