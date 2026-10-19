package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.combat.AiWorkBudget;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.*;

@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class DefendPatrolTests {
    private static final Map<UUID,Probe> PROBES=new HashMap<>();
    private static final class Probe {long requests,last=-1,minGap=Long.MAX_VALUE;int maximum;}
    /** Read-only test observer after entity AI: counts actual acquired path slots, not planned goals. */
    @SubscribeEvent public static void paths(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level))return;
        for(var entry:PROBES.entrySet())if(level.getEntity(entry.getKey())!=null) {
            var p=entry.getValue();int count=AiWorkBudget.of(level).paths();p.maximum=Math.max(p.maximum,count);
            if(count>0){long tick=level.getGameTime();if(p.last>=0)p.minGap=Math.min(p.minGap,tick-p.last);p.last=tick;p.requests+=count;}
        }
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="wide_arena",batch="defend-patrol-01-small",timeoutTicks=550)
    public static void idleRadius4ReallyMovesAndPauses(GameTestHelper h){idle(h,4,500);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="wide_arena",batch="defend-patrol-02-medium",timeoutTicks=650)
    public static void idleRadius32ReallyMovesAndPauses(GameTestHelper h){idle(h,32,600);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="wide_arena",batch="defend-patrol-03-large",timeoutTicks=650)
    public static void idleRadius128ExploresBeyondLegacyCap(GameTestHelper h){idle(h,128,600);}
    private static void idle(GameTestHelper h,int radius,int duration) {
        begin(h);var n=npc(h,radius);var anchor=n.position();var points=new HashSet<Vector3>();
        boolean[] pause={false},look={false};double[] farthest={0};float[] yaw={Float.NaN};
        h.onEachTick(()->{
            inside(h,n);farthest[0]=Math.max(farthest[0],n.position().subtract(anchor).horizontalDistance());
            var d=n.controller()==null?null:n.controller().decision();if(d==null)return;
            h.assertTrue(d.intent()==TacticalIntent.PATROL && n.controller().targetId()==null,"no invented combat contact during idle patrol");
            if(d.movement().destination()!=null)points.add(d.movement().destination());
            if(d.movement().mode()==MovementCommand.Mode.HOLD && !n.getNavigation().isDone())h.fail("pause must stop navigation");
            if(d.movement().mode()==MovementCommand.Mode.HOLD && !points.isEmpty()) {
                pause[0]=true;if(Float.isFinite(yaw[0]) && Math.abs(n.getYHeadRot()-yaw[0])>1)look[0]=true;yaw[0]=n.getYHeadRot();
            }
        });
        h.runAtTickTime(duration,()->{
            h.assertTrue(points.size()>=3 && pause[0] && look[0] && farthest[0]>(radius==4?.7:10),"real motion, different waypoints and pause/look radius="+radius+" points="+points.size()+" distance="+farthest[0]+" pause="+pause[0]+" look="+look[0]);
            budget(h,n,duration);finish(n);h.succeed();
        });
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="wide_arena",batch="defend-patrol-04-blocked",timeoutTicks=550)
    public static void blockedWaypointBacksOffAndSelectsAnother(GameTestHelper h) {
        begin(h);var n=npc(h,32);final Vector3[] blocked={null};boolean[] replaced={false},pause={false};var origin=n.position();
        h.onEachTick(()->{
            inside(h,n);var d=n.controller()==null?null:n.controller().decision();if(d==null)return;
            if(blocked[0]==null && d.movement().destination()!=null) {
                blocked[0]=d.movement().destination();var cell=BlockPos.containing(blocked[0].x(),blocked[0].y(),blocked[0].z());
                for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<4;y++)h.getLevel().setBlockAndUpdate(cell.offset(x,y,z),Blocks.STONE.defaultBlockState());
            } else if(blocked[0]!=null) {
                pause[0]|=d.movement().mode()==MovementCommand.Mode.HOLD;
                replaced[0]|=d.movement().destination()!=null && !d.movement().destination().equals(blocked[0]);
            }
        });
        h.runAtTickTime(500,()->{
            h.assertTrue(blocked[0]!=null && replaced[0] && pause[0] && n.position().distanceTo(origin)>1,"closed original waypoint replaced after bounded recovery/backoff with real motion");
            budget(h,n,500);finish(n);h.succeed();
        });
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="wide_arena",batch="defend-patrol-05-combat",timeoutTicks=550)
    public static void combatAndLiveMemoryInterruptThenPatrolResumes(GameTestHelper h) {
        begin(h);var n=npc(h,4);var anchor=n.position();
        var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
        gun.setCurrentAmmoCount(stack,30);gun.setBulletInBarrel(stack,true);n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.runtime().onEquip(stack);
        n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,0);
        final net.minecraftforge.common.util.FakePlayer[] enemy={null};boolean[] before={false},memory={false},after={false};final Vec3[] resumedAt={null};double[] resumedMotion={0};
        h.runAtTickTime(80,()->{
            var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"patrol-contact"));
            p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);p.moveTo(anchor.x+14,anchor.y,anchor.z);h.getLevel().addNewPlayer(p);enemy[0]=p;
            n.setYRot(-90);n.setYHeadRot(-90);
        });
        h.runAtTickTime(180,()->{
            h.assertTrue(n.controller().shots()>0 && ProjectileProbe.count(n.getUUID())>0,"real native fire interrupts idle patrol against an exterior target");
            var wall=BlockPos.containing(anchor.add(7,0,0));for(int z=-6;z<=6;z++)for(int y=0;y<5;y++)h.getLevel().setBlockAndUpdate(wall.offset(0,y,z),Blocks.STONE.defaultBlockState());
        });
        h.runAtTickTime(260,()->{enemy[0].discard();enemy[0]=null;});
        h.onEachTick(()->{
            inside(h,n);var c=n.controller();if(c==null || c.decision()==null)return;var d=c.decision();
            if(h.getTick()<80)before[0]|=d.intent()==TacticalIntent.PATROL && d.movement().mode()==MovementCommand.Mode.NAVIGATE;
            if(h.getTick()>195 && h.getTick()<250 && c.observation()!=null && !c.observation().visible()) {
                memory[0]=true;h.assertTrue(d.intent()!=TacticalIntent.PATROL,"live hidden-contact memory must keep search/combat, not resume idle patrol");
            }
            if(h.getTick()>280 && c.observation()==null && d.intent()==TacticalIntent.PATROL && d.movement().mode()==MovementCommand.Mode.NAVIGATE) {
                after[0]=true;if(resumedAt[0]==null)resumedAt[0]=n.position();else resumedMotion[0]=Math.max(resumedMotion[0],n.position().distanceTo(resumedAt[0]));
            }
        });
        h.runAtTickTime(500,()->{
            h.assertTrue(before[0] && memory[0] && after[0] && resumedMotion[0]>.5,"idle -> actual combat -> live-memory search -> actual idle patrol");
            h.assertTrue(n.ammoPolicy()==TacticalNpcEntity.AmmoPolicy.FINITE && gun.getDummyAmmoAmount(stack)==0,"patrol transitions do not replenish native finite reserve");
            budget(h,n,500);finish(n);h.succeed();
        });
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="wide_arena",batch="defend-patrol-06-save",timeoutTicks=550)
    public static void persistedDefendRebuildsPatrolAfterEntityLoad(GameTestHelper h) {
        begin(h);var original=npc(h,32,UUID.fromString("2ca0999a-f025-4308-88ba-5e2ec2f39aa8"));final CompoundTag saved=new CompoundTag();final TacticalNpcEntity[] loaded={null};
        final Vec3[] position={null};final OrderState[] order={null};var points=new HashSet<Vector3>();double[] distance={0};
        h.runAtTickTime(180,()->{
            h.assertTrue(original.position().distanceTo(h.absoluteVec(new Vec3(31.5,2,31.5)))>1,"original actually patrolled before save");
            original.save(saved);order[0]=original.runtime().orderState();position[0]=original.position();original.discard();
            h.assertTrue(original.controller()==null,"unload/remove releases original controller and route state");
        });
        h.runAtTickTime(182,()->{
            loaded[0]=(TacticalNpcEntity)EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);
            h.assertTrue(loaded[0]!=null && order[0].equals(loaded[0].runtime().orderState()) && saved.getCompound("tacticalnpc").getInt("schema")==3,"schema 3 retains persisted order/anchor/radius");
            loaded[0].setOnGround(true);h.assertTrue(h.getLevel().addFreshEntity(loaded[0]),"saved entity re-added with original UUID");
        });
        h.onEachTick(()->{
            var n=loaded[0];if(n==null)return;inside(h,n);distance[0]=Math.max(distance[0],n.position().distanceTo(position[0]));
            if(n.controller()!=null && n.controller().decision()!=null && n.controller().decision().movement().destination()!=null)points.add(n.controller().decision().movement().destination());
        });
        h.runAtTickTime(500,()->{
            String evidence="points="+points+" distance="+distance[0]+" position="+loaded[0].position()+" neutral="+loaded[0].neutral()+" entityTicks="+loaded[0].tickCount+" chunkTicking="+h.getLevel().isPositionEntityTicking(loaded[0].blockPosition())+" noAI="+loaded[0].isNoAi()+" controller="+(loaded[0].controller()==null?"null":loaded[0].controller().decision())+" navigationDone="+loaded[0].getNavigation().isDone();
            TacticalNpcAiMod.LOGGER.info("STAGE07 persisted patrol diagnostic {}",evidence);
            h.assertTrue(points.size()>=2 && distance[0]>2,"new controller rebuilds actual patrol after load: "+evidence);budget(h,loaded[0],500);finish(loaded[0]);h.succeed();
        });
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="wide_arena",batch="defend-patrol-07-legacy",timeoutTicks=550)
    public static void independentPatrolOrderKeepsOldCardinalRoute(GameTestHelper h) {
        begin(h);var n=npc(h,32);n.setOrder(OrderState.Kind.PATROL,32);var origin=n.position();var points=new HashSet<Vector3>();double[] distance={0};
        h.onEachTick(()->{
            var d=n.controller()==null?null:n.controller().decision();if(d==null || d.movement().destination()==null)return;
            var offset=d.movement().destination().minus(vector(origin));
            h.assertTrue(Math.abs(offset.length()-10)<.001 && (Math.abs(offset.x())<.001 || Math.abs(offset.z())<.001),"separate PATROL keeps its old ten-block cardinal route");
            points.add(d.movement().destination());distance[0]=Math.max(distance[0],n.position().distanceTo(origin));
        });
        h.runAtTickTime(500,()->{h.assertTrue(n.order().equals("PATROL") && points.size()>=3 && distance[0]>3,"standalone PATROL still really moves through different legs");budget(h,n,500);finish(n);h.succeed();});
    }
    private static void begin(GameTestHelper h){ScenarioIsolation.begin(h);PROBES.clear();}
    private static TacticalNpcEntity npc(GameTestHelper h,int radius) {
        return npc(h,radius,null);
    }
    private static TacticalNpcEntity npc(GameTestHelper h,int radius,UUID id) {
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(31.5,2,31.5));n.moveTo(at.x,at.y,at.z);n.setOnGround(true);
        if(id!=null)n.setUUID(id);
        n.setOrder(OrderState.Kind.DEFEND_AREA,radius);n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);PROBES.put(n.getUUID(),new Probe());return n;
    }
    private static void inside(GameTestHelper h,TacticalNpcEntity n) {
        var order=n.runtime().orderState();double x=n.getX()-order.anchor().x(),z=n.getZ()-order.anchor().z();
        h.assertTrue(Math.sqrt(x*x+z*z)+n.getBbWidth()/Math.sqrt(2)<=order.radius()+.001,"whole body remains inside defend");
    }
    private static void budget(GameTestHelper h,TacticalNpcEntity n,int ticks) {
        var p=PROBES.get(n.getUUID());h.assertTrue(p!=null && p.requests>0 && p.maximum<=1 && p.minGap>=4 && p.requests<ticks/3,"bounded real path slots: requests="+(p==null?-1:p.requests)+" minGap="+(p==null?-1:p.minGap)+" maxPerTick="+(p==null?-1:p.maximum));
        TacticalNpcAiMod.LOGGER.info("STAGE05 path slots owner={} requests={} minGap={} maxPerTick={} horizon={} ticks; not a performance benchmark",n.getUUID(),p.requests,p.minGap,p.maximum,ticks);
    }
    private static void finish(TacticalNpcEntity n){PROBES.remove(n.getUUID());n.discard();}
    private static Vector3 vector(Vec3 v){return new Vector3(v.x,v.y,v.z);}
}
