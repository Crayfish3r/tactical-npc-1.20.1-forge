package dev.zuma.tacticalnpc.core;
import java.util.*;
public final class ActionRouteTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("action route check "+checks);}
    public static void main(String[] args){
        var a=UUID.randomUUID();var b=UUID.randomUUID();var c=UUID.randomUUID();var table=new TransitionReservations();
        check(table.acquire(1,a,0));check(!table.acquire(1,b,0));check(!table.acquire(1,c,1));
        table.release(1,a);check(!table.acquire(1,c,2));check(table.acquire(1,b,2));
        table.forget(b);check(table.acquire(1,c,3));check(table.acquire(2,a,3));
        check(table.acquire(1,b,100));table.forget(b);table.forget(a);table.prune(200);check(table.size()==0);
        var clock=new ActionRoute.Clock();for(int i=0;i<100;i++)check(!clock.advance(i,ActionRoute.Status.WAITING_RESOURCE,5));
        for(int i=100;i<105;i++)check(!clock.advance(i,ActionRoute.Status.RUNNING,5));
        check(clock.advance(105,ActionRoute.Status.RUNNING,5));check(clock.activeTicks()==6 && clock.waitingTicks()==100);
        var p=new Vector3(0,0,0);var source=new ArrayList<ActionRoute.Transition>();
        source.add(new ActionRoute.Transition(ActionRoute.Kind.WALK,p,p,p,null,ActionRoute.Condition.SUPPORTED_BODY_CLEAR,ActionRoute.Completion.GROUNDED_AT_EXIT,20));
        var route=new ActionRoute(source,p);source.clear();check(route.transitions().size()==1);
        try{route.transitions().clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
        System.out.println("Action route checks: "+checks+" PASS");
    }
}
