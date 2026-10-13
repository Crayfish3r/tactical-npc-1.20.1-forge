package dev.zuma.tacticalnpc.core;
public record TacticalState(TacticalIntent intent, long since, long commitUntil, int strafeDirection) {
    public static TacticalState idle() { return new TacticalState(TacticalIntent.PATROL,0,0,1); }
}
