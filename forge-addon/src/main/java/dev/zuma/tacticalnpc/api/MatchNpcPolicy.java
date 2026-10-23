package dev.zuma.tacticalnpc.api;

import net.minecraft.server.level.ServerPlayer;

/** Server-thread callbacks. Must be fast, side-effect free and perform no I/O or chunk loads.
 * Exceptions fail closed. Rechecked for acquisition, remembered contacts, native rounds and damage. */
public interface MatchNpcPolicy {
    boolean isActive();
    /** Include membership, elimination, disconnect and any TT-specific eligibility here. */
    boolean canAttack(ServerPlayer player);
}
