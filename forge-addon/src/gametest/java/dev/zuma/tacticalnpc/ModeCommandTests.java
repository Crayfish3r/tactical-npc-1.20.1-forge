package dev.zuma.tacticalnpc;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tacz.guns.api.item.IGun;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.OrderState;
import dev.zuma.tacticalnpc.entity.NpcEntities;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class ModeCommandTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="mode-unified-state",timeoutTicks=55)
    public static void aliasesShareStatePersistenceAndInvalidation(GameTestHelper h){
        ScenarioIsolation.begin(h);var n=npc(h,3.5,7.5);
        var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=IGun.getIGunOrNull(stack);
        gun.setCurrentAmmoCount(stack,5);gun.setBulletInBarrel(stack,true);n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.runtime().onEquip(stack);
        n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,17);n.addTag(RuntimeRegistry.TAG);
        h.runAtTickTime(10,()->{
            var source=console(h);String name=n.npcName();var controller=n.controller();
            dev.zuma.tacticalnpc.config.ServerConfig.STRAFE.set(.22);
            h.assertTrue(controller!=null && controller.decision()!=null,"single controller active");
            var dest=h.absoluteVec(new Vec3(11.5,2,7.5));
            h.assertTrue(n.getNavigation().moveTo(dest.x,dest.y,dest.z,1),"real navigation exists");
            long generation=controller.generation(),cooldown=n.runtime().operator().getSynShootCoolDown();
            int rounds=gun.getCurrentAmmoCount(stack),reserve=gun.getDummyAmmoAmount(stack);boolean chamber=gun.hasBulletInBarrel(stack);
            run(h,source,"npc mode "+name.toLowerCase(java.util.Locale.ROOT)+" assault");
            h.assertTrue(n.order().equals("ASSAULT") && controller.generation()>generation && controller.decision()==null && n.getNavigation().isDone(),"name command invalidates actual path and decisions");
            run(h,source,"npc mode "+name+" stand");h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.WAIT && n.runtime().orderState().radius()==6,"fixed STAND phase");
            run(h,source,"npc mode "+name+" patrol 9");h.assertTrue(n.runtime().orderState().kind()==OrderState.Kind.PATROL,"PATROL remains distinct");
            run(h,source,"npc mode "+name+" defend 128");var expected=n.runtime().orderState();
            h.assertTrue(expected.anchor().equals(new dev.zuma.tacticalnpc.core.Vector3(n.getX(),n.getY(),n.getZ())),"anchor is recipient position");
            h.assertTrue(dev.zuma.tacticalnpc.config.ServerConfig.STRAFE.get()==.22,"NPC management never resets serverconfig");
            h.assertTrue(n.controller()==controller && gun.getCurrentAmmoCount(stack)==rounds && gun.getDummyAmmoAmount(stack)==reserve && gun.hasBulletInBarrel(stack)==chamber && n.runtime().operator().getSynShootCoolDown()==cooldown,"orders preserve native ammo/cooldown and controller ownership");
            var saved=new CompoundTag();n.save(saved);var restored=(TacticalNpcEntity)EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);
            h.assertTrue(saved.getCompound("tacticalnpc").getInt("schema")==6 && restored!=null && expected.equals(restored.runtime().orderState()) && name.equals(restored.npcName()) && n.serviceId().equals(restored.serviceId()),"schema6 persists name, service identity and existing state");
            restored.discard();h.assertTrue(run(h,source,"npc info "+name)==1,"unadmitted NBT clone cannot evict original loaded index");
            n.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="mode-unified-atomic",timeoutTicks=50)
    public static void validateWholeBatchBeforeMutationAndRequireConsoleTargets(GameTestHelper h){
        ScenarioIsolation.begin(h);var a=npc(h,3.5,7.5);var b=npc(h,10.5,10.5);
        var at=h.absoluteVec(new Vec3(8.5,2,7.5));var other=new ArmorStand(h.getLevel(),at.x,at.y,at.z);h.getLevel().addFreshEntity(other);
        a.addTag("mode-batch");b.addTag("mode-batch");other.addTag("mode-batch");
        h.runAtTickTime(10,()->{
            var source=console(h);var oldA=a.runtime().orderState();var oldB=b.runtime().orderState();
            for(String cmd:new String[]{"npc mode @e[tag=mode-batch] assault","npc ammo @e[tag=mode-batch] finite 99","npc equip @e[tag=mode-batch] tacz:glock_17","npc info @e[tag=mode-batch]"})reject(h,source,cmd);
            for(String tail:new String[]{"defend 3","defend 129","defend NaN","patrol 65","assault 10","stand 10"})reject(h,source,"npc mode "+a.npcName()+" "+tail);
            for(String value:new String[]{"0","129","-1","NaN","Infinity","32","UnknownName"})reject(h,source,"npc mode "+value+" assault");
            for(String cmd:new String[]{"npc mode assault","npc mode stand","npc info","npc team red"})reject(h,source,cmd);
            h.assertTrue(oldA.equals(a.runtime().orderState()) && oldB.equals(b.runtime().orderState()) && a.ammoPolicy()==TacticalNpcEntity.AmmoPolicy.INFINITE && a.getMainHandItem().isEmpty(),"all invalid batches/arguments leave recipients unchanged");
            h.assertTrue(run(h,source,"npc mode @e[type=tacticalnpcai:npc,tag=mode-batch] defend 16")==2,"explicit console selector works");
            h.assertTrue(!a.runtime().orderState().anchor().equals(b.runtime().orderState().anchor()),"each NPC has its own anchor");
            if(net.minecraftforge.fml.ModList.get().isLoaded("tacznpcs")){
                var legacy=RuntimeRegistry.createLegacy(h.getLevel()).entity();legacy.moveTo(at.x,at.y,at.z);h.getLevel().addFreshEntity(legacy);legacy.addTag("mode-batch");
                reject(h,source,"npc mode @e[tag=mode-batch,type=!minecraft:armor_stand] stand");legacy.discard();
            }
            a.discard();b.discard();other.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="mode-unified-permissions",timeoutTicks=50)
    public static void permissionSuggestionsAndOldCommandSurface(GameTestHelper h){
        ScenarioIsolation.begin(h);var n=npc(h,3.5,7.5);
        h.runAtTickTime(10,()->{
            var source=console(h).withPermission(2);var d=h.getLevel().getServer().getCommands().getDispatcher();
            for(String text:new String[]{"npc help","npc spawn","npc list","npc info "+n.npcName(),"npc mode "+n.npcName()+" assault","npc team "+n.npcName()+" red","npc equip "+n.npcName()+" tacz:ak47","npc ammo "+n.npcName()+" finite 1","npc debug diagnostics"})reject(h,source.withPermission(1),text);
            h.assertTrue(n.order().equals("HOLD_POSITION"),"permission1 changes nothing");
            h.assertTrue(d.getRoot().getChild("tacticalnpc")==null && (d.getRoot().getChild("spawn")==null || d.getRoot().getChild("spawn").getChild("npc")==null),"removed root/alias registrations absent");
            for(String text:new String[]{"npc order","npc faction","npc squad","npc hard","npc unfair","tacticalnpc hard","tacticalnpc unfair","spawn npc"})reject(h,source,text);
            h.assertTrue(suggestions(d,source,"npc ").containsAll(java.util.List.of("help","spawn","list","info","mode","team","equip","ammo","debug")),"sections complete");
            h.assertTrue(suggestions(d,source,"npc mode "+n.npcName()+" ").containsAll(java.util.List.of("assault","defend","patrol","stand")),"four modes complete");
            h.assertTrue(suggestions(d,source,"npc info "+n.npcName().substring(0,1)).contains(n.npcName()),"names complete");
            h.assertTrue(suggestions(d,source,"npc equip "+n.npcName()+" tacz:").contains("tacz:ak47"),"loaded weapon IDs complete");
            h.assertTrue(suggestions(d,source,"npc mode @e[ty").contains("type="),"vanilla selector options complete");
            h.assertTrue(net.minecraft.commands.synchronization.ArgumentTypeInfos.isClassRecognized(dev.zuma.tacticalnpc.command.NpcTargetArgument.class),"target argument has client command-tree serialization");
            var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try{
                @SuppressWarnings("unchecked") var sharedTree=(com.mojang.brigadier.tree.RootCommandNode<net.minecraft.commands.SharedSuggestionProvider>)(com.mojang.brigadier.tree.RootCommandNode<?>)d.getRoot();
                new net.minecraft.network.protocol.game.ClientboundCommandsPacket(sharedTree).write(buffer);
                var tree=new net.minecraft.network.protocol.game.ClientboundCommandsPacket(buffer).getRoot(net.minecraft.commands.CommandBuildContext.simple(h.getLevel().getServer().registryAccess(),net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
                var target=(com.mojang.brigadier.tree.ArgumentCommandNode<net.minecraft.commands.SharedSuggestionProvider,?>)tree.getChild("npc").getChild("mode").getChild("targets");
                int[] requests={0};
                var client=(net.minecraft.commands.SharedSuggestionProvider)java.lang.reflect.Proxy.newProxyInstance(net.minecraft.commands.SharedSuggestionProvider.class.getClassLoader(),new Class<?>[]{net.minecraft.commands.SharedSuggestionProvider.class},(proxy,method,args)->{
                    if(method.getName().equals("customSuggestion")){requests[0]++;return com.mojang.brigadier.suggestion.Suggestions.empty();}
                    throw new UnsupportedOperationException(method.getName());
                });
                h.assertTrue(target.getCustomSuggestions()!=null,"serialized client target has a suggestion provider");
                var clientDispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.SharedSuggestionProvider>(tree);
                var clientContext=new com.mojang.brigadier.context.CommandContextBuilder<>(clientDispatcher,client,tree,0).build("npc mode ");
                try{target.getCustomSuggestions().getSuggestions(clientContext,new com.mojang.brigadier.suggestion.SuggestionsBuilder("npc mode ",9)).join();}
                catch(CommandSyntaxException ex){h.fail(ex.toString());}
                h.assertTrue(requests[0]==1,"actual serialized client tree requests server NPC name completions");
            }finally{buffer.release();}
            run(h,source,"npc mode "+n.npcName()+" assault");run(h,source,"npc help");run(h,source,"npc list 1");reject(h,source,"npc list 1000000");
            n.discard();h.succeed();
        });
    }
    private static java.util.List<String> suggestions(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> d,CommandSourceStack source,String text){return d.getCompletionSuggestions(d.parse(text,source)).join().getList().stream().map(s->s.getText()).toList();}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="mode-unified-radius",timeoutTicks=50)
    public static void radiusUsesSphericalPositionsAcrossTeamsWithoutAim(GameTestHelper h){
        ScenarioIsolation.begin(h);var a=npc(h,6.5,7.5);var b=npc(h,7.5,7.5);var corner=npc(h,6.5,10.5);var edge=npc(h,7.6,7.5);var p=player(h);
        dev.zuma.tacticalnpc.runtime.NpcTeams.assign(a,"red");dev.zuma.tacticalnpc.runtime.NpcTeams.assign(b,"blue");
        h.runAtTickTime(10,()->{
            p.moveTo(a.getX()-3,a.getY(),a.getZ(),90,0); // Face away; use exact current feet height.
            for(int y=2;y<7;y++)h.setBlock(new BlockPos(5,y,7),Blocks.STONE);
            var source=p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            h.assertTrue(run(h,source,"npc mode 4 defend 12")==2,"radius includes all teams and inclusive boundary, excludes AABB corner and overlapping edge");
            h.assertTrue(a.order().equals("DEFEND_AREA") && b.order().equals("DEFEND_AREA") && corner.order().equals("HOLD_POSITION") && edge.order().equals("HOLD_POSITION"),"distance is to NPC feet position, not body intersection");
            h.assertTrue(run(h,source,"npc team 4 green")==2 && p.getTeam()==null,"radius never includes player");
            h.assertTrue(run(h,source,"npc ammo 4 finite 17")==2 && run(h,source,"npc equip 4 tacz:glock_17")==2 && run(h,source,"npc info 4")==2,"all operations share radius resolver");
            for(String value:new String[]{"0","128.1","129","-2","NaN"})reject(h,source,"npc mode "+value+" assault");
            run(h,source,"npc mode "+corner.npcName()+" patrol 64");h.assertTrue(corner.order().equals("PATROL"),"name ignores wall, facing and selection distance");
            a.discard();b.discard();corner.discard();edge.discard();p.discard();h.succeed();
        });
    }
    private static TacticalNpcEntity npc(GameTestHelper h,double x,double z) {
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(x,2,z));
        n.moveTo(at.x,at.y,at.z);n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);h.getLevel().addFreshEntity(n);return n;
    }
    private static FakePlayer player(GameTestHelper h) {
        var p=new FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"mode-player"));
        p.setGameMode(GameType.CREATIVE);p.setInvulnerable(true);var at=h.absoluteVec(new Vec3(3.5,2,7.5));
        p.moveTo(at.x,at.y,at.z,-90,0);p.setYHeadRot(-90);p.setYBodyRot(-90);h.getLevel().addNewPlayer(p);return p;
    }
    private static CommandSourceStack console(GameTestHelper h) {return h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withSuppressedOutput();}
    private static int run(GameTestHelper h,CommandSourceStack source,String text) {
        try {return h.getLevel().getServer().getCommands().getDispatcher().execute(text,source);}
        catch(CommandSyntaxException ex) {h.fail(text+": "+ex.getMessage());return 0;}
    }
    private static String reject(GameTestHelper h,CommandSourceStack source,String text) {
        try {h.getLevel().getServer().getCommands().getDispatcher().execute(text,source);h.fail("Expected rejection: "+text);return "";}
        catch(CommandSyntaxException ex) {return ex.getMessage();}
    }
}
