package com.aurora.vpn;

import java.util.List;

/** Optional live source check; this is not evidence of availability on a user's network. */
public class GateLiveCheck {
    public static void main(String[] args) throws Exception {
        List<Node> nodes=GateParser.parse(Discovery.download());
        if(nodes.isEmpty())throw new AssertionError("Live source had no compatible TCP profiles");
        System.out.println("Live VPN Gate source parsed: "+nodes.size()+" compatible public TCP profiles");
    }
}
