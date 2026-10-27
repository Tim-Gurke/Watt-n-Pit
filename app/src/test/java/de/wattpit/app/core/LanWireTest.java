package de.wattpit.app.core;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.*;

public final class LanWireTest {
    @Test public void invitationSurvivesClipboardWrappingAndDuplicatedHeader(){
        String json="{\"id\":\"test\",\"name\":\"Tims Wohnwagen – Süden\"}",code=LanWire.encodeInvitation(json);
        assertEquals(json,LanWire.decodeInvitation(code));
        String wrapped="\uFEFF„WNP1."+code.substring(0,30)+"\r\n\u00a0"+code.substring(30)+"\u200b”.";
        assertEquals(json,LanWire.decodeInvitation(wrapped));
        assertEquals(json,LanWire.decodeInvitation(code.replace("WNP1.","wnp1.")));
    }
    @Test public void invalidInvitationsGiveAnActionableErrorWithoutRawDecoderText(){
        for(String code:new String[]{"WNP1.abc.def","WNP1.…","WNP1.%%%%","WNP1.e30WNP1.e30","https://example.com/","WNP1._w"}){
            try{LanWire.decodeInvitation(code);fail("invalid invitation accepted");}catch(IllegalArgumentException expected){assertEquals(LanWire.INVALID_INVITATION,expected.getMessage());assertFalse(expected.getMessage().contains("base64"));}
        }
    }
    @Test public void authenticatedFramesRoundTripWithFreshNonces()throws Exception{
        byte[] key=LanWire.key(LanWire.random(32));ByteArrayOutputStream first=new ByteArrayOutputStream(),second=new ByteArrayOutputStream();
        LanWire.write(first,key,"Wohnwagen · 12,8 V",1024);LanWire.write(second,key,"Wohnwagen · 12,8 V",1024);
        assertFalse(Arrays.equals(first.toByteArray(),second.toByteArray()));
        assertEquals("Wohnwagen · 12,8 V",LanWire.read(new ByteArrayInputStream(first.toByteArray()),key,1024));
    }
    @Test public void wrongKeyAndTamperedFramesRevealNoPlaintext()throws Exception{
        byte[] key=LanWire.key(LanWire.random(32));ByteArrayOutputStream out=new ByteArrayOutputStream();LanWire.write(out,key,"Private Messwerte",1024);
        try{LanWire.read(new ByteArrayInputStream(out.toByteArray()),LanWire.key(LanWire.random(32)),1024);fail("wrong key accepted");}catch(javax.crypto.AEADBadTagException expected){}
        byte[] bad=out.toByteArray();bad[bad.length-1]^=1;try{LanWire.read(new ByteArrayInputStream(bad),key,1024);fail("tampering accepted");}catch(javax.crypto.AEADBadTagException expected){}
    }
    @Test public void framingIsBoundedBeforeAllocation()throws Exception{
        byte[] key=LanWire.key(LanWire.random(32));ByteArrayOutputStream out=new ByteArrayOutputStream();new DataOutputStream(out).writeInt(Integer.MAX_VALUE);
        try{LanWire.read(new ByteArrayInputStream(out.toByteArray()),key,100);fail("unbounded frame");}catch(IOException expected){}
        try{LanWire.write(new ByteArrayOutputStream(),key,"too long",2);fail("unbounded write");}catch(IOException expected){}
        try{LanWire.key(LanWire.random(16));fail("weak key");}catch(IllegalArgumentException expected){}
    }
    @Test public void endpointsAreNumericAndLocal(){
        for(String address:new String[]{"192.168.43.1","10.0.0.1","172.16.0.1","169.254.1.2","127.0.0.1"})assertTrue(address,LanWire.localAddress(address));
        for(String address:new String[]{"8.8.8.8","172.32.0.1","0.0.0.0","example.com","192.168.1.999","192.168.1.1:80","::1"})assertFalse(address,LanWire.localAddress(address));
    }
    @Test public void realTcpConnectionCarriesOnlyAuthenticatedFrames()throws Exception{
        byte[] key=LanWire.key(LanWire.random(32));ExecutorService worker=Executors.newSingleThreadExecutor();
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            Future<String> received=worker.submit(()->{try(Socket peer=server.accept()){peer.setSoTimeout(2000);String request=LanWire.read(peer.getInputStream(),key,LanWire.MAX_REQUEST);LanWire.write(peer.getOutputStream(),key,"response:"+request,LanWire.MAX_RESPONSE);return request;}});
            try(Socket client=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort())){client.setSoTimeout(2000);LanWire.write(client.getOutputStream(),key,"soc=76.4",LanWire.MAX_REQUEST);assertEquals("response:soc=76.4",LanWire.read(client.getInputStream(),key,LanWire.MAX_RESPONSE));}
            assertEquals("soc=76.4",received.get(3,TimeUnit.SECONDS));
        }finally{worker.shutdownNow();}
    }
}
