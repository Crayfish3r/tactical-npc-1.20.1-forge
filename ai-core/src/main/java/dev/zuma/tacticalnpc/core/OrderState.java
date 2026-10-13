package dev.zuma.tacticalnpc.core;
public record OrderState(Kind kind,Vector3 anchor,double radius) {
    public enum Kind { DEFEND_AREA,HOLD_POSITION,ASSAULT,PATROL }
    public OrderState {if(kind==null || anchor==null || !Double.isFinite(radius) || radius<1)throw new IllegalArgumentException("Invalid order");}
}
