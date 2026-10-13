package dev.zuma.tacticalnpc.runtime;
import dev.zuma.tacticalnpc.combat.TacticalCombatGoal;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
public final class RuntimeRegistry {
    public static final String TAG = "tactical_ai";
    private static final List<Function<LivingEntity,NpcRuntimeAdapter>> PROVIDERS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final Map<LivingEntity,WeakReference<TacticalCombatGoal>> ACTIVE = new WeakHashMap<>();
    private static volatile Function<ServerLevel,NpcRuntimeAdapter> spawner;
    private static volatile Function<ServerLevel,NpcRuntimeAdapter> legacySpawner;
    public static void register(Function<LivingEntity,NpcRuntimeAdapter> provider, Function<ServerLevel,NpcRuntimeAdapter> create) { PROVIDERS.add(provider); legacySpawner=create; }
    public static void registerIndependent(Function<LivingEntity,NpcRuntimeAdapter> provider, Function<ServerLevel,NpcRuntimeAdapter> create) { PROVIDERS.add(provider); spawner=create; }
    public static NpcRuntimeAdapter createLegacy(ServerLevel level) {
        if(legacySpawner==null) throw new IllegalArgumentException("Install the NPCs compatibility artifact and NPCs 2.1.0 first"); return legacySpawner.apply(level);
    }
    public static NpcRuntimeAdapter adapt(LivingEntity entity) {
        for(var provider:PROVIDERS) { var runtime=provider.apply(entity); if(runtime!=null) return runtime; } return null;
    }
    public static NpcRuntimeAdapter create(ServerLevel level) {
        if(spawner==null) throw new IllegalStateException("No NPC runtime registered"); return spawner.apply(level);
    }
    public static void activate(LivingEntity entity,TacticalCombatGoal goal) { ACTIVE.put(entity,new WeakReference<>(goal)); }
    public static void release(LivingEntity entity) { ACTIVE.remove(entity); }
    public static TacticalCombatGoal active(LivingEntity entity) { var ref=ACTIVE.get(entity); return ref==null?null:ref.get(); }
    public static List<TacticalCombatGoal> activeIn(ServerLevel level) {
        List<TacticalCombatGoal> result=new ArrayList<>();
        for(var entry:ACTIVE.entrySet()) if(entry.getKey().level()==level) { var goal=entry.getValue().get(); if(goal!=null) result.add(goal); }
        return result;
    }
    private RuntimeRegistry() {}
}
