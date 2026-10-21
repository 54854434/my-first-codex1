package com.aurora.vpn;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.net.SocketFactory;
public final class DirectProbeTest {
    static class Factory extends SocketFactory {
        final AtomicInteger calls=new AtomicInteger();final AtomicBoolean valid;
        Factory(AtomicBoolean flag){valid=flag;}
        public Socket createSocket(){return new Socket(){public void connect(SocketAddress address,int timeout)throws IOException{
            if(timeout!=1800)throw new AssertionError("Unexpected connect bound");
            calls.incrementAndGet();if(valid!=null)valid.set(false);
        }};}
        public Socket createSocket(String a,int b)throws IOException{throw new IOException("Wrong factory overload");}
        public Socket createSocket(String a,int b,InetAddress c,int d)throws IOException{throw new IOException("Wrong factory overload");}
        public Socket createSocket(InetAddress a,int b)throws IOException{throw new IOException("Wrong factory overload");}
        public Socket createSocket(InetAddress a,int b,InetAddress c,int d)throws IOException{throw new IOException("Wrong factory overload");}
    }
    public static void main(String[] args)throws Exception{
        Node n=new Node("8.8.8.8","US","US",443,0,"fixture");
        Factory bound=new Factory(null);
        List<Node> measured=Discovery.probe(Collections.singletonList(n),bound,()->true);
        if(bound.calls.get()!=1||measured.get(0).latencyMs<=0)throw new AssertionError("Supplied route factory was not used");
        AtomicBoolean valid=new AtomicBoolean(true);Factory changed=new Factory(valid);boolean rejected=false;
        try{Discovery.probe(Collections.singletonList(n),changed,valid::get);}catch(IOException expected){rejected=true;}
        if(!rejected)throw new AssertionError("Network-change measurements must be discarded");
        System.out.println("Bound socket factory and network-change rejection checks passed");
    }
}
