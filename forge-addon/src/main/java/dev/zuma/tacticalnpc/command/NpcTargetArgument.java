package dev.zuma.tacticalnpc.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import com.mojang.brigadier.suggestion.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;

/** One parser/resolver for every public operation; selectors retain vanilla grammar and permissions. */
public final class NpcTargetArgument implements ArgumentType<NpcTargetArgument.Target> {
    public record Target(String token,EntitySelector selector) {}
    @Override public Target parse(StringReader reader) throws CommandSyntaxException {
        if(reader.canRead() && reader.peek()=='@')return new Target(null,new EntitySelectorParser(reader).parse());
        return new Target(reader.readUnquotedString(),null);
    }
    public static CommandSyntaxException error(String message){return new SimpleCommandExceptionType(Component.literal(message)).create();}
    public static List<LivingEntity> resolve(CommandContext<CommandSourceStack> context,boolean players) throws CommandSyntaxException {
        var source=context.getSource();var target=context.getArgument("targets",Target.class);
        var names=NpcNames.of(source.getServer());var result=new ArrayList<LivingEntity>();
        if(target.selector()!=null){
            var entities=target.selector().findEntities(source);
            for(var entity:entities){
                if(!(entity instanceof TacticalNpcEntity || players && entity instanceof ServerPlayer) || !entity.isAlive() || entity.isRemoved())
                    throw error("Every target must be a live independent NPC"+(players?" or explicit player":"")+"; no targets changed.");
                result.add((LivingEntity)entity);
            }
        }else{
            var token=target.token();
            if(token.matches("[+-]?(?:[0-9].*|\\.[0-9].*)") || token.equalsIgnoreCase("NaN") || token.equalsIgnoreCase("Infinity")){
                double radius;
                try{radius=Double.parseDouble(token);}catch(NumberFormatException ex){throw error("Selection radius must be a number in 1..128 blocks.");}
                if(!Double.isFinite(radius) || radius<1 || radius>128)throw error("Selection radius must be 1..128 blocks.");
                var player=source.getPlayerOrException();
                for(var npc:names.loaded(source.getServer()))if(npc.level()==player.level() && npc.position().distanceToSqr(player.position())<=radius*radius)result.add(npc);
            }else{
                var entry=names.find(token);
                if(entry==null)throw error("Unknown NPC name: "+token+". Use /npc list or an explicit vanilla selector.");
                var npc=names.resolve(source.getServer(),entry.id());
                if(npc==null)throw error("NPC "+entry.name()+" is unloaded or no longer alive; no chunk loaded and no order queued.");
                result.add(npc);
            }
        }
        if(result.isEmpty())throw error("No live loaded NPC targets selected.");
        return List.copyOf(result);
    }
    @Override public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context,SuggestionsBuilder builder){
        if(context.getSource() instanceof CommandSourceStack source){
            if(!source.hasPermission(2))return builder.buildFuture();
            if(!builder.getRemaining().startsWith("@"))return SharedSuggestionProvider.suggest(NpcNames.of(source.getServer()).entries().stream().map(NpcNames.Entry::name),builder);
        }
        return EntityArgument.entities().listSuggestions(context,builder);
    }
    @Override public Collection<String> getExamples(){return List.of("Steve","32","@e[type=tacticalnpcai:npc]");}
}
