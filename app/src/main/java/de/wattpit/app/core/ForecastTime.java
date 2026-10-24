package de.wattpit.app.core;

import java.time.*;

/** Provider day labels and real local clock times have different offset rules. */
public final class ForecastTime {
    private ForecastTime(){}
    /** Open-Meteo holds its response offset fixed, including across DST changes. */
    public static LocalDate providerDate(long epochSeconds,int responseOffsetSeconds){
        return Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.ofTotalSeconds(responseOffsetSeconds)).toLocalDate();
    }
    /** Hourly samples and measurement windows retain the actual location timezone. */
    public static long[] boundaries(LocalDate date,ZoneId zone){
        return new long[]{date.atStartOfDay(zone).toInstant().toEpochMilli(),date.atTime(11,0).atZone(zone).toInstant().toEpochMilli(),date.atTime(16,0).atZone(zone).toInstant().toEpochMilli(),date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()};
    }
}
