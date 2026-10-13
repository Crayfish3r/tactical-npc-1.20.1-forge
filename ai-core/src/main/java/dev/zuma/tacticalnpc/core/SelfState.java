package dev.zuma.tacticalnpc.core;
public record SelfState(Frame frame, Vector3 position, double healthRatio, boolean alive, boolean damaged) {
    public SelfState {
        if (frame == null || position == null || !Double.isFinite(healthRatio) || healthRatio < 0 || healthRatio > 1) throw new IllegalArgumentException("Invalid self state");
    }
}
