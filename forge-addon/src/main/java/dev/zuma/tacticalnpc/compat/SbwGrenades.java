package dev.zuma.tacticalnpc.compat;

import dev.zuma.tacticalnpc.TacticalNpcAiMod;
import dev.zuma.tacticalnpc.combat.CombatTargets;
import dev.zuma.tacticalnpc.config.ServerConfig;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/** Exact optional SBW API boundary. Vanilla registry and projectile APIs stay reobfuscatable. */
public final class SbwGrenades {
    public static final ResourceLocation RGO=new ResourceLocation("superbwarfare","rgo_grenade");
    private static final ResourceLocation THROW_SOUND=new ResourceLocation("superbwarfare","grenade_throw");
    private static final String MARK="tacticalnpc_sbw_rgo", STOCK="tacticalnpc_rgo_stock";
    private static final String PACKAGE="com.atsuishio.superbwarfare.";
    private static final double VERIFIED_GRAVITY=(double).05f;
    public static final double MAX_SPEED=1.8f;
    public static final int LIFE=65;
    private record Backend(EntityType<?> type, Class<?> projectileClass, Method life, Method gravity,
                           ForgeConfigSpec.IntValue radius, boolean legacyImpact) {}
    private static Backend backend;

    private SbwGrenades() {}

    public static void initialize() {
        if(backend!=null)return;
        var mod=ModList.get().getModContainerById("superbwarfare").orElse(null);
        if(mod==null)return;
        String version=mod.getModInfo().getVersion().toString();
        boolean legacyImpact=version.equals("0.8.9");
        if(!legacyImpact && !version.equals("0.8.9.1")) {
            TacticalNpcAiMod.LOGGER.warn("NPC RGO disabled for unsupported SBW {}",version);
            return;
        }
        try {
            var loader=mod.getMod().getClass().getClassLoader();
            var projectile=Class.forName(PACKAGE+"entity.projectile.RgoGrenadeEntity",false,loader).asSubclass(Projectile.class);
            var type=ForgeRegistries.ENTITY_TYPES.getValue(RGO);
            var config=Class.forName(PACKAGE+"config.server.ExplosionConfig",false,loader);
            if(type==null || !ForgeRegistries.ITEMS.containsKey(RGO))throw new IllegalStateException("RGO registries missing");
            var life=projectile.getMethod("setLife",int.class);
            var gravity=projectile.getMethod(legacyImpact?"getGravityValue":"getCustomGravity");
            if(gravity.getReturnType()!=float.class)throw new IllegalStateException("RGO gravity signature");
            var radius=(ForgeConfigSpec.IntValue)config.getField("RGO_GRENADE_EXPLOSION_RADIUS").get(null);
            if(!legacyImpact) {
                var hit=Class.forName(PACKAGE+"api.event.ProjectileHitEvent$HitEntity",false,loader).asSubclass(Event.class);
                installImpactFilter(hit,hit.getMethod("getProjectile"),hit.getMethod("getTarget"));
            }
            backend=new Backend(type,projectile,life,gravity,radius,legacyImpact);
            TacticalNpcAiMod.LOGGER.info("Verified SBW {} NPC RGO registry and API",version);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError ex) {
            TacticalNpcAiMod.LOGGER.warn("NPC RGO disabled because SBW API verification failed",ex);
        }
    }

    private static <T extends Event> void installImpactFilter(Class<T> event, Method projectile, Method target) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,event,e->{
            try {
                var source=(Projectile)projectile.invoke(e);
                if(marked(source) && source.level() instanceof ServerLevel && !mayHarm(source,(Entity)target.invoke(e)))e.setCanceled(true);
            } catch(ReflectiveOperationException ex) {
                e.setCanceled(true);
                TacticalNpcAiMod.LOGGER.error("SBW hit safety adapter failed",ex);
            }
        });
    }

    public static boolean available(){return backend!=null;}
    public static double gravity(){return VERIFIED_GRAVITY;}
    public static boolean legacyImpact(){return backend!=null && backend.legacyImpact();}

    public static double dangerRadius(){return backend==null?0:2.0*backend.radius().get();}

    public static int stock(LivingEntity npc) {
        var data=npc.getPersistentData();
        if(available() && !data.contains(STOCK))data.putInt(STOCK,ServerConfig.GRENADE_STOCK.get());
        return Math.max(0,data.getInt(STOCK));
    }

    public static boolean release(LivingEntity owner, Vec3 at, Vec3 velocity) {
        var current=backend;
        if(current==null || !(owner.level() instanceof ServerLevel level) || !level.getServer().isSameThread() || stock(owner)==0)return false;
        if(!(current.type().create(level) instanceof Projectile grenade) || grenade.getClass()!=current.projectileClass())return false;
        try {
            if(Double.compare(((Float)current.gravity().invoke(grenade)).doubleValue(),VERIFIED_GRAVITY)!=0)
                throw new IllegalStateException("RGO gravity changed");
            current.life().invoke(grenade,LIFE);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError ex) {
            backend=null;
            TacticalNpcAiMod.LOGGER.warn("NPC RGO disabled during native launch",ex);
            return false;
        }
        grenade.moveTo(at.x,at.y,at.z,(float)Math.toDegrees(Math.atan2(velocity.x,velocity.z)),
                (float)Math.toDegrees(Math.atan2(velocity.y,velocity.horizontalDistance())));
        grenade.setOwner(owner);
        grenade.setDeltaMovement(velocity);
        grenade.addTag(MARK);
        if(current.legacyImpact())grenade.getPersistentData().putBoolean("tacticalnpc_rgo_legacy_impact",true);
        if(owner instanceof TacticalNpcEntity npc && npc.ownerMatchId()!=null)
            grenade.getPersistentData().putUUID("tacticalnpc_match",npc.ownerMatchId());
        if(!level.addFreshEntity(grenade))return false;
        owner.getPersistentData().putInt(STOCK,stock(owner)-1);
        var sound=ForgeRegistries.SOUND_EVENTS.getValue(THROW_SOUND);
        if(sound!=null)level.playSound(null,owner.blockPosition(),sound,SoundSource.HOSTILE,1,1);
        return true;
    }

    public static boolean marked(Entity entity) {
        return entity instanceof Projectile && entity.getTags().contains(MARK) && RGO.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
    }

    static boolean hasCompanionBlast(Projectile source) {
        return source.getPersistentData().getBoolean("tacticalnpc_rgo_legacy_impact");
    }

    public static boolean mayHarm(Projectile source, Entity victim) {
        return mayHarm(source.getOwner(),source,victim);
    }

    static boolean mayHarm(Entity attacker, Projectile source, Entity victim) {
        if(!(attacker instanceof LivingEntity owner) || !owner.isAlive() || owner.isRemoved() ||
                !(victim instanceof LivingEntity living))return false;
        var runtime=RuntimeRegistry.adapt(owner);
        return runtime!=null && ShotSafetyEvents.matchDamageAllowed(owner,source,victim) && CombatTargets.eligible(runtime.entity(),living);
    }
}
