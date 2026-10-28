package dev.zuma.tacticalnpc.core;
/** Behaviour tuning only. Technical compatibility and native mechanics stay in GunController. */
public record WeaponProfile(Kind kind,double minimum,double maximum,int volleyRequests,int pauseTicks,boolean mobile) {
    public WeaponProfile {
        if(kind==null || !Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum<0 || maximum<=minimum || volleyRequests<1 || pauseTicks<1)
            throw new IllegalArgumentException("Invalid weapon behaviour profile");
    }
    public enum Kind { PISTOL,SMG,AR,DMR,SNIPER,SHOTGUN,LMG,RPG,CONSERVATIVE }
    public static WeaponProfile of(Kind kind) {
        return switch(kind) {
            case PISTOL -> new WeaponProfile(kind,3,18,2,8,true);
            case SMG -> new WeaponProfile(kind,5,22,5,8,true);
            case AR -> new WeaponProfile(kind,10,36,4,10,true);
            case DMR -> new WeaponProfile(kind,18,50,1,12,false);
            case SNIPER -> new WeaponProfile(kind,24,64,1,20,false);
            case SHOTGUN -> new WeaponProfile(kind,3,14,1,10,true);
            case LMG -> new WeaponProfile(kind,15,42,8,12,false);
            case RPG -> new WeaponProfile(kind,12,64,1,20,false);
            case CONSERVATIVE -> new WeaponProfile(kind,6,16,1,16,false);
        };
    }
}
