package de.wattpit.app.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveClockAlignmentTest {
    private static final long EPOCH=1700000000000L;
    private byte[] anchor(long c,long at){byte[] b=new byte[8];System.arraycopy(ArchiveCodec.le(c,4),0,b,0,4);System.arraycopy(ArchiveCodec.le(at/1000,4),0,b,4,4);return b;}
    private ArchiveTimeReference read(long counter,long now){return new ArchiveTimeReference(counter,now-50,now+50,10,110);}
    @Test public void correctsAnAccumulatedOffsetUsingRealReferencesInsteadOfAFixedShift(){
        ArchiveCodec.Clock c=new ArchiveCodec.Clock();c.anchors(anchor(100,EPOCH));
        assertTrue(c.observe(read(5000100,EPOCH+5000543000L)));
        assertEquals(Long.valueOf(EPOCH),c.millis(100));assertEquals(Long.valueOf(EPOCH+2500271500L),c.millis(2500100));
        assertEquals(Long.valueOf(EPOCH+5000543000L),c.millis(5000100));
        assertEquals(Long.valueOf(2500100),c.counter(EPOCH+2500271500L));
    }
    @Test public void aLocalReferenceNeverCrossesAnEarlierPowerLoss(){
        ArchiveCodec.Clock c=new ArchiveCodec.Clock();c.anchors(anchor(100,EPOCH));c.reboots(ArchiveCodec.le(1000,4));
        c.observe(read(1100,EPOCH+10000000));
        assertEquals(Long.valueOf(EPOCH+100000),c.millis(200));assertEquals(Long.valueOf(EPOCH+9950000),c.millis(1050));
        c.reboots(ArchiveCodec.le(2000,4));assertNull(c.millis(2100));
    }
    @Test public void preservesCurrentMappingAcrossCacheAndIgnoresCounterQuantizationJitter(){
        ArchiveCodec.Clock c=new ArchiveCodec.Clock();c.anchors(anchor(100,EPOCH));c.observe(read(5000100,EPOCH+5000543000L));
        byte[] locals=c.localBytes();ArchiveCodec.Clock restored=ArchiveCodec.Clock.restore(5000100,5000200,c.anchorBytes(),c.rebootBytes());restored.localBytes(locals);
        long time=restored.millis(5000200);assertFalse(restored.observe(read(5000200,time+1200)));assertArrayEquals(locals,restored.localBytes());
        assertEquals(c.millis(100000),restored.millis(100000));assertTrue(restored.observe(read(5000300,restored.millis(5000300)+4000)));
    }
    @Test public void inverseRejectsAmbiguousOriginalClockIntervals(){
        ArchiveCodec.Clock c=new ArchiveCodec.Clock();c.anchors(anchor(100,EPOCH));c.anchors(anchor(200,EPOCH+50000));
        assertNull(c.counter(EPOCH+75000));assertEquals(Long.valueOf(110),c.counter(EPOCH+10000));assertNull(c.counter(EPOCH+123));
    }
    @Test public void oldBootWithoutAnAnchorIsNotBorrowedFromTheCurrentBoot(){
        ArchiveCodec.Clock c=new ArchiveCodec.Clock();c.reboots(ArchiveCodec.le(1000,4));c.observe(read(1100,EPOCH));
        assertNull(c.millis(900));assertNotNull(c.millis(1100));assertTrue(c.hasAnchors());
    }
    @Test public void aNewDeviceSyncDoesNotUndoTheRetainedHistoricalDriftReference(){
        ArchiveCodec.Clock c=new ArchiveCodec.Clock();c.anchors(anchor(100,EPOCH));c.observe(read(5000100,EPOCH+5000543000L));
        long historical=c.millis(2500100);c.anchors(anchor(5000090,EPOCH+5000533000L));
        assertEquals(Long.valueOf(historical),c.millis(2500100));
        c.observe(read(5021700,EPOCH+5022145345L));
        assertTrue(Math.abs(c.millis(2500100)-historical)<1000);
    }
    @Test public void longRunningCountersDoNotOverflowTheDriftCalculation(){
        ArchiveCodec.Clock c=new ArchiveCodec.Clock();c.anchors(anchor(100,EPOCH));long counter=2000000100L,now=EPOCH+2000200000000L;
        c.observe(read(counter,now));assertEquals(Long.valueOf(now),c.millis(counter));assertEquals(Long.valueOf(counter),c.counter(now));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsAnUntrustedRead(){
        new ArchiveCodec.Clock().observe(new ArchiveTimeReference(100,EPOCH,EPOCH+60000,10,110));
    }
}
