package dev.zuma.tacticalnpc.compat;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.LuaValue;

/** Conservative contract: native kinetic shooting, no arbitrary Lua shooting/property hooks. */
public final class GunSupport {
    private static volatile boolean auditedReloadScript;
    private static final Set<String> MAINTENANCE = Set.of("start_reload", "tick_reload", "interrupt_reload", "start_bolt", "tick_bolt");
    public static String rejection(ItemStack stack, CommonGunIndex index) {
        IGun gun = IGun.getIGunOrNull(stack);
        if(stack.getItem().getClass()!=com.tacz.guns.item.ModernKineticGunItem.class) return "unaudited gun item implementation";
        var data = index.getGunData();
        if (gun == null || gun.useInventoryAmmo(stack)) return "inventory-fed or non-TaCZ weapon";
        if ("rpg".equals(index.getType()) || data.getChargeData(gun.getFireMode(stack)) != null) return "RPG/charge unsupported";
        if (index.getBulletData().getExplosionData() != null && index.getBulletData().getExplosionData().isExplode()) return "explosive projectile unsupported";
        if (index.getBulletData().getIgnite().isIgniteEntity() || index.getBulletData().getIgnite().isIgniteBlock()) return "incendiary projectile unsupported";
        if (!data.getFireModeSet().contains(gun.getFireMode(stack))) return "fire mode absent from GunData.fireModeSet";
        var script = index.getScript();
        if (script != null) {
            if(!new ResourceLocation("tacz:xmag_reload_logic").equals(data.getScript()) || !auditedReloadScript)
                return "Lua script not verified against pinned default pack: " + data.getScript();
            LuaValue key = LuaValue.NIL;
            while (true) {
                var entry = script.next(key);
                key = entry.arg1();
                if (key.isnil()) break;
                if (!MAINTENANCE.contains(key.tojstring())) return "unaudited Lua hook: " + key.tojstring();
            }
        }
        if (data.hasHeatData()) return "heat-dependent spread not audited";
        return "";
    }
    public static void verifyResources(net.minecraft.server.packs.resources.ResourceManager manager) {
        auditedReloadScript=false;
        try {
            var resource=manager.getResource(new ResourceLocation("tacz:scripts/xmag_reload_logic.lua"));
            if(resource.isPresent()) {
                try(var stream=resource.get().open()) {
                    String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
                    auditedReloadScript=digest.equalsIgnoreCase("5F25D34F7866FA928D2C937D52A2A5E14C47ADCE1CBF727D5B58114F33755F46");
                }
            }
        } catch(java.io.IOException | java.security.NoSuchAlgorithmException ex) {
            dev.zuma.tacticalnpc.TacticalNpcAiMod.LOGGER.warn("Could not verify pinned TaCZ reload script",ex);
        }
        dev.zuma.tacticalnpc.TacticalNpcAiMod.LOGGER.info("Pinned TaCZ xmag reload script verified: {}",auditedReloadScript);
    }
    public static FireMode preferredMode(CommonGunIndex index) {
        var modes = index.getGunData().getFireModeSet();
        if (modes.contains(FireMode.AUTO)) return FireMode.AUTO;
        if (modes.contains(FireMode.SEMI)) return FireMode.SEMI;
        if (modes.contains(FireMode.BURST)) return FireMode.BURST;
        throw new IllegalArgumentException("No supported native fire mode");
    }
    public static ItemStack create(ResourceLocation id) {
        var index = TimelessAPI.getCommonGunIndex(id).orElseThrow(() -> new IllegalArgumentException("Unknown gun " + id));
        ItemStack stack = GunItemBuilder.create().setId(id).setAmmoCount(0).setAmmoInBarrel(false)
                .setFireMode(preferredMode(index)).build();
        String reason = rejection(stack, index);
        if (!reason.isEmpty()) throw new IllegalArgumentException(id + ": " + reason);
        return stack;
    }
    private GunSupport() {}
}
