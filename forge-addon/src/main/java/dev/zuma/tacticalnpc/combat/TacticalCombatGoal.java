package dev.zuma.tacticalnpc.combat;

import dev.zuma.tacticalnpc.TacticalNpcAiMod;
import dev.zuma.tacticalnpc.compat.TaczGunController;
import dev.zuma.tacticalnpc.config.ServerConfig;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.runtime.*;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class TacticalCombatGoal extends Goal {
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private final NpcRuntimeAdapter runtime;
    private final PathfinderMob npc;
    @SuppressWarnings("unused") private final Object ownershipLifetime;
    private final VisibilitySensor sensor = new VisibilitySensor();
    private final GunController guns;
    private final ActionArbiter arbiter = new ActionArbiter();
    private final MovementExecutor movement;
    private final CoverSensor covers;
    private final MovementPlanner movementPlanner=new MovementPlanner();
    private final OrderPlanner orderPlanner=new OrderPlanner();
    private final SquadPlanner squadPlanner=new SquadPlanner();
    private long stateSince;
    private final SuppressionPlanner suppressionPlanner=new SuppressionPlanner();
    private long suppressionTrack=-1,nextSuppression;
    private int suppressionRounds;
    private UUID targetId;
    private UUID candidateId;
    private final ContactReadiness contact=new ContactReadiness();
    private boolean freshVisible,latencyRecorded;
    private VisibilitySensor.Los activeLos=VisibilitySensor.Los.BLOCKED;
    private final ObservationMemory memory=new ObservationMemory();
    private long nextScan, nextStrafeChange, nextPlan, generation=GENERATIONS.incrementAndGet();
    private int strafe=1;
    private TacticalIntent previous;
    private CombatDecision decision;
    private boolean failed;
    private int previousRounds=-1;
    private boolean previousBusy;
    private long nextActivation;
    private boolean nearActor;

    public TacticalCombatGoal(NpcRuntimeAdapter runtime,Object lifetime) {
        this.runtime=runtime; npc=runtime.entity(); ownershipLifetime=lifetime;
        runtime.requireServer();npc.setYBodyRot(npc.getYRot());
        guns=new TaczGunController(runtime); movement=new MovementExecutor(runtime);covers=new CoverSensor(runtime);
        nextScan=npc.level().getGameTime()+Math.floorMod(npc.getUUID().hashCode(),ServerConfig.ACQUISITION.get());
        setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.TARGET));
    }
    @Override public boolean canUse() { return npc.isAlive() && !failed; }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    public boolean failed() { return failed; }
    public long shots() { return guns.shots(); }
    public long reloads() { return guns.reloads(); }
    public UUID targetId() { return freshVisible?targetId:null; }
    public UUID contactId() {return contact.subject();}
    public long generation() { return generation; }
    public PathfinderMob entity() { return npc; }
    public CombatDecision decision() {return decision;}
    public WeaponState weaponState() {return guns.snapshot();}
    private dev.zuma.tacticalnpc.entity.TacticalNpcEntity neutralOwner() {
        return npc instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity own && own.neutral()?own:null;
    }
    public void retaliate(UUID attacker) {
        runtime.requireServer();stop();candidateId=attacker;nextScan=0;
    }
    public void recordProjectile(long tick) {
        runtime.requireServer();
        if(freshVisible && contact.ready(tick) && !latencyRecorded) {
            AiTelemetry.contactShot((ServerLevel)npc.level(),contact.reacquiring(),tick-contact.episodeStart());latencyRecorded=true;
        }
    }
    public void forget(UUID subject) {
        var neutral=neutralOwner();
        if(neutral!=null && neutral.reactiveTo(subject)) {neutral.clearRetaliation();stop();return;}
        var known=memory.sample(npc.level().getGameTime(),false,vector(npc.position()),ServerConfig.MEMORY_RANGE.get());
        boolean matched=subject.equals(targetId) || subject.equals(candidateId) || subject.equals(contact.subject()) || known!=null && subject.equals(known.subject());
        memory.forget(subject);
        if(matched){SquadRuntime.of((ServerLevel)npc.level()).releaseClaims(npc.getUUID());targetId=null;candidateId=null;contact.clear();freshVisible=false;decision=null;generation=GENERATIONS.incrementAndGet();nextPlan=0;movement.contactRestored();}
    }
    public Observation observation() {
        var known=memory.sample(npc.level().getGameTime(),freshVisible,vector(npc.position()),ServerConfig.MEMORY_RANGE.get());
        return known!=null && subjectAlive(known.subject())?known:null;
    }
    private boolean subjectAlive(UUID subject) {
        // Validity only. No position/velocity from this reference reaches the planner.
        return CombatTargets.eligible(npc,CombatTargets.resolve((ServerLevel)npc.level(),subject));
    }
    public void hear(Player source) {
        runtime.requireServer(); long tick=npc.level().getGameTime();
        if(neutralOwner()!=null)return;
        if(!sensor.valid(npc,source,ServerConfig.HEARING_RANGE.get()) || targetId!=null) return;
        // Sound reports a quantized region with bounded jitter, never the hidden hitbox.
        double x=Math.floor(source.getX()/8)*8+4+(npc.getRandom().nextDouble()-.5)*4;
        double z=Math.floor(source.getZ()/8)*8+4+(npc.getRandom().nextDouble()-.5)*4;
        var old=observation();
        memory.record(new Observation(source.getUUID(),new Vector3(x,Math.floor(source.getY()/4)*4+2,z),new Vector3(0,0,0),tick,tick+60,Observation.Source.HEARING,.55,8,false));
        if(old==null || !old.subject().equals(source.getUUID())) {covers.reset();generation=GENERATIONS.incrementAndGet();suppressionTrack=-1;}nextPlan=0;
    }
    private SuppressionPermit suppressionPermit(Observation known,long tick,boolean request) {
        var neutral=neutralOwner();
        return new SuppressionPermit(known,(neutral==null || known!=null && neutral.reactiveTo(known.subject())) && ServerConfig.SUPPRESSION.get() && suppressionRounds<6 && (!request || tick>=nextSuppression),known!=null && tick-known.tick()>=ServerConfig.REACTION.get());
    }

    public boolean allowNativeFire() {
        long start=AiTelemetry.begin((ServerLevel)npc.level());
        try{return nativeFireSafe();}finally{AiTelemetry.end((ServerLevel)npc.level(),start);}
    }
    private boolean nativeFireSafe() {
        runtime.requireServer();
        long tick=npc.level().getGameTime();
        Observation known=observation();
        var neutral=neutralOwner();
        if(neutral!=null && (known==null || !neutral.reactiveTo(known.subject()) || tick>neutral.reactiveUntil()))return false;
        if(npc.isSprinting() || runtime.operator().getSynSprintTime()>0 || npc.getHealth()/npc.getMaxHealth()<=.25) return false;
        if(decision!=null && decision.aim().mode()==AimCommand.Mode.SUPPRESS_AREA) {
            WeaponState current=guns.snapshot();
            if(failed || !npc.isAlive() || !current.supported() || current.busy() || decision.frame().generation()!=generation || tick>decision.expires() ||
                    !suppressionPermit(known,tick,false).valid(tick) || !decision.aim().point().equals(known.position()) || known.position().minus(vector(npc.position())).length()>current.effectiveRange()) return false;
            Vec3 point=vec(known.position()),end=npc.getEyePosition().add(Vec3.directionFromRotation(npc.getXRot(),npc.getYHeadRot()).scale(point.distanceTo(npc.getEyePosition())));
            if(sensor.los(npc,point,true)!=VisibilitySensor.Los.CLEAR || sensor.los(npc,end,true)!=VisibilitySensor.Los.CLEAR || sensor.occupied(npc,null,point) || sensor.occupied(npc,null,end)) return false;
            suppressionRounds++;return true;
        }
        LivingEntity target=CombatTargets.resolve((ServerLevel)npc.level(),targetId);
        WeaponState current=guns.snapshot();
        if(!npc.isAlive() || failed || !current.supported() || current.busy() || !sensor.valid(npc,target,ServerConfig.RANGE.get()) || !sensor.inFov(npc,target) ||
                !freshVisible || !contact.ready(tick) || decision==null || !decision.firePermitted() || decision.frame().generation()!=generation || tick>decision.expires() || decision.aim().mode()!=AimCommand.Mode.DIRECT) return false;
        Vec3 point=sensor.aimPoint(target);
        if(npc.position().distanceTo(point)>current.effectiveRange()) return false;
        Vec3 end=npc.getEyePosition().add(Vec3.directionFromRotation(npc.getXRot(),npc.getYHeadRot()).scale(point.distanceTo(npc.getEyePosition())));
        return sensor.los(npc,point,true)==VisibilitySensor.Los.CLEAR && sensor.los(npc,end,true)==VisibilitySensor.Los.CLEAR && !sensor.occupied(npc,target,point) && !sensor.occupied(npc,target,end);
    }
    @Override public void tick() {
        long start=AiTelemetry.begin((ServerLevel)npc.level());
        try { combatTick(); }
        catch(RuntimeException | LinkageError ex) {
            failed=true; stop();
            TacticalNpcAiMod.LOGGER.error("Tactical controller failed for {}; releasing controls",npc.getUUID(),ex);
        }
        finally{AiTelemetry.end((ServerLevel)npc.level(),start);}
    }
    private void combatTick() {
        runtime.requireServer(); runtime.beforeTick();movement.guardTerritory();
        long tick=npc.level().getGameTime(); double range=ServerConfig.RANGE.get();
        var neutral=neutralOwner();
        if(neutral!=null && neutral.reactiveAttacker()!=null) {
            if(tick>neutral.reactiveUntil() || !CombatTargets.validSubject(npc,CombatTargets.resolve((ServerLevel)npc.level(),neutral.reactiveAttacker()))) {
                neutral.clearRetaliation();stop();
            } else if(candidateId==null && targetId==null)candidateId=neutral.reactiveAttacker();
        }
        var squad=SquadRuntime.of((ServerLevel)npc.level());
        if(runtime.squadId()!=null) squad.heartbeat(runtime.squadId(),npc.getUUID(),vector(npc.position()),tick);
        var stored=memory.sample(tick,false,vector(npc.position()),ServerConfig.MEMORY_RANGE.get());
        if(neutral!=null && targetId!=null && stored==null) {neutral.clearRetaliation();stop();}
        if(targetId==null && stored==null && (neutral==null || neutral.reactiveAttacker()==null)) {
            if(tick>=nextActivation) {
                nextActivation=tick+20;
                double activation=Math.max(ServerConfig.ACTIVATION_RANGE.get(),Math.max(range,ServerConfig.HEARING_RANGE.get()));
                var nearby=sensor.activation(npc,activation);
                // A partial page/budget deferral cannot prove that all potential contacts vanished.
                if(nearby!=VisibilitySensor.Nearby.DEFERRED)nearActor=nearby==VisibilitySensor.Nearby.PRESENT;
            }
            if(!nearActor && Math.floorMod(tick+npc.getUUID().hashCode(),10)!=0)return;
        }
        if(stored!=null && !subjectAlive(stored.subject())) {memory.clear();contact.clear();targetId=null;squad.releaseClaims(npc.getUUID());generation=GENERATIONS.incrementAndGet();nextPlan=0;}
        if(targetId!=null && stored==null) {contact.clear();targetId=null;generation=GENERATIONS.incrementAndGet();nextPlan=0;}
        UUID checkId=candidateId!=null?candidateId:targetId;
        LivingEntity target=CombatTargets.resolve((ServerLevel)npc.level(),checkId);
        boolean wasVisible=freshVisible;long oldEpisode=contact.episodeStart();UUID oldContact=targetId;
        boolean reactive=neutral!=null && neutral.reactiveAttacker()!=null;
        // A real hit grants identity, not a hidden aim point. Rearward response still requires LOS.
        activeLos=sensor.valid(npc,target,range) && (reactive || sensor.inFov(npc,target))?sensor.los(npc,sensor.aimPoint(target),true):VisibilitySensor.Los.BLOCKED;
        if(activeLos==VisibilitySensor.Los.CLEAR) {
            if(!contact.visual(target.getUUID(),tick,reactive?0:ServerConfig.PERCEPTION.get(),reactive?0:ServerConfig.REACTION.get(),reactive?0:ServerConfig.REACQUIRE.get(),60)) target=null;
            else targetId=target.getUUID();
        } else {
            if(activeLos==VisibilitySensor.Los.DEFERRED) contact.deferred();else contact.blocked();
            target=null;
        }
        // Retained UUID is checked every tick; scans for new contacts remain bounded/staggered.
        if(neutral==null && target==null && tick>=nextScan && activeLos!=VisibilitySensor.Los.DEFERRED) {
            int interval=ServerConfig.ACQUISITION.get();
            nextScan=tick+1+Math.floorMod(npc.getUUID().hashCode()-tick-1,interval);
            // A freshly visible candidate may still be awaiting perception. Keep that identity
            // without scanning another page, but retain the same UUID-staggered scan cadence.
            if(activeLos!=VisibilitySensor.Los.CLEAR) {
                LivingEntity detected=sensor.acquire(npc,range);
                if(detected==null) {
                    if(sensor.acquisitionDeferred()) {activeLos=VisibilitySensor.Los.DEFERRED;contact.deferred();}
                    else candidateId=null;
                }
                else {
                    candidateId=detected.getUUID();
                    if(contact.visual(candidateId,tick,ServerConfig.PERCEPTION.get(),ServerConfig.REACTION.get(),ServerConfig.REACQUIRE.get(),60)) {
                        target=detected;targetId=target.getUUID();activeLos=VisibilitySensor.Los.CLEAR;
                    }
                }
            }
        }
        freshVisible=target!=null && contact.visible();
        if(freshVisible && !wasVisible && movement.blocked(tick)) movement.contactRestored();
        if(freshVisible!=wasVisible || contact.episodeStart()!=oldEpisode || !java.util.Objects.equals(oldContact,targetId)) {
            generation=GENERATIONS.incrementAndGet();nextPlan=0;
            if(freshVisible && contact.episodeStart()!=oldEpisode) latencyRecorded=false;
        }
        WeaponState gun=guns.snapshot();
        if(tick>=nextStrafeChange) { nextStrafeChange=tick+35+npc.getRandom().nextInt(25); strafe=npc.getRandom().nextBoolean()?1:-1; nextPlan=0; }
        Frame frame=new Frame(npc.getUUID(),npc.level().dimension().location().toString(),tick,generation);
        boolean visible=freshVisible;
        if(visible) {
            if(neutral!=null)neutral.refreshRetaliation();
            Observation old=memory.sample(tick,false,vector(npc.position()),ServerConfig.MEMORY_RANGE.get());
            if(old!=null && !old.subject().equals(targetId)) {squad.releaseClaims(npc.getUUID());covers.reset();}
            Vector3 point=vector(sensor.aimPoint(target)),velocity=new Vector3(0,0,0);
            if(old!=null && old.subject().equals(targetId) && old.source()==Observation.Source.VISION && tick>old.tick()) {
                velocity=point.minus(old.position()).times(1.0/(tick-old.tick()));
                if(velocity.length()>.5) velocity=velocity.times(.5/velocity.length());
            }
            memory.record(new Observation(targetId,point,velocity,tick,tick+ServerConfig.MEMORY.get(),Observation.Source.VISION,1,0,true));
            if(runtime.squadId()!=null) squad.report(runtime.squadId(),npc.getUUID(),observation(),tick);
        } else if(neutral==null && runtime.squadId()!=null) {
            var received=squad.receive(runtime.squadId(),npc.getUUID(),vector(npc.position()),tick);
            if(received!=null && subjectAlive(received.subject())) {
                var old=observation();memory.record(received);
                if(old==null || !old.subject().equals(received.subject())) {squad.releaseClaims(npc.getUUID());generation=GENERATIONS.incrementAndGet();suppressionTrack=-1;nextPlan=0;}
            }
        }
        Observation observation=memory.sample(tick,visible,vector(npc.position()),ServerConfig.MEMORY_RANGE.get());
        if(observation==null) suppressionTrack=-1;
        else if(visible || suppressionTrack<0) {suppressionRounds=0;suppressionTrack=observation.tick();}
        // A visible sample already passed a ray in this server tick. Sector work is only useful
        // when native ammo/cooldown can permit firing; arbitration and every native round recheck it.
        boolean clear=visible || activeLos!=VisibilitySensor.Los.DEFERRED && observation!=null && sensor.los(npc,vec(observation.position()),true)==VisibilitySensor.Los.CLEAR;
        boolean canRequestFire=gun.supported() && !gun.busy() && gun.cooldownReady() && gun.rounds()>0;
        boolean occupied=canRequestFire && observation!=null && sensor.occupied(npc,target,vec(observation.position()));
        SuppressionPermit suppression=suppressionPermit(observation,tick,true);
        Vector3 failure=movement.failedDestination(tick);if(failure!=null) covers.reject(failure,tick);
        var coverOptions=movement.blocked(tick)?List.<CoverOption>of():covers.sample(observation,tick,previous==TacticalIntent.REPOSITION && tick-stateSince==4);
        CombatSnapshot snapshot=new CombatSnapshot(new SelfState(frame,vector(npc.position()),Math.max(0,Math.min(1,npc.getHealth()/npc.getMaxHealth())),npc.isAlive(),npc.hurtTime>0),
                observation,gun,new TerrainState(coverOptions.stream().map(CoverOption::position).toList(),List.of(),movement.blocked(tick),clear,occupied,coverOptions),new TacticalState(previous==null?TacticalIntent.PATROL:previous,stateSince,stateSince+20,strafe));
        if(decision==null || tick>=nextPlan || gun.rounds()!=previousRounds || gun.busy()!=previousBusy || npc.hurtTime>0 && previous!=TacticalIntent.TAKE_COVER && previous!=TacticalIntent.RETREAT || npc.getHealth()/npc.getMaxHealth()<=.25 && previous!=TacticalIntent.RETREAT) {
            decision=movementPlanner.plan(snapshot,new CombatBrain(ServerConfig.MEMORY.get(),gun.effectiveRange()).decide(snapshot)); nextPlan=tick+4;
            if(runtime.squadId()!=null)decision=squadPlanner.plan(snapshot,decision,squad.role(runtime.squadId(),npc.getUUID()));
            long patrolRevision=orderPlanner.patrolRevision();
            decision=orderPlanner.plan(snapshot,decision,runtime.orderState(),npc.getBbWidth(),movement.recoveryStatus());
            if(orderPlanner.patrolRevision()!=patrolRevision)movement.patrolLegChanged();
            decision=suppressionPlanner.plan(snapshot,decision,suppression);
            if(decision.reason().equals("unusable peek timeout") && !coverOptions.isEmpty()) covers.reject(coverOptions.get(0).position(),tick);
            previousRounds=gun.rounds(); previousBusy=gun.busy();
        }
        if(visible) decision=decision.track(observation,targetId,tick);
        // Final policy after squad/order/suppression overlays, before action arbitration.
        if(neutral!=null && (observation==null || !neutral.reactiveTo(observation.subject())))
            decision=new CombatDecision(decision.intent(),false,"neutral",decision.weaponAction()==ActionArbiter.WeaponAction.SHOOT?ActionArbiter.WeaponAction.NONE:decision.weaponAction(),MovementCommand.hold(),AimCommand.none(),decision.frame(),decision.expires());
        if(decision.intent()!=previous) { debug("state "+decision.intent()+" weapon "+decision.weaponAction()); previous=decision.intent();stateSince=tick; }
        boolean aligned=false;
        if(decision.aim().point()!=null) aligned=turn(decision.aim().point(),tick);
        else if(candidateId==null && (tick%4==0 || orderPlanner.patrolling() && decision.movement().mode()==MovementCommand.Mode.HOLD)) {
            // Bounded idle sweep lets a patrol eventually inspect the rear hemisphere.
            float yaw=npc.getYHeadRot()+ServerConfig.TURN.get().floatValue()/2;
            npc.setYRot(yaw);npc.getLookControl().setLookAt(npc.getEyePosition().add(Vec3.directionFromRotation(0,yaw).scale(10)));
        }
        if(observation!=null && aligned && canRequestFire && decision.weaponAction()==ActionArbiter.WeaponAction.SHOOT) {
            Vec3 point=vec(observation.position());
            Vec3 end=npc.getEyePosition().add(Vec3.directionFromRotation(npc.getXRot(),npc.getYHeadRot()).scale(point.distanceTo(npc.getEyePosition())));
            clear=clear && sensor.los(npc,end,true)==VisibilitySensor.Los.CLEAR; occupied=occupied || sensor.occupied(npc,target,end);
        }
        var commands=arbiter.commands(decision,frame,new ActionArbiter.Safety(npc.isAlive(),visible && sensor.valid(npc,target,range) && snapshot.targetDistanceBlocks()<=gun.effectiveRange(),visible,
                contact.ready(tick),aligned,clear,occupied,gun.busy() || !gun.supported(),gun.cooldownReady()),suppression);
        if(ServerConfig.DEBUG.get() && tick%40==0 && visible) debug("gates action="+commands.weapon()+" reason="+decision.reason()+" clear="+clear+" occupied="+occupied+" aligned="+aligned+" busy="+gun.busy()+" ready="+gun.cooldownReady()+" valid="+commands.valid());
        boolean relocating=decision.intent()==TacticalIntent.RETREAT || decision.intent()==TacticalIntent.ADVANCE || decision.intent()==TacticalIntent.TAKE_COVER || decision.intent()==TacticalIntent.REPOSITION;
        guns.aim(commands.valid() && !relocating && !commands.movement().sprint() && (visible || decision.aim().mode()==AimCommand.Mode.SUPPRESS_AREA)); npc.setAggressive(observation!=null);
        movement.apply(commands.movement(),tick);
        long shots=guns.shots();
        guns.apply(commands.weapon(),tick);
        var level=(ServerLevel)npc.level();
        if(commands.weapon()!=ActionArbiter.WeaponAction.SHOOT) {
            AiTelemetry.gate(level,activeLos==VisibilitySensor.Los.DEFERRED?AiTelemetry.Gate.LOS_DEFERRED:!visible?observation==null?AiTelemetry.Gate.NO_CONTACT:AiTelemetry.Gate.LOS_BLOCKED:
                !contact.ready(tick)?AiTelemetry.Gate.REACTION:gun.busy() || !gun.supported()?AiTelemetry.Gate.WEAPON_BUSY:!gun.cooldownReady()?AiTelemetry.Gate.COOLDOWN:
                !aligned?AiTelemetry.Gate.TURN:occupied?AiTelemetry.Gate.ALLY:AiTelemetry.Gate.POLICY);
        } else if(guns.shots()==shots) AiTelemetry.gate(level,AiTelemetry.Gate.NATIVE_DECLINED);
        if(guns.shots()>shots && decision.aim().mode()==AimCommand.Mode.SUPPRESS_AREA) nextSuppression=tick+10;
    }
    private boolean turn(Vector3 knownPoint,long tick) {
        Vec3 d=new Vec3(knownPoint.x(),knownPoint.y(),knownPoint.z()).subtract(npc.getEyePosition());
        float yaw=(float)(Mth.atan2(d.z,d.x)*180/Math.PI-90);
        float pitch=(float)(-Mth.atan2(d.y,d.horizontalDistance())*180/Math.PI);
        double phase=(npc.getUUID().getLeastSignificantBits()&255)+tick*.13;
        float error=ServerConfig.ERROR.get().floatValue(), turn=ServerConfig.TURN.get().floatValue();
        float noisyYaw=yaw+(float)Math.sin(phase)*error, noisyPitch=pitch+(float)Math.cos(phase*.73)*error;
        npc.setYRot(Mth.approachDegrees(npc.getYRot(),noisyYaw,turn)); npc.setYBodyRot(npc.getYRot());
        Vec3 look=npc.getEyePosition().add(Vec3.directionFromRotation(noisyPitch,noisyYaw).scale(20));
        npc.getLookControl().setLookAt(look.x,look.y,look.z,turn,turn);
        return Math.abs(Mth.wrapDegrees(yaw-npc.getYHeadRot()))<=error+2 && Math.abs(pitch-npc.getXRot())<=error+2;
    }
    @Override public void stop() {
        orderPlanner.resetPatrol();
        generation=GENERATIONS.incrementAndGet(); targetId=null; candidateId=null; contact.clear();freshVisible=false;memory.clear();covers.reset(); decision=null;
        sensor.resetAcquisition();
        previous=null;stateSince=npc.level().getGameTime();nextPlan=0;previousRounds=-1;suppressionTrack=-1;suppressionRounds=0;
        nextActivation=0;
        SquadRuntime.of((ServerLevel)npc.level()).release(npc.getUUID());
        try { guns.aim(false); } catch(RuntimeException | LinkageError ex) { TacticalNpcAiMod.LOGGER.warn("Could not reset aim for {}",npc.getUUID(),ex); }
        npc.setAggressive(false); npc.setTarget(null); movement.stop(npc.level().getGameTime());
    }
    private static Vector3 vector(Vec3 v) { return new Vector3(v.x,v.y,v.z); }
    private static Vec3 vec(Vector3 v) {return new Vec3(v.x(),v.y(),v.z());}
    private void debug(String message) { if(ServerConfig.DEBUG.get()) TacticalNpcAiMod.LOGGER.info("NPC {} {}",npc.getUUID(),message); }
}
