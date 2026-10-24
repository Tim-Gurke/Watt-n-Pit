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

/** Forecast history is separate from measurements. All history work runs off the UI/bridge thread. */
final class SolarPlanning extends SQLiteOpenHelper {
    private final Repository repository;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean();
    private final Consumer<JSONObject> callback;
    private volatile boolean closed;
    SolarPlanning(Context context,Repository repository,Consumer<JSONObject> callback){
        super(context,"wattpit-planning.db",null,1);this.repository=repository;this.callback=callback;setWriteAheadLoggingEnabled(true);
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE observations(start INTEGER NOT NULL,end INTEGER NOT NULL,source TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(start,end,source))");
        db.execSQL("CREATE TABLE forecasts(key TEXT NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,issued INTEGER NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(key,start,end,issued))");
        db.execSQL("CREATE TABLE contexts(at INTEGER PRIMARY KEY,key TEXT NOT NULL,mobile_free INTEGER NOT NULL,source TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
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
    void context(String key){
        if(closed||!(key.isEmpty()||key.matches("k-[a-f0-9]{16}")))return;
        worker.execute(()->{try{
            String source=source(devices());boolean free=mobileFree();SQLiteDatabase db=getWritableDatabase();
            try(Cursor c=db.rawQuery("SELECT key,mobile_free FROM contexts WHERE source=? ORDER BY at DESC LIMIT 1",new String[]{source})){if(c.moveToFirst()&&key.equals(c.getString(0))&&free==(c.getInt(1)==1))return;}
            ContentValues v=new ContentValues();v.put("at",System.currentTimeMillis());v.put("key",key);v.put("source",source);v.put("mobile_free",free?1:0);db.insertOrThrow("contexts",null,v);
        }catch(Exception ignored){/* No context means no eligible learning interval. */}});
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
    void request(){
        if(closed||!busy.compareAndSet(false,true))return;
        worker.execute(()->{JSONObject result=new JSONObject();try{
            if(repository.archiveBusy)return;long now=System.currentTimeMillis();String[] devices=devices();String source=source(devices());
            JSONObject weather=new JSONObject(repository.prefs.getString("weatherCache","{}"));SQLiteDatabase db=getWritableDatabase();long since=now-7L*86400000;
            try(Cursor c=db.rawQuery("SELECT MAX(end) FROM observations WHERE source=?",new String[]{source})){if(c.moveToFirst()&&!c.isNull(0))since=Math.max(since,c.getLong(0)-2L*86400000);}
            String currentKey="";try(Cursor c=db.rawQuery("SELECT key FROM contexts WHERE source=? ORDER BY at DESC LIMIT 1",new String[]{source})){if(c.moveToFirst())currentKey=c.getString(0);}
            String signature=repository.prefs.getString("equipment-v1","{\"version\":1,\"groups\":[]}")+"|"+repository.prefs.getString("solar-setup-v1","{}");
            JSONArray observations=weather.optInt("version")>=4&&!signature.equals(weather.optString("signature"))?new JSONArray():repository.db.planningObservations(since,now,weather,devices[0],devices[1]);
            db.beginTransaction();try{for(int i=0;i<observations.length();i++){
                JSONObject row=observations.getJSONObject(i);long from=row.getLong("from"),to=row.getLong("to");String key="";boolean free=false,known=false;
                try(Cursor c=db.rawQuery("SELECT key,mobile_free FROM contexts WHERE source=? AND at<=? ORDER BY at DESC LIMIT 1",new String[]{source,""+from})){if(c.moveToFirst()){known=true;key=c.getString(0);free=c.getInt(1)==1;}}
                try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM contexts WHERE source=? AND at>? AND at<?",new String[]{source,""+from,""+to})){if(c.moveToFirst()&&c.getLong(0)>0)free=false;}
                // Never recompute an old site's night windows using the new site's weather/timezone.
                if(known&&!key.equals(currentKey))continue;
                // Unlabelled historical days require actual solar coverage; do not invent old night zeroes.
                if(!known){
                    boolean unmetered=false;JSONArray groups=repository.equipment().getJSONArray("groups");for(int j=0;j<groups.length();j++){JSONObject g=groups.getJSONObject(j);if(!g.optString("measurement").equals("solar")||g.optString("placement").equals("mobile"))unmetered=true;}
                    free=!unmetered&&row.optDouble("actualSolarCoverage",0)>=.98;
                }
                row.put("key",key).put("mobileFree",free&&!row.optBoolean("extraCharging"));ContentValues v=new ContentValues();v.put("start",from);v.put("end",to);v.put("source",source);v.put("payload",row.toString());db.insertWithOnConflict("observations",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            }db.delete("observations","end<?",new String[]{""+(now-400L*86400000)});db.delete("contexts","at<?",new String[]{""+(now-410L*86400000)});db.setTransactionSuccessful();}finally{db.endTransaction();}
            JSONArray samples=new JSONArray(),forecasts=new JSONArray();
            try(Cursor c=db.rawQuery("SELECT payload FROM observations WHERE source=? ORDER BY start DESC LIMIT 1300",new String[]{source})){while(c.moveToNext())samples.put(new JSONObject(c.getString(0)));}
            // One original eligible prediction per interval keeps the bridge payload small.
            // SQLite's single MIN aggregate selects payload from that same earliest row.
            try(Cursor c=db.rawQuery("SELECT payload,MIN(issued) FROM forecasts WHERE key=? AND issued<=start-43200000 AND issued>=start-259200000 GROUP BY start,end ORDER BY start DESC LIMIT 200",new String[]{currentKey})){while(c.moveToNext())forecasts.put(new JSONObject(c.getString(0)));}
            result.put("source",source).put("observations",samples).put("forecasts",forecasts).put("updated",now);
        }catch(Exception e){try{result.put("error","Lerndaten gerade nicht verfügbar. Die Solarvorschau bleibt nutzbar.");}catch(JSONException ignored){}}
        finally{busy.set(false);if(!closed&&result.length()>0)callback.accept(result);}});
    }
    void shutdown(){closed=true;worker.shutdown();/* Existing jobs finish before the process releases SQLite. */}
}
