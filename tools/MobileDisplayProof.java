package de.wattpit.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import de.wattpit.app.core.Reading;
import org.json.*;
import java.time.*;

/** Actual model storage, setup boundaries and display accounting against real SQLite. */
final class MobileDisplayProof {
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("PASS "+text);}
    static void run(Repository repo,Context context)throws Exception{
        System.setProperty("wattpit.test.namedDatabases","true");SolarPlanning p=new SolarPlanning(context,repo,r->{});
        long H=3600000L,start=(System.currentTimeMillis()/H-3)*H,end=start+H;
        try{
            repo.prefs.edit().putString("devices","[{\"mac\":\"AA:11:22:33:44:55\",\"kind\":\"solar\"},{\"mac\":\"AA:11:22:33:44:66\",\"kind\":\"shunt\"}]").commit();
            JSONObject fixed=new JSONObject("{\"id\":\"roof\",\"label\":\"Fixed\",\"count\":2,\"watts\":100,\"placement\":\"fixed\",\"measurement\":\"solar\",\"tilt\":0}"),mobile=new JSONObject("{\"id\":\"case\",\"label\":\"Case\",\"count\":1,\"watts\":100,\"placement\":\"mobile\",\"measurement\":\"shunt\",\"controller\":\"Dokio PWM\",\"tilt\":0}");
            JSONObject eq=Repository.checkedEquipment(new JSONObject().put("version",1).put("groups",new JSONArray().put(fixed).put(mobile))),setup=SolarPlanning.checked(new JSONObject(),eq);
            for(String id:new String[]{"roof","case"})setup.getJSONObject("groups").getJSONObject(id).put("active",true).put("shade",new JSONArray("[0,0,0]"));
            JSONObject place=new JSONObject("{\"name\":\"Public test site\",\"latitude\":52.5,\"longitude\":13.4}");
            JSONObject weather=new JSONObject().put("timezone","UTC").put("place",place).put("solar",new JSONArray().put(new JSONObject().put("geometry","0:0").put("values",new JSONArray().put(new JSONObject().put("t",end).put("v",1000)))));
            repo.prefs.edit().putString("equipment-v1",eq.toString()).putString("solar-setup-v1",setup.toString()).putString("weatherPlace",place.toString()).putString("weatherCache",weather.toString()).commit();
            SQLiteDatabase db=p.getWritableDatabase();db.execSQL("DELETE FROM contexts");db.execSQL("DELETE FROM display_contexts");db.execSQL("DELETE FROM model_weather");String source=RollerLedger.source(repo.prefs);
            JSONObject snapshot=new JSONObject().put("equipment",eq).put("setup",setup).put("place",place);
            db.execSQL("INSERT INTO contexts(at,key,mobile_free,source,solar_key,payload) VALUES(?,?,0,?,?,?)",new Object[]{start-1,"k-1111111111111111",source,"k-2222222222222222",snapshot.toString()});
            for(long t=start;t<=end;t+=1800000){Reading s=new Reading();s.device="AA:11:22:33:44:55";s.kind="solar";s.origin="archive";s.timestamp=t;s.period=1800;s.solar=155.0;repo.db.insert(s);Reading b=new Reading();b.device="AA:11:22:33:44:66";b.kind="shunt";b.origin="archive";b.timestamp=t;b.period=1800;b.power=49.0;b.voltage=13.35;b.current=49.0/13.35;repo.db.insert(b);}
            JSONObject f=p.energyFlow(start,end,start,end,"all");
            check(Math.abs(f.getJSONObject("mobile").getJSONObject("summary").getDouble("last")-60)<1e-8,"stored PWM declaration supplies a separate 60 Wh mobile estimate for a documented hour");
            check(Math.abs(f.getJSONObject("consumption").getJSONObject("summary").getDouble("last")-166)<1e-8&&f.getInt("accountingVersion")==2,"device display adds mobile once: 155 fixed + 60 mobile - 49 battery = 166 Wh");
            check(Math.abs(repo.db.energyFlow(start,end,"all").getJSONObject("consumption").getJSONObject("summary").getDouble("last")-106)<1e-8,"underlying learning residual and original sensor history remain unchanged");
            JSONObject power=p.dashboardData(start,end,"mobileSolarPower").getJSONObject("summary");check(power.getDouble("last")==60&&power.isNull("live"),"historical power is 60 W and never masquerades as a current live model value");
            JSONObject off=new JSONObject(setup.toString());off.getJSONObject("groups").getJSONObject("case").put("active",false);
            db.execSQL("INSERT INTO display_contexts(source,at,payload) VALUES(?,?,?)",new Object[]{source,start+H/2,new JSONObject(snapshot.toString()).put("setup",off).toString()});repo.prefs.edit().putString("solar-setup-v1",off.toString()).commit();
            f=p.energyFlow(start,end,start,end,"all");check(Math.abs(f.getJSONObject("mobile").getJSONObject("summary").getDouble("last")-30)<1e-8&&Math.abs(f.getJSONObject("consumption").getJSONObject("summary").getDouble("last")-136)<1e-8,"switching mobile off mid-hour preserves earlier yield and changes only the remaining half-hour");
            repo.prefs.edit().putString("weatherCache","{}").putString("weatherPlace","{\"latitude\":48,\"longitude\":8}").commit();
            check(Math.abs(p.energyFlow(start,end,start,end,"all").getJSONObject("mobile").getJSONObject("summary").getDouble("last")-30)<1e-8,"saved model weather and the original setup survive a later site change and missing current weather");
            db.execSQL("DELETE FROM model_weather");db.execSQL("DELETE FROM contexts");db.execSQL("DELETE FROM display_contexts");
            db.execSQL("INSERT INTO display_contexts(source,at,payload) VALUES(?,?,?)",new Object[]{source,start-1,new JSONObject(snapshot.toString()).put("setup",off).toString()});
            f=p.energyFlow(start,end,start,end,"all");check(f.getJSONObject("mobile").getJSONObject("summary").getDouble("last")==0&&f.getJSONObject("consumption").getJSONObject("summary").getDouble("last")==106&&f.getJSONObject("consumption").getJSONObject("summary").getLong("modelledMillis")==0,"mobile off works without weather and returns device consumption to fixed solar minus battery");
            repo.prefs.edit().putString("solar-setup-v1",setup.toString()).commit();db.execSQL("DELETE FROM display_contexts");db.execSQL("INSERT INTO display_contexts(source,at,payload) VALUES(?,?,?)",new Object[]{source,start-1,snapshot.toString()});
            f=p.energyFlow(start,end,start,end,"all");check(f.getJSONObject("mobile").getJSONObject("summary").isNull("last")&&f.getJSONObject("consumption").getJSONObject("summary").isNull("last")&&f.getJSONObject("balance").getJSONObject("summary").getDouble("last")==49,"active mobile with missing weather stays unknown while the battery balance remains measured");
            JSONObject noMobile=new JSONObject(eq.toString()).put("groups",new JSONArray().put(fixed));repo.prefs.edit().putString("equipment-v1",noMobile.toString()).putString("solar-setup-v1",SolarPlanning.checked(new JSONObject(),noMobile).toString()).commit();db.execSQL("DELETE FROM display_contexts");db.execSQL("DELETE FROM contexts");
            f=p.energyFlow(start,end,start,end,"all");check(f.getJSONObject("mobile").getJSONObject("summary").getDouble("last")==0&&f.getJSONObject("consumption").getJSONObject("summary").getDouble("last")==106,"a plant that has no mobile module needs no weather or historical mobile context");
            MobileSolarDisplay.Model m=p.display.model(start,end);check(m.extra.size()==1&&!m.extra.get(0).estimated,"absent mobile generation is known zero rather than a fabricated estimate");
            db.execSQL("INSERT INTO contexts(at,key,mobile_free,source,payload) VALUES(?,?,1,?,?)",new Object[]{start-5,"k-3333333333333333","migration-sentinel","{\"retained\":true}"});
            db.execSQL("DROP TABLE display_contexts");db.execSQL("DROP TABLE model_weather");db.execSQL("PRAGMA user_version=3");p.close();
            SolarPlanning upgraded=new SolarPlanning(context,repo,r->{});try{
                SQLiteDatabase next=upgraded.getWritableDatabase();boolean retained;
                try(android.database.Cursor c=next.rawQuery("SELECT payload FROM contexts WHERE source='migration-sentinel'",null)){retained=c.moveToFirst()&&new JSONObject(c.getString(0)).getBoolean("retained");}
                next.rawQuery("SELECT COUNT(*) FROM display_contexts",null).close();next.rawQuery("SELECT COUNT(*) FROM model_weather",null).close();
                check(retained,"upgrading the existing version-3 planning database adds model tables without losing stored setup history");
            }finally{upgraded.shutdown();upgraded.close();}
        }finally{p.shutdown();System.clearProperty("wattpit.test.namedDatabases");}
    }
}
