package com.aurora.vpn;

import java.util.List;

/** Public, dated bootstrap data; never represents a successful VPN connection. */
public final class FeedSnapshot {
    public static final long MAX_AGE=7L*24*60*60*1000;
    private static final String PREFIX="#aurora_snapshot_timestamp=";
    public final String raw;
    public final long fetchedAt;
    public final List<Node> nodes;
    private FeedSnapshot(String raw,long fetchedAt,List<Node> nodes){this.raw=raw;this.fetchedAt=fetchedAt;this.nodes=nodes;}
    public static FeedSnapshot online(String raw,long now){
        List<Node> nodes=GateParser.parse(raw);
        if(nodes.isEmpty())throw new IllegalArgumentException("No compatible public TCP profiles");
        return new FeedSnapshot(raw,now,nodes);
    }
    public String encode(){return PREFIX+fetchedAt+"\n"+raw;}
    public static FeedSnapshot decode(String text,long now){
        int newline=text.indexOf('\n');
        if(newline<0||!text.startsWith(PREFIX))throw new IllegalArgumentException("Undated snapshot");
        long stamp=Long.parseLong(text.substring(PREFIX.length(),newline).trim());
        if(now-stamp>MAX_AGE||stamp-now>300000)throw new IllegalArgumentException("Expired snapshot or incorrect clock");
        return online(text.substring(newline+1),stamp);
    }
    public static FeedSnapshot newest(long now,String... choices){
        FeedSnapshot best=null;
        for(String text:choices){
            if(text==null)continue;
            try{FeedSnapshot next=decode(text,now);if(best==null||next.fetchedAt>best.fetchedAt)best=next;}
            catch(IllegalArgumentException ignored){}
        }
        return best;
    }
}
