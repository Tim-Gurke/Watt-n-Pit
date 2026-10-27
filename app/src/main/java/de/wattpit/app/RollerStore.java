package de.wattpit.app;

import android.content.*;
import de.wattpit.app.core.*;
import org.json.*;

/** Independent roller ledger: never adds its input estimate to the Shunt totals. */
final class RollerStore {
    private static RollerStore instance;
    static synchronized RollerStore get(Context c){if(instance==null)instance=new RollerStore(c.getApplicationContext());return instance;}
    private final SharedPreferences prefs,plantPrefs;
    private JSONObject profile,live=new JSONObject();
    private RollerEnergy.Session session=new RollerEnergy.Session();
    private String status="Nicht verbunden";
    private RollerStore(Context c){
        prefs=c.getSharedPreferences("wattpit-roller",Context.MODE_PRIVATE);plantPrefs=c.getSharedPreferences("wattpit",Context.MODE_PRIVATE);
        try{profile=checked(new JSONObject(prefs.getString("profile","{}")));JSONObject s=new JSONObject(prefs.getString("session","{}"));
            session.started=s.optLong("started");session.ended=s.optLong("ended");session.observedMillis=s.optLong("observedMillis");session.gapMillis=s.optLong("gapMillis");session.batteryWh=s.optDouble("batteryWh",0);session.inputWh=s.optDouble("inputWh",0);
            // A process restart ends the session rather than inventing missing energy.
            if(s.optBoolean("active")){session.active=true;session.lastAt=s.optLong("savedAt",session.started);session.stop(System.currentTimeMillis());saveSession();archiveSession();}
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    static JSONObject checked(JSONObject in)throws JSONException{
        String name=in.optString("name","Roller").trim(),mac=in.optString("mac","").trim().toUpperCase(java.util.Locale.ROOT),tech=in.optString("technology","Li-Ion");
        if(name.isEmpty()||name.length()>60||(!mac.isEmpty()&&!mac.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}"))||!java.util.Arrays.asList("Li-Ion","LiFePO4").contains(tech))throw new IllegalArgumentException("Bitte Name, Technologie und Bluetooth-Adresse prüfen.");
        JSONObject o=new JSONObject().put("planCharge",in.optBoolean("planCharge",false)).put("name",name).put("mac",mac).put("technology",tech);
        String[] keys={"capacityAh","nominalVoltage","reservePercent","targetPercent","idleWatts","conversionEfficiency","chargeWatts","rideKm","whPerKm","rideReservePercent","departure","returnAt"};
        double[] defaults={26,48.1,20,90,21,96.0/117.0,96,0,15,20,0,0};
        double[] min={1,1,0,1,0,.1,1,0,1,0,0,0},max={1000,200,99,100,500,1,5000,2000,200,100,4102444800000.0,4102444800000.0};
        for(int i=0;i<keys.length;i++){double v=in.has(keys[i])?in.getDouble(keys[i]):defaults[i];if(!Double.isFinite(v)||v<min[i]||v>max[i])throw new IllegalArgumentException("Ungültiger Wert: "+keys[i]);o.put(keys[i],v);}
        if(o.getDouble("targetPercent")<o.getDouble("reservePercent"))throw new IllegalArgumentException("Ladeziel muss mindestens der Mindestreserve entsprechen.");
        if(o.getDouble("returnAt")>0&&o.getDouble("returnAt")<=o.getDouble("departure"))throw new IllegalArgumentException("Rückkehr muss nach der Abfahrt liegen.");
        return o;
    }
    synchronized void configure(String json)throws JSONException{
        if(json.length()>8000)throw new IllegalArgumentException("Profil zu groß");
        if(session.active)throw new IllegalArgumentException("Bitte zuerst die Ladeaufzeichnung beenden.");
        profile=checked(new JSONObject(json));prefs.edit().putString("profile",profile.toString()).apply();live=new JSONObject();
    }
    synchronized String mac(){return profile.optString("mac");}
    synchronized void status(String value){status=value;}
    synchronized void disconnected(){live=new JSONObject();session.interrupt(System.currentTimeMillis());saveSession();}
    synchronized void accept(BmsPacket p,long now){accept(p,now,"Direkt");}
    synchronized void accept(BmsPacket p,long now,String source){try{
        live=new JSONObject().put("source",source).put("at",now).put("voltage",p.voltage).put("current",p.current).put("power",p.voltage*p.current).put("soc",p.soc<0?JSONObject.NULL:p.soc).put("remainingAh",p.remainingAh).put("fullAh",p.fullAh).put("chargeEnabled",p.chargeEnabled).put("dischargeEnabled",p.dischargeEnabled);
        status="BMS verbunden";session.sample(now,p.voltage*p.current,profile.optDouble("idleWatts"),profile.optDouble("conversionEfficiency"));saveSession();
    }catch(JSONException ignored){}}
    synchronized void toggle(boolean start){
        long now=System.currentTimeMillis();
        if(start){if(session.active)return;if(!fresh(now))throw new IllegalArgumentException("Zuerst aktuelle BMS-Werte empfangen.");session.start(now);session.sample(now,live.optDouble("power"),profile.optDouble("idleWatts"),profile.optDouble("conversionEfficiency"));}
        else {if(!session.active)return;session.stop(now);archiveSession();}
        saveSession();
    }
    private boolean fresh(long now){long at=live.optLong("at");return at>0&&now>=at&&now-at<=10000;}
    private JSONObject sessionJson()throws JSONException{return new JSONObject().put("usageType","roller-charge").put("usageLabel","Sondernutzung · Rollerladung").put("started",session.started).put("ended",session.ended).put("active",session.active).put("batteryWh",session.batteryWh).put("inputWh",session.inputWh).put("observedMillis",session.observedMillis).put("gapMillis",session.gapMillis).put("savedAt",System.currentTimeMillis());}
    private void saveSession(){try{prefs.edit().putString("session",sessionJson().toString()).apply();
        JSONArray windows=new JSONArray(plantPrefs.getString("rollerChargeWindows","[]")),out=new JSONArray();
        for(int i=Math.max(0,windows.length()-4999);i<windows.length();i++){JSONObject w=windows.getJSONObject(i);if(w.optLong("from")!=session.started)out.put(w);}
        if(session.started>0)out.put(new JSONObject().put("type","roller-charge").put("from",session.started).put("to",session.active?0:session.ended));
        plantPrefs.edit().putString("rollerChargeWindows",out.toString()).apply();
    }catch(JSONException ignored){}}
    private void archiveSession(){try{if(session.started<=0)return;JSONArray rows=new JSONArray(prefs.getString("journal","[]")),out=new JSONArray();for(int i=Math.max(0,rows.length()-199);i<rows.length();i++)out.put(rows.getJSONObject(i));out.put(sessionJson().put("profile",new JSONObject(profile.toString())));prefs.edit().putString("journal",out.toString()).apply();}catch(JSONException ignored){}}
    synchronized JSONObject snapshot()throws JSONException{
        long now=System.currentTimeMillis();boolean fresh=fresh(now);double watts=fresh?Math.max(0,live.optDouble("power")):0;
        JSONObject s=sessionJson();s.put("gapMillis",session.gapMillis+(session.active?session.pendingGap(now):0));
        return new JSONObject().put("journal",new JSONArray(prefs.getString("journal","[]"))).put("profile",new JSONObject(profile.toString())).put("live",fresh?new JSONObject(live.toString()):new JSONObject()).put("fresh",fresh).put("status",status).put("session",s).put("estimatedInputWatts",fresh?RollerEnergy.inputWatts(watts,profile.optDouble("idleWatts"),profile.optDouble("conversionEfficiency")):JSONObject.NULL);
    }
}
