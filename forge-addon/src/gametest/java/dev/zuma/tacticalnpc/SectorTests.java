package dev.zuma.tacticalnpc;
import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.compat.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import com.tacz.guns.api.item.IGun;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
public final class SectorTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="sector-01-side",timeoutTicks=100)
    public static void thirtyTwoSideObjectsNoLongerSaturateAndMeasure(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,false);var c=new TaczGunController(n.runtime());var sensor=new VisibilitySensor();
        var crowd=new ArrayList<ArmorStand>();long[] times={0,0};int[] samples={0};
        h.runAtTickTime(10,()->{
            c.maintain(h.getLevel().getGameTime());double radius=.6+Math.tan(Math.toRadians(c.snapshot().spreadDegrees()*3+1))*128;
            // Keep all test bodies inside the loaded 64x64 fixture, independent of structure placement.
            double offset=Math.min(radius*.7,20);
            System.out.println("SECTOR_SCENE radius="+radius+" sideOffset="+offset+" origin="+n.position());
            for(int i=0;i<32;i++)crowd.add(stand(h,n.position().add(5+(i%4)*.1,0,offset+(i/4)*.1)));
        });
        h.onEachTick(()->{
            long tick=h.getTick();if(tick<12 || tick>51)return;var weapon=c.snapshot();Vec3 end=n.getEyePosition().add(12,0,0);
            long start=System.nanoTime();int old=oldBroadCount(n,end,weapon.spreadDegrees());long oldNanos=System.nanoTime()-start;
            start=System.nanoTime();boolean blocked=sensor.occupied(n,null,end,weapon);long newNanos=System.nanoTime()-start;
            h.assertTrue(old==32 && !blocked && sensor.lastSector()==VisibilitySensor.Sector.CLEAR,"same 32 bodies saturate old broad query but are outside actual narrow corridor: old="+old+" blocked="+blocked+" sector="+sensor.lastSector()+" spread="+weapon.spreadDegrees()+" source="+n.position());
            if(tick>=22){times[0]+=oldNanos;times[1]+=newNanos;samples[0]++;}
        });
        h.runAtTickTime(55,()->{System.out.println("SECTOR_MEASURE samples="+samples[0]+" old_nanos="+times[0]+" new_nanos="+times[1]+" old_queries_per_check=1 new_queries_per_clear=4");crowd.forEach(Entity::discard);n.discard();h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="sector-02-protection",timeoutTicks=90)
    public static void movingProtectedBodyBeforeAndAfterTargetAndDeferral(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,false);var c=new TaczGunController(n.runtime());var sensor=new VisibilitySensor();var body=stand(h,n.position().add(6,0,0));
        h.runAtTickTime(10,()->{
            c.maintain(h.getLevel().getGameTime());var end=n.getEyePosition().add(12,0,0);var weapon=c.snapshot();
            h.assertTrue(sensor.occupied(n,null,end,weapon) && sensor.lastSector()==VisibilitySensor.Sector.OCCUPIED,"protected front body blocks");
            body.moveTo(n.getX()+20,n.getY(),n.getZ());
            h.assertTrue(sensor.occupied(n,null,end,weapon),"body behind target within confirmed 128 ballistic length blocks");
            body.moveTo(n.getX()+3,n.getY(),n.getZ()+30);
            h.assertFalse(sensor.occupied(n,null,end,weapon),"fresh movement away clears without stale prior geometry");
            body.moveTo(n.getX()+4,n.getY(),n.getZ());
            h.assertTrue(sensor.occupied(n,null,end,weapon),"new same-tick crossing is rechecked");
            var b=AiWorkBudget.of(h.getLevel());while(b.query(n.getUUID(),true)){}
            h.assertTrue(sensor.occupied(n,null,end,weapon) && sensor.lastSector()==VisibilitySensor.Sector.DEFERRED,"query denial is distinct from occupied/clear");
        });
        h.runAtTickTime(11,()->{body.moveTo(n.getX()+3,n.getY(),n.getZ()+30);h.assertFalse(sensor.occupied(n,null,n.getEyePosition().add(12,0,0),c.snapshot()),"next call resumes ordering but validates every section fresh");body.discard();n.discard();h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="sector-03-native",timeoutTicks=200)
    public static void sideCrowdAllowsFiniteNativeRounds(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,true);var crowd=new ArrayList<ArmorStand>();
        for(int i=0;i<32;i++)crowd.add(stand(h,n.position().add(3+(i%4)*.1,0,20+(i/4)*.1)));
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"sector-target"));p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setInvulnerable(true);p.moveTo(n.getX()+12,n.getY(),n.getZ());h.getLevel().addNewPlayer(p);
        h.runAtTickTime(160,()->{
            int bullets=ProjectileProbe.count(n.getUUID());h.assertTrue(bullets>0 && bullets<=31,"same loaded finite gun produces actual native projectiles beside 32 protected objects: "+bullets);
            crowd.forEach(Entity::discard);p.discard();n.discard();h.succeed();
        });
    }
    /** Exact original broad query, retained only as measurement reference, never production safety. */
    private static int oldBroadCount(TacticalNpcEntity n,Vec3 end,double spread){
        Vec3 start=n.getEyePosition();end=start.add(end.subtract(start).normalize().scale(128));double radius=.6+Math.tan(Math.toRadians(Math.min(75,spread*3+1)))*128;
        var nearby=new ArrayList<LivingEntity>();n.level().getEntities(EntityTypeTest.forClass(LivingEntity.class),new AABB(start,end).inflate(radius),e->e!=n && e.isAlive(),nearby,32);return nearby.size();
    }
    private static TacticalNpcEntity npc(GameTestHelper h,boolean active){
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(24.5,2,24.5));n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.setOrder(dev.zuma.tacticalnpc.core.OrderState.Kind.ASSAULT,24);
        n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);n.setNoAi(!active);
        var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,30);gun.setBulletInBarrel(stack,true);n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,0);
        if(active)n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);return n;
    }
    private static ArmorStand stand(GameTestHelper h,Vec3 at){var a=new ArmorStand(h.getLevel(),at.x,at.y,at.z);a.setNoGravity(true);h.getLevel().addFreshEntity(a);return a;}
}
