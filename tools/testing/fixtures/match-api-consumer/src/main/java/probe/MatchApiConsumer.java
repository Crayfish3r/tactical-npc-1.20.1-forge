package probe;

import dev.zuma.tacticalnpc.api.*;
import dev.zuma.tacticalnpc.core.OrderState;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import dev.zuma.tacticalnpc.command.NpcNames;
import dev.zuma.tacticalnpc.runtime.NpcTeams;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** External test mod, compiled against the published distribution JAR, never shipped in TacticalNPC. */
@Mod("matchapiprobe")
public final class MatchApiConsumer {
    private int ticks;
    private boolean done;
    public MatchApiConsumer(){MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener(this::stopped);}
    /** Test-only disk fixture: convert this new isolated world's real saved files to schema1. */
    private void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){
        if(!"save".equals(System.getProperty("tactical.match.probe.phase")))return;
        try{
            for(String id:List.of("tacticalnpc_names","tacticalnpc_matches")){
                var file=event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve(id+".dat").toFile();var root=NbtIo.readCompressed(file);var data=root.getCompound("data");data.putInt("schema",1);
                if(id.equals("tacticalnpc_names")){for(var item:data.getList("entries",Tag.TAG_COMPOUND))((CompoundTag)item).remove("ownerMatchId");data.putInt("nextCandidate",50000);}
                else{data.remove("usedIds");data.remove("usedIdsSha256");}
                NbtIo.writeCompressed(root,file);
            }
            System.out.println("MATCH_API_PROBE_LEGACY_DISK names=1 matches=1 entity=7");
        }catch(Exception ex){throw new IllegalStateException("Legacy disk fixture failed",ex);}
    }
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static final MatchNpcPolicy POLICY=new MatchNpcPolicy(){public boolean isActive(){return true;}public boolean canAttack(net.minecraft.server.level.ServerPlayer p){return false;}};
    private void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END || done)return;
        var server=event.getServer();var level=server.overworld();ticks++;
        try{
            boolean initial="save".equals(System.getProperty("tactical.match.probe.phase"));
            var api=TacticalNpcMatchApi.get(server);var data=ProbeData.of(server);
            if(ticks==20){
                for(int x=0;x<=2;x++)for(int z=0;z<=2;z++)level.setChunkForced(x,z,true);
                if(initial)level.setChunkForced(32,32,true);
            }
            if(initial){
                if(ticks==100){
                    floor(level,0,48);floor(level,512,528);
                    var match=UUID.randomUUID();api.registerPolicy(match,POLICY);
                    var group=api.spawn(request(level,match,new Vec3(24.5,-60,24.5),"ak47","glock_17","ai_awp","b93r"));
                    var remote=api.spawn(request(level,match,new Vec3(520.5,-60,520.5),"glock_17"));
                    check(group.success() && remote.success(),"Spawn failed: "+group+" "+remote);
                    var all=new ArrayList<>(group.entityIds());all.addAll(remote.entityIds());
                    for(var id:all){var n=(TacticalNpcEntity)level.getEntity(id);n.removeTag(RuntimeRegistry.TAG);n.setNoAi(true);check(!n.isCustomNameVisible() && n.getTeam().getNameTagVisibility()==net.minecraft.world.scores.Team.Visibility.NEVER,"Hidden match names/shared team");}
                    var manual=NpcSpawnService.spawnManual(level,new Vec3(40.5,-60,40.5),0,0,"probe-manual",1);
                    check(manual.success(),"Manual fixture spawn failed");var n=(TacticalNpcEntity)level.getEntity(manual.entityIds().get(0));
                    n.removeTag(RuntimeRegistry.TAG);n.setNoAi(true);n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,37);n.setOrder(OrderState.Kind.PATROL,16);
                    var root=data.root;root.putUUID("match",match);root.putUUID("manual",n.getUUID());root.putUUID("service",n.serviceId());root.putUUID("remote",remote.entityIds().get(0));root.put("weapon",n.getMainHandItem().save(new CompoundTag()));
                    root.putString("localTeam",level.getEntity(group.entityIds().get(0)).getTeam().getName());root.putString("remoteTeam",level.getEntity(remote.entityIds().get(0)).getTeam().getName());data.setDirty();
                    root.putString("manualName",n.npcName());check(api.activeNpcCount(match)==5,"All five initial API actors admitted");level.setChunkForced(32,32,false);
                    check(new NpcNames.Entry(n.getUUID(),n.npcName(),level.dimension().location().toString(),n.order()).name().equals(n.npcName()),"Legacy four-argument public Entry constructor remains callable");
                }
                if(ticks>120 && data.root.hasUUID("match") && api.activeNpcCount(data.root.getUUID("match"))==4 && level.getChunkSource().getChunkNow(32,32)==null){
                    check(level.getChunkSource().getChunkNow(32,32)==null,"Remote entity must really unload with its chunk");
                    check(NpcNames.of(server).counts().temporary()==5 && NpcNames.of(server).counts().permanent()==1 && NpcNames.of(server).counts().loaded()==5,"Unloaded temporary name preserved without a stale loaded handle (4 local + 1 manual)");
                    System.out.println("MATCH_API_PROBE_SAVED loaded=4 remoteUnloaded=true manual=1 names="+NpcNames.of(server).counts());done=true;server.halt(false);
                }
            }else{
                if(ticks==100){
                    check(data.root.hasUUID("match"),"Missing persisted external fixture data");var root=data.root;var match=root.getUUID("match");
                    check(api.activeNpcCount(match)==0,"No old loaded NPC may become active after restart");
                    check(server.getScoreboard().getPlayerTeam(root.getString("localTeam"))==null && server.getScoreboard().getPlayerTeam(root.getString("remoteTeam"))==null,"Old API teams must be cleaned at startup");
                    boolean denied=false;try{api.registerPolicy(match,POLICY);}catch(IllegalArgumentException ex){denied=true;}check(denied,"Old match ID must stay closed");
                    var n=(TacticalNpcEntity)level.getEntity(root.getUUID("manual"));
                    check(n!=null && n.ownerMatchId()==null && n.npcName().equals(root.getString("manualName")) && !n.isCustomNameVisible() && n.serviceId().equals(root.getUUID("service")) && n.order().equals("PATROL") && n.ammoPolicy()==TacticalNpcEntity.AmmoPolicy.FINITE && "probe-manual".equals(NpcTeams.name(n)) && root.getCompound("weapon").equals(n.getMainHandItem().save(new CompoundTag())),"Manual identity/name/order/ammo/native stack/team must survive disk restart");
                    level.setChunkForced(32,32,true);
                }
                if(ticks==180){
                    var root=data.root;check(level.getChunkSource().getChunkNow(32,32)!=null && level.getEntity(root.getUUID("remote"))==null,"Previously unloaded match actor must be removed on real disk admission");
                    check(api.activeNpcCount(root.getUUID("match"))==0 && api.remove(root.getUUID("match"))==0 && api.remove(root.getUUID("match"))==0,"Idempotent cleanup after restart");
                    var counts=MatchNpcRegistry.of(server).counts();check(counts.matches()==0 && counts.members()==0 && counts.policies()==0 && counts.loaded()==0 && NpcNames.of(server).counts().temporary()==0 && NpcNames.of(server).counts().permanent()==1,"Restart releases proven temporary names and all match records");
                    check(NpcNames.of(server).save(new CompoundTag()).getInt("schema")==2 && MatchNpcRegistry.of(server).save(new CompoundTag()).getInt("schema")==2,"Schema1 disk migration writes schema2");
                    System.out.println("MATCH_API_PROBE_RESTART_PASS oldLoaded=0 oldRemote=0 teams=0 manualPreserved=true names="+NpcNames.of(server).counts()+" matches="+counts);done=true;server.halt(false);
                }
            }
            if(ticks>1400)throw new IllegalStateException("Ordinary lifecycle probe timed out");
        }catch(Throwable failure){failure.printStackTrace();System.out.println("MATCH_API_PROBE_FAIL "+failure);done=true;server.halt(false);}
    }
    private static SpawnGroupRequest request(ServerLevel level,UUID match,Vec3 position,String... guns){
        return new SpawnGroupRequest(level,match,UUID.randomUUID(),position,0,0,Arrays.stream(guns).map(g->new SpawnMemberSpec(new ResourceLocation("tacz",g),TacticalNpcEntity.AmmoPolicy.FINITE,37,OrderState.Kind.DEFEND_AREA,16)).toList());
    }
    private static void floor(ServerLevel level,int min,int max){
        for(int x=min;x<max;x++)for(int z=min;z<max;z++){
            level.setBlock(new BlockPos(x,-61,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=-60;y<=-56;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
    }
    public static final class ProbeData extends SavedData {
        CompoundTag root=new CompoundTag();
        static ProbeData of(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(ProbeData::load,ProbeData::new,"match_api_external_probe");}
        static ProbeData load(CompoundTag tag){var data=new ProbeData();data.root=tag.copy();return data;}
        @Override public CompoundTag save(CompoundTag tag){return root.copy();}
    }
}
