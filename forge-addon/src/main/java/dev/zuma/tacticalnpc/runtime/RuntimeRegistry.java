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
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.zuma.tacticalnpc.TacticalNpcAiMod.ID)
public final class RuntimeRegistry {
    public static final String TAG = "tactical_ai";
    private static final List<Function<LivingEntity,NpcRuntimeAdapter>> PROVIDERS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final Map<LivingEntity,WeakReference<TacticalCombatGoal>> ACTIVE = new WeakHashMap<>();
    // Loaded supported NPC identities only. No entity references or full-world scans.
    private static final Map<ServerLevel,Loaded> LOADED=new WeakHashMap<>();
    private static final class Loaded {
        final ArrayList<java.util.UUID> ids=new ArrayList<>();
        final Map<java.util.UUID,Integer> positions=new java.util.HashMap<>();
        void add(java.util.UUID id){if(!positions.containsKey(id)){positions.put(id,ids.size());ids.add(id);}}
        void remove(java.util.UUID id){Integer index=positions.remove(id);if(index==null)return;var last=ids.remove(ids.size()-1);if(index<ids.size()){ids.set(index,last);positions.put(last,index);}}
    }
    public static int loadedNpcCount(ServerLevel level){var loaded=LOADED.get(level);return loaded==null?0:loaded.ids.size();}
    public static LivingEntity loadedNpcAt(ServerLevel level,int index){
        var loaded=LOADED.get(level);if(loaded==null || index<0 || index>=loaded.ids.size())return null;
        var id=loaded.ids.get(index);var entity=level.getEntity(id);
        if(entity instanceof LivingEntity living && !entity.isRemoved())return living;
        loaded.remove(id);return null;
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof LivingEntity living && adapt(living)!=null)
            LOADED.computeIfAbsent(level,k->new Loaded()).add(living.getUUID());
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void left(net.minecraftforge.event.entity.EntityLeaveLevelEvent event){
        if(event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof LivingEntity living){
            dev.zuma.tacticalnpc.combat.AiWorkBudget.of(level).forget(living.getUUID());
            dev.zuma.tacticalnpc.combat.GunHearing.forget(level,living.getUUID());
            var loaded=LOADED.get(level);if(loaded!=null)loaded.remove(living.getUUID());
            for(var goal:activeIn(level))goal.forget(living.getUUID());
        }
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void unloaded(net.minecraftforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)LOADED.remove(level);}
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
