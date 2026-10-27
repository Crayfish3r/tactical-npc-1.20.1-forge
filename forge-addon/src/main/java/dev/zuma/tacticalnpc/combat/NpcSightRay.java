package dev.zuma.tacticalnpc.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/** One bounded block DDA, one existing ray token, no collision-result cache or chunk loads. */
public final class NpcSightRay {
    public enum Reason { CLEAR, BLOCK, LEAF_LIMIT, UNLOADED, RANGE, VISIT_LIMIT, READ_LIMIT, BUDGET }
    // A <=512-block segment crosses at most ceil(512*sqrt(3))+3 cells. Extra room for native endpoint epsilon.
    private static final int MAX_VISITS=1024,MAX_READS=4096;
    public record Result(VisibilitySensor.Los los,Reason reason,BlockPos block,int visited,int leaves) {}

    public static Result trace(PathfinderMob npc,Vec3 start,Vec3 end,boolean active) {
        if(!(npc.level() instanceof ServerLevel level))return deferred(Reason.UNLOADED);
        if(!level.getServer().isSameThread())throw new IllegalStateException("NPC sight ray requires server thread");
        double distance=start.distanceTo(end);
        if(!Double.isFinite(distance) || distance>512)return deferred(Reason.RANGE);
        // Preserve the existing chunk-grid corner preflight; it performs no block raycasts.
        if(!VisibilitySensor.loadedSegment(level,start,end))return deferred(Reason.UNLOADED);
        var budget=AiWorkBudget.of(level);
        if(!(active?budget.activeRay(npc.getUUID()):budget.ray(npc.getUUID())))return deferred(Reason.BUDGET);
        var pass=new Pass(level,new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,npc));
        return BlockGetter.traverseBlocks(start,end,pass,(p,pos)->p.visit(pos),p->p.result(VisibilitySensor.Los.CLEAR,Reason.CLEAR,null));
    }
    private static Result deferred(Reason reason){return new Result(VisibilitySensor.Los.DEFERRED,reason,null,0,0);}
    private static final class Pass implements BlockGetter {
        private final ServerLevel level;
        private final ClipContext ray;
        private int visits,leaves,reads;
        private Reason unavailable;
        private BlockPos unavailableAt;
        Pass(ServerLevel level,ClipContext ray){this.level=level;this.ray=ray;}
        Result result(VisibilitySensor.Los los,Reason reason,BlockPos pos){return new Result(los,reason,pos==null?null:pos.immutable(),visits,leaves);}
        Result visit(BlockPos pos) {
            if(visits>=MAX_VISITS)return result(VisibilitySensor.Los.DEFERRED,Reason.VISIT_LIMIT,pos);
            visits++;
            var state=getBlockState(pos);
            if(unavailable!=null)return result(VisibilitySensor.Los.DEFERRED,unavailable,unavailableAt);
            switch(NpcSightBlocks.classify(state)) {
                case TRANSPARENT: return null; // Continue this DDA, rather than starting another ray.
                case LEAVES:
                    if(++leaves>1)return result(VisibilitySensor.Los.BLOCKED,Reason.LEAF_LIMIT,pos);
                    return null;
                default:
                    // Preserve the original COLLIDER shape and interaction override for other blocks.
                    // Neighbour reads by modded shapes also go through the non-loading bounded view.
                    var shape=ray.getBlockShape(state,this,pos);
                    var hit=clipWithInteractionOverride(ray.getFrom(),ray.getTo(),pos,shape,state);
                    if(unavailable!=null)return result(VisibilitySensor.Los.DEFERRED,unavailable,unavailableAt);
                    return hit==null?null:result(VisibilitySensor.Los.BLOCKED,Reason.BLOCK,pos);
            }
        }
        private LevelChunk chunk(BlockPos pos) {
            if(unavailable!=null)return null;
            if(++reads>MAX_READS){unavailable=Reason.READ_LIMIT;unavailableAt=pos.immutable();return null;}
            var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            if(chunk==null){unavailable=Reason.UNLOADED;unavailableAt=pos.immutable();}
            return chunk;
        }
        @Override public BlockState getBlockState(BlockPos pos){var c=chunk(pos);return c==null?Blocks.AIR.defaultBlockState():c.getBlockState(pos);}
        @Override public FluidState getFluidState(BlockPos pos){var c=chunk(pos);return c==null?Fluids.EMPTY.defaultFluidState():c.getFluidState(pos);}
        @Override public BlockEntity getBlockEntity(BlockPos pos){var c=chunk(pos);return c==null?null:c.getBlockEntity(pos,LevelChunk.EntityCreationType.CHECK);}
        @Override public int getHeight(){return level.getHeight();}
        @Override public int getMinBuildHeight(){return level.getMinBuildHeight();}
    }
    private NpcSightRay(){}
}
