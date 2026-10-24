package dev.zuma.tacticalnpc.command;

import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent administrator identities and explicitly match-owned temporary reservations. */
public final class NpcNames extends SavedData {
    private static final List<String> POOL=List.of("Steve","Kevin","John","Tyler","Alex","James","Jack","Daniel","David","Tom",
        "Mike","Sam","Ben","Luke","Ryan","Adam","Mark","Paul","Peter","Nick","Max","Jake","Josh","Chris","Matt",
        "Joe","Dan","Tim","Bob","Bill","Will","Harry","Henry","Oliver","Oscar","Leo","Liam","Noah","Ethan","Owen",
        "Dylan","Aaron","Eric","Ian","Sean","Scott","Dean","Cole","Evan","Nathan");
    public record Entry(UUID id,String name,String dimension,String mode,UUID ownerMatchId) {
        /** Preserve the previous public constructor for administrator/legacy callers. */
        public Entry(UUID id,String name,String dimension,String mode){this(id,name,dimension,mode,null);}
    }
    private final Map<UUID,Entry> entries=new HashMap<>();
    private final Map<String,UUID> owners=new HashMap<>();
    private record Loaded(ResourceKey<Level> dimension,int entityId,java.lang.ref.WeakReference<TacticalNpcEntity> entity) {}
    private final Map<UUID,Loaded> loaded=new HashMap<>();
    private int nextCandidate;
    private int temporaryCount;
    private List<Entry> sortedEntries;
    public record Counts(int permanent,int temporary,int loaded) {}
    public Counts counts(){pruneLoaded();return new Counts(entries.size()-temporaryCount,temporaryCount,loaded.size());}
    // Native chunk removal can finish after Forge's tracking-end callback. Trim those
    // weak handles on demand; nonremoved tracking loss must remain addressable.
    private void pruneLoaded(){loaded.values().removeIf(ref->{var npc=ref.entity().get();return npc==null || npc.isRemoved();});}
    public boolean temporary(UUID id){var entry=entries.get(id);return entry!=null && entry.ownerMatchId()!=null;}
    public void forgetTemporary(UUID id){var entry=entries.get(id);if(entry!=null && entry.ownerMatchId()!=null)forgetMatchMember(id,entry.ownerMatchId());}
    public static NpcNames of(MinecraftServer server){
        if(!server.isSameThread())throw new IllegalStateException("NPC names require server thread");
        return server.overworld().getDataStorage().computeIfAbsent(NpcNames::load,()->StrictSavedData.create(server,"tacticalnpc_names",NpcNames::new),"tacticalnpc_names");
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
        var entry=new Entry(npc.getUUID(),npc.npcName(),npc.level().dimension().location().toString(),npc.order(),npc.ownerMatchId());
        if(!entry.equals(canonical)){
            if(canonical!=null && canonical.ownerMatchId()!=null)temporaryCount--;
            if(entry.ownerMatchId()!=null)temporaryCount++;
            entries.put(entry.id(),entry);sortedEntries=null;setDirty();
        }
    }
    public void leave(TacticalNpcEntity npc){
        if(current(npc)){update(npc);loaded.remove(npc.getUUID());}
    }
    public void leave(TacticalNpcEntity npc,net.minecraft.world.entity.Entity.RemovalReason reason){
        // Ignore stale callbacks from a source instance after a dimension transfer.
        if(!current(npc))return;
        leave(npc);
        if(reason.shouldDestroy() && npc.ownerMatchId()!=null)forgetMatchMember(npc.getUUID(),npc.ownerMatchId());
    }
    /** The caller has authoritative entity/group UUID evidence, including for schema1 records. */
    public void forgetMatchMember(UUID id,UUID match){
        Objects.requireNonNull(match);var entry=entries.get(id);
        if(entry==null || entry.ownerMatchId()!=null && !entry.ownerMatchId().equals(match))return;
        entries.remove(id);owners.remove(key(entry.name()),id);loaded.remove(id);
        if(entry.ownerMatchId()!=null)temporaryCount--;
        // Reconsider freed auto-names, without retaining a second unbounded free-name collection.
        nextCandidate=0;sortedEntries=null;setDirty();
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
        pruneLoaded();
        var result=new ArrayList<TacticalNpcEntity>();
        for(var id:loaded.keySet()){var npc=resolve(server,id);if(npc!=null)result.add(npc);}
        return result;
    }
    public List<Entry> entries(){if(sortedEntries==null)sortedEntries=entries.values().stream().sorted(Comparator.comparing(Entry::name,String.CASE_INSENSITIVE_ORDER)).toList();return sortedEntries;}
    public static NpcNames load(CompoundTag root){
        int schema=root.getInt("schema");String error="tacticalnpc_names schema "+schema+": ";
        if(schema!=1 && schema!=2)throw new IllegalStateException(error+"unsupported version");
        if(!(root.get("entries") instanceof ListTag list) || !list.isEmpty() && list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalStateException(error+"invalid entries list");
        var data=new NpcNames(); // Rebuild allocation cursor; an old high-water mark need not survive.
        for(var item:root.getList("entries",Tag.TAG_COMPOUND)){
            var tag=(CompoundTag)item;
            if(!tag.hasUUID("uuid") || !valid(tag.getString("name")) || tag.contains("ownerMatchId") && !tag.hasUUID("ownerMatchId"))throw new IllegalStateException(error+"invalid entry");
            var entry=new Entry(tag.getUUID("uuid"),tag.getString("name"),tag.getString("dimension"),tag.getString("mode"),schema>=2 && tag.hasUUID("ownerMatchId")?tag.getUUID("ownerMatchId"):null);
            if(data.owners.putIfAbsent(key(entry.name()),entry.id())!=null || data.entries.putIfAbsent(entry.id(),entry)!=null)
                throw new IllegalStateException(error+"duplicate entry");
            if(entry.ownerMatchId()!=null)data.temporaryCount++;
        }
        if(schema==1)data.setDirty();
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        root.putInt("schema",2);var list=new ListTag();
        for(var entry:entries.values()){
            var tag=new CompoundTag();tag.putUUID("uuid",entry.id());tag.putString("name",entry.name());
            tag.putString("dimension",entry.dimension());tag.putString("mode",entry.mode());if(entry.ownerMatchId()!=null)tag.putUUID("ownerMatchId",entry.ownerMatchId());list.add(tag);
        }
        root.put("entries",list);return root;
    }
}
