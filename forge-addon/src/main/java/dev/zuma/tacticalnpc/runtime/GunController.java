package dev.zuma.tacticalnpc.runtime;
import dev.zuma.tacticalnpc.core.ActionArbiter.WeaponAction;
import dev.zuma.tacticalnpc.core.WeaponState;
public interface GunController {
    void maintain(long tick);
    WeaponState snapshot();
    void apply(WeaponAction action, long tick);
    void aim(boolean aiming);
    long shots();
    long reloads();
}
