package dev.zuma.tacticalnpc.command;

import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.arguments.*;
import dev.zuma.tacticalnpc.core.OrderState;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity.AmmoPolicy;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.*;
import net.minecraft.commands.synchronization.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.registries.DeferredRegister;

/** The sole user command root. Operations live in NpcOperations / NpcSpawning. */
public final class NpcCommands {
    private static final DeferredRegister<ArgumentTypeInfo<?,?>> ARGUMENTS=DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE,"tacticalnpcai");
    static {ARGUMENTS.register("target",()->ArgumentTypeInfos.registerByClass(NpcTargetArgument.class,SingletonArgumentInfo.contextFree(NpcTargetArgument::new)));}
    public static void registerArguments(IEventBus bus){ARGUMENTS.register(bus);}
    @SubscribeEvent public void register(RegisterCommandsEvent event){
        // Spawn-only compatibility aliases share the exact partial administrator spawn path.
        event.getDispatcher().register(Commands.literal("spawn").then(spawnAlias("npc")));
        event.getDispatcher().register(Commands.literal("tacticalnpc").requires(s->s.hasPermission(2)).then(spawn()));
        event.getDispatcher().register(Commands.literal("npc").requires(s->s.hasPermission(2))
            .executes(c->help(c.getSource()))
            .then(Commands.literal("help").executes(c->help(c.getSource())))
            .then(spawn())
            .then(Commands.literal("list").executes(c->NpcOperations.list(c.getSource(),1))
                .then(Commands.argument("page",IntegerArgumentType.integer(1)).executes(c->NpcOperations.list(c.getSource(),IntegerArgumentType.getInteger(c,"page")))))
            .then(Commands.literal("info").then(targets().executes(c->NpcOperations.info(c,false))))
            .then(Commands.literal("mode").then(targets()
                .then(Commands.literal("assault").executes(c->NpcOperations.mode(c,OrderState.Kind.ASSAULT,null))
                    .then(Commands.argument("unexpected",StringArgumentType.greedyString()).executes(c->{throw NpcTargetArgument.error("assault uses free movement and takes no behavior radius. Use /npc mode <target> assault.");})))
                .then(area("defend",OrderState.Kind.DEFEND_AREA,128))
                .then(area("patrol",OrderState.Kind.PATROL,64))
                .then(Commands.literal("stand").executes(c->NpcOperations.mode(c,OrderState.Kind.HOLD_POSITION,null)))))
            .then(Commands.literal("team").then(targets()
                .then(Commands.literal("clear").executes(c->NpcOperations.team(c,null)))
                .then(Commands.argument("team",StringArgumentType.string()).suggests((c,b)->SharedSuggestionProvider.suggest(c.getSource().getServer().getScoreboard().getTeamNames().stream().map(StringArgumentType::escapeIfRequired),b))
                    .executes(c->NpcOperations.team(c,StringArgumentType.getString(c,"team"))))))
            .then(Commands.literal("equip").then(targets().then(gun(false))))
            .then(Commands.literal("ammo").then(targets()
                .then(Commands.literal("infinite").executes(c->NpcOperations.ammo(c,AmmoPolicy.INFINITE,0)))
                .then(Commands.literal("finite").then(Commands.argument("reserve",IntegerArgumentType.integer(0,1000000))
                    .executes(c->NpcOperations.ammo(c,AmmoPolicy.FINITE,IntegerArgumentType.getInteger(c,"reserve")))))))
            .then(Commands.literal("debug")
                .then(Commands.literal("diagnostics").executes(c->{c.getSource().sendSuccess(()->Component.literal(dev.zuma.tacticalnpc.combat.AiTelemetry.diagnostics(c.getSource().getLevel())+" names="+NpcNames.of(c.getSource().getServer()).counts()+" matches="+dev.zuma.tacticalnpc.api.MatchNpcRegistry.of(c.getSource().getServer()).counts()),false);return 1;}))
                .then(Commands.literal("info").then(targets().executes(c->NpcOperations.info(c,true))))
                .then(Commands.literal("legacy")
                    .then(Commands.literal("spawn").executes(c->NpcSpawning.spawnLegacy(c.getSource())))
                    .then(Commands.literal("equip").then(Commands.argument("legacy",EntityArgument.entities()).then(gun(true)))))));
    }
    private static RequiredArgumentBuilder<CommandSourceStack,NpcTargetArgument.Target> targets(){
        // A custom provider serializes as ask_server: clients do not own the persistent name registry.
        return Commands.argument("targets",new NpcTargetArgument()).suggests((c,b)->new NpcTargetArgument().listSuggestions(c,b));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> area(String literal,OrderState.Kind kind,int maximum){
        return Commands.literal(literal).then(Commands.argument("radius",DoubleArgumentType.doubleArg(4,maximum)).executes(c->NpcOperations.mode(c,kind,DoubleArgumentType.getDouble(c,"radius"))));
    }
    private static RequiredArgumentBuilder<CommandSourceStack,net.minecraft.resources.ResourceLocation> gun(boolean legacy){
        return Commands.argument("gun",ResourceLocationArgument.id())
            .suggests((c,b)->SharedSuggestionProvider.suggestResource(com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex().stream().map(java.util.Map.Entry::getKey),b))
            .executes(c->NpcOperations.equip(c,ResourceLocationArgument.getId(c,"gun"),legacy));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> spawn(){
        return spawnAlias("spawn");
    }
    private static LiteralArgumentBuilder<CommandSourceStack> spawnAlias(String name){
        return Commands.literal(name).requires(s->s.hasPermission(2)).executes(c->NpcSpawning.spawnIndependent(c.getSource(),null,1))
            .then(Commands.literal("count").then(Commands.argument("count",IntegerArgumentType.integer(1,16)).executes(c->NpcSpawning.spawnIndependent(c.getSource(),null,IntegerArgumentType.getInteger(c,"count")))))
            .then(Commands.literal("team").then(Commands.argument("team",StringArgumentType.string())
                .suggests((c,b)->SharedSuggestionProvider.suggest(c.getSource().getServer().getScoreboard().getTeamNames().stream().map(StringArgumentType::escapeIfRequired),b))
                .executes(c->NpcSpawning.spawnIndependent(c.getSource(),StringArgumentType.getString(c,"team"),1))
                .then(Commands.literal("count").then(Commands.argument("count",IntegerArgumentType.integer(1,16)).executes(c->NpcSpawning.spawnIndependent(c.getSource(),StringArgumentType.getString(c,"team"),IntegerArgumentType.getInteger(c,"count")))))));
    }
    private static int help(CommandSourceStack source){
        for(String line:new String[]{"/npc spawn [team <team>] [count <1..16>] | /npc list [page] | /npc info <target>",
            "/npc mode <target> assault | defend <4..128> | patrol <4..64> | stand",
            "/npc team <target> <team|clear> | /npc equip <target> <gun> | /npc ammo <target> infinite|finite <reserve>",
            "target = NPC name, selection radius 1..128 from player, or vanilla selector. No aim required. Names work from console and across dimensions; unloaded NPCs are never queued.",
            "/npc debug info <target> | diagnostics | legacy spawn | legacy equip <selector> <gun>. Combat profile: HARD."})
            source.sendSuccess(()->Component.literal(line),false);
        return 1;
    }
}
