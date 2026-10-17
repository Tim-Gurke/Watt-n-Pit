package de.wattpit.app.core;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveLinkTest {
    private static final byte[] CREDIT={(byte)0xf9,1};

    @Test public void sendsOnlyAfterAnActualDeviceCredit(){
        ArchiveLink link=new ArchiveLink();assertFalse(link.reserveSend());
        link.control(new byte[]{(byte)0xfa,(byte)0x80,(byte)0xff});assertFalse(link.reserveSend());
        link.control(CREDIT);assertTrue(link.reserveSend());assertFalse(link.reserveSend());
        link.control(new byte[]{(byte)0xf9,(byte)0xff});
        for(int i=0;i<255;i++)assertTrue(link.reserveSend());assertFalse(link.reserveSend());
    }

    @Test public void receiveCreditsFollowConsumedPacketsAndNeverIdleTime(){
        ArchiveLink link=new ArchiveLink();
        for(int i=0;i<64;i++){link.receivedData();assertEquals(0,link.receiveGrant());}
        link.receivedData();assertEquals(65,link.receiveGrant());
        for(long now=0;now<60000;now+=250)assertEquals(0,link.receiveGrant());
        assertEquals(65,link.received());assertEquals(1,link.grants());
    }

    /** Independent peer: enforces its receive window, one outgoing credit, and a 10 s watchdog. */
    private static final class Peer {
        int window=128,packets,heartbeats;
        long lastHeartbeat;
        void command(byte[] b,long now){if(Arrays.equals(b,ArchiveSession.keepAlive())){lastHeartbeat=now;heartbeats++;}}
        void check(long now){assertTrue("session watchdog expired",now-lastHeartbeat<10000);}
        void grant(int n){window+=n;assertTrue("unused receive credits accumulated",window<=128);}
        void send(ArchiveLink link){assertTrue(window>0);window--;packets++;link.receivedData();}
    }
    private static void maintain(ArchiveLink link,Peer peer,long now){
        int grant;while((grant=link.receiveGrant())>0)peer.grant(grant);
        if(link.heartbeatDue(now)){
            assertTrue("no device credit for heartbeat",link.reserveSend());
            peer.command(ArchiveSession.keepAlive(),now);link.heartbeatSent(now);link.control(CREDIT);
        }
        peer.check(now);
    }
    @Test public void aMinuteOfFastArchiveTrafficRespectsBothWindowsAndWatchdog(){
        ArchiveLink link=new ArchiveLink();Peer peer=new Peer();link.control(CREDIT);
        assertTrue(link.reserveSend());peer.command(ArchiveSession.keepAlive(),0);link.heartbeatSent(0);link.control(CREDIT);
        for(long now=50;now<=60000;now+=50){
            // A full burst can arrive between reader polls; the device stops at its advertised limit.
            for(int i=0;i<40&&peer.window>0;i++)peer.send(link);
            if(now%250==0){
                maintain(link,peer,now);
                assertTrue(link.reserveSend());link.control(CREDIT);
            }
            peer.check(now);
        }
        assertTrue(peer.packets>10000);assertTrue(peer.heartbeats>=20);assertEquals(peer.packets,link.received());
    }
    @Test public void aSlowRegisterReplyDoesNotLeaveTheSessionWithoutHeartbeats(){
        ArchiveLink link=new ArchiveLink();Peer peer=new Peer();link.control(CREDIT);peer.command(ArchiveSession.keepAlive(),0);link.heartbeatSent(0);
        for(long now=250;now<=12000;now+=250)maintain(link,peer,now);
        assertEquals(5,peer.heartbeats);assertEquals(0,link.grants());
    }
    @Test public void reopeningContextZeroIsNotAWatchdogRenewal(){
        Peer peer=new Peer();peer.command(ArchiveSession.keepAlive(),0);
        peer.command(new byte[]{3,0},3500);peer.command(new byte[]{3,0},7000);
        assertEquals(0,peer.lastHeartbeat);assertTrue(10000-peer.lastHeartbeat>=10000);
    }
}
