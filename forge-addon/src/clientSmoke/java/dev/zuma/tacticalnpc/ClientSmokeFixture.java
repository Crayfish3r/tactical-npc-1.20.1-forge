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
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player)) return;
        var level=player.serverLevel();
        for(int x=0;x<=16;x++)for(int z=0;z<=16;z++) {
            level.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState());
            for(int y=101;y<=104;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        player.setGameMode(GameType.CREATIVE);player.teleportTo(level,9,101,11,180,0);
        level.setDayTime(6000);
        level.getEntitiesOfClass(dev.zuma.tacticalnpc.entity.TacticalNpcEntity.class,
            new net.minecraft.world.phys.AABB(0,100,0,17,106,17),n->n.getTags().contains("client_smoke")).forEach(net.minecraft.world.entity.Entity::discard);
        for(int i=0;i<2;i++) {
            var npc=NpcEntities.NPC.get().create(level);npc.moveTo(8+i*2,101,6,0,0);
            npc.setItemSlot(EquipmentSlot.MAINHAND,GunSupport.create(new net.minecraft.resources.ResourceLocation("tacz:ak47")));
            npc.addTag(RuntimeRegistry.TAG);npc.addTag("client_smoke");level.addFreshEntity(npc);
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
        started=player.server.getTickCount();stage=0;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || started<0 || stage>=4)return;
        var server=event.getServer();
        if(server.getTickCount()-started<(stage+1)*160)return;
        String team=switch(stage++){case 0->"1";case 1->"2";case 2->"clear";default->"skin_other";};
        int count=server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"npc team @e[type=tacticalnpcai:npc,tag=client_smoke] "+team);
        TacticalNpcAiMod.LOGGER.info("TACTICAL_SKIN_SMOKE_TEAM stage={} team={} commandResult={}",stage,team,count);
    }
}
