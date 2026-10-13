package dev.zuma.tacticalnpc.npcs;

import dev.zuma.tacticalnpc.TacticalNpcAiMod;
import dev.zuma.tacticalnpc.combat.TacticalCombatGoal;
import dev.zuma.tacticalnpc.combat.TacticalLookControl;
import dev.zuma.tacticalnpc.config.ServerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Swap schedulers before Mob.serverAiStep; retain the original weapon maintenance tick. */
public final class Npc210Adapter {
    public static final String TAG = "tactical_ai";
    // Weak keys AND weak values: state goals contain the entity, so values must not be strong.
    private final Map<AutoGunOperator, java.lang.ref.WeakReference<Ownership>> owned = new WeakHashMap<>();
    // Ownership lives with the entity's installed goal, never in a global entity registry.
    private final Map<AutoGunOperator, Boolean> failed = new WeakHashMap<>();

    public Npc210Adapter() {
        dev.zuma.tacticalnpc.runtime.RuntimeRegistry.register(e -> e.getClass()==AutoGunOperator.class ? runtime((AutoGunOperator)e) : null,
                level -> runtime(AutoGunOperator.create(level,"default")));
    }
    private static dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter runtime(AutoGunOperator npc) {
        return new dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter() {
            public AutoGunOperator entity() { return npc; }
            public boolean allied(net.minecraft.world.entity.LivingEntity other) {
                return npc.isAlliedTo(other) || npc.isAlliedTo((net.minecraft.world.entity.Entity)other);
            }
        };
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void tick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof AutoGunOperator npc) || npc.level().isClientSide) return;
        requireServer(npc);
        Ownership state = owned.containsKey(npc) ? owned.get(npc).get() : null;
        boolean enabled = ServerConfig.ENABLED.get() && npc.getTags().contains(TAG) && npc.isAlive();
        if (!enabled) {
            failed.remove(npc);
            if (state != null) release(npc, state);
            return;
        }
        if (state != null) {
            // No base target: AutoGunOperator.tick has a hardcoded melee/look path outside goals.
            npc.setTarget(null);
            if (state.combat.failed()) {
                release(npc, state);
                failed.put(npc, true);
            }
            return;
        }
        if (failed.containsKey(npc)) return;
        Ownership claim = null;
        try {
            if (npc.getClass() != AutoGunOperator.class || npc.fireAtNearestTargetGoal == null)
                throw new IllegalStateException("Unsupported NPC implementation");
            claim = new Ownership(npc);
            clear(npc);
            npc.setTarget(null);
            npc.getNavigation().stop();
            npc.setAggressive(false);
            npc.lookControl = new TacticalLookControl(npc);
            npc.bodyRotationControl = new dev.zuma.tacticalnpc.combat.TacticalBodyControl(npc,claim.body);
            npc.goalSelector.addGoal(0, new FloatGoal(npc));
            npc.goalSelector.addGoal(1, claim.combat);
            owned.put(npc, new java.lang.ref.WeakReference<>(claim));
            dev.zuma.tacticalnpc.runtime.RuntimeRegistry.activate(npc,claim.combat);
            TacticalNpcAiMod.LOGGER.info("Tactical control acquired for {}: base goals suspended", npc.getUUID());
        } catch (RuntimeException | LinkageError ex) {
            if (claim != null) release(npc, claim);
            failed.put(npc, true);
            TacticalNpcAiMod.LOGGER.error("Tactical adapter rejected {}; base AI retained", npc.getUUID(), ex);
        }
    }

    @SubscribeEvent
    public void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof AutoGunOperator npc && !event.getLevel().isClientSide) {
            Ownership state = owned.containsKey(npc) ? owned.get(npc).get() : null;
            if (state != null) release(npc, state);
            failed.remove(npc);
        }
    }

    private void release(AutoGunOperator npc, Ownership state) {
        clear(npc);
        state.combat.stop();
        npc.lookControl = state.look;
        npc.bodyRotationControl = state.body;
        for (WrappedGoal g : state.goals) npc.goalSelector.addGoal(g.getPriority(), g.getGoal());
        for (WrappedGoal g : state.targets) npc.targetSelector.addGoal(g.getPriority(), g.getGoal());
        npc.setTarget(null);
        owned.remove(npc);
        dev.zuma.tacticalnpc.runtime.RuntimeRegistry.release(npc);
        TacticalNpcAiMod.LOGGER.info("Tactical control released for {}; base goals restored", npc.getUUID());
    }

    private static void clear(AutoGunOperator npc) {
        for (WrappedGoal g : new ArrayList<>(npc.goalSelector.getAvailableGoals())) npc.goalSelector.removeGoal(g.getGoal());
        for (WrappedGoal g : new ArrayList<>(npc.targetSelector.getAvailableGoals())) npc.targetSelector.removeGoal(g.getGoal());
    }

    public static void requireServer(AutoGunOperator npc) {
        if (npc.level().isClientSide || npc.getServer() == null || !npc.getServer().isSameThread())
            throw new IllegalStateException("NPC mutation requires the Minecraft server thread");
    }

    private static final class Ownership {
        final List<WrappedGoal> goals, targets;
        final TacticalCombatGoal combat;
        final net.minecraft.world.entity.ai.control.LookControl look;
        final net.minecraft.world.entity.ai.control.BodyRotationControl body;
        Ownership(AutoGunOperator npc) {
            goals = List.copyOf(npc.goalSelector.getAvailableGoals());
            targets = List.copyOf(npc.targetSelector.getAvailableGoals());
            look = npc.getLookControl();
            body = npc.bodyRotationControl;
            combat = new TacticalCombatGoal(runtime(npc), this);
        }
    }
}
