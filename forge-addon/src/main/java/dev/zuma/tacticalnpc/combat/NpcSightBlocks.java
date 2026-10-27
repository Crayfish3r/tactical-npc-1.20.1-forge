package dev.zuma.tacticalnpc.combat;

import dev.zuma.tacticalnpc.TacticalNpcAiMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/** AI observation/fire permission only. Does not describe projectile penetration. */
public final class NpcSightBlocks {
    public enum Kind { TRANSPARENT, LEAVES, GEOMETRY }
    public static final TagKey<Block> TRANSPARENT=tag("npc_sight_transparent");
    public static final TagKey<Block> LEAVES=tag("npc_sight_leaves");
    public static final TagKey<Block> WINDOWED_DOORS=tag("npc_sight_windowed_doors");
    public static final TagKey<Block> WINDOWED_TRAPDOORS=tag("npc_sight_windowed_trapdoors");
    private static TagKey<Block> tag(String name){return TagKey.create(Registries.BLOCK,new ResourceLocation(TacticalNpcAiMod.ID,name));}
    public static Kind classify(BlockState state) {
        // Count leaves even if a datapack also puts them in the unconditional transparent tag.
        if(state.is(LEAVES))return Kind.LEAVES;
        if(state.is(TRANSPARENT))return Kind.TRANSPARENT;
        // Only explicitly tagged windowed types bypass their panel, in both open/closed states.
        // Untagged open doors still use actual collision geometry, including the swung panel.
        if(state.is(WINDOWED_DOORS) && state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.OPEN))return Kind.TRANSPARENT;
        if(state.is(WINDOWED_TRAPDOORS) && state.getBlock() instanceof TrapDoorBlock && state.hasProperty(TrapDoorBlock.OPEN))return Kind.TRANSPARENT;
        return Kind.GEOMETRY;
    }
    private NpcSightBlocks(){}
}
