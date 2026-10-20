package de.wattpit.app.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveBudgetTest {
    @Test public void automaticQueueStopsWithinThirtySecondsEvenAcrossDevices(){
        ArchiveBudget b=new ArchiveBudget(true,1000);b.device(1000);
        assertEquals(15000,b.remaining(1000));assertEquals(0,b.remaining(16000));
        b.device(16000);assertEquals(15000,b.remaining(16000));assertEquals(0,b.remaining(31000));assertTrue(b.queueEnded(31000));
    }
    @Test public void startingAnotherDeviceCannotExtendTheWholeQueue(){
        ArchiveBudget b=new ArchiveBudget(true,1000);b.device(30000);
        assertEquals(1000,b.remaining(30000));b.device(31000);assertEquals(0,b.remaining(31000));
    }
    @Test public void manualContinuationHasItsOwnFiniteBudget(){
        ArchiveBudget b=new ArchiveBudget(false,1000);b.device(1000);
        assertEquals(120000,b.remaining(1000));assertFalse(b.queueEnded(120999));assertTrue(b.queueEnded(121000));
    }
    @Test public void gattTimeoutIsCappedByTheRemainingDeviceBudget(){
        ArchiveBudget b=new ArchiveBudget(true,1000);b.device(1000);
        assertEquals(15000,b.waitMillis(1000,20000));assertEquals(500,b.waitMillis(15500,20000));assertEquals(0,b.waitMillis(16000,20000));
    }
    @Test public void repeatedForegroundEntriesCannotRestartDuringCooldown(){
        long next=ArchiveBudget.nextAutomatic(1000,false);
        assertFalse(ArchiveBudget.automaticDue(next,31001));assertFalse(ArchiveBudget.automaticDue(next,300999));assertTrue(ArchiveBudget.automaticDue(next,301000));
    }
    @Test public void explicitCancellationPausesAutomaticRetriesForThirtyMinutes(){
        long next=ArchiveBudget.nextAutomatic(1000,true);
        assertFalse(ArchiveBudget.automaticDue(next,1001));assertFalse(ArchiveBudget.automaticDue(next,1800999));assertTrue(ArchiveBudget.automaticDue(next,1801000));
    }
    @Test public void oneDevicesCooldownCannotSuppressAnotherDevice(){
        assertFalse(ArchiveBudget.automaticDue(ArchiveBudget.nextAutomatic(1000,false),1001));assertTrue(ArchiveBudget.automaticDue(0,1001));
    }
    @Test public void aLargeBackwardPhoneClockChangeCannotBlockSyncIndefinitely(){
        assertTrue(ArchiveBudget.automaticDue(7200000,1000));
    }
    @Test public void manualBudgetDoesNotConsultTheAutomaticCooldown(){
        long next=ArchiveBudget.nextAutomatic(1000,true);assertFalse(ArchiveBudget.automaticDue(next,1001));
        ArchiveBudget manual=new ArchiveBudget(false,1001);manual.device(1001);assertEquals(120000,manual.remaining(1001));
    }
}
