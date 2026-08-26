package com.lifestyle.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

/**
 * A WebView shell around the Lifestyle web app.
 *
 * The page is served from bundled assets through WebViewAssetLoader, so it runs
 * on a fixed local origin (https://appassets.androidplatform.net). That matters:
 * addJavascriptInterface exposes the SMS bridge to whatever the WebView has
 * loaded, so the WebView must never load anything but our own bundled files.
 * Any other URL is handed to the system browser instead.
 */
public class MainActivity extends Activity {

    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final int REQ_SMS = 4011;

    private WebView webView;
    private WebViewAssetLoader assetLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        // Nothing is loaded over the network, so leave every remote door shut.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMediaPlaybackRequiresUserGesture(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (url != null && ORIGIN.equals(url.getScheme() + "://" + url.getAuthority())) {
                    return false; // our own page, let it load
                }
                // Anything else opens outside, never inside the bridged WebView.
                try {
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, url));
                } catch (Exception ignored) {
                    // no handler installed; do nothing
                }
                return true;
            }
        });

        webView.addJavascriptInterface(new SmsBridge(this), "AndroidSms");
        webView.loadUrl(ORIGIN + "/assets/web/index.html");

        setContentView(webView);
    }

    void requestSmsPermission() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            notifyPermission(true);
            return;
        }
        requestPermissions(new String[]{Manifest.permission.READ_SMS}, REQ_SMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_SMS) return;
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        notifyPermission(granted);
    }

    private void notifyPermission(final boolean granted) {
        if (webView == null) return;
        final String js = "window.dispatchEvent(new CustomEvent('android-sms-permission',{detail:"
                + (granted ? "true" : "false") + "}));";
        webView.post(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript(js, null);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
