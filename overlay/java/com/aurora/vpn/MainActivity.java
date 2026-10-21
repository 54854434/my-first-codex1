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
import android.util.AtomicFile;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import de.blinkt.openvpn.LaunchVPN;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.*;

/** Single APK UI: the upstream native VPN engine and notifications live in this application. */
public final class MainActivity extends Activity implements VpnStatus.StateListener, VpnStatus.ByteCountListener, VpnStatus.LogListener {
    private static final int WHITE=Color.WHITE, INK=0xff142342, MUTED=0xff9baed3, CYAN=0xff60e4dd;
    private static final List<Node> nodes=new ArrayList<>();
    private static Node selected;
    private static long refreshedAt;
    private static String feedNote="尚未获取节点", feedError="";
    private static String probeLabel="直连网络尚未检测";
    private DirectNetworkProbe attemptNetwork;
    private boolean preflighting;
    private int preflightGeneration;
    private Future<?> preflightFuture;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService control=Executors.newSingleThreadExecutor();
    private final ExecutorService verification=new ThreadPoolExecutor(0,3,20,TimeUnit.SECONDS,new SynchronousQueue<>());
    private Future<?> verificationFuture;
    private TunnelCheck.Session verificationSession;
    private NodeHealth health;
    private String attemptScope="other";
    private boolean verifying;
    private long verifyDeadline, attemptStarted;
    private final AttemptWindow window=new AttemptWindow();
    private final List<String> diagnostics=new ArrayList<>();
    private String expectedProfile, lastState="", failure="";
    private boolean permissionPending, stopUnconfirmed, reattach=true;
    private LinearLayout body, rows;
    private TextView statusView, statsView, resultView;
    private String page="home", status="尚未连接", search="", speedResult="连接后可检测实际下载速度";
    private long bytesIn, bytesOut;
    private boolean busy, connecting, connected, auto, switching, bound, destroyed;
    private int attempt;
    private final List<Node> queue=new ArrayList<>();
    private IOpenVPNServiceInternal engine;
    private final Runnable watchdog=new Runnable(){public void run(){
        if(destroyed)return;
        if(verifying&&SystemClock.elapsedRealtime()>=verifyDeadline)endAttempt("隧道 HTTPS 验证超时",true);
        if(window.expired(SystemClock.elapsedRealtime())){
            if(window.stage==AttemptWindow.Stage.CONNECTING)endAttempt("25 秒内未建立隧道（"+lastState+"）",!lastState.equals("NONETWORK"));
            else {auto=false;switching=false;connecting=false;connected=false;stopUnconfirmed=true;window.clear();
                status="内核未确认停止，请到系统 VPN 设置断开";record(status);}
        }
        updateLabels();main.postDelayed(this,500);
    }};
    private final ServiceConnection engineConnection=new ServiceConnection(){
        public void onServiceConnected(ComponentName n,IBinder binder){engine=IOpenVPNServiceInternal.Stub.asInterface(binder);}
        public void onServiceDisconnected(ComponentName n){engine=null;}
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        health=NodeHealth.decode(getPreferences(0).getString("node_health",null),System.currentTimeMillis());
        getWindow().setStatusBarColor(0xff213f91);getWindow().setNavigationBarColor(0xff101a3b);
        if(state!=null)page=state.getString("page","home");
        Intent service=new Intent(this,OpenVPNService.class).setAction(OpenVPNService.START_SERVICE);
        bound=bindService(service,engineConnection,Context.BIND_AUTO_CREATE);
        VpnStatus.addStateListener(this);VpnStatus.addByteCountListener(this);VpnStatus.addLogListener(this);
        render();main.post(watchdog);
        if(!getPreferences(0).getBoolean("public_node_notice",false))notice();
        else if(nodes.isEmpty())refresh();
    }
    private void notice() {
        new AlertDialog.Builder(this).setTitle("免费公共节点说明")
            .setMessage("线路来自 VPN Gate 志愿者和 VPNBook 公共服务器。无需账号、订阅或 API 密钥。节点可能记录连接与流量，可能掉线，速度不保证。来源网页会收到你的 IP；节点检测会向列表中的公开端口发起连接。应用不会关闭证书校验。\n\n点击继续后自动获取和检测节点。")
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
        button("查看连接诊断",body,0xff293a62,()->showDiagnostics());
        resultView=label(speedResult,13,MUTED);body.addView(resultView);
        button("检测当前隧道下载速度（约 1 MB）",body,0xff293a62,()->speedTest());
        gap(body,18);body.addView(label("公共志愿者节点 · 可能记录流量\n免费，不保证可用性与速度",12,0xffffce85));
        body.addView(label(feedNote,12,MUTED));
        if(!feedError.isEmpty())body.addView(label(feedError,12,0xffffce85));
    }
    private void renderNodes(){
        body.addView(text("发现免费线路",26,INK));
        body.addView(text("VPN Gate / VPNBook · 免费公共线路\n延迟为绑定底层网络的 TCP 建连耗时；带宽为源站标称值"+"\n"+probeLabel,12,0xff72809b));
        body.addView(text(feedNote,12,0xff596d93));
        if(!feedError.isEmpty())body.addView(text(feedError,13,0xffa95637));
        EditText filter=new EditText(this);filter.setSingleLine(true);filter.setTextSize(15);filter.setHint("搜索国家 / IP");filter.setText(search);
        body.addView(filter);filter.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){search=s.toString();fillRows();}public void afterTextChanged(Editable e){}});
        button(busy?"正在获取和检测…":"刷新并检测（最多 40 条）",body,0xff244994,()->refresh());
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);body.addView(rows);fillRows();
    }
    private void fillRows(){
        if(rows==null)return;rows.removeAllViews();
        if(nodes.isEmpty())rows.addView(text(busy?"正在获取公开线路，请稍候…":"暂无线路。点击刷新；来源无法访问时会显示错误。",15,INK));
        int found=0;String currentProbeKey="";
        try{currentProbeKey=DirectNetworkProbe.capture(this).key;}catch(IOException ignored){}
        Set<String> favorites=getPreferences(0).getStringSet("favorites",Collections.emptySet());
        for(Node n:nodes){
            if(!n.matches(search))continue;
            found++;
            LinearLayout row=new LinearLayout(this);row.setPadding(dp(14),dp(12),dp(14),dp(12));row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(background(WHITE,18));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);rows.addView(row,rp);
            row.addView(text(n.flag()+"  ",25,INK));
            LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);row.addView(content,new LinearLayout.LayoutParams(0,-2,1));
            content.addView(text(n.country+(n==selected?"  ✓":""),17,INK));
            content.addView(text(n.ip+":"+n.port+" · TCP · "+n.source,12,0xff718099));
            content.addView(text(probeStatus(n,currentProbeKey)+(n.advertisedBps>0?"  /  标称 "+String.format(Locale.ROOT,"%.0f",n.advertisedBps/1000000.0)+" Mbps":"  /  来源未提供带宽"),11,0xff596d93));
            content.addView(text(n.cloudNote(System.currentTimeMillis()),11,0xff596d93));
            content.addView(text(health.note((connecting||connected)?attemptScope:networkScope(),n,System.currentTimeMillis()),11,0xff596d93));
            content.addView(text("公共节点 · 日志风险 · "+clock(n.checkedAt),10,0xffa46735));
            TextView star=text(favorites.contains(n.ip)?"★":"☆",24,0xffc39b43);row.addView(star);
            star.setOnClickListener(v->{Set<String> f=new HashSet<>(getPreferences(0).getStringSet("favorites",Collections.emptySet()));if(!f.add(n.ip))f.remove(n.ip);getPreferences(0).edit().putStringSet("favorites",f).apply();fillRows();});
            row.setOnClickListener(v->{if(connecting||connected){toast("请先断开当前线路");return;}selected=n;page="home";render();});
            row.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle(n.country+" · "+n.ip).setMessage("来源："+(n.source.equals("VPNBook")?BookSource.PAGE:Discovery.SOURCE)+"\n协议：OpenVPN TCP\n检测："+clock(n.checkedAt)+"\n端口可达不代表 VPN 可连。源站带宽不等于你的下载速度。志愿者可能记录流量，证书可证明配置与服务端匹配，但无法证明运营者可信。").setPositiveButton("知道了",null).show();return true;});
        }
        if(found==0&&!nodes.isEmpty()){
            rows.addView(text("没有匹配的国家或 IP，当前共有 "+nodes.size()+" 条候选",15,INK));
            button("清除搜索条件",rows,0xff244994,()->{search="";render();});
        }
    }
    private String probeStatus(Node n,String currentKey){
        if(n.checkedAt==0)return "尚未检测当前直连网络";
        if(currentKey.isEmpty()||!currentKey.equals(n.checkedNetwork))return "网络已变化，需重新检测";
        return n.latencyMs>0?n.latencyMs+" ms · 直连 TCP 建连（非隧道延迟）":"当前直连 TCP 未通过";
    }
    private String readFeed(InputStream input)throws IOException{
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int count;
            while((count=in.read(buffer))!=-1){if(out.size()+count>5*1024*1024)throw new IOException("Snapshot too large");out.write(buffer,0,count);}
            return out.toString("UTF-8");
        }
    }
    private FeedSnapshot localFeed(){
        String cache=null,seed=null;
        try{cache=readFeed(new FileInputStream(new File(getFilesDir(),"gate-cache.csv")));}catch(IOException ignored){}
        try{seed=readFeed(getAssets().open("gate-seed.csv"));}catch(IOException ignored){}
        return FeedSnapshot.newest(System.currentTimeMillis(),cache,seed);
    }
    private void cacheFeed(FeedSnapshot snapshot)throws IOException{
        AtomicFile cache=new AtomicFile(new File(getFilesDir(),"gate-cache.csv"));
        FileOutputStream output=cache.startWrite();
        try{output.write(snapshot.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));cache.finishWrite(output);}
        catch(IOException e){cache.failWrite(output);throw e;}
    }
    private void showFeed(FeedSnapshot snapshot,List<Node> list,String origin,String error,boolean done){
        if(destroyed)return;
        nodes.clear();nodes.addAll(list);refreshedAt=snapshot.fetchedAt;
        feedNote=origin+" · "+nodes.size()+" 条候选 · 获取时间 "+new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date(refreshedAt));
        feedError=error;
        if(selected!=null){String old=selected.ip;selected=null;for(Node n:nodes)if(n.ip.equals(old)){selected=n;break;}}
        if(done)busy=false;render();
    }
    private void refresh(){
        if(busy)return;if(connecting||connected){toast("请先断开本应用的线路再刷新；允许借助其他 VPN 获取列表");return;}
        busy=true;feedError="";render();worker.execute(()->{
            FeedSnapshot chosen=localFeed();
            if(chosen!=null){FeedSnapshot local=chosen;List<Node> preview=new ArrayList<>(local.nodes.subList(0,Math.min(40,local.nodes.size())));main.post(()->showFeed(local,preview,"本地公开节点快照（未验证连通）","正在尝试在线更新…",false));}
            String origin="在线公开节点源",error="";
            try{
                chosen=FeedSnapshot.online(Discovery.download(),System.currentTimeMillis());origin=Discovery.lastReport;
                try{cacheFeed(chosen);}catch(IOException ignored){error="在线列表已获取，但本地缓存写入失败。";}
            }catch(Exception e){
                origin="缓存 / APK 公开节点快照";
                error="在线更新失败："+e.getClass().getSimpleName()+(e.getMessage()==null?"":(" · "+e.getMessage()))+"。来源可能被阻断、DNS 失败或请求超时。";
            }
            if(chosen==null){String reason=error+"\n没有七天内的有效缓存或内置快照。可先开启手机现有 VPN，再点刷新；无需填写 API。";main.post(()->{if(destroyed)return;busy=false;feedError=reason;render();});return;}
            List<Node> tested;
            try{DirectNetworkProbe route=DirectNetworkProbe.capture(this);tested=route.measure(this,chosen.nodes);probeLabel=route.label+" · 网络 "+route.key;}catch(Exception e){tested=Discovery.balanced(chosen.nodes,40);for(Node n:tested){n.latencyMs=-1;n.checkedAt=0;n.checkedNetwork="";}probeLabel="直连检测未完成";error+="\n"+e.getClass().getSimpleName()+"："+e.getMessage()+"。连接前必须重新检测。";}
            int reachable=0;for(Node node:tested)if(node.latencyMs>0)reachable++;
            if(reachable==0)error+="\n当前直连网络没有检测通过的端口；不会把候选列表当作可用线路。";
            FeedSnapshot result=chosen;List<Node> resultNodes=tested;String resultOrigin=origin,resultError=error;
            main.post(()->showFeed(result,resultNodes,resultOrigin,resultError,true));
        });
    }
    private String networkScope(){
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
        if(caps==null||caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return "other";
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"wifi":caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"mobile":"other";
    }
    private void saveHealth(){getPreferences(0).edit().putString("node_health",health.encode(System.currentTimeMillis())).apply();}
    private boolean hasActiveVpn(){
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        Network n=cm.getActiveNetwork();NetworkCapabilities caps=cm.getNetworkCapabilities(n);
        return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }
    private void record(String message){
        diagnostics.add(clock(System.currentTimeMillis())+" "+message);
        if(diagnostics.size()>60)diagnostics.remove(0);
        getPreferences(0).edit().putString("last_diagnostics",String.join("\n",diagnostics)).apply();
    }
    private void showDiagnostics(){
        String report="极光 0.7 · OpenVPN TCP\n"+feedNote+"\n"+
            (diagnostics.isEmpty()?getPreferences(0).getString("last_diagnostics","尚无连接记录"):String.join("\n",diagnostics));
        new AlertDialog.Builder(this).setTitle("连接诊断（无密钥）").setMessage(report)
            .setPositiveButton("关闭",null).setNeutralButton("复制",(d,w)->{
                ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Aurora diagnostics",report));toast("诊断已复制");
            }).show();
    }
    private void toggle(){
        if(preflighting){preflightGeneration++;preflighting=false;busy=false;if(preflightFuture!=null)preflightFuture.cancel(true);status="已取消直连检测";render();return;}
        if(busy&&!connected&&!connecting){toast("正在获取节点，请稍候");return;}
        if(connecting||connected||permissionPending||stopUnconfirmed){
            auto=false;switching=false;permissionPending=false;
            if(window.stage!=AttemptWindow.Stage.STOPPING)endAttempt("用户取消",false);
        }else if(selected!=null){beginPreflight(false);}else connectBest();
    }
    private void connectBest(){
        if(connecting||connected||permissionPending||stopUnconfirmed){toast("已有连接，请先断开");return;}if(busy){toast("正在检测节点，请稍候");return;}
        beginPreflight(true);
    }
    private void beginPreflight(boolean automatic){
        if(nodes.isEmpty()&&selected==null){toast("没有候选线路，请先刷新");return;}
        DirectNetworkProbe route;
        try{route=DirectNetworkProbe.capture(this);}catch(IOException error){status=error.getMessage();record(status);render();return;}
        List<Node> candidates=automatic?new ArrayList<>(nodes):Collections.singletonList(selected);
        int generation=++preflightGeneration;preflighting=true;busy=true;
        status="检测当前"+route.label;record(status+" · 网络 "+route.key);render();
        preflightFuture=worker.submit(()->{
            try{
                List<Node> tested=route.measure(this,candidates);
                main.post(()->{
                    if(destroyed||generation!=preflightGeneration)return;
                    preflighting=false;busy=false;
                    if(!route.current(this)){status="网络已切换，检测结果作废，请重新连接";record(status);render();return;}
                    List<Node> reachable=new ArrayList<>();
                    for(Node n:tested)if(n.latencyMs>0&&route.key.equals(n.checkedNetwork))reachable.add(n);
                    probeLabel=route.label+" · 网络 "+route.key;
                    record(probeLabel+"：TCP 通过 "+reachable.size()+"/"+tested.size());
                    if(reachable.isEmpty()){
                        status="当前"+route.label+"无可达端口 · 未启动隧道";
                        record(status);feedError="已检测 "+tested.size()+" 条候选，TCP 均未通过。可切换 Wi-Fi/移动网络对照；如有其他 VPN 的阻止直连策略，先关闭后重测。";render();return;
                    }
                    attemptNetwork=route;attemptScope=route.scope;queue.clear();
                    queue.addAll(automatic?health.select(reachable,attemptScope,System.currentTimeMillis(),6):reachable);
                    if(queue.isEmpty()){status="可达候选均在失败冷却期，可稍后或手动尝试";render();return;}
                    attempt=0;auto=automatic;authorize();
                });
            }catch(Exception error){main.post(()->{
                if(destroyed||generation!=preflightGeneration)return;
                preflighting=false;busy=false;status="直连检测失败："+error.getClass().getSimpleName()+" · "+error.getMessage();record(status);render();
            });}
        });
    }
    private void authorize(){
        Intent permission=android.net.VpnService.prepare(this);
        if(permission!=null){permissionPending=true;status="等待系统 VPN 授权";render();startActivityForResult(permission,71);}
        else next();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==71){boolean wanted=permissionPending;permissionPending=false;
            if(result==RESULT_OK&&wanted)next();else{auto=false;status="已取消 VPN 授权";render();}}
    }
    private void next(){
        if(destroyed)return;
        if(attempt>=queue.size()){auto=false;connecting=false;status="候选均未连通，请刷新节点或更换网络";record(status);render();return;}
        cancelVerification();attemptStarted=System.currentTimeMillis();reattach=false;selected=queue.get(attempt++);connecting=true;connected=false;switching=false;bytesIn=bytesOut=0;
        failure="";lastState="启动内核";window.start(SystemClock.elapsedRealtime());
        status="正在连接";page="home";render();record("尝试 "+attempt+"/"+queue.size()+" "+selected.ip+":"+selected.port+" "+selected.country);
        DirectNetworkProbe route=attemptNetwork;Node candidate=selected;int token=window.generation;
        worker.execute(()->{
            try{
                if(route==null)throw new IOException("缺少直连检测，请重新连接");
                route.check(this,candidate);
                main.post(()->{if(!destroyed&&window.active(token)&&connecting&&window.stage==AttemptWindow.Stage.CONNECTING){record("连接前复检通过 · "+route.label+" · "+candidate.latencyMs+" ms");launchSelected();}});
            }catch(Exception error){main.post(()->{if(!destroyed&&window.active(token)&&connecting)endAttempt("连接前直连 TCP 复检失败："+error.getMessage(),route!=null&&route.current(this));});}
        });
    }
    private void launchSelected(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},20);
        try{
            ConfigParser parser=new ConfigParser();parser.parseConfig(new StringReader(selected.config));
            VpnProfile p=parser.convertProfile();p.mName=selected.source+" · "+selected.country+" · "+selected.ip;
            if(p.mUsername==null||p.mUsername.isEmpty())p.mUsername="vpn";if(p.mPassword==null||p.mPassword.isEmpty())p.mPassword="vpn";p.mUseDefaultRoute=true;p.mUseDefaultRoutev6=true;
            p.mBlockUnusedAddressFamilies=true;p.mAllowedAppsVpn.clear();p.mAllowedAppsVpnAreDisallowed=true;
            p.mOverrideDNS=false; // Retain the server-pushed DNS used by this tunnel.
            p.mConnectRetryMax="1";p.mConnectRetry="1";p.mConnectRetryMaxTime="1";
            for(Connection c:p.mConnections)c.mConnectTimeout=8;
            p.mUseCustomConfig=true;p.mCustomConfigOptions+="\nhand-window 15\n";
            expectedProfile=p.getUUIDString();ProfileManager.setTemporaryProfile(this,p);
            getPreferences(0).edit().putString("active_label",p.mName).apply();
            Intent launch=new Intent(this,LaunchVPN.class).setAction(Intent.ACTION_MAIN);
            launch.putExtra(LaunchVPN.EXTRA_KEY,expectedProfile);launch.putExtra(LaunchVPN.EXTRA_HIDELOG,true);
            startActivity(launch);
        }catch(Exception e){endAttempt("配置或启动失败："+e.getClass().getSimpleName(),true);}
    }
    private void endAttempt(String reason,boolean retry){
        if(window.stage==AttemptWindow.Stage.STOPPING)return;
        cancelVerification();failure=reason;record(reason);
        if(retry&&selected!=null){health.failure(attemptScope,selected,System.currentTimeMillis());saveHealth();}switching=retry&&auto&&attempt<queue.size();
        if(!switching)auto=false;
        connected=false;connecting=true;status="正在停止当前尝试";
        window.stopping(SystemClock.elapsedRealtime());int token=window.generation;
        IOpenVPNServiceInternal service=engine;
        if(service==null){auto=false;switching=false;record("内核服务不可用");return;}
        // Control calls never wait behind refresh or speed-test network IO.
        control.execute(()->{
            try{boolean hadProcess=service.stopVPN(false);main.post(()->{
                if(!destroyed&&window.active(token)&&window.rpcFinished(hadProcess))afterStopped();
            });}catch(Exception e){main.post(()->{if(window.active(token))record("停止内核失败："+e.getClass().getSimpleName());});}
        });updateLabels();
    }
    private void afterStopped(){
        stopUnconfirmed=false;boolean retry=switching&&auto;window.clear();connecting=false;connected=false;expectedProfile=null;
        switching=false;
        if(retry){status="正在换线";int token=window.generation;connecting=true;
            main.postDelayed(()->{if(!destroyed&&auto&&window.active(token))next();},1200);
        }else{auto=false;status=failure+" · 未连接";}
        updateLabels();
    }
    @Override public void updateState(String state,String log,int res,ConnectionStatus level,Intent intent){main.post(()->{
        if(destroyed)return;
        if(window.stage==AttemptWindow.Stage.STOPPING){
            if(level==ConnectionStatus.LEVEL_NOTCONNECTED&&window.stopped())afterStopped();return;
        }
        if(expectedProfile!=null&&!expectedProfile.equals(VpnStatus.getLastConnectedVPNProfile()))return;
        if(window.stage==AttemptWindow.Stage.IDLE){
            // Reattaching after Activity recreation may observe an existing real connection.
            if(reattach&&level==ConnectionStatus.LEVEL_CONNECTED){reattach=false;window.start(SystemClock.elapsedRealtime());window.connected();verifyTunnel();}
            else if(level==ConnectionStatus.LEVEL_NOTCONNECTED){connected=false;connecting=false;if(stopUnconfirmed){stopUnconfirmed=false;status="内核已停止 · 未连接";}}
            updateLabels();return;
        }
        if(!state.equals(lastState)){lastState=state;record("内核状态 "+state);}
        if(level==ConnectionStatus.LEVEL_CONNECTED){
            if(window.connected())verifyTunnel();
        }else if(level==ConnectionStatus.LEVEL_NOTCONNECTED||level==ConnectionStatus.LEVEL_AUTH_FAILED){
            endAttempt(level==ConnectionStatus.LEVEL_AUTH_FAILED?"认证失败":"内核结束连接（"+state+"）",true);
            if(window.stopped())afterStopped();
        }else if(level==ConnectionStatus.LEVEL_NONETWORK){cancelVerification();connected=false;connecting=true;
            if(window.stage==AttemptWindow.Stage.CONNECTED)window.start(SystemClock.elapsedRealtime());
            status="网络切换中，等待恢复（保留连接时限）";}
        else if(level==ConnectionStatus.LEVEL_VPNPAUSED){endAttempt("VPN 已暂停",false);}
        else if(level==ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT){status="内核等待授权或输入";}
        else {if(window.stage==AttemptWindow.Stage.CONNECTED){cancelVerification();window.start(SystemClock.elapsedRealtime());auto=false;}connecting=true;connected=false;status=state.equals("CONNECTRETRY")?"线路未响应，有限重试中":"正在建立隧道";}
        updateLabels();
    });}
    private void cancelVerification(){
        verifying=false;if(verificationSession!=null)verificationSession.cancel();
        if(verificationFuture!=null)verificationFuture.cancel(true);
        verificationSession=null;verificationFuture=null;
    }
    private void verifyTunnel(){
        verifying=true;connected=false;connecting=true;verifyDeadline=SystemClock.elapsedRealtime()+22000;
        status="隧道已建立，正在连续验证 HTTPS";record(status);int token=window.generation;
        TunnelCheck.Session session=new TunnelCheck.Session();verificationSession=session;long verifyStart=SystemClock.elapsedRealtime();
        try{verificationFuture=verification.submit(()->{
            try{TunnelCheck.Proof proof=TunnelCheck.request(this,true,session);main.post(()->{
                if(destroyed||!window.active(token)||!verifying)return;
                if(selected!=null){health.success(attemptScope,selected,System.currentTimeMillis(),(int)(SystemClock.elapsedRealtime()-verifyStart));saveHealth();}
                verifying=false;connected=true;connecting=false;auto=false;
                status="已验证联网 · "+proof.ip;record("VPN 网络内 HTTPS 成功，收到 "+proof.bytes+" 字节，出口 "+proof.ip);updateLabels();
            });}catch(Exception error){main.post(()->{
                if(!destroyed&&window.active(token)&&verifying)endAttempt("隧道 HTTPS 失败："+error.getClass().getSimpleName()+" · "+error.getMessage(),true);
            });}
        });}catch(RejectedExecutionException busyProbe){endAttempt("旧验证任务尚未释放，本轮已停止，请稍后重试",false);}
        updateLabels();
    }
    @Override public void newLog(LogItem item){
        if(item.getLogtime()<attemptStarted)return;
        String message=DiagnosticLog.error(item.getString(this));if(message==null)return;
        main.post(()->{if(!destroyed&&(connecting||verifying))record("原生错误："+message);});
    }
    @Override public void setConnectedVPN(String uuid){}
    @Override public void updateByteCount(long in,long out,long diffIn,long diffOut){main.post(()->{bytesIn=in;bytesOut=out;updateLabels();});}
    private void updateLabels(){if(statusView!=null){String detail="";
        if(window.stage==AttemptWindow.Stage.CONNECTING)detail="\n"+attempt+" / "+queue.size()+" · 剩余 "+window.seconds(SystemClock.elapsedRealtime())+" 秒";
        if(verifying)detail="\nHTTPS 验证剩余 "+Math.max(0,(verifyDeadline-SystemClock.elapsedRealtime()+999)/1000)+" 秒";
        if(window.stage==AttemptWindow.Stage.STOPPING)detail="\n停止等待 "+window.seconds(SystemClock.elapsedRealtime())+" 秒";
        statusView.setText(status+detail);}if(statsView!=null)statsView.setText(String.format(Locale.ROOT,"↓ %.2f MB    ↑ %.2f MB",bytesIn/1048576.0,bytesOut/1048576.0));}
    private void speedTest(){
        if(!connected){toast("请先连接 VPN");return;}if(busy){toast("已有任务进行中");return;}
        busy=true;speedResult="正在检测隧道内实际下载速度…";render();
        Node speedNode=selected;String speedScope=attemptScope;int speedGeneration=window.generation;
        worker.execute(()->{
            String result;double measuredMbps=0;
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
                    measuredMbps=total*8.0/((System.nanoTime()-start)/1e9)/1e6;
                result=String.format(Locale.ROOT,"本次下载 %.2f Mbps · %.0f KB（含建连耗时）",total*8.0/((System.nanoTime()-start)/1e9)/1e6,total/1024.0);
                }finally{c.disconnect();}
            }catch(Exception e){result="测速失败：未确认 VPN 网络或测试服务不可达";}
            String finalResult=result;double finalMbps=measuredMbps;main.post(()->{if(destroyed)return;busy=false;
                boolean same=connected&&selected==speedNode&&window.active(speedGeneration);
                try{same=same&&attemptNetwork!=null&&DirectNetworkProbe.capture(this).key.equals(attemptNetwork.key);}catch(IOException changed){same=false;}
                if(same&&finalMbps>0){health.speed(speedScope,speedNode,System.currentTimeMillis(),finalMbps);saveHealth();record("保存本机下载采样："+String.format(Locale.ROOT,"%.2f Mbps",finalMbps));}
                speedResult=same?finalResult:"连接已断开，测速结果作废";render();});
        });
    }
    private String clock(long t){return t==0?"未检测":new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(t));}
    private void toast(String s){if(!destroyed)Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void about(){new AlertDialog.Builder(this).setTitle("极光 · 免费线路版 0.7")
        .setMessage("同一 APK 内集成 OpenVPN for Android 内核，无需手填 API 或节点配置。\n\n公开源：外置实连筛选列表（优先）；VPN Gate（含同源备用镜像）、VPNBook（回退）\n云端采样不是手机速度；本机测速保存于本机并参与下一次优选。标称带宽来自源站，不保证实际速度。延迟仅是端口连接耗时。自动优选最多尝试 6 条不同线路，每条连接上限 25 秒，停止等待上限 8 秒。停止未确认时结束自动连接，提示在系统 VPN 设置断开；自动换线仅在此页面存活时运行。\n\n公共节点可能记录连接与流量，不提供匿名保证。隧道断开时系统可能恢复直连。可在 Android VPN 设置中按需启用始终开启和阻止无 VPN 连接。\n\n测试流量发往 Cloudflare，约 1 MB。无账号、支付、广告或自建后台。节点源被阻断时，无法保证首次获取成功。\n\n内核许可证与源代码见本项目 README。")
        .setPositiveButton("知道了",null).setNeutralButton("内核诊断",(d,w)->startActivity(new Intent(this,de.blinkt.openvpn.activities.MainActivity.class))).show();}
    @Override protected void onSaveInstanceState(Bundle s){super.onSaveInstanceState(s);s.putString("page",page);}
    @Override protected void onDestroy(){destroyed=true;auto=false;preflightGeneration++;if(preflightFuture!=null)preflightFuture.cancel(true);main.removeCallbacksAndMessages(null);cancelVerification();worker.shutdownNow();control.shutdownNow();verification.shutdownNow();VpnStatus.removeLogListener(this);VpnStatus.removeStateListener(this);VpnStatus.removeByteCountListener(this);if(bound)unbindService(engineConnection);super.onDestroy();}
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


