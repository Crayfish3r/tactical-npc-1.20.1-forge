package dev.zuma.tacticalnpc.core;
public record AimCommand(Mode mode, Vector3 point) {
    public enum Mode { NONE, DIRECT, SUPPRESS_AREA, SEARCH }
    public static AimCommand none() { return new AimCommand(Mode.NONE,null); }
}
