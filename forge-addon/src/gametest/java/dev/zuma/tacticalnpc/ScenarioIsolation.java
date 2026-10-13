package dev.zuma.tacticalnpc;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
/** Test-only reset between sequential batches, including leftovers after failed assertions. */
public final class ScenarioIsolation {
    public static void begin(GameTestHelper h) {
        if(!(h.getLevel().getServer() instanceof GameTestServer)) throw new IllegalStateException("Isolated harness requires GameTestServer");
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,h.getLevel().getServer());
        java.util.List<LivingEntity> leftovers=new java.util.ArrayList<>();
        h.getLevel().getAllEntities().forEach(e->{ if(e instanceof LivingEntity living) leftovers.add(living); });
        leftovers.forEach(LivingEntity::discard);
        var config=dev.zuma.tacticalnpc.config.ServerConfig.STRAFE;config.set(.55);
        dev.zuma.tacticalnpc.config.ServerConfig.PERCEPTION.set(1);dev.zuma.tacticalnpc.config.ServerConfig.REACTION.set(3);
        dev.zuma.tacticalnpc.config.ServerConfig.REACQUIRE.set(1);dev.zuma.tacticalnpc.config.ServerConfig.ACQUISITION.set(3);dev.zuma.tacticalnpc.config.ServerConfig.TURN.set(17.0);dev.zuma.tacticalnpc.config.ServerConfig.SUPPRESSION.set(true);
    }
    private ScenarioIsolation() {}
}
