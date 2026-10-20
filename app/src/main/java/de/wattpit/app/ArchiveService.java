package de.wattpit.app;

import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import de.wattpit.app.core.ArchiveCodec;
import de.wattpit.app.core.ArchiveSession;
import de.wattpit.app.core.ArchiveDiagnostic;
import de.wattpit.app.core.ArchiveLink;
import de.wattpit.app.core.ArchiveSyncPlan;
import de.wattpit.app.core.ArchiveBudget;
import org.json.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Short-lived, serial GATT archive reader. Every stored block survives cancellation. */
@SuppressLint("MissingPermission")
public final class ArchiveService extends Service {
    private static final String CHANNEL="wattpit-archive";
    private static final UUID SERVICE=uuid(1),CONTROL=uuid(2),DATA=uuid(3),BULK=uuid(4),CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final BlockingQueue<byte[]> received=new LinkedBlockingQueue<>(512);
    private final ArrayDeque<ArchiveCodec.Message> messages=new ArrayDeque<>();
    private ArchiveCodec.Stream stream=new ArchiveCodec.Stream();
    private ArchiveLink link=new ArchiveLink();
    private volatile CompletableFuture<Integer> operation;
    private volatile String operationType="";
    private volatile int negotiatedMtu=23;
    private volatile int disconnectStatus;
    private String stage="Verbindung";
    private final List<String> diagnostics=new ArrayList<>();
    private volatile boolean cancelled,connected,started;
    private volatile BluetoothGatt gatt;
    private BluetoothGattCharacteristic control,data;
    private Repository repository;
    private ArchiveBudget budget;
    private boolean automatic;
    private String mac="",kind="";
    private int changed,missing,unmapped,processed,total,unknown,blocks,skipped;
    private long lastNotification;
    private static UUID uuid(int n){return UUID.fromString(String.format(Locale.ROOT,"306b%04x-b081-4037-83dc-e59fcc3cdfd0",n));}
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){
            if(g!=gatt)return;
            connected=status==BluetoothGatt.GATT_SUCCESS&&state==BluetoothProfile.STATE_CONNECTED;
            if(connected)complete("connect",0);
            else if(state==BluetoothProfile.STATE_DISCONNECTED){disconnectStatus=status;trace("Bluetooth getrennt · Status "+status);failOperation(new LinkClosed(status));received.offer(new byte[0]);}
        }
        @Override public void onServicesDiscovered(BluetoothGatt g,int status){if(g==gatt)complete("discover",status);}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){if(g==gatt)complete("descriptor",status);}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){if(g==gatt)complete("write",status);}
        @Override public void onMtuChanged(BluetoothGatt g,int mtu,int status){if(g==gatt){if(status==0)negotiatedMtu=mtu;complete("mtu",status);}}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] v){if(g==gatt)incoming(c,v);}
        @SuppressWarnings("deprecation") @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){if(g==gatt&&Build.VERSION.SDK_INT<33)incoming(c,c.getValue());}
    };
    private void incoming(BluetoothGattCharacteristic c,byte[] v){
        UUID id=c.getUuid();
        if(CONTROL.equals(id)){link.control(v);return;}
        if(DATA.equals(id))link.receivedData();
        if((DATA.equals(id)||BULK.equals(id))&&!received.offer(v.clone()))cancelled=true;
    }
    private void complete(String type,int status){CompletableFuture<Integer> op=operation;if(op!=null&&type.equals(operationType))op.complete(status);}
    private void failOperation(Exception failure){CompletableFuture<Integer> op=operation;if(op!=null)op.completeExceptionally(failure);}
    private void stage(String label){stage=label;trace(label);progress(label+" …");}
    private void trace(String text){synchronized(diagnostics){if(diagnostics.size()>=80)diagnostics.remove(0);diagnostics.add(ArchiveDiagnostic.clean(text));}}
    private void saveDiagnostics(String result){try{
        JSONArray lines;synchronized(diagnostics){lines=new JSONArray(diagnostics);}
        repository.archiveDiagnostics(kind,new JSONObject().put("result",result).put("steps",lines).put("stored",changed).put("blocks",blocks).put("skipped",skipped).put("missing",missing).put("unmapped",unmapped).put("sentPackets",link.sent()).put("receivedPackets",link.received()).put("receiveGrants",link.grants()));
    }catch(JSONException ignored){}}
    private static final class GattFailure extends IOException{
        private static final long serialVersionUID=1;final int status;
        GattFailure(int status){super("GATT "+status);this.status=status;}
    }
    private static final class LinkClosed extends IOException{
        private static final long serialVersionUID=1;
        LinkClosed(int status){super("Bluetooth-Verbindung getrennt (Status "+status+")");}
    }
    private static final class GattRejected extends IOException{
        private static final long serialVersionUID=1;
        GattRejected(String operation){super("Android hat den Bluetooth-Auftrag nicht gestartet ("+operation+")");}
    }
    @Override public void onCreate(){super.onCreate();repository=Repository.get(this);getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Gerätearchiv importieren",NotificationManager.IMPORTANCE_LOW));}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("CANCEL".equals(intent.getAction())){cancelled=true;repository.pauseAutomaticArchives();failOperation(new IOException("cancelled"));received.offer(new byte[0]);if(!started)stopSelf();return START_NOT_STICKY;}
        if(started)return START_NOT_STICKY;started=true;mac=intent.getStringExtra("mac");
        automatic="AUTO".equals(mac);budget=new ArchiveBudget(automatic,SystemClock.elapsedRealtime());
        try {
            if(Build.VERSION.SDK_INT>=31)startForeground(11,notification("Verbindung wird aufgebaut …"),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else if(Build.VERSION.SDK_INT>=29)startForeground(11,notification("Verbindung wird aufgebaut …"),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(11,notification("Verbindung wird aufgebaut …"));
            repository.archiveRun.begin(System.currentTimeMillis());repository.archiveBusy=true;
            repository.archiveState(mac,true,"Verbindung wird aufgebaut …",0,0);
            worker.execute(this::runImports);
        }catch(Exception e){repository.archiveState(mac,false,"Import konnte nicht starten. Bluetooth-Berechtigung prüfen.",0,0);stopSelf();}
        return START_NOT_STICKY;
    }
    private Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel=PendingIntent.getService(this,3,new Intent(this,ArchiveService.class).setAction("CANCEL"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_bolt).setContentTitle("Watt’n’Pit · Gerätearchiv").setContentText(text).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Abbrechen",cancel).build()).build();
    }
    private void progress(String text){repository.archiveState(mac,true,text,processed,total);long now=SystemClock.elapsedRealtime();if(now-lastNotification>750){lastNotification=now;getSystemService(NotificationManager.class).notify(11,notification(text));}}
    private static final class BudgetReached extends IOException {private static final long serialVersionUID=1;}
    private void check() throws IOException {if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("cancelled");if(budget!=null&&budget.remaining(SystemClock.elapsedRealtime())==0)throw new BudgetReached();}
    private interface GattAction {boolean run();}
    private void op(String type,GattAction action) throws Exception {
        check();long wait=budget==null?20000:budget.waitMillis(SystemClock.elapsedRealtime(),20000);if(wait==0)throw new BudgetReached();
        CompletableFuture<Integer> future=new CompletableFuture<>();operationType=type;operation=future;
        main.post(()->{if(future.isDone()||cancelled)return;try{if(!action.run())future.completeExceptionally(new GattRejected(type));}catch(Exception e){future.completeExceptionally(e);}});
        try {int status=future.get(wait,TimeUnit.MILLISECONDS);if(status!=0){trace(type+" · GATT "+status);throw new GattFailure(status);}}
        catch(TimeoutException e){future.completeExceptionally(e);check();throw e;}
        catch(ExecutionException e){if(e.getCause() instanceof Exception)throw (Exception)e.getCause();throw e;}
        finally {operation=null;operationType="";}
    }
    @SuppressWarnings("deprecation") private void write(BluetoothGattCharacteristic c,byte[] value) throws Exception {
        if(c==data){
            long deadline=SystemClock.elapsedRealtime()+8000;
            replenish();
            while(!link.reserveSend()){
                check();if(!connected)throw new LinkClosed(disconnectStatus);
                if(SystemClock.elapsedRealtime()>=deadline)throw new GattRejected("Sendefreigabe vom Gerät fehlt");
                replenish();Thread.sleep(20);
            }
        }
        op("write",()->{int type=(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0?BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE:BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
            if(Build.VERSION.SDK_INT>=33){int status=gatt.writeCharacteristic(c,value,type);if(status!=BluetoothStatusCodes.SUCCESS)throw new IllegalStateException("Android-Schreibauftrag abgelehnt · Code "+status);return true;}
            c.setWriteType(type);c.setValue(value);return gatt.writeCharacteristic(c);});
    }
    private void replenish()throws Exception{
        int grant;while((grant=link.receiveGrant())>0)write(control,new byte[]{(byte)0xf9,(byte)grant});
    }
    private void maintain()throws Exception{
        check();if(!connected)throw new LinkClosed(disconnectStatus);
        replenish();
        if(link.heartbeatDue(SystemClock.elapsedRealtime())){
            write(data,ArchiveSession.keepAlive());link.heartbeatSent(SystemClock.elapsedRealtime());
        }
    }
    @SuppressWarnings("deprecation") private void subscribe(BluetoothGattCharacteristic c) throws Exception {
        BluetoothGattDescriptor descriptor=c.getDescriptor(CCCD);
        if(descriptor==null)throw new IOException("Benachrichtigungen fehlen.");
        op("descriptor",()->{if(!gatt.setCharacteristicNotification(c,true))return false;
            byte[] value=(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0?BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE:BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
            if(Build.VERSION.SDK_INT>=33)return gatt.writeDescriptor(descriptor,value)==BluetoothStatusCodes.SUCCESS;
            descriptor.setValue(value);return gatt.writeDescriptor(descriptor);});
    }
    private byte[] exchange(int reg,byte[] command) throws Exception {
        check();messages.removeIf(m->m.reg==reg);
        maintain();
        write(data,command);
        if(command[0]==6)write(data,ArchiveCodec.read(reg));
        long deadline=SystemClock.elapsedRealtime()+12000;
        while(SystemClock.elapsedRealtime()<deadline){
            check();
            while(!messages.isEmpty()){
                ArchiveCodec.Message m=messages.removeFirst();
                if(m.reg!=reg)continue;
                if(m.code==8)return m.data;
                if(command[0]==5||m.status!=3)return null; // Unsupported field, never a trend data block.
            }
            maintain();
            byte[] bytes=received.poll(250,TimeUnit.MILLISECONDS);
            if(bytes!=null&&bytes.length>0)messages.addAll(stream.feed(bytes));
        }
        throw new TimeoutException("Keine Archivantwort.");
    }
    private byte[] read(int reg) throws Exception{return exchange(reg,ArchiveCodec.read(reg));}
    private byte[] require(int reg) throws Exception{byte[] b=read(reg);if(b==null)throw new IllegalArgumentException("Dieses Gerät stellt kein unterstütztes Trendarchiv bereit.");return b;}
    private void connect(BluetoothDevice device) throws Exception {
        int bond=device.getBondState();
        if(automatic&&bond!=BluetoothDevice.BOND_BONDED)throw new IllegalArgumentException("Für die erste Android-Kopplung unter Geräte den Archivabruf von Hand starten");
        if(bond!=BluetoothDevice.BOND_BONDED){
            stage("Android-Kopplung bestätigen");
            trace(bond==BluetoothDevice.BOND_BONDING?"Android-Kopplung läuft bereits":"Neue Android-Kopplung anfordern");
            if(bond==BluetoothDevice.BOND_NONE){
                CompletableFuture<Boolean> request=new CompletableFuture<>();main.post(()->{try{request.complete(device.createBond());}catch(Exception e){request.completeExceptionally(e);}});
                if(!request.get(5,TimeUnit.SECONDS))throw new IllegalArgumentException("Android konnte die Kopplung nicht starten. In den Bluetooth-Einstellungen mit der Geräte-PIN koppeln und erneut versuchen");
            }
            long end=SystemClock.elapsedRealtime()+60000;boolean pairingSeen=bond==BluetoothDevice.BOND_BONDING;
            while(device.getBondState()!=BluetoothDevice.BOND_BONDED&&SystemClock.elapsedRealtime()<end){
                check();int state=device.getBondState();if(state==BluetoothDevice.BOND_BONDING)pairingSeen=true;
                if(pairingSeen&&state==BluetoothDevice.BOND_NONE)throw new IllegalArgumentException("Android-Kopplung abgebrochen oder PIN nicht bestätigt. Kopplung erneut starten");
                Thread.sleep(200);
            }
            if(device.getBondState()!=BluetoothDevice.BOND_BONDED)throw new IllegalArgumentException("Android-Kopplung nicht abgeschlossen. PIN-Abfrage oder Bluetooth-Kopplungsbenachrichtigung prüfen");
            trace("Neue Android-Kopplung bestätigt");
        }else trace("Android-Kopplung bereits vorhanden · keine erneute PIN-Abfrage nötig");
        stage("Bluetooth verbinden");
        op("connect",()->{gatt=device.connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE,BluetoothDevice.PHY_LE_1M_MASK,main);return gatt!=null;});
        stage("Bluetooth-Dienste lesen");op("discover",()->gatt.discoverServices());
        stage("Bluetooth-Paketgröße aushandeln");
        try{op("mtu",()->gatt.requestMtu(517));}catch(GattFailure|TimeoutException e){if(!connected)throw e;trace("Paketgröße 23 · Standardwert verwenden");}
        trace("Paketgröße "+negotiatedMtu);
        BluetoothGattService s=gatt.getService(SERVICE);
        if(s==null)throw new IllegalArgumentException("Das Gerät bietet diese Archiv-Schnittstelle nicht an.");
        control=s.getCharacteristic(CONTROL);data=s.getCharacteristic(DATA);BluetoothGattCharacteristic bulk=s.getCharacteristic(BULK);
        if(control==null||data==null||bulk==null)throw new IllegalArgumentException("Unvollständige Archiv-Schnittstelle.");
        stage("Archiv-Benachrichtigungen aktivieren");subscribe(control);subscribe(data);subscribe(bulk);
        write(control,new byte[]{(byte)0xfa,(byte)0x80,(byte)0xff});write(control,new byte[]{(byte)0xf9,(byte)0x80});
        for(ArchiveSession.Step step:ArchiveSession.setup()){
            stage(step.label);write(data,step.command);
            if(step.reply==0)link.heartbeatSent(SystemClock.elapsedRealtime());
            if(step.reply!=0)awaitSession(step);
        }
        trace("Alle drei Verbindungskontexte bestätigt");
    }
    private void awaitSession(ArchiveSession.Step step)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+12000;
        while(SystemClock.elapsedRealtime()<deadline){
            check();while(!messages.isEmpty())if(ArchiveSession.accepted(step,messages.removeFirst()))return;
            maintain();byte[] bytes=received.poll(250,TimeUnit.MILLISECONDS);
            if(bytes!=null&&bytes.length>0)messages.addAll(stream.feed(bytes));
        }
        throw new TimeoutException();
    }
    private void runImports(){
        try {
            List<String> queue=new ArrayList<>();
            if(automatic){JSONArray configs=repository.automaticArchiveDevices();for(int i=0;i<configs.length();i++)queue.add(configs.getJSONObject(i).getString("mac"));}
            else queue.add(mac);
            for(String next:queue){if(cancelled||budget.queueEnded(SystemClock.elapsedRealtime()))break;budget.device(SystemClock.elapsedRealtime());mac=next;stage="Verbindung";changed=missing=unmapped=processed=total=unknown=blocks=skipped=0;negotiatedMtu=23;disconnectStatus=0;link=new ArchiveLink();synchronized(diagnostics){diagnostics.clear();}received.clear();messages.clear();stream=new ArchiveCodec.Stream();runImport();}
        }catch(Exception e){repository.archiveState(mac,false,"Import konnte nicht vorbereitet werden.",0,0);}
        finally{main.post(this::stopSelf);}
    }
    private static final class ArchiveTask {
        final int channel,slot;final ArchiveCodec.Level level;long cursor;int remaining;
        ArchiveTask(int channel,int slot,ArchiveCodec.Level level){this.channel=channel;this.slot=slot;this.level=level;cursor=level.newest;remaining=level.capacity;}
    }
    private void nextArchiveTurn(ArchiveTask task,int size){repository.prefs.edit().putInt("archiveTurn."+mac,(task.slot+1)%size).apply();}
    private void runImport(){
        try {
            JSONObject cfg=null;JSONArray list=repository.configs();for(int i=0;i<list.length();i++)if(list.getJSONObject(i).getString("mac").equals(mac))cfg=list.getJSONObject(i);
            if(cfg==null)throw new IllegalArgumentException("Bitte zuerst ein Gerät einrichten.");kind=cfg.getString("kind");
            BluetoothManager manager=getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null||!adapter.isEnabled())throw new IllegalArgumentException("Bitte Bluetooth einschalten.");
            connect(adapter.getRemoteDevice(mac));
            stage("Archivmetadaten lesen");
            long currentUptime=ArchiveCodec.u(require(0xec5a),0,4);
            byte[] ids=require(0xec5d);if(ids.length!=16)throw new IllegalArgumentException("Unbekannte Archivkanäle.");
            ArchiveCodec.Clock clock=repository.archiveClock(mac,currentUptime);boolean cachedClock=clock.hasAnchors();
            byte[] anchor=read(0xec5f);if(anchor!=null)clock.anchors(anchor);repository.saveArchiveClock(mac,currentUptime,clock);
            // Current summaries must not wait behind a large historical backlog.
            if(kind.equals("solar"))solarDays();else shuntHistory();
            // Read historical anchors and boot boundaries, including paginated older entries.
            stage("Archivzeiten zuordnen");
            for(int reg:new int[]{0xec63,0xec64}){
                long cursor=currentUptime;boolean jumped=false;
                for(int page=0;page<64;page++){
                    byte[] b=exchange(reg,ArchiveCodec.request(reg,ArchiveCodec.le(cursor,4)));if(b==null)break;
                    long oldest=ArchiveCodec.NA;int added;
                    if(reg==0xec63){added=clock.anchors(b);oldest=clock.oldestAnchor(b);}
                    else {added=clock.reboots(b);for(int j=0;j+4<=b.length;j+=4){long t=ArchiveCodec.u(b,j,4);if(t!=ArchiveCodec.NA)oldest=Math.min(oldest,t);}}
                    repository.saveArchiveClock(mac,currentUptime,clock);
                    if(oldest==ArchiveCodec.NA||oldest==0||oldest>=cursor)break;
                    if(cachedClock&&added==0){
                        long stored=reg==0xec63?clock.oldestStoredAnchor():clock.oldestStoredReboot();
                        if(!jumped&&stored!=ArchiveCodec.NA&&stored>0&&stored<cursor){cursor=stored-1;jumped=true;continue;}
                        break;
                    }
                    cursor=oldest-1;
                }
            }
            if(anchor!=null)clock.anchors(anchor);repository.saveArchiveClock(mac,currentUptime,clock);
            stage("Archivkanäle lesen");
            int auxMode=-1;byte[] aux=read(0xeef8);if(aux!=null&&aux.length>0)auxMode=aux[0]&255;
            Map<Integer,List<ArchiveCodec.Level>> levels=new LinkedHashMap<>();Map<Integer,ArchiveCodec.Field> fields=new LinkedHashMap<>();Map<Integer,Integer> batches=new HashMap<>(),registers=new HashMap<>();
            for(int ch=0;ch<8;ch++){
                int id=(int)ArchiveCodec.u(ids,ch*2,2);if(id==0||id==65535)continue;
                ArchiveCodec.Field field=ArchiveCodec.field(id,auxMode);if(field==null){unknown++;continue;}
                byte[] config=read(0xec4a+ch),refs=read(0xec52+ch);if(config==null||refs==null){unknown++;continue;}
                List<ArchiveCodec.Level> tier=ArchiveCodec.levels(config,refs);levels.put(ch,tier);fields.put(ch,field);registers.put(ch,id);
                batches.put(ch,Math.max(1,Math.min(config[1]&255,56/field.width)));for(ArchiveCodec.Level l:tier)total+=l.capacity;
            }
            if(fields.isEmpty())throw new IllegalArgumentException("Keine unterstützten Trendkanäle gefunden.");
            List<Integer> channelOrder=new ArrayList<>(fields.keySet());channelOrder.sort(Comparator.comparingInt(ch->ArchiveSession.priority(fields.get(ch).name)));
            // One block per channel/tier turn: an old Solar channel cannot monopolize the queue.
            List<ArchiveTask> tasks=new ArrayList<>();int tierCount=0;
            for(List<ArchiveCodec.Level> tier:levels.values())tierCount=Math.max(tierCount,tier.size());
            for(int tier=0;tier<tierCount;tier++)for(int ch:channelOrder)if(tier<levels.get(ch).size())tasks.add(new ArchiveTask(ch,tasks.size(),levels.get(ch).get(tier)));
            int taskCount=tasks.size();if(taskCount>0)Collections.rotate(tasks,-Math.floorMod(repository.prefs.getInt("archiveTurn."+mac,0),taskCount));
            ArrayDeque<ArchiveTask> pending=new ArrayDeque<>(tasks);
            Map<Integer,ArchiveSyncPlan.Availability> availability=new HashMap<>();
            while(!pending.isEmpty()){
                maintain();ArchiveTask task=pending.removeFirst();int ch=task.channel;ArchiveCodec.Level level=task.level;ArchiveCodec.Field field=fields.get(ch);
                stage=ArchiveSession.label(field.name)+" mit lokalen Daten abgleichen";
                ArchiveSyncPlan.Availability local=availability.get(ch);
                if(local==null){local=repository.db.archiveAvailability(mac,kind,registers.get(ch),field.name,levels.get(ch),clock,System.currentTimeMillis(),this::maintain);availability.put(ch,local);}
                ArchiveSyncPlan.Request request=ArchiveSyncPlan.next(task.cursor,task.remaining,level.oldest,level.period,batches.get(ch),clock,local);
                skipped+=request.skipped-request.deferred;unmapped+=request.deferred;processed+=request.skipped;task.remaining-=request.skipped;task.cursor=request.cursor;
                if(request.skipped>0)progress("Lokaler Abgleich · "+skipped+" vorhanden · "+unmapped+" ohne Zeitreferenz zurückgestellt");
                int count=request.count;
                if(count==0){nextArchiveTurn(task,taskCount);continue;}
                byte[] reply=exchange(0xec5b,ArchiveCodec.trendQuery(ch,task.cursor,count));
                if(reply==null){unknown++;nextArchiveTurn(task,taskCount);continue;}
                ArchiveCodec.Block block=new ArchiveCodec.Block(reply,field,ch,count);
                if(block.count==0){nextArchiveTurn(task,taskCount);continue;}
                List<long[]> times=new ArrayList<>();List<Double> values=new ArrayList<>();
                for(int i=0;i<block.count;i++){
                    if(block.values[i]==null){missing++;continue;}
                    long uptime=block.time-(long)i*block.period;Long time=clock.millis(uptime);
                    if(time==null||time>System.currentTimeMillis()+300000){unmapped++;continue;}
                    times.add(new long[]{time,block.period});values.add(block.values[i]);
                }
                int added=repository.db.archiveBlock(mac,kind,field.name,times,values);changed+=added;blocks++;
                if(added>0)repository.invalidateStats();
                for(long[] time:times)local.reading(time[0],"archive",(int)time[1]);
                for(long[] window:ArchiveSyncPlan.completed(block,clock,System.currentTimeMillis())){
                    repository.db.cover(mac,registers.get(ch),block.period,window[0],window[1]);local.imported(block.period,window[0],window[1]);
                }
                processed+=block.count;task.remaining-=block.count;
                long next=block.time-(long)block.count*block.period;nextArchiveTurn(task,taskCount);
                progress(ArchiveSession.label(field.name)+" · "+Math.min(100,(int)(processed*100L/Math.max(1,total)))+" % · "+changed+" Werte übernommen");
                if(block.period!=level.period){unknown++;trace("Abweichende Archivauflösung · Bereich zurückgestellt");continue;}
                if(next>=task.cursor){unknown++;trace("Archivantwort ohne Zeitfortschritt · Bereich zurückgestellt");continue;}
                task.cursor=next;if(task.cursor>=level.oldest&&task.remaining>0)pending.addLast(task);
            }
            repository.invalidateStats();String result=changed+" neue oder ergänzte Werte · "+missing+" ohne Messwert";
            if(skipped>0)result+=" · "+skipped+" bereits vorhanden, nicht geladen";
            if(unmapped>0)result+=" · "+unmapped+" ohne Zeitreferenz zurückgestellt";
            if(unknown>0)result+=" · "+unknown+" Kanäle/Abfragen nicht unterstützt";
            traceSummary();saveDiagnostics(result);repository.archiveState(mac,false,result,processed,total);repository.message(result);
        }catch(Exception e){
            String reason=e instanceof GattFailure?"GATT "+((GattFailure)e).status:e instanceof TimeoutException?"Zeitüberschreitung":e instanceof IllegalArgumentException||e instanceof LinkClosed||e instanceof GattRejected||e instanceof IllegalStateException?e.getMessage():e instanceof SecurityException?"Bluetooth-Berechtigung fehlt":"Verbindung beendet";
            reason=ArchiveDiagnostic.clean(reason);
            String result=(cancelled?"Import abgebrochen · automatische Abrufe 30 Minuten pausiert":e instanceof BudgetReached?"Abgleich pausiert · Zeitlimit erreicht":stage+": "+reason)+". "+changed+" Archivwerte übernommen. Weitere Daten unter Geräte → Gerätearchiv ergänzen nachladen.";
            trace(cancelled?"Abgebrochen":e instanceof BudgetReached?"Zeitbudget erreicht · gespeicherte Werte bleiben erhalten":stage+" · "+reason);traceSummary();saveDiagnostics(result);
            repository.invalidateStats();repository.archiveState(mac,false,result,processed,total);repository.message(result);
        }finally{closeGatt();repository.archiveAttemptFinished(mac,cancelled);}
    }
    private void traceSummary(){trace(blocks+" Datenblöcke · "+changed+" übernommene Werte · "+skipped+" lokal vorhanden, nicht geladen · "+missing+" fehlend · "+unmapped+" ohne sichere Zeit");trace(link.sent()+" Datenpakete gesendet · "+link.received()+" empfangen · "+link.grants()+" Empfangsfreigaben");}
    private void solarDays() throws Exception {
        stage("Solar-Tageswerte lesen");LocalDate today=LocalDate.now();
        for(int day=0;day<31;day++){
            String date=today.minusDays(day).toString();
            if(day>0&&repository.db.archiveDayComplete(mac,date))continue;
            byte[] b=read(0x1050+day);
            if(!LocalDate.now().equals(today)){today=LocalDate.now();day=-1;continue;}
            if(b==null||b.length!=34)continue;
            long yield=ArchiveCodec.u(b,1,4);if(yield==ArchiveCodec.NA)continue;
            JSONObject p=new JSONObject().put("day",date).put("device",mac).put("origin","archive")
                    .put("Yield(Wh)",yield*10).put("Max. PV power(W)",ArchiveCodec.u(b,24,4))
                    .put("Min. battery voltage(V)",ArchiveCodec.u(b,11,2)/100.0).put("Max. battery voltage(V)",ArchiveCodec.u(b,9,2)/100.0)
                    .put("Max. PV voltage(V)",ArchiveCodec.u(b,30,2)/100.0).put("Bulk(min)",ArchiveCodec.u(b,18,2)).put("Absorption(min)",ArchiveCodec.u(b,20,2)).put("Float(min)",ArchiveCodec.u(b,22,2));
            repository.db.archiveDaily(mac,date,p,day>0);
        }
    }
    private void shuntHistory() throws Exception {
        stage("Shunt-Gesamtwerte lesen");JSONObject p=new JSONObject().put("device",mac).put("updated",System.currentTimeMillis());
        for(int r=0x0300;r<=0x0311;r++){byte[] b=read(r);if(b==null||b.length<1||b.length>4)continue;
            long raw=ArchiveCodec.u(b,0,b.length);
            boolean signed=r<=0x0302||r==0x0305;long sign=1L<<(8*b.length-1);if(raw==(signed?sign-1:(sign*2-1)))continue;if(signed&&(raw&sign)!=0)raw-=1L<<(8*b.length);
            double divisor=r<=0x0302||r==0x0305?10:r==0x0306||r==0x0307||r>=0x030e?100:1;p.put(Integer.toHexString(r),raw/divisor);
        }
        repository.db.archiveHistory(mac,p);
    }
    private void closeGatt(){BluetoothGatt g=gatt;gatt=null;connected=false;if(g!=null){try{g.disconnect();}catch(SecurityException ignored){}try{g.close();}catch(SecurityException ignored){}}}
    @Override public void onDestroy(){cancelled=true;failOperation(new IOException("cancelled"));closeGatt();repository.archiveBusy=false;repository.finishArchiveIfActive();repository.archiveRun.finish();worker.shutdownNow();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
