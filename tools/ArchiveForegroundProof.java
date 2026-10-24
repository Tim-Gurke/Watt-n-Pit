package de.wattpit.app;

import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import de.wattpit.app.core.*;
import org.json.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Production foreground trigger and Repository/SQLite, controlled clock and simulated GATT only. */
public final class ArchiveForegroundProof {
    static long now=1800000000000L;
    static final long BASE=now-86400000L;
    static int starts,queries,repeated;
    static boolean fail;
    static Repository repository;
    static final Set<String> received=new HashSet<>();
    static ArchiveCodec.Clock clock(){
        ArchiveCodec.Clock c=new ArchiveCodec.Clock();byte[] b=new byte[8];
        System.arraycopy(ArchiveCodec.le(BASE/1000,4),0,b,4,4);c.anchors(b);return c;
    }
    static SharedPreferences preferences(){
        Map<String,Object> data=new HashMap<>();
        return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)->{
            if(m.getName().equals("edit")){
                Map<String,Object> staged=new HashMap<>();
                return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(e,n,b)->{
                    if(n.getName().startsWith("put")){staged.put((String)b[0],b[1]);return e;}
                    if(n.getName().equals("apply")||n.getName().equals("commit")){data.putAll(staged);return n.getName().equals("commit")?true:null;}
                    throw new UnsupportedOperationException(n.getName());
                });
            }
            if(m.getName().equals("contains"))return data.containsKey(a[0]);
            return data.getOrDefault(a[0],a[1]);
        });
    }
    static final class TestContext extends ContextWrapper{
        final SharedPreferences prefs=preferences();
        TestContext(){super(null);}
        @Override public Context getApplicationContext(){return this;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return prefs;}
    }
    static JSONObject config()throws Exception{return new JSONObject().put("mac","solar-test").put("kind","solar");}
    static final int[] PERIODS={1,30,300,1800},COUNTS={121,121,121,121};
    static List<ArchiveCodec.Level> levels(){
        long end=(now-BASE)/1000;List<ArchiveCodec.Level> out=new ArrayList<>();
        for(int i=0;i<PERIODS.length;i++)out.add(new ArchiveCodec.Level(COUNTS[i],PERIODS[i],end-end%PERIODS[i]));
        return out;
    }
    static void importData()throws Exception{
        starts++;
        repository.archiveAttemptStarted("solar-test");
        if(fail){repository.archiveAttemptFinished("solar-test",false,false);return;}
        ArchiveCodec.Clock clock=clock();List<ArchiveCodec.Level> levels=levels();
        Map<Integer,List<ArchiveCodec.Level>> tiers=new LinkedHashMap<>();
        Map<Integer,ArchiveCodec.Field> fields=new LinkedHashMap<>();Map<Integer,Integer> regs=new LinkedHashMap<>();
        for(int ch=0;ch<2;ch++){tiers.put(ch,levels);regs.put(ch,ch==0?0xec8a:0xedbb);fields.put(ch,ArchiveCodec.field(regs.get(ch),-1));}
        repository.saveArchivePlan("solar-test","solar",(now-BASE)/1000,now,clock,tiers,fields,regs);
        for(int ch=0;ch<2;ch++){
            ArchiveSyncPlan.Availability a=repository.db.archiveAvailability("solar-test","solar",regs.get(ch),fields.get(ch).name,levels,clock,now,()->{});
            Map<Integer,ArchiveSyncPlan.Availability> local=new HashMap<>();local.put(ch,a);
            Map<Integer,Integer> batches=new HashMap<>();batches.put(ch,28);
            ArchiveTransferPlan transfer=new ArchiveTransferPlan(clock,local,batches);
            for(ArchiveCodec.Level l:levels)transfer.add(ch,l);
            ArchiveTransferPlan.Read read;
            while((read=transfer.next(c->{}))!=null){
                int n=read.request.count;queries++;
                ArchiveCodec.Field f=fields.get(ch);byte[] data=new byte[8+n*f.width];data[0]=(byte)ch;
                System.arraycopy(ArchiveCodec.le(read.request.cursor,4),0,data,1,4);data[5]=(byte)n;
                System.arraycopy(ArchiveCodec.le(read.period,2),0,data,6,2);
                List<long[]> times=new ArrayList<>();List<Double> values=new ArrayList<>();
                for(int i=0;i<n;i++){
                    long cursor=read.request.cursor-(long)i*read.period,time=clock.millis(cursor);
                    if(!received.add(ch+":"+read.period+":"+time))repeated++;
                    times.add(new long[]{time,read.period});values.add(ch==0?60.0:18.0);
                    System.arraycopy(ArchiveCodec.le(ch==0?60:1800,f.width),0,data,8+i*f.width,f.width);
                }
                ArchiveCodec.Block block=new ArchiveCodec.Block(data,f,ch,n);
                repository.db.archiveBlock("solar-test","solar",f.name,regs.get(ch),read.period,times,values,ArchiveSyncPlan.completed(block,clock,now));
                if(!transfer.accept(block))throw new AssertionError("simulated valid block was rejected");
            }
        }
        try{
            // The simulated successful session also completes its optional settings read.
            Method settings=Repository.class.getDeclaredMethod("saveDeviceSettings",String.class,String.class,JSONObject.class,int.class,long.class);
            settings.setAccessible(true);settings.invoke(repository,"solar-test","solar",new JSONObject(),0,now);
        }catch(NoSuchMethodException baseline){/* Older production did not read settings. */}
        repository.archiveAttemptFinished("solar-test",false,true);
    }
    static void live()throws Exception{
        Reading r=new Reading();r.timestamp=now;r.device="solar-test";r.kind="solar";r.period=10;r.solar=60.0;
        try{
            Method method=Repository.class.getDeclaredMethod("recordLive",Reading.class);
            method.setAccessible(true);method.invoke(repository,r);
        }catch(NoSuchMethodException baseline){
            Field field=Repository.class.getDeclaredField("latest");field.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String,Reading> latest=(Map<String,Reading>)field.get(repository);
            latest.put(r.device,r);repository.db.insert(r);
        }
    }
    static void require(boolean ok,String name){if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception{
        System.setProperty("wattpit.test.database",args[0]);Class.forName("org.sqlite.JDBC");
        repository=Repository.get(new TestContext());
        repository.prefs.edit().putString("devices",new JSONArray().put(config()).toString()).putInt("sampleSeconds",10).putBoolean("cockpitNotice-v1",true).commit();
        live();importData();starts=queries=repeated=0;received.clear();
        ForegroundHarness ui=new ForegroundHarness(repository);ui.enter();
        for(int second=5;second<=3600;second+=5){now+=5000;if(second%10==0)live();if(second%15==0)ui.tick();}
        int connected=starts;
        System.out.println("RESULT successful_hour_connections="+connected+" measurement_requests="+queries+" repeated_points="+repeated);
        if(args[1].equals("fixed"))require(connected==0,"successful archive is not restarted while foreground remains open");
        else require(connected>1,"published 1.3.6 repeatedly opens archives after successful imports");
        for(int second=5;second<=335;second+=5){now+=5000;if(second%10==0)live();}ui.enter();
        require(starts>connected,"reopening still fetches the accumulated archive-only PV-voltage data");
        require(!repository.archiveNeedsData(config(),now),"reopening leaves no available archive-only data gap");
        require(repeated==0,"completed archive points are never requested twice");
        if(args[1].equals("fixed")){
            int before=starts;
            for(int second=5;second<=35;second+=5){now+=5000;if(second%10==0)live();}ui.enter();
            require(starts==before+1,"a foreground return can fetch new data before five minutes have elapsed");
            require(!repository.archiveNeedsData(config(),now),"short foreground return fills the new available range");
        }
        starts=queries=0;fail=true;
        now+=400000;live();ui.enter();
        require(starts==1,"one foreground attempt reaches a simulated unavailable GATT connection");
        for(int second=5;second<=1800;second+=5){now+=5000;if(second%10==0)live();if(second%15==0)ui.tick();}
        int failedRepeats=starts-1;
        System.out.println("RESULT failed_half_hour_retries="+failedRepeats);
        if(args[1].equals("fixed")){
            require(failedRepeats==0,"continuous live packets do not repeatedly retry a failed GATT session");
            now+=60000;live();fail=false;ui.tick();
            require(starts==2,"a real reception gap and recovery permits one pending retry");
            require(!repository.archiveNeedsData(config(),now),"recovery imports the remaining archived data");
            for(int i=0;i<40;i++){now+=15000;live();ui.tick();}
            require(starts==2,"completed recovery does not start another archive loop");
            repository.pauseAutomaticArchives();now+=60000;live();ui.tick();ui.enter();
            require(starts==2,"manual cancellation pause also blocks a foreground or reception trigger");
        }else require(failedRepeats>0,"published foreground timer retries failed sessions without reception recovery");
        repository.db.close();
    }
}

