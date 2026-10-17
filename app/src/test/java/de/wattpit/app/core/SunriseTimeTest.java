package de.wattpit.app.core;

import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class SunriseTimeTest {
    @Test public void usesTheWeatherLocationTimezoneIncludingSummerTime(){
        assertEquals(Instant.parse("2026-10-17T05:48:00Z").toEpochMilli(),SunriseTime.parse("2026-10-17","2026-10-17T07:48","Europe/Berlin"));
        assertEquals(Instant.parse("2026-11-17T06:48:00Z").toEpochMilli(),SunriseTime.parse("2026-11-17","2026-11-17T07:48","Europe/Berlin"));
    }
    @Test public void missingPolarOrMalformedTimesDoNotBecomeMidnight(){
        for(String value:new String[]{"","null","bad-date"})assertEquals(0,SunriseTime.parse("2026-10-17",value,"Europe/Berlin"));
        assertEquals(0,SunriseTime.parse("2026-10-17","2026-10-18T07:48","Europe/Berlin"));
        assertEquals(0,SunriseTime.parse("2026-10-17","2026-10-17T07:48","invalid"));
    }
}
