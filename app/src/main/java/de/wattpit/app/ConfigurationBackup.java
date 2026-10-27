package de.wattpit.app;

import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Portable preferences only. Never reads or writes device identities, keys or measurements. */
final class ConfigurationBackup {
    static final int MAX_BYTES=256000;
    static JSONObject normalize(String json) throws Exception {
        if(json.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Die Einstellungsdatei darf höchstens 256 KB groß sein.");
        JSONObject input=new JSONObject(json);
        if(!input.optString("format").equals("wattnpit-settings")||input.optInt("version")!=1)throw new IllegalArgumentException("Keine unterstützte Watt’n’Pit-Einstellungssicherung.");
        JSONObject s=input.getJSONObject("settings");
        String theme=s.getString("theme"),palette=s.getString("palette"),miniRange=s.getString("miniRange");
        double capacity=s.getDouble("capacity"),seconds=s.getDouble("sampleSeconds");
        if(!Arrays.asList("light","dark").contains(theme)||!Arrays.asList("classic","blue","green","turquoise","orange","neutral").contains(palette)||!Arrays.asList("sunrise","today","last6h","last24h","yesterday","week","month").contains(miniRange)||!Double.isFinite(capacity)||capacity<1||capacity>10000||!Arrays.asList(5.0,10.0,30.0,60.0).contains(seconds))throw new IllegalArgumentException("Die allgemeinen Einstellungen sind ungültig.");
        JSONObject settings=new JSONObject().put("theme",theme).put("palette",palette).put("miniRange",miniRange).put("capacity",capacity).put("sampleSeconds",(int)seconds);
        JSONObject board=input.getJSONObject("dashboard");JSONArray rows=board.getJSONArray("tiles"),tiles=new JSONArray();
        if(board.optInt("version")!=1||rows.length()>64||board.toString().length()>128000)throw new IllegalArgumentException("Die gesicherte Übersicht ist ungültig.");
        Set<String> ids=new HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String id=row.getString("id"),metric=row.getString("metric");
            if(!id.matches("[a-zA-Z0-9_-]{1,90}")||!ids.add(id)||!metric.matches("[a-zA-Z][a-zA-Z0-9]{0,40}"))throw new IllegalArgumentException("Ungültige Kachel in der Sicherung.");
            JSONObject tile=pick(row,"id","metric","title","unit","type","digits","fontSize","color","colorMid","colorEnd","adaptiveColor","multicolor","invert","min","max","scaleMode","valueMode","reverseSign","background","opacity");
            tile.put("fontSize",Math.max(18,Math.min(20,row.optDouble("fontSize",20))));
            if(row.has("period"))tile.put("period",pick(row.getJSONObject("period"),"mode","amount","unit","aggregate","resetAt","from","to"));
            if(row.has("layouts")){
                JSONObject layouts=row.getJSONObject("layouts"),clean=new JSONObject();
                for(String profile:new String[]{"compact","wide"})if(layouts.has(profile))clean.put(profile,pick(layouts.getJSONObject(profile),"x","y","w","h"));
                tile.put("layouts",clean);
            }
            tiles.put(tile);
        }
        JSONArray order=board.getJSONArray("blockOrder");Set<String> blocks=new HashSet<>();
        if(order.length()!=3&&order.length()!=4&&order.length()!=5)throw new IllegalArgumentException("Ungültige Blockreihenfolge.");
        for(int i=0;i<order.length();i++)if(!Arrays.asList("tiles","weather","history","record","roller").contains(order.getString(i))||!blocks.add(order.getString(i)))throw new IllegalArgumentException("Ungültige Blockreihenfolge.");
        if(!blocks.containsAll(Arrays.asList("tiles","history","record")))throw new IllegalArgumentException("Ungültige Blockreihenfolge.");
        JSONObject dashboard=new JSONObject().put("version",1).put("blockOrder",order).put("tiles",tiles);
        if(board.optInt("lanTileVersion")==1)dashboard.put("lanTileVersion",1);
        if(board.optInt("dischargeSignVersion")==1)dashboard.put("dischargeSignVersion",1);
        if(board.optInt("energyFlowVersion")==1)dashboard.put("energyFlowVersion",1);
        if(board.has("energyPeriod"))dashboard.put("energyPeriod",pick(board.getJSONObject("energyPeriod"),"mode","amount","unit","aggregate","resetAt","from","to"));
        if(board.has("rollerLayout")){
            JSONObject given=board.getJSONObject("rollerLayout"),clean=new JSONObject();
            for(String profile:new String[]{"compact","wide"}){JSONObject row=given.optJSONObject(profile);double width=row==null?12:row.optDouble("width",12),height=row==null?650:row.optDouble("height",650);if(!Double.isFinite(width)||!Double.isFinite(height))throw new IllegalArgumentException("Ungültige Rollergröße");clean.put(profile,new JSONObject().put("width",Math.round(Math.max(4,Math.min(12,width))*2)/2.0).put("height",Math.round(Math.max(250,Math.min(1600,height)))));}
            dashboard.put("rollerLayout",clean);
        }
        if(board.has("historyHeight")){
            JSONObject heights=board.getJSONObject("historyHeight"),clean=new JSONObject();
            for(String profile:new String[]{"compact","wide"}){
                double value=heights.optDouble(profile,Double.NaN);Object raw=heights.opt(profile);
                clean.put(profile,raw!=null&&raw!=JSONObject.NULL&&!raw.equals("")&&Double.isFinite(value)?Math.round(Math.max(300,Math.min(1000,value))):profile.equals("wide")?380:320);
            }
            dashboard.put("historyHeight",clean);
        }
        JSONObject place=input.optJSONObject("weatherPlace");if(place==null)place=new JSONObject();
        if(place.length()>0)place=WeatherClient.checkedPlace(place);
        JSONObject result=new JSONObject().put("format","wattnpit-settings").put("version",1).put("settings",settings).put("dashboard",dashboard).put("equipment",Repository.checkedEquipment(input.getJSONObject("equipment"))).put("weatherPlace",place);
        JSONObject solar=SolarPlanning.checked(input.optJSONObject("solarSetup")==null?new JSONObject():input.getJSONObject("solarSetup"),result.getJSONObject("equipment"));
        JSONArray circles=result.getJSONObject("equipment").getJSONArray("groups");for(int i=0;i<circles.length();i++){JSONObject g=circles.getJSONObject(i);if(g.optString("placement").equals("mobile"))solar.getJSONObject("groups").getJSONObject(g.getString("id")).put("active",false);}
        result.put("solarSetup",solar);
        if(input.has("createdAt"))result.put("createdAt",input.optString("createdAt").substring(0,Math.min(40,input.optString("createdAt").length())));
        return result;
    }
    private static JSONObject pick(JSONObject source,String...keys)throws JSONException{
        JSONObject result=new JSONObject();for(String key:keys)if(source.has(key))result.put(key,source.get(key));return result;
    }
    private ConfigurationBackup(){}
}
