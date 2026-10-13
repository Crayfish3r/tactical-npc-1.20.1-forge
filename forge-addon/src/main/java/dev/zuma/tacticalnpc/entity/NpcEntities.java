package dev.zuma.tacticalnpc.entity;
import dev.zuma.tacticalnpc.TacticalNpcAiMod;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
public final class NpcEntities {
    public static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,TacticalNpcAiMod.ID);
    public static final RegistryObject<EntityType<TacticalNpcEntity>> NPC=TYPES.register("npc",()->EntityType.Builder.of(TacticalNpcEntity::new,MobCategory.CREATURE)
            .sized(.6f,1.8f).clientTrackingRange(10).updateInterval(2).build(TacticalNpcAiMod.ID+":npc"));
    public static void register(IEventBus bus) {
        TYPES.register(bus);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.EntityLeaveLevelEvent event)->{
            if(!event.getLevel().isClientSide && event.getEntity() instanceof TacticalNpcEntity npc) npc.release();
        });
        bus.addListener((EntityAttributeCreationEvent event)->event.put(NPC.get(),net.minecraft.world.entity.Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,64).build()));
        RuntimeRegistry.registerIndependent(e->e instanceof TacticalNpcEntity npc?npc.runtime():null,
                level->{var npc=NPC.get().create(level); if(npc==null) throw new IllegalStateException("Entity creation failed"); return npc.runtime();});
    }
    private NpcEntities() {}
}
