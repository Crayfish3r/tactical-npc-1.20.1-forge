package dev.zuma.tacticalnpc;

import com.tacz.guns.api.item.IGun;
import dev.zuma.tacticalnpc.combat.CombatTargets;
import dev.zuma.tacticalnpc.combat.SquadRuntime;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.config.ServerConfig;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid=TacticalNpcAiMod.ID)
public final class NeutralTests {
    @SubscribeEvent public static void cancelHurt(LivingHurtEvent e) {
        if(e.getEntity().getTags().contains("stage07-cancel-hurt"))e.setCanceled(true);
    }
    @SubscribeEvent public static void cancelDamage(LivingDamageEvent e) {
        if(e.getEntity().getTags().contains("stage07-cancel-damage"))e.setCanceled(true);
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="neutral-01-spawn",timeoutTicks=70)
    public static void aliasesSafePlacementPermissionsAndOldEquipment(GameTestHelper h) {
        ScenarioIsolation.begin(h);
        h.runAtTickTime(5,()->{
            var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();
            dispatcher.register(net.minecraft.commands.Commands.literal("spawn").then(net.minecraft.commands.Commands.literal("stage07-other").executes(c->7)));
            var at=h.absoluteVec(new Vec3(6.5,2,7.5));
            var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPosition(at).withSuppressedOutput();
            h.assertTrue(run(h,source.withPermission(0),"spawn stage07-other")==7,"unrelated /spawn child remains usable");
            boolean denied=false;try{dispatcher.execute("spawn npc",source.withPermission(1));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){denied=true;}
            h.assertTrue(denied,"spawn npc requires permission 2");
            h.setBlock(new BlockPos(9,1,7),Blocks.MAGMA_BLOCK);
            h.assertTrue(run(h,source,"spawn npc")==1 && run(h,source,"tacticalnpc spawn")==1,"both aliases use safe independent spawn");
            var all=new java.util.ArrayList<TacticalNpcEntity>();
            h.getLevel().getAllEntities().forEach(e->{if(e instanceof TacticalNpcEntity n)all.add(n);});
            h.assertTrue(all.size()==2,"one independent NPC per alias");
            for(var n:all) {
                h.assertTrue(n.neutral() && n.reactiveAttacker()==null && h.getLevel().noCollision(n),"safe neutral, no reactive identity");
                h.assertFalse(h.getLevel().getBlockState(n.blockPosition().below()).is(Blocks.MAGMA_BLOCK),"hazard candidate skipped");
                h.assertTrue(IGun.getIGunOrNull(n.getMainHandItem())!=null,"AK loadout preserved");
            }
            var n=all.get(0);String id=n.getUUID().toString();
            h.assertTrue(run(h,source,"tacticalnpc equip "+id+" tacz:glock_17")==1,"old equip");
            h.assertTrue(run(h,source,"tacticalnpc ammo "+id+" finite 7")==1,"old ammo");
            h.assertTrue(n.neutral() && n.ammoPolicy()==TacticalNpcEntity.AmmoPolicy.FINITE,"equipment does not activate neutral");
            h.assertTrue(run(h,source,"npc mode attack "+id)==1 && !n.neutral(),"explicit mode activates same controller");
            all.forEach(Entity::discard);h.succeed();
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="neutral-02-idle",timeoutTicks=180)
    public static void proximitySoundAndSquadCannotStartCombat(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"ak47",30);var p=player(h,11.5,7.5);
        UUID squad=UUID.randomUUID(),reporter=UUID.randomUUID();n.setSquad(squad);
        h.runAtTickTime(10,()->{
            n.controller().hear(p);
            var board=SquadRuntime.of(h.getLevel());long tick=h.getLevel().getGameTime();
            board.heartbeat(squad,reporter,new Vector3(n.getX(),n.getY(),n.getZ()),tick);
            board.report(squad,reporter,new Observation(p.getUUID(),new Vector3(p.getX(),p.getEyeY(),p.getZ()),new Vector3(0,0,0),tick,tick+80,Observation.Source.VISION,1,0,true),tick);
        });
        h.runAtTickTime(130,()->{
            h.assertTrue(n.controller().shots()==0 && ProjectileProbe.count(n.getUUID())==0,"neutral never shoots first");
            h.assertTrue(n.controller().observation()==null && n.controller().contactId()==null && n.reactiveAttacker()==null,"no visual/sound/report target");
            h.assertFalse(n.controller().allowNativeFire(),"native gate independently closed");finish(h,n,p);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="neutral-03-melee",timeoutTicks=160)
    public static void realMeleeReactsWithoutDelayThenDeathReturnsNeutral(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"ak47",30);var p=player(h,11.5,7.5);var third=player(h,3.5,1.5);
        n.setYRot(90);n.setYHeadRot(90);
        h.runAtTickTime(15,()->{
            float health=n.getHealth();hit(n,h.getLevel().damageSources().playerAttack(p));
            h.assertTrue(n.getHealth()<health && n.reactiveTo(p.getUUID()),"real server health loss authenticates attacker");
            h.assertFalse(CombatTargets.eligible(n,third),"third player cannot replace attacker");
        });
        h.runAtTickTime(17,()->{
            h.assertTrue(p.getUUID().equals(n.controller().contactId()),"rear attacker visible without acquisition/perception wait");
            h.assertTrue(n.getYHeadRot()!=90 && Math.abs(net.minecraft.util.Mth.wrapDegrees(n.getYHeadRot()-90))<=ServerConfig.TURN.get()*3+.01,"turn starts promptly and remains bounded");
        });
        h.runAtTickTime(105,()->{
            h.assertTrue(ProjectileProbe.count(n.getUUID())>0,"native response creates projectiles");p.setHealth(0);
        });
        h.runAtTickTime(115,()->{
            h.assertTrue(n.neutral() && n.reactiveAttacker()==null && n.controller().observation()==null,"dead attacker ends personal contact");
            h.assertFalse(n.controller().allowNativeFire(),"queued rounds forbidden after contact invalidation");finish(h,n,p,third);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="neutral-04-allied",timeoutTicks=70)
    public static void actualAlliedDamageIsPersonalAndNewAttackerReplacesIt(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"ak47",30);var ally=NpcEntities.NPC.get().create(h.getLevel());
        var at=h.absoluteVec(new Vec3(11.5,2,7.5));ally.moveTo(at.x,at.y,at.z);h.getLevel().addFreshEntity(ally);
        var p=player(h,3.5,1.5);
        h.runAtTickTime(10,()->{
            h.assertTrue(n.runtime().allied(ally),"same faction fixture");
            hit(n,h.getLevel().damageSources().mobAttack(ally));
            h.assertTrue(n.reactiveTo(ally.getUUID()) && CombatTargets.eligible(n,ally),"real allied damage permits narrow response");
            h.assertTrue(n.faction().equals(ally.faction()) && n.runtime().allied(ally),"global faction/alliance unchanged");
            hit(n,h.getLevel().damageSources().playerAttack(p));
            h.assertTrue(n.reactiveTo(p.getUUID()) && !CombatTargets.eligible(n,ally),"actual new source replaces old personal hostility");
            finish(h,n,ally,p);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="neutral-05-reject",timeoutTicks=70)
    public static void cancelledZeroUnknownSelfAndMobNeverAuthorizeRetaliation(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"ak47",30);var p=player(h,11.5,7.5);
        h.runAtTickTime(10,()->{
            for(String tag:new String[]{"stage07-cancel-hurt","stage07-cancel-damage"}) {
                float health=n.getHealth();n.addTag(tag);hit(n,h.getLevel().damageSources().playerAttack(p));n.removeTag(tag);
                h.assertTrue(n.getHealth()==health && n.reactiveAttacker()==null,"cancelled "+tag+" has no reactive side effect");
            }
            n.invulnerableTime=0;n.hurt(h.getLevel().damageSources().playerAttack(p),0);
            hit(n,h.getLevel().damageSources().generic());
            hit(n,h.getLevel().damageSources().mobAttack(n));
            var mob=h.spawn(EntityType.ZOMBIE,new BlockPos(12,2,12));mob.setNoAi(true);
            hit(n,h.getLevel().damageSources().mobAttack(mob));
            var orphan=new Arrow(h.getLevel(),n.getX()+2,n.getEyeY(),n.getZ());
            hit(n,h.getLevel().damageSources().arrow(orphan,p));
            h.assertTrue(n.reactiveAttacker()==null && n.controller().observation()==null,"zero/unknown/self/mob/unowned projectile rejected despite other real damage");
            finish(h,n,p,mob);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="neutral-06-projectile",timeoutTicks=90)
    public static void actualArrowCollisionUsesOwner(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"ak47",30);var p=player(h,11.5,7.5);float health=n.getHealth();
        h.runAtTickTime(10,()->{
            var arrow=new Arrow(h.getLevel(),p);arrow.moveTo(n.getX()+4,n.getY()+1,n.getZ());arrow.setNoGravity(true);
            arrow.shoot(-1,0,0,.8f,0);h.getLevel().addFreshEntity(arrow);
        });
        h.runAtTickTime(30,()->{
            h.assertTrue(n.getHealth()<health && n.reactiveTo(p.getUUID()),"physical arrow collision identifies genuine projectile owner");finish(h,n,p);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="neutral-07-hidden",timeoutTicks=300)
    public static void hiddenAttackerGrantsNoAimAndExpiresWithoutThirdParty(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"ak47",30);var p=player(h,11.5,7.5);
        wall(h,true);
        h.runAtTickTime(10,()->{hit(n,h.getLevel().damageSources().playerAttack(p));p.moveTo(p.getX(),p.getY(),p.getZ()+3);});
        h.runAtTickTime(50,()->{
            h.assertTrue(n.reactiveTo(p.getUUID()) && n.controller().observation()==null,"hidden source grants identity only");
            h.assertTrue(n.controller().shots()==0 && !n.controller().allowNativeFire(),"wall/no known point blocks fire");
        });
        h.runAtTickTime(20+ServerConfig.MEMORY.get(),()->{
            h.assertTrue(n.reactiveAttacker()==null && n.controller().observation()==null,"bounded damage contact expires");wall(h,false);
        });
        h.runAtTickTime(50+ServerConfig.MEMORY.get(),()->{h.assertTrue(n.controller().shots()==0,"opening wall after expiry does not provoke neutral");finish(h,n,p);});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="neutral-08-reload",timeoutTicks=230)
    public static void retaliationCannotBypassNativeReload(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"ak47",0);var p=player(h,11.5,7.5);
        boolean[] damagedDuringReload={false};
        h.onEachTick(()->{
            boolean reloading=n.runtime().operator().getSynReloadState().getStateType().isReloading();
            if(reloading && !damagedDuringReload[0]) {
                damagedDuringReload[0]=true;hit(n,h.getLevel().damageSources().playerAttack(p));
                h.assertTrue(n.runtime().operator().getSynReloadState().getStateType().isReloading(),"damage does not cancel native reload");
            }
            if(reloading)h.assertTrue(n.controller().shots()==0 && ProjectileProbe.count(n.getUUID())==0,"native busy forbids accepted requests and real rounds");
        });
        h.runAtTickTime(180,()->{h.assertTrue(damagedDuringReload[0] && ProjectileProbe.count(n.getUUID())>0,"neutral reload starts; response resumes after native reload/ADS/cooldown");finish(h,n,p);});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="neutral-09-burst",timeoutTicks=180)
    public static void returningNeutralCancelsUncreatedNativeBurstRounds(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"b93r",20);var p=player(h,11.5,7.5);
        IGun.getIGunOrNull(n.getMainHandItem()).setFireMode(n.getMainHandItem(),com.tacz.guns.api.item.gun.FireMode.BURST);
        h.runAtTickTime(15,()->hit(n,h.getLevel().damageSources().playerAttack(p)));
        int[] count={-1};
        h.onEachTick(()->{
            if(count[0]<0 && ProjectileProbe.count(n.getUUID())>0) {
                count[0]=ProjectileProbe.count(n.getUUID());n.enterNeutral();
                h.assertFalse(n.controller().allowNativeFire(),"returning neutral closes per-round gate immediately");
            } else if(count[0]>=0)h.assertTrue(ProjectileProbe.count(n.getUUID())==count[0],"no further delayed native rounds after neutral reset");
        });
        h.runAtTickTime(130,()->{h.assertTrue(count[0]>0,"real native BURST exercised");finish(h,n,p);});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="neutral-10-nbt",timeoutTicks=70)
    public static void schemaMigrationPreservesConfiguredNpcsAndNeutralInventory(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"ak47",29);var p=player(h,11.5,7.5);
        h.runAtTickTime(10,()->{
            n.setFaction("stage07");n.setSquad(UUID.randomUUID());n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,17);
            hit(n,h.getLevel().damageSources().playerAttack(p));var saved=new CompoundTag();n.save(saved);
            var loaded=(TacticalNpcEntity)EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);
            h.assertTrue(loaded.neutral() && loaded.reactiveAttacker()==null && loaded.getUUID().equals(n.getUUID()) && loaded.serviceId().equals(n.serviceId()),"new schema preserves identity/neutral, never reactive target");
            h.assertTrue(loaded.faction().equals(n.faction()) && loaded.runtime().squadId().equals(n.runtime().squadId()),"faction/squad intact");
            h.assertTrue(IGun.getIGunOrNull(loaded.getMainHandItem()).getDummyAmmoAmount(loaded.getMainHandItem())==17 && IGun.getIGunOrNull(loaded.getMainHandItem()).getCurrentAmmoCount(loaded.getMainHandItem())==29,"finite reserve/magazine intact");
            n.setOrder(OrderState.Kind.DEFEND_AREA,128);n.save(saved);
            for(int schema:new int[]{1,2,3}) {
                var migrated=saved.copy();var data=migrated.getCompound("tacticalnpc");data.putInt("schema",schema);data.remove("neutral");
                var old=(TacticalNpcEntity)EntityType.loadEntityRecursive(migrated,h.getLevel(),e->e);
                h.assertTrue(!old.neutral() && old.order().equals("DEFEND_AREA") && old.runtime().orderState().radius()==128,"schema "+schema+" missing neutral preserves configured behavior/radius128");
                if(schema>=2)h.assertTrue(old.runtime().orderState().anchor().equals(n.runtime().orderState().anchor()),"original anchor retained");
            }
            finish(h,n,p);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="neutral-11-native-damage",timeoutTicks=180)
    public static void actualTaczProjectileDamageAuthenticatesNpcShooter(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"ak47",30);n.setFaction("neutral-test");
        n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);n.setHealth(200);
        var attacker=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(11.5,2,7.5));attacker.moveTo(at.x,at.y,at.z,90,0);attacker.setYHeadRot(90);attacker.setInvulnerable(true);
        var stack=GunSupport.create(new ResourceLocation("tacz:ak47"));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,30);gun.setBulletInBarrel(stack,true);
        attacker.setItemSlot(EquipmentSlot.MAINHAND,stack);attacker.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(attacker);
        h.onEachTick(()->{
            if(n.getHealth()<200 && n.reactiveTo(attacker.getUUID())) {
                h.assertTrue(ProjectileProbe.count(attacker.getUUID())>0,"genuine native projectile inflicted server damage");finish(h,n,attacker);
            }
        });
        h.runAtTickTime(140,()->h.fail("native NPC projectile never established the authenticated reactive source"));
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID,template="arena",batch="neutral-12-memory",timeoutTicks=300)
    public static void lostVisualContactKeepsFrozenMemoryThenReturnsNeutral(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"ak47",30);var p=player(h,11.5,7.5);Vector3[] frozen={null};
        h.runAtTickTime(10,()->hit(n,h.getLevel().damageSources().playerAttack(p)));
        h.runAtTickTime(30,()->{
            var known=n.controller().observation();long generation=n.controller().generation();
            h.assertTrue(known!=null,"first real source has legitimate visual memory");
            hit(n,h.getLevel().damageSources().playerAttack(p));
            h.assertTrue(n.controller().generation()==generation && n.controller().observation()!=null && n.controller().observation().position().equals(known.position()),"same attacker refreshes contact without erasing permitted memory/commands");
        });
        h.runAtTickTime(35,()->{
            h.assertTrue(n.controller().observation()!=null,"legitimate visible retaliation contact");
            frozen[0]=n.controller().observation().position();wall(h,true);p.moveTo(p.getX(),p.getY(),p.getZ()+4);
        });
        h.runAtTickTime(50,()->h.assertTrue(n.controller().observation()!=null && n.controller().observation().position().equals(frozen[0]),"hidden attacker does not update known point"));
        h.runAtTickTime(60+ServerConfig.MEMORY.get(),()->{
            h.assertTrue(n.reactiveAttacker()==null && n.controller().observation()==null && !n.controller().allowNativeFire(),"expired visual contact returns to safe neutral");finish(h,n,p);
        });
    }
    private static TacticalNpcEntity npc(GameTestHelper h,String id,int ammo) {
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(3.5,2,7.5));n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);n.enterNeutral();
        var stack=GunSupport.create(new ResourceLocation("tacz",id));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,ammo);gun.setBulletInBarrel(stack,ammo>0);
        n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);return n;
    }
    private static ServerPlayer player(GameTestHelper h,double x,double z) {
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"neutral-test"));p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);
        var at=h.absoluteVec(new Vec3(x,2,z));p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);return p;
    }
    private static void hit(TacticalNpcEntity n,net.minecraft.world.damagesource.DamageSource source) {n.invulnerableTime=0;n.hurt(source,1);}
    private static void wall(GameTestHelper h,boolean solid) {for(int y=2;y<=6;y++)for(int z=0;z<16;z++)h.setBlock(new BlockPos(7,y,z),solid?Blocks.STONE:Blocks.AIR);}
    private static int run(GameTestHelper h,net.minecraft.commands.CommandSourceStack source,String command) {
        try{return h.getLevel().getServer().getCommands().getDispatcher().execute(command,source);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new AssertionError(command,e);}
    }
    private static void finish(GameTestHelper h,Entity... entities) {for(var e:entities)e.discard();h.succeed();}
}
