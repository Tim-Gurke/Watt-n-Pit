package de.wattpit.app.core;

/** Retry spacing is independent of the size and duration of a genuine data gap. */
public final class ArchiveSchedule {
    private ArchiveSchedule() { }
    public static final long RETRY_MS=300000,CANCEL_PAUSE_MS=1800000;
    public static boolean automaticDue(long nextAllowed,long now){return nextAllowed<=now||nextAllowed-now>CANCEL_PAUSE_MS;}
    public static boolean automaticDue(long nextAllowed,long now,boolean missing){return missing&&automaticDue(nextAllowed,now);}
    public static long nextAutomatic(long now,boolean cancelled){return now+(cancelled?CANCEL_PAUSE_MS:RETRY_MS);}
}
