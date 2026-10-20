package com.aurora.vpn;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Only the provider's published, no-signup OpenVPN downloads and public shared login. */
public final class BookSource {
    public static final String PAGE="https://www.vpnbook.com/freevpn/openvpn";
    static final class Server {final String host,ip,country,code;Server(String h,String i,String c,String cc){host=h;ip=i;country=c;code=cc;}}
    static String[] credentials(String page)throws IOException{
        int start=page.indexOf("VPN Credentials");if(start<0)throw new IOException("VPNBook credential section changed");
        String section=page.substring(start,Math.min(page.length(),start+10000));
        Matcher m=Pattern.compile("<code[^>]*>([A-Za-z0-9]{3,64})</code>").matcher(section);
        if(!m.find()||!m.group(1).equals("vpnbook"))throw new IOException("VPNBook public username unavailable");
        if(!m.find())throw new IOException("VPNBook public password unavailable");return new String[]{"vpnbook",m.group(1)};
    }
    static List<Server> servers(String page){
        String data=page.replace("\\\"","\"");List<Server> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        Matcher m=Pattern.compile("\"hostname\":\"([a-z0-9]+\\.vpnbook\\.com)\",\"ipAddress\":\"([0-9.]+)\",\"countryCode\":\"([A-Z]{2})\",\"countryName\":\"([A-Za-z ]{2,50})\"").matcher(data);
        while(m.find())if(GateParser.publicIp(m.group(2))&&seen.add(m.group(1)))result.add(new Server(m.group(1),m.group(2),m.group(4),m.group(3)));
        return result;
    }
    static Node profile(Server server,String config,String[] login){
        // tun1 is a desktop device name; Android supplies its own tun file descriptor.
        String raw=config.replaceAll("(?m)^dev tun[0-9]*\\s*$","dev tun")
            .replaceAll("(?m)^auth-user-pass\\s*$","<auth-user-pass>\n"+login[0]+"\n"+login[1]+"\n</auth-user-pass>");
        Node n=GateParser.parseConfig(server.ip,server.country,server.code,0,raw);
        return new Node(n.ip,n.country,n.code,n.port,0,n.config,"VPNBook");
    }
    public static List<Node> download()throws Exception{
        String page=Discovery.readUrl(PAGE,512*1024);String[] login=credentials(page);List<Server> servers=servers(page);
        if(servers.isEmpty())throw new IOException("VPNBook server metadata unavailable");
        ExecutorService pool=Executors.newFixedThreadPool(3);List<Callable<Node>> calls=new ArrayList<>();
        for(Server s:servers.subList(0,Math.min(10,servers.size())))calls.add(()->{
            String url="https://www.vpnbook.com/api/openvpn?hostname="+URLEncoder.encode(s.host,"UTF-8")+"&protocol=tcp443&ip="+s.ip;
            return profile(s,Discovery.readUrl(url,65536),login);
        });
        List<Node> result=new ArrayList<>();
        try{for(Future<Node> task:pool.invokeAll(calls,18000,TimeUnit.MILLISECONDS))try{if(!task.isCancelled())result.add(task.get());}catch(ExecutionException ignored){}}
        finally{pool.shutdownNow();}
        if(result.isEmpty())throw new IOException("VPNBook returned no compatible public TCP profiles");return result;
    }
}
