package dev.zuma.tacticalnpc.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** One transactional group, 1..16 members, bounded local placement in already loaded chunks. */
public record SpawnGroupRequest(ServerLevel level, UUID matchId, UUID groupId, Vec3 origin,
                                float yaw, float pitch, List<SpawnMemberSpec> members) {
    public SpawnGroupRequest {
        Objects.requireNonNull(level); Objects.requireNonNull(matchId); Objects.requireNonNull(groupId); Objects.requireNonNull(origin);
        members=List.copyOf(members);
        if(members.isEmpty() || members.size()>16 || !Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch))throw new IllegalArgumentException("Invalid group size/position/rotation");
    }
}
