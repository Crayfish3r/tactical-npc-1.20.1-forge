package dev.zuma.tacticalnpc.core;
import java.util.List;
public record TerrainState(List<Vector3> coverPositions, List<Vector3> routes, boolean blocked,
                           boolean lineOfFireClear, boolean occupied, List<CoverOption> covers) {
    public TerrainState(List<Vector3> cover,List<Vector3> routes,boolean blocked,boolean clear,boolean occupied) {
        this(cover,routes,blocked,clear,occupied,List.of());
    }
    public TerrainState { coverPositions = List.copyOf(coverPositions); routes = List.copyOf(routes); covers=List.copyOf(covers); }
}
