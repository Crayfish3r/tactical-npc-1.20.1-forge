package dev.zuma.tacticalnpc.core;
public record WeaponState(String id, int rounds, boolean reserve, boolean boltNeeded, boolean busy,
                          boolean cooldownReady, boolean supported, double effectiveRange, String mode, WeaponProfile profile, double spreadDegrees) {
    public WeaponState(String id,int rounds,boolean reserve,boolean boltNeeded,boolean busy,boolean ready,boolean supported,double range,String mode) {
        this(id,rounds,reserve,boltNeeded,busy,ready,supported,range,mode,WeaponProfile.of(WeaponProfile.Kind.AR),1);
    }
    public WeaponState {
        if (id == null || mode == null || profile==null || rounds < 0 || !Double.isFinite(effectiveRange) || effectiveRange <= 0) throw new IllegalArgumentException("Invalid weapon state");
    }
}
