package de.wattpit.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

/** Keeps the elected plant host available while its screen is off. */
public final class LanService extends Service {
    private static final String CHANNEL="wattpit-lan";
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        LanSync lan=LanSync.get(this);if(!lan.host()){stopSelf();return START_NOT_STICKY;}
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Anlagenverbund",NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_bolt).setContentTitle("Watt’n’Pit · Anlagenverbund")
            .setContentText("Hauptgerät im lokalen WLAN verfügbar").setContentIntent(open).setOngoing(true).build();
        try{
            if(Build.VERSION.SDK_INT>=29)startForeground(12,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(12,notification);
            lan.startHost();
        }catch(Exception e){lan.hostFailed();stopSelf();return START_NOT_STICKY;}
        return START_STICKY;
    }
    @Override public void onDestroy(){LanSync.get(this).stopHost();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
