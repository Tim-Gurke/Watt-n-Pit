package de.wattpit.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.util.Collections;
import java.util.concurrent.*;

/** Passive BLE monitor. No GATT writes, no changes to the Victron equipment. */
public final class MonitorService extends Service {
    private static final String CHANNEL = "wattpit-monitor";
    private static final int NOTIFICATION = 10;
    private Repository repository;
    private BluetoothLeScanner scanner;
    private boolean scanning;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { restartScan(); }
    };
    private final ScanCallback callback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override public void onScanResult(int callbackType, ScanResult result) {
            if (result.getScanRecord() == null || worker.isShutdown()) return;
            byte[] data = result.getScanRecord().getManufacturerSpecificData(0x02e1);
            if (data == null) return;
            try {
                String mac = result.getDevice().getAddress();
                String name = result.getScanRecord().getDeviceName();
                worker.execute(() -> repository.advertise(mac,name,result.getRssi(),data));
            } catch (SecurityException ignored) { repository.status = "Bluetooth-Berechtigung fehlt"; }
        }
        @Override public void onScanFailed(int errorCode) {
            scanning = false;
            repository.status = "Bluetooth-Suche unterbrochen (" + errorCode + "). Neuer Versuch folgt.";
        }
    };

    @Override public void onCreate() {
        super.onCreate(); repository = Repository.get(this);
        NotificationChannel channel = new NotificationChannel(CHANNEL,"Watt’n’Pit-Aufzeichnung",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Lokale Bluetooth-Aufzeichnung deiner Energieanlage");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(bluetoothReceiver,filter,Context.RECEIVER_EXPORTED);
        else registerReceiver(bluetoothReceiver,filter);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent!=null&&"ARCHIVE_SCAN".equals(intent.getAction())){if(repository.running){restartScan();getSystemService(NotificationManager.class).notify(NOTIFICATION,notification());return START_STICKY;}stopSelf();return START_NOT_STICKY;}
        if (intent != null && "STOP".equals(intent.getAction())) { repository.prefs.edit().putBoolean("recording",false).apply(); stopSelf(); return START_NOT_STICKY; }
        if (!hasPermissions()) { repository.status = "Bluetooth-Berechtigung fehlt"; stopSelf(); return START_NOT_STICKY; }
        try {
            if (Build.VERSION.SDK_INT >= 31) startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(NOTIFICATION,notification());
        } catch (SecurityException e) { repository.status = "Aufzeichnung konnte nicht gestartet werden. App öffnen und Berechtigungen prüfen."; stopSelf(); return START_NOT_STICKY; }
        boolean first = !repository.running;
        repository.running = true;
        repository.prefs.edit().putBoolean("recording",true).apply();
        restartScan();
        if (first) worker.scheduleWithFixedDelay(() -> main.post(() -> {
            if (!repository.running) return;
            if (!scanning) startScan();
            getSystemService(NotificationManager.class).notify(NOTIFICATION,notification());
        }),30,30,TimeUnit.SECONDS);
        return START_STICKY;
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= 31) return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void startScan() {
        if (!repository.running || scanning || !hasPermissions()) return;
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) { repository.status = "Dieses Gerät unterstützt kein Bluetooth LE."; return; }
        if (!adapter.isEnabled()) { repository.status = "Bluetooth ist ausgeschaltet"; return; }
        if (Build.VERSION.SDK_INT <= 30) {
            android.location.LocationManager location = getSystemService(android.location.LocationManager.class);
            if (location != null && !location.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                    && !location.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                repository.status = "Android benötigt für die BLE-Suche den eingeschalteten Standortdienst."; return;
            }
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { repository.status = "Bluetooth ist noch nicht bereit"; return; }
        ScanFilter filter = new ScanFilter.Builder().setManufacturerData(0x02e1,new byte[]{0x10},new byte[]{(byte)0xff}).build();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).setReportDelay(0).build();
        try {
            scanner.startScan(Collections.singletonList(filter),settings,callback);
            scanning = true; repository.status = "Bluetooth-Empfang aktiv";
        } catch (SecurityException e) { repository.status = "Bluetooth-Berechtigung fehlt"; }
        catch (IllegalStateException e) { repository.status = "Bluetooth ist noch nicht bereit"; }
    }
    @SuppressLint("MissingPermission")
    private void restartScan() {
        if (scanner != null && scanning && hasPermissions()) {
            try { scanner.stopScan(callback); } catch (IllegalStateException | SecurityException ignored) { }
        }
        scanning = false; startScan();
    }
    private Notification notification() {
        Intent open = new Intent(this,MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this,1,new Intent(this,MonitorService.class).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_bolt).setContentTitle("Watt’n’Pit zeichnet lokal auf")
                .setContentText(repository.status).setContentIntent(content).setOngoing(true)
                .addAction(new Notification.Action.Builder(null,"Pausieren",stop).build()).build();
    }
    @Override public void onDestroy() {
        repository.running = false;
        repository.status = "Aufzeichnung pausiert";
        if (scanner != null && scanning && hasPermissions()) {
            try { scanner.stopScan(callback); } catch (SecurityException | IllegalStateException ignored) { }
        }
        scanning = false; worker.shutdown();
        try { unregisterReceiver(bluetoothReceiver); } catch (IllegalArgumentException ignored) { }
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
