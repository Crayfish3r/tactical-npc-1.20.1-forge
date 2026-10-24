package dev.zuma.tacticalnpc;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import dev.zuma.tacticalnpc.client.NpcClient;
import net.minecraft.client.Screenshot;
import java.util.UUID;
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID,value=Dist.CLIENT)
public final class ClientSmokeProbe {
    private static final String[] TEAMS={"owned","1","2","none","skin_other"};
    private static final String[] SKINS={"default","red","blue","default","default"};
    private static int rendered, ticks, stage, stableTicks, completedAt=-1;
    private static boolean armed,loaded,aimed;
    private static int forcedNameEvents;
    private static UUID identity;
    private static TacticalNpcEntity actor;
    @SubscribeEvent public static void forceName(net.minecraftforge.client.event.RenderNameTagEvent event){if(event.getEntity() instanceof TacticalNpcEntity){event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);forcedNameEvents++;}}
    @SubscribeEvent public static void rendered(RenderLivingEvent.Post<?,?> event) {
        if(event.getEntity() instanceof TacticalNpcEntity npc && npc.getCustomName()!=null && npc.getCustomName().getString().equals("tactical_client_smoke")) {
            rendered++;var gun=IGun.getIGunOrNull(npc.getMainHandItem());
            armed|=gun!=null && gun.getGunId(npc.getMainHandItem()).toString().equals("tacz:ak47");
            loaded|=gun!=null && gun.getCurrentAmmoCount(npc.getMainHandItem())>0;
            actor=npc;
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance(); if(mc.level==null || mc.player==null) return;
        ticks++;
        if(completedAt>=0 && ticks-completedAt>=40) {mc.stop();return;}
        if(ticks>=900) {fail("timeout stage="+stage);return;}
        if(actor==null || stage>=TEAMS.length)return;
        double dx=actor.getX()-mc.player.getX(),dz=actor.getZ()-mc.player.getZ(),dy=actor.getEyeY()-mc.player.getEyeY();
        mc.player.setYRot((float)(Math.toDegrees(Math.atan2(dz,dx))-90));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));
        aimed|=mc.crosshairPickEntity==actor && mc.player.distanceTo(actor)<6;
        String team=actor.getTeam()==null?"none":actor.getTeam().getName();
        if(!(TEAMS[stage].equals("owned")?team.startsWith("tnpc_"):TEAMS[stage].equals(team))) {stableTicks=0;return;}
        if(++stableTicks<40)return;
        if(identity==null)identity=actor.getUUID();
        if(!identity.equals(actor.getUUID())) {fail("entity recreated");return;}
        if(!(mc.getEntityRenderDispatcher().getRenderer(actor) instanceof NpcClient.Renderer renderer)) {fail("unexpected renderer");return;}
        String expected="tacticalnpcai:textures/entity/npc/military_skin_"+SKINS[stage]+".png";
        if(!renderer.getTextureLocation(actor).toString().equals(expected)) {fail("wrong texture for "+team);return;}
        var model=renderer.getModel();
        if(!(model.hat.visible && model.jacket.visible && model.leftSleeve.visible && model.rightSleeve.visible && model.leftPants.visible && model.rightPants.visible)) {fail("outer layer hidden");return;}
        if(actor.isCustomNameVisible() || actor.getCustomName()==null) {fail("hidden name contract");return;}
        if(!armed || !loaded || !aimed || forcedNameEvents==0){
            if(stableTicks==40)TacticalNpcAiMod.LOGGER.info("TACTICAL_CLIENT_NATIVE_WARMUP armed={} loaded={} aimed={} nameEvents={}",armed,loaded,aimed,forcedNameEvents);
            return; // Native readiness is an outcome, bounded by the existing overall timeout.
        }
        try{var method=NpcClient.Renderer.class.getDeclaredMethod("shouldShowName",TacticalNpcEntity.class);method.setAccessible(true);if((boolean)method.invoke(renderer,actor)){fail("renderer reveals aimed NPC");return;}}catch(ReflectiveOperationException ex){fail(ex.toString());return;}
        String filename="skin-smoke-"+stage+"-"+team+"-"+System.currentTimeMillis()+".png";
        Screenshot.grab(mc.gameDirectory,filename,mc.getMainRenderTarget(),message->TacticalNpcAiMod.LOGGER.info("TACTICAL_SKIN_SCREENSHOT {}",message.getString()));
        TacticalNpcAiMod.LOGGER.info("TACTICAL_SKIN_SMOKE_STAGE stage={} team={} texture={} uuid={} rendered={} outer=true armed={} loaded={} screenshot={}",stage,team,expected,identity,rendered,armed,loaded,filename);
        stage++;stableTicks=0;
        if(stage==TEAMS.length) {
            boolean noNpcs=!net.minecraftforge.fml.ModList.get().isLoaded("tacznpcs");
            if(rendered>=3 && armed && loaded && noNpcs) TacticalNpcAiMod.LOGGER.info("TACTICAL_CLIENT_SMOKE_PASS stages={} rendered={} armed={} loaded={} noNpcs={} aimedClose={} forcedNameEvents={} hidden=true",stage,rendered,armed,loaded,noNpcs,aimed,forcedNameEvents);
            else {fail("render/weapon/standalone assertion");return;}
            completedAt=ticks;
        }
    }
    private static void fail(String reason) {
        TacticalNpcAiMod.LOGGER.error("TACTICAL_CLIENT_SMOKE_FAIL {}",reason);
        Minecraft.getInstance().stop();
    }
}
