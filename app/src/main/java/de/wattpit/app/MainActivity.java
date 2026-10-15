package de.wattpit.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.webkit.*;
import de.wattpit.app.core.CsvCodec;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private WebView web;
    private Repository repository;
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private CsvCodec.Table pendingImport;
    private boolean pendingStart;
    private static final int PERMISSIONS = 11, BLUETOOTH = 12, IMPORT = 20, EXPORT = 21;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = Repository.get(this);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(13,18,27));
        web.setFitsSystemWindows(true);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccessFromFileURLs(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setTextZoom(100);
        web.getSettings().setSupportZoom(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!url.startsWith("file:///android_asset/")) return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                return null;
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(),"WattPit");
        setContentView(web);
        applyTheme(repository.prefs.getString("theme","dark"));
        web.loadUrl("file:///android_asset/index.html");
        if (repository.prefs.getBoolean("recording",false) && !repository.running) web.postDelayed(this::requestStart,800);
    }

    private void applyTheme(String theme) {
        boolean light = theme.equals("light");
        int color = light ? Color.rgb(244,246,249) : Color.rgb(13,18,27);
        getWindow().setStatusBarColor(color); getWindow().setNavigationBarColor(color);
        int flags = light ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    private void call(String method, JSONObject value) {
        runOnUiThread(() -> { if (!isFinishing() && web != null) web.evaluateJavascript("window." + method + " && window." + method + "(" + value.toString() + ")",null); });
    }
    private void error(Exception e) {
        repository.message(Repository.safeError(e));
        try { call("nativeError",new JSONObject().put("message",Repository.safeError(e))); } catch (JSONException ignored) { }
    }

    @SuppressLint("MissingPermission")
    private void requestStart() {
        if (isFinishing() || isDestroyed()) return;
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!permissions.isEmpty()) { pendingStart = true; requestPermissions(permissions.toArray(new String[0]),PERMISSIONS); return; }
        startAfterPermissions();
    }
    @SuppressLint("MissingPermission")
    private void startAfterPermissions() {
        if (isFinishing() || isDestroyed()) return;
        boolean allowed = Build.VERSION.SDK_INT >= 31 ? checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                : checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!allowed) { repository.message("Bitte die Bluetooth-Berechtigung in den Android-App-Einstellungen erlauben."); return; }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) { repository.message("Dieses Gerät unterstützt kein Bluetooth LE. Verlauf und Demo sind weiterhin verfügbar."); return; }
        if (!adapter.isEnabled()) { pendingStart = true; startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),BLUETOOTH); return; }
        pendingStart = false;
        try { startForegroundService(new Intent(this,MonitorService.class)); }
        catch (IllegalStateException | SecurityException e) { repository.message("Bitte die App öffnen, um die Aufzeichnung zu starten."); }
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if (requestCode == PERMISSIONS && pendingStart) startAfterPermissions();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == BLUETOOTH) {
            if (resultCode == RESULT_OK) startAfterPermissions();
            else { pendingStart = false; repository.message("Bluetooth wurde nicht eingeschaltet."); }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == IMPORT) files.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] block = new byte[8192]; int n;
                while ((n = input.read(block)) != -1) { buffer.write(block,0,n); if (buffer.size() > 25*1024*1024) throw new IllegalArgumentException("Die CSV darf höchstens 25 MB groß sein."); }
                byte[] bytes = buffer.toByteArray();
                String text;
                if (bytes.length >= 2 && ((bytes[0] == (byte)0xff && bytes[1] == (byte)0xfe) || (bytes[0] == (byte)0xfe && bytes[1] == (byte)0xff))) text = new String(bytes,StandardCharsets.UTF_16);
                else {
                    try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
                    catch (CharacterCodingException e) { text = new String(bytes,Charset.forName("windows-1252")); }
                }
                CsvCodec.Table table = CsvCodec.parse(text);
                pendingImport = table;
                JSONArray preview = new JSONArray();
                for (int i=0;i<Math.min(3,table.rows.size());i++) preview.put(new JSONArray(table.rows.get(i)));
                JSONObject result = new JSONObject().put("headers",new JSONArray(table.headers)).put("rows",table.rows.size())
                        .put("preview",preview).put("map",new JSONObject(CsvCodec.guess(table.headers))).put("name",displayName(uri));
                call("onImportPreview",result);
            } catch (Exception e) { pendingImport = null; error(e); }
        });
        if (requestCode == EXPORT) files.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri,"wt")) {
                if (output == null) throw new IOException();
                repository.db.exportTo(output); repository.message("Verlauf als CSV gespeichert.");
            } catch (Exception e) { error(e); }
        });
    }
    private String displayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            return c != null && c.moveToFirst() ? c.getString(0) : "CSV-Datei";
        } catch (Exception ignored) { return "CSV-Datei"; }
    }

    final class Bridge {
        @JavascriptInterface public String snapshot() { try { return repository.snapshot().toString(); } catch (Exception e) { return "{}"; } }
        @JavascriptInterface public String history(long from, long to, String metric, String source) {
            try { return new JSONObject().put("points",repository.db.query(from,to,metric,source)).toString(); }
            catch (Exception e) { return "{\"points\":[],\"error\":\"Verlauf konnte nicht geladen werden.\"}"; }
        }
        @JavascriptInterface public String dashboard() { return repository.prefs.getString("dashboard-v1","{}"); }
        @JavascriptInterface public String saveDashboard(String json) {
            try {
                if(json.length()>128000) throw new IllegalArgumentException("Die Übersicht ist zu groß.");
                JSONObject config=new JSONObject(json);
                if(config.getInt("version")!=1 || config.getJSONArray("tiles").length()>64) throw new IllegalArgumentException("Ungültige Übersicht.");
                if(!repository.prefs.edit().putString("dashboard-v1",config.toString()).commit()) throw new IOException();
                return "";
            } catch(Exception e) { return Repository.safeError(e); }
        }
        @JavascriptInterface public String dashboardHistory(long from,long to,String metric) {
            try { return repository.db.dashboardData(from,to,metric).toString(); }
            catch(Exception e) { return "{\"points\":[],\"summary\":{},\"error\":\"Zeitraum konnte nicht geladen werden.\"}"; }
        }
        @JavascriptInterface public void start() { runOnUiThread(MainActivity.this::requestStart); }
        @JavascriptInterface public void stop() {
            repository.prefs.edit().putBoolean("recording",false).apply();
            runOnUiThread(() -> stopService(new Intent(MainActivity.this,MonitorService.class)));
        }
        @JavascriptInterface public String saveDevice(String json) {
            try { repository.saveDevice(new JSONObject(json)); return ""; } catch (Exception e) { return Repository.safeError(e); }
        }
        @JavascriptInterface public String removeDevice(String mac) {
            try { repository.removeDevice(mac); return ""; } catch (Exception e) { return Repository.safeError(e); }
        }
        @JavascriptInterface public String settings(String json) {
            try { JSONObject s = new JSONObject(json); repository.saveSettings(s); runOnUiThread(() -> applyTheme(s.optString("theme",repository.prefs.getString("theme","dark")))); return ""; }
            catch (Exception e) { return Repository.safeError(e); }
        }
        @JavascriptInterface public void chooseImport() {
            runOnUiThread(() -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),IMPORT));
        }
        @JavascriptInterface public void chooseExport() {
            runOnUiThread(() -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/csv").putExtra(Intent.EXTRA_TITLE,"Watt-n-Pit-Verlauf.csv"),EXPORT));
        }
        @JavascriptInterface public void importCsv(String mapping, String kind) {
            files.execute(() -> {
                try {
                    CsvCodec.Table table = pendingImport;
                    if (table == null) throw new IllegalArgumentException("Bitte zuerst eine CSV-Datei auswählen.");
                    JSONObject map = new JSONObject(mapping);
                    Map<String,Integer> fields = new LinkedHashMap<>();
                    for (String field : CsvCodec.FIELDS) {
                        int i = map.optInt(field,-1);
                        if (i >= 0 && i < table.headers.size()) fields.put(field,i);
                    }
                    if (!fields.containsKey("timestamp") && !fields.containsKey("date")) throw new IllegalArgumentException("Bitte die Datum-/Zeitspalte zuordnen.");
                    int[] counts = repository.db.importTable(table,fields,kind);
                    repository.invalidateStats(); pendingImport = null;
                    call("onImportDone",new JSONObject().put("inserted",counts[0]).put("duplicates",counts[1]).put("rejected",counts[2]));
                } catch (Exception e) { error(e); }
            });
        }
        @JavascriptInterface public void cancelImport() { files.execute(() -> pendingImport = null); }
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("window.handleBack ? window.handleBack() : false",value -> { if (!"true".equals(value)) finish(); });
    }
    @Override protected void onDestroy() { files.shutdown(); if (web != null) { web.removeJavascriptInterface("WattPit"); web.destroy(); web = null; } super.onDestroy(); }
}
