package com.aurora.vpn;
import java.nio.file.*;
import java.util.*;
/** Export only profiles accepted by the same parser as Android. No raw archive is executed. */
public final class FeedExport {
    public static void main(String[] args)throws Exception{
        List<Node> all=new ArrayList<>();
        try{all.addAll(GateParser.parse(Discovery.downloadDirect()));}catch(Exception e){System.out.println("Direct providers unavailable: "+e.getClass().getSimpleName());}
        String extra=Files.readString(Path.of(".checks/extra.csv"));
        if(!extra.isEmpty())try{all.addAll(GateParser.parse(extra));}catch(Exception ignored){}
        String raw=Discovery.encodeNodes(all);all=GateParser.parse(raw);
        if(all.isEmpty())throw new IllegalStateException("No actual public configurations");
        Path out=Path.of(".checks/feed");Files.createDirectories(out);
        Files.writeString(out.resolve("candidates.csv"),raw);
        // Rotate the unexplored pool across runs instead of retesting only advertised top nodes.
        Collections.shuffle(all,new Random(System.currentTimeMillis()/3600000L));
        List<Node> candidates=Discovery.probe(all);List<Node> chosen=new ArrayList<>();Set<String> subnets=new HashSet<>();
        for(int pass=0;pass<2;pass++)for(Node n:candidates){
            String subnet=n.ip.substring(0,n.ip.lastIndexOf('.'));
            if(chosen.size()>=24||n.latencyMs<=0||chosen.contains(n)||(pass==0&&subnets.contains(subnet)))continue;
            chosen.add(n);subnets.add(subnet);
        }
        int i=0;for(Node n:chosen)Files.writeString(out.resolve("node-"+(i++)+".ovpn"),n.config);
        Files.writeString(out.resolve("selected.csv"),Discovery.encodeNodes(chosen));
        System.out.println("Unique public candidates "+all.size()+"; isolated tunnel checks "+chosen.size());
    }
}
