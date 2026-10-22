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
    private final SuppressionCycle suppressionCycle=new SuppressionCycle();
    private final SuppressionSectors suppressionSectors=new SuppressionSectors();
    private final UnfairHints unfairHints=new UnfairHints();
    private final TacticalUtility utility=new TacticalUtility();
    private final FlankRoute flankRoute;
    private Observation hint;
    private Vector3 suppressionPoint;
    private long nativeRoundToken;
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
    private int blockedFireTicks;
    private long nextOpportunityScan,nextTargetChange;
    private boolean nearActor;

    public TacticalCombatGoal(NpcRuntimeAdapter runtime,Object lifetime) {
        this.runtime=runtime; npc=runtime.entity(); ownershipLifetime=lifetime;
        runtime.requireServer();npc.setYBodyRot(npc.getYRot());
        guns=new TaczGunController(runtime); movement=new MovementExecutor(runtime);covers=new CoverSensor(runtime);flankRoute=new FlankRoute(runtime);
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
    public void standTriggered() {runtime.requireServer();orderPlanner.resetPatrol();nextPlan=0;}
    private dev.zuma.tacticalnpc.entity.TacticalNpcEntity neutralOwner() {
        return npc instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity own && own.neutral()?own:null;
    }
    public void retaliate(UUID attacker) {
        runtime.requireServer();stop();candidateId=attacker;nextScan=0;
    }
    public void recordProjectile(long tick) {
        runtime.requireServer();
        if(npc instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity own)own.standShot();
        if(decision!=null && decision.aim().mode()==AimCommand.Mode.SUPPRESS_AREA)
            suppressionCycle.nativeProjectile(nativeRoundToken,tick,ServerConfig.SUPPRESSION_BURST.get(),ServerConfig.SUPPRESSION_PAUSE.get());
        if(freshVisible && contact.ready(tick) && !latencyRecorded) {
            AiTelemetry.contactShot((ServerLevel)npc.level(),contact.reacquiring(),tick-contact.episodeStart());latencyRecorded=true;
        }
    }
    /** One verified GunFireEvent per cartridge precedes its pellet loop in the pinned native API. */
    public void authorizeNativeRound(){runtime.requireServer();nativeRoundToken++;}
    public void forget(UUID subject) {
        var neutral=neutralOwner();
        if(neutral!=null && neutral.reactiveTo(subject)) {neutral.clearRetaliation();stop();return;}
        var known=memory.sample(npc.level().getGameTime(),false,vector(npc.position()),ServerConfig.memoryRange());
        boolean matched=subject.equals(targetId) || subject.equals(candidateId) || subject.equals(contact.subject()) || known!=null && subject.equals(known.subject());
        memory.forget(subject);
        if(matched){SquadRuntime.of((ServerLevel)npc.level()).releaseClaims(npc.getUUID());targetId=null;candidateId=null;contact.clear();freshVisible=false;decision=null;generation=GENERATIONS.incrementAndGet();nextPlan=0;movement.contactRestored();movement.subject(null);}
    }
    public Observation observation() {
        var known=knowledge(memory.sample(npc.level().getGameTime(),freshVisible,vector(npc.position()),ServerConfig.memoryRange()),npc.level().getGameTime());
        return known!=null && known.expires()>=npc.level().getGameTime() && subjectAlive(known.subject())?known:null;
    }
    private Observation knowledge(Observation fair,long tick){
        if(!ServerConfig.unfair() || hint==null || hint.expires()<tick)return fair;
        if(fair==null || !fair.visible() && tick-fair.tick()>=20 && hint.tick()>fair.tick())return hint;
        return fair;
    }
    private boolean subjectAlive(UUID subject) {
        // Validity only. No position/velocity from this reference reaches the planner.
        return CombatTargets.eligible(npc,CombatTargets.resolve((ServerLevel)npc.level(),subject));
    }
    public boolean hear(LivingEntity source) {
        runtime.requireServer(); long tick=npc.level().getGameTime();
        if(neutralOwner()!=null)return false;
        if(!CombatTargets.eligible(npc,source) || npc.distanceToSqr(source)>ServerConfig.hearingRange()*ServerConfig.hearingRange() || targetId!=null) return false;
        // Sound reports a quantized region with bounded jitter, never the hidden hitbox.
        double x=Math.floor(source.getX()/8)*8+4+(npc.getRandom().nextDouble()-.5)*4;
        double z=Math.floor(source.getZ()/8)*8+4+(npc.getRandom().nextDouble()-.5)*4;
        var old=observation();
        memory.record(new Observation(source.getUUID(),new Vector3(x,Math.floor(source.getY()/4)*4+2,z),new Vector3(0,0,0),tick,tick+60,Observation.Source.HEARING,.55,8,false));
        if(old==null || !old.subject().equals(source.getUUID())) {covers.reset();generation=GENERATIONS.incrementAndGet();suppressionCycle.reset();}nextPlan=0;return true;
    }
    private SuppressionPermit suppressionPermit(Observation known,long tick) {
        var neutral=neutralOwner();
        boolean eligible=known!=null && CombatTargets.eligible(npc,CombatTargets.resolve((ServerLevel)npc.level(),known.subject()));
        return new SuppressionPermit(known,eligible && (neutral==null || neutral.reactiveTo(known.subject())) && ServerConfig.SUPPRESSION.get() && suppressionCycle.ready(tick,ServerConfig.SUPPRESSION_DURATION.get(),ServerConfig.SUPPRESSION_ROUNDS.get()),known!=null && tick-known.tick()>=ServerConfig.reaction(),suppressionPoint,ServerConfig.SUPPRESSION_DURATION.get());
    }

    public boolean allowNativeFire() {
        long component=System.nanoTime();
        long start=AiTelemetry.begin((ServerLevel)npc.level());
        try{return nativeFireSafe();}finally{AiTelemetry.end((ServerLevel)npc.level(),start);AiTelemetry.component((ServerLevel)npc.level(),AiTelemetry.Component.NATIVE_SAFETY,System.nanoTime()-component);}
    }
    private boolean nativeFireSafe() {
        runtime.requireServer();
        if(npc instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity own)own.syncTeam();
        long tick=npc.level().getGameTime();
        Observation known=observation();
        var neutral=neutralOwner();
        if(neutral!=null && (known==null || !neutral.reactiveTo(known.subject()) || tick>neutral.reactiveUntil()))return false;
        if(npc.isSprinting() || runtime.operator().getSynSprintTime()>0 || npc.getHealth()/npc.getMaxHealth()<=.25) return false;
        if(decision!=null && decision.aim().mode()==AimCommand.Mode.SUPPRESS_AREA) {
            WeaponState current=guns.snapshot();
            if(failed || !npc.isAlive() || !decision.firePermitted() || !current.supported() || current.busy() || decision.frame().generation()!=generation || tick>decision.expires() ||
                    !suppressionPermit(known,tick).valid(tick) || !decision.aim().point().equals(suppressionPoint) || suppressionPoint.minus(vector(npc.position())).length()>current.effectiveRange()) return false;
            Vec3 point=vec(suppressionPoint),end=npc.getEyePosition().add(Vec3.directionFromRotation(npc.getXRot(),npc.getYHeadRot()).scale(point.distanceTo(npc.getEyePosition())));
            // Identity exemption for the intended hostile only; no hidden coordinates are read.
            var subject=CombatTargets.resolve((ServerLevel)npc.level(),known.subject());
            if(sensor.los(npc,point,true)!=VisibilitySensor.Los.CLEAR || sensor.los(npc,end,true)!=VisibilitySensor.Los.CLEAR || sensor.occupied(npc,subject,point,current) || sensor.occupied(npc,subject,end,current)) return false;
            return true;
        }
        LivingEntity target=CombatTargets.resolve((ServerLevel)npc.level(),targetId);
        WeaponState current=guns.snapshot();
        if(!npc.isAlive() || failed || !current.supported() || current.busy() || !sensor.valid(npc,target,ServerConfig.RANGE.get()) || !sensor.inFov(npc,target) ||
                !freshVisible || !contact.ready(tick) || decision==null || !decision.firePermitted() || decision.frame().generation()!=generation || tick>decision.expires() || decision.aim().mode()!=AimCommand.Mode.DIRECT) return false;
        Vec3 point=sensor.aimPoint(target);
        if(npc.position().distanceTo(point)>current.effectiveRange()) return false;
        Vec3 end=npc.getEyePosition().add(Vec3.directionFromRotation(npc.getXRot(),npc.getYHeadRot()).scale(point.distanceTo(npc.getEyePosition())));
        return sensor.los(npc,point,true)==VisibilitySensor.Los.CLEAR && sensor.los(npc,end,true)==VisibilitySensor.Los.CLEAR && !sensor.occupied(npc,target,point,current) && !sensor.occupied(npc,target,end,current);
    }
    @Override public void tick() {
        long component=System.nanoTime();
        long start=AiTelemetry.begin((ServerLevel)npc.level());
        try { combatTick(); }
        catch(RuntimeException | LinkageError ex) {
            failed=true; stop();
            TacticalNpcAiMod.LOGGER.error("Tactical controller failed for {}; releasing controls",npc.getUUID(),ex);
        }
        finally{AiTelemetry.end((ServerLevel)npc.level(),start);AiTelemetry.component((ServerLevel)npc.level(),AiTelemetry.Component.GOAL,System.nanoTime()-component);}
    }
    private void combatTick() {
        runtime.requireServer();
        runtime.beforeTick();guns.maintain(npc.level().getGameTime());
        long tick=npc.level().getGameTime(); double range=ServerConfig.RANGE.get();
        var neutral=neutralOwner();
        if(neutral!=null && neutral.reactiveAttacker()!=null) {
            if(tick>neutral.reactiveUntil() || !CombatTargets.validSubject(npc,CombatTargets.resolve((ServerLevel)npc.level(),neutral.reactiveAttacker()))) {
                neutral.clearRetaliation();stop();
            } else if(candidateId==null && targetId==null)candidateId=neutral.reactiveAttacker();
        }
        var squad=SquadRuntime.of((ServerLevel)npc.level());
        if(runtime.squadId()!=null) squad.heartbeat(runtime.squadId(),npc.getUUID(),vector(npc.position()),tick);
        hint=unfairHints.sample(runtime,tick,freshVisible);
        if(hint!=null && !subjectAlive(hint.subject()))hint=null;
        var stored=memory.sample(tick,false,vector(npc.position()),ServerConfig.memoryRange());
        stored=knowledge(stored,tick);
        // Expired knowledge cancels its route before activation/cadence can defer this tick.
        movement.subject(stored==null?null:stored.subject());movement.guardTerritory();
        if(neutral!=null && targetId!=null && stored==null) {neutral.clearRetaliation();stop();}
        if(targetId==null && stored==null && (neutral==null || neutral.reactiveAttacker()==null)) {
            if(tick>=nextActivation) {
                nextActivation=tick+20;
                double activation=Math.max(ServerConfig.ACTIVATION_RANGE.get(),Math.max(range,ServerConfig.hearingRange()));
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
            if(!contact.visual(target.getUUID(),tick,reactive?0:ServerConfig.perception(),reactive?0:ServerConfig.reaction(),reactive?0:ServerConfig.reacquire(),60)) target=null;
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
                    activeLos=VisibilitySensor.Los.CLEAR;
                    if(contact.visual(candidateId,tick,ServerConfig.perception(),ServerConfig.reaction(),ServerConfig.reacquire(),60)) {
                        target=detected;targetId=target.getUUID();activeLos=VisibilitySensor.Los.CLEAR;
                    }
                }
            }
        }
        // A visible but persistently unsafe contact must not monopolize the first acquisition page.
        // Continue the existing bounded sensor cursor; replace it only with a much closer fresh LOS sample.
        if(neutral==null && target!=null && blockedFireTicks>=12 && tick>=nextOpportunityScan && tick>=nextTargetChange && npc.getHealth()/npc.getMaxHealth()>.4){
            nextOpportunityScan=tick+ServerConfig.ACQUISITION.get()*2;
            var alternative=sensor.acquire(npc,range);
            if(alternative!=null && alternative!=target && TacticalUtility.preferTarget(npc.distanceToSqr(target),npc.distanceToSqr(alternative))){
                candidateId=alternative.getUUID();targetId=null;contact.clear();
                contact.visual(candidateId,tick,ServerConfig.perception(),ServerConfig.reaction(),ServerConfig.reacquire(),60);
                target=null;blockedFireTicks=0;nextTargetChange=tick+40;
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
            Observation old=memory.sample(tick,false,vector(npc.position()),ServerConfig.memoryRange());
            if(old!=null && !old.subject().equals(targetId)) {squad.releaseClaims(npc.getUUID());covers.reset();}
            Vector3 point=vector(sensor.aimPoint(target)),velocity=new Vector3(0,0,0);
            if(old!=null && old.subject().equals(targetId) && old.source()==Observation.Source.VISION && tick-old.tick()==1 && wasVisible) {
                velocity=point.minus(old.position()).times(1.0/(tick-old.tick()));
                if(velocity.length()>.5) velocity=velocity.times(.5/velocity.length());
            }
            memory.record(new Observation(targetId,point,velocity,tick,tick+ServerConfig.MEMORY.get(),Observation.Source.VISION,1,0,true));
            if(runtime.squadId()!=null) squad.report(runtime.squadId(),npc.getUUID(),observation(),tick,ServerConfig.unfair()?2:8);
        } else if(neutral==null && runtime.squadId()!=null) {
            var received=squad.receive(runtime.squadId(),npc.getUUID(),vector(npc.position()),tick,ServerConfig.unfair()?128:96);
            if(received!=null && subjectAlive(received.subject())) {
                var old=observation();memory.record(received);
                if(old==null || !old.subject().equals(received.subject())) {squad.releaseClaims(npc.getUUID());generation=GENERATIONS.incrementAndGet();suppressionCycle.reset();nextPlan=0;}
            }
        }
        Observation observation=knowledge(memory.sample(tick,visible,vector(npc.position()),ServerConfig.memoryRange()),tick);
        // Finish only after all perception inputs, including this tick's delayed squad delivery.
        if(npc instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity own && own.standPhase()==dev.zuma.tacticalnpc.entity.TacticalNpcEntity.StandPhase.COMBAT &&
                !visible && activeLos==VisibilitySensor.Los.BLOCKED && observation==null && !own.standDamageContact()){
            own.endStandContact();return; // stop invalidated this frame; ordinary DEFEND plans next tick.
        }
        suppressionCycle.observe(observation,tick);
        Vector3 oldSector=suppressionPoint;
        suppressionPoint=ServerConfig.SUPPRESSION.get()?suppressionSectors.sample(runtime,sensor,observation,tick):null;
        if(!visible && !java.util.Objects.equals(oldSector,suppressionPoint))nextPlan=0;
        // Every actual round still revalidates the selected offset and actual barrel direction.
        boolean clear=visible || activeLos!=VisibilitySensor.Los.DEFERRED && suppressionPoint!=null && sensor.los(npc,vec(suppressionPoint),true)==VisibilitySensor.Los.CLEAR;
        boolean canRequestFire=gun.supported() && !gun.busy() && gun.cooldownReady() && gun.rounds()>0;
        Vector3 safetyPoint=visible && observation!=null?observation.position():suppressionPoint;
        LivingEntity safetySubject=visible?target:observation==null?null:CombatTargets.resolve((ServerLevel)npc.level(),observation.subject());
        boolean occupied=canRequestFire && safetyPoint!=null && sensor.occupied(npc,safetySubject,vec(safetyPoint),gun);
        blockedFireTicks=visible && occupied && sensor.lastSector()==VisibilitySensor.Sector.OCCUPIED?Math.min(200,blockedFireTicks+1):0;
        SuppressionPermit suppression=suppressionPermit(observation,tick);
        Vector3 failure=movement.failedDestination(tick);if(failure!=null) covers.reject(failure,tick);
        long coverRevision=covers.revision();
        var coverOptions=movement.blocked(tick)?List.<CoverOption>of():covers.sample(observation,tick,previous==TacticalIntent.REPOSITION && tick-stateSince==4);
        boolean coverDeferred=!movement.blocked(tick) && covers.deferred();
        if(covers.revision()!=coverRevision || coverDeferred)nextPlan=0;
        CombatSnapshot snapshot=new CombatSnapshot(new SelfState(frame,vector(npc.position()),Math.max(0,Math.min(1,npc.getHealth()/npc.getMaxHealth())),npc.isAlive(),npc.hurtTime>0),
                observation,gun,new TerrainState(coverOptions.stream().map(CoverOption::position).toList(),List.of(),movement.blocked(tick),clear,occupied,coverOptions),new TacticalState(previous==null?TacticalIntent.PATROL:previous,stateSince,stateSince+20,strafe));
        if(decision==null || tick>=nextPlan || gun.rounds()!=previousRounds || gun.busy()!=previousBusy || npc.hurtTime>0 && previous!=TacticalIntent.TAKE_COVER && previous!=TacticalIntent.RETREAT || npc.getHealth()/npc.getMaxHealth()<=.25 && previous!=TacticalIntent.RETREAT) {
            decision=movementPlanner.plan(snapshot,new CombatBrain(ServerConfig.MEMORY.get(),gun.effectiveRange()).decide(snapshot)); nextPlan=tick+4;
            if(runtime.squadId()!=null)decision=squadPlanner.plan(snapshot,decision,squad.role(runtime.squadId(),npc.getUUID()));
            var role=runtime.squadId()==null?SquadBlackboard.Role.SUPPRESSOR:squad.role(runtime.squadId(),npc.getUUID());
            Vector3 flank=flankRoute.sample(runtime,snapshot,role==SquadBlackboard.Role.FLANKER,movement.blocked(tick));
            if(utility.flank(snapshot,role,flank!=null))
                decision=new CombatDecision(TacticalIntent.FLANK,false,"committed flank utility",decision.weaponAction()==ActionArbiter.WeaponAction.SHOOT?ActionArbiter.WeaponAction.NONE:decision.weaponAction(),MovementCommand.navigate(flank,MovementCommand.Locomotion.RELOCATION),decision.aim(),decision.frame(),decision.expires());
            else if(!visible && suppressionSectors.reposition(tick) && decision.intent()==TacticalIntent.SEARCH)
                decision=new CombatDecision(TacticalIntent.REPOSITION,false,"blocked suppression sectors").bind(snapshot);
            else if(role==SquadBlackboard.Role.ANCHOR && visible && decision.intent()==TacticalIntent.ENGAGE)
                decision=new CombatDecision(decision.intent(),decision.firePermitted(),"covering squad advance",decision.weaponAction(),MovementCommand.hold(),decision.aim(),decision.frame(),decision.expires());
            long patrolRevision=orderPlanner.patrolRevision();
            decision=orderPlanner.plan(snapshot,decision,runtime.orderState(),npc.getBbWidth(),movement.recoveryStatus());
            if(orderPlanner.patrolRevision()!=patrolRevision)movement.patrolLegChanged();
            if(ServerConfig.SUPPRESSION.get() && !visible && suppression.knowledgeValid(tick) && gun.supported() && (gun.rounds()>0 || gun.reserve()) && decision.intent()==TacticalIntent.SEARCH &&
                    suppressionCycle.active(tick,ServerConfig.SUPPRESSION_DURATION.get(),ServerConfig.SUPPRESSION_ROUNDS.get()))
                decision=new CombatDecision(decision.intent(),false,"pressure burst pause",decision.weaponAction()==ActionArbiter.WeaponAction.SHOOT?ActionArbiter.WeaponAction.NONE:decision.weaponAction(),MovementCommand.hold(),decision.aim(),decision.frame(),decision.expires());
            decision=suppressionPlanner.plan(snapshot,decision,suppression);
            if(decision.reason().equals("unusable peek timeout") && !coverOptions.isEmpty() && !covers.deferred()) covers.reject(coverOptions.get(0).position(),tick);
            previousRounds=gun.rounds(); previousBusy=gun.busy();
        }
        if(visible) decision=decision.track(observation,targetId,tick);
        if(visible && decision.aim().mode()==AimCommand.Mode.DIRECT) {
            var aim=gun.ballistics().aim(vector(npc.getEyePosition()),observation,12);
            decision=new CombatDecision(decision.intent(),decision.firePermitted(),decision.reason(),decision.weaponAction(),decision.movement(),new AimCommand(AimCommand.Mode.DIRECT,aim),decision.frame(),decision.expires());
        }
        boolean usesCover=decision.intent()==TacticalIntent.HOLD || decision.intent()==TacticalIntent.PEEK || decision.intent()==TacticalIntent.TAKE_COVER;
        if(coverDeferred && usesCover)decision=new CombatDecision(decision.intent(),false,"cover validation deferred",decision.weaponAction()==ActionArbiter.WeaponAction.SHOOT?ActionArbiter.WeaponAction.NONE:decision.weaponAction(),MovementCommand.hold(),decision.aim(),decision.frame(),decision.expires());
        if(npc instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity own && !own.neutral() && own.standPhase()==dev.zuma.tacticalnpc.entity.TacticalNpcEntity.StandPhase.WAIT)
            decision=new CombatDecision(decision.intent(),decision.firePermitted(),decision.reason(),decision.weaponAction(),MovementCommand.hold(),decision.aim(),decision.frame(),decision.expires());
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
            Vec3 point=vec(decision.aim().point());
            Vec3 end=npc.getEyePosition().add(Vec3.directionFromRotation(npc.getXRot(),npc.getYHeadRot()).scale(point.distanceTo(npc.getEyePosition())));
            clear=clear && sensor.los(npc,end,true)==VisibilitySensor.Los.CLEAR; occupied=occupied || sensor.occupied(npc,safetySubject,end,gun);
        }
        var commands=arbiter.commands(decision,frame,new ActionArbiter.Safety(npc.isAlive(),visible && sensor.valid(npc,target,range) && snapshot.targetDistanceBlocks()<=gun.effectiveRange(),visible,
                contact.ready(tick),aligned,clear,occupied,gun.busy() || !gun.supported(),gun.cooldownReady()),suppression);
        if(ServerConfig.DEBUG.get() && tick%40==0 && visible) debug("gates action="+commands.weapon()+" reason="+decision.reason()+" clear="+clear+" occupied="+occupied+" aligned="+aligned+" busy="+gun.busy()+" ready="+gun.cooldownReady()+" valid="+commands.valid());
        boolean relocating=decision.intent()==TacticalIntent.RETREAT || decision.intent()==TacticalIntent.ADVANCE || decision.intent()==TacticalIntent.TAKE_COVER || decision.intent()==TacticalIntent.REPOSITION;
        guns.aim(commands.valid() && !relocating && !commands.movement().sprint() && (visible || decision.aim().mode()==AimCommand.Mode.SUPPRESS_AREA)); npc.setAggressive(observation!=null);
        movement.subject(observation==null?null:observation.subject());
        movement.apply(commands.movement(),tick);
        long shots=guns.shots();
        guns.apply(commands.weapon(),tick);
        var level=(ServerLevel)npc.level();
        if(commands.weapon()!=ActionArbiter.WeaponAction.SHOOT) {
            AiTelemetry.gate(level,activeLos==VisibilitySensor.Los.DEFERRED?AiTelemetry.Gate.LOS_DEFERRED:!visible?observation==null?AiTelemetry.Gate.NO_CONTACT:AiTelemetry.Gate.LOS_BLOCKED:
                !contact.ready(tick)?AiTelemetry.Gate.REACTION:gun.busy() || !gun.supported()?AiTelemetry.Gate.WEAPON_BUSY:!gun.cooldownReady()?AiTelemetry.Gate.COOLDOWN:
                !aligned?AiTelemetry.Gate.TURN:occupied?AiTelemetry.Gate.ALLY:AiTelemetry.Gate.POLICY);
        } else if(guns.shots()==shots) AiTelemetry.gate(level,AiTelemetry.Gate.NATIVE_DECLINED);

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
        stop(false);
    }
    public void stopForOrderChange(){stop(true);}
    public void migrateHard(){runtime.requireServer();runtime.operator().cancelReload();stop();}
    private void stop(boolean orderChange) {
        orderPlanner.resetPatrol();
        generation=GENERATIONS.incrementAndGet(); targetId=null; candidateId=null; contact.clear();freshVisible=false;memory.clear();covers.reset(); decision=null;
        sensor.resetAcquisition();
        previous=null;stateSince=npc.level().getGameTime();nextPlan=0;previousRounds=-1;suppressionCycle.reset();suppressionSectors.reset();suppressionPoint=null;flankRoute.reset();utility.reset();unfairHints.reset();hint=null;
        nextActivation=0;
        blockedFireTicks=0;nextOpportunityScan=nextTargetChange=0;
        SquadRuntime.of((ServerLevel)npc.level()).release(npc.getUUID());
        try { guns.aim(false); } catch(RuntimeException | LinkageError ex) { TacticalNpcAiMod.LOGGER.warn("Could not reset aim for {}",npc.getUUID(),ex); }
        npc.setAggressive(false); npc.setTarget(null);
        if(orderChange)movement.orderChanged(npc.level().getGameTime());else movement.stop(npc.level().getGameTime());
    }
    private static Vector3 vector(Vec3 v) { return new Vector3(v.x,v.y,v.z); }
    private static Vec3 vec(Vector3 v) {return new Vec3(v.x(),v.y(),v.z());}
    public String movementDiagnostics(){return movement.actionDiagnostics();}
    private void debug(String message) { if(ServerConfig.DEBUG.get()) TacticalNpcAiMod.LOGGER.info("NPC {} {}",npc.getUUID(),message); }
}
