package dev.zuma.tacticalnpc;
import dev.zuma.tacticalnpc.entity.NpcEntities;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
/** Test-only, compiled solely with -PclientSmoke. */
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class ClientSmokeFixture {
    private static int started=-1, stage;
    private static java.util.UUID match;
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player)) return;
        var level=player.serverLevel();
        for(int x=-8;x<=24;x++)for(int z=-8;z<=24;z++) {
            level.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState());
            for(int y=101;y<=104;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        player.setGameMode(GameType.CREATIVE);player.teleportTo(level,9,101,11,180,0);
        level.setDayTime(6000);
        level.getEntitiesOfClass(dev.zuma.tacticalnpc.entity.TacticalNpcEntity.class,
            new net.minecraft.world.phys.AABB(0,100,0,17,106,17),n->n.getTags().contains("client_smoke")).forEach(net.minecraft.world.entity.Entity::discard);
        var api=dev.zuma.tacticalnpc.api.TacticalNpcMatchApi.get(player.server);if(match!=null)api.remove(match);match=java.util.UUID.randomUUID();
        api.registerPolicy(match,new dev.zuma.tacticalnpc.api.MatchNpcPolicy(){public boolean isActive(){return true;}public boolean canAttack(ServerPlayer p){return false;}});
        var spec=new dev.zuma.tacticalnpc.api.SpawnMemberSpec(new net.minecraft.resources.ResourceLocation("tacz:ak47"),dev.zuma.tacticalnpc.entity.TacticalNpcEntity.AmmoPolicy.INFINITE,0,dev.zuma.tacticalnpc.core.OrderState.Kind.HOLD_POSITION,6);
        var result=api.spawn(new dev.zuma.tacticalnpc.api.SpawnGroupRequest(level,match,java.util.UUID.randomUUID(),new net.minecraft.world.phys.Vec3(9,101,9),0,0,java.util.List.of(spec,spec)));
        if(!result.success())throw new IllegalStateException("Client match fixture: "+result);
        for(int i=0;i<2;i++) {
            var npc=(dev.zuma.tacticalnpc.entity.TacticalNpcEntity)level.getEntity(result.entityIds().get(i));npc.moveTo(8+i*2,101,6,0,0);
            npc.addTag("client_smoke");
            if(npc.getTeam().getNameTagVisibility()!=net.minecraft.world.scores.Team.Visibility.NEVER)throw new IllegalStateException("API group must hide names");
            // Admission assigns the persistent name; keep a test label only for this isolated fixture.
            npc.setCustomName(net.minecraft.network.chat.Component.literal(i==0?"tactical_client_smoke":"armored_client_smoke"));
            npc.enterNeutral();
            if(i==1) {
                npc.setItemSlot(EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
                npc.setItemSlot(EquipmentSlot.CHEST,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
                npc.setItemSlot(EquipmentSlot.LEGS,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_LEGGINGS));
                npc.setItemSlot(EquipmentSlot.FEET,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_BOOTS));
            }
            TacticalNpcAiMod.LOGGER.info("TACTICAL_CLIENT_SMOKE_FIXTURE player={} npc={} serviceId={}",player.getUUID(),npc.getUUID(),npc.serviceId());
        }
        level.getEntitiesOfClass(net.minecraft.world.entity.animal.Sheep.class,new net.minecraft.world.phys.AABB(0,100,0,17,106,17)).forEach(net.minecraft.world.entity.Entity::discard);
        var sheep=net.minecraft.world.entity.EntityType.SHEEP.create(level);sheep.moveTo(11,101,6);sheep.setNoAi(true);sheep.setCustomName(net.minecraft.network.chat.Component.literal("VisibleSheep"));sheep.setCustomNameVisible(true);level.addFreshEntity(sheep);
        started=player.server.getTickCount();stage=0;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || started<0 || stage>=4)return;
        var server=event.getServer();
        if(server.getTickCount()-started<(stage+1)*160)return;
        String team=switch(stage++){case 0->"1";case 1->"2";case 2->"clear";default->"skin_other";};
        int count=server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"npc team @e[type=tacticalnpcai:npc,tag=client_smoke] "+team);
        TacticalNpcAiMod.LOGGER.info("TACTICAL_SKIN_SMOKE_TEAM stage={} team={} commandResult={}",stage,team,count);
        if(stage==3){
            // Native removal/readmission after persisted NBT, exercising client packet recreation.
            var level=server.overworld();var actors=level.getEntitiesOfClass(dev.zuma.tacticalnpc.entity.TacticalNpcEntity.class,new net.minecraft.world.phys.AABB(0,100,0,17,106,17),n->n.getTags().contains("client_smoke"));
            for(var n:actors){var saved=n.saveWithoutId(new net.minecraft.nbt.CompoundTag());String label=n.getCustomName().getString();String name=n.npcName();n.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
                server.tell(new net.minecraft.server.TickTask(server.getTickCount()+1,()->{var copy=NpcEntities.NPC.get().create(level);copy.load(saved);level.addFreshEntity(copy);copy.setCustomName(net.minecraft.network.chat.Component.literal(label));if(!copy.npcName().equals(name) || copy.isCustomNameVisible())throw new IllegalStateException("Reload lost hidden name");}));
            }
            TacticalNpcAiMod.LOGGER.info("TACTICAL_NAME_SMOKE_READMISSION count={}",actors.size());
        }
    }
}
