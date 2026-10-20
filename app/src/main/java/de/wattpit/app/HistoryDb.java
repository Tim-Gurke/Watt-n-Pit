package de.wattpit.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import de.wattpit.app.core.CsvCodec;
import de.wattpit.app.core.Reading;
import de.wattpit.app.core.HistoryQuery;
import de.wattpit.app.core.ArchiveMerge;
import de.wattpit.app.core.ArchiveCodec;
import de.wattpit.app.core.ArchiveSyncPlan;
import de.wattpit.app.core.EnergyIntegral;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.time.ZoneId;
import java.util.*;

final class HistoryDb extends SQLiteOpenHelper {
    private static final String[] NUMBERS = {"voltage","current","power","soc","solar","yieldWh","ttg","consumed","temperature","aux","load","state","error","alarm","pvVoltage","auxRaw"};
    HistoryDb(Context context) { super(context, "wattpit-history.db", null, 4); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE readings (ts INTEGER NOT NULL, device TEXT NOT NULL, kind TEXT NOT NULL, origin TEXT NOT NULL, " +
                "voltage REAL, current REAL, power REAL, soc REAL, solar REAL, yieldWh REAL, ttg REAL, consumed REAL, temperature REAL, aux REAL, load REAL, " +
                "state INTEGER, error INTEGER, alarm INTEGER, pvVoltage REAL, auxRaw REAL, period INTEGER NOT NULL DEFAULT 0, UNIQUE(ts,device,origin))");
        db.execSQL("CREATE INDEX history_window ON readings(ts,kind,origin)");
        db.execSQL("CREATE INDEX history_device ON readings(device,ts)");
        createDaily(db);
        createArchives(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createDaily(db);
        if(oldVersion<3){db.execSQL("ALTER TABLE readings ADD COLUMN period INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE readings ADD COLUMN pvVoltage REAL");db.execSQL("ALTER TABLE readings ADD COLUMN auxRaw REAL");createArchives(db);}
        else if(oldVersion<4)db.execSQL("ALTER TABLE device_daily ADD COLUMN finalized INTEGER NOT NULL DEFAULT 0");
    }

    private static void createArchives(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE device_daily(device TEXT NOT NULL,day TEXT NOT NULL,payload TEXT NOT NULL,finalized INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(device,day))");
        db.execSQL("CREATE TABLE archive_coverage(device TEXT NOT NULL,channel INTEGER NOT NULL,period INTEGER NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,PRIMARY KEY(device,channel,period,start))");
        db.execSQL("CREATE TABLE device_history(device TEXT PRIMARY KEY,payload TEXT NOT NULL)");
    }
    interface ArchiveCheck { void run() throws Exception; }
    ArchiveSyncPlan.Availability archiveAvailability(String device,String kind,int channel,String field,List<ArchiveCodec.Level> levels,ArchiveCodec.Clock clock,long now,ArchiveCheck check) throws Exception{
        List<Integer> periods=new ArrayList<>();for(ArchiveCodec.Level level:levels)periods.add(level.period);
        ArchiveSyncPlan.Availability local=new ArchiveSyncPlan.Availability(periods,now);
        long[] bounds=ArchiveSyncPlan.bounds(levels,clock);
        try(Cursor c=getReadableDatabase().rawQuery(ArchiveSyncPlan.readingsSql(field),new String[]{device,kind,""+bounds[0],""+bounds[1]})){
            int rows=0;while(c.moveToNext()){local.reading(c.getLong(0),c.getString(1),c.getInt(2));if(++rows%512==0)check.run();}
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT period,start,end FROM archive_coverage WHERE device=? AND channel=? AND start<=? AND end>=?",new String[]{device,""+channel,""+bounds[1],""+bounds[0]})){
            int rows=0;while(c.moveToNext()){local.imported(c.getInt(0),c.getLong(1),c.getLong(2));if(++rows%512==0)check.run();}
        }
        return local;
    }
    /** Catch live data recorded after preflight without reloading historical rows. */
    void archiveRecentLive(String device,String kind,String field,long since,ArchiveSyncPlan.Availability local){
        try(Cursor c=getReadableDatabase().rawQuery(ArchiveSyncPlan.readingsSql(field),new String[]{device,kind,""+Math.max(0,since-45000),""+Long.MAX_VALUE})){
            while(c.moveToNext())if(c.getString(1).equals("live"))local.reading(c.getLong(0),"live",c.getInt(2));
        }
    }
    synchronized void cover(String device,int channel,int period,long from,long to){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        String where="device=? AND channel=? AND period=? AND start<=? AND end>=?";
        String[] args={device,""+channel,""+period,""+(to+period*1000L),""+(from-period*1000L)};
        try {
            try(Cursor c=db.rawQuery("SELECT MIN(start),MAX(end) FROM archive_coverage WHERE "+where,args)){if(c.moveToFirst()&&!c.isNull(0)){from=Math.min(from,c.getLong(0));to=Math.max(to,c.getLong(1));}}
            db.delete("archive_coverage",where,args);ContentValues v=new ContentValues();v.put("device",device);v.put("channel",channel);v.put("period",period);v.put("start",from);v.put("end",to);db.insertOrThrow("archive_coverage",null,v);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    synchronized boolean archiveDayComplete(String device,String day){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM device_daily WHERE device=? AND day=? AND finalized=1",new String[]{device,day})){return c.moveToFirst();}
    }
    synchronized void archiveDaily(String device,String day,JSONObject payload,boolean finalized) {
        ContentValues v=new ContentValues();v.put("device",device);v.put("day",day);v.put("payload",payload.toString());v.put("finalized",finalized?1:0);
        getWritableDatabase().insertWithOnConflict("device_daily",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    synchronized void archiveHistory(String device,JSONObject payload) {
        ContentValues v=new ContentValues();v.put("device",device);v.put("payload",payload.toString());
        getWritableDatabase().insertWithOnConflict("device_history",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    JSONArray histories() throws Exception {
        JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM device_history",null)){while(c.moveToNext())out.put(new JSONObject(c.getString(0)));}return out;
    }
    /** A channel block is atomic. Complementary fields merge; finer values win at the same instant. */
    synchronized int archiveBlock(String device,String kind,String field,List<long[]> times,List<Double> numbers) {
        if(!Arrays.asList(NUMBERS).contains(field))throw new IllegalArgumentException("Unbekannter Archivwert.");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();int changed=0;
        try {for(int i=0;i<times.size();i++){
            long[] t=times.get(i);Double value=numbers.get(i);if(value==null)continue;
            ArchiveMerge match=ArchiveMerge.existing(field,device,kind,t[0],(int)t[1],value);
            try(Cursor c=db.rawQuery(match.sql,match.args)){if(c.moveToFirst())continue;}
            ContentValues v=new ContentValues();v.put(field,value);
            String[] key={String.valueOf(t[0]),device};
            boolean exists;int period=0;Double old=null;
            try(Cursor c=db.rawQuery("SELECT period,"+field+" FROM readings WHERE ts=? AND device=? AND origin='archive'",key)){
                exists=c.moveToFirst();if(exists){period=c.getInt(0);if(!c.isNull(1))old=c.getDouble(1);}
            }
            if(exists){
                if(old!=null&&(period<t[1]||Double.compare(old,value)==0))continue;
                v.put("period",period==0?t[1]:Math.min(period,t[1]));db.update("readings",v,"ts=? AND device=? AND origin='archive'",key);
            } else {v.put("ts",t[0]);v.put("device",device);v.put("kind",kind);v.put("origin","archive");v.put("period",t[1]);db.insertOrThrow("readings",null,v);}
            changed++;
        }
        // Power is derived only from fields at the same sample time.
        if(kind.equals("shunt")&&!times.isEmpty()&&(field.equals("voltage")||field.equals("current"))){
            long from=Long.MAX_VALUE,to=Long.MIN_VALUE;for(long[] t:times){from=Math.min(from,t[0]);to=Math.max(to,t[0]);}
            db.execSQL("UPDATE readings SET power=voltage*current WHERE device=? AND origin='archive' AND ts BETWEEN ? AND ? AND voltage IS NOT NULL AND current IS NOT NULL AND (power IS NULL OR power!=voltage*current)",new Object[]{device,from,to});
        }
        db.setTransactionSuccessful();return changed;
        }finally{db.endTransaction();}
    }
    private static void createDaily(SQLiteDatabase db) { db.execSQL("CREATE TABLE IF NOT EXISTS solar_daily(day TEXT PRIMARY KEY, payload TEXT NOT NULL)"); }
    JSONArray daily() throws Exception {
        JSONArray out = new JSONArray();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM (SELECT day,payload FROM solar_daily WHERE day NOT IN (SELECT day FROM device_daily) UNION ALL SELECT day,payload FROM device_daily) ORDER BY day DESC",null)) { while(c.moveToNext()) out.put(new JSONObject(c.getString(0))); }
        return out;
    }
    private int[] importDaily(CsvCodec.Table table) {
        int inserted=0,duplicates=0,rejected=0;
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            for(List<String> row:table.rows) {
                try {
                    java.time.LocalDate day=java.time.LocalDate.parse(row.get(1),java.time.format.DateTimeFormatter.ofPattern("M/d/uu"));
                    JSONObject payload=new JSONObject().put("day",day.toString());
                    for(int i=2;i<table.headers.size();i++) payload.put(table.headers.get(i),Double.parseDouble(row.get(i)));
                    ContentValues v=new ContentValues();v.put("day",day.toString());v.put("payload",payload.toString());
                    if(db.insertWithOnConflict("solar_daily",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1)inserted++;else duplicates++;
                } catch(Exception e) { rejected++; }
            }
            if(inserted==0 && duplicates==0)throw new IllegalArgumentException("Keine lesbaren Tagesdaten gefunden.");
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        return new int[]{inserted,duplicates,rejected};
    }

    boolean insert(Reading r) {
        ContentValues v = new ContentValues();
        v.put("ts", r.timestamp); v.put("device", r.device); v.put("kind", r.kind); v.put("origin", r.origin);v.put("period",r.period);
        Object[] values = values(r);
        for (int i = 0; i < NUMBERS.length; i++) {
            Object value = values[i];
            if (value == null) v.putNull(NUMBERS[i]);
            else if (value instanceof Integer) v.put(NUMBERS[i], (Integer) value);
            else v.put(NUMBERS[i], ((Number) value).doubleValue());
        }
        return getWritableDatabase().insertWithOnConflict("readings", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    JSONArray query(long from, long to, String metric, String source) throws Exception {
        if(flowKey(metric)!=null)return energyFlow(from,to,source).getJSONObject(flowKey(metric)).getJSONArray("points");
        if(metric.equals("solarEnergy")||metric.equals("dischargeEnergy")||metric.equals("batteryEnergy"))return energyData(from,to,metric,source).getJSONArray("points");
        HistoryQuery query = HistoryQuery.build(from,to,metric,source);
        JSONArray result = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(query.sql, query.args)) {
            while (c.moveToNext()) {
                JSONObject p = new JSONObject();
                p.put("t", c.getLong(0)); p.put("last", c.getLong(1)); p.put("device", c.getString(2));
                p.put("kind", c.getString(3)); p.put("origin", c.getString(4)); p.put("v", c.getDouble(5));
                p.put("period", c.getLong(9)); p.put("min", c.getDouble(6)); p.put("max", c.getDouble(7)); p.put("n", c.getLong(8));
                result.put(p);
            }
        }
        return result;
    }

    JSONObject stats() throws Exception {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*),MIN(ts),MAX(ts),SUM(CASE WHEN origin='archive' THEN 1 ELSE 0 END) FROM readings", null)) {
            c.moveToFirst();
            return new JSONObject().put("count", c.getLong(0)).put("from", c.isNull(1) ? 0 : c.getLong(1)).put("to", c.isNull(2) ? 0 : c.getLong(2)).put("archiveCount",c.isNull(3)?0:c.getLong(3));
        }
    }

    JSONObject dashboardData(long from,long to,String metric) throws Exception {
        if(flowKey(metric)!=null)return energyFlow(from,to,"all").getJSONObject(flowKey(metric));
        if(metric.equals("solarEnergy")||metric.equals("dischargeEnergy")||metric.equals("batteryEnergy"))return energyData(from,to,metric,"all");
        JSONObject summary=new JSONObject();
        HistoryQuery aggregate=HistoryQuery.summary(from,to,metric,"all");
        try (Cursor c=getReadableDatabase().rawQuery(aggregate.sql,aggregate.args)) {
            if(c.moveToFirst()) {
                for(int i=0;i<3;i++) summary.put(new String[]{"avg","min","max"}[i],c.isNull(i)?JSONObject.NULL:c.getDouble(i));
                summary.put("n",c.getLong(3));summary.put("lastTime",c.isNull(4)?0:c.getLong(4));
            }
        }
        HistoryQuery last=HistoryQuery.latest(from,to,metric,"all");
        try (Cursor c=getReadableDatabase().rawQuery(last.sql,last.args)) { summary.put("last",c.moveToFirst()?c.getDouble(0):JSONObject.NULL); }
        return new JSONObject().put("summary",summary).put("points",query(from,to,metric,"all"));
    }

    JSONObject energyData(long from,long to,String metric,String source) throws Exception {
        boolean discharge=metric.equals("dischargeEnergy");
        boolean balance=metric.equals("batteryEnergy");
        if(!discharge&&!balance&&!metric.equals("solarEnergy"))throw new IllegalArgumentException("Unbekannte Energiegröße.");
        to=Math.min(to,System.currentTimeMillis());
        if(to<=from)return new JSONObject().put("points",new JSONArray()).put("summary",new JSONObject());
        List<EnergyIntegral.Sample> samples=powerSamples(from,to,discharge||balance?"batteryPower":"solar",source);
        EnergyIntegral.Result result=balance?EnergyIntegral.balance(samples,from,to):EnergyIntegral.calculate(samples,from,to,discharge);
        return energyJson(result,from,to,balance?"battery-balance":discharge?"shunt":"solar");
    }

    private static String flowKey(String metric){
        return metric.equals("flowSolarEnergy")?"solar":metric.equals("consumptionEnergy")?"consumption":metric.equals("flowBalanceEnergy")?"balance":null;
    }

    JSONObject energyFlow(long from,long to,String source) throws Exception {
        return energyFlow(from,to,from,source);
    }
    JSONObject energyFlow(long from,long to,long solarFrom,String source) throws Exception {
        return energyFlow(from,to,solarFrom,to,source);
    }
    JSONObject energyFlow(long from,long to,long solarFrom,long solarTo,String source) throws Exception {
        to=Math.min(to,System.currentTimeMillis());
        solarFrom=Math.max(from,solarFrom);
        EnergyIntegral.Flow flow=to>from?EnergyIntegral.flow(powerSamples(from,to,"solar",source),powerSamples(from,to,"batteryPower",source),from,to,solarFrom,solarTo):null;
        JSONObject out=new JSONObject();String[] kinds={"solar","consumption","balance"};
        EnergyIntegral.Result[] results=flow==null?new EnergyIntegral.Result[]{new EnergyIntegral.Result(),new EnergyIntegral.Result(),new EnergyIntegral.Result()}:new EnergyIntegral.Result[]{flow.solar,flow.consumption,flow.balance};
        for(int i=0;i<kinds.length;i++)out.put(kinds[i],energyJson(results[i],i==0&&flow!=null?flow.solarFrom:from,i==0&&flow!=null?flow.solarTo:to,kinds[i]));
        return out;
    }

    private List<EnergyIntegral.Sample> powerSamples(long from,long to,String metric,String source){
        HistoryQuery query=HistoryQuery.rawPower(Math.max(0,from-EnergyIntegral.LOOKAROUND),Math.min(System.currentTimeMillis(),to+EnergyIntegral.LOOKAROUND),metric,source);
        List<EnergyIntegral.Sample> samples=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(query.sql,query.args)){
            while(c.moveToNext())samples.add(new EnergyIntegral.Sample(c.getLong(0),c.getDouble(1),c.getInt(2),c.getString(3),c.getString(4)));
        }
        return samples;
    }

    private JSONObject energyJson(EnergyIntegral.Result result,long from,long to,String kind)throws Exception{
        JSONArray points=new JSONArray();
        for(EnergyIntegral.Point p:result.points)points.put(new JSONObject().put("t",p.time).put("last",p.time).put("v",p.wh).put("min",p.wh).put("max",p.wh).put("n",1).put("gap",p.gap).put("kind",kind).put("device","calculated-"+kind).put("origin","calculated"));
        Object total=result.wh==null?JSONObject.NULL:result.wh;
        JSONObject summary=new JSONObject().put("last",total).put("min",result.wh==null?JSONObject.NULL:result.minimumWh).put("max",result.wh==null?JSONObject.NULL:result.maximumWh).put("n",result.points.size()).put("coverage",result.coverage(from,to)).put("coveredMillis",result.coveredMillis).put("from",from).put("to",to);
        return new JSONObject().put("points",points).put("summary",summary);
    }

    Reading last(String device) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM readings WHERE device=? AND origin='live' ORDER BY ts DESC LIMIT 1", new String[]{device})) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    int[] importTable(CsvCodec.Table table, Map<String, Integer> map, String kind) {
        if (table.headers.contains("Days ago") && table.headers.contains("Yield(Wh)")) return importDaily(table);
        int inserted = 0, duplicates = 0, rejected = 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (List<String> row : table.rows) {
                Reading r;
                try { r = CsvCodec.reading(table, row, map, kind, ZoneId.systemDefault()); }
                catch (IllegalArgumentException e) { rejected++; continue; }
                if (insert(r)) inserted++; else duplicates++;
            }
            if (inserted == 0 && duplicates == 0) throw new IllegalArgumentException("Keine lesbaren Messwerte gefunden. Bitte die Spaltenzuordnung prüfen.");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return new int[]{inserted, duplicates, rejected};
    }

    void exportTo(OutputStream output) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, java.nio.charset.StandardCharsets.UTF_8));
        writer.write(CsvCodec.EXPORT_HEADER); writer.newLine();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM readings ORDER BY ts", null)) {
            while (c.moveToNext()) { writer.write(CsvCodec.exportRow(fromCursor(c))); writer.newLine(); }
        }
        writer.flush();
    }

    private static Object[] values(Reading r) {
        return new Object[]{r.voltage,r.current,r.power,r.soc,r.solar,r.yieldWh,r.ttg,r.consumed,r.temperature,r.aux,r.load,r.state,r.error,r.alarm,r.pvVoltage,r.auxRaw};
    }
    static JSONObject json(Reading r) throws Exception {
        JSONObject out = new JSONObject().put("t", r.timestamp).put("device", r.device).put("kind", r.kind).put("origin", r.origin);
        Object[] values = values(r);
        for (int i = 0; i < NUMBERS.length; i++) out.put(NUMBERS[i], values[i] == null ? JSONObject.NULL : values[i]);
        out.put("period",r.period);return out;
    }
    private static Reading fromCursor(Cursor c) {
        Reading r = new Reading();
        r.timestamp = c.getLong(c.getColumnIndexOrThrow("ts"));
        r.device = c.getString(c.getColumnIndexOrThrow("device")); r.kind = c.getString(c.getColumnIndexOrThrow("kind"));
        r.origin = c.getString(c.getColumnIndexOrThrow("origin"));
        r.voltage = number(c,"voltage"); r.current = number(c,"current"); r.power = number(c,"power"); r.soc = number(c,"soc");
        r.solar = number(c,"solar"); r.yieldWh = number(c,"yieldWh"); r.ttg = number(c,"ttg"); r.consumed = number(c,"consumed");
        r.temperature = number(c,"temperature"); r.aux = number(c,"aux"); r.load = number(c,"load");
        r.state = integer(c,"state"); r.error = integer(c,"error"); r.alarm = integer(c,"alarm");
        r.pvVoltage=number(c,"pvVoltage");r.auxRaw=number(c,"auxRaw");r.period=c.getInt(c.getColumnIndexOrThrow("period"));
        return r;
    }
    private static Double number(Cursor c, String name) { int i = c.getColumnIndexOrThrow(name); return c.isNull(i) ? null : c.getDouble(i); }
    private static Integer integer(Cursor c, String name) { int i = c.getColumnIndexOrThrow(name); return c.isNull(i) ? null : c.getInt(i); }
}
