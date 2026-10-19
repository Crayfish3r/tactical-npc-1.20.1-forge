package dev.zuma.tacticalnpc.core;
public record OrderState(Kind kind,Vector3 anchor,double radius) {
    public static double maximumRadius(Kind kind) {return kind==Kind.DEFEND_AREA?128:64;}
    public static boolean validRadius(Kind kind,double radius) {return kind!=null && Double.isFinite(radius) && radius>=4 && radius<=maximumRadius(kind);}
    public enum Kind { DEFEND_AREA,HOLD_POSITION,ASSAULT,PATROL }
    public OrderState {if(kind==null || anchor==null || !Double.isFinite(radius) || radius<1 || kind==Kind.DEFEND_AREA && !validRadius(kind,radius))throw new IllegalArgumentException("Invalid order");}
}
