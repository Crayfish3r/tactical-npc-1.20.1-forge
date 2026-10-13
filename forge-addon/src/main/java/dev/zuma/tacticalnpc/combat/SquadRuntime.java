package dev.zuma.tacticalnpc.combat;
import dev.zuma.tacticalnpc.core.SquadBlackboard;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
/** Weak level ownership; board values contain immutable coordinates and UUIDs only. */
public final class SquadRuntime {
    private static final Map<ServerLevel,SquadBlackboard> LEVELS=new WeakHashMap<>();
    public static SquadBlackboard of(ServerLevel level){return LEVELS.computeIfAbsent(level,k->new SquadBlackboard());}
    public static void remove(ServerLevel level){LEVELS.remove(level);}
    private SquadRuntime(){}
}
