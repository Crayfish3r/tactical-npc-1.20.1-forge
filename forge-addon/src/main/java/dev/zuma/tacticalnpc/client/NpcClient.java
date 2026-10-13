package dev.zuma.tacticalnpc.client;
import dev.zuma.tacticalnpc.TacticalNpcAiMod;
import dev.zuma.tacticalnpc.entity.NpcEntities;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NpcClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(NpcEntities.NPC.get(),Renderer::new); }
    public static final class Renderer extends HumanoidMobRenderer<TacticalNpcEntity,HumanoidModel<TacticalNpcEntity>> {
        private static final ResourceLocation TEXTURE=new ResourceLocation("minecraft:textures/entity/zombie/zombie.png");
        public Renderer(EntityRendererProvider.Context context) { super(context,new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),.5f); }
        @Override public ResourceLocation getTextureLocation(TacticalNpcEntity npc) { return TEXTURE; }
    }
    private NpcClient() {}
}
