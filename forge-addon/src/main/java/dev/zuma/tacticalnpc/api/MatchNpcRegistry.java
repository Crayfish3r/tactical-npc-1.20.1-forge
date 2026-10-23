package dev.zuma.tacticalnpc.api;

import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.PlayerTeam;

/** Persistent ownership/tombstones; transient policies and weak loaded handles. No world scans. */
public final class MatchNpcRegistry extends SavedData implements TacticalNpcMatchApi {
    private static final class Group {
        final String team,marker;
        final Set<UUID> members=new HashSet<>();
        PlayerTeam liveTeam;
        Group(String team,String marker){this.team=team;this.marker=marker;}
    }
    private static final class Match {
        boolean closed;
        final Map<UUID,Group> groups=new HashMap<>();
    }
    private final Map<UUID,Match> matches=new HashMap<>();
    private final Map<UUID,MatchNpcPolicy> policies=new HashMap<>();
    private final Map<UUID,WeakReference<TacticalNpcEntity>> loaded=new HashMap<>();
    private MinecraftServer server;

    public static MatchNpcRegistry of(MinecraftServer server){
        if(!server.isSameThread())throw new IllegalStateException("Match NPC API requires server thread");
        var data=server.overworld().getDataStorage().computeIfAbsent(MatchNpcRegistry::load,MatchNpcRegistry::new,"tacticalnpc_matches");
        data.server=server;return data;
    }
    private void requireServer(){if(server==null || !server.isSameThread())throw new IllegalStateException("Match NPC API requires server thread");}
    @Override public void registerPolicy(UUID id,MatchNpcPolicy policy){
        requireServer();Objects.requireNonNull(id);Objects.requireNonNull(policy);
        if(matches.containsKey(id) && (!policies.containsKey(id) || matches.get(id).closed))
            throw new IllegalArgumentException("Match ID is closed or belongs to a previous server session");
        matches.computeIfAbsent(id,key->new Match());policies.put(id,policy);setDirty();
    }
    public boolean isActive(UUID id){
        requireServer();var state=matches.get(id);var policy=policies.get(id);
        if(state==null || state.closed || policy==null)return false;
        try{return policy.isActive();}catch(RuntimeException | LinkageError ex){return false;}
    }
    /** Match-owned NPCs attack only admitted players or NPCs of the same active match. */
    public static boolean permits(TacticalNpcEntity npc,LivingEntity target){
        if(npc.ownerMatchId()==null)return !npc.invalidMatchBinding();
        if(!(npc.level() instanceof ServerLevel level) || !level.getServer().isSameThread())return false;
        var registry=of(level.getServer());var id=npc.ownerMatchId();
        if(!registry.isActive(id))return false;
        if(target instanceof ServerPlayer player){
            if(player.connection==null || player.hasDisconnected() || level.getEntity(player.getUUID())!=player)return false;
            try{return registry.policies.get(id).canAttack(player);}catch(RuntimeException | LinkageError ex){return false;}
        }
        return target instanceof TacticalNpcEntity other && id.equals(other.ownerMatchId());
    }
    @Override public SpawnResult spawn(SpawnGroupRequest request){
        requireServer();Objects.requireNonNull(request);
        if(request.level().getServer()!=server)throw new IllegalArgumentException("Level belongs to another server");
        return NpcSpawnService.spawnMatch(request,this);
    }
    boolean hasGroup(UUID id,UUID group){return matches.get(id).groups.containsKey(group);}
    String reserveGroup(UUID id,UUID group,List<TacticalNpcEntity> entities){
        requireServer();var scoreboard=server.getScoreboard();String name=null;
        for(int attempt=0;attempt<8;attempt++){String candidate="tnpc_"+UUID.randomUUID().toString().replace("-","");if(scoreboard.getPlayerTeam(candidate)==null){name=candidate;break;}}
        if(name==null)throw new IllegalStateException("Could not allocate a unique match team");
        var state=new Group(name,"TacticalNPC group "+UUID.randomUUID());state.liveTeam=scoreboard.addPlayerTeam(name);
        state.liveTeam.setDisplayName(net.minecraft.network.chat.Component.literal(state.marker));
        entities.forEach(n->state.members.add(n.getUUID()));matches.get(id).groups.put(group,state);setDirty();return name;
    }
    void rollback(UUID id,UUID group){
        var state=matches.get(id).groups.remove(group);if(state!=null)cleanupTeam(state);setDirty();
    }
    /** Called after native admission, before name assignment and the first AI tick. */
    public boolean admit(TacticalNpcEntity npc){
        requireServer();
        if(npc.invalidMatchBinding()){server.getScoreboard().removePlayerFromTeam(npc.getScoreboardName());return false;}
        var id=npc.ownerMatchId();if(id==null)return true;
        var match=matches.get(id);var group=match==null?null:match.groups.get(npc.groupId());
        if(group==null || !group.members.contains(npc.getUUID()) || !isActive(id)){
            // Remove only this owned entity's membership; never assign or alter a player.
            server.getScoreboard().removePlayerFromTeam(npc.getScoreboardName());return false;
        }
        loaded.put(npc.getUUID(),new WeakReference<>(npc));return true;
    }
    public void leave(TacticalNpcEntity npc){
        requireServer();var ref=loaded.get(npc.getUUID());if(ref!=null && ref.get()==npc)loaded.remove(npc.getUUID());
    }
    private List<TacticalNpcEntity> entities(UUID id){
        var result=new ArrayList<TacticalNpcEntity>();
        loaded.values().removeIf(ref->ref.get()==null || ref.get().isRemoved());
        for(var ref:loaded.values()){var npc=ref.get();if(npc!=null && id.equals(npc.ownerMatchId()))result.add(npc);}
        return result;
    }
    @Override public List<UUID> activeNpcIds(UUID id){requireServer();Objects.requireNonNull(id);return entities(id).stream().filter(TacticalNpcEntity::isAlive).map(TacticalNpcEntity::getUUID).sorted().toList();}
    @Override public void deactivate(UUID id){
        requireServer();Objects.requireNonNull(id);policies.remove(id);
        matches.computeIfAbsent(id,key->new Match()).closed=true;setDirty();
        for(var npc:entities(id)){npc.removeTag(dev.zuma.tacticalnpc.runtime.RuntimeRegistry.TAG);npc.release();npc.getNavigation().stop();npc.setTarget(null);}
    }
    @Override public int remove(UUID id){
        deactivate(id);var entities=entities(id);entities.forEach(npc->{server.getScoreboard().removePlayerFromTeam(npc.getScoreboardName());npc.discard();});
        var state=matches.get(id);state.groups.values().forEach(this::cleanupTeam);state.groups.clear();setDirty();return entities.size();
    }
    private void cleanupTeam(Group group){
        var scoreboard=server.getScoreboard();var team=scoreboard.getPlayerTeam(group.team);
        // If an administrator removed/recreated the team during this session, it is no longer ours.
        if(team==null || group.liveTeam!=null && group.liveTeam!=team || !team.getDisplayName().getString().equals(group.marker))return;
        for(var id:group.members)if(scoreboard.getPlayersTeam(id.toString())==team)scoreboard.removePlayerFromTeam(id.toString());
        // Preserve any player or foreign entity an administrator explicitly joined to this team.
        if(team.getPlayers().isEmpty())scoreboard.removePlayerTeam(team);
    }
    /** No policy survives restart, even if TT registers a different new match immediately. */
    public void startup(){requireServer();for(var id:new ArrayList<>(matches.keySet()))if(!policies.containsKey(id))remove(id);}
    public static MatchNpcRegistry load(CompoundTag root){
        var data=new MatchNpcRegistry();
        if(root.getInt("schema")!=1)throw new IllegalStateException("Unsupported match registry schema");
        for(var item:root.getList("matches",Tag.TAG_COMPOUND)){
            var tag=(CompoundTag)item;if(!tag.hasUUID("id"))continue;var match=new Match();match.closed=true;
            for(var child:tag.getList("groups",Tag.TAG_COMPOUND)){
                var g=(CompoundTag)child;String team=g.getString("team");
                if(!g.hasUUID("id") || !team.matches("tnpc_[0-9a-f]{32}") || !g.getString("marker").startsWith("TacticalNPC group "))continue;
                var group=new Group(team,g.getString("marker"));
                for(var member:g.getList("members",Tag.TAG_COMPOUND)){var m=(CompoundTag)member;if(m.hasUUID("id"))group.members.add(m.getUUID("id"));}
                match.groups.put(g.getUUID("id"),group);
            }
            data.matches.put(tag.getUUID("id"),match);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        root.putInt("schema",1);var list=new ListTag();
        matches.forEach((id,match)->{var tag=new CompoundTag();tag.putUUID("id",id);var groups=new ListTag();
            match.groups.forEach((gid,group)->{var g=new CompoundTag();g.putUUID("id",gid);g.putString("team",group.team);g.putString("marker",group.marker);var members=new ListTag();
                group.members.forEach(uuid->{var m=new CompoundTag();m.putUUID("id",uuid);members.add(m);});g.put("members",members);groups.add(g);});tag.put("groups",groups);list.add(tag);});
        root.put("matches",list);return root;
    }
}
