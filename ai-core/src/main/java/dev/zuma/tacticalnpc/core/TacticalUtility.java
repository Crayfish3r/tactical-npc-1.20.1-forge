package dev.zuma.tacticalnpc.core;

/** Utility margin plus commitment. Health, weapon maintenance and route failure always interrupt. */
public final class TacticalUtility {
    /** A substantial distance advantage is required before disturbing an established contact. */
    public static boolean preferTarget(double heldSquared,double candidateSquared){
        return Double.isFinite(heldSquared) && Double.isFinite(candidateSquared) && candidateSquared>=0 && candidateSquared<heldSquared*.81;
    }
    private TacticalIntent choice;private long until;
    public boolean flank(CombatSnapshot s,SquadBlackboard.Role role,boolean route){
        long tick=s.self().frame().tick();
        if(!route || role!=SquadBlackboard.Role.FLANKER || !s.targetKnown() || s.self().healthRatio()<.4 ||
                s.self().damaged() || s.weapon().busy() || s.weapon().rounds()<4 || s.pathBlocked()) {choice=null;until=0;return false;}
        double engage=s.targetVisible() && s.terrain().lineOfFireClear()?65:25;
        double flank=45+(s.targetVisible()?0:35)+(s.terrain().occupied()?20:0);
        if(choice==TacticalIntent.FLANK && tick<until)return true;
        if(flank>=engage+10){choice=TacticalIntent.FLANK;until=tick+80;return true;}
        choice=null;return false;
    }
    public void reset(){choice=null;until=0;}
}
