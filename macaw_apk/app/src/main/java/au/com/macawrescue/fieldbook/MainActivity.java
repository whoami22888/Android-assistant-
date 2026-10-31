package au.com.macawrescue.fieldbook;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final int PICK_MEDIA = 1001;
    private static final int SAVE_DOCUMENT = 1002;
    private static final String HOME = "https://appassets.androidplatform.net/index.html";
    private WebView webView;
    private ValueCallback<Uri[]> chooserCallback;
    private byte[] pendingData;
    private String pendingMime;
    private String pendingFilename;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setSavePassword(false);
        webView.addJavascriptInterface(new FileExports(), "MacawNative");
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (!"appassets.androidplatform.net".equals(u.getHost()) || !"https".equals(u.getScheme())) return null;
                String path = u.getPath();
                if (!"/index.html".equals(path)) return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", java.util.Collections.emptyMap(), new java.io.ByteArrayInputStream(new byte[0]));
                try {
                    InputStream input = getAssets().open("index.html");
                    return new WebResourceResponse("text/html", "UTF-8", input);
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "UTF-8", new java.io.ByteArrayInputStream("Failed to load offline fieldbook".getBytes(StandardCharsets.UTF_8)));
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost())) return false;
                if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception e) { Toast.makeText(MainActivity.this, "No browser available", Toast.LENGTH_SHORT).show(); }
                }
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (chooserCallback != null) chooserCallback.onReceiveValue(null);
                chooserCallback = callback;
                try {
                    Intent i = params.createIntent();
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(i, PICK_MEDIA);
                    return true;
                } catch (Exception e) { chooserCallback = null; return false; }
            }
        });
        webView.loadUrl(HOME);
    }

    public final class FileExports {
        @JavascriptInterface public void saveText(String filename, String mime, String data) {
            if (data == null || data.length() > 10_000_000) return;
            queueExport(filename, mime, data.getBytes(StandardCharsets.UTF_8));
        }
        @JavascriptInterface public void savePng(String filename, String base64) {
            if (base64 == null || base64.length() > 12_000_000) return;
            try { queueExport(filename, "image/png", Base64.decode(base64, Base64.DEFAULT)); } catch (IllegalArgumentException ignored) { }
        }
        private void queueExport(String filename, String mime, byte[] payload) {
            runOnUiThread(() -> {
                if (pendingData != null) { Toast.makeText(MainActivity.this, "Finish current export first", Toast.LENGTH_SHORT).show(); return; }
                pendingData = payload;
                pendingMime = (mime != null && (mime.equals("application/json") || mime.startsWith("text/") || mime.equals("image/png"))) ? mime : "application/octet-stream";
                pendingFilename = filename == null ? "macaw-export.txt" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(pendingMime);
                intent.putExtra(Intent.EXTRA_TITLE, pendingFilename);
                try { startActivityForResult(intent, SAVE_DOCUMENT); }
                catch(Exception e) { pendingData=null; Toast.makeText(MainActivity.this,"Cannot open Save dialog",Toast.LENGTH_LONG).show(); }
            });
        }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == PICK_MEDIA && chooserCallback != null) {
            chooserCallback.onReceiveValue(result == RESULT_OK ? WebChromeClient.FileChooserParams.parseResult(result, data) : null);
            chooserCallback = null;
        } else if (code == SAVE_DOCUMENT) {
            if (result == RESULT_OK && data != null && data.getData() != null && pendingData != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out != null) { out.write(pendingData); out.flush(); Toast.makeText(this, "Saved " + pendingFilename, Toast.LENGTH_SHORT).show(); }
                } catch (Exception e) { Toast.makeText(this, "Export failed: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show(); }
            }
            pendingData = null; pendingMime=null; pendingFilename=null;
        }
    }
    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (chooserCallback != null) { chooserCallback.onReceiveValue(null); chooserCallback = null; }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}