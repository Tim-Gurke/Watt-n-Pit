package de.wattpit.app;

import android.content.*;
import android.content.pm.PackageManager;
import de.wattpit.app.core.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Real LanSync TCP host/client and real HistoryDb; substitutes Android lifecycle/NSD only. */
public final class LanSyncProof {
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS "+name);}
    private static final class TestContext extends ContextWrapper {
        final Path root;final Map<String,SharedPreferences> values=new HashMap<>();
        TestContext(Path root)throws Exception{super(null);this.root=root;Files.createDirectories(root);}
        @Override public Context getApplicationContext(){return this;}
        @Override public String getPackageName(){return "de.wattpit.app";}
        @Override public File getFilesDir(){return root.toFile();}
        @Override public Object getSystemService(String name){return null;}
        @Override public String getSystemServiceName(Class<?> type){return null;}
        @Override public int checkSelfPermission(String permission){return PackageManager.PERMISSION_DENIED;}
        @Override public boolean stopService(Intent intent){return true;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return values.computeIfAbsent(name,k->preferences());}
    }
    private static SharedPreferences preferences(){
        Map<String,Object> values=new ConcurrentHashMap<>();
        return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
            if(method.getName().equals("edit")){
                Map<String,Object> next=new HashMap<>();Set<String> removed=new HashSet<>();
                return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(editor,m,a)->{
                    if(m.getName().startsWith("put")){next.put((String)a[0],a[1]);return editor;}if(m.getName().equals("remove")){removed.add((String)a[0]);return editor;}
                    if(m.getName().equals("commit")||m.getName().equals("apply")){removed.forEach(values::remove);values.putAll(next);return m.getName().equals("commit")?true:null;}throw new UnsupportedOperationException(m.getName());
                });
            }
            if(method.getName().equals("contains"))return values.containsKey(args[0]);if(method.getName().equals("getAll"))return new HashMap<>(values);
            return values.getOrDefault((String)args[0],args[1]);
        });
    }
    private static Repository repository(TestContext context)throws Exception{
        System.setProperty("wattpit.test.database",context.root.resolve("history.db").toString());System.setProperty("wattpit.test.namedDatabases","true");
        Field instance=Repository.class.getDeclaredField("instance");instance.setAccessible(true);instance.set(null,null);Repository repo=Repository.get(context);repo.db.getWritableDatabase();return repo;
    }
    private static LanSync sync(TestContext context)throws Exception{Constructor<LanSync> constructor=LanSync.class.getDeclaredConstructor(Context.class);constructor.setAccessible(true);return constructor.newInstance(context);}
    private static void close(LanSync sync)throws Exception{sync.stopHost();Field worker=LanSync.class.getDeclaredField("client");worker.setAccessible(true);((ScheduledExecutorService)worker.get(sync)).shutdownNow();}
    private static void measurement(Repository repo,long at,double power)throws Exception{Reading r=new Reading();r.device="AA:00:00:00:00:01";r.kind="shunt";r.origin="live";r.period=10;r.timestamp=at;r.power=power;r.voltage=12.8;r.current=power/12.8;r.soc=76.4;repo.db.insert(r);}
    public static void main(String[] args)throws Exception{
        Class.forName("org.sqlite.JDBC");Path root=Path.of(args[0]);TestContext main=new TestContext(root.resolve("host"));Repository source=repository(main);long now=System.currentTimeMillis();
        source.prefs.edit().putString("devices",new JSONArray().put(new JSONObject().put("mac","AA:00:00:00:00:01").put("name","Test-Shunt").put("kind","shunt").put("key","PRIVATE-DEVICE-KEY-SENTINEL").put("pin","PRIVATE-PIN-SENTINEL")).toString()).commit();
        source.running=true;measurement(source,now-30000,-12);measurement(source,now-20000,-12);measurement(source,now-10000,-12);
        LanSync host=sync(main),viewer=null,restarted=null,wrong=null;
        try{
            host.create("Test-Wohnwagen");host.startHost();Field configuration=LanSync.class.getDeclaredField("config"),server=LanSync.class.getDeclaredField("server");configuration.setAccessible(true);server.setAccessible(true);
            JSONObject invitation=new JSONObject(((JSONObject)configuration.get(host)).toString()).put("port",((java.net.ServerSocket)server.get(host)).getLocalPort());
            invitation.put("addresses",new JSONArray().put("127.0.0.1"));String code="WNP1."+Base64.getUrlEncoder().withoutPadding().encodeToString(invitation.toString().getBytes(StandardCharsets.UTF_8));
            TestContext display=new TestContext(root.resolve("viewer"));Repository own=repository(display);measurement(own,now-10000,-999);viewer=sync(display);
            System.setProperty("wattpit.test.database",main.root.resolve("history.db").toString());
            boolean badFormat=false;try{JSONObject bad=new JSONObject(invitation.toString()).put("key","WNP1.invalid.key");viewer.join(LanWire.encodeInvitation(bad.toString()));}catch(IllegalArgumentException expected){badFormat=expected.getMessage().equals(LanWire.INVALID_INVITATION);}
            check(badFormat&&!viewer.viewer(),"a malformed nested key gives a readable format error before any pairing state is changed");
            viewer.join("\uFEFF„WNP1."+code.substring(0,35)+"\n\u00a0"+code.substring(35)+"\u200b”.");
            check(viewer.consumeJoined()&&!viewer.consumeJoined(),"pairing acknowledgement is persisted natively and consumed exactly once, independently of WebView session storage");
            JSONObject snapshot=new JSONObject(viewer.snapshot());check(viewer.viewer()&&viewer.state().getBoolean("connected"),"a real encrypted TCP handshake activates the intended plant viewer");
            check(snapshot.getJSONArray("readings").getJSONObject(0).getDouble("power")==-12,"viewer shows the source device rather than its independent -999 W database");
            check(!snapshot.toString().contains("PRIVATE-"),"device keys and PINs never leave the host snapshot");
            Path savedDisplay=display.root.resolve("lan-display.json");Files.setLastModifiedTime(savedDisplay,java.nio.file.attribute.FileTime.fromMillis(1000000));
            Method refresh=LanSync.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(viewer);
            check(Files.getLastModifiedTime(savedDisplay).toMillis()==1000000,"frequent live refreshes do not rewrite the complete persistent display bundle");
            JSONArray history=viewer.query("history",new JSONArray().put(now-60000).put(now).put("batteryPower").put("all")).getJSONArray("points");
            JSONObject mobilePower=viewer.query("dashboardHistory",new JSONArray().put(now-60000).put(now).put("mobileSolarPower"));
            check(mobilePower.getJSONObject("summary").getDouble("last")==0,"a viewer receives known zero mobile power from a host without mobile modules, independently of its own weather");
            check(viewer.query("energyHistoryWindow",new JSONArray().put(now-60000).put(now).put("all").put(now-60000).put(now)).getInt("accountingVersion")==2,"LAN energy responses identify complete device accounting so older host data cannot be mislabeled");
            java.time.ZoneId zone=java.time.ZoneId.systemDefault();java.time.LocalDate today=java.time.LocalDate.now(zone);
            long monthFrom=today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli(),monthTo=today.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli();
            for(int i=3;i>=1;i--){Reading solar=new Reading();solar.device="AA:00:00:00:00:02";solar.kind="solar";solar.origin="live";solar.timestamp=now-i*10000;solar.period=10;solar.solar=30.0;source.db.insert(solar);}
            JSONObject localDays=source.db.dailyEnergy(monthFrom,monthTo,new JSONObject());
            JSONObject sharedDays=viewer.query("dailyEnergy",new JSONArray().put(monthFrom).put(monthTo).put(1).put(1));
            JSONArray aDays=localDays.getJSONArray("days"),bDays=sharedDays.getJSONArray("days");boolean same=aDays.length()==bDays.length(),measured=false;
            for(int i=0;i<aDays.length();i++){JSONObject a=aDays.getJSONObject(i),b=bDays.getJSONObject(i);same&=a.getString("day").equals(b.getString("day"))&&a.isNull("last")==b.isNull("last");if(!a.isNull("last")){measured|=a.getDouble("last")>0;same&=Math.abs(a.getDouble("last")-b.getDouble("last"))<1e-6;}}
            check(same&&measured,"the complete current-month request returns the same daily consumption on host and viewer, even though the month ends in the future");
            for(int i=0;i<2;i++){String day=today.minusDays(i).toString();android.content.ContentValues row=new android.content.ContentValues();row.put("device","AA:00:00:00:00:02");row.put("day",day);row.put("payload",new JSONObject().put("day",day).put("Yield(Wh)",i==0?410:0).put("Max. PV power(W)",170).put("Min. battery voltage(V)",12.9).put("Max. battery voltage(V)",14.2).toString());source.db.getWritableDatabase().insertWithOnConflict("device_daily",null,row,android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);}
            refresh.invoke(viewer);check(source.db.daily().toString().equals(new JSONObject(viewer.snapshot()).getJSONArray("solarDaily").toString()),"yield, real zero, maximum power and voltage columns arrive unchanged in the viewer's solar day archive");
            boolean dailyBound=false;try{viewer.query("dailyEnergy",new JSONArray().put(monthFrom).put(monthFrom+33L*86400000));}catch(IOException expected){dailyBound=true;}
            check(dailyBound,"monthly consumption remains bounded to 32 days despite accepting a future month end");


            check(history.length()>0&&history.getJSONObject(0).getDouble("v")==-12,"history queries use the same host database over actual TCP");
            check(own.db.last("AA:00:00:00:00:01").power==-999,"display and history reads preserve the viewer's own stored measurements");
            boolean rejected=false;try{viewer.query("saveEquipment",new JSONArray().put(now-60000).put(now));}catch(IOException expected){rejected=true;}
            check(rejected,"network API rejects unrelated plant-write commands");
            String preset=Files.readString(Path.of("app/src/main/assets/dashboard-preset.json"));
            DashboardPreset.install(own.prefs,preset);JSONObject installed=new JSONObject(own.prefs.getString("dashboard-v1","{}"));
            check(installed.getJSONArray("tiles").length()==11&&installed.getJSONArray("tiles").getJSONObject(0).getJSONObject("layouts").getJSONObject("compact").getDouble("h")==8,"the approved G5 layout is installed on an existing viewer, including compact geometry");
            boolean small=true;for(int i=0;i<installed.getJSONArray("tiles").length();i++)small&=installed.getJSONArray("tiles").getJSONObject(i).getDouble("fontSize")<=20;
            check(small&&installed.getJSONArray("tiles").getJSONObject(0).getJSONObject("layouts").getJSONObject("wide").getDouble("w")==2,"all preset font sizes are at most 20 and the separate wide geometry survives");
            own.prefs.edit().putString("dashboard-v1","custom-after-update").commit();DashboardPreset.install(own.prefs,preset);
            check(own.prefs.getString("dashboard-v1","").equals("custom-after-update"),"reopening the app does not reapply the one-time rollout over subsequent edits");
            own.prefs.edit().remove(DashboardPreset.MARKER).putString("dashboard-v1","device-layout-without-old-marker").commit();DashboardPreset.install(own.prefs,preset);
            check(own.prefs.getString("dashboard-v1","").equals("device-layout-without-old-marker")&&own.prefs.getBoolean(DashboardPreset.MARKER,false),"skipping the original rollout preserves an existing device layout even without its migration marker");
            DashboardPreset.install(source.prefs,preset);source.prefs.edit().putString("dashboard-v1","different-main-device-layout").remove(DashboardPreset.MARKER).commit();DashboardPreset.install(source.prefs,preset);
            check(source.prefs.getString("dashboard-v1","").equals("different-main-device-layout")&&own.prefs.getString("dashboard-v1","").equals("device-layout-without-old-marker"),"main and display layouts survive the update independently");
            JSONObject groups=new JSONObject().put("version",1).put("groups",new JSONArray().put(new JSONObject().put("id","mobile").put("label","Mobile test").put("count",1).put("watts",100).put("placement","mobile").put("measurement","shunt").put("tilt",45)));
            viewer.control("saveEquipment",new JSONArray().put(groups.toString()),viewer.controlRevision());
            check(source.equipment().getJSONArray("groups").length()==1&&new JSONObject(viewer.snapshot()).getJSONObject("equipment").getJSONArray("groups").length()==1,"a viewer saves plant equipment on its host and receives the confirmed configuration");
            JSONObject setup=new JSONObject().put("version",1).put("groups",new JSONObject().put("mobile",new JSONObject().put("active",true).put("azimuth",180).put("shade",new JSONArray().put(0).put(0).put(0))));
            viewer.control("saveSolarSite",new JSONArray().put(setup.toString()).put("{}"),viewer.controlRevision());
            check(new JSONObject(source.prefs.getString("solar-setup-v1","{}")).getJSONObject("groups").getJSONObject("mobile").getBoolean("active"),"mobile-panel activation and orientation entered on the viewer reach the actual host");
            long configBefore=viewer.controlRevision();String configId=LanWire.random(16);JSONArray controlArgs=new JSONArray().put("settings").put(new JSONArray().put("{\"capacity\":120,\"sampleSeconds\":30}")).put(configBefore).put(configId);
            JSONObject confirmed=viewer.query("control",controlArgs);long afterConfig=confirmed.getLong("controlRevision");
            viewer.query("control",controlArgs);
            check(source.prefs.getFloat("capacity",0)==120&&source.prefs.getInt("sampleSeconds",0)==30&&host.controlRevision()==afterConfig,"shared battery settings commit before acknowledgement and a retried command is applied once");
            rejected=false;try{viewer.query("control",new JSONArray().put("settings").put(new JSONArray().put("{\"capacity\":5}")).put(configBefore).put(LanWire.random(16)));}catch(IllegalArgumentException expected){rejected=expected.getMessage().contains("inzwischen");}
            check(rejected&&source.prefs.getFloat("capacity",0)==120,"a stale configuration command cannot overwrite a newer acknowledged change");
            refresh.invoke(viewer);rejected=false;try{viewer.control("settings",new JSONArray().put("{\"capacity\":0}"),viewer.controlRevision());}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected&&source.prefs.getFloat("capacity",0)==120,"host-side validation rejects invalid values from the remote form");
            rejected=false;try{viewer.control("deleteDatabase",new JSONArray(),viewer.controlRevision());}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"the control API accepts only the explicit app-operation whitelist");
            long presenceFrom=System.currentTimeMillis()+3600000,presenceTo=presenceFrom+86400000;
            viewer.control("saveAbsence",new JSONArray().put(new JSONObject().put("from",presenceFrom).put("to",presenceTo).toString()),viewer.controlRevision());
            JSONObject sharedPresence=new JSONObject(viewer.part("presence"));String absenceId=sharedPresence.getJSONArray("plans").getJSONObject(0).getString("id");
            check(sharedPresence.getJSONArray("plans").getJSONObject(0).getLong("to")==presenceTo,"a viewer creates a confirmed future absence in the host's shared planning store");
            viewer.control("saveAbsence",new JSONArray().put(new JSONObject().put("id",absenceId).put("from",presenceFrom+3600000).put("to",presenceTo).toString()),viewer.controlRevision());
            check(new JSONObject(viewer.part("presence")).getJSONArray("plans").getJSONObject(0).getLong("to")==presenceTo,"remote departure edits preserve the original return and are acknowledged by the host");
            viewer.control("resolveAbsence",new JSONArray().put(absenceId).put("cancel"),viewer.controlRevision());
            check(new JSONObject(viewer.part("presence")).getJSONArray("plans").getJSONObject(0).getString("status").equals("cancelled"),"remote cancellation removes the planned absence from forecasting on every viewer");
            viewer.control("weatherPlace",new JSONArray().put("{\"off\":true}"),viewer.controlRevision());
            check(!source.prefs.contains("weatherPlace"),"the viewer can disable shared weather at the host");
            source.prefs.edit().putString("theme","dark").putString("dashboard-v1","host-layout").commit();
            JSONObject backup=new JSONObject().put("format","wattnpit-settings").put("version",1).put("dashboard",new JSONObject(preset)).put("equipment",groups).put("solarSetup",setup).put("weatherPlace",new JSONObject()).put("settings",new JSONObject().put("theme","light").put("palette","blue").put("miniRange","today").put("capacity",130).put("sampleSeconds",10));
            viewer.control("importSettings",new JSONArray().put(backup.toString()),viewer.controlRevision());
            check(source.prefs.getFloat("capacity",0)==130&&source.prefs.getString("theme","").equals("dark")&&source.prefs.getString("dashboard-v1","").equals("host-layout"),"a remote configuration import applies shared settings while preserving host appearance and layout");
            check(own.prefs.getFloat("capacity",100)==100&&own.prefs.getString("dashboard-v1","").equals("device-layout-without-old-marker"),"remote configuration writes do not alter the viewer's independent plant preferences");
            RollerStore roller=RollerStore.get(main);JSONObject profile=RollerStore.checked(new JSONObject()).put("mac","AA:BB:CC:DD:EE:FF");roller.configure(profile.toString());
            byte[] frame=new byte[30];frame[0]=(byte)0xdd;frame[1]=3;frame[3]=23;frame[4]=0x13;frame[5]=(byte)0xd6;frame[7]=(byte)189;frame[23]=70;int sum=0;for(int i=2;i<27;i++)sum+=frame[i]&255;int checksum=(-sum)&65535;frame[27]=(byte)(checksum>>8);frame[28]=(byte)checksum;frame[29]=0x77;
            roller.accept(BmsPacket.decode(frame),System.currentTimeMillis());refresh.invoke(viewer);long initialRevision=roller.snapshot().getLong("sessionRevision");String command=LanWire.random(16);
            JSONObject acknowledged=viewer.query("rollerSession",new JSONArray().put(true).put(initialRevision).put(command));
            check(acknowledged.getJSONObject("roller").getJSONObject("session").getBoolean("active")&&roller.snapshot().getJSONObject("session").getBoolean("active"),"an authenticated viewer can start special use on the actual host and receives its confirmed state");
            viewer.query("rollerSession",new JSONArray().put(true).put(initialRevision).put(command));
            check(roller.snapshot().getLong("sessionRevision")==initialRevision+1,"a retried network command cannot create a second charge session");
            refresh.invoke(viewer);acknowledged=viewer.rollerSession(false);
            check(!acknowledged.getJSONObject("session").getBoolean("active")&&!roller.snapshot().getJSONObject("session").getBoolean("active"),"the viewer can stop the host charge through the production command path");
            rejected=false;try{viewer.query("rollerSession",new JSONArray().put(true).put(initialRevision).put(LanWire.random(16)));}catch(IllegalArgumentException expected){rejected=expected.getMessage().contains("inzwischen");}
            check(rejected&&!roller.snapshot().getJSONObject("session").getBoolean("active"),"a stale delayed start is rejected with a readable state-change explanation");
            roller.disconnected();rejected=false;try{viewer.rollerSession(true);}catch(IllegalArgumentException expected){rejected=expected.getMessage().contains("BMS");}
            check(rejected&&!roller.snapshot().getJSONObject("session").getBoolean("active"),"the host rejects a remote start without fresh BMS readings");
            Field singleton=Repository.class.getDeclaredField("instance");singleton.setAccessible(true);singleton.set(null,source);source.running=true;
            RollerHandover handover=RollerHandover.get(main);JSONObject link=new JSONObject().put("mac",profile.getString("mac")).put("token","01234567-89ab-cdef-0123-456789abcdef").put("epoch",1);
            JSONObject reply=viewer.rollerLink("take",link);
            check(!reply.getBoolean("granted")&&handover.remoteOwns()&&source.running,"a remote take reserves only the roller reader and waits for the host BLE close while solar recording stays active");
            handover.hostSample(BmsPacket.decode(frame));check(handover.remoteOwns(),"a final in-flight host packet cannot cancel a pending handover");
            boolean other=false;try{viewer.rollerLink("take",new JSONObject(link.toString()).put("token","11234567-89ab-cdef-0123-456789abcdef"));}catch(IllegalArgumentException expected){other=true;}
            check(other,"a second ownership token cannot steal the active reader lease");
            handover.hostClosed();Thread.sleep(1600);reply=viewer.rollerLink("take",link);
            check(reply.getBoolean("granted"),"the riding phone is granted ownership only after the host close and radio settling interval");
            link.put("frame",Base64.getEncoder().encodeToString(frame)).put("sequence",1).put("age",0);
            viewer.rollerLink("take",link);long remoteAt=roller.snapshot().getJSONObject("live").getLong("at");
            viewer.rollerLink("take",link);
            check(roller.snapshot().getJSONObject("live").getLong("at")==remoteAt&&handover.snapshot().getString("phase").equals("s20"),"fresh remote BMS frames cross the real encrypted LAN once; duplicate frames cannot create energy");
            Field since=RollerHandover.class.getDeclaredField("homeSince");since.setAccessible(true);since.setLong(handover,android.os.SystemClock.elapsedRealtime()-9000);
            other=false;try{viewer.rollerLink("offer",new JSONObject(link.toString()).put("tripEnded",false));}catch(IllegalArgumentException expected){other=true;}
            check(other&&handover.remoteOwns(),"a paused or ongoing ride never returns ownership merely because its output is locked");
            byte[] unlocked=frame.clone();unlocked[24]=3;sum=0;for(int i=2;i<27;i++)sum+=unlocked[i]&255;checksum=(-sum)&65535;unlocked[27]=(byte)(checksum>>8);unlocked[28]=(byte)checksum;
            viewer.rollerLink("take",new JSONObject(link.toString()).put("frame",Base64.getEncoder().encodeToString(unlocked)).put("sequence",2));
            other=false;try{viewer.rollerLink("offer",new JSONObject(link.toString()).put("sequence",2).put("tripEnded",true));}catch(IllegalArgumentException expected){other=true;}
            check(other,"return is refused until fresh BMS data actually confirms the discharge output is locked");
            reply=viewer.rollerLink("offer",new JSONObject(link.toString()).put("sequence",3).put("tripEnded",true));
            check(reply.getBoolean("returnRequested")&&handover.remoteOwns(),"a previously locked roller can be returned after ride end and stable home contact without another lock command");
            viewer.rollerLink("released",link);check(handover.remoteOwns(),"return waits for confirmed rider disconnect and a settling interval");Thread.sleep(1600);
            check(!handover.remoteOwns(),"the host can reconnect after the rider has released the link");
            handover.hostSample(BmsPacket.decode(frame));reply=viewer.rollerLink("inspect",link);
            check(reply.getBoolean("hostFresh")&&reply.getString("phase").equals("host")&&source.running,"only a fresh host BMS sample completes the return; solar recording remains active throughout");
            other=false;try{viewer.rollerLink("take",new JSONObject(link.toString()).put("operation",1));}catch(IllegalArgumentException expected){other=true;}
            check(other&&handover.snapshot().getString("phase").equals("host"),"a late authenticated take cannot undo a newer completed return");
            viewer.rollerLink("take",new JSONObject(link.toString()).put("epoch",2).put("token","21234567-89ab-cdef-0123-456789abcdef"));
            other=false;try{viewer.rollerLink("take",link);}catch(IllegalArgumentException expected){other=true;}
            check(other,"a delayed request from an earlier service process cannot replace the new reader epoch");
            Field expiry=RollerHandover.class.getDeclaredField("expires");expiry.setAccessible(true);expiry.setLong(handover,android.os.SystemClock.elapsedRealtime()-1);
            check(!handover.remoteOwns(),"an unreachable or crashed riding app cannot leave a permanent reader reservation");
            check(!viewer.rollerLink("inspect",new JSONObject(link.toString()).put("mac","02:00:00:00:00:01")).optBoolean("available"),"a different scooter remains independent of this plant's BMS ownership");
            TestContext rejectedContext=new TestContext(root.resolve("wrong-key"));repository(rejectedContext);wrong=sync(rejectedContext);JSONObject wrongCode=new JSONObject(invitation.toString()).put("key",LanWire.random(32));
            rejected=false;try{wrong.join("WNP1."+Base64.getUrlEncoder().withoutPadding().encodeToString(wrongCode.toString().getBytes(StandardCharsets.UTF_8)));}catch(IOException expected){rejected=true;}
            check(rejected&&!wrong.viewer(),"an invalid key cannot pair or replace the independent local data source");
            restarted=sync(display);check(!restarted.state().getBoolean("connected")&&!new JSONObject(restarted.snapshot()).getBoolean("running"),"process restart retains the cached display but never marks it live");
            host.stopHost();Field received=LanSync.class.getDeclaredField("receivedElapsed");received.setAccessible(true);received.setLong(viewer,android.os.SystemClock.elapsedRealtime()-11000);
            check(!viewer.state().getBoolean("connected")&&!new JSONObject(viewer.snapshot()).getBoolean("running"),"a missing source expires the live status without inventing fresh measurements");
            viewer.leave();check(!viewer.viewer()&&own.db.last("AA:00:00:00:00:01").power==-999,"leaving the plant restores access to unchanged independent measurements");
        }finally{close(host);if(viewer!=null)close(viewer);if(restarted!=null)close(restarted);if(wrong!=null)close(wrong);}
    }
}
