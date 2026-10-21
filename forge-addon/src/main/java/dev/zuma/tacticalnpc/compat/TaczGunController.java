package dev.zuma.tacticalnpc.compat;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import dev.zuma.tacticalnpc.TacticalNpcAiMod;
import dev.zuma.tacticalnpc.config.ServerConfig;
import dev.zuma.tacticalnpc.core.ActionArbiter.WeaponAction;
import net.minecraft.world.entity.LivingEntity;
import com.tacz.guns.api.entity.IGunOperator;
import dev.zuma.tacticalnpc.runtime.NpcRuntimeAdapter;
import dev.zuma.tacticalnpc.runtime.GunController;
import dev.zuma.tacticalnpc.core.WeaponState;
import dev.zuma.tacticalnpc.core.WeaponProfile;
import com.tacz.guns.api.GunProperties;
import net.minecraft.world.item.ItemStack;

public final class TaczGunController implements GunController {
    public record GunSnapshot(boolean supported, int rounds, boolean reserve, boolean bolt,
                              boolean busy, boolean ready) {}
    private final LivingEntity npc;
    private final NpcRuntimeAdapter runtime;
    private final IGunOperator operator;
    private ItemStack drawnStack = ItemStack.EMPTY;
    private net.minecraft.resources.ResourceLocation drawnId;
    private long lastActionTick = Long.MIN_VALUE, nextReloadTick,nextBoltTick;
    private long shots, reloads;
    private long nextVolley;
    private int volleyRequests;
    private String lastRejection = "";
    private long reads,lookups,draws,profiles;
    public long[] measurements(){return new long[]{reads,lookups,draws,profiles};}
    private java.util.Optional<com.tacz.guns.resource.index.CommonGunIndex> index(net.minecraft.resources.ResourceLocation id){lookups++;return TimelessAPI.getCommonGunIndex(id);}
    private WeaponProfile profile(com.tacz.guns.resource.index.CommonGunIndex index){profiles++;return WeaponProfiles.from(index);}
    private record Metadata(ItemStack stack,net.minecraft.resources.ResourceLocation id,com.tacz.guns.api.item.gun.FireMode mode,long generation,
                            Object properties,com.tacz.guns.resource.index.CommonGunIndex index,WeaponProfile profile){}
    private Metadata metadata;
    private long maintained=Long.MIN_VALUE,drawnGeneration=-1;
    private Metadata metadata(ItemStack stack,IGun gun) {
        if(gun==null)return null;
        var id=gun.getGunId(stack);var mode=gun.getFireMode(stack);long generation=GunSupport.resourceGeneration();
        if(metadata==null || metadata.stack()!=stack || !metadata.id().equals(id) || metadata.mode()!=mode || metadata.generation()!=generation || metadata.properties()!=operator.getCacheProperty()) {
            var index=index(id).orElse(null);
            metadata=new Metadata(stack,id,mode,generation,operator.getCacheProperty(),index,index==null?WeaponProfile.of(WeaponProfile.Kind.CONSERVATIVE):profile(index));
        }
        return metadata;
    }
    /** Native state changes belong only to this explicit server tick point. */
    @Override public void maintain(long tick) {
        runtime.requireServer();if(maintained==tick)return;long started=System.nanoTime();
        try{maintainTick(tick);}finally{dev.zuma.tacticalnpc.combat.AiTelemetry.component((net.minecraft.server.level.ServerLevel)npc.level(),dev.zuma.tacticalnpc.combat.AiTelemetry.Component.NATIVE_MAINTENANCE,System.nanoTime()-started);}
    }
    private void maintainTick(long tick) {
        runtime.requireServer();if(maintained==tick)return;maintained=tick;
        var stack=npc.getMainHandItem();var gun=IGun.getIGunOrNull(stack);var data=metadata(stack,gun);
        if(data==null || data.index()==null || !GunSupport.rejection(stack,data.index()).isEmpty())return;
        if(stack!=drawnStack || !data.id().equals(drawnId) || drawnGeneration!=data.generation()) {
            draws++;operator.draw(npc::getMainHandItem);drawnStack=stack;drawnId=data.id();drawnGeneration=data.generation();
            nextVolley=0;volleyRequests=0;
        }
    }

    public TaczGunController(NpcRuntimeAdapter runtime) {
        this.runtime=runtime; this.npc=runtime.entity(); this.operator=runtime.operator();
    }
    @Override public WeaponState snapshot() {
        runtime.requireServer();
        reads++;
        var state=observe(); var gun=IGun.getIGunOrNull(npc.getMainHandItem());
        var data=metadata(npc.getMainHandItem(),gun);
        WeaponProfile profile=data==null?WeaponProfile.of(WeaponProfile.Kind.CONSERVATIVE):data.profile();
        double range=profile.maximum(),spread=Double.NaN;
        if(operator.getCacheProperty()!=null) {
            Float falloff=operator.getCacheProperty().getCache(GunProperties.EFFECTIVE_RANGE);
            if(falloff!=null && Float.isFinite(falloff) && falloff>0) range=Math.min(range,falloff);
            java.util.Map<com.tacz.guns.resource.pojo.data.gun.InaccuracyType,Float> spreads=operator.getCacheProperty().getCache(GunProperties.INACCURACY);
            if(spreads!=null && !spreads.isEmpty()) {spread=0;for(Float value:spreads.values())spread=Math.max(spread,value);}
        }
        return new WeaponState(gun==null?"none":gun.getGunId(npc.getMainHandItem()).toString(),state.rounds(),state.reserve(),state.bolt(),state.busy(),state.ready(),state.supported(),range,
                gun==null?"NONE":gun.getFireMode(npc.getMainHandItem()).name(),profile,spread);
    }
    @Override public void aim(boolean aiming) { runtime.requireServer(); operator.aim(aiming); }

    public GunSnapshot observe() {
        runtime.requireServer();
        ItemStack stack = npc.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return new GunSnapshot(false, 0, false, false, true, false);
        var data=metadata(stack,gun);var index=data.index();
        // Inventory-fed and charge/explosive weapons are outside the MVP weapon contract.
        String rejection = index==null ? "missing gun index" : GunSupport.rejection(stack, index);
        if(rejection.isEmpty() && operator.needCheckAmmo() && index.getGunData().getReloadData().isInfinite()) rejection="native infinite reload conflicts with FINITE ammo policy";
        if (!rejection.isEmpty()) return blocked(rejection,gun.getGunId(stack).toString());
        if(stack!=drawnStack || !data.id().equals(drawnId) || drawnGeneration!=data.generation())return new GunSnapshot(false,0,false,false,true,false);
        var cache=operator.getCacheProperty();
        if(cache==null) return new GunSnapshot(false,0,false,false,true,false);
        com.tacz.guns.resource.pojo.data.gun.ExplosionData explosion=cache.getCache(com.tacz.guns.api.GunProperties.EXPLOSION);
        com.tacz.guns.resource.pojo.data.gun.Ignite ignite=cache.getCache(com.tacz.guns.api.GunProperties.IGNITE);
        if(explosion!=null && explosion.isExplode() || ignite!=null && (ignite.isIgniteBlock() || ignite.isIgniteEntity())) {
            return blocked("explosive/incendiary attachment projectile",gun.getGunId(stack).toString());
        }
        lastRejection="";
        Bolt bolt = index.getGunData().getBolt();
        boolean chamber = bolt != Bolt.OPEN_BOLT && gun.hasBulletInBarrel(stack);
        int rounds = Math.max(0, gun.getCurrentAmmoCount(stack)) + (chamber ? 1 : 0);
        // hasInventoryAmmo is inventory-fed only in 1.1.8-hotfix2; it returns false for magazines.
        boolean reserve = !operator.needCheckAmmo() || gun instanceof com.tacz.guns.api.item.gun.AbstractGunItem logic && logic.canReload(npc,stack);
        boolean busy = operator.getDataHolder().reloadStateType.isReloading() || operator.getDataHolder().isBolting ||
                operator.getSynDrawCoolDown() != 0 || operator.getSynMeleeCoolDown() != 0;
        return new GunSnapshot(true, rounds, reserve, bolt == Bolt.MANUAL_ACTION && !chamber && rounds > 0,
                busy, operator.getSynShootCoolDown() == 0 && operator.getSynSprintTime()==0 && !npc.isSprinting() && npc.level().getGameTime()>=nextVolley);
    }

    public void apply(WeaponAction action, long tick) {
        runtime.requireServer();
        if (action == WeaponAction.NONE || tick == lastActionTick) return;
        GunSnapshot gun = observe();
        if (!gun.supported() || gun.busy()) return;
        lastActionTick = tick;
        if (action == WeaponAction.RELOAD && gun.reserve() && operator.getSynShootCoolDown()==0 && tick >= nextReloadTick) {
            nextReloadTick = tick + 20;
            operator.reload();
            if (operator.getDataHolder().reloadStateType.isReloading()) {
                reloads++;
                debug("reload started");
            } else debug("reload request declined by TaCZ");
        } else if (action == WeaponAction.BOLT && operator.getSynShootCoolDown()==0 && tick>=nextBoltTick) {
            nextBoltTick=tick+4;
            operator.bolt();
            if(operator.getDataHolder().isBolting) debug("bolt started");
        } else if (action == WeaponAction.SHOOT && gun.ready() && gun.rounds() > 0) {
            ShootResult result = operator.shoot(npc::getXRot, npc::getYHeadRot);
            if (result == ShootResult.SUCCESS) {
                shots++;debug("shot SUCCESS");
                var profile=metadata.profile();
                if(++volleyRequests>=profile.volleyRequests()) {volleyRequests=0;nextVolley=tick+profile.pauseTicks();}
            }
            else if (result == ShootResult.NEED_BOLT) operator.bolt();
            else if (result == ShootResult.NOT_DRAW) drawnStack=ItemStack.EMPTY;
            else if (tick % 20 == 0) debug("shot gated: " + result);
        }
    }

    public long shots() { return shots; }
    public long reloads() { return reloads; }
    private GunSnapshot blocked(String reason,String id) {
        if(!reason.equals(lastRejection)) TacticalNpcAiMod.LOGGER.warn("NPC {} weapon {} blocked: {}",npc.getUUID(),id,reason);
        lastRejection=reason;
        return new GunSnapshot(false,0,false,false,true,false);
    }
    private void debug(String message) {
        if (ServerConfig.DEBUG.get()) TacticalNpcAiMod.LOGGER.info("NPC {} {}", npc.getUUID(), message);
    }
}
