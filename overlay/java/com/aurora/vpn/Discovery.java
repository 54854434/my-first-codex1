package com.aurora.vpn;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class Discovery {
    public static final String SOURCE="https://www.vpngate.net/api/iphone/";
    public static String download() throws IOException {
        IOException last=null;
        for(String url:Arrays.asList(SOURCE,"https://vpngate.net/api/iphone/")) {
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","AuroraFreeVPN/0.1 (Android; public VPN Gate client)");
            try {
                if(c.getResponseCode()!=200)throw new IOException("Node source HTTP "+c.getResponseCode());
                try(InputStream input=c.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                    byte[] b=new byte[8192];int count;
                    while((count=input.read(b))!=-1) {
                        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                        if(output.size()+count>5*1024*1024)throw new IOException("Node source too large");
                        output.write(b,0,count);
                    }
                    return output.toString(StandardCharsets.UTF_8.name());
                }
            }catch(IOException e){last=e;}finally{c.disconnect();}
        }
        throw last==null?new IOException("Node source unavailable"):last;
    }
    public static List<Node> find() throws Exception {
        List<Node> all=GateParser.parse(download());
        // Bounded checks: one TCP connection per candidate, no payload or broad network scanning.
        List<Node> candidates=new ArrayList<>(all.subList(0,Math.min(all.size(),40)));
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> checks=new ArrayList<>();
            for(Node n:candidates)checks.add(()->{
                long start=System.nanoTime();
                try(Socket s=new Socket()) {
                    s.connect(new InetSocketAddress(n.ip,n.port),1800);
                    n.latencyMs=(int)Math.max(1,(System.nanoTime()-start)/1000000);
                }catch(IOException ignored){n.latencyMs=-2;}
                n.checkedAt=System.currentTimeMillis();return null;
            });
            for(Future<Void> f:pool.invokeAll(checks,25,TimeUnit.SECONDS)){if(!f.isCancelled())f.get();}
        }finally{pool.shutdownNow();}
        candidates.sort(Comparator.comparingInt((Node n)->n.latencyMs>0?n.latencyMs:Integer.MAX_VALUE)
            .thenComparing(Comparator.comparingLong((Node n)->n.advertisedBps).reversed()));
        return candidates;
    }
}
