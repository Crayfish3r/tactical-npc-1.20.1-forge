package dev.zuma.tacticalnpc;
import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
public final class PeekRevalidationTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="peek-01-obstruction",timeoutTicks=80)
    public static void cachedExitObstructedFindsAnotherWithoutRestarting(GameTestHelper h){scenario(h,false);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="peek-02-deferred",timeoutTicks=80)
    public static void deferredDoesNotExportStalePeekAndCoverLossInvalidates(GameTestHelper h){scenario(h,true);}
    private static void scenario(GameTestHelper h,boolean exhaust) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(7.5,2,26.5));n.moveTo(at.x,at.y,at.z);n.setOrder(OrderState.Kind.ASSAULT,24);n.setNoAi(true);h.getLevel().addFreshEntity(n);
        wall(h,true);var sensor=new CoverSensor(n.runtime());var cover=vector(at);var old=vector(h.absoluteVec(new Vec3(7.5,2,22.5)));var threat=vector(h.absoluteVec(new Vec3(30.5,3,26.5)));
        long[] until={0};boolean[] alternate={false};
        h.runAtTickTime(5,()->{
            // Reproduce an existing cache, then require actual world revalidation of both positions.
            try{var field=CoverSensor.class.getDeclaredField("selected");field.setAccessible(true);field.set(sensor,new CoverOption(cover,old,h.getLevel().getGameTime()+100));}catch(ReflectiveOperationException e){throw new AssertionError(e);}
            var options=sensor.sample(observation(threat,h),h.getLevel().getGameTime(),false);
            h.assertTrue(!options.isEmpty() && options.get(0).peek()!=null,"initial real wall/standing/exit checks validate cached fixture");until[0]=h.getLevel().getGameTime();
        });
        h.runAtTickTime(10,()->{
            if(exhaust){var budget=AiWorkBudget.of(h.getLevel());while(budget.ray(n.getUUID())){} }
            else for(int x=6;x<=9;x++)for(int z=20;z<=24;z++)for(int y=2;y<=5;y++)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
            var options=sensor.sample(observation(threat,h),h.getLevel().getGameTime(),false);
            h.assertTrue(options.isEmpty() || options.get(0).peek()==null || !options.get(0).peek().equals(old),"expired/blocked cached exit is never exported as CLEAR");
            if(exhaust)h.assertTrue(sensor.deferred(),"ray exhaustion is explicit DEFERRED");
        });
        h.onEachTick(()->{
            if(h.getTick()<11 || h.getTick()>32)return;
            var options=sensor.sample(observation(threat,h),h.getLevel().getGameTime(),false);
            if(!options.isEmpty() && options.get(0).peek()!=null && (exhaust || !options.get(0).peek().equals(old)))alternate[0]=true;
        });
        h.runAtTickTime(34,()->{
            h.assertTrue(alternate[0],"usable exit is recovered with bounded continuing cursor after obstruction/DEFERRED");wall(h,false);
            // Changed legitimate threat invalidates both TTLs now; no cover survives removal.
            var changed=threat.plus(new Vector3(0,0,2));var options=sensor.sample(observation(changed,h),h.getLevel().getGameTime(),false);
            h.assertTrue(options.isEmpty(),"destroyed protection/new threat cannot renew cached cover");n.discard();h.succeed();
        });
    }
    private static Observation observation(Vector3 point,GameTestHelper h){long t=h.getLevel().getGameTime();return new Observation(new UUID(13,1),point,new Vector3(0,0,0),t,t+100,Observation.Source.VISION,1,0,true);}
    private static Vector3 vector(Vec3 p){return new Vector3(p.x,p.y,p.z);}
    private static void wall(GameTestHelper h,boolean solid){for(int y=2;y<=5;y++)for(int z=25;z<=27;z++)h.setBlock(new BlockPos(10,y,z),solid?Blocks.STONE:Blocks.AIR);}
}
