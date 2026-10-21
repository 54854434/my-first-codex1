package com.aurora.vpn;
import java.util.*;
public final class CloudFeedTest {
    static void check(boolean value){if(!value)throw new AssertionError("Cloud feed boundary failed");}
    public static void main(String[] args)throws Exception{
        long now=System.currentTimeMillis();
        String config="client\ndev tun\nproto tcp\nremote 8.8.8.8 443\n<ca>\nfixture\n</ca>\n";
        Node slow=GateParser.parseConfig("8.8.8.8","US","US",999999999,config);
        Node fast=GateParser.parseConfig("9.9.9.9","US","US",1,config);fast.cloudCheckedAt=now-1000;fast.cloudMbps=8;fast.cloudConnectMs=5000;
        String raw=Discovery.encodeNodes(Arrays.asList(slow,fast));
        String dated="#aurora_snapshot_timestamp="+now+"\n"+raw;
        FeedSnapshot feed=CloudFeed.decode(dated,now);
        check(feed.nodes.get(0).ip.equals(fast.ip));
        check(feed.nodes.get(0).cloudMbps==8);
        check(Discovery.balanced(feed.nodes,1).get(0).cloudMbps==8);
        boolean stale=false;try{CloudFeed.decode(dated,now+7*3600000L);}catch(IllegalArgumentException e){stale=true;}check(stale);
        boolean unproven=false;try{CloudFeed.decode("#aurora_snapshot_timestamp="+now+"\n"+Discovery.encodeNodes(Arrays.asList(slow)),now);}catch(IllegalArgumentException e){unproven=true;}check(unproven);
        boolean undated=false;try{CloudFeed.decode(raw,now);}catch(IllegalArgumentException e){undated=true;}check(undated);
        System.out.println("Cloud evidence roundtrip, ranking, expiry and unproven-feed rejection passed");
    }
}
