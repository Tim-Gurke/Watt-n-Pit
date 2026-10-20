package de.wattpit.app.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveClockCacheTest {
    private byte[] anchor(long uptime,long unix){byte[] b=new byte[8];System.arraycopy(ArchiveCodec.le(uptime,4),0,b,0,4);System.arraycopy(ArchiveCodec.le(unix,4),0,b,4,4);return b;}
    @Test public void cachedClockPreservesBothTimeAndBootBoundaries(){
        ArchiveCodec.Clock c=new ArchiveCodec.Clock();c.anchors(anchor(100,1700000000));c.anchors(anchor(1100,1700010000));c.reboots(ArchiveCodec.le(1000,4));
        ArchiveCodec.Clock restored=ArchiveCodec.Clock.restore(1200,1300,c.anchorBytes(),c.rebootBytes());
        assertEquals(c.millis(200),restored.millis(200));assertEquals(c.millis(1100),restored.millis(1100));
        assertEquals(100,restored.oldestStoredAnchor());assertEquals(1000,restored.oldestStoredReboot());
    }
    @Test public void cachedReferencesAreDiscardedWhenDeviceTimeGoesBackwards(){
        ArchiveCodec.Clock c=ArchiveCodec.Clock.restore(1200,100,anchor(1100,1700010000),new byte[0]);
        assertFalse(c.hasAnchors());assertNull(c.millis(100));
    }
    @Test public void alreadyCachedPagesAreRecognizedWithoutCountingThemAsNew(){
        ArchiveCodec.Clock c=new ArchiveCodec.Clock();byte[] a=anchor(100,1700000000);
        assertEquals(1,c.anchors(a));assertEquals(0,c.anchors(a));
        assertEquals(1,c.reboots(ArchiveCodec.le(90,4)));assertEquals(0,c.reboots(ArchiveCodec.le(90,4)));
    }
    @Test public void correctedCurrentReferenceReplacesOnlyTheMatchingCachedReference(){
        ArchiveCodec.Clock c=new ArchiveCodec.Clock();c.anchors(anchor(100,1700000000));c.anchors(anchor(200,1700000100));
        assertEquals(1,c.anchors(anchor(200,1700000105)));assertEquals(Long.valueOf(1700000105000L),c.millis(200));assertEquals(Long.valueOf(1700000000000L),c.millis(100));
    }
    @Test public void aBootWithoutItsOwnAnchorStaysUnknownAfterCacheRestore(){
        ArchiveCodec.Clock c=ArchiveCodec.Clock.restore(1200,1300,anchor(100,1700000000),ArchiveCodec.le(1000,4));
        assertNotNull(c.millis(200));assertNull(c.millis(1100));
    }
    @Test(expected=IllegalArgumentException.class) public void malformedCachedAnchorIsRejected(){ArchiveCodec.Clock.restore(100,200,new byte[7],new byte[0]);}
    @Test public void unavailableAnchorSentinelsAreNotCachedAsARealReference(){
        ArchiveCodec.Clock c=new ArchiveCodec.Clock();assertEquals(0,c.anchors(anchor(ArchiveCodec.NA,1700000000)));assertFalse(c.hasAnchors());
    }
}
