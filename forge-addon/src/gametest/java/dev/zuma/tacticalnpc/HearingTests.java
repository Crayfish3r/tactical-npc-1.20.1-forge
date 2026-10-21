package dev.zuma.tacticalnpc;
import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import com.tacz.guns.api.item.IGun;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
public final class HearingTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="hearing-01-region",timeoutTicks=110)
    public static void hiddenInvisibleSourcesGrantOnlyExpiringRegionAndNeutralIgnores(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,3.5,7.5,true);var neutral=npc(h,3.5,11.5,true);neutral.enterNeutral();
        n.setFaction("hearing-listeners");neutral.setFaction("hearing-listeners");
        var p=player(h,12.5,7.5);p.setInvisible(true);
        for(int y=2;y<6;y++)for(int z=0;z<16;z++)h.setBlock(new BlockPos(7,y,z),Blocks.STONE);
        Vector3[] heard={null};
        h.runAtTickTime(10,()->{
            h.assertTrue(n.controller().hear(p),"supported invisible Player behind wall is audible without FOV/LOS");
            var o=n.controller().observation();heard[0]=o.position();
            h.assertTrue(o.source()==Observation.Source.HEARING && !o.visible() && o.uncertainty()==8 && o.confidence()==.55,"sound is uncertain HEARING only");
            h.assertFalse(o.position().equals(new Vector3(p.getX(),p.getEyeY(),p.getZ())),"hidden exact aim point never copied");
            h.assertFalse(neutral.controller().hear(p),"neutral cannot start on real sound");
            neutral.invulnerableTime=0;neutral.hurt(h.getLevel().damageSources().playerAttack(p),1);
            var other=npc(h,13.5,12.5,false);other.setFaction("sound-source");other.setInvisible(true);
            h.assertTrue(n.controller().hear(other),"supported NPC source has same sound eligibility");
            h.assertFalse(neutral.controller().hear(other),"sound cannot replace reactive attacker");
            h.assertTrue(neutral.reactiveTo(p.getUUID()),"original authenticated attacker retained");
            other.discard();n.controller().hear(p);
        });
        h.runAtTickTime(20,()->{h.assertTrue(n.controller().shots()==0 && !n.controller().allowNativeFire(),"hearing alone never DIRECT/SUPPRESS fire");p.moveTo(p.getX()+2,p.getY(),p.getZ()+3);});
        h.runAtTickTime(30,()->h.assertTrue(n.controller().observation()!=null && !n.controller().observation().visible(),"hidden motion does not create visual sample"));
        h.runAtTickTime(80,()->{h.assertTrue(n.controller().observation()==null && ProjectileProbe.count(n.getUUID())==0,"TTL expires without delivered new event");n.discard();neutral.discard();p.discard();h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="hearing-02-local",timeoutTicks=70)
    public static void boundedLocalDeliveryCoalescesAndRotatesRecipients(GameTestHelper h) {
        ScenarioIsolation.begin(h);GunHearing.remove(h.getLevel());var source=npc(h,32.5,32.5,false);source.setFaction("source");source.setInvisible(true);
        var listeners=new ArrayList<TacticalNpcEntity>();for(int i=0;i<50;i++)listeners.add(npc(h,22.5+i%10,23.5+i/10,false));
        var heard=new HashSet<UUID>();
        for(int t=10;t<=14;t++)h.runAtTickTime(t,()->{
            int delivered=GunHearing.deliver(source);h.assertTrue(delivered<=16,"bounded recipients per delivered event");
            long[] before=GunHearing.measurements(h.getLevel());
            h.assertTrue(GunHearing.deliver(source)==0,"same-tick native pellets/rounds coalesce without new observation");
            long[] after=GunHearing.measurements(h.getLevel());h.assertTrue(after[2]==before[2] && after[5]==before[5],"coalescing neither queries nor extends delivered TTL");
            for(var n:listeners)if(n.controller().observation()!=null)heard.add(n.getUUID());
        });
        h.runAtTickTime(16,()->{
            var m=GunHearing.measurements(h.getLevel());h.assertTrue(heard.size()==50 && m[2]==5 && m[4]<=560,"local dense scene reaches late listeners over real event calls without global active list");
            System.out.println("HEARING_MEASURE listeners=50 events="+m[0]+" queries="+m[2]+" candidates="+m[4]+" delivered="+m[5]+" nanos="+m[6]+" old_global_calls_for_5_events=250");
            var b=AiWorkBudget.of(h.getLevel());for(int i=0;i<40;i++){var id=UUID.randomUUID();while(b.query(id,false)){}}
            long time=listeners.get(0).controller().observation().tick();h.assertTrue(GunHearing.deliver(source)==0,"query denial drops optional event without fabricated observation");
            h.assertTrue(listeners.get(0).controller().observation().tick()==time,"denial never refreshes TTL");
            listeners.forEach(Entity::discard);source.discard();GunHearing.remove(h.getLevel());h.assertTrue(GunHearing.measurements(h.getLevel())[0]==0,"level cleanup clears sound delivery state");h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="hearing-03-native",timeoutTicks=180)
    public static void actualNpcNativeProjectileDeliversSound(GameTestHelper h) {
        ScenarioIsolation.begin(h);var shooter=npc(h,24.5,24.5,true);shooter.setFaction("source");shooter.setOrder(OrderState.Kind.ASSAULT,24);
        var listener=npc(h,24.5,34.5,false);var p=player(h,40.5,24.5);boolean[] heard={false};
        h.onEachTick(()->{var o=listener.controller()==null?null:listener.controller().observation();if(o!=null && o.source()==Observation.Source.HEARING && o.subject().equals(shooter.getUUID()))heard[0]=true;});
        h.runAtTickTime(140,()->{h.assertTrue(ProjectileProbe.count(shooter.getUUID())>0 && heard[0],"genuine supported NPC kinetic rounds deliver regional sound");shooter.discard();listener.discard();p.discard();h.succeed();});
    }
    private static TacticalNpcEntity npc(GameTestHelper h,double x,double z,boolean ai) {
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(x,2,z));n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.setOrder(OrderState.Kind.HOLD_POSITION,24);n.setNoAi(!ai);
        n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var g=IGun.getIGunOrNull(stack);g.setCurrentAmmoCount(stack,30);g.setBulletInBarrel(stack,true);n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);return n;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,double x,double z){var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"hearing-source"));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);var at=h.absoluteVec(new Vec3(x,2,z));p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);return p;}
}
