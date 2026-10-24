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
    private static int captureImport(HistoryDb history,CapturedVictronDevice peer)throws Exception{
        Map<Integer,List<ArchiveCodec.Level>> levels=peer.levels();Map<Integer,ArchiveSyncPlan.Availability> local=new HashMap<>();Map<Integer,Integer> batches=new HashMap<>();
        for(int ch:levels.keySet()){
            local.put(ch,history.archiveAvailability("capture-shunt","shunt",peer.register(ch),peer.field(ch).name,levels.get(ch),peer.clock(),peer.now(),()->{}));
            batches.put(ch,peer.registers.get(0xec4a+ch)[1]&255);
        }
        ArchiveTransferPlan plan=new ArchiveTransferPlan(peer.clock(),local,batches);
        for(int ch:levels.keySet())for(ArchiveCodec.Level level:levels.get(ch))plan.add(ch,level);
        ArchiveTransferPlan.Read r;int calls=0;
        while((r=plan.next(ch->{}))!=null){
            if(++calls>550)throw new AssertionError("Captured transfer loops");byte[] raw=peer.reply(r);
            if(!ArchiveSyncPlan.related(r.channel,r.request,r.period,raw))throw new AssertionError("Captured reply filtered out");
            ArchiveCodec.Field field=peer.field(r.channel);ArchiveCodec.Block block=new ArchiveCodec.Block(raw,field,r.channel,r.request.count);
            List<long[]> times=new ArrayList<>();List<Double> values=new ArrayList<>();
            for(int i=0;i<block.count;i++)if(block.values[i]!=null){times.add(new long[]{peer.clock().millis(block.time-(long)i*block.period),block.period});values.add(block.values[i]);}
            history.archiveBlock("capture-shunt","shunt",field.name,peer.register(r.channel),block.period,times,values,ArchiveSyncPlan.completed(block,peer.clock(),peer.now()));
            List<long[]> unavailable=plan.unavailable(block);
            if(!unavailable.isEmpty())history.archiveBlock("capture-shunt","shunt",field.name,peer.register(r.channel),r.period,Collections.emptyList(),Collections.emptyList(),unavailable);
            if(!plan.accept(block))throw new AssertionError("Captured reply causes unnecessary replan");
        }
        for(int ch:levels.keySet()){
            ArchiveSyncPlan.Availability saved=history.archiveAvailability("capture-shunt","shunt",peer.register(ch),peer.field(ch).name,levels.get(ch),peer.clock(),peer.now(),()->{});
            if(ArchiveSyncPlan.needsData(levels.get(ch),peer.clock(),saved))throw new AssertionError("SQLite audit requests the captured archive again");
        }
        return calls;
    }
    private static void captureValues(SQLiteDatabase db,CapturedVictronDevice peer){
        Map<String,Double> actual=new HashMap<>();
        for(int ch:peer.levels().keySet())try(Cursor c=db.rawQuery("SELECT ts,period,"+peer.field(ch).name+" FROM readings WHERE device='capture-shunt' AND "+peer.field(ch).name+" IS NOT NULL",null)){
            while(c.moveToNext())actual.put(ch+":"+c.getInt(1)+":"+((c.getLong(0)-CapturedVictronDevice.EPOCH)/1000),c.getDouble(2));
        }
        require(actual.equals(peer.validValues()),"every valid captured measurement survives the actual HistoryDb without alteration");
    }
    private static void captureIncoming(CapturedVictronDevice peer)throws Exception{
        IncomingHarness h=new IncomingHarness();ArchiveCodec.Stream stream=new ArchiveCodec.Stream();int window=128,decoded=0,parts=0;
        for(CapturedVictronDevice.Pair pair:peer.pairs){
            byte[] frame=new byte[pair.response.length+6];byte[] prefix={8,3,0x19,(byte)0xec,0x5b,(byte)(0x40+pair.response.length)};
            if(pair.response.length>=24){frame=new byte[pair.response.length+7];prefix=new byte[]{8,3,0x19,(byte)0xec,0x5b,0x58,(byte)pair.response.length};}
            System.arraycopy(prefix,0,frame,0,prefix.length);System.arraycopy(pair.response,0,frame,prefix.length,pair.response.length);
            int cut=frame.length/2;
            for(byte[] part:new byte[][]{Arrays.copyOfRange(frame,0,cut),Arrays.copyOfRange(frame,cut,frame.length)}){
                if(window--<=0)throw new AssertionError("Combined register notification credits exhausted");
                h.feed((parts++%2)==0?3:4,part);int grant;while((grant=h.link.receiveGrant())>0)window+=grant;
                byte[] bytes;while((bytes=h.received.poll())!=null)for(ArchiveCodec.Message message:stream.feed(bytes)){
                    if(message.reg!=0xec5b||!Arrays.equals(message.data,peer.pairs.get(decoded).response))throw new AssertionError("Merged capture stream changed");decoded++;
                }
            }
        }
        require(decoded==peer.pairs.size()&&h.link.received()==parts&&!h.cancelled,"actual incoming method merges both characteristics and replenishes all 956 captured fragments");
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("wattpit.test.database",args[0]);Class.forName("org.sqlite.JDBC");
        Repository repo=Repository.get(new TestContext(Path.of(args[0]+".prefs")));HistoryDb history=repo.db;SQLiteDatabase db=history.getWritableDatabase();
        long epoch=1700000000000L;ArchiveCodec.Clock c=clock(epoch);
        if(args[1].equals("weather_contract")){
            WeatherContractProof.run(Path.of(args[2]).getParent().getParent().resolve("weather"),Path.of(args[3]));
        }else if(args[1].equals("solar_planning")){
            SolarPlanningProof.run(repo,new TestContext(Path.of(args[0]+".prefs")));
        }else if(args[1].equals("manual_queue")){
            repo.prefs.edit().putString("devices",new JSONArray().put(new JSONObject().put("mac","manual-solar").put("kind","solar")).put(new JSONObject().put("mac","manual-shunt").put("kind","shunt")).toString()).commit();
            repo.pauseAutomaticArchives();
            require(repo.requestedArchiveDevices("AUTO").length()==0&&repo.requestedArchiveDevices("RETRY").length()==0,"automatic requests respect an explicit cancellation pause");
            require(repo.requestedArchiveDevices("MANUAL").length()==2,"manual reload selects both configured devices during the cancellation pause");
            require(repo.requestedArchiveDevices("manual-solar").length()==1&&repo.requestedArchiveDevices("unknown").length()==0,"manual single-device requests remain scoped to configured devices");
            history.archiveBlock("manual-solar","solar","solar",0xec8a,30,Arrays.asList(new long[]{epoch+30000,30}),Arrays.asList(60.0),Arrays.asList(new long[]{epoch+30000,epoch+60000}));
            repo.archiveAttemptStarted("manual-solar");repo.archiveAttemptFinished("manual-solar",true,false);
            require(repo.requestedArchiveDevices("MANUAL").length()==2,"manual reload can be requested again immediately after cancellation");
            require(count(db,"readings","device='manual-solar'")==1&&count(db,"archive_coverage","device='manual-solar'")==1,"manual retry selection preserves committed measurements and NA acknowledgements");
        }else if(args[1].equals("daily_energy")){
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));java.time.ZoneId zone=java.time.ZoneId.systemDefault();
            java.time.LocalDate day=java.time.LocalDate.of(2026,3,29);
            long start=day.atStartOfDay(zone).toInstant().toEpochMilli(),end=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            for(long t=start;t<=end;t+=1800000){
                Reading solar=new Reading();solar.timestamp=t;solar.device="daily-solar";solar.kind="solar";solar.origin="archive";solar.period=1800;solar.solar=6.0;history.insert(solar);
                Reading shunt=new Reading();shunt.timestamp=t;shunt.device="daily-shunt";shunt.kind="shunt";shunt.origin="archive";shunt.period=1800;shunt.voltage=12.0;shunt.current=-0.5;history.insert(shunt);
            }
            long monthStart=day.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli(),monthEnd=day.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli();
            JSONArray days=history.dailyEnergy(monthStart,monthEnd,new JSONObject()).getJSONArray("days");JSONObject result=null;
            for(int i=0;i<days.length();i++)if(days.getJSONObject(i).getString("day").equals(day.toString()))result=days.getJSONObject(i);
            require(days.length()==31&&result!=null&&result.getLong("to")-result.getLong("from")==23*3600000L,"daily summaries follow local calendar days across daylight saving time");
            require(Math.abs(result.getDouble("last")-276)<1e-8&&result.getDouble("coverage")==1,"a complete 23-hour day retains the existing consumption formula and full coverage");
            require(Math.abs(result.getDouble("last")-history.energyFlow(start,end,"all").getJSONObject("consumption").getJSONObject("summary").getDouble("last"))<1e-8,"daily consumption equals the existing full-day energy history");
            require(days.getJSONObject(0).isNull("last")&&days.getJSONObject(0).getDouble("coverage")==0,"days without raw measurements remain unknown instead of zero");
            long partial=day.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()+12*3600000L;
            for(int i=0;i<2;i++){
                Reading solar=new Reading();solar.timestamp=partial+i*1800000;solar.device="daily-solar";solar.kind="solar";solar.origin="archive";solar.period=1800;solar.solar=0.0;history.insert(solar);
                Reading shunt=new Reading();shunt.timestamp=solar.timestamp;shunt.device="daily-shunt";shunt.kind="shunt";shunt.origin="archive";shunt.period=1800;shunt.voltage=12.0;shunt.current=0.0;history.insert(shunt);
            }
            days=history.dailyEnergy(monthStart,monthEnd,new JSONObject()).getJSONArray("days");result=days.getJSONObject(30);
            require(result.getDouble("last")==0&&result.getLong("coveredMillis")==1800000&&result.getDouble("coverage")<0.03,"measured zero is retained with partial coverage without filling the surrounding gap");
            java.time.LocalDate nightDay=java.time.LocalDate.of(2026,3,28);long night=nightDay.atStartOfDay(zone).toInstant().toEpochMilli();
            for(long t=night;t<=night+4*3600000L;t+=1800000){
                Reading shunt=new Reading();shunt.timestamp=t;shunt.device="daily-shunt";shunt.kind="shunt";shunt.origin="archive";shunt.period=1800;shunt.voltage=12.0;shunt.current=-1.0;history.insert(shunt);
            }
            JSONObject weather=new JSONObject().put("days",new JSONArray().put(new JSONObject().put("day",nightDay.toString()).put("sunrise",night+7*3600000L).put("sunset",night+19*3600000L)));
            days=history.dailyEnergy(monthStart,monthEnd,weather).getJSONArray("days");result=days.getJSONObject(27);
            require(Math.abs(result.getDouble("last")-48)<1e-8&&result.getLong("coveredMillis")==4*3600000L,"known night-time consumption uses observed shunt data without inventing daylight solar measurements");
        }else if(args[1].startsWith("capture_")){
            CapturedVictronDevice peer=new CapturedVictronDevice(Path.of(args[2]));
            if(args[1].equals("capture_write")){
                captureIncoming(peer);int requests=captureImport(history,peer);require(requests>0&&requests<500,"actual HistoryDb import completes the captured 45-day archive in fewer than 500 block reads with zero replans");captureValues(db,peer);
                Map<Integer,ArchiveCodec.Field> fields=new LinkedHashMap<>();Map<Integer,Integer> registers=new LinkedHashMap<>();
                for(int ch:peer.levels().keySet()){fields.put(ch,peer.field(ch));registers.put(ch,peer.register(ch));}
                repo.saveArchivePlan("capture-shunt","shunt",ArchiveCodec.u(peer.registers.get(0xec5a),0,4),peer.now(),peer.clock(),peer.levels(),fields,registers);
                require(!repo.archiveNeedsData(new JSONObject().put("mac","capture-shunt").put("kind","shunt"),peer.now()),"actual Repository opens no GATT session for the fully stored captured archive");
                System.out.println("RESULT captured_full_reads="+requests);
            }else if(args[1].equals("capture_resume")){
                require(captureImport(history,peer)==0,"fresh JVM reopens the captured archive with zero measurement requests");captureValues(db,peer);require(!repo.archiveNeedsData(new JSONObject().put("mac","capture-shunt").put("kind","shunt"),peer.now()),"fresh Repository cache preserves real tier ages and does not start an unnecessary connection");
            }else{
                long from=peer.now()-3*3600000L;
                db.delete("readings","device='capture-shunt' AND ts>=?",new String[]{""+from});
                db.execSQL("DELETE FROM archive_coverage WHERE device='capture-shunt' AND start>=?",new Object[]{from});
                db.execSQL("UPDATE archive_coverage SET end=? WHERE device='capture-shunt' AND end>=?",new Object[]{from-1,from});
                int requests=captureImport(history,peer);require(requests>0&&requests<100,"three missing captured hours use fewer than 100 block reads without replaying the older archive");captureValues(db,peer);
                require(captureImport(history,peer)==0,"completed three-hour recovery causes no repeated transfer");System.out.println("RESULT captured_three_hour_reads="+requests);
            }
        }else if(args[1].equals("atomic_write")){
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
            // Use the fixture's elapsed time; runner speed must not age past its live buffer.
            JSONArray configs=repo.configs();long now=repo.prefs.getLong("proofFixtureTime",0)+120000;
            require(repo.archiveNeedsData(configs.getJSONObject(0),now),"cached device layout survives restart and finds the remaining gap");
            require(!repo.archiveNeedsData(configs.getJSONObject(1),now),"cached device layout survives restart without restarting a complete device");
        }else{
            long end=21*86400L,now=System.currentTimeMillis()/1000*1000,base=now-end*1000;c=clock(base);
            JSONObject solar=new JSONObject().put("mac","live-solar").put("kind","solar"),shunt=new JSONObject().put("mac","live-shunt").put("kind","shunt");
            repo.prefs.edit().putString("devices",new JSONArray().put(solar).put(shunt).toString()).putInt("sampleSeconds",60).putLong("proofFixtureTime",now).commit();
            db.beginTransaction();try{
                // Queries over 21 days can outlast five minutes on a slow test runner.
                for(long t=0;t<=end+3600;t+=60){
                    Reading s=new Reading();s.timestamp=base+t*1000;s.device="live-solar";s.kind="solar";s.period=60;s.solar=60.0;history.insert(s);
                    Reading b=new Reading();b.timestamp=s.timestamp;b.device="live-shunt";b.kind="shunt";b.period=60;b.voltage=12.0;b.current=-5.0;history.insert(b);
                }
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            snapshot(repo,"live-solar","solar",end,now,c,0xec8a);snapshot(repo,"live-shunt","shunt",end,now,c,0xed8d,0xed8c);
            require(repo.deviceSettingsDue(solar,now),"missing settings request one initial bounded read even with complete measurements");
            repo.prefs.edit().putString("deviceSettings.live-solar",new JSONObject().put("at",now).toString()).putString("deviceSettings.live-shunt",new JSONObject().put("at",now).toString()).commit();
            require(!repo.deviceSettingsDue(solar,now+300000)&&repo.deviceSettingsDue(solar,now+VictronSettings.MAX_AGE),"settings refresh has a six-hour interval instead of reconnecting at every poll");
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
