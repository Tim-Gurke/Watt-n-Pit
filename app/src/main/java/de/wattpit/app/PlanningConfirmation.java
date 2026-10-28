package de.wattpit.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Bounded user confirmations are separate from observations recorded at the time. */
final class PlanningConfirmation {
    private static final long HOUR=3600000L;
    // An opaque match to the already stored context, not an address, location or setup.
    // The owner explicitly confirmed 20–26 October 2026 before authorizing this update.
    private static final String APPROVED="42084f788e8200e0614f676028fc40a429d8d32684997460b54a8562500885fa";
    static void create(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS confirmed_setups(source TEXT NOT NULL,id TEXT NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,key TEXT NOT NULL,solar_key TEXT NOT NULL,payload TEXT NOT NULL,weather TEXT NOT NULL DEFAULT '{}',PRIMARY KEY(source,id))");}
    static String fingerprint(String source,long at,String key)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest((source+"|"+at+"|"+key).getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:hash)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
    static boolean installApproved(SQLiteDatabase db,String source)throws Exception{
        ZoneId zone=ZoneId.of("Europe/Berlin");
        return install(db,source,"owner-confirmation-20261028",APPROVED,LocalDate.of(2026,10,20).atStartOfDay(zone).toInstant().toEpochMilli(),LocalDate.of(2026,10,27).atStartOfDay(zone).toInstant().toEpochMilli());
    }
    static boolean install(SQLiteDatabase db,String source,String id,String selector,long from,long to)throws Exception{
        if(to<=from||to-from>93L*24*HOUR)throw new IllegalArgumentException("Ungültiger Bestätigungszeitraum.");
        try(Cursor c=db.rawQuery("SELECT 1 FROM confirmed_setups WHERE source=? AND id=?",new String[]{source,id})){if(c.moveToFirst())return false;}
        try(Cursor c=db.rawQuery("SELECT at,key,solar_key,payload FROM contexts WHERE source=? ORDER BY at",new String[]{source})){
            while(c.moveToNext()){
                if(!selector.equals(fingerprint(source,c.getLong(0),c.getString(1))))continue;
                JSONObject payload=new JSONObject(c.getString(3));if(!payload.has("equipment")||!payload.has("setup")||!payload.has("place"))return false;
                ContentValues v=new ContentValues();v.put("source",source);v.put("id",id);v.put("start",from);v.put("end",to);v.put("key",c.getString(1));v.put("solar_key",c.getString(2));v.put("payload",payload.toString());
                return db.insertWithOnConflict("confirmed_setups",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
            }
        }return false;
    }
    static final class Entry {
        final String id,key,solarKey;final long from,to;final JSONObject payload;JSONObject weather;
        Entry(Cursor c)throws Exception{id=c.getString(0);from=c.getLong(1);to=c.getLong(2);key=c.getString(3);solarKey=c.getString(4);payload=new JSONObject(c.getString(5));weather=new JSONObject(c.getString(6));}
        boolean contains(long a,long b){return a>=from&&b<=to;}
        JSONObject notice()throws Exception{
            ZoneId zone=ZoneId.of(weather.optString("timezone","Europe/Berlin"));
            return new JSONObject().put("from",from).put("to",to).put("fromDay",Instant.ofEpochMilli(from).atZone(zone).toLocalDate().toString()).put("throughDay",Instant.ofEpochMilli(to-1).atZone(zone).toLocalDate().toString()).put("weatherComplete",weather.optBoolean("complete"));
        }
    }
    static final class Result {final List<Entry> entries=new ArrayList<>();long changedFrom=Long.MAX_VALUE,weatherSince;}
    static Result read(SQLiteDatabase db,String source,JSONObject fresh)throws Exception{
        Result result=new Result();
        try(Cursor c=db.rawQuery("SELECT id,start,end,key,solar_key,payload,weather FROM confirmed_setups WHERE source=? ORDER BY start",new String[]{source})){
            while(c.moveToNext())result.entries.add(new Entry(c));
        }
        for(Entry e:result.entries){
            if(!e.weather.optBoolean("complete")&&samePlace(e.payload.optJSONObject("place"),fresh.optJSONObject("place"))){
                JSONObject merged=merge(e,fresh);if(!merged.toString().equals(e.weather.toString())){
                    e.weather=merged;ContentValues v=new ContentValues();v.put("weather",merged.toString());db.update("confirmed_setups",v,"source=? AND id=?",new String[]{source,e.id});result.changedFrom=Math.min(result.changedFrom,e.from);
                }
            }
            if(!e.weather.optBoolean("complete"))result.weatherSince=result.weatherSince==0?e.from:Math.min(result.weatherSince,e.from);
        }return result;
    }
    private static boolean samePlace(JSONObject a,JSONObject b){
        if(a==null||b==null)return false;for(String k:new String[]{"latitude","longitude"}){double x=a.optDouble(k,Double.NaN),y=b.optDouble(k,Double.NaN);if(!Double.isFinite(x)||!Double.isFinite(y)||Math.round(x*1000)!=Math.round(y*1000))return false;}return true;
    }
    private static JSONArray mergeTimes(JSONArray old,JSONArray fresh,long from,long to)throws Exception{
        TreeMap<Long,JSONObject> times=new TreeMap<>();
        for(JSONArray list:new JSONArray[]{old,fresh})if(list!=null)for(int i=0;i<list.length();i++){
            JSONObject p=list.getJSONObject(i);long t=p.optLong("t");if(t<=from||t>to)continue;JSONObject previous=times.get(t);
            if(previous==null||previous.isNull("v")&&!p.isNull("v"))times.put(t,p);
        }
        JSONArray out=new JSONArray();for(JSONObject p:times.values())out.put(p);return out;
    }
    private static JSONObject merge(Entry e,JSONObject fresh)throws Exception{
        JSONObject out=new JSONObject(e.weather.toString()).put("version",5).put("timezone",fresh.optString("timezone","Europe/Berlin")).put("place",e.payload.getJSONObject("place"));
        ZoneId zone=ZoneId.of(out.getString("timezone"));String first=Instant.ofEpochMilli(e.from).atZone(zone).toLocalDate().toString(),last=Instant.ofEpochMilli(e.to-1).atZone(zone).toLocalDate().toString();
        TreeMap<String,JSONObject> days=new TreeMap<>();
        for(JSONArray list:new JSONArray[]{e.weather.optJSONArray("days"),fresh.optJSONArray("days")})if(list!=null)for(int i=0;i<list.length();i++){
            JSONObject d=list.getJSONObject(i);String day=d.optString("day");if(day.compareTo(first)<0||day.compareTo(last)>0)continue;
            JSONObject previous=days.get(day);if(previous==null||previous.isNull("sunrise")||previous.isNull("sunset"))days.put(day,d);
        }
        JSONArray dated=new JSONArray();for(JSONObject d:days.values())dated.put(d);out.put("days",dated);
        TreeMap<String,JSONArray> series=new TreeMap<>();
        for(JSONArray list:new JSONArray[]{e.weather.optJSONArray("solar"),fresh.optJSONArray("solar")})if(list!=null)for(int i=0;i<list.length();i++){
            JSONObject s=list.getJSONObject(i);String geometry=s.optString("geometry");series.put(geometry,mergeTimes(series.get(geometry),s.optJSONArray("values"),e.from,e.to));
        }
        JSONArray solar=new JSONArray();for(Map.Entry<String,JSONArray> s:series.entrySet())solar.put(new JSONObject().put("geometry",s.getKey()).put("values",s.getValue()));out.put("solar",solar);
        boolean complete=true;for(LocalDate day=LocalDate.parse(first);!day.isAfter(LocalDate.parse(last));day=day.plusDays(1)){
            JSONObject d=days.get(day.toString());if(d==null||d.optLong("sunrise")<=0||d.optLong("sunset")<=d.optLong("sunrise"))complete=false;
        }
        JSONArray groups=e.payload.getJSONObject("equipment").getJSONArray("groups");JSONObject setup=e.payload.getJSONObject("setup").getJSONObject("groups");
        for(int i=0;i<groups.length();i++){
            JSONObject g=groups.getJSONObject(i),p=setup.getJSONObject(g.getString("id"));if(!p.optBoolean("active")||g.optString("measurement").equals("solar"))continue;
            String geometry=SolarPlanning.geometry(g,p);JSONArray values=geometry==null?null:series.get(geometry);Set<Long> times=new HashSet<>();
            if(values!=null)for(int j=0;j<values.length();j++){JSONObject v=values.getJSONObject(j);double n=v.optDouble("v",Double.NaN);if(Double.isFinite(n)&&n>=0)times.add(v.getLong("t"));}
            for(long t=e.from+HOUR;t<=e.to;t+=HOUR)if(!times.contains(t))complete=false;
        }
        return out.put("complete",complete);
    }
    private PlanningConfirmation(){}
}
