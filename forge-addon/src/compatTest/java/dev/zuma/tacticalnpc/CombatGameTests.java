package dev.zuma.tacticalnpc;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import dev.zuma.tacticalnpc.combat.TacticalCombatGoal;
import dev.zuma.tacticalnpc.combat.VisibilitySensor;
import dev.zuma.tacticalnpc.npcs.Npc210Adapter;
import dev.zuma.tacticalnpc.config.ServerConfig;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.entity.goal.FireAtNearestTargetGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class CombatGameTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template = "arena", timeoutTicks = 600)
    public static void combatLifecycle(GameTestHelper h) {
        ScenarioIsolation.begin(h);
        ServerConfig.DEBUG.set(true);
        AutoGunOperator npc = AutoGunOperator.create(h.getLevel(), "default");
        Vec3 origin = h.absoluteVec(new Vec3(3.5, 2, 7.5));
        npc.moveTo(origin.x, origin.y, origin.z, -90, 0);
        npc.setYHeadRot(-90);
        npc.setYBodyRot(-90);
        npc.setPersistenceRequired();
        var stack = GunItemBuilder.create().setId(new ResourceLocation("tacz", "ak47"))
                .setFireMode(FireMode.SEMI).setAmmoCount(1).setAmmoInBarrel(true).build();
        npc.setItemSlot(EquipmentSlot.MAINHAND, stack);
        npc.addTag(Npc210Adapter.TAG);
        h.getLevel().addFreshEntity(npc);
        float[] lastAngles = {npc.getYHeadRot(), npc.getXRot()};
        h.onEachTick(() -> {
            if (goal(npc) != null && goal(npc).targetId() != null) {
                h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(npc.getYHeadRot() - lastAngles[0])) <= ServerConfig.TURN.get() + 0.01,
                        "bounded horizontal turn: " + lastAngles[0] + " -> " + npc.getYHeadRot());
                h.assertTrue(Math.abs(npc.getXRot() - lastAngles[1]) <= ServerConfig.TURN.get() + 0.01, "bounded vertical turn");
            }
            lastAngles[0] = npc.getYHeadRot(); lastAngles[1] = npc.getXRot();
        });
        h.runAtTickTime(10, () -> {
            TacticalCombatGoal goal = goal(npc);
            h.assertTrue(goal != null, "custom goal installed");
            h.assertTrue(npc.targetSelector.getAvailableGoals().isEmpty(), "base target/melee/avoid selectors suspended");
            h.assertFalse(npc.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof FireAtNearestTargetGoal), "base fire goal removed");
            h.assertTrue(goal.shots() == 0 && goal.targetId() == null, "no target means no shots");
            var player = net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "tactical-test"));
            player.setGameMode(GameType.SURVIVAL);
            player.setInvulnerable(true);
            Vec3 pos = h.absoluteVec(new Vec3(11.5, 2, 7.5));
            player.moveTo(pos.x, pos.y, pos.z, 90, 0);
            h.getLevel().addNewPlayer(player);
            // Closed plane separates NPC and player. No hidden target acquisition allowed.
            wall(h, true);
            h.runAtTickTime(40, () -> {
                h.assertTrue(goal.targetId() == null && goal.shots() == 0, "wall blocks detection and fire");
                h.assertFalse(new VisibilitySensor().clear(npc, new VisibilitySensor().aimPoint(player)), "physical ray hits wall");
                npc.setYRot(-90);npc.setYHeadRot(-90);npc.getLookControl().setLookAt(player,10,10);
                wall(h, false);
            });
            h.runAtTickTime(45, () -> {
                h.assertTrue(new VisibilitySensor().valid(npc, player, 40), "test player eligible: alive=" + player.isAlive() + " creative=" + player.isCreative() + " spectator=" + player.isSpectator() + " distance=" + npc.distanceTo(player));
                var hit = npc.level().clip(new net.minecraft.world.level.ClipContext(npc.getEyePosition(), new VisibilitySensor().aimPoint(player), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, npc));
                h.assertTrue(hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS, "removed wall opens physical sightline");
            });
            h.runAtTickTime(60,()->h.assertTrue(player.getUUID().equals(goal.targetId()),"production perception acquires the eligible player within the scan/reaction fixture"));
            h.runAtTickTime(180, () -> {
                h.assertTrue(goal.shots() >= 2, "real TaCZ shooting succeeded");
                h.assertTrue(goal.reloads() >= 1, "empty magazine requested real reload");
                h.assertTrue(npc.position().subtract(origin).horizontalDistance() > 0.25, "strafe moves the real entity horizontally");
                h.assertFalse(goal.failed(), "controller remained healthy");
                h.assertTrue(npc.getTarget() == null, "base hardcoded melee has no target");
                // Corridor guard blocks even a non-allied mob.
                Vec3 middle = npc.position().add(player.position()).scale(0.5);
                var blocker = h.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM, h.relativeVec(middle));
                blocker.setNoAi(true);
                h.assertTrue(new VisibilitySensor().occupied(npc, player, new VisibilitySensor().aimPoint(player)), "crossing living entity blocks fire corridor");
                blocker.discard();
                // Strafe may orbit across the fixed wall plane; restore the fixture's two sides.
                npc.moveTo(origin.x,origin.y,origin.z,npc.getYRot(),npc.getXRot());
                npc.setDeltaMovement(Vec3.ZERO);
                long count = goal.shots();
                wall(h, true);
                h.assertFalse(new VisibilitySensor().clear(npc,new VisibilitySensor().aimPoint(player)),"closed fixture wall actually separates moving NPC and player");
                h.runAtTickTime(200, () -> {
                    h.assertTrue(goal.shots() == count && goal.targetId() == null, "lost visibility stops shots immediately");
                    wall(h, false);
                    player.setHealth(0);
                });
                h.runAtTickTime(220, () -> {
                    h.assertTrue(goal.targetId() == null && goal.shots() == count, "dead player cannot be attacked");
                    npc.removeTag(Npc210Adapter.TAG);
                });
                h.runAtTickTime(230, () -> {
                    h.assertTrue(goal(npc) == null, "tag removal releases ownership");
                    h.assertTrue(npc.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() == npc.fireAtNearestTargetGoal), "original fire goal restored exactly once");
                    h.assertTrue(npc.goalSelector.getAvailableGoals().stream().filter(g -> g.getGoal() == npc.fireAtNearestTargetGoal).count() == 1, "no duplicate goal registration");
                    npc.addTag(Npc210Adapter.TAG);
                });
                h.runAtTickTime(240, () -> {
                    h.assertTrue(goal(npc) != null, "tag can reacquire ownership");
                    npc.setHealth(0);
                });
                h.runAtTickTime(250, () -> {
                    h.assertTrue(goal(npc) == null, "NPC death releases controller");
                    player.discard(); npc.discard();
                    ServerConfig.DEBUG.set(false); h.succeed();
                });
            });
        });
    }

    private static TacticalCombatGoal goal(AutoGunOperator npc) {
        return npc.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal)
                .filter(TacticalCombatGoal.class::isInstance).map(TacticalCombatGoal.class::cast).findFirst().orElse(null);
    }
    private static void wall(GameTestHelper h, boolean solid) {
        for (int y = 2; y <= 6; y++) for (int z = 0; z < 16; z++)
            h.setBlock(new BlockPos(7, y, z), solid ? Blocks.STONE : Blocks.AIR);
    }
}
