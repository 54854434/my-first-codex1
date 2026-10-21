package com.aurora.vpn;
import java.util.*;
public final class NodeHealthTest {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    static Node n(String ip,String code,int latency){Node n=new Node(ip,code,code,443,999999999,"fixture");n.latencyMs=latency;return n;}
    public static void main(String[] args){
        long now=100000000L;Node a=n("8.8.8.8","US",20),b=n("1.1.1.1","AU",1),c=n("9.9.9.9","US",3);
        NodeHealth h=new NodeHealth();h.success("wifi",a,now,400);
        check(h.select(Arrays.asList(b,a,c),"wifi",now,6).get(0)==a,"Verified node must outrank TCP latency");
        check(h.select(Arrays.asList(b,a,c),"mobile",now,6).get(0)==b,"Wi-Fi evidence must not affect mobile ranking");
        h.failure("wifi",a,now+1);check(!h.select(Arrays.asList(a,b),"wifi",now+1,6).contains(a),"Failure cooldown must exclude endpoint");
        check(h.cooldown("wifi",a,now+60001)==0,"Cooldown must expire");h.failure("wifi",a,now+60002);
        check(h.cooldown("wifi",a,now+60002)==120000,"Repeated failure extends cooldown");
        h.success("wifi",a,now+70000,800);check(h.cooldown("wifi",a,now+70000)==0,"Actual success resets failures");
        NodeHealth loaded=NodeHealth.decode(h.encode(now+70000),now+70000);check(loaded.verified("wifi",a,now+70000),"Evidence survives restart");
        check(!loaded.verified("wifi",a,now+7*3600000),"Stale evidence cannot stay preferred");
        check(!NodeHealth.decode("corrupt",now).verified("wifi",a,now),"Corrupt cache is ignored");
        check(h.select(Arrays.asList(a,a,b),"wifi",now+70000,6).size()==2,"Endpoint deduplication");
        List<Node> mixed=new ArrayList<>();for(int i=1;i<=8;i++){Node g=n("9.0.0."+i,"JP",300);mixed.add(g);Node book=new Node("8.0.0."+i,"US","US",443,0,"fixture","VPNBook");book.latencyMs=1;mixed.add(book);}
        List<Node> selected=new NodeHealth().select(mixed,"wifi",now,6);
        check(selected.stream().filter(x->x.source.equals("VPNBook")).count()==3,"Unverified candidates must not be monopolized by one provider");
        h.success("wifi",a,now+80000,500);h.speed("wifi",a,now+80000,.1);
        b.cloudCheckedAt=now+80000;b.cloudMbps=10;b.cloudConnectMs=3000;
        check(h.select(Arrays.asList(a,b),"wifi",now+80000,2).get(0)==b,"Known slow node must not outrank fresh cloud throughput evidence");
        h.speed("wifi",a,now+80000,20);
        NodeHealth saved=NodeHealth.decode(h.encode(now+80000),now+80000);
        check(saved.select(Arrays.asList(b,a),"wifi",now+80000,2).get(0)==a,"Measured local speed must persist and outrank remote evidence");
        saved.failure("wifi",a,now+80001);
        check(saved.select(Arrays.asList(a,b),"wifi",now+140002,2).get(0)==b,"A failed connection clears its old speed priority even after cooldown");
        System.out.println("Node evidence, network separation, cooldown and persistence checks passed");
    }
}

