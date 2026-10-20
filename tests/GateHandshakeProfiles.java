package com.aurora.vpn;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Export only parser-validated public TCP profiles for isolated dev-null handshakes in CI. */
public final class GateHandshakeProfiles {
    public static void main(String[] args)throws Exception{
        FeedSnapshot feed=FeedSnapshot.newest(System.currentTimeMillis(),Files.readString(Path.of(args[0])));
        if(feed==null)throw new IllegalStateException("No fresh seed");
        Path out=Path.of(args[1]);Files.createDirectories(out);
        List<Node> nodes=Discovery.probe(feed.nodes);Set<String> countries=new HashSet<>(),used=new HashSet<>();int count=0;
        for(int pass=0;pass<2;pass++)for(Node n:nodes){
            if(count>=6||used.contains(n.ip)||(pass==0&&!countries.add(n.code)))continue;
            used.add(n.ip);Files.writeString(out.resolve(n.ip+".ovpn"),n.config,StandardCharsets.UTF_8);count++;
        }
    }
}
