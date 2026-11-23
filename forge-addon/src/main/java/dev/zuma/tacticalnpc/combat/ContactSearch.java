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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.Vec3;

import static dev.zuma.tacticalnpc.combat.ActionGeometry.vec;
import static dev.zuma.tacticalnpc.combat.ActionGeometry.vector;

/** A lost visual contact can lead to two distinct ground probes around its last sighting. */
final class ContactSearch {
    private static final int MAX_PASSAGES = 2;
    private static final int PROBE_DISTANCE = 3;
    private static final int GUARD_AFTER_SEARCH = 16;
    private final ActionGeometry geometry;
    private final MovementExecutor movement;
    private Observation sighting;
    private Vec3 origin, waypoint, look;
    private Direction[] directions;
    private Direction passageHeading;
    private boolean searching, inspecting;
    private int nextDirection, passages;
    private long scanSince = -1;
    private long exhaustedAt = -1;

    ContactSearch(NpcRuntimeAdapter runtime, MovementExecutor movement) {
        geometry = new ActionGeometry(runtime);
        this.movement = movement;
    }

    void seen(Observation observation, Vec3 feet) {
        Direction previousHeading=sighting!=null && sighting.subject().equals(observation.subject())?directions[0]:null;
        clear();
        Vector3 velocity = observation.velocity();
        Direction heading=previousHeading;
        if (velocity.x()!=0 || velocity.z()!=0) heading=horizontal(velocity.x(),velocity.z());
        if (heading==null) {
            Vec3 ahead=feet.subtract(geometry.runtime.entity().position());
            heading=horizontal(ahead.x,ahead.z);
        }
        sighting = observation;
        origin = feet;
        directions = new Direction[]{heading, heading.getClockWise(), heading.getCounterClockWise()};
    }

    private static Direction horizontal(double x,double z){
        return Math.abs(x)>=Math.abs(z)?(x>=0?Direction.EAST:Direction.WEST):(z>=0?Direction.SOUTH:Direction.NORTH);
    }

    boolean invalidateUnless(Observation observation) {
        if (sighting == null || observation != null && observation.subject().equals(sighting.subject()) &&
                geometry.level.getGameTime()<=sighting.expires()) return false;
        boolean interrupted = searching;
        clear();
        return interrupted;
    }

    CombatDecision plan(CombatDecision base, boolean blockedSight) {
        if (sighting == null || (!blockedSight && !searching)) return base;
        if (base.intent() != TacticalIntent.SEARCH && !(searching && base.intent() == TacticalIntent.REPOSITION)) return base;

        var npc = geometry.runtime.entity();
        if(npc.getAttributeValue(Attributes.MOVEMENT_SPEED)<=0)return base;
        long tick = geometry.level.getGameTime();
        if (!searching) {
            if (!AiWorkBudget.of(geometry.level).candidate(npc.getUUID())) return base;
            waypoint = geometry.terrain.safeStanding(origin);
            if(waypoint==null)waypoint=geometry.terrain.safeStanding(origin.subtract(0,2,0));
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
            if(exhausted && exhaustedAt<0)exhaustedAt=tick;
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
        Vec3 lastStanding=null;
        for (int step = 0; step < PROBE_DISTANCE; step++) {
            Vec3 guess=previous.add(direction.getStepX(),0,direction.getStepZ());
            var next = geometry.terrain.safeStanding(guess);
            if(next==null){
                BlockPos door=BlockPos.containing(guess);
                if(!RouteInteractions.VANILLA.supports(geometry.level,door) ||
                        geometry.level.getBlockState(door).getValue(DoorBlock.FACING).getAxis()!=direction.getAxis())break;
                next=ActionGeometry.center(door);
                if(!geometry.permits(previous,next))break;
                previous=next;
                continue;
            }
            if (Math.abs(next.y - previous.y) > 1 || !geometry.permits(previous, next)) break;
            previous = next;
            lastStanding=next;
        }
        return lastStanding;
    }

    boolean active() { return searching; }
    boolean finished(long tick){return exhaustedAt>=0 && tick-exhaustedAt>=GUARD_AFTER_SEARCH;}

    void clear() {
        sighting = null;
        origin = waypoint = look = null;
        directions = null;
        passageHeading = null;
        nextDirection = passages = 0;
        scanSince = -1;
        exhaustedAt = -1;
        searching = inspecting = false;
    }
}
