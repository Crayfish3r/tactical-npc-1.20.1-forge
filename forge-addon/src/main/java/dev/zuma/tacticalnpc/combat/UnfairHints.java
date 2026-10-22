package dev.zuma.tacticalnpc.combat;

import dev.zuma.tacticalnpc.config.ServerConfig;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter;
import net.minecraft.server.level.ServerLevel;

/** Explicit cheat channel. Never visual, never target velocity, never copied to the squad's visual report. */
public final class UnfairHints {
    private final ObservationMemory memory=new ObservationMemory();
    private long next;private int cursor;
    public Observation sample(NpcRuntimeAdapter runtime,long tick,boolean visible){
        if(!ServerConfig.unfair()){memory.clear();return null;}
        var n=runtime.entity();var level=(ServerLevel)n.level();
        if(!visible && tick>=next && !(n instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity own && own.neutral())){
            var budget=AiWorkBudget.of(level);
            if(budget.query(n.getUUID(),false)){
                next=tick+ServerConfig.HINT_INTERVAL.get();var players=level.players();int count=players.size();
                for(int i=0;i<Math.min(16,count);i++){
                    var p=players.get(Math.floorMod(cursor++,count));
                    if(!CombatTargets.eligible(n,p) || n.distanceToSqr(p)>192*192)continue;
                    Vector3 point=new Vector3(Math.floor(p.getX()/4)*4+2,Math.floor(p.getY()/4)*4+2,Math.floor(p.getZ()/4)*4+2);
                    memory.record(new Observation(p.getUUID(),point,new Vector3(0,0,0),tick,tick+80,Observation.Source.UNFAIR_HINT,.7,5,false));AiTelemetry.unfairHint(level);break;
                }
            }
        }
        return memory.sample(tick,false,new Vector3(n.getX(),n.getY(),n.getZ()),192);
    }
    public void reset(){memory.clear();next=0;cursor=0;}
}
