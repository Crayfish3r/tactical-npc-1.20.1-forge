package dev.zuma.tacticalnpc;

import com.tacz.guns.api.item.IGun;
import dev.zuma.tacticalnpc.compat.GunSupport;
import dev.zuma.tacticalnpc.core.*;
import dev.zuma.tacticalnpc.entity.*;
import dev.zuma.tacticalnpc.runtime.RuntimeRegistry;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@PrefixGameTestTemplate(false)
public final class WeaponProfileTests {
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="profile-01-ar",timeoutTicks=180)
    public static void rifleNativeFire(GameTestHelper h){weapon(h,"ak47",WeaponProfile.Kind.AR,30,true,1);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="profile-02-pistol",timeoutTicks=180)
    public static void pistolNativeFire(GameTestHelper h){weapon(h,"glock_17",WeaponProfile.Kind.PISTOL,15,true,1);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="profile-03-shotgun",timeoutTicks=180)
    public static void shotgunNativePellets(GameTestHelper h){weapon(h,"aa12",WeaponProfile.Kind.SHOTGUN,8,false,10);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="profile-04-sniper",timeoutTicks=180)
    public static void sniperNativeBoltFire(GameTestHelper h){weapon(h,"ai_awp",WeaponProfile.Kind.SNIPER,3,false,1);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="profile-05-smg",timeoutTicks=180)
    public static void smgNativeFire(GameTestHelper h){weapon(h,"uzi",WeaponProfile.Kind.SMG,25,false,1);}
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="profile-06-lmg",timeoutTicks=180)
    public static void lmgNativeFire(GameTestHelper h){weapon(h,"m249",WeaponProfile.Kind.LMG,30,false,1);}

    private static void weapon(GameTestHelper h,String id,WeaponProfile.Kind kind,int ammo,boolean chamber,int pellets) {
        ScenarioIsolation.begin(h);var n=npc(h,id,ammo,chamber,new Vec3(3.5,2,7.5));
        n.setAmmoPolicy(TacticalNpcEntity.AmmoPolicy.FINITE,0);
        n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var p=player(h,new Vec3(10.5,2,7.5));
        h.runAtTickTime(150,()->{
            var state=n.controller().weaponState();
            h.assertTrue(state.supported() && state.profile().kind()==kind,"verified native gun type: "+state);
            h.assertTrue(state.effectiveRange()<=state.profile().maximum(),"native falloff can only shorten profile distance");
            h.assertTrue(n.controller().shots()>0 && ProjectileProbe.count(n.getUUID())>=pellets,"native requests and projectiles: "+id+" shots="+n.controller().shots()+" bullets="+ProjectileProbe.count(n.getUUID())+" decision="+n.controller().decision());
            h.assertTrue(n.controller().shots()<=150/state.profile().pauseTicks()+ammo,"profile pauses never cancel native cooldown");
            if(kind==WeaponProfile.Kind.SNIPER) h.assertTrue(n.controller().shots()<=3,"manual bolt rifle respects finite loaded rounds before reload");
            p.discard();n.discard();h.succeed();
        });
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="wide_arena",batch="profile-09-working-distances",timeoutTicks=500)
    public static void weaponDistanceReallyMoves(GameTestHelper h) {
        ScenarioIsolation.begin(h);var smg=npc(h,"uzi",25,false,new Vec3(3.5,2,20.5));smg.setOrder(OrderState.Kind.ASSAULT,24);
        var p=player(h,new Vec3(35.5,2,20.5));
        h.runAtTickTime(110,()->{
            h.assertTrue(smg.distanceTo(p)<29,"SMG actually closes toward its weapon range: "+smg.position()+" decision="+smg.controller().decision());
            smg.discard();var sniper=npc(h,"ai_awp",3,true,new Vec3(20.5,2,20.5));sniper.setOrder(OrderState.Kind.ASSAULT,24);
            h.runAtTickTime(450,()->{
                h.assertTrue(sniper.distanceTo(p)>18,"sniper really retreats toward a distant position: "+sniper.position()+" decision="+sniper.controller().decision());
                sniper.discard();p.discard();h.succeed();
            });
        });
    }

    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="profile-07-orders",timeoutTicks=200)
    public static void defendReturnAndOrderPersistence(GameTestHelper h) {
        ScenarioIsolation.begin(h);var n=npc(h,"uzi",20,false,new Vec3(3.5,2,7.5));
        n.setOrder(OrderState.Kind.DEFEND_AREA,4);var anchor=n.runtime().orderState().anchor();
        Vec3 displaced=h.absoluteVec(new Vec3(12.5,2,7.5));n.moveTo(displaced.x,displaced.y,displaced.z);
        h.runAtTickTime(120,()->{
            h.assertTrue(n.position().distanceTo(new Vec3(anchor.x(),anchor.y(),anchor.z()))<3,"defender really returns to anchor: "+n.position()+" decision="+n.controller().decision());
            n.setOrder(OrderState.Kind.PATROL,4);var tag=new net.minecraft.nbt.CompoundTag();n.save(tag);
            var restored=(TacticalNpcEntity)net.minecraft.world.entity.EntityType.loadEntityRecursive(tag,h.getLevel(),e->e);
            h.assertTrue(restored.order().equals("PATROL") && restored.runtime().orderState().anchor().equals(n.runtime().orderState().anchor()),"schema 2 preserves order and anchor");
            Vec3 start=n.position();
            h.runAtTickTime(170,()->{h.assertTrue(n.position().subtract(start).horizontalDistance()>.5,"patrol really navigates without target");restored.discard();n.discard();h.succeed();});
        });
    }

    /** Independent 1v4 baseline, before any squad coordination exists. FakePlayer, not a human duel. */
    @GameTest(templateNamespace=TacticalNpcAiMod.ID, template="arena",batch="profile-08-independent-1v4",timeoutTicks=220)
    public static void independentFourAgainstOne(GameTestHelper h) {
        ScenarioIsolation.begin(h);var actors=new java.util.ArrayList<TacticalNpcEntity>();
        for(int i=0;i<4;i++) {
            var n=npc(h,"ak47",30,true,new Vec3(3.5,2,1.5+4*i));
            n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);actors.add(n);
        }
        var p=player(h,new Vec3(13.5,2,7.5));
        h.runAtTickTime(180,()->{
            for(var n:actors) {
                h.assertTrue(n.controller()!=null && !n.controller().failed() && ProjectileProbe.count(n.getUUID())>0,"each independent NPC emits native projectiles: "+n.getUUID()+" count="+ProjectileProbe.count(n.getUUID())+" decision="+n.controller().decision());
                h.assertTrue(n.getHealth()==n.getMaxHealth(),"no allied damage during converging fire");
            }
            p.discard();actors.forEach(TacticalNpcEntity::discard);h.succeed();
        });
    }
    private static TacticalNpcEntity npc(GameTestHelper h,String id,int rounds,boolean chamber,Vec3 local) {
        var n=NpcEntities.NPC.get().create(h.getLevel());var at=h.absoluteVec(local);n.moveTo(at.x,at.y,at.z,-90,0);n.setYHeadRot(-90);
        var stack=GunSupport.create(new ResourceLocation("tacz",id));var gun=IGun.getIGunOrNull(stack);gun.setCurrentAmmoCount(stack,rounds);gun.setBulletInBarrel(stack,chamber);
        n.setItemSlot(EquipmentSlot.MAINHAND,stack);n.addTag(RuntimeRegistry.TAG);h.getLevel().addFreshEntity(n);return n;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,Vec3 local) {
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"profile-test"));
        p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);var at=h.absoluteVec(local);p.moveTo(at.x,at.y,at.z);h.getLevel().addNewPlayer(p);return p;
    }
}
