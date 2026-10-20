package com.aurora.vpn;

import android.content.Context;
import android.net.*;
import android.os.SystemClock;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** An HTTPS payload from a VPN Network is required; a native CONNECTED event alone is insufficient. */
public final class TunnelCheck {
    public static final class Proof {
        public final String ip; public final int bytes;
        Proof(String ip,int bytes){this.ip=ip;this.bytes=bytes;}
    }
    public static Proof request(Context context,boolean requireVpn)throws IOException {
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network=null;NetworkCapabilities caps=null;
        long until=SystemClock.elapsedRealtime()+3000;
        do{
            network=cm.getActiveNetwork();caps=cm.getNetworkCapabilities(network);
            if(network!=null&&caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==requireVpn)break;
            SystemClock.sleep(200);
        }while(SystemClock.elapsedRealtime()<until);
        if(network==null||caps==null||caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)!=requireVpn)
            throw new IOException(requireVpn?"Android 尚未提供 VPN 网络":"基线网络不是直连");
        IOException last=null;
        for(String endpoint:new String[]{"https://api.ipify.org","https://www.cloudflare.com/cdn-cgi/trace"}){
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)network.openConnection(new URL(endpoint));
                connection.setConnectTimeout(4000);connection.setReadTimeout(4000);connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(false);
                if(connection.getResponseCode()!=200)throw new IOException("HTTPS HTTP "+connection.getResponseCode());
                ByteArrayOutputStream body=new ByteArrayOutputStream();
                try(InputStream input=connection.getInputStream()){
                    byte[] b=new byte[512];int n;
                    while((n=input.read(b))!=-1){if(body.size()+n>4096)throw new IOException("验证响应过大");body.write(b,0,n);}
                }
                String text=body.toString(StandardCharsets.UTF_8.name()).trim(),ip=text;
                if(endpoint.contains("cloudflare")){ip="";for(String line:text.split("\n"))if(line.startsWith("ip="))ip=line.substring(3).trim();}
                if(!GateParser.publicIp(ip))throw new IOException("验证服务未返回有效 IPv4");
                NetworkCapabilities now=cm.getNetworkCapabilities(network);
                if(!network.equals(cm.getActiveNetwork())||now==null||now.hasTransport(NetworkCapabilities.TRANSPORT_VPN)!=requireVpn)
                    throw new IOException("验证期间网络已切换");
                return new Proof(ip,body.size());
            }catch(IOException error){last=error;}finally{if(connection!=null)connection.disconnect();}
        }
        throw last==null?new IOException("HTTPS 验证失败"):last;
    }
}
