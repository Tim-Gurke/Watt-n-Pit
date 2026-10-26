package de.wattpit.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.wattpit.app.core.*;
import org.json.*;
import java.util.*;

/** Actual native migration, restart and rollback proof; all samples are synthetic. */
public final class ArchiveClockProof {
    private static final long EPOCH=1700000000000L;
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);System.out.println("PASS "+message);}
    private static long count(SQLiteDatabase db,String where){try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM readings WHERE "+where,null)){c.moveToFirst();return c.getLong(0);}}
    private static ArchiveCodec.Clock clock(){ArchiveCodec.Clock c=new ArchiveCodec.Clock();byte[] a=new byte[8];System.arraycopy(ArchiveCodec.le(EPOCH/1000,4),0,a,4,4);c.anchors(a);return c;}
    private static ArchiveTimeReference read(long counter,long at){return new ArchiveTimeReference(counter,at-50,at+50,10,110);}
    private static String plan(ArchiveCodec.Clock c,long uptime,long at)throws Exception{return new JSONObject().put("kind","solar").put("uptime",uptime).put("at",at).put("anchors",Base64.getEncoder().encodeToString(c.anchorBytes())).put("reboots",Base64.getEncoder().encodeToString(c.rebootBytes())).toString();}
    private static void sample(HistoryDb history,String device,long counter,String origin){Reading r=new Reading();r.timestamp=EPOCH+counter*1000;r.device=device;r.kind="solar";r.origin=origin;r.period=1;r.solar=(double)counter;r.voltage=13.4;r.current=2.5;history.insert(r);}
    public static void run(Repository repo,String phase)throws Exception{
        HistoryDb history=repo.db;SQLiteDatabase db=history.getWritableDatabase();
        if(phase.equals("clock_alignment_write")){
            ArchiveCodec.Clock old=clock(),next=old.copy();long at=EPOCH+1230000;String previous=plan(old,1200,at);
            repo.prefs.edit().putString("archivePlan.clock-align",previous).commit();
            for(int i=0;i<=120;i++){sample(history,"clock-align",i,"archive");sample(history,"clock-align",i,"live");}
            sample(history,"clock-unrelated",60,"archive");
            db.execSQL("INSERT INTO archive_coverage VALUES('clock-align',60554,1,?,?)",new Object[]{EPOCH,EPOCH+120000});
            db.execSQL("INSERT INTO archive_coverage VALUES('clock-unrelated',60554,1,?,?)",new Object[]{EPOCH,EPOCH+120000});
            int moved=repo.alignArchiveClock("clock-align","solar",read(1200,at),next,previous,old,()->{});
            require(moved==121&&count(db,"device='clock-align' AND origin='archive'")==121,"alignment retains every archive sample while correcting an overlapping 30-second timestamp shift");
            require(count(db,"device='clock-align' AND origin='archive' AND ts=archiveOriginalTs+30000 AND archiveCounter=(archiveOriginalTs-"+EPOCH+")/1000 AND voltage=13.4 AND current=2.5 AND solar=archiveCounter")==121,"raw counters, original timestamps and every measured field survive the correction");
            require(count(db,"device='clock-align' AND origin='live' AND ts="+EPOCH+"+solar*1000 AND archiveCounter IS NULL AND archiveOriginalTs IS NULL")==121&&count(db,"device='clock-unrelated' AND ts="+(EPOCH+60000))==1,"live timestamps and another device are unchanged");
            try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM archive_coverage WHERE device='clock-align'",null)){c.moveToFirst();require(c.getInt(0)==0,"only the corrected device's stale acknowledgements are invalidated for a fresh gap check");}
            try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM archive_coverage WHERE device='clock-unrelated'",null)){c.moveToFirst();require(c.getInt(0)==1,"another device's archive acknowledgements remain intact");}
            require(repo.archiveNeedsData(new JSONObject().put("mac","clock-align").put("kind","solar"),at),"a stale previous archive plan cannot certify the corrected time range");
            Map<Integer,List<ArchiveCodec.Level>> levels=new HashMap<>();levels.put(0,Arrays.asList(new ArchiveCodec.Level(121,1,120)));
            Map<Integer,ArchiveCodec.Field> fields=new HashMap<>();fields.put(0,ArchiveCodec.field(0xec8a,-1));Map<Integer,Integer> registers=new HashMap<>();registers.put(0,0xec8a);
            repo.saveArchivePlan("clock-align","solar",1200,at,next,levels,fields,registers);
            require(!repo.archiveNeedsData(new JSONObject().put("mac","clock-align").put("kind","solar"),at),"the corrected plan recognizes every stored point without repeated measurement requests");
            long revision=history.archiveClockState("clock-align").getLong("revision");
            require(repo.alignArchiveClock("clock-align","solar",read(1201,at+2000),next,previous,old,()->{})==0&&history.archiveClockState("clock-align").getLong("revision")==revision,"a quantized follow-up clock read causes neither another migration nor another learning revision");
            history.archiveBlock("clock-align","solar","solar",0xec8a,1,Arrays.asList(new long[]{next.millis(125),1,125}),Arrays.asList(125.0),Collections.emptyList());
            require(count(db,"device='clock-align' AND origin='archive' AND archiveCounter=125 AND archiveOriginalTs IS NULL")==1,"new archive blocks retain their original device counter immediately");
        }else if(phase.equals("clock_alignment_resume")){
            repo.prefs.edit().putString("archiveClock.clock-align","{}").commit();ArchiveCodec.Clock c=repo.archiveClock("clock-align",1202);
            require(c.millis(60)==EPOCH+90000&&c.counter(EPOCH+90000)==60,"a fresh process uses the committed database mapping even with a stale preference cache");
            require(count(db,"device='clock-align' AND origin='archive' AND archiveOriginalTs IS NOT NULL")==121,"restart retains all original archive timestamps");
            require(history.archiveClockState("clock-align").getLong("revision")==1,"restart does not silently advance the learning revision");
        }else if(phase.equals("clock_alignment_failure")){
            sample(history,"clock-failure",0,"archive");sample(history,"clock-failure",1,"archive");
            db.execSQL("INSERT INTO archive_coverage VALUES('clock-failure',60554,1,?,?)",new Object[]{EPOCH,EPOCH+1000});
            db.execSQL("CREATE TRIGGER clock_failure BEFORE UPDATE OF ts ON readings WHEN NEW.device='clock-failure' AND NEW.ts>=0 BEGIN SELECT RAISE(ABORT,'injected clock correction failure'); END");
            ArchiveCodec.Clock c=clock();boolean failed=false;
            try{repo.alignArchiveClock("clock-failure","solar",read(1200,EPOCH+1230000),c.copy(),plan(c,1200,EPOCH+1230000),c,()->{});}catch(RuntimeException expected){failed=true;}
            require(failed&&count(db,"device='clock-failure' AND ts>=0 AND archiveCounter IS NULL AND archiveOriginalTs IS NULL")==2,"an interrupted correction rolls back every temporary timestamp and counter");
            require(history.archiveClockState("clock-failure").length()==0,"failed correction cannot commit a new clock mapping");
            try(Cursor r=db.rawQuery("SELECT COUNT(*) FROM archive_coverage WHERE device='clock-failure'",null)){r.moveToFirst();require(r.getInt(0)==1,"failed correction retains previous archive acknowledgements");}
            db.execSQL("DROP TRIGGER clock_failure");
            require(repo.alignArchiveClock("clock-failure","solar",read(1200,EPOCH+1230000),c.copy(),plan(c,1200,EPOCH+1230000),c,()->{})==2,"the same correction succeeds after restart without deleting measurements");
            db.execSQL("INSERT INTO archive_coverage VALUES('clock-na-only',60554,1,?,?)",new Object[]{EPOCH,EPOCH+1000});
            repo.alignArchiveClock("clock-na-only","solar",read(1200,EPOCH+1230000),c.copy(),plan(c,1200,EPOCH+1230000),c,()->{});
            try(Cursor r=db.rawQuery("SELECT COUNT(*) FROM archive_coverage WHERE device='clock-na-only'",null)){r.moveToFirst();require(r.getInt(0)==0,"clock correction invalidates stale unavailable-value acknowledgements even without any stored measurement");}
        }else if(phase.equals("clock_alignment_reset")){
            ArchiveCodec.Clock fresh=new ArchiveCodec.Clock();long at=EPOCH+86400000;
            require(repo.alignArchiveClock("clock-align","solar",read(100,at),fresh,"{}",fresh.copy(),()->{})==0,"a device-counter reset preserves the already corrected previous generation");
            history.archiveBlock("clock-align","solar","solar",0xec8a,1,Arrays.asList(new long[]{fresh.millis(100),1,100}),Arrays.asList(10.0),Collections.emptyList());
            ArchiveCodec.Clock advanced=fresh.copy();repo.alignArchiveClock("clock-align","solar",read(200,at+104000),advanced,"{}",fresh,()->{});
            require(count(db,"device='clock-align' AND origin='archive' AND archiveOriginalTs IS NOT NULL")==122&&count(db,"device='clock-align' AND origin='archive' AND ts="+(EPOCH+90000))==1,"a later correction moves the new counter generation while retaining older archive times");
        }
    }
}
