package de.wattpit.app;

import org.json.*;
import java.time.*;
import java.util.*;
import de.wattpit.app.core.SolarEquipment;

/** Save prospective weather baselines with the display off; no learned correction or history scan. */
final class NativeForecast {
    static JSONArray rows(JSONObject equipment,JSONObject plan,JSONObject weather,String source)throws Exception{
        JSONArray result=new JSONArray(),groups=equipment.getJSONArray("groups"),windows=weather.optJSONArray("windows"),hours=weather.optJSONArray("hours"),days=weather.optJSONArray("days"),solar=weather.optJSONArray("solar");
        if(windows==null||hours==null||days==null||solar==null||weather.optLong("updated")<=0)return result;
        String key=SolarKeys.key(equipment,plan,weather.optJSONObject("place"),source,true,false),hardware=SolarKeys.key(equipment,plan,new JSONObject("{\"latitude\":0,\"longitude\":0}"),source,true,true);if(key.isEmpty())return result;
        Map<String,Map<Long,Double>> values=new HashMap<>();for(int i=0;i<solar.length();i++){JSONObject s=solar.getJSONObject(i);Map<Long,Double> map=new HashMap<>();JSONArray rows=s.getJSONArray("values");for(int j=0;j<rows.length();j++){JSONObject r=rows.getJSONObject(j);map.put(r.getLong("t"),r.optDouble("v",Double.NaN));}values.put(s.getString("geometry"),map);}
        List<JSONObject> measured=new ArrayList<>();for(int i=0;i<groups.length();i++){JSONObject g=groups.getJSONObject(i),p=plan.getJSONObject("groups").getJSONObject(g.getString("id"));if(g.optString("measurement").equals("solar")&&p.optBoolean("active")){if(SolarPlanning.geometry(g,p)==null)return result;measured.add(g);}}
        if(measured.isEmpty())return result;ZoneId zone;try{zone=ZoneId.of(weather.getString("timezone"));}catch(Exception e){return result;}
        Map<String,List<Long>> byDay=new HashMap<>();for(int i=0;i<hours.length();i++){long t=hours.getJSONObject(i).getLong("t");String d=Instant.ofEpochMilli(t-1800000).atZone(zone).toLocalDate().toString();byDay.computeIfAbsent(d,x->new ArrayList<>()).add(t);}
        Map<String,Integer> counts=new HashMap<>();for(int i=0;i<days.length();i++){String day=days.getJSONObject(i).getString("day");counts.put(day,counts.getOrDefault(day,0)+1);}
        long issued=weather.getLong("updated");String today=Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate().toString();int forecastDays=0;
        for(int i=0;i<days.length()&&forecastDays<14;i++){
            String day=days.getJSONObject(i).getString("day");if(day.compareTo(today)<0||counts.get(day)!=1)continue;forecastDays++;
            JSONObject[] bins=new JSONObject[3];for(int j=0;j<windows.length();j++){JSONObject w=windows.getJSONObject(j);int slot=w.optInt("slot",-1);if(day.equals(w.optString("day"))&&slot>=0&&slot<3)bins[slot]=w;}
            boolean valid=true;long expected=0;for(int j=0;j<3;j++){JSONObject b=bins[j];if(b==null||b.optLong("to")<=b.optLong("from")||j>0&&b.optLong("from")!=bins[j-1].optLong("to")){valid=false;break;}expected+=(b.getLong("to")-b.getLong("from"))/3600000;}
            List<Long> timeline=byDay.getOrDefault(day,Collections.emptyList());if(!valid||timeline.size()!=expected)continue;double[] wh={0,0,0};
            for(JSONObject g:measured){JSONObject p=plan.getJSONObject("groups").getJSONObject(g.getString("id"));Map<Long,Double> series=values.get(SolarPlanning.geometry(g,p));if(series==null){valid=false;break;}
                double factor=SolarEquipment.yieldFactor(g.optString("controller"),g.optString("controllerType","auto"),g.optString("yieldMode"),g.isNull("yieldFactor")?null:g.optDouble("yieldFactor"));
                for(long t:timeline){Double value=series.get(t);if(value==null||!Double.isFinite(value)||value<0){valid=false;break;}int h=Instant.ofEpochMilli(t-1800000).atZone(zone).getHour(),slot=h<11?0:h<16?1:2;JSONArray shades=p.getJSONArray("shade");int shade=shades.isNull(slot)?0:shades.getInt(slot);wh[slot]+=g.getDouble("count")*g.getDouble("watts")*value/1000*factor*new double[]{1,.8,.55,.25}[shade];}
            }
            if(valid)for(int slot=0;slot<3;slot++)if(bins[slot].getLong("from")>issued)result.put(new JSONObject().put("key",key).put("systemKey",hardware).put("from",bins[slot].getLong("from")).put("to",bins[slot].getLong("to")).put("issued",issued).put("wh",wh[slot]));
        }return result;
    }
    private NativeForecast(){}
}
