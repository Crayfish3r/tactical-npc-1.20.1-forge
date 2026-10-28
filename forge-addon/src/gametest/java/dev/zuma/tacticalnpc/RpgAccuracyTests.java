package dev.zuma.tacticalnpc;

import com.tacz.guns.api.item.IGun;
import dev.zuma.tacticalnpc.compat.*;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class RpgAccuracyTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="rpg-03-orphan",timeoutTicks=150)
    public static void nativeInFlightRocketAfterOwnerUnload(GameTestHelper h){inFlight(h,false);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="rpg-04-inactive",timeoutTicks=150)
    public static void nativeInFlightRocketAfterMatchDeactivation(GameTestHelper h){inFlight(h,true);}
    private static void inFlight(GameTestHelper h,boolean inactive) {
        ScenarioIsolation.begin(h);var n=npc(h,"rpg7",1);var p=player(h,n.position().add(28,0,0));int[] blasts={0};boolean[] changed={false};
        for(int y=2;y<=8;y++)for(int z=26;z<=38;z++)h.setBlock(new net.minecraft.core.BlockPos(50,y,z),net.minecraft.world.level.block.Blocks.STONE);
        Vec3[] fixed={p.position()};h.onEachTick(()->{p.setPos(fixed[0]);p.setDeltaMovement(Vec3.ZERO);});
        var launched=new HashSet<UUID>();var rounds=new ArrayList<com.tacz.guns.entity.EntityKineticBullet>();boolean[] safe={true};
        var match=UUID.randomUUID();var registry=dev.zuma.tacticalnpc.api.MatchNpcRegistry.of(h.getLevel().getServer());
        var listener=new Object(){@net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
            public void blast(net.minecraftforge.event.level.ExplosionEvent.Detonate e){
                if(changed[0] && e.getExplosion().getDirectSourceEntity() instanceof com.tacz.guns.entity.EntityKineticBullet b && launched.contains(b.getUUID())){
                    blasts[0]++;safe[0]&=e.getAffectedEntities().isEmpty();
                }
            }};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(listener);
        h.onEachTick(()->{if(!changed[0] && ProjectileProbe.count(n.getUUID())>0){
            changed[0]=true;
            // Avoid an entity-hit Pre cancellation: force the real rocket's next impact to be native block-hit splash.
            fixed[0]=h.absoluteVec(new Vec3(49.5,2,34.5));p.moveTo(fixed[0].x,fixed[0].y,fixed[0].z);
            h.getLevel().getAllEntities().forEach(e->{if(e instanceof com.tacz.guns.entity.EntityKineticBullet b && b.getOwner()==n){launched.add(b.getUUID());rounds.add(b);}});
            if(inactive){
                registry.registerPolicy(match,new dev.zuma.tacticalnpc.api.MatchNpcPolicy(){public boolean isActive(){return false;}public boolean canAttack(net.minecraft.server.level.ServerPlayer target){return false;}});
                h.getLevel().getAllEntities().forEach(e->{if(e instanceof com.tacz.guns.entity.EntityKineticBullet b && b.getOwner()==n)b.getPersistentData().putUUID("tacticalnpc_match",match);});
            }else n.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        }});
        h.runAtTickTime(130,()->{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);h.assertTrue(changed[0] && blasts[0]>0 && safe[0],"real rocket outlives authorization; launched="+launched.size()+" blasts="+blasts[0]+" safe="+safe[0]+" projectiles="+rounds.stream().map(b->"pos="+b.position()+" local="+b.position().subtract(h.absoluteVec(Vec3.ZERO))+" ticks="+b.tickCount+" removed="+b.getRemovalReason()+" velocity="+b.getDeltaMovement()).toList()+" decision="+(n.controller()==null?null:n.controller().fireDiagnostics()));n.discard();p.discard();h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="accuracy-03-api",timeoutTicks=50)
    public static void mixedPublicSpawnApi(GameTestHelper h) {
        ScenarioIsolation.begin(h);var registry=dev.zuma.tacticalnpc.api.MatchNpcRegistry.of(h.getLevel().getServer());var match=UUID.randomUUID();
        registry.registerPolicy(match,new dev.zuma.tacticalnpc.api.MatchNpcPolicy(){public boolean isActive(){return true;}public boolean canAttack(net.minecraft.server.level.ServerPlayer p){return false;}});
        h.runAtTickTime(5,()->{
            var old=new dev.zuma.tacticalnpc.api.SpawnMemberSpec(new ResourceLocation("tacz:ak47"),TacticalNpcEntity.AmmoPolicy.FINITE,4,OrderState.Kind.ASSAULT,24);
            var rocket=new dev.zuma.tacticalnpc.api.SpawnMemberSpec(new ResourceLocation("tacz:rpg7"),TacticalNpcEntity.AmmoPolicy.FINITE,2,OrderState.Kind.HOLD_POSITION,24,NpcDifficulty.DEFAULT);
            var result=registry.spawn(new dev.zuma.tacticalnpc.api.SpawnGroupRequest(h.getLevel(),match,UUID.randomUUID(),h.absoluteVec(new Vec3(32.5,2,32.5)),-90,0,List.of(old,rocket)));
            h.assertTrue(result.success() && result.entityIds().size()==2,"mixed public group: "+result.diagnostic());
            for(int i=0;i<2;i++){
                var n=(TacticalNpcEntity)h.getLevel().getEntity(result.entityIds().get(i));var spec=i==0?old:rocket;
                h.assertTrue(n.difficulty()==spec.difficulty() && IGun.getIGunOrNull(n.getMainHandItem()).getGunId(n.getMainHandItem()).equals(spec.weapon()) && n.order().equals(spec.order().name()),"independent member accuracy/order/weapon");
            }
            registry.remove(match);h.succeed();
        });
    }
    private static TacticalNpcEntity npc(GameTestHelper h,String id,int rounds) {
        return npc(h,id,rounds,UUID.randomUUID());
    }
    private static TacticalNpcEntity npc(GameTestHelper h,String id,int rounds,UUID identity) {
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(16.5,2,32.5));
        n.setUUID(identity);
        n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.setOrder(OrderState.Kind.HOLD_POSITION,24);
        var s=GunSupport.create(new ResourceLocation("tacz",id));IGun.getIGunOrNull(s).setCurrentAmmoCount(s,rounds);
        n.setItemSlot(EquipmentSlot.MAINHAND,s);n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,3);
        n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);return n;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,Vec3 at) {
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"rpg-test"));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setInvulnerable(true);p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);return p;
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="rpg-01-close",timeoutTicks=250)
    public static void closeStandWaitThenNativeRocketAndReload(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"rpg7",0);var p=player(h,n.position().add(4,0,0));var start=n.position();
        Vec3[] fixed={p.position()};h.onEachTick(()->{p.setPos(fixed[0]);p.setDeltaMovement(Vec3.ZERO);});
        h.runAtTickTime(85,()->{
            h.assertTrue(ProjectileProbe.count(n.getUUID())==0 && n.standPhase()==TacticalNpcEntity.StandPhase.WAIT && n.position().distanceTo(start)<.1,"close RPG waits without fake STAND shot or movement");
            h.assertTrue(n.controller().reloads()==1,"ordinary native initial reload");fixed[0]=start.add(24,0,0);p.moveTo(fixed[0].x,fixed[0].y,fixed[0].z);
        });
        h.runAtTickTime(230,()->{
            h.assertTrue(ProjectileProbe.count(n.getUUID())>=2 && n.controller().reloads()>=2,"actual native rockets and subsequent reload: rounds="+ProjectileProbe.count(n.getUUID())+" reloads="+n.controller().reloads()+" npc="+n.position()+" target="+p.position()+" "+n.controller().fireDiagnostics());
            var gun=IGun.getIGunOrNull(n.getMainHandItem());h.assertTrue(gun.getDummyAmmoAmount(n.getMainHandItem())<3,"native reload consumes rocket reserve");
            h.assertTrue(new TaczGunController(n.runtime()).snapshot().profile().kind()==WeaponProfile.Kind.RPG,"dedicated RPG profile");n.discard();p.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="rpg-02-blast",timeoutTicks=200)
    public static void allyPreflightAndActualExplosion(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"rpg7",1);var p=player(h,n.position().add(24,0,0));
        for(int y=2;y<=8;y++)for(int z=26;z<=38;z++)h.setBlock(new net.minecraft.core.BlockPos(44,y,z),net.minecraft.world.level.block.Blocks.STONE);
        Vec3[] fixed={p.position()};h.onEachTick(()->{p.setPos(fixed[0]);p.setDeltaMovement(Vec3.ZERO);});
        var ally=NpcEntities.NPC.get().create(h.getLevel());ally.setNoAi(true);ally.moveTo(p.getX(),p.getY(),p.getZ()+2);h.getLevel().addFreshEntity(ally);
        var team=h.getLevel().getScoreboard().addPlayerTeam("rocket-allies");
        h.getLevel().getScoreboard().addPlayerToTeam(n.getScoreboardName(),team);h.getLevel().getScoreboard().addPlayerToTeam(ally.getScoreboardName(),team);n.syncTeam();
        boolean[] moved={false};int[] blasts={0};
        var listener=new Object(){@net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
            public void blast(net.minecraftforge.event.level.ExplosionEvent.Detonate e){
                if(e.getExplosion().getDirectSourceEntity() instanceof com.tacz.guns.entity.EntityKineticBullet b && b.getOwner()==n){blasts[0]++;h.assertFalse(e.getAffectedEntities().contains(ally),"native explosion excludes ally before damage AND knockback");}
            }};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(listener);
        h.runAtTickTime(55,()->{h.assertTrue(ProjectileProbe.count(n.getUUID())==0,"ally near impact prevents real launch");ally.moveTo(n.getX(),n.getY(),n.getZ()+16);});
        h.onEachTick(()->{
            if(!moved[0] && ProjectileProbe.count(n.getUUID())>0){moved[0]=true;ally.moveTo(p.getX()+3,p.getY(),p.getZ()+2);ally.setDeltaMovement(Vec3.ZERO);fixed[0]=p.position().add(3,0,-2);}
        });
        h.runAtTickTime(175,()->{
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
            h.assertTrue(blasts[0]>0 && ally.getHealth()==ally.getMaxHealth() && Math.abs(ally.getZ()-(p.getZ()+4))<.1,"real native explosion protects ally health and displacement; blasts="+blasts[0]);
            n.discard();p.discard();ally.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="accuracy-01",timeoutTicks=520)
    public static void measuredOrdinaryWeaponAccuracyAndFireVolume(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"ak47",30,new UUID(0,730));n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.INFINITE,0);
        n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var p=player(h,n.position().add(32,0,0));double[] errors={0,0};int[] calls={0,0};int[] phase={0};
        var fixed=p.position();h.onEachTick(()->{p.setPos(fixed);p.setDeltaMovement(Vec3.ZERO);});
        var listener=new Object(){@net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
            public void fire(com.tacz.guns.api.event.common.GunFireEvent e){if(e.getShooter()!=n || e.isCanceled() || n.controller().decision().aim().point()==null)return;
                var point=n.controller().decision().aim().point();Vec3 d=new Vec3(point.x(),point.y(),point.z()).subtract(n.getEyePosition());
                double yaw=Math.toDegrees(Math.atan2(d.z,d.x))-90,pitch=-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance()));
                errors[phase[0]]+=Math.hypot(net.minecraft.util.Mth.wrapDegrees(yaw-n.getYHeadRot()),pitch-n.getXRot());calls[phase[0]]++;
            }};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(listener);
        h.runAtTickTime(250,()->{n.setDifficulty(NpcDifficulty.DEFAULT);phase[0]=1;});
        h.runAtTickTime(495,()->{
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
            double hard=errors[0]/Math.max(1,calls[0]),normal=errors[1]/Math.max(1,calls[1]);
            TacticalNpcAiMod.LOGGER.info("ACCURACY_MEASURE hard={} default={} calls={}/{} native={} state={} weapon={}",hard,normal,calls[0],calls[1],ProjectileProbe.count(n.getUUID()),n.controller().fireDiagnostics(),n.controller().weaponState());
            h.assertTrue(calls[0]>=12 && calls[1]>=12 && Math.abs(calls[0]-calls[1])<=Math.max(calls[0],calls[1])*.3 && normal>hard*1.8,"actual barrel angular error rises while requests remain comparable: "+hard+"/"+normal+" calls="+Arrays.toString(calls));
            h.assertTrue(ProjectileProbe.count(n.getUUID())>=calls[0]+calls[1]-2,"requests produce real native rounds");n.discard();p.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="accuracy-02",timeoutTicks=50)
    public static void savedNamesLegacyConstructorAndCommand(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"ak47",0);
        h.runAtTickTime(10,()->{
            var old=new dev.zuma.tacticalnpc.api.SpawnMemberSpec(new ResourceLocation("tacz:ak47"),TacticalNpcEntity.AmmoPolicy.FINITE,4,OrderState.Kind.ASSAULT,24);
            h.assertTrue(old.difficulty()==NpcDifficulty.HARD,"old binary constructor defaults HARD");
            var c=h.getLevel().getServer().getCommands();var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPermission(4);
            var order=n.runtime().orderState();var phase=n.standPhase();var controller=n.controller();long generation=controller.generation();
            c.performPrefixedCommand(source,"npc difficulty "+n.npcName()+" default");
            h.assertTrue(n.difficulty()==NpcDifficulty.DEFAULT && order.equals(n.runtime().orderState()) && phase==n.standPhase() && generation==controller.generation(),"command changes only accuracy");
            var tag=new net.minecraft.nbt.CompoundTag();n.save(tag);
            var restored=(TacticalNpcEntity)EntityType.loadEntityRecursive(tag,h.getLevel(),e->e);
            h.assertTrue(restored.difficulty()==NpcDifficulty.DEFAULT,"named NBT round trip");
            tag.getCompound("tacticalnpc").remove("difficulty");restored.readAdditionalSaveData(tag);h.assertTrue(restored.difficulty()==NpcDifficulty.HARD,"old NPC defaults HARD");
            tag.getCompound("tacticalnpc").putString("difficulty","UNFAIR");restored.readAdditionalSaveData(tag);h.assertTrue(restored.difficulty()==NpcDifficulty.HARD,"unknown legacy value falls back HARD");
            n.discard();restored.discard();h.succeed();
        });
    }
}
