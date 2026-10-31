package de.wattpit.app;

import android.content.*;
import de.wattpit.app.core.*;
import org.json.*;

/** Independent roller ledger: never adds its input estimate to the Shunt totals. */
final class RollerStore {
    private static RollerStore instance;
    static synchronized RollerStore get(Context c){if(instance==null)instance=new RollerStore(c.getApplicationContext());return instance;}
    private final SharedPreferences prefs,plantPrefs;
    private final RollerLedger ledger;
    private JSONObject profile,live=new JSONObject();
    private RollerEnergy.Session session=new RollerEnergy.Session();
    private final RollerEnergy.AutoCharge detector=new RollerEnergy.AutoCharge();
    private boolean automatic;
    private long lastAcceptedAt,autoWindowFrom,autoWindowTo;
    private double lastAcceptedPower;
    private boolean continuousObservation;
    private long revision;
    private String lastCommand="";
    private String status="Nicht verbunden";
    private RollerStore(Context c){
        ledger=RollerLedger.get(c);
        prefs=c.getSharedPreferences("wattpit-roller",Context.MODE_PRIVATE);plantPrefs=c.getSharedPreferences("wattpit",Context.MODE_PRIVATE);
        try{profile=checked(new JSONObject(prefs.getString("profile","{}")));JSONObject s=new JSONObject(prefs.getString("session","{}"));
            session.started=s.optLong("started");session.ended=s.optLong("ended");session.observedMillis=s.optLong("observedMillis");session.gapMillis=s.optLong("gapMillis");session.batteryWh=s.optDouble("batteryWh",0);session.inputWh=s.optDouble("inputWh",0);automatic=s.optBoolean("automatic");detector.suppressed=prefs.getBoolean("autoSuppressed",false);revision=prefs.getLong("sessionRevision",0);lastCommand=prefs.getString("lastCommand","");
            // A process restart ends the session rather than inventing missing energy.
            if(s.optBoolean("active")){session.active=true;session.lastAt=s.optLong("savedAt",session.started);session.stop(automatic?session.lastAt:System.currentTimeMillis());revision++;saveSession();archiveSession();}
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    static JSONObject checked(JSONObject in)throws JSONException{
        String name=in.optString("name","Roller").trim(),mac=in.optString("mac","").trim().toUpperCase(java.util.Locale.ROOT),tech=in.optString("technology","Li-Ion");
        if(name.isEmpty()||name.length()>60||(!mac.isEmpty()&&!mac.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}"))||!java.util.Arrays.asList("Li-Ion","LiFePO4").contains(tech))throw new IllegalArgumentException("Bitte Name, Technologie und Bluetooth-Adresse prüfen.");
        JSONObject o=new JSONObject().put("planCharge",in.optBoolean("planCharge",false)).put("autoSpecialUse",in.optBoolean("autoSpecialUse",false)).put("name",name).put("mac",mac).put("technology",tech);
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
        JSONObject next=checked(new JSONObject(json));if(!prefs.edit().putString("profile",next.toString()).putBoolean("autoSuppressed",false).commit())throw new IllegalArgumentException("Profil konnte nicht gespeichert werden.");
        profile=next;detector.suppressed=false;detector.interrupt();live=new JSONObject();lastAcceptedAt=0;
    }
    synchronized String mac(){return profile.optString("mac");}
    synchronized void status(String value){status=value;}
    synchronized void disconnected(){live=new JSONObject();continuousObservation=false;detector.interrupt();session.interrupt(System.currentTimeMillis());saveSession();}
    synchronized void accept(BmsPacket p,long now){accept(p,now,"Direkt");}
    synchronized void accept(BmsPacket p,long now,String source){try{
        if(now<=lastAcceptedAt)return;double watts=p.voltage*p.current,idle=profile.optDouble("idleWatts"),efficiency=profile.optDouble("conversionEfficiency");
        JSONArray temperatures=new JSONArray();for(double temperature:p.temperatures)temperatures.put(Double.isFinite(temperature)?temperature:JSONObject.NULL);
        live=new JSONObject().put("source",source).put("at",now).put("voltage",p.voltage).put("current",p.current).put("power",watts).put("soc",p.soc<0?JSONObject.NULL:p.soc).put("remainingAh",p.remainingAh).put("fullAh",p.fullAh).put("chargeEnabled",p.chargeEnabled).put("dischargeEnabled",p.dischargeEnabled).put("temperatures",temperatures);
        int action=0;boolean started=false;
        if(profile.optBoolean("autoSpecialUse")&&(!session.active||automatic))action=detector.sample(now,watts,session.active);
        if(action==1){automatic=true;session.start(detector.startAt);revision++;session.sample(detector.startAt,detector.startPower,idle,efficiency);autoWindowFrom=detector.startAt;autoWindowTo=now;started=true;}
        if(session.active&&automatic&&!started&&continuousObservation&&lastAcceptedAt>0&&now-lastAcceptedAt<=RollerEnergy.MAX_GAP_MS&&(lastAcceptedPower>RollerEnergy.AutoCharge.STOP_WATTS||watts>RollerEnergy.AutoCharge.STOP_WATTS)){
            // Finite observed intervals: a lost BMS never excludes hours of ordinary demand.
            if(autoWindowTo!=lastAcceptedAt)autoWindowFrom=lastAcceptedAt;
            autoWindowTo=now;
        }
        if(session.active&&continuousObservation&&lastAcceptedAt>=session.started&&now-lastAcceptedAt<=RollerEnergy.MAX_GAP_MS){
            double input=(RollerEnergy.inputWatts(Math.max(0,lastAcceptedPower),idle,efficiency)+RollerEnergy.inputWatts(Math.max(0,watts),idle,efficiency))*.5*(now-lastAcceptedAt)/3600000.0;
            try{ledger.add(RollerLedger.source(plantPrefs),mac(),session.started,lastAcceptedAt,now,input);}catch(RuntimeException failure){plantPrefs.edit().putBoolean("rollerInputStorageError",true).apply();}
        }
        status="BMS verbunden";session.sample(now,watts,idle,efficiency);
        lastAcceptedAt=now;lastAcceptedPower=watts;continuousObservation=true;
        if(action==-1){session.stop(now);revision++;archiveSession();detector.interrupt();}
        saveSession();
    }catch(JSONException ignored){}}
    synchronized void toggle(boolean start){
        long now=System.currentTimeMillis();
        if(start){if(session.active)return;if(!fresh(now))throw new IllegalArgumentException("Zuerst aktuelle BMS-Werte empfangen.");automatic=false;detector.interrupt();session.start(Math.max(now,session.started+1));revision++;session.sample(now,live.optDouble("power"),profile.optDouble("idleWatts"),profile.optDouble("conversionEfficiency"));}
        else {if(!session.active)return;if(profile.optBoolean("autoSpecialUse"))detector.suppress();session.stop(now);revision++;archiveSession();}
        saveSession();
    }
    synchronized void pause(){if(session.active){session.stop(System.currentTimeMillis());revision++;archiveSession();}detector.interrupt();saveSession();}
    synchronized void command(boolean start,long expectedRevision,String id){
        if(!id.matches("[A-Za-z0-9_-]{22}"))throw new IllegalArgumentException("Ungültige Ladeanfrage.");
        if(id.equals(lastCommand))return;
        if(expectedRevision!=revision)throw new IllegalArgumentException("Der Ladestatus hat sich inzwischen geändert. Bitte aktualisieren und erneut versuchen.");
        toggle(start);lastCommand=id;saveSession();
    }
    private boolean fresh(long now){long at=live.optLong("at");return at>0&&now>=at&&now-at<=10000;}
    private JSONObject sessionJson()throws JSONException{return new JSONObject().put("usageType","roller-charge").put("usageLabel","Sondernutzung · Rollerladung").put("automatic",automatic).put("started",session.started).put("ended",session.ended).put("active",session.active).put("batteryWh",session.batteryWh).put("inputWh",session.inputWh).put("observedMillis",session.observedMillis).put("gapMillis",session.gapMillis).put("savedAt",lastAcceptedAt>0?lastAcceptedAt:System.currentTimeMillis());}
    private void saveSession(){try{prefs.edit().putString("session",sessionJson().toString()).putBoolean("autoSuppressed",detector.suppressed).putLong("sessionRevision",revision).putString("lastCommand",lastCommand).apply();
        JSONArray windows=new JSONArray(plantPrefs.getString("rollerChargeWindows","[]")),out=new JSONArray();
        long from=automatic?autoWindowFrom:session.started,to=automatic?autoWindowTo:session.active?0:session.ended;
        for(int i=Math.max(0,windows.length()-4999);i<windows.length();i++){JSONObject w=windows.getJSONObject(i);if(from<=0||w.optLong("from")!=from)out.put(w);}
        if(from>0&&(!automatic||to>from))out.put(new JSONObject().put("type","roller-charge").put("from",from).put("to",to));
        plantPrefs.edit().putString("rollerChargeWindows",out.toString()).apply();
    }catch(JSONException ignored){}}
    private void archiveSession(){try{if(session.started<=0)return;JSONArray rows=new JSONArray(prefs.getString("journal","[]")),out=new JSONArray();for(int i=Math.max(0,rows.length()-199);i<rows.length();i++)out.put(rows.getJSONObject(i));out.put(sessionJson().put("profile",new JSONObject(profile.toString())));prefs.edit().putString("journal",out.toString()).apply();}catch(JSONException ignored){}}
    synchronized JSONObject snapshot()throws JSONException{
        long now=System.currentTimeMillis();boolean fresh=fresh(now);double watts=fresh?Math.max(0,live.optDouble("power")):0;
        JSONObject s=sessionJson();s.put("gapMillis",session.gapMillis+(session.active?session.pendingGap(now):0));
        return new JSONObject().put("journal",new JSONArray(prefs.getString("journal","[]"))).put("profile",new JSONObject(profile.toString())).put("live",fresh?new JSONObject(live.toString()):new JSONObject()).put("fresh",fresh).put("autoSuppressed",detector.suppressed).put("sessionRevision",revision).put("status",status).put("session",s).put("estimatedInputWatts",fresh?RollerEnergy.inputWatts(watts,profile.optDouble("idleWatts"),profile.optDouble("conversionEfficiency")):JSONObject.NULL);
    }
}
