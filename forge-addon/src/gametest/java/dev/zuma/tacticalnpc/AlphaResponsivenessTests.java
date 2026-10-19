package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.config.ServerConfig;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@PrefixGameTestTemplate(false)
public final class AlphaResponsivenessTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="alpha-01",timeoutTicks=200)
    public static void contactReacquireTrackingAndDeferred(GameTestHelper h) {
        ScenarioIsolation.begin(h);ServerConfig.PERCEPTION.set(2);ServerConfig.REACTION.set(3);ServerConfig.REACQUIRE.set(1);ServerConfig.ACQUISITION.set(4);ServerConfig.TURN.set(17.0);ServerConfig.SUPPRESSION.set(false);
        var n=NpcEntities.NPC.get().create(h.getLevel());Vec3 origin=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(origin.x,origin.y,origin.z,-90,0);n.setYHeadRot(-90);
        // Isolate ready/aligned fire latency from pursuit: an immobile HOLD stand must not start a search path.
        n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);n.setOrder(OrderState.Kind.HOLD_POSITION,24);
        var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,30);gun.setBulletInBarrel(stack,true);n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"alpha-contact"));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);Vec3 at=h.absoluteVec(new Vec3(11.5,2,7.5));p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);wall(h,true);
        long[] now={0},opened={0},firstLatency={-1},reopened={0},reacquireLatency={-1},closed={0};int[] count={0},stage={0};boolean[] tracked={false},deferred={false};
        h.onEachTick(()->{
            long tick=++now[0];if(n.controller()==null)return;var goal=n.controller();
            if(tick==40) {wall(h,false);opened[0]=tick;stage[0]=1;}
            if(stage[0]==1 && ProjectileProbe.count(n.getUUID())>0) {
                firstLatency[0]=tick-opened[0];wall(h,true);count[0]=ProjectileProbe.count(n.getUUID());closed[0]=tick;stage[0]=2;
            } else if(stage[0]==2) {
                h.assertTrue(ProjectileProbe.count(n.getUUID())==count[0],"no projectile crosses closed wall including native delayed rounds");
                if(tick>closed[0]+2) h.assertTrue(goal.targetId()==null && p.getUUID().equals(goal.contactId()),"visibility and retained UUID are separate");
                if(tick>closed[0]+10 && goal.weaponState().cooldownReady()) {wall(h,false);reopened[0]=tick;stage[0]=3;}
            } else if(stage[0]==3 && ProjectileProbe.count(n.getUUID())>count[0]) {
                reacquireLatency[0]=tick-reopened[0];stage[0]=4;
            } else if(stage[0]==4) {
                var o=goal.observation();var d=goal.decision();
                if(o!=null && o.visible() && d.aim().mode()==AimCommand.Mode.DIRECT && d.frame().tick()<h.getLevel().getGameTime()) {
                    h.assertTrue(d.aim().point().equals(o.position()),"DIRECT aim tracks fresh visual sample between tactic plans");tracked[0]=true;
                }
                p.setPos(at.x,at.y,at.z+Math.sin(tick*.2)*.5);
                if(tracked[0] && !deferred[0]) {
                    var budget=AiWorkBudget.of(h.getLevel());while(budget.activeRay(n.getUUID())){}
                    h.assertTrue(new VisibilitySensor().los(n,new VisibilitySensor().aimPoint(p),true)==VisibilitySensor.Los.DEFERRED,"exhaustion is DEFERRED not BLOCKED");
                    int bullets=ProjectileProbe.count(n.getUUID());goal.tick();
                    h.assertTrue(p.getUUID().equals(goal.contactId()) && !goal.observation().visible() && !goal.allowNativeFire() && ProjectileProbe.count(n.getUUID())==bullets,"DEFERRED retains contact and fails native fire closed");deferred[0]=true;
                }
            }
        });
        h.runAtTickTime(175,()->{
            h.assertTrue(firstLatency[0]>0 && reacquireLatency[0]>=0 && reacquireLatency[0]<firstLatency[0],"measured ready/aligned reacquire faster: first="+firstLatency[0]+" reacquire="+reacquireLatency[0]);
            h.assertTrue(tracked[0] && deferred[0],"fresh aim and DEFERRED exercised in runtime");
            TacticalNpcAiMod.LOGGER.info("ALPHA latency fixture first={} reacquire={} ticks, wall-open -> native projectile; not human balance. {}",firstLatency[0],reacquireLatency[0],AiTelemetry.diagnostics(h.getLevel()));
            ServerConfig.SUPPRESSION.set(true);p.discard();n.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="alpha-02",timeoutTicks=120)
    public static void failedDestinationRecoversLocally(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());Vec3 origin=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(origin.x,origin.y,origin.z);n.setOrder(OrderState.Kind.ASSAULT,24);h.getLevel().addFreshEntity(n);
        var movement=new MovementExecutor(n.runtime());var bad=new Vector3(origin.x+8,origin.y+20,origin.z);long[] ticks={0};boolean[] failed={false},arrived={false};
        h.onEachTick(()->{movement.apply(MovementCommand.navigate(bad,MovementCommand.Locomotion.RELOCATION),++ticks[0]);failed[0]|=movement.failedDestination(ticks[0])!=null;arrived[0]|=movement.recoveryStatus()==RouteRecovery.Status.ACCEPTED;});
        h.runAtTickTime(100,()->{h.assertTrue(failed[0] && arrived[0] && n.position().distanceTo(origin)>1,"one unsafe destination chooses a real reachable local alternative within 100 ticks: status="+movement.recoveryStatus()+" pos="+n.position());movement.stop(ticks[0]);n.discard();h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="alpha-03",timeoutTicks=40)
    public static void emergencyStopsPeekWhilePathDeferred(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());Vec3 origin=h.absoluteVec(new Vec3(3.5,2,7.5));
        n.moveTo(origin.x,origin.y,origin.z);n.setOrder(OrderState.Kind.ASSAULT,24);h.getLevel().addFreshEntity(n);
        var movement=new MovementExecutor(n.runtime());Vec3 peek=h.absoluteVec(new Vec3(10.5,1,7.5)),retreat=h.absoluteVec(new Vec3(3.5,1,11.5));
        h.runAtTickTime(10,()->{movement.apply(MovementCommand.navigate(new Vector3(peek.x,peek.y,peek.z),MovementCommand.Locomotion.PEEK),h.getLevel().getGameTime());h.assertFalse(n.getNavigation().isDone(),"real peek navigation started");});
        h.runAtTickTime(11,()->{
            h.assertFalse(AiWorkBudget.of(h.getLevel()).path(n.getUUID(),4),"replacement path is budget deferred");
            movement.apply(MovementCommand.navigate(new Vector3(retreat.x,retreat.y,retreat.z),MovementCommand.Locomotion.RETREAT),h.getLevel().getGameTime());
            h.assertTrue(n.getNavigation().isDone(),"emergency cancels old peek even without a replacement path budget");
            movement.stop(h.getLevel().getGameTime());n.discard();h.succeed();
        });
    }
    private static void wall(GameTestHelper h,boolean solid) {for(int y=2;y<=6;y++)for(int z=0;z<16;z++)h.setBlock(new BlockPos(7,y,z),solid?Blocks.STONE:Blocks.AIR);}
}
