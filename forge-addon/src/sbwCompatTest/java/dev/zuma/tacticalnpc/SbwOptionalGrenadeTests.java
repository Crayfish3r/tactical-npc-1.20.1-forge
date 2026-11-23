package dev.zuma.tacticalnpc;

import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Verifies the shared controller on the pinned NPCs adapter. */
@PrefixGameTestTemplate(false)
public final class SbwOptionalGrenadeTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="sbw-02-optional",timeoutTicks=310)
    public static void optionalNpcUsesNativeRgo(GameTestHelper h) {
        ScenarioIsolation.begin(h);
        SbwGrenadeTests.run(h,AutoGunOperator.create(h.getLevel(),"default"),1);
    }
}
