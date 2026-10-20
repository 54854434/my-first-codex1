package io.nekohasekai.sfa.nodeatlas;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.nekohasekai.sfa.constant.Alert;
import io.nekohasekai.sfa.constant.Status;

public final class NodeAtlasActivity extends Activity {
    private static final int VPN_PERMISSION_REQUEST = 2407;
    private WebView webView;
    private ExecutorService executor;
    private AndroidBridge bridge;
    private io.nekohasekai.sfa.bg.ServiceConnection serviceConnection;
    private String pendingVpnTitle;
    private String pendingVpnConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newCachedThreadPool();

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        bridge = new AndroidBridge(this, webView, executor);
        webView.addJavascriptInterface(bridge, "Android");
        webView.loadUrl("file:///android_asset/nodeatlas.html");
        setContentView(webView);
    }

    private void beginVpn(String title, String json) {
        if (json == null || json.trim().isEmpty()) {
            bridge.dispatchVpnStatus("error", "节点配置为空");
            return;
        }
        try {
            Intent prepare = VpnService.prepare(this);
            if (prepare != null) {
                pendingVpnTitle = title;
                pendingVpnConfig = json;
                bridge.dispatchVpnStatus("permission", "请在系统弹窗中允许 NodeAtlas 建立 VPN 连接");
                startActivityForResult(prepare, VPN_PERMISSION_REQUEST);
                return;
            }
            startVpnNow(title, json);
        } catch (Exception exception) {
            bridge.dispatchVpnStatus("error", messageOf(exception));
        }
    }

    private void startVpnNow(String title, String json) {
        bridge.dispatchVpnStatus("starting", "正在启动 VPN");
        NodeAtlasController.start(this, title, json, new NodeAtlasController.Callback() {
            @Override
            public void onResult(String status, String message) {
                bridge.dispatchVpnStatus(status, message == null ? "" : message);
                if ("starting".equals(status)) observeService();
            }
        });
    }

    private void observeService() {
        if (serviceConnection != null) return;
        serviceConnection = new io.nekohasekai.sfa.bg.ServiceConnection(this, new io.nekohasekai.sfa.bg.ServiceConnection.Callback() {
            @Override
            public void onServiceStatusChanged(Status status) {
                String value;
                if (status == Status.Started) value = "started";
                else if (status == Status.Starting) value = "starting";
                else if (status == Status.Stopping) value = "stopping";
                else value = "stopped";
                if (bridge != null) bridge.dispatchVpnStatus(value, "");
            }

            @Override
            public void onServiceAlert(Alert type, String message) {
                if (bridge != null) bridge.dispatchVpnStatus("error", message == null ? type.name() : message);
            }
        }, true);
        serviceConnection.connect();
    }

    private void stopVpn() {
        bridge.dispatchVpnStatus("stopping", "正在断开");
        NodeAtlasController.stop(this);
        bridge.dispatchVpnStatus("stopped", "已断开");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != VPN_PERMISSION_REQUEST) return;
        String title = pendingVpnTitle;
        String config = pendingVpnConfig;
        pendingVpnTitle = null;
        pendingVpnConfig = null;
        if (resultCode == RESULT_OK && config != null) {
            startVpnNow(title, config);
        } else {
            bridge.dispatchVpnStatus("stopped", "未获得系统 VPN 授权");
        }
    }

    private static String messageOf(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    @Override
    protected void onDestroy() {
        if (serviceConnection != null) {
            serviceConnection.disconnect();
            serviceConnection = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }

    private static final class AndroidBridge {
        private final Context context;
        private final WebView webView;
        private final ExecutorService executor;

        AndroidBridge(Context context, WebView webView, ExecutorService executor) {
            this.context = context;
            this.webView = webView;
            this.executor = executor;
        }

        @JavascriptInterface
        public String getPlatform() {
            return "android-vpn";
        }

        @JavascriptInterface
        public void startVpn(String title, String json) {
            ((NodeAtlasActivity) context).beginVpn(title, json);
        }

        @JavascriptInterface
        public void stopVpn() {
            ((NodeAtlasActivity) context).stopVpn();
        }

        @JavascriptInterface
        public void fetchText(String rawUrl, String requestId) {
            executor.execute(() -> {
                boolean ok = false;
                String body = "";
                String error = "";
                HttpURLConnection connection = null;
                try {
                    URI uri = URI.create(rawUrl.trim());
                    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                    if (!scheme.equals("https")) {
                        throw new IllegalArgumentException("只允许通过 HTTPS 抓取订阅源");
                    }
                    URL url = uri.toURL();
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(12000);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", "NodeAtlas/0.2 Android VPN");
                    connection.setRequestProperty("Accept", "text/plain, application/json, */*");
                    int status = connection.getResponseCode();
                    if (status < 200 || status >= 300) {
                        throw new IllegalStateException("HTTP " + status);
                    }
                    try (InputStream input = connection.getInputStream();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                        StringBuilder builder = new StringBuilder();
                        String line;
                        int bytes = 0;
                        while ((line = reader.readLine()) != null) {
                            bytes += line.getBytes(StandardCharsets.UTF_8).length + 1;
                            if (bytes > 2_000_000) {
                                throw new IllegalStateException("订阅内容超过 2 MB，已停止读取");
                            }
                            builder.append(line).append('\n');
                        }
                        body = builder.toString();
                    }
                    ok = true;
                } catch (Exception exception) {
                    error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
                dispatch("__nativeFetchResult", requestId, ok, body, error);
            });
        }

        @JavascriptInterface
        public void testTcp(String rawHost, int port, String requestId) {
            executor.execute(() -> {
                boolean ok = false;
                long elapsed = -1;
                String error = "";
                String host = rawHost == null ? "" : rawHost.trim();
                if (host.startsWith("[") && host.endsWith("]")) {
                    host = host.substring(1, host.length() - 1);
                }
                long start = System.nanoTime();
                try (Socket socket = new Socket()) {
                    if (port < 1 || port > 65535) {
                        throw new IllegalArgumentException("端口无效");
                    }
                    socket.connect(new InetSocketAddress(host, port), 3500);
                    elapsed = Math.max(1, (System.nanoTime() - start) / 1_000_000L);
                    ok = true;
                } catch (Exception exception) {
                    error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                }
                dispatch("__nativeTcpResult", requestId, ok, Long.toString(elapsed), error);
            });
        }

        @JavascriptInterface
        public void openUrl(String rawUrl) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl));
                context.startActivity(intent);
            } catch (Exception exception) {
                Toast.makeText(context, "没有可处理这个链接的应用", Toast.LENGTH_SHORT).show();
            }
        }

        @JavascriptInterface
        public void shareText(String title, String text) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            context.startActivity(Intent.createChooser(intent, title == null ? "分享" : title));
        }

        @JavascriptInterface
        public void copyText(String text) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("NodeAtlas", text));
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show();
            }
        }

        private void dispatch(String function, String requestId, boolean ok, String value, String error) {
            String script = "window." + function + "(" + quote(requestId) + "," + ok + "," + quote(value) + "," + quote(error) + ");";
            webView.post(() -> webView.evaluateJavascript(script, null));
        }

        void dispatchVpnStatus(String status, String message) {
            String script = "window.__nativeVpnResult(" + quote(status) + "," + quote(message) + ");";
            webView.post(() -> webView.evaluateJavascript(script, null));
        }

        private static String quote(String value) {
            try {
                return JSONObject.quote(value == null ? "" : value);
            } catch (Exception exception) {
                return "\"\"";
            }
        }
    }
}
