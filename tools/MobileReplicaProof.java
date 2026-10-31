package de.wattpit.app;

import org.json.*;
import java.nio.file.*;
import java.lang.reflect.*;
import de.wattpit.app.core.LanWire;

/** Reuse the real PWM/curtailment/site-change regression inputs across two TCP viewers. */
public final class MobileReplicaProof {
    public static void main(String[] args)throws Exception{
        Class.forName("org.sqlite.JDBC");Path root=Path.of(args[0]);LanSyncProof.TestContext center=new LanSyncProof.TestContext(root.resolve("central"));Repository source=LanSyncProof.repository(center);
        LanSync host=LanSyncProof.sync(center);host.create("Mobile replica proof");host.startHost();
        Field config=LanSync.class.getDeclaredField("config"),server=LanSync.class.getDeclaredField("server");config.setAccessible(true);server.setAccessible(true);
        String code=LanWire.encodeInvitation(new JSONObject(config.get(host).toString()).put("port",((java.net.ServerSocket)server.get(host)).getLocalPort()).put("addresses",new JSONArray().put("127.0.0.1")).toString());
        LanSyncProof.TestContext phone1=new LanSyncProof.TestContext(root.resolve("s20"));LanSyncProof.repository(phone1);LanSync one=LanSyncProof.sync(phone1);one.join(code);
        LanSyncProof.TestContext phone2=new LanSyncProof.TestContext(root.resolve("s10"));LanSyncProof.repository(phone2);LanSync two=LanSyncProof.sync(phone2);two.join(code);
        final int[] cases={0};boolean success=false;
        try{
            MobileDisplayProof.run(source,center,(from,to,origin,reference)->{
                JSONArray devices=source.configs();for(int i=0;i<devices.length();i++)devices.getJSONObject(i).put("name",devices.getJSONObject(i).optString("kind"));source.prefs.edit().putString("devices",devices.toString()).commit();
                JSONArray q=new JSONArray().put(from).put(to).put(origin).put(from).put(to);JSONObject a=one.query("energyHistoryWindow",q),b=two.query("energyHistoryWindow",q);
                for(String key:new String[]{"solar","consumption","balance","mobile"}){
                    JSONObject expected=reference.getJSONObject(key).getJSONObject("summary"),x=a.getJSONObject(key).getJSONObject("summary"),y=b.getJSONObject(key).getJSONObject("summary");
                    if(!expected.toString().equals(x.toString())||!expected.toString().equals(y.toString()))throw new AssertionError("Replica case "+cases[0]+" "+key+" differs: "+expected+" / "+x+" / "+y);
                }
                cases[0]++;System.out.println("PASS both viewer replicas match native mobile accounting case "+cases[0]);
            });
            if(cases[0]<10)throw new AssertionError("Missing mobile scenarios");success=true;
        }catch(Throwable error){error.printStackTrace();}finally{LanSyncProof.close(two);LanSyncProof.close(one);LanSyncProof.close(host);System.exit(success?0:1);}
    }
}
