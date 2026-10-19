package dev.zuma.tacticalnpc;
import dev.zuma.tacticalnpc.entity.NpcEntities;
import dev.zuma.tacticalnpc.config.ServerConfig;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
@PrefixGameTestTemplate(false)
public final class LookControlTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="look-01-navigation-clamp",timeoutTicks=80)
    public static void navigationCannotSnapAim(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(8.5,2,8.5));n.moveTo(at.x,at.y,at.z,180,0);n.setYHeadRot(0);h.getLevel().addFreshEntity(n);
        h.runAtTickTime(5,()->{
            var destination=h.absoluteVec(new Vec3(8.5,2,3.5));
            h.assertTrue(n.getNavigation().moveTo(destination.x,destination.y,destination.z,1),"active native path fixture");
            n.setYBodyRot(180);n.setYHeadRot(0);
            n.getLookControl().setLookAt(at.x,n.getEyeY(),at.z+5,ServerConfig.TURN.get().floatValue(),ServerConfig.TURN.get().floatValue());
        });
        h.runAtTickTime(6,()->{
            h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(n.getYHeadRot()))<=ServerConfig.TURN.get()+.01,"actual native navigation/body clamp respects head turn rate: "+n.getYHeadRot());
            n.discard();h.succeed();
        });
    }
}
