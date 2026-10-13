package dev.zuma.tacticalnpc.combat;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;

/** Preserve pitch between ticks so vanilla's turn limit works for elevated targets too. */
public final class TacticalLookControl extends LookControl {
    public TacticalLookControl(Mob mob) { super(mob); }
    @Override protected boolean resetXRotOnTick() { return false; }
    @Override public void tick() {
        float yaw=mob.getYHeadRot(),pitch=mob.getXRot();
        super.tick();
        if(!mob.level().isClientSide) {
            // Vanilla's navigation/body clamp runs after rotateTowards and can otherwise snap
            // the head beyond the requested rate when walking away from the known threat.
            float rate=dev.zuma.tacticalnpc.config.ServerConfig.TURN.get().floatValue();
            mob.setYHeadRot(net.minecraft.util.Mth.approachDegrees(yaw,mob.getYHeadRot(),rate));
            mob.setXRot(net.minecraft.util.Mth.approachDegrees(pitch,mob.getXRot(),rate));
        }
    }
}
