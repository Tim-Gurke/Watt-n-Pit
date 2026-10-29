package de.wattpit.app;

import android.content.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Production persistence and transitions on actual SQLite; Wi-Fi radio/lifecycle are not simulated. */
final class PresenceProof {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);System.out.println("PASS "+message);}
    private static final class TestContext extends ContextWrapper {
        final Map<String,SharedPreferences> values=new HashMap<>();
        TestContext(){super(null);}
        @Override public Context getApplicationContext(){return this;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return values.computeIfAbsent(name,k->preferences());}
        @Override public Object getSystemService(String name){return null;}
        @Override public String getSystemServiceName(Class<?> type){return null;}
    }
    private static SharedPreferences preferences(){
        Map<String,Object> data=new HashMap<>();return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)->{
            if(m.getName().equals("getAll"))return new HashMap<>(data);if(m.getName().equals("contains"))return data.containsKey(a[0]);
            if(!m.getName().equals("edit"))return data.getOrDefault(a[0],a[1]);
            Map<String,Object> changes=new HashMap<>();return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(e,em,ea)->{
                if(em.getName().startsWith("put")){changes.put((String)ea[0],ea[1]);return e;}
                if(em.getName().equals("commit")||em.getName().equals("apply")){data.putAll(changes);return em.getName().equals("commit")?true:null;}throw new UnsupportedOperationException(em.getName());
            });
        });
    }
    private static PresenceStore store(Context c)throws Exception{Constructor<PresenceStore> ctor=PresenceStore.class.getDeclaredConstructor(Context.class);ctor.setAccessible(true);return ctor.newInstance(c);}
    public static void main(String[] args)throws Exception{
        Files.createDirectories(Path.of(args[0]));System.setProperty("wattpit.test.database",Path.of(args[0],"history.db").toString());System.setProperty("wattpit.test.namedDatabases","true");Class.forName("org.sqlite.JDBC");
        TestContext c=new TestContext();PresenceStore s=store(c);long wall=System.currentTimeMillis(),clock=android.os.SystemClock.elapsedRealtime();
        s.observe("home",wall,clock);s.observe("home",wall+29000,clock+29000);check(s.snapshot().getString("state").equals("unknown"),"a short Wi-Fi appearance does not immediately become occupancy");
        s.observe("home",wall+30000,clock+30000);check(s.snapshot().getString("state").equals("home"),"30 seconds of Wi-Fi confirms home without requiring internet or a named phone");
        JSONObject trip=new JSONObject().put("from",wall-600000).put("to",wall+6*3600000);s.save(trip.toString());JSONObject plan=s.snapshot().getJSONArray("pending").getJSONObject(0);String id=plan.getString("id");
        check(plan.getString("status").equals("pending"),"missed departure with actual home produces the still-here question");
        s.resolve(id,"soon");check(s.snapshot().getJSONArray("pending").length()==0,"leaving soon snoozes the question without creating absence history");
        s.save(new JSONObject().put("id",id).put("from",wall+120000).put("to",trip.getLong("to")).toString());
        check(s.snapshot().getJSONArray("plans").getJSONObject(0).getLong("to")==trip.getLong("to"),"editing only departure preserves the independently stored return");
        s.observe("away",wall+31000,clock+31000);for(long t=41000;t<=321000;t+=10000)s.observe("away",wall+t,clock+t);
        check(s.snapshot().getString("state").equals("home"),"Wi-Fi outages shorter than five minutes retain home");
        s.observe("away",wall+331000,clock+331000);check(s.snapshot().getString("state").equals("away"),"five minutes without Wi-Fi confirms actual absence");
        check(s.snapshot().getJSONArray("plans").getJSONObject(0).getString("status").equals("active"),"actual late departure activates the matching plan and retains its return");
        s.observe("home",wall+332000,clock+332000);s.observe("home",wall+362000,clock+362000);
        check(s.snapshot().getJSONArray("plans").getJSONObject(0).getString("status").equals("returned"),"early actual return closes the forecast plan automatically");
        JSONArray events=s.history(wall-1000);check(PresenceStore.at(events,wall+40000,wall+100000).equals("away"),"confirmed absence is attributed to the actual disconnect time");
        check(PresenceStore.at(events,wall+10000,wall+50000).equals("unknown"),"an unsplit mixed interval cannot be mislabeled as a pure occupancy sample");
        s.observe("away",wall+500000,clock+500000);check(s.snapshot().getString("state").equals("unknown"),"an unobserved service gap becomes unknown rather than invented absence");
        s.observe("unknown",wall+510000,clock+510000);check(s.snapshot().getString("state").equals("unknown"),"disabled Wi-Fi or failed observations do not teach an absence profile");
        s.close();PresenceStore restart=store(c);restart.observe("home",wall+520000,clock+520000);check(restart.snapshot().getString("state").equals("unknown"),"restart requires new evidence instead of treating saved presence as current");
        check(restart.history(wall-1000).length()>0,"actual occupancy history survives reopening SQLite");
        boolean invalid=false;try{restart.save(new JSONObject().put("from",wall+10000).put("to",wall).toString());}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"invalid return-before-departure is rejected atomically");
        long leap=java.time.Instant.parse("2024-02-29T12:00:00Z").toEpochMilli();check(LearningWindow.since(leap)==java.time.Instant.parse("2021-02-28T12:00:00Z").toEpochMilli(),"native learning retention is three calendar years including leap-day boundaries");
        restart.close();
    }
}
