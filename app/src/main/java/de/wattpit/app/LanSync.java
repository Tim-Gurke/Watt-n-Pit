package de.wattpit.app;

import android.content.*;
import android.net.nsd.*;
import android.os.*;
import de.wattpit.app.core.LanWire;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Optional, read-only plant display in the local WLAN. Never merges independent databases. */
final class LanSync {
    private static LanSync instance;
    static synchronized LanSync get(Context context){if(instance==null)instance=new LanSync(context.getApplicationContext());return instance;}
    static final String READ_ONLY="Bitte diese Einstellung am Hauptgerät ändern.";
    private static final String TYPE="_wattpit._tcp.";
    private final Context context;
    private final SharedPreferences prefs;
    private final Repository repository;
    private final NsdManager nsd;
    private final ScheduledExecutorService client=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean refreshing=new AtomicBoolean();
    private final Map<String,JSONObject> peers=new ConcurrentHashMap<>();
    private final LinkedHashMap<String,Boolean> requests=new LinkedHashMap<>();
    private volatile JSONObject config,remote=new JSONObject();
    private volatile long receivedAt,receivedElapsed,lastPlanRequest,lastCacheElapsed;
    private volatile String error="",resolvedHost="";
    private volatile int resolvedPort;
    private volatile boolean visible,hosting;
    private volatile Consumer<JSONObject> listener;
    private ServerSocket server;
    private ExecutorService acceptor;
    private ThreadPoolExecutor connections;
    private NsdManager.RegistrationListener registration;
    private NsdManager.DiscoveryListener discovery;
    private final AtomicBoolean resolving=new AtomicBoolean();
    private SolarPlanning planning;
    private final File cache;

    private LanSync(Context context){
        this.context=context;repository=Repository.get(context);prefs=context.getSharedPreferences("wattpit-lan",Context.MODE_PRIVATE);nsd=context.getSystemService(NsdManager.class);
        try{config=new JSONObject(prefs.getString("config","{}"));}catch(Exception e){config=new JSONObject();}
        if(!prefs.contains("deviceId"))prefs.edit().putString("deviceId",LanWire.random(12)).apply();
        cache=new File(context.getFilesDir(),"lan-display.json");
        if(viewer()&&cache.length()<=LanWire.MAX_RESPONSE)try(InputStream in=new FileInputStream(cache)){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){if(bytes.size()+n>LanWire.MAX_RESPONSE)throw new IOException();bytes.write(block,0,n);}JSONObject saved=new JSONObject(bytes.toString("UTF-8"));if(config.optString("id").equals(saved.optString("id"))){remote=saved.getJSONObject("bundle");receivedAt=saved.optLong("at");}}catch(Exception ignored){}
        client.scheduleWithFixedDelay(()->{if(visible&&viewer())refresh();},0,3,TimeUnit.SECONDS);
    }
    boolean viewer(){return "viewer".equals(config.optString("role"));}
    boolean host(){return "host".equals(config.optString("role"));}
    void hostFailed(){error="Anlagenverbund konnte nicht gestartet werden. App erneut öffnen und WLAN prüfen.";}
    void foreground(Consumer<JSONObject> callback){visible=true;listener=callback;if(viewer()){discover();client.execute(this::refresh);}}
    void background(){visible=false;listener=null;if(viewer())stopDiscovery();}
    private void save(JSONObject next)throws Exception{
        if(!prefs.edit().putString("config",next.toString()).commit())throw new IOException();config=next;
    }
    synchronized void create(String name)throws Exception{
        name=name.trim();if(name.isEmpty()||name.length()>60)throw new IllegalArgumentException("Bitte einen Namen mit 1 bis 60 Zeichen eingeben.");
        if(viewer()||hosting)throw new IllegalArgumentException("Bitte den bestehenden Anlagenverbund zuerst verlassen.");
        save(new JSONObject().put("role","host").put("name",name).put("id",LanWire.random(16)).put("key",LanWire.random(32)));
        error="";
    }
    private static JSONObject invitation(String text)throws Exception{
        try{
        JSONObject input=new JSONObject(LanWire.decodeInvitation(text));
        if(!input.optString("id").matches("[A-Za-z0-9_-]{22}")||input.optString("name").isEmpty()||input.optString("name").length()>60)throw new IllegalArgumentException("Ungültiger Verbindungscode.");
        try{LanWire.key(input.getString("key"));}catch(IllegalArgumentException e){throw new IllegalArgumentException(LanWire.INVALID_INVITATION);}int port=input.getInt("port");if(port<1024||port>65535)throw new IllegalArgumentException("Ungültige Hauptgerät-Adresse.");
        JSONArray addresses=input.getJSONArray("addresses");if(addresses.length()==0||addresses.length()>8)throw new IllegalArgumentException("Keine lokale Hauptgerät-Adresse im Code.");
        for(int i=0;i<addresses.length();i++)if(!LanWire.localAddress(addresses.getString(i)))throw new IllegalArgumentException("Nur lokale WLAN-Adressen sind erlaubt.");
        // Whitelist fields: invitations can never supply a command, path or external URL.
        return new JSONObject().put("role","viewer").put("id",input.getString("id")).put("name",input.getString("name")).put("key",input.getString("key")).put("port",port).put("addresses",addresses);
        }catch(JSONException e){throw new IllegalArgumentException(LanWire.INVALID_INVITATION);}
    }
    synchronized void join(String code)throws Exception{
        if(host()||viewer())throw new IllegalArgumentException("Bitte den bestehenden Anlagenverbund zuerst verlassen.");
        if(repository.archiveBusy)throw new IllegalArgumentException("Bitte den Geräteabruf zuerst abschließen oder abbrechen.");
        JSONObject next=invitation(code),bundle=rpc(next,"bundle",new JSONArray());validateBundle(bundle);
        next.put("previousRecording",repository.prefs.getBoolean("recording",false));save(next);
        context.stopService(new Intent(context,MonitorService.class));remote=new JSONObject();receivedElapsed=lastCacheElapsed=0;resolvedHost="";
        accept(bundle,next);discover();
    }
    synchronized boolean leave()throws Exception{
        boolean resume=viewer()&&config.optBoolean("previousRecording");stopHost();stopDiscovery();save(new JSONObject());
        remote=new JSONObject();receivedAt=receivedElapsed=lastCacheElapsed=0;resolvedHost="";resolvedPort=0;error="";cache.delete();return resume;
    }
    String pairingCode()throws Exception{
        if(!host()||!hosting)throw new IllegalArgumentException("Der Anlagenverbund ist noch nicht bereit.");
        JSONArray addresses=addresses();if(addresses.length()==0)throw new IllegalArgumentException("Bitte WLAN oder Hotspot einschalten.");
        JSONObject token=new JSONObject().put("id",config.getString("id")).put("name",config.getString("name")).put("key",config.getString("key")).put("port",server.getLocalPort()).put("addresses",addresses);
        return LanWire.encodeInvitation(token.toString());
    }
    private static JSONArray addresses()throws Exception{
        JSONArray out=new JSONArray();Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
        while(interfaces!=null&&interfaces.hasMoreElements()){NetworkInterface net=interfaces.nextElement();if(!net.isUp()||net.isLoopback())continue;Enumeration<InetAddress> values=net.getInetAddresses();
            while(values.hasMoreElements()){InetAddress value=values.nextElement();if(value instanceof Inet4Address&&LanWire.localAddress(value.getHostAddress())&&!value.isLoopbackAddress()&&out.length()<8)out.put(value.getHostAddress());}}
        return out;
    }
    JSONObject state(){
        JSONObject result=new JSONObject();try{
            long now=SystemClock.elapsedRealtime();JSONArray connected=new JSONArray();for(JSONObject peer:peers.values())if(now-peer.optLong("seen")<20000)connected.put(new JSONObject().put("name",peer.optString("name")));
            result.put("role",config.optString("role","off")).put("name",config.optString("name")).put("hosting",hosting).put("connected",viewer()?receivedElapsed>0&&now-receivedElapsed<10000:hosting)
                .put("receivedAt",receivedAt).put("error",error).put("peers",connected).put("address",resolvedHost);
            if(hosting)result.put("addresses",addresses()).put("port",server.getLocalPort());
        }catch(Exception ignored){}return result;
    }
    String snapshot(){
        try{
            JSONObject result=new JSONObject(remote.optJSONObject("snapshot")==null?"{\"devices\":[],\"found\":[],\"readings\":[],\"stats\":{},\"settings\":{}}":remote.getJSONObject("snapshot").toString());
            JSONObject settings=result.getJSONObject("settings");for(String key:new String[]{"theme","palette","miniRange"})settings.put(key,repository.prefs.getString(key,key.equals("theme")?"dark":key.equals("palette")?"classic":"today"));
            if(!state().optBoolean("connected"))result.put("running",false);
            return result.put("lan",state()).toString();
        }catch(Exception e){return "{}";}
    }
    String part(String name){JSONObject value=remote.optJSONObject(name);if(value==null)return "{}";try{JSONObject result=new JSONObject(value.toString());if(name.equals("roller")&&!state().optBoolean("connected"))result.put("fresh",false).put("status","Hauptgerät nicht erreichbar");return result.toString();}catch(Exception e){return "{}";}}
    void refreshNow(){if(viewer())client.execute(this::refresh);}
    private void refresh(){
        if(!viewer()||!refreshing.compareAndSet(false,true))return;JSONObject active=config;
        try{JSONObject bundle=rpc(active,"bundle",new JSONArray());validateBundle(bundle);accept(bundle,active);}
        catch(Exception e){if(active==config)error="Hauptgerät nicht erreichbar. WLAN und Watt’n’Pit am Hauptgerät prüfen.";}
        finally{refreshing.set(false);}
    }
    private static void validateBundle(JSONObject bundle)throws JSONException{
        JSONObject snapshot=bundle.getJSONObject("snapshot");snapshot.getJSONArray("devices");snapshot.getJSONArray("readings");snapshot.getJSONObject("settings");
    }
    private synchronized void accept(JSONObject bundle,JSONObject expected){
        if(expected!=config||!viewer())return;
        remote=bundle;receivedAt=System.currentTimeMillis();receivedElapsed=SystemClock.elapsedRealtime();error="";
        // A cached display never becomes live merely because the Activity or process restarts.
        // Live refreshes stay in memory; avoid rewriting the full weather/learning bundle
        // every three seconds. The saved display is explicitly timestamped on restart.
        if(lastCacheElapsed==0||receivedElapsed-lastCacheElapsed>=60000)try{File temporary=new File(cache.getPath()+".tmp");try(OutputStream out=new FileOutputStream(temporary)){out.write(new JSONObject().put("id",config.optString("id")).put("at",receivedAt).put("bundle",bundle).toString().getBytes(StandardCharsets.UTF_8));}if(temporary.renameTo(cache))lastCacheElapsed=receivedElapsed;else temporary.delete();}catch(Exception ignored){}
        Consumer<JSONObject> callback=listener;if(callback!=null)callback.accept(bundle);
    }
    JSONObject query(String method,JSONArray args)throws Exception{
        if(!viewer())throw new IllegalStateException();JSONObject active=config;JSONObject result=rpc(active,method,args);if(active!=config)throw new IOException("Anlage wurde gewechselt.");return result;
    }
    private JSONObject rpc(JSONObject target,String method,JSONArray args)throws Exception{
        List<String> hosts=new ArrayList<>();int port=target.getInt("port");
        if(target==config&&LanWire.localAddress(resolvedHost)){hosts.add(resolvedHost);if(resolvedPort>0)port=resolvedPort;}
        JSONArray listed=target.getJSONArray("addresses");for(int i=0;i<listed.length();i++)if(!hosts.contains(listed.getString(i)))hosts.add(listed.getString(i));
        for(String host:hosts){if(!LanWire.localAddress(host))continue;try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress(host,port),1800);socket.setSoTimeout(10000);
            String nonce=LanWire.random(18);JSONObject request=new JSONObject().put("group",target.getString("id")).put("nonce",nonce).put("at",System.currentTimeMillis())
                .put("peer",prefs.getString("deviceId","")).put("name",Build.MODEL).put("method",method).put("args",args);
            byte[] key=LanWire.key(target.getString("key"));LanWire.write(socket.getOutputStream(),key,request.toString(),LanWire.MAX_REQUEST);
            JSONObject response=new JSONObject(LanWire.read(socket.getInputStream(),key,LanWire.MAX_RESPONSE));
            if(!nonce.equals(response.optString("nonce")))throw new IOException("Falsche Antwort.");if(response.has("error"))throw new IOException(response.getString("error"));
            if(target==config){resolvedHost=host;resolvedPort=port;}return response.getJSONObject("data");
        }catch(Exception ignored){/* Try another local interface from the same invitation. */}}
        throw new IOException("Hauptgerät nicht erreichbar oder Verbindungscode ungültig.");
    }
    synchronized void startHost()throws Exception{
        if(!host()||hosting)return;
        connections=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(6));
        server=new ServerSocket();server.setReuseAddress(true);try{server.bind(new InetSocketAddress(28760));}catch(IOException used){server.bind(new InetSocketAddress(0));}
        server.setSoTimeout(2000);hosting=true;planning=new SolarPlanning(context,repository,result->{});
        final ServerSocket currentServer=server;
        acceptor=Executors.newSingleThreadExecutor();acceptor.execute(()->{while(hosting&&server==currentServer){try{
            Socket socket=currentServer.accept();if(!LanWire.localAddress(socket.getInetAddress().getHostAddress())){socket.close();continue;}
            try{connections.execute(()->serve(socket));}catch(RejectedExecutionException busy){socket.close();}
        }catch(SocketTimeoutException ignored){}catch(Exception e){if(hosting)error="Lokale Verbindung wurde unterbrochen.";}}});
        register();error="";
    }
    private void serve(Socket socket){
        try(Socket open=socket){open.setSoTimeout(10000);JSONObject active=config;byte[] key=LanWire.key(active.getString("key"));
            JSONObject request=new JSONObject(LanWire.read(open.getInputStream(),key,LanWire.MAX_REQUEST));String nonce=request.getString("nonce");
            if(!host()||!active.getString("id").equals(request.optString("group"))||!nonce.matches("[A-Za-z0-9_-]{24}")||Math.abs(System.currentTimeMillis()-request.optLong("at"))>120000)return;
            synchronized(requests){if(requests.containsKey(nonce))return;requests.put(nonce,true);if(requests.size()>512)requests.remove(requests.keySet().iterator().next());}
            JSONObject response=new JSONObject().put("nonce",nonce);try{response.put("data",read(request.getString("method"),request.getJSONArray("args")));}catch(Exception e){response.put("error","Daten konnten gerade nicht gelesen werden.");}
            if(!host()||active!=config)return;
            LanWire.write(open.getOutputStream(),key,response.toString(),LanWire.MAX_RESPONSE);
            if(peers.size()<20||peers.containsKey(request.optString("peer")))peers.put(request.optString("peer"),new JSONObject().put("name",request.optString("name").substring(0,Math.min(60,request.optString("name").length()))).put("seen",SystemClock.elapsedRealtime()));
        }catch(Exception ignored){/* Authentication errors never reveal data or key material. */}
    }
    private JSONObject read(String method,JSONArray args)throws Exception{
        if(method.equals("bundle")){
            long now=System.currentTimeMillis();if(!repository.archiveBusy&&now-lastPlanRequest>30000){lastPlanRequest=now;planning.request();}
            JSONObject snapshot=repository.archiveBusy?new JSONObject(repository.backgroundSnapshot()):repository.snapshot();snapshot.put("found",new JSONArray()).remove("archiveDiagnostics");snapshot.remove("event");snapshot.remove("eventId");
            return new JSONObject().put("snapshot",snapshot).put("setup",planning.setup()).put("planning",new JSONObject(SolarPlanning.sharedKnowledge)).put("roller",RollerStore.get(context).snapshot());
        }
        long from=args.getLong(0);if(method.equals("sunsetSoc")){
            if(from<=0||from>System.currentTimeMillis())throw new IllegalArgumentException();String shunt="";JSONArray devices=repository.configs();
            for(int i=0;i<devices.length();i++)if(devices.getJSONObject(i).optString("kind").equals("shunt")){shunt=devices.getJSONObject(i).optString("mac");break;}
            JSONObject point=shunt.isEmpty()?null:repository.db.sunsetSoc(from,System.currentTimeMillis(),shunt);return new JSONObject().put("point",point==null?JSONObject.NULL:point);
        }
        long to=args.getLong(1);if(from<0||to<=from||to-from>366L*86400000||to>System.currentTimeMillis()+86400000)throw new IllegalArgumentException();
        switch(method){
            case "history":return new JSONObject().put("points",repository.db.query(from,to,args.getString(2),args.getString(3)));
            case "dashboardHistory":return repository.db.dashboardData(from,to,args.getString(2));
            case "energyHistoryWindow":return repository.db.energyFlow(from,to,args.getLong(3),args.getLong(4),args.getString(2));
            case "energyHistory":return repository.db.energyFlow(from,to,args.getLong(3),args.getString(2));
            case "dailyEnergy":return repository.db.dailyEnergy(from,to,new JSONObject(repository.prefs.getString("weatherCache","{}")));
            default:throw new IllegalArgumentException();
        }
    }
    synchronized void stopHost(){
        hosting=false;if(server!=null)try{server.close();}catch(IOException ignored){}
        if(acceptor!=null)acceptor.shutdownNow();if(connections!=null)connections.shutdownNow();if(planning!=null){planning.shutdown();planning=null;}
        if(registration!=null&&nsd!=null)try{nsd.unregisterService(registration);}catch(Exception ignored){}registration=null;peers.clear();synchronized(requests){requests.clear();}
    }
    private void register(){
        if(nsd==null)return;NsdServiceInfo service=new NsdServiceInfo();service.setServiceName("WattPit-"+config.optString("id").substring(0,8));service.setServiceType(TYPE);service.setPort(server.getLocalPort());service.setAttribute("group",config.optString("id"));
        registration=new NsdManager.RegistrationListener(){public void onServiceRegistered(NsdServiceInfo info){}public void onRegistrationFailed(NsdServiceInfo info,int code){}public void onServiceUnregistered(NsdServiceInfo info){}public void onUnregistrationFailed(NsdServiceInfo info,int code){}};
        try{nsd.registerService(service,NsdManager.PROTOCOL_DNS_SD,registration);}catch(Exception ignored){}
    }
    @SuppressWarnings("deprecation")
    private synchronized void discover(){
        if(nsd==null||discovery!=null||!viewer()||!visible)return;
        discovery=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String type){}public void onDiscoveryStopped(String type){}public void onStartDiscoveryFailed(String type,int code){stopDiscovery();}public void onStopDiscoveryFailed(String type,int code){}public void onServiceLost(NsdServiceInfo info){}
            public void onServiceFound(NsdServiceInfo info){if(!viewer()||!info.getServiceName().startsWith("WattPit-"+config.optString("id").substring(0,8))||!resolving.compareAndSet(false,true))return;
                try{nsd.resolveService(info,new NsdManager.ResolveListener(){public void onResolveFailed(NsdServiceInfo value,int code){resolving.set(false);}public void onServiceResolved(NsdServiceInfo value){
                    resolving.set(false);byte[] group=value.getAttributes().get("group");String host=value.getHost()==null?"":value.getHost().getHostAddress();
                    if(viewer()&&group!=null&&config.optString("id").equals(new String(group,StandardCharsets.UTF_8))&&LanWire.localAddress(host)){resolvedHost=host;resolvedPort=value.getPort();refreshNow();}
                }});}catch(Exception e){resolving.set(false);}}
        };
        try{nsd.discoverServices(TYPE,NsdManager.PROTOCOL_DNS_SD,discovery);}catch(Exception e){discovery=null;}
    }
    private synchronized void stopDiscovery(){if(discovery!=null&&nsd!=null)try{nsd.stopServiceDiscovery(discovery);}catch(Exception ignored){}discovery=null;}
}
