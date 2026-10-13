package dev.zuma.tacticalnpc.runtime;
import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
/** Minecraft runtime boundary; no NPCs types escape this interface. */
public interface NpcRuntimeAdapter {
    PathfinderMob entity();
    default IGunOperator operator() { return IGunOperator.fromLivingEntity(entity()); }
    default boolean allied(LivingEntity other) { return entity().isAlliedTo(other); }
    default void onEquip(net.minecraft.world.item.ItemStack stack) {}
    default java.util.UUID squadId(){return null;}
    default dev.zuma.tacticalnpc.core.OrderState orderState() {
        var p=entity().position();return new dev.zuma.tacticalnpc.core.OrderState(dev.zuma.tacticalnpc.core.OrderState.Kind.ASSAULT,new dev.zuma.tacticalnpc.core.Vector3(p.x,p.y,p.z),64);
    }
    default void beforeTick() { entity().setTarget(null); }
    default void requireServer() {
        if (entity().level().isClientSide || entity().getServer() == null || !entity().getServer().isSameThread())
            throw new IllegalStateException("NPC mutation requires server thread");
    }
}
