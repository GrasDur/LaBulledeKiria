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
import android.webkit.CookieManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.view.ViewGroup;
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
    private FrameLayout rootLayout;
    /* Navigateur invisible pour lire la Fnac (passe mieux les protections anti-robots qu'une requête simple) */
    private WebView fnacView;
    private String fnacId = null;
    private boolean fnacDelivered = true;
    private LinearLayout fnacOverlay;
    private final Handler ui = new Handler(Looper.getMainLooper());
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
        rootLayout = root;
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

    // ---------- Fnac ----------
    private static boolean isFnac(String url) {
        try { String h = Uri.parse(url).getHost(); return h != null && (h.equals("fnac.com") || h.endsWith(".fnac.com")); } catch (Exception e) { return false; }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void ensureFnacView() {
        if (fnacView != null) return;
        fnacView = new WebView(this);
        WebSettings fs = fnacView.getSettings();
        fs.setJavaScriptEnabled(true);
        fs.setDomStorageEnabled(true);
        fs.setLoadsImagesAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(fnacView, true);
        fnacView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                final String id = fnacId;
                if (id == null || fnacDelivered) return;
                // laisser le temps aux scripts de la page (et à la vérification anti-robot)
                ui.postDelayed(() -> deliverFnac(id, 0), 1800);
            }
        });
        // le WebView doit être attaché pour exécuter correctement les scripts : on le met minuscule et invisible
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(2, 2);
        fnacView.setAlpha(0f);
        rootLayout.addView(fnacView, 0, lp);
    }

    private void deliverFnac(final String id, final int tries) {
        if (fnacView == null || !id.equals(fnacId) || fnacDelivered) return;
        fnacView.evaluateJavascript("(function(){return document.documentElement ? document.documentElement.outerHTML : ''})()", value -> {
            if (!id.equals(fnacId) || fnacDelivered) return;
            String v = value == null ? "\"\"" : value;
            // page encore vide ou en cours de vérification : on réessaie un peu
            if (v.length() < 3000 && tries < 4) { ui.postDelayed(() -> deliverFnac(id, tries + 1), 1500); return; }
            fnacDelivered = true;
            webView.evaluateJavascript("window.__kiriaHttp(" + JSONObject.quote(id) + ",200," + v + ",'')", null);
        });
    }

    private void showFnacOverlay(String url) {
        ensureFnacView();
        if (fnacOverlay != null) return;
        rootLayout.removeView(fnacView);
        fnacView.setAlpha(1f);
        fnacView.getSettings().setLoadsImagesAutomatically(true);
        fnacOverlay = new LinearLayout(this);
        fnacOverlay.setOrientation(LinearLayout.VERTICAL);
        fnacOverlay.setBackgroundColor(Color.WHITE);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(24, 12, 12, 12);
        bar.setBackgroundColor(Color.parseColor("#8B5CF6"));
        TextView t = new TextView(this);
        t.setText("Fnac – valide la vérification puis appuie sur Terminé");
        t.setTextColor(Color.WHITE);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button done = new Button(this);
        done.setText("Terminé");
        done.setOnClickListener(v -> closeFnacOverlay());
        bar.addView(done);
        fnacOverlay.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fnacOverlay.addView(fnacView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rootLayout.addView(fnacOverlay, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        fnacId = null; fnacDelivered = true;
        fnacView.loadUrl(url);
    }

    private void closeFnacOverlay() {
        if (fnacOverlay == null) return;
        CookieManager.getInstance().flush();
        fnacOverlay.removeView(fnacView);
        rootLayout.removeView(fnacOverlay);
        fnacOverlay = null;
        fnacView.setAlpha(0f);
        fnacView.getSettings().setLoadsImagesAutomatically(false);
        rootLayout.addView(fnacView, 0, new FrameLayout.LayoutParams(2, 2));
        webView.evaluateJavascript("window.__fnacClosed && window.__fnacClosed()", null);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (fnacOverlay != null) { closeFnacOverlay(); return; }
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
        /** Charge une page Fnac dans le navigateur invisible et renvoie son HTML à __kiriaHttp(id, …). */
        @JavascriptInterface
        public void fnacGet(final String id, final String url) {
            if (!isFnac(url)) { ui.post(() -> webView.evaluateJavascript("window.__kiriaHttp(" + JSONObject.quote(id) + ",0,null,'hôte non autorisé')", null)); return; }
            ui.post(() -> {
                ensureFnacView();
                if (fnacOverlay != null) { webView.evaluateJavascript("window.__kiriaHttp(" + JSONObject.quote(id) + ",0,null,'vérification en cours')", null); return; }
                fnacId = id; fnacDelivered = false;
                fnacView.stopLoading();
                fnacView.loadUrl(url);
            });
        }

        /** Affiche la Fnac en plein écran (pour valider une vérification anti-robot). */
        @JavascriptInterface
        public void fnacShow(final String url) {
            if (!isFnac(url)) return;
            ui.post(() -> showFnacOverlay(url));
        }
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
                        if (!(host.endsWith("bnf.fr") || host.endsWith("googleapis.com") || host.endsWith("openlibrary.org") || host.equals("itunes.apple.com") || host.endsWith("fnac-static.com"))) {
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
