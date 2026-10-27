package dev.zuma.tacticalnpc;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class TransparentSightTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="sight-01-native",timeoutTicks=110)
    public static void firstAcquisitionAndNativeBurstThroughGlassThenStoneStopsRounds(GameTestHelper h) {
        begin(h);wall(h,7,Blocks.GLASS);var n=npc(h,true,"b93r");var p=player(h,new Vec3(11.5,2,7.5));
        IGun.getIGunOrNull(n.getMainHandItem()).setFireMode(n.getMainHandItem(),FireMode.BURST);
        int[] first={-1},rounds={0};Vector3[] seen={null};
        h.onEachTick(()->{
            if(first[0]<0 && ProjectileProbe.count(n.getUUID())>0) {
                first[0]=(int)h.getTick();rounds[0]=ProjectileProbe.count(n.getUUID());
                var o=n.controller().observation();h.assertTrue(o!=null && o.visible() && o.source()==Observation.Source.VISION,"first contact acquired through glass without earlier open sight");seen[0]=o.position();
                h.assertTrue(n.controller().weaponState().rounds()>3,"wall appears during an armed native burst, before ammo exhaustion");
                // Isolate the barrel-gate assertion from rays already spent in this tick by cover work.
                AiWorkBudget.remove(h.getLevel());h.assertTrue(n.controller().allowNativeFire(),"normal direction through glass passes the actual native gate");
                float yaw=n.getYHeadRot(),pitch=n.getXRot();
                for(int y=2;y<=5;y++)put(h,new BlockPos(7,y,9),Blocks.STONE.defaultBlockState());
                n.setYHeadRot(-65);n.setXRot(0);
                h.assertFalse(n.controller().allowNativeFire(),"opaque block on actual barrel direction blocks despite a visible target through glass");
                n.setYHeadRot(yaw);n.setXRot(pitch);
                wall(h,8,Blocks.STONE);p.setPos(p.getX(),p.getY(),p.getZ()+1);
            }
            if(first[0]>=0 && h.getTick()==first[0]+16) {
                h.assertTrue(ProjectileProbe.count(n.getUUID())==rounds[0],"new stone stops uncreated native BURST rounds; in-flight rounds are untouched");
                var o=n.controller().observation();h.assertTrue(o!=null && !o.visible() && o.position().equals(seen[0]) && o.velocity().lengthSquared()==0,"opaque wall freezes hidden position and velocity");
                System.out.println("TRANSPARENT_NATIVE firstTick="+first[0]+" actualProjectiles="+rounds[0]+" hits=NOT_TESTED");n.discard();p.discard();h.succeed();
            }
        });
        h.runAtTickTime(100,()->h.assertTrue(first[0]>=0,"must create a genuine native projectile through glass: "+n.controller().fireDiagnostics()));
    }

    private record Case(String name,BlockState state,boolean vertical,double height,VisibilitySensor.Los expected) {}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="sight-02-types",timeoutTicks=70)
    public static void explicitDefaultsDoorsThinBlocksAndOrdinaryShapeGeometry(GameTestHelper h) {
        begin(h);var n=npc(h,false,null);
        h.runAtTickTime(5,()->{
            for(var color:DyeColor.values())for(String suffix:List.of("_stained_glass","_stained_glass_pane")) {
                var block=BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft",color.getName()+suffix));
                h.assertTrue(NpcSightBlocks.classify(block.defaultBlockState())==NpcSightBlocks.Kind.TRANSPARENT,"loaded policy tag includes "+color+suffix);
            }
        });
        var cases=List.of(
            new Case("glass",Blocks.GLASS.defaultBlockState(),false,3.5,VisibilitySensor.Los.CLEAR),
            new Case("tinted glass",Blocks.TINTED_GLASS.defaultBlockState(),false,3.5,VisibilitySensor.Los.CLEAR),
            new Case("pane",Blocks.BLUE_STAINED_GLASS_PANE.defaultBlockState(),false,3.5,VisibilitySensor.Los.CLEAR),
            new Case("oak door closed",Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.WEST),false,3.5,VisibilitySensor.Los.CLEAR),
            new Case("oak door upper open",Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.WEST).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER).setValue(DoorBlock.OPEN,true),false,3.5,VisibilitySensor.Los.CLEAR),
            new Case("oak trapdoor closed",Blocks.OAK_TRAPDOOR.defaultBlockState(),true,0,VisibilitySensor.Los.CLEAR),
            new Case("oak trapdoor open",Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN,true).setValue(TrapDoorBlock.FACING,Direction.WEST),false,3.5,VisibilitySensor.Los.CLEAR),
            new Case("spruce door closed",Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.WEST),false,3.5,VisibilitySensor.Los.BLOCKED),
            new Case("spruce open doorway",Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.WEST).setValue(DoorBlock.OPEN,true),false,3.5,VisibilitySensor.Los.CLEAR),
            new Case("spruce trapdoor",Blocks.SPRUCE_TRAPDOOR.defaultBlockState(),true,0,VisibilitySensor.Los.BLOCKED),
            new Case("fence",Blocks.OAK_FENCE.defaultBlockState(),false,3.5,VisibilitySensor.Los.CLEAR),
            new Case("nether fence",Blocks.NETHER_BRICK_FENCE.defaultBlockState(),false,3.5,VisibilitySensor.Los.CLEAR),
            new Case("ladder",Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.WEST),false,3.5,VisibilitySensor.Los.CLEAR),
            new Case("rail",Blocks.RAIL.defaultBlockState(),false,3.02,VisibilitySensor.Los.CLEAR),
            new Case("powered rail",Blocks.POWERED_RAIL.defaultBlockState(),false,3.02,VisibilitySensor.Los.CLEAR),
            new Case("standing sign",Blocks.OAK_SIGN.defaultBlockState(),false,3.5,VisibilitySensor.Los.CLEAR),
            new Case("hanging sign",Blocks.BAMBOO_HANGING_SIGN.defaultBlockState(),false,3.5,VisibilitySensor.Los.CLEAR),
            new Case("stone",Blocks.STONE.defaultBlockState(),false,3.5,VisibilitySensor.Los.BLOCKED),
            new Case("slab occupied half",Blocks.STONE_SLAB.defaultBlockState(),false,3.25,VisibilitySensor.Los.BLOCKED),
            new Case("slab actual gap",Blocks.STONE_SLAB.defaultBlockState(),false,3.75,VisibilitySensor.Los.CLEAR));
        for(int i=0;i<cases.size();i++) {
            var c=cases.get(i);h.runAtTickTime(6+i,()->{
                put(h,new BlockPos(7,3,7),c.state());
                var start=h.absoluteVec(c.vertical()?new Vec3(7.5,2.5,7.5):new Vec3(3.5,c.height(),7.5));
                var end=h.absoluteVec(c.vertical()?new Vec3(7.5,5.5,7.5):new Vec3(11.5,c.height(),7.5));
                var budget=AiWorkBudget.of(h.getLevel());int before=budget.rays();var result=NpcSightRay.trace(n,start,end,true);
                h.assertTrue(result.los()==c.expected(),c.name()+" expected "+c.expected()+" got "+result);
                h.assertTrue(budget.rays()==before+1 && result.visited()<=1024,"one token and bounded single traversal: "+c.name());
            });
        }
        h.runAtTickTime(30,()->{n.discard();h.succeed();});
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="sight-03-depth",timeoutTicks=50)
    public static void glassContinuesToWallAndLeafCountSurvivesAirGap(GameTestHelper h) {
        begin(h);var n=npc(h,false,null);var a=h.absoluteVec(new Vec3(3.5,3.5,7.5));var b=h.absoluteVec(new Vec3(13.5,3.5,7.5));
        h.runAtTickTime(5,()->{
            put(h,new BlockPos(6,3,7),Blocks.GLASS.defaultBlockState());put(h,new BlockPos(10,3,7),Blocks.STONE.defaultBlockState());
            var stone=NpcSightRay.trace(n,a,b,true);h.assertTrue(stone.reason()==NpcSightRay.Reason.BLOCK && stone.block().equals(h.absolutePos(new BlockPos(10,3,7))),"stone behind glass is the actual early blocker: "+stone);
            put(h,new BlockPos(10,3,7),Blocks.AIR.defaultBlockState());put(h,new BlockPos(7,3,7),Blocks.OAK_LEAVES.defaultBlockState());
            h.assertTrue(NpcSightRay.trace(n,a,b,true).los()==VisibilitySensor.Los.CLEAR,"one leaf block plus glass admits sight");
            put(h,new BlockPos(10,3,7),Blocks.BIRCH_LEAVES.defaultBlockState());
            var leaves=NpcSightRay.trace(n,a,b,true);h.assertTrue(leaves.reason()==NpcSightRay.Reason.LEAF_LIMIT && leaves.leaves()==2 && leaves.block().equals(h.absolutePos(new BlockPos(10,3,7))),"second different leaves block after air gap blocks: "+leaves);
            h.assertTrue(NpcSightRay.trace(n,b,a,true).reason()==NpcSightRay.Reason.LEAF_LIMIT,"reverse traversal counts the same two leaf cells");
            put(h,new BlockPos(7,3,7),Blocks.AIR.defaultBlockState());h.assertTrue(NpcSightRay.trace(n,a,b,true).los()==VisibilitySensor.Los.CLEAR,"leaf count is per ray, never retained from a previous query");
            h.assertTrue(NpcSightRay.trace(n,a,a.add(513,0,0),true).reason()==NpcSightRay.Reason.RANGE,"distance cap fails deferred before traversal");n.discard();h.succeed();
        });
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="sight-04-cover",timeoutTicks=50)
    public static void glassAloneIsNotCoverButStoneBehindItIs(GameTestHelper h) {
        begin(h);wall(h,7,Blocks.GLASS);var n=npc(h,false,null);var sensor=new CoverSensor(n.runtime());
        var feet=vector(h.absoluteVec(new Vec3(3.5,2,7.5)));var target=vector(h.absoluteVec(new Vec3(11.5,3.3,7.5)));
        h.runAtTickTime(5,()->{
            seedCover(sensor,feet,h.getLevel().getGameTime());h.assertTrue(sensor.sample(known(target,h),h.getLevel().getGameTime(),false).isEmpty(),"glass alone cannot validate real cached protected position");
        });
        h.runAtTickTime(6,()->{
            wall(h,8,Blocks.STONE);seedCover(sensor,feet,h.getLevel().getGameTime());
            h.assertTrue(!sensor.sample(known(target,h),h.getLevel().getGameTime(),false).isEmpty(),"six real cover rays find opaque wall behind glass");n.discard();h.succeed();
        });
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="sight-05-safety",timeoutTicks=150)
    public static void glassDoesNotBypassNeutralOrAlliedBodySafety(GameTestHelper h) {
        begin(h);wall(h,7,Blocks.GLASS);var n=npc(h,true,"ak47");var p=player(h,new Vec3(11.5,2,7.5));
        var at=h.absoluteVec(new Vec3(5.5,2,7.5));var body=new ArmorStand(h.getLevel(),at.x,at.y,at.z);body.setNoGravity(true);h.getLevel().addFreshEntity(body);
        var ally=NpcEntities.NPC.get().create(h.getLevel());ally.moveTo(at.x,at.y,at.z);ally.setNoAi(true);
        h.runAtTickTime(35,()->{
            h.assertTrue(n.controller().observation()!=null && n.controller().observation().visible() && ProjectileProbe.count(n.getUUID())==0,"transparent visual contact still cannot shoot a protected neutral body");
            body.discard();n.setFaction("sight-safety");ally.setFaction("sight-safety");h.getLevel().addFreshEntity(ally);
        });
        h.runAtTickTime(65,()->{
            h.assertTrue(n.controller().observation()!=null && n.controller().observation().visible() && ProjectileProbe.count(n.getUUID())==0,"actual allied NPC in corridor blocks through-glass fire");ally.discard();
        });
        h.runAtTickTime(120,()->{h.assertTrue(ProjectileProbe.count(n.getUUID())>0,"native fire starts once protected corridor clears");n.discard();p.discard();h.succeed();});
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="sight-06-suppression",timeoutTicks=160)
    public static void suppressionUsesGlassRuleWithoutRefreshingHiddenTarget(GameTestHelper h) {
        begin(h);wall(h,7,Blocks.GLASS);var n=npc(h,true,"ak47");var p=player(h,new Vec3(11.5,2,7.5));
        int[] before={0},after={0};Vector3[] seen={null};
        h.runAtTickTime(35,()->{
            var o=n.controller().observation();h.assertTrue(o!=null && o.visible(),"legitimate visual contact through glass precedes suppression");seen[0]=o.position();before[0]=ProjectileProbe.count(n.getUUID());
            for(int x=1;x<15;x++)for(int y=2;y<7;y++)put(h,new BlockPos(x,y,10),Blocks.STONE.defaultBlockState());p.setPos(p.getX(),p.getY(),p.getZ()+6);
        });
        h.runAtTickTime(65,()->{
            var o=n.controller().observation();after[0]=ProjectileProbe.count(n.getUUID());
            h.assertTrue(o!=null && !o.visible() && o.position().equals(seen[0]) && o.velocity().lengthSquared()==0,"suppression retains frozen known point, never hidden live position");
            h.assertTrue(after[0]>before[0] && after[0]-before[0]<=48,"actual bounded suppression rounds pass AI glass permission");
            h.assertTrue(n.controller().weaponState().rounds()>0,"opaque stop is exercised before ammo exhaustion");wall(h,8,Blocks.STONE);
        });
        h.runAtTickTime(90,()->{h.assertTrue(ProjectileProbe.count(n.getUUID())==after[0],"opaque wall behind glass blocks all subsequent native suppression rounds");n.discard();p.discard();h.succeed();});
    }

    private static void begin(GameTestHelper h) {
        ScenarioIsolation.begin(h);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
            put(h,new BlockPos(x,1,z),Blocks.STONE.defaultBlockState());
            for(int y=2;y<8;y++)put(h,new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
    }
    private static void put(GameTestHelper h,BlockPos pos,BlockState state){h.getLevel().setBlock(h.absolutePos(pos),state,2);}
    private static void wall(GameTestHelper h,int x,Block block){for(int y=2;y<7;y++)for(int z=0;z<16;z++)put(h,new BlockPos(x,y,z),block.defaultBlockState());}
    private static TacticalNpcEntity npc(GameTestHelper h,boolean active,String gunId) {
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.setOrder(OrderState.Kind.HOLD_POSITION,24);
        n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);n.setNoAi(!active);
        if(gunId!=null){var stack=GunSupport.create(new ResourceLocation("tacz",gunId));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,gunId.equals("b93r")?20:30);gun.setBulletInBarrel(stack,true);n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,0);}
        if(active)n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);return n;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,Vec3 local) {
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"sight-test"));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);var at=h.absoluteVec(local);p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);return p;
    }
    private static Vector3 vector(Vec3 v){return new Vector3(v.x,v.y,v.z);}
    private static Observation known(Vector3 point,GameTestHelper h){long tick=h.getLevel().getGameTime();return new Observation(new UUID(29,1),point,new Vector3(0,0,0),tick,tick+100,Observation.Source.VISION,1,0,true);}
    private static void seedCover(CoverSensor sensor,Vector3 point,long tick){try{var f=CoverSensor.class.getDeclaredField("selected");f.setAccessible(true);f.set(sensor,new CoverOption(point,null,tick+100));}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
}
