package dev.zuma.tacticalnpc.combat;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;

/** Retain vanilla body motion; the tactical LookControl alone owns server head rotation. */
public final class TacticalBodyControl extends BodyRotationControl {
    private final Mob npc;
    private final BodyRotationControl original;
    public TacticalBodyControl(Mob npc,BodyRotationControl original) {
        super(npc);this.npc=npc;this.original=original;
    }
    @Override public void clientTick() {
        float head=npc.getYHeadRot();
        original.clientTick();
        if(!npc.level().isClientSide) npc.setYHeadRot(head);
    }
}
