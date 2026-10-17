package de.wattpit.app;

import android.content.Context;
import android.content.SharedPreferences;
import de.wattpit.app.core.*;
import org.json.*;
import java.util.*;

final class Repository {
    private static Repository instance;
    static synchronized Repository get(Context context) {
        if (instance == null) instance = new Repository(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    final HistoryDb db;
    final SharedPreferences prefs;
    private final Map<String,Reading> latest = new LinkedHashMap<>();
    private final Map<String,JSONObject> found = new LinkedHashMap<>();
    private final Map<String,String> errors = new HashMap<>();
    private final Map<String,byte[]> keys = new HashMap<>();
    private final Map<String,Long> stored = new HashMap<>();
    volatile boolean running = false;
    volatile boolean archiveBusy=false;
    volatile String status = "Aufzeichnung pausiert";
    private JSONObject cachedStats = new JSONObject();
    private long statsTime, eventId;
    private String event = "";
    private Repository(Context context) { this.context=context;prefs = context.getSharedPreferences("wattpit", Context.MODE_PRIVATE); db = new HistoryDb(context); }

    private JSONObject archive=new JSONObject();
    synchronized void archiveState(String mac,boolean active,String text,int done,int total){
        try{archive=new JSONObject().put("mac",mac).put("active",active).put("text",text).put("done",done).put("total",total).put("updated",System.currentTimeMillis());}catch(JSONException ignored){}
    }
    synchronized void finishArchiveIfActive(){
        if(archive.optBoolean("active"))archiveState(archive.optString("mac"),false,"Archivimport beendet. Bereits gelesene Werte bleiben gespeichert.",archive.optInt("done"),archive.optInt("total"));
    }
    synchronized void archiveDiagnostics(String kind,JSONObject result)throws JSONException{
        if(!kind.equals("solar")&&!kind.equals("shunt"))return;
        JSONObject all=new JSONObject(prefs.getString("archiveDiagnostics","{}"));all.put(kind,result);prefs.edit().putString("archiveDiagnostics",all.toString()).apply();
    }
    synchronized String diagnosticText()throws JSONException{
        JSONObject all=new JSONObject(prefs.getString("archiveDiagnostics","{}"));StringBuilder text=new StringBuilder("Watt’n’Pit 0.1.4 · Archivdiagnose\n");
        for(String kind:new String[]{"solar","shunt"}){
            JSONObject d=all.optJSONObject(kind);if(d==null)continue;text.append(kind.equals("solar")?"\nSolarladeregler\n":"\nBatteriewächter\n");
            text.append(d.optString("result")).append('\n');JSONArray lines=d.optJSONArray("steps");if(lines!=null)for(int i=0;i<lines.length();i++)text.append("· ").append(lines.getString(i)).append('\n');
        }
        return text.toString();
    }
    synchronized JSONArray configs() throws JSONException { return new JSONArray(prefs.getString("devices", "[]")); }
    synchronized void message(String text) { event = text; eventId++; }

    synchronized void saveDevice(JSONObject input) throws Exception {
        String mac = VictronDecoder.normalizeMac(input.getString("mac"));
        String original = input.optString("original", "");
        String kind = input.getString("kind");
        if (!kind.equals("solar") && !kind.equals("shunt")) throw new IllegalArgumentException("Unbekannter Gerätetyp.");
        JSONArray list = configs();
        JSONObject previous = null;
        for (int i=0;i<list.length();i++) {
            JSONObject c = list.getJSONObject(i);
            if (c.getString("mac").equals(original.isEmpty() ? mac : original)) previous = c;
            else if (c.getString("kind").equals(kind)) throw new IllegalArgumentException("Ein Gerät dieses Typs ist schon eingerichtet. Bitte dieses Gerät bearbeiten.");
        }
        String key = input.optString("key", "").trim();
        String secret;
        if (key.isEmpty() && previous != null && previous.getString("mac").equals(mac)) secret = previous.getString("secret");
        else { secret = KeyVault.encrypt(VictronDecoder.parseKey(key)); }
        String name = input.optString("name", "").trim();
        if (name.isEmpty()) name = kind.equals("solar") ? "Solarladeregler" : "SmartShunt";
        if (name.length() > 80) name = name.substring(0,80);
        JSONObject config = new JSONObject().put("mac",mac).put("kind",kind).put("name",name).put("secret",secret);
        JSONArray updated = new JSONArray();
        for (int i=0;i<list.length();i++) if (list.getJSONObject(i) != previous) updated.put(list.getJSONObject(i));
        updated.put(config);
        prefs.edit().putString("devices",updated.toString()).apply();
        keys.clear(); errors.remove(mac); latest.remove(mac); stored.remove(mac);
        if (!original.isEmpty()) { latest.remove(original); stored.remove(original); errors.remove(original); }
        message("Gerät gespeichert. Für Livewerte Aufzeichnung starten.");
    }
    synchronized JSONObject equipment()throws JSONException{return new JSONObject(prefs.getString("equipment-v1","{\"version\":1,\"groups\":[]}"));}
    synchronized void saveEquipment(JSONObject input)throws Exception{
        if(input.optInt("version")!=1)throw new IllegalArgumentException("Unbekannte Anlagenkonfiguration.");
        JSONArray rows=input.getJSONArray("groups");List<SolarEquipment.Group> groups=new ArrayList<>();
        if(rows.length()>32)throw new IllegalArgumentException("Bis zu 32 Solargruppen sind möglich.");
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i);double count=r.getDouble("count");if(count!=Math.rint(count))throw new IllegalArgumentException("Die Modulanzahl muss ganzzahlig sein.");
            groups.add(new SolarEquipment.Group(r.getString("id"),r.getString("label"),(int)count,r.getDouble("watts"),r.getString("placement"),r.optString("controller",""),r.getString("measurement")));
        }
        SolarEquipment setup=new SolarEquipment(groups);JSONArray clean=new JSONArray();
        for(SolarEquipment.Group g:setup.groups)clean.put(new JSONObject().put("id",g.id).put("label",g.label).put("count",g.count).put("watts",g.watts).put("placement",g.placement).put("controller",g.controller).put("measurement",g.measurement));
        JSONObject result=new JSONObject().put("version",1).put("groups",clean).put("ratedWatts",setup.ratedWatts());
        if(!prefs.edit().putString("equipment-v1",result.toString()).commit())throw new java.io.IOException();
    }
    synchronized void removeDevice(String mac) throws Exception {
        JSONArray updated = new JSONArray(), list = configs();
        for (int i=0;i<list.length();i++) if (!list.getJSONObject(i).getString("mac").equals(mac)) updated.put(list.getJSONObject(i));
        prefs.edit().putString("devices",updated.toString()).apply();
        latest.remove(mac); keys.remove(mac); errors.remove(mac); stored.remove(mac);
        message("Gerät entfernt. Gespeicherte Messwerte bleiben im Verlauf.");
    }
    synchronized void advertise(String mac, String name, int rssi, byte[] packet) {
        try {
            int type = VictronDecoder.recordType(packet);
            if (type != 1 && type != 2) return;
            long now = System.currentTimeMillis();
            found.put(mac,new JSONObject().put("mac",mac).put("name",name == null ? "Victron-Gerät" : name)
                    .put("kind",type == 1 ? "solar" : "shunt").put("rssi",rssi).put("seen",now));
            JSONArray list = configs();
            for (int i=0;i<list.length();i++) {
                JSONObject c = list.getJSONObject(i);
                if (!mac.equals(c.getString("mac"))) continue;
                try {
                    byte[] key = keys.get(mac);
                    if (key == null) { key = KeyVault.decrypt(c.getString("secret")); keys.put(mac,key); }
                    Reading r = VictronDecoder.decode(packet,key);
                    if (!r.kind.equals(c.getString("kind"))) throw new IllegalArgumentException("Gerätetyp stimmt nicht. Einrichtung prüfen.");
                    r.device = mac; r.timestamp = now;
                    latest.put(mac,r); errors.remove(mac);
                    int seconds = prefs.getInt("sampleSeconds",10);
                    if (now - stored.getOrDefault(mac,0L) >= seconds * 1000L) { db.insert(r); stored.put(mac,now); }
                } catch (Exception e) { errors.put(mac,safeError(e)); }
            }
        } catch (Exception e) { message(safeError(e)); }
    }

    private String bondState(String mac){
        try{
            if(android.os.Build.VERSION.SDK_INT>=31&&context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return "unknown";
            android.bluetooth.BluetoothManager manager=context.getSystemService(android.bluetooth.BluetoothManager.class);
            android.bluetooth.BluetoothAdapter adapter=manager==null?null:manager.getAdapter();if(adapter==null||!adapter.isEnabled())return "unknown";
            int state=adapter.getRemoteDevice(mac).getBondState();return state==android.bluetooth.BluetoothDevice.BOND_BONDED?"bonded":state==android.bluetooth.BluetoothDevice.BOND_BONDING?"bonding":"none";
        }catch(SecurityException|IllegalArgumentException e){return "unknown";}
    }
    synchronized JSONObject snapshot() throws Exception {
        long now = System.currentTimeMillis();
        JSONArray devices = new JSONArray(), discovery = new JSONArray(), readings = new JSONArray(), list = configs();
        for (int i=0;i<list.length();i++) {
            JSONObject c = list.getJSONObject(i);
            String mac = c.getString("mac");
            Reading r = latest.get(mac);
            if (r == null) { r = db.last(mac); if (r != null) latest.put(mac,r); }
            JSONObject publicConfig = new JSONObject().put("mac",mac).put("name",c.getString("name"))
                    .put("kind",c.getString("kind")).put("bondState",bondState(mac)).put("hasKey",true).put("error",errors.getOrDefault(mac,""));
            publicConfig.put("seen",r == null ? 0 : r.timestamp);
            if (found.containsKey(mac)) publicConfig.put("rssi",found.get(mac).getInt("rssi"));
            devices.put(publicConfig);
            if (r != null) readings.put(HistoryDb.json(r));
        }
        Iterator<Map.Entry<String,JSONObject>> iterator = found.entrySet().iterator();
        while (iterator.hasNext()) {
            JSONObject f = iterator.next().getValue();
            if (now - f.getLong("seen") > 120_000) iterator.remove(); else discovery.put(f);
        }
        if (now - statsTime > 30_000) { cachedStats = db.stats(); statsTime = now; }
        return new JSONObject().put("running",running).put("status",status).put("devices",devices).put("found",discovery)
                .put("equipment",equipment()).put("archiveDiagnostics",new JSONObject(prefs.getString("archiveDiagnostics","{}"))).put("archive",archive).put("deviceHistory",db.histories()).put("weatherError",prefs.getString("weatherError","")).put("weather",new JSONObject(prefs.getString("weatherCache","{}"))).put("readings",readings).put("stats",cachedStats).put("solarDaily",db.daily()).put("event",event).put("eventId",eventId)
                .put("settings",new JSONObject().put("theme",prefs.getString("theme","dark"))
                    .put("palette",prefs.getString("palette","classic")).put("weatherPlace",new JSONObject(prefs.getString("weatherPlace","{}")))
                    .put("capacity",prefs.getFloat("capacity",100f)).put("sampleSeconds",prefs.getInt("sampleSeconds",10)));
    }
    synchronized void invalidateStats() { statsTime = 0; }
    void saveSettings(JSONObject settings) throws Exception {
        String theme = settings.optString("theme",prefs.getString("theme","dark"));
        double capacity = settings.optDouble("capacity",prefs.getFloat("capacity",100f));
        int seconds = settings.optInt("sampleSeconds",prefs.getInt("sampleSeconds",10));
        if ((!theme.equals("light") && !theme.equals("dark")) || capacity <= 0 || capacity > 10000 || seconds < 5 || seconds > 60)
            throw new IllegalArgumentException("Bitte gültige Einstellungen eingeben.");
        String palette=settings.optString("palette",prefs.getString("palette","classic"));
        if(!Arrays.asList("classic","blue","green","turquoise","orange","neutral").contains(palette))throw new IllegalArgumentException("Unbekanntes Farbdesign.");
        prefs.edit().putString("palette",palette).putString("theme",theme).putFloat("capacity",(float)capacity).putInt("sampleSeconds",seconds).apply();
    }
    static String safeError(Exception e) {
        if (e instanceof IllegalArgumentException) return e.getMessage();
        if (e instanceof java.io.IOException) return "Datei konnte nicht gelesen oder gespeichert werden.";
        return "Die Aktion konnte nicht abgeschlossen werden. Bitte erneut versuchen.";
    }
}
