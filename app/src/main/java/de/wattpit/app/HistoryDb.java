package de.wattpit.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import de.wattpit.app.core.CsvCodec;
import de.wattpit.app.core.Reading;
import de.wattpit.app.core.HistoryQuery;
import de.wattpit.app.core.ArchiveCodec;
import de.wattpit.app.core.ArchiveSyncPlan;
import de.wattpit.app.core.EnergyIntegral;
import de.wattpit.app.core.SampleTimeline;
import de.wattpit.app.core.HistorySchema;
import de.wattpit.app.core.VictronSettings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.time.ZoneId;
import java.util.*;

final class HistoryDb extends SQLiteOpenHelper {
    private static final String[] NUMBERS = {"voltage","current","power","soc","solar","yieldWh","ttg","consumed","temperature","aux","load","state","error","alarm","pvVoltage","auxRaw"};
    HistoryDb(Context context) { super(context, "wattpit-history.db", null, 8); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL(HistorySchema.CREATE);
        createArchiveTimes(db);
        db.execSQL("CREATE INDEX history_window ON readings(ts,kind,origin)");
        db.execSQL("CREATE INDEX history_device ON readings(device,ts)");
        createDaily(db);
        createArchives(db);
        createDeviceSettings(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createDaily(db);
        if(oldVersion<3){db.execSQL("ALTER TABLE readings ADD COLUMN period INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE readings ADD COLUMN pvVoltage REAL");db.execSQL("ALTER TABLE readings ADD COLUMN auxRaw REAL");createArchives(db);}
        else if(oldVersion<4)db.execSQL("ALTER TABLE device_daily ADD COLUMN finalized INTEGER NOT NULL DEFAULT 0");
        if(oldVersion<5)for(String sql:HistorySchema.MIGRATE)db.execSQL(sql);
        if(oldVersion<6)createDeviceSettings(db);
        if(oldVersion<7)db.execSQL(HistorySchema.REPAIR_UNAVAILABLE_SHUNT_CURRENT);
        if(oldVersion<8&&newVersion>=8)createArchiveTimes(db);
    }

    private static void createArchiveTimes(SQLiteDatabase db){
        db.execSQL("ALTER TABLE readings ADD COLUMN archiveCounter INTEGER");
        db.execSQL("ALTER TABLE readings ADD COLUMN archiveOriginalTs INTEGER");
        db.execSQL("CREATE TABLE IF NOT EXISTS archive_clock_state(device TEXT PRIMARY KEY,payload TEXT NOT NULL)");
    }
    JSONObject archiveClockState(String device)throws Exception{
        try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM archive_clock_state WHERE device=?",new String[]{device})){return c.moveToFirst()?new JSONObject(c.getString(0)):new JSONObject();}
    }
    String archiveClockRevision()throws Exception{
        StringBuilder out=new StringBuilder();try(Cursor c=getReadableDatabase().rawQuery("SELECT device,payload FROM archive_clock_state ORDER BY device",null)){
            while(c.moveToNext())out.append(c.getString(0)).append(':').append(new JSONObject(c.getString(1)).optLong("revision")).append(';');
        }return out.toString();
    }
    /** Counter recovery, timestamp changes, acknowledgements and the mapping commit together.
     * Original UTC timestamps and all measurement fields remain available. */
    synchronized int alignArchiveTimes(String device,ArchiveCodec.Clock before,ArchiveCodec.Clock after,JSONObject state,boolean counterReset,boolean remap,ArchiveCheck check)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();int moved=0;
        try{
            db.execSQL("CREATE TEMP TABLE IF NOT EXISTS archive_time_repair(id INTEGER PRIMARY KEY,old_ts INTEGER,new_ts INTEGER,counter INTEGER,period INTEGER,UNIQUE(new_ts,period))");
            db.execSQL("DELETE FROM archive_time_repair");
            if(remap)try(Cursor c=db.rawQuery("SELECT rowid,ts,period,archiveCounter FROM readings WHERE device=? AND origin='archive' ORDER BY ts",new String[]{device})){
                int rows=0;while(c.moveToNext()){
                    long id=c.getLong(0),old=c.getLong(1);Long counter=c.isNull(3)?before.counter(old):c.getLong(3);
                    if(counter==null){if(counterReset)continue;throw new IllegalStateException("Eine alte Archivzeit ist nicht eindeutig zuordenbar. Messwerte bleiben erhalten; bitte Diagnoseexport senden.");}
                    // A reset device counter may reuse old counter values. Never move the previous generation.
                    if(!c.isNull(3)&&!Objects.equals(before.millis(counter),old))continue;
                    Long time=counterReset?old:after.millis(counter);if(time==null)continue;
                    if(time<0||time>state.getLong("at")+300000)throw new IllegalStateException("Berichtigte Archivzeit liegt außerhalb des sicheren Bereichs.");
                    if(time!=old||c.isNull(3)){
                        ContentValues v=new ContentValues();v.put("id",id);v.put("old_ts",old);v.put("new_ts",time);v.put("counter",counter);v.put("period",c.getInt(2));db.insertOrThrow("archive_time_repair",null,v);
                        if(time!=old)moved++;
                    }
                    if(++rows%256==0)check.run();
                }
            }
            // Negative temporary keys prevent a shifted row colliding with its next old neighbour.
            db.execSQL("UPDATE readings SET ts=-rowid-1,archiveOriginalTs=COALESCE(archiveOriginalTs,ts) WHERE rowid IN (SELECT id FROM archive_time_repair WHERE new_ts!=old_ts)");
            db.execSQL("UPDATE readings SET ts=(SELECT new_ts FROM archive_time_repair WHERE id=readings.rowid),archiveCounter=(SELECT counter FROM archive_time_repair WHERE id=readings.rowid) WHERE rowid IN (SELECT id FROM archive_time_repair)");
            if(remap||counterReset)db.delete("archive_coverage","device=?",new String[]{device});
            state.put("movedRows",moved);ContentValues saved=new ContentValues();saved.put("device",device);saved.put("payload",state.toString());db.insertWithOnConflict("archive_clock_state",null,saved,SQLiteDatabase.CONFLICT_REPLACE);
            db.execSQL("DELETE FROM archive_time_repair");db.setTransactionSuccessful();return moved;
        }finally{db.endTransaction();}
    }

    private static void createDeviceSettings(SQLiteDatabase db){db.execSQL("CREATE TABLE device_settings(device TEXT NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(device,start))");}
    /** Equal reads confirm an interval; a changed profile never applies before the new read. */
    synchronized void deviceSettings(String device,JSONObject snapshot)throws Exception{
        long at=snapshot.getLong("at"),start=at;SQLiteDatabase db=getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT start,end,payload FROM device_settings WHERE device=? ORDER BY start DESC LIMIT 1",new String[]{device})){
            if(c.moveToFirst()&&at>=c.getLong(1)&&at-c.getLong(1)<=VictronSettings.MAX_AGE&&new JSONObject(c.getString(2)).getJSONObject("values").toString().equals(snapshot.getJSONObject("values").toString()))start=c.getLong(0);
        }
        ContentValues v=new ContentValues();v.put("device",device);v.put("start",start);v.put("end",at);v.put("payload",snapshot.toString());db.insertWithOnConflict("device_settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        db.delete("device_settings","end<?",new String[]{""+(LearningWindow.since(at)-10L*86400000)});
    }
    JSONArray deviceSettingsHistory(String device,long from,long to)throws Exception{
        JSONArray result=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT start,end,payload FROM device_settings WHERE device=? AND start<=? AND end>=? ORDER BY start",new String[]{device,""+to,""+(from-VictronSettings.MAX_AGE)})){
            while(c.moveToNext())result.put(new JSONObject(c.getString(2)).put("since",c.getLong(0)).put("until",c.getLong(1)));
        }return result;
    }

    private static void createArchives(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE device_daily(device TEXT NOT NULL,day TEXT NOT NULL,payload TEXT NOT NULL,finalized INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(device,day))");
        db.execSQL(HistorySchema.COVERAGE);
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
    /** Runs inside the same transaction as the block's measurements. */
    private void cover(SQLiteDatabase db,String device,int channel,int period,long from,long to){
        String where="device=? AND channel=? AND period=? AND start<=? AND end>=?";
        String[] args={device,""+channel,""+period,""+(to+period*1000L),""+(from-period*1000L)};
        try(Cursor c=db.rawQuery("SELECT MIN(start),MAX(end) FROM archive_coverage WHERE "+where,args)){if(c.moveToFirst()&&!c.isNull(0)){from=Math.min(from,c.getLong(0));to=Math.max(to,c.getLong(1));}}
        db.delete("archive_coverage",where,args);ContentValues v=new ContentValues();v.put("device",device);v.put("channel",channel);v.put("period",period);v.put("start",from);v.put("end",to);db.insertOrThrow("archive_coverage",null,v);
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
    /** Measurements and all acknowledgements, including NA, commit or roll back together. */
    synchronized int archiveBlock(String device,String kind,String field,int channel,int period,List<long[]> times,List<Double> numbers,List<long[]> completed) {
        if(!Arrays.asList(NUMBERS).contains(field))throw new IllegalArgumentException("Unbekannter Archivwert.");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();int changed=0;
        try {for(int i=0;i<times.size();i++){
            long[] t=times.get(i);Double value=numbers.get(i);if(value==null)continue;
            ContentValues v=new ContentValues();v.put(field,value);if(t.length>2)v.put("archiveCounter",t[2]);
            String[] key={String.valueOf(t[0]),device,String.valueOf(t[1])};
            boolean exists;Double old=null;
            Long oldCounter=null;
            try(Cursor c=db.rawQuery("SELECT period,"+field+",archiveCounter FROM readings WHERE ts=? AND device=? AND period=? AND origin='archive'",key)){
                exists=c.moveToFirst();if(exists&&!c.isNull(1))old=c.getDouble(1);if(exists&&!c.isNull(2))oldCounter=c.getLong(2);
            }
            if(exists){
                if(old!=null&&Double.compare(old,value)==0&&(t.length<3||Objects.equals(oldCounter,t[2])))continue;
                v.put("period",t[1]);db.update("readings",v,"ts=? AND device=? AND period=? AND origin='archive'",key);
            } else {v.put("ts",t[0]);v.put("device",device);v.put("kind",kind);v.put("origin","archive");v.put("period",t[1]);db.insertOrThrow("readings",null,v);}
            changed++;
        }
        // Power is derived only from fields at the same sample time.
        if(kind.equals("shunt")&&!times.isEmpty()&&(field.equals("voltage")||field.equals("current"))){
            long from=Long.MAX_VALUE,to=Long.MIN_VALUE;for(long[] t:times){from=Math.min(from,t[0]);to=Math.max(to,t[0]);}
            db.execSQL("UPDATE readings SET power=voltage*current WHERE device=? AND origin='archive' AND ts BETWEEN ? AND ? AND voltage IS NOT NULL AND current IS NOT NULL AND (power IS NULL OR power!=voltage*current)",new Object[]{device,from,to});
        }
        for(long[] range:completed)cover(db,device,channel,period,range[0],range[1]);
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
        r.discardUnavailableCurrent();
        ContentValues v = new ContentValues();
        v.put("ts", r.timestamp); v.put("device", r.device); v.put("kind", r.kind); v.put("origin", r.origin);v.put("period",r.period);
        if(r.origin.equals("archive")){if(r.archiveCounter!=null)v.put("archiveCounter",r.archiveCounter);if(r.archiveOriginalTs!=null)v.put("archiveOriginalTs",r.archiveOriginalTs);}
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
        HistoryQuery query=HistoryQuery.raw(Math.max(0,from-EnergyIntegral.LOOKAROUND),Math.min(System.currentTimeMillis(),to+EnergyIntegral.LOOKAROUND),metric,source);
        Map<String,List<SampleTimeline.Sample>> devices=new LinkedHashMap<>();Map<String,String> kinds=new HashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery(query.sql,query.args)){
            while(c.moveToNext()){
                String device=c.getString(3);kinds.put(device,c.getString(5));
                devices.computeIfAbsent(device,k->new ArrayList<>()).add(new SampleTimeline.Sample(c.getLong(0),c.getDouble(1),c.getInt(2),device,c.getString(4)));
            }
        }
        if(metric.equals("power")||metric.equals("batteryPower")){
            HistoryQuery candidates=HistoryQuery.powerDevices(Math.max(0,from-EnergyIntegral.LOOKAROUND),Math.min(System.currentTimeMillis(),to+EnergyIntegral.LOOKAROUND),source);
            try(Cursor c=getReadableDatabase().rawQuery(candidates.sql,candidates.args)){
                while(c.moveToNext()){String device=c.getString(0);devices.computeIfAbsent(device,k->new ArrayList<>());kinds.put(device,c.getString(1));}
            }
        }
        JSONArray result=new JSONArray();
        for(Map.Entry<String,List<SampleTimeline.Sample>> entry:devices.entrySet()){
            List<SampleTimeline.Interval> intervals=kinds.get(entry.getKey()).equals("shunt")&&(metric.equals("power")||metric.equals("batteryPower"))?
                    EnergyIntegral.powerIntervals(fieldSamples(from,to,"batteryPower",source,entry.getKey()),fieldSamples(from,to,"batteryVoltage",source,entry.getKey()),fieldSamples(from,to,"batteryCurrent",source,entry.getKey()),from,to,false):SampleTimeline.intervals(entry.getValue(),from,to,true);
            for(SampleTimeline.PlotPoint point:SampleTimeline.plot(entry.getValue(),intervals,from,to)){
                result.put(new JSONObject().put("t",point.time).put("last",point.time).put("device",entry.getKey()).put("kind",kinds.get(entry.getKey()))
                .put("origin",source.equals("all")?"merged":source).put("v",point.value).put("min",point.value).put("max",point.value).put("n",1).put("period",0).put("gap",point.gap));
            }
        }
        return result;
    }

    JSONObject sunsetSoc(long at,long now,String device)throws Exception {
        HistoryQuery query=HistoryQuery.sunsetSoc(at,now,device);
        try(Cursor c=getReadableDatabase().rawQuery(query.sql,query.args)){
            if(c.moveToFirst())return new JSONObject().put("t",c.getLong(0)).put("soc",c.getDouble(1)).put("origin",c.getString(2));
        }
        return null;
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
        JSONArray plotted=query(from,to,metric,"all");
        if(metric.equals("batteryPower")&&plotted.length()>0){
            JSONObject latest=plotted.getJSONObject(plotted.length()-1);double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
            for(int i=0;i<plotted.length();i++){JSONObject p=plotted.getJSONObject(i);low=Math.min(low,p.getDouble("v"));high=Math.max(high,p.getDouble("v"));if(p.getLong("t")>latest.getLong("t"))latest=p;}
            if(summary.isNull("last")||latest.getLong("t")>summary.optLong("lastTime"))summary.put("last",latest.getDouble("v")).put("lastTime",latest.getLong("t"));
            summary.put("min",Math.min(summary.optDouble("min",low),low)).put("max",Math.max(summary.optDouble("max",high),high));
            if(summary.isNull("avg")){
                EnergyIntegral.Result energy=EnergyIntegral.calculateIntervals(batteryIntervals(from,to,"all",null),from,to,false,true);
                if(energy.wh!=null&&energy.coveredMillis>0)summary.put("avg",energy.wh*3600000/energy.coveredMillis);
            }
        }
        return new JSONObject().put("summary",summary).put("points",plotted);
    }

    JSONObject energyData(long from,long to,String metric,String source) throws Exception {
        boolean discharge=metric.equals("dischargeEnergy");
        boolean balance=metric.equals("batteryEnergy");
        if(!discharge&&!balance&&!metric.equals("solarEnergy"))throw new IllegalArgumentException("Unbekannte Energiegröße.");
        to=Math.min(to,System.currentTimeMillis());
        if(to<=from)return new JSONObject().put("points",new JSONArray()).put("summary",new JSONObject());
        EnergyIntegral.Result result=discharge||balance?EnergyIntegral.calculateIntervals(batteryIntervals(from,to,source,null),from,to,discharge,balance):EnergyIntegral.calculate(powerSamples(from,to,"solar",source),from,to,false);
        return energyJson(result,from,to,balance?"battery-balance":discharge?"shunt":"solar");
    }

    private static String flowKey(String metric){
        return metric.equals("flowSolarEnergy")?"solar":metric.equals("consumptionEnergy")?"consumption":metric.equals("flowBalanceEnergy")?"balance":null;
    }

    JSONObject energyFlow(long from,long to,String source) throws Exception {
        return energyFlow(from,to,from,source);
    }
    /** Calendar-day summaries of the existing energy flow, read once per month off the UI thread. */
    JSONObject dailyEnergy(long from,long to,JSONObject weather)throws Exception{
        return dailyEnergy(from,to,weather,null);
    }
    JSONObject dailyEnergy(long from,long to,JSONObject weather,List<EnergyIntegral.ModelInterval> model)throws Exception{
        if(from<0||to<=from||to-from>32L*86400000)throw new IllegalArgumentException("Ungültiger Tageszeitraum.");
        long end=Math.min(to,System.currentTimeMillis());JSONArray days=new JSONArray();
        if(end<=from)return new JSONObject().put("days",days);
        List<SampleTimeline.Interval> solar=SampleTimeline.energyIntervals(powerSamples(from,end,"solar","all"),from,end,false);
        List<SampleTimeline.Interval> battery=batteryIntervals(from,end,"all",null);
        java.time.ZoneId zone=java.time.ZoneId.systemDefault();
        java.time.LocalDate day=java.time.Instant.ofEpochMilli(from).atZone(zone).toLocalDate();
        JSONArray sunDays=weather.optJSONArray("days");
        while(day.atStartOfDay(zone).toInstant().toEpochMilli()<end){
            long start=Math.max(from,day.atStartOfDay(zone).toInstant().toEpochMilli());
            long dayEnd=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),next=Math.min(end,dayEnd);
            long solarFrom=start,solarTo=next;
            if(sunDays!=null)for(int i=0;i<sunDays.length();i++){
                JSONObject sun=sunDays.getJSONObject(i);if(!day.toString().equals(sun.optString("day")))continue;
                long rise=sun.optLong("sunrise"),set=sun.optLong("sunset");
                if(rise>=start&&rise<dayEnd)solarFrom=Math.min(next,rise);
                if(set>rise&&set<=dayEnd&&set>start)solarTo=Math.min(next,set);
                break;
            }
            List<EnergyIntegral.ModelInterval> clipped=null;if(model!=null){clipped=new ArrayList<>();for(EnergyIntegral.ModelInterval x:model){long a=Math.max(start,x.interval.from),b=Math.min(next,x.interval.to);if(b>a)clipped.add(x.slice(a,b));}}
            EnergyIntegral.Result result=EnergyIntegral.flowIntervals(clipIntervals(solar,start,next),clipIntervals(battery,start,next),start,next,solarFrom,solarTo,clipped,null).consumption;
            days.put(new JSONObject().put("day",day.toString()).put("last",result.wh==null?JSONObject.NULL:result.wh)
                    .put("coverage",result.coverage(start,next)).put("modelledMillis",result.modelledMillis).put("inconsistentMillis",result.inconsistentMillis).put("adjustedMillis",result.adjustedMillis).put("limitedMillis",result.limitedMillis).put("interpolatedMillis",result.interpolatedMillis).put("coveredMillis",result.coveredMillis).put("from",start).put("to",next));
            day=day.plusDays(1);
        }
        return new JSONObject().put("days",days);
    }
    /** Original energy formula in local bins; learning may use complete sub-intervals of a bin. */
    JSONArray planningObservations(long from,long to,JSONObject weather,String solarDevice,String shuntDevice)throws Exception{
        return planningObservations(from,to,weather,solarDevice,shuntDevice,Collections.emptyList());
    }
    JSONArray planningObservations(long from,long to,JSONObject weather,String solarDevice,String shuntDevice,List<Long> changes)throws Exception{
        java.time.ZoneId zone;try{zone=java.time.ZoneId.of(weather.optString("timezone",java.time.ZoneId.systemDefault().getId()));}catch(Exception e){zone=java.time.ZoneId.systemDefault();}
        java.time.LocalDate day=java.time.Instant.ofEpochMilli(from).atZone(zone).toLocalDate();
        List<EnergyIntegral.Sample> solarSamples=planningSamples(from,to,"solar",solarDevice);
        List<SampleTimeline.Interval> solar=SampleTimeline.energyIntervals(solarSamples,from,to,false,false);
        List<SampleTimeline.Interval> battery=EnergyIntegral.powerIntervals(planningSamples(from,to,"batteryPower",shuntDevice),planningSamples(from,to,"batteryVoltage",shuntDevice),planningSamples(from,to,"batteryCurrent",shuntDevice),from,to,false);
        List<SampleTimeline.Interval> states=SampleTimeline.intervals(planningSamples(from,to,"state",solarDevice),from,to,false);
        List<SampleTimeline.Interval> volts=SampleTimeline.intervals(planningSamples(from,to,"solarVoltage",solarDevice),from,to,false);
        List<SampleTimeline.Interval> amps=SampleTimeline.intervals(planningSamples(from,to,"solarCurrent",solarDevice),from,to,true);
        List<SampleTimeline.Interval> errors=SampleTimeline.intervals(planningSamples(from,to,"error",solarDevice),from,to,false);
        TreeSet<Long> energyCuts=new TreeSet<>(),statusCuts=new TreeSet<>();
        planningRunCuts(energyCuts,solar,0);planningRunCuts(energyCuts,battery,0);
        planningRunCuts(statusCuts,states,1);planningRunCuts(statusCuts,errors,2);planningRunCuts(statusCuts,volts,0);planningRunCuts(statusCuts,amps,0);
        JSONArray days=weather.optJSONArray("days"),rows=new JSONArray();
        while(day.atStartOfDay(zone).toInstant().toEpochMilli()<to){
            long start=day.atStartOfDay(zone).toInstant().toEpochMilli(),end=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),rise=start,set=end;
            if(days!=null)for(int i=0;i<days.length();i++){JSONObject d=days.getJSONObject(i);if(day.toString().equals(d.optString("day"))){long r=d.optLong("sunrise"),s=d.optLong("sunset");if(r>=start&&s>r&&s<=end){rise=r;set=s;}break;}}
            long[] bounds={start,day.atTime(11,0).atZone(zone).toInstant().toEpochMilli(),day.atTime(16,0).atZone(zone).toInstant().toEpochMilli(),end};
            for(int slot=0;slot<3;slot++){
                long a=bounds[slot],b=bounds[slot+1];if(a<from||a>=to)continue;
                JSONObject row=planningFlow(a,b,rise,set,solar,battery,states).put("day",day.toString()).put("slot",slot);
                TreeSet<Long> cuts=new TreeSet<>();long endOfData=Math.min(b,to);cuts.add(a);cuts.add(endOfData);
                for(long t=a+3600000;t<endOfData;t+=3600000)cuts.add(t);
                if(rise>a&&rise<endOfData)cuts.add(rise);if(set>a&&set<endOfData)cuts.add(set);
                for(long t:changes)if(t>a&&t<endOfData)cuts.add(t);
                JSONArray segments=new JSONArray();Long previous=null;
                for(long next:cuts){if(previous!=null&&next>previous){
                    JSONObject segment=planningSegment(previous,next,rise,set,solar,battery,states,volts,amps,errors);
                    TreeSet<Long> pieces=new TreeSet<>(energyCuts.subSet(previous,false,next,false));
                    if(segment.getLong("lightMillis")>0)pieces.addAll(statusCuts.subSet(previous,false,next,false));
                    if(!pieces.isEmpty()){
                        pieces.add(previous);pieces.add(next);JSONArray observed=new JSONArray();Long part=null;
                        for(long edge:pieces){if(part!=null)observed.put(planningSegment(part,edge,rise,set,solar,battery,states,volts,amps,errors));part=edge;}
                        segment.put("usageSegments",observed);
                    }
                    segments.put(segment);
                }previous=next;}
                rows.put(row.put("segments",segments));
            }day=day.plusDays(1);
        }return rows;
    }
    /** Split only at real gaps or changes in Bulk/error eligibility, never at every sample. */
    private static void planningRunCuts(TreeSet<Long> cuts,List<SampleTimeline.Interval> intervals,int condition){
        long start=-1,end=-1;
        for(SampleTimeline.Interval x:intervals){
            boolean eligible=condition==0||(condition==1?x.a==3&&x.b==3:x.a==0&&x.b==0);
            if(!eligible)continue;
            if(start>=0&&x.from<=end){end=Math.max(end,x.to);continue;}
            if(start>=0){cuts.add(start);cuts.add(end);}start=x.from;end=x.to;
        }
        if(start>=0){cuts.add(start);cuts.add(end);}
    }
    private static JSONObject planningSegment(long from,long to,long rise,long set,List<SampleTimeline.Interval> solar,List<SampleTimeline.Interval> battery,List<SampleTimeline.Interval> states,List<SampleTimeline.Interval> volts,List<SampleTimeline.Interval> amps,List<SampleTimeline.Interval> errors)throws Exception{
        JSONObject segment=planningFlow(from,to,rise,set,solar,battery,states);
        planningRange(segment,"voltage",volts,from,to);planningRange(segment,"current",amps,from,to);planningRange(segment,"error",errors,from,to);planningRange(segment,"state",states,from,to);return segment;
    }
    private static JSONObject planningFlow(long a,long b,long rise,long set,List<SampleTimeline.Interval> solar,List<SampleTimeline.Interval> battery,List<SampleTimeline.Interval> states)throws Exception{
        long sunFrom=Math.max(a,Math.min(b,rise)),sunTo=Math.max(sunFrom,Math.min(b,set));
        EnergyIntegral.Flow flow=EnergyIntegral.flowIntervals(clipIntervals(solar,a,b),clipIntervals(battery,a,b),a,b,sunFrom,sunTo);
        long lightFrom=flow.solarFrom,lightTo=flow.solarTo,bulk=0;for(SampleTimeline.Interval x:clipIntervals(states,lightFrom,lightTo))if(x.a==3&&x.b==3)bulk+=x.to-x.from;
        EnergyIntegral.Result measuredSolar=EnergyIntegral.calculateIntervals(clipIntervals(solar,lightFrom,lightTo),lightFrom,lightTo,false,true);
        boolean extraCharging=false;EnergyIntegral.Point previous=null;for(EnergyIntegral.Point point:flow.consumption.points){if(previous!=null&&!point.gap&&point.wh<previous.wh-2)extraCharging=true;previous=point;}
        return new JSONObject().put("from",a).put("to",b).put("consumption",flow.consumption.wh==null?JSONObject.NULL:flow.consumption.wh).put("solar",lightFrom==lightTo?0:measuredSolar.wh==null?JSONObject.NULL:measuredSolar.wh)
            .put("coverage",flow.consumption.coverage(a,b)).put("solarCoverage",lightTo>lightFrom?measuredSolar.coverage(lightFrom,lightTo):1).put("extraCharging",extraCharging)
            .put("actualSolarCoverage",planningCoverage(solar,a,b)).put("lightMillis",Math.max(0,lightTo-lightFrom))
            .put("bulkCoverage",lightTo>lightFrom?Math.min(1,bulk/(double)(lightTo-lightFrom)):1);
    }
    private static void planningRange(JSONObject row,String name,List<SampleTimeline.Interval> intervals,long from,long to)throws Exception{
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;long covered=0;
        for(SampleTimeline.Interval x:clipIntervals(intervals,from,to)){covered+=x.to-x.from;min=Math.min(min,Math.min(x.a,x.b));max=Math.max(max,Math.max(x.a,x.b));}
        if(covered>=(to-from)*.98)row.put(name+"Min",min).put(name+"Max",max);
    }
    private List<EnergyIntegral.Sample> planningSamples(long from,long to,String field,String device){
        if(device==null||device.isEmpty())return new ArrayList<>();
        List<EnergyIntegral.Sample> samples=fieldSamples(from,to,field,"all",device);samples.removeIf(s->s.origin.equals("csv"));return samples;
    }
    private static double planningCoverage(List<SampleTimeline.Interval> intervals,long from,long to){long covered=0;for(SampleTimeline.Interval x:clipIntervals(intervals,from,to))covered+=x.to-x.from;return to>from?Math.min(1,covered/(double)(to-from)):0;}
    private static List<SampleTimeline.Interval> clipIntervals(List<SampleTimeline.Interval> intervals,long from,long to){
        List<SampleTimeline.Interval> clipped=new ArrayList<>();
        for(SampleTimeline.Interval x:intervals){
            if(x.to<=from)continue;if(x.from>=to)break;
            long start=Math.max(from,x.from),end=Math.min(to,x.to);
            if(end>start)clipped.add(new SampleTimeline.Interval(start,end,x.value(start),x.value(end),x.interpolated));
        }
        return clipped;
    }
    JSONObject energyFlow(long from,long to,long solarFrom,String source) throws Exception {
        return energyFlow(from,to,solarFrom,to,source);
    }
    JSONObject energyFlow(long from,long to,long solarFrom,long solarTo,String source) throws Exception {
        return energyFlow(from,to,solarFrom,solarTo,source,null,null);
    }
    JSONObject energyFlow(long from,long to,long solarFrom,long solarTo,String source,List<EnergyIntegral.ModelInterval> extra,List<EnergyIntegral.ModelInterval> mobile) throws Exception {
        to=Math.min(to,System.currentTimeMillis());
        solarFrom=Math.max(from,solarFrom);
        EnergyIntegral.Flow flow=to>from?EnergyIntegral.flowIntervals(SampleTimeline.energyIntervals(powerSamples(from,to,"solar",source),from,to,false),batteryIntervals(from,to,source,null),from,to,solarFrom,solarTo,extra,mobile):null;
        JSONObject out=new JSONObject();String[] kinds={"solar","consumption","balance","mobile"};
        EnergyIntegral.Result[] results=flow==null?new EnergyIntegral.Result[]{new EnergyIntegral.Result(),new EnergyIntegral.Result(),new EnergyIntegral.Result(),new EnergyIntegral.Result()}:new EnergyIntegral.Result[]{flow.solar,flow.consumption,flow.balance,flow.mobile};
        for(int i=0;i<kinds.length;i++)out.put(kinds[i],energyJson(results[i],i==0&&flow!=null?flow.solarFrom:from,i==0&&flow!=null?flow.solarTo:to,kinds[i]));
        return out;
    }

    private List<SampleTimeline.Interval> batteryIntervals(long from,long to,String source,String device){
        return EnergyIntegral.powerIntervals(fieldSamples(from,to,"batteryPower",source,device),fieldSamples(from,to,"batteryVoltage",source,device),fieldSamples(from,to,"batteryCurrent",source,device),from,to);
    }
    private static final class MobilePast {
        final String key;final long at;final List<SampleTimeline.Interval> solar,battery,voltage;
        MobilePast(String key,long at,List<SampleTimeline.Interval> solar,List<SampleTimeline.Interval> battery,List<SampleTimeline.Interval> voltage){this.key=key;this.at=at;this.solar=solar;this.battery=battery;this.voltage=voltage;}
    }
    private MobilePast mobilePast;
    private final Object mobilePastLock=new Object();
    /** Five-minute means are used only for an explicitly estimated historical load prior. */
    private static List<SampleTimeline.Interval> priorMeans(List<SampleTimeline.Interval> input){
        List<SampleTimeline.Interval> out=new ArrayList<>();long from=-1,end=-1,bucket=-1,duration=0;double area=0;
        for(SampleTimeline.Interval x:input){long a=x.from;while(a<x.to){long slot=a/300000,b=Math.min(x.to,(slot+1)*300000);
            if(duration>0&&(slot!=bucket||a!=end)){double avg=area/duration;out.add(new SampleTimeline.Interval(from,end,avg,avg));duration=0;area=0;}
            if(duration==0){from=a;bucket=slot;}area+=(x.value(a)+x.value(b))/2*(b-a);duration+=b-a;end=b;a=b;
        }}if(duration>0){double avg=area/duration;out.add(new SampleTimeline.Interval(from,end,avg,avg));}return out;
    }
    private MobilePast mobilePast(long from,long to,String source){synchronized(mobilePastLock){
        long now=android.os.SystemClock.elapsedRealtime();String key=from+":"+to+":"+source;
        if(mobilePast!=null&&mobilePast.key.equals(key)&&now>=mobilePast.at&&now-mobilePast.at<60000)return mobilePast;
        List<SampleTimeline.Interval> sun=new ArrayList<>(),net=new ArrayList<>(),volts=new ArrayList<>();
        if(to>from){sun=priorMeans(SampleTimeline.energyIntervals(powerSamples(from,to,"solar",source),from,to,false));net=priorMeans(batteryIntervals(from,to,source,null));volts=priorMeans(SampleTimeline.energyIntervals(fieldSamples(from,to,"batteryVoltage",source,null),from,to,false));}
        return mobilePast=new MobilePast(key,now,sun,net,volts);
    }}
    de.wattpit.app.core.MobileSolarAccounting.Result adjustMobileModel(long lookback,long from,long to,String source,List<EnergyIntegral.ModelInterval> extra,List<EnergyIntegral.ModelInterval> mobile,List<SampleTimeline.Interval> roller){
        long cut=from/86400000L*86400000L;MobilePast past=mobilePast(lookback,cut,source);
        List<SampleTimeline.Interval> sun=new ArrayList<>(past.solar),net=new ArrayList<>(past.battery),volts=new ArrayList<>(past.voltage);
        sun.addAll(SampleTimeline.energyIntervals(powerSamples(from,to,"solar",source),from,to,false));net.addAll(batteryIntervals(from,to,source,null));volts.addAll(SampleTimeline.energyIntervals(fieldSamples(from,to,"batteryVoltage",source,null),from,to,false));
        return de.wattpit.app.core.MobileSolarAccounting.adjust(from,to,sun,net,
            SampleTimeline.energyIntervals(fieldSamples(from,to,"state",source,null),from,to,false),volts,
            SampleTimeline.energyIntervals(fieldSamples(from,to,"soc",source,null),from,to,false),extra,mobile,roller);
    }
    private List<EnergyIntegral.Sample> fieldSamples(long from,long to,String metric,String source,String device){
        HistoryQuery query=HistoryQuery.raw(Math.max(0,from-EnergyIntegral.LOOKAROUND),Math.min(System.currentTimeMillis(),to+EnergyIntegral.LOOKAROUND),metric,source);
        List<EnergyIntegral.Sample> samples=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(query.sql,query.args)){
            while(c.moveToNext())if(device==null||device.equals(c.getString(3)))samples.add(new EnergyIntegral.Sample(c.getLong(0),c.getDouble(1),c.getInt(2),c.getString(3),c.getString(4)));
        }
        return samples;
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
        JSONObject summary=new JSONObject().put("last",total).put("min",result.wh==null?JSONObject.NULL:result.minimumWh).put("max",result.wh==null?JSONObject.NULL:result.maximumWh).put("n",result.points.size()).put("coverage",result.coverage(from,to)).put("measuredCoverage",result.measuredCoverage(from,to)).put("modelledMillis",result.modelledMillis).put("inconsistentMillis",result.inconsistentMillis).put("adjustedMillis",result.adjustedMillis).put("limitedMillis",result.limitedMillis).put("estimated",kind.equals("mobile")||result.modelledMillis>0).put("interpolatedMillis",result.interpolatedMillis).put("coveredMillis",result.coveredMillis).put("from",from).put("to",to);
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
        int counter=c.getColumnIndexOrThrow("archiveCounter"),original=c.getColumnIndexOrThrow("archiveOriginalTs");
        if(!c.isNull(counter))r.archiveCounter=c.getLong(counter);if(!c.isNull(original))r.archiveOriginalTs=c.getLong(original);
        return r;
    }
    private static Double number(Cursor c, String name) { int i = c.getColumnIndexOrThrow(name); return c.isNull(i) ? null : c.getDouble(i); }
    private static Integer integer(Cursor c, String name) { int i = c.getColumnIndexOrThrow(name); return c.isNull(i) ? null : c.getInt(i); }
}
