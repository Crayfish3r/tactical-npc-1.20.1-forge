package dev.zuma.tacticalnpc.compat;

import dev.zuma.tacticalnpc.combat.AiWorkBudget;
import dev.zuma.tacticalnpc.combat.CombatTargets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Only called before an actual native explosive round; no trajectory simulation. */
public final class ExplosiveSafety {
    public static double radius(LivingEntity npc) {
        var cache=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(npc).getCacheProperty();
        com.tacz.guns.resource.pojo.data.gun.ExplosionData data=cache==null?null:cache.getCache(com.tacz.guns.api.GunProperties.EXPLOSION);
        return data==null?Double.NaN:data.getRadius();
    }
    public static double minimum(LivingEntity npc){return Math.max(12,2*radius(npc)+2);}
    public static boolean safe(PathfinderMob npc,Vec3 impact,Vec3 barrelEnd) {
        double r=radius(npc);
        if(!Double.isFinite(r) || r<=0 || r>8 || npc.getEyePosition().distanceTo(impact)<minimum(npc))return false;
        // Conservatively include twice the native radius and body extent.
        double reach=r*2+1;
        var region=new AABB(impact,barrelEnd).inflate(reach);
        var level=(ServerLevel)npc.level();var budget=AiWorkBudget.of(level);
        if(!budget.query(npc.getUUID(),true))return false;
        for(int x=net.minecraft.util.Mth.floor(region.minX)>>4;x<=net.minecraft.util.Mth.floor(region.maxX)>>4;x++)
            for(int z=net.minecraft.util.Mth.floor(region.minZ)>>4;z<=net.minecraft.util.Mth.floor(region.maxZ)>>4;z++)
                if(level.getChunkSource().getChunkNow(x,z)==null)return false;
        var protectedEntities=new java.util.ArrayList<LivingEntity>(1);int[] scanned={0};
        budget.worldQuery();
        level.getEntities(EntityTypeTest.forClass(LivingEntity.class),region,e->++scanned[0]>64 || e.isAlive() && (e==npc || !CombatTargets.eligible(npc,e)),protectedEntities,1);
        return scanned[0]<=64 && protectedEntities.isEmpty();
    }
    private ExplosiveSafety(){}
}
