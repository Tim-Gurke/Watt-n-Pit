package de.wattpit.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import de.wattpit.app.core.VictronSettings;

/** Forecast history is separate from measurements. All history work runs off the UI/bridge thread. */
final class SolarPlanning extends SQLiteOpenHelper {
    private final Repository repository;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean();
    private final Consumer<JSONObject> callback;
    private volatile boolean closed;
    SolarPlanning(Context context,Repository repository,Consumer<JSONObject> callback){
        super(context,"wattpit-planning.db",null,2);this.repository=repository;this.callback=callback;setWriteAheadLoggingEnabled(true);
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE observations(start INTEGER NOT NULL,end INTEGER NOT NULL,source TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(start,end,source))");
        db.execSQL("CREATE TABLE forecasts(key TEXT NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,issued INTEGER NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(key,start,end,issued))");
        db.execSQL("CREATE TABLE contexts(at INTEGER PRIMARY KEY,key TEXT NOT NULL,mobile_free INTEGER NOT NULL,source TEXT NOT NULL,solar_key TEXT NOT NULL DEFAULT '',payload TEXT NOT NULL DEFAULT '{}')");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){db.execSQL("ALTER TABLE contexts ADD COLUMN solar_key TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE contexts ADD COLUMN payload TEXT NOT NULL DEFAULT '{}'");}
    }
    static JSONObject checked(JSONObject input,JSONObject equipment)throws JSONException{
        if(input.has("version")&&input.optInt("version")!=1)throw new IllegalArgumentException("Unbekannte Aufstellung.");
        JSONObject groups=new JSONObject(),given=input.optJSONObject("groups");JSONArray list=equipment.getJSONArray("groups");
        for(int i=0;i<list.length();i++){
            JSONObject g=list.getJSONObject(i),p=given==null?null:given.optJSONObject(g.getString("id"));if(p==null)p=new JSONObject();
            Object az=JSONObject.NULL;if(!p.isNull("azimuth")){double value=p.getDouble("azimuth");if(!Double.isFinite(value)||value<0||value>=360)throw new IllegalArgumentException("Bitte eine gültige Himmelsrichtung wählen.");az=value;}
            JSONArray shade=p.optJSONArray("shade"),clean=new JSONArray();if(shade!=null&&shade.length()!=3)throw new IllegalArgumentException("Bitte die Verschattung prüfen.");
            for(int j=0;j<3;j++){if(shade==null||shade.isNull(j))clean.put(JSONObject.NULL);else{double v=shade.getDouble(j);if(v!=Math.rint(v)||v<0||v>3)throw new IllegalArgumentException("Bitte die Verschattung prüfen.");clean.put((int)v);}}
            groups.put(g.getString("id"),new JSONObject().put("active",p.optBoolean("active",!g.optString("placement").equals("mobile")&&g.optBoolean("defaultActive",true))).put("azimuth",az).put("shade",clean));
        }
        JSONArray manual=input.optJSONArray("manual"),values=new JSONArray();if(manual!=null&&manual.length()!=4)throw new IllegalArgumentException("Bitte die saisonalen Verbrauchswerte prüfen.");
        for(int i=0;i<4;i++){if(manual==null||manual.isNull(i))values.put(JSONObject.NULL);else{double v=manual.getDouble(i);if(!Double.isFinite(v)||v<0||v>100000)throw new IllegalArgumentException("Bitte gültige Verbrauchswerte eingeben.");values.put(v);}}
        return new JSONObject().put("version",1).put("groups",groups).put("usageMode",input.optString("usageMode").equals("manual")?"manual":"auto").put("manual",values);
    }
    JSONObject setup()throws Exception{return checked(new JSONObject(repository.prefs.getString("solar-setup-v1","{}")),repository.equipment());}
    static String geometry(JSONObject g,JSONObject p){
        if(g.isNull("tilt"))return null;double tilt=g.optDouble("tilt",Double.NaN);if(!Double.isFinite(tilt))return null;
        double az=tilt==0?180:p==null?Double.NaN:p.optDouble("azimuth",Double.NaN);if(!Double.isFinite(az))return null;
        return decimal(tilt)+":"+decimal(az-180);
    }
    private static String decimal(double value){return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    void save(String json,boolean newSite)throws Exception{
        if(json.length()>32000)throw new IllegalArgumentException("Die Aufstellung ist zu groß.");
        JSONObject clean=checked(new JSONObject(json),repository.equipment());
        if(newSite){JSONArray groups=repository.equipment().getJSONArray("groups");for(int i=0;i<groups.length();i++){JSONObject g=groups.getJSONObject(i);if(g.optString("placement").equals("mobile"))clean.getJSONObject("groups").getJSONObject(g.getString("id")).put("active",false);}}
        if(!repository.prefs.edit().putString("solar-setup-v1",clean.toString()).commit())throw new java.io.IOException();
        WeatherClient.refresh(repository,true);
    }
    void saveSite(String setupJson,String placeJson)throws Exception{
        if(setupJson.length()>32000||placeJson.length()>2000)throw new IllegalArgumentException("Die Aufstellung ist zu groß.");
        JSONObject clean=checked(new JSONObject(setupJson),repository.equipment()),given=new JSONObject(placeJson);
        android.content.SharedPreferences.Editor edit=repository.prefs.edit().putString("solar-setup-v1",clean.toString());
        if(given.length()>0){JSONObject place=WeatherClient.checkedPlace(given);if(!place.toString().equals(repository.prefs.getString("weatherPlace","")))edit.putString("weatherPlace",place.toString()).remove("weatherCache").remove("weatherError");}
        if(!edit.commit())throw new java.io.IOException();WeatherClient.refresh(repository,true);
    }
    private String[] devices()throws JSONException{
        String solar="",shunt="";JSONArray configs=repository.configs();for(int i=0;i<configs.length();i++){JSONObject d=configs.getJSONObject(i);if(d.optString("kind").equals("solar"))solar=d.optString("mac");if(d.optString("kind").equals("shunt"))shunt=d.optString("mac");}return new String[]{solar,shunt};
    }
    private static String source(String[] devices)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(String.join("|",devices).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private boolean mobileFree()throws Exception{
        JSONObject settings=setup().getJSONObject("groups");JSONArray groups=repository.equipment().getJSONArray("groups");
        for(int i=0;i<groups.length();i++){JSONObject g=groups.getJSONObject(i);if(settings.getJSONObject(g.getString("id")).optBoolean("active")&&(g.optString("placement").equals("mobile")||!g.optString("measurement").equals("solar")))return false;}return true;
    }
    void context(String input){
        if(closed||input.length()>300)return;
        try{
            JSONObject keys=input.startsWith("{")?new JSONObject(input):new JSONObject().put("key",input).put("solarKey",input);
            String key=keys.optString("key"),solarKey=keys.optString("solarKey",key);
            if(!key.matches("k-[a-f0-9]{16}")||!solarKey.matches("k-[a-f0-9]{16}"))return;
            // Capture at the bridge call, before another quick mobile/site change can alter prefs.
            String source=source(devices());boolean free=mobileFree();long at=System.currentTimeMillis();
            JSONObject payload=new JSONObject().put("equipment",repository.equipment()).put("setup",setup()).put("place",new JSONObject(repository.prefs.getString("weatherPlace","{}")));
            worker.execute(()->{try{
                SQLiteDatabase db=getWritableDatabase();
                try(Cursor c=db.rawQuery("SELECT at,key,mobile_free,solar_key,payload FROM contexts WHERE source=? ORDER BY at DESC LIMIT 1",new String[]{source})){
                    if(c.moveToFirst()&&key.equals(c.getString(1))&&free==(c.getInt(2)==1)){
                        if(payload.toString().equals(c.getString(4))&&solarKey.equals(c.getString(3)))return;
                        // A matching legacy key already encodes exactly this equipment and setup.
                        if(c.getString(4).equals("{}")){ContentValues v=new ContentValues();v.put("payload",payload.toString());v.put("solar_key",solarKey);db.update("contexts",v,"at=? AND source=?",new String[]{c.getString(0),source});return;}
                    }
                }
                ContentValues v=new ContentValues();v.put("at",at);v.put("key",key);v.put("source",source);v.put("mobile_free",free?1:0);v.put("solar_key",solarKey);v.put("payload",payload.toString());db.insertWithOnConflict("contexts",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            }catch(Exception ignored){/* Unknown context remains ineligible for estimated daylight learning. */}});
        }catch(Exception ignored){}
    }
    void remember(String json){
        if(closed||json.length()>128000)return;worker.execute(()->{SQLiteDatabase db=null;try{
            JSONArray rows=new JSONArray(json);if(rows.length()>48)return;db=getWritableDatabase();db.beginTransaction();long now=System.currentTimeMillis();
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);String key=row.getString("key");long from=row.getLong("from"),to=row.getLong("to"),issued=row.getLong("issued");double wh=row.getDouble("wh");
                if(!key.matches("k-[a-f0-9]{16}")||!Double.isFinite(wh)||wh<0||wh>10000000||issued>now+60000||issued<now-86400000||from<issued||from>issued+16L*86400000||to<=from||to-from>12L*3600000)throw new IllegalArgumentException();
                ContentValues v=new ContentValues();v.put("key",key);v.put("start",from);v.put("end",to);v.put("issued",issued);v.put("payload",row.toString());db.insertWithOnConflict("forecasts",null,v,SQLiteDatabase.CONFLICT_IGNORE);
            }db.delete("forecasts","start<?",new String[]{""+(now-45L*86400000)});db.setTransactionSuccessful();
        }catch(Exception ignored){}finally{if(db!=null&&db.inTransaction())db.endTransaction();}});
    }
    private static final class ContextEntry {
        final long at;final String key,solarKey;final boolean free;final JSONObject payload;
        ContextEntry(Cursor c)throws Exception{at=c.getLong(0);key=c.getString(1);free=c.getInt(2)==1;solarKey=c.getString(3).isEmpty()?key:c.getString(3);payload=new JSONObject(c.getString(4));}
    }
    private static ContextEntry at(NavigableMap<Long,ContextEntry> entries,long time){Map.Entry<Long,ContextEntry> e=entries.floorEntry(time);return e==null?null:e.getValue();}
    private static boolean samePlace(JSONObject a,JSONObject b){
        if(a==null||b==null)return false;
        for(String name:new String[]{"latitude","longitude"}){double x=a.optDouble(name,Double.NaN),y=b.optDouble(name,Double.NaN);if(!Double.isFinite(x)||!Double.isFinite(y)||Math.round(x*1000)!=Math.round(y*1000))return false;}return true;
    }
    private static JSONObject profileAt(JSONArray profiles,long from,long to)throws Exception{
        for(int i=profiles.length()-1;i>=0;i--){JSONObject p=profiles.getJSONObject(i);long start=p.getLong("since"),end=p.getLong("until");
            if(from>=start&&to<=end+(i==profiles.length()-1?VictronSettings.MAX_AGE:0))return p.getJSONObject("values");
        }return null;
    }
    private static Double number(JSONObject value,String key){double n=value==null?Double.NaN:value.optDouble(key,Double.NaN);return Double.isFinite(n)?n:null;}
    private static boolean unrestricted(JSONObject segment,JSONObject profile){
        return profile!=null&&segment.optDouble("bulkCoverage",0)>=.98&&segment.optDouble("errorMax",Double.NaN)==0
            &&VictronSettings.limit(3,segment.optDouble("voltageMax",Double.NaN),segment.optDouble("currentMax",Double.NaN),number(profile,"absorptionVoltage"),number(profile,"maxChargeCurrent"),number(profile,"temperatureCompensation")).equals("clear");
    }
    /** Potential unmetered generation is only a labelled estimate for unconstrained daylight. */
    private static Double mobileEstimate(JSONObject snapshot,JSONObject weather,long from,long to,int slot)throws Exception{
        JSONObject plan=snapshot.optJSONObject("setup"),equipment=snapshot.optJSONObject("equipment");if(plan==null||equipment==null)return null;
        JSONArray groups=equipment.getJSONArray("groups"),series=weather.optJSONArray("solar");double result=0;
        for(int i=0;i<groups.length();i++){
            JSONObject g=groups.getJSONObject(i),p=plan.getJSONObject("groups").getJSONObject(g.getString("id"));
            if(!p.optBoolean("active")||g.optString("measurement").equals("solar"))continue;
            String geometry=geometry(g,p);if(geometry==null||series==null)return null;JSONArray values=null;
            for(int j=0;j<series.length();j++)if(geometry.equals(series.getJSONObject(j).optString("geometry")))values=series.getJSONObject(j).optJSONArray("values");
            if(values==null)return null;long covered=0;double irradiance=0;
            for(int j=0;j<values.length();j++){
                JSONObject h=values.getJSONObject(j);long end=h.optLong("t"),a=Math.max(from,end-3600000),b=Math.min(to,end);if(b<=a)continue;
                double v=h.optDouble("v",Double.NaN);if(!Double.isFinite(v)||v<0)return null;covered+=b-a;irradiance+=v*(b-a)/3600000.0;
            }
            if(covered!=to-from)return null;JSONArray shade=p.getJSONArray("shade");int value=shade.isNull(slot)?0:shade.getInt(slot);
            result+=g.getInt("count")*g.getDouble("watts")*irradiance/1000*.8*new double[]{1,.8,.55,.25}[value];
        }return result;
    }
    private static boolean unmetered(JSONObject snapshot)throws Exception{
        JSONObject equipment=snapshot.getJSONObject("equipment"),plan=snapshot.getJSONObject("setup").getJSONObject("groups");JSONArray groups=equipment.getJSONArray("groups");
        for(int i=0;i<groups.length();i++){JSONObject g=groups.getJSONObject(i);if(plan.getJSONObject(g.getString("id")).optBoolean("active")&&!g.optString("measurement").equals("solar"))return true;}return false;
    }
    private boolean annotate(JSONObject row,NavigableMap<Long,ContextEntry> contexts,JSONArray profiles,JSONObject weather,String currentKey,String solarKey,long now)throws Exception{
        JSONArray segments=row.getJSONArray("segments");double wh=0;long duration=0,estimated=0,latest=0;boolean solarEligible=true,free=true;String rowKey="";
        JSONObject current=new JSONObject().put("equipment",repository.equipment()).put("setup",setup());
        for(int i=0;i<segments.length();i++){
            JSONObject segment=segments.getJSONObject(i);long from=segment.getLong("from"),to=segment.getLong("to");ContextEntry context=at(contexts,from);
            JSONObject snapshot=null;boolean known=false,extra=true;
            if(context!=null){
                if(rowKey.isEmpty())rowKey=context.key;free&=context.free;
                if(context.payload.length()>0){
                    // Preserve previously stored observations across a site change, including night windows.
                    if(!samePlace(context.payload.optJSONObject("place"),weather.optJSONObject("place")))return false;
                    snapshot=context.payload;known=true;extra=unmetered(snapshot);
                }else{
                    if(!context.key.equals(currentKey))return false;
                    snapshot=current;known=true;extra=context.free?false:unmetered(snapshot);
                }
            }else free=false;
            boolean night=segment.optLong("lightMillis",1)==0,clear=unrestricted(segment,profileAt(profiles,from,to));
            if(!night&&(segment.optDouble("solarCoverage",0)<.98||!clear||context==null||!context.solarKey.equals(solarKey)))solarEligible=false;
            // Before context history, only truly observed solar intervals can establish a baseline.
            boolean actual=segment.optDouble("actualSolarCoverage",0)>=.98;
            if(!known&&!actual)continue;
            if(segment.optDouble("coverage",0)<.98)continue;
            double measured=segment.optDouble("consumption",Double.NaN);if(!Double.isFinite(measured))continue;
            boolean estimate=false;double value=measured;
            if(extra&&!night){
                if(!known||!clear)continue;
                Double mobile=mobileEstimate(snapshot,weather,from,to,row.getInt("slot"));if(mobile==null){if(context!=null&&!context.key.equals(currentKey))return false;continue;}
                value+=mobile;estimate=true;
            }else if(segment.optBoolean("extraCharging"))continue;
            if(value<0||value>1000000)continue;
            wh+=value;duration+=to-from;if(estimate)estimated+=to-from;latest=Math.max(latest,to);
        }
        row.remove("segments");row.put("key",rowKey).put("solarKey",solarKey).put("mobileFree",free&&!row.optBoolean("extraCharging"))
            .put("learningVersion",2).put("solarEligible",solarEligible&&row.getLong("to")<=now)
            .put("usage",new JSONObject().put("wh",wh).put("duration",duration).put("estimatedDuration",estimated).put("latest",latest));return true;
    }
    void request(){
        if(closed||!busy.compareAndSet(false,true))return;
        worker.execute(()->{JSONObject result=new JSONObject();try{
            if(repository.archiveBusy)return;long now=System.currentTimeMillis();String[] devices=devices();String source=source(devices());
            JSONObject weather=new JSONObject(repository.prefs.getString("weatherCache","{}"));SQLiteDatabase db=getWritableDatabase();long since=now-7L*86400000;
            boolean rebuild=repository.prefs.getInt("planningEnergyVersion."+source,0)<147;
            if(rebuild){try(Cursor c=db.rawQuery("SELECT MIN(start) FROM observations WHERE source=?",new String[]{source})){if(c.moveToFirst()&&!c.isNull(0))since=Math.max(now-400L*86400000,Math.min(since,c.getLong(0)));}}
            else try(Cursor c=db.rawQuery("SELECT MAX(end) FROM observations WHERE source=? AND end<=?",new String[]{source,""+now})){if(c.moveToFirst()&&!c.isNull(0))since=Math.max(since,c.getLong(0)-2L*86400000);}
            NavigableMap<Long,ContextEntry> contexts=new TreeMap<>();List<Long> changes=new ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT at,key,mobile_free,solar_key,payload FROM contexts WHERE source=? ORDER BY at",new String[]{source})){while(c.moveToNext()){ContextEntry entry=new ContextEntry(c);contexts.put(entry.at,entry);changes.add(entry.at);}}
            ContextEntry latest=contexts.isEmpty()?null:contexts.lastEntry().getValue();String currentKey=latest==null?"":latest.key,solarKey=latest==null?"":latest.solarKey;
            JSONArray profiles=repository.db.deviceSettingsHistory(devices[0],since,now);
            for(int i=0;i<profiles.length();i++){JSONObject p=profiles.getJSONObject(i);changes.add(p.getLong("since"));changes.add(p.getLong("until"));changes.add(p.getLong("until")+VictronSettings.MAX_AGE);}
            String signature=repository.prefs.getString("equipment-v1","{\"version\":1,\"groups\":[]}")+"|"+repository.prefs.getString("solar-setup-v1","{}");
            JSONArray observations=weather.optInt("version")>=4&&!signature.equals(weather.optString("signature"))?new JSONArray():repository.db.planningObservations(since,now,weather,devices[0],devices[1],changes);
            db.beginTransaction();try{for(int i=0;i<observations.length();i++){
                JSONObject row=observations.getJSONObject(i);long from=row.getLong("from"),to=row.getLong("to");
                if(!annotate(row,contexts,profiles,weather,currentKey,solarKey,now))continue;
                ContentValues v=new ContentValues();v.put("start",from);v.put("end",to);v.put("source",source);v.put("payload",row.toString());db.insertWithOnConflict("observations",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            }db.delete("observations","end<?",new String[]{""+(now-400L*86400000)});db.delete("contexts","at<?",new String[]{""+(now-410L*86400000)});db.setTransactionSuccessful();}finally{db.endTransaction();}
            if(observations.length()>0)repository.prefs.edit().putInt("planningEnergyVersion."+source,147).apply();
            JSONArray samples=new JSONArray(),forecasts=new JSONArray();
            try(Cursor c=db.rawQuery("SELECT payload FROM observations WHERE source=? ORDER BY start DESC LIMIT 1300",new String[]{source})){while(c.moveToNext()){JSONObject row=new JSONObject(c.getString(0));if(!row.has("solarKey")&&currentKey.equals(row.optString("key")))row.put("solarKey",solarKey);samples.put(row);}}
            // One original eligible prediction per interval keeps the bridge payload small.
            // SQLite's single MIN aggregate selects payload from that same earliest row.
            try(Cursor c=db.rawQuery("SELECT payload,MIN(issued) FROM forecasts WHERE key IN (?,?) AND issued<=start-43200000 AND issued>=start-259200000 GROUP BY start,end ORDER BY start DESC LIMIT 200",new String[]{currentKey,solarKey})){while(c.moveToNext())forecasts.put(new JSONObject(c.getString(0)).put("key",solarKey));}
            result.put("source",source).put("observations",samples).put("forecasts",forecasts).put("updated",now);
        }catch(Exception e){try{result.put("error","Lerndaten gerade nicht verfügbar. Die Solarvorschau bleibt nutzbar.");}catch(JSONException ignored){}}
        finally{busy.set(false);if(!closed&&result.length()>0)callback.accept(result);}});
    }
    Future<JSONObject> diagnosticSnapshot(){return worker.submit(()->{
        long now=System.currentTimeMillis();String[] selected=devices();String currentSource=source(selected);SQLiteDatabase db=getReadableDatabase();
        JSONObject result=new JSONObject().put("format","Watt-n-Pit-Diagnose").put("version",1).put("appVersion",BuildConfig.VERSION_NAME).put("at",now)
            .put("timezone",java.util.TimeZone.getDefault().getID()).put("source",currentSource).put("equipment",repository.equipment()).put("setup",setup())
            .put("weather",new JSONObject(repository.prefs.getString("weatherCache","{}"))).put("history",repository.db.stats())
            .put("capacity",repository.prefs.getFloat("capacity",100f)).put("sampleSeconds",repository.prefs.getInt("sampleSeconds",10));
        JSONArray observations=new JSONArray(),forecasts=new JSONArray(),contexts=new JSONArray(),devices=new JSONArray();
        try(Cursor c=db.rawQuery("SELECT payload FROM observations WHERE source=? ORDER BY start",new String[]{currentSource})){while(c.moveToNext())observations.put(new JSONObject(c.getString(0)));}
        try(Cursor c=db.rawQuery("SELECT payload FROM forecasts ORDER BY start,issued",null)){while(c.moveToNext())forecasts.put(new JSONObject(c.getString(0)));}
        try(Cursor c=db.rawQuery("SELECT at,key,mobile_free,solar_key,payload FROM contexts WHERE source=? ORDER BY at",new String[]{currentSource})){while(c.moveToNext())contexts.put(new JSONObject().put("at",c.getLong(0)).put("key",c.getString(1)).put("mobileFree",c.getInt(2)==1).put("solarKey",c.getString(3)).put("payload",new JSONObject(c.getString(4))));}
        for(int i=0;i<selected.length;i++)if(!selected[i].isEmpty())devices.put(new JSONObject().put("kind",i==0?"solar":"shunt").put("device",selected[i]).put("profiles",repository.db.deviceSettingsHistory(selected[i],now-410L*86400000,now)));
        return result.put("observations",observations).put("forecasts",forecasts).put("contexts",contexts).put("devices",devices);
    });}
    void shutdown(){closed=true;worker.shutdown();/* Existing jobs finish before the process releases SQLite. */}
}
