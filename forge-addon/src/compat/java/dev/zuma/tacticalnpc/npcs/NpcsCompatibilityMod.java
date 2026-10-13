package dev.zuma.tacticalnpc.npcs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
@Mod("tacticalnpcai_npcs")
public final class NpcsCompatibilityMod {
    public NpcsCompatibilityMod() { MinecraftForge.EVENT_BUS.register(new Npc210Adapter()); }
}
