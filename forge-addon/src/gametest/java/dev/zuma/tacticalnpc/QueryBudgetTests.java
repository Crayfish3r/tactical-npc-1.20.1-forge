package dev.zuma.tacticalnpc;
import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.compat.GunSupport;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
public final class QueryBudgetTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="query-04-optional-ray-fair",timeoutTicks=50)
    public static void optionalRaysRemainFairAcrossAcquisitionCadence(GameTestHelper h) {
        ScenarioIsolation.begin(h);AiWorkBudget.remove(h.getLevel());var ids=new ArrayList<UUID>();for(int i=0;i<50;i++)ids.add(new UUID(1015,i));var served=new HashSet<UUID>();
        for(int tick=5;tick<=29;tick+=3)h.runAtTickTime(tick,()->{
            var budget=AiWorkBudget.of(h.getLevel());for(var id:ids)for(int ray=0;ray<4;ray++)if(budget.ray(id))served.add(id);
            h.assertTrue(budget.rays()<=128,"optional ray reserve stays bounded");
        });
        h.runAtTickTime(30,()->{h.assertTrue(served.size()==50,"all late owners receive optional LOS on three-tick acquisition cadence: "+served.size());AiWorkBudget.remove(h.getLevel());h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="query-03-ray-fair",timeoutTicks=50)
    public static void persistentRayPressureCannotStarveLastOfFifty(GameTestHelper h) {
        ScenarioIsolation.begin(h);AiWorkBudget.remove(h.getLevel());var ids=new ArrayList<UUID>();for(int i=0;i<50;i++)ids.add(new UUID(1014,i));
        var served=new HashSet<UUID>();
        for(int tick=5;tick<=12;tick++)h.runAtTickTime(tick,()->{
            var budget=AiWorkBudget.of(h.getLevel());for(var id:ids)for(int round=0;round<8;round++)if(budget.activeRay(id))served.add(id);
            h.assertTrue(budget.rays()<=384,"global ray limit never raised for fairness");
        });
        h.runAtTickTime(13,()->{h.assertTrue(served.size()==50,"all 50 owners must receive active LOS/native safety service under fixed order: served="+served.size());AiWorkBudget.remove(h.getLevel());h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="query-01-denial",timeoutTicks=60)
    public static void deniedBeforeAcquireActivationAndSafetyThenResumes(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.setNoAi(true);
        n.setItemSlot(EquipmentSlot.MAINHAND,GunSupport.create(new ResourceLocation("tacz:ak47")));h.getLevel().addFreshEntity(n);var sensor=new VisibilitySensor();
        h.runAtTickTime(10,()->{
            var budget=AiWorkBudget.of(h.getLevel());
            for(int i=0;i<80;i++){var id=UUID.randomUUID();while(budget.query(id,false)){}while(budget.query(id,true)){}}
            int before=budget.queries(),worldBefore=budget.worldQueries();
            h.assertTrue(sensor.acquire(n,40)==null && sensor.acquisitionDeferred(),"zero optional query capacity is DEFERRED before page reads");
            h.assertTrue(sensor.activation(n,40)==VisibilitySensor.Nearby.DEFERRED,"activation cannot query world after denial");
            var gun=new dev.zuma.tacticalnpc.compat.TaczGunController(n.runtime());gun.maintain(h.getLevel().getGameTime());
            h.assertTrue(sensor.occupied(n,null,n.getEyePosition().add(10,0,0),gun.snapshot()),"native safety fails closed when query deferred");
            h.assertTrue(before==budget.queries() && worldBefore==budget.worldQueries() && budget.queryDenials()>0,"no granted query or world API query after exhaustion");
        });
        h.runAtTickTime(11,()->{h.assertTrue(AiWorkBudget.of(h.getLevel()).query(n.getUUID(),true),"denied owner receives reserved next tick query capacity");n.discard();AiWorkBudget.remove(h.getLevel());h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="query-02-fair",timeoutTicks=60)
    public static void fixedTickOrderCannotStarveLateOwnersAndUnloadClears(GameTestHelper h) {
        ScenarioIsolation.begin(h);AiWorkBudget.remove(h.getLevel());var ids=new ArrayList<UUID>();for(int i=0;i<100;i++)ids.add(UUID.randomUUID());
        var served=new HashSet<UUID>();
        for(int t=5;t<=12;t++)h.runAtTickTime(t,()->{
            var budget=AiWorkBudget.of(h.getLevel());
            for(var id:ids)for(int i=0;i<8;i++)if(budget.query(id,true))served.add(id);
            h.assertTrue(budget.queries()<=1024,"hard per-level native section query limit");
        });
        h.runAtTickTime(13,()->{
            h.assertTrue(served.size()==100,"all 100 identities served under stable earliest-first overload, including last");
            var old=AiWorkBudget.of(h.getLevel());h.assertTrue(old.maximumDeferredAge()>0,"bounded deferred-age counter records pressure");AiWorkBudget.remove(h.getLevel());
            h.assertTrue(AiWorkBudget.of(h.getLevel())!=old && AiWorkBudget.of(h.getLevel()).queryDenials()==0,"level unload cleanup discards queues/counters");h.succeed();
        });
    }
}
