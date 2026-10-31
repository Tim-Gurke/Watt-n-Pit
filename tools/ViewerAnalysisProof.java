package de.wattpit.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.wattpit.app.core.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Actual encrypted TCP, source SQLite and two independent native calculation replicas. */
public final class ViewerAnalysisProof {
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
    private static long scalar(SQLiteDatabase db,String sql){try(Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):0;}}
    private static void same(JSONObject a,JSONObject b,String label)throws Exception{
        for(String key:new String[]{"solar","balance","consumption","mobile"}){
            JSONObject x=a.getJSONObject(key),y=b.getJSONObject(key);boolean equal=x.getJSONArray("points").toString().equals(y.getJSONArray("points").toString())&&x.getJSONObject("summary").toString().equals(y.getJSONObject("summary").toString());if(!equal){System.out.println("DIFF "+x.getJSONObject("summary")+" / "+y.getJSONObject("summary"));System.out.println("POINTS "+x.getJSONArray("points").length()+" / "+y.getJSONArray("points").length());for(int i=0;i<Math.min(x.getJSONArray("points").length(),y.getJSONArray("points").length());i++)if(!x.getJSONArray("points").get(i).toString().equals(y.getJSONArray("points").get(i).toString())){System.out.println("FIRST "+x.getJSONArray("points").get(i)+" / "+y.getJSONArray("points").get(i));break;}}check(equal,label+" · "+key);
        }
    }
    private static ViewerAnalysis replica(LanSync s)throws Exception{Field f=LanSync.class.getDeclaredField("analysis");f.setAccessible(true);return (ViewerAnalysis)f.get(s);}
    private static String invitation(LanSync host)throws Exception{Field c=LanSync.class.getDeclaredField("config"),s=LanSync.class.getDeclaredField("server");c.setAccessible(true);s.setAccessible(true);return LanWire.encodeInvitation(new JSONObject(c.get(host).toString()).put("port",((java.net.ServerSocket)s.get(host)).getLocalPort()).put("addresses",new JSONArray().put("127.0.0.1")).toString());}
    private static void seed(Repository repo,long from,long to)throws Exception{
        SQLiteDatabase db=repo.db.getWritableDatabase();db.beginTransaction();try{
            for(long at=from;at<=to;at+=15000)for(int k=0;k<2;k++){
                Reading r=new Reading();r.kind=k==0?"solar":"shunt";r.device=k==0?"solar":"shunt";r.origin="live";r.timestamp=at;r.period=15;r.voltage=13.2;
                if(k==0){r.solar=140.0;r.state=3;}else{r.power=40.0;r.current=40/13.2;r.soc=72.0;}
                repo.db.insert(r);
            }
            for(int i=0;i<800;i++){Reading r=new Reading();r.kind="solar";r.device="csv-solar";r.origin="csv";r.timestamp=from+i*1000;r.period=1;r.solar=i==0?null:0.0;repo.db.insert(r);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public static void main(String[] args)throws Exception{
        Class.forName("org.sqlite.JDBC");Path root=Path.of(args[0]);TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
        LanSyncProof.TestContext central=new LanSyncProof.TestContext(root.resolve("central"));Repository source=LanSyncProof.repository(central);
        source.prefs.edit().putString("devices","[{\"kind\":\"solar\",\"mac\":\"solar\",\"name\":\"Solar\",\"key\":\"SECRET_SENTINEL\"},{\"kind\":\"shunt\",\"mac\":\"shunt\",\"name\":\"Shunt\"}]").putString("dashboard-v1","central-layout").commit();
        long to=(System.currentTimeMillis()/HistoryExchange.BLOCK-2)*HistoryExchange.BLOCK,from=to-7L*86400000;
        seed(source,from,to);check(source.db.stats().getLong("count")>75000,"the week fixture contains more than 75,000 actual original readings");
        LanSync host=LanSyncProof.sync(central),one=null,two=null;
        boolean success=false;try{
            host.create("Replica proof");host.startHost();String code=invitation(host);
            LanSyncProof.TestContext s20=new LanSyncProof.TestContext(root.resolve("s20"));Repository own1=LanSyncProof.repository(s20);own1.prefs.edit().putString("dashboard-v1","s20-layout").putString("equipment-v1","{\"version\":1,\"groups\":[]}").commit();
            Reading sentinel=new Reading();sentinel.kind="shunt";sentinel.device="private";sentinel.timestamp=to;sentinel.power=-999.0;own1.db.insert(sentinel);one=LanSyncProof.sync(s20);one.join(code);
            JSONArray query=new JSONArray().put(from).put(to).put("all").put(from).put(to);long started=System.nanoTime();
            JSONObject a=one.query("energyHistoryWindow",query);long cold=(System.nanoTime()-started)/1000000;
            ViewerAnalysis r1=replica(one);check(a.getJSONObject("analysis").getBoolean("local"),"the native response explicitly identifies viewer-side calculation");
            SolarPlanning reference=new SolarPlanning(central,source,x->{});JSONObject direct=reference.energyFlow(from,to,from,to,"all");same(direct,a,"central direct and viewer local energy are identical");
            JSONObject diag=one.analysisDiagnostics();check(diag.getLong("downloadedRows")>75000,"the first viewer fills its own bounded raw-data cache");
            JSONObject b=one.query("energyHistoryWindow",query);same(a,b,"a warm replica reuses the same results");check(one.analysisDiagnostics().getLong("downloadedRows")==0,"an unchanged week transfers zero original rows on the next query");
            LanSyncProof.TestContext s10=new LanSyncProof.TestContext(root.resolve("s10"));Repository own2=LanSyncProof.repository(s10);own2.prefs.edit().putString("dashboard-v1","s10-layout").commit();two=LanSyncProof.sync(s10);two.join(code);JSONObject c=two.query("energyHistoryWindow",query);same(a,c,"two independent viewer databases produce the same energy values");
            check(!r1.repo.db.getReadableDatabase().equals(replica(two).repo.db.getReadableDatabase()),"the displays have separate database instances");
            check(own1.db.last("private").power==-999&&own1.prefs.getString("dashboard-v1","").equals("s20-layout")&&own2.prefs.getString("dashboard-v1","").equals("s10-layout")&&source.prefs.getString("dashboard-v1","").equals("central-layout"),"original viewer measurements and all three local layouts survive replication");
            check(!AnalysisExchange.metadata(central,source,reference,from,to,host.controlRevision()).toString().contains("SECRET_SENTINEL"),"typed calculation metadata contains no Bluetooth device keys");
            long changed=from+3*86400000+120000;ContentValues v=new ContentValues();v.put("power",20.0);source.db.getWritableDatabase().update("readings",v,"ts=? AND kind='shunt'",new String[]{""+changed});
            JSONObject corrected=one.query("energyHistoryWindow",query);same(reference.energyFlow(from,to,from,to,"all"),corrected,"a corrected historical value invalidates its cached block");check(one.analysisDiagnostics().getLong("downloadedBlocks")==1,"a historical correction retransfers exactly one fifteen-minute block");
            source.db.getWritableDatabase().delete("readings","ts>=? AND ts<?",new String[]{""+changed/HistoryExchange.BLOCK*HistoryExchange.BLOCK,""+(changed/HistoryExchange.BLOCK+1)*HistoryExchange.BLOCK});
            JSONObject deleted=one.query("energyHistoryWindow",query);same(reference.energyFlow(from,to,from,to,"all"),deleted,"an emptied block removes its old cached measurements");
            check(one.analysisDiagnostics().getLong("downloadedBlocks")==1,"a deleted block arrives as a bounded tombstone");
            long block=from/HistoryExchange.BLOCK;JSONObject manifest=HistoryExchange.manifest(source.db,from,from+HistoryExchange.BLOCK);long revision=manifest.getJSONArray("blocks").getJSONArray(0).getLong(1);
            JSONObject first=HistoryExchange.page(source.db,block,revision,0);check(first.getJSONArray("rows").length()==HistoryExchange.PAGE&&!first.getBoolean("complete"),"a dense raw block is paginated at the fixed transport row bound");
            source.db.getWritableDatabase().update("readings",v,"ts=? AND kind='shunt'",new String[]{""+from});
            check(HistoryExchange.page(source.db,block,revision,first.getLong("cursor")).getBoolean("changed"),"a mutation between pages rejects the unfinished block");
            long cached=HistoryExchange.cachedVersion(r1.repo.db,block);boolean partial=false;try{HistoryExchange.replace(r1.repo.db,block,revision,100000,first.getJSONArray("rows"));}catch(IllegalArgumentException expected){partial=true;}check(partial&&HistoryExchange.cachedVersion(r1.repo.db,block)==cached,"partial block data never replaces a previously complete replica block");
            // An archive-time repair uses a temporary negative key and must invalidate both days.
            long movedAt=from+86400000,moveTo=movedAt+2*86400000+123;
            source.db.getWritableDatabase().execSQL("UPDATE readings SET ts=-rowid-1 WHERE ts="+movedAt+" AND kind='solar'");
            source.db.getWritableDatabase().execSQL("UPDATE readings SET ts="+moveTo+" WHERE ts<0");
            same(reference.energyFlow(from,to,from,to,"all"),one.query("energyHistoryWindow",query),"archive timestamp repairs reach both the old and new cached blocks");
            JSONObject power=one.query("history",new JSONArray().put(from).put(to).put("power").put("all"));check(power.getJSONArray("points").toString().equals(source.db.query(from,to,"power","all").toString()),"weekly power is also calculated locally from the same raw readings");
            Field p=LanSync.class.getDeclaredField("planning");p.setAccessible(true);SolarPlanning hostPlan=(SolarPlanning)p.get(host);
            check(scalar(hostPlan.getReadableDatabase(),"SELECT COUNT(*) FROM observations")==0,"serving both viewers and polling bundles never starts central history learning");
            JSONObject k=one.analysisKnowledge(null);check(k.has("observations")&&k.getString("source").equals(RollerLedger.source(source.prefs)),"the viewer builds learning observations for the central source");
            check(scalar(hostPlan.getReadableDatabase(),"SELECT COUNT(*) FROM observations")==0,"learning on a viewer leaves the central derived ledger untouched");
            check(scalar(r1.planning.getReadableDatabase(),"SELECT COUNT(*) FROM observations")>0,"learning results are stored in the viewer's separate planning cache");
            JSONObject knowledge2=two.analysisKnowledge(null);check(k.getJSONArray("observations").toString().equals(knowledge2.getJSONArray("observations").toString()),"two viewers derive identical learning rows from authoritative context and presence");
            one.control("settings",new JSONArray().put("{\"capacity\":123,\"sampleSeconds\":20}"),one.controlRevision());check(source.prefs.getFloat("capacity",0)==123,"a viewer can still change acknowledged central settings while the central display is inactive");
            Field remote=LanSync.class.getDeclaredField("remote");remote.setAccessible(true);JSONObject old=new JSONObject(remote.get(one).toString());old.remove("analysisProtocol");remote.set(one,old);boolean upgrade=false;try{one.query("history",new JSONArray().put(from).put(to).put("power").put("all"));}catch(IOException e){upgrade=e.getMessage().contains("Hauptgerät");}check(upgrade,"an older central protocol produces an explicit update instruction");remote.set(one,new JSONObject(remote.get(two).toString()));
            String previousEpoch=r1.repo.prefs.getString("analysis.epoch","");source.db.getWritableDatabase().execSQL("UPDATE analysis_meta SET epoch='replacement-database'");one.query("energyHistoryWindow",query);check(!previousEpoch.equals(r1.repo.prefs.getString("analysis.epoch","")),"a replaced source database resets only its dedicated replica");
            check(own1.db.last("private").power==-999,"a source reset cannot erase the viewer's independent original data");
            host.stopHost();boolean offline=false;try{one.query("energyHistoryWindow",query);}catch(IOException e){offline=true;}check(offline&&r1.repo.db.stats().getLong("count")>0,"a disconnected central is an error with retained cache, never a fabricated empty history");
            reference.shutdown();System.out.println("INFO cold_week_ms="+cold+" raw_rows="+diag.getLong("downloadedRows"));success=true;
        }catch(Throwable failure){failure.printStackTrace();}finally{if(two!=null)LanSyncProof.close(two);if(one!=null)LanSyncProof.close(one);LanSyncProof.close(host);System.exit(success?0:1);}
    }
}
