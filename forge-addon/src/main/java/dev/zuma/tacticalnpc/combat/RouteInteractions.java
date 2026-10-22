package dev.zuma.tacticalnpc.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/** Extension point for interaction adapters; no fake player or client-side use packet. */
public final class RouteInteractions {
    public interface DoorAdapter {
        boolean supports(ServerLevel level,BlockPos lower);
        boolean open(PathfinderMob npc,BlockPos lower);
    }
    @Cancelable public static final class OpenDoorEvent extends Event {
        private final PathfinderMob npc;private final BlockPos position;
        public OpenDoorEvent(PathfinderMob npc,BlockPos position){this.npc=npc;this.position=position.immutable();}
        public PathfinderMob npc(){return npc;}public BlockPos position(){return position;}
    }
    // Deliberately only vanilla wooden doors. A modded subclass needs an explicit future adapter.
    public static final DoorAdapter VANILLA=new DoorAdapter(){
        public boolean supports(ServerLevel level,BlockPos p){
            if(!level.hasChunkAt(p) || !level.hasChunkAt(p.above()))return false;
            var s=level.getBlockState(p);var upper=level.getBlockState(p.above());
            return s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER &&
                DoorBlock.isWoodenDoor(s) && BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals("minecraft") &&
                upper.is(s.getBlock()) && upper.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER &&
                s.getValue(DoorBlock.FACING)==upper.getValue(DoorBlock.FACING) && s.getValue(DoorBlock.HINGE)==upper.getValue(DoorBlock.HINGE);
        }
        public boolean open(PathfinderMob npc,BlockPos p){
            var level=(ServerLevel)npc.level();
            if(!level.getServer().isSameThread())throw new IllegalStateException("Door interaction requires server thread");
            if(!supports(level,p))return false;
            var s=level.getBlockState(p);if(s.getValue(DoorBlock.OPEN))return true;
            if(npc.distanceToSqr(p.getX()+.5,p.getY(),p.getZ()+.5)>6.25 ||
                !ForgeEventFactory.getMobGriefingEvent(level,npc) || MinecraftForge.EVENT_BUS.post(new OpenDoorEvent(npc,p)))return false;
            ((DoorBlock)s.getBlock()).setOpen(npc,level,s,p,true);
            return level.getBlockState(p).getValue(DoorBlock.OPEN) && level.getBlockState(p.above()).getValue(DoorBlock.OPEN);
        }
    };
    private RouteInteractions(){}
}
