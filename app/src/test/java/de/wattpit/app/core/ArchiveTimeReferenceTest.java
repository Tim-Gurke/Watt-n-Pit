package de.wattpit.app.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveTimeReferenceTest {
    private static final long NOW=1793011620000L;
    @Test public void bracketsCounterReadAndReportsTheSignOfAStaleMapping(){
        ArchiveTimeReference r=new ArchiveTimeReference(1234,NOW,NOW+200,50,250);
        assertTrue(r.trusted);assertEquals(NOW+100,r.observedMillis);assertEquals(1100,r.uncertaintyMillis);
        assertEquals(Long.valueOf(530100),r.differenceMillis(NOW-530000));
        assertEquals(Long.valueOf(-49900),r.differenceMillis(NOW+50000));
    }
    @Test public void missingDeviceAnchorRemainsUnknown(){
        ArchiveTimeReference r=new ArchiveTimeReference(1234,NOW,NOW+100,10,110);
        assertTrue(r.trusted);assertNull(r.differenceMillis(null));
    }
    @Test public void phoneClockJumpCannotBecomeAUsableOffset(){
        ArchiveTimeReference r=new ArchiveTimeReference(1234,NOW,NOW+60000,10,110);
        assertFalse(r.trusted);assertNull(r.differenceMillis(NOW));
        assertFalse(new ArchiveTimeReference(1234,NOW,NOW-60000,10,110).trusted);
    }
    @Test public void delayedOrInvalidReadsAreExplicitlyUntrusted(){
        assertFalse(new ArchiveTimeReference(1234,NOW,NOW+3000,10,3010).trusted);
        assertFalse(new ArchiveTimeReference(1234,NOW,NOW+100,110,10).trusted);
        assertFalse(new ArchiveTimeReference(ArchiveCodec.NA,NOW,NOW+100,10,110).trusted);
        assertFalse(new ArchiveTimeReference(-1,NOW,NOW+100,10,110).trusted);
    }
    @Test public void comparisonDoesNotChangeAnExistingClock(){
        ArchiveCodec.Clock c=new ArchiveCodec.Clock();byte[] anchor=new byte[8];
        System.arraycopy(ArchiveCodec.le(100,4),0,anchor,0,4);System.arraycopy(ArchiveCodec.le(NOW/1000-630,4),0,anchor,4,4);c.anchors(anchor);
        byte[] before=c.anchorBytes();Long time=c.millis(200);
        ArchiveTimeReference r=new ArchiveTimeReference(200,NOW,NOW+200,50,250);
        assertEquals(Long.valueOf(530100),r.differenceMillis(time));
        assertArrayEquals(before,c.anchorBytes());assertEquals(time,c.millis(200));
    }
}
