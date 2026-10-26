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
  Path root=Path.of(args[0]);Files.createDirectories(root);TestContext c=new TestContext(root);RollerStore s=RollerStore.get(c);
  JSONObject p=RollerStore.checked(new JSONObject());p.put("mac","AA:BB:CC:DD:EE:FF");s.configure(p.toString());
  require(s.mac().equals("AA:BB:CC:DD:EE:FF"),"roller profile stored");
  boolean rejected=false;try{s.toggle(true);}catch(IllegalArgumentException e){rejected=true;}require(rejected,"no charge session without a fresh BMS");
  byte[] b=new byte[30];b[0]=(byte)0xdd;b[1]=3;b[3]=23;b[4]=0x13;b[5]=(byte)0xd6;b[7]=(byte)189;b[23]=70;int sum=0;for(int i=2;i<27;i++)sum+=b[i]&255;int checksum=(-sum)&65535;b[27]=(byte)(checksum>>8);b[28]=(byte)checksum;b[29]=0x77;BmsPacket packet=BmsPacket.decode(b);require(packet!=null,"valid production packet");
  long now=System.currentTimeMillis();s.accept(packet,now);s.toggle(true);now=System.currentTimeMillis();s.accept(packet,now+1000);
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
 }
}
