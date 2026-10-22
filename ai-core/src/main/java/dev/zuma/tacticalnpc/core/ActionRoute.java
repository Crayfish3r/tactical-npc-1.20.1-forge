package dev.zuma.tacticalnpc.core;

import java.util.List;
import java.util.Objects;

/** Immutable, world-independent contract. Only an adapter may certify its conditions. */
public record ActionRoute(List<Transition> transitions, Vector3 destination) {
    public enum Kind { WALK, OPEN_DOOR, CLIMB_UP, CLIMB_DOWN }
    public enum Condition { SUPPORTED_BODY_CLEAR, OPENABLE_DOOR, CONTINUOUS_LADDER }
    public enum Completion { GROUNDED_AT_EXIT, OPEN_PASSAGE_AND_GROUNDED_AT_EXIT }
    public enum Status { RUNNING, WAITING_RESOURCE, WAITING_BUDGET, SUCCESS, FAILURE }
    public record Transition(Kind kind, Vector3 approach, Vector3 entry, Vector3 exit,
                             Vector3 resource, Condition precondition, Completion completion, int timeoutTicks) {
        public Transition {
            Objects.requireNonNull(kind);Objects.requireNonNull(approach);Objects.requireNonNull(entry);
            Objects.requireNonNull(exit);Objects.requireNonNull(precondition);Objects.requireNonNull(completion);
            if(timeoutTicks<1 || timeoutTicks>1200)throw new IllegalArgumentException("transition timeout");
            if(kind!=Kind.WALK)Objects.requireNonNull(resource);
        }
    }
    public ActionRoute {
        transitions=List.copyOf(transitions);Objects.requireNonNull(destination);
        if(transitions.isEmpty() || transitions.size()>128)throw new IllegalArgumentException("route size");
    }
    /** Waiting consumes a separate finite allowance; it is never evidence of physical stuckness. */
    public static final class Clock {
        private int active,waiting;
        private long last=Long.MIN_VALUE;
        public boolean advance(long tick, Status status, int timeout) {
            if(tick==last)return active>timeout || waiting>400;
            last=tick;
            if(status==Status.WAITING_RESOURCE || status==Status.WAITING_BUDGET)waiting++;
            else if(status==Status.RUNNING)active++;
            return active>timeout || waiting>400;
        }
        public int activeTicks(){return active;}
        public int waitingTicks(){return waiting;}
    }
}
