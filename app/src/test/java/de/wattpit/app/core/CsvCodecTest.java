package de.wattpit.app.core;

import java.time.ZoneId;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CsvCodecTest {
    @Test public void importsGermanVictronStyleExportWithMetadataAndUnits() {
        CsvCodec.Table t=CsvCodec.parse("\ufeffExport: SmartSolar 75/15\r\nDatum;Zeit;Battery voltage (V);Battery current (mA);PV power (kW);Yield today (kWh)\r\n14.10.2026;11:00:00;13,2;5000;0,120;0,76\r\n");
        Reading r=CsvCodec.reading(t,t.rows.get(0),CsvCodec.guess(t.headers),"solar",ZoneId.of("Europe/Berlin"));
        assertEquals(13.2,r.voltage,1e-8);assertEquals(5,r.current,1e-8);assertEquals(120,r.solar,1e-8);assertEquals(760,r.yieldWh,1e-8);
        assertEquals("2026-10-14T09:00:00Z",java.time.Instant.ofEpochMilli(r.timestamp).toString());
    }
    @Test public void roundTripsNativeBackupWithoutSourceLoss() {
        Reading original=new Reading();original.timestamp=1791968400000L;original.kind="shunt";original.device="AA:BB:CC:DD:EE:FF";
        original.voltage=12.94;original.current=-2.2;original.soc=76.4;original.origin="live";original.calculatePower();
        CsvCodec.Table table=CsvCodec.parse(CsvCodec.EXPORT_HEADER+"\n"+CsvCodec.exportRow(original)+"\n");
        Reading restored=CsvCodec.reading(table,table.rows.get(0),CsvCodec.guess(table.headers),"solar",ZoneId.of("UTC"));
        assertEquals(original.timestamp,restored.timestamp);assertEquals(original.device,restored.device);assertEquals("live",restored.origin);
        assertEquals(original.soc,restored.soc);assertEquals(original.power,restored.power);
    }
    @Test public void handlesQuotedCommasNewlinesAndEmptyCells() {
        CsvCodec.Table t=CsvCodec.parse("Timestamp,\"Battery Voltage (V)\",\"Note, quoted\"\n2026-10-14T09:00:00Z,13.2,\"first\nsecond\"\n");
        assertEquals(3,t.headers.size());assertEquals("first\nsecond",t.rows.get(0).get(2));
        assertEquals(13.2,CsvCodec.reading(t,t.rows.get(0),CsvCodec.guess(t.headers),"shunt",ZoneId.of("UTC")).voltage,1e-8);
    }
    @Test public void parsesOffsetTimeEpochAndDoesNotInventMissingNumbers() {
        assertEquals(CsvCodec.parseTime("2026-10-14T11:00:00+02:00",ZoneId.of("UTC")),CsvCodec.parseTime("2026-10-14 11:00:00",ZoneId.of("Europe/Berlin")));
        assertEquals(1791968400000L,CsvCodec.parseTime("1791968400",ZoneId.of("UTC")));
        CsvCodec.Table t=CsvCodec.parse("timestamp;soc;voltage\n2026-10-14T09:00:00Z;N/A;13.2\n");
        Reading r=CsvCodec.reading(t,t.rows.get(0),CsvCodec.guess(t.headers),"shunt",ZoneId.of("UTC"));assertNull(r.soc);assertNull(r.current);assertNull(r.power);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsMalformedQuotes() { CsvCodec.parse("timestamp;voltage\n\"broken;13.2"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidDateInsteadOfSilentlyShiftingIt() { CsvCodec.parseTime("31.02.2026 12:00",ZoneId.of("UTC")); }
    @Test public void archiveBackupRetainsResolutionAndPvVoltage(){
        Reading original=new Reading();original.timestamp=1700000000000L;original.device="TEST";original.kind="solar";original.origin="archive";original.period=1800;original.pvVoltage=18.9;original.auxRaw=123.0;
        CsvCodec.Table table=CsvCodec.parse(CsvCodec.EXPORT_HEADER+"\n"+CsvCodec.exportRow(original));Reading restored=CsvCodec.reading(table,table.rows.get(0),CsvCodec.guess(table.headers),"solar",ZoneId.of("UTC"));
        assertEquals("archive",restored.origin);assertEquals(1800,restored.period);assertEquals(original.pvVoltage,restored.pvVoltage);assertEquals(original.auxRaw,restored.auxRaw);
    }
    @Test public void nativeBackupRepairsUnavailableCurrentAndRetainsOtherFields() {
        Reading old=new Reading();old.timestamp=1700000000000L;old.device="test-shunt";old.kind="shunt";
        old.voltage=13.4;old.current=2097.151;old.power=28101.8234;old.soc=100.0;old.period=10;old.alarm=0;
        CsvCodec.Table table=CsvCodec.parse(CsvCodec.EXPORT_HEADER+"\n"+CsvCodec.exportRow(old));
        Reading r=CsvCodec.reading(table,table.rows.get(0),CsvCodec.guess(table.headers),"solar",ZoneId.of("UTC"));
        assertNull(r.current);assertNull(r.power);assertEquals(old.timestamp,r.timestamp);assertEquals(old.device,r.device);
        assertEquals(old.voltage,r.voltage);assertEquals(old.soc,r.soc);assertEquals(old.alarm,r.alarm);assertEquals(10,r.period);
        // Real observed failure also had no voltage or SOC: retain the row's time and alarm.
        old.voltage=null;old.soc=null;old.power=null;
        table=CsvCodec.parse(CsvCodec.EXPORT_HEADER+"\n"+CsvCodec.exportRow(old));
        r=CsvCodec.reading(table,table.rows.get(0),CsvCodec.guess(table.headers),"solar",ZoneId.of("UTC"));
        assertNull(r.current);assertNull(r.power);assertEquals(old.timestamp,r.timestamp);assertEquals(old.alarm,r.alarm);
        // A backup made after the repair still retains this status-only sample.
        table=CsvCodec.parse(CsvCodec.EXPORT_HEADER+"\n"+CsvCodec.exportRow(r));
        Reading again=CsvCodec.reading(table,table.rows.get(0),CsvCodec.guess(table.headers),"solar",ZoneId.of("UTC"));
        assertEquals(r.timestamp,again.timestamp);assertEquals(r.device,again.device);assertEquals(r.alarm,again.alarm);assertNull(again.current);assertNull(again.power);
    }
    @Test public void unavailableCurrentRepairIsLimitedToInstantReadout() {
        for(String kind:new String[]{"solar","shunt"})for(String origin:new String[]{"archive","csv","live"}){
            if(kind.equals("shunt")&&origin.equals("live"))continue;
            Reading r=new Reading();r.kind=kind;r.origin=origin;r.voltage=12.0;r.current=2097.151;r.calculatePower();
            assertEquals(2097.151,r.current,1e-9);assertEquals(25165.812,r.power,1e-8);
        }
    }
}
