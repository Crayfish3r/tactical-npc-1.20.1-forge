package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.combat.MovementExecutor;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@PrefixGameTestTemplate(false)
public final class AlphaLocomotionTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="wide_arena",batch="alpha-speed",timeoutTicks=570)
    public static void straightLaneNpcGaits(GameTestHelper h) {
        ScenarioIsolation.begin(h);Vec3 origin=h.absoluteVec(new Vec3(3.5,2,20.5));
        // Seal a dry lane before clearing it: old test-world fluids must not flow into a speed sample.
        for(int x=0;x<=60;x++)for(int z=17;z<=23;z++)for(int y=1;y<=6;y++)
            if(x==0 || x==60 || z==17 || z==23 || y==1 || y==6) h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        for(int x=1;x<=59;x++)for(int z=18;z<=22;z++)for(int y=2;y<=5;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        var goal=new Vector3(origin.x+50,origin.y,origin.z);long[] ticks={0},startNanos=new long[4];double[] start=new double[4],speed=new double[4],wallSpeed=new double[4];int[] previous={-1};boolean[] ads={false},recovered={false};
        TacticalNpcEntity[] actor={null};MovementExecutor[] movement={null};
        h.onEachTick(()->{
            long tick=++ticks[0];int phase=tick<=120?0:tick<=240?1:tick<=390?2:3;
            long local=tick-(phase==0?0:phase==1?120:phase==2?240:390);
            if(phase!=previous[0]) {
                if(actor[0]!=null) {movement[0].stop(tick);actor[0].discard();}
                // New navigation state for each pass: teleporting an old path would contaminate vanilla stuck timers.
                var n=NpcEntities.NPC.get().create(h.getLevel());n.moveTo(origin.x,origin.y,origin.z,-90,0);n.setOrder(OrderState.Kind.ASSAULT,64);
                if(phase>=2) n.setItemSlot(EquipmentSlot.MAINHAND,GunSupport.create(new ResourceLocation("tacz:ak47")));
                h.getLevel().addFreshEntity(n);actor[0]=n;movement[0]=new MovementExecutor(n.runtime());previous[0]=phase;
            }
            var n=actor[0];if(phase>=2 && local==2)n.runtime().operator().draw(n::getMainHandItem);
            n.runtime().operator().aim(phase==3);
            movement[0].apply(MovementCommand.navigate(goal,phase==1 || phase==2?MovementCommand.Locomotion.RUN:MovementCommand.Locomotion.WALK),tick);
            recovered[0]|=movement[0].recoveryStatus()==RouteRecovery.Status.RECOVERING || movement[0].recoveryStatus()==RouteRecovery.Status.EXHAUSTED;
            int begin=phase<2?40:70,end=begin+60;
            if(local>=begin && local<=end) h.assertTrue(!n.isInWater() && Math.abs(n.getY()-origin.y)<.01 && Math.abs(n.getZ()-origin.z)<.05,"speed sample stays dry, flat and straight: "+n.position());
            if(local==begin) {start[phase]=n.getX();startNanos[phase]=System.nanoTime();}
            if(local==end) {double distance=n.getX()-start[phase];speed[phase]=distance*20/60;wallSpeed[phase]=distance*1e9/(System.nanoTime()-startNanos[phase]);
                TacticalNpcAiMod.LOGGER.info("ALPHA gait sample phase={} testTick={} entityTicks={} alive={} ground={} pos={} navDone={} recovery={} speed={}",phase,tick,n.tickCount,n.isAlive(),n.onGround(),n.position(),n.getNavigation().isDone(),movement[0].recoveryStatus(),speed[phase]);}
            if(phase==3 && local>70) {ads[0]|=n.runtime().operator().getSynAimingProgress()>.9;h.assertFalse(n.isSprinting(),"ADS walk never forces sprint");}
        });
        h.runAtTickTime(540,()->{
            h.assertTrue(!recovered[0] && speed[0]>0 && speed[1]>speed[0]*1.15 && speed[2]>0 && ads[0],"straight primary paths, run faster than walk; armed native movement and ADS exercised: "+java.util.Arrays.toString(speed)+" recovered="+recovered[0]+" ticks="+ticks[0]);
            TacticalNpcAiMod.LOGGER.info("ALPHA NPC same-lane steady speed blocks/s at 20 TPS: walk={} unarmedRun={} AK47Run={} AK47ADS={} (60-tick samples after warmup; fresh actor per pass, no real-player calibration)",speed[0],speed[1],speed[2],speed[3]);
            TacticalNpcAiMod.LOGGER.info("ALPHA NPC same-lane actual wall-clock blocks/s [walk,unarmedRun,AK47Run,AK47ADS]={}",java.util.Arrays.toString(wallSpeed));
            movement[0].stop(ticks[0]);actor[0].discard();h.succeed();
        });
    }
}
