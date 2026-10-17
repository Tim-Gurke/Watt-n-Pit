package de.wattpit.app;

import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import de.wattpit.app.core.ArchiveCodec;
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
    private volatile CompletableFuture<Integer> operation;
    private volatile boolean cancelled,connected,started;
    private volatile BluetoothGatt gatt;
    private BluetoothGattCharacteristic control,data;
    private Repository repository;
    private String mac="",kind="";
    private int changed,missing,unmapped,processed,total,unknown;
    private long lastPing,lastNotification;
    private static UUID uuid(int n){return UUID.fromString(String.format(Locale.ROOT,"306b%04x-b081-4037-83dc-e59fcc3cdfd0",n));}
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){
            if(g!=gatt)return;
            connected=status==BluetoothGatt.GATT_SUCCESS&&state==BluetoothProfile.STATE_CONNECTED;
            if(connected)complete(0);else {complete(status==0?-1:status);received.offer(new byte[0]);}
        }
        @Override public void onServicesDiscovered(BluetoothGatt g,int status){if(g==gatt)complete(status);}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){if(g==gatt)complete(status);}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){if(g==gatt)complete(status);}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] v){if(g==gatt)incoming(c,v);}
        @SuppressWarnings("deprecation") @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){if(g==gatt&&Build.VERSION.SDK_INT<33)incoming(c,c.getValue());}
    };
    private void incoming(BluetoothGattCharacteristic c,byte[] v){if((DATA.equals(c.getUuid())||BULK.equals(c.getUuid()))&&!received.offer(v.clone()))cancelled=true;}
    private void complete(int status){CompletableFuture<Integer> op=operation;if(op!=null)op.complete(status);}
    @Override public void onCreate(){super.onCreate();repository=Repository.get(this);getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Gerätearchiv importieren",NotificationManager.IMPORTANCE_LOW));}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("CANCEL".equals(intent.getAction())){cancelled=true;complete(-1);received.offer(new byte[0]);return START_NOT_STICKY;}
        if(started)return START_NOT_STICKY;started=true;mac=intent.getStringExtra("mac");
        try {
            if(Build.VERSION.SDK_INT>=31)startForeground(11,notification("Verbindung wird aufgebaut …"),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else if(Build.VERSION.SDK_INT>=29)startForeground(11,notification("Verbindung wird aufgebaut …"),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(11,notification("Verbindung wird aufgebaut …"));
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
    private void check() throws IOException {if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("cancelled");}
    private interface GattAction {boolean run();}
    private void op(GattAction action) throws Exception {
        check();CompletableFuture<Integer> future=new CompletableFuture<>();operation=future;
        main.post(()->{try{if(!action.run())future.complete(-1);}catch(Exception e){future.completeExceptionally(e);}});
        try {if(future.get(15,TimeUnit.SECONDS)!=0)throw new IOException("Bluetooth-Vorgang fehlgeschlagen.");}
        finally {operation=null;}
    }
    @SuppressWarnings("deprecation") private void write(BluetoothGattCharacteristic c,byte[] value) throws Exception {
        op(()->{int type=(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0?BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE:BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
            if(Build.VERSION.SDK_INT>=33)return gatt.writeCharacteristic(c,value,type)==BluetoothStatusCodes.SUCCESS;
            c.setWriteType(type);c.setValue(value);return gatt.writeCharacteristic(c);});
    }
    @SuppressWarnings("deprecation") private void subscribe(BluetoothGattCharacteristic c) throws Exception {
        BluetoothGattDescriptor descriptor=c.getDescriptor(CCCD);
        if(descriptor==null)throw new IOException("Benachrichtigungen fehlen.");
        op(()->{if(!gatt.setCharacteristicNotification(c,true))return false;
            byte[] value=(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0?BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE:BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
            if(Build.VERSION.SDK_INT>=33)return gatt.writeDescriptor(descriptor,value)==BluetoothStatusCodes.SUCCESS;
            descriptor.setValue(value);return gatt.writeDescriptor(descriptor);});
    }
    private byte[] exchange(int reg,byte[] command) throws Exception {
        check();messages.removeIf(m->m.reg==reg);
        if(SystemClock.elapsedRealtime()-lastPing>3500){write(control,new byte[]{(byte)0xf9,0x41});write(data,new byte[]{3,0});lastPing=SystemClock.elapsedRealtime();}
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
            if(!connected)throw new IOException("Verbindung unterbrochen.");
            if(SystemClock.elapsedRealtime()-lastPing>3500){write(control,new byte[]{(byte)0xf9,0x41});write(data,new byte[]{3,0});lastPing=SystemClock.elapsedRealtime();}
            byte[] bytes=received.poll(250,TimeUnit.MILLISECONDS);
            if(bytes!=null&&bytes.length>0)messages.addAll(stream.feed(bytes));
        }
        throw new TimeoutException("Keine Archivantwort.");
    }
    private byte[] read(int reg) throws Exception{return exchange(reg,ArchiveCodec.read(reg));}
    private byte[] require(int reg) throws Exception{byte[] b=read(reg);if(b==null)throw new IllegalArgumentException("Dieses Gerät stellt kein unterstütztes Trendarchiv bereit.");return b;}
    private void connect(BluetoothDevice device) throws Exception {
        progress("Falls Android fragt: mit der Bluetooth-PIN des Geräts koppeln.");
        op(()->{gatt=device.connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE);return gatt!=null;});
        if(device.getBondState()!=BluetoothDevice.BOND_BONDED){
            main.post(device::createBond);long end=SystemClock.elapsedRealtime()+60000;
            while(device.getBondState()!=BluetoothDevice.BOND_BONDED&&SystemClock.elapsedRealtime()<end){check();Thread.sleep(200);}
            if(device.getBondState()!=BluetoothDevice.BOND_BONDED)throw new IllegalArgumentException("Koppeln nicht abgeschlossen. In Android mit der sechsstelligen Geräte-PIN koppeln und erneut importieren.");
        }
        progress("Archiv wird vorbereitet …");op(()->gatt.discoverServices());
        BluetoothGattService s=gatt.getService(SERVICE);
        if(s==null)throw new IllegalArgumentException("Das Gerät bietet diese Archiv-Schnittstelle nicht an.");
        control=s.getCharacteristic(CONTROL);data=s.getCharacteristic(DATA);BluetoothGattCharacteristic bulk=s.getCharacteristic(BULK);
        if(control==null||data==null||bulk==null)throw new IllegalArgumentException("Unvollständige Archiv-Schnittstelle.");
        subscribe(control);subscribe(data);subscribe(bulk);
        write(control,new byte[]{(byte)0xfa,(byte)0x80,(byte)0xff});write(control,new byte[]{(byte)0xf9,(byte)0x80});
        write(data,new byte[]{1});write(data,new byte[]{3,0});
        // Session keepalive only; no battery/charger settings or device clock are changed.
        write(data,new byte[]{6,0,(byte)0x82,0x18,(byte)0x93,0x42,0x10,0x27});
        lastPing=SystemClock.elapsedRealtime();
    }
    private void runImports(){
        try {
            List<String> queue=new ArrayList<>();
            if("AUTO".equals(mac)){JSONArray configs=repository.configs();for(int i=0;i<configs.length();i++)queue.add(configs.getJSONObject(i).getString("mac"));}
            else queue.add(mac);
            for(String next:queue){if(cancelled)break;mac=next;changed=missing=unmapped=processed=total=unknown=0;received.clear();messages.clear();stream=new ArchiveCodec.Stream();runImport();}
        }catch(Exception e){repository.archiveState(mac,false,"Import konnte nicht vorbereitet werden.",0,0);}
        finally{main.post(this::stopSelf);}
    }
    private void runImport(){
        try {
            JSONObject cfg=null;JSONArray list=repository.configs();for(int i=0;i<list.length();i++)if(list.getJSONObject(i).getString("mac").equals(mac))cfg=list.getJSONObject(i);
            if(cfg==null)throw new IllegalArgumentException("Bitte zuerst ein Gerät einrichten.");kind=cfg.getString("kind");
            BluetoothManager manager=getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null||!adapter.isEnabled())throw new IllegalArgumentException("Bitte Bluetooth einschalten.");
            connect(adapter.getRemoteDevice(mac));
            long currentUptime=ArchiveCodec.u(require(0xec5a),0,4);
            byte[] ids=require(0xec5d);if(ids.length!=16)throw new IllegalArgumentException("Unbekannte Archivkanäle.");
            ArchiveCodec.Clock clock=new ArchiveCodec.Clock();byte[] anchor=read(0xec5f);if(anchor!=null)clock.anchors(anchor);
            // Read historical anchors and boot boundaries, including paginated older entries.
            for(int reg:new int[]{0xec63,0xec64}){
                long cursor=currentUptime;
                for(int page=0;page<64;page++){
                    byte[] b=exchange(reg,ArchiveCodec.request(reg,ArchiveCodec.le(cursor,4)));if(b==null)break;
                    long oldest=ArchiveCodec.NA;
                    if(reg==0xec63){clock.anchors(b);oldest=clock.oldestAnchor(b);}
                    else {clock.reboots(b);for(int j=0;j+4<=b.length;j+=4){long t=ArchiveCodec.u(b,j,4);if(t!=ArchiveCodec.NA)oldest=Math.min(oldest,t);}}
                    if(oldest==ArchiveCodec.NA||oldest==0||oldest>=cursor)break;cursor=oldest-1;
                }
            }
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
            for(int ch:fields.keySet()){
                ArchiveCodec.Field field=fields.get(ch);
                for(ArchiveCodec.Level level:levels.get(ch)){
                    long cursor=level.newest;int remaining=level.capacity;
                    while(cursor>=level.oldest&&remaining>0){
                        check();int count=Math.min(batches.get(ch),remaining);
                        Long knownFrom=clock.millis(cursor-(long)(count-1)*level.period),knownTo=clock.millis(cursor);
                        if(knownFrom!=null&&knownTo!=null&&knownTo<System.currentTimeMillis()-2L*level.period*1000&&repository.db.covered(mac,registers.get(ch),level.period,knownFrom,knownTo)){
                            processed+=count;remaining-=count;cursor-=(long)count*level.period;progress("Vorhandenes Archiv geprüft · "+Math.min(100,(int)(processed*100L/Math.max(1,total)))+" %");continue;
                        }
                        byte[] reply=exchange(0xec5b,ArchiveCodec.trendQuery(ch,cursor,count));
                        if(reply==null){unknown++;break;}
                        ArchiveCodec.Block block=new ArchiveCodec.Block(reply,field,ch,count);
                        if(block.count==0)break;
                        List<long[]> times=new ArrayList<>();List<Double> values=new ArrayList<>();
                        for(int i=0;i<block.count;i++){
                            if(block.values[i]==null){missing++;continue;}
                            long uptime=block.time-(long)i*block.period;Long time=clock.millis(uptime);
                            if(time==null||time>System.currentTimeMillis()+300000){unmapped++;continue;}
                            times.add(new long[]{time,block.period});values.add(block.values[i]);
                        }
                        changed+=repository.db.archiveBlock(mac,kind,field.name,times,values);
                        Long blockFrom=clock.millis(block.time-(long)(block.count-1)*block.period),blockTo=clock.millis(block.time);
                        if(blockFrom!=null&&blockTo!=null&&blockFrom<=blockTo&&blockTo<System.currentTimeMillis()-2L*block.period*1000)repository.db.cover(mac,registers.get(ch),block.period,blockFrom,blockTo);
                        processed+=block.count;remaining-=block.count;
                        long next=block.time-(long)block.count*block.period;
                        progress("Archiv lesen · "+Math.min(100,(int)(processed*100L/Math.max(1,total)))+" %");
                        if(next>=cursor)break;cursor=next;
                    }
                }
            }
            if(kind.equals("solar"))solarDays();else shuntHistory();
            repository.invalidateStats();String result=changed+" neue oder ergänzte Werte · "+missing+" ohne Messwert";
            if(unmapped>0)result+=" · "+unmapped+" ohne sichere Zeitzuordnung";
            if(unknown>0)result+=" · "+unknown+" Kanäle/Abfragen nicht unterstützt";
            if(changed==0&&unmapped>0)result="Kein Import möglich: Zeitanker fehlen. "+result;
            repository.archiveState(mac,false,result,processed,total);repository.message(result);
        }catch(Exception e){
            String result=cancelled?"Import abgebrochen. Bereits gelesene Werte bleiben gespeichert.":e instanceof IllegalArgumentException?e.getMessage():"Verbindung unterbrochen oder keine Antwort. VictronConnect trennen, in der Nähe bleiben und erneut importieren. Bereits gelesene Werte bleiben gespeichert.";
            repository.invalidateStats();repository.archiveState(mac,false,result,processed,total);repository.message(result);
        }finally{closeGatt();}
    }
    private void solarDays() throws Exception {
        progress("Solar-Tageswerte lesen …");LocalDate today=LocalDate.now();
        for(int day=0;day<31;day++){
            byte[] b=read(0x1050+day);if(b==null||b.length!=34)continue;
            long yield=ArchiveCodec.u(b,1,4);if(yield==ArchiveCodec.NA)continue;
            JSONObject p=new JSONObject().put("day",today.minusDays(day).toString()).put("device",mac).put("origin","archive")
                    .put("Yield(Wh)",yield*10).put("Max. PV power(W)",ArchiveCodec.u(b,24,4))
                    .put("Min. battery voltage(V)",ArchiveCodec.u(b,11,2)/100.0).put("Max. battery voltage(V)",ArchiveCodec.u(b,9,2)/100.0)
                    .put("Max. PV voltage(V)",ArchiveCodec.u(b,30,2)/100.0).put("Bulk(min)",ArchiveCodec.u(b,18,2)).put("Absorption(min)",ArchiveCodec.u(b,20,2)).put("Float(min)",ArchiveCodec.u(b,22,2));
            repository.db.archiveDaily(mac,p.getString("day"),p);
        }
    }
    private void shuntHistory() throws Exception {
        progress("Shunt-Gesamtwerte lesen …");JSONObject p=new JSONObject().put("device",mac).put("updated",System.currentTimeMillis());
        for(int r=0x0300;r<=0x0311;r++){byte[] b=read(r);if(b==null||b.length<1||b.length>4)continue;
            long raw=ArchiveCodec.u(b,0,b.length);
            boolean signed=r<=0x0302||r==0x0305;long sign=1L<<(8*b.length-1);if(raw==(signed?sign-1:(sign*2-1)))continue;if(signed&&(raw&sign)!=0)raw-=1L<<(8*b.length);
            double divisor=r<=0x0302||r==0x0305?10:r==0x0306||r==0x0307||r>=0x030e?100:1;p.put(Integer.toHexString(r),raw/divisor);
        }
        repository.db.archiveHistory(mac,p);
    }
    private void closeGatt(){BluetoothGatt g=gatt;gatt=null;connected=false;if(g!=null){try{g.disconnect();}catch(SecurityException ignored){}try{g.close();}catch(SecurityException ignored){}}}
    @Override public void onDestroy(){cancelled=true;complete(-1);closeGatt();worker.shutdownNow();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
