package dev.zuma.tacticalnpc;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import dev.zuma.tacticalnpc.combat.TacticalCombatGoal;
import dev.zuma.tacticalnpc.combat.VisibilitySensor;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.npcs.Npc210Adapter;
import dev.zuma.tacticalnpc.config.ServerConfig;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Separate batches prevent nearby fixtures/FakePlayers from becoming each other's targets. */
@PrefixGameTestTemplate(false)
public final class WeaponScenarioTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena", batch="01-no-target", timeoutTicks=200)
    public static void noTargetMaintenance(GameTestHelper h) {
        var n = spawn(h, "ak47", 0, false);
        h.runAtTickTime(100, () -> {
            h.assertTrue(goal(n).shots() == 0 && goal(n).targetId() == null, "idle NPC never shoots");
            h.assertTrue(goal(n).reloads() == 1 && IGun.getIGunOrNull(n.getMainHandItem()).getCurrentAmmoCount(n.getMainHandItem()) > 0, "idle empty gun reloads once");
            finish(h,n);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena", batch="02-reload-cover", timeoutTicks=220)
    public static void reloadBehindWall(GameTestHelper h) {
        var n = spawn(h,"ak47",0,false); var p = player(h);
        wall(h,true);
        h.runAtTickTime(25, () -> h.assertTrue(n.getDataHolder().reloadStateType.isReloading() && goal(n).shots() == 0, "reload active behind wall, no shots"));
        h.runAtTickTime(105, () -> {
            h.assertTrue(goal(n).reloads() == 1 && !n.getDataHolder().reloadStateType.isReloading(), "native reload completed in cover");
            h.assertTrue(goal(n).shots() == 0 && goal(n).targetId() == null, "occluded player never acquired");
            p.discard(); finish(h,n);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena", batch="03-reaction", timeoutTicks=180)
    public static void reactionThenLoss(GameTestHelper h) {
        var n=spawn(h,"ak47",30,true); var p=player(h);
        h.runAtTickTime(4, () -> h.assertTrue(goal(n).shots()==0,"no instant shot"));
        h.runAtTickTime(65, () -> {
            h.assertTrue(goal(n).shots()>0,"visible player receives native fire: head="+n.getYHeadRot()+" body="+n.yBodyRot+" decision="+goal(n).decision()+" observation="+goal(n).observation());
            long count=goal(n).shots(); wall(h,true);
            h.runAtTickTime(80, () -> {
                h.assertTrue(goal(n).shots()==count && goal(n).targetId()==null,"LoS loss cancels fire");
                p.discard(); finish(h,n);
            });
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena", batch="04-bolt", timeoutTicks=160)
    public static void boltWithoutTarget(GameTestHelper h) {
        var n=spawn(h,"ai_awp",3,false);
        h.runAtTickTime(90, () -> {
            h.assertTrue(IGun.getIGunOrNull(n.getMainHandItem()).hasBulletInBarrel(n.getMainHandItem()),"manual action chambers native round without target");
            h.assertTrue(goal(n).shots()==0,"bolt does not fire"); finish(h,n);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena", batch="05-corridor", timeoutTicks=180)
    public static void allyCrossingAndBehindTarget(GameTestHelper h) {
        ServerConfig.DEBUG.set(true);
        var n=spawn(h,"aa12",8,true); var p=player(h);
        var ally=h.spawn(EntityType.IRON_GOLEM,new BlockPos(14,2,7)); ally.setNoAi(true);
        h.runAtTickTime(30, () -> {
            h.assertTrue(new VisibilitySensor().occupied(n,p,new VisibilitySensor().aimPoint(p)),"shotgun cone continues behind target");
            h.assertTrue(goal(n).shots()==0,"entity behind target blocks shot");
            Vec3 center=n.position().add(p.position()).scale(.5); ally.moveTo(center.x,center.y,center.z);
        });
        h.runAtTickTime(45, () -> {
            h.assertTrue(goal(n).shots()==0,"crossing living blocker prevents fire"); ally.discard();
        });
        h.runAtTickTime(90, () -> { h.assertTrue(goal(n).shots()>0,"shotgun fires after corridor clears: target="+goal(n).targetId()+" head="+n.getYHeadRot()+" position="+n.position()+" decision="+goal(n).decision()+" gun="+goal(n).weaponState()); p.discard(); finish(h,n); });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena", batch="06-ownership", timeoutTicks=100)
    public static void tagDeathUnload(GameTestHelper h) {
        var n=spawn(h,"ak47",30,true);
        var originalLook=n.getLookControl();var originalBody=n.bodyRotationControl;
        h.runAtTickTime(10, () -> { h.assertTrue(goal(n)!=null,"claimed"); n.removeTag(Npc210Adapter.TAG); });
        h.runAtTickTime(15, () -> {
            h.assertTrue(goal(n)==null && n.goalSelector.getAvailableGoals().stream().filter(g->g.getGoal()==n.fireAtNearestTargetGoal).count()==1,"restored once");
            h.assertTrue(n.getLookControl()==originalLook && n.bodyRotationControl==originalBody,"original look/body controllers restored");
            n.addTag(Npc210Adapter.TAG);
        });
        h.runAtTickTime(20, () -> { h.assertTrue(goal(n)!=null,"reclaimed"); n.setHealth(0); });
        h.runAtTickTime(25, () -> { h.assertTrue(goal(n)==null,"death releases"); n.discard(); finish(h,n); });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="08-legacy-navigation-clamp",timeoutTicks=80)
    public static void legacyNavigationCannotSnapAim(GameTestHelper h) {
        var n=spawn(h,"ak47",30,true);var at=n.position();
        h.runAtTickTime(5,()->{
            h.assertTrue(n.getNavigation().moveTo(at.x,at.y,at.z+5,1),"active legacy native path fixture");
            n.setYBodyRot(180);n.setYHeadRot(0);
            n.getLookControl().setLookAt(at.x,n.getEyeY(),at.z+5,ServerConfig.TURN.get().floatValue(),ServerConfig.TURN.get().floatValue());
        });
        h.runAtTickTime(6,()->{
            h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(n.getYHeadRot()))<=ServerConfig.TURN.get()+.01,"legacy navigation/body respects head rate: "+n.getYHeadRot());
            finish(h,n);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena", batch="07-modes", timeoutTicks=160)
    public static void nativeModesAndBurst(GameTestHelper h) {
        h.assertTrue(IGun.getIGunOrNull(GunSupport.create(new ResourceLocation("tacz:ak47"))).getFireMode(GunSupport.create(new ResourceLocation("tacz:ak47")))==FireMode.AUTO,"AK chooses allowed AUTO");
        var glock=GunSupport.create(new ResourceLocation("tacz:glock_17"));
        h.assertTrue(IGun.getIGunOrNull(glock).getFireMode(glock)==FireMode.SEMI,"pistol chooses allowed SEMI");
        var n=spawn(h,"b93r",20,true); var p=player(h);
        IGun.getIGunOrNull(n.getMainHandItem()).setFireMode(n.getMainHandItem(),FireMode.BURST);
        h.runAtTickTime(70, () -> {
            h.assertTrue(goal(n).shots()>0 && ProjectileProbe.count(n.getUUID())>0,"native burst creates real bullets");
            long shots=goal(n).shots();int bullets=ProjectileProbe.count(n.getUUID());wall(h,true);
            h.runAtTickTime(85, () -> { h.assertTrue(goal(n).shots()==shots && ProjectileProbe.count(n.getUUID())==bullets,"burst stops actual delayed rounds behind wall"); p.discard(); finish(h,n); });
        });
    }
    private static AutoGunOperator spawn(GameTestHelper h,String id,int ammo,boolean chamber) {
        ScenarioIsolation.begin(h);
        ServerConfig.STRAFE.set(0.0);
        AutoGunOperator n=AutoGunOperator.create(h.getLevel(),"default");
        Vec3 pos=h.absoluteVec(new Vec3(3.5,2,7.5)); n.moveTo(pos.x,pos.y,pos.z,-90,0); n.setYHeadRot(-90);
        var stack=GunSupport.create(new ResourceLocation("tacz",id)); var gun=IGun.getIGunOrNull(stack);
        gun.setCurrentAmmoCount(stack,ammo); gun.setBulletInBarrel(stack,chamber);
        n.setItemSlot(EquipmentSlot.MAINHAND,stack); n.addTag(Npc210Adapter.TAG); h.getLevel().addFreshEntity(n); return n;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h) {
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"weapon-test"));
        p.setGameMode(GameType.SURVIVAL); p.setInvulnerable(true); Vec3 pos=h.absoluteVec(new Vec3(11.5,2,7.5)); p.moveTo(pos.x,pos.y,pos.z); h.getLevel().addNewPlayer(p); return p;
    }
    private static TacticalCombatGoal goal(AutoGunOperator n) {
        return n.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(TacticalCombatGoal.class::isInstance).map(TacticalCombatGoal.class::cast).findFirst().orElse(null);
    }
    private static void wall(GameTestHelper h,boolean solid) {
        for(int y=2;y<=6;y++) for(int z=0;z<16;z++) h.setBlock(new BlockPos(7,y,z),solid?Blocks.STONE:Blocks.AIR);
    }
    private static void finish(GameTestHelper h,AutoGunOperator n) { n.discard(); ServerConfig.STRAFE.set(.55); h.succeed(); }
}
