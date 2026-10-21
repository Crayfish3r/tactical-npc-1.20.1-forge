package dev.zuma.tacticalnpc;

import com.tacz.guns.api.item.IGun;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class StandTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="stand-05-report",timeoutTicks=70)
    public static void deliveredReportIsProcessedBeforeEndingCombatPhase(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,0);n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,0);n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var p=player(h,42.5,32.5);var q=player(h,32.5,42.5);UUID squad=UUID.randomUUID();n.setSquad(squad);
        var scout=NpcEntities.NPC.get().create(h.getLevel());scout.moveTo(q.getX(),q.getY(),q.getZ()+2,180,0);scout.setYHeadRot(180);scout.setNoAi(true);scout.setSquad(squad);h.getLevel().addFreshEntity(scout);UUID reporter=scout.getUUID();
        for(int x=29;x<=36;x++)for(int y=2;y<=6;y++)h.setBlock(new net.minecraft.core.BlockPos(x,y,38),net.minecraft.world.level.block.Blocks.STONE);
        h.runAtTickTime(5,()->{
            long t=h.getLevel().getGameTime();var board=dev.zuma.tacticalnpc.combat.SquadRuntime.of(h.getLevel());
            var sensor=new dev.zuma.tacticalnpc.combat.VisibilitySensor();var aim=sensor.aimPoint(q);
            h.assertTrue(sensor.valid(scout,q,48) && sensor.inFov(scout,q) && sensor.los(scout,aim,true)==dev.zuma.tacticalnpc.combat.VisibilitySensor.Los.CLEAR,"actual supported scout observes target from its side of wall");
            board.heartbeat(n.runtime().squadId(),reporter,new Vector3(scout.getX(),scout.getY(),scout.getZ()),t);
            board.report(n.runtime().squadId(),reporter,new Observation(q.getUUID(),new Vector3(aim.x,aim.y,aim.z),new Vector3(0,0,0),t,t+80,Observation.Source.VISION,1,0,true),t);
        });
        h.runAtTickTime(10,()->{n.invulnerableTime=0;n.hurt(h.getLevel().damageSources().playerAttack(p),1);h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.COMBAT,"real damage begins local contact");});
        h.runAtTickTime(15,()->{
            p.discard();n.controller().tick();var o=n.controller().observation();
            h.assertTrue(o!=null && o.subject().equals(q.getUUID()),"delayed legitimate report is actually delivered in this tick");
            h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.COMBAT && n.runtime().orderState().radius()==6,"delivered contact must prevent premature DEFEND32");
            q.discard();scout.discard();n.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="stand-01-wait",timeoutTicks=170)
    public static void waitHoldsDespiteSoundAndRejectedShot(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,0);n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,0);var origin=n.position();
        var p=player(h,40.5,32.5);
        h.runAtTickTime(10,()->n.controller().hear(p));
        h.onEachTick(()->{
            h.assertTrue(n.position().subtract(origin).horizontalDistance()<.1,"WAIT cannot patrol or move toward heard/seen contact");
            h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.WAIT,"sound/detection/empty gun cannot trigger COMBAT");
        });
        h.runAtTickTime(140,()->{h.assertTrue(ProjectileProbe.count(n.getUUID())==0,"no genuine round with empty finite ammo");n.discard();p.discard();h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="stand-02-native",timeoutTicks=500)
    public static void nativeRoundThenLocalCombatThenOriginalDefendPatrol(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,30);var p=player(h,42.5,32.5);var anchor=n.runtime().orderState().anchor();
        int[] first={-1},ended={-1};double[] patrol={0};
        h.onEachTick(()->{
            int tick=(int)h.getTick();int rounds=ProjectileProbe.count(n.getUUID());
            if(first[0]<0 && rounds>0){first[0]=tick;h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.COMBAT,"first real native projectile triggers COMBAT");}
            if(first[0]>=0 && tick<first[0]+45){
                h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.COMBAT && n.runtime().orderState().radius()==6,"live contact never expands local zone");
                h.assertTrue(new DefendTerritory(anchor,6,DefendTerritory.clearance(n.getBbWidth())).contains(new Vector3(n.getX(),n.getY(),n.getZ())),"entire moving body remains in XZ radius6");
            }
            if(first[0]>=0 && tick==first[0]+45)p.discard();
            if(n.order().equals("DEFEND_AREA")) {
                if(ended[0]<0)ended[0]=tick;
                h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.NONE && n.runtime().orderState().radius()==32 && n.runtime().orderState().anchor().equals(anchor),"ordinary DEFEND32 preserves initial anchor");
                patrol[0]=Math.max(patrol[0],n.position().distanceTo(new Vec3(anchor.x(),anchor.y(),anchor.z())));
            }
        });
        h.runAtTickTime(450,()->{h.assertTrue(first[0]>0 && ended[0]>=first[0]+45 && ended[0]<first[0]+65 && patrol[0]>2,"real firing, prompt death transition and actual defend patrol exercised");n.discard();p.discard();h.succeed();});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="stand-03-damage",timeoutTicks=100)
    public static void actualDamageTriggersButCancellationAndMobDoNot(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,0);n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,0);var p=player(h,42.5,32.5);
        h.runAtTickTime(10,()->{
            n.addTag("stage07-cancel-damage");n.hurt(h.getLevel().damageSources().playerAttack(p),1);n.removeTag("stage07-cancel-damage");
            h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.WAIT,"cancelled damage never starts phase");
            n.invulnerableTime=0;n.hurt(h.getLevel().damageSources().generic(),1);
            h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.WAIT,"unknown damage never starts phase");
            n.invulnerableTime=0;n.hurt(h.getLevel().damageSources().playerAttack(p),1);
            h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.COMBAT,"actual supported attacker starts local combat immediately");
        });
        h.runAtTickTime(65,()->{
            h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.COMBAT,"contact cannot end on arbitrary short timer");
            n.setOrder(OrderState.Kind.ASSAULT,24);h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.NONE,"new mode cancels phase");
            n.moveTo(n.getX()+2,n.getY(),n.getZ());n.setOrder(OrderState.Kind.HOLD_POSITION,27);
            h.assertTrue(n.standPhase()==TacticalNpcEntity.StandPhase.WAIT && n.runtime().orderState().anchor().x()==n.getX() && n.runtime().orderState().radius()==6,"repeat stand resets anchor/phase and ignores old radius");
            n.discard();p.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="wide_arena",batch="stand-04-nbt",timeoutTicks=60)
    public static void allPhasesAndOldHoldMigrateWithoutLiveContact(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,17);n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,23);n.setSquad(UUID.randomUUID());
        for(int phase=0;phase<3;phase++) {
            if(phase==1)n.standShot();if(phase==2)n.endStandContact();
            var data=new CompoundTag();n.save(data);var copy=(TacticalNpcEntity)EntityType.loadEntityRecursive(data,h.getLevel(),e->e);
            h.assertTrue(copy.standPhase()==n.standPhase() && copy.runtime().orderState().equals(n.runtime().orderState()),"phase/anchor/radius survive actual NBT roundtrip");
            h.assertTrue(copy.serviceId().equals(n.serviceId()) && copy.runtime().squadId().equals(n.runtime().squadId()) && !copy.standDamageContact(),"identity/squad retained; live damage contact omitted");
            h.assertTrue(IGun.getIGunOrNull(copy.getMainHandItem()).getDummyAmmoAmount(copy.getMainHandItem())==23,"finite reserve retained");
        }
        n.setOrder(OrderState.Kind.HOLD_POSITION,64);var saved=new CompoundTag();n.save(saved);
        for(int schema:new int[]{1,2,3}){
            var data=saved.copy();data.getCompound("tacticalnpc").putInt("schema",schema);data.getCompound("tacticalnpc").remove("standPhase");
            var old=(TacticalNpcEntity)EntityType.loadEntityRecursive(data,h.getLevel(),e->e);
            h.assertTrue(!old.neutral() && old.standPhase()==TacticalNpcEntity.StandPhase.WAIT && old.runtime().orderState().radius()==6,"legacy HOLD migrates to fixed stand WAIT");
        }
        n.discard();h.succeed();
    }
    private static TacticalNpcEntity npc(GameTestHelper h,int ammo) {
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(32.5,2,32.5));n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.setOrder(OrderState.Kind.HOLD_POSITION,24);
        var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,ammo);gun.setBulletInBarrel(stack,ammo>0);
        n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);return n;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,double x,double z) {
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"stand-test"));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);
        var at=h.absoluteVec(new Vec3(x,2,z));p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);return p;
    }
}
