package com.aurora.vpn;

/** Monotonic deadlines. Native retry/status events must never extend a window. */
public final class AttemptWindow {
    public static final long CONNECT_MS=25000, STOP_MS=8000;
    public enum Stage { IDLE, CONNECTING, STOPPING, CONNECTED }
    public Stage stage=Stage.IDLE;
    public int generation;
    private long deadline;
    private boolean rpcDone, nativeStopped;
    public int start(long now){generation++;stage=Stage.CONNECTING;deadline=now+CONNECT_MS;return generation;}
    public boolean active(int token){return token==generation;}
    public boolean expired(long now){return (stage==Stage.CONNECTING||stage==Stage.STOPPING)&&now>=deadline;}
    public long seconds(long now){return Math.max(0,(deadline-now+999)/1000);}
    public boolean connected(){if(stage!=Stage.CONNECTING)return false;stage=Stage.CONNECTED;return true;}
    public void stopping(long now){stage=Stage.STOPPING;deadline=now+STOP_MS;rpcDone=false;nativeStopped=false;}
    public boolean rpcFinished(boolean hadProcess){rpcDone=true;if(!hadProcess)nativeStopped=true;return ready();}
    public boolean stopped(){nativeStopped=true;return ready();}
    private boolean ready(){return stage==Stage.STOPPING&&rpcDone&&nativeStopped;}
    public void clear(){generation++;stage=Stage.IDLE;}
}
