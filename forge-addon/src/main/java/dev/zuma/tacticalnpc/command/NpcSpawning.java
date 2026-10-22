package dev.zuma.tacticalnpc.command;
import com.tacz.guns.api.item.IGun;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import dev.zuma.tacticalnpc.compat.GunSupport;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
public final class NpcSpawning {
    public static int spawnIndependent(net.minecraft.commands.CommandSourceStack source,String team,int count){
        try{dev.zuma.tacticalnpc.runtime.NpcTeams.validate(team);}catch(IllegalArgumentException ex){source.sendFailure(Component.literal(ex.getMessage()));return 0;}
        var level=source.getLevel();var made=new java.util.ArrayList<dev.zuma.tacticalnpc.entity.TacticalNpcEntity>();
        // Keep a group clear of immediate teammate bodies; weapon safety remains native.
        // Single spawn searches +/-6, groups +/-12, always capped at 49 loaded candidates.
        int spacing=count==1?2:4;
        for(int number=0;number<count;number++){
            var runtime=RuntimeRegistry.create(level);runtime.requireServer();var npc=(dev.zuma.tacticalnpc.entity.TacticalNpcEntity)runtime.entity();
            var standing=new dev.zuma.tacticalnpc.combat.CoverSensor(runtime);Vec3 pos=null;
            for(int candidate=0;candidate<49;candidate++){
                int index=(candidate+number*7)%49;int dx=(index%7-3)*spacing,dz=(index/7-3)*spacing;
                if(dx==0 && dz==0)continue;
                Vec3 point=standing.safeStanding(source.getPosition().add(dx,0,dz));
                if(point!=null){
                    var body=net.minecraft.world.phys.AABB.ofSize(point.add(0,npc.getBbHeight()/2,0),npc.getBbWidth(),npc.getBbHeight(),npc.getBbWidth());
                    if(!level.getWorldBorder().isWithinBounds(body))continue;
                    var occupants=new java.util.ArrayList<net.minecraft.world.entity.LivingEntity>(1);
                    level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.LivingEntity.class),body.inflate(.1),e->e.isAlive(),occupants,1);
                    if(occupants.isEmpty()){pos=point;break;}
                }
            }
            if(pos==null)break;
            npc.moveTo(pos.x,pos.y,pos.z,source.getRotation().y,source.getRotation().x);npc.setYHeadRot(source.getRotation().y);
            npc.setOrder(dev.zuma.tacticalnpc.core.OrderState.Kind.ASSAULT,24);
            npc.setItemSlot(EquipmentSlot.MAINHAND,gun(new ResourceLocation("tacz:ak47")));runtime.onEquip(npc.getMainHandItem());npc.addTag(RuntimeRegistry.TAG);
            if(!level.noCollision(npc) || !level.addFreshEntity(npc))break;
            dev.zuma.tacticalnpc.runtime.NpcTeams.assign(npc,team);made.add(npc);
        }
        if(made.isEmpty()){source.sendFailure(Component.literal("Cannot spawn: no safe loaded space nearby."));return 0;}
        source.sendSuccess(()->Component.literal("Spawned "+made.size()+"/"+count+" independent NPC(s); team="+(team==null?"none":team)+", mode=assault, AK-47; names="+made.stream().map(dev.zuma.tacticalnpc.entity.TacticalNpcEntity::npcName).toList()+"; native reload/ammo."),true);
        if(made.size()<count)source.sendFailure(Component.literal("Remaining NPCs were not created: no safe loaded space nearby."));return made.size();
    }

    public static int spawnLegacy(net.minecraft.commands.CommandSourceStack source) {
        try {
            var level=source.getLevel();var runtime=RuntimeRegistry.createLegacy(level);
            runtime.requireServer();var npc=runtime.entity();Vec3 pos=source.getPosition().add(3,0,0);
            npc.moveTo(pos.x,pos.y,pos.z,0,0);npc.setPersistenceRequired();
            npc.setItemSlot(EquipmentSlot.MAINHAND,gun(new ResourceLocation("tacz:ak47")));runtime.onEquip(npc.getMainHandItem());npc.addTag(RuntimeRegistry.TAG);
            if(!level.noCollision(npc) || !level.addFreshEntity(npc)) {
                source.sendFailure(Component.literal("Cannot spawn: clear a space 3 blocks east of your position."));return 0;
            }
            source.sendSuccess(()->Component.literal("Tactical NPC "+npc.getUUID()+" spawned with AK-47; runtime=NPCs 2.1.0"),true);return 1;
        } catch(IllegalArgumentException ex) { source.sendFailure(Component.literal(ex.getMessage()));return 0; }
    }

    private static ItemStack gun(ResourceLocation id){return GunSupport.create(id);}
    private NpcSpawning(){}
}
