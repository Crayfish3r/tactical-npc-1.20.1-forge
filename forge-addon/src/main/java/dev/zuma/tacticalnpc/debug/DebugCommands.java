package dev.zuma.tacticalnpc.debug;

import com.tacz.guns.api.TimelessAPI;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import dev.zuma.tacticalnpc.compat.GunSupport;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class DebugCommands {
    @SubscribeEvent
    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tacticalnpc").requires(s -> s.hasPermission(2))
            .then(Commands.literal("diagnostics").executes(c->{c.getSource().sendSuccess(()->Component.literal(dev.zuma.tacticalnpc.combat.AiTelemetry.diagnostics(c.getSource().getLevel())),false);return 1;}))
            .then(Commands.literal("hard").executes(c->{var s=c.getSource();dev.zuma.tacticalnpc.config.ServerConfig.PERCEPTION.set(1);dev.zuma.tacticalnpc.config.ServerConfig.REACTION.set(3);dev.zuma.tacticalnpc.config.ServerConfig.REACQUIRE.set(1);dev.zuma.tacticalnpc.config.ServerConfig.ACQUISITION.set(3);dev.zuma.tacticalnpc.config.ServerConfig.TURN.set(17.0);s.sendSuccess(()->Component.literal("Hard starting profile: perception=1 reaction=3 reacquire=1 acquisition=3 turn=17. Latency/speed need measurement. Verify persistent values in the server config."),true);return 1;}))
            .then(Commands.literal("spawn").executes(c -> spawn(c.getSource(),false)))
            .then(Commands.literal("spawnlegacy").executes(c -> spawn(c.getSource(),true)))
            .then(Commands.literal("squad").then(Commands.argument("npcs",EntityArgument.entities())
                .then(Commands.literal("clear").executes(c->squad(c.getSource(),EntityArgument.getEntities(c,"npcs"),null)))
                .then(Commands.argument("id",net.minecraft.commands.arguments.UuidArgument.uuid()).executes(c->squad(c.getSource(),EntityArgument.getEntities(c,"npcs"),net.minecraft.commands.arguments.UuidArgument.getUuid(c,"id"))))))
            .then(Commands.literal("order").then(Commands.argument("npc",EntityArgument.entity())
                .then(Commands.argument("kind",com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((c,b)->{for(var kind:dev.zuma.tacticalnpc.core.OrderState.Kind.values())b.suggest(kind.name());return b.buildFuture();})
                    .then(Commands.argument("radius",com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(4,64))
                        .executes(c->order(c.getSource(),EntityArgument.getEntity(c,"npc"),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"kind"),com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c,"radius")))))))
            .then(Commands.literal("faction").then(Commands.argument("npc",EntityArgument.entity())
                .then(Commands.argument("name",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c->{
                    if(!(EntityArgument.getEntity(c,"npc") instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity npc)) return 0;
                    try {npc.setFaction(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"name"));}
                    catch(IllegalArgumentException ex){c.getSource().sendFailure(Component.literal(ex.getMessage()));return 0;}
                    c.getSource().sendSuccess(()->Component.literal("Faction "+npc.faction()),true);return 1;
                }))))
            .then(Commands.literal("ammo").then(Commands.argument("npc",EntityArgument.entity())
                .then(Commands.literal("infinite").executes(c->ammo(c.getSource(),EntityArgument.getEntity(c,"npc"),false,0)))
                .then(Commands.literal("finite").then(Commands.argument("reserve",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,1000000))
                    .executes(c->ammo(c.getSource(),EntityArgument.getEntity(c,"npc"),true,com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"reserve")))))))
            .then(Commands.literal("equip").then(Commands.argument("npc", EntityArgument.entity())
                .then(Commands.argument("gun", ResourceLocationArgument.id()).executes(c -> {
                    var entity = EntityArgument.getEntity(c, "npc");
                    ResourceLocation id = ResourceLocationArgument.getId(c, "gun");
                    var runtime=entity instanceof net.minecraft.world.entity.LivingEntity living ? RuntimeRegistry.adapt(living) : null;
                    if (runtime==null || TimelessAPI.getCommonGunIndex(id).isEmpty()) {
                        c.getSource().sendFailure(Component.literal("Select a TACZ NPC and a valid loaded TaCZ gun ID."));
                        return 0;
                    }
                    ItemStack replacement;
                    try { replacement = gun(id); }
                    catch (IllegalArgumentException ex) {
                        c.getSource().sendFailure(Component.literal(ex.getMessage()));
                        return 0;
                    }
                    var npc=runtime.entity();
                    runtime.requireServer();
                    runtime.operator().cancelReload();
                    npc.setItemSlot(EquipmentSlot.MAINHAND, replacement);
                    runtime.onEquip(replacement);
                    runtime.operator().draw(npc::getMainHandItem);
                    c.getSource().sendSuccess(() -> Component.literal("Equipped " + id + " (empty magazine; NPC will reload)."), true);
                    return 1;
                })))));
    }

    private static int ammo(net.minecraft.commands.CommandSourceStack source,net.minecraft.world.entity.Entity entity,boolean finite,int reserve) {
        if(!(entity instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity npc)) {
            source.sendFailure(Component.literal("Ammo policy requires the independent tacticalnpcai:npc runtime."));return 0;
        }
        npc.setAmmoPolicy(finite?dev.zuma.tacticalnpc.entity.TacticalNpcEntity.AmmoPolicy.FINITE:dev.zuma.tacticalnpc.entity.TacticalNpcEntity.AmmoPolicy.INFINITE,reserve);
        source.sendSuccess(()->Component.literal("Ammo policy "+npc.ammoPolicy()+" reserve="+reserve),true);return 1;
    }
    private static int order(net.minecraft.commands.CommandSourceStack source,net.minecraft.world.entity.Entity entity,String kind,double radius) {
        if(!(entity instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity npc)) {
            source.sendFailure(Component.literal("Orders require the independent NPC runtime."));return 0;
        }
        try {npc.setOrder(dev.zuma.tacticalnpc.core.OrderState.Kind.valueOf(kind.toUpperCase(java.util.Locale.ROOT)),radius);}
        catch(IllegalArgumentException ex){source.sendFailure(Component.literal("Use DEFEND_AREA, HOLD_POSITION, ASSAULT or PATROL; radius 4..64."));return 0;}
        source.sendSuccess(()->Component.literal("Order "+npc.order()+" anchored at current NPC position; radius="+radius),true);return 1;
    }
    private static int squad(net.minecraft.commands.CommandSourceStack source,java.util.Collection<? extends net.minecraft.world.entity.Entity> entities,java.util.UUID id) {
        int count=0;for(var entity:entities)if(entity instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity npc){npc.setSquad(id);count++;}
        final int changed=count;source.sendSuccess(()->Component.literal("Squad "+id+" assigned to "+changed+" independent NPCs. Keep factions allied."),true);return count;
    }

    private static int spawn(net.minecraft.commands.CommandSourceStack source,boolean legacy) {
        try {
            var level=source.getLevel();var runtime=legacy?RuntimeRegistry.createLegacy(level):RuntimeRegistry.create(level);
            runtime.requireServer();var npc=runtime.entity();Vec3 pos=source.getPosition().add(3,0,0);
            npc.moveTo(pos.x,pos.y,pos.z,0,0);npc.setPersistenceRequired();
            npc.setItemSlot(EquipmentSlot.MAINHAND,gun(new ResourceLocation("tacz:ak47")));runtime.onEquip(npc.getMainHandItem());npc.addTag(RuntimeRegistry.TAG);
            if(!level.noCollision(npc) || !level.addFreshEntity(npc)) {
                source.sendFailure(Component.literal("Cannot spawn: clear a space 3 blocks east of your position."));return 0;
            }
            source.sendSuccess(()->Component.literal("Tactical NPC "+npc.getUUID()+" spawned with AK-47; runtime="+(legacy?"NPCs 2.1.0":"independent")),true);return 1;
        } catch(IllegalArgumentException ex) { source.sendFailure(Component.literal(ex.getMessage()));return 0; }
    }

    private static ItemStack gun(ResourceLocation id) {
        return GunSupport.create(id);
    }
}

