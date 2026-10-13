package dev.zuma.tacticalnpc.core;
import java.util.UUID;
/** A generation changes on ownership/target/lifecycle changes. */
public record Frame(UUID owner, String dimension, long tick, long generation) {
    public Frame {
        if (owner == null || dimension == null || tick < 0 || generation < 0) throw new IllegalArgumentException("Invalid frame");
    }
}
