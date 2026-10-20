package de.wattpit.app.core;

/** Monotonic limits bound the whole queue, individual devices and every transport wait. */
public final class ArchiveBudget {
    public static final long AUTO_DEVICE_MS=15000, AUTO_RUN_MS=30000, MANUAL_MS=120000;
    public static final long RETRY_MS=300000, CANCEL_PAUSE_MS=1800000;
    private final boolean automatic;
    private final long runEnd;
    private long deviceEnd;
    public ArchiveBudget(boolean automatic,long now){this.automatic=automatic;runEnd=now+(automatic?AUTO_RUN_MS:MANUAL_MS);deviceEnd=runEnd;}
    public void device(long now){deviceEnd=Math.min(runEnd,now+(automatic?AUTO_DEVICE_MS:MANUAL_MS));}
    public long remaining(long now){return Math.max(0,Math.min(runEnd,deviceEnd)-now);}
    public long waitMillis(long now,long maximum){return Math.min(remaining(now),maximum);}
    public boolean queueEnded(long now){return now>=runEnd;}
    public static boolean automaticDue(long nextAllowed,long now){return nextAllowed<=now||nextAllowed-now>CANCEL_PAUSE_MS;}
    public static long nextAutomatic(long now,boolean cancelled){return now+(cancelled?CANCEL_PAUSE_MS:RETRY_MS);}
}
