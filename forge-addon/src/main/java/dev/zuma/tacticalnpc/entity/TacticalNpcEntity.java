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
    private AmmoPolicy ammoPolicy=AmmoPolicy.INFINITE;
    private String faction="hostile", loadout="tacz:ak47", order="HOLD_POSITION";
    private int initialReserve=120;
    private UUID serviceId=UUID.randomUUID();
    private UUID squadId;
    private double leash=24;
    private Vector3 anchor;
    private boolean initialized;
    private TacticalCombatGoal controller;
    private final NpcRuntimeAdapter runtime=new NpcRuntimeAdapter() {
        @Override public TacticalNpcEntity entity() { return TacticalNpcEntity.this; }
        @Override public UUID squadId(){return squadId;}
        @Override public OrderState orderState() {
            requireServer();
            if(anchor==null) anchor=new Vector3(getX(),getY(),getZ());
            return new OrderState(OrderState.Kind.valueOf(order),anchor,leash);
        }
        @Override public boolean allied(LivingEntity other) {
            return TacticalNpcEntity.this.isAlliedTo(other) || other instanceof TacticalNpcEntity npc && faction.equals(npc.faction);
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
    public String faction() { return faction; }
    public String order() { return order; }
    public void setSquad(UUID id) {runtime.requireServer();if(controller!=null)controller.stop();squadId=id;}
    public void setOrder(OrderState.Kind kind,double radius) {
        runtime.requireServer();
        if(!OrderState.validRadius(kind,radius)) throw new IllegalArgumentException("Order radius must be 4.."+OrderState.maximumRadius(kind)+" blocks");
        order=kind.name();leash=radius;anchor=new Vector3(getX(),getY(),getZ());
        if(controller!=null) controller.stop();
        getNavigation().stop();
    }
    public void setFaction(String value) {
        runtime.requireServer();
        if(!value.matches("[a-zA-Z0-9_-]{1,32}")) throw new IllegalArgumentException("Faction must contain 1..32 letters, digits, _ or -");
        faction=value;if(controller!=null) controller.stop();
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
        if(controller!=null) { goalSelector.removeGoal(controller); controller.stop(); controller=null; }
        RuntimeRegistry.release(this);
    }
    @Override public void remove(Entity.RemovalReason reason) { if(!level().isClientSide) release(); super.remove(reason); }
    @Override protected void removeAfterChangingDimensions() { if(!level().isClientSide) release(); super.removeAfterChangingDimensions(); }
    @Override public void addAdditionalSaveData(CompoundTag root) {
        super.addAdditionalSaveData(root);
        CompoundTag tag=new CompoundTag(); tag.putInt("schema",2); tag.putUUID("serviceId",serviceId);
        tag.putString("loadout",loadout); tag.putString("faction",faction); tag.putString("order",order);
        tag.putString("ammoPolicy",ammoPolicy.name()); tag.putInt("initialReserve",initialReserve); tag.putDouble("leash",leash);
        Vector3 saved=anchor==null?new Vector3(getX(),getY(),getZ()):anchor;
        tag.putDouble("anchorX",saved.x());tag.putDouble("anchorY",saved.y());tag.putDouble("anchorZ",saved.z());
        if(squadId!=null)tag.putUUID("squad",squadId);
        root.put("tacticalnpc",tag);
    }
    @Override public void readAdditionalSaveData(CompoundTag root) {
        super.readAdditionalSaveData(root);
        CompoundTag tag=root.getCompound("tacticalnpc");
        if(tag.isEmpty()) return;
        if(tag.getInt("schema")<1 || tag.getInt("schema")>2) {
            removeTag(RuntimeRegistry.TAG); ammoPolicy=AmmoPolicy.FINITE; initialReserve=0;
            dev.zuma.tacticalnpc.TacticalNpcAiMod.LOGGER.warn("NPC {} unsupported NBT schema {}; tactical control disabled",getUUID(),tag.getInt("schema"));
            return;
        }
        if(tag.hasUUID("serviceId")) serviceId=tag.getUUID("serviceId");
        squadId=tag.hasUUID("squad")?tag.getUUID("squad"):null;
        if(tag.contains("loadout")) loadout=tag.getString("loadout");
        if(tag.contains("faction")) faction=tag.getString("faction");
        try {order=OrderState.Kind.valueOf(tag.getString("order")).name();}catch(IllegalArgumentException ex){order="HOLD_POSITION";}
        try { ammoPolicy=AmmoPolicy.valueOf(tag.getString("ammoPolicy")); } catch(IllegalArgumentException ex) { ammoPolicy=AmmoPolicy.FINITE; }
        initialReserve=Math.max(0,tag.getInt("initialReserve")); leash=Math.max(4,Math.min(OrderState.maximumRadius(OrderState.Kind.valueOf(order)),tag.getDouble("leash")));
        if(!Double.isFinite(leash)) leash=24;
        anchor=null;
        if(tag.getInt("schema")==2) {
            double x=tag.getDouble("anchorX"),y=tag.getDouble("anchorY"),z=tag.getDouble("anchorZ");
            if(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) anchor=new Vector3(x,y,z);
        }
        initialized=false;
    }
}
