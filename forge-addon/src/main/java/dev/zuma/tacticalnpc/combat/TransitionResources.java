package dev.zuma.tacticalnpc.combat;
import dev.zuma.tacticalnpc.core.TransitionReservations;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
/** Per-level leases and FIFO queues. Values contain identities, never entities or levels. */
public final class TransitionResources {
    private static final Map<ServerLevel,TransitionReservations> LEVELS=new WeakHashMap<>();
    public static TransitionReservations of(ServerLevel level){return LEVELS.computeIfAbsent(level,k->new TransitionReservations());}
    public static void forget(ServerLevel level,UUID id){var table=LEVELS.get(level);if(table!=null)table.forget(id);}
    public static void remove(ServerLevel level){LEVELS.remove(level);}
}
