package dev.zuma.tacticalnpc.api;

import dev.zuma.tacticalnpc.combat.CoverSensor;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.OrderState;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import dev.zuma.tacticalnpc.runtime.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;

/** Shared bounded loaded-space placement. Match path is atomic; administrator path stays partial. */
public final class NpcSpawnService {
    private static void requireServer(ServerLevel level){
        if(!level.getServer().isSameThread())throw new IllegalStateException("NPC spawn requires server thread");
        if(level.getServer().getLevel(level.dimension())!=level)throw new IllegalArgumentException("Level is not registered");
    }
    static SpawnResult spawnMatch(SpawnGroupRequest request,MatchNpcRegistry registry){
        var level=request.level();requireServer(level);
        if(!registry.isActive(request.matchId()))return SpawnResult.failure(SpawnResult.Status.INACTIVE_MATCH,"Match policy absent, inactive or failed");
        if(registry.hasGroup(request.matchId(),request.groupId()))return SpawnResult.failure(SpawnResult.Status.DUPLICATE_GROUP,"Group ID already used in this match");
        var prepared=new ArrayList<TacticalNpcEntity>();
        for(int i=0;i<request.members().size();i++){
            var result=prepare(level,request.origin(),request.yaw(),request.pitch(),request.members().get(i),i,request.members().size(),prepared);
            if(result.npc()==null){prepared.forEach(TacticalNpcEntity::discard);return SpawnResult.failure(result.status(),result.reason());}
            result.npc().bindMatch(request.matchId(),request.groupId());prepared.add(result.npc());
        }
        String team=null;boolean committed=false;
        try{
            if(!registry.isActive(request.matchId()))return SpawnResult.failure(SpawnResult.Status.INACTIVE_MATCH,"Policy changed during preflight");
            team=registry.reserveGroup(request.matchId(),request.groupId(),prepared);
            for(var npc:prepared){
                // Revalidate after previous EntityJoin callbacks; another mod may have changed geometry.
                if(!npc.position().equals(new CoverSensor(npc.runtime()).safeStanding(npc.position())) || !space(level,npc,npc.position(),prepared) || !registry.isActive(request.matchId()) || !level.addFreshEntity(npc) || npc.isRemoved())
                    throw new IllegalStateException("Native entity admission rejected");
                NpcTeams.assign(npc,team);
            }
            if(!registry.isActive(request.matchId()))throw new IllegalStateException("Match deactivated during admission");
            for(var npc:prepared)if(npc.isRemoved() || level.getEntity(npc.getUUID())!=npc || !team.equals(NpcTeams.name(npc)))throw new IllegalStateException("Group changed during admission");
            committed=true;return new SpawnResult(SpawnResult.Status.SUCCESS,prepared.stream().map(TacticalNpcEntity::getUUID).toList(),"Group created");
        }catch(RuntimeException | LinkageError ex){
            return SpawnResult.failure(SpawnResult.Status.ENTITY_REJECTED,ex.getClass().getSimpleName()+": "+ex.getMessage());
        }finally{
            if(!committed){prepared.forEach(TacticalNpcEntity::discard);if(team!=null)registry.rollback(request.matchId(),request.groupId());}
        }
    }

    /** For existing commands: keep successfully admitted members if a later placement fails. */
    public static SpawnResult spawnManual(ServerLevel level,Vec3 origin,float yaw,float pitch,String team,int count){
        requireServer(level);NpcTeams.validate(team);
        if(count<1 || count>16)throw new IllegalArgumentException("Count must be 1..16");
        var made=new ArrayList<TacticalNpcEntity>();SpawnResult.Status status=SpawnResult.Status.SUCCESS;String reason="Created";
        var spec=new SpawnMemberSpec(new net.minecraft.resources.ResourceLocation("tacz:ak47"),TacticalNpcEntity.AmmoPolicy.INFINITE,0,OrderState.Kind.ASSAULT,24);
        for(int i=0;i<count;i++){
            var result=prepare(level,origin,yaw,pitch,spec,i,count,made);
            if(result.npc()==null){status=result.status();reason=result.reason();break;}
            var npc=result.npc();
            if(!level.addFreshEntity(npc)){npc.discard();status=SpawnResult.Status.ENTITY_REJECTED;reason="Native entity admission rejected";break;}
            NpcTeams.assign(npc,team);made.add(npc);
        }
        return new SpawnResult(status,made.stream().map(TacticalNpcEntity::getUUID).toList(),reason);
    }
    private record Prepared(TacticalNpcEntity npc,SpawnResult.Status status,String reason){}
    private static Prepared prepare(ServerLevel level,Vec3 origin,float yaw,float pitch,SpawnMemberSpec spec,int number,int count,List<TacticalNpcEntity> reserved){
        net.minecraft.world.item.ItemStack weapon;
        try{weapon=GunSupport.create(spec.weapon());}catch(RuntimeException | LinkageError ex){return new Prepared(null,SpawnResult.Status.INVALID_WEAPON,"Unsupported weapon "+spec.weapon()+": "+ex.getMessage());}
        TacticalNpcEntity npc;
        try{npc=(TacticalNpcEntity)RuntimeRegistry.create(level).entity();}catch(RuntimeException | LinkageError ex){return new Prepared(null,SpawnResult.Status.ENTITY_REJECTED,"Entity factory failed: "+ex.getMessage());}
        var standing=new CoverSensor(npc.runtime());int spacing=count==1?2:4;Vec3 pos=null;
        for(int candidate=0;candidate<49;candidate++){
            int index=(candidate+number*7)%49,dx=(index%7-3)*spacing,dz=(index/7-3)*spacing;
            if(dx==0 && dz==0)continue;
            var guess=origin.add(dx,0,dz);
            if(!loaded(level,AABB.ofSize(guess,3,8,3)))continue;
            var point=standing.safeStanding(guess);
            if(point!=null && space(level,npc,point,reserved)){pos=point;break;}
        }
        if(pos==null){npc.discard();return new Prepared(null,SpawnResult.Status.NO_SPACE,"No safe loaded space nearby");}
        npc.moveTo(pos.x,pos.y,pos.z,yaw,pitch);npc.setYHeadRot(yaw);
        npc.setOrder(spec.order(),spec.radius());npc.setAmmoPolicy(spec.ammoPolicy(),spec.reserve());
        npc.setItemSlot(EquipmentSlot.MAINHAND,weapon);npc.runtime().onEquip(weapon);npc.addTag(RuntimeRegistry.TAG);
        return new Prepared(npc,SpawnResult.Status.SUCCESS,"");
    }
    private static boolean space(ServerLevel level,TacticalNpcEntity npc,Vec3 point,List<TacticalNpcEntity> reserved){
        var body=AABB.ofSize(point.add(0,npc.getBbHeight()/2,0),npc.getBbWidth(),npc.getBbHeight(),npc.getBbWidth());
        if(!loaded(level,body.inflate(.1)) || !level.getWorldBorder().isWithinBounds(body) || !safeVolume(level,body) || !level.noCollision(npc,body))return false;
        for(var other:reserved)if(other!=npc && body.inflate(.1).intersects(other.getBoundingBox()))return false;
        var occupants=new ArrayList<LivingEntity>(1);
        level.getEntities(EntityTypeTest.forClass(LivingEntity.class),body.inflate(.1),e->e!=npc && e.isAlive(),occupants,1);
        return occupants.isEmpty();
    }
    private static boolean safeVolume(ServerLevel level,AABB body){
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(net.minecraft.core.BlockPos.containing(body.minX,body.minY,body.minZ),net.minecraft.core.BlockPos.containing(body.maxX,body.maxY,body.maxZ))){
            var state=level.getBlockState(pos);
            if(!state.getFluidState().isEmpty() || state.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                    || state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH) || state.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE))return false;
        }
        return true;
    }
    private static boolean loaded(ServerLevel level,AABB box){
        if(box.minY<level.getMinBuildHeight() || box.maxY>=level.getMaxBuildHeight())return false;
        for(int x=net.minecraft.util.Mth.floor(box.minX)>>4;x<=net.minecraft.util.Mth.floor(box.maxX)>>4;x++)
            for(int z=net.minecraft.util.Mth.floor(box.minZ)>>4;z<=net.minecraft.util.Mth.floor(box.maxZ)>>4;z++)
                if(level.getChunkSource().getChunkNow(x,z)==null)return false;
        return true;
    }
    private NpcSpawnService(){}
}
