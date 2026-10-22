package dev.zuma.tacticalnpc;
import dev.zuma.tacticalnpc.compat.*;
import dev.zuma.tacticalnpc.entity.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class WeaponSnapshotTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="snapshot-01-measure",timeoutTicks=60)
    public static void measureSnapshotReads(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(at.x,at.y,at.z);n.setNoAi(true);
        n.setItemSlot(EquipmentSlot.MAINHAND,GunSupport.create(new ResourceLocation("tacz:ak47")));h.getLevel().addFreshEntity(n);
        h.runAtTickTime(10,()->{
            var c=new TaczGunController(n.runtime());
            long draw=n.runtime().operator().getDataHolder().drawTimestamp;c.snapshot();
            h.assertTrue(c.measurements()[2]==0 && draw==n.runtime().operator().getDataHolder().drawTimestamp,"read does not draw or mutate native draw clock");
            c.maintain(h.getLevel().getGameTime());c.maintain(h.getLevel().getGameTime());
            h.assertTrue(c.measurements()[2]==1,"one explicit draw for stack in tick");
            for(int i=0;i<2000;i++)c.snapshot();
            var mx=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();long thread=Thread.currentThread().getId();
            long bytes=mx.getThreadAllocatedBytes(thread),start=System.nanoTime();var before=c.measurements();
            for(int i=0;i<10000;i++)h.assertTrue(c.snapshot().profile().kind()==dev.zuma.tacticalnpc.core.WeaponProfile.Kind.AR,"stable native metadata");
            long nanos=System.nanoTime()-start;bytes=mx.getThreadAllocatedBytes(thread)-bytes;var after=c.measurements();
            System.out.println("SNAPSHOT_MEASURE reads="+(after[0]-before[0])+" lookups="+(after[1]-before[1])+" draw="+(after[2]-before[2])+" profiles="+(after[3]-before[3])+" nanos="+nanos+" bytes="+bytes);
            n.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="snapshot-02-equip",timeoutTicks=80)
    public static void equipModePropertiesAndResourcesInvalidateWithoutReadDraw(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(at.x,at.y,at.z);n.setNoAi(true);
        n.setItemSlot(EquipmentSlot.MAINHAND,GunSupport.create(new ResourceLocation("tacz:ak47")));h.getLevel().addFreshEntity(n);
        h.runAtTickTime(10,()->{
            var c=new TaczGunController(n.runtime());long tick=h.getLevel().getGameTime();c.maintain(tick);
            var operator=n.runtime().operator();operator.reload();var before=operator.getDataHolder().reloadTimestamp;
            for(int i=0;i<10;i++)c.snapshot();
            h.assertTrue(before==operator.getDataHolder().reloadTimestamp && c.measurements()[2]==1,"read leaves native reload intact");
            n.addTag(dev.zuma.tacticalnpc.runtime.RuntimeRegistry.TAG);
            long drawClock=operator.getDataHolder().drawTimestamp;
            try {
                var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPermission(2).withSuppressedOutput();
                h.assertTrue(h.getLevel().getServer().getCommands().getDispatcher().execute("tacticalnpc equip "+n.getUUID()+" tacz:glock_17",source)==1,"actual equip command succeeds");
            } catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){throw new IllegalStateException(ex);}
            var replacement=n.getMainHandItem();
            h.assertTrue(operator.getDataHolder().drawTimestamp==drawClock,"equip command leaves the single draw to tick maintenance");
            h.assertTrue(!c.snapshot().supported() && c.measurements()[2]==1,"equip between ticks fails closed without draw");
            c.maintain(tick+1);h.assertTrue(c.snapshot().supported() && c.measurements()[2]==2 && c.snapshot().profile().kind()==dev.zuma.tacticalnpc.core.WeaponProfile.Kind.PISTOL,"next explicit maintenance draws changed stack/id once");
            var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(replacement);long lookups=c.measurements()[1];gun.setFireMode(replacement,com.tacz.guns.api.item.gun.FireMode.BURST);
            h.assertTrue(!c.snapshot().supported() && c.measurements()[1]>lookups,"mode invalidation preserves unsupported mode rejection");
            gun.setFireMode(replacement,com.tacz.guns.api.item.gun.FireMode.SEMI);
            var cache=operator.getCacheProperty();cache.setCache(com.tacz.guns.api.GunProperties.EFFECTIVE_RANGE,7f);
            h.assertTrue(c.snapshot().damageFalloff()==7 && c.snapshot().effectiveRange()>7,"mutable damage falloff read fresh without clamping kinetic reach");
            double oldSpeed=c.snapshot().ballistics().speed();
            Float cachedSpeed=cache.getCache(com.tacz.guns.api.GunProperties.AMMO_SPEED);
            cache.setCache(com.tacz.guns.api.GunProperties.AMMO_SPEED,cachedSpeed/2);
            h.assertTrue(Math.abs(c.snapshot().ballistics().speed()-oldSpeed/2)<1e-6,"live attachment speed read fresh in verified units");
            GunSupport.verifyResources(h.getLevel().getServer().getResourceManager());
            h.assertTrue(!c.snapshot().supported() && c.measurements()[2]==2,"resource generation invalidates native readiness without read draw");
            c.maintain(tick+2);h.assertTrue(c.snapshot().supported() && c.measurements()[2]==3,"resource maintenance rebuilds native draw");
            n.setItemSlot(EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
            h.assertTrue(!c.snapshot().supported(),"non-gun remains blocked");n.discard();h.succeed();
        });
    }
}
