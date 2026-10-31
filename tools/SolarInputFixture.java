package de.wattpit.app;

import org.json.*;
import java.nio.file.*;
import java.time.*;

/** Inputs and native outputs for an independent comparison with the shipped JS engine. */
public final class SolarInputFixture {
    public static void main(String[] args)throws Exception{
        JSONObject equipment=new JSONObject("{\"version\":1,\"groups\":[{\"id\":\"g-1\",\"count\":2,\"watts\":100,\"tilt\":30,\"measurement\":\"solar\",\"placement\":\"fixed\",\"controller\":\"Victron / MPPT\",\"yieldMode\":\"auto\"},{\"id\":\"g-2\",\"count\":1,\"watts\":120,\"tilt\":30,\"measurement\":\"shunt\",\"placement\":\"mobile\",\"controller\":\"PWM\",\"controllerType\":\"pwm\",\"yieldMode\":\"auto\"}]}");
        JSONObject plan=SolarPlanning.checked(new JSONObject("{\"groups\":{\"g-1\":{\"active\":true,\"azimuth\":180,\"shade\":[0,1,null]},\"g-2\":{\"active\":true,\"azimuth\":180,\"shade\":[0,0,0]}}}"),equipment);
        JSONArray tests=new JSONArray();double[][] places={{52.5,13.4},{1.2345,-1.2345},{-0.00049,-0.0},{-0.0005,0.0005},{89.9999,-179.9999}};
        for(double[] xy:places){JSONObject place=new JSONObject().put("latitude",xy[0]).put("longitude",xy[1]);tests.put(new JSONObject().put("equipment",equipment).put("plan",plan).put("place",place).put("key",SolarKeys.key(equipment,plan,place,"source",false,false)).put("solar",SolarKeys.key(equipment,plan,place,"source",true,false)).put("hardware",SolarKeys.key(equipment,plan,place,"source",true,true)));}
        for(String id:new String[]{"a_A","A-a","z-9","g_1","g-0","Ä-id"}){JSONObject eq=new JSONObject(equipment.toString());eq.getJSONArray("groups").getJSONObject(1).put("id",id).put("yieldFactor",.65);JSONObject p=new JSONObject(plan.toString());p.getJSONObject("groups").put(id,p.getJSONObject("groups").remove("g-2"));JSONObject place=new JSONObject("{\"latitude\":0,\"longitude\":0}");tests.put(new JSONObject().put("equipment",eq).put("plan",p).put("place",place).put("key",SolarKeys.key(eq,p,place,"source",false,false)).put("solar",SolarKeys.key(eq,p,place,"source",true,false)).put("hardware",SolarKeys.key(eq,p,place,"source",true,true)));}
        long now=System.currentTimeMillis();ZoneId zone=ZoneId.of("Europe/Berlin");LocalDate tomorrow=Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1);JSONArray days=new JSONArray(),hours=new JSONArray(),windows=new JSONArray(),values=new JSONArray();
        for(int i=0;i<3;i++){LocalDate d=tomorrow.plusDays(i);long start=d.atStartOfDay(zone).toInstant().toEpochMilli(),end=d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();days.put(new JSONObject().put("day",d.toString()).put("sunrise",start+7*3600000).put("sunset",start+18*3600000).put("hours",6).put("radiation",10));long[] b={start,d.atTime(11,0).atZone(zone).toInstant().toEpochMilli(),d.atTime(16,0).atZone(zone).toInstant().toEpochMilli(),end};for(int j=0;j<3;j++)windows.put(new JSONObject().put("day",d.toString()).put("slot",j).put("from",b[j]).put("to",b[j+1]));for(long t=start+3600000;t<=end;t+=3600000){hours.put(new JSONObject().put("t",t));values.put(new JSONObject().put("t",t).put("v",t>start+7*3600000&&t<=start+18*3600000?500:0));}}
        JSONObject weather=new JSONObject().put("updated",now).put("timezone",zone.getId()).put("place",new JSONObject("{\"latitude\":52.5,\"longitude\":13.4}")).put("days",days).put("hours",hours).put("windows",windows).put("solar",new JSONArray().put(new JSONObject().put("geometry","30:0").put("values",values)));
        Files.writeString(Path.of(args[0]),new JSONObject().put("keys",tests).put("equipment",equipment).put("plan",plan).put("weather",weather).put("nativeRows",NativeForecast.rows(equipment,plan,weather,"source")).toString());
    }
}
