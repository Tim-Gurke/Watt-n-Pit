package de.wattpit.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Local Wi-Fi evidence and explicit future plans. A plan never fabricates occupancy history. */
final class PresenceStore extends SQLiteOpenHelper {
    private static PresenceStore instance;
    static synchronized PresenceStore get(Context c){if(instance==null)instance=new PresenceStore(c.getApplicationContext());return instance;}
    private final Context context;
    private final SharedPreferences prefs;
    private String candidate="",lastState="unknown";
    private long candidateElapsed,candidateWall,lastElapsed,lastWall,lastSaved,lastPrune;
    private PresenceStore(Context c){super(c,"wattpit-presence.db",null,1);context=c;prefs=c.getSharedPreferences("wattpit-presence",Context.MODE_PRIVATE);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE events(at INTEGER PRIMARY KEY,state TEXT NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
    synchronized void tick(){
        long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();String signal="unknown";
        try{
            WifiManager wifi=context.getSystemService(WifiManager.class);ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);
            if(wifi!=null&&wifi.isWifiEnabled()&&cm!=null){signal="away";
                for(Network network:cm.getAllNetworks()){
                    NetworkCapabilities caps=cm.getNetworkCapabilities(network);android.net.NetworkInfo info=cm.getNetworkInfo(network);
                    // INTERNET/VALIDATED are deliberately irrelevant for an offline hotspot.
                    if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&info!=null&&info.isConnected()){signal="home";break;}
                }
            }
            observe(signal,now,elapsed);notifyDue(now);
        }catch(Exception ignored){/* Failed observations never manufacture absence. The UI exposes stale evidence. */}
    }
    synchronized void observe(String signal,long now,long elapsed)throws Exception{
        String mode=prefs.getString("mode","auto");if(!mode.equals("auto"))signal=mode;
        if(!Arrays.asList("home","away","unknown").contains(signal))throw new IllegalArgumentException();
        if(lastWall==0){long saved=prefs.getLong("observedAt",0);if(saved>0)event(Math.min(now,saved),"unknown");lastState="unknown";}
        if(lastWall>0&&(elapsed<lastElapsed||elapsed-lastElapsed>65000||Math.abs((now-lastWall)-(elapsed-lastElapsed))>60000)){
            event(Math.min(now,lastWall),"unknown");lastState="unknown";candidate="";
        }
        if(!signal.equals(candidate)){candidate=signal;candidateElapsed=elapsed;candidateWall=now;}
        long delay=mode.equals("auto")?(signal.equals("home")?30000:signal.equals("away")?300000:0):0;
        if(elapsed-candidateElapsed>=delay&&!signal.equals(lastState)){
            String before=lastState;event(candidateWall,signal);lastState=signal;
            JSONArray plans=plans();boolean changed=false;
            for(int i=0;i<plans.length();i++){JSONObject p=plans.getJSONObject(i);String status=p.optString("status");
                if(signal.equals("away")&&p.getLong("from")<=now&&p.getLong("to")>now&&!status.equals("cancelled")&&!status.equals("returned")){p.put("status","active");changed=true;}
                if(signal.equals("home")&&before.equals("away")&&status.equals("active")){p.put("status","returned").put("returnedAt",candidateWall);changed=true;}
            }
            if(changed)savePlans(plans);
        }
        lastElapsed=elapsed;lastWall=now;
        if(now-lastSaved>=30000){prefs.edit().putLong("observedAt",now).commit();lastSaved=now;}
        due(now);
        if(now-lastPrune>3600000){long since=LearningWindow.since(now),anchor=0;try(Cursor c=getReadableDatabase().rawQuery("SELECT MAX(at) FROM events WHERE at<?",new String[]{""+since})){if(c.moveToFirst())anchor=c.getLong(0);}getWritableDatabase().delete("events","at<?",new String[]{""+anchor});lastPrune=now;}
    }
    private void event(long at,String state)throws Exception{
        ContentValues v=new ContentValues();v.put("at",at);v.put("state",state);getWritableDatabase().insertWithOnConflict("events",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    synchronized JSONArray history(long since)throws Exception{
        long anchor=since;try(Cursor c=getReadableDatabase().rawQuery("SELECT MAX(at) FROM events WHERE at<=?",new String[]{""+since})){if(c.moveToFirst()&&!c.isNull(0))anchor=c.getLong(0);}
        JSONArray result=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT at,state FROM events WHERE at>=? ORDER BY at",new String[]{""+anchor})){while(c.moveToNext())result.put(new JSONObject().put("at",c.getLong(0)).put("state",c.getString(1)));}return result;
    }
    static String at(JSONArray events,long from,long to)throws Exception{
        String state="legacy";for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);long t=e.getLong("at");if(t<=from)state=e.getString("state");else if(t<to)return "unknown";else break;}return state;
    }
    private JSONArray plans()throws Exception{return new JSONArray(prefs.getString("plans","[]"));}
    private void savePlans(JSONArray plans)throws Exception{if(!prefs.edit().putString("plans",plans.toString()).putLong("revision",prefs.getLong("revision",0)+1).commit())throw new IOException();}
    private void due(long now)throws Exception{
        if(lastWall==0||now-lastWall>65000)return;
        JSONArray plans=plans();boolean changed=false;
        for(int i=0;i<plans.length();i++){JSONObject p=plans.getJSONObject(i);String status=p.optString("status");
            if(lastState.equals("away")&&status.equals("planned")&&p.getLong("from")<=now&&p.getLong("to")>now){p.put("status","active");changed=true;}
            if(lastState.equals("home")&&status.equals("planned")&&now>=p.getLong("from")+300000&&now<p.getLong("to")){p.put("status","pending");changed=true;}
            if(lastState.equals("home")&&status.equals("snoozed")&&now>=p.optLong("askAfter")&&p.getLong("to")>now){p.put("status","pending");changed=true;}
        }if(changed)savePlans(plans);
    }
    synchronized JSONObject snapshot()throws Exception{
        long now=System.currentTimeMillis();due(now);JSONArray active=new JSONArray(),pending=new JSONArray();
        JSONArray stored=plans();for(int i=0;i<stored.length();i++){JSONObject p=stored.getJSONObject(i);if(p.getLong("to")>=now-30L*86400000){active.put(p);if(p.optString("status").equals("pending")&&p.getLong("to")>now&&lastState.equals("home"))pending.put(p);}}
        String state=lastWall>0&&SystemClock.elapsedRealtime()-lastElapsed<=65000?lastState:"unknown";
        return new JSONObject().put("state",state).put("mode",prefs.getString("mode","auto")).put("observedAt",lastWall).put("revision",prefs.getLong("revision",0)).put("plans",active).put("pending",pending).put("history",history(now-30L*86400000));
    }
    synchronized void mode(String value)throws Exception{
        if(!Arrays.asList("auto","home","away").contains(value))throw new IllegalArgumentException("Bitte den Anwesenheitsmodus prüfen.");
        if(!prefs.edit().putString("mode",value).putLong("revision",prefs.getLong("revision",0)+1).commit())throw new IOException();candidate="";
        observe("unknown",System.currentTimeMillis(),SystemClock.elapsedRealtime());tick();
    }
    synchronized void save(String json)throws Exception{
        if(json.length()>2000)throw new IllegalArgumentException("Der Eintrag ist zu groß.");JSONObject input=new JSONObject(json);long now=System.currentTimeMillis(),from=input.getLong("from"),to=input.getLong("to");
        if(from<now-30L*86400000||from>now+3L*366*86400000||to<=from||to-from>366L*86400000)throw new IllegalArgumentException("Bitte gültige Abfahrts- und Rückkehrzeiten eingeben (höchstens ein Jahr je Abwesenheit).");
        JSONArray old=plans(),next=new JSONArray();String id=input.optString("id");boolean found=id.isEmpty();
        if(!id.isEmpty()&&!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Ungültiger Eintrag.");
        for(int i=0;i<old.length();i++){JSONObject p=old.getJSONObject(i);if(p.getString("id").equals(id)){found=true;continue;}if(p.getLong("to")<LearningWindow.since(now))continue;
            if(!Arrays.asList("cancelled","returned").contains(p.optString("status"))&&p.getLong("from")<to&&p.getLong("to")>from)throw new IllegalArgumentException("Dieser Zeitraum überschneidet sich mit einer anderen Abwesenheit.");next.put(p);}
        if(!found)throw new IllegalArgumentException("Dieser Eintrag wurde inzwischen gelöscht.");if(next.length()>=200)throw new IllegalArgumentException("Bitte zuerst ältere Einträge entfernen.");
        next.put(new JSONObject().put("id",id.isEmpty()?UUID.randomUUID().toString():id).put("from",from).put("to",to).put("status",lastState.equals("away")&&from<=now&&to>now?"active":"planned"));savePlans(next);due(now);
    }
    synchronized void resolve(String id,String action)throws Exception{
        JSONArray next=plans();boolean found=false;
        for(int i=0;i<next.length();i++){JSONObject p=next.getJSONObject(i);if(!p.getString("id").equals(id))continue;found=true;
            if(action.equals("cancel"))p.put("status","cancelled");else if(action.equals("soon")){if(!lastState.equals("home")||!p.optString("status").equals("pending"))throw new IllegalArgumentException("Der Anwesenheitsstatus hat sich geändert. Bitte erneut prüfen.");p.put("status","snoozed").put("askAfter",System.currentTimeMillis()+15*60000);}else throw new IllegalArgumentException("Ungültige Planänderung.");}
        if(!found)throw new IllegalArgumentException("Dieser Eintrag wurde inzwischen gelöscht.");savePlans(next);
    }
    private void notifyDue(long now)throws Exception{notifySnapshot(context,snapshot(),"local");}
    static void notifySnapshot(Context context,JSONObject state,String scope)throws Exception{
        JSONArray pending=state.optJSONArray("pending");android.app.NotificationManager manager=context.getSystemService(android.app.NotificationManager.class);if(manager==null)return;
        if(pending==null||pending.length()==0||state.optBoolean("stale")||!state.optString("state").equals("home")){manager.cancel(16);return;}
        SharedPreferences notices=context.getSharedPreferences("wattpit-presence-notices",Context.MODE_PRIVATE);
        String key=scope+":"+pending.getJSONObject(0).getString("id")+":"+state.optLong("revision");if(key.equals(notices.getString("notified","")))return;
        if(android.os.Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        manager.createNotificationChannel(new android.app.NotificationChannel("wattpit-presence","Abwesenheitsplanung",android.app.NotificationManager.IMPORTANCE_DEFAULT));
        android.app.PendingIntent open=android.app.PendingIntent.getActivity(context,16,new Intent(context,MainActivity.class).putExtra("presencePlan",true),android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE);
        manager.notify(16,new android.app.Notification.Builder(context,"wattpit-presence").setSmallIcon(R.drawable.ic_bolt).setContentTitle("Du bist offenbar noch da")
            .setContentText("Geplante Abwesenheit ändern, absagen oder gleich losfahren?").setContentIntent(open).setAutoCancel(true).build());notices.edit().putString("notified",key).apply();
    }
}
