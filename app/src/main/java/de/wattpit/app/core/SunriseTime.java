package de.wattpit.app.core;

import java.time.*;

public final class SunriseTime {
    /** Open-Meteo local ISO time belongs to its returned IANA zone, not the phone zone. */
    public static long parse(String day,String local,String timezone) {
        try {
            LocalDateTime time=LocalDateTime.parse(local);
            if(!time.toLocalDate().equals(LocalDate.parse(day)))return 0;
            return time.atZone(ZoneId.of(timezone)).toInstant().toEpochMilli();
        }catch(RuntimeException e){return 0;}
    }
    private SunriseTime(){}
}
