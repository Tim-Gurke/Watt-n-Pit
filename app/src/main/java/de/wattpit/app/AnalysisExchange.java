package de.wattpit.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Typed calculation inputs only. The central phone never builds a plot for this protocol. */
final class AnalysisExchange {
    static final String[] TABLES={"contexts","display_contexts","model_weather","observations","forecasts","confirmed_setups"};
    static final String[] ALL={"contexts","display_contexts","model_weather","observations","forecasts","confirmed_setups","device_settings","input_intervals","archive_clock_state"};
    static final int PAGE=256;
    static String schema(String table){switch(table){
        case "contexts":return "at:l,key:s,mobile_free:l,source:s,solar_key:s,payload:s";
        case "display_contexts":return "source:s,at:l,payload:s";
        case "model_weather":return "place:s,geometry:s,end:l,value:d,slot:l,night:l,sunrise:l,sunset:l";
        case "observations":return "start:l,end:l,source:s,payload:s";
        case "forecasts":return "key:s,start:l,end:l,issued:l,payload:s";
        case "confirmed_setups":return "source:s,id:s,start:l,end:l,key:s,solar_key:s,payload:s,weather:s";
        case "device_settings":return "device:s,start:l,end:l,payload:s";
        case "input_intervals":return "source:s,start:l,end:l,wh:d,mac:s,session:l";
        case "archive_clock_state":return "device:s,payload:s";
        default:throw new IllegalArgumentException("Unbekannte Datentabelle.");
    }}
    static boolean incremental(String table){return table.equals("observations")||table.equals("forecasts");}
    static JSONObject state(Repository repo,SolarPlanning plan,long controlRevision)throws Exception{
        StringBuilder text=new StringBuilder();for(String key:new String[]{"weatherCache","devices","equipment-v1","solar-setup-v1","weatherPlace","rollerChargeWindows"})text.append(repo.prefs.getString(key,""));
        String token=java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return new JSONObject().put("token",token+":"+plan.modelRevision()+":"+controlRevision);
    }
    static SQLiteDatabase database(String table,Repository repo,SolarPlanning plan,RollerLedger ledger,boolean write){
        // schema() is also the table-name whitelist, before any SQL is constructed.
        schema(table);
        if(table.equals("input_intervals"))return write?ledger.getWritableDatabase():ledger.getReadableDatabase();
        if(table.equals("device_settings")||table.equals("archive_clock_state"))return write?repo.db.getWritableDatabase():repo.db.getReadableDatabase();
        return write?plan.getWritableDatabase():plan.getReadableDatabase();
    }
    static String where(String table,long from,long to){
        long since=LearningWindow.since(System.currentTimeMillis())-10L*86400000;
        if(table.equals("model_weather"))return "end>"+from+" AND end<="+(to+3600000);
        if(table.equals("input_intervals"))return "end>"+from+" AND start<"+to;
        if(table.equals("device_settings"))return "end>="+Math.max(0,from-de.wattpit.app.core.VictronSettings.MAX_AGE)+" AND start<="+to;
        if(table.equals("observations")||table.equals("forecasts")||table.equals("confirmed_setups"))return "end>="+since;
        return "1=1";
    }
    static String version(String table,Repository repo,SolarPlanning plan,RollerLedger ledger,long from,long to){
        if(Arrays.asList(TABLES).contains(table))try(Cursor c=plan.getReadableDatabase().rawQuery("SELECT revision FROM analysis_model_tables WHERE name=?",new String[]{table})){return c.moveToFirst()?c.getString(0):"0";}
        SQLiteDatabase db=database(table,repo,plan,ledger,false);
        try(Cursor c=db.rawQuery("SELECT COALESCE(MAX(rowid),0),COUNT(*) FROM "+table+" WHERE "+where(table,from,to),null)){return c.moveToFirst()?c.getLong(0)+":"+c.getLong(1):"0:0";}
    }
    static JSONObject metadata(Context context,Repository repo,SolarPlanning plan,long from,long to,long controlRevision)throws Exception{
        if(from<0||to<=from||to-from>376L*86400000||to>System.currentTimeMillis()+86400000)throw new IllegalArgumentException("Ungültiger Datenzeitraum.");
        plan.captureNative();plan.flushInputs();JSONObject before=state(repo,plan,controlRevision);RollerLedger ledger=RollerLedger.get(context);JSONObject versions=new JSONObject(),settings=new JSONObject();
        for(String table:ALL)versions.put(table,version(table,repo,plan,ledger,from,to));
        for(String key:new String[]{"equipment-v1","solar-setup-v1","weatherPlace","weatherCache","rollerChargeWindows"})settings.put(key,repo.prefs.getString(key,key.equals("rollerChargeWindows")?"[]":key.equals("equipment-v1")?"{\"version\":1,\"groups\":[]}":"{}"));
        JSONArray devices=new JSONArray(),configs=repo.configs();for(int i=0;i<configs.length();i++){JSONObject d=configs.getJSONObject(i);devices.put(new JSONObject().put("mac",d.optString("mac")).put("kind",d.optString("kind")).put("name",d.optString("name")));}
        settings.put("devices",devices.toString()).put("capacity",repo.prefs.getFloat("capacity",100)).put("sampleSeconds",repo.prefs.getInt("sampleSeconds",10));
        // Seed the existing learning checkpoint; a viewer then advances its own derived ledger.
        JSONObject markers=new JSONObject();for(Map.Entry<String,?> e:repo.prefs.getAll().entrySet())if(e.getKey().startsWith("planningEnergyVersion.")||e.getKey().startsWith("planningArchiveTimeRevision.")||e.getKey().startsWith("planningSpecialUsage.")||e.getKey().startsWith("planningRollerInputEnd."))markers.put(e.getKey(),e.getValue());
        long first=0,last=0;try(Cursor c=repo.db.getReadableDatabase().rawQuery("SELECT MIN(ts) FROM readings WHERE ts>=0",null)){if(c.moveToFirst()&&!c.isNull(0))first=c.getLong(0);}
        try(Cursor c=repo.db.getReadableDatabase().rawQuery("SELECT MAX(ts) FROM readings WHERE ts>=0 AND ts<=?",new String[]{""+System.currentTimeMillis()})){if(c.moveToFirst()&&!c.isNull(0))last=c.getLong(0);}
        return new JSONObject().put("protocol",1).put("algorithm",SolarPlanning.ALGORITHM).put("inputToken",before.getString("token")).put("changed",!before.getString("token").equals(state(repo,plan,controlRevision).getString("token"))).put("firstMeasurement",first).put("measurementAt",Math.min(last,System.currentTimeMillis())).put("settings",settings).put("markers",markers).put("tables",versions)
            .put("presenceHistory",PresenceStore.get(context).history(from)).put("presence",PresenceStore.get(context).snapshot()).put("controlRevision",controlRevision)
            .put("timezone",TimeZone.getDefault().getID()).put("at",System.currentTimeMillis());
    }
    static JSONObject page(Context context,Repository repo,SolarPlanning plan,String table,long from,long to,String expected,long after)throws Exception{
        if(from<0||to<=from||to-from>376L*86400000||to>System.currentTimeMillis()+86400000||after<0)throw new IllegalArgumentException("Ungültiger Datenzeitraum.");
        RollerLedger ledger=RollerLedger.get(context);SQLiteDatabase db=database(table,repo,plan,ledger,false);String before=version(table,repo,plan,ledger,from,to);
        if(!before.equals(expected))return new JSONObject().put("changed",true);
        String[] fields=schema(table).split(",");List<String> columns=new ArrayList<>();for(String f:fields)columns.add(f.split(":")[0]);JSONArray rows=new JSONArray();long cursor=after;
        try(Cursor c=db.rawQuery("SELECT rowid,"+String.join(",",columns)+" FROM "+table+" WHERE "+where(table,from,to)+" AND rowid>? ORDER BY rowid LIMIT "+PAGE,new String[]{""+after})){
            while(c.moveToNext()){cursor=c.getLong(0);JSONArray row=new JSONArray();for(int i=0;i<fields.length;i++){
                String type=fields[i].split(":")[1];if(c.isNull(i+1))row.put(JSONObject.NULL);else if(type.equals("s"))row.put(c.getString(i+1));else if(type.equals("l"))row.put(c.getLong(i+1));else row.put(c.getDouble(i+1));
            }rows.put(row);}
        }
        return new JSONObject().put("rows",rows).put("cursor",cursor).put("complete",rows.length()<PAGE).put("changed",!expected.equals(version(table,repo,plan,ledger,from,to)));
    }
    static void install(String table,Repository repo,SolarPlanning plan,RollerLedger ledger,long from,long to,JSONArray rows)throws Exception{
        SQLiteDatabase db=database(table,repo,plan,ledger,true);String[] fields=schema(table).split(",");db.beginTransaction();try{
            if(!incremental(table))db.delete(table,where(table,from,to),null);
            for(int i=0;i<rows.length();i++){
                JSONArray row=rows.getJSONArray(i);if(row.length()!=fields.length)throw new IllegalArgumentException("Unvollständige Berechnungsdaten.");ContentValues v=new ContentValues();
                for(int j=0;j<fields.length;j++){String[] f=fields[j].split(":");if(row.isNull(j))v.putNull(f[0]);else if(f[1].equals("s"))v.put(f[0],row.getString(j));else if(f[1].equals("l"))v.put(f[0],row.getLong(j));else{double value=row.getDouble(j);if(!Double.isFinite(value))throw new IllegalArgumentException("Ungültige Berechnungsdaten.");v.put(f[0],value);}}
                db.insertWithOnConflict(table,null,v,SQLiteDatabase.CONFLICT_REPLACE);
            }db.setTransactionSuccessful();
        }finally{db.endTransaction();}repo.db.clearDisplayPrior();
    }
    private AnalysisExchange(){}
}
