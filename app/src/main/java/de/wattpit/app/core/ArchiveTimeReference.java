package de.wattpit.app.core;

/** Read-only comparison of a device counter with a bracketed phone-clock read.
 * This observation never changes the archive clock or any stored measurement. */
public final class ArchiveTimeReference {
    public final long counter,wallBefore,wallAfter,roundTripMillis,wallElapsedMillis,observedMillis,uncertaintyMillis;
    public final boolean trusted;
    public ArchiveTimeReference(long counter,long wallBefore,long wallAfter,long elapsedBefore,long elapsedAfter){
        this.counter=counter;this.wallBefore=wallBefore;this.wallAfter=wallAfter;
        roundTripMillis=elapsedAfter-elapsedBefore;wallElapsedMillis=wallAfter-wallBefore;
        observedMillis=wallBefore+Math.max(0,roundTripMillis)/2;
        // The device counter has one-second resolution; transport adds half the round trip.
        uncertaintyMillis=1000+Math.max(0,roundTripMillis)/2;
        trusted=counter>=0&&counter<ArchiveCodec.NA&&wallBefore>946684800000L
            &&roundTripMillis>=0&&roundTripMillis<=2000
            &&Math.abs(wallElapsedMillis-roundTripMillis)<=1000;
    }
    /** Positive means the saved mapping places the current device counter too early. */
    public Long differenceMillis(Long mappedMillis){return trusted&&mappedMillis!=null?observedMillis-mappedMillis:null;}
}
