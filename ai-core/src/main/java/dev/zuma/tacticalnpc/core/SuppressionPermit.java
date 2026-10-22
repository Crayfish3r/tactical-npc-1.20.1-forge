package dev.zuma.tacticalnpc.core;
/** A separate authority from visual DIRECT fire. Never contains a live entity. */
public record SuppressionPermit(Observation observation,boolean budgetReady,boolean reactionComplete,Vector3 point,int maxAge) {
    public SuppressionPermit(Observation observation,boolean budgetReady,boolean reactionComplete){this(observation,budgetReady,reactionComplete,observation==null?null:observation.position(),40);}
    public static SuppressionPermit none(){return new SuppressionPermit(null,false,false);}
    public boolean valid(long tick) {
        return knowledgeValid(tick) && budgetReady && reactionComplete;
    }
    public boolean knowledgeValid(long tick) {
        return observation!=null && !observation.visible() && observation.expires()>=tick && tick>=observation.tick() &&
                tick-observation.tick()<=maxAge && observation.confidence()>=.35 && observation.uncertainty()<=16 &&
                point!=null && point.minus(observation.position()).lengthSquared()<=64;
    }
}
