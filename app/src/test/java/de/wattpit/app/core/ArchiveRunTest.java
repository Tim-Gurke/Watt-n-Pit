package de.wattpit.app.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveRunTest {
    @Test public void backgroundIsDefaultForEachNewQueue(){ArchiveRun r=new ArchiveRun();r.begin(100);long id=r.state().id;assertTrue(r.state().background);assertTrue(r.background(id));assertTrue(r.state().background);r.finish();r.begin(200);assertTrue(r.state().background);assertTrue(r.state().id>id);}
    @Test public void repeatedBeginForAnotherDeviceRetainsTheChoice(){ArchiveRun r=new ArchiveRun();r.begin(100);long id=r.state().id;r.background(id);r.begin(200);assertEquals(id,r.state().id);assertEquals(100,r.state().startedAt);assertTrue(r.state().background);}
    @Test public void aStaleChoiceCannotAffectTheNextQueue(){ArchiveRun r=new ArchiveRun();r.begin(100);long old=r.state().id;r.finish();r.begin(200);assertFalse(r.background(old));assertTrue(r.state().background);}
    @Test public void completionCancellationAndErrorsDiscardTheChoice(){ArchiveRun r=new ArchiveRun();r.begin(100);long id=r.state().id;r.background(id);r.finish();assertFalse(r.state().busy);assertFalse(r.state().background);assertFalse(r.background(id));}
    @Test public void aPublishedStateCannotChangeBehindTheReader(){ArchiveRun r=new ArchiveRun();r.begin(100);ArchiveRun.State before=r.state();r.background(before.id);assertTrue(before.background);assertTrue(r.state().background);r.finish();assertTrue(before.busy);}
    @Test public void noBackgroundChoiceIsAcceptedWithoutAnActiveQueue(){ArchiveRun r=new ArchiveRun();assertFalse(r.background(0));assertFalse(r.state().busy);r.finish();assertFalse(r.state().background);}
}
