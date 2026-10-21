package android.database.sqlite;

import android.content.Context;
import android.database.Cursor;

/** JVM test adapter only; production still uses Android's SQLiteOpenHelper. */
public abstract class SQLiteOpenHelper implements AutoCloseable {
    private SQLiteDatabase db;
    private final int version;
    public SQLiteOpenHelper(Context context,String name,SQLiteDatabase.CursorFactory factory,int version){this.version=version;}
    public void setWriteAheadLoggingEnabled(boolean enabled){ }
    public synchronized SQLiteDatabase getWritableDatabase(){
        if(db==null){
            db=new SQLiteDatabase(System.getProperty("wattpit.test.database"));int previous;
            try(Cursor c=db.rawQuery("PRAGMA user_version",null)){c.moveToFirst();previous=c.getInt(0);}
            if(previous==0)onCreate(db);else if(previous<version)onUpgrade(db,previous,version);
            db.execSQL("PRAGMA user_version="+version);
        }
        return db;
    }
    public SQLiteDatabase getReadableDatabase(){return getWritableDatabase();}
    public abstract void onCreate(SQLiteDatabase db);
    public abstract void onUpgrade(SQLiteDatabase db,int previous,int version);
    public void close(){if(db!=null)db.close();}
}
