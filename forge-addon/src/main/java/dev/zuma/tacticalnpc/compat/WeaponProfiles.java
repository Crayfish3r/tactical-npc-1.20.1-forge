package dev.zuma.tacticalnpc.compat;
import dev.zuma.tacticalnpc.core.WeaponProfile;
import com.tacz.guns.resource.index.CommonGunIndex;
/** Types verified against the pinned default gunpack; unknown technical guns get conservative tuning. */
public final class WeaponProfiles {
    public static WeaponProfile from(CommonGunIndex index) {
        var kind=switch(index.getType()==null?"":index.getType()) {
            case "rpg" -> WeaponProfile.Kind.RPG;
            case "pistol" -> WeaponProfile.Kind.PISTOL;
            case "smg" -> WeaponProfile.Kind.SMG;
            case "rifle" -> WeaponProfile.Kind.AR;
            case "sniper" -> WeaponProfile.Kind.SNIPER;
            case "shotgun" -> WeaponProfile.Kind.SHOTGUN;
            case "mg" -> WeaponProfile.Kind.LMG;
            case "dmr" -> WeaponProfile.Kind.DMR;
            default -> WeaponProfile.Kind.CONSERVATIVE;
        };
        return WeaponProfile.of(kind);
    }
    private WeaponProfiles(){}
}
