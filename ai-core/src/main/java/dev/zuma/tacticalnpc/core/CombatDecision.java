package dev.zuma.tacticalnpc.core;

/** A combat intent and a separate fire authorization; adapter remains final arbiter. */
public record CombatDecision(TacticalIntent intent, boolean firePermitted, String reason,
                             ActionArbiter.WeaponAction weaponAction, MovementCommand movement,
                             AimCommand aim, Frame frame, long expires) {
    public CombatDecision(TacticalIntent intent, boolean firePermitted, String reason) {
        this(intent, firePermitted, reason, intent == TacticalIntent.RELOAD ? ActionArbiter.WeaponAction.RELOAD :
                intent == TacticalIntent.BOLT ? ActionArbiter.WeaponAction.BOLT :
                firePermitted ? ActionArbiter.WeaponAction.SHOOT : ActionArbiter.WeaponAction.NONE);
    }
    public CombatDecision(TacticalIntent intent, boolean firePermitted, String reason, ActionArbiter.WeaponAction weapon) {
        this(intent,firePermitted,reason,weapon,MovementCommand.hold(),AimCommand.none(),null,0);
    }
    public CombatDecision bind(CombatSnapshot s) {
        MovementCommand move = MovementCommand.hold();
        if ((intent == TacticalIntent.TAKE_COVER || intent == TacticalIntent.RETREAT) && s.coverAvailable())
            move = MovementCommand.navigate(s.terrain().coverPositions().get(0),intent==TacticalIntent.RETREAT?MovementCommand.Locomotion.RETREAT:MovementCommand.Locomotion.RELOCATION);
        else if (intent == TacticalIntent.ENGAGE && s.targetVisible() && s.weapon().profile().mobile() && s.targetDistanceBlocks()>s.weapon().profile().maximum()*1.25) {
            Vector3 delta=s.observation().position().minus(s.self().position());
            move=MovementCommand.navigate(s.self().position().plus(new Vector3(delta.x(),0,delta.z()).times(Math.min(8,delta.length())/delta.length())),MovementCommand.Locomotion.WALK);
        } else if (intent == TacticalIntent.ENGAGE && s.targetVisible() && s.weapon().profile().mobile())
            move = new MovementCommand(MovementCommand.Mode.STRAFE,null,0,s.tactical().strafeDirection(),.55);
        else if(intent==TacticalIntent.RETREAT && s.targetKnown()) {
            Vector3 away=s.self().position().minus(s.observation().position());away=new Vector3(away.x(),0,away.z());
            if(away.lengthSquared()<.01)away=new Vector3(1,0,0);
            move=MovementCommand.navigate(s.self().position().plus(away.times(4/away.length())),MovementCommand.Locomotion.RETREAT);
        }
        else if (intent == TacticalIntent.ADVANCE && s.targetKnown()) {
            Vector3 away=s.self().position().minus(s.observation().position());away=new Vector3(away.x(),0,away.z());
            if(away.lengthSquared()<.01) away=new Vector3(1,0,0);
            double distance=Math.min(s.weapon().effectiveRange()*.7,(s.weapon().profile().minimum()+s.weapon().effectiveRange())/2);
            Vector3 point=s.observation().position().plus(away.times(distance/away.length()));
            move=MovementCommand.navigate(new Vector3(point.x(),s.self().position().y(),point.z()),MovementCommand.Locomotion.RUN);
        } else if (intent == TacticalIntent.SEARCH && s.targetKnown()) move = MovementCommand.navigate(s.observation().position(),MovementCommand.Locomotion.RUN);
        else if(intent==TacticalIntent.REPOSITION && s.targetKnown()) {
            Vector3 toward=s.observation().position().minus(s.self().position());
            Vector3 side=new Vector3(-toward.z(),0,toward.x());
            if(side.lengthSquared()<.01) side=new Vector3(1,0,0);
            move=MovementCommand.navigate(s.self().position().plus(side.times(3.0*s.tactical().strafeDirection()/side.length())),MovementCommand.Locomotion.RELOCATION);
        }
        AimCommand look = s.targetKnown() ? new AimCommand(s.targetVisible()?AimCommand.Mode.DIRECT:AimCommand.Mode.SEARCH,s.observation().position()) : AimCommand.none();
        return new CombatDecision(intent,firePermitted,reason,weaponAction,move,look,s.self().frame(),s.self().frame().tick()+8);
    }
    /** Only a fresh visual sample of the same subject may replace a DIRECT aim point. */
    public CombatDecision track(Observation fresh,java.util.UUID expectedSubject,long tick) {
        if(aim.mode()!=AimCommand.Mode.DIRECT || fresh==null || !fresh.subject().equals(expectedSubject) || !fresh.visible() || fresh.source()!=Observation.Source.VISION || fresh.tick()!=tick) return this;
        return new CombatDecision(intent,firePermitted,reason,weaponAction,movement,new AimCommand(AimCommand.Mode.DIRECT,fresh.position()),frame,expires);
    }
}
