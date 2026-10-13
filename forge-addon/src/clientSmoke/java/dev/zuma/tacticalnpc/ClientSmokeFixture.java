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
/** Test-only, compiled solely with -PclientSmoke. */
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class ClientSmokeFixture {
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player)) return;
        var level=player.serverLevel();
        for(int x=0;x<=16;x++)for(int z=0;z<=16;z++) {
            level.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState());
            for(int y=101;y<=104;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        player.setGameMode(GameType.CREATIVE);player.teleportTo(level,8,101,12,180,0);
        level.getEntitiesOfClass(dev.zuma.tacticalnpc.entity.TacticalNpcEntity.class,
            new net.minecraft.world.phys.AABB(0,100,0,17,106,17),n->n.getTags().contains("client_smoke")).forEach(net.minecraft.world.entity.Entity::discard);
        var npc=NpcEntities.NPC.get().create(level);npc.moveTo(8,101,6,0,0);
        npc.setCustomName(net.minecraft.network.chat.Component.literal("tactical_client_smoke"));
        npc.setItemSlot(EquipmentSlot.MAINHAND,GunSupport.create(new net.minecraft.resources.ResourceLocation("tacz:ak47")));
        npc.addTag(RuntimeRegistry.TAG);npc.addTag("client_smoke");level.addFreshEntity(npc);
        TacticalNpcAiMod.LOGGER.info("TACTICAL_CLIENT_SMOKE_FIXTURE player={} npc={} serviceId={}",player.getUUID(),npc.getUUID(),npc.serviceId());
    }
}
