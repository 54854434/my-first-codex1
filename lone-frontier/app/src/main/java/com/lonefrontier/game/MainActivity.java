package com.lonefrontier.game;

import android.app.Activity;
import android.os.Bundle;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Local-only game host. No network, account, advertising or tracking permissions. */
public class MainActivity extends Activity {
    private WebView web;
    private SharedPreferences saves;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        saves = getSharedPreferences("frontier", MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(16,21,18));
        getWindow().setNavigationBarColor(Color.rgb(16,21,18));
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(16,21,18));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            }
        });
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(false);
        settings.setTextZoom(100);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }
        });
        web.addJavascriptInterface(new SaveBridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private class SaveBridge {
        @JavascriptInterface public String load() { return saves.getString("save", ""); }
        @JavascriptInterface public boolean save(String json) {
            if (json == null || json.length() > 1048576) return false;
            return saves.edit().putString("save", json).commit();
        }
        @JavascriptInterface public void copy(final String text) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("孤军存档", text));
                }
            });
        }
    }

    @Override protected void onPause() {
        if (web != null) {
            web.evaluateJavascript("window.nativePause&&window.nativePause()", null);
            web.onPause();
        }
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            web.evaluateJavascript("window.nativeResume&&window.nativeResume()", null);
        }
    }
    @Override public void onBackPressed() {
        if (web != null) web.evaluateJavascript("window.nativeBack&&window.nativeBack()", null);
    }
    @Override protected void onDestroy() {
        if (web != null) { web.removeJavascriptInterface("Android"); web.destroy(); web = null; }
        super.onDestroy();
    }
}
