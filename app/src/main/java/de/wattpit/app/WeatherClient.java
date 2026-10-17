package de.wattpit.app;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import de.wattpit.app.core.SunriseTime;
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
        if(input.optBoolean("off")){repository.prefs.edit().remove("weatherPlace").remove("weatherCache").remove("weatherError").apply();return;}
        JSONObject p=checkedPlace(input);
        repository.prefs.edit().putString("weatherPlace",p.toString()).remove("weatherCache").remove("weatherError").apply();refresh(repository,true);
    }
    static JSONObject checkedPlace(JSONObject input)throws Exception{
        double lat=input.getDouble("latitude"),lon=input.getDouble("longitude");String name=input.getString("name").trim();
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180||name.isEmpty()||name.length()>180)throw new IllegalArgumentException("Ungültiger Wetterort.");
        return new JSONObject().put("latitude",lat).put("longitude",lon).put("name",name);
    }
    static void refresh(Repository repository,boolean force){
        String place=repository.prefs.getString("weatherPlace","");if(place.isEmpty()||!BUSY.compareAndSet(false,true))return;
        NETWORK.execute(()->{
            try {
                JSONObject cached=new JSONObject(repository.prefs.getString("weatherCache","{}"));
                if(!force&&cached.optInt("version")==2&&System.currentTimeMillis()-cached.optLong("updated")<6*3600000L)return;
                JSONObject p=new JSONObject(place);
                JSONObject result=get("https://api.open-meteo.com/v1/forecast?latitude="+p.getDouble("latitude")+"&longitude="+p.getDouble("longitude")+"&daily=weather_code,sunshine_duration,sunrise&forecast_days=14&timezone=auto");
                JSONObject daily=result.getJSONObject("daily");JSONArray dates=daily.getJSONArray("time"),codes=daily.getJSONArray("weather_code"),sun=daily.getJSONArray("sunshine_duration"),days=new JSONArray();
                JSONArray rises=daily.optJSONArray("sunrise");
                for(int i=0;i<Math.min(14,dates.length());i++){
                    String date=dates.getString(i);LocalDate.parse(date);
                    Object hours=JSONObject.NULL;if(!sun.isNull(i)){double seconds=sun.getDouble(i);if(Double.isFinite(seconds)&&seconds>=0&&seconds<=86400)hours=seconds/3600;}
                    long sunrise=SunriseTime.parse(date,rises==null?"":rises.optString(i),result.optString("timezone"));
                    days.put(new JSONObject().put("day",date).put("code",codes.isNull(i)?JSONObject.NULL:codes.getInt(i)).put("hours",hours).put("sunrise",sunrise>0?sunrise:JSONObject.NULL));
                }
                if(days.length()==0)throw new IOException();
                JSONObject cache=new JSONObject().put("version",2).put("place",p).put("updated",System.currentTimeMillis()).put("days",days).put("timezone",result.optString("timezone"));
                if(place.equals(repository.prefs.getString("weatherPlace","")))repository.prefs.edit().putString("weatherCache",cache.toString()).remove("weatherError").apply();
            }catch(Exception e){if(place.equals(repository.prefs.getString("weatherPlace","")))repository.prefs.edit().putString("weatherError","Wetter gerade nicht erreichbar. Gespeicherte Vorschau bleibt verfügbar.").apply();}
            finally {BUSY.set(false);if(!place.equals(repository.prefs.getString("weatherPlace","")))refresh(repository,false);}
        });
    }
}
