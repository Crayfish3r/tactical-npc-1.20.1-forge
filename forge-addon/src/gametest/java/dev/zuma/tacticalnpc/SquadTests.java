package dev.zuma.tacticalnpc;
import dev.zuma.tacticalnpc.combat.SquadRuntime;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class SquadTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="squad-01-report-lifecycle",timeoutTicks=160)
    public static void delayedFrozenReportsAndLeaderDeath(GameTestHelper h) {
        ScenarioIsolation.begin(h);UUID squad=UUID.randomUUID();
        var leader=npc(h,new Vec3(3.5,2,4.5),squad);var receiver=npc(h,new Vec3(3.5,2,12.5),squad);
        var p=player(h,new Vec3(13.5,2,4.5));
        for(int y=1;y<6;y++)for(int z=7;z<16;z++)h.setBlock(new BlockPos(7,y,z),Blocks.STONE);
        Vector3[] frozen={null};var board=SquadRuntime.of(h.getLevel());
        h.runAtTickTime(8,()->h.assertTrue(receiver.controller().observation()==null,"radio is not instant omniscience"));
        h.runAtTickTime(45,()->{
            var report=receiver.controller().observation();
            h.assertTrue(report!=null && report.source()==Observation.Source.SQUAD && !report.visible() && report.tick()<h.getLevel().getGameTime(),"receiver gets delayed real observation behind wall: "+report);
            frozen[0]=report.position();Vec3 hidden=h.absoluteVec(new Vec3(14,2,14));p.moveTo(hidden.x,hidden.y,hidden.z);
            h.assertTrue(board.claim(squad,leader.getUUID(),new Vector3(5,1,5),h.getLevel().getGameTime()),"claim owned by leader");
        });
        h.runAtTickTime(75,()->{
            var report=receiver.controller().observation();h.assertTrue(report!=null && report.position().equals(frozen[0]),"radio does not track hidden new player coordinates");
            leader.discard();
        });
        h.runAtTickTime(90,()->{
            h.assertTrue(board.members(squad)==1 && board.role(squad,receiver.getUUID())==SquadBlackboard.Role.ANCHOR,"dead leader releases membership and roles");
            h.assertTrue(board.available(squad,receiver.getUUID(),new Vector3(5,1,5),h.getLevel().getGameTime()),"dead leader claim released");
            receiver.removeTag(RuntimeRegistry.TAG);
        });
        h.runAtTickTime(100,()->{h.assertTrue(board.members(squad)==0,"tag ownership release clears squad references");receiver.discard();p.discard();h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="squad-02-coordinated-1v4",timeoutTicks=220)
    public static void coordinatedFourNativeFire(GameTestHelper h) {
        ScenarioIsolation.begin(h);UUID group=UUID.randomUUID();var actors=new ArrayList<TacticalNpcEntity>();
        for(int i=0;i<4;i++)actors.add(npc(h,new Vec3(3.5,2,1.5+4*i),group));
        var p=player(h,new Vec3(13.5,2,7.5));
        h.runAtTickTime(180,()->{
            var board=SquadRuntime.of(h.getLevel());long flankers=actors.stream().filter(n->board.role(group,n.getUUID())==SquadBlackboard.Role.FLANKER).count();
            h.assertTrue(flankers==1 && board.members(group)==4,"bounded stable squad roles");
            for(var n:actors)h.assertTrue(n.controller()!=null && !n.controller().failed() && ProjectileProbe.count(n.getUUID())>0 && n.getHealth()==n.getMaxHealth(),"each coordinated NPC fires real native rounds without allied damage");
            actors.forEach(TacticalNpcEntity::discard);h.assertTrue(board.members(group)==0,"squad cleanup after all actors leave");p.discard();h.succeed();
        });
    }
    private static TacticalNpcEntity npc(GameTestHelper h,Vec3 local,UUID squad) {
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(local);n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.setSquad(squad);n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var stack=GunSupport.create(new ResourceLocation("tacz","ak47"));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,30);gun.setBulletInBarrel(stack,true);n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);return n;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,Vec3 local) {
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"squad-test"));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);var at=h.absoluteVec(local);p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);return p;
    }
}
