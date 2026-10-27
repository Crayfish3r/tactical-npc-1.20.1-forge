package dev.zuma.tacticalnpc.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.OrderState;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import dev.zuma.tacticalnpc.runtime.*;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;

/** All validation precedes mutation. Registration and targeting have no operation implementations. */
public final class NpcOperations {
    private static void reply(CommandSourceStack source,String message){source.sendSuccess(()->Component.literal(message),true);}
    public static int mode(CommandContext<CommandSourceStack> c,OrderState.Kind kind,Double radius) throws CommandSyntaxException {
        if(radius!=null && !OrderState.validRadius(kind,radius))throw NpcTargetArgument.error("Behavior radius must be 4.."+OrderState.maximumRadius(kind)+" blocks.");
        var targets=NpcTargetArgument.resolve(c,false);
        for(var entity:targets){var npc=(TacticalNpcEntity)entity;
            npc.setOrder(kind,radius==null?Math.min(OrderState.maximumRadius(kind),npc.runtime().orderState().radius()):radius);
        }
        reply(c.getSource(),"Order "+kind+" assigned to "+targets.size()+" NPC(s); anchor=each current position; "+
            (kind==OrderState.Kind.ASSAULT?"movement=free":kind==OrderState.Kind.HOLD_POSITION?"STAND_WAIT -> COMBAT XZ=6 -> DEFEND radius=32": "behavior radius="+radius));
        return targets.size();
    }
    public static int team(CommandContext<CommandSourceStack> c,String team) throws CommandSyntaxException {
        try{NpcTeams.validate(team);}catch(IllegalArgumentException ex){throw NpcTargetArgument.error(ex.getMessage());}
        var targets=NpcTargetArgument.resolve(c,true);
        for(var entity:targets)NpcTeams.assign(entity,team);
        long count=targets.stream().filter(TacticalNpcEntity.class::isInstance).count();
        reply(c.getSource(),"Team "+(team==null?"none":team)+" assigned to "+count+" NPC(s), "+(targets.size()-count)+" player(s); coordination=automatic.");
        return targets.size();
    }
    public static int ammo(CommandContext<CommandSourceStack> c,TacticalNpcEntity.AmmoPolicy policy,int reserve) throws CommandSyntaxException {
        var targets=NpcTargetArgument.resolve(c,false);
        for(var entity:targets)((TacticalNpcEntity)entity).setAmmoPolicy(policy,reserve);
        reply(c.getSource(),"Ammo policy "+policy+" reserve="+(policy==TacticalNpcEntity.AmmoPolicy.INFINITE?"infinite":reserve)+" assigned to "+targets.size()+" NPC(s).");
        return targets.size();
    }
    public static int equip(CommandContext<CommandSourceStack> c,ResourceLocation id,boolean legacy) throws CommandSyntaxException {
        // Construct and validate every replacement before touching native reload or equipment.
        var targets=legacy?legacyTargets(c):NpcTargetArgument.resolve(c,false);
        var replacements=new ArrayList<net.minecraft.world.item.ItemStack>();
        if(TimelessAPI.getCommonGunIndex(id).isEmpty())throw NpcTargetArgument.error("Unknown loaded TaCZ gun: "+id);
        try{for(var entity:targets)replacements.add(GunSupport.create(id));}
        catch(IllegalArgumentException ex){throw NpcTargetArgument.error(ex.getMessage());}
        for(int i=0;i<targets.size();i++){
            var entity=targets.get(i);var runtime=RuntimeRegistry.adapt(entity);runtime.requireServer();runtime.operator().cancelReload();
            entity.setItemSlot(EquipmentSlot.MAINHAND,replacements.get(i));runtime.onEquip(replacements.get(i));
            if(!entity.getTags().contains(RuntimeRegistry.TAG))runtime.operator().draw(entity::getMainHandItem);
        }
        reply(c.getSource(),"Equipped "+id+" for "+targets.size()+" NPC(s); empty magazine, native reload.");return targets.size();
    }
    private static List<LivingEntity> legacyTargets(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var result=new ArrayList<LivingEntity>();
        for(var entity:net.minecraft.commands.arguments.EntityArgument.getEntities(c,"legacy")){
            if(!(entity instanceof LivingEntity living) || entity instanceof TacticalNpcEntity || RuntimeRegistry.adapt(living)==null || !entity.isAlive() || entity.isRemoved())
                throw NpcTargetArgument.error("Select only live supported optional legacy NPCs; no targets changed.");
            result.add(living);
        }
        return result;
    }
    public static int info(CommandContext<CommandSourceStack> c,boolean debug) throws CommandSyntaxException {
        var targets=NpcTargetArgument.resolve(c,false);
        for(var entity:targets){var npc=(TacticalNpcEntity)entity;var stack=npc.getMainHandItem();var gun=IGun.getIGunOrNull(stack);
            String detail=npc.npcName()+" team="+teamName(npc)+" mode="+modeName(npc.order())+" availability=loaded dimension="+npc.level().dimension().location()+
                (npc.order().equals("ASSAULT")?" movement=free":" behaviorRadius="+npc.runtime().orderState().radius())+
                " gun="+(gun==null?"none":gun.getGunId(stack))+" ammo="+npc.ammoPolicy()+" magazine="+(gun==null?0:gun.getCurrentAmmoCount(stack))+
                " chamber="+(gun!=null && gun.hasBulletInBarrel(stack))+" reserve="+(npc.ammoPolicy()==TacticalNpcEntity.AmmoPolicy.INFINITE?"infinite":gun==null?0:gun.getDummyAmmoAmount(stack))+" profile=HARD";
            if(debug)detail+=" uuid="+npc.getUUID()+" serviceId="+npc.serviceId()+" standPhase="+npc.standPhase()+" knowledge="+(npc.controller()==null || npc.controller().observation()==null?"none":npc.controller().observation().source())+
                " fire={"+(npc.controller()==null?"inactive":npc.controller().fireDiagnostics())+"}";
            final String message=detail;c.getSource().sendSuccess(()->Component.literal(message),false);
        }
        return targets.size();
    }
    public static int list(CommandSourceStack source,int page) throws CommandSyntaxException {
        var names=NpcNames.of(source.getServer());var entries=names.entries();int pages=Math.max(1,(entries.size()+9)/10);
        if(page>pages)throw NpcTargetArgument.error("Page must be 1.."+pages);
        reply(source,"NPC list page "+page+"/"+pages+"; known="+entries.size());
        for(var entry:entries.subList((page-1)*10,Math.min(page*10,entries.size()))){
            var npc=names.resolve(source.getServer(),entry.id());var team=source.getServer().getScoreboard().getPlayersTeam(entry.id().toString());
            reply(source,entry.name()+" team="+(team==null?"none":team.getName())+" mode="+modeName(npc==null?entry.mode():npc.order())+" availability="+(npc==null?"unloaded/absent":"loaded")+" dimension="+(npc==null?entry.dimension():npc.level().dimension().location()));
        }
        return entries.size();
    }
    private static String teamName(TacticalNpcEntity npc){return npc.getTeam()==null?"none":npc.getTeam().getName();}
    private static String modeName(String mode){return switch(mode){case "ASSAULT"->"assault";case "DEFEND_AREA"->"defend";case "HOLD_POSITION"->"stand";case "PATROL"->"patrol";default->mode;};}
    private NpcOperations(){}
}
