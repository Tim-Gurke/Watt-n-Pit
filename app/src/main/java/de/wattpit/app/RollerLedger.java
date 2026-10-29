package de.wattpit.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Finite observed charger-input estimates, separate from measured plant energy. */
final class RollerLedger extends SQLiteOpenHelper {
    private static RollerLedger instance;
    static synchronized RollerLedger get(Context c){if(instance==null)instance=new RollerLedger(c.getApplicationContext());return instance;}
    private long from,lastTo,session,lastPrune;
    private double wh;
    private String source="",mac="";
    private RollerLedger(Context c){super(c,"wattpit-roller-input.db",null,1);setWriteAheadLoggingEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE input_intervals(source TEXT NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,wh REAL NOT NULL,mac TEXT NOT NULL,session INTEGER NOT NULL,PRIMARY KEY(source,start))");db.execSQL("CREATE INDEX input_end ON input_intervals(source,end)");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){}
    static String source(SharedPreferences prefs){try{
        String[] selected={"",""};JSONArray devices=new JSONArray(prefs.getString("devices","[]"));
        for(int i=0;i<devices.length();i++){JSONObject d=devices.getJSONObject(i);if(d.optString("kind").equals("solar"))selected[0]=d.optString("mac");if(d.optString("kind").equals("shunt"))selected[1]=d.optString("mac");}
        StringBuilder result=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(String.join("|",selected).getBytes(StandardCharsets.UTF_8)))result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return result.toString();
    }catch(Exception e){return "";}}
    synchronized void add(String plant,String address,long started,long a,long b,double value){
        if(plant.isEmpty()||b<=a||b-a>10000||!Double.isFinite(value)||value<0)return;
        if(!plant.equals(source)||!address.equals(mac)||started!=session||lastTo!=a||b-from>60000){from=a;wh=0;}
        source=plant;mac=address;session=started;lastTo=b;wh+=value;
        ContentValues row=new ContentValues();row.put("source",plant);row.put("start",from);row.put("end",b);row.put("wh",wh);row.put("mac",address);row.put("session",started);
        SQLiteDatabase db=getWritableDatabase();db.insertWithOnConflict("input_intervals",null,row,SQLiteDatabase.CONFLICT_REPLACE);
        if(b-lastPrune>3600000){db.delete("input_intervals","end<?",new String[]{""+(LearningWindow.since(b))});lastPrune=b;}
    }
    synchronized JSONArray read(String plant,long a,long b)throws JSONException{
        JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT start,end,wh FROM input_intervals WHERE source=? AND end>? AND start<? ORDER BY start",new String[]{plant,""+a,""+b})){
            while(c.moveToNext())out.put(new JSONObject().put("from",c.getLong(0)).put("to",c.getLong(1)).put("wh",c.getDouble(2)));
        }return out;
    }
    synchronized long[] range(String plant){try(Cursor c=getReadableDatabase().rawQuery("SELECT MIN(start),MAX(end) FROM input_intervals WHERE source=?",new String[]{plant})){if(c.moveToFirst()&&!c.isNull(0))return new long[]{c.getLong(0),c.getLong(1)};}return new long[]{0,0};}
    static Double estimate(JSONArray rows,long a,long b)throws JSONException{
        long cursor=a;double value=0;
        for(int i=0;i<rows.length()&&cursor<b;i++){JSONObject row=rows.getJSONObject(i);long from=row.getLong("from"),to=row.getLong("to");if(to<=cursor)continue;if(from>cursor)return null;long end=Math.min(b,to);if(end<=cursor||to<=from)return null;
            double wh=row.optDouble("wh",Double.NaN);if(!Double.isFinite(wh)||wh<0)return null;value+=wh*(end-cursor)/(to-from);cursor=end;
        }return cursor==b?value:null;
    }
}
