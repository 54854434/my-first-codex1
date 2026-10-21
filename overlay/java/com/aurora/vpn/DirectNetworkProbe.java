package com.aurora.vpn;

import android.content.Context;
import android.net.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** A captured physical Android Network; never silently falls back to the default VPN route. */
public final class DirectNetworkProbe {
    private final ConnectivityManager cm;
    public final Network network;
    public final String key,label,scope;
    private DirectNetworkProbe(ConnectivityManager manager,Network n,NetworkCapabilities caps){
        cm=manager;network=n;key=Long.toString(n.getNetworkHandle());
        scope=caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"wifi":caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"mobile":"other";
        label=scope.equals("wifi")?"Wi-Fi 直连":scope.equals("mobile")?"移动网络直连":"底层网络直连";
    }
    private static boolean physical(NetworkCapabilities c){
        return c!=null&&!c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    public static DirectNetworkProbe capture(Context context)throws IOException{
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network active=cm.getActiveNetwork();NetworkCapabilities caps=cm.getNetworkCapabilities(active);
        if(active!=null&&physical(caps))return new DirectNetworkProbe(cm,active,caps);
        Network best=null;NetworkCapabilities bestCaps=null;int bestScore=-1;
        for(Network n:cm.getAllNetworks()){
            NetworkCapabilities c=cm.getNetworkCapabilities(n);if(!physical(c))continue;
            int score=(c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?100:0)+(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?20:0);
            if(score>bestScore){best=n;bestCaps=c;bestScore=score;}
        }
        if(best==null)throw new IOException("没有可用的底层 Wi-Fi/移动网络");
        return new DirectNetworkProbe(cm,best,bestCaps);
    }
    public boolean current(Context context){
        if(!physical(cm.getNetworkCapabilities(network)))return false;
        try{return network.equals(capture(context).network);}catch(IOException e){return false;}
    }
    public List<Node> measure(Context context,List<Node> nodes)throws Exception{
        List<Node> result;
        try{result=Discovery.probe(nodes,network.getSocketFactory(),()->current(context));}
        catch(Exception error){for(Node n:nodes){n.latencyMs=-1;n.checkedAt=0;n.checkedNetwork="";}throw error;}
        if(!current(context))throw new IOException("检测时底层网络已切换，结果作废");
        for(Node n:result)n.checkedNetwork=key;
        return result;
    }
    public void check(Context context,Node node)throws IOException{
        if(!current(context))throw new IOException("底层网络已切换，请重新检测");
        long start=System.nanoTime();
        try(Socket socket=network.getSocketFactory().createSocket()){
            socket.connect(new InetSocketAddress(node.ip,node.port),2500);
        }
        if(!current(context))throw new IOException("复检期间底层网络已切换");
        node.latencyMs=(int)Math.max(1,(System.nanoTime()-start)/1000000);
        node.checkedAt=System.currentTimeMillis();node.checkedNetwork=key;
    }
}
