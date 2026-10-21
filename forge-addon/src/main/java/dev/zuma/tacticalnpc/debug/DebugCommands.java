package dev.zuma.tacticalnpc.debug;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
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
        // Merge only this literal child; do not require permission on another mod's /spawn root.
        event.getDispatcher().register(Commands.literal("spawn")
            .then(independentSpawn("npc").requires(s->s.hasPermission(2))));
        event.getDispatcher().register(Commands.literal("npc").requires(s->s.hasPermission(2))
            .then(teamCommands())
            .then(Commands.literal("info").executes(c->info(c,false))
                .then(Commands.argument("targets",EntityArgument.entities()).executes(c->info(c,true))))
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
            .then(independentSpawn("spawn"))
            .then(Commands.literal("spawnlegacy").executes(c -> spawnLegacy(c.getSource())))
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
                    // Tagged tactical runtimes draw once at their explicit server tick maintenance.
                    if(!npc.getTags().contains(RuntimeRegistry.TAG))runtime.operator().draw(npc::getMainHandItem);
                    c.getSource().sendSuccess(() -> Component.literal("Equipped " + id + " (empty magazine; NPC will reload)."), true);
                    return 1;
                })))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> independentSpawn(String literal){
        return Commands.literal(literal).executes(c->spawnIndependent(c.getSource(),null,1))
            .then(Commands.literal("count").then(Commands.argument("count",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,16))
                .executes(c->spawnIndependent(c.getSource(),null,com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"count")))))
            .then(Commands.literal("team").then(Commands.argument("team",com.mojang.brigadier.arguments.StringArgumentType.string())
                .suggests((c,b)->{for(String name:c.getSource().getLevel().getScoreboard().getTeamNames())b.suggest(com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(name));return b.buildFuture();})
                .executes(c->spawnIndependent(c.getSource(),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"team"),1))
                .then(Commands.literal("count").then(Commands.argument("count",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,16))
                    .executes(c->spawnIndependent(c.getSource(),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"team"),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"count")))))));
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> teamCommands(){
        return Commands.literal("team")
            .then(Commands.literal("clear").executes(c->team(c,null,false))
                .then(Commands.argument("targets",EntityArgument.entities()).executes(c->team(c,null,true))))
            .then(Commands.argument("team",com.mojang.brigadier.arguments.StringArgumentType.string())
                .suggests((c,b)->{for(String name:c.getSource().getLevel().getScoreboard().getTeamNames())b.suggest(com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(name));return b.buildFuture();})
                .executes(c->team(c,com.mojang.brigadier.arguments.StringArgumentType.getString(c,"team"),false))
                .then(Commands.argument("targets",EntityArgument.entities()).executes(c->team(c,com.mojang.brigadier.arguments.StringArgumentType.getString(c,"team"),true))));
    }
    private static int team(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,String name,boolean explicit) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try{dev.zuma.tacticalnpc.runtime.NpcTeams.validate(name);}catch(IllegalArgumentException ex){throw commandError(ex.getMessage());}
        var targets=explicit?EntityArgument.getEntities(c,"targets"):java.util.List.of(aimedNpc(c.getSource()));
        if(!explicit && targets.stream().anyMatch(target->!(target instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity)))throw commandError("Without targets, aim at an independent NPC; players require explicit targets.");
        for(var target:targets)if(!(target instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity || target instanceof net.minecraft.server.level.ServerPlayer) || !target.isAlive() || target.isRemoved())throw commandError("Every target must be a live independent NPC or player; no targets changed.");
        for(var target:targets)dev.zuma.tacticalnpc.runtime.NpcTeams.assign((net.minecraft.world.entity.LivingEntity)target,name);
        c.getSource().sendSuccess(()->Component.literal("Team "+(name==null?"none (independent enemies)":name)+" assigned to "+targets.size()+" target(s); NPC squad coordination is automatic."),true);return targets.size();
    }
    private static int info(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,boolean explicit) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var targets=explicit?EntityArgument.getEntities(c,"targets"):java.util.List.of(aimedNpc(c.getSource()));
        if(targets.size()>32)throw commandError("Select at most 32 NPCs for info.");
        for(var target:targets)if(!(target instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity))throw commandError("Info targets must be independent NPCs.");
        for(var target:targets){var npc=(dev.zuma.tacticalnpc.entity.TacticalNpcEntity)target;var gun=IGun.getIGunOrNull(npc.getMainHandItem());
            String mode=npc.neutral()?"legacy-neutral":switch(dev.zuma.tacticalnpc.core.OrderState.Kind.valueOf(npc.order())){case ASSAULT->"attack";case DEFEND_AREA->"defend";case PATROL->"patrol";case HOLD_POSITION->"stand("+npc.standPhase().name().toLowerCase(java.util.Locale.ROOT)+")";};
            String area=npc.order().equals("ASSAULT")?" movement=free":" radius="+npc.runtime().orderState().radius();
            String reserve=npc.ammoPolicy()==dev.zuma.tacticalnpc.entity.TacticalNpcEntity.AmmoPolicy.INFINITE?"infinite":Integer.toString(gun==null?0:gun.getDummyAmmoAmount(npc.getMainHandItem()));
            c.getSource().sendSuccess(()->Component.literal(npc.getUUID()+" team="+(npc.getTeam()==null?"none":npc.getTeam().getName())+" mode="+mode+area+" gun="+(gun==null?"none":gun.getGunId(npc.getMainHandItem()))+" ammo="+npc.ammoPolicy().name().toLowerCase(java.util.Locale.ROOT)+" magazine="+(gun==null?0:gun.getCurrentAmmoCount(npc.getMainHandItem()))+" chamber="+(gun!=null&&gun.hasBulletInBarrel(npc.getMainHandItem()))+" reserve="+reserve),false);
        }return targets.size();
    }
    private static int spawnIndependent(net.minecraft.commands.CommandSourceStack source,String team,int count){
        try{dev.zuma.tacticalnpc.runtime.NpcTeams.validate(team);}catch(IllegalArgumentException ex){source.sendFailure(Component.literal(ex.getMessage()));return 0;}
        var level=source.getLevel();var made=new java.util.ArrayList<dev.zuma.tacticalnpc.entity.TacticalNpcEntity>();
        // Keep a group clear of immediate teammate bodies; weapon safety remains native.
        // Single spawn searches +/-6, groups +/-12, always capped at 49 loaded candidates.
        int spacing=count==1?2:4;
        for(int number=0;number<count;number++){
            var runtime=RuntimeRegistry.create(level);runtime.requireServer();var npc=(dev.zuma.tacticalnpc.entity.TacticalNpcEntity)runtime.entity();
            var standing=new dev.zuma.tacticalnpc.combat.CoverSensor(runtime);Vec3 pos=null;
            for(int candidate=0;candidate<49;candidate++){
                int index=(candidate+number*7)%49;int dx=(index%7-3)*spacing,dz=(index/7-3)*spacing;
                if(dx==0 && dz==0)continue;
                Vec3 point=standing.safeStanding(source.getPosition().add(dx,0,dz));
                if(point!=null){
                    var body=net.minecraft.world.phys.AABB.ofSize(point.add(0,npc.getBbHeight()/2,0),npc.getBbWidth(),npc.getBbHeight(),npc.getBbWidth());
                    if(!level.getWorldBorder().isWithinBounds(body))continue;
                    var occupants=new java.util.ArrayList<net.minecraft.world.entity.LivingEntity>(1);
                    level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.LivingEntity.class),body.inflate(.1),e->e.isAlive(),occupants,1);
                    if(occupants.isEmpty()){pos=point;break;}
                }
            }
            if(pos==null)break;
            npc.moveTo(pos.x,pos.y,pos.z,source.getRotation().y,source.getRotation().x);npc.setYHeadRot(source.getRotation().y);
            npc.setOrder(dev.zuma.tacticalnpc.core.OrderState.Kind.ASSAULT,24);
            npc.setItemSlot(EquipmentSlot.MAINHAND,gun(new ResourceLocation("tacz:ak47")));runtime.onEquip(npc.getMainHandItem());npc.addTag(RuntimeRegistry.TAG);
            if(!level.noCollision(npc) || !level.addFreshEntity(npc))break;
            dev.zuma.tacticalnpc.runtime.NpcTeams.assign(npc,team);made.add(npc);
        }
        if(made.isEmpty()){source.sendFailure(Component.literal("Cannot spawn: no safe loaded space nearby."));return 0;}
        source.sendSuccess(()->Component.literal("Spawned "+made.size()+"/"+count+" independent NPC(s); team="+(team==null?"none":team)+", mode=attack, AK-47; native reload/ammo."),true);
        if(made.size()<count)source.sendFailure(Component.literal("Remaining NPCs were not created: no safe loaded space nearby."));return made.size();
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
        source.sendSuccess(()->Component.literal("Order "+kind+" assigned to "+targets.size()+" NPC(s), anchored at each current position; "+(kind==dev.zuma.tacticalnpc.core.OrderState.Kind.HOLD_POSITION?"STAND_WAIT -> COMBAT XZ=6 -> DEFEND radius=32 (fixed).":radius==null?"existing radii retained up to this order's maximum.":"radius="+radius)),true);
        return targets.size();
    }
    private static net.minecraft.world.entity.Entity aimedNpc(net.minecraft.commands.CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player))
            throw commandError("Console/non-player sources require explicit targets.");
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
        final int changed=count;source.sendSuccess(()->Component.literal("Legacy squad mapped to team "+(id==null?"none":id)+" for "+changed+" NPC(s). Use /npc team; coordination is automatic."),true);return count;
    }

    private static int spawnLegacy(net.minecraft.commands.CommandSourceStack source) {
        try {
            var level=source.getLevel();var runtime=RuntimeRegistry.createLegacy(level);
            runtime.requireServer();var npc=runtime.entity();Vec3 pos=source.getPosition().add(3,0,0);
            npc.moveTo(pos.x,pos.y,pos.z,0,0);npc.setPersistenceRequired();
            npc.setItemSlot(EquipmentSlot.MAINHAND,gun(new ResourceLocation("tacz:ak47")));runtime.onEquip(npc.getMainHandItem());npc.addTag(RuntimeRegistry.TAG);
            if(!level.noCollision(npc) || !level.addFreshEntity(npc)) {
                source.sendFailure(Component.literal("Cannot spawn: clear a space 3 blocks east of your position."));return 0;
            }
            source.sendSuccess(()->Component.literal("Tactical NPC "+npc.getUUID()+" spawned with AK-47; runtime=NPCs 2.1.0"),true);return 1;
        } catch(IllegalArgumentException ex) { source.sendFailure(Component.literal(ex.getMessage()));return 0; }
    }

    private static ItemStack gun(ResourceLocation id) {
        return GunSupport.create(id);
    }
}

