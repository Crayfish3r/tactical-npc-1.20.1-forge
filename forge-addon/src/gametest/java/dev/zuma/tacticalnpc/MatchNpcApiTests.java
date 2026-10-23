package dev.zuma.tacticalnpc;

import com.tacz.guns.api.item.IGun;
import dev.zuma.tacticalnpc.api.*;
import dev.zuma.tacticalnpc.combat.CombatTargets;
import dev.zuma.tacticalnpc.core.OrderState;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class MatchNpcApiTests {
    private static final class Policy implements MatchNpcPolicy {
        boolean active=true,fail;
        final Set<UUID> players=new HashSet<>();
        public boolean isActive(){if(fail)throw new IllegalStateException("fixture policy error");return active;}
        public boolean canAttack(ServerPlayer player){if(fail)throw new IllegalStateException("fixture policy error");return players.contains(player.getUUID());}
    }
    private record Scene(TacticalNpcMatchApi api,UUID match,Policy policy){}
    private static Scene scene(GameTestHelper h){
        ScenarioIsolation.begin(h);
        for(int x=0;x<64;x++)for(int z=0;z<64;z++){h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<=5;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);}
        var api=TacticalNpcMatchApi.get(h.getLevel().getServer());var id=UUID.randomUUID();var policy=new Policy();api.registerPolicy(id,policy);return new Scene(api,id,policy);
    }
    private static SpawnMemberSpec spec(String gun){return new SpawnMemberSpec(new ResourceLocation("tacz",gun),TacticalNpcEntity.AmmoPolicy.FINITE,37,OrderState.Kind.ASSAULT,24);}
    private static SpawnGroupRequest request(GameTestHelper h,Scene s,String... guns){return new SpawnGroupRequest(h.getLevel(),s.match(),UUID.randomUUID(),h.absoluteVec(new Vec3(32.5,2,32.5)),-90,0,Arrays.stream(guns).map(MatchNpcApiTests::spec).toList());}
    private static TacticalNpcEntity npc(GameTestHelper h,Scene s,SpawnResult result){h.assertTrue(result.success(),result.diagnostic());return (TacticalNpcEntity)h.getLevel().getEntity(result.entityIds().get(0));}
    private static void finish(GameTestHelper h,Scene s){s.api().remove(s.match());h.succeed();}

    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-01-single",timeoutTicks=45)
    public static void singleNpcAndServerThread(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{var n=npc(h,s,s.api().spawn(request(h,s,"ak47")));
            h.assertTrue(s.match().equals(n.ownerMatchId()) && n.groupId()!=null && s.api().activeNpcCount(s.match())==1,"typed single spawn/binding/count");
            h.assertTrue(n.ammoPolicy()==TacticalNpcEntity.AmmoPolicy.FINITE && IGun.getIGunOrNull(n.getMainHandItem()).getDummyAmmoAmount(n.getMainHandItem())==37,"finite reserve initialized once");
            boolean denied=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{s.api().activeNpcCount(s.match());return false;}catch(IllegalStateException ex){return true;}}).join();
            h.assertTrue(denied,"off-thread API rejected before world access");finish(h,s);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-02-group",timeoutTicks=45)
    public static void groupFourIndividualLoadoutsAndOrders(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{var r=request(h,s,"ak47","glock_17","ai_awp","b93r");
            var kinds=OrderState.Kind.values();var members=new ArrayList<SpawnMemberSpec>();
            for(int i=0;i<4;i++)members.add(new SpawnMemberSpec(r.members().get(i).weapon(),i==0?TacticalNpcEntity.AmmoPolicy.INFINITE:TacticalNpcEntity.AmmoPolicy.FINITE,13,kinds[i],16));
            r=new SpawnGroupRequest(r.level(),r.matchId(),r.groupId(),r.origin(),r.yaw(),r.pitch(),members);var result=s.api().spawn(r);npc(h,s,result);
            h.assertTrue(result.entityIds().size()==4 && s.api().activeNpcCount(s.match())==4,"four admitted members");var team=h.getLevel().getEntity(result.entityIds().get(0)).getTeam();
            for(int i=0;i<4;i++){var n=(TacticalNpcEntity)h.getLevel().getEntity(result.entityIds().get(i));h.assertTrue(n.getTeam()==team && team.getName().startsWith("tnpc_") && n.groupId().equals(r.groupId()),"native shared team/group");h.assertTrue(IGun.getIGunOrNull(n.getMainHandItem()).getGunId(n.getMainHandItem()).equals(members.get(i).weapon()) && n.order().equals(kinds[i].name()),"individual gun/order preserved");}
            h.assertTrue(s.api().spawn(r).status()==SpawnResult.Status.DUPLICATE_GROUP,"no accidental duplicate group");finish(h,s);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-03-weapons",timeoutTicks=45)
    public static void invalidAndUnsupportedWeaponsLeaveNoGroup(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{int teams=h.getLevel().getScoreboard().getPlayerTeams().size();
            for(String gun:List.of("not_a_gun","rpg7")){var r=s.api().spawn(request(h,s,"ak47",gun));h.assertTrue(r.status()==SpawnResult.Status.INVALID_WEAPON && r.entityIds().isEmpty(),"invalid/unsupported weapon: "+gun+" "+r);}
            h.assertTrue(s.api().activeNpcCount(s.match())==0 && teams==h.getLevel().getScoreboard().getPlayerTeams().size(),"preflight has no live entity/team side effects");finish(h,s);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-04-space",timeoutTicks=45)
    public static void insufficientSpaceIsAtomicAndNoChunkLoads(GameTestHelper h){
        var s=scene(h);for(int x=16;x<=48;x++)for(int z=16;z<=48;z++)h.setBlock(new BlockPos(x,1,z),Blocks.MAGMA_BLOCK);h.setBlock(new BlockPos(28,1,32),Blocks.STONE);
        h.runAtTickTime(5,()->{var r=s.api().spawn(request(h,s,"ak47","ak47","ak47","ak47"));h.assertTrue(r.status()==SpawnResult.Status.NO_SPACE && s.api().activeNpcCount(s.match())==0,"one safe cell cannot commit partial group");
            int loaded=h.getLevel().getChunkSource().getLoadedChunksCount();var remote=new SpawnGroupRequest(h.getLevel(),s.match(),UUID.randomUUID(),new Vec3(20000000,80,20000000),0,0,List.of(spec("ak47")));
            h.assertTrue(s.api().spawn(remote).status()==SpawnResult.Status.NO_SPACE && loaded==h.getLevel().getChunkSource().getLoadedChunksCount(),"placement does not load missing chunks");finish(h,s);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-05-rollback",timeoutTicks=45)
    public static void nativeAdmissionFailureRollsBackEntitiesAndTeam(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{int[] admitted={0};int teams=h.getLevel().getScoreboard().getPlayerTeams().size();
            Consumer<EntityJoinLevelEvent> reject=e->{if(e.getEntity() instanceof TacticalNpcEntity n && s.match().equals(n.ownerMatchId()) && ++admitted[0]==2)e.setCanceled(true);};
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH,false,EntityJoinLevelEvent.class,reject);
            SpawnResult result;try{result=s.api().spawn(request(h,s,"ak47","ak47","ak47","ak47"));}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(reject);}
            h.assertTrue(result.status()==SpawnResult.Status.ENTITY_REJECTED && result.entityIds().isEmpty() && admitted[0]==2,"native second admission actually rejected");
            h.assertTrue(s.api().activeNpcCount(s.match())==0 && teams==h.getLevel().getScoreboard().getPlayerTeams().size(),"rollback leaves no actors/team");finish(h,s);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-14-geometry",timeoutTicks=45)
    public static void fluidsHeadHazardsAndWorldBorderRejectSpawn(GameTestHelper h){
        var s=scene(h);
        h.runAtTickTime(5,()->{
            for(int x=16;x<=48;x++)for(int z=16;z<=48;z++)h.setBlock(new BlockPos(x,2,z),Blocks.WATER);
            h.assertTrue(s.api().spawn(request(h,s,"ak47")).status()==SpawnResult.Status.NO_SPACE,"water is not a valid spawn");
            for(int x=16;x<=48;x++)for(int z=16;z<=48;z++){h.setBlock(new BlockPos(x,2,z),Blocks.AIR);h.setBlock(new BlockPos(x,3,z),Blocks.POWDER_SNOW);}
            h.assertTrue(s.api().spawn(request(h,s,"ak47")).status()==SpawnResult.Status.NO_SPACE,"stable powder-snow head hazard rejected");
            for(int x=16;x<=48;x++)for(int z=16;z<=48;z++)h.setBlock(new BlockPos(x,3,z),Blocks.AIR);
            var border=h.getLevel().getWorldBorder();double x=border.getCenterX(),z=border.getCenterZ(),size=border.getSize();
            try{var at=h.absoluteVec(new Vec3(32.5,2,32.5));border.setCenter(at.x,at.z);border.setSize(1);h.assertTrue(s.api().spawn(request(h,s,"ak47")).status()==SpawnResult.Status.NO_SPACE,"all candidates outside border rejected");}
            finally{border.setCenter(x,z);border.setSize(size);}
            finish(h,s);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-15-team-owner",timeoutTicks=45)
    public static void recreatedForeignTeamIsNeverRemovedAfterRestart(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{var n=npc(h,s,s.api().spawn(request(h,s,"ak47")));var board=h.getLevel().getScoreboard();String name=n.getTeam().getName();board.removePlayerTeam(board.getPlayerTeam(name));var foreign=board.addPlayerTeam(name);
            var data=MatchNpcRegistry.of(h.getLevel().getServer()).save(new CompoundTag());n.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.getLevel().getServer().overworld().getDataStorage().set("tacticalnpc_matches",MatchNpcRegistry.load(data));var fresh=MatchNpcRegistry.of(h.getLevel().getServer());fresh.startup();
            h.assertTrue(board.getPlayerTeam(name)==foreign,"persistent ownership marker preserves foreign replacement even when empty");h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-06-nbt",timeoutTicks=65)
    public static void bindingNbtReloadPreservesNativeState(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{var n=npc(h,s,s.api().spawn(request(h,s,"glock_17")));n.removeTag(RuntimeRegistry.TAG);n.setOrder(OrderState.Kind.PATROL,16);
            var saved=new CompoundTag();n.save(saved);var service=n.serviceId();var group=n.groupId();var hand=n.getMainHandItem().save(new CompoundTag());var team=n.getTeam();n.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(s.api().activeNpcCount(s.match())==0,"unloaded not counted as active");
            h.runAtTickTime(10,()->{var restored=(TacticalNpcEntity)EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);
                h.assertTrue(restored!=null && restored.ownerMatchId().equals(s.match()) && restored.groupId().equals(group) && restored.serviceId().equals(service) && restored.order().equals("PATROL") && restored.ammoPolicy()==TacticalNpcEntity.AmmoPolicy.FINITE && hand.equals(restored.getMainHandItem().save(new CompoundTag())),"binding migration preserves exact native stack/identity/order/reserve");
                h.getLevel().addFreshEntity(restored);h.assertTrue(!restored.isRemoved() && restored.getTeam()==team && s.api().activeNpcCount(s.match())==1,"active session reload admission and native scoreboard authority");finish(h,s);
            });
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-07-old",timeoutTicks=45)
    public static void schemasOneThroughSixRemainManual(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{var original=NpcEntities.NPC.get().create(h.getLevel());original.moveTo(h.absoluteVec(new Vec3(30.5,2,30.5)));original.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,17);
            for(int schema=1;schema<=6;schema++){var root=original.saveWithoutId(new CompoundTag());root.getCompound("tacticalnpc").putInt("schema",schema);var copy=NpcEntities.NPC.get().create(h.getLevel());copy.load(root);
                h.assertTrue(copy.ownerMatchId()==null && copy.groupId()==null && !copy.invalidMatchBinding() && copy.serviceId().equals(original.serviceId()) && copy.ammoPolicy()==TacticalNpcEntity.AmmoPolicy.FINITE,"old schema "+schema+" stays manual with identity/ammo");copy.discard();}
            original.discard();finish(h,s);
        });
    }
    private static ServerPlayer player(GameTestHelper h,double x){var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"match-target"));p.setGameMode(GameType.SURVIVAL);p.moveTo(h.absoluteVec(new Vec3(x,2,32.5)));h.getLevel().addNewPlayer(p);return p;}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-16-corrupt",timeoutTicks=45)
    public static void malformedBindingNeverBecomesManual(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{
            for(String field:List.of("ownerMatchId","groupId")){
                var n=NpcEntities.NPC.get().create(h.getLevel());var root=n.saveWithoutId(new CompoundTag());root.getCompound("tacticalnpc").putString(field,"invalid-uuid");n.load(root);
                h.assertTrue(n.invalidMatchBinding(),"malformed "+field+" remains fail closed");
                h.getLevel().addFreshEntity(n);h.assertTrue(n.isRemoved(),"invalid binding discarded during admission");
            }
            finish(h,s);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-17-aliases",timeoutTicks=45)
    public static void spawnAliasesRemainManualAndPermissionChecked(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{
            var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPosition(h.absoluteVec(new Vec3(32.5,2,32.5))).withPermission(2).withSuppressedOutput();
            try{var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();
                h.assertTrue(dispatcher.execute("spawn npc team alias-a",source)==1 && dispatcher.execute("tacticalnpc spawn team alias-b",source)==1,"both spawn aliases execute the shared service");
                for(String command:List.of("spawn npc","tacticalnpc spawn")){boolean refused=false;try{dispatcher.execute(command,source.withPermission(1));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){refused=true;}h.assertTrue(refused,"permission2 retained");}
            }catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){throw new GameTestAssertException(ex.getMessage());}
            var all=new ArrayList<TacticalNpcEntity>();h.getLevel().getAllEntities().forEach(e->{if(e instanceof TacticalNpcEntity n)all.add(n);});
            h.assertTrue(all.size()==2 && all.stream().allMatch(n->n.ownerMatchId()==null && n.order().equals("ASSAULT")) && s.api().activeNpcCount(s.match())==0,"administrator aliases never bind to TT match");all.forEach(Entity::discard);finish(h,s);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-08-policy",timeoutTicks=60)
    public static void playerMembershipEliminationSpectatorAndMemory(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{var n=npc(h,s,s.api().spawn(request(h,s,"ak47")));var member=player(h,36.5);var outsider=player(h,40.5);s.policy().players.add(member.getUUID());
            h.assertTrue(CombatTargets.eligible(n,member) && !CombatTargets.eligible(n,outsider),"only admitted match player selectable");
            h.runAtTickTime(12,()->{n.controller().stop();h.assertTrue(n.controller().hear(member) && n.controller().observation()!=null,"legitimate member hearing remembered");s.policy().players.clear();
                h.assertTrue(!CombatTargets.eligible(n,member) && n.controller().observation()==null && !n.controller().allowNativeFire(),"elimination revokes remembered target and fire immediately");
                s.policy().players.add(member.getUUID());member.setGameMode(GameType.SPECTATOR);h.assertFalse(CombatTargets.eligible(n,member),"spectator refused even if TT callback allows");member.setGameMode(GameType.SURVIVAL);member.setHealth(0);h.assertFalse(CombatTargets.eligible(n,member),"dead player refused");member.discard();outsider.discard();finish(h,s);
            });
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-09-closed",timeoutTicks=45)
    public static void missingAndFailingPolicyCannotFireOrDamage(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{var n=npc(h,s,s.api().spawn(request(h,s,"ak47")));var p=player(h,40.5);s.policy().players.add(p.getUUID());
            s.policy().fail=true;h.assertFalse(CombatTargets.eligible(n,p),"policy exception fails closed");s.policy().fail=false;
            s.api().registerPolicy(s.match(),new MatchNpcPolicy(){public boolean isActive(){return true;}public boolean canAttack(ServerPlayer player){throw new IllegalStateException("fixture eligibility error");}});
            h.assertFalse(CombatTargets.eligible(n,p),"canAttack exception fails closed");s.api().deactivate(s.match());
            h.assertFalse(CombatTargets.eligible(n,p),"absent policy fails closed");float health=p.getHealth();p.hurt(h.getLevel().damageSources().mobAttack(n),5);h.assertTrue(p.getHealth()==health,"final generic damage guard protects players");
            var event=new com.tacz.guns.api.event.common.GunFireEvent(n,n.getMainHandItem(),net.minecraftforge.fml.LogicalSide.SERVER);h.assertTrue(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event),"native fire refused without policy/controller");
            var unregistered=NpcEntities.NPC.get().create(h.getLevel());unregistered.bindMatch(UUID.randomUUID(),UUID.randomUUID());h.getLevel().addFreshEntity(unregistered);h.assertTrue(unregistered.isRemoved(),"unknown old match removed on discovery");
            p.discard();finish(h,s);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-10-unload",timeoutTicks=60)
    public static void cleanupLoadedAndPreviouslyUnloadedIsIdempotent(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{var r=s.api().spawn(request(h,s,"ak47","glock_17"));var n=npc(h,s,r);var other=(TacticalNpcEntity)h.getLevel().getEntity(r.entityIds().get(1));String team=n.getTeam().getName();var root=new CompoundTag();other.save(root);other.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(s.api().remove(s.match())==1 && s.api().remove(s.match())==0 && s.api().activeNpcCount(s.match())==0 && h.getLevel().getScoreboard().getPlayerTeam(team)==null,"idempotent loaded cleanup and unloaded UUID membership cleanup");
            h.runAtTickTime(10,()->{var restored=(TacticalNpcEntity)EntityType.loadEntityRecursive(root,h.getLevel(),e->e);h.getLevel().addFreshEntity(restored);h.assertTrue(restored.isRemoved() && h.getLevel().getScoreboard().getPlayerTeam(team)==null,"unloaded NPC cannot resurrect match/team");finish(h,s);});
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-11-restart",timeoutTicks=45)
    public static void savedRegistryRestartClosesOldIds(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{var n=npc(h,s,s.api().spawn(request(h,s,"ak47")));var root=new CompoundTag();n.save(root);String team=n.getTeam().getName();n.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var data=MatchNpcRegistry.of(h.getLevel().getServer()).save(new CompoundTag());h.getLevel().getServer().overworld().getDataStorage().set("tacticalnpc_matches",MatchNpcRegistry.load(data));
            var fresh=MatchNpcRegistry.of(h.getLevel().getServer());fresh.startup();boolean rejected=false;try{fresh.registerPolicy(s.match(),new Policy());}catch(IllegalArgumentException ex){rejected=true;}
            h.assertTrue(rejected && h.getLevel().getScoreboard().getPlayerTeam(team)==null,"restart never reopens old match and cleans native team");
            var restored=(TacticalNpcEntity)EntityType.loadEntityRecursive(root,h.getLevel(),e->e);h.getLevel().addFreshEntity(restored);h.assertTrue(restored.isRemoved(),"old persisted owner removed on admission");h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-12-manual",timeoutTicks=45)
    public static void manualNpcAndForeignTeamsArePreserved(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{var manual=NpcEntities.NPC.get().create(h.getLevel());manual.moveTo(h.absoluteVec(new Vec3(40.5,2,40.5)));h.getLevel().addFreshEntity(manual);NpcTeams.assign(manual,"manual-team");var p=player(h,44.5);
            h.assertTrue(manual.ownerMatchId()==null && CombatTargets.eligible(manual,p),"manual hostile behavior independent of TT policy");var n=npc(h,s,s.api().spawn(request(h,s,"ak47")));var team=n.getTeam();NpcTeams.assign(p,team.getName());
            s.api().remove(s.match());h.assertTrue(!manual.isRemoved() && p.getTeam()==team && h.getLevel().getScoreboard().getPlayerTeam("manual-team")!=null && team.getPlayers().size()==1,"cleanup preserves manual NPC/team and explicitly joined player");
            NpcTeams.assign(p,null);manual.discard();p.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="match-13-flight",timeoutTicks=240)
    public static void deactivationRevokesAnActuallyLaunchedNativeBullet(GameTestHelper h){
        var s=scene(h);h.runAtTickTime(5,()->{var n=npc(h,s,s.api().spawn(request(h,s,"glock_17")));var p=player(h,40.5);s.policy().players.add(p.getUUID());p.setInvulnerable(true);
            n.moveTo(h.absoluteVec(new Vec3(30.5,2,32.5)));n.setYRot(-90);n.setYHeadRot(-90);n.setOrder(OrderState.Kind.DEFEND_AREA,4);
            var gun=IGun.getIGunOrNull(n.getMainHandItem());gun.setCurrentAmmoCount(n.getMainHandItem(),16);gun.setBulletInBarrel(n.getMainHandItem(),true);
            com.tacz.guns.entity.EntityKineticBullet[] launched={null};
            Consumer<EntityJoinLevelEvent> capture=e->{if(!e.isCanceled() && e.getEntity() instanceof com.tacz.guns.entity.EntityKineticBullet b && b.getOwner()==n)launched[0]=b;};
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,EntityJoinLevelEvent.class,capture);
            h.runAtTickTime(180,()->{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(capture);
                h.assertTrue(launched[0]!=null && ProjectileProbe.count(n.getUUID())>0,"real uncanceled native round required");var bullet=launched[0];var outsider=player(h,44.5);
                var hitDamage=h.getLevel().damageSources().generic();var sources=org.apache.commons.lang3.tuple.Pair.of(hitDamage,hitDamage);var id=new ResourceLocation("tacz:glock_17");
                h.assertFalse(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre(bullet,p,n,id,id,5,sources,false,1,net.minecraftforge.fml.LogicalSide.SERVER)),"active match permits member native damage");
                h.assertTrue(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre(bullet,outsider,n,id,id,5,sources,false,1,net.minecraftforge.fml.LogicalSide.SERVER)),"active match protects outsider at final native hit");
                h.assertTrue(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre(bullet,p,null,id,id,5,sources,false,1,net.minecraftforge.fml.LogicalSide.SERVER)),"orphan match bullet fails closed");
                outsider.discard();s.api().deactivate(s.match());
                var damage=h.getLevel().damageSources().generic();var pre=new com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre(bullet,p,n,new ResourceLocation("tacz:glock_17"),new ResourceLocation("tacz:glock_17"),5,org.apache.commons.lang3.tuple.Pair.of(damage,damage),false,1,net.minecraftforge.fml.LogicalSide.SERVER);
                h.assertTrue(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(pre) && bullet.getPersistentData().hasUUID("tacticalnpc_match"),"actual in-flight round revoked by final native hit guard");
                h.assertTrue(n.controller()==null,"deactivate immediately releases combat owner");p.discard();finish(h,s);
            });
        });
    }
}
