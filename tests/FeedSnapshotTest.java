package com.aurora.vpn;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class FeedSnapshotTest {
    static int checks;
    static void check(boolean b){checks++;if(!b)throw new AssertionError("Check "+checks);}
    public static void main(String[] args){
        String raw="*vpn_servers\n#HostName,IP,CountryLong,CountryShort,Speed,OpenVPN_ConfigData_Base64\n"
            +"node,8.8.4.4,Japan,JP,100000000,"+Base64.getEncoder().encodeToString(GateParserTest.config.getBytes(StandardCharsets.UTF_8))+"\n*";
        long now=1800000000000L;
        FeedSnapshot fresh=FeedSnapshot.online(raw,now);
        check(FeedSnapshot.decode(fresh.encode(),now).nodes.size()==1);
        String older=FeedSnapshot.online(raw,now-3600000).encode();
        check(FeedSnapshot.newest(now,older,fresh.encode()).fetchedAt==now);
        check(FeedSnapshot.newest(now,"broken cache",fresh.encode()).nodes.size()==1);
        check(FeedSnapshot.newest(now,null,older).fetchedAt==now-3600000);
        check(FeedSnapshot.newest(now,FeedSnapshot.online(raw,now-FeedSnapshot.MAX_AGE-1).encode())==null);
        check(FeedSnapshot.newest(now,FeedSnapshot.online(raw,now+300001).encode())==null);
        try{FeedSnapshot.online("<html>blocked</html>",now);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
        try{FeedSnapshot.online("#HostName,IP,CountryLong,CountryShort,Speed,OpenVPN_ConfigData_Base64\n*",now);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
        System.out.println("Passed "+checks+" snapshot fallback/freshness checks");
    }
}
