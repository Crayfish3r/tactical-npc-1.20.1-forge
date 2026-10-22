package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.command.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.NpcTeams;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class NpcNameTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="names-pool",timeoutTicks=50)
    public static void moreThanFiftyNamesAndRegistrySaveLoad(GameTestHelper h){
        ScenarioIsolation.begin(h);var all=new ArrayList<TacticalNpcEntity>();var used=new HashSet<String>();
        for(int i=0;i<65;i++){
            var npc=create(h);npc.addTag("names-pool-info");h.getLevel().addFreshEntity(npc);all.add(npc);
            h.assertTrue(NpcNames.valid(npc.npcName()) && used.add(npc.npcName().toLowerCase(Locale.ROOT)),"65 globally unique nonnumeric names");
            h.assertTrue(npc.hasCustomName() && npc.isCustomNameVisible() && npc.getCustomName().getString().equals(npc.npcName()),"visible persistent user name");
        }
        var store=NpcNames.of(h.getLevel().getServer());var saved=store.save(new CompoundTag());var reloaded=NpcNames.load(saved);
        h.assertTrue(all.stream().anyMatch(n->n.npcName().matches(".*[2-9][0-9]*")),"suffixes after base pool");
        for(var npc:all)h.assertTrue(reloaded.find(npc.npcName().toUpperCase(Locale.ROOT)).id().equals(npc.getUUID()) && reloaded.resolve(h.getLevel().getServer(),npc.getUUID())==null,"registry persists assignments but never a loaded entity index");
        try{h.assertTrue(h.getLevel().getServer().getCommands().getDispatcher().execute("npc info @e[type=tacticalnpcai:npc,tag=names-pool-info]",h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withSuppressedOutput())==65,"info accepts the entire valid group, including more than32 selected NPCs");}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){h.fail(ex.toString());}
        all.forEach(Entity::discard);h.succeed();
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="names-migrate-clone",timeoutTicks=55)
    public static void oldNbtClonesNumericNamesAndTeamMembership(GameTestHelper h){
        ScenarioIsolation.begin(h);var original=create(h);NpcTeams.assign(original,"names-team");h.getLevel().addFreshEntity(original);
        var saved=new CompoundTag();original.save(saved);var service=original.serviceId();
        for(String claimed:List.of(original.npcName(),original.npcName().toUpperCase(Locale.ROOT),"123")){
            var copy=saved.copy();copy.putUUID("UUID",UUID.randomUUID());copy.getCompound("tacticalnpc").putString("name",claimed);
            var npc=(TacticalNpcEntity)EntityType.loadEntityRecursive(copy,h.getLevel(),e->e);h.getLevel().addFreshEntity(npc);
            h.assertTrue(NpcNames.valid(npc.npcName()) && !npc.npcName().equalsIgnoreCase(original.npcName()),"cloned/case duplicate/numeric NBT receives unused name");npc.discard();
        }
        original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.runAtTickTime(5,()->{
            var tag=saved.getCompound("tacticalnpc");tag.putInt("schema",5);tag.remove("name");
            var restored=(TacticalNpcEntity)EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);h.getLevel().addFreshEntity(restored);
            h.assertTrue(restored.npcName().equals(original.npcName()) && restored.serviceId().equals(service) && restored.faction().equals("names-team"),"registry restores old NBT identity and keeps UUID scoreboard membership");
            var brandNew=saved.copy();brandNew.putUUID("UUID",UUID.randomUUID());var migrated=(TacticalNpcEntity)EntityType.loadEntityRecursive(brandNew,h.getLevel(),e->e);h.getLevel().addFreshEntity(migrated);
            h.assertTrue(NpcNames.valid(migrated.npcName()) && !migrated.npcName().equalsIgnoreCase(restored.npcName()),"unnamed legacy identity migrates on admission");
            restored.discard();migrated.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="names-unloaded",timeoutTicks=55)
    public static void unloadedNameFailsWithoutQueueAndReloadRestores(GameTestHelper h){
        ScenarioIsolation.begin(h);var npc=create(h);h.getLevel().addFreshEntity(npc);var saved=new CompoundTag();npc.save(saved);
        var names=NpcNames.of(h.getLevel().getServer());var name=npc.npcName();npc.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.runAtTickTime(5,()->{
            var server=h.getLevel().getServer();var dispatcher=server.getCommands().getDispatcher();var source=server.createCommandSourceStack().withLevel(h.getLevel()).withSuppressedOutput();
            var forced=new HashSet<>(h.getLevel().getForcedChunks());
            try{dispatcher.execute("npc mode "+name+" assault",source);h.fail("unloaded name accepted");}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){h.assertTrue(ex.getMessage().contains("unloaded"),"explicit unloaded explanation");}
            h.assertTrue(names.resolve(server,npc.getUUID())==null && forced.equals(h.getLevel().getForcedChunks()),"no entity retention or chunk tickets");
            var newcomer=create(h);h.getLevel().addFreshEntity(newcomer);h.assertTrue(!newcomer.npcName().equalsIgnoreCase(name),"unloaded name cannot be reused");
            var restored=(TacticalNpcEntity)EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);h.getLevel().addFreshEntity(restored);
            h.assertTrue(restored.npcName().equals(name) && restored.order().equals("HOLD_POSITION"),"reload restores name and has no queued assault");
            newcomer.discard();restored.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="names-cancel",timeoutTicks=35)
    public static void canceledSpawnConsumesNoReservation(GameTestHelper h){
        ScenarioIsolation.begin(h);var npc=create(h);var names=NpcNames.of(h.getLevel().getServer());int before=names.entries().size();
        var listener=new CancelJoin(npc.getUUID());net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(listener);
        try{h.assertFalse(h.getLevel().addFreshEntity(npc),"join canceled");}
        finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);}
        h.assertTrue(names.entries().size()==before && npc.npcName().isEmpty(),"failed admission leaves no name allocation");h.succeed();
    }
    public static final class CancelJoin {
        private final UUID id;CancelJoin(UUID id){this.id=id;}
        @SubscribeEvent public void cancel(EntityJoinLevelEvent event){if(event.getEntity().getUUID().equals(id))event.setCanceled(true);}
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="names-retrack",timeoutTicks=40)
    public static void sameInstanceTrackingRestartKeepsNameAddressable(GameTestHelper h){
        ScenarioIsolation.begin(h);var npc=create(h);h.getLevel().addFreshEntity(npc);String name=npc.npcName();
        h.runAtTickTime(5,()->{
            try{
                var field=net.minecraft.server.level.ServerLevel.class.getDeclaredField("entityManager");field.setAccessible(true);
                @SuppressWarnings("unchecked") var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<Entity>)field.get(h.getLevel());
                var chunk=new net.minecraft.world.level.ChunkPos(npc.blockPosition());
                manager.updateChunkStatus(chunk,net.minecraft.server.level.FullChunkStatus.INACCESSIBLE);
                h.assertTrue(!npc.isRemoved(),"tracking loss precedes actual entity unload");
                manager.updateChunkStatus(chunk,net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);
                h.assertTrue(h.getLevel().getEntity(npc.getUUID())==npc && NpcNames.of(h.getLevel().getServer()).resolve(h.getLevel().getServer(),npc.getUUID())==npc,"native retracking without a new admission keeps index");
                h.assertTrue(h.getLevel().getServer().getCommands().getDispatcher().execute("npc info "+name,h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withSuppressedOutput())==1,"same named instance remains command-addressable");
            }catch(ReflectiveOperationException | com.mojang.brigadier.exceptions.CommandSyntaxException ex){h.fail(ex.toString());}
            npc.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="names-dimension",timeoutTicks=80)
    public static void dimensionTransferKeepsNameAndConsoleResolution(GameTestHelper h){
        ScenarioIsolation.begin(h);var npc=create(h);h.getLevel().addFreshEntity(npc);NpcTeams.assign(npc,"name-dimension");String name=npc.npcName();
        h.runAtTickTime(5,()->{
            var server=h.getLevel().getServer();var nether=server.getLevel(Level.NETHER);
            var moved=(TacticalNpcEntity)npc.changeDimension(nether,new net.minecraftforge.common.util.ITeleporter(){
                @Override public net.minecraft.world.level.portal.PortalInfo getPortalInfo(Entity entity,net.minecraft.server.level.ServerLevel destination,java.util.function.Function<net.minecraft.server.level.ServerLevel,net.minecraft.world.level.portal.PortalInfo> fallback){return new net.minecraft.world.level.portal.PortalInfo(new Vec3(8,110,8),Vec3.ZERO,0,0);}
            });
            h.assertTrue(moved!=null && moved.npcName().equals(name) && moved.faction().equals("name-dimension") && NpcNames.of(server).resolve(server,moved.getUUID())==moved,"name/index/team survive dimension transfer: moved="+moved+" name="+(moved==null?null:moved.npcName())+" expected="+name+" team="+(moved==null?null:moved.faction())+" resolved="+(moved==null?null:NpcNames.of(server).resolve(server,moved.getUUID())));
            try{h.assertTrue(server.getCommands().getDispatcher().execute("npc mode "+name+" defend 19",server.createCommandSourceStack().withLevel(h.getLevel()).withSuppressedOutput())==1 && moved.runtime().orderState().radius()==19,"console selects different dimension by name");}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){h.fail(ex.getMessage());}
            moved.discard();h.succeed();
        });
    }
    private static TacticalNpcEntity create(GameTestHelper h){var npc=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(3.5,2,7.5));npc.moveTo(at.x,at.y,at.z);npc.setNoAi(true);return npc;}
}
