package de.wattpit.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.*;

/** Explicit shared app operations. Device firmware, arbitrary methods and file paths are not exposed. */
final class PlantCommands {
    @SuppressLint("MissingPermission")
    static JSONArray bonded(Context context){
        JSONArray out=new JSONArray();try{BluetoothManager manager=context.getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter!=null)for(BluetoothDevice device:adapter.getBondedDevices())out.put(new JSONObject().put("mac",device.getAddress()).put("name",device.getName()));
        }catch(Exception ignored){}return out;
    }
    static void apply(Context context,Repository repo,SolarPlanning planning,String action,JSONArray args)throws Exception{
        switch(action){
            case "saveSolarSetup":arity(args,2);planning.save(args.getString(0),args.getBoolean(1));break;
            case "saveSolarSite":arity(args,2);planning.saveSite(args.getString(0),args.getString(1));break;
            case "weatherPlace":arity(args,1);WeatherClient.place(repo,new JSONObject(args.getString(0)));break;
            case "refreshWeather":arity(args,0);WeatherClient.refresh(repo,true);break;
            case "saveEquipment":arity(args,1);if(args.getString(0).length()>20000)throw new IllegalArgumentException("Die Anlagenkonfiguration ist zu groß.");repo.saveEquipment(new JSONObject(args.getString(0)));WeatherClient.refresh(repo,true);break;
            case "saveRoller":arity(args,1);RollerStore.get(context).configure(args.getString(0));break;
            case "rollerReconnect":arity(args,0);RollerHandover.get(context).reconnect();break;
            case "saveDevice":arity(args,1);repo.saveDevice(new JSONObject(args.getString(0)));break;
            case "removeDevice":arity(args,1);repo.removeDevice(args.getString(0));break;
            case "settings":{
                arity(args,1);JSONObject given=new JSONObject(args.getString(0)),shared=new JSONObject();
                for(String key:new String[]{"capacity","sampleSeconds"})if(given.has(key))shared.put(key,given.get(key));
                if(shared.length()!=given.length())throw new IllegalArgumentException("Diese Darstellungseinstellung gehört auf das jeweilige Handy.");
                repo.saveSettings(shared);break;
            }
            case "importSettings":arity(args,1);repo.importSharedConfiguration(args.getString(0));WeatherClient.refresh(repo,true);break;
            case "start":arity(args,0);bluetoothReady(context);context.startForegroundService(new Intent(context,MonitorService.class));break;
            case "stop":arity(args,0);repo.prefs.edit().putBoolean("recording",false).commit();context.stopService(new Intent(context,MonitorService.class));break;
            case "importArchive":case "importMissingArchive":{
                arity(args,action.equals("importArchive")?1:0);bluetoothReady(context);
                String mac=action.equals("importArchive")?args.getString(0):"MANUAL";
                if(!mac.equals("MANUAL")){boolean found=false;JSONArray devices=repo.configs();for(int i=0;i<devices.length();i++)if(mac.equals(devices.getJSONObject(i).optString("mac")))found=true;if(!found)throw new IllegalArgumentException("Dieses Gerät ist am Hauptgerät nicht eingerichtet.");}
                if(repo.archiveBusy)throw new IllegalArgumentException("Eine Synchronisation läuft bereits.");
                context.startForegroundService(new Intent(context,ArchiveService.class).putExtra("mac",mac));break;
            }
            case "cancelArchive":arity(args,0);if(repo.archiveBusy)context.startService(new Intent(context,ArchiveService.class).setAction("CANCEL"));break;
            case "backgroundArchive":arity(args,1);if(!repo.archiveRun.background(args.getLong(0)))throw new IllegalArgumentException("Dieser Synchronisationslauf ist bereits beendet. Bitte erneut versuchen.");break;
            default:throw new IllegalArgumentException("Diese Bedienaktion wird nicht unterstützt. Bitte beide Geräte aktualisieren.");
        }
        repo.invalidateStats();planning.request();
    }
    private static void arity(JSONArray args,int count){if(args.length()!=count)throw new IllegalArgumentException("Ungültige Bedienanfrage.");}
    @SuppressLint("MissingPermission")
    private static void bluetoothReady(Context c){
        boolean allowed=Build.VERSION.SDK_INT>=31?c.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED:c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        if(!allowed)throw new IllegalArgumentException("Bitte Watt’n’Pit am Hauptgerät öffnen und die Bluetooth-Berechtigung erteilen.");
        BluetoothManager manager=c.getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null||!adapter.isEnabled())throw new IllegalArgumentException("Bitte Bluetooth am Hauptgerät einschalten.");
    }
    private PlantCommands(){}
}
