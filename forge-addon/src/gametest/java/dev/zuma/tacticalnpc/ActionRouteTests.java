package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.combat.*;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class ActionRouteTests {
    private static final Map<GameTestHelper,BlockPos> BASE=new WeakHashMap<>();
    private static final List<ChunkPos> CHUNKS=new ArrayList<>();
    private static net.minecraft.server.level.ServerLevel sceneLevel;
    private static int sceneIndex;
    public static void releaseSceneChunks(){if(sceneLevel!=null)for(var c:CHUNKS)sceneLevel.setChunkForced(c.x,c.z,false);CHUNKS.clear();sceneLevel=null;}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent e){releaseSceneChunks();}
    private static BlockPos position(GameTestHelper h,BlockPos p){return BASE.get(h).offset(p);}
    private static Vec3 absolute(GameTestHelper h,Vec3 p){var b=BASE.get(h);return p.add(b.getX(),b.getY(),b.getZ());}
    private static void block(GameTestHelper h,BlockPos p,Block b){block(h,p,b.defaultBlockState());}
    private static void block(GameTestHelper h,BlockPos p,net.minecraft.world.level.block.state.BlockState s){h.getLevel().setBlockAndUpdate(position(h,p),s);}
    private static net.minecraft.world.level.block.state.BlockState state(GameTestHelper h,BlockPos p){return h.getLevel().getBlockState(position(h,p));}
    private static void success(GameTestHelper h){releaseSceneChunks();h.succeed();}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="actions-13-door-cancel",timeoutTicks=300)
    public static void orderCancellationClearsDoorThroat(GameTestHelper h){
        begin(h);corridor(h);door(h,false);var n=npc(h,3.5,2,7.5);var m=new MovementExecutor(n.runtime());
        boolean[] cancelled={false};int[] last={-1};double doorX=position(h,new BlockPos(7,2,7)).getX();
        h.onEachTick(()->{
            if(n.tickCount<5 || last[0]==n.tickCount)return;last[0]=n.tickCount;
            if(!cancelled[0] && n.getX()>doorX+.05 && n.getX()<doorX+.95){
                cancelled[0]=true;m.orderChanged(h.getLevel().getGameTime());
                h.assertTrue(m.actionActive() && TransitionResources.of(h.getLevel()).size()==1,"door throat keeps its controller and lease during cancellation");
            }
            m.apply(cancelled[0]?MovementCommand.hold():MovementCommand.navigate(v(absolute(h,new Vec3(12.5,2,7.5)))),h.getLevel().getGameTime());
            if(cancelled[0] && !m.actionActive()){
                h.assertTrue(n.onGround() && n.getX()-n.getBbWidth()/2>doorX+1,"whole body clears the door after cancellation: "+n.position()+" "+m.actionDiagnostics());
                h.assertTrue(n.getX()<doorX+2 && TransitionResources.of(h.getLevel()).size()==0,"remaining walk is cancelled and lease released at exit");
                receipt(h,"door cancellation",m,n);n.discard();success(h);
            }
        });
        h.runAtTickTime(285,()->h.assertTrue(n.isRemoved(),"door cancellation exercised="+cancelled[0]+" pos="+n.position()+" ticks="+n.tickCount+" "+m.actionDiagnostics()));
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="actions-11-cancel",timeoutTicks=300)
    public static void orderCancellationFinishesCurrentLadderExit(GameTestHelper h){
        begin(h);tower(h);var n=npc(h,3.5,2,7.5);var m=new MovementExecutor(n.runtime());boolean[] cancelled={false};int[] last={-1};
        h.onEachTick(()->{
            if(n.tickCount<5 || last[0]==n.tickCount)return;last[0]=n.tickCount;
            if(!cancelled[0] && n.getY()>position(h,new BlockPos(0,4,0)).getY()){
                cancelled[0]=true;m.orderChanged(h.getLevel().getGameTime());
                h.assertTrue(m.actionActive(),"incomplete ladder exit remains owned during safe cancellation");
            }
            m.apply(cancelled[0]?MovementCommand.hold():MovementCommand.navigate(v(absolute(h,new Vec3(12.5,8,7.5)))),h.getLevel().getGameTime());
            if(cancelled[0] && !m.actionActive()){
                h.assertTrue(n.onGround() && n.getY()>=position(h,new BlockPos(0,8,0)).getY()-.1 && m.actionDiagnostics().contains("climbed=1"),"cancellation completes actual upper landing: "+n.position()+" "+m.actionDiagnostics());
                h.assertTrue(TransitionResources.of(h.getLevel()).size()==0,"cancelled resource released");n.discard();success(h);
            }
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="actions-12-unload",timeoutTicks=240)
    public static void unloadReleasesLadderLease(GameTestHelper h){
        begin(h);tower(h);var n=npc(h,3.5,2,7.5);var m=new MovementExecutor(n.runtime());int[] last={-1};
        h.onEachTick(()->{
            if(n.tickCount<5 || last[0]==n.tickCount)return;last[0]=n.tickCount;
            m.apply(MovementCommand.navigate(v(absolute(h,new Vec3(12.5,8,7.5)))),h.getLevel().getGameTime());
            if(n.onClimbable() && !n.onGround()){
                h.assertTrue(TransitionResources.of(h.getLevel()).size()==1,"live ladder lease acquired");
                n.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
                h.assertTrue(TransitionResources.of(h.getLevel()).size()==0,"Forge entity-leave releases lease immediately");success(h);
            }
        });
        h.runAtTickTime(225,()->h.assertTrue(n.isRemoved(),"unload precondition: entityTicks="+n.tickCount+" pos="+n.position()+" ground="+n.onGround()+" climbable="+n.onClimbable()+" "+m.actionDiagnostics()));
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="actions-10-combat",timeoutTicks=620)
    public static void knownTargetDoorLadderAndNativeCombat(GameTestHelper h){
        begin(h);tower(h);var n=npc(h,3.5,2,7.5);
        dev.zuma.tacticalnpc.config.ServerConfig.DEBUG.set(true);
        n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var stack=dev.zuma.tacticalnpc.compat.GunSupport.create(new net.minecraft.resources.ResourceLocation("tacz:glock_17"));
        var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,17);gun.setBulletInBarrel(stack,true);
        n.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,stack);n.runtime().onEquip(stack);n.setYRot(-90);n.setYHeadRot(-90);n.addTag(dev.zuma.tacticalnpc.runtime.RuntimeRegistry.TAG);
        var player=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"route-target")){
            @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){return false;}
        };
        var p=absolute(h,new Vec3(8.5,8,7.5));player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.moveTo(p.x,p.y,p.z);h.getLevel().addNewPlayer(player);
        boolean[] closed={false},arrived={false};int[] prior={0};long[] arrivalTick={-1};
        h.onEachTick(()->{
            if(n.controller()==null)return;var o=n.controller().observation();
            if(!closed[0] && h.getTick()>=30 && o!=null && o.visible()){
                closed[0]=true;dev.zuma.tacticalnpc.config.ServerConfig.MEMORY.set(600);dev.zuma.tacticalnpc.config.ServerConfig.SUPPRESSION.set(false);
                for(int y=2;y<=11;y++)for(int z=0;z<16;z++)if(z!=7 || y>3)block(h,new BlockPos(5,y,z),Blocks.STONE);
                putDoor(h,new BlockPos(5,2,7),false);
                var hidden=absolute(h,new Vec3(14.5,8,7.5));player.moveTo(hidden.x,hidden.y,hidden.z);
                n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(.25);
            }
            if(closed[0] && !arrived[0] && n.onGround() && n.getY()>=position(h,new BlockPos(0,8,0)).getY()-.1 && n.controller().movementDiagnostics().contains("climbed=1")){
                arrived[0]=true;arrivalTick[0]=h.getTick();prior[0]=ProjectileProbe.count(n.getUUID());
                h.assertTrue(state(h,new BlockPos(5,2,7)).getValue(DoorBlock.OPEN),"real door interaction precedes upper arrival");
            }
            if(arrived[0] && ProjectileProbe.count(n.getUUID())>prior[0]){
                TacticalNpcAiMod.LOGGER.info("ACTION_COMBAT arrivalTick={} shotTick={} actualProjectiles={} {}",arrivalTick[0],h.getTick(),ProjectileProbe.count(n.getUUID())-prior[0],n.controller().movementDiagnostics());
                n.discard();player.discard();success(h);
            }
        });
        h.runAtTickTime(100,()->h.assertTrue(closed[0],"legitimate visual observation before occlusion"));
        for(int t:new int[]{100,200,300,450})h.runAtTickTime(t,()->TacticalNpcAiMod.LOGGER.info("ACTION_COMBAT_PROGRESS tick={} npc={} target={} alive={} arrived={} actualBullets={} observation={} {}",h.getTick(),n.position(),player.position(),player.isAlive(),arrived[0],ProjectileProbe.count(n.getUUID()),n.controller()==null?null:n.controller().observation(),n.controller()==null?"no controller":n.controller().movementDiagnostics()));
        h.runAtTickTime(600,()->h.assertTrue(arrived[0] && n.isRemoved(),"arrival and actual native shot: pos="+n.position()+" decision="+n.controller().decision()+" "+n.controller().movementDiagnostics()));
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="actions-01-door-east",timeoutTicks=240)
    public static void woodenDoorFromWest(GameTestHelper h){doorRun(h,false,false);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="actions-02-door-west",timeoutTicks=240)
    public static void woodenDoorFromEast(GameTestHelper h){doorRun(h,true,false);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="actions-03-reclose",timeoutTicks=420)
    public static void closedAgainBeforeNextNpc(GameTestHelper h){doorRun(h,false,true);}
    private static void doorRun(GameTestHelper h,boolean reverse,boolean reclose){
        begin(h);corridor(h);door(h,false);var n=npc(h,reverse?12.5:3.5,2,7.5);var movement=new MovementExecutor(n.runtime());
        var exit=absolute(h,new Vec3(reverse?3.5:12.5,2,7.5));int[] last={-1};boolean[] first={false};
        h.onEachTick(()->{
            if(n.tickCount<5 || n.tickCount==last[0])return;last[0]=n.tickCount;
            movement.apply(MovementCommand.navigate(v(exit)),h.getLevel().getGameTime());
            if(n.position().distanceTo(exit)<.65 && !movement.actionActive()){
                h.assertTrue(state(h,new BlockPos(7,2,7)).getValue(DoorBlock.OPEN) && state(h,new BlockPos(7,3,7)).getValue(DoorBlock.OPEN),"both door halves opened by actual action");
                if(!reclose || first[0]){receipt(h,"door",movement,n);movement.stop(h.getLevel().getGameTime());n.discard();success(h);return;}
                first[0]=true;movement.stop(h.getLevel().getGameTime());door(h,false);
                // Second actor starts on the other side; no action state is shared with its predecessor.
                n.discard();var next=npc(h,12.5,2,7.5);drive(h,next,absolute(h,new Vec3(3.5,2,7.5)),"reclosed door",true);
            }
        });
        h.runAtTickTime(225,()->h.assertTrue(n.isRemoved(),"door arrival: entityTicks="+n.tickCount+" ground="+n.onGround()+" pos="+n.position()+" exit="+exit+" nav="+n.getNavigation().isDone()+" "+movement.actionDiagnostics()));
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="actions-04-up",timeoutTicks=440)
    public static void ladderUpAndStableExit(GameTestHelper h){begin(h);tower(h);var n=npc(h,3.5,2,7.5);drive(h,n,absolute(h,new Vec3(12.5,8,7.5)),"up",true);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="actions-05-down",timeoutTicks=440)
    public static void ladderDownAndStableExit(GameTestHelper h){begin(h);tower(h);var n=npc(h,12.5,8,7.5);drive(h,n,absolute(h,new Vec3(3.5,2,7.5)),"down",true);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="actions-06-destroy",timeoutTicks=360)
    public static void ladderDestroyedDuringClimb(GameTestHelper h){broken(h,false);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="actions-07-landing",timeoutTicks=360)
    public static void landingBlockedDuringClimb(GameTestHelper h){broken(h,true);}
    private static void broken(GameTestHelper h,boolean landing){
        begin(h);tower(h);var n=npc(h,3.5,2,7.5);var m=new MovementExecutor(n.runtime());var exit=absolute(h,new Vec3(12.5,8,7.5));int[] last={-1};boolean[] changed={false};
        h.onEachTick(()->{
            if(n.tickCount<5 || n.tickCount==last[0])return;last[0]=n.tickCount;
            m.apply(MovementCommand.navigate(v(exit)),h.getLevel().getGameTime());
            if(!changed[0] && n.getY()>position(h,new BlockPos(0,4,0)).getY()){
                changed[0]=true;
                if(landing){block(h,new BlockPos(8,8,7),Blocks.STONE);block(h,new BlockPos(8,9,7),Blocks.STONE);}
                else block(h,new BlockPos(7,5,7),Blocks.AIR);
            }
            if(changed[0] && (m.actionStatus()==ActionRoute.Status.FAILURE || m.recoveryStatus()!=RouteRecovery.Status.IDLE)){
                h.assertTrue(m.actionDiagnostics().contains(landing?"landing":"ladder"),"diagnostic cause: "+m.actionDiagnostics());
                h.assertTrue(TransitionResources.of(h.getLevel()).size()==0,"failure releases narrow resource");
                receipt(h,landing?"blocked landing":"destroyed ladder",m,n);m.stop(h.getLevel().getGameTime());n.discard();success(h);
            }
        });
        h.runAtTickTime(340,()->h.assertTrue(n.isRemoved(),"failure scenario changed="+changed[0]+" entityTicks="+n.tickCount+" pos="+n.position()+" "+m.actionDiagnostics()));
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="actions-08-four",timeoutTicks=1000)
    public static void fourNpcDoorAndLadderQueue(GameTestHelper h){
        begin(h);tower(h);for(int y=2;y<=5;y++)for(int z=0;z<=15;z++)if(z!=7 || y>3)block(h,new BlockPos(5,y,z),Blocks.STONE);
        putDoor(h,new BlockPos(5,2,7),false);
        var ns=new ArrayList<TacticalNpcEntity>();var ms=new ArrayList<MovementExecutor>();var done=new HashSet<UUID>();var exit=absolute(h,new Vec3(12.5,8,7.5));var ticks=new HashMap<UUID,Integer>();
        for(int i=0;i<4;i++){var n=npc(h,2.5,2,5.5+i);ns.add(n);ms.add(new MovementExecutor(n.runtime()));}
        h.onEachTick(()->{
            for(int i=0;i<ns.size();i++){
                var n=ns.get(i);if(done.contains(n.getUUID()) || n.tickCount<5 || ticks.getOrDefault(n.getUUID(),-1)==n.tickCount)continue;ticks.put(n.getUUID(),n.tickCount);
                var m=ms.get(i);m.apply(MovementCommand.navigate(v(exit)),h.getLevel().getGameTime());
                h.assertTrue(AiWorkBudget.of(h.getLevel()).routeNodes()<=512 && AiWorkBudget.of(h.getLevel()).paths()<=8,"global route/path limits");
                if(n.onGround() && n.position().distanceTo(exit)<.65 && !m.actionActive()){done.add(n.getUUID());receipt(h,"four #"+i,m,n);m.stop(h.getLevel().getGameTime());n.discard();}
            }
            if(done.size()==4){h.assertTrue(TransitionResources.of(h.getLevel()).size()==0,"all resources released");success(h);}
        });
        h.runAtTickTime(990,()->h.assertTrue(done.size()==4,"four actual arrivals: "+done.size()+" "+java.util.stream.IntStream.range(0,ns.size()).mapToObj(i->ns.get(i).position()+" "+ms.get(i).actionDiagnostics()).toList()));
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="actions-09-denied",timeoutTicks=420)
    public static void forgeInteractionVetoAndIronDoor(GameTestHelper h){
        begin(h);corridor(h);door(h,false);var n=npc(h,3.5,2,7.5);var m=new MovementExecutor(n.runtime());
        java.util.function.Consumer<RouteInteractions.OpenDoorEvent> veto=e->{if(e.npc()==n)e.setCanceled(true);};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(veto);
        int[] last={-1},ready={-1};
        h.onEachTick(()->{
            if(n.tickCount<5 || last[0]==n.tickCount)return;last[0]=n.tickCount;m.apply(MovementCommand.navigate(v(absolute(h,new Vec3(12.5,2,7.5)))),h.getLevel().getGameTime());
            if(ready[0]<0)ready[0]=n.tickCount;
            if(m.actionDiagnostics().contains("interaction denied")){
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);
                h.assertTrue(!state(h,new BlockPos(7,2,7)).getValue(DoorBlock.OPEN),"veto leaves door closed");m.stop(h.getLevel().getGameTime());
                var iron=Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST);
                block(h,new BlockPos(7,2,7),iron);block(h,new BlockPos(7,3,7),iron.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
                h.assertTrue(!RouteInteractions.VANILLA.supports(h.getLevel(),position(h,new BlockPos(7,2,7))),"iron has no interaction edge");n.discard();success(h);
            }
            h.assertTrue(n.isRemoved() || n.tickCount-ready[0]<180,"door veto incomplete after 180 active ticks: pos="+n.position()+" entityTicks="+n.tickCount+" ticking="+h.getLevel().isPositionEntityTicking(n.blockPosition())+" ground="+n.onGround()+" navDone="+n.getNavigation().isDone()+" controller="+n.controller()+" "+m.actionDiagnostics());
        });
        h.runAtTickTime(410,()->h.fail("door veto fixture did not complete active window: ready="+ready[0]+" entityTicks="+n.tickCount+" "+m.actionDiagnostics()));
    }
    private static void drive(GameTestHelper h,TacticalNpcEntity n,Vec3 exit,String label,boolean success){
        var m=new MovementExecutor(n.runtime());int[] last={-1};
        h.onEachTick(()->{
            if(n.isRemoved() || n.tickCount<5 || last[0]==n.tickCount)return;last[0]=n.tickCount;
            m.apply(MovementCommand.navigate(v(exit)),h.getLevel().getGameTime());
            if(n.onGround() && n.position().distanceTo(exit)<.65 && !m.actionActive()){receipt(h,label,m,n);m.stop(h.getLevel().getGameTime());n.discard();success(h);}
        });
        h.runAtTickTime(230,()->TacticalNpcAiMod.LOGGER.info("ACTION_PROGRESS {} tick={} pos={} {}",label,h.getTick(),n.position(),m.actionDiagnostics()));
        h.runAtTickTime(410,()->h.assertTrue(n.isRemoved(),label+" actual arrival: "+n.position()+" "+m.actionDiagnostics()));
    }
    private static void begin(GameTestHelper h){
        ScenarioIsolation.begin(h);
        // Dedicated finite sky fixture: ordinary catalog tests also write corridors outside their
        // templates. Their terrain and ticket lifetimes must not become action-route inputs.
        var base=new BlockPos(20000+32*sceneIndex++,100,20000);BASE.put(h,base);sceneLevel=h.getLevel();
        for(int x=(base.getX()-1)>>4;x<=(base.getX()+16)>>4;x++)for(int z=(base.getZ()-1)>>4;z<=(base.getZ()+16)>>4;z++){
            var c=new ChunkPos(x,z);if(sceneLevel.setChunkForced(x,z,true))CHUNKS.add(c);
        }
        h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(true,h.getLevel().getServer());
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){block(h,new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<=13;y++)block(h,new BlockPos(x,y,z),Blocks.AIR);}
    }
    static void tower(GameTestHelper h){
        // Six blocks of vertical separation. No stairs, jumps, ground ramp or alternate upper exit.
        for(int x=8;x<=14;x++)for(int z=6;z<=8;z++)block(h,new BlockPos(x,7,z),Blocks.STONE);
        for(int y=2;y<=7;y++){block(h,new BlockPos(8,y,7),Blocks.STONE);block(h,new BlockPos(7,y,7),Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.WEST));}
    }
    private static void corridor(GameTestHelper h){for(int x=0;x<16;x++)for(int y=2;y<=5;y++){block(h,new BlockPos(x,y,6),Blocks.STONE);block(h,new BlockPos(x,y,8),Blocks.STONE);}for(int y=2;y<=5;y++){block(h,new BlockPos(0,y,7),Blocks.STONE);block(h,new BlockPos(15,y,7),Blocks.STONE);if(y>3)block(h,new BlockPos(7,y,7),Blocks.STONE);}}
    private static void door(GameTestHelper h,boolean open){putDoor(h,new BlockPos(7,2,7),open);}
    private static void putDoor(GameTestHelper h,BlockPos p,boolean open){var state=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST).setValue(DoorBlock.OPEN,open);block(h,p,state);block(h,p.above(),state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));}
    private static TacticalNpcEntity npc(GameTestHelper h,double x,double y,double z){var n=(TacticalNpcEntity)dev.zuma.tacticalnpc.runtime.RuntimeRegistry.create(h.getLevel()).entity();var p=absolute(h,new Vec3(x,y,z));n.moveTo(p.x,p.y,p.z,0,0);n.setOrder(OrderState.Kind.ASSAULT,64);h.getLevel().addFreshEntity(n);return n;}
    private static Vector3 v(Vec3 p){return new Vector3(p.x,p.y,p.z);}
    private static void receipt(GameTestHelper h,String scene,MovementExecutor m,TacticalNpcEntity n){TacticalNpcAiMod.LOGGER.info("ACTION_RESULT scene={} tick={} position={} grounded={} {}",scene,h.getTick(),n.position(),n.onGround(),m.actionDiagnostics());}
}
