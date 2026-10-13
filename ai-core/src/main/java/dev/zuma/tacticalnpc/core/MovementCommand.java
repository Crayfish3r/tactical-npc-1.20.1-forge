package dev.zuma.tacticalnpc.core;
public record MovementCommand(Mode mode, Vector3 destination, double forward, double sideways, double speed, Locomotion locomotion) {
    public enum Mode { HOLD, STRAFE, NAVIGATE }
    public enum Locomotion { WALK, RUN, RELOCATION, COMBAT_STRAFE, PEEK, RETREAT }
    public MovementCommand(Mode mode,Vector3 destination,double forward,double sideways,double speed) {
        this(mode,destination,forward,sideways,speed,mode==Mode.STRAFE?Locomotion.COMBAT_STRAFE:Locomotion.WALK);
    }
    public static MovementCommand hold() { return new MovementCommand(Mode.HOLD,null,0,0,0); }
    public static MovementCommand navigate(Vector3 point) { return new MovementCommand(Mode.NAVIGATE,point,0,0,1); }
    public static MovementCommand navigate(Vector3 point,Locomotion gait) {
        double speed=switch(gait) {case RUN,RELOCATION->1.25;case PEEK->.65;case RETREAT->1.1;default->1;};
        return new MovementCommand(Mode.NAVIGATE,point,0,0,speed,gait);
    }
    public boolean sprint() {return mode==Mode.NAVIGATE && (locomotion==Locomotion.RUN || locomotion==Locomotion.RELOCATION);}
}
