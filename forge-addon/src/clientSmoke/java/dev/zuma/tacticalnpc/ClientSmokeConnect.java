package dev.zuma.tacticalnpc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Test-only normal server-list ping/connect path, without persistent QuickPlay state. */
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID,value=Dist.CLIENT)
public final class ClientSmokeConnect {
    private static ServerStatusPinger pinger;
    private static ServerData data;
    private static boolean connected;
    private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        String address=System.getProperty("tactical.client.smoke.address");
        if(address==null || connected || event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(!(mc.screen instanceof TitleScreen))return;
        if(data==null){
            data=new ServerData("Name smoke",address,false);pinger=new ServerStatusPinger();
            try{pinger.pingServer(data,()->{});}catch(java.net.UnknownHostException ex){throw new IllegalStateException(ex);}
        }
        pinger.tick();
        if(data.forgeData!=null){
            pinger.removeAll();connected=true;
            TacticalNpcAiMod.LOGGER.info("TACTICAL_CLIENT_STATUS_CONNECT address={} forgeStatus=true",address);
            ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),data,false);
        }else if(++ticks>400){pinger.removeAll();TacticalNpcAiMod.LOGGER.error("TACTICAL_CLIENT_SMOKE_FAIL status ping timeout");mc.stop();connected=true;}
    }
}
