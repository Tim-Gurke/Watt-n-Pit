package de.wattpit.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.wattpit.app.core.*;
import org.json.*;
import java.nio.file.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;

/** Runs the actual HistoryDb and Repository on the JDBC test adapter, with no APK or radio. */
public final class ArchiveStorageProof {
    private static void require(boolean condition,String name){if(!condition)throw new AssertionError(name);System.out.println("PASS "+name);}
    private static SharedPreferences preferences(Path path)throws Exception{
        Properties values=new Properties();if(Files.exists(path))try(InputStream in=Files.newInputStream(path)){values.load(in);}
        return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,m,a)->{
            if(m.getName().equals("edit")){
                Map<String,String> changes=new HashMap<>();Set<String> removed=new HashSet<>();
                return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(editor,method,args)->{
                    String name=method.getName();
                    if(name.startsWith("put")){removed.remove((String)args[0]);changes.put((String)args[0],String.valueOf(args[1]));return editor;}
                    if(name.equals("remove")){changes.remove((String)args[0]);removed.add((String)args[0]);return editor;}
                    if(name.equals("apply")||name.equals("commit")){removed.forEach(values::remove);values.putAll(changes);try(OutputStream out=Files.newOutputStream(path)){values.store(out,"Test preferences");}return name.equals("commit")?true:null;}
                    throw new UnsupportedOperationException(name);
                });
            }
            String key=(String)a[0],v=values.getProperty(key);
            switch(m.getName()){
                case "getString":return v==null?a[1]:v;
                case "getBoolean":return v==null?a[1]:Boolean.parseBoolean(v);
                case "getInt":return v==null?a[1]:Integer.parseInt(v);
                case "getFloat":return v==null?a[1]:Float.parseFloat(v);
                case "getLong":return v==null?a[1]:Long.parseLong(v);
                case "contains":return v!=null;
                default:throw new UnsupportedOperationException(m.getName());
            }
        });
    }
    private static final class TestContext extends ContextWrapper {
        private final SharedPreferences preferences;
        TestContext(Path path)throws Exception{super(null);preferences=preferences(path);}
        @Override public Context getApplicationContext(){return this;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return preferences;}
    }
    private static ArchiveCodec.Clock clock(long epoch){ArchiveCodec.Clock c=new ArchiveCodec.Clock();byte[] anchor=new byte[8];System.arraycopy(ArchiveCodec.le(epoch/1000,4),0,anchor,4,4);c.anchors(anchor);return c;}
    private static long count(SQLiteDatabase db,String table,String where){try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table+" WHERE "+where,null)){c.moveToFirst();return c.getLong(0);}}
    private static void snapshot(Repository repo,String device,String kind,long end,long at,ArchiveCodec.Clock clock,int... registers)throws Exception{
        Map<Integer,List<ArchiveCodec.Level>> levels=new LinkedHashMap<>();Map<Integer,ArchiveCodec.Field> fields=new LinkedHashMap<>();Map<Integer,Integer> ids=new LinkedHashMap<>();
        int[] periods={1,30,300,1800},capacities={121,241,289,1441};
        for(int i=0;i<registers.length;i++){
            List<ArchiveCodec.Level> tier=new ArrayList<>();for(int j=0;j<periods.length;j++)tier.add(new ArchiveCodec.Level(capacities[j],periods[j],end-end%periods[j]));
            levels.put(i,tier);fields.put(i,ArchiveCodec.field(registers[i],-1));ids.put(i,registers[i]);
        }
        repo.saveArchivePlan(device,kind,end,at,clock,levels,fields,ids);
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("wattpit.test.database",args[0]);Class.forName("org.sqlite.JDBC");
        Repository repo=Repository.get(new TestContext(Path.of(args[0]+".prefs")));HistoryDb history=repo.db;SQLiteDatabase db=history.getWritableDatabase();
        long epoch=1700000000000L;ArchiveCodec.Clock c=clock(epoch);
        if(args[1].equals("atomic_write")){
            history.archiveBlock("atomic","solar","solar",0xec8a,30,Arrays.asList(new long[]{epoch+10000,30}),Arrays.asList(10.0),Arrays.asList(new long[]{epoch+10000,epoch+10000}));
            db.execSQL("CREATE TRIGGER fail_ack BEFORE INSERT ON archive_coverage BEGIN SELECT RAISE(ABORT,'injected checkpoint failure'); END");
            boolean failed=false;
            try{history.archiveBlock("atomic","solar","solar",0xec8a,30,Arrays.asList(new long[]{epoch+40000,30}),Arrays.asList(40.0),Arrays.asList(new long[]{epoch+40000,epoch+70000}));}catch(RuntimeException e){failed=true;}
            require(failed,"injected failure reached the real HistoryDb checkpoint write");
            require(count(db,"readings","device='atomic'")==1,"failed checkpoint rolls back its measurement");
            try(Cursor r=db.rawQuery("SELECT start,end FROM archive_coverage WHERE device='atomic'",null)){require(r.moveToFirst()&&r.getLong(0)==epoch+10000&&r.getLong(1)==epoch+10000,"rollback preserves the previous confirmation");}
            db.execSQL("DROP TRIGGER fail_ack");
            history.archiveBlock("atomic","solar","solar",0xec8a,30,Arrays.asList(new long[]{epoch+40000,30}),Arrays.asList(40.0),Arrays.asList(new long[]{epoch+40000,epoch+70000}));
            require(count(db,"readings","device='atomic'")==2,"successful block keeps both measurements");
            repo.archiveAttemptStarted("atomic");
        }else if(args[1].equals("atomic_resume")){
            List<ArchiveCodec.Level> levels=Arrays.asList(new ArchiveCodec.Level(3,30,70));
            ArchiveSyncPlan.Availability local=history.archiveAvailability("atomic","solar",0xec8a,"solar",levels,c,epoch+71000,()->{});
            require(!ArchiveSyncPlan.needsData(levels,c,local),"fresh JVM resumes with zero repeated valid or unavailable points");
            require(count(db,"readings","device='atomic' AND ts="+(epoch+70000))==0,"confirmed unavailable point creates no fabricated reading");
            require(repo.prefs.getBoolean("archivePending.atomic",false),"an interrupted attempt remains pending after a fresh JVM");
        }else if(args[1].equals("config_write")){
            for(String mode:new String[]{"sunrise","today","last6h","last24h","yesterday","week","month"}){
                repo.saveSettings(new JSONObject().put("miniRange",mode));
                require(repo.prefs.getString("miniRange","").equals(mode),"native preferences accept and store overview range "+mode);
            }
            JSONObject backup=new JSONObject().put("format","wattnpit-settings").put("version",1)
                .put("settings",new JSONObject().put("theme","dark").put("palette","classic").put("miniRange","month").put("capacity",100).put("sampleSeconds",10))
                .put("dashboard",new JSONObject().put("version",1).put("blockOrder",new JSONArray("[\"tiles\",\"history\",\"record\"]")).put("tiles",new JSONArray()).put("historyHeight",new JSONObject().put("compact",460).put("wide",640)))
                .put("equipment",new JSONObject().put("version",1).put("groups",new JSONArray()));
            repo.importConfiguration(backup.toString());
            require(new JSONObject(repo.prefs.getString("dashboard-v1","{}")).getJSONObject("historyHeight").getInt("wide")==640,"native backup import preserves the separate history heights");
            backup.getJSONObject("dashboard").getJSONObject("historyHeight").put("compact",1).put("wide",5000);
            JSONObject heights=ConfigurationBackup.normalize(backup.toString()).getJSONObject("dashboard").getJSONObject("historyHeight");
            require(heights.getInt("compact")==300&&heights.getInt("wide")==1000,"native backup import clamps history height to safe bounds");
            backup.getJSONObject("settings").put("miniRange","unknown");boolean rejected=false;
            try{repo.importConfiguration(backup.toString());}catch(IllegalArgumentException expected){rejected=true;}
            require(rejected&&repo.prefs.getString("miniRange","").equals("month"),"invalid overview range cannot partially replace saved settings");
        }else if(args[1].equals("config_resume")){
            JSONObject heights=new JSONObject(repo.prefs.getString("dashboard-v1","{}")).getJSONObject("historyHeight");
            require(repo.prefs.getString("miniRange","").equals("month")&&heights.getInt("compact")==460&&heights.getInt("wide")==640,"fresh JVM retains overview range and both history heights");
        }else if(args[1].equals("metadata_resume")){
            JSONArray configs=repo.configs();long now=System.currentTimeMillis();
            require(repo.archiveNeedsData(configs.getJSONObject(0),now),"cached device layout survives restart and finds the remaining gap");
            require(!repo.archiveNeedsData(configs.getJSONObject(1),now),"cached device layout survives restart without restarting a complete device");
        }else{
            long end=21*86400L,now=System.currentTimeMillis()/1000*1000,base=now-end*1000;c=clock(base);
            JSONObject solar=new JSONObject().put("mac","live-solar").put("kind","solar"),shunt=new JSONObject().put("mac","live-shunt").put("kind","shunt");
            repo.prefs.edit().putString("devices",new JSONArray().put(solar).put(shunt).toString()).putInt("sampleSeconds",60).commit();
            db.beginTransaction();try{
                for(long t=0;t<=end+300;t+=60){
                    Reading s=new Reading();s.timestamp=base+t*1000;s.device="live-solar";s.kind="solar";s.period=60;s.solar=60.0;history.insert(s);
                    Reading b=new Reading();b.timestamp=s.timestamp;b.device="live-shunt";b.kind="shunt";b.period=60;b.voltage=12.0;b.current=-5.0;history.insert(b);
                }
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            snapshot(repo,"live-solar","solar",end,now,c,0xec8a);snapshot(repo,"live-shunt","shunt",end,now,c,0xed8d,0xed8c);
            require(repo.automaticArchiveDevices().length()==0,"actual Repository starts no archive when all live data exist");
            require(!repo.archiveNeedsData(solar,now+300000)&&!repo.archiveNeedsData(shunt,now+300000),"advancing five minutes with continued recording still needs no connection");
            db.execSQL("DELETE FROM readings WHERE device='live-solar' AND ts>? AND ts<?",new Object[]{now-3*86400000L,now-90000});
            require(repo.archiveNeedsData(solar,now),"three offline days are found despite a recent live sample");
            repo.prefs.edit().putLong("archiveNext.live-solar",0).putLong("archiveNext.live-shunt",0).commit();
            JSONArray due=repo.automaticArchiveDevices();require(due.length()==1&&due.getJSONObject(0).getString("mac").equals("live-solar"),"only the device with missing data is selected");
            db.execSQL("DELETE FROM readings WHERE device='live-solar'");
            require(repo.archiveNeedsData(solar,now),"three offline weeks remain eligible for backfill");
            require(!repo.archiveNeedsData(shunt,now),"one device's gap cannot cause another device's archive connection");
            require(repo.archiveNeedsData(new JSONObject().put("mac","new-device").put("kind","shunt"),now),"a new device gets its first real metadata preflight");
        }
        history.close();
    }
}
