package de.wattpit.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.wattpit.app.core.EnergyIntegral.ModelInterval;
import de.wattpit.app.core.SolarEquipment;
import org.json.*;
import java.time.*;
import java.util.*;

/** Model output stays separate from sensor history and from the learning ledger. */
final class MobileSolarDisplay {
    private static final long HOUR=3600000L;
    private static final Object LOCK=new Object();
    private final Repository repo;private final SolarPlanning planning;
    private String weatherSeen="";
    MobileSolarDisplay(Repository repo,SolarPlanning planning){this.repo=repo;this.planning=planning;}
    static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS display_contexts(source TEXT NOT NULL,at INTEGER NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(source,at))");
        db.execSQL("CREATE TABLE IF NOT EXISTS model_weather(place TEXT NOT NULL,geometry TEXT NOT NULL,end INTEGER NOT NULL,value REAL NOT NULL,slot INTEGER NOT NULL,PRIMARY KEY(place,geometry,end))");
    }
    private JSONObject current()throws Exception{return new JSONObject().put("equipment",repo.equipment()).put("setup",planning.setup()).put("place",new JSONObject(repo.prefs.getString("weatherPlace","{}")));}
    void record()throws Exception{synchronized(LOCK){
        String source=RollerLedger.source(repo.prefs),payload=current().toString();long at=System.currentTimeMillis();SQLiteDatabase db=planning.getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT at,payload FROM display_contexts WHERE source=? ORDER BY at DESC LIMIT 1",new String[]{source})){
            if(c.moveToFirst()){if(payload.equals(c.getString(1)))return;at=Math.max(at,c.getLong(0)+1);}
        }
        ContentValues v=new ContentValues();v.put("source",source);v.put("at",at);v.put("payload",payload);db.insertWithOnConflict("display_contexts",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        long since=LearningWindow.since(at);db.delete("display_contexts","at<? AND at NOT IN (SELECT MAX(at) FROM display_contexts WHERE at<? GROUP BY source)",new String[]{""+since,""+since});
    }}
    private static String place(JSONObject p){
        if(p==null||!Double.isFinite(p.optDouble("latitude",Double.NaN))||!Double.isFinite(p.optDouble("longitude",Double.NaN)))return "";
        return Math.round(p.optDouble("latitude")*1000)+":"+Math.round(p.optDouble("longitude")*1000);
    }
    private void archiveWeather()throws Exception{synchronized(LOCK){
        String text=repo.prefs.getString("weatherCache","{}");if(text.equals(weatherSeen))return;JSONObject weather=new JSONObject(text);String location=place(weather.optJSONObject("place"));JSONArray series=weather.optJSONArray("solar");
        if(location.isEmpty()||series==null)return;ZoneId zone;try{zone=ZoneId.of(weather.optString("timezone"));}catch(Exception e){return;}
        SQLiteDatabase db=planning.getWritableDatabase();long now=System.currentTimeMillis();db.beginTransaction();try{
            for(int i=0;i<series.length();i++){JSONObject row=series.getJSONObject(i);JSONArray values=row.optJSONArray("values");if(values==null)continue;
                for(int j=0;j<values.length();j++){JSONObject h=values.getJSONObject(j);long end=h.optLong("t");double value=h.optDouble("v",Double.NaN);
                    if(end<=LearningWindow.since(now)||end>now+16L*24*HOUR||!Double.isFinite(value)||value<0)continue;
                    int hour=Instant.ofEpochMilli(end-HOUR/2).atZone(zone).getHour();ContentValues v=new ContentValues();v.put("place",location);v.put("geometry",row.optString("geometry"));v.put("end",end);v.put("value",value);v.put("slot",hour<11?0:hour<16?1:2);
                    db.insertWithOnConflict("model_weather",null,v,SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.delete("model_weather","end<?",new String[]{""+LearningWindow.since(now)});db.setTransactionSuccessful();weatherSeen=text;
        }finally{db.endTransaction();}
    }}
    static final class Model {final List<ModelInterval> extra=new ArrayList<>(),mobile=new ArrayList<>();}
    private static boolean hasExtra(JSONObject snapshot)throws Exception{
        JSONArray groups=snapshot.getJSONObject("equipment").getJSONArray("groups");for(int i=0;i<groups.length();i++)if(!groups.getJSONObject(i).optString("measurement").equals("solar"))return true;return false;
    }
    private static final class Hour {final double value;final int slot;Hour(double value,int slot){this.value=value;this.slot=slot;}}
    Model model(long from,long to)throws Exception{
        if(to<=from)return new Model();record();archiveWeather();SQLiteDatabase db=planning.getReadableDatabase();String source=RollerLedger.source(repo.prefs);
        TreeMap<Long,JSONObject> contexts=new TreeMap<>();boolean anyExtra=hasExtra(current());
        try(Cursor c=db.rawQuery("SELECT at,payload FROM contexts WHERE source=? ORDER BY at",new String[]{source})){
            while(c.moveToNext()){JSONObject p=new JSONObject(c.getString(1));if(p.has("equipment")&&p.has("setup")){contexts.put(c.getLong(0),p);anyExtra|=hasExtra(p);}}
        }
        // New records are exact native changes, including changes made from a display phone.
        try(Cursor c=db.rawQuery("SELECT at,payload FROM display_contexts WHERE source=? ORDER BY at",new String[]{source})){
            while(c.moveToNext()){JSONObject p=new JSONObject(c.getString(1));contexts.put(c.getLong(0),p);anyExtra|=hasExtra(p);}
        }
        Model result=new Model();if(!anyExtra){result.extra.add(new ModelInterval(from,to,0,false));result.mobile.add(new ModelInterval(from,to,0,false));return result;}
        Map<String,Map<String,NavigableMap<Long,Hour>>> weather=new HashMap<>();TreeSet<Long> cuts=new TreeSet<>();cuts.add(from);cuts.add(to);
        for(long at:contexts.keySet())if(at>from&&at<to)cuts.add(at);
        Set<String> places=new HashSet<>();for(JSONObject p:contexts.values())places.add(place(p.optJSONObject("place")));
        for(String location:places){Map<String,NavigableMap<Long,Hour>> series=new HashMap<>();weather.put(location,series);
            try(Cursor c=db.rawQuery("SELECT geometry,end,value,slot FROM model_weather WHERE place=? AND end>? AND end<=? ORDER BY end",new String[]{location,""+from,""+(to+HOUR)})){
                while(c.moveToNext()){long end=c.getLong(1);series.computeIfAbsent(c.getString(0),k->new TreeMap<>()).put(end,new Hour(c.getDouble(2),c.getInt(3)));if(end>from&&end<to)cuts.add(end);if(end-HOUR>from&&end-HOUR<to)cuts.add(end-HOUR);}
            }
        }
        Long previous=null;for(long end:cuts){if(previous==null){previous=end;continue;}long start=previous;previous=end;Map.Entry<Long,JSONObject> at=contexts.floorEntry(start);if(at==null)continue;
            JSONObject p=at.getValue(),settings=p.getJSONObject("setup").getJSONObject("groups");JSONArray groups=p.getJSONObject("equipment").getJSONArray("groups");
            double total=0,mobile=0;boolean known=true,estimated=false,mobileEstimated=false;Map<String,NavigableMap<Long,Hour>> series=weather.get(place(p.optJSONObject("place")));
            for(int i=0;i<groups.length();i++){JSONObject g=groups.getJSONObject(i),s=settings.optJSONObject(g.getString("id"));
                if(s==null){known=false;break;}if(!s.optBoolean("active")||g.optString("measurement").equals("solar"))continue;
                String geometry=SolarPlanning.geometry(g,s);NavigableMap<Long,Hour> values=series==null?null:series.get(geometry);Map.Entry<Long,Hour> h=values==null?null:values.ceilingEntry(start+1);
                if(h==null||start<h.getKey()-HOUR||end>h.getKey()){known=false;break;}
                JSONArray shade=s.optJSONArray("shade");int level=shade==null||shade.isNull(h.getValue().slot)?0:shade.optInt(h.getValue().slot,0);
                double factor=SolarEquipment.yieldFactor(g.optString("controller"),g.optString("controllerType","auto"),g.optString("yieldMode"),g.isNull("yieldFactor")?null:g.optDouble("yieldFactor"));
                double watts=g.getDouble("count")*g.getDouble("watts")*h.getValue().value/1000*factor*new double[]{1,.8,.55,.25}[Math.max(0,Math.min(3,level))];
                if(!Double.isFinite(watts)||watts<0){known=false;break;}total+=watts;estimated=true;if(g.optString("placement").equals("mobile")){mobile+=watts;mobileEstimated=true;}
            }
            if(known){result.extra.add(new ModelInterval(start,end,total,estimated));result.mobile.add(new ModelInterval(start,end,mobile,mobileEstimated));}
        }return result;
    }
    JSONObject power(long from,long to)throws Exception{
        long now=System.currentTimeMillis();to=Math.min(to,now);Model model=model(from,to);JSONArray points=new JSONArray();double area=0,min=Double.POSITIVE_INFINITY,max=0;long covered=0,lastEnd=-1;Double last=null;
        for(ModelInterval m:model.mobile){long a=m.interval.from,b=m.interval.to;double v=m.interval.a;
            points.put(new JSONObject().put("t",a).put("last",a).put("v",v).put("min",v).put("max",v).put("n",1).put("gap",lastEnd!=a).put("device","mobile-model").put("origin","estimated"));
            points.put(new JSONObject().put("t",b).put("last",b).put("v",v).put("min",v).put("max",v).put("n",1).put("gap",false).put("device","mobile-model").put("origin","estimated"));
            covered+=b-a;area+=v*(b-a);min=Math.min(min,v);max=Math.max(max,v);last=v;lastEnd=b;
        }
        JSONObject summary=new JSONObject().put("last",last==null?JSONObject.NULL:last).put("live",lastEnd==to&&now-to<=45000&&last!=null?last:JSONObject.NULL).put("liveAt",lastEnd)
            .put("min",covered==0?JSONObject.NULL:min).put("max",covered==0?JSONObject.NULL:max).put("avg",covered==0?JSONObject.NULL:area/covered).put("n",points.length()).put("coverage",to>from?covered/(double)(to-from):0).put("estimated",true).put("from",from).put("to",to);
        return new JSONObject().put("points",points).put("summary",summary);
    }
}
