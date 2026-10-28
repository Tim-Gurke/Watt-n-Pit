package de.wattpit.app;

import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.security.MessageDigest;

/** Same-phone bridge for official ScootPit; LAN credentials never leave Watt'n'Pit. */
public final class RollerLinkProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @SuppressWarnings("deprecation") private void authorize(){
        PackageManager pm=getContext().getPackageManager();String[] names=pm.getPackagesForUid(Binder.getCallingUid());
        if(names!=null)for(String name:names)if(name.equals("de.wortmonster.jbdtrigger"))try{
            PackageInfo info=pm.getPackageInfo(name,Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
            Signature[] signatures=Build.VERSION.SDK_INT>=28?info.signingInfo.getApkContentsSigners():info.signatures;
            if(signatures!=null)for(Signature signature:signatures){StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()))hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                if(hex.toString().equals("c58abf26de59344a21efbc0576e391a36b6179724a0b4fad27a35de03de184fb"))return;}
        }catch(Exception ignored){}
        throw new SecurityException("BMS-Übergabe ist nur für die offizielle lokale ScootPit-App freigegeben.");
    }
    @Override public Bundle call(String method,String mac,Bundle extras){
        authorize();Bundle out=new Bundle();out.putInt("protocol",2);
        try{
            LanSync lan=LanSync.get(getContext());if(!lan.viewer()){out.putBoolean("available",false);return out;}
            if(extras==null||extras.getString("payload","").length()>5000)throw new IllegalArgumentException();
            JSONObject data=new JSONObject(extras.getString("payload","{}")).put("mac",mac);
            JSONObject response=lan.rollerLink(method,data);out.putBoolean("available",true);out.putString("response",response.toString());
        }catch(Exception e){out.putBoolean("available",e instanceof IllegalArgumentException);out.putString("error",e instanceof IllegalArgumentException?e.getMessage():"Zentrale nicht erreichbar · direkte BMS-Verbindung bleibt möglich");}
        return out;
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){authorize();throw new UnsupportedOperationException();}
    @Override public String getType(Uri u){authorize();return "application/vnd.wattpit.roller-link";}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
