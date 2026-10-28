package de.wattpit.app;

import android.content.Context;
import android.os.SystemClock;
import de.wattpit.app.core.BmsPacket;
import org.json.*;
import java.util.Base64;

/** Authenticated plant-wide reader ownership. Never issues a BMS control command. */
final class RollerHandover {
    private static RollerHandover instance;
    static synchronized RollerHandover get(Context c){if(instance==null)instance=new RollerHandover(c.getApplicationContext());return instance;}
    private final Context context;
    private String phase="host",peer="",token="",leasedMac="",name="Fahrhandy",message="Zentrale liest das Roller-BMS";
    private long expires,readyAt,resumeAt,lastPeer,homeSince,lastSequence=-1,remoteAt,hostAt,reconnectRevision;
    private boolean closed,remoteDischarge,hostDischarge;
    private final JSONArray events=new JSONArray();
    private final java.util.Map<String,Long> operations=new java.util.HashMap<>();
    private RollerHandover(Context c){context=c;}
    private void event(String text){message=text;try{
        if(events.length()>0&&text.equals(events.getJSONObject(events.length()-1).optString("text")))return;
        events.put(new JSONObject().put("at",System.currentTimeMillis()).put("text",text));if(events.length()>60)events.remove(0);
    }catch(JSONException ignored){}}
    private void expire(long now){if(!peer.isEmpty()&&(now>=expires||!leasedMac.equals(RollerStore.get(context).mac()))){phase="host";peer=token=leasedMac="";closed=false;readyAt=resumeAt=0;event("Übergabe beendet · Zentrale versucht die Verbindung erneut");}}
    synchronized void reset(){phase="host";peer=token=leasedMac="";closed=false;readyAt=resumeAt=0;event("Anlagenverbund beendet · Zentrale liest selbst");}
    synchronized boolean remoteOwns(){long now=SystemClock.elapsedRealtime();expire(now);return !peer.isEmpty()&&(!phase.equals("returning")||resumeAt==0||now<resumeAt);}
    synchronized void hostClosed(){if(!peer.isEmpty()&&!closed){closed=true;readyAt=SystemClock.elapsedRealtime()+1500;event("Zentrale hat freigegeben · warte auf BMS-Werte vom Fahrhandy");}}
    synchronized void hostSample(BmsPacket p){hostAt=System.currentTimeMillis();hostDischarge=p.dischargeEnabled;if(phase.equals("returning")&&resumeAt>0&&SystemClock.elapsedRealtime()>=resumeAt){phase="host";peer=token="";closed=false;readyAt=resumeAt=0;event("Roller wieder mit Zentrale verbunden");}}
    synchronized long reconnectRevision(){return reconnectRevision;}
    synchronized void reconnect(){reconnectRevision++;event(remoteOwns()?"Fahrhandy liest das BMS · dessen Verbindung bleibt erhalten":"Roller-BMS wird neu verbunden");}
    synchronized JSONObject snapshot()throws JSONException{
        expire(SystemClock.elapsedRealtime());long at=phase.equals("host")?hostAt:remoteAt,age=System.currentTimeMillis()-at;boolean fresh=at>0&&age>=0&&age<=10000;
        return new JSONObject().put("protocol",2).put("phase",phase).put("owner",phase.equals("host")?"Zentrale":name+" · ScootPit").put("status",message)
            .put("at",at).put("fresh",fresh).put("dischargeEnabled",fresh?(phase.equals("host")?hostDischarge:remoteDischarge):JSONObject.NULL).put("events",new JSONArray(events.toString()));
    }
    static JSONObject view(Context c)throws JSONException{JSONObject value=RollerStore.get(c).snapshot();return value.put("handover",get(c).snapshot());}
    synchronized JSONObject handle(String caller,String action,JSONObject data)throws Exception{
        long now=SystemClock.elapsedRealtime();expire(now);String mac=data.optString("mac","").toUpperCase(java.util.Locale.ROOT);
        if(!mac.equals(RollerStore.get(context).mac())||mac.isEmpty())return new JSONObject().put("protocol",2).put("available",false);
        if(!java.util.Arrays.asList("inspect","take","offer","released","cancel").contains(action))throw new IllegalArgumentException("Unbekannte BMS-Übergabe.");
        String request=data.optString("token");if(!request.matches("[A-Za-z0-9-]{16,80}")||!caller.matches("[A-Za-z0-9_-]{16}"))throw new IllegalArgumentException("Ungültige BMS-Anfrage.");
        android.content.SharedPreferences state=context.getSharedPreferences("wattpit-roller-ownership",Context.MODE_PRIVATE);
        long epoch=data.optLong("epoch",0),previousEpoch=state.getLong("epoch."+caller,0);
        if(epoch<=0||epoch<previousEpoch)throw new IllegalArgumentException("Veraltete BMS-Übergabeanfrage verworfen.");
        long operation=data.optLong("operation",0),previousOperation=epoch>previousEpoch?0:operations.getOrDefault(caller,0L);
        if(operation<=0||operation<previousOperation)throw new IllegalArgumentException("Verspätete BMS-Übergabeanfrage verworfen.");
        if(operations.size()<20||operations.containsKey(caller))operations.put(caller,operation);
        boolean own=caller.equals(peer)&&request.equals(token);
        if(action.equals("take")){
            if(!peer.isEmpty()&&!own&&(!caller.equals(peer)||epoch<=previousEpoch))throw new IllegalArgumentException("Ein anderes Fahrhandy übernimmt bereits das BMS.");
            if(epoch>previousEpoch&&!state.edit().putLong("epoch."+caller,epoch).commit())throw new IllegalArgumentException("Übergabe konnte nicht gespeichert werden.");
            if(!own){peer=caller;token=request;leasedMac=mac;phase="releasing";closed=false;readyAt=resumeAt=0;lastSequence=-1;remoteAt=0;event("Fahrhandy fordert das Roller-BMS an");}
            if(now-lastPeer>7000||homeSince==0)homeSince=now;lastPeer=now;expires=now+45000;
            if(!Repository.get(context).running)hostClosed();
            if(phase.equals("returning")){phase=closed?"s20":"releasing";resumeAt=0;event("Neuer Fahrwunsch · Fahrhandy übernimmt");}
        }
        own=caller.equals(peer)&&request.equals(token);
        if(own&&(action.equals("take")||action.equals("offer"))){
            if(now-lastPeer>7000||homeSince==0)homeSince=now;lastPeer=now;expires=now+45000;
            long seq=data.optLong("sequence",-1),age=data.optLong("age",Long.MAX_VALUE);
            if(seq>lastSequence&&age>=0&&age<=8000&&data.optString("frame").length()<=1400&&closed&&now>=readyAt){
                BmsPacket packet=null;try{packet=BmsPacket.decode(Base64.getDecoder().decode(data.optString("frame")));}catch(IllegalArgumentException ignored){}
                if(packet!=null){lastSequence=seq;remoteAt=System.currentTimeMillis()-age;remoteDischarge=packet.dischargeEnabled;
                    RollerStore.get(context).accept(packet,remoteAt,"ScootPit · Anlagenverbund");
                    if(!phase.equals("returning")){phase="s20";event("Fahrhandy hat übernommen · aktuelle BMS-Werte empfangen");}
                }
            }
        }
        if(action.equals("offer")){
            if(!own)throw new IllegalArgumentException("Diese BMS-Verbindung gehört nicht zu diesem Fahrhandy.");
            if(!Repository.get(context).running)throw new IllegalArgumentException("Aufzeichnung der Zentrale pausiert · Fahrhandy bleibt zuständig.");
            long age=System.currentTimeMillis()-remoteAt;
            if(!data.optBoolean("tripEnded")||age<0||age>4000||remoteDischarge||homeSince==0||now-homeSince<6000)throw new IllegalArgumentException("Rückgabe wartet auf Fahrtende, bestätigte Sperre und eine stabile Verbindung.");
            phase="returning";event("BMS gesperrt · Rückgabe an die Zentrale wird abgestimmt");
        }
        if(action.equals("released")&&own&&phase.equals("returning")){
            if(resumeAt==0){resumeAt=now+1500;expires=now+45000;event("Fahrhandy hat freigegeben · Zentrale verbindet sich");}
        }
        if(action.equals("cancel")&&own&&remoteAt==0){expires=now;expire(now);}
        return new JSONObject().put("protocol",2).put("available",true).put("phase",phase).put("granted",own&&closed&&now>=readyAt&&!phase.equals("returning"))
            .put("returnRequested",own&&phase.equals("returning")&&resumeAt==0).put("hostFresh",phase.equals("host")&&hostAt>0&&System.currentTimeMillis()-hostAt<=10000)
            .put("owner",phase.equals("host")?"Zentrale":name).put("message",message);
    }
}
