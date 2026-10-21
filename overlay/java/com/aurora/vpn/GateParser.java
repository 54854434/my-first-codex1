package com.aurora.vpn;

import java.util.*;
import java.nio.charset.StandardCharsets;

/** Parses the public VPN Gate CSV. No credentials, subscriptions or private feeds. */
public final class GateParser {
    public static List<String> csv(String line) {
        List<String> out = new ArrayList<>(); StringBuilder cell=new StringBuilder();
        boolean quoted=false;
        for(int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if(c=='"') {
                if(quoted && i+1<line.length() && line.charAt(i+1)=='"') {cell.append('"');i++;}
                else quoted=!quoted;
            } else if(c==',' && !quoted) {out.add(cell.toString());cell.setLength(0);}
            else cell.append(c);
        }
        if(quoted) throw new IllegalArgumentException("Incomplete CSV");
        out.add(cell.toString()); return out;
    }
    public static boolean publicIp(String ip) {
        String[] parts=ip.split("\\."); if(parts.length!=4) return false;
        int[] n=new int[4];
        try {for(int i=0;i<4;i++){if(!parts[i].matches("[0-9]{1,3}"))return false;n[i]=Integer.parseInt(parts[i]);if(n[i]>255)return false;}}
        catch(NumberFormatException e){return false;}
        return !(n[0]==0 || n[0]==10 || n[0]==127 || n[0]>=224 ||
            (n[0]==169 && n[1]==254) || (n[0]==172 && n[1]>=16 && n[1]<=31) ||
            (n[0]==192 && n[1]==168) || (n[0]==100 && n[1]>=64 && n[1]<=127) ||
            (n[0]==192 && n[1]==0) || (n[0]==198 && (n[1]==18 || n[1]==19 || n[1]==51)) ||
            (n[0]==203 && n[1]==0 && n[2]==113));
    }
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
        "client","dev","dev-type","proto","remote","resolv-retry","nobind","persist-key","persist-tun",
        "cipher","data-ciphers","data-ciphers-fallback","auth","verb","mute","remote-cert-tls",
        "verify-x509-name","tls-client","tls-version-min","tls-cipher","key-direction","auth-nocache",
        "comp-lzo","compress","allow-compression","connect-retry","connect-timeout","connect-retry-max",
        "tun-mtu","mssfix","sndbuf","rcvbuf","reneg-sec","keepalive","ping","ping-restart",
        "fast-io","pull","auth-user-pass","redirect-gateway","route-delay","explicit-exit-notify"));
    public static Node parseConfig(String ip,String country,String code,long bps,String raw) {
        if(!publicIp(ip) || raw.length()>65536) throw new IllegalArgumentException("Invalid endpoint");
        String block=null; StringBuilder safe=new StringBuilder(); int port=0, remotes=0;
        boolean tcp=false, ca=false, tun=false, client=false;
        for(String original:raw.split("\\r?\\n")) {
            String line=original.trim();
            if(line.isEmpty() || line.startsWith("#") || line.startsWith(";"))continue;
            if(block!=null) {
                if(line.equals("</"+block+">")){block=null;}
                else if(line.startsWith("<"))throw new IllegalArgumentException("Nested block");
                safe.append(line).append('\n'); continue;
            }
            if(line.startsWith("<")) {
                if(!Arrays.asList("<ca>","<cert>","<key>","<tls-auth>","<tls-crypt>","<auth-user-pass>").contains(line))
                    throw new IllegalArgumentException("Unsupported inline block");
                block=line.substring(1,line.length()-1); if(block.equals("ca"))ca=true;
                safe.append(line).append('\n'); continue;
            }
            String[] p=line.split("\\s+"); String key=p[0].toLowerCase(Locale.ROOT);
            if(!ALLOWED.contains(key))throw new IllegalArgumentException("Unsupported directive");
            if(key.equals("proto"))tcp=p.length==2 && (p[1].equals("tcp")||p[1].equals("tcp-client"));
            if(key.equals("client"))client=true;
            if(key.equals("dev"))tun=p.length==2 && p[1].equals("tun");
            if(key.equals("remote")) {
                if(p.length<3 || p.length>3)throw new IllegalArgumentException("Invalid remote");
                port=Integer.parseInt(p[2]); if(port<1||port>65535)throw new IllegalArgumentException("Invalid port");
                remotes++; line="remote "+ip+" "+port;
            }
            // Public VPN Gate credentials, when the source asks for them; never a local filename.
            if(key.equals("auth-user-pass")) {
                if(p.length!=1)throw new IllegalArgumentException("External credentials rejected");
                line="<auth-user-pass>\nvpn\nvpn\n</auth-user-pass>";
            }
            safe.append(line).append('\n');
        }
        if(block!=null||!tcp||!tun||!client||!ca||remotes!=1)throw new IllegalArgumentException("Incomplete TCP profile");
        return new Node(ip,country,code,port,bps,safe.toString());
    }
    public static List<Node> parse(String text) {
        List<Node> result=new ArrayList<>(); Map<String,Integer> header=new HashMap<>(); Set<String> seen=new HashSet<>();
        for(String line:text.split("\\r?\\n")) {
            if(line.startsWith("#HostName,")) {
                List<String> cells=csv(line.substring(1));for(int i=0;i<cells.size();i++)header.put(cells.get(i),i);continue;
            }
            if(line.startsWith("*")||line.startsWith("#")||line.trim().isEmpty())continue;
            if(!header.keySet().containsAll(Arrays.asList("IP","CountryLong","CountryShort","Speed","OpenVPN_ConfigData_Base64")))continue;
            try {
                List<String> row=csv(line);
                String raw=new String(Base64.getDecoder().decode(row.get(header.get("OpenVPN_ConfigData_Base64"))),StandardCharsets.UTF_8);
                Node node=parseConfig(row.get(header.get("IP")),row.get(header.get("CountryLong")),
                    row.get(header.get("CountryShort")).toUpperCase(Locale.ROOT),Long.parseLong(row.get(header.get("Speed"))),raw);
                if(header.containsKey("AuroraSource")&&row.get(header.get("AuroraSource")).equals("VPNBook"))
                    node=new Node(node.ip,node.country,node.code,node.port,node.advertisedBps,node.config,"VPNBook");
                if(header.containsKey("AuroraCheckedAt")&&header.containsKey("AuroraMbps")&&header.containsKey("AuroraConnectMs")){
                    long stamp=Long.parseLong(row.get(header.get("AuroraCheckedAt")));
                    double speed=Double.parseDouble(row.get(header.get("AuroraMbps")));int ms=Integer.parseInt(row.get(header.get("AuroraConnectMs")));
                    if(stamp>0&&stamp<=System.currentTimeMillis()&&Double.isFinite(speed)&&speed>0&&speed<100000&&ms>0&&ms<120000){node.cloudCheckedAt=stamp;node.cloudMbps=speed;node.cloudConnectMs=ms;}
                }
                if(seen.add(node.ip+":"+node.port))result.add(node);
            }catch(IllegalArgumentException|IndexOutOfBoundsException ignored){ /* one bad row does not poison the feed */ }
        }
        if(header.isEmpty())throw new IllegalArgumentException("Node source format changed");
        result.sort(Comparator.comparingInt((Node n)->n.cloudFresh(System.currentTimeMillis())?0:1)
            .thenComparing(Comparator.comparingDouble((Node n)->n.cloudFresh(System.currentTimeMillis())?n.cloudMbps:0).reversed())
            .thenComparing(Comparator.comparingLong((Node n)->n.advertisedBps).reversed()));return result;
    }
}


