package de.wattpit.app;

import org.json.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Replays public Berlin API responses through the production network-response parsers. */
final class WeatherContractProof {
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);System.out.println("PASS "+text);}
    static void run(Path fixtures,Path output)throws Exception{
        JSONObject raw=new JSONObject(Files.readString(fixtures.resolve("berlin-2026-10-24-base.json"))),tilted=new JSONObject(Files.readString(fixtures.resolve("berlin-2026-10-24-tilted.json")));
        JSONObject weather=WeatherClient.timeline(raw).put("place",new JSONObject().put("name","Öffentlicher Testort Berlin").put("latitude",52.52).put("longitude",13.41)).put("updated",Instant.parse("2026-10-24T08:27:00Z").toEpochMilli())
            .put("solar",new JSONArray().put(WeatherClient.irradiance("35:0",tilted)));
        JSONArray days=weather.getJSONArray("days"),windows=weather.getJSONArray("windows"),hours=weather.getJSONArray("hours");Set<String> unique=new HashSet<>();ZoneId zone=ZoneId.of(weather.getString("timezone"));LocalDate previous=null;int index=-1;
        for(int i=0;i<days.length();i++){LocalDate date=LocalDate.parse(days.getJSONObject(i).getString("day"));check(previous==null||date.equals(previous.plusDays(1)),"provider day "+date+" remains consecutive across the offset change");unique.add(date.toString());previous=date;if(date.equals(LocalDate.of(2026,10,25)))index=i;}
        check(days.length()==22&&unique.size()==22&&index>=0,"all 22 recorded provider days remain unique, including Sunday 25 October");
        check(weather.getInt("version")==5&&weather.getInt("utcOffsetSeconds")==7200,"new cache retains the provider's fixed response offset");
        long from=windows.getJSONObject(index*3).getLong("from"),to=windows.getJSONObject(index*3+2).getLong("to");int count=0,two=0;
        for(int i=0;i<hours.length();i++){long t=hours.getJSONObject(i).getLong("t");if(t>from&&t<=to){count++;if(Instant.ofEpochMilli(t-1800000L).atZone(zone).getHour()==2)two++;}}
        check(to-from==25*3600000L&&count==25&&two==2,"real hourly timestamps retain both autumn 02:00 hours in one 25-hour Sunday");
        for(int i=index-1;i<index+13;i++){long a=windows.getJSONObject(i*3).getLong("from"),b=windows.getJSONObject(i*3+2).getLong("to");int found=0;JSONArray values=weather.getJSONArray("solar").getJSONObject(0).getJSONArray("values");for(int j=0;j<values.length();j++){JSONObject v=values.getJSONObject(j);if(v.getLong("t")>a&&v.getLong("t")<=b&&!v.isNull("v"))found++;}check(found==(b-a)/3600000L,"complete recorded irradiance remains available for "+days.getJSONObject(i).getString("day"));}
        // Keep failures explicit rather than accepting duplicate or skipped provider labels.
        JSONObject broken=new JSONObject(raw.toString());broken.getJSONObject("daily").getJSONArray("time").put(index+1,broken.getJSONObject("daily").getJSONArray("time").getLong(index));boolean rejected=false;
        try{WeatherClient.timeline(broken);}catch(Exception expected){rejected=true;}check(rejected,"duplicate provider days are rejected before entering the cache");
        Files.writeString(output,weather.toString());
    }
}
