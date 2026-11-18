package dev.zuma.tacticalnpc.combat;

import dev.zuma.tacticalnpc.core.ActionArbiter.WeaponAction;
import dev.zuma.tacticalnpc.core.AimCommand;
import dev.zuma.tacticalnpc.core.CombatDecision;
import dev.zuma.tacticalnpc.core.CombatSnapshot;
import dev.zuma.tacticalnpc.core.MovementCommand;
import dev.zuma.tacticalnpc.core.Observation;
import dev.zuma.tacticalnpc.core.OrderState;
import dev.zuma.tacticalnpc.core.TacticalIntent;
import dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.Vec3;

import static dev.zuma.tacticalnpc.combat.ActionGeometry.*;
import static dev.zuma.tacticalnpc.combat.VisibilitySensor.Los.*;

/** Looks for a reachable indoor hold position behind a visible vanilla wooden door. */
final class BuildingDefense {
    private static final int SEARCH_RADIUS = 6;
    private static final int SEARCH_BATCH = 4;
    private static final int HOLD_AFTER_CONTACT = 120;
    private static final int HOLD_BEFORE_PEEK = 24;
    private static final int PEEK_TICKS = 24;
    private static final List<BlockPos> OFFSETS = offsets();

    private record Room(BlockPos door, Vec3 cover, Vec3 peek, Vec3 entrance) {}
    private enum Phase { ENTER, HOLD, PEEK }

    private final ActionGeometry geometry;
    private final MovementExecutor movement;
    private BlockPos searchOrigin;
    private int cursor;
    private long nextScan, nextCheck, expires, phaseSince, peekArrivedAt;
    private Room room;
    private Phase phase;

    BuildingDefense(NpcRuntimeAdapter runtime, MovementExecutor movement) {
        geometry = new ActionGeometry(runtime);
        this.movement = movement;
    }

    boolean active() { return room != null; }
    boolean precisePeek() { return room != null && phase == Phase.PEEK; }

    boolean consider(Observation contact, long tick) {
        if (room != null || contact == null || !contact.visible() || tick < nextScan) return false;
        var npc = geometry.runtime.entity();
        var order = geometry.runtime.orderState().kind();
        if (order != OrderState.Kind.ASSAULT && order != OrderState.Kind.DEFEND_AREA) return false;
        var position = npc.blockPosition();
        if (!geometry.loaded(position) || roofed(position)) return false;
        if (searchOrigin == null || searchOrigin.distSqr(position) > 4 || searchOrigin.getY() != position.getY()) {
            searchOrigin = position;
            cursor = 0;
        }
        nextScan = tick + 4;
        var budget = AiWorkBudget.of(geometry.level);
        for (int count = 0; count < SEARCH_BATCH && cursor < OFFSETS.size(); count++) {
            if (!budget.candidate(npc.getUUID())) break;
            var door = searchOrigin.offset(OFFSETS.get(cursor));
            boolean deferred = inspect(door, vec(contact.position()));
            if (room != null) {
                phase = Phase.ENTER;
                expires = tick + HOLD_AFTER_CONTACT;
                nextCheck = tick + 16;
                movement.tacticalLegChanged();
                return true;
            }
            if (deferred) return false;
            cursor++;
        }
        if (cursor == OFFSETS.size()) {
            cursor = 0;
            nextScan = tick + 40;
        }
        return false;
    }

    /** Returns true when a ray has been deferred and this candidate should be revisited. */
    private boolean inspect(BlockPos door, Vec3 threat) {
        if (!geometry.loaded(door) || !RouteInteractions.VANILLA.supports(geometry.level, door)) return false;
        var npc = geometry.runtime.entity();
        Direction inward = geometry.level.getBlockState(door).getValue(DoorBlock.FACING);
        Vec3 towardDoor = center(door).subtract(npc.position());
        if (towardDoor.x * inward.getStepX() + towardDoor.z * inward.getStepZ() < 0) inward = inward.getOpposite();
        var approachBlock = door.relative(inward.getOpposite());
        var approach = geometry.standing(approachBlock);
        var inside = geometry.standing(door.relative(inward));
        var peek = geometry.standing(door.relative(inward, 2));
        if (approach == null || inside == null || peek == null || roofed(approachBlock) ||
                !roofed(BlockPos.containing(inside)) || !roofed(BlockPos.containing(peek)) ||
                !geometry.permits(npc.position(), approach) || !geometry.permits(approach, inside) || !geometry.permits(inside, peek)) return false;

        var entrance = center(door).add(0, npc.getEyeHeight(), 0);
        Vec3 outerFace = entrance.add(-inward.getStepX() * .55, 0, -inward.getStepZ() * .55);
        var sight = NpcSightRay.trace(npc, npc.getEyePosition(), outerFace, false).los();
        if (sight != CLEAR) return sight == DEFERRED;

        for (Direction side : List.of(inward.getClockWise(), inward.getCounterClockWise())) {
            BlockPos corner = BlockPos.containing(peek).relative(side);
            var cover = geometry.standing(corner);
            if (cover == null || !roofed(corner) || !geometry.permits(peek, cover)) continue;
            var protection = protection(cover, threat, door);
            if (protection == DEFERRED) return true;
            if (protection == BLOCKED) {
                room = new Room(door, cover, peek, entrance);
                return false;
            }
        }
        return false;
    }

    CombatDecision plan(CombatSnapshot snapshot, CombatDecision ordered, CombatDecision combat, boolean enabled) {
        if (room == null) return ordered;
        long tick = snapshot.self().frame().tick();
        boolean visible = snapshot.targetVisible();
        if (!enabled || ordered.intent() == TacticalIntent.RETREAT || tick > expires || movement.blocked(tick) || !roomStillUsable()) {
            abandon(tick);
            return ordered;
        }
        if (visible && tick >= nextCheck) {
            var protection = protection(room.cover(), vec(snapshot.observation().position()), room.door());
            if (protection == CLEAR) {
                abandon(tick);
                return ordered;
            }
            nextCheck = tick + (protection == DEFERRED ? 4 : 16);
        }
        if (visible) expires = tick + HOLD_AFTER_CONTACT;
        if (phase == Phase.ENTER && arrived(room.cover())) setPhase(Phase.HOLD, tick);
        if (phase == Phase.ENTER) {
            return decision(ordered, TacticalIntent.TAKE_COVER, "enter building", room.cover(),
                    MovementCommand.Locomotion.RELOCATION, WeaponAction.NONE, false, doorway());
        }

        var weapon = combat.weaponAction();
        boolean maintenance = weapon == WeaponAction.RELOAD || weapon == WeaponAction.BOLT;
        if (phase == Phase.PEEK && (maintenance || snapshot.weapon().busy() ||
                peekArrivedAt >= 0 && tick - peekArrivedAt >= PEEK_TICKS)) setPhase(Phase.HOLD, tick);
        if (phase == Phase.HOLD && arrived(room.cover()) && !maintenance && !snapshot.weapon().busy() &&
                snapshot.weapon().rounds() > 0 && tick - phaseSince >= HOLD_BEFORE_PEEK) setPhase(Phase.PEEK, tick);

        if (phase == Phase.PEEK) {
            var npc = geometry.runtime.entity();
            boolean atPeek = npc.onGround() && npc.position().distanceToSqr(room.peek()) < MovementExecutor.PRECISE_ARRIVAL_SQUARED;
            if (atPeek && peekArrivedAt < 0) peekArrivedAt = tick;
            boolean fire = atPeek && visible && combat.firePermitted();
            AimCommand aim = visible ? combat.aim() : doorway();
            return decision(ordered, TacticalIntent.PEEK, "watch building entrance", atPeek ? null : room.peek(),
                    MovementCommand.Locomotion.PEEK, fire ? WeaponAction.SHOOT : WeaponAction.NONE, fire, aim);
        }
        boolean atCover = arrived(room.cover());
        return decision(ordered, TacticalIntent.HOLD, "hold inside building", atCover ? null : room.cover(),
                MovementCommand.Locomotion.WALK, atCover && maintenance ? weapon : WeaponAction.NONE, false, doorway());
    }

    private boolean roomStillUsable() {
        return geometry.loaded(room.door()) && RouteInteractions.VANILLA.supports(geometry.level, room.door()) &&
                usable(room.cover()) && usable(room.peek());
    }

    private boolean usable(Vec3 point) {
        var block = BlockPos.containing(point);
        return geometry.standing(block) != null && roofed(block) && geometry.terrain.withinOrder(point);
    }

    private boolean roofed(BlockPos feet) {
        for (int height = 2; height <= 4; height++) {
            BlockPos roof = feet.above(height);
            if (geometry.level.getBlockState(roof).isFaceSturdy(geometry.level, roof, Direction.DOWN)) return true;
        }
        return false;
    }

    private VisibilitySensor.Los protection(Vec3 cover, Vec3 threat, BlockPos door) {
        var npc = geometry.runtime.entity();
        var eyes = coverRay(cover.add(0, npc.getEyeHeight(), 0), threat, door);
        return eyes == BLOCKED ? coverRay(cover.add(0, .8, 0), threat, door) : eyes;
    }

    private VisibilitySensor.Los coverRay(Vec3 start, Vec3 threat, BlockPos door) {
        var ray = NpcSightRay.trace(geometry.runtime.entity(), start, threat, false);
        if (door.equals(ray.block()) || door.above().equals(ray.block())) return CLEAR;
        return ray.los();
    }

    private boolean arrived(Vec3 point) {
        var npc = geometry.runtime.entity();
        return npc.onGround() && npc.position().distanceToSqr(point) < .5;
    }

    private AimCommand doorway() { return new AimCommand(AimCommand.Mode.SEARCH, vector(room.entrance())); }

    private void setPhase(Phase next, long tick) {
        phase = next;
        phaseSince = tick;
        peekArrivedAt = -1;
        movement.tacticalLegChanged();
    }

    private static CombatDecision decision(CombatDecision base, TacticalIntent intent, String reason, Vec3 point,
            MovementCommand.Locomotion gait, WeaponAction weapon, boolean fire, AimCommand aim) {
        var move = point == null ? MovementCommand.hold() : MovementCommand.navigate(vector(point), gait);
        return new CombatDecision(intent, fire, reason, weapon, move, aim, base.frame(), base.expires());
    }

    private void abandon(long tick) {
        clear();
        nextScan = tick + 40;
        movement.tacticalLegChanged();
    }

    void clear() {
        room = null;
        searchOrigin = null;
        cursor = 0;
        nextScan = 0;
    }

    private static List<BlockPos> offsets() {
        var result = new ArrayList<BlockPos>();
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                if (x * x + z * z <= SEARCH_RADIUS * SEARCH_RADIUS) result.add(new BlockPos(x, 0, z));
            }
        }
        result.sort(Comparator.comparingDouble(BlockPos.ZERO::distSqr));
        return List.copyOf(result);
    }
}
