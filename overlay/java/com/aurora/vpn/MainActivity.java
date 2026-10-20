package com.aurora.vpn;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import de.blinkt.openvpn.LaunchVPN;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.*;

/** Single APK UI: the upstream native VPN engine and notifications live in this application. */
public final class MainActivity extends Activity implements VpnStatus.StateListener, VpnStatus.ByteCountListener {
    private static final int WHITE=Color.WHITE, INK=0xff142342, MUTED=0xff9baed3, CYAN=0xff60e4dd;
    private static final List<Node> nodes=new ArrayList<>();
    private static Node selected;
    private static long refreshedAt;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout body, rows;
    private TextView statusView, statsView, resultView;
    private String page="home", status="尚未连接", search="", speedResult="连接后可检测实际下载速度";
    private long bytesIn, bytesOut;
    private boolean busy, connecting, connected, auto, switching, bound, destroyed, timerSet;
    private int attempt;
    private final List<Node> queue=new ArrayList<>();
    private IOpenVPNServiceInternal engine;
    private final Runnable timeout=()->{
        if(connecting && !connected) {
            status="连接超时，正在断开"; switching=auto && attempt<queue.size();
            if(!switching)auto=false;
            disconnectEngine(); updateLabels();
        }
    };
    private final ServiceConnection engineConnection=new ServiceConnection(){
        public void onServiceConnected(ComponentName n,IBinder binder){engine=IOpenVPNServiceInternal.Stub.asInterface(binder);}
        public void onServiceDisconnected(ComponentName n){engine=null;}
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff213f91);getWindow().setNavigationBarColor(0xff101a3b);
        if(state!=null)page=state.getString("page","home");
        Intent service=new Intent(this,OpenVPNService.class).setAction(OpenVPNService.START_SERVICE);
        bound=bindService(service,engineConnection,Context.BIND_AUTO_CREATE);
        VpnStatus.addStateListener(this);VpnStatus.addByteCountListener(this);
        render();
        if(!getPreferences(0).getBoolean("public_node_notice",false))notice();
        else if(nodes.isEmpty())refresh();
    }
    private void notice() {
        new AlertDialog.Builder(this).setTitle("免费公共节点说明")
            .setMessage("线路来自 VPN Gate 志愿者。无需账号、订阅或 API 密钥。节点可能记录连接与流量，可能掉线，速度不保证。来源网页会收到你的 IP；节点检测会向列表中的公开端口发起连接。应用不会关闭证书校验。\n\n点击继续后自动获取和检测节点。")
            .setPositiveButton("了解，获取免费节点",(d,w)->{getPreferences(0).edit().putBoolean("public_node_notice",true).apply();refresh();})
            .setNegativeButton("稍后",null).show();
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(4),dp(8),dp(4),dp(8));return t;}
    private TextView label(String value,int size,int color){TextView t=text(value,size,color);t.setGravity(Gravity.CENTER);return t;}
    private void gap(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private TextView button(String title,LinearLayout parent,int color,Runnable action){
        TextView t=label(title,16,color==CYAN?INK:WHITE);t.setTypeface(null,Typeface.BOLD);
        t.setBackground(background(color,22)); t.setPadding(dp(20),dp(16),dp(20),dp(16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);parent.addView(t,p);
        t.setOnClickListener(v->action.run());return t;
    }
    private void render(){
        boolean list=page.equals("nodes");
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(22),dp(8),dp(22),dp(12));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            root.setPadding(dp(22)+insets.getSystemWindowInsetLeft(),dp(8)+insets.getSystemWindowInsetTop(),
                dp(22)+insets.getSystemWindowInsetRight(),dp(12)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.setBackground(list?background(0xfff5f7fc,0):new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff23449a,0xff101731}));
        setContentView(root);
        LinearLayout nav=new LinearLayout(this);
        TextView brand=text("AURORA  /  极光",20,list?INK:WHITE);brand.setTypeface(null,Typeface.BOLD);
        nav.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView info=text("说明",14,list?INK:MUTED);nav.addView(info);info.setOnClickListener(v->about());root.addView(nav);
        LinearLayout tabs=new LinearLayout(this);
        for(String key:new String[]{"home","nodes"}){
            TextView t=label(key.equals("home")?"一键连接":"免费线路",16,list?INK:WHITE);
            if(page.equals(key)){t.setBackground(background(list?0xffe3eafb:0xff354e90,18));t.setTypeface(null,Typeface.BOLD);}
            tabs.addView(t,new LinearLayout.LayoutParams(0,dp(48),1));t.setOnClickListener(v->{page=key;render();});
        }
        root.addView(tabs);gap(root,12);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);
        statusView=null;statsView=null;resultView=null;
        if(list)renderNodes();else renderHome();
    }
    private void renderHome(){
        body.addView(label("PUBLIC NETWORK · 免费公共网络",11,MUTED));gap(body,12);
        PowerView power=new PowerView();LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(204),dp(204));pp.gravity=Gravity.CENTER;body.addView(power,pp);
        power.setContentDescription("连接或断开 VPN");power.setOnClickListener(v->toggle());gap(body,8);
        statusView=label(status,21,WHITE);body.addView(statusView);
        body.addView(label("OpenVPN  /  全局隧道",13,MUTED));
        TextView route=label(selected==null?((connected||connecting)?getPreferences(0).getString("active_label","当前 VPN 线路"):"🌐  自动选择可达线路  ›"):selected.flag()+"  "+selected.country+"  ›",18,WHITE);
        route.setPadding(dp(16),dp(20),dp(16),dp(20));route.setBackground(background(0xff2c3c68,28));body.addView(route);
        route.setOnClickListener(v->{page="nodes";render();});
        statsView=label("",14,MUTED);body.addView(statsView);updateLabels();
        button(busy?"正在获取和检测…":"刷新免费节点",body,0xff304b87,()->refresh());
        button("自动优选连接",body,CYAN,()->connectBest());
        resultView=label(speedResult,13,MUTED);body.addView(resultView);
        button("检测当前隧道下载速度（约 1 MB）",body,0xff293a62,()->speedTest());
        gap(body,18);body.addView(label("公共志愿者节点 · 可能记录流量\n免费，不保证可用性与速度",12,0xffffce85));
        body.addView(label(refreshedAt==0?"尚未获取节点":("VPN Gate · 更新于 "+clock(refreshedAt)),11,MUTED));
    }
    private void renderNodes(){
        body.addView(text("发现免费线路",26,INK));
        body.addView(text("VPN Gate · 公开志愿者节点\n延迟为本机 TCP 探测；带宽为源站标称值",12,0xff72809b));
        EditText filter=new EditText(this);filter.setSingleLine(true);filter.setTextSize(15);filter.setHint("搜索国家 / IP");filter.setText(search);
        body.addView(filter);filter.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){search=s.toString();fillRows();}public void afterTextChanged(Editable e){}});
        button(busy?"正在获取和检测…":"刷新并检测（最多 40 条）",body,0xff244994,()->refresh());
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);body.addView(rows);fillRows();
    }
    private void fillRows(){
        if(rows==null)return;rows.removeAllViews();
        if(nodes.isEmpty())rows.addView(text(busy?"正在获取公开线路，请稍候…":"暂无线路。点击刷新；来源无法访问时会显示错误。",15,INK));
        int found=0;
        Set<String> favorites=getPreferences(0).getStringSet("favorites",Collections.emptySet());
        for(Node n:nodes){
            if(!((n.country+" "+n.code+" "+n.ip).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))))continue;
            found++;
            LinearLayout row=new LinearLayout(this);row.setPadding(dp(14),dp(12),dp(14),dp(12));row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(background(WHITE,18));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);rows.addView(row,rp);
            row.addView(text(n.flag()+"  ",25,INK));
            LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);row.addView(content,new LinearLayout.LayoutParams(0,-2,1));
            content.addView(text(n.country+(n==selected?"  ✓":""),17,INK));
            content.addView(text(n.ip+":"+n.port+" · TCP",12,0xff718099));
            content.addView(text((n.latencyMs>0?n.latencyMs+" ms · 端口可达":"探测未通过")+"  /  标称 "+String.format(Locale.ROOT,"%.0f",n.advertisedBps/1000000.0)+" Mbps",11,n.latencyMs>0?0xff207e72:0xffa95637));
            content.addView(text("公共节点 · 日志风险 · "+clock(n.checkedAt),10,0xffa46735));
            TextView star=text(favorites.contains(n.ip)?"★":"☆",24,0xffc39b43);row.addView(star);
            star.setOnClickListener(v->{Set<String> f=new HashSet<>(getPreferences(0).getStringSet("favorites",Collections.emptySet()));if(!f.add(n.ip))f.remove(n.ip);getPreferences(0).edit().putStringSet("favorites",f).apply();fillRows();});
            row.setOnClickListener(v->{if(connecting||connected){toast("请先断开当前线路");return;}selected=n;page="home";render();});
            row.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle(n.country+" · "+n.ip).setMessage("来源："+Discovery.SOURCE+"\n协议：OpenVPN TCP\n检测："+clock(n.checkedAt)+"\n端口可达不代表 VPN 可连。源站带宽不等于你的下载速度。志愿者可能记录流量，证书可证明配置与服务端匹配，但无法证明运营者可信。").setPositiveButton("知道了",null).show();return true;});
        }
        if(found==0&&!nodes.isEmpty())rows.addView(text("没有匹配的国家或 IP",15,INK));
    }
    private void refresh(){
        if(busy)return;if(connecting||connected||hasActiveVpn()){toast("请先断开，再检测本地网络的节点延迟");return;}
        busy=true;render();worker.execute(()->{
            try{List<Node> discovered=Discovery.find();main.post(()->{
                if(destroyed)return;nodes.clear();nodes.addAll(discovered);refreshedAt=System.currentTimeMillis();
                if(selected!=null){String old=selected.ip;selected=null;for(Node n:nodes)if(n.ip.equals(old)){selected=n;break;}}
                busy=false;render();if(nodes.isEmpty())toast("源站没有兼容的 TCP 节点，稍后重试");
            });}catch(Exception e){main.post(()->{if(destroyed)return;busy=false;render();toast("获取失败：来源可能无法访问。已有列表不会被清空，请稍后重试。");});}
        });
    }
    private boolean hasActiveVpn(){
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        Network n=cm.getActiveNetwork();NetworkCapabilities caps=cm.getNetworkCapabilities(n);
        return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }
    private void toggle(){if(connecting||connected){auto=false;switching=false;main.removeCallbacks(timeout);disconnectEngine();}else if(selected!=null){queue.clear();queue.add(selected);attempt=0;auto=false;next();}else connectBest();}
    private void connectBest(){
        if(connecting||connected){toast("已有连接，请先断开");return;}if(busy){toast("正在检测节点，请稍候");return;}
        queue.clear();for(Node n:nodes)if(n.latencyMs>0 && queue.size()<3)queue.add(n);
        if(queue.isEmpty()){toast("暂无探测通过的线路，请先刷新");return;}attempt=0;auto=true;next();
    }
    private void next(){
        if(attempt>=queue.size()){auto=false;connecting=false;status="候选线路均未连通，请刷新";render();return;}
        selected=queue.get(attempt++);connecting=true;connected=false;switching=false;timerSet=false;bytesIn=bytesOut=0;
        status="正在连接 · "+attempt+" / "+queue.size();page="home";render();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},20);
        try{
            ConfigParser parser=new ConfigParser();parser.parseConfig(new StringReader(selected.config));
            VpnProfile p=parser.convertProfile();p.mName="VPN Gate · "+selected.country+" · "+selected.ip;
            p.mUsername="vpn";p.mPassword="vpn";p.mUseDefaultRoute=true;p.mUseDefaultRoutev6=true;
            p.mBlockUnusedAddressFamilies=true;p.mAllowedAppsVpn.clear();p.mAllowedAppsVpnAreDisallowed=true;
            p.mOverrideDNS=true;p.mDNS1="1.1.1.1";p.mDNS2="8.8.8.8";
            ProfileManager.setTemporaryProfile(this,p);
            getPreferences(0).edit().putString("active_label",p.mName).apply();
            Intent launch=new Intent(this,LaunchVPN.class).setAction(Intent.ACTION_MAIN);
            launch.putExtra(LaunchVPN.EXTRA_KEY,p.getUUIDString());launch.putExtra(LaunchVPN.EXTRA_HIDELOG,true);
            startActivity(launch);
        }catch(Exception e){connecting=false;auto=false;status="节点配置不兼容，请换线";render();}
    }
    private void disconnectEngine(){
        if(engine==null){toast("内核暂未就绪，请稍后重试");return;}
        status="正在断开…";updateLabels();
        worker.execute(()->{try{engine.stopVPN(false);}catch(Exception e){main.post(()->toast("断开失败，请在系统 VPN 设置中断开"));}});
    }
    @Override public void updateState(String state,String log,int res,ConnectionStatus level,Intent intent){main.post(()->{
        if(destroyed)return;
        if(level==ConnectionStatus.LEVEL_CONNECTED){connected=true;connecting=false;auto=false;switching=false;status="已连接";main.removeCallbacks(timeout);}
        else if(level==ConnectionStatus.LEVEL_NOTCONNECTED || level==ConnectionStatus.LEVEL_AUTH_FAILED){
            boolean retry=switching || (auto&&connecting&&attempt<queue.size());
            connected=false;connecting=false;main.removeCallbacks(timeout);timerSet=false;
            if(retry){switching=false;status="正在切换候选线路…";main.postDelayed(()->{if(!destroyed&&auto)next();},1200);}
            else {auto=false;status=level==ConnectionStatus.LEVEL_AUTH_FAILED?"认证失败，请换线":"尚未连接";}
        }else if(level==ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET || level==ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED || level==ConnectionStatus.LEVEL_START){
            connecting=true;connected=false;status="正在建立隧道 · "+state;
            if(!timerSet){timerSet=true;main.postDelayed(timeout,35000);}
        }else if(level==ConnectionStatus.LEVEL_NONETWORK){status="网络不可用，等待恢复";connected=false;}
        else if(level==ConnectionStatus.LEVEL_VPNPAUSED){status="连接已暂停";connected=false;}
        else if(level==ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT){status="等待系统授权";main.removeCallbacks(timeout);timerSet=false;}
        updateLabels();
    });}
    @Override public void setConnectedVPN(String uuid){}
    @Override public void updateByteCount(long in,long out,long diffIn,long diffOut){main.post(()->{bytesIn=in;bytesOut=out;updateLabels();});}
    private void updateLabels(){if(statusView!=null)statusView.setText(status);if(statsView!=null)statsView.setText(String.format(Locale.ROOT,"↓ %.2f MB    ↑ %.2f MB",bytesIn/1048576.0,bytesOut/1048576.0));}
    private void speedTest(){
        if(!connected){toast("请先连接 VPN");return;}if(busy){toast("已有任务进行中");return;}
        busy=true;speedResult="正在检测隧道内实际下载速度…";render();
        worker.execute(()->{
            String result;
            try {
                ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
                Network network=cm.getActiveNetwork();NetworkCapabilities caps=cm.getNetworkCapabilities(network);
                if(network==null||caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))throw new IOException("No active VPN");
                HttpURLConnection c=(HttpURLConnection)network.openConnection(new URL("https://speed.cloudflare.com/__down?bytes=1048576"));
                c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);
                long start=System.nanoTime(),total=0;
                try {
                    if(c.getResponseCode()!=200)throw new IOException("Speed test endpoint unavailable");
                    try(InputStream input=c.getInputStream()){
                        byte[] buffer=new byte[8192];int count;
                        while(total<1048576 && (count=input.read(buffer,0,(int)Math.min(buffer.length,1048576-total)))!=-1){
                            total+=count;if(System.nanoTime()-start>15000000000L)break;
                        }
                    }
                    if(total<32768)throw new IOException("Insufficient data");
                    result=String.format(Locale.ROOT,"本次下载 %.2f Mbps · %.0f KB（含建连耗时）",total*8.0/((System.nanoTime()-start)/1e9)/1e6,total/1024.0);
                }finally{c.disconnect();}
            }catch(Exception e){result="测速失败：未确认 VPN 网络或测试服务不可达";}
            String finalResult=result;main.post(()->{if(destroyed)return;busy=false;speedResult=connected?finalResult:"连接已断开，测速结果作废";render();});
        });
    }
    private String clock(long t){return t==0?"未检测":new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(t));}
    private void toast(String s){if(!destroyed)Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void about(){new AlertDialog.Builder(this).setTitle("极光 · 免费线路版 0.1")
        .setMessage("同一 APK 内集成 OpenVPN for Android 内核，无需手填 API 或节点配置。\n\n公开源：VPN Gate\n标称带宽来自源站，不保证实际速度。延迟仅是端口连接耗时。自动优选最多尝试 3 条线路，每条约 35 秒；自动换线仅在此页面存活时运行。\n\n公共节点可能记录连接与流量，不提供匿名保证。隧道断开时系统可能恢复直连。可在 Android VPN 设置中按需启用始终开启和阻止无 VPN 连接。\n\n测试流量发往 Cloudflare，约 1 MB。无账号、支付、广告或自建后台。节点源被阻断时，无法保证首次获取成功。\n\n内核许可证与源代码见本项目 README。")
        .setPositiveButton("知道了",null).setNeutralButton("内核诊断",(d,w)->startActivity(new Intent(this,de.blinkt.openvpn.activities.MainActivity.class))).show();}
    @Override protected void onSaveInstanceState(Bundle s){super.onSaveInstanceState(s);s.putString("page",page);}
    @Override protected void onDestroy(){destroyed=true;auto=false;main.removeCallbacksAndMessages(null);worker.shutdownNow();VpnStatus.removeStateListener(this);VpnStatus.removeByteCountListener(this);if(bound)unbindService(engineConnection);super.onDestroy();}
    private final class PowerView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        PowerView(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){
            float x=getWidth()/2f,y=getHeight()/2f,r=getWidth()*.41f;
            paint.setStyle(Paint.Style.FILL);paint.setColor(0x224fe0da);paint.setShadowLayer(dp(24),0,0,0x554ce5df);c.drawCircle(x,y,r*1.13f,paint);paint.clearShadowLayer();
            paint.setShader(new LinearGradient(0,y-r,0,y+r,new int[]{0xff65e8d8,0xff4e9ddd},null,Shader.TileMode.CLAMP));c.drawCircle(x,y,r,paint);paint.setShader(null);
            paint.setColor(WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(6));paint.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(x-r*.38f,y-r*.38f,x+r*.38f,y+r*.38f,-48,276,false,paint);c.drawLine(x,y-r*.52f,x,y-r*.08f,paint);paint.setStyle(Paint.Style.FILL);
        }
    }
}
