package dev.zuma.tacticalnpc.combat;

import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import static dev.zuma.tacticalnpc.combat.ActionGeometry.*;
import static dev.zuma.tacticalnpc.core.ActionRoute.*;

/** Incremental local A*: at most 768 expansions, 64/owner/tick and 512/level/tick. */
final class ActionRoutePlanner {
    private record Node(BlockPos p,double cost,double score,Node parent,Transition edge){}
    private final ActionGeometry g;private final BlockPos start,goal;
    private final PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::score));
    private final Map<BlockPos,Double> best=new HashMap<>();
    private int expanded;private boolean done;private ActionRoute result;
    ActionRoutePlanner(NpcRuntimeAdapter runtime,Vec3 destination){
        g=new ActionGeometry(runtime);start=runtime.entity().blockPosition();goal=BlockPos.containing(destination);
        open.add(new Node(start,0,distance(start,goal),null,null));best.put(start,0.0);
    }
    boolean done(){return done;}ActionRoute result(){return result;}
    void advance(){
        for(int i=0;i<64 && !done;i++){
            if(!AiWorkBudget.of(g.level).routeNode(g.runtime.entity().getUUID()))return;
            if(open.isEmpty() || expanded++>=768){done=true;return;}
            var n=open.remove();if(n.cost>best.getOrDefault(n.p,Double.MAX_VALUE))continue;
            if(n.p.equals(goal)){
                var reversed=new ArrayList<Transition>();for(var step=n;step.parent!=null;step=step.parent)reversed.add(step.edge);
                Collections.reverse(reversed);
                if(!reversed.isEmpty() && reversed.size()<=128)result=new ActionRoute(reversed,vector(center(goal)));
                done=true;return;
            }
            for(var d:Direction.Plane.HORIZONTAL){
                var next=n.p.relative(d);
                if(g.loaded(next) && g.level.getBlockState(next).getBlock() instanceof DoorBlock){
                    var lower=g.level.getBlockState(next).getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER?next.below():next;
                    var exit=lower.relative(d);var at=g.standing(exit);
                    if(next.equals(lower) && RouteInteractions.VANILLA.supports(g.level,lower) &&
                        g.level.getBlockState(lower).getValue(DoorBlock.FACING).getAxis()==d.getAxis() && at!=null)
                        add(n,exit,new Transition(Kind.OPEN_DOOR,vector(center(n.p)),vector(center(lower)),vector(at),vector(center(lower)),Condition.OPENABLE_DOOR,Completion.OPEN_PASSAGE_AND_GROUNDED_AT_EXIT,120),3);
                    continue;
                }
                for(int dy:new int[]{0,1,-1}){
                    var p=next.offset(0,dy,0);var at=g.standing(p);
                    if(at!=null){
                        // No diagonal tunnelling into a step or head-high overhang.
                        if(g.clear(center(n.p).add(0,Math.max(0,dy),0)) && g.clear(at))
                            add(n,p,new Transition(Kind.WALK,vector(center(n.p)),vector(center(n.p)),vector(at),null,Condition.SUPPORTED_BODY_CLEAR,Completion.GROUNDED_AT_EXIT,120),1+Math.abs(dy)*.4);
                        break;
                    }
                }
            }
            // Only adjacent columns are inspected; never scan a terrain volume or unloaded chunks.
            for(var d:Direction.Plane.HORIZONTAL)for(int dy:new int[]{0,-1})ladder(n,n.p.relative(d).offset(0,dy,0));
            ladder(n,n.p);
        }
    }
    private void ladder(Node n,BlockPos q){
        var l=g.ladder(q);if(l==null)return;
        for(var d:Direction.Plane.HORIZONTAL)for(int y:new int[]{l.top().getY()+1,l.top().getY()}){
            var upper=new BlockPos(q.getX()+d.getStepX(),y,q.getZ()+d.getStepZ());var landing=g.standing(upper);
            if(landing==null)continue;
            var lower=l.bottom().relative(l.facing());var floor=g.standing(lower);
            if(floor==null || !g.clear(l.bottomPoint()))continue;
            if(n.p.equals(lower))add(n,upper,new Transition(Kind.CLIMB_UP,vector(floor),vector(l.bottomPoint()),vector(landing),vector(center(l.bottom())),Condition.CONTINUOUS_LADDER,Completion.GROUNDED_AT_EXIT,80+(y-l.bottom().getY())*20),4+(y-l.bottom().getY())*1.5);
            if(n.p.equals(upper))add(n,lower,new Transition(Kind.CLIMB_DOWN,vector(landing),vector(aligned(l.top().above(),l.facing())),vector(floor),vector(center(l.bottom())),Condition.CONTINUOUS_LADDER,Completion.GROUNDED_AT_EXIT,80+(y-l.bottom().getY())*20),4+(y-l.bottom().getY())*1.5);
        }
    }
    private void add(Node n,BlockPos p,Transition edge,double cost){
        if(Math.abs(p.getX()-start.getX())>16 || Math.abs(p.getZ()-start.getZ())>16 || Math.abs(p.getY()-start.getY())>24 || !g.permits(center(n.p),center(p)))return;
        double value=n.cost+cost;if(value>=best.getOrDefault(p,Double.MAX_VALUE) || best.size()>=2048)return;
        best.put(p,value);open.add(new Node(p,value,value+distance(p,goal),n,edge));
    }
    private static double distance(BlockPos a,BlockPos b){return Math.abs(a.getX()-b.getX())+Math.abs(a.getZ()-b.getZ())+Math.abs(a.getY()-b.getY());}
}
