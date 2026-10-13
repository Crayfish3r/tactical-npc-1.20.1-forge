package dev.zuma.tacticalnpc.core;

/** One bounded track. Hidden samples are never extrapolated or refreshed from a live entity. */
public final class ObservationMemory {
    private Observation last;
    public void record(Observation observation) {
        if (observation == null) return;
        if (last != null && observation.tick() < last.tick()) return;
        // A sound cannot replace a recent precise visual track of the same subject.
        if (last != null && last.subject().equals(observation.subject()) && last.source()==Observation.Source.VISION &&
                observation.source()!=Observation.Source.VISION && observation.tick()-last.tick()<20) return;
        last=observation;
    }
    public Observation sample(long tick, boolean currentlyVisible, Vector3 self, double memoryRange) {
        if(last==null) return null;
        if(tick>last.expires() || self.minus(last.position()).lengthSquared()>memoryRange*memoryRange) { clear(); return null; }
        long age=Math.max(0,tick-last.tick());
        boolean visible=currentlyVisible && age==0 && last.source()==Observation.Source.VISION;
        double lifetime=Math.max(1,last.expires()-last.tick());
        return new Observation(last.subject(),last.position(),visible?last.velocity():new Vector3(0,0,0),last.tick(),last.expires(),
                last.source(),last.confidence()*Math.max(0,1-age/lifetime),last.uncertainty()+age*.08,visible);
    }
    public void forget(java.util.UUID subject) { if(last!=null && last.subject().equals(subject)) clear(); }
    public void clear() { last=null; }
}
