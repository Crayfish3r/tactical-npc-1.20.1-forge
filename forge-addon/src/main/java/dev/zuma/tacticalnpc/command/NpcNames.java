package dev.zuma.tacticalnpc.command;

import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Overworld SavedData owns names, including unloaded/dead identities. No strong Entity references. */
public final class NpcNames extends SavedData {
    private static final List<String> POOL=List.of("Steve","Kevin","John","Tyler","Alex","James","Jack","Daniel","David","Tom",
        "Mike","Sam","Ben","Luke","Ryan","Adam","Mark","Paul","Peter","Nick","Max","Jake","Josh","Chris","Matt",
        "Joe","Dan","Tim","Bob","Bill","Will","Harry","Henry","Oliver","Oscar","Leo","Liam","Noah","Ethan","Owen",
        "Dylan","Aaron","Eric","Ian","Sean","Scott","Dean","Cole","Evan","Nathan");
    public record Entry(UUID id,String name,String dimension,String mode) {}
    private final Map<UUID,Entry> entries=new HashMap<>();
    private final Map<String,UUID> owners=new HashMap<>();
    private record Loaded(ResourceKey<Level> dimension,int entityId,java.lang.ref.WeakReference<TacticalNpcEntity> entity) {}
    private final Map<UUID,Loaded> loaded=new HashMap<>();
    private int nextCandidate;
    public static NpcNames of(MinecraftServer server){
        if(!server.isSameThread())throw new IllegalStateException("NPC names require server thread");
        return server.overworld().getDataStorage().computeIfAbsent(NpcNames::load,NpcNames::new,"tacticalnpc_names");
    }
    public static boolean valid(String name){return name!=null && name.matches("[A-Za-z][A-Za-z0-9]{0,31}") && !name.equalsIgnoreCase("NaN") && !name.equalsIgnoreCase("Infinity");}
    private static String key(String name){return name.toLowerCase(Locale.ROOT);}
    /** Called only after native admission, so a canceled spawn never consumes a name. */
    public void join(TacticalNpcEntity npc){
        var old=entries.get(npc.getUUID());String name=old==null?npc.npcName():old.name();
        if(old==null){
            if(!valid(name) || owners.containsKey(key(name)))name=allocate();
            owners.put(key(name),npc.getUUID());
        }
        npc.assignNpcName(name);
        loaded.put(npc.getUUID(),new Loaded(npc.level().dimension(),npc.getId(),new java.lang.ref.WeakReference<>(npc)));
        update(npc);
    }
    private String allocate(){
        while(true){
            int candidate=nextCandidate++;int suffix=candidate/POOL.size()+1;
            String name=POOL.get(candidate%POOL.size())+(suffix==1?"":Integer.toString(suffix));
            if(!owners.containsKey(key(name)))return name;
        }
    }
    public void update(TacticalNpcEntity npc){
        if(!current(npc))return;
        var canonical=entries.get(npc.getUUID());
        if(canonical!=null && !canonical.name().equals(npc.npcName()))npc.assignNpcName(canonical.name());
        var entry=new Entry(npc.getUUID(),npc.npcName(),npc.level().dimension().location().toString(),npc.order());
        if(!entry.equals(entries.put(npc.getUUID(),entry)))setDirty();
    }
    public void leave(TacticalNpcEntity npc){
        if(current(npc)){update(npc);loaded.remove(npc.getUUID());}
    }
    private boolean current(TacticalNpcEntity npc){var entry=loaded.get(npc.getUUID());return entry!=null && entry.entity().get()==npc && entry.entityId()==npc.getId() && entry.dimension()==npc.level().dimension();}
    public Entry find(String name){var id=owners.get(key(name));return id==null?null:entries.get(id);}
    public TacticalNpcEntity resolve(MinecraftServer server,UUID id){
        var location=loaded.get(id);var npc=location==null?null:location.entity().get();
        // A newly transferred admitted entity may precede the destination's native tracking lookup.
        // A weak handle supports that interval without retaining an unloaded entity or loading its chunk.
        return npc!=null && npc.getServer()==server && current(npc) && npc.isAlive() && !npc.isRemoved()?npc:null;
    }
    public List<TacticalNpcEntity> loaded(MinecraftServer server){
        var result=new ArrayList<TacticalNpcEntity>();
        for(var id:loaded.keySet()){var npc=resolve(server,id);if(npc!=null)result.add(npc);}
        return result;
    }
    public List<Entry> entries(){return entries.values().stream().sorted(Comparator.comparing(Entry::name,String.CASE_INSENSITIVE_ORDER)).toList();}
    public static NpcNames load(CompoundTag root){
        if(root.getInt("schema")!=1)throw new IllegalStateException("Unsupported NPC name registry schema");
        var data=new NpcNames();data.nextCandidate=Math.max(0,root.getInt("nextCandidate"));
        for(var item:root.getList("entries",Tag.TAG_COMPOUND)){
            var tag=(CompoundTag)item;
            if(!tag.hasUUID("uuid") || !valid(tag.getString("name")))throw new IllegalStateException("Invalid NPC name registry entry");
            var entry=new Entry(tag.getUUID("uuid"),tag.getString("name"),tag.getString("dimension"),tag.getString("mode"));
            if(data.owners.putIfAbsent(key(entry.name()),entry.id())!=null || data.entries.putIfAbsent(entry.id(),entry)!=null)
                throw new IllegalStateException("Duplicate NPC name registry entry");
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        root.putInt("schema",1);root.putInt("nextCandidate",nextCandidate);var list=new ListTag();
        for(var entry:entries()){
            var tag=new CompoundTag();tag.putUUID("uuid",entry.id());tag.putString("name",entry.name());
            tag.putString("dimension",entry.dimension());tag.putString("mode",entry.mode());list.add(tag);
        }
        root.put("entries",list);return root;
    }
}
