package dev.zuma.tacticalnpc.core;
/** A separate authority from visual DIRECT fire. Never contains a live entity. */
public record SuppressionPermit(Observation observation,boolean budgetReady,boolean reactionComplete) {
    public static SuppressionPermit none(){return new SuppressionPermit(null,false,false);}
    public boolean valid(long tick) {
        return observation!=null && !observation.visible() && observation.expires()>=tick && tick>=observation.tick() &&
                tick-observation.tick()<=40 && observation.confidence()>=.35 && observation.uncertainty()<=12 && budgetReady && reactionComplete;
    }
}
