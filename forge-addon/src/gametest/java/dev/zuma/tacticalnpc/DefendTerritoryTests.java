package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder(TacticalNpcAiMod.ID) @PrefixGameTestTemplate(false)
public final class DefendTerritoryTests {
    @GameTest(template="wide_arena",batch="defend-01-radius",timeoutTicks=40)
    public static void commandSetterAndNbtRadiusContract(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,10.5,10.5);
        h.runAtTickTime(5,()->{
            var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withSuppressedOutput();
            var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();String id=n.getUUID().toString();
            try {
                for(int radius:new int[]{4,64,128}) {
                    dispatcher.execute("npc mode defend "+radius+" "+id,source);var expected=n.runtime().orderState();
                    dispatcher.execute("tacticalnpc order "+id+" DEFEND_AREA "+radius,source);
                    h.assertTrue(expected.equals(n.runtime().orderState()),"both command interfaces accept "+radius);
                }
                var saved=new CompoundTag();n.save(saved);
                var restored=(TacticalNpcEntity)EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);
                h.assertTrue(restored!=null && restored.runtime().orderState().equals(n.runtime().orderState()) && saved.getCompound("tacticalnpc").getDouble("leash")==128,"NBT preserves defend 128 and anchor");restored.discard();
                for(String radius:new String[]{"3","129","NaN"})for(String command:new String[]{"npc mode defend "+radius+" "+id,"tacticalnpc order "+id+" DEFEND_AREA "+radius}) {
                    try {dispatcher.execute(command,source);h.fail("accepted invalid radius: "+command);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
                }
                for(double radius:new double[]{3,129,Double.NaN}) {
                    try {n.setOrder(OrderState.Kind.DEFEND_AREA,radius);h.fail("setter accepted "+radius);}catch(IllegalArgumentException expected) {}
                }
                h.assertTrue(n.runtime().orderState().radius()==128,"rejections leave order intact");
                for(var kind:List.of(OrderState.Kind.ASSAULT,OrderState.Kind.HOLD_POSITION,OrderState.Kind.PATROL)) {
                    try {dispatcher.execute("tacticalnpc order "+id+" "+kind+" 128",source);h.fail("expanded legacy radius "+kind);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
                }
                dispatcher.execute("npc mode attack "+id,source);h.assertTrue(n.runtime().orderState().radius()==64,"switch from defend 128 keeps legacy maximum 64");
            }catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){h.fail(ex.getMessage());}
            n.discard();h.succeed();
        });
    }

    @GameTest(template="wide_arena",batch="defend-02-detour",timeoutTicks=50)
    public static void interiorEndpointRejectsExteriorDetourAndRecovery(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,10.5,10.5);var movement=new MovementExecutor(n.runtime());
        var goal=h.absoluteVec(new Vec3(12.5,2,10.5));final boolean[] ready={false};
        for(int z=6;z<=14;z++)for(int y=1;y<=4;y++)h.setBlock(new BlockPos(11,y,z),Blocks.STONE);
        h.runAtTickTime(5,()->{
            n.setOrder(OrderState.Kind.DEFEND_AREA,4);
            var path=n.getNavigation().createPath(BlockPos.containing(goal),0);
            h.assertTrue(path!=null && path.canReach(),"vanilla really offers a route with an interior endpoint");
            h.assertTrue(!new CoverSensor(n.runtime()).withinOrderPath(path),"actual intermediate detour nodes violate territory");
            movement.apply(MovementCommand.navigate(vector(goal)),h.getLevel().getGameTime());
            h.assertTrue(n.getNavigation().isDone() && movement.recoveryStatus()==RouteRecovery.Status.RECOVERING,"executor rejects route and enters bounded recovery: navDone="+n.getNavigation().isDone()+" recovery="+movement.recoveryStatus()+" ground="+n.onGround()+" point="+new CoverSensor(n.runtime()).safeStanding(goal)+" paths="+AiWorkBudget.of(h.getLevel()).paths());
            ready[0]=true;
        });
        h.onEachTick(()->{
            if(!ready[0])return;
            movement.apply(MovementCommand.navigate(vector(goal)),h.getLevel().getGameTime());movement.guardTerritory();
            h.assertTrue(new CoverSensor(n.runtime()).territory().distanceSquared(vector(n.position()))<=16,"recovery never exits defend circle");
        });
        h.runAtTickTime(40,()->{n.discard();h.succeed();});
    }

    @GameTest(template="wide_arena",batch="defend-03-edge",timeoutTicks=160)
    public static void strafeAndRetreatCannotLeaveEdge(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,10.5,10.5);var movement=new MovementExecutor(n.runtime());
        final double[] maximum={0};final Vec3[] anchor={null};final int[] ticks={0};
        h.runAtTickTime(5,()->{
            n.setOrder(OrderState.Kind.DEFEND_AREA,4);anchor[0]=n.position();n.setPos(n.getX()+2.6,n.getY(),n.getZ());n.setYRot(0);
        });
        h.onEachTick(()->{
            if(anchor[0]==null)return;
            long tick=h.getLevel().getGameTime();boolean retreat=++ticks[0]>=70;n.setYRot(retreat?90:0);
            // Local sideways at yaw 0 points east, directly out through the edge.
            movement.apply(retreat?new MovementCommand(MovementCommand.Mode.STRAFE,null,-1,0,.65,MovementCommand.Locomotion.RETREAT):new MovementCommand(MovementCommand.Mode.STRAFE,null,0,1,.55),tick);
            movement.guardTerritory();double d=n.position().subtract(anchor[0]).horizontalDistance();maximum[0]=Math.max(maximum[0],d);
            h.assertTrue(d+n.getBbWidth()/Math.sqrt(2)<=4.001,"body remains inside during outward strafe");
        });
        h.runAtTickTime(65,()->{
            var outside=anchor[0].add(8,0,0);movement.apply(MovementCommand.navigate(vector(outside),MovementCommand.Locomotion.RETREAT),h.getLevel().getGameTime());
            h.assertTrue(n.getNavigation().isDone(),"outward retreat endpoint rejected");
        });
        h.runAtTickTime(140,()->{h.assertTrue(maximum[0]<3,"outward strafe is actually stopped");n.discard();h.succeed();});
    }

    @GameTest(template="wide_arena",batch="defend-04-return",timeoutTicks=220)
    public static void externalDisplacementReturnsThroughOutsideNodes(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,10.5,10.5);var movement=new MovementExecutor(n.runtime());final Vec3[] anchor={null};
        h.runAtTickTime(5,()->{
            n.setOrder(OrderState.Kind.DEFEND_AREA,4);anchor[0]=n.position();n.setPos(n.getX()+6.988,n.getY(),n.getZ()+.311);
            var cell=n.blockPosition();
            var arrived=new net.minecraft.world.level.pathfinder.Path(List.of(
                new net.minecraft.world.level.pathfinder.Node(cell.getX()+1,cell.getY(),cell.getZ()),
                new net.minecraft.world.level.pathfinder.Node(cell.getX(),cell.getY(),cell.getZ()),
                new net.minecraft.world.level.pathfinder.Node(cell.getX()-1,cell.getY(),cell.getZ())),BlockPos.containing(anchor[0]),true);
            arrived.setNextNodeIndex(1);
            h.assertTrue(new CoverSensor(n.runtime()).withinOrderPath(arrived),"already-arrived exterior waypoint must not block the next inward segment");
            var standing=new CoverSensor(n.runtime()).safeStanding(n.position());
            h.assertTrue(standing!=null && Math.abs(standing.y-n.getY())<.01,"displaced fixture feet have checked support");
            n.setOnGround(true); // Explicit precondition of the direct native createPath probe.
            var path=n.getNavigation().createPath(BlockPos.containing(anchor[0]),0);
            h.assertTrue(path!=null && path.canReach(),"native return path exists: self="+n.position()+" anchor="+anchor[0]+" path="+path+" ground="+n.onGround()+" ticks="+n.tickCount);
            String nodes="";for(int i=0;i<path.getNodeCount();i++)nodes+=path.getEntityPosAtNode(n,i)+" ";
            h.assertTrue(new CoverSensor(n.runtime()).withinOrderPath(path),"initial outside nodes are legal on an inward return: self="+n.position()+" nodes="+nodes);
            n.push(.6,.2,0);movement.guardTerritory();h.assertTrue(n.getDeltaMovement().x>0,"boundary guard preserves external knockback");
        });
        h.onEachTick(()->{if(anchor[0]!=null){movement.apply(MovementCommand.navigate(vector(anchor[0]),MovementCommand.Locomotion.RETREAT),h.getLevel().getGameTime());movement.guardTerritory();}});
        h.runAtTickTime(200,()->{h.assertTrue(n.position().subtract(anchor[0]).horizontalDistance()<2,"displaced NPC really walked back inside: "+n.position());n.discard();h.succeed();});
    }

    @GameTest(template="wide_arena",batch="defend-05-long-return",timeoutTicks=250)
    public static void radius128UsesLocalReturnSegments(GameTestHelper h) {
        ScenarioIsolation.begin(h);
        // Beyond the structure's ticking area: load AND tick only this test's finite corridor.
        var forced=new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int x=4;x<=164;x+=16) {
            var chunk=new net.minecraft.world.level.ChunkPos(h.absolutePos(new BlockPos(x,1,10)));
            if(!h.getLevel().getForcedChunks().contains(chunk.toLong())) {h.getLevel().setChunkForced(chunk.x,chunk.z,true);forced.add(chunk);}
        }
        for(int x=4;x<=150;x++)for(int z=8;z<=12;z++){h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<=6;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);}
        var n=npc(h,5.5,10.5);var movement=new MovementExecutor(n.runtime());final Vec3[] anchor={null};final boolean[] local={false},longLeg={false};
        h.runAtTickTime(5,()->{n.setOrder(OrderState.Kind.DEFEND_AREA,128);anchor[0]=n.position();n.setPos(n.getX()+140,n.getY(),n.getZ());});
        h.onEachTick(()->{
            if(anchor[0]==null)return;
            movement.apply(MovementCommand.navigate(vector(anchor[0]),MovementCommand.Locomotion.RETREAT),h.getLevel().getGameTime());movement.guardTerritory();
            var path=n.getNavigation().getPath();if(path!=null && !path.isDone()) {
                local[0]=true;longLeg[0]|=n.position().distanceTo(path.getEntityPosAtNode(n,path.getNodeCount()-1))>18;
            }
        });
        h.runAtTickTime(230,()->{
            forced.forEach(chunk->h.getLevel().setChunkForced(chunk.x,chunk.z,false));
            h.assertFalse(longLeg[0],"path leg stays local, despite 140-block anchor distance");
            h.assertTrue(local[0] && n.position().subtract(anchor[0]).horizontalDistance()<126,"returned into radius 128 using native short paths: pos="+n.position()+" anchor="+anchor[0]+" ticks="+n.tickCount+" localPath="+local[0]+" recovery="+movement.recoveryStatus());n.discard();h.succeed();
        });
    }

    @GameTest(template="wide_arena",batch="defend-06-slope",timeoutTicks=200)
    public static void heightAndSlopeAreIndependentFromHorizontalCircle(GameTestHelper h) {
        ScenarioIsolation.begin(h);
        for(int x=9;x<=12;x++)for(int z=9;z<=11;z++)for(int y=2;y<=x-8;y++)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        var n=npc(h,9.5,10.5);var movement=new MovementExecutor(n.runtime());final Vec3[] goal={null};
        h.runAtTickTime(5,()->{
            n.setOrder(OrderState.Kind.DEFEND_AREA,4);goal[0]=h.absoluteVec(new Vec3(11.5,4,10.5));
            h.assertTrue(new CoverSensor(n.runtime()).withinOrder(goal[0]),"height does not consume horizontal radius");
        });
        h.onEachTick(()->{if(goal[0]!=null){movement.apply(MovementCommand.navigate(vector(goal[0])),h.getLevel().getGameTime());movement.guardTerritory();}});
        h.runAtTickTime(180,()->{h.assertTrue(n.position().distanceTo(goal[0])<1 && n.getY()>h.absolutePos(new BlockPos(0,2,0)).getY(),"native navigation climbs checked slope inside circle: "+n.position());n.discard();h.succeed();});
    }
    @GameTest(template="wide_arena",batch="defend-07-external-contact",timeoutTicks=230)
    public static void visibleExternalTargetIsShotWithoutPursuit(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,10.5,10.5);n.setYRot(-90);n.setYHeadRot(-90);
        n.setOrder(OrderState.Kind.DEFEND_AREA,4);var anchor=n.position();
        var stack=dev.zuma.tacticalnpc.compat.GunSupport.create(new net.minecraft.resources.ResourceLocation("tacz:ak47"));
        var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,30);gun.setBulletInBarrel(stack,true);
        n.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,stack);n.runtime().onEquip(stack);n.addTag(dev.zuma.tacticalnpc.runtime.RuntimeRegistry.TAG);
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"defend-external"));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setInvulnerable(true);var at=h.absoluteVec(new Vec3(20.5,2,10.5));p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);
        h.onEachTick(()->h.assertTrue(n.position().subtract(anchor).horizontalDistance()+n.getBbWidth()/Math.sqrt(2)<=4.001,"real combat keeps whole body inside defend 4"));
        h.runAtTickTime(210,()->{
            h.assertTrue(n.controller()!=null && n.controller().shots()>0 && ProjectileProbe.count(n.getUUID())>0,"visible external target receives real native TaCZ fire");
            p.discard();n.discard();h.succeed();
        });
    }
    private static TacticalNpcEntity npc(GameTestHelper h,double x,double z) {
        var n=NpcEntities.NPC.get().create(h.getLevel());var p=h.absoluteVec(new Vec3(x,2,z));n.moveTo(p.x,p.y,p.z);
        // Fixture feet are exactly on the floor; chunk activation may delay the first physics tick.
        n.setOnGround(true);h.getLevel().addFreshEntity(n);return n;
    }
    private static Vector3 vector(Vec3 v){return new Vector3(v.x,v.y,v.z);}
}
