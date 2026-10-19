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
        event.getDispatcher().register(Commands.literal("npc").requires(s->s.hasPermission(2))
            .then(Commands.literal("mode").requires(s->s.hasPermission(2))
                .then(mode("attack",dev.zuma.tacticalnpc.core.OrderState.Kind.ASSAULT))
                .then(Commands.literal("defend")
                    .then(Commands.argument("radius",com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(4,128))
                        .executes(c->modeOrder(c,dev.zuma.tacticalnpc.core.OrderState.Kind.DEFEND_AREA,false,com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c,"radius")))
                        .then(Commands.argument("targets",EntityArgument.entities())
                            .executes(c->modeOrder(c,dev.zuma.tacticalnpc.core.OrderState.Kind.DEFEND_AREA,true,com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c,"radius"))))))
                .then(mode("stand",dev.zuma.tacticalnpc.core.OrderState.Kind.HOLD_POSITION))));
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
                    .then(Commands.argument("radius",com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(4,128))
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
    private static int order(net.minecraft.commands.CommandSourceStack source,net.minecraft.world.entity.Entity entity,String kind,double radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        dev.zuma.tacticalnpc.core.OrderState.Kind order;
        try {order=dev.zuma.tacticalnpc.core.OrderState.Kind.valueOf(kind.toUpperCase(java.util.Locale.ROOT));}
        catch(IllegalArgumentException ex){source.sendFailure(Component.literal("Use DEFEND_AREA (radius 4..128), HOLD_POSITION, ASSAULT or PATROL (4..64)."));return 0;}
        return applyOrder(source,java.util.List.of(entity),order,radius);
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> mode(String name,dev.zuma.tacticalnpc.core.OrderState.Kind kind) {
        return Commands.literal(name).executes(c->modeOrder(c,kind,false,null))
            .then(Commands.argument("targets",EntityArgument.entities()).executes(c->modeOrder(c,kind,true,null)));
    }
    private static int modeOrder(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,dev.zuma.tacticalnpc.core.OrderState.Kind kind,boolean explicit,Double radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source=context.getSource();
        var targets=explicit?EntityArgument.getEntities(context,"targets"):java.util.List.of(aimedNpc(source));
        return applyOrder(source,targets,kind,radius);
    }
    /** Both syntaxes mutate only the entity's existing order state, after validating all recipients. */
    private static int applyOrder(net.minecraft.commands.CommandSourceStack source,java.util.Collection<? extends net.minecraft.world.entity.Entity> targets,dev.zuma.tacticalnpc.core.OrderState.Kind kind,Double radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(radius!=null && !dev.zuma.tacticalnpc.core.OrderState.validRadius(kind,radius))throw commandError("Order radius must be 4.."+dev.zuma.tacticalnpc.core.OrderState.maximumRadius(kind)+" blocks.");
        if(targets.isEmpty())throw commandError("No NPC targets selected.");
        for(var entity:targets) {
            if(!(entity instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity npc) || !npc.isAlive() || npc.isRemoved())
                throw commandError("Every target must be a live independent tacticalnpcai:npc; no targets changed.");
            npc.runtime().requireServer();
        }
        for(var entity:targets) {
            var npc=(dev.zuma.tacticalnpc.entity.TacticalNpcEntity)entity;
            npc.setOrder(kind,radius==null?Math.min(dev.zuma.tacticalnpc.core.OrderState.maximumRadius(kind),npc.runtime().orderState().radius()):radius);
        }
        source.sendSuccess(()->Component.literal("Order "+kind+" assigned to "+targets.size()+" NPC(s), anchored at each current position; "+(radius==null?"existing radii retained up to this order's maximum.":"radius="+radius)),true);
        return targets.size();
    }
    private static net.minecraft.world.entity.Entity aimedNpc(net.minecraft.commands.CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player))
            throw commandError("Console/non-player sources require explicit targets at the end of /npc mode.");
        var level=player.serverLevel();Vec3 start=player.getEyePosition(),end=start.add(player.getViewVector(1).scale(32));
        var block=level.clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        boolean wall=block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS;
        if(wall)end=block.getLocation();
        var candidates=new java.util.ArrayList<net.minecraft.world.entity.Entity>();
        level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.Entity.class),
            new net.minecraft.world.phys.AABB(start,end).inflate(1),e->e!=player && !e.isSpectator() && e.isPickable() && !e.isRemoved(),candidates,64);
        if(candidates.size()==64)throw commandError("Aim selection is crowded; use explicit targets.");
        net.minecraft.world.entity.Entity hit=null;double nearest=start.distanceToSqr(end);
        for(var entity:candidates) {
            var box=entity.getBoundingBox().inflate(entity.getPickRadius());
            var point=box.contains(start)?java.util.Optional.of(start):box.clip(start,end);
            if(point.isPresent()) {
                double distance=start.distanceToSqr(point.get());
                if(distance<nearest || !wall && distance==nearest) {nearest=distance;hit=entity;}
            }
        }
        if(hit==null)throw commandError("Aim at an independent NPC within 32 blocks with no wall, or supply targets.");
        return hit;
    }
    private static com.mojang.brigadier.exceptions.CommandSyntaxException commandError(String message) {
        return new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal(message)).create();
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

