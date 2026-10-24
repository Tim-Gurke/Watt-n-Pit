package de.wattpit.app;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import de.wattpit.app.core.ForecastTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in weather only. Energy data and device identifiers never enter requests. */
final class WeatherClient {
    private static final ExecutorService NETWORK=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    static JSONObject get(String url) throws Exception {
        URL target=new URL(url);
        if(!target.getProtocol().equals("https")||!(target.getHost().equals("api.open-meteo.com")||target.getHost().equals("geocoding-api.open-meteo.com")))throw new IOException();
        HttpURLConnection c=(HttpURLConnection)target.openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept","application/json");
        try {
            if(c.getResponseCode()!=200)throw new IOException();
            try(InputStream input=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1){out.write(b,0,n);if(out.size()>1024*1024)throw new IOException();}
                return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            }
        }finally{c.disconnect();}
    }
    static JSONArray search(String query) throws Exception {
        if(query.trim().length()<2||query.length()>120)throw new IllegalArgumentException("Bitte einen Ort mit mindestens zwei Zeichen eingeben.");
        JSONObject result=get("https://geocoding-api.open-meteo.com/v1/search?count=6&language=de&name="+URLEncoder.encode(query.trim(),"UTF-8"));
        JSONArray out=new JSONArray(),places=result.optJSONArray("results");
        if(places!=null)for(int i=0;i<places.length();i++){
            JSONObject p=places.getJSONObject(i);String name=p.getString("name");
            if(!p.optString("admin1").isEmpty()&&!p.optString("admin1").equals(name))name+=", "+p.getString("admin1");
            if(!p.optString("country").isEmpty())name+=", "+p.getString("country");
            out.put(new JSONObject().put("name",name).put("latitude",p.getDouble("latitude")).put("longitude",p.getDouble("longitude")));
        }
        return out;
    }
    static void place(Repository repository,JSONObject input) throws Exception {
        if(input.optBoolean("off")){repository.prefs.edit().remove("weatherPlace").remove("weatherCache").remove("weatherError").putString("solar-setup-v1",resetSite(repository).toString()).apply();return;}
        JSONObject p=checkedPlace(input);
        android.content.SharedPreferences.Editor edit=repository.prefs.edit().putString("weatherPlace",p.toString()).remove("weatherCache").remove("weatherError");
        JSONObject old=new JSONObject(repository.prefs.getString("weatherPlace","{}"));
        if(Math.abs(old.optDouble("latitude",Double.NaN)-p.getDouble("latitude"))>.001||Math.abs(old.optDouble("longitude",Double.NaN)-p.getDouble("longitude"))>.001||old.length()==0)edit.putString("solar-setup-v1",resetSite(repository).toString());
        edit.apply();refresh(repository,true);
    }
    private static JSONObject resetSite(Repository repository)throws Exception{
        JSONObject equipment=repository.equipment(),plan=SolarPlanning.checked(new JSONObject(repository.prefs.getString("solar-setup-v1","{}")),equipment);JSONArray groups=equipment.getJSONArray("groups");
        for(int i=0;i<groups.length();i++){JSONObject g=groups.getJSONObject(i),p=plan.getJSONObject("groups").getJSONObject(g.getString("id"));p.put("azimuth",JSONObject.NULL).put("shade",new JSONArray("[null,null,null]"));if(g.optString("placement").equals("mobile"))p.put("active",false);}return plan;
    }
    static JSONObject checkedPlace(JSONObject input)throws Exception{
        double lat=input.getDouble("latitude"),lon=input.getDouble("longitude");String name=input.getString("name").trim();
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180||name.isEmpty()||name.length()>180)throw new IllegalArgumentException("Ungültiger Wetterort.");
        return new JSONObject().put("latitude",lat).put("longitude",lon).put("name",name);
    }
    static void refresh(Repository repository,boolean force){
        String place=repository.prefs.getString("weatherPlace","");if(place.isEmpty()||!BUSY.compareAndSet(false,true))return;
        String equipment=repository.prefs.getString("equipment-v1","{\"version\":1,\"groups\":[]}"),setup=repository.prefs.getString("solar-setup-v1","{}");
        String signature=equipment+"|"+setup;
        NETWORK.execute(()->{
            try {
                JSONObject cached=new JSONObject(repository.prefs.getString("weatherCache","{}"));
                if(!force&&cached.optInt("version")==5&&signature.equals(cached.optString("signature"))&&System.currentTimeMillis()-cached.optLong("updated")<6*3600000L)return;
                JSONObject p=new JSONObject(place);long updated=System.currentTimeMillis();
                String base="https://api.open-meteo.com/v1/forecast?latitude="+p.getDouble("latitude")+"&longitude="+p.getDouble("longitude")+"&forecast_days=15&past_days=7&timeformat=unixtime&timezone=auto";
                JSONObject result=get(base+"&daily=weather_code,sunshine_duration,sunrise,sunset,shortwave_radiation_sum&hourly=shortwave_radiation,sunshine_duration");
                JSONObject cache=timeline(result);JSONArray series=new JSONArray();
                JSONObject eq=new JSONObject(equipment),plan=SolarPlanning.checked(new JSONObject(setup),eq);JSONArray groups=eq.getJSONArray("groups");java.util.Set<String> requested=new java.util.LinkedHashSet<>();boolean partial=false;
                for(int i=0;i<groups.length();i++){
                    JSONObject g=groups.getJSONObject(i),setting=plan.getJSONObject("groups").getJSONObject(g.getString("id"));
                    if(!setting.optBoolean("active"))continue;String geometry=SolarPlanning.geometry(g,setting);if(geometry==null||!requested.add(geometry))continue;
                    if(!place.equals(repository.prefs.getString("weatherPlace",""))||!setup.equals(repository.prefs.getString("solar-setup-v1","{}")))break;
                    try{
                        String[] angles=geometry.split(":");series.put(irradiance(geometry,get(base+"&hourly=global_tilted_irradiance&tilt="+angles[0]+"&azimuth="+angles[1])));
                    }catch(Exception e){partial=true;}
                }
                cache.put("signature",signature).put("place",p).put("updated",updated).put("solar",series).put("partial",partial);
                if(place.equals(repository.prefs.getString("weatherPlace",""))&&equipment.equals(repository.prefs.getString("equipment-v1","{\"version\":1,\"groups\":[]}"))&&setup.equals(repository.prefs.getString("solar-setup-v1","{}")))repository.prefs.edit().putString("weatherCache",cache.toString()).remove("weatherError").apply();
            }catch(Exception e){if(place.equals(repository.prefs.getString("weatherPlace","")))repository.prefs.edit().putString("weatherError","Solarvorschau gerade nicht erreichbar. Gespeicherte Daten bleiben verfügbar.").apply();}
            finally {BUSY.set(false);if(!place.equals(repository.prefs.getString("weatherPlace",""))||!equipment.equals(repository.prefs.getString("equipment-v1","{\"version\":1,\"groups\":[]}"))||!setup.equals(repository.prefs.getString("solar-setup-v1","{}")))refresh(repository,false);}
        });
    }
    static JSONObject irradiance(String geometry,JSONObject result)throws Exception{
        JSONObject tilted=result.getJSONObject("hourly");JSONArray t=tilted.getJSONArray("time"),v=tilted.getJSONArray("global_tilted_irradiance"),values=new JSONArray();
        for(int j=0;j<t.length();j++)values.put(new JSONObject().put("t",t.getLong(j)*1000L).put("v",finiteAt(v,j,1)));
        return new JSONObject().put("geometry",geometry).put("values",values);
    }
    /** Same parser for live responses and recorded public API contract checks. */
    static JSONObject timeline(JSONObject result)throws Exception{
        JSONObject daily=result.getJSONObject("daily"),hourly=result.getJSONObject("hourly");
        java.time.ZoneId zone=java.time.ZoneId.of(result.getString("timezone"));int offset=result.getInt("utc_offset_seconds");
        JSONArray dates=daily.getJSONArray("time"),codes=daily.getJSONArray("weather_code"),sun=daily.getJSONArray("sunshine_duration"),days=new JSONArray(),windows=new JSONArray(),hours=new JSONArray();
        LocalDate previous=null;
        for(int i=0;i<dates.length();i++){
            LocalDate date=ForecastTime.providerDate(dates.getLong(i),offset);
            if(previous!=null&&!date.equals(previous.plusDays(1)))throw new IOException("Wettertage sind nicht aufeinanderfolgend.");previous=date;
            long rise=daily.getJSONArray("sunrise").optLong(i)*1000L,set=daily.getJSONArray("sunset").optLong(i)*1000L;
            days.put(new JSONObject().put("day",date.toString()).put("code",codes.isNull(i)?JSONObject.NULL:codes.getInt(i)).put("hours",finiteAt(sun,i,3600)).put("radiation",finiteAt(daily.optJSONArray("shortwave_radiation_sum"),i,.0036)).put("sunrise",rise>0?rise:JSONObject.NULL).put("sunset",set>rise?set:JSONObject.NULL));
            long[] bounds=ForecastTime.boundaries(date,zone);for(int slot=0;slot<3;slot++)windows.put(new JSONObject().put("day",date.toString()).put("slot",slot).put("from",bounds[slot]).put("to",bounds[slot+1]));
        }
        if(days.length()==0)throw new IOException();
        JSONArray times=hourly.getJSONArray("time");for(int i=0;i<times.length();i++)hours.put(new JSONObject().put("t",times.getLong(i)*1000L).put("ghi",finiteAt(hourly.optJSONArray("shortwave_radiation"),i,1)).put("sun",finiteAt(hourly.optJSONArray("sunshine_duration"),i,1)));
        return new JSONObject().put("version",5).put("timezone",zone.getId()).put("utcOffsetSeconds",offset).put("days",days).put("windows",windows).put("hours",hours);
    }
    private static Object finiteAt(JSONArray values,int index,double divisor){
        if(values==null||values.isNull(index))return JSONObject.NULL;double value=values.optDouble(index,Double.NaN);return Double.isFinite(value)&&value>=0?value/divisor:JSONObject.NULL;
    }
}
