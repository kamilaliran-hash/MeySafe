package com.kamil.meysafe;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * MeySafe — private photo / video / file / note vault.
 * Developer: Kamil
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/www/index.html";
    private static final int REQ_PICK = 101, REQ_DELETE = 102, REQ_PERM = 103;

    private WebView web;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> fileCallback;
    private final ArrayList<Uri> lastPicked = new ArrayList<>();
    private int pendingDeleteCount = 0, deletedDirectly = 0;

    private static class SaveJob { OutputStream out; Uri uri; File file; String where; }
    private final HashMap<String, SaveJob> saves = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hide content from screenshots, screen recording and the recent-apps preview.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.parseColor("#0B1220"));
        getWindow().setNavigationBarColor(Color.parseColor("#111A2E"));

        assetLoader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0B1220"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !HOST.equals(request.getUrl().getHost());
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                ArrayList<String> mimes = new ArrayList<>();
                boolean any = false;
                for (String t : params.getAcceptTypes()) {
                    if (t == null) continue;
                    for (String p : t.split(",")) {
                        p = p.trim();
                        if (p.equals("application/octet-stream")) any = true;
                        if (p.contains("/")) mimes.add(p);
                    }
                }
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                if (!any && !mimes.isEmpty()) i.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toArray(new String[0]));
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(i, REQ_PICK);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "MeySafeNative");

        if (Build.VERSION.SDK_INT < 29 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
        }
        web.loadUrl(START_URL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK) {
            Uri[] result = null;
            lastPicked.clear();
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<Uri> list = new ArrayList<>();
                if (data.getClipData() != null) {
                    for (int k = 0; k < data.getClipData().getItemCount(); k++) list.add(data.getClipData().getItemAt(k).getUri());
                } else if (data.getData() != null) list.add(data.getData());
                lastPicked.addAll(list);
                result = list.toArray(new Uri[0]);
            }
            if (fileCallback != null) fileCallback.onReceiveValue(result);
            fileCallback = null;
            return;
        }
        if (requestCode == REQ_DELETE) {
            int n = deletedDirectly + (resultCode == RESULT_OK ? pendingDeleteCount : 0);
            notifyDeleted(n);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.msBack ? window.msBack() : ''", v -> {
            if (v == null || !v.contains("1")) moveTaskToBack(true);
        });
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private void notifyDeleted(int n) {
        lastPicked.clear();
        runOnUiThread(() -> web.evaluateJavascript("window.msOriginalsDeleted && window.msOriginalsDeleted(" + n + ")", null));
    }

    /** Convert a picker URI into a MediaStore URI if it points at a gallery item. */
    private Uri toMediaUri(Uri u) {
        try {
            String auth = u.getAuthority();
            if ("com.android.providers.media.documents".equals(auth)) {
                String[] parts = DocumentsContract.getDocumentId(u).split(":");
                long id = Long.parseLong(parts[1]);
                switch (parts[0]) {
                    case "image": return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    case "video": return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    case "audio": return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                }
            } else if (MediaStore.AUTHORITY.equals(auth) && !u.getPath().contains("/picker")) {
                return u;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private String folderFor(String mime) {
        if (mime.startsWith("image/")) return Environment.DIRECTORY_PICTURES;
        if (mime.startsWith("video/")) return Environment.DIRECTORY_MOVIES;
        if (mime.startsWith("audio/")) return Environment.DIRECTORY_MUSIC;
        return Environment.DIRECTORY_DOWNLOADS;
    }

    /** Methods callable from the web UI as window.MeySafeNative.* */
    private class Bridge {
        @JavascriptInterface
        public String beginSave(String name, String mime) {
            try {
                String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                String dir = folderFor(mime);
                SaveJob job = new SaveJob();
                job.where = dir + "/MeySafe";
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentResolver cr = getContentResolver();
                    Uri collection;
                    if (mime.startsWith("image/")) collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    else if (mime.startsWith("video/")) collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    else if (mime.startsWith("audio/")) collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    else collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, safe);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, job.where);
                    cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    job.uri = cr.insert(collection, cv);
                    if (job.uri == null) return null;
                    job.out = cr.openOutputStream(job.uri);
                } else {
                    File folder = new File(Environment.getExternalStoragePublicDirectory(dir), "MeySafe");
                    if (!folder.exists()) folder.mkdirs();
                    File f = new File(folder, safe);
                    int i = 1;
                    String base = safe, ext = "";
                    int dot = safe.lastIndexOf('.');
                    if (dot > 0) { base = safe.substring(0, dot); ext = safe.substring(dot); }
                    while (f.exists()) f = new File(folder, base + " (" + (i++) + ")" + ext);
                    job.file = f;
                    job.out = new FileOutputStream(f);
                }
                String h = UUID.randomUUID().toString();
                saves.put(h, job);
                return h;
            } catch (Exception e) {
                return null;
            }
        }

        @JavascriptInterface
        public boolean appendChunk(String handle, String b64) {
            SaveJob job = saves.get(handle);
            if (job == null) return false;
            try {
                job.out.write(Base64.decode(b64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String endSave(String handle) {
            SaveJob job = saves.remove(handle);
            if (job == null) return null;
            try {
                job.out.flush();
                job.out.close();
                if (job.uri != null && Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(job.uri, cv, null, null);
                } else if (job.file != null) {
                    MediaScannerConnection.scanFile(MainActivity.this, new String[]{job.file.getAbsolutePath()}, null, null);
                }
                return job.where;
            } catch (Exception e) {
                return null;
            }
        }

        @JavascriptInterface
        public boolean canDeleteOriginals() {
            return !lastPicked.isEmpty();
        }

        @JavascriptInterface
        public void deleteOriginals() {
            runOnUiThread(() -> {
                ContentResolver cr = getContentResolver();
                ArrayList<Uri> media = new ArrayList<>();
                int direct = 0;
                for (Uri u : new ArrayList<>(lastPicked)) {
                    Uri m = Build.VERSION.SDK_INT >= 30 ? toMediaUri(u) : null;
                    if (m != null) { media.add(m); continue; }
                    try {
                        if (DocumentsContract.isDocumentUri(MainActivity.this, u) && DocumentsContract.deleteDocument(cr, u)) { direct++; continue; }
                    } catch (Exception ignored) { }
                    try { if (cr.delete(u, null, null) > 0) direct++; } catch (Exception ignored) { }
                }
                if (Build.VERSION.SDK_INT >= 30 && !media.isEmpty()) {
                    try {
                        List<Uri> list = media;
                        PendingIntent pi = MediaStore.createDeleteRequest(cr, list);
                        pendingDeleteCount = media.size();
                        deletedDirectly = direct;
                        startIntentSenderForResult(pi.getIntentSender(), REQ_DELETE, null, 0, 0, 0);
                        return;
                    } catch (Exception ignored) { }
                }
                notifyDeleted(direct);
            });
        }

        @JavascriptInterface
        public void setDisguise(boolean on) {
            runOnUiThread(() -> {
                PackageManager pm = getPackageManager();
                ComponentName normal = new ComponentName(MainActivity.this, getPackageName() + ".LauncherDefault");
                ComponentName calc = new ComponentName(MainActivity.this, getPackageName() + ".LauncherCalc");
                pm.setComponentEnabledSetting(on ? calc : normal, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(on ? normal : calc, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            });
        }
    }
}
