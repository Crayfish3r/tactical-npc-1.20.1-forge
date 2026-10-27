package dev.zuma.tacticalnpc;

import com.tacz.guns.api.item.IGun;
import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class FiringRegressionTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="firing-01-stand",timeoutTicks=240)
    public static void emptyStandReloadsWithSelectedCoverBeforeFirstRound(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,0,OrderState.Kind.HOLD_POSITION);var p=player(h,n.position().add(10,0,0));var origin=n.position();
        // A real protected, reachable position, away from the open current line of fire.
        for(int y=2;y<=5;y++)for(int z=33;z<=38;z++)h.setBlock(new BlockPos(37,y,z),Blocks.STONE);
        h.runAtTickTime(12,()->{
            h.assertTrue(n.controller().observation()!=null && n.controller().observation().visible(),"empty WAIT already sees the hostile before reserve arrives");
            var covers=(CoverSensor)field(n.controller(),"covers");
            var at=covers.safeStanding(h.absoluteVec(new Vec3(32.5,2,36.5)));
            h.assertTrue(at!=null,"cover has real standing geometry");
            set(covers,"selected",new CoverOption(vector(at),null,h.getLevel().getGameTime()+100));
            h.assertTrue(!covers.sample(n.controller().observation(),h.getLevel().getGameTime(),false).isEmpty(),"seeded cover passes actual world and order validation");
            var gun=IGun.getIGunOrNull(n.getMainHandItem());gun.setDummyAmmoAmount(n.getMainHandItem(),30);gun.setMaxDummyAmmoAmount(n.getMainHandItem(),30);
        });
        h.onEachTick(()->{if(ProjectileProbe.count(n.getUUID())==0) {
            h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.WAIT,"maintenance never fakes a first shot");
            h.assertTrue(n.position().subtract(origin).horizontalDistance()<.1,"WAIT stays physically at anchor while reloading");
        }});
        h.runAtTickTime(210,()->{
            h.assertTrue(n.controller().reloads()==1 && ProjectileProbe.count(n.getUUID())>0,"one accepted native reload then actual native round; reloads="+n.controller().reloads()+" rounds="+ProjectileProbe.count(n.getUUID())+" decision="+n.controller().decision());
            h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.COMBAT,"actual first round releases WAIT");n.discard();p.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="firing-02-edge",timeoutTicks=130)
    public static void exhaustedDefendBoundaryStillFiresAndCancelsEpisode(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,30,OrderState.Kind.DEFEND_AREA);n.setOrder(OrderState.Kind.DEFEND_AREA,4);
        n.setPos(n.getX()+2.7,n.getY(),n.getZ());var p=player(h,n.position().add(2,0,0));int[] before={0};
        n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        h.runAtTickTime(12,()->{
            var movement=(MovementExecutor)field(n.controller(),"movement");var recovery=(RouteRecovery)field(movement,"recovery");
            long tick=h.getLevel().getGameTime();var bad=vector(n.position().add(0,0,3));
            recovery.fail(bad,tick);for(int i=0;i<4;i++)recovery.alternative(vector(n.position()),bad,tick);
            h.assertTrue(recovery.status()==RouteRecovery.Status.EXHAUSTED,"repro starts with an exhausted finite recovery");
            before[0]=ProjectileProbe.count(n.getUUID());n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(.25);
            set(n.controller(),"nextPlan",0L);n.controller().tick();
            var d=n.controller().decision();
            h.assertTrue(d.movement().mode()==MovementCommand.Mode.HOLD && d.firePermitted(),"order clips reposition but retains safe fire: "+d);
            h.assertTrue(movement.recoveryStatus()==RouteRecovery.Status.IDLE,"HOLD cancels obsolete recovery instead of sticking EXHAUSTED");
            h.assertTrue(recovery.rejected(bad,tick),"canceling the episode retains destination-specific backoff");
        });
        h.runAtTickTime(100,()->{h.assertTrue(ProjectileProbe.count(n.getUUID())>before[0],"boundary NPC creates actual native rounds after exhausted recovery");n.discard();p.discard();h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="firing-03-health",timeoutTicks=130)
    public static void criticalHealthAllowsActualReturnFire(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,30,OrderState.Kind.ASSAULT);n.setHealth(n.getMaxHealth()*.25F);
        n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var p=player(h,n.position().add(10,0,0));
        h.runAtTickTime(100,()->{
            h.assertTrue(n.getHealth()/n.getMaxHealth()<=.25 && n.controller().decision().intent()==TacticalIntent.RETREAT,"critical retreat policy exercised");
            h.assertTrue(ProjectileProbe.count(n.getUUID())>0,"actual native gate allows critically wounded return fire");n.discard();p.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="firing-04-sector",timeoutTicks=80)
    public static void distantBodiesAndOtherHostilesDoNotVetoCloseShot(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,30,OrderState.Kind.ASSAULT);n.setNoAi(true);
        var p=player(h,n.position().add(2,0,0));var other=player(h,n.position().add(6,0,0));
        var body=new ArmorStand(h.getLevel(),n.getX()+24,n.getY(),n.getZ());body.setNoGravity(true);h.getLevel().addFreshEntity(body);
        var guns=new dev.zuma.tacticalnpc.compat.TaczGunController(n.runtime());var sensor=new VisibilitySensor();
        h.runAtTickTime(20,()->{
            guns.maintain(h.getLevel().getGameTime());var end=sensor.aimPoint(p);var weapon=guns.snapshot();
            h.assertFalse(sensor.occupied(n,p,end,weapon),"another eligible hostile and distant neutral behind a close target do not veto fire");
            other.moveTo(n.getX()+1,n.getY(),n.getZ());
            h.assertFalse(sensor.occupied(n,p,end,weapon),"eligible hostile crossing ahead of target is not mislabeled as an ally");
            var team=h.getLevel().getScoreboard().addPlayerTeam("fire-protected");
            h.getLevel().getScoreboard().addPlayerToTeam(n.getScoreboardName(),team);h.getLevel().getScoreboard().addPlayerToTeam(other.getScoreboardName(),team);n.syncTeam();
            h.assertTrue(sensor.occupied(n,p,end,weapon) && other.getUUID().equals(sensor.lastBlocker()) && sensor.lastBlockerAllied(),"actual ally in front still blocks, with exact identity and relation");
            h.getLevel().getScoreboard().removePlayerFromTeam(other.getScoreboardName());
            body.moveTo(n.getX()+6,n.getY(),n.getZ());
            h.assertTrue(sensor.occupied(n,p,end,weapon) && body.getUUID().equals(sensor.lastBlocker()) && !sensor.lastBlockerAllied(),"nearby non-hostile behind target is protected and distinguished from an ally");
            body.discard();other.discard();p.discard();n.discard();h.succeed();
        });
    }
    private static TacticalNpcEntity npc(GameTestHelper h,int ammo,OrderState.Kind kind) {
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(32.5,2,32.5));n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.setOrder(kind,24);
        var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,ammo);gun.setBulletInBarrel(stack,ammo>0);
        n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,0);n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);return n;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,Vec3 at) {
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"fire-"+UUID.randomUUID().toString().substring(0,8)));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);return p;
    }
    private static Object field(Object owner,String name){try{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void set(Object owner,String name,Object value){try{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Vector3 vector(Vec3 p){return new Vector3(p.x,p.y,p.z);}
}
