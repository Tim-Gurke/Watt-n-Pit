package de.wattpit.app;
import android.content.*;
import de.wattpit.app.core.*;
import org.json.*;
import java.nio.file.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public final class RollerStoreProof {
 private static void require(boolean v,String name){if(!v)throw new AssertionError(name);System.out.println("PASS "+name);}
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
        private final Path path; private final Map<String,SharedPreferences> cache=new HashMap<>();
        TestContext(Path path)throws Exception{super(null);this.path=path;}
        @Override public Context getApplicationContext(){return this;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){try{return cache.computeIfAbsent(name,k->{try{return preferences(path.resolve(k+".properties"));}catch(Exception e){throw new RuntimeException(e);}});}catch(Exception e){throw new RuntimeException(e);}}
    }

 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]);Files.createDirectories(root);System.setProperty("wattpit.test.database",root.resolve("history.db").toString());System.setProperty("wattpit.test.namedDatabases","true");Class.forName("org.sqlite.JDBC");TestContext c=new TestContext(root);RollerStore s=RollerStore.get(c);
  JSONObject p=RollerStore.checked(new JSONObject());p.put("mac","AA:BB:CC:DD:EE:FF");s.configure(p.toString());
  require(s.mac().equals("AA:BB:CC:DD:EE:FF"),"roller profile stored");
  boolean rejected=false;try{s.toggle(true);}catch(IllegalArgumentException e){rejected=true;}require(rejected,"no charge session without a fresh BMS");
  byte[] b=new byte[30];b[0]=(byte)0xdd;b[1]=3;b[3]=23;b[4]=0x13;b[5]=(byte)0xd6;b[7]=(byte)189;b[23]=70;int sum=0;for(int i=2;i<27;i++)sum+=b[i]&255;int checksum=(-sum)&65535;b[27]=(byte)(checksum>>8);b[28]=(byte)checksum;b[29]=0x77;BmsPacket packet=BmsPacket.decode(b);require(packet!=null,"valid production packet");
  long now=System.currentTimeMillis();s.accept(packet,now);s.toggle(true);Thread.sleep(5);now=System.currentTimeMillis();s.accept(packet,now);
  JSONObject live=s.snapshot();require(!live.getBoolean("fresh")||Math.abs(live.getDouble("estimatedInputWatts")-138)<.1,"calibrated power or stale BMS");
  rejected=false;try{s.configure(p.toString());}catch(IllegalArgumentException e){rejected=true;}require(rejected,"active session freezes its loss profile");
  s.toggle(false);s.toggle(false);require(s.snapshot().getJSONArray("journal").length()==1,"one completed session archived exactly once");
  require(s.snapshot().getJSONObject("session").getDouble("batteryWh")>0,"valid BMS intervals integrated");
  JSONArray windows=new JSONArray(c.getSharedPreferences("wattpit",0).getString("rollerChargeWindows","[]"));require(windows.length()==1&&windows.getJSONObject(0).getLong("to")>0,"household-learning exclusion survives session end");
  s.accept(packet,System.currentTimeMillis());s.toggle(true);Field field=RollerStore.class.getDeclaredField("instance");field.setAccessible(true);field.set(null,null);RollerStore resumed=RollerStore.get(new TestContext(root));JSONObject result=resumed.snapshot();
  require(!result.getJSONObject("session").getBoolean("active"),"process restart ends rather than fabricates an open session");
  require(result.getJSONArray("journal").length()==2,"both sessions survive process restart");
  require(result.getJSONObject("live").length()==0,"restart never exposes old BMS readings as live");
  require(result.getJSONObject("profile").getString("mac").equals("AA:BB:CC:DD:EE:FF"),"profile survives restart");
  field.set(null,null);TestContext automaticContext=new TestContext(Files.createDirectories(root.resolve("automatic")));RollerStore auto=RollerStore.get(automaticContext);
  p.put("autoSpecialUse",true);auto.configure(p.toString());long at=System.currentTimeMillis()-300000;
  for(int i=0;i<=3;i++)auto.accept(packet,at+i*2000);
  require(auto.snapshot().getJSONObject("session").getBoolean("active")&&auto.snapshot().getJSONObject("session").getBoolean("automatic"),"fresh positive BMS samples automatically start the special-use session");
  require(auto.snapshot().getJSONObject("session").getLong("started")==at&&auto.snapshot().getJSONObject("session").getDouble("batteryWh")>0,"confirmation retains the measured charge start and first energy interval");
  double measured=auto.snapshot().getJSONObject("session").getDouble("batteryWh");auto.accept(packet,at+66000);
  require(auto.snapshot().getJSONObject("session").getBoolean("active")&&auto.snapshot().getJSONObject("session").getDouble("batteryWh")==measured,"missing BMS time neither ends the charge nor fabricates energy");
  auto.accept(packet,at+68000);
  JSONArray spans=new JSONArray(automaticContext.getSharedPreferences("wattpit",0).getString("rollerChargeWindows","[]"));
  boolean finite=true,noGap=true;for(int i=0;i<spans.length();i++){JSONObject span=spans.getJSONObject(i);finite&=span.getLong("to")>span.getLong("from");noGap&=!(span.getLong("from")<at+66000&&span.getLong("to")>at+6000);}
  require(finite&&noGap&&spans.length()==2,"automatic learning exclusions cover observed charge intervals and leave reception gaps open");
  byte[] idle=b.clone();idle[6]=idle[7]=0;sum=0;for(int i=2;i<27;i++)sum+=idle[i]&255;checksum=(-sum)&65535;idle[27]=(byte)(checksum>>8);idle[28]=(byte)checksum;BmsPacket zero=BmsPacket.decode(idle);
  for(int i=0;i<=30;i++)auto.accept(zero,at+70000+i*2000);
  require(!auto.snapshot().getJSONObject("session").getBoolean("active")&&auto.snapshot().getJSONArray("journal").length()==1,"confirmed BMS charge end closes and archives exactly one automatic session");
  at=System.currentTimeMillis();auto.accept(packet,at);long rev=auto.snapshot().getLong("sessionRevision");String startId=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);auto.command(true,rev,startId);auto.command(true,rev,startId);
  require(auto.snapshot().getJSONObject("session").getBoolean("active")&&auto.snapshot().getLong("sessionRevision")==rev+1,"a repeated remote start is acknowledged once without duplicating a session");
  String stopId="B"+startId.substring(1);auto.command(false,rev+1,stopId);auto.command(false,rev+1,stopId);
  rejected=false;try{auto.command(true,rev,"C"+startId.substring(1));}catch(IllegalArgumentException expected){rejected=true;}
  require(rejected&&!auto.snapshot().getJSONObject("session").getBoolean("active"),"stale commands cannot restart an already completed charge");
  for(int i=1;i<=5;i++)auto.accept(packet,at+i*2000);
  require(!auto.snapshot().getJSONObject("session").getBoolean("active")&&auto.snapshot().getBoolean("autoSuppressed"),"manual stop is respected while the physical charger remains on");
  for(int i=0;i<=30;i++)auto.accept(zero,at+12000+i*2000);
  for(int i=0;i<=3;i++)auto.accept(packet,at+74000+i*2000);
  require(auto.snapshot().getJSONObject("session").getBoolean("active")&&!auto.snapshot().getBoolean("autoSuppressed"),"the next physical charge starts automatically after a confirmed intervening end");

 }
}
