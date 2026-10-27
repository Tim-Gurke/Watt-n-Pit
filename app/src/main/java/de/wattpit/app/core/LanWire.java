package de.wattpit.app.core;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Length-bounded, authenticated LAN frames. A fresh nonce is used for every frame. */
public final class LanWire {
    public static final int MAX_REQUEST=16*1024, MAX_RESPONSE=8*1024*1024;
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final byte[] AAD="Watt-n-Pit LAN 1".getBytes(StandardCharsets.UTF_8);
    private LanWire(){}
    public static String random(int bytes){byte[] value=new byte[bytes];RANDOM.nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    public static byte[] key(String value){byte[] key=Base64.getUrlDecoder().decode(value);if(key.length!=32)throw new IllegalArgumentException("Ungültiger Verbindungscode.");return key;}
    public static boolean localAddress(String value){
        if(value==null||!value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))return false;
        try{String[] parts=value.split("\\.");for(String part:parts)if(Integer.parseInt(part)>255)return false;
            InetAddress address=InetAddress.getByName(value);return address.isSiteLocalAddress()||address.isLinkLocalAddress()||address.isLoopbackAddress();
        }catch(Exception e){return false;}
    }
    public static void write(OutputStream stream,byte[] key,String text,int limit)throws Exception{
        byte[] plain=text.getBytes(StandardCharsets.UTF_8);if(plain.length>limit)throw new IOException("Antwort ist zu groß.");
        byte[] nonce=new byte[12];RANDOM.nextBytes(nonce);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(AAD);
        byte[] encrypted=cipher.doFinal(plain);DataOutputStream out=new DataOutputStream(stream);
        out.writeInt(nonce.length+encrypted.length);out.write(nonce);out.write(encrypted);out.flush();
    }
    public static String read(InputStream stream,byte[] key,int limit)throws Exception{
        DataInputStream in=new DataInputStream(stream);int length=in.readInt();if(length<28||length>limit+28)throw new IOException("Ungültige Nachrichtengröße.");
        byte[] nonce=new byte[12],encrypted=new byte[length-12];in.readFully(nonce);in.readFully(encrypted);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(AAD);
        return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
    }
}
