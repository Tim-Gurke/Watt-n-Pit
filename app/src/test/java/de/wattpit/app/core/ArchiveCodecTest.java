package de.wattpit.app.core;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveCodecTest {
    private byte[] hex(String s){byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    @Test public void fragmentsAtEveryByteAndIgnoresHeaderLikeBytesInsideSamples(){
        byte[] response=hex("080319ec5b4c0200010000021e0008030903");
        for(int split=0;split<=response.length;split++){
            ArchiveCodec.Stream stream=new ArchiveCodec.Stream();List<ArchiveCodec.Message> out=new ArrayList<>();
            out.addAll(stream.feed(Arrays.copyOfRange(response,0,split)));out.addAll(stream.feed(Arrays.copyOfRange(response,split,response.length)));
            assertEquals(1,out.size());assertEquals(0xec5b,out.get(0).reg);assertEquals(12,out.get(0).data.length);
        }
        ArchiveCodec.Stream stream=new ArchiveCodec.Stream();List<ArchiveCodec.Message> all=new ArrayList<>();for(byte b:response)all.addAll(stream.feed(new byte[]{b}));assertEquals(1,all.size());
    }
    @Test public void longCborByteStringIsNotAShortLength(){
        byte[] b=new byte[6+64];byte[] header=hex("080319ec635840");System.arraycopy(header,0,b,0,header.length);
        // Header occupies 7 bytes: use a correctly sized register frame.
        b=Arrays.copyOf(b,71);assertEquals(64,new ArchiveCodec.Stream().feed(b).get(0).data.length);
    }
    @Test public void statusIsNotDataAndHandshakeCanBeFragmented(){
        ArchiveCodec.Stream stream=new ArchiveCodec.Stream();assertTrue(stream.feed(hex("029f000001")).isEmpty());
        List<ArchiveCodec.Message> out=stream.feed(hex("000301ff07000300090319ec5b03"));assertEquals(1,out.size());assertEquals(9,out.get(0).code);assertNull(out.get(0).data);assertEquals(3,out.get(0).status);
    }
    @Test public void validatesWidthsSignsSentinelsAndNewestFirst(){
        ArchiveCodec.Field current=ArchiveCodec.field(0xed8c,-1);
        ArchiveCodec.Block b=new ArchiveCodec.Block(hex("03e8030000021e0030f8ffffffffff7f"),current,3,2);
        assertEquals(1000,b.time);assertEquals(30,b.period);assertEquals(-2.0,b.values[0],1e-8);assertNull(b.values[1]);
        assertEquals("pvVoltage",ArchiveCodec.field(0xedbb,-1).name);assertEquals("auxRaw",ArchiveCodec.field(0xec86,-1).name);assertEquals("aux",ArchiveCodec.field(0xec86,0).name);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnexpectedChannel(){new ArchiveCodec.Block(hex("02e8030000011e000000"),ArchiveCodec.field(0xec8a,-1),1,1);}
    @Test(expected=IllegalArgumentException.class) public void neverAllowsArchiveErase(){ArchiveCodec.request(0xec5c,new byte[4]);}
    @Test(expected=IllegalArgumentException.class) public void neverAllowsClockWrite(){ArchiveCodec.request(0xec5f,new byte[8]);}
    @Test public void doesNotBorrowAClockAnchorAcrossReboots(){
        ArchiveCodec.Clock clock=new ArchiveCodec.Clock();byte[] anchors=new byte[16];System.arraycopy(ArchiveCodec.le(100,4),0,anchors,0,4);System.arraycopy(ArchiveCodec.le(1700000000L,4),0,anchors,4,4);System.arraycopy(ArchiveCodec.le(1100,4),0,anchors,8,4);System.arraycopy(ArchiveCodec.le(1700010000L,4),0,anchors,12,4);
        clock.anchors(anchors);clock.reboots(ArchiveCodec.le(1000,4));
        assertEquals(Long.valueOf(1700000100000L),clock.millis(200));assertEquals(Long.valueOf(1700009950000L),clock.millis(1050));
        clock.reboots(ArchiveCodec.le(2000,4));assertNull(clock.millis(2100));
    }
    @Test public void derivesRetentionFromDeviceInsteadOfHardcodingModels(){
        List<ArchiveCodec.Level> levels=ArchiveCodec.levels(hex("023878000100ef001e00"),hex("e803000084030000"));
        assertEquals(2,levels.size());assertEquals(881,levels.get(0).oldest);assertEquals(30,levels.get(1).period);assertEquals(0,levels.get(1).oldest);
        assertEquals(13,ArchiveCodec.trendQuery(2,1000,28).length);
    }
}
