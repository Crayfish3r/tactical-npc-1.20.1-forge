package dev.zuma.tacticalnpc.api;

import dev.zuma.tacticalnpc.core.OrderState;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity.AmmoPolicy;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** Magazine starts empty; reserve is consumed by ordinary native reload. */
public record SpawnMemberSpec(ResourceLocation weapon, AmmoPolicy ammoPolicy, int reserve,
                              OrderState.Kind order, double radius, dev.zuma.tacticalnpc.core.NpcDifficulty difficulty) {
    /** Binary-compatible constructor for already compiled consumers. */
    public SpawnMemberSpec(ResourceLocation weapon, AmmoPolicy ammoPolicy, int reserve, OrderState.Kind order, double radius) {
        this(weapon,ammoPolicy,reserve,order,radius,dev.zuma.tacticalnpc.core.NpcDifficulty.HARD);
    }
    public SpawnMemberSpec {
        Objects.requireNonNull(difficulty); Objects.requireNonNull(weapon); Objects.requireNonNull(ammoPolicy);
        if(reserve<0 || !OrderState.validRadius(order,radius))throw new IllegalArgumentException("Invalid ammo reserve/order radius");
    }
}
