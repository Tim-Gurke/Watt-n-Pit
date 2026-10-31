package de.wattpit.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Original measurements in bounded, revisioned fifteen-minute blocks. No plot or energy math. */
final class HistoryExchange {
    static final long BLOCK=900000L;
    static final int PAGE=512;
    static final String[] COLUMNS=(de.wattpit.app.core.HistorySchema.COLUMNS+",archiveCounter,archiveOriginalTs").split(",");
    static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE analysis_meta(epoch TEXT NOT NULL,revision INTEGER NOT NULL)");
        db.execSQL("INSERT INTO analysis_meta VALUES(?,1)",new Object[]{UUID.randomUUID().toString()});
        db.execSQL("CREATE TABLE analysis_blocks(block INTEGER PRIMARY KEY,revision INTEGER NOT NULL,n INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX analysis_changes ON analysis_blocks(revision,block)");
        db.execSQL("INSERT INTO analysis_blocks SELECT ts/900000,1,COUNT(*) FROM readings WHERE ts>=0 GROUP BY ts/900000");
        String bump="UPDATE analysis_meta SET revision=revision+1;";
        String old="INSERT OR IGNORE INTO analysis_blocks VALUES(OLD.ts/900000,0,0);UPDATE analysis_blocks SET revision=(SELECT revision FROM analysis_meta),n=n-1 WHERE block=OLD.ts/900000 AND OLD.ts>=0;";
        String next="INSERT OR IGNORE INTO analysis_blocks VALUES(NEW.ts/900000,0,0);UPDATE analysis_blocks SET revision=(SELECT revision FROM analysis_meta),n=n+1 WHERE block=NEW.ts/900000 AND NEW.ts>=0;";
        db.execSQL("CREATE TRIGGER analysis_insert AFTER INSERT ON readings WHEN NEW.ts>=0 BEGIN "+bump+next+" END");
        db.execSQL("CREATE TRIGGER analysis_delete AFTER DELETE ON readings WHEN OLD.ts>=0 BEGIN "+bump+old+" END");
        db.execSQL("CREATE TRIGGER analysis_update AFTER UPDATE ON readings BEGIN "+bump+old+next+" END");
        db.execSQL("CREATE TABLE replica_blocks(block INTEGER PRIMARY KEY,revision INTEGER NOT NULL,n INTEGER NOT NULL)");
    }
    static JSONObject manifest(HistoryDb history,long from,long to)throws Exception{
        if(from<0||to<=from||to-from>376L*86400000||to>System.currentTimeMillis()+86400000)throw new IllegalArgumentException("Ungültiger Datenzeitraum.");
        SQLiteDatabase db=history.getReadableDatabase();JSONArray blocks=new JSONArray();String epoch="";long revision=0;
        try(Cursor c=db.rawQuery("SELECT epoch,revision FROM analysis_meta",null)){if(c.moveToFirst()){epoch=c.getString(0);revision=c.getLong(1);}}
        try(Cursor c=db.rawQuery("SELECT block,revision,n FROM analysis_blocks WHERE block>=? AND block<=? ORDER BY block",new String[]{""+(from/BLOCK),""+(to/BLOCK)})){
            while(c.moveToNext())blocks.put(new JSONArray().put(c.getLong(0)).put(c.getLong(1)).put(c.getLong(2)));
        }
        return new JSONObject().put("protocol",1).put("epoch",epoch).put("revision",revision).put("blocks",blocks).put("at",System.currentTimeMillis());
    }
    static long[] version(SQLiteDatabase db,long block){try(Cursor c=db.rawQuery("SELECT revision,n FROM analysis_blocks WHERE block=?",new String[]{""+block})){return c.moveToFirst()?new long[]{c.getLong(0),c.getLong(1)}:new long[]{0,0};}}
    static JSONObject changes(HistoryDb history,long after)throws Exception{
        SQLiteDatabase db=history.getReadableDatabase();long first=0,revision=0;String epoch="";
        try(Cursor c=db.rawQuery("SELECT epoch,revision FROM analysis_meta",null)){if(c.moveToFirst()){epoch=c.getString(0);revision=c.getLong(1);}}
        try(Cursor c=db.rawQuery("SELECT MIN(block) FROM analysis_blocks WHERE revision>? AND block>=?",new String[]{""+after,""+(LearningWindow.since(System.currentTimeMillis())/BLOCK)})){if(c.moveToFirst()&&!c.isNull(0))first=c.getLong(0)*BLOCK;}
        return new JSONObject().put("epoch",epoch).put("revision",revision).put("changedFrom",first);
    }
    static JSONObject page(HistoryDb history,long block,long expected,long after)throws Exception{
        if(block<0||block>(System.currentTimeMillis()+86400000)/BLOCK||expected<0||after<0)throw new IllegalArgumentException("Ungültiger Datenblock.");
        SQLiteDatabase db=history.getReadableDatabase();long[] before=version(db,block);
        if(before[0]!=expected)return new JSONObject().put("changed",true);
        JSONArray rows=new JSONArray();long cursor=after;
        try(Cursor c=db.rawQuery("SELECT rowid,"+String.join(",",COLUMNS)+" FROM readings WHERE ts>=? AND ts<? AND rowid>? ORDER BY rowid LIMIT "+PAGE,new String[]{""+(block*BLOCK),""+((block+1)*BLOCK),""+after})){
            while(c.moveToNext()){
                cursor=c.getLong(0);JSONArray row=new JSONArray();
                for(int i=0;i<COLUMNS.length;i++){
                    if(c.isNull(i+1))row.put(JSONObject.NULL);
                    else if(i>=1&&i<=3)row.put(c.getString(i+1));
                    else if(i==0||i>=20)row.put(c.getLong(i+1));
                    else{double v=c.getDouble(i+1);row.put(Double.isFinite(v)?v:JSONObject.NULL);}
                }rows.put(row);
            }
        }
        long[] end=version(db,block);
        return new JSONObject().put("changed",end[0]!=expected).put("block",block).put("revision",expected).put("n",before[1]).put("rows",rows).put("cursor",cursor).put("complete",rows.length()<PAGE);
    }
    static long cachedVersion(HistoryDb history,long block){try(Cursor c=history.getReadableDatabase().rawQuery("SELECT revision FROM replica_blocks WHERE block=?",new String[]{""+block})){return c.moveToFirst()?c.getLong(0):-1;}}
    static void replace(HistoryDb history,long block,long revision,long count,JSONArray rows)throws Exception{
        if(rows.length()!=count||count>100000)throw new IllegalArgumentException("Datenblock ist noch unvollständig.");
        List<ContentValues> checked=new ArrayList<>();
        for(int r=0;r<rows.length();r++){
            JSONArray row=rows.getJSONArray(r);if(row.length()!=COLUMNS.length||row.getLong(0)/BLOCK!=block)throw new IllegalArgumentException("Ungültige Messwertzeile.");
            if(!Arrays.asList("solar","shunt").contains(row.getString(2))||!Arrays.asList("live","archive","csv").contains(row.getString(3)))throw new IllegalArgumentException("Unbekannte Messwertquelle.");
            ContentValues values=new ContentValues();for(int i=0;i<COLUMNS.length;i++){
                if(row.isNull(i))values.putNull(COLUMNS[i]);
                else if(i>=1&&i<=3)values.put(COLUMNS[i],row.getString(i));
                else if(i==0||i>=20)values.put(COLUMNS[i],row.getLong(i));
                else{double v=row.getDouble(i);if(!Double.isFinite(v))throw new IllegalArgumentException("Ungültiger Messwert.");values.put(COLUMNS[i],v);}
            }checked.add(values);
        }
        SQLiteDatabase db=history.getWritableDatabase();db.beginTransaction();try{
            db.delete("readings","ts>=? AND ts<?",new String[]{""+(block*BLOCK),""+((block+1)*BLOCK)});
            for(ContentValues values:checked)db.insertOrThrow("readings",null,values);
            ContentValues v=new ContentValues();v.put("block",block);v.put("revision",revision);v.put("n",count);db.insertWithOnConflict("replica_blocks",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}history.clearDisplayPrior();
    }
    static void reset(HistoryDb history){SQLiteDatabase db=history.getWritableDatabase();db.beginTransaction();try{db.delete("readings",null,null);db.delete("replica_blocks",null,null);db.setTransactionSuccessful();}finally{db.endTransaction();}history.clearDisplayPrior();}
    private HistoryExchange(){}
}
