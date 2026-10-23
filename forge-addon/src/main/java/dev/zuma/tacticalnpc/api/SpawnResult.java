package dev.zuma.tacticalnpc.api;

import java.util.List;
import java.util.UUID;

/** Match failures return no IDs. Manual command spawning may return a safe partial group. */
public record SpawnResult(Status status, List<UUID> entityIds, String diagnostic) {
    public enum Status { SUCCESS, INACTIVE_MATCH, DUPLICATE_GROUP, INVALID_WEAPON, NO_SPACE, ENTITY_REJECTED }
    public SpawnResult { entityIds=List.copyOf(entityIds); }
    public boolean success(){return status==Status.SUCCESS;}
    public static SpawnResult failure(Status status,String diagnostic){return new SpawnResult(status,List.of(),diagnostic);}
}
