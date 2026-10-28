package dev.zuma.tacticalnpc.core;

/** Accuracy only. Neither profile grants additional information or changes native mechanics. */
public enum NpcDifficulty {
    HARD, DEFAULT;

    public double errorMultiplier(double defaultMultiplier) {
        return this == DEFAULT ? defaultMultiplier : 1.0;
    }
}
