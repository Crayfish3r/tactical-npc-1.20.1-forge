package dev.zuma.tacticalnpc.combat;

import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Loaded blocks only. Ladder admissibility deliberately does not require intermediate support. */
final class ActionGeometry {
    final NpcRuntimeAdapter runtime;final ServerLevel level;final CoverSensor terrain;
    ActionGeometry(NpcRuntimeAdapter runtime){this.runtime=runtime;level=(ServerLevel)runtime.entity().level();terrain=new CoverSensor(runtime);}
    static Vec3 center(BlockPos p){return Vec3.atBottomCenterOf(p);}
    static Vector3 vector(Vec3 p){return new Vector3(p.x,p.y,p.z);}
    static Vec3 vec(Vector3 p){return new Vec3(p.x(),p.y(),p.z());}
    boolean loaded(BlockPos p){return p.getY()>level.getMinBuildHeight() && p.getY()+3<level.getMaxBuildHeight() && level.hasChunkAt(p) && level.hasChunkAt(p.offset(1,0,1)) && level.hasChunkAt(p.offset(-1,0,-1));}
    boolean clear(Vec3 p){
        if(!loaded(BlockPos.containing(p)))return false;
        var n=runtime.entity();double w=n.getBbWidth()/2+.005;
        var box=new AABB(p.x-w,p.y+.01,p.z-w,p.x+w,p.y+n.getBbHeight(),p.z+w);
        var feet=BlockPos.containing(p);
        return !level.getBlockCollisions(n,box).iterator().hasNext() && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty() &&
            !level.getBlockState(feet).is(Blocks.FIRE) && !level.getBlockState(feet.above()).is(Blocks.FIRE) && !level.getBlockState(feet).is(Blocks.POWDER_SNOW);
    }
    Vec3 standing(BlockPos p){if(!loaded(p))return null;var at=terrain.safeStanding(center(p));return at!=null && Math.abs(at.y-p.getY())<.05?at:null;}
    boolean permits(Vec3 from,Vec3 to){var boundary=terrain.territory();return boundary==null?terrain.withinOrder(to):boundary.permitsStep(vector(from),vector(to));}
    record Ladder(BlockPos bottom,BlockPos top,Direction facing,Vec3 bottomPoint){}
    Ladder ladder(BlockPos at){
        if(!loaded(at) || !level.getBlockState(at).is(Blocks.LADDER))return null;
        var facing=level.getBlockState(at).getValue(LadderBlock.FACING);var bottom=at;var top=at;
        for(int i=0;i<24 && loaded(bottom.below()) && level.getBlockState(bottom.below()).is(Blocks.LADDER);i++)bottom=bottom.below();
        for(int i=0;i<24 && loaded(top.above()) && level.getBlockState(top.above()).is(Blocks.LADDER);i++)top=top.above();
        if(top.getY()-bottom.getY()>24 || !loaded(bottom.below()) || !loaded(top.above()) ||
            level.getBlockState(bottom.below()).is(Blocks.LADDER) || level.getBlockState(top.above()).is(Blocks.LADDER))return null;
        for(int y=bottom.getY();y<=top.getY();y++){
            var p=new BlockPos(at.getX(),y,at.getZ());if(!loaded(p))return null;
            var s=level.getBlockState(p);
            if(!s.is(Blocks.LADDER) || s.getValue(LadderBlock.FACING)!=facing || !clear(aligned(p,facing)))return null;
        }
        return new Ladder(bottom,top,facing,aligned(bottom,facing));
    }
    static Vec3 aligned(BlockPos p,Direction facing){return center(p).add(facing.getStepX()*.12,0,facing.getStepZ()*.12);}
}
