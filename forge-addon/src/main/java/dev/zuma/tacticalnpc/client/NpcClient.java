package dev.zuma.tacticalnpc.client;
import dev.zuma.tacticalnpc.TacticalNpcAiMod;
import dev.zuma.tacticalnpc.entity.NpcEntities;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NpcClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(NpcEntities.NPC.get(),Renderer::new); }
    public static final class Renderer extends HumanoidMobRenderer<TacticalNpcEntity,PlayerModel<TacticalNpcEntity>> {
        private static final ResourceLocation DEFAULT_SKIN=skin("default");
        private static final ResourceLocation RED_SKIN=skin("red");
        private static final ResourceLocation BLUE_SKIN=skin("blue");
        public Renderer(EntityRendererProvider.Context context) {
            // Standard 64x64, four-pixel arms, including hat/jacket/sleeves/trousers.
            // PlayerModel keeps HumanoidModel animation and native TaCZ hand transforms.
            super(context,new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER),false),.5f);
            addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),context.getModelManager()));
        }
        private static ResourceLocation skin(String variant) {
            return new ResourceLocation(TacticalNpcAiMod.ID,"textures/entity/npc/military_skin_"+variant+".png");
        }
        @Override protected boolean shouldShowName(TacticalNpcEntity npc){return false;}
        // Forge's RenderNameTagEvent can override shouldShowName (ALLOW). Keep the final
        // renderer boundary hidden as well, without affecting any other entity renderer.
        @Override protected void renderNameTag(TacticalNpcEntity npc,net.minecraft.network.chat.Component text,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light){}
        @Override public ResourceLocation getTextureLocation(TacticalNpcEntity npc) {
            // Read the synced vanilla scoreboard every render; no cached skin or extra NBT.
            var team=npc.getTeam();
            if(team==null)return DEFAULT_SKIN;
            return switch(team.getName()) {
                case "1" -> RED_SKIN;
                case "2" -> BLUE_SKIN;
                default -> DEFAULT_SKIN;
            };
        }
    }
    private NpcClient() {}
}
