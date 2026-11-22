package com.yusaa.videomerkezi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Video Merkezi: sunucudaki video uretim panelini WebView ile acar.
 * Acilista /app/version.json kontrolu yapar; yeni surum varsa APK'yi
 * indirip PackageInstaller oturumu ile kurulumu baslatir (in-app update).
 * Sunucu URL'si ayarlardan degistirilebilir (DNS yoksa elle girilir).
 */
public class MainActivity extends Activity {

    private static final String PREFS = "video_merkezi";
    private static final String KEY_URL = "server_url";
    private static final String DEFAULT_URL = "https://video.yusaa.com.tr";

    private WebView webView;
    private SharedPreferences prefs;
    private boolean updateChecked = false;
    private long pendingDownloadId = -1;
    private BroadcastReceiver downloadReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!updateChecked) {
                    updateChecked = true;
                    checkForUpdate();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    promptServerUrl("Sunucuya ulasilamadi. Adresi kontrol edin:");
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        loadServer();
    }

    private String serverUrl() {
        String u = prefs.getString(KEY_URL, DEFAULT_URL);
        if (u == null || u.trim().isEmpty()) return DEFAULT_URL;
        u = u.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private void loadServer() {
        updateChecked = false;
        webView.loadUrl(serverUrl());
    }

    // ---- Menu: sunucu adresi degistirme ----

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Sunucu Adresi");
        menu.add(0, 2, 1, "Yenile");
        menu.add(0, 3, 2, "Guncellemeyi Denetle");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == 1) {
            promptServerUrl("Sunucu adresi:");
            return true;
        } else if (id == 2) {
            loadServer();
            return true;
        } else if (id == 3) {
            checkForUpdate();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void promptServerUrl(String message) {
        final EditText input = new EditText(this);
        input.setText(serverUrl());
        new AlertDialog.Builder(this)
                .setTitle("Video Merkezi")
                .setMessage(message)
                .setView(input)
                .setPositiveButton("Kaydet", (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (!v.isEmpty()) {
                        if (!v.startsWith("http://") && !v.startsWith("https://")) {
                            v = "https://" + v;
                        }
                        prefs.edit().putString(KEY_URL, v).apply();
                        loadServer();
                    }
                })
                .setNegativeButton("Vazgec", null)
                .show();
    }

    // ---- In-app update ----

    private void checkForUpdate() {
        final String base = serverUrl();
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL(base + "/app/version.json");
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Accept", "application/json");
                int code = c.getResponseCode();
                if (code != 200) return;
                BufferedReader r = new BufferedReader(
                        new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();

                JSONObject j = new JSONObject(sb.toString());
                final int remoteCode = j.optInt("versionCode", -1);
                final String remoteName = j.optString("versionName", "");
                String apkUrl = j.optString("apkUrl", "");
                final String notes = j.optString("notes", "");

                int localCode = getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionCode;
                if (remoteCode > localCode && !apkUrl.isEmpty()) {
                    if (apkUrl.startsWith("/")) apkUrl = base + apkUrl;
                    final String finalApk = apkUrl;
                    runOnUiThread(() -> askUpdate(remoteName, notes, finalApk));
                }
            } catch (Exception ignored) {
                // Ag yoksa / DNS henuz yoksa sessiz gec
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private void askUpdate(String remoteName, String notes, String apkUrl) {
        String msg = "Yeni surum mevcut: " + remoteName + "\n" + notes
                + "\n\nIndirip kurulsun mu?";
        new AlertDialog.Builder(this)
                .setTitle("Guncelleme")
                .setMessage(msg)
                .setPositiveButton("Indir ve Kur", (d, w) -> downloadAndInstall(apkUrl))
                .setNegativeButton("Sonra", null)
                .show();
    }

    private void downloadAndInstall(String apkUrl) {
        // Android 8+: bilinmeyen kaynak izni gerekli
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(this,
                        "Kurulum icin 'bilinmeyen kaynak' izni verin",
                        Toast.LENGTH_LONG).show();
                try {
                    startActivity(new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {
                }
                return;
            }
        }
        try {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl));
            req.setTitle("Video Merkezi guncellemesi");
            req.setDescription("Yeni surum indiriliyor...");
            req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(
                    this, Environment.DIRECTORY_DOWNLOADS, "video-merkezi-update.apk");
            pendingDownloadId = dm.enqueue(req);
            Toast.makeText(this, "Indirme basladi", Toast.LENGTH_SHORT).show();

            if (downloadReceiver == null) {
                downloadReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context ctx, Intent intent) {
                        long id = intent.getLongExtra(
                                DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                        if (id == pendingDownloadId) {
                            pendingDownloadId = -1;
                            installViaSession();
                        }
                    }
                };
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(downloadReceiver,
                            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                            RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(downloadReceiver,
                            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Indirme baslatilamadi: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * APK'yi PackageInstaller oturumu ile kurar. Saf framework API'dir;
     * FileProvider / AndroidX gerektirmez.
     */
    private void installViaSession() {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Kurulum hazirlaniyor...");
        pd.setCancelable(false);
        pd.show();

        new Thread(() -> {
            PackageInstaller pi = getPackageManager().getPackageInstaller();
            PackageInstaller.Session session = null;
            try {
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(
                                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int sessionId = pi.createSession(params);
                session = pi.openSession(sessionId);

                File apk = new File(getExternalFilesDir(
                        Environment.DIRECTORY_DOWNLOADS),
                        "video-merkezi-update.apk");
                try (InputStream in = new FileInputStream(apk);
                     OutputStream out = session.openWrite("apk", 0, apk.length())) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    session.fsync(out);
                }

                Intent confirm = new Intent(this, InstallResultActivity.class);
                android.app.PendingIntent pi2 = android.app.PendingIntent.getActivity(
                        this, sessionId, confirm,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                | android.app.PendingIntent.FLAG_MUTABLE);
                session.commit(pi2.getIntentSender());
                if (session != null) session.close();
                runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(MainActivity.this,
                            "Kurulum ekrani aciliyor, onaylayin",
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                if (session != null) session.close();
                final String msg = e.getMessage();
                runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(MainActivity.this,
                            "Kurulum basarisiz: " + msg,
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (downloadReceiver != null) {
            try {
                unregisterReceiver(downloadReceiver);
            } catch (Exception ignored) {
            }
            downloadReceiver = null;
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
