package de.wattpit.app;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import java.security.MessageDigest;
import java.util.*;

/** Authenticated, local, read-only ScootPit telemetry and cooperative BLE ownership. */
final class ScootLink {
    private static final String PACKAGE="de.wortmonster.jbdtrigger",CERT="c58abf26de59344a21efbc0576e391a36b6179724a0b4fad27a35de03de184fb";
    private static final Uri URI=Uri.parse("content://de.wortmonster.jbdtrigger.bms");
    private final Context context;
    private final String token=UUID.randomUUID().toString();
    private String leasedMac="";
    ScootLink(Context c){context=c;}
    static final class State {
        boolean supported,internal,granted,failed;
        byte[] frame;long at,elapsed;String mac="";
    }
    @SuppressWarnings("deprecation")
    private boolean trustedProvider(){
        PackageManager pm=context.getPackageManager();ProviderInfo provider=pm.resolveContentProvider(URI.getAuthority(),0);
        if(provider==null)return false;
        if(!PACKAGE.equals(provider.packageName))throw new SecurityException("Unerwarteter BMS-Datenanbieter");
        try{
            PackageInfo info=pm.getPackageInfo(PACKAGE,Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
            Signature[] signatures=Build.VERSION.SDK_INT>=28?info.signingInfo.getApkContentsSigners():info.signatures;
            if(signatures!=null)for(Signature signature:signatures){byte[] digest=MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(Locale.ROOT,"%02x",b&255));if(CERT.equals(hex.toString()))return true;}
        }catch(PackageManager.NameNotFoundException e){return false;}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        throw new SecurityException("ScootPit-Signatur stimmt nicht überein");
    }
    private Bundle call(String method,String mac){Bundle args=new Bundle();args.putString("token",token);return context.getContentResolver().call(URI,method,mac,args);}
    State read(String mac){
        State s=new State();
        try{
            if(!trustedProvider())return s;s.supported=true;
            Bundle data=call("snapshot",mac);if(data==null||data.getInt("protocol")!=1){s.failed=true;return s;}
            s.internal=data.getBoolean("internal");s.frame=data.getByteArray("frame");s.at=data.getLong("at");s.elapsed=data.getLong("elapsed");s.mac=data.getString("mac","");
        }catch(RuntimeException e){s.supported=true;s.failed=true;}
        return s;
    }
    boolean acquire(String mac){try{Bundle data=call("acquire",mac);boolean ok=data!=null&&data.getInt("protocol")==1&&data.getBoolean("granted");if(ok)leasedMac=mac;return ok;}catch(RuntimeException e){return false;}}
    void release(){if(leasedMac.isEmpty())return;String mac=leasedMac;leasedMac="";try{call("release",mac);}catch(RuntimeException ignored){}}
}
