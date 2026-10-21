package com.aurora.vpn;
public final class AttemptWindowTest {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
    public static void main(String[] args){
        AttemptWindow w=new AttemptWindow();int token=w.start(100);
        check(!w.expired(25099));check(w.expired(25100));
        check(w.seconds(100)==25);check(w.seconds(25099)==1);check(w.seconds(50000)==0);
        // CONNECTRETRY events do not call start; elapsed deadlines do not slide.
        for(int i=0;i<100;i++)check(w.active(token));check(w.expired(60000));
        w.stopping(30000);check(!w.expired(37999));check(w.expired(38000));
        check(!w.stopped());check(w.rpcFinished(true));
        w.clear();check(!w.active(token));check(!w.expired(Long.MAX_VALUE));
        w.start(0);w.stopping(25000);check(!w.rpcFinished(true));check(w.stopped());
        w.start(0);w.stopping(1);check(w.rpcFinished(false));
        w.start(0);check(w.connected());check(!w.expired(Long.MAX_VALUE));
        w.stopping(1);check(!w.connected());w.clear();check(!w.connected());
        System.out.println(checks+" connection deadline checks passed");
    }
}
