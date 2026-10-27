package de.wattpit.app;

import android.content.*;
import android.net.nsd.*;
import android.os.*;
import de.wattpit.app.core.LanWire;
import de.wattpit.app.core.ArchiveRun;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Local plant data and explicitly scoped, acknowledged app controls. Never merges databases. */
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
    private String controlFingerprint="";
    private long controlRevision;

    private LanSync(Context context){
        this.context=context;repository=Repository.get(context);prefs=context.getSharedPreferences("wattpit-lan",Context.MODE_PRIVATE);nsd=context.getSystemService(NsdManager.class);
        try{config=new JSONObject(prefs.getString("config","{}"));}catch(Exception e){config=new JSONObject();}
        if(!prefs.contains("deviceId"))prefs.edit().putString("deviceId",LanWire.random(12)).apply();
        cache=new File(context.getFilesDir(),"lan-display.json");
        controlRevision=prefs.getLong("controlRevision",0);
        if(viewer()&&cache.length()<=LanWire.MAX_RESPONSE)try(InputStream in=new FileInputStream(cache)){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){if(bytes.size()+n>LanWire.MAX_RESPONSE)throw new IOException();bytes.write(block,0,n);}JSONObject saved=new JSONObject(bytes.toString("UTF-8"));if(config.optString("id").equals(saved.optString("id"))){remote=saved.getJSONObject("bundle");receivedAt=saved.optLong("at");}}catch(Exception ignored){}
        client.scheduleWithFixedDelay(()->{if(visible&&viewer())refresh();},0,3,TimeUnit.SECONDS);
    }
    boolean viewer(){return "viewer".equals(config.optString("role"));}
    boolean host(){return "host".equals(config.optString("role"));}
    void hostFailed(){error="Anlagenverbund konnte nicht gestartet werden. App erneut öffnen und WLAN prüfen.";}
    void foreground(Consumer<JSONObject> callback){visible=true;listener=callback;if(viewer()){discover();client.execute(this::refresh);}}
    void background(){visible=false;listener=null;if(viewer())stopDiscovery();}
    private void save(JSONObject next)throws Exception{
        if(!prefs.edit().putString("config",next.toString()).putBoolean("joinedNotice","viewer".equals(next.optString("role"))).commit())throw new IOException();config=next;
    }
    synchronized boolean consumeJoined(){return viewer()&&prefs.getBoolean("joinedNotice",false)&&prefs.edit().putBoolean("joinedNotice",false).commit();}
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
            return result.put("controlRevision",remote.optLong("controlRevision",-1)).put("lan",state()).toString();
        }catch(Exception e){return "{}";}
    }
    String part(String name){JSONObject value=remote.optJSONObject(name);if(value==null)return "{}";try{JSONObject result=new JSONObject(value.toString());if(name.equals("roller")&&!state().optBoolean("connected"))result.put("fresh",false).put("status","Hauptgerät nicht erreichbar");return result.toString();}catch(Exception e){return "{}";}}
    String bondedDevices(){return viewer()?remote.optJSONArray("bonded")==null?"[]":remote.optJSONArray("bonded").toString():PlantCommands.bonded(context).toString();}
    long controlRevision(){if(viewer())return remote.optLong("controlRevision",-1);synchronized(this){try{return currentControlRevision();}catch(Exception e){return -1;}}}
    String remoteSyncState(){JSONObject sync=remote.optJSONObject("sync");try{
        JSONObject value=sync==null?new JSONObject():new JSONObject(sync.toString());
        // Progress is shared, while notice consent, theme and palette belong to this handset.
        JSONObject local=new JSONObject(repository.syncState());for(String k:new String[]{"cockpitNoticeAccepted","theme","palette"})value.put(k,local.opt(k));
        if(!state().optBoolean("connected"))value.put("busy",false).put("archive",new JSONObject());
        return value.toString();
    }catch(Exception e){return "{}";}}
    private String fingerprint()throws Exception{
        JSONArray values=new JSONArray();Map<String,?> all=repository.prefs.getAll();
        for(String key:new String[]{"devices","equipment-v1","solar-setup-v1","weatherPlace","capacity","sampleSeconds","recording"})values.put(all.get(key));
        ArchiveRun.State run=repository.archiveRun.state();values.put(run.id).put(run.startedAt).put(run.busy);
        values.put(RollerStore.get(context).snapshot().optJSONObject("profile"));return digest(values.toString());
    }
    private static String digest(String value)throws Exception{return java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
    private long currentControlRevision()throws Exception{
        String next=fingerprint();if(!next.equals(controlFingerprint)){
            controlRevision++;if(!prefs.edit().putLong("controlRevision",controlRevision).commit())throw new IOException();controlFingerprint=next;
        }return controlRevision;
    }
    synchronized void localControl(String action,JSONArray args,SolarPlanning source)throws Exception{
        if(viewer())throw new IllegalArgumentException(READ_ONLY);
        PlantCommands.apply(context,repository,source,action,args);controlFingerprint="";currentControlRevision();
    }
    synchronized void importLocalConfiguration(String json,SolarPlanning source)throws Exception{
        if(viewer())throw new IllegalArgumentException(READ_ONLY);
        boolean changed=repository.importConfiguration(json);if(changed)WeatherClient.refresh(repository,true);source.request();controlFingerprint="";currentControlRevision();
    }
    JSONObject control(String action,JSONArray args,long expectedRevision)throws Exception{
        if(!viewer())throw new IllegalStateException();JSONObject active=config;
        if(remote.optInt("controlProtocol")!=1)throw new IllegalArgumentException("Bitte zuerst das Hauptgerät auf Version 1.6.3 oder neuer aktualisieren.");
        JSONObject bundle=rpc(active,"control",new JSONArray().put(action).put(args).put(expectedRevision).put(LanWire.random(16)));
        validateBundle(bundle);if(active!=config)throw new IOException("Anlage wurde gewechselt.");accept(bundle,active);return bundle;
    }
    private JSONObject applyControl(JSONArray args)throws Exception{
        if(args.length()!=4||!(args.get(1) instanceof JSONArray))throw new IllegalArgumentException("Ungültige Bedienanfrage.");
        String action=args.getString(0),id=args.getString(3),hash=digest(action+args.getJSONArray(1).toString());
        if(!id.matches("[A-Za-z0-9_-]{22}"))throw new IllegalArgumentException("Ungültige Bedienanfrage.");
        JSONArray receipts=new JSONArray(prefs.getString("controlReceipts","[]"));
        for(int i=0;i<receipts.length();i++){JSONObject receipt=receipts.getJSONObject(i);if(id.equals(receipt.optString("id"))){if(!hash.equals(receipt.optString("hash")))throw new IllegalArgumentException("Die Anfragekennung wurde bereits verwendet.");return read("bundle",new JSONArray());}}
        if(args.getLong(2)!=currentControlRevision())throw new IllegalArgumentException("Die Einstellungen wurden inzwischen geändert. Bitte die Ansicht schließen, neu öffnen und erneut versuchen.");
        PlantCommands.apply(context,repository,planning,action,args.getJSONArray(1));
        controlFingerprint="";currentControlRevision();JSONArray kept=new JSONArray();for(int i=Math.max(0,receipts.length()-31);i<receipts.length();i++)kept.put(receipts.get(i));kept.put(new JSONObject().put("id",id).put("hash",hash));
        if(!prefs.edit().putString("controlReceipts",kept.toString()).commit())throw new IOException();
        return read("bundle",new JSONArray());
    }
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
        if(bundle.optLong("controlRevision",-1)<remote.optLong("controlRevision",-1))return;
        // A slower background poll must not undo a newer, acknowledged charge command.
        JSONObject oldRoller=remote.optJSONObject("roller"),newRoller=bundle.optJSONObject("roller");
        if(oldRoller!=null&&newRoller!=null&&oldRoller.optLong("sessionRevision")>newRoller.optLong("sessionRevision"))try{bundle.put("roller",oldRoller);}catch(JSONException ignored){}
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
    JSONObject rollerSession(boolean start)throws Exception{
        JSONObject roller=remote.optJSONObject("roller");return rollerSession(start,roller==null?-1:roller.optLong("sessionRevision",-1));
    }
    Object identity(){return config;}
    JSONObject rollerSession(boolean start,long expectedRevision)throws Exception{
        if(!viewer())throw new IllegalStateException();JSONObject active=config,roller=remote.optJSONObject("roller");
        if(roller==null||!roller.has("sessionRevision"))throw new IllegalArgumentException("Bitte auch das Hauptgerät auf Version 1.6.2 oder neuer aktualisieren.");
        JSONObject bundle=rpc(active,"rollerSession",new JSONArray().put(start).put(expectedRevision).put(LanWire.random(16)));
        validateBundle(bundle);if(active!=config)throw new IOException("Anlage wurde gewechselt.");accept(bundle,active);return bundle.getJSONObject("roller");
    }
    private JSONObject rpc(JSONObject target,String method,JSONArray args)throws Exception{
        List<String> hosts=new ArrayList<>();int port=target.getInt("port");
        if(target==config&&LanWire.localAddress(resolvedHost)){hosts.add(resolvedHost);if(resolvedPort>0)port=resolvedPort;}
        JSONArray listed=target.getJSONArray("addresses");for(int i=0;i<listed.length();i++)if(!hosts.contains(listed.getString(i)))hosts.add(listed.getString(i));
        long deadline=SystemClock.elapsedRealtime()+10000;
        boolean command=method.equals("rollerSession")||method.equals("control");
        for(String host:hosts){if(command&&SystemClock.elapsedRealtime()>deadline)break;if(!LanWire.localAddress(host))continue;try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress(host,port),1800);socket.setSoTimeout(command?4000:10000);
            String nonce=LanWire.random(18);JSONObject request=new JSONObject().put("group",target.getString("id")).put("nonce",nonce).put("at",System.currentTimeMillis())
                .put("peer",prefs.getString("deviceId","")).put("name",Build.MODEL).put("method",method).put("args",args);
            byte[] key=LanWire.key(target.getString("key"));LanWire.write(socket.getOutputStream(),key,request.toString(),LanWire.MAX_REQUEST);
            JSONObject response=new JSONObject(LanWire.read(socket.getInputStream(),key,LanWire.MAX_RESPONSE));
            if(!nonce.equals(response.optString("nonce")))throw new IOException("Falsche Antwort.");if(response.has("error")){if(command)throw new IllegalArgumentException(response.getString("error"));throw new IOException(response.getString("error"));}
            if(target==config){resolvedHost=host;resolvedPort=port;}return response.getJSONObject("data");
        }catch(IllegalArgumentException e){throw e;}catch(Exception ignored){/* Try another local interface from the same invitation. */}}
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
            JSONObject response=new JSONObject().put("nonce",nonce);try{
                if(request.optString("method").equals("rollerSession")||request.optString("method").equals("control")||request.optString("method").equals("bundle")){synchronized(this){if(active!=config||!host())return;response.put("data",read(request.getString("method"),request.getJSONArray("args")));}}
                else response.put("data",read(request.getString("method"),request.getJSONArray("args")));
            }catch(Exception e){response.put("error",(request.optString("method").equals("rollerSession")||request.optString("method").equals("control"))&&e instanceof IllegalArgumentException?e.getMessage():"Die Anfrage konnte gerade nicht abgeschlossen werden. Bitte erneut versuchen.");}
            if(!host()||active!=config)return;
            LanWire.write(open.getOutputStream(),key,response.toString(),LanWire.MAX_RESPONSE);
            if(peers.size()<20||peers.containsKey(request.optString("peer")))peers.put(request.optString("peer"),new JSONObject().put("name",request.optString("name").substring(0,Math.min(60,request.optString("name").length()))).put("seen",SystemClock.elapsedRealtime()));
        }catch(Exception ignored){/* Authentication errors never reveal data or key material. */}
    }
    private JSONObject read(String method,JSONArray args)throws Exception{
        if(method.equals("control"))return applyControl(args);
        if(method.equals("rollerSession")){
            if(args.length()!=3||!(args.get(0) instanceof Boolean))throw new IllegalArgumentException("Ungültige Ladeanfrage.");
            RollerStore.get(context).command(args.getBoolean(0),args.getLong(1),args.getString(2));repository.invalidateStats();planning.request();return read("bundle",new JSONArray());
        }
        if(method.equals("bundle")){
            long now=System.currentTimeMillis();if(!repository.archiveBusy&&now-lastPlanRequest>30000){lastPlanRequest=now;planning.request();}
            JSONObject snapshot=repository.archiveBusy?new JSONObject(repository.backgroundSnapshot()):repository.snapshot();snapshot.remove("archiveDiagnostics");snapshot.remove("event");snapshot.remove("eventId");
            return new JSONObject().put("snapshot",snapshot).put("setup",planning.setup()).put("planning",new JSONObject(SolarPlanning.sharedKnowledge)).put("roller",RollerStore.get(context).snapshot())
                .put("controlProtocol",1).put("controlRevision",currentControlRevision()).put("sync",new JSONObject(repository.syncState())).put("bonded",PlantCommands.bonded(context));
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
