package de.wattpit.app.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveScheduleTest {
    @Test public void timeAloneNeverStartsAnArchiveConnection(){
        assertFalse(ArchiveSchedule.automaticDue(0,1000000,false));
        assertTrue(ArchiveSchedule.automaticDue(0,1000000,true));
    }
    @Test public void missingDataStillRespectTheCancellationPause(){
        assertFalse(ArchiveSchedule.automaticDue(ArchiveSchedule.nextAutomatic(1000,true),1001,true));
    }
    @Test public void repeatedForegroundEntriesCannotRestartDuringCooldown(){
        long next=ArchiveSchedule.nextAutomatic(1000,false);
        assertFalse(ArchiveSchedule.automaticDue(next,31001));assertFalse(ArchiveSchedule.automaticDue(next,300999));assertTrue(ArchiveSchedule.automaticDue(next,301000));
    }
    @Test public void explicitCancellationPausesAutomaticRetriesForThirtyMinutes(){
        long next=ArchiveSchedule.nextAutomatic(1000,true);
        assertFalse(ArchiveSchedule.automaticDue(next,1001));assertFalse(ArchiveSchedule.automaticDue(next,1800999));assertTrue(ArchiveSchedule.automaticDue(next,1801000));
    }
    @Test public void oneDevicesCooldownCannotSuppressAnotherDevice(){
        assertFalse(ArchiveSchedule.automaticDue(ArchiveSchedule.nextAutomatic(1000,false),1001));assertTrue(ArchiveSchedule.automaticDue(0,1001));
    }
    @Test public void aLargeBackwardPhoneClockChangeCannotBlockSyncIndefinitely(){
        assertTrue(ArchiveSchedule.automaticDue(7200000,1000));
    }
}
