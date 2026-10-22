package dev.zuma.tacticalnpc.core;
/** effectiveRange is maximum authorized kinetic reach; profile distances are preferences, damageFalloff is native damage onset. */
public record WeaponState(String id, int rounds, boolean reserve, boolean boltNeeded, boolean busy,
                          boolean cooldownReady, boolean supported, double effectiveRange, String mode, WeaponProfile profile, double spreadDegrees,
                          Ballistics ballistics,double damageFalloff) {
    public WeaponState(String id,int rounds,boolean reserve,boolean boltNeeded,boolean busy,boolean cooldownReady,boolean supported,double effectiveRange,String mode,WeaponProfile profile,double spreadDegrees){
        this(id,rounds,reserve,boltNeeded,busy,cooldownReady,supported,effectiveRange,mode,profile,spreadDegrees,Ballistics.UNKNOWN,effectiveRange);
    }
    public WeaponState(String id,int rounds,boolean reserve,boolean boltNeeded,boolean busy,boolean ready,boolean supported,double range,String mode) {
        this(id,rounds,reserve,boltNeeded,busy,ready,supported,range,mode,WeaponProfile.of(WeaponProfile.Kind.AR),1);
    }
    public WeaponState {
        if (id == null || mode == null || profile==null || rounds < 0 || !Double.isFinite(effectiveRange) || effectiveRange <= 0) throw new IllegalArgumentException("Invalid weapon state");
    }
}
