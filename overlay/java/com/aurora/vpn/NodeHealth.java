package com.aurora.vpn;

import java.util.*;

/** Device-local evidence, separated by Wi-Fi/mobile/other transport; never uploaded. */
public final class NodeHealth {
    private static final long DAY=86400000L, FRESH=6*3600000L;
    private static final class Entry {long success,failure;int failures,httpsMs;long speedAt;double mbps;}
    private final Map<String,Entry> entries=new HashMap<>();
    private String key(String scope,Node node){return scope+"|"+node.ip+":"+node.port;}
    private Entry entry(String scope,Node node){return entries.computeIfAbsent(key(scope,node),k->new Entry());}
    public void success(String scope,Node node,long now,int httpsMs){Entry e=entry(scope,node);e.success=now;e.failures=0;e.failure=0;e.httpsMs=Math.max(1,httpsMs);prune(now);}
    public void speed(String scope,Node node,long now,double mbps){if(!Double.isFinite(mbps)||mbps<=0||mbps>100000)return;Entry e=entry(scope,node);e.speedAt=now;e.mbps=mbps;prune(now);}
    private boolean measured(String scope,Node node,long now){Entry e=entries.get(key(scope,node));return e!=null&&e.speedAt>0&&e.speedAt<=now&&now-e.speedAt<=FRESH;}
    private int tier(String scope,Node node,long now){
        if(measured(scope,node,now))return entries.get(key(scope,node)).mbps>=1?0:3;
        return node.cloudFresh(now)?1:verified(scope,node,now)?2:4;
    }
    private double speedValue(String scope,Node node,long now){return measured(scope,node,now)?entries.get(key(scope,node)).mbps:node.cloudFresh(now)?node.cloudMbps:0;}
    public void failure(String scope,Node node,long now){Entry e=entry(scope,node);e.failure=now;e.failures=Math.min(5,e.failures+1);prune(now);}
    public long cooldown(String scope,Node node,long now){
        Entry e=entries.get(key(scope,node));if(e==null||e.failure<=e.success||e.failure>now)return 0;
        return Math.max(0,e.failure+Math.min(900000L,60000L*(1L<<Math.max(0,e.failures-1)))-now);
    }
    public boolean verified(String scope,Node node,long now){Entry e=entries.get(key(scope,node));return e!=null&&e.success>0&&e.success<=now&&now-e.success<=FRESH&&e.success>=e.failure;}
    public String note(String scope,Node node,long now){
        long cold=cooldown(scope,node,now);if(cold>0)return "本机近期失败 · 冷却 "+((cold+999)/1000)+" 秒（仍可手动尝试）";
        if(measured(scope,node,now))return String.format(Locale.ROOT,"本机本类网络下载采样 %.2f Mbps",entries.get(key(scope,node)).mbps);
        if(verified(scope,node,now)){Entry e=entries.get(key(scope,node));return "本机本类网络验证成功 · HTTPS "+e.httpsMs+" ms";}return "尚无本机近期联网验证";
    }
    public List<Node> select(List<Node> nodes,String scope,long now,int limit){
        List<Node> eligible=new ArrayList<>();Set<String> endpoints=new HashSet<>();
        for(Node n:nodes)if(cooldown(scope,n,now)==0&&endpoints.add(n.ip+":"+n.port))eligible.add(n);
        eligible.sort(Comparator.comparingInt((Node n)->tier(scope,n,now))
            .thenComparing(Comparator.comparingDouble((Node n)->speedValue(scope,n,now)).reversed())
            .thenComparingInt(n->verified(scope,n,now)?entries.get(key(scope,n)).httpsMs:n.latencyMs>0?n.latencyMs:Integer.MAX_VALUE));
        List<Node> result=new ArrayList<>();Set<String> countries=new HashSet<>();
        for(Node n:eligible)if(result.size()<limit&&tier(scope,n,now)<=2){result.add(n);countries.add(n.code);}
        Map<String,List<Node>> groups=new LinkedHashMap<>();
        for(Node n:eligible)if(!result.contains(n))groups.computeIfAbsent(n.source,k->new ArrayList<>()).add(n);
        boolean added=true;
        while(result.size()<limit&&added){
            added=false;
            for(List<Node> group:groups.values())if(!group.isEmpty()&&result.size()<limit){
                Node chosen=group.get(0);
                for(Node n:group)if(!countries.contains(n.code)){chosen=n;break;}
                group.remove(chosen);result.add(chosen);countries.add(chosen.code);added=true;
            }
        }
        return result;
    }
    private void prune(long now){entries.entrySet().removeIf(x->Math.max(x.getValue().success,x.getValue().failure)<now-DAY);while(entries.size()>256){String oldest=Collections.min(entries.keySet(),Comparator.comparingLong(k->Math.max(entries.get(k).success,entries.get(k).failure)));entries.remove(oldest);}}
    public String encode(long now){prune(now);StringBuilder out=new StringBuilder("aurora-health-1\n");for(Map.Entry<String,Entry> row:entries.entrySet()){Entry e=row.getValue();out.append(row.getKey()).append('\t').append(e.success).append('\t').append(e.failure).append('\t').append(e.failures).append('\t').append(e.httpsMs).append('\t').append(e.speedAt).append('\t').append(e.mbps).append('\n');}return out.toString();}
    public static NodeHealth decode(String raw,long now){
        NodeHealth health=new NodeHealth();if(raw==null||raw.length()>65536||!raw.startsWith("aurora-health-1\n"))return health;
        for(String line:raw.split("\n")){try{String[] c=line.split("\t");if((c.length!=5&&c.length!=7)||!c[0].matches("(wifi|mobile|other)\\|[0-9.]+:[0-9]+"))continue;
            Entry e=new Entry();e.success=Long.parseLong(c[1]);e.failure=Long.parseLong(c[2]);e.failures=Integer.parseInt(c[3]);e.httpsMs=Integer.parseInt(c[4]);
            if(c.length==7){e.speedAt=Long.parseLong(c[5]);e.mbps=Double.parseDouble(c[6]);if(e.speedAt<0||e.speedAt>now||!Double.isFinite(e.mbps)||e.mbps<0||e.mbps>100000)continue;}
            if(e.success<0||e.failure<0||e.success>now||e.failure>now||e.failures<0||e.failures>5||e.httpsMs<0||e.httpsMs>120000)continue;health.entries.put(c[0],e);
        }catch(NumberFormatException ignored){}}
        health.prune(now);return health;
    }
}

