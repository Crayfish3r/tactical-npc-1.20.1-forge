package dev.zuma.tacticalnpc;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
/** Test-only reset between sequential batches, including leftovers after failed assertions. */
public final class ScenarioIsolation {
    private static final java.util.List<net.minecraft.world.level.ChunkPos> ADDED_CHUNKS=new java.util.ArrayList<>();
    private static net.minecraft.server.level.ServerLevel forcedLevel;
    public static void begin(GameTestHelper h) {
        if(!(h.getLevel().getServer() instanceof GameTestServer)) throw new IllegalStateException("Isolated harness requires GameTestServer");
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,h.getLevel().getServer());
        dev.zuma.tacticalnpc.config.ServerConfig.DEBUG.set(false);
        dev.zuma.tacticalnpc.config.ServerConfig.DIFFICULTY.set(dev.zuma.tacticalnpc.config.ServerConfig.Difficulty.HARD);
        // Historical temporal fixtures explicitly use a short memory; upgrade fixtures apply Hard/320.
        dev.zuma.tacticalnpc.config.ServerConfig.MEMORY.set(160);
        // Native projectiles/items also belong to the previous fixture; an in-flight enemy
        // round may legally outlive its owner and must not damage the next fixture's NPC.
        java.util.List<net.minecraft.world.entity.Entity> leftovers=new java.util.ArrayList<>();
        h.getLevel().getAllEntities().forEach(leftovers::add);
        leftovers.forEach(net.minecraft.world.entity.Entity::discard);
        // Teams are now native persistent state; one fixture must not ally the next fixture's players.
        var scoreboard=h.getLevel().getScoreboard();
        for(var team:new java.util.ArrayList<>(scoreboard.getPlayerTeams()))scoreboard.removePlayerTeam(team);
        // Pinned StructureUtils.forceLoadChunks forces only a small area around the origin,
        // not every chunk of a 64x64 fixture. A loaded block is not an entity-ticking chunk.
        net.minecraft.world.phys.AABB bounds;
        try {
            var method=GameTestHelper.class.getDeclaredMethod("getBounds");method.setAccessible(true);
            bounds=(net.minecraft.world.phys.AABB)method.invoke(h);
        } catch(ReflectiveOperationException ex){throw new IllegalStateException("Pinned GameTestHelper.getBounds unavailable",ex);}
        var required=new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        for(int x=net.minecraft.util.Mth.floor(bounds.minX/16);x<=net.minecraft.util.Mth.floor((bounds.maxX-.001)/16);x++)
            for(int z=net.minecraft.util.Mth.floor(bounds.minZ/16);z<=net.minecraft.util.Mth.floor((bounds.maxZ-.001)/16);z++)
                required.add(new net.minecraft.world.level.ChunkPos(x,z));
        // Adjacent batches share chunks. Keep their ticket continuously instead of an
        // unload/reload transition while the next fixture's entities are being added.
        var retained=new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        if(forcedLevel!=null)for(var chunk:ADDED_CHUNKS){
            if(forcedLevel==h.getLevel() && required.contains(chunk))retained.add(chunk);
            else forcedLevel.setChunkForced(chunk.x,chunk.z,false);
        }
        ADDED_CHUNKS.clear();ADDED_CHUNKS.addAll(retained);forcedLevel=h.getLevel();
        for(var chunk:required)if(h.getLevel().setChunkForced(chunk.x,chunk.z,true))ADDED_CHUNKS.add(chunk);
        var config=dev.zuma.tacticalnpc.config.ServerConfig.STRAFE;config.set(.55);
        dev.zuma.tacticalnpc.config.ServerConfig.PERCEPTION.set(1);dev.zuma.tacticalnpc.config.ServerConfig.REACTION.set(3);
        dev.zuma.tacticalnpc.config.ServerConfig.REACQUIRE.set(1);dev.zuma.tacticalnpc.config.ServerConfig.ACQUISITION.set(3);dev.zuma.tacticalnpc.config.ServerConfig.TURN.set(17.0);dev.zuma.tacticalnpc.config.ServerConfig.SUPPRESSION.set(true);
    }
    private ScenarioIsolation() {}
}
