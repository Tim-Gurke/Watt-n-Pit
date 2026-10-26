package de.wattpit.app;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.os.*;
import android.content.Context;
import java.util.*;
import de.wattpit.app.core.BmsPacket;

/** JBD/Overkill basic-info reads only. No MOS, configuration or firmware writes. */
@SuppressLint("MissingPermission")
final class RollerBle {
    private static UUID uuid(String id){return UUID.fromString("0000"+id+"-0000-1000-8000-00805f9b34fb");}
    private static final UUID SERVICE=uuid("ff00"),NOTIFY=uuid("ff01"),WRITE=uuid("ff02"),CCCD=uuid("2902");
    private static final byte[] REQUEST={(byte)0xdd,(byte)0xa5,3,0,(byte)0xff,(byte)0xfd,0x77};
    private final Context context;
    private final RollerStore store;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic write;
    private String address="";
    private byte[] pending=new byte[0];
    private boolean running,ready,busy;
    private long connectedAt,lastPacket;
    RollerBle(Context c){context=c;store=RollerStore.get(c);}
    void start(){if(running)return;running=true;handler.post(tick);}
    void stop(){running=false;handler.removeCallbacksAndMessages(null);store.toggle(false);close();store.status("Aufzeichnung pausiert");}
    private void close(){BluetoothGatt previous=gatt;gatt=null;ready=busy=false;write=null;pending=new byte[0];lastPacket=0;store.disconnected();if(previous!=null){try{previous.disconnect();previous.close();}catch(SecurityException ignored){}}}
    private final Runnable tick=new Runnable(){public void run(){
        if(!running)return;
        try{
            String mac=store.mac();if(!mac.equals(address)){close();address=mac;}
            long now=SystemClock.elapsedRealtime();
            if(!mac.isEmpty()){
                BluetoothManager manager=context.getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
                if(adapter==null||!adapter.isEnabled()){close();store.status("Bluetooth ausgeschaltet");}
                else if(gatt==null){store.status("Verbinde Roller-BMS …");connectedAt=now;gatt=adapter.getRemoteDevice(mac).connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);}
                else if(now-(lastPacket>0?lastPacket:connectedAt)>20000){close();store.status("Kein BMS-Empfang · neuer Verbindungsversuch folgt");}
                else if(ready&&!busy){busy=true;boolean accepted;
                    if(Build.VERSION.SDK_INT>=33)accepted=gatt.writeCharacteristic(write,REQUEST,BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)==BluetoothStatusCodes.SUCCESS;
                    else{write.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);write.setValue(REQUEST);accepted=gatt.writeCharacteristic(write);}
                    if(!accepted)busy=false;
                }
            }else store.status("Noch kein Roller-BMS ausgewählt");
        }catch(SecurityException e){close();store.status("Bluetooth-Berechtigung fehlt");}catch(IllegalArgumentException|IllegalStateException e){close();store.status("BMS-Verbindung nicht verfügbar");}
        handler.postDelayed(this,2000);
    }};
    private void failed(String message){close();store.status(message);}
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){handler.post(()->{if(g!=gatt||!running)return;if(status==BluetoothGatt.GATT_SUCCESS&&state==BluetoothProfile.STATE_CONNECTED){if(!g.discoverServices())failed("BMS-Dienstsuche fehlgeschlagen");}else if(state==BluetoothProfile.STATE_DISCONNECTED||status!=BluetoothGatt.GATT_SUCCESS)failed("BMS getrennt · neuer Versuch folgt");});}
        @Override public void onServicesDiscovered(BluetoothGatt g,int status){handler.post(()->{
            if(g!=gatt||!running)return;
            BluetoothGattService service=status==BluetoothGatt.GATT_SUCCESS?g.getService(SERVICE):null;
            BluetoothGattCharacteristic notify=service==null?null:service.getCharacteristic(NOTIFY);write=service==null?null:service.getCharacteristic(WRITE);
            if(notify==null||write==null){failed("Kein unterstütztes JBD/Overkill-BMS");return;}
            BluetoothGattDescriptor descriptor=notify.getDescriptor(CCCD);
            if(descriptor==null||!g.setCharacteristicNotification(notify,true)){failed("BMS-Benachrichtigungen fehlen");return;}
            boolean accepted;if(Build.VERSION.SDK_INT>=33)accepted=g.writeDescriptor(descriptor,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)==BluetoothStatusCodes.SUCCESS;
            else{descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);accepted=g.writeDescriptor(descriptor);}
            if(!accepted)failed("BMS-Benachrichtigungen konnten nicht aktiviert werden");
        });}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){handler.post(()->{if(g!=gatt||!running)return;if(status!=BluetoothGatt.GATT_SUCCESS){failed("BMS-Benachrichtigungen fehlgeschlagen");return;}ready=true;store.status("Warte auf BMS-Messwerte");});}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){handler.post(()->{if(g!=gatt)return;busy=false;if(status!=BluetoothGatt.GATT_SUCCESS)failed("BMS-Abfrage fehlgeschlagen");});}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){byte[] v=c.getValue();if(v!=null)receive(g,c,v.clone());}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] v){receive(g,c,v.clone());}
    };
    private void receive(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){handler.post(()->{
        if(g!=gatt||!running||!c.getUuid().equals(NOTIFY))return;
        if(pending.length+value.length>1024)pending=new byte[0];
        byte[] all=Arrays.copyOf(pending,pending.length+value.length);System.arraycopy(value,0,all,pending.length,value.length);int cursor=0;
        while(cursor+4<=all.length){if((all[cursor]&255)!=0xdd){cursor++;continue;}int size=(all[cursor+3]&255)+7;if(cursor+size>all.length)break;
            BmsPacket packet=BmsPacket.decode(Arrays.copyOfRange(all,cursor,cursor+size));
            if(packet!=null){lastPacket=SystemClock.elapsedRealtime();store.accept(packet,System.currentTimeMillis());cursor+=size;}else cursor++;
        }
        pending=Arrays.copyOfRange(all,cursor,all.length);
    });}
}
