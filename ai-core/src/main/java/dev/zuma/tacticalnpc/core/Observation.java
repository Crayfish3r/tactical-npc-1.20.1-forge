package dev.zuma.tacticalnpc.core;
import java.util.UUID;
/** Registered knowledge only. Velocity is in blocks/tick. */
public record Observation(UUID subject, Vector3 position, Vector3 velocity, long tick, long expires,
                          Source source, double confidence, double uncertainty, boolean visible) {
    public enum Source { VISION, HEARING, DAMAGE, SQUAD }
    public Observation {
        if (position == null || velocity == null || source == null || tick < 0 || expires < tick ||
                !Double.isFinite(confidence) || confidence < 0 || confidence > 1 || !Double.isFinite(uncertainty) || uncertainty < 0)
            throw new IllegalArgumentException("Invalid observation");
    }
}
