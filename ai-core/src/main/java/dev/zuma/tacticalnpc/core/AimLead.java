package dev.zuma.tacticalnpc.core;

/**
 * Straight-line intercept demonstrator; no projectile gravity or TaCZ-specific
 * ballistics. Use only after adapter verifies projectile speed in blocks/tick.
 * An accurate intercept is still subject to perception freshness, reaction delay
 * and bounded aim errors in the production adapter.
 */
public final class AimLead {
    private AimLead() { }

    public static Vector3 predict(
            Vector3 muzzle, Vector3 observedTargetPosition, Vector3 observedTargetVelocity,
            double bulletSpeedBlocksPerTick, double maxPredictionTicks) {
        if (!(bulletSpeedBlocksPerTick > 0) || !(maxPredictionTicks >= 0) ||
                !Double.isFinite(bulletSpeedBlocksPerTick) || !Double.isFinite(maxPredictionTicks)) {
            return observedTargetPosition;
        }
        Vector3 r = observedTargetPosition.minus(muzzle);
        double speed2 = bulletSpeedBlocksPerTick * bulletSpeedBlocksPerTick;
        double a = observedTargetVelocity.lengthSquared() - speed2;
        double b = 2.0 * r.dot(observedTargetVelocity);
        double c = r.lengthSquared();
        double t;
        if (Math.abs(a) < 1e-8) {
            t = Math.abs(b) < 1e-8 ? Math.sqrt(c) / bulletSpeedBlocksPerTick : -c / b;
        } else {
            double disc = b * b - 4.0 * a * c;
            if (disc < 0.0) return observedTargetPosition;
            double root = Math.sqrt(disc);
            double t1 = (-b - root) / (2.0 * a);
            double t2 = (-b + root) / (2.0 * a);
            t = t1 > 0.0 ? t1 : t2;
        }
        if (!Double.isFinite(t) || t <= 0.0) return observedTargetPosition;
        return observedTargetPosition.plus(observedTargetVelocity.times(Math.min(t, maxPredictionTicks)));
    }
}
