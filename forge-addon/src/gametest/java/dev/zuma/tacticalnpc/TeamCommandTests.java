package dev.zuma.tacticalnpc;

import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.*;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class TeamCommandTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="team-command-01-spawn",timeoutTicks=45)
    public static void twoCommandsCreateArmedOpposingTeamsWithSafePlacement(GameTestHelper h){
        ScenarioIsolation.begin(h);h.runAtTickTime(5,()->{
            var source=source(h,22.5,32.5);
            h.assertTrue(run(h,source,"npc spawn team \"РљСЂР°СЃРЅР°СЏ РєРѕРјР°РЅРґР°\" count 4")==4,"one command creates four red NPCs");
            h.assertTrue(run(h,source(h,42.5,32.5),"npc spawn team blue count 4")==4,"new root creates second group");
            var all=npcs(h);h.assertTrue(all.size()==8,"exactly two groups of four");
            for(var n:all){
                h.assertTrue(!n.neutral() && n.order().equals("ASSAULT") && n.getTags().contains(RuntimeRegistry.TAG),"spawn is active attack without another command");
                h.assertTrue(com.tacz.guns.api.item.IGun.getIGunOrNull(n.getMainHandItem())!=null && n.ammoPolicy()==TacticalNpcEntity.AmmoPolicy.INFINITE,"native AK47/ammo policy retained");
                h.assertTrue(n.runtime().squadId()!=null,"team has automatic coordination");
                for(var other:all)if(n!=other){h.assertFalse(n.getBoundingBox().intersects(other.getBoundingBox()),"spawn bodies never overlap");h.assertTrue(n.runtime().allied(other)==n.faction().equals(other.faction()),"team determines alliance");}
                for(var other:all)if(n!=other && n.runtime().allied(other))h.assertTrue(n.distanceToSqr(other)>=16,"group placement leaves at least four blocks between members");
            }
            all.forEach(Entity::discard);h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="team-command-02-errors",timeoutTicks=45)
    public static void permissionCountAndInvalidNamesDoNotCreateAnything(GameTestHelper h){
        ScenarioIsolation.begin(h);h.runAtTickTime(5,()->{
            var source=source(h,32.5,32.5);
            reject(h,source.withPermission(1),"npc spawn team red count 4");reject(h,source,"npc spawn count 0");reject(h,source,"npc spawn count 17");
            h.assertTrue(run(h,source,"npc spawn team \"   \"")==0,"blank name rejected before creation");
            h.assertTrue(run(h,source,"npc spawn team "+"x".repeat(65))==0,"long name rejected before creation");
            reject(h,source,"npc team red");reject(h,source,"npc info");
            h.assertTrue(npcs(h).isEmpty() && h.getLevel().getScoreboard().getPlayerTeam("red")==null,"invalid commands leave entities and teams untouched");h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="team-command-03-selection",timeoutTicks=50)
    public static void namedNpcExplicitPlayerAndAtomicMixedSelector(GameTestHelper h){
        ScenarioIsolation.begin(h);var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(32.5,2,32.5));n.moveTo(at.x,at.y,at.z);n.setNoAi(true);h.getLevel().addFreshEntity(n);
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"team-cmd-player"));p.setGameMode(GameType.CREATIVE);p.moveTo(at.x,at.y,at.z-4,0,0);h.getLevel().addNewPlayer(p);
        var mob=EntityType.ZOMBIE.create(h.getLevel());mob.moveTo(at.x+8,at.y,at.z);mob.setNoAi(true);h.getLevel().addFreshEntity(mob);n.addTag("team-mixed");mob.addTag("team-mixed");
        h.runAtTickTime(8,()->{
            var source=p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            h.assertTrue(run(h,source,"npc team "+n.npcName()+" red")==1 && n.faction().equals("red"),"explicit NPC name");
            h.assertTrue(run(h,source,"npc team @s red")==1 && n.runtime().allied(p),"explicit player joins same native team");
            reject(h,source.withPermission(1),"npc team @s blue");
            reject(h,source,"npc team @e[tag=team-mixed] blue");h.assertTrue(n.faction().equals("red"),"mixed selector is atomic");
            long draw=n.runtime().operator().getDataHolder().drawTimestamp;
            h.assertTrue(run(h,source,"npc info "+n.npcName())==1 && draw==n.runtime().operator().getDataHolder().drawTimestamp,"info never draws");
            h.assertTrue(run(h,source,"npc team "+n.npcName()+" clear")==1 && n.getTeam()==null && n.runtime().squadId()==null,"clear removes membership");
            reject(h,source,"npc team blue");reject(h,source,"npc info");
            NpcTeams.assign(p,null);n.discard();p.discard();mob.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="team-command-04-partial",timeoutTicks=45)
    public static void limitedSafeSpaceReportsPartialCountWithoutUnsafeSpawns(GameTestHelper h){
        ScenarioIsolation.begin(h);for(int x=16;x<=48;x++)for(int z=16;z<=48;z++)h.setBlock(new BlockPos(x,1,z),Blocks.MAGMA_BLOCK);
        h.setBlock(new BlockPos(28,1,32),Blocks.STONE);
        h.runAtTickTime(5,()->{
            h.assertTrue(run(h,source(h,32.5,32.5),"npc spawn team partial count 4")==1,"only one safe position creates one NPC");
            var all=npcs(h);h.assertTrue(all.size()==1,"no extra or overlapping bodies");
            h.assertTrue(!h.getLevel().getBlockState(all.get(0).blockPosition().below()).is(Blocks.MAGMA_BLOCK),"hazard never accepted");
            all.forEach(Entity::discard);h.succeed();
        });
    }
    private static CommandSourceStack source(GameTestHelper h,double x,double z){return h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPermission(2).withPosition(h.absoluteVec(new Vec3(x,2,z))).withSuppressedOutput();}
    private static java.util.List<TacticalNpcEntity> npcs(GameTestHelper h){var result=new java.util.ArrayList<TacticalNpcEntity>();h.getLevel().getAllEntities().forEach(e->{if(e instanceof TacticalNpcEntity n)result.add(n);});return result;}
    private static int run(GameTestHelper h,CommandSourceStack source,String command){try{return h.getLevel().getServer().getCommands().getDispatcher().execute(command,source);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){throw new GameTestAssertException(command+": "+ex.getMessage());}}
    private static void reject(GameTestHelper h,CommandSourceStack source,String command){boolean rejected=false;try{h.getLevel().getServer().getCommands().getDispatcher().execute(command,source);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){rejected=true;}h.assertTrue(rejected,"command rejected: "+command);}
}
