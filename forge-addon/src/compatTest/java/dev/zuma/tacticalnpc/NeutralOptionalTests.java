package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.entity.NpcEntities;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class NeutralOptionalTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="neutral-optional-01",timeoutTicks=60)
    public static void verifiedLegacyDamageAuthorizesPersonalResponse(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(at.x,at.y,at.z);n.enterNeutral();h.getLevel().addFreshEntity(n);
        var legacy=RuntimeRegistry.createLegacy(h.getLevel()).entity();legacy.moveTo(at.x+8,at.y,at.z);h.getLevel().addFreshEntity(legacy);
        h.runAtTickTime(5,()->{
            float health=n.getHealth();n.hurt(h.getLevel().damageSources().mobAttack(legacy),1);
            h.assertTrue(n.getHealth()<health && n.reactiveTo(legacy.getUUID()),"pinned optional runtime accepted only after real damage");
            n.discard();legacy.discard();h.succeed();
        });
    }
}
