package com.aurora.vpn;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
import de.blinkt.openvpn.core.*;

/** Installs/launches the actual app, uses its discovery/connection path and validates VPN egress. */
public final class RealConnectionInstrumentation extends Instrumentation {
    private MainActivity activity;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private Object value(String name)throws Exception{
        AtomicReference<Object> result=new AtomicReference<>();AtomicReference<Exception> error=new AtomicReference<>();
        runOnMainSync(()->{try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);result.set(f.get(activity));}catch(Exception e){error.set(e);}});
        if(error.get()!=null)throw error.get();return result.get();
    }
    private void call(String name)throws Exception{
        AtomicReference<Exception> error=new AtomicReference<>();
        runOnMainSync(()->{try{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}catch(Exception e){error.set(e);}});
        if(error.get()!=null)throw error.get();
    }
    @Override public void onStart(){
        JSONObject report=new JSONObject();boolean success=false;long started=System.currentTimeMillis();
        try{
            Context context=getTargetContext();
            report.put("scope","Android API "+Build.VERSION.SDK_INT+" / "+Build.SUPPORTED_ABIS[0]+" emulator; not the user's phone");
            report.put("version",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName);
            TunnelCheck.Proof before=TunnelCheck.request(context,false);report.put("direct_ip",before.ip);
            // This only pre-accepts the public-node notice in this isolated test device.
            context.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("public_node_notice",true).commit();
            context.getSharedPreferences("vpn.MainActivity",0).edit().putBoolean("public_node_notice",true).commit();
            activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long wait=SystemClock.elapsedRealtime()+90000;
            while((Boolean)value("busy")&&SystemClock.elapsedRealtime()<wait)SystemClock.sleep(500);
            if((Boolean)value("busy"))throw new IOException("Discovery did not finish in 90 seconds");
            call("connectBest");
            wait=SystemClock.elapsedRealtime()+330000;
            while(SystemClock.elapsedRealtime()<wait){
                if((Boolean)value("connected")){
                    TunnelCheck.Proof after=TunnelCheck.request(context,true);
                    report.put("vpn_ip",after.ip);report.put("https_bytes",after.bytes);
                    if(after.ip.equals(before.ip))throw new IOException("VPN egress equals direct egress; route not proven");
                    report.put("native_connected",true);report.put("android_vpn_network",true);report.put("egress_changed",true);
                    // Native service must stay up after the successful request.
                    SystemClock.sleep(3000);
                    if(!(Boolean)value("connected"))throw new IOException("VPN disconnected after HTTPS verification");
                    success=true;break;
                }
                if(!(Boolean)value("busy")&&!(Boolean)value("permissionPending")&&!(Boolean)value("connecting")&&!(Boolean)value("auto"))
                    throw new IOException("Candidates exhausted: "+value("status"));
                SystemClock.sleep(500);
            }
            if(!success)throw new IOException("No verified VPN connection within test deadline");
            Node first=(Node)value("selected");report.put("first_endpoint",first.ip+":"+first.port);report.put("first_source",first.source);
            report.put("first_attempts",value("attempt"));
            DirectNetworkProbe physical=DirectNetworkProbe.capture(context);
            android.net.ConnectivityManager cm=(android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkCapabilities physicalCaps=cm.getNetworkCapabilities(physical.network);
            if(physicalCaps==null||physicalCaps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN))
                throw new IOException("Direct probe selected the VPN network");
            if(physical.network.equals(cm.getActiveNetwork()))throw new IOException("Direct and VPN routes unexpectedly identical");
            physical.check(context,first);
            report.put("probe_bound_to_physical_network",true);report.put("direct_probe_label",physical.label);
            report.put("probe_while_vpn_active_ms",first.latencyMs);
            call("toggle");wait=SystemClock.elapsedRealtime()+12000;
            while((Boolean)value("connecting")&&SystemClock.elapsedRealtime()<wait)SystemClock.sleep(200);
            if((Boolean)value("connecting")||(Boolean)value("stopUnconfirmed"))throw new IOException("Disconnect did not complete");
            // Recreate the Activity to prove local evidence persists across a real lifecycle restart.
            runOnMainSync(()->activity.finish());SystemClock.sleep(1000);
            activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            call("connectBest");wait=SystemClock.elapsedRealtime()+35000;
            while((Boolean)value("busy")&&SystemClock.elapsedRealtime()<wait)SystemClock.sleep(200);
            if((Boolean)value("busy"))throw new IOException("Direct preflight did not complete");
            Node preferred=(Node)value("selected");
            if(!preferred.ip.equals(first.ip)||preferred.port!=first.port)throw new IOException("Verified node was not preferred after Activity restart");
            report.put("persisted_priority",true);success=false;wait=SystemClock.elapsedRealtime()+65000;
            while(SystemClock.elapsedRealtime()<wait){
                if((Boolean)value("connected")){
                    if((Integer)value("attempt")!=1)throw new IOException("Preferred node was not reused on first attempt");
                    TunnelCheck.Proof again=TunnelCheck.request(context,true);
                    if(again.ip.equals(before.ip))throw new IOException("Reconnection did not change egress");
                    report.put("reconnect_https_bytes",again.bytes);report.put("reconnect_vpn_ip",again.ip);report.put("reconnect_attempts",1);
                    success=true;break;
                }
                SystemClock.sleep(500);
            }
            if(!success)throw new IOException("Preferred-node reconnection did not verify");
        }catch(Throwable error){success=false;try{report.put("error",error.toString());}catch(JSONException ignored){}}
        finally{
            try{
                report.put("success",success);report.put("elapsed_ms",System.currentTimeMillis()-started);
                if(activity!=null){report.put("status",String.valueOf(value("status")));report.put("diagnostics",new JSONArray((java.util.List<?>)value("diagnostics")));}
                JSONArray errors=new JSONArray();
                for(LogItem item:VpnStatus.getlogbuffer())if(item.getLogtime()>=started){String line=DiagnosticLog.error(item.getString(getTargetContext()));if(line!=null)errors.put(line);}
                report.put("native_errors",errors);
                try(FileOutputStream file=getTargetContext().openFileOutput("connection-proof.json",Context.MODE_PRIVATE)){
                    file.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                Bitmap screenshot=getUiAutomation().takeScreenshot();
                if(screenshot!=null){try(FileOutputStream file=getTargetContext().openFileOutput("connection-proof.png",Context.MODE_PRIVATE)){screenshot.compress(Bitmap.CompressFormat.PNG,100,file);}screenshot.recycle();}
                if(activity!=null&&((Boolean)value("connected")||(Boolean)value("connecting")))call("toggle");
            }catch(Exception error){android.util.Log.e("AuroraProof","Failed writing proof",error);}
            Bundle result=new Bundle();result.putString("proof",report.toString());finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
}

