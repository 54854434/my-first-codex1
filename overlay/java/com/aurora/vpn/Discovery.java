package com.aurora.vpn;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.net.SocketFactory;

public final class Discovery {
    public static final String SOURCE="https://www.vpngate.net/api/iphone/";
    public static volatile String lastReport="尚未查询来源";
    public static String readUrl(String url,int maxBytes)throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(4000);c.setReadTimeout(5000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","AuroraFreeVPN/0.7 public source client");long deadline=System.nanoTime()+12000000000L;
        try{
            if(c.getResponseCode()!=200)throw new IOException("Source HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;
                while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new InterruptedIOException("Source deadline");if(out.size()+n>maxBytes)throw new IOException("Source exceeds size bound");out.write(b,0,n);}
                return out.toString(StandardCharsets.UTF_8.name());
            }
        }finally{c.disconnect();}
    }
    private static List<Node> gate()throws IOException{
        IOException last=null;
        for(String url:Arrays.asList(SOURCE,
            "https://raw.githubusercontent.com/GeorgeXie2333/vpngate-list-mirror/main/data/vpngate.csv",
            "https://cdn.jsdelivr.net/gh/GeorgeXie2333/vpngate-list-mirror@main/data/vpngate.csv")){
            try{List<Node> result=GateParser.parse(readUrl(url,5*1024*1024));if(!result.isEmpty())return result;last=new IOException("No compatible profiles");}
            catch(IOException|IllegalArgumentException error){last=new IOException(error.getMessage());}
        }throw last==null?new IOException("VPN Gate sources unavailable"):last;
    }
    static String encodeNodes(List<Node> nodes){
        StringBuilder csv=new StringBuilder("*vpn_servers\n#HostName,IP,CountryLong,CountryShort,Speed,OpenVPN_ConfigData_Base64,AuroraSource,AuroraCheckedAt,AuroraMbps,AuroraConnectMs\n");Set<String> seen=new HashSet<>();
        for(Node n:nodes)if(seen.add(n.ip+":"+n.port))csv.append("public,").append(n.ip).append(',').append('"').append(n.country.replace("\"","\"\"")).append('"').append(',').append(n.code).append(',').append(n.advertisedBps).append(',')
            .append(Base64.getEncoder().encodeToString(n.config.getBytes(StandardCharsets.UTF_8))).append(',').append(n.source).append(',').append(n.cloudCheckedAt).append(',').append(n.cloudMbps).append(',').append(n.cloudConnectMs).append('\n');
        return csv.toString();
    }
    public static String download()throws IOException{
        try{
            FeedSnapshot feed=CloudFeed.download();
            lastReport="外置采集与实连筛选 · 云端通过 "+feed.nodes.stream().filter(n->n.cloudFresh(System.currentTimeMillis())).count()+" 条";
            return encodeNodes(feed.nodes);
        }catch(Exception unavailable){return downloadDirect();}
    }
    public static String downloadDirect()throws IOException{
        ExecutorService pool=Executors.newFixedThreadPool(2);List<Node> all=new ArrayList<>();List<String> reports=new ArrayList<>();
        try{
            List<Callable<List<Node>>> sources=Arrays.asList(Discovery::gate,BookSource::download);
            List<Future<List<Node>>> results=pool.invokeAll(sources,30,TimeUnit.SECONDS);String[] names={"VPN Gate（含备用镜像）","VPNBook"};
            for(int i=0;i<results.size();i++)try{List<Node> list=results.get(i).get();all.addAll(list);reports.add(names[i]+" "+list.size()+" 条");}
                catch(Exception error){reports.add(names[i]+" 获取失败");}
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw new InterruptedIOException("Discovery cancelled");}
        finally{pool.shutdownNow();}
        lastReport=String.join(" · ",reports);if(all.isEmpty())throw new IOException(lastReport);return encodeNodes(all);
    }
    public static List<Node> balanced(List<Node> all,int limit){
        Map<String,Queue<Node>> groups=new LinkedHashMap<>();for(Node n:all)groups.computeIfAbsent(n.source,k->new ArrayDeque<>()).add(n);
        List<Node> selected=new ArrayList<>();
        for(Node n:all)if(n.cloudFresh(System.currentTimeMillis())&&selected.size()<limit)selected.add(n);
        for(Queue<Node> group:groups.values())group.removeAll(selected);
        boolean added=true;
        while(selected.size()<limit&&added){added=false;for(Queue<Node> group:groups.values())if(!group.isEmpty()&&selected.size()<limit){selected.add(group.remove());added=true;}}
        return selected;
    }
    public static List<Node> find() throws Exception {
        return probe(GateParser.parse(download()));
    }
    public static List<Node> probe(List<Node> all) throws Exception {return probe(all,SocketFactory.getDefault(),()->true);}
    public static List<Node> probe(List<Node> all,SocketFactory factory,BooleanSupplier networkValid) throws Exception {
        AtomicBoolean changed=new AtomicBoolean(false);
        // Bounded checks: one TCP connection per candidate, no payload or broad network scanning.
        List<Node> candidates=balanced(all,40);
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> checks=new ArrayList<>();
            for(Node n:candidates)checks.add(()->{
                long start=System.nanoTime();
                try(Socket s=factory.createSocket()) {
                    if(!networkValid.getAsBoolean()){changed.set(true);throw new IOException("Probe network changed");}
                    s.connect(new InetSocketAddress(n.ip,n.port),1800);
                    if(!networkValid.getAsBoolean()){changed.set(true);throw new IOException("Probe network changed");}
                    n.latencyMs=(int)Math.max(1,(System.nanoTime()-start)/1000000);
                }catch(IOException ignored){n.latencyMs=-2;}
                n.checkedAt=System.currentTimeMillis();return null;
            });
            for(Future<Void> f:pool.invokeAll(checks,25,TimeUnit.SECONDS)){if(!f.isCancelled())f.get();}
        }finally{pool.shutdownNow();}
        if(changed.get()||!networkValid.getAsBoolean())throw new IOException("Probe network changed; discard results");
        candidates.sort(Comparator.comparingInt((Node n)->n.latencyMs>0?n.latencyMs:Integer.MAX_VALUE)
            .thenComparing(Comparator.comparingLong((Node n)->n.advertisedBps).reversed()));
        return candidates;
    }
}


