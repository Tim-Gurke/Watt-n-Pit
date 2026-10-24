package de.wattpit.app.core;

import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ForecastTimeTest {
    @Test public void autumnProviderDaysRemainDistinctWhenTheResponseUsesSummerOffset(){
        long first=Instant.parse("2026-10-23T22:00:00Z").getEpochSecond();
        for(int i=0;i<15;i++)assertEquals(LocalDate.of(2026,10,24).plusDays(i),ForecastTime.providerDate(first+i*86400L,7200));
        assertEquals(LocalDate.of(2026,10,26),ForecastTime.providerDate(Instant.parse("2026-10-25T22:00:00Z").getEpochSecond(),7200));
    }
    @Test public void springPastDaysKeepTheirDateWhenTheResponseAlreadyUsesSummerOffset(){
        long first=Instant.parse("2026-03-26T22:00:00Z").getEpochSecond();
        for(int i=0;i<15;i++)assertEquals(LocalDate.of(2026,3,27).plusDays(i),ForecastTime.providerDate(first+i*86400L,7200));
    }
    @Test public void providerDateUsesTheReturnedOffsetForWesternAndHalfHourTimezones(){
        assertEquals(LocalDate.of(2026,10,25),ForecastTime.providerDate(Instant.parse("2026-10-25T04:00:00Z").getEpochSecond(),-14400));
        assertEquals(LocalDate.of(2026,10,26),ForecastTime.providerDate(Instant.parse("2026-10-25T18:30:00Z").getEpochSecond(),19800));
    }
    @Test public void localWindowsPreserveRealTwentyThreeAndTwentyFiveHourDays(){
        ZoneId zone=ZoneId.of("Europe/Berlin");long[] spring=ForecastTime.boundaries(LocalDate.of(2026,3,29),zone),autumn=ForecastTime.boundaries(LocalDate.of(2026,10,25),zone);
        assertEquals(23*3600000L,spring[3]-spring[0]);assertEquals(10*3600000L,spring[1]-spring[0]);
        assertEquals(25*3600000L,autumn[3]-autumn[0]);assertEquals(12*3600000L,autumn[1]-autumn[0]);
        assertEquals(Instant.parse("2026-10-25T10:00:00Z").toEpochMilli(),autumn[1]);
    }
}
