package dev.zuma.tacticalnpc;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID,value=Dist.CLIENT)
public final class ClientSmokeProbe {
    private static int rendered, ticks;
    private static boolean armed,loaded;
    @SubscribeEvent public static void rendered(RenderLivingEvent.Post<?,?> event) {
        if(event.getEntity() instanceof TacticalNpcEntity npc && npc.getCustomName()!=null && npc.getCustomName().getString().equals("tactical_client_smoke")) {
            rendered++;var gun=IGun.getIGunOrNull(npc.getMainHandItem());
            armed|=gun!=null && gun.getGunId(npc.getMainHandItem()).toString().equals("tacz:ak47");
            loaded|=gun!=null && gun.getCurrentAmmoCount(npc.getMainHandItem())>0;
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance(); if(mc.level==null || mc.player==null) return;
        if(++ticks==200) {
            boolean noNpcs=!net.minecraftforge.fml.ModList.get().isLoaded("tacznpcs");
            if(rendered>=3 && armed && loaded && noNpcs) TacticalNpcAiMod.LOGGER.info("TACTICAL_CLIENT_SMOKE_PASS rendered={} armed={} loaded={} noNpcs={}",rendered,armed,loaded,noNpcs);
            else TacticalNpcAiMod.LOGGER.error("TACTICAL_CLIENT_SMOKE_FAIL rendered={} armed={} loaded={} noNpcs={}",rendered,armed,loaded,noNpcs);
            mc.stop();
        }
    }
}
