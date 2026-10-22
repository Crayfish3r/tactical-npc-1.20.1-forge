package dev.zuma.tacticalnpc.core;

/** Audited kinetic recurrence: position += velocity; velocity = velocity*(1-friction) - gravity. */
public record Ballistics(double speed, double gravity, double friction, int lifeTicks) {
    public static final Ballistics UNKNOWN=new Ballistics(0,0,0,0);
    public boolean valid(){return Double.isFinite(speed) && speed>0 && Double.isFinite(gravity) && gravity>=0 &&
            Double.isFinite(friction) && friction>=0 && friction<1 && lifeTicks>0;}
    public double maximumRange(){
        if(!valid())return 16;
        int n=Math.min(1200,lifeTicks);
        return Math.max(1,Math.min(512,speed*(friction<1e-8?n:(1-Math.pow(1-friction,n))/friction)));
    }
    /** Finite bisection intercept; velocity comes exclusively from consecutive visual samples. */
    public Vector3 aim(Vector3 muzzle,Observation seen,double horizon){
        if(!valid() || seen==null || !seen.visible() || seen.source()!=Observation.Source.VISION)return seen==null?muzzle:seen.position();
        Vector3 velocity=seen.velocity();
        if(velocity.length()>.6)velocity=velocity.times(.6/velocity.length());
        double low=0,high=Math.min(60,lifeTicks),limit=Math.max(0,Math.min(20,horizon));
        for(int i=0;i<20;i++){
            double t=(low+high)/2;
            Vector3 offset=seen.position().plus(velocity.times(Math.min(t,limit))).minus(muzzle).plus(new Vector3(0,drop(t),0));
            if(offset.length()>speed*travel(t))low=t;else high=t;
        }
        double t=(low+high)/2;
        return seen.position().plus(velocity.times(Math.min(t,limit))).plus(new Vector3(0,drop(t),0));
    }
    private double travel(double t){
        int n=(int)t;double q=1-friction;
        return (friction<1e-8?n:(1-Math.pow(q,n))/friction)+(t-n)*Math.pow(q,n);
    }
    private double drop(double t){
        int n=(int)t;double sum=0,vy=0;
        for(int i=0;i<n;i++){sum+=vy;vy=vy*(1-friction)+gravity;}
        return sum+(t-n)*vy;
    }
}
