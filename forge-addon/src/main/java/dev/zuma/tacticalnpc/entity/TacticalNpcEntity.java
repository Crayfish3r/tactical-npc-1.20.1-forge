package dev.zuma.tacticalnpc.entity;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import dev.zuma.tacticalnpc.combat.TacticalCombatGoal;
import dev.zuma.tacticalnpc.combat.TacticalLookControl;
import dev.zuma.tacticalnpc.config.ServerConfig;
import dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import dev.zuma.tacticalnpc.core.OrderState;
import dev.zuma.tacticalnpc.core.Vector3;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Own runtime; all weapon tick maintenance belongs to TaCZ's LivingEntityMixin. */
public final class TacticalNpcEntity extends PathfinderMob {
    public enum AmmoPolicy { INFINITE, FINITE }
    public enum StandPhase { NONE, WAIT, COMBAT }
    private StandPhase standPhase=StandPhase.WAIT;
    private UUID standDamageSource;
    private long standDamageUntil;
    private AmmoPolicy ammoPolicy=AmmoPolicy.INFINITE;
    private String loadout="tacz:ak47", order="HOLD_POSITION";
    private String legacyFaction;
    private UUID legacySquad;
    private boolean pendingLegacyTeam;
    private net.minecraft.world.scores.Team observedTeam;
    private int initialReserve=120;
    private UUID serviceId=UUID.randomUUID();
    private double leash=24;
    private Vector3 anchor;
    private boolean initialized;
    private boolean neutral;
    // Transient identity only: never serialize an attacker, live target or hidden coordinates.
    private UUID reactiveAttacker;
    private long reactiveUntil;
    private TacticalCombatGoal controller;
    private final NpcRuntimeAdapter runtime=new NpcRuntimeAdapter() {
        @Override public TacticalNpcEntity entity() { return TacticalNpcEntity.this; }
        @Override public UUID squadId(){return dev.zuma.tacticalnpc.runtime.NpcTeams.group(TacticalNpcEntity.this);}
        @Override public OrderState orderState() {
            requireServer();
            if(anchor==null) anchor=new Vector3(getX(),getY(),getZ());
            if(!neutral && standPhase!=StandPhase.NONE)return new OrderState(OrderState.Kind.DEFEND_AREA,anchor,6);
            return new OrderState(OrderState.Kind.valueOf(order),anchor,leash);
        }
        @Override public boolean allied(LivingEntity other) {
            return dev.zuma.tacticalnpc.runtime.NpcTeams.allied(TacticalNpcEntity.this,other);
        }
        @Override public void onEquip(ItemStack stack) {
            requireServer(); var gun=IGun.getIGunOrNull(stack);
            if(gun!=null) {
                loadout=gun.getGunId(stack).toString();
                if(ammoPolicy==AmmoPolicy.FINITE) { gun.setDummyAmmoAmount(stack,initialReserve); gun.setMaxDummyAmmoAmount(stack,initialReserve); }
            }
        }
    };
    public TacticalNpcEntity(EntityType<? extends PathfinderMob> type,Level level) {
        super(type,level); lookControl=new TacticalLookControl(this); setPersistenceRequired();
        for(var hazard:new net.minecraft.world.level.pathfinder.BlockPathTypes[]{net.minecraft.world.level.pathfinder.BlockPathTypes.WATER,net.minecraft.world.level.pathfinder.BlockPathTypes.LAVA,net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_FIRE,net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_FIRE}) setPathfindingMalus(hazard,-1);
    }
    public NpcRuntimeAdapter runtime() { return runtime; }
    public TacticalCombatGoal controller() { return controller; }
    public UUID serviceId() { return serviceId; }
    public AmmoPolicy ammoPolicy() { return ammoPolicy; }
    /** Compatibility getter; there is no independent faction membership. */
    public String faction() { var name=dev.zuma.tacticalnpc.runtime.NpcTeams.name(this);return name==null?"":name; }
    public void syncTeam() {
        runtime.requireServer();
        if(pendingLegacyTeam){
            pendingLegacyTeam=false;
            try{
                dev.zuma.tacticalnpc.runtime.NpcTeams.validate(legacyFaction);
                if(getTeam()==null)dev.zuma.tacticalnpc.runtime.NpcTeams.assign(this,legacyFaction);
                if(legacySquad!=null || !faction().equals(legacyFaction))dev.zuma.tacticalnpc.TacticalNpcAiMod.LOGGER.warn("NPC {} migrated to scoreboard team '{}'; former faction '{}' and squad '{}' archived in schema5",getUUID(),faction(),legacyFaction,legacySquad);
            }catch(IllegalArgumentException ex){removeTag(RuntimeRegistry.TAG);dev.zuma.tacticalnpc.TacticalNpcAiMod.LOGGER.warn("NPC {} invalid legacy team; tactical control disabled: {}",getUUID(),ex.getMessage());}
        }
        var current=getTeam();if(observedTeam==current)return;
        if(controller!=null)controller.stop();
        clearRetaliation();standDamageSource=null;standDamageUntil=0;observedTeam=current;
    }
    public String order() { return order; }
    public StandPhase standPhase() {return standPhase;}
    public void standShot() {
        runtime.requireServer();
        if(!neutral && standPhase==StandPhase.WAIT) {standPhase=StandPhase.COMBAT;if(controller!=null)controller.standTriggered();}
    }
    public boolean standDamageContact() {
        runtime.requireServer();
        return standDamageSource!=null && level().getGameTime()<=standDamageUntil &&
            dev.zuma.tacticalnpc.combat.CombatTargets.eligible(this,dev.zuma.tacticalnpc.combat.CombatTargets.resolve((net.minecraft.server.level.ServerLevel)level(),standDamageSource));
    }
    public void endStandContact() {
        runtime.requireServer();
        if(standPhase!=StandPhase.COMBAT)return;
        standPhase=StandPhase.NONE;standDamageSource=null;standDamageUntil=0;order=OrderState.Kind.DEFEND_AREA.name();leash=32;
        if(controller!=null)controller.stop();
    }
    public boolean neutral() { return neutral; }
    public UUID reactiveAttacker() { return reactiveAttacker; }
    public boolean reactiveTo(UUID id) { return id!=null && id.equals(reactiveAttacker); }
    public long reactiveUntil() { return reactiveUntil; }
    public void enterNeutral() {
        runtime.requireServer();setOrder(OrderState.Kind.HOLD_POSITION,24);neutral=true;standPhase=StandPhase.NONE;leash=24;
    }
    public void clearRetaliation() { runtime.requireServer();reactiveAttacker=null;reactiveUntil=0; }
    public void refreshRetaliation() {
        runtime.requireServer();if(reactiveAttacker!=null)reactiveUntil=level().getGameTime()+ServerConfig.MEMORY.get();
    }
    @Override protected void actuallyHurt(net.minecraft.world.damagesource.DamageSource source,float amount) {
        float before=getHealth()+getAbsorptionAmount();
        super.actuallyHurt(source,amount);
        if(level().isClientSide || !isAlive() || !(getHealth()+getAbsorptionAmount()<before))return;
        // Runs after Forge hurt/damage cancellation and reductions, not from a pre-damage event.
        if(!(source.getEntity() instanceof LivingEntity attacker) ||
                !dev.zuma.tacticalnpc.combat.CombatTargets.validSubject(this,attacker))return;
        Entity direct=source.getDirectEntity();
        if(direct!=attacker && !(direct instanceof net.minecraft.world.entity.projectile.Projectile projectile && projectile.getOwner()==attacker))return;
        if(!neutral) {
            if(standPhase!=StandPhase.NONE && dev.zuma.tacticalnpc.combat.CombatTargets.eligible(this,attacker)) {
                standDamageSource=attacker.getUUID();standDamageUntil=level().getGameTime()+ServerConfig.MEMORY.get();standShot();
            }
            return;
        }
        runtime.requireServer();boolean changed=!reactiveTo(attacker.getUUID());
        reactiveAttacker=attacker.getUUID();refreshRetaliation();
        if(changed && controller!=null)controller.retaliate(reactiveAttacker);
    }
    /** Legacy UUID syntax joins the corresponding named team, rather than a second squad. */
    public void setSquad(UUID id) {runtime.requireServer();legacySquad=id;dev.zuma.tacticalnpc.runtime.NpcTeams.assign(this,id==null?null:id.toString());}
    public void setOrder(OrderState.Kind kind,double radius) {
        runtime.requireServer();
        if(!OrderState.validRadius(kind,radius)) throw new IllegalArgumentException("Order radius must be 4.."+OrderState.maximumRadius(kind)+" blocks");
        neutral=false;clearRetaliation();
        standPhase=kind==OrderState.Kind.HOLD_POSITION?StandPhase.WAIT:StandPhase.NONE;standDamageSource=null;standDamageUntil=0;
        order=kind.name();leash=radius;anchor=new Vector3(getX(),getY(),getZ());
        if(standPhase==StandPhase.WAIT)leash=6;
        if(controller!=null) controller.stop();
        getNavigation().stop();
    }
    public void setFaction(String value) {
        runtime.requireServer();
        dev.zuma.tacticalnpc.runtime.NpcTeams.validate(value);
        syncTeam();
        legacyFaction=value;
        dev.zuma.tacticalnpc.runtime.NpcTeams.assign(this,value);
    }
    public void setAmmoPolicy(AmmoPolicy policy,int reserve) {
        runtime.requireServer();
        if(runtime.operator().getDataHolder().reloadStateType.isReloading()) runtime.operator().cancelReload();
        if(controller!=null) controller.stop();
        ammoPolicy=policy; initialReserve=Math.max(0,reserve);
        var gun=IGun.getIGunOrNull(getMainHandItem());
        if(gun!=null && policy==AmmoPolicy.FINITE) { gun.setDummyAmmoAmount(getMainHandItem(),initialReserve); gun.setMaxDummyAmmoAmount(getMainHandItem(),initialReserve); }
    }
    // This overrides the method supplied to LivingEntity by TaCZ, without implementing/copying its operator.
    public boolean needCheckAmmo() { return ammoPolicy==AmmoPolicy.FINITE; }
    @Override protected void registerGoals() { goalSelector.addGoal(0,new FloatGoal(this)); }
    @Override protected net.minecraft.world.entity.ai.control.BodyRotationControl createBodyControl() {
        return new dev.zuma.tacticalnpc.combat.TacticalBodyControl(this,super.createBodyControl());
    }
    @Override public void tick() {
        if(!level().isClientSide) {
            runtime.requireServer();
            syncTeam();
            if(!initialized) { runtime.operator().initialData(); initialized=true; }
            boolean enabled=isAlive() && ServerConfig.ENABLED.get() && getTags().contains(RuntimeRegistry.TAG);
            if(enabled && controller==null) {
                controller=new TacticalCombatGoal(runtime,this); goalSelector.addGoal(1,controller); RuntimeRegistry.activate(this,controller);
            } else if(!enabled && controller!=null) release();
            if(controller!=null && controller.failed()) { removeTag(RuntimeRegistry.TAG); release(); }
        }
        super.tick();
    }
    public void release() {
        clearRetaliation();
        standDamageSource=null;standDamageUntil=0;
        if(controller!=null) { goalSelector.removeGoal(controller); controller.stop(); controller=null; }
        RuntimeRegistry.release(this);
    }
    @Override public void remove(Entity.RemovalReason reason) { if(!level().isClientSide) release(); super.remove(reason); }
    @Override protected void removeAfterChangingDimensions() { if(!level().isClientSide) release(); super.removeAfterChangingDimensions(); }
    @Override public void addAdditionalSaveData(CompoundTag root) {
        super.addAdditionalSaveData(root);
        CompoundTag tag=new CompoundTag(); tag.putInt("schema",5); tag.putUUID("serviceId",serviceId);
        tag.putString("standPhase",standPhase.name());
        tag.putBoolean("neutral",neutral);
        tag.putString("loadout",loadout); tag.putString("order",order);
        // Native scoreboard.dat persists team membership, including unloaded NPC UUID entries.
        if(legacyFaction!=null)tag.putString("legacyFaction",legacyFaction);
        if(legacySquad!=null)tag.putUUID("legacySquad",legacySquad);
        if(pendingLegacyTeam)tag.putBoolean("pendingLegacyTeam",true);
        tag.putString("ammoPolicy",ammoPolicy.name()); tag.putInt("initialReserve",initialReserve); tag.putDouble("leash",leash);
        Vector3 saved=anchor==null?new Vector3(getX(),getY(),getZ()):anchor;
        tag.putDouble("anchorX",saved.x());tag.putDouble("anchorY",saved.y());tag.putDouble("anchorZ",saved.z());
        root.put("tacticalnpc",tag);
    }
    @Override public void readAdditionalSaveData(CompoundTag root) {
        super.readAdditionalSaveData(root);
        CompoundTag tag=root.getCompound("tacticalnpc");
        if(tag.isEmpty()) return;
        reactiveAttacker=null;reactiveUntil=0;neutral=false;
        pendingLegacyTeam=false;
        standDamageSource=null;standDamageUntil=0;standPhase=StandPhase.NONE;
        if(tag.getInt("schema")<1 || tag.getInt("schema")>5) {
            removeTag(RuntimeRegistry.TAG); ammoPolicy=AmmoPolicy.FINITE; initialReserve=0;
            dev.zuma.tacticalnpc.TacticalNpcAiMod.LOGGER.warn("NPC {} unsupported NBT schema {}; tactical control disabled",getUUID(),tag.getInt("schema"));
            return;
        }
        if(tag.hasUUID("serviceId")) serviceId=tag.getUUID("serviceId");
        if(tag.contains("loadout")) loadout=tag.getString("loadout");
        if(tag.getInt("schema")<5){
            legacyFaction=tag.contains("faction")?tag.getString("faction"):"hostile";
            legacySquad=tag.hasUUID("squad")?tag.getUUID("squad"):null;
            // NBT decoding may run on the entity deserializer. Native team mutation waits for server activation.
            pendingLegacyTeam=true;
        } else {
            legacyFaction=tag.contains("legacyFaction")?tag.getString("legacyFaction"):null;
            legacySquad=tag.hasUUID("legacySquad")?tag.getUUID("legacySquad"):null;
            pendingLegacyTeam=tag.getBoolean("pendingLegacyTeam") && legacyFaction!=null;
        }
        neutral=tag.getInt("schema")>=3 && tag.getBoolean("neutral");
        try {order=OrderState.Kind.valueOf(tag.getString("order")).name();}catch(IllegalArgumentException ex){order="HOLD_POSITION";}
        if(!neutral && order.equals("HOLD_POSITION")) {
            try {standPhase=tag.getInt("schema")>=4?StandPhase.valueOf(tag.getString("standPhase")):StandPhase.WAIT;}
            catch(IllegalArgumentException ex){standPhase=StandPhase.WAIT;}
        }
        try { ammoPolicy=AmmoPolicy.valueOf(tag.getString("ammoPolicy")); } catch(IllegalArgumentException ex) { ammoPolicy=AmmoPolicy.FINITE; }
        initialReserve=Math.max(0,tag.getInt("initialReserve")); leash=Math.max(4,Math.min(OrderState.maximumRadius(OrderState.Kind.valueOf(order)),tag.getDouble("leash")));
        if(!Double.isFinite(leash)) leash=24;
        anchor=null;
        if(tag.getInt("schema")>=2) {
            double x=tag.getDouble("anchorX"),y=tag.getDouble("anchorY"),z=tag.getDouble("anchorZ");
            if(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) anchor=new Vector3(x,y,z);
        }
        initialized=false;
    }
}
