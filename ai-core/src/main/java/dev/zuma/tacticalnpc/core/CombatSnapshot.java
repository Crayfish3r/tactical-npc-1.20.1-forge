package dev.zuma.tacticalnpc.core;

/**
 * Immutable, de-identified view of a bot's *legitimately observed* environment.
 * The Forge adapter must not insert hidden target positions as live observations.
 */
public record CombatSnapshot(SelfState self, Observation observation, WeaponState weapon,
                             TerrainState terrain, TacticalState tactical) {
    public boolean targetKnown() { return observation != null && observation.expires() >= self.frame().tick(); }
    public boolean targetVisible() { return targetKnown() && observation.visible(); }
    public int lastSeenTicksAgo() { return observation == null ? Integer.MAX_VALUE : (int)Math.min(Integer.MAX_VALUE, Math.max(0,self.frame().tick()-observation.tick())); }
    public double targetDistanceBlocks() { return observation == null ? 0 : observation.position().minus(self.position()).length(); }
    public double healthRatio() { return self.healthRatio(); }
    public int magazineRounds() { return weapon.rounds(); }
    public boolean reserveAmmoAvailable() { return weapon.reserve(); }
    public boolean incomingFire() { return self.damaged(); }
    public boolean coverAvailable() { return !terrain.coverPositions().isEmpty(); }
    public boolean flankRouteAvailable() { return !terrain.routes().isEmpty(); }
    public boolean pathBlocked() { return terrain.blocked(); }
    public boolean lineOfFireClear() { return terrain.lineOfFireClear(); }
    public boolean allyInFireLine() { return terrain.occupied(); }
    public boolean boltNeeded() { return weapon.boltNeeded(); }
    public boolean shootCooldownReady() { return weapon.cooldownReady(); }
}
