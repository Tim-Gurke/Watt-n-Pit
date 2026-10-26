package de.wattpit.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.wattpit.app.core.Reading;
import org.json.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real planning SQL and real measurement integration; no weather network, sensors or APK. */
final class SolarPlanningProof {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);System.out.println("PASS "+message);}
    private static JSONObject row(JSONArray rows,long from)throws Exception{for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getLong("from")==from)return rows.getJSONObject(i);throw new AssertionError("Missing planning bin");}
    static void run(Repository repo,Context context)throws Exception{
        long hour=3600000L,day=24*hour,start=LocalDate.now(ZoneOffset.UTC).minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),end=start+day;
        JSONObject group=new JSONObject().put("id","roof").put("label","Testdach").put("count",2).put("watts",100).put("tilt",35).put("placement","fixed").put("controller","Test").put("measurement","solar");
        JSONObject equipment=Repository.checkedEquipment(new JSONObject().put("version",1).put("groups",new JSONArray().put(group))),setup=SolarPlanning.checked(new JSONObject(),equipment);
        setup.getJSONObject("groups").getJSONObject("roof").put("azimuth",157.5).put("shade",new JSONArray("[0,1,2]"));
        check(SolarPlanning.geometry(group,setup.getJSONObject("groups").getJSONObject("roof")).equals("35:-22.5"),"native tilted-irradiance request uses the south-based azimuth convention");
        repo.prefs.edit().putString("equipment-v1",equipment.toString()).putString("solar-setup-v1",setup.toString()).putString("devices",new JSONArray().put(new JSONObject().put("mac","planning-solar").put("kind","solar")).put(new JSONObject().put("mac","planning-shunt").put("kind","shunt")).toString()).commit();
        JSONObject weather=new JSONObject().put("version",5).put("signature",equipment+"|"+setup).put("timezone","UTC").put("days",new JSONArray().put(new JSONObject().put("day",Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate().toString()).put("sunrise",start+7*hour).put("sunset",start+18*hour)));
        repo.prefs.edit().putString("weatherCache",weather.toString()).commit();
        for(long t=start;t<=end;t+=1800000){Reading solar=new Reading();solar.timestamp=t;solar.device="planning-solar";solar.kind="solar";solar.origin="archive";solar.period=1800;solar.solar=6.0;solar.state=3;repo.db.insert(solar);Reading battery=new Reading();battery.timestamp=t;battery.device="planning-shunt";battery.kind="shunt";battery.origin="archive";battery.period=1800;battery.voltage=12.0;battery.current=-.5;repo.db.insert(battery);}
        JSONArray bins=repo.db.planningObservations(start,end,weather,"planning-solar","planning-shunt");double total=0;for(int i=0;i<bins.length();i++)total+=bins.getJSONObject(i).getDouble("consumption");
        check(bins.length()==3&&Math.abs(total-288)<1e-8,"native planning bins retain solar minus battery consumption without a new energy formula");
        check(row(bins,start+11*hour).getDouble("coverage")==1&&row(bins,start+11*hour).getDouble("bulkCoverage")==1,"complete Bulk measurements remain eligible for learning");
        repo.db.getWritableDatabase().execSQL("UPDATE readings SET state=4 WHERE device='planning-solar' AND ts>=? AND ts<=?",new Object[]{start+11*hour,start+16*hour});
        bins=repo.db.planningObservations(start,end,weather,"planning-solar","planning-shunt");check(row(bins,start+11*hour).getDouble("bulkCoverage")<.98,"absorption/full-battery intervals cannot teach a lower solar potential");
        repo.db.getWritableDatabase().execSQL("UPDATE readings SET state=3 WHERE device='planning-solar'");
        System.setProperty("wattpit.test.namedDatabases","true");BlockingQueue<JSONObject> replies=new LinkedBlockingQueue<>();SolarPlanning planning=new SolarPlanning(context,repo,replies::add);
        try{
            StringBuilder source=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest("planning-solar|planning-shunt".getBytes(StandardCharsets.UTF_8)))source.append(String.format(Locale.ROOT,"%02x",b&255));
            SQLiteDatabase db=planning.getWritableDatabase();String key="k-0123456789abcdef";
            db.execSQL("INSERT INTO contexts(at,key,mobile_free,source) VALUES(?,?,1,?)",new Object[]{start-1,key,source.toString()});
            planning.request();JSONObject result=replies.poll(20,TimeUnit.SECONDS);check(result!=null&&!result.has("error"),"learning reads and writes finish asynchronously using a separate SQLite database");
            check(row(result.getJSONArray("observations"),start).getBoolean("mobileFree")&&row(result.getJSONArray("observations"),start).getString("key").equals(key),"observations keep their source and the setup valid at the measured time");
            long issued=System.currentTimeMillis(),future=issued+day;planning.remember(new JSONArray().put(new JSONObject().put("key",key).put("from",future).put("to",future+5*hour).put("issued",issued).put("wh",321)).toString());planning.request();result=replies.poll(20,TimeUnit.SECONDS);
            check(result!=null&&result.getJSONArray("forecasts").length()==1&&result.getJSONArray("forecasts").getJSONObject(0).getDouble("wh")==321,"original issue time and predicted Wh survive native forecast storage");
            repo.prefs.edit().putString("diagnostic-private-test","DIAGNOSTIC-SECRET-SENTINEL").commit();
            JSONObject diagnostic=planning.diagnosticSnapshot().get(20,TimeUnit.SECONDS);
            check(diagnostic.getString("format").equals("Watt-n-Pit-Diagnose")&&diagnostic.getJSONArray("observations").length()>0&&diagnostic.getJSONArray("contexts").length()>0,"diagnostic snapshot retains the actual learning observations and setup timeline");
            check(diagnostic.getJSONArray("forecasts").getJSONObject(0).getDouble("wh")==321&&diagnostic.getJSONArray("devices").length()==2&&diagnostic.has("weather")&&diagnostic.has("history"),"diagnostic snapshot includes original forecasts, weather, device profiles and measurement inventory");
            check(!diagnostic.toString().contains("DIAGNOSTIC-SECRET-SENTINEL"),"diagnostic snapshot never serializes arbitrary private preferences");
            db.execSQL("INSERT INTO contexts(at,key,mobile_free,source) VALUES(?,?,0,?)",new Object[]{System.currentTimeMillis(),"k-fedcba9876543210",source.toString()});
            repo.db.getWritableDatabase().execSQL("UPDATE readings SET solar=600 WHERE device='planning-solar'");planning.request();result=replies.poll(20,TimeUnit.SECONDS);
            check(result!=null&&row(result.getJSONArray("observations"),start).getDouble("consumption")==132,"a changed site/setup cannot overwrite an earlier site's learned observations with current weather windows");
            repo.db.getWritableDatabase().execSQL("UPDATE readings SET solar=6 WHERE device='planning-solar'");repo.db.getWritableDatabase().execSQL("UPDATE readings SET current=10 WHERE device='planning-shunt' AND ts>=? AND ts<=?",new Object[]{start+11*hour,start+16*hour});
            bins=repo.db.planningObservations(start,end,weather,"planning-solar","planning-shunt");check(row(bins,start+11*hour).getBoolean("extraCharging"),"recognizable additional charging is flagged even before it can teach the consumption model");
            learningSegments(repo,planning,db,replies,start+day,source.toString());
        }finally{planning.shutdown();System.clearProperty("wattpit.test.namedDatabases");}
    }
    private static JSONObject ask(SolarPlanning planning,BlockingQueue<JSONObject> replies)throws Exception{
        planning.request();JSONObject result=replies.poll(30,TimeUnit.SECONDS);check(result!=null&&!result.has("error"),"segmented planning completes against the real SQLite store");return result;
    }
    private static void learningSegments(Repository repo,SolarPlanning planning,SQLiteDatabase db,BlockingQueue<JSONObject> replies,long start,String source)throws Exception{
        long hour=3600000L,end=start+24*hour;String key="k-aaaaaaaaaaaaaaaa",solarKey="k-bbbbbbbbbbbbbbbb";
        JSONObject roof=new JSONObject().put("id","roof").put("label","Testdach").put("count",2).put("watts",100).put("tilt",35).put("placement","fixed").put("measurement","solar");
        JSONObject mobile=new JSONObject(roof.toString()).put("id","case").put("label","Testkoffer").put("count",1).put("placement","mobile").put("measurement","shunt");
        JSONObject eq=Repository.checkedEquipment(new JSONObject().put("version",1).put("groups",new JSONArray().put(roof).put(mobile))),setup=SolarPlanning.checked(new JSONObject(),eq);
        for(String id:new String[]{"roof","case"})setup.getJSONObject("groups").getJSONObject(id).put("active",true).put("azimuth",157.5).put("shade",new JSONArray("[0,0,0]"));
        JSONObject place=new JSONObject().put("name","Öffentlicher Testort").put("latitude",52.52).put("longitude",13.41);
        JSONArray values=new JSONArray();for(long t=start+hour;t<=end;t+=hour)values.put(new JSONObject().put("t",t).put("v",t>start+7*hour&&t<=start+18*hour?100:0));
        JSONObject weather=new JSONObject().put("version",5).put("signature",eq+"|"+setup).put("timezone","UTC").put("place",place)
            .put("days",new JSONArray().put(new JSONObject().put("day",Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate().toString()).put("sunrise",start+7*hour).put("sunset",start+18*hour)))
            .put("solar",new JSONArray().put(new JSONObject().put("geometry","35:-22.5").put("values",values)));
        repo.prefs.edit().putString("equipment-v1",eq.toString()).putString("solar-setup-v1",setup.toString()).putString("weatherPlace",place.toString()).putString("weatherCache",weather.toString()).commit();
        JSONObject payload=new JSONObject().put("equipment",eq).put("setup",setup).put("place",place);
        db.execSQL("DELETE FROM contexts");db.execSQL("DELETE FROM observations");db.execSQL("DELETE FROM forecasts");
        db.execSQL("INSERT INTO contexts(at,key,mobile_free,source,solar_key,payload) VALUES(?,?,0,?,?,?)",new Object[]{start-1,key,source,solarKey,payload.toString()});
        SQLiteDatabase history=repo.db.getWritableDatabase();history.execSQL("DELETE FROM device_settings");
        JSONObject profile=new JSONObject().put("absorptionVoltage",14.2).put("floatVoltage",13.6).put("maxChargeCurrent",15).put("temperatureCompensation",0);
        for(long t=start-1;t<=end+4*hour;t+=4*hour)repo.db.deviceSettings("planning-solar",new JSONObject().put("at",t).put("values",profile));
        history.execSQL("DELETE FROM readings WHERE device IN ('planning-solar','planning-shunt') AND ts>=? AND ts<=?",new Object[]{start,end});
        for(long t=start;t<=end;t+=1800000){
            boolean sun=t>=start+7*hour&&t<=start+18*hour;
            Reading s=new Reading();s.timestamp=t;s.device="planning-solar";s.kind="solar";s.origin="archive";s.period=1800;s.solar=sun?6.0:0.0;s.voltage=13.0;s.current=sun?.5:0.0;s.state=3;s.error=0;repo.db.insert(s);
            Reading b=new Reading();b.timestamp=t;b.device="planning-shunt";b.kind="shunt";b.origin="archive";b.period=1800;b.voltage=12.0;b.current=sun?0.0:-.5;repo.db.insert(b);
        }
        JSONObject result=ask(planning,replies),mid=row(result.getJSONArray("observations"),start+11*hour),morning=row(result.getJSONArray("observations"),start);
        check(Math.abs(mid.getDouble("consumption")-30)<1e-8&&Math.abs(mid.getJSONObject("usage").getDouble("wh")-70)<1e-8,"mobile potential corrects only the labelled learning sample; measured residual remains 30 Wh");
        check(mid.getJSONObject("usage").getLong("estimatedDuration")==5*hour&&mid.getBoolean("solarEligible"),"an active unmetered mobile module no longer blocks independent fixed-source solar training");
        check(morning.getJSONObject("usage").getLong("duration")>6*hour&&morning.getJSONObject("usage").getLong("estimatedDuration")<morning.getJSONObject("usage").getLong("duration"),"night evidence is measured even while the mobile flag stays on");
        JSONObject temporaryOff=new JSONObject(setup.toString());temporaryOff.getJSONObject("groups").getJSONObject("case").put("active",false);
        db.execSQL("INSERT INTO contexts(at,key,mobile_free,source,solar_key,payload) VALUES(?,?,1,?,?,?)",new Object[]{start+13*hour+hour/4,"k-cccccccccccccccc",source,solarKey,new JSONObject(payload.toString()).put("setup",temporaryOff).toString()});
        db.execSQL("INSERT INTO contexts(at,key,mobile_free,source,solar_key,payload) VALUES(?,?,0,?,?,?)",new Object[]{start+14*hour,key,source,solarKey,payload.toString()});
        result=ask(planning,replies);mid=row(result.getJSONArray("observations"),start+11*hour);
        check(mid.getJSONObject("usage").getLong("duration")==5*hour&&mid.getJSONObject("usage").getLong("estimatedDuration")==17*hour/4&&mid.getBoolean("solarEligible"),"a mobile switch inside an hour splits only that interval and keeps fixed-source solar experience");
        db.execSQL("DELETE FROM contexts WHERE at>?",new Object[]{start});
        fragmentedLearning(repo,planning,replies,start);
        history.execSQL("DELETE FROM readings WHERE device='planning-shunt' AND ts>=? AND ts<=?",new Object[]{start+11*hour,start+16*hour});
        result=ask(planning,replies);mid=row(result.getJSONArray("observations"),start+11*hour);
        check(mid.getBoolean("solarEligible")&&mid.getDouble("solarCoverage")==1&&mid.getDouble("coverage")<.98,"independently measured solar can train even when the Shunt has a gap");
        for(long t=start+11*hour;t<=start+16*hour;t+=1800000){Reading b=new Reading();b.timestamp=t;b.device="planning-shunt";b.kind="shunt";b.origin="archive";b.period=1800;b.voltage=12.0;b.current=0.0;repo.db.insert(b);}
        history.execSQL("UPDATE readings SET state=5,voltage=13.6 WHERE device='planning-solar' AND ts>=? AND ts<=?",new Object[]{start+11*hour,start+16*hour});
        result=ask(planning,replies);mid=row(result.getJSONArray("observations"),start+11*hour);
        check(mid.getJSONObject("usage").getLong("duration")==0&&!mid.getBoolean("solarEligible"),"voltage-controlled daylight cannot turn unused mobile potential into invented consumption or a lower solar correction");
        check(row(result.getJSONArray("observations"),start).getJSONObject("usage").getLong("duration")>=6*hour,"limited daylight does not discard the day's useful night evidence");
        JSONObject off=new JSONObject(setup.toString());off.getJSONObject("groups").getJSONObject("case").put("active",false);
        payload.put("setup",off);db.execSQL("UPDATE contexts SET payload=?,mobile_free=1",new Object[]{payload.toString()});
        result=ask(planning,replies);mid=row(result.getJSONArray("observations"),start+11*hour);
        check(mid.getJSONObject("usage").getLong("duration")==5*hour&&mid.getJSONObject("usage").getLong("estimatedDuration")==0,"fully measured consumption remains usable in a limited charge phase");
        payload.put("setup",setup);db.execSQL("UPDATE contexts SET payload=?,mobile_free=0",new Object[]{payload.toString()});
        history.execSQL("UPDATE readings SET state=3,voltage=13 WHERE device='planning-solar'");history.execSQL("DELETE FROM device_settings");
        result=ask(planning,replies);mid=row(result.getJSONArray("observations"),start+11*hour);
        check(mid.getJSONObject("usage").getLong("duration")==0&&!mid.getBoolean("solarEligible"),"unknown historical settings do not imply uncurtailed mobile production");
        check(row(result.getJSONArray("observations"),start).getJSONObject("usage").getLong("duration")>=6*hour,"missing controller settings do not block measured night learning");
        repo.db.deviceSettings("planning-solar",new JSONObject().put("at",start).put("values",profile));
        repo.db.deviceSettings("planning-solar",new JSONObject().put("at",start+hour).put("values",profile));
        JSONObject gentle=new JSONObject(profile.toString()).put("absorptionVoltage",13.6);
        repo.db.deviceSettings("planning-solar",new JSONObject().put("at",start+2*hour).put("values",gentle));
        JSONArray profiles=repo.db.deviceSettingsHistory("planning-solar",start,end);
        check(profiles.length()==2&&profiles.getJSONObject(0).getLong("until")==start+hour&&profiles.getJSONObject(1).getLong("since")==start+2*hour,"a newly read gentle profile is timestamped and never retroactively assigned across the unknown change interval");
        db.execSQL("DROP TABLE contexts");db.execSQL("CREATE TABLE contexts(at INTEGER PRIMARY KEY,key TEXT NOT NULL,mobile_free INTEGER NOT NULL,source TEXT NOT NULL)");
        db.execSQL("INSERT INTO contexts VALUES(?,?,1,?)",new Object[]{start,key,source});planning.onUpgrade(db,1,2);
        try(Cursor c=db.rawQuery("SELECT key,solar_key,payload FROM contexts",null)){c.moveToFirst();check(c.getString(0).equals(key)&&c.getString(1).isEmpty()&&c.getString(2).equals("{}"),"planning v1 migration retains the earlier context without inventing its missing configuration");}
        long before;try(Cursor c=history.rawQuery("SELECT COUNT(*) FROM readings",null)){c.moveToFirst();before=c.getLong(0);}
        history.execSQL("DROP TABLE device_settings");repo.db.onUpgrade(history,5,6);
        try(Cursor c=history.rawQuery("SELECT COUNT(*) FROM readings",null)){c.moveToFirst();check(c.getLong(0)==before,"history v5 migration adds profile storage without changing or rebuilding measurements");}
    }
    private static void fragmentedLearning(Repository repo,SolarPlanning planning,BlockingQueue<JSONObject> replies,long start)throws Exception{
        long hour=3600000L,from=start+11*hour,to=from+hour;SQLiteDatabase history=repo.db.getWritableDatabase();
        history.execSQL("DELETE FROM readings WHERE device IN ('planning-solar','planning-shunt') AND ts>=? AND ts<=?",new Object[]{from,to});
        for(long t=from;t<=to;t+=10000){
            boolean missingStatus=t>from&&t<to&&(t-from)%300000==0;
            Reading s=new Reading();s.timestamp=t;s.device="planning-solar";s.kind="solar";s.origin="live";s.period=10;s.solar=6.0;s.voltage=13.0;s.current=.5;if(!missingStatus){s.state=3;s.error=0;}repo.db.insert(s);
            Reading b=new Reading();b.timestamp=t;b.device="planning-shunt";b.kind="shunt";b.origin="live";b.period=10;b.voltage=12.0;b.current=0.0;repo.db.insert(b);
        }
        JSONObject mid=row(ask(planning,replies).getJSONArray("observations"),from),usage=mid.getJSONObject("usage");
        long observed=5*hour-11*20000;
        check(usage.getLong("duration")==observed&&usage.getLong("estimatedDuration")==observed,"eleven short status gaps keep the observed parts of the learning hour without filling the missing 220 seconds");
        check(Math.abs(usage.getDouble("wh")-14.0*observed/hour)<1e-8&&Math.abs(mid.getDouble("consumption")-30)<1e-8,"partial learning keeps proportional mobile potential and preserves the measured five-hour energy total");
        check(!mid.getBoolean("solarEligible"),"partial consumption evidence cannot certify a gappy full interval for solar calibration");
        check(Math.abs(usage.getDouble("wh")-usage.getDouble("measuredWh")-usage.getDouble("estimatedWh"))<1e-8,"measured and estimated usage contributions reconcile to the original learning total");
        history.execSQL("DELETE FROM readings WHERE device IN ('planning-solar','planning-shunt') AND ts>=? AND ts<=?",new Object[]{from,to});
        for(long t=from;t<=to;t+=1800000){Reading s=new Reading();s.timestamp=t;s.device="planning-solar";s.kind="solar";s.origin="archive";s.period=1800;s.solar=6.0;s.voltage=13.0;s.current=.5;s.state=3;s.error=0;repo.db.insert(s);Reading b=new Reading();b.timestamp=t;b.device="planning-shunt";b.kind="shunt";b.origin="archive";b.period=1800;b.voltage=12.0;b.current=0.0;repo.db.insert(b);}
    }
}
