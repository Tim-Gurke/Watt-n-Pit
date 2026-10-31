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
import de.wattpit.app.core.SolarEquipment;

/** Forecast history is separate from measurements. All history work runs off the UI/bridge thread. */
final class SolarPlanning extends SQLiteOpenHelper {
    static final int ALGORITHM=1690;
    private final RollerLedger rollerLedger;
    private final Context context;
    private JSONArray rollerInputs=new JSONArray(),presenceHistory=new JSONArray();
    static volatile String sharedKnowledge="{}";
    private final Repository repository;
    final MobileSolarDisplay display;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean();
    private final AtomicBoolean requestedAgain=new AtomicBoolean();
    private final Consumer<JSONObject> callback;
    private volatile boolean closed;
    final boolean analysis;
    private volatile JSONArray suppliedPresence;
    private volatile JSONObject suppliedPresenceState;
    private volatile long forcedSince=Long.MAX_VALUE;
    private volatile long analysisUntil,invalidateSince=Long.MAX_VALUE;
    private volatile JSONObject lastKnowledge=new JSONObject();
    private volatile String lastFailure="";
    private volatile boolean displayActive=true;
    private String nativeForecastSeen="";
    void displayActive(boolean active){displayActive=active;}
    SolarPlanning(Context context,Repository repository,Consumer<JSONObject> callback){
        this(context,repository,callback,"wattpit-planning.db",null);
    }
    SolarPlanning(Context context,Repository repository,Consumer<JSONObject> callback,String database,RollerLedger ledger){
        super(context,database,null,6);this.repository=repository;this.callback=callback;this.context=context.getApplicationContext();analysis=ledger!=null;rollerLedger=ledger==null?RollerLedger.get(context):ledger;display=new MobileSolarDisplay(repository,this);setWriteAheadLoggingEnabled(true);
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE observations(start INTEGER NOT NULL,end INTEGER NOT NULL,source TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(start,end,source))");
        db.execSQL("CREATE TABLE forecasts(key TEXT NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,issued INTEGER NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(key,start,end,issued))");
        db.execSQL("CREATE TABLE contexts(at INTEGER PRIMARY KEY,key TEXT NOT NULL,mobile_free INTEGER NOT NULL,source TEXT NOT NULL,solar_key TEXT NOT NULL DEFAULT '',payload TEXT NOT NULL DEFAULT '{}')");
        PlanningConfirmation.create(db);
        MobileSolarDisplay.create(db);
        createModelRevision(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){db.execSQL("ALTER TABLE contexts ADD COLUMN solar_key TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE contexts ADD COLUMN payload TEXT NOT NULL DEFAULT '{}'");}
        if(oldVersion<3&&newVersion>=3)PlanningConfirmation.create(db);
        if(oldVersion<4&&newVersion>=4)MobileSolarDisplay.create(db);
        if(oldVersion==4&&newVersion>=5){db.execSQL("ALTER TABLE model_weather ADD COLUMN night INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE model_weather ADD COLUMN sunrise INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE model_weather ADD COLUMN sunset INTEGER NOT NULL DEFAULT 0");}
        if(oldVersion<6&&newVersion>=6)createModelRevision(db);
    }
    private static void createModelRevision(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS analysis_model_meta(revision INTEGER NOT NULL)");db.execSQL("INSERT INTO analysis_model_meta SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM analysis_model_meta)");
        db.execSQL("CREATE TABLE IF NOT EXISTS analysis_model_tables(name TEXT PRIMARY KEY,revision INTEGER NOT NULL)");
        for(String table:AnalysisExchange.TABLES){db.execSQL("INSERT OR IGNORE INTO analysis_model_tables VALUES(?,1)",new Object[]{table});for(String event:new String[]{"INSERT","UPDATE","DELETE"})db.execSQL("CREATE TRIGGER IF NOT EXISTS model_"+table+"_"+event+" AFTER "+event+" ON "+table+" BEGIN UPDATE analysis_model_meta SET revision=revision+1; UPDATE analysis_model_tables SET revision=(SELECT revision FROM analysis_model_meta) WHERE name='"+table+"'; END");}
        db.execSQL("CREATE INDEX IF NOT EXISTS analysis_observations ON observations(start)");db.execSQL("CREATE INDEX IF NOT EXISTS analysis_forecasts ON forecasts(start)");
    }
    long modelRevision(){try(Cursor c=getReadableDatabase().rawQuery("SELECT revision FROM analysis_model_meta",null)){return c.moveToFirst()?c.getLong(0):0;}}
    boolean hasObservations(){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM observations WHERE source=? LIMIT 1",new String[]{RollerLedger.source(repository.prefs)})){return c.moveToFirst();}}
    void analysisPresence(JSONArray rows,JSONObject state,long since,long until){suppliedPresence=rows;suppliedPresenceState=state;forcedSince=Math.min(forcedSince,since);analysisUntil=until;}
    void invalidateLearning(long since){if(since>0)invalidateSince=Math.min(invalidateSince,since);}
    long learningSince(long now)throws Exception{
        long since=now-7L*86400000;String source=source(devices());
        try(Cursor c=getReadableDatabase().rawQuery("SELECT MAX(end) FROM observations WHERE source=? AND end<=?",new String[]{source,""+now})){if(c.moveToFirst()&&!c.isNull(0))since=Math.max(since,c.getLong(0)-2L*86400000);else{long first=repository.db.stats().optLong("from");if(first>0)since=Math.min(since,first);}}
        return Math.max(LearningWindow.since(now),Math.min(since,forcedSince));
    }
    void captureNative(){if(analysis)return;try{
        display.record();display.archiveWeather();JSONObject eq=repository.equipment(),plan=setup(),place=new JSONObject(repository.prefs.getString("weatherPlace","{}"));String src=RollerLedger.source(repository.prefs);
        String key=SolarKeys.key(eq,plan,place,src,false,false);if(key.isEmpty())return;
        context(new JSONObject().put("key",key).put("solarKey",SolarKeys.key(eq,plan,place,src,true,false)).put("systemKey",SolarKeys.key(eq,plan,new JSONObject("{\"latitude\":0,\"longitude\":0}"),src,true,true)).toString());
        String weather=repository.prefs.getString("weatherCache","{}");String seen=key+weather;
        if(!seen.equals(nativeForecastSeen)){JSONObject w=new JSONObject(weather);String signature=repository.prefs.getString("equipment-v1","{\"version\":1,\"groups\":[]}")+"|"+repository.prefs.getString("solar-setup-v1","{}");if(w.optInt("version")<4||signature.equals(w.optString("signature"))){JSONArray rows=NativeForecast.rows(eq,plan,w,src);if(rows.length()>0)remember(rows.toString());}nativeForecastSeen=seen;}
    }catch(Exception ignored){}}
    JSONObject knowledge(){return lastKnowledge;}
    JSONObject calculateKnowledge()throws Exception{request();JSONObject value=worker.submit(()->lastKnowledge).get(120,TimeUnit.SECONDS);if(value.length()==0||!lastFailure.isEmpty())throw new java.io.IOException(lastFailure.isEmpty()?"Lerndaten konnten nicht berechnet werden.":lastFailure);return value;}
    void flushInputs()throws Exception{worker.submit(()->{}).get(10,TimeUnit.SECONDS);}
    private MobileSolarDisplay.Model displayModel(long from,long to,String source)throws Exception{
        long lookback=Math.max(0,from/86400000L*86400000L-4L*86400000L);MobileSolarDisplay.Model potential=display.model(lookback,to);
        boolean needsAccounting=false;for(de.wattpit.app.core.EnergyIntegral.ModelInterval m:potential.extra)if(m.estimated&&m.interval.to>from&&m.interval.from<to){needsAccounting=true;break;}
        if(!needsAccounting){MobileSolarDisplay.Model result=new MobileSolarDisplay.Model();for(de.wattpit.app.core.EnergyIntegral.ModelInterval m:potential.extra){long a=Math.max(from,m.interval.from),b=Math.min(to,m.interval.to);if(b>a)result.extra.add(m.slice(a,b));}for(de.wattpit.app.core.EnergyIntegral.ModelInterval m:potential.mobile){long a=Math.max(from,m.interval.from),b=Math.min(to,m.interval.to);if(b>a)result.mobile.add(m.slice(a,b));}return result;}
        List<de.wattpit.app.core.SampleTimeline.Interval> special=new ArrayList<>();JSONArray rows=rollerLedger.read(RollerLedger.source(repository.prefs),lookback,to);
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);long a=r.optLong("from"),b=r.optLong("to");double wh=r.optDouble("wh",Double.NaN);if(b>a&&Double.isFinite(wh)&&wh>=0)special.add(new de.wattpit.app.core.SampleTimeline.Interval(a,b,wh*3600000/(b-a),wh*3600000/(b-a)));}
        de.wattpit.app.core.MobileSolarAccounting.Result adjusted=repository.db.adjustMobileModel(lookback,from,to,source,potential.extra,potential.mobile,special);
        MobileSolarDisplay.Model result=new MobileSolarDisplay.Model();result.extra.addAll(adjusted.extra);result.mobile.addAll(adjusted.mobile);result.missingPriorMillis=adjusted.missingPriorMillis;result.abnormalMillis=adjusted.abnormalMillis;result.limitedMillis=adjusted.limitedMillis;return result;
    }
    private JSONObject modelNotes(JSONObject data,MobileSolarDisplay.Model model)throws Exception{
        data.getJSONObject("summary").put("missingPriorMillis",model.missingPriorMillis).put("abnormalMillis",model.abnormalMillis).put("limitedUnknownMillis",model.limitedMillis).put("mobileAccountingVersion",2);return data;
    }
    JSONObject energyFlow(long from,long to,long solarFrom,long solarTo,String source)throws Exception{
        long until=Math.min(to,System.currentTimeMillis());MobileSolarDisplay.Model model=displayModel(from,until,source);
        JSONObject result=repository.db.energyFlow(from,until,solarFrom,solarTo,source,model.extra,model.mobile).put("accountingVersion",2);modelNotes(result.getJSONObject("consumption"),model);modelNotes(result.getJSONObject("mobile"),model);return result;
    }
    JSONObject energyFlow(long from,long to,long solarFrom,String source)throws Exception{return energyFlow(from,to,solarFrom,to,source);}
    JSONObject dashboardData(long from,long to,String metric)throws Exception{
        if(metric.equals("mobileSolarPower")){long until=Math.min(to,System.currentTimeMillis());MobileSolarDisplay.Model model=displayModel(from,until,"all");return modelNotes(display.power(from,until,model),model);}
        String kind=metric.equals("mobileSolarEnergy")?"mobile":metric.equals("consumptionEnergy")?"consumption":metric.equals("flowSolarEnergy")?"solar":metric.equals("flowBalanceEnergy")?"balance":null;
        return kind==null?repository.db.dashboardData(from,to,metric):energyFlow(from,to,from,to,"all").getJSONObject(kind);
    }
    JSONArray history(long from,long to,String metric,String source)throws Exception{
        if(metric.equals("mobileSolarPower"))return dashboardData(from,to,metric).getJSONArray("points");
        String kind=metric.equals("mobileSolarEnergy")?"mobile":metric.equals("consumptionEnergy")?"consumption":metric.equals("flowSolarEnergy")?"solar":metric.equals("flowBalanceEnergy")?"balance":null;
        return kind==null?repository.db.query(from,to,metric,source):energyFlow(from,to,from,to,source).getJSONObject(kind).getJSONArray("points");
    }
    JSONObject dailyEnergy(long from,long to,JSONObject weather)throws Exception{
        if(from<0||to<=from||to-from>32L*86400000)throw new IllegalArgumentException("Ungültiger Tageszeitraum.");
        long until=Math.min(to,System.currentTimeMillis());MobileSolarDisplay.Model model=displayModel(from,until,"all");return repository.db.dailyEnergy(from,until,weather,model.extra).put("accountingVersion",2);
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
            String key=keys.optString("key"),solarKey=keys.optString("solarKey",key),systemKey=keys.optString("systemKey","");
            if(!key.matches("k-[a-f0-9]{16}")||!solarKey.matches("k-[a-f0-9]{16}")||!systemKey.isEmpty()&&!systemKey.matches("k-[a-f0-9]{16}"))return;
            // Capture at the bridge call, before another quick mobile/site change can alter prefs.
            String source=source(devices());boolean free=mobileFree();long at=System.currentTimeMillis();
            JSONObject payload=new JSONObject().put("systemKey",systemKey).put("equipment",repository.equipment()).put("setup",setup()).put("place",new JSONObject(repository.prefs.getString("weatherPlace","{}")));
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
            }db.delete("forecasts","start<?",new String[]{""+(LearningWindow.since(now))});db.setTransactionSuccessful();
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
            double factor=SolarEquipment.yieldFactor(g.optString("controller"),g.optString("controllerType","auto"),g.optString("yieldMode"),number(g,"yieldFactor"));
            result+=g.getInt("count")*g.getDouble("watts")*irradiance/1000*factor*new double[]{1,.8,.55,.25}[value];
        }return result;
    }
    private static boolean unmetered(JSONObject snapshot)throws Exception{
        JSONObject equipment=snapshot.getJSONObject("equipment"),plan=snapshot.getJSONObject("setup").getJSONObject("groups");JSONArray groups=equipment.getJSONArray("groups");
        for(int i=0;i<groups.length();i++){JSONObject g=groups.getJSONObject(i);if(plan.getJSONObject(g.getString("id")).optBoolean("active")&&!g.optString("measurement").equals("solar"))return true;}return false;
    }
    private static boolean unshadedMeasured(JSONObject snapshot,int slot)throws Exception{
        JSONArray groups=snapshot.getJSONObject("equipment").getJSONArray("groups");JSONObject settings=snapshot.getJSONObject("setup").getJSONObject("groups");boolean any=false;
        for(int i=0;i<groups.length();i++){JSONObject g=groups.getJSONObject(i);if(!g.optString("measurement").equals("solar"))continue;JSONObject p=settings.optJSONObject(g.getString("id"));
            if(p==null||!p.optBoolean("active")||geometry(g,p)==null)return false;JSONArray shade=p.optJSONArray("shade");if(shade==null||shade.isNull(slot)||shade.optInt(slot,-1)!=0)return false;any=true;
        }return any;
    }
    private static boolean rollerCharging(JSONArray windows,long from,long to)throws JSONException{
        for(int i=0;i<windows.length();i++){JSONObject w=windows.getJSONObject(i);long end=w.optLong("to");if(w.optLong("from")<to&&(end==0||end>from))return true;}return false;
    }
    private static void reject(JSONObject reasons,String code,long millis)throws Exception{reasons.put(code,reasons.optLong(code)+millis);}
    private static boolean reconstructionAllowed(JSONObject part,JSONObject profile){
        if(part.has("stateMax")&&(part.optDouble("stateMin")!=3||part.optDouble("stateMax")!=3))return false;
        if(part.has("errorMax")&&part.optDouble("errorMax")!=0)return false;
        String limit=VictronSettings.limit(3,part.optDouble("voltageMax",Double.NaN),part.optDouble("currentMax",Double.NaN),number(profile,"absorptionVoltage"),number(profile,"maxChargeCurrent"),number(profile,"temperatureCompensation"));
        return !limit.equals("voltage")&&!limit.equals("current");
    }
    private boolean annotate(JSONObject row,NavigableMap<Long,ContextEntry> contexts,JSONArray profiles,JSONObject weather,String currentKey,String solarKey,long now,JSONArray specialUses,List<PlanningConfirmation.Entry> confirmations)throws Exception{
        JSONArray segments=row.getJSONArray("segments");double wh=0,estimatedWh=0,reconstructedWh=0,rollerInputWh=0;long duration=0,estimated=0,latest=0,excluded=0,reconstructed=0,confirmedMillis=0,rollerAdjustedMillis=0;boolean solarEligible=true,portableEligible=true,free=true;String rowKey="",rowSolarKey="",rowSystemKey="";JSONObject rejected=new JSONObject(),presenceUsage=new JSONObject();
        JSONObject current=new JSONObject().put("equipment",repository.equipment()).put("setup",setup());
        for(int i=0;i<segments.length();i++){
            JSONObject segment=segments.getJSONObject(i);long from=segment.getLong("from"),to=segment.getLong("to");ContextEntry context=at(contexts,from);
            PlanningConfirmation.Entry confirmation=null;if(context==null)for(PlanningConfirmation.Entry e:confirmations)if(e.contains(from,to)){confirmation=e;break;}
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
            }else if(confirmation!=null){snapshot=confirmation.payload;known=true;extra=unmetered(snapshot);free&=!extra;if(rowKey.isEmpty())rowKey=confirmation.key;}
            else free=false;
            boolean night=segment.optLong("lightMillis",1)==0,clear=unrestricted(segment,profileAt(profiles,from,to));
            if(context!=null){if(rowSolarKey.isEmpty())rowSolarKey=context.solarKey;else if(!rowSolarKey.equals(context.solarKey))solarEligible=false;
                String hardware=context.payload.optString("systemKey","");if(hardware.isEmpty())portableEligible=false;if(rowSystemKey.isEmpty())rowSystemKey=hardware;else if(!rowSystemKey.equals(hardware))portableEligible=false;}
            if(!night&&(segment.optDouble("solarCoverage",0)<.98||!clear||context==null))solarEligible=false;
            if(snapshot==null||!unshadedMeasured(snapshot,row.getInt("slot")))portableEligible=false;
            JSONArray pieces=segment.optJSONArray("usageSegments");if(pieces==null)pieces=new JSONArray().put(segment);
            double sectionWh=0,sectionEstimatedWh=0,sectionPotential=0,sectionRollerWh=0;long sectionDuration=0,sectionEstimated=0,sectionLatest=0,sectionReconstructed=0,sectionRollerMillis=0;
            for(int j=0;j<pieces.length();j++){
                JSONObject part=pieces.getJSONObject(j);long a=part.getLong("from"),b=part.getLong("to");
                // Deliberately assigned roller charging must not become normal household demand.
                boolean roller=rollerCharging(specialUses,a,b);Double rollerWh=Double.valueOf(0);if(roller)rollerWh=RollerLedger.estimate(rollerInputs,a,b);
                if(roller&&rollerWh==null){excluded+=b-a;reject(rejected,"roller",b-a);continue;}
                // Unknown intervals stay out; only their actually observed neighbours can teach usage.
                if(!known&&part.optDouble("actualSolarCoverage",0)<.98){reject(rejected,"solarGap",b-a);continue;}
                if(part.optDouble("coverage",0)<.98){reject(rejected,"measurementGap",b-a);continue;}
                double value=part.optDouble("consumption",Double.NaN);if(!Double.isFinite(value)){reject(rejected,"missingValue",b-a);continue;}
                if(roller)value-=rollerWh;
                boolean estimate=extra&&!night,reconstructPart=false;double partPotential=0;
                if(estimate){
                    if(!known){reject(rejected,"unknownSetup",b-a);continue;}
                    JSONObject profile=profileAt(profiles,a,b);boolean clearPart=unrestricted(part,profile),reconstruct=confirmation!=null&&!clearPart&&reconstructionAllowed(part,profile);
                    if(!clearPart&&!reconstruct){reject(rejected,profile==null&&reconstructionAllowed(part,null)?"controllerProfile":"chargeLimit",b-a);continue;}
                    Double mobile=mobileEstimate(snapshot,confirmation==null?weather:confirmation.weather,a,b,row.getInt("slot"));if(mobile==null){if(context!=null&&!context.key.equals(currentKey))return false;reject(rejected,"weather",b-a);continue;}
                    value+=mobile;
                    if(reconstruct){reconstructPart=true;partPotential=mobile;}
                }else if(segment.optBoolean("extraCharging")||part.optBoolean("extraCharging")){reject(rejected,"extraCharging",b-a);continue;}
                if(Math.abs(value)>1000000){reject(rejected,"invalidValue",b-a);continue;}
                if(reconstructPart){sectionReconstructed+=b-a;sectionPotential+=partPotential;}
                sectionWh+=value;sectionDuration+=b-a;sectionLatest=Math.max(sectionLatest,b);
                if(estimate||roller){sectionEstimated+=b-a;sectionEstimatedWh+=value;}
                if(roller){sectionRollerMillis+=b-a;sectionRollerWh+=rollerWh;}
            }
            // Keep the original hourly/setup bucket: clipping negative fragments alone
            // would bias a variable mobile source upwards. Judge their signed sum together.
            if(sectionWh<0||sectionWh>1000000){reject(rejected,"inconsistentEnergy",sectionDuration);continue;}
            if(sectionReconstructed>0){
                // Unknown historical curtailment: actual mobile input lies between
                // no contribution and model potential. Use the feasible midpoint
                // of the signed hourly bucket, never clip individual fragments.
                double adjustment=(Math.max(0,sectionWh-sectionPotential)+sectionWh)/2-sectionWh;
                sectionWh+=adjustment;sectionEstimatedWh+=adjustment;
                reconstructed+=sectionEstimated;reconstructedWh+=sectionEstimatedWh;
            }
            rollerAdjustedMillis+=sectionRollerMillis;rollerInputWh+=sectionRollerWh;wh+=sectionWh;duration+=sectionDuration;estimated+=sectionEstimated;estimatedWh+=sectionEstimatedWh;latest=Math.max(latest,sectionLatest);
            if(confirmation!=null)confirmedMillis+=sectionDuration;
            if(sectionDuration>0){String occupancy=PresenceStore.at(presenceHistory,from,to);JSONObject u=presenceUsage.optJSONObject(occupancy);if(u==null){u=new JSONObject();presenceUsage.put(occupancy,u);}
                String[] fields={"wh","duration","estimatedDuration","estimatedWh","measuredWh","reconstructedDuration","reconstructedWh","rollerAdjustedDuration","rollerInputWh"};
                double[] amounts={sectionWh,sectionDuration,sectionEstimated,sectionEstimatedWh,sectionWh-sectionEstimatedWh,sectionReconstructed,sectionReconstructed>0?sectionEstimatedWh:0,sectionRollerMillis,sectionRollerWh};
                for(int k=0;k<fields.length;k++)u.put(fields[k],u.optDouble(fields[k],0)+amounts[k]);u.put("latest",Math.max(u.optLong("latest"),sectionLatest));
            }
        }
        row.remove("segments");row.put("key",rowKey).put("solarKey",rowSolarKey.isEmpty()?solarKey:rowSolarKey).put("systemKey",rowSystemKey).put("portableEligible",portableEligible&&solarEligible&&!rowSystemKey.isEmpty()).put("mobileFree",free&&!row.optBoolean("extraCharging"))
            .put("usageByPresence",presenceUsage).put("learningVersion",2).put("solarEligible",solarEligible&&row.getLong("to")<=now)
            .put("usage",new JSONObject().put("wh",wh).put("duration",duration).put("excludedRollerMillis",excluded).put("estimatedDuration",estimated).put("estimatedWh",estimatedWh).put("measuredWh",wh-estimatedWh).put("rollerAdjustedDuration",rollerAdjustedMillis).put("rollerInputWh",rollerInputWh).put("reconstructedDuration",reconstructed).put("reconstructedWh",reconstructedWh).put("confirmedDuration",confirmedMillis).put("rejected",rejected).put("latest",latest));return true;
    }
    void request(){
        if(closed||!analysis&&!displayActive)return;if(!busy.compareAndSet(false,true)){requestedAgain.set(true);return;}
        worker.execute(()->{JSONObject result=new JSONObject();try{
            if(repository.archiveBusy)return;long now=analysis&&analysisUntil>0?analysisUntil:System.currentTimeMillis();String[] devices=devices();String source=source(devices());
            JSONObject weather=new JSONObject(repository.prefs.getString("weatherCache","{}"));SQLiteDatabase db=getWritableDatabase();long since=now-7L*86400000;
            boolean confirmed=PlanningConfirmation.installApproved(db,source);PlanningConfirmation.Result confirmations=PlanningConfirmation.read(db,source,weather);
            repository.prefs.edit().putLong("planningWeatherSince",confirmations.weatherSince).apply();
            if(confirmations.weatherSince>0&&!analysis)WeatherClient.refresh(repository,false);
            JSONArray days=new JSONArray();Set<String> dated=new HashSet<>();for(PlanningConfirmation.Entry e:confirmations.entries){JSONArray old=e.weather.optJSONArray("days");if(old!=null)for(int i=0;i<old.length();i++){JSONObject d=old.getJSONObject(i);if(dated.add(d.getString("day")))days.put(d);}}
            JSONArray freshDays=weather.optJSONArray("days");if(freshDays!=null)for(int i=0;i<freshDays.length();i++){JSONObject d=freshDays.getJSONObject(i);if(dated.add(d.getString("day")))days.put(d);}
            JSONObject observationWeather=new JSONObject(weather.toString()).put("days",days);
            String timeRevision=repository.db.archiveClockRevision();
            boolean rebuild=confirmed||repository.prefs.getInt("planningEnergyVersion."+source,0)<ALGORITHM||!timeRevision.equals(repository.prefs.getString("planningArchiveTimeRevision."+source,""));
            if(rebuild){long first=repository.db.stats().optLong("from",0);if(first>0)since=Math.max(LearningWindow.since(now),Math.min(since,first));try(Cursor c=db.rawQuery("SELECT MIN(start) FROM observations WHERE source=?",new String[]{source})){if(c.moveToFirst()&&!c.isNull(0))since=Math.max(LearningWindow.since(now),Math.min(since,c.getLong(0)));}}
            else try(Cursor c=db.rawQuery("SELECT MAX(end) FROM observations WHERE source=? AND end<=?",new String[]{source,""+now})){if(c.moveToFirst()&&!c.isNull(0))since=Math.max(since,c.getLong(0)-2L*86400000);}
            if(confirmations.changedFrom!=Long.MAX_VALUE)since=Math.max(LearningWindow.since(now),Math.min(since,confirmations.changedFrom));
            since=Math.max(LearningWindow.since(now),Math.min(since,forcedSince));
            long[] inputRange=rollerLedger.range(source);long previousInputEnd=repository.prefs.getLong("planningRollerInputEnd."+source,0);
            if(inputRange[1]>previousInputEnd)since=Math.max(LearningWindow.since(now),Math.min(since,previousInputEnd>0?previousInputEnd-3600000:inputRange[0]));
            try{java.time.ZoneId zone=java.time.ZoneId.of(weather.optString("timezone",java.time.ZoneId.systemDefault().getId()));since=java.time.Instant.ofEpochMilli(since).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();}catch(Exception ignored){}
            JSONArray specialUses=new JSONArray(repository.prefs.getString("rollerChargeWindows","[]")),previousUses=new JSONArray(repository.prefs.getString("planningSpecialUsage."+source,"[]"));
            Map<Long,Long> previousWindows=new HashMap<>(),nextWindows=new HashMap<>();
            for(int i=0;i<previousUses.length();i++){JSONObject use=previousUses.getJSONObject(i);previousWindows.put(use.getLong("from"),use.optLong("to"));}
            for(int i=0;i<specialUses.length();i++){JSONObject use=specialUses.getJSONObject(i);nextWindows.put(use.getLong("from"),use.optLong("to"));}
            long changedAt=now;Set<Long> starts=new HashSet<>(previousWindows.keySet());starts.addAll(nextWindows.keySet());
            for(long at:starts)if(!Objects.equals(previousWindows.get(at),nextWindows.get(at)))changedAt=Math.min(changedAt,at);
            if(changedAt<now){java.time.ZoneId zone;try{zone=java.time.ZoneId.of(weather.optString("timezone",java.time.ZoneId.systemDefault().getId()));}catch(Exception e){zone=java.time.ZoneId.systemDefault();}
                long dayStart=java.time.Instant.ofEpochMilli(changedAt).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();since=Math.max(LearningWindow.since(now),Math.min(since,dayStart));}
            if(analysis&&forcedSince!=Long.MAX_VALUE)since=forcedSince;
            NavigableMap<Long,ContextEntry> contexts=new TreeMap<>();List<Long> changes=new ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT at,key,mobile_free,solar_key,payload FROM contexts WHERE source=? ORDER BY at",new String[]{source})){while(c.moveToNext()){ContextEntry entry=new ContextEntry(c);contexts.put(entry.at,entry);changes.add(entry.at);}}
            for(PlanningConfirmation.Entry e:confirmations.entries){changes.add(e.from);changes.add(e.to);}
            ContextEntry latest=contexts.isEmpty()?null:contexts.lastEntry().getValue();String currentKey=latest==null?"":latest.key,solarKey=latest==null?"":latest.solarKey;
            presenceHistory=suppliedPresence==null?PresenceStore.get(context).history(since):suppliedPresence;
            for(int i=0;i<presenceHistory.length();i++)changes.add(presenceHistory.getJSONObject(i).getLong("at"));
            JSONArray profiles=repository.db.deviceSettingsHistory(devices[0],since,now);
            for(int i=0;i<profiles.length();i++){JSONObject p=profiles.getJSONObject(i);changes.add(p.getLong("since"));changes.add(p.getLong("until"));changes.add(p.getLong("until")+VictronSettings.MAX_AGE);}
            // Split at actual special-use boundaries; preserve ordinary household evidence
            // immediately before/after charging rather than discarding the enclosing hour.
            for(int i=0;i<specialUses.length();i++){JSONObject use=specialUses.getJSONObject(i);changes.add(use.getLong("from"));if(use.optLong("to")>0)changes.add(use.getLong("to"));}
            String signature=repository.prefs.getString("equipment-v1","{\"version\":1,\"groups\":[]}")+"|"+repository.prefs.getString("solar-setup-v1","{}");
            rollerInputs=rollerLedger.read(source,since,now);
            for(int i=0;i<rollerInputs.length();i++){JSONObject input=rollerInputs.getJSONObject(i);changes.add(input.getLong("from"));changes.add(input.getLong("to"));}
            JSONArray observations=weather.optInt("version")>=4&&!signature.equals(weather.optString("signature"))?new JSONArray():repository.db.planningObservations(since,now,observationWeather,devices[0],devices[1],changes);
            db.beginTransaction();try{if(analysis&&invalidateSince!=Long.MAX_VALUE)db.delete("observations","source=? AND end>? AND start<?",new String[]{source,""+invalidateSince,""+now});for(int i=0;i<observations.length();i++){
                JSONObject row=observations.getJSONObject(i);long from=row.getLong("from"),to=row.getLong("to");
                // A shorter rolling weather cache cannot erase an older valid
                // observation before its missing historical weather is restored.
                if(!dated.contains(row.getString("day"))&&!rollerCharging(specialUses,from,to))try(Cursor c=db.rawQuery("SELECT 1 FROM observations WHERE source=? AND start=? AND end=?",new String[]{source,""+from,""+to})){if(c.moveToFirst())continue;}
                if(!annotate(row,contexts,profiles,weather,currentKey,solarKey,now,specialUses,confirmations.entries))continue;
                if(row.getJSONObject("usage").optLong("duration")==0&&row.optDouble("solarCoverage",0)<=0)continue;
                ContentValues v=new ContentValues();v.put("start",from);v.put("end",to);v.put("source",source);v.put("payload",row.toString());db.insertWithOnConflict("observations",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            }db.delete("observations","end<?",new String[]{""+(LearningWindow.since(now))});db.delete("contexts","at<? AND at NOT IN (SELECT MAX(at) FROM contexts WHERE at<? GROUP BY source)",new String[]{""+(LearningWindow.since(now)-10L*86400000),""+(LearningWindow.since(now)-10L*86400000)});db.setTransactionSuccessful();}finally{db.endTransaction();}
            if(observations.length()>0)repository.prefs.edit().putInt("planningEnergyVersion."+source,ALGORITHM).putLong("planningRollerInputEnd."+source,inputRange[1]).putString("planningArchiveTimeRevision."+source,timeRevision).putString("planningSpecialUsage."+source,specialUses.toString()).apply();
            JSONArray samples=new JSONArray(),forecasts=new JSONArray();
            try(Cursor c=db.rawQuery("SELECT payload FROM observations WHERE source=? ORDER BY start DESC LIMIT 3500",new String[]{source})){while(c.moveToNext()){JSONObject row=new JSONObject(c.getString(0));if(!row.has("solarKey")&&currentKey.equals(row.optString("key")))row.put("solarKey",solarKey);samples.put(row);}}
            // One original eligible prediction per interval keeps the bridge payload small.
            // SQLite's single MIN aggregate selects payload from that same earliest row.
            Set<String> forecastKeys=new HashSet<>();for(ContextEntry entry:contexts.values()){forecastKeys.add(entry.key);forecastKeys.add(entry.solarKey);}for(PlanningConfirmation.Entry e:confirmations.entries){forecastKeys.add(e.key);forecastKeys.add(e.solarKey);}
            try(Cursor c=db.rawQuery("SELECT payload,MIN(issued) FROM forecasts WHERE issued<=start-43200000 AND issued>=start-259200000 GROUP BY key,start,end ORDER BY start DESC LIMIT 15000",null)){while(c.moveToNext()&&forecasts.length()<3500){JSONObject f=new JSONObject(c.getString(0));if(forecastKeys.contains(f.optString("key")))forecasts.put(f);}}
            JSONArray notices=new JSONArray();for(PlanningConfirmation.Entry e:confirmations.entries)notices.put(e.notice());
            result.put("source",source).put("observations",samples).put("forecasts",forecasts).put("confirmedSetups",notices).put("presence",suppliedPresenceState==null?PresenceStore.get(context).snapshot():suppliedPresenceState).put("updated",now);
        }catch(Exception e){try{result.put("error","Lerndaten gerade nicht verfügbar. Die Solarvorschau bleibt nutzbar.");}catch(JSONException ignored){}}
        finally{busy.set(false);lastFailure=result.optString("error","");if(!closed&&result.length()>0){if(!result.has("error")){lastKnowledge=result;forcedSince=invalidateSince=Long.MAX_VALUE;if(!analysis)sharedKnowledge=result.toString();}callback.accept(result);}if(!closed&&requestedAgain.getAndSet(false))request();}});
    }
    Future<JSONObject> diagnosticSnapshot(){return worker.submit(()->{
        long now=System.currentTimeMillis();String[] selected=devices();String currentSource=source(selected);SQLiteDatabase db=getReadableDatabase();
        JSONObject result=new JSONObject().put("format","Watt-n-Pit-Diagnose").put("version",2).put("appVersion",BuildConfig.VERSION_NAME).put("learningAlgorithm",ALGORITHM).put("at",now)
            .put("timezone",java.util.TimeZone.getDefault().getID()).put("source",currentSource).put("equipment",repository.equipment()).put("setup",setup())
            .put("weather",new JSONObject(repository.prefs.getString("weatherCache","{}"))).put("history",repository.db.stats())
            .put("capacity",repository.prefs.getFloat("capacity",100f)).put("sampleSeconds",repository.prefs.getInt("sampleSeconds",10));
        JSONArray observations=new JSONArray(),forecasts=new JSONArray(),contexts=new JSONArray(),devices=new JSONArray();
        try(Cursor c=db.rawQuery("SELECT payload FROM observations WHERE source=? ORDER BY start",new String[]{currentSource})){while(c.moveToNext())observations.put(new JSONObject(c.getString(0)));}
        try(Cursor c=db.rawQuery("SELECT payload FROM forecasts ORDER BY start,issued",null)){while(c.moveToNext())forecasts.put(new JSONObject(c.getString(0)));}
        try(Cursor c=db.rawQuery("SELECT at,key,mobile_free,solar_key,payload FROM contexts WHERE source=? ORDER BY at",new String[]{currentSource})){while(c.moveToNext())contexts.put(new JSONObject().put("at",c.getLong(0)).put("key",c.getString(1)).put("mobileFree",c.getInt(2)==1).put("solarKey",c.getString(3)).put("payload",new JSONObject(c.getString(4))));}
        for(int i=0;i<selected.length;i++)if(!selected[i].isEmpty())devices.put(new JSONObject().put("kind",i==0?"solar":"shunt").put("device",selected[i]).put("profiles",repository.db.deviceSettingsHistory(selected[i],LearningWindow.since(now)-10L*86400000,now)).put("archiveTime",repository.archiveTimeDiagnostic(selected[i])));
        JSONArray confirmations=new JSONArray();try(Cursor c=db.rawQuery("SELECT id,start,end,key,solar_key,payload,weather FROM confirmed_setups WHERE source=? ORDER BY start",new String[]{currentSource})){while(c.moveToNext()){PlanningConfirmation.Entry e=new PlanningConfirmation.Entry(c);confirmations.put(e.notice().put("id",e.id).put("key",e.key).put("solarKey",e.solarKey).put("payload",e.payload).put("weather",e.weather));}}
        return result.put("mobileDisplay",display.diagnostic(currentSource)).put("rollerInputIntervals",rollerLedger.read(currentSource,LearningWindow.since(now),now)).put("rollerHandover",RollerHandover.get(context).snapshot()).put("observations",observations).put("forecasts",forecasts).put("contexts",contexts).put("confirmedSetups",confirmations).put("devices",devices);
    });}
    void shutdown(){closed=true;worker.shutdown();/* Existing jobs finish before the process releases SQLite. */}
}
