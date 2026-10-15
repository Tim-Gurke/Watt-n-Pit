package de.wattpit.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import de.wattpit.app.core.CsvCodec;
import de.wattpit.app.core.Reading;
import de.wattpit.app.core.HistoryQuery;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.time.ZoneId;
import java.util.*;

final class HistoryDb extends SQLiteOpenHelper {
    private static final String[] NUMBERS = {"voltage","current","power","soc","solar","yieldWh","ttg","consumed","temperature","aux","load","state","error","alarm"};
    HistoryDb(Context context) { super(context, "wattpit-history.db", null, 2); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE readings (ts INTEGER NOT NULL, device TEXT NOT NULL, kind TEXT NOT NULL, origin TEXT NOT NULL, " +
                "voltage REAL, current REAL, power REAL, soc REAL, solar REAL, yieldWh REAL, ttg REAL, consumed REAL, temperature REAL, aux REAL, load REAL, " +
                "state INTEGER, error INTEGER, alarm INTEGER, UNIQUE(ts,device,origin))");
        db.execSQL("CREATE INDEX history_window ON readings(ts,kind,origin)");
        db.execSQL("CREATE INDEX history_device ON readings(device,ts)");
        createDaily(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { if (oldVersion < 2) createDaily(db); }

    private static void createDaily(SQLiteDatabase db) { db.execSQL("CREATE TABLE IF NOT EXISTS solar_daily(day TEXT PRIMARY KEY, payload TEXT NOT NULL)"); }
    JSONArray daily() throws Exception {
        JSONArray out = new JSONArray();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM solar_daily ORDER BY day DESC",null)) { while(c.moveToNext()) out.put(new JSONObject(c.getString(0))); }
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
        v.put("ts", r.timestamp); v.put("device", r.device); v.put("kind", r.kind); v.put("origin", r.origin);
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
        HistoryQuery query = HistoryQuery.build(from,to,metric,source);
        JSONArray result = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(query.sql, query.args)) {
            while (c.moveToNext()) {
                JSONObject p = new JSONObject();
                p.put("t", c.getLong(0)); p.put("last", c.getLong(1)); p.put("device", c.getString(2));
                p.put("kind", c.getString(3)); p.put("origin", c.getString(4)); p.put("v", c.getDouble(5));
                p.put("min", c.getDouble(6)); p.put("max", c.getDouble(7)); p.put("n", c.getLong(8));
                result.put(p);
            }
        }
        return result;
    }

    JSONObject stats() throws Exception {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*),MIN(ts),MAX(ts) FROM readings", null)) {
            c.moveToFirst();
            return new JSONObject().put("count", c.getLong(0)).put("from", c.isNull(1) ? 0 : c.getLong(1)).put("to", c.isNull(2) ? 0 : c.getLong(2));
        }
    }

    JSONObject dashboardData(long from,long to,String metric) throws Exception {
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

    Reading last(String device) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM readings WHERE device=? ORDER BY ts DESC LIMIT 1", new String[]{device})) {
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
        return new Object[]{r.voltage,r.current,r.power,r.soc,r.solar,r.yieldWh,r.ttg,r.consumed,r.temperature,r.aux,r.load,r.state,r.error,r.alarm};
    }
    static JSONObject json(Reading r) throws Exception {
        JSONObject out = new JSONObject().put("t", r.timestamp).put("device", r.device).put("kind", r.kind).put("origin", r.origin);
        Object[] values = values(r);
        for (int i = 0; i < NUMBERS.length; i++) out.put(NUMBERS[i], values[i] == null ? JSONObject.NULL : values[i]);
        return out;
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
        return r;
    }
    private static Double number(Cursor c, String name) { int i = c.getColumnIndexOrThrow(name); return c.isNull(i) ? null : c.getDouble(i); }
    private static Integer integer(Cursor c, String name) { int i = c.getColumnIndexOrThrow(name); return c.isNull(i) ? null : c.getInt(i); }
}
