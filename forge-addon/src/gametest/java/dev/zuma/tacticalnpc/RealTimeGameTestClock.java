package dev.zuma.tacticalnpc;

import java.util.concurrent.locks.LockSupport;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Test-only: GameTestServer skips tick waits, while TaCZ measures real milliseconds. */
@Mod.EventBusSubscriber(modid = TacticalNpcAiMod.ID)
public final class RealTimeGameTestClock {
    private static long nextTick;
    @SubscribeEvent
    public static void pace(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(ServerLifecycleHooks.getCurrentServer() instanceof GameTestServer)) return;
        // Chunk workers can unpark the server thread early. A single park is not a clock.
        boolean interrupted=Thread.interrupted();
        long remaining;
        while((remaining=nextTick-System.nanoTime())>0) {
            LockSupport.parkNanos(remaining);
            interrupted|=Thread.interrupted();
        }
        nextTick=System.nanoTime()+50_000_000L;
        if(interrupted) Thread.currentThread().interrupt();
    }
}
