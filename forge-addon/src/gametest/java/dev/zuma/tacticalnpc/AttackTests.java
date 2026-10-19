package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.combat.AiWorkBudget;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.*;

@GameTestHolder(TacticalNpcAiMod.ID) @PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class AttackTests {
    private static UUID watched;
    private static long requests,last,minGap;
    private static int maximum;
    private static ServerLevel fixtureLevel;
    private static final List<net.minecraft.world.level.ChunkPos> FORCED=new ArrayList<>();
    @SubscribeEvent public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event){releaseChunks();}
    @SubscribeEvent public static void paths(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || watched==null)return;
        if(level==fixtureLevel && level.getEntity(watched)==null){watched=null;releaseChunks();return;}
        if(level.getEntity(watched)==null)return;
        int count=AiWorkBudget.of(level).paths();maximum=Math.max(maximum,count);
        if(count>0){long tick=level.getGameTime();if(last>=0)minGap=Math.min(minGap,tick-last);last=tick;requests+=count;}
    }
    @GameTest(template="wide_arena",batch="attack-01-idle",timeoutTicks=550)
    public static void idleAttackReallyExploresBeyondOldAnchor(GameTestHelper h) {
        begin(h);var n=npc(h,31.5,31.5,false);var origin=n.position();var points=new HashSet<Vector3>();double[] distance={0};boolean[] pause={false};
        h.onEachTick(()->{
            var d=decision(n);if(d==null)return;
            h.assertTrue(n.controller().observation()==null && d.intent()==TacticalIntent.PATROL,"idle attack invents no target");
            if(d.movement().destination()!=null)points.add(d.movement().destination());else if(!points.isEmpty())pause[0]=true;
            distance[0]=Math.max(distance[0],n.position().distanceTo(origin));
        });
        h.runAtTickTime(500,()->{h.assertTrue(points.size()>=3 && distance[0]>8 && pause[0],"actual free movement and held legs/pauses beyond ASSAULT radius 4: points="+points.size()+" distance="+distance[0]+" pause="+pause[0]);budget(h,500);finish(n);h.succeed();});
    }
    @GameTest(template="wide_arena",batch="attack-02-blocked",timeoutTicks=550)
    public static void unreachableExplorationBacksOffWithoutPathSpam(GameTestHelper h) {
        begin(h);var n=npc(h,31.5,31.5,false);Vector3[] closed={null};boolean[] pause={false},replaced={false};var origin=n.position();
        h.onEachTick(()->{
            var d=decision(n);if(d==null)return;var point=d.movement().destination();
            if(closed[0]==null && point!=null) {
                closed[0]=point;var cell=BlockPos.containing(point.x(),point.y(),point.z());
                for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<4;y++)h.getLevel().setBlockAndUpdate(cell.offset(x,y,z),Blocks.STONE.defaultBlockState());
            } else if(closed[0]!=null){pause[0]|=point==null;replaced[0]|=point!=null && !point.equals(closed[0]);}
        });
        h.runAtTickTime(500,()->{h.assertTrue(closed[0]!=null && pause[0] && replaced[0] && n.position().distanceTo(origin)>2,"unreachable exploration replaced after finite recovery/backoff");budget(h,500);finish(n);h.succeed();});
    }
    @GameTest(template="wide_arena",batch="attack-03-native",timeoutTicks=650)
    public static void approachRunsThenAdsAndNativeProjectile(GameTestHelper h) {
        begin(h);var n=npc(h,9.5,31.5,true);var origin=n.position();var p=player(h,47.5,31.5);boolean[] run={false},adsAfterRun={false},advance={false};double[] nearest={100},farthest={0};
        int bulletsBefore=ProjectileProbe.count(n.getUUID());
        h.onEachTick(()->{
            var d=decision(n);if(d==null)return;advance[0]|=d.intent()==TacticalIntent.ADVANCE;
            run[0]|=n.isSprinting() && d.movement().sprint();
            adsAfterRun[0]|=run[0] && !n.isSprinting() && n.runtime().operator().getSynAimingProgress()>.9;
            nearest[0]=Math.min(nearest[0],n.distanceTo(p));
            farthest[0]=Math.max(farthest[0],n.position().distanceTo(origin));
        });
        h.runAtTickTime(600,()->{
            h.assertTrue(advance[0] && run[0] && adsAfterRun[0] && nearest[0]<=n.controller().weaponState().effectiveRange() && farthest[0]>4,"real weapon approach outside old anchor, RUN -> native ADS: nearest="+nearest[0]+" farthest="+farthest[0]+" run="+run[0]+" ads="+adsAfterRun[0]+" decision="+decision(n));
            h.assertTrue(n.controller().shots()>0 && ProjectileProbe.count(n.getUUID())>bulletsBefore,"new native TaCZ projectile after this approach");
            var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(n.getMainHandItem());h.assertTrue(n.ammoPolicy()==TacticalNpcEntity.AmmoPolicy.FINITE && gun.getDummyAmmoAmount(n.getMainHandItem())==0 && gun.getCurrentAmmoCount(n.getMainHandItem())<25,"finite native ammunition is consumed, never replenished by movement");
            budget(h,600);p.discard();finish(n);h.succeed();
        });
    }
    @GameTest(template="wide_arena",batch="attack-04-memory",timeoutTicks=550)
    public static void hiddenRouteFrozenUntilExpiryThenFreeSearch(GameTestHelper h) {
        begin(h);var n=npc(h,11.5,31.5,true);var p=player(h,27.5,31.5);Observation[] frozen={null};boolean[] hidden={false},resumed={false};Vec3[] resumedAt={null};double[] motion={0};
        n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.INFINITE,0);
        long[] closedAt={-1};
        h.runAtTickTime(100,()->h.assertTrue(frozen[0]!=null,"genuine visual contact within 100 ticks: entityTicks="+n.tickCount+" pos="+n.position()+" decision="+decision(n)));
        h.runAtTickTime(150,()->{var at=h.absoluteVec(new Vec3(57.5,2,10.5));p.moveTo(at.x,at.y,at.z);});
        h.onEachTick(()->{
            if(n.controller()==null)return;var o=n.controller().observation();var d=decision(n);if(d==null)return;
            if(frozen[0]==null && h.getTick()>=40 && o!=null && o.visible()) {
                frozen[0]=o;closedAt[0]=h.getTick();for(int z=1;z<=62;z++)for(int y=2;y<=6;y++)h.setBlock(new BlockPos(20,y,z),Blocks.STONE);
                var at=h.absoluteVec(new Vec3(53.5,2,50.5));p.moveTo(at.x,at.y,at.z);
            }
            if(frozen[0]==null || h.getTick()<=closedAt[0]+1)return;
            if(o!=null) {
                hidden[0]=true;h.assertTrue(!o.visible() && o.position().equals(frozen[0].position()) && o.tick()==frozen[0].tick() && o.expires()==frozen[0].expires() && o.velocity().lengthSquared()==0,"no live hidden position, velocity or TTL refresh");
                h.assertTrue(d.aim().mode()!=AimCommand.Mode.DIRECT && !n.controller().allowNativeFire(),"wall blocks direct/native fire");
            } else if(h.getTick()>300 && d.intent()==TacticalIntent.PATROL && d.movement().destination()!=null) {
                resumed[0]=true;if(resumedAt[0]==null)resumedAt[0]=n.position();motion[0]=Math.max(motion[0],n.position().distanceTo(resumedAt[0]));
            }
        });
        h.runAtTickTime(500,()->{h.assertTrue(hidden[0] && resumed[0] && motion[0]>.5,"memory TTL ends and actual free exploration resumes: motion="+motion[0]);budget(h,500);p.discard();finish(n);h.succeed();});
    }
    @GameTest(template="wide_arena",batch="attack-05-obstacle",timeoutTicks=350)
    public static void legitimateMemoryPathDetoursSimpleBarrier(GameTestHelper h) {
        begin(h);var n=npc(h,11.5,31.5,true);var p=player(h,23.5,31.5);Vector3[] frozen={null};double[] side={0};boolean[] beyond={false};
        h.runAtTickTime(30,()->{
            h.assertTrue(n.controller().observation()!=null && n.controller().observation().visible(),"legitimate point before obstacle");frozen[0]=n.controller().observation().position();
            for(int z=29;z<=33;z++)for(int y=2;y<=5;y++)h.setBlock(new BlockPos(16,y,z),Blocks.STONE);
            // Move the live subject far away; path can only use its previously seen point.
            var at=h.absoluteVec(new Vec3(1000.5,2,1000.5));p.moveTo(at.x,at.y,at.z);
        });
        h.onEachTick(()->{
            if(frozen[0]==null || h.getTick()<=31 || n.controller()==null)return;var o=n.controller().observation();if(o==null)return;
            h.assertTrue(!o.visible() && o.position().equals(frozen[0]),"detour uses frozen observation only");
            side[0]=Math.max(side[0],Math.abs(n.getZ()-h.absoluteVec(new Vec3(11.5,2,31.5)).z));beyond[0]|=n.getX()>h.absoluteVec(new Vec3(17,2,31.5)).x;
        });
        h.runAtTickTime(300,()->{h.assertTrue(beyond[0] && side[0]>2,"native path makes real detour around simple barrier before memory expires: side="+side[0]+" beyond="+beyond[0]+" pos="+n.position());budget(h,300);p.discard();finish(n);h.succeed();});
    }
    private static void begin(GameTestHelper h){
        releaseChunks();ScenarioIsolation.begin(h);watched=null;requests=0;last=-1;minGap=Long.MAX_VALUE;maximum=0;
        // A loaded structure is not necessarily entity-ticking throughout its 64x64 area.
        // Force only this fixture, preserving prior tickets; production never uses this helper.
        fixtureLevel=h.getLevel();var first=new net.minecraft.world.level.ChunkPos(h.absolutePos(new BlockPos(0,1,0)));var end=new net.minecraft.world.level.ChunkPos(h.absolutePos(new BlockPos(63,1,63)));
        for(int x=first.x;x<=end.x;x++)for(int z=first.z;z<=end.z;z++) {
            var chunk=new net.minecraft.world.level.ChunkPos(x,z);if(!fixtureLevel.getForcedChunks().contains(chunk.toLong())){fixtureLevel.setChunkForced(x,z,true);FORCED.add(chunk);}
        }
    }
    private static void releaseChunks(){if(fixtureLevel!=null)for(var chunk:FORCED)fixtureLevel.setChunkForced(chunk.x,chunk.z,false);FORCED.clear();fixtureLevel=null;}
    private static TacticalNpcEntity npc(GameTestHelper h,double x,double z,boolean armed) {
        var n=NpcEntities.NPC.get().create(h.getLevel());n.setUUID(new UUID(0,606));var at=h.absoluteVec(new Vec3(x,2,z));n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.setOnGround(true);n.setOrder(OrderState.Kind.ASSAULT,4);
        if(armed){var stack=GunSupport.create(new ResourceLocation("tacz:uzi"));var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,25);gun.setBulletInBarrel(stack,false);n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,0);}
        n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);watched=n.getUUID();return n;
    }
    private static net.minecraftforge.common.util.FakePlayer player(GameTestHelper h,double x,double z) {
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"attack-contact"));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);var at=h.absoluteVec(new Vec3(x,2,z));p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);return p;
    }
    private static CombatDecision decision(TacticalNpcEntity n){return n.controller()==null?null:n.controller().decision();}
    private static void budget(GameTestHelper h,int ticks){h.assertTrue(requests>0 && maximum<=1 && minGap>=4 && requests<ticks/3,"bounded real path slots: requests="+requests+" gap="+minGap+" max="+maximum);TacticalNpcAiMod.LOGGER.info("STAGE06 path slots requests={} minGap={} maxPerTick={} horizon={} ticks; not a performance benchmark",requests,minGap,maximum,ticks);}
    private static void finish(TacticalNpcEntity n){watched=null;releaseChunks();n.discard();}
}
