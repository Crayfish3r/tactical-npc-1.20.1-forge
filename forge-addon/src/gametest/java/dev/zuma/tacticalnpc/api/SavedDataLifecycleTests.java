package dev.zuma.tacticalnpc.api;

import dev.zuma.tacticalnpc.*;
import dev.zuma.tacticalnpc.command.NpcNames;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.NpcTeams;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class SavedDataLifecycleTests {
    private static final MatchNpcPolicy POLICY=new MatchNpcPolicy(){public boolean isActive(){return true;}public boolean canAttack(net.minecraft.server.level.ServerPlayer p){return false;}};
    private static MatchNpcRegistry fresh(GameTestHelper h){
        ScenarioIsolation.begin(h);var storage=h.getLevel().getServer().overworld().getDataStorage();
        storage.set("tacticalnpc_names",new NpcNames());storage.set("tacticalnpc_matches",new MatchNpcRegistry());return MatchNpcRegistry.of(h.getLevel().getServer());
    }
    private static TacticalNpcEntity create(GameTestHelper h){var n=NpcEntities.NPC.get().create(h.getLevel());n.moveTo(h.absoluteVec(new Vec3(3.5,2,7.5)));n.setNoAi(true);return n;}
    private static TacticalNpcEntity member(GameTestHelper h,MatchNpcRegistry registry,UUID match){
        var n=create(h);var group=UUID.randomUUID();n.bindMatch(match,group);
        var team=registry.reserveGroup(match,group,List.of(n));h.assertTrue(h.getLevel().addFreshEntity(n),"native admission");NpcTeams.assign(n,team);return n;
    }
    private static int bytes(CompoundTag tag){
        try{var out=new java.io.ByteArrayOutputStream();NbtIo.write(tag,new java.io.DataOutputStream(out));return out.size();}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
    }
    private static void denied(GameTestHelper h,Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalArgumentException | IllegalStateException ex){rejected=true;}h.assertTrue(rejected,message);}

    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="saved-01-names",timeoutTicks=50)
    public static void thousandTemporaryNamesReuseFreedSlots(GameTestHelper h){
        var registry=fresh(h);var names=NpcNames.of(h.getLevel().getServer());var match=UUID.randomUUID();registry.registerPolicy(match,POLICY);
        var before=names.counts();int size=bytes(names.save(new CompoundTag()));String first=null;
        for(int i=0;i<1000;i++){
            var n=member(h,registry,match);if(first==null)first=n.npcName();h.assertTrue(first.equals(n.npcName()),"freed name reused without growing suffix "+i);
            n.discard();h.assertTrue(names.find(n.npcName())==null && names.counts().equals(before),"all name indexes released at iteration "+i);
            h.assertTrue(registry.counts().members()==0 && registry.counts().loaded()==0,"destroyed UUID/weak reference released");
        }
        h.assertTrue(bytes(names.save(new CompoundTag()))==size,"no serialized name growth");registry.remove(match);
        TacticalNpcAiMod.LOGGER.info("SAVED_DATA_NAMES_STRESS iterations=1000 before={} after={} nbtBytesBefore={} nbtBytesAfter={}",before,names.counts(),size,bytes(names.save(new CompoundTag())));h.succeed();
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="saved-02-matches",timeoutTicks=50)
    public static void thousandMatchesStayBoundedAndIdsNeverReopen(GameTestHelper h){
        var registry=fresh(h);var names=NpcNames.of(h.getLevel().getServer());var before=registry.counts();int size=bytes(registry.save(new CompoundTag()));int nameSize=bytes(names.save(new CompoundTag()));
        var ids=new ArrayList<UUID>();
        for(int i=0;i<1000;i++){
            var id=UUID.randomUUID();ids.add(id);registry.registerPolicy(id,POLICY);member(h,registry,id);
            h.assertTrue(registry.remove(id)==1 && registry.remove(id)==0,"remove idempotent "+i);
            h.assertTrue(registry.counts().equals(before) && names.counts().temporary()==0 && names.counts().loaded()==0,"no per-match retained state "+i);
        }
        var saved=registry.save(new CompoundTag());h.assertTrue(bytes(saved)==size && bytes(names.save(new CompoundTag()))==nameSize,"bounded serialized registries");
        registry.setDirty(false);registry.remove(ids.get(0));h.assertFalse(registry.isDirty(),"repeated cleanup does not dirty unchanged SavedData");
        for(var id:ids)denied(h,()->registry.registerPolicy(id,POLICY),"closed ID rejected in same session");
        h.getLevel().getServer().overworld().getDataStorage().set("tacticalnpc_matches",MatchNpcRegistry.load(saved));var reloaded=MatchNpcRegistry.of(h.getLevel().getServer());reloaded.startup();
        for(var id:ids)denied(h,()->reloaded.registerPolicy(id,POLICY),"closed ID rejected after roundtrip");
        h.assertTrue(h.getLevel().getScoreboard().getPlayerTeams().isEmpty(),"1000 teams cleaned");
        TacticalNpcAiMod.LOGGER.info("SAVED_DATA_MATCH_STRESS iterations=1000 before={} after={} nbtBytesBefore={} nbtBytesAfter={} nameBytesBefore={} nameBytesAfter={}",before,reloaded.counts(),size,bytes(saved),nameSize,bytes(names.save(new CompoundTag())));h.succeed();
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="saved-03-unload",timeoutTicks=60)
    public static void unloadKeepsNameThenClosedUnknownBindingCannotRevive(GameTestHelper h){
        var registry=fresh(h);var match=UUID.randomUUID();registry.registerPolicy(match,POLICY);var n=member(h,registry,match);var names=NpcNames.of(h.getLevel().getServer());String name=n.npcName();var saved=n.saveWithoutId(new CompoundTag());
        n.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.assertTrue(names.find(name)!=null && names.counts().temporary()==1 && names.counts().loaded()==0 && registry.counts().members()==1,"unload preserves ownership and name only");
        h.runAtTickTime(5,()->{
            var copy=create(h);copy.load(saved);h.getLevel().addFreshEntity(copy);h.assertTrue(!copy.isRemoved() && name.equals(copy.npcName()) && !copy.isCustomNameVisible(),"same live match/name restored hidden");
            copy.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);registry.remove(match);h.assertTrue(names.find(name)==null && registry.counts().matches()==0,"cleanup releases even unloaded reservations");
            h.runAtTickTime(10,()->{
                var stale=create(h);stale.load(saved);h.getLevel().addFreshEntity(stale);h.assertTrue(stale.isRemoved() && stale.ownerMatchId().equals(match),"pruned/unknown match stays closed");
                for(var marker:List.<Tag>of(ByteTag.valueOf(true),ByteTag.valueOf(false),StringTag.valueOf("damaged"))){
                    var missing=saved.copy();missing.getCompound("tacticalnpc").remove("ownerMatchId");missing.getCompound("tacticalnpc").remove("groupId");missing.getCompound("tacticalnpc").put("matchOwned",marker);var invalid=create(h);invalid.load(missing);h.getLevel().addFreshEntity(invalid);h.assertTrue(invalid.invalidMatchBinding() && invalid.isRemoved(),"marker presence prevents missing fields becoming manual even if marker is damaged");
                }
                h.succeed();
            });
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="saved-04-death",timeoutTicks=60)
    public static void naturalDeathReleasesTemporaryButKeepsAdministratorIdentity(GameTestHelper h){
        var registry=fresh(h);var match=UUID.randomUUID();registry.registerPolicy(match,POLICY);var n=member(h,registry,match);String name=n.npcName();n.kill();
        var manual=create(h);h.getLevel().addFreshEntity(manual);String persistent=manual.npcName();manual.kill();
        h.runAtTickTime(30,()->{var names=NpcNames.of(h.getLevel().getServer());h.assertTrue(n.isRemoved() && names.find(name)==null && names.find(persistent)!=null && names.counts().temporary()==0 && registry.counts().members()==0,"natural death removes only temporary identity/member");registry.remove(match);h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="saved-05-migration",timeoutTicks=50)
    public static void schemaOneUsesProvenMembersAndPreservesUnknownNames(GameTestHelper h){
        var registry=fresh(h);var id=UUID.randomUUID();registry.registerPolicy(id,POLICY);var n=member(h,registry,id);String temporary=n.npcName();var manual=create(h);h.getLevel().addFreshEntity(manual);String name=manual.npcName();
        var names=NpcNames.of(h.getLevel().getServer()).save(new CompoundTag());names.putInt("schema",1);for(var entry:names.getList("entries",Tag.TAG_COMPOUND))((CompoundTag)entry).remove("ownerMatchId");
        var matches=registry.save(new CompoundTag());matches.putInt("schema",1);matches.remove("usedIds");matches.remove("usedIdsSha256");
        n.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);manual.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var storage=h.getLevel().getServer().overworld().getDataStorage();storage.set("tacticalnpc_names",NpcNames.load(names));storage.set("tacticalnpc_matches",MatchNpcRegistry.load(matches));var loaded=MatchNpcRegistry.of(h.getLevel().getServer());loaded.startup();var migrated=NpcNames.of(h.getLevel().getServer());
        h.assertTrue(migrated.find(temporary)==null && migrated.find(name).id().equals(manual.getUUID()) && migrated.counts().permanent()==1,"only authoritative schema1 group UUID is temporary; unknown provenance retained");
        h.assertTrue(migrated.save(new CompoundTag()).getInt("schema")==2 && loaded.save(new CompoundTag()).getInt("schema")==2 && loaded.counts().matches()==0,"explicit schema2 migration and pruning");denied(h,()->loaded.registerPolicy(id,POLICY),"migrated ID never reopens");h.succeed();
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="saved-06-corrupt",timeoutTicks=45)
    public static void malformedRegistriesFailClosedWithSchemaDiagnostics(GameTestHelper h){
        fresh(h);
        for(int schema:new int[]{0,1,2,99}){
            var bad=new CompoundTag();bad.putInt("schema",schema);bad.putString("entries","broken");
            try{NpcNames.load(bad);h.fail("corrupt names accepted");}catch(IllegalStateException ex){h.assertTrue(ex.getMessage().contains("tacticalnpc_names schema "+schema),"registry/schema diagnostic");}
            bad.putString("matches","broken");try{MatchNpcRegistry.load(bad);h.fail("corrupt matches accepted");}catch(IllegalStateException ex){h.assertTrue(ex.getMessage().contains("tacticalnpc_matches schema "+schema),"match registry/schema diagnostic");}
        }
        var saved=MatchNpcRegistry.of(h.getLevel().getServer()).save(new CompoundTag());var bits=saved.getLongArray("usedIds");bits[0]^=1;saved.putLongArray("usedIds",bits);denied(h,()->MatchNpcRegistry.load(saved),"bit corruption cannot reopen old matches");
        // Exercise the actual Minecraft exception-swallowing path against an isolated disk file.
        var server=h.getLevel().getServer();String id="tacticalnpc_names_corrupt_probe";
        var file=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve(id+".dat");
        try{
            java.nio.file.Files.createDirectories(file.getParent());byte[] damaged={1,2,3,4};java.nio.file.Files.write(file,damaged);
            denied(h,()->server.overworld().getDataStorage().computeIfAbsent(NpcNames::load,()->dev.zuma.tacticalnpc.command.StrictSavedData.create(server,id,NpcNames::new),id),"native IO failure cannot allocate an empty registry");
            h.assertTrue(Arrays.equals(damaged,java.nio.file.Files.readAllBytes(file)),"corrupt disk file preserved byte-for-byte");
        }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
        h.succeed();
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="saved-07-teams",timeoutTicks=45)
    public static void hiddenGroupNamesPreserveAllianceAndForeignTeams(GameTestHelper h){
        var registry=fresh(h);var match=UUID.randomUUID();registry.registerPolicy(match,POLICY);var a=create(h);var b=create(h);var group=UUID.randomUUID();a.bindMatch(match,group);b.bindMatch(match,group);String team=registry.reserveGroup(match,group,List.of(a,b));
        h.getLevel().addFreshEntity(a);h.getLevel().addFreshEntity(b);NpcTeams.assign(a,team);NpcTeams.assign(b,team);
        h.assertTrue(a.getTeam()==b.getTeam() && a.getTeam().getNameTagVisibility()==Team.Visibility.NEVER && NpcTeams.allied(a,b) && !a.isCustomNameVisible() && !b.isCustomNameVisible(),"hidden shared team and allies");
        var board=h.getLevel().getScoreboard();var owned=board.getPlayerTeam(team);owned.setAllowFriendlyFire(false);float health=b.getHealth();
        h.assertFalse(b.hurt(h.getLevel().damageSources().mobAttack(a),4),"allied damage blocked");h.assertTrue(b.getHealth()==health && !owned.isAllowFriendlyFire(),"friendly fire preserved");
        var foreign=board.addPlayerTeam("admin-visible");var manual=create(h);h.getLevel().addFreshEntity(manual);NpcTeams.assign(manual,foreign.getName());h.assertTrue(foreign.getNameTagVisibility()==Team.Visibility.ALWAYS,"administrator team not mutated");
        registry.remove(match);h.assertTrue(board.getPlayerTeam(foreign.getName())==foreign && !manual.isRemoved(),"manual independence");manual.discard();h.succeed();
    }
}
