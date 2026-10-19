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
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="mode-01-aliases",timeoutTicks=60)
    public static void aliasesShareStatePersistenceAndInvalidation(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,3.5,7.5);
        var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=IGun.getIGunOrNull(stack);
        gun.setCurrentAmmoCount(stack,5);gun.setBulletInBarrel(stack,true);
        n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.runtime().onEquip(stack);
        n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,17);n.addTag(RuntimeRegistry.TAG);
        h.runAtTickTime(10,()->{
            var source=console(h);String id=n.getUUID().toString();var controller=n.controller();
            h.assertTrue(controller!=null && controller.decision()!=null,"existing single controller active");
            var dest=h.absoluteVec(new Vec3(11.5,2,7.5));
            h.assertTrue(n.getNavigation().moveTo(dest.x,dest.y,dest.z,1) && !n.getNavigation().isDone(),"real old navigation exists");
            long generation=controller.generation(),cooldown=n.runtime().operator().getSynShootCoolDown();
            int rounds=gun.getCurrentAmmoCount(stack),reserve=gun.getDummyAmmoAmount(stack);boolean chamber=gun.hasBulletInBarrel(stack);
            run(h,source,"tacticalnpc order "+id+" ASSAULT 19");var assault=n.runtime().orderState();
            run(h,source,"npc mode attack "+id);h.assertTrue(assault.equals(n.runtime().orderState()),"attack is the same ASSAULT/order/anchor/radius");
            h.assertTrue(controller.generation()>generation && controller.decision()==null && n.getNavigation().isDone(),"old decisions and actual path invalidated");
            run(h,source,"tacticalnpc order "+id+" DEFEND_AREA 12");var defend=n.runtime().orderState();
            run(h,source,"npc mode defend 12 "+id);h.assertTrue(defend.equals(n.runtime().orderState()),"defend shares legacy DEFEND_AREA state");
            run(h,source,"tacticalnpc order "+id+" HOLD_POSITION 27");var stand=n.runtime().orderState();
            run(h,source,"npc mode stand "+id);h.assertTrue(stand.equals(n.runtime().orderState()),"stand shares HOLD_POSITION and retains radius");
            h.assertTrue(gun.getCurrentAmmoCount(stack)==rounds && gun.getDummyAmmoAmount(stack)==reserve && gun.hasBulletInBarrel(stack)==chamber && n.runtime().operator().getSynShootCoolDown()==cooldown,"mode/order commands preserve native magazine/reserve/chamber/cooldown");
            h.assertTrue(n.controller()==controller,"aliases do not install a second controller");
            run(h,source,"tacticalnpc order "+id+" PATROL 9");h.assertTrue(n.runtime().orderState().kind()==OrderState.Kind.PATROL,"PATROL remains independent");
            run(h,source,"npc mode defend 14 "+id);var expected=n.runtime().orderState();
            var saved=new CompoundTag();n.save(saved);
            h.assertTrue(saved.getCompound("tacticalnpc").getInt("schema")==3 && !saved.getCompound("tacticalnpc").contains("mode"),"schema 3 adds neutral policy without duplicating the order/mode source of truth");
            var restored=(TacticalNpcEntity)EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);
            h.assertTrue(restored!=null && expected.equals(restored.runtime().orderState()) && n.serviceId().equals(restored.serviceId()),"new command mode persists via actual entity save/load");
            n.discard();restored.discard();h.succeed();
        });
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="mode-02-targets",timeoutTicks=60)
    public static void validateWholeBatchBeforeMutationAndRequireConsoleTargets(GameTestHelper h) {
        ScenarioIsolation.begin(h);var a=npc(h,3.5,7.5);var b=npc(h,10.5,10.5);
        var at=h.absoluteVec(new Vec3(8.5,2,7.5));var other=new ArmorStand(h.getLevel(),at.x,at.y,at.z);h.getLevel().addFreshEntity(other);
        a.addTag("mode-batch");b.addTag("mode-batch");other.addTag("mode-batch");
        h.runAtTickTime(10,()->{
            var source=console(h);var oldA=a.runtime().orderState();var oldB=b.runtime().orderState();
            reject(h,source,"npc mode attack @e[tag=mode-batch]");
            h.assertTrue(oldA.equals(a.runtime().orderState()) && oldB.equals(b.runtime().orderState()),"mixed unsupported batch changes no valid recipients");
            reject(h,source,"npc mode defend 3 "+a.getUUID());reject(h,source,"npc mode defend 129 "+a.getUUID());
            reject(h,source,"npc mode defend NaN "+a.getUUID());reject(h,source,"npc mode attack "+UUID.randomUUID());
            h.assertTrue(oldA.equals(a.runtime().orderState()),"invalid radius/targets do not mutate state");
            for(String text:new String[]{"npc mode attack","npc mode defend 16","npc mode stand"})
                h.assertTrue(reject(h,source,text).contains("targets"),"console without targets has explicit actionable error");
            h.assertTrue(run(h,source,"npc mode defend 16 @e[type=tacticalnpcai:npc,tag=mode-batch]")==2,"console selector affects both supported NPCs");
            h.assertTrue(a.runtime().orderState().kind()==OrderState.Kind.DEFEND_AREA && b.runtime().orderState().radius()==16,"batch uses existing order state on each entity");
            if(net.minecraftforge.fml.ModList.get().isLoaded("tacznpcs")) {
                var legacy=RuntimeRegistry.createLegacy(h.getLevel()).entity();legacy.moveTo(at.x,at.y,at.z);h.getLevel().addFreshEntity(legacy);
                legacy.addTag("mode-batch");var before=a.runtime().orderState();
                reject(h,source,"npc mode stand "+legacy.getUUID());
                reject(h,source,"npc mode stand @e[tag=mode-batch,type=!minecraft:armor_stand]");
                h.assertTrue(before.equals(a.runtime().orderState()),"optional legacy is rejected before mutating independent recipients");legacy.discard();
            }
            a.discard();b.discard();other.discard();h.succeed();
        });
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="mode-03-permission",timeoutTicks=50)
    public static void permissionSuggestionsAndOldCommandSurface(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,3.5,7.5);
        h.runAtTickTime(10,()->{
            var source=console(h).withPermission(2);var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();
            reject(h,source.withPermission(1),"npc mode attack "+n.getUUID());
            reject(h,source.withPermission(1),"tacticalnpc order "+n.getUUID()+" ASSAULT 24");
            h.assertTrue(n.order().equals("HOLD_POSITION"),"permission 1 cannot mutate either interface");
            run(h,source,"npc mode attack "+n.getUUID());h.assertTrue(n.order().equals("ASSAULT"),"permission 2 is sufficient");
            var modes=dispatcher.getCompletionSuggestions(dispatcher.parse("npc mode ",source)).join().getList().stream().map(s->s.getText()).toList();
            h.assertTrue(modes.containsAll(java.util.List.of("attack","defend","stand")),"mode suggestions expose all three literals");
            var root=dispatcher.getRoot().getChild("tacticalnpc");
            for(String name:new String[]{"spawn","spawnlegacy","order","equip","ammo","faction","squad","hard","diagnostics"})
                h.assertTrue(root.getChild(name)!=null,"old command remains registered: "+name);
            var orders=dispatcher.getCompletionSuggestions(dispatcher.parse("tacticalnpc order "+n.getUUID()+" ",source)).join().getList().stream().map(s->s.getText()).toList();
            h.assertTrue(orders.containsAll(java.util.List.of("ASSAULT","DEFEND_AREA","HOLD_POSITION","PATROL")),"old order suggestions including PATROL remain");
            n.discard();h.succeed();
        });
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="mode-04-aim",timeoutTicks=50)
    public static void aimSelectsHitInsteadOfNearestAndRespectsWall(GameTestHelper h) {
        ScenarioIsolation.begin(h);var near=npc(h,5.5,10.5);var hit=npc(h,11.5,7.5);var player=player(h);
        h.runAtTickTime(10,()->{
            var source=player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            run(h,source,"npc mode attack");
            h.assertTrue(hit.order().equals("ASSAULT") && near.order().equals("HOLD_POSITION"),"aim selects farther intersected NPC, not closer off-axis NPC");
            for(int y=2;y<7;y++)h.setBlock(new BlockPos(7,y,7),Blocks.STONE);
            reject(h,source,"npc mode stand");h.assertTrue(hit.order().equals("ASSAULT"),"wall blocks command selection");
            for(int y=2;y<7;y++)h.setBlock(new BlockPos(7,y,7),Blocks.AIR);
            player.setYRot(90);player.setYHeadRot(90);player.setXRot(0);reject(h,source,"npc mode defend 16");
            h.assertTrue(near.order().equals("HOLD_POSITION") && hit.order().equals("ASSAULT"),"empty aim has no nearest fallback");
            near.discard();hit.discard();player.discard();h.succeed();
        });
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="wide_arena",batch="mode-05-range",timeoutTicks=50)
    public static void aimRangeAndUnsupportedFrontEntity(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,38.5,7.5);var player=player(h);
        h.runAtTickTime(10,()->{
            var source=player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            reject(h,source,"npc mode attack");h.assertTrue(n.order().equals("HOLD_POSITION"),"35-block target is outside aim range");
            var at=h.absoluteVec(new Vec3(34.5,2,7.5));n.moveTo(at.x,at.y,at.z);
            run(h,source,"npc mode defend 4");h.assertTrue(n.order().equals("DEFEND_AREA") && n.runtime().orderState().radius()==4,"31-block target and minimum defend radius accepted");
            run(h,source,"npc mode defend 64");h.assertTrue(n.runtime().orderState().radius()==64,"64-block defend radius remains accepted");
            var front=h.absoluteVec(new Vec3(6.5,2,7.5));var stand=new ArmorStand(h.getLevel(),front.x,front.y,front.z);h.getLevel().addFreshEntity(stand);
            reject(h,source,"npc mode attack");h.assertTrue(n.order().equals("DEFEND_AREA"),"unsupported entity under crosshair does not select NPC behind it");
            stand.discard();n.discard();player.discard();h.succeed();
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
