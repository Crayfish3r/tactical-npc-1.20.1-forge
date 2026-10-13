package dev.zuma.tacticalnpc.core;

/** High-level action; the adapter may never interpret an intent as direct projectile spawn. */
public enum TacticalIntent {
    PATROL, SEARCH, ENGAGE, TAKE_COVER, HOLD, PEEK, FLANK, ADVANCE, REPOSITION, RELOAD, BOLT, RETREAT
}
