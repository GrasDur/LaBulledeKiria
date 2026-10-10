package fr.kiria.bulle;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.widget.FrameLayout;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private static final int PICK_IMAGE = 42;
    private String userAgent = "Mozilla/5.0 (Linux; Android) LaBulleDeKiria";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int bg = night ? Color.parseColor("#14101E") : Color.parseColor("#FBF7FF");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);
        webView = new WebView(this);
        webView.setBackgroundColor(bg);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        setupSystemBars(root, night, bg);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = webView.getSettings();
        try { userAgent = WebSettings.getDefaultUserAgent(this); } catch (Exception ignored) { }
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(true);

        webView.addJavascriptInterface(new Bridge(), "KiriaAndroid");
        // Choix d'une image dans la galerie (couverture personnalisée)
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                String[] types = params.getAcceptTypes();
                boolean image = types != null && types.length > 0 && types[0] != null && types[0].startsWith("image");
                pick.setType(image ? "image/*" : "*/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, image ? "Choisir une couverture" : "Choisir la sauvegarde"), PICK_IMAGE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(START_URL);
    }

    private void setupSystemBars(View root, boolean night, int bg) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return WindowInsets.CONSUMED;
            });
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(night ? 0 : light, light);
            }
        } else {
            root.setFitsSystemWindows(true);
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
            if (!night && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                root.setSystemUiVisibility(flags);
            }
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    private static void writeFile(File f, String text) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Écrit un fichier dans Téléchargements/LaBulleDeKiria. Retourne l'emplacement lisible. */
    private String writeDownload(String name, String text, boolean reuse) throws Exception {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver cr = getContentResolver();
            SharedPreferences prefs = getSharedPreferences("backup", MODE_PRIVATE);
            if (reuse) {
                String saved = prefs.getString("auto_uri", null);
                if (saved != null) {
                    try (OutputStream o = cr.openOutputStream(Uri.parse(saved), "wt")) {
                        if (o != null) { o.write(data); return "Téléchargements/LaBulleDeKiria/" + name; }
                    } catch (Exception ignored) { }
                }
            }
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LaBulleDeKiria");
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("création impossible");
            try (OutputStream o = cr.openOutputStream(uri, "wt")) {
                if (o == null) throw new Exception("écriture impossible");
                o.write(data);
            }
            if (reuse) prefs.edit().putString("auto_uri", uri.toString()).apply();
            return "Téléchargements/LaBulleDeKiria/" + name;
        } else {
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "LaBulleDeKiria");
            dir.mkdirs();
            File f = new File(dir, name);
            writeFile(f, text);
            return f.getAbsolutePath();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("window.kiriaBack ? window.kiriaBack() : false", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    /** Petites fonctions natives accessibles depuis la page (window.KiriaAndroid). */
    public class Bridge {
        /** Ancienne méthode : partage en texte (gardée pour compatibilité). */
        @JavascriptInterface
        public void share(String subject, String text) {
            shareBackup(text);
        }

        /** Partage la sauvegarde comme un FICHIER (pas de limite de taille). */
        @JavascriptInterface
        public String shareBackup(String json) {
            try {
                File dir = new File(getCacheDir(), "export");
                dir.mkdirs();
                File f = new File(dir, "LaBulleDeKiria-sauvegarde.json");
                writeFile(f, json);
                Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                runOnUiThread(() -> {
                    try {
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("application/json");
                        send.putExtra(Intent.EXTRA_STREAM, uri);
                        send.putExtra(Intent.EXTRA_SUBJECT, "La Bulle de Kiria – sauvegarde");
                        send.setClipData(ClipData.newRawUri("sauvegarde", uri));
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(send, "Envoyer la sauvegarde"));
                    } catch (Exception e) {
                        webView.evaluateJavascript("toast('Partage impossible')", null);
                    }
                });
                return "ok";
            } catch (Exception e) {
                return "erreur: " + e.getMessage();
            }
        }

        /** Enregistre la sauvegarde dans Téléchargements/LaBulleDeKiria (conservée même si l'app est désinstallée). */
        @JavascriptInterface
        public String saveBackup(String name, String json) {
            try {
                return writeDownload(name, json, false);
            } catch (Exception e) {
                return "erreur: " + e.getMessage();
            }
        }

        /** Sauvegarde automatique (même fichier, remplacé à chaque fois). */
        @JavascriptInterface
        public String autoBackup(String json) {
            try {
                return writeDownload("sauvegarde-auto.json", json, true);
            } catch (Exception e) {
                return "erreur: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public String copyText(String text) {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("La Bulle de Kiria", text));
                return "ok";
            } catch (Exception e) {
                return "erreur: " + e.getMessage();
            }
        }

        /** Requête HTTP GET native (sans restriction CORS), limitée aux catalogues de livres.
         *  Plusieurs tentatives : HTTPS navigateur, HTTPS simple, puis HTTP (certains serveurs coupent la connexion). */
        @JavascriptInterface
        public void httpGet(final String id, final String url) {
            new Thread(() -> {
                int code = 0;
                String body = null;
                StringBuilder errs = new StringBuilder();
                String[][] attempts = {
                        {url, userAgent},
                        {url, null},
                        {url.replaceFirst("^https://", "http://"), userAgent}
                };
                for (String[] at : attempts) {
                    HttpURLConnection c = null;
                    try {
                        URL u = new URL(at[0]);
                        String host = u.getHost();
                        if (!(host.endsWith("bnf.fr") || host.endsWith("googleapis.com") || host.endsWith("openlibrary.org") || host.equals("itunes.apple.com"))) {
                            throw new SecurityException("hôte non autorisé");
                        }
                        c = (HttpURLConnection) u.openConnection();
                        c.setConnectTimeout(12000);
                        c.setReadTimeout(20000);
                        if (at[1] != null) c.setRequestProperty("User-Agent", at[1]);
                        c.setRequestProperty("Accept", "application/xml,text/xml,application/json,*/*");
                        c.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9");
                        c.setInstanceFollowRedirects(true);
                        code = c.getResponseCode();
                        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                        body = null;
                        if (in != null) {
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            byte[] buf = new byte[16384];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                            in.close();
                            body = out.toString("UTF-8");
                        }
                        if (code >= 200 && code < 300) break;
                        errs.append("HTTP ").append(code).append(" ; ");
                    } catch (Exception e) {
                        code = 0;
                        body = null;
                        errs.append(e.getClass().getSimpleName()).append(" ; ");
                    } finally {
                        if (c != null) c.disconnect();
                    }
                }
                final String js = "window.__kiriaHttp(" + JSONObject.quote(id) + "," + code + ","
                        + (body == null ? "null" : JSONObject.quote(body)) + "," + JSONObject.quote(errs.toString()) + ")";
                runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(js, null); });
            }).start();
        }

        @JavascriptInterface
        public void keepAwake(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }
    }
}
