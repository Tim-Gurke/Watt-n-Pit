package android.database.sqlite;

import android.content.Context;
import android.database.Cursor;

/** JVM test adapter only; production still uses Android's SQLiteOpenHelper. */
public abstract class SQLiteOpenHelper implements AutoCloseable {
    private SQLiteDatabase db;
    private final int version;
    private final String name;
    private final Context context;
    private boolean wal;
    public SQLiteOpenHelper(Context context,String name,SQLiteDatabase.CursorFactory factory,int version){this.version=version;this.name=name;this.context=context;}
    public void setWriteAheadLoggingEnabled(boolean enabled){wal=enabled;}
    public synchronized SQLiteDatabase getWritableDatabase(){
        if(db==null){
            String path=System.getProperty("wattpit.test.database");if(Boolean.getBoolean("wattpit.test.namedDatabases")||name.startsWith("wattpit-view-"))path=directory(path).resolve(name).toString();
            db=new SQLiteDatabase(path);if(wal)db.execSQL("PRAGMA journal_mode=WAL");int previous;
            try(Cursor c=db.rawQuery("PRAGMA user_version",null)){c.moveToFirst();previous=c.getInt(0);}
            if(previous==0)onCreate(db);else if(previous<version)onUpgrade(db,previous,version);
            db.execSQL("PRAGMA user_version="+version);
        }
        return db;
    }
    private java.nio.file.Path directory(String path){try{if(context!=null)return context.getFilesDir().toPath();}catch(RuntimeException ignored){}return java.nio.file.Path.of(path).getParent();}
    public SQLiteDatabase getReadableDatabase(){return getWritableDatabase();}
    public abstract void onCreate(SQLiteDatabase db);
    public abstract void onUpgrade(SQLiteDatabase db,int previous,int version);
    public void close(){if(db!=null)db.close();}
}
