package dev.zuma.tacticalnpc.core;

/** Horizontal convex territory. Terrain/loaded chunks are deliberately the adapter's job. */
public record DefendTerritory(Vector3 anchor,double radius,double clearance) {
    public DefendTerritory {
        if(anchor==null || !OrderState.validRadius(OrderState.Kind.DEFEND_AREA,radius) || !Double.isFinite(clearance) || clearance<0 || clearance>=radius)
            throw new IllegalArgumentException("Invalid defend territory");
    }
    /** Circumscribed body footprint plus a conservative vanilla node-arrival allowance. */
    public static double clearance(double width) {return width/Math.sqrt(2)+.75;}
    public double distanceSquared(Vector3 point) {
        double x=point.x()-anchor.x(),z=point.z()-anchor.z();return x*x+z*z;
    }
    public boolean contains(Vector3 point) {double usable=radius-clearance;return distanceSquared(point)<=usable*usable;}
    /** Displacement may start outside. Until entry, every real segment must move inward. */
    public boolean permitsStep(Vector3 from,Vector3 to) {
        double dx=to.x()-from.x(),dz=to.z()-from.z();
        // Radial derivative at the segment end: disallow crossing the circle and exiting again.
        double outward=(to.x()-anchor.x())*dx+(to.z()-anchor.z())*dz;
        return contains(to) || !contains(from) && outward<=1e-7 && distanceSquared(to)<=distanceSquared(from)+1e-7;
    }
}
