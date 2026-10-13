package dev.zuma.tacticalnpc;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class ProjectileProbe {
    private static final Map<UUID,Integer> COUNTS=new HashMap<>();
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide && event.getEntity() instanceof com.tacz.guns.entity.EntityKineticBullet b && b.getOwner()!=null)
            COUNTS.merge(b.getOwner().getUUID(),1,Integer::sum);
    }
    public static int count(UUID owner) { return COUNTS.getOrDefault(owner,0); }
}
