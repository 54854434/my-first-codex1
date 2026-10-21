package com.aurora.vpn;
import java.io.*;
/** Public static data, no app/user API credentials; a cloud test is never a phone test. */
public final class CloudFeed {
    public static FeedSnapshot decode(String text,long now){
        FeedSnapshot feed=FeedSnapshot.decode(text,now);
        if(now-feed.fetchedAt>6*3600000L)throw new IllegalArgumentException("Cloud feed expired");
        for(Node n:feed.nodes)if(n.cloudCheckedAt>feed.fetchedAt){n.cloudCheckedAt=0;n.cloudMbps=0;n.cloudConnectMs=0;}
        if(feed.nodes.stream().noneMatch(n->n.cloudFresh(now)))throw new IllegalArgumentException("No fresh cloud tunnel evidence");
        return feed;
    }
    public static FeedSnapshot download()throws IOException{
        IOException last=null;
        for(String url:new String[]{"https://raw.githubusercontent.com/54854434/my-first-codex1/public-feed/nodes.csv","https://cdn.jsdelivr.net/gh/54854434/my-first-codex1@public-feed/nodes.csv"}){
            try{return decode(Discovery.readUrl(url,5*1024*1024),System.currentTimeMillis());}
            catch(IOException|IllegalArgumentException error){last=new IOException("Cloud feed unavailable or stale");}
        }
        throw last;
    }
}
