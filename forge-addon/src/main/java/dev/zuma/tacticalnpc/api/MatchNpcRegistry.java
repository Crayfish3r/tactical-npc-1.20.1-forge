package dev.zuma.tacticalnpc.api;

import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import dev.zuma.tacticalnpc.command.NpcNames;
import dev.zuma.tacticalnpc.command.StrictSavedData;
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
    private UsedMatchIds usedIds=new UsedMatchIds();
    public record Counts(int active,int pendingCleanup,int loaded,int matches,int members,int policies) {}
    /** On-demand only; cost is proportional to currently retained groups/loaded handles, not history. */
    public Counts counts(){
        requireServer();pruneLoaded();
        int active=0,closed=0,members=0;for(var entry:matches.entrySet()){var match=entry.getValue();if(match.closed)closed++;else if(isActive(entry.getKey()))active++;for(var group:match.groups.values())members+=group.members.size();}
        return new Counts(active,closed,loaded.size(),matches.size(),members,policies.size());
    }

    public static MatchNpcRegistry of(MinecraftServer server){
        if(!server.isSameThread())throw new IllegalStateException("Match NPC API requires server thread");
        var data=server.overworld().getDataStorage().computeIfAbsent(MatchNpcRegistry::load,()->StrictSavedData.create(server,"tacticalnpc_matches",MatchNpcRegistry::new),"tacticalnpc_matches");
        data.server=server;return data;
    }
    private void requireServer(){if(server==null || !server.isSameThread())throw new IllegalStateException("Match NPC API requires server thread");}
    @Override public void registerPolicy(UUID id,MatchNpcPolicy policy){
        requireServer();Objects.requireNonNull(id);Objects.requireNonNull(policy);
        var match=matches.get(id);
        if(match!=null && (!policies.containsKey(id) || match.closed) || match==null && usedIds.contains(id))
            throw new IllegalArgumentException("Match ID is closed/previous-session or rejected by the persistent used-ID filter; use a fresh UUID");
        if(match==null){usedIds.add(id);matches.put(id,new Match());setDirty();}
        policies.put(id,policy);
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
        state.liveTeam.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.NEVER);
        entities.forEach(n->state.members.add(n.getUUID()));matches.get(id).groups.put(group,state);setDirty();return name;
    }
    void rollback(UUID id,UUID group){
        var match=matches.get(id);var state=match==null?null:match.groups.remove(group);
        if(state!=null){forgetMembers(id,state);cleanupTeam(state);setDirty();}
    }
    /** Called after native admission, before name assignment and the first AI tick. */
    public boolean admit(TacticalNpcEntity npc){
        requireServer();
        var names=NpcNames.of(server);
        if(npc.invalidMatchBinding() || npc.ownerMatchId()==null && names.temporary(npc.getUUID())){names.forgetTemporary(npc.getUUID());server.getScoreboard().removePlayerFromTeam(npc.getScoreboardName());return false;}
        var id=npc.ownerMatchId();if(id==null)return true;
        var match=matches.get(id);var group=match==null?null:match.groups.get(npc.groupId());
        if(group==null || !group.members.contains(npc.getUUID()) || !isActive(id)){
            // Remove only this owned entity's membership; never assign or alter a player.
            NpcNames.of(server).forgetMatchMember(npc.getUUID(),id);
            server.getScoreboard().removePlayerFromTeam(npc.getScoreboardName());return false;
        }
        loaded.put(npc.getUUID(),new WeakReference<>(npc));return true;
    }
    public void leave(TacticalNpcEntity npc){
        requireServer();var ref=loaded.get(npc.getUUID());if(ref!=null && ref.get()==npc)loaded.remove(npc.getUUID());
    }
    public void leave(TacticalNpcEntity npc,net.minecraft.world.entity.Entity.RemovalReason reason){
        requireServer();var ref=loaded.get(npc.getUUID());if(ref==null || ref.get()!=npc)return;
        leave(npc);
        if(reason.shouldDestroy()){
            var match=matches.get(npc.ownerMatchId());var group=match==null?null:match.groups.get(npc.groupId());
            if(group!=null && group.members.remove(npc.getUUID())){
                var team=server.getScoreboard().getPlayerTeam(group.team);
                if(team!=null && server.getScoreboard().getPlayersTeam(npc.getScoreboardName())==team)server.getScoreboard().removePlayerFromTeam(npc.getScoreboardName());
                if(group.members.isEmpty())cleanupTeam(group);
                setDirty();
            }
        }
    }
    private List<TacticalNpcEntity> entities(UUID id){
        var result=new ArrayList<TacticalNpcEntity>();
        pruneLoaded();
        for(var ref:loaded.values()){var npc=ref.get();if(npc!=null && id.equals(npc.ownerMatchId()))result.add(npc);}
        return result;
    }
    private void pruneLoaded(){loaded.values().removeIf(ref->{var npc=ref.get();return npc==null || npc.isRemoved();});}
    @Override public List<UUID> activeNpcIds(UUID id){requireServer();Objects.requireNonNull(id);return entities(id).stream().filter(TacticalNpcEntity::isAlive).map(TacticalNpcEntity::getUUID).sorted().toList();}
    @Override public void deactivate(UUID id){
        requireServer();Objects.requireNonNull(id);policies.remove(id);
        if(usedIds.add(id))setDirty();var match=matches.get(id);
        if(match!=null && !match.closed){match.closed=true;setDirty();}
        for(var npc:entities(id)){npc.removeTag(dev.zuma.tacticalnpc.runtime.RuntimeRegistry.TAG);npc.release();npc.getNavigation().stop();npc.setTarget(null);}
    }
    @Override public int remove(UUID id){
        deactivate(id);var entities=entities(id);entities.forEach(npc->{server.getScoreboard().removePlayerFromTeam(npc.getScoreboardName());npc.discard();});
        var state=matches.remove(id);
        if(state!=null){state.groups.values().forEach(group->{forgetMembers(id,group);cleanupTeam(group);});setDirty();}
        return entities.size();
    }
    private void forgetMembers(UUID match,Group group){for(var member:group.members)NpcNames.of(server).forgetMatchMember(member,match);}
    private void cleanupTeam(Group group){
        var scoreboard=server.getScoreboard();var team=scoreboard.getPlayerTeam(group.team);
        // If an administrator removed/recreated the team during this session, it is no longer ours.
        if(team==null || group.liveTeam!=null && group.liveTeam!=team || !team.getDisplayName().getString().equals(group.marker))return;
        for(var id:group.members)if(scoreboard.getPlayersTeam(id.toString())==team)scoreboard.removePlayerFromTeam(id.toString());
        // Preserve any player or foreign entity an administrator explicitly joined to this team.
        if(team.getPlayers().isEmpty())scoreboard.removePlayerTeam(team);
    }
    /** No policy survives restart, even if TT registers a different new match immediately. */
    public void startup(){
        requireServer();for(var id:new ArrayList<>(matches.keySet()))if(!policies.containsKey(id))remove(id);
        // Explicit schema2 ownership also covers a save interrupted between the two registry writes.
        for(var entry:NpcNames.of(server).entries())if(entry.ownerMatchId()!=null && !matches.containsKey(entry.ownerMatchId()))NpcNames.of(server).forgetMatchMember(entry.id(),entry.ownerMatchId());
    }
    public static MatchNpcRegistry load(CompoundTag root){
        var data=new MatchNpcRegistry();
        int schema=root.getInt("schema");String error="tacticalnpc_matches schema "+schema+": ";
        if(schema!=1 && schema!=2)throw new IllegalStateException(error+"unsupported version");
        if(!(root.get("matches") instanceof ListTag list) || !list.isEmpty() && list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalStateException(error+"invalid matches list");
        if(schema==2)data.usedIds=UsedMatchIds.load(root);
        var allMembers=new HashSet<UUID>();
        for(var item:root.getList("matches",Tag.TAG_COMPOUND)){
            var tag=(CompoundTag)item;if(!tag.hasUUID("id") || !(tag.get("groups") instanceof ListTag groups) || !groups.isEmpty() && groups.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalStateException(error+"invalid match/groups");var match=new Match();match.closed=true;
            for(var child:tag.getList("groups",Tag.TAG_COMPOUND)){
                var g=(CompoundTag)child;String team=g.getString("team");
                if(!g.hasUUID("id") || !team.matches("tnpc_[0-9a-f]{32}") || !g.getString("marker").startsWith("TacticalNPC group ") || !(g.get("members") instanceof ListTag members) || !members.isEmpty() && members.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalStateException(error+"invalid group");
                var group=new Group(team,g.getString("marker"));
                for(var member:g.getList("members",Tag.TAG_COMPOUND)){var m=(CompoundTag)member;if(!m.hasUUID("id") || !allMembers.add(m.getUUID("id")))throw new IllegalStateException(error+"invalid/duplicate member");group.members.add(m.getUUID("id"));}
                if(match.groups.put(g.getUUID("id"),group)!=null)throw new IllegalStateException(error+"duplicate group");
            }
            if(data.matches.put(tag.getUUID("id"),match)!=null)throw new IllegalStateException(error+"duplicate match");
            if(schema==2 && !data.usedIds.contains(tag.getUUID("id")))throw new IllegalStateException(error+"match missing from used-ID filter");
            data.usedIds.add(tag.getUUID("id"));
        }
        if(schema==1)data.setDirty();
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        root.putInt("schema",2);usedIds.save(root);var list=new ListTag();
        matches.forEach((id,match)->{var tag=new CompoundTag();tag.putUUID("id",id);var groups=new ListTag();
            match.groups.forEach((gid,group)->{var g=new CompoundTag();g.putUUID("id",gid);g.putString("team",group.team);g.putString("marker",group.marker);var members=new ListTag();
                group.members.forEach(uuid->{var m=new CompoundTag();m.putUUID("id",uuid);members.add(m);});g.put("members",members);groups.add(g);});tag.put("groups",groups);list.add(tag);});
        root.put("matches",list);return root;
    }
}
