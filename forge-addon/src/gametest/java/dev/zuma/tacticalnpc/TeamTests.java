package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.combat.CombatTargets;
import dev.zuma.tacticalnpc.combat.SquadRuntime;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.*;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class TeamTests {
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void allyAfterFirstNativeRound(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(event.isCanceled() || event.getLevel().isClientSide || !(event.getEntity() instanceof com.tacz.guns.entity.EntityKineticBullet bullet) ||
                !(bullet.getOwner() instanceof TacticalNpcEntity n) || !n.getTags().contains("team-switch-burst") || n.getTags().contains("team-burst-switched") || n.controller()==null)return;
        var subject=CombatTargets.resolve((net.minecraft.server.level.ServerLevel)n.level(),n.controller().contactId());
        if(subject instanceof net.minecraft.server.level.ServerPlayer){n.addTag("team-burst-switched");NpcTeams.assign(subject,"burst-friends");}
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="team-05-burst",timeoutTicks=180)
    public static void playerJoiningTeamCancelsRemainingNativeBurstThenEnemyCanBeReacquired(GameTestHelper h){
        ScenarioIsolation.begin(h);var n=npc(h,3.5);n.setNoAi(false);n.setYRot(-90);n.setYHeadRot(-90);n.setOrder(OrderState.Kind.ASSAULT,24);n.setFaction("burst-friends");
        n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var stack=dev.zuma.tacticalnpc.compat.GunSupport.create(new net.minecraft.resources.ResourceLocation("tacz:b93r"));var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,20);gun.setBulletInBarrel(stack,true);gun.setFireMode(stack,com.tacz.guns.api.item.gun.FireMode.BURST);
        n.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,stack);n.addTag(RuntimeRegistry.TAG);n.addTag("team-switch-burst");
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"team-burst"));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);p.moveTo(n.getX()+8,n.getY(),n.getZ());h.getLevel().addNewPlayer(p);
        h.runAtTickTime(110,()->{
            h.assertTrue(n.getTags().contains("team-burst-switched") && ProjectileProbe.count(n.getUUID())==1,"first real BURST round joins team; every remaining native round is canceled; projectiles="+ProjectileProbe.count(n.getUUID())+" switched="+n.getTags().contains("team-burst-switched")+" team="+NpcTeams.name(p)+" observation="+(n.controller()==null?null:n.controller().observation())+" yaw="+n.getYHeadRot()+" pos="+n.position()+" target="+p.position());
            h.assertTrue(n.runtime().allied(p) && !n.controller().allowNativeFire(),"allied player's current membership closes native gate");
            NpcTeams.assign(p,null);n.setOrder(OrderState.Kind.ASSAULT,24);
        });
        h.runAtTickTime(165,()->{h.assertTrue(ProjectileProbe.count(n.getUUID())>1,"leaving team restores ordinary legitimate combat, not a disabled weapon");p.discard();n.discard();h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="team-06-pending",timeoutTicks=40)
    public static void deserializedLegacyDataResavesPendingMigrationUntilServerActivation(GameTestHelper h){
        ScenarioIsolation.begin(h);var n=npc(h,3.5);var root=n.saveWithoutId(new CompoundTag());var tag=root.getCompound("tacticalnpc");tag.putInt("schema",4);tag.putString("faction","pending-team");
        h.runAtTickTime(5,()->{
            n.readAdditionalSaveData(root);h.assertTrue(n.getTeam()==null,"NBT decoding does not mutate world scoreboard");
            var pending=n.saveWithoutId(new CompoundTag());h.assertTrue(pending.getCompound("tacticalnpc").getBoolean("pendingLegacyTeam"),"resave before activation retains migration request");
            n.readAdditionalSaveData(pending);n.syncTeam();h.assertTrue(n.faction().equals("pending-team"),"server activation applies exactly one native membership");
            h.assertFalse(n.saveWithoutId(new CompoundTag()).getCompound("tacticalnpc").getBoolean("pendingLegacyTeam"),"completed migration never resurrects a future cleared team");n.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="team-01-alliance",timeoutTicks=40)
    public static void teamlessNpcAndPlayersHaveExplicitAllianceRules(GameTestHelper h) {
        ScenarioIsolation.begin(h);var a=npc(h,3.5);var b=npc(h,7.5);
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"team-player"));p.setGameMode(GameType.SURVIVAL);p.moveTo(a.getX()+12,a.getY(),a.getZ());h.getLevel().addNewPlayer(p);
        h.runAtTickTime(5,()->{
            h.assertTrue(CombatTargets.eligible(a,b) && CombatTargets.eligible(a,p),"teamless NPCs are enemies of each other and survival players");
            NpcTeams.assign(a,"Красные");NpcTeams.assign(b,"Красные");NpcTeams.assign(p,"Красные");
            h.assertTrue(!CombatTargets.eligible(a,b) && !CombatTargets.eligible(a,p),"one native team protects NPCs and players");
            h.assertTrue(a.runtime().squadId()!=null && a.runtime().squadId().equals(b.runtime().squadId()),"same team automatically shares squad");
            NpcTeams.assign(b,"blue");p.setGameMode(GameType.ADVENTURE);NpcTeams.assign(p,"blue");
            h.assertTrue(CombatTargets.eligible(a,b) && CombatTargets.eligible(a,p),"different teams are enemies, including adventure players");
            p.setGameMode(GameType.CREATIVE);h.assertFalse(CombatTargets.eligible(a,p),"creative is never a target");
            p.setGameMode(GameType.SPECTATOR);h.assertFalse(CombatTargets.eligible(a,p),"spectator is never a target");
            NpcTeams.assign(a,null);NpcTeams.assign(b,null);
            h.assertTrue(a.runtime().squadId()==null && b.runtime().squadId()==null && CombatTargets.eligible(a,b),"none is not a shared allied team");
            NpcTeams.assign(p,null);p.discard();a.discard();b.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="team-02-lifecycle",timeoutTicks=45)
    public static void nativeTeamChangesInvalidateFramesReportsAndClaims(GameTestHelper h) {
        ScenarioIsolation.begin(h);var a=npc(h,3.5);var b=npc(h,7.5);a.setNoAi(false);a.addTag(RuntimeRegistry.TAG);
        NpcTeams.assign(a,"team-lifecycle");NpcTeams.assign(b,"team-lifecycle");UUID old=a.runtime().squadId();var board=SquadRuntime.of(h.getLevel());
        h.runAtTickTime(8,()->{
            long tick=h.getLevel().getGameTime();board.heartbeat(old,a.getUUID(),new Vector3(a.getX(),a.getY(),a.getZ()),tick);
            h.assertTrue(board.claim(old,a.getUUID(),new Vector3(5,2,5),tick),"old team owns a real reservation");
            var scoreboard=h.getLevel().getScoreboard();var blue=scoreboard.addPlayerTeam("team-lifecycle-blue");scoreboard.addPlayerToTeam(a.getScoreboardName(),blue);
            h.assertFalse(a.controller().allowNativeFire(),"native round observes vanilla membership change before entity tick");
            h.assertTrue(board.members(old)==0 && board.available(old,b.getUUID(),new Vector3(5,2,5),tick),"old reports/membership/claims removed");
            NpcTeams.assign(a,"team-lifecycle");UUID first=a.runtime().squadId();var team=a.getTeam();scoreboard.removePlayerTeam((net.minecraft.world.scores.PlayerTeam)team);
            NpcTeams.assign(a,"team-lifecycle");h.assertFalse(first.equals(a.runtime().squadId()),"recreated team cannot inherit old radio state");
            a.discard();b.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="team-03-persistence",timeoutTicks=40)
    public static void nativeScoreboardPersistsMembershipAndDoesNotResurrectRemovedTeams(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,3.5);NpcTeams.assign(n,"team-save");
        h.runAtTickTime(5,()->{
            var saved=new net.minecraft.world.scores.ScoreboardSaveData(h.getLevel().getScoreboard()).save(new CompoundTag());
            var restored=new net.minecraft.world.scores.Scoreboard();new net.minecraft.world.scores.ScoreboardSaveData(restored).load(saved);
            h.assertTrue(restored.getPlayersTeam(n.getScoreboardName()).getName().equals("team-save"),"real vanilla team data preserves NPC UUID membership");
            var entity=n.saveWithoutId(new CompoundTag());
            h.getLevel().getScoreboard().removePlayerTeam((net.minecraft.world.scores.PlayerTeam)n.getTeam());
            n.readAdditionalSaveData(entity);
            h.assertTrue(n.getTeam()==null && n.runtime().squadId()==null,"schema5 entity reload cannot resurrect a deleted native team");
            n.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="team-04-migrate",timeoutTicks=50)
    public static void schemasOneThroughFourMigrateWithoutLosingLegacyData(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,3.5);n.setOrder(OrderState.Kind.DEFEND_AREA,128);n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,37);var service=n.serviceId();UUID oldSquad=UUID.randomUUID();
        h.runAtTickTime(5,()->{
            for(int schema=1;schema<=4;schema++){
                var saved=n.saveWithoutId(new CompoundTag());var tag=saved.getCompound("tacticalnpc");tag.putInt("schema",schema);tag.putString("faction","old-red");tag.putUUID("squad",oldSquad);tag.putBoolean("neutral",schema>=3);
                NpcTeams.assign(n,null);n.readAdditionalSaveData(saved);n.syncTeam();
                h.assertTrue(n.faction().equals("old-red") && n.serviceId().equals(service) && n.runtime().orderState().radius()==128 && n.ammoPolicy()==TacticalNpcEntity.AmmoPolicy.FINITE,"schema "+schema+" preserves identity, order and finite policy");
                h.assertTrue(n.neutral()==(schema>=3),"old configured neutral state is not silently activated");
                var migrated=n.saveWithoutId(new CompoundTag()).getCompound("tacticalnpc");
                h.assertTrue(migrated.getInt("schema")==6 && migrated.getString("legacyFaction").equals("old-red") && migrated.getUUID("legacySquad").equals(oldSquad),"legacy faction and squad UUID archived exactly");
                h.assertFalse(migrated.contains("faction") || migrated.contains("squad"),"no second mutable membership in new entity schema");
            }
            n.discard();h.succeed();
        });
    }
    private static TacticalNpcEntity npc(GameTestHelper h,double x){var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(x,2,7.5));n.moveTo(at.x,at.y,at.z);n.setNoAi(true);h.getLevel().addFreshEntity(n);return n;}
}
