package dev.zuma.tacticalnpc.api;

import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Public v1 server API shipped in the ordinary main JAR. All calls require the server thread.
 * Match IDs are single-use per save. Policies never persist or resume after restart. */
public interface TacticalNpcMatchApi {
    static TacticalNpcMatchApi get(MinecraftServer server){return MatchNpcRegistry.of(server);}
    /** Register a new ID or replace its policy during this live session. Closed IDs cannot reopen. */
    void registerPolicy(UUID matchId, MatchNpcPolicy policy);
    SpawnResult spawn(SpawnGroupRequest request);
    /** Immediately revoke fire/damage and stop loaded controllers, without loading chunks. */
    void deactivate(UUID matchId);
    /** Deactivate and discard loaded NPCs; unloaded NPCs are discarded on next admission. Idempotent. */
    int remove(UUID matchId);
    /** Alive, loaded NPC identities only; no chunk loads. */
    List<UUID> activeNpcIds(UUID matchId);
    default int activeNpcCount(UUID matchId){return activeNpcIds(matchId).size();}
}
