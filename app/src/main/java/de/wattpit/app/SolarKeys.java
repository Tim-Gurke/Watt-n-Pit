package de.wattpit.app;

import de.wattpit.app.core.SolarEquipment;
import org.json.*;
import java.util.*;

/** The same declared-setup identifiers used by solar-core.js; available without a WebView. */
final class SolarKeys {
    static String key(JSONObject equipment,JSONObject plan,JSONObject place,String source,boolean solarOnly,boolean hardware)throws Exception{
        if(place==null||!Double.isFinite(place.optDouble("latitude",Double.NaN))||!Double.isFinite(place.optDouble("longitude",Double.NaN)))return "";
        List<JSONArray> groups=new ArrayList<>();JSONArray list=equipment.getJSONArray("groups");
        for(int i=0;i<list.length();i++){
            JSONObject g=list.getJSONObject(i);if((solarOnly||hardware)&&!g.optString("measurement").equals("solar"))continue;
            JSONObject p=plan.getJSONObject("groups").getJSONObject(g.getString("id"));
            Double own=g.isNull("yieldFactor")?null:g.getDouble("yieldFactor");String controller=g.optString("controller",""),type=SolarEquipment.controllerType(controller,g.optString("controllerType","auto")),mode=SolarEquipment.yieldMode(g.optString("yieldMode",null),own);
            String id=g.getString("id");if(hardware)id+="|"+controller+"|"+JSONObject.numberToString(SolarEquipment.yieldFactor(controller,type,mode,own));
            JSONArray row=new JSONArray().put(id).put(g.getInt("count")).put(g.getDouble("watts")).put(hardware?0:g.opt("tilt")).put(g.optString("measurement")).put(g.optString("placement"))
                .put(hardware||p.optBoolean("active")).put(hardware?180:p.opt("azimuth")).put(hardware?new JSONArray("[0,0,0]"):p.getJSONArray("shade"));
            if(own!=null)row.put(own).put(controller);
            if(type.equals("pwm")&&mode.equals("auto"))row.put("pwm-start-v1");groups.add(row);
        }
        java.text.Collator order=java.text.Collator.getInstance(Locale.US);groups.sort((a,b)->order.compare(a.optString(0),b.optString(0)));
        JSONArray sorted=new JSONArray();for(JSONArray row:groups)sorted.put(row);
        double lat=hardware?0:place.getDouble("latitude"),lon=hardware?0:place.getDouble("longitude");
        String text=new JSONArray().put(source).put(fixed(lat)).put(fixed(lon)).put(sorted).toString();
        int a=(int)2166136261L,b=(int)3339675911L;for(int i=0;i<text.length();i++){a=(a^text.charAt(i))*16777619;b=(b^text.charAt(i))*(int)2246822519L;}
        return String.format(Locale.ROOT,"k-%08x%08x",a,b);
    }
    private static String fixed(double value){String result=new java.math.BigDecimal(Math.abs(value)).setScale(3,java.math.RoundingMode.HALF_UP).toPlainString();return value<0?"-"+result:result;}
    private SolarKeys(){}
}
