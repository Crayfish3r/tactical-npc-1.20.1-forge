package dev.zuma.tacticalnpc.combat;

import dev.zuma.tacticalnpc.config.ServerConfig;
import dev.zuma.tacticalnpc.core.AimCommand;
import dev.zuma.tacticalnpc.core.ActionArbiter.WeaponAction;
import dev.zuma.tacticalnpc.core.CombatDecision;
import dev.zuma.tacticalnpc.core.MovementCommand;
import dev.zuma.tacticalnpc.core.Observation;
import dev.zuma.tacticalnpc.core.TacticalIntent;
import dev.zuma.tacticalnpc.core.Vector3;
import dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import static dev.zuma.tacticalnpc.combat.ActionGeometry.vec;
import static dev.zuma.tacticalnpc.combat.ActionGeometry.vector;

/** A lost visual contact can lead to two distinct ground probes around its last sighting. */
final class ContactSearch {
    private static final int MAX_PASSAGES = 2;
    private static final int PROBE_DISTANCE = 3;
    private final ActionGeometry geometry;
    private final MovementExecutor movement;
    private Observation sighting;
    private Vec3 origin, waypoint, look;
    private Direction[] directions;
    private Direction passageHeading;
    private boolean searching, inspecting;
    private int nextDirection, passages;
    private long scanSince = -1;

    ContactSearch(NpcRuntimeAdapter runtime, MovementExecutor movement) {
        geometry = new ActionGeometry(runtime);
        this.movement = movement;
    }

    void seen(Observation observation, Vec3 feet) {
        clear();
        Vector3 velocity = observation.velocity();
        if (velocity.x() == 0 && velocity.z() == 0) return;

        Direction heading;
        if (Math.abs(velocity.x()) >= Math.abs(velocity.z())) {
            heading = velocity.x() > 0 ? Direction.EAST : Direction.WEST;
        } else {
            heading = velocity.z() > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        sighting = observation;
        origin = feet;
        directions = new Direction[]{heading, heading.getClockWise(), heading.getCounterClockWise()};
    }

    boolean invalidateUnless(Observation observation) {
        if (sighting == null || observation != null && observation.source() == Observation.Source.VISION &&
                observation.tick() == sighting.tick() && observation.subject().equals(sighting.subject())) return false;
        boolean interrupted = searching;
        clear();
        return interrupted;
    }

    CombatDecision plan(CombatDecision base, boolean blockedSight) {
        if (sighting == null || (!blockedSight && !searching)) return base;
        if (base.intent() != TacticalIntent.SEARCH && !(searching && base.intent() == TacticalIntent.REPOSITION)) return base;

        var npc = geometry.runtime.entity();
        long tick = geometry.level.getGameTime();
        if (!searching) {
            if (!AiWorkBudget.of(geometry.level).candidate(npc.getUUID())) return base;
            waypoint = geometry.terrain.safeStanding(origin);
            if (waypoint == null || !geometry.terrain.withinOrder(waypoint)) {
                clear();
                return base;
            }
            searching = true;
            origin = waypoint;
            look = vec(sighting.position());
            movement.tacticalLegChanged();
        }
        if (waypoint != null) {
            boolean arrived = npc.onGround() && npc.position().distanceToSqr(waypoint) < .5;
            if (arrived || movement.blocked(tick)) {
                if (arrived && inspecting) scanSince = tick;
                waypoint = null;
                inspecting = true;
                movement.tacticalLegChanged();
            }
        }
        if (scanSince >= 0) {
            // Allow a full turn between side glances at the native turn limit.
            int sideTicks = (int)Math.ceil(180 / ServerConfig.TURN.get()) + ServerConfig.PERCEPTION.get();
            long elapsed = tick - scanSince;
            if (elapsed < 2L * sideTicks) {
                Direction side = elapsed < sideTicks ? passageHeading.getCounterClockWise() : passageHeading.getClockWise();
                Vec3 sideLook = npc.getEyePosition().add(side.getStepX() * 4, 0, side.getStepZ() * 4);
                return search(base, MovementCommand.hold(), sideLook, "scan passage " + passages);
            }
            scanSince = -1;
        }
        if (inspecting && waypoint == null) choosePassage();

        if (waypoint == null) {
            boolean exhausted = passages == MAX_PASSAGES || nextDirection == directions.length;
            return search(base, MovementCommand.hold(), look, exhausted ? "contact search exhausted" : "contact search deferred");
        }
        var move = MovementCommand.navigate(vector(waypoint), inspecting ? MovementCommand.Locomotion.WALK : MovementCommand.Locomotion.RUN);
        return search(base, move, look, inspecting ? "inspect passage " + passages : "approach last sighting");
    }

    private static CombatDecision search(CombatDecision base, MovementCommand move, Vec3 look, String reason) {
        var weapon = base.weaponAction() == WeaponAction.SHOOT ? WeaponAction.NONE : base.weaponAction();
        return new CombatDecision(TacticalIntent.SEARCH, false, reason, weapon, move,
                new AimCommand(AimCommand.Mode.SEARCH, vector(look)), base.frame(), base.expires());
    }

    private void choosePassage() {
        var npc = geometry.runtime.entity();
        var budget = AiWorkBudget.of(geometry.level);
        while (nextDirection < directions.length && passages < MAX_PASSAGES) {
            if (!budget.candidate(npc.getUUID())) return;
            Direction direction = directions[nextDirection++];
            Vec3 point = passageEnd(direction);
            if (point == null) continue;
            waypoint = point;
            passageHeading = direction;
            look = point.add(direction.getStepX(), npc.getEyeHeight(), direction.getStepZ());
            passages++;
            return;
        }
    }

    private Vec3 passageEnd(Direction direction) {
        Vec3 previous = origin;
        for (int step = 0; step < PROBE_DISTANCE; step++) {
            var next = geometry.terrain.safeStanding(previous.add(direction.getStepX(), 0, direction.getStepZ()));
            if (next == null || Math.abs(next.y - previous.y) > 1 || !geometry.permits(previous, next)) return null;
            previous = next;
        }
        return previous;
    }

    boolean active() { return searching; }

    void clear() {
        sighting = null;
        origin = waypoint = look = null;
        directions = null;
        passageHeading = null;
        nextDirection = passages = 0;
        scanSince = -1;
        searching = inspecting = false;
    }
}
