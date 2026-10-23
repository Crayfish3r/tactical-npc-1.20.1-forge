package dev.zuma.tacticalnpc;

import com.mojang.logging.LogUtils;
import dev.zuma.tacticalnpc.entity.NpcEntities;
import dev.zuma.tacticalnpc.config.ServerConfig;
import dev.zuma.tacticalnpc.command.NpcCommands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(TacticalNpcAiMod.ID)
public final class TacticalNpcAiMod {
    public static final String ID = "tacticalnpcai";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TacticalNpcAiMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        NpcEntities.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        NpcCommands.registerArguments(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(ServerConfig::onConfig);
        MinecraftForge.EVENT_BUS.register(new dev.zuma.tacticalnpc.compat.ShotSafetyEvents());
        MinecraftForge.EVENT_BUS.register(new NpcCommands());
        // Warm SavedData before ordinary ticks, including worlds without any NPCs yet.
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStartedEvent event)->{
            dev.zuma.tacticalnpc.command.NpcNames.of(event.getServer());
            dev.zuma.tacticalnpc.api.MatchNpcRegistry.of(event.getServer()).startup();
        });
        MinecraftForge.EVENT_BUS.register(new dev.zuma.tacticalnpc.combat.AiTelemetry());
        LOGGER.info("Tactical NPC AI 0.2.0: independent runtime / TaCZ 1.1.8-hotfix2; optional NPCs 2.1.0 compatibility");
    }
}
