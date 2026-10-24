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
            db.execSQL("INSERT INTO contexts(at,key,mobile_free,source) VALUES(?,?,0,?)",new Object[]{System.currentTimeMillis(),"k-fedcba9876543210",source.toString()});
            repo.db.getWritableDatabase().execSQL("UPDATE readings SET solar=600 WHERE device='planning-solar'");planning.request();result=replies.poll(20,TimeUnit.SECONDS);
            check(result!=null&&row(result.getJSONArray("observations"),start).getDouble("consumption")==132,"a changed site/setup cannot overwrite an earlier site's learned observations with current weather windows");
            repo.db.getWritableDatabase().execSQL("UPDATE readings SET solar=6 WHERE device='planning-solar'");repo.db.getWritableDatabase().execSQL("UPDATE readings SET current=10 WHERE device='planning-shunt' AND ts>=? AND ts<=?",new Object[]{start+11*hour,start+16*hour});
            bins=repo.db.planningObservations(start,end,weather,"planning-solar","planning-shunt");check(row(bins,start+11*hour).getBoolean("extraCharging"),"recognizable additional charging is flagged even before it can teach the consumption model");
        }finally{planning.shutdown();System.clearProperty("wattpit.test.namedDatabases");}
    }
}
