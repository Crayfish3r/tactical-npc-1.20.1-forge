package dev.zuma.tacticalnpc;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.entity.NpcEntities;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class IndependentNpcTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="own-01-maintenance",timeoutTicks=180)
    public static void nativeLifecycleWithoutNpcs(GameTestHelper h) {
        if(Boolean.getBoolean("tactical.tests.noNpcs")) h.assertFalse(net.minecraftforge.fml.ModList.get().isLoaded("tacznpcs"),"NPCs is absent from standalone process");
        var npc=spawn(h,"ak47",0,false);
        h.runAtTickTime(140,()->{
            var op=IGunOperator.fromLivingEntity(npc);
            h.assertTrue(op.getCacheProperty()!=null && op.getDataHolder().currentGunItem.get()==npc.getMainHandItem(),"native initialData/cache/draw");
            h.assertTrue(npc.controller().reloads()==1 && !op.getSynReloadState().getStateType().isReloading(),"native reload completes once without a target: reloads="+npc.controller().reloads()+" state="+op.getSynReloadState().getStateType()+" ammo="+IGun.getIGunOrNull(npc.getMainHandItem()).getCurrentAmmoCount(npc.getMainHandItem()));
            h.assertTrue(IGun.getIGunOrNull(npc.getMainHandItem()).getCurrentAmmoCount(npc.getMainHandItem())>0,"native reload filled magazine");
            h.assertTrue(npc.controller().shots()==0,"maintenance never shoots"); finish(h,npc);
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="own-02-combat",timeoutTicks=320)
    public static void combatSmoke(GameTestHelper h) {
        dev.zuma.tacticalnpc.config.ServerConfig.DEBUG.set(true);
        var npc=spawn(h,"ak47",0,false); var p=player(h); wall(h,true);
        h.runAtTickTime(25,()->h.assertTrue(npc.runtime().operator().getDataHolder().reloadStateType.isReloading() && npc.controller().shots()==0,"reload behind wall without firing"));
        h.runAtTickTime(105,()->{h.assertTrue(npc.controller().reloads()==1,"one accepted reload"); npc.setYRot(-90);npc.setYHeadRot(-90);npc.getLookControl().setLookAt(p,10,10);wall(h,false);});
        h.runAtTickTime(106,()->h.assertTrue(npc.controller().shots()==0,"perception/reaction protects first tick of newly visible player"));
        h.runAtTickTime(190,()->{
            h.assertTrue(npc.position().subtract(h.absoluteVec(new Vec3(3.5,2,7.5))).horizontalDistance()>.25,"independent strafe really moves horizontally");
            h.assertTrue(npc.controller().shots()>=2 && ProjectileProbe.count(npc.getUUID())>=2,"real TaCZ projectiles on own entity: requests="+npc.controller().shots()+" bullets="+ProjectileProbe.count(npc.getUUID())+" at="+npc.position()+" decision="+npc.controller().decision()+" observation="+npc.controller().observation());
            long shots=npc.controller().shots(); int bullets=ProjectileProbe.count(npc.getUUID()); long generation=npc.controller().generation();
            Vec3 reset=h.absoluteVec(new Vec3(3.5,2,7.5));npc.moveTo(reset.x,reset.y,reset.z,npc.getYRot(),npc.getXRot());npc.setDeltaMovement(Vec3.ZERO);
            wall(h,true);
            h.assertTrue(h.getLevel().clip(new net.minecraft.world.level.ClipContext(npc.getEyePosition(),new dev.zuma.tacticalnpc.combat.VisibilitySensor().aimPoint(p),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,npc)).getType()!=net.minecraft.world.phys.HitResult.Type.MISS,"wall fixture physically separates both actors");
            h.runAtTickTime(205,()->{
                h.assertTrue(npc.controller().shots()==shots && ProjectileProbe.count(npc.getUUID())==bullets,"wall stops requests AND delayed native bullets");
                h.assertTrue(npc.controller().generation()!=generation,"LoS loss invalidates old commands");
                p.setHealth(0); npc.removeTag(RuntimeRegistry.TAG);
            });
            h.runAtTickTime(215,()->{h.assertTrue(npc.controller()==null && RuntimeRegistry.active(npc)==null,"tag releases runtime"); npc.addTag(RuntimeRegistry.TAG);});
            h.runAtTickTime(225,()->{h.assertTrue(npc.controller()!=null,"tag reacquires once");npc.setHealth(0);});
            h.runAtTickTime(230,()->{h.assertTrue(npc.controller()==null,"death cleanup");p.discard();finish(h,npc);});
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="own-03-finite",timeoutTicks=180)
    public static void finiteReserveAndBolt(GameTestHelper h) {
        var npc=spawn(h,"ai_awp",3,false);
        npc.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,0);
        h.runAtTickTime(70,()->{
            var gun=IGun.getIGunOrNull(npc.getMainHandItem());
            h.assertTrue(gun.hasBulletInBarrel(npc.getMainHandItem()),"native manual-action bolt chambers");
            h.assertTrue(npc.runtime().operator().needCheckAmmo(),"finite policy reaches native operator");
            gun.setCurrentAmmoCount(npc.getMainHandItem(),0);gun.setBulletInBarrel(npc.getMainHandItem(),false);
        });
        h.runAtTickTime(110,()->{h.assertTrue(npc.controller().reloads()==0,"empty reserve refuses reload");finish(h,npc);});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="own-04-persistence",timeoutTicks=180)
    public static void nbtUnloadReload(GameTestHelper h) {
        var npc=spawn(h,"glock_17",10,true);npc.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,7);
        CompoundTag saved=new CompoundTag();var service=npc.serviceId();long[] oldGeneration={0};
        h.runAtTickTime(15,()->{
            oldGeneration[0]=npc.controller().generation();npc.save(saved);npc.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(RuntimeRegistry.active(npc)==null,"unload releases live references");
        });
        h.runAtTickTime(25,()->{
            var restored=(TacticalNpcEntity)EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);
            h.assertTrue(restored!=null && restored.serviceId().equals(service) && restored.ammoPolicy()==TacticalNpcEntity.AmmoPolicy.FINITE,"versioned NBT restores identity/policy");
            h.assertTrue(IGun.getIGunOrNull(restored.getMainHandItem()).getDummyAmmoAmount(restored.getMainHandItem())==7,"reserve persisted in native gun NBT");
            h.getLevel().addFreshEntity(restored);
            h.runAtTickTime(40,()->{
                h.assertTrue(restored.controller()!=null && restored.controller().generation()!=oldGeneration[0],"loaded entity owns new generation");finish(h,restored);
            });
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="own-07-finite-consumption",timeoutTicks=220)
    public static void finiteReloadConsumesReserve(GameTestHelper h) {
        var npc=spawn(h,"glock_17",0,false);npc.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,7);
        h.runAtTickTime(120,()->{
            var stack=npc.getMainHandItem();var gun=IGun.getIGunOrNull(stack);
            int rounds=gun.getCurrentAmmoCount(stack)+(gun.hasBulletInBarrel(stack)?1:0);
            h.assertTrue(rounds==7 && gun.getDummyAmmoAmount(stack)==0,"native finite reload conserves seven reserve rounds: rounds="+rounds+" reserve="+gun.getDummyAmmoAmount(stack));
            h.assertTrue(npc.controller().reloads()==1,"one finite reload");gun.setCurrentAmmoCount(stack,0);gun.setBulletInBarrel(stack,false);
        });
        h.runAtTickTime(165,()->{h.assertTrue(npc.controller().reloads()==1,"exhausted reserve cannot reload again");finish(h,npc);});
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="own-05-dimension",timeoutTicks=120)
    public static void dimensionTransfer(GameTestHelper h) {
        var npc=spawn(h,"ak47",15,true);
        h.runAtTickTime(15,()->{
            var nether=h.getLevel().getServer().getLevel(Level.NETHER);
            var moved=(TacticalNpcEntity)npc.changeDimension(nether,new net.minecraftforge.common.util.ITeleporter(){
                @Override public net.minecraft.world.level.portal.PortalInfo getPortalInfo(Entity e,net.minecraft.server.level.ServerLevel dest,java.util.function.Function<net.minecraft.server.level.ServerLevel,net.minecraft.world.level.portal.PortalInfo> fallback) {
                    return new net.minecraft.world.level.portal.PortalInfo(new Vec3(8,110,8),Vec3.ZERO,0,0);
                }
            });
            h.assertTrue(moved!=null && moved.level()==nether && RuntimeRegistry.active(npc)==null,"dimension transfer releases old runtime");
            h.runAtTickTime(25,()->{moved.discard();finish(h,npc);});
        });
    }
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="own-06-friendly",timeoutTicks=100)
    public static void alliedDamageEventGuard(GameTestHelper h) {
        var npc=spawn(h,"ak47",30,true);
        var ally=NpcEntities.NPC.get().create(h.getLevel());var pos=h.absoluteVec(new Vec3(14,2,7));ally.moveTo(pos.x,pos.y,pos.z);h.getLevel().addFreshEntity(ally);
        h.runAtTickTime(15,()->{
            var index=TimelessAPI.getCommonGunIndex(new ResourceLocation("tacz:ak47")).orElseThrow();
            var bullet=new com.tacz.guns.entity.EntityKineticBullet(h.getLevel(),npc,npc.getMainHandItem(),index.getGunData().getAmmoId(),new ResourceLocation("tacz:ak47"),false,index.getGunData(),index.getBulletData());
            bullet.addTag("tactical_projectile");
            var source=h.getLevel().damageSources().generic();
            var event=new com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre(bullet,ally,npc,new ResourceLocation("tacz:ak47"),new ResourceLocation("tacz:ak47"),9,org.apache.commons.lang3.tuple.Pair.of(source,source),false,1,net.minecraftforge.fml.LogicalSide.SERVER);
            h.assertTrue(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event),"native cancellable pre-damage event rejects allied hit");
            npc.removeTag(RuntimeRegistry.TAG);h.assertTrue(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre(bullet,ally,npc,new ResourceLocation("tacz:ak47"),new ResourceLocation("tacz:ak47"),9,org.apache.commons.lang3.tuple.Pair.of(source,source),false,1,net.minecraftforge.fml.LogicalSide.SERVER)),"in-flight tagged bullet protected after AI disable");
            h.assertTrue(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre(bullet,ally,null,new ResourceLocation("tacz:ak47"),new ResourceLocation("tacz:ak47"),9,org.apache.commons.lang3.tuple.Pair.of(source,source),false,1,net.minecraftforge.fml.LogicalSide.SERVER)),"orphan tactical bullet fails closed when native attacker cannot resolve");
            bullet.discard();ally.discard();finish(h,npc);
        });
    }
    private static TacticalNpcEntity spawn(GameTestHelper h,String id,int rounds,boolean chamber) {
        ScenarioIsolation.begin(h);
        var npc=NpcEntities.NPC.get().create(h.getLevel());var pos=h.absoluteVec(new Vec3(3.5,2,7.5));npc.moveTo(pos.x,pos.y,pos.z,-90,0);npc.setYHeadRot(-90);
        var stack=GunSupport.create(new ResourceLocation("tacz",id));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,rounds);gun.setBulletInBarrel(stack,chamber);
        npc.setItemSlot(EquipmentSlot.MAINHAND,stack);npc.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(npc);return npc;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h) {
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"own-npc-test"));
        p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);var pos=h.absoluteVec(new Vec3(11.5,2,7.5));p.moveTo(pos.x,pos.y,pos.z);h.getLevel().addNewPlayer(p);return p;
    }
    private static void wall(GameTestHelper h,boolean solid) { for(int y=2;y<=6;y++)for(int z=0;z<16;z++)h.setBlock(new BlockPos(7,y,z),solid?Blocks.STONE:Blocks.AIR); }
    private static void finish(GameTestHelper h,TacticalNpcEntity n) { n.discard();h.succeed(); }
}
