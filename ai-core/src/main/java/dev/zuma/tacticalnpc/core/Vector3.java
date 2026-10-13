package dev.zuma.tacticalnpc.core;

/** Coordinates / velocity units must be consistent with the adapter. */
public record Vector3(double x, double y, double z) {
    public Vector3 plus(Vector3 b) { return new Vector3(x + b.x, y + b.y, z + b.z); }
    public Vector3 minus(Vector3 b) { return new Vector3(x - b.x, y - b.y, z - b.z); }
    public Vector3 times(double scalar) { return new Vector3(x * scalar, y * scalar, z * scalar); }
    public double dot(Vector3 b) { return x * b.x + y * b.y + z * b.z; }
    public double lengthSquared() { return dot(this); }
    public double length() { return Math.sqrt(lengthSquared()); }
}
