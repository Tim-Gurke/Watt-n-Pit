package de.wattpit.app.core;
import org.junit.Test;import static org.junit.Assert.*;
public class ArchiveDiagnosticTest {
    @Test public void missingPlatformMessageCannotBreakFailureReporting(){assertEquals("Unbekannter Fehler",ArchiveDiagnostic.clean(null));}
    @Test public void identifiersAndPinsAreRemovedButGattStatusRemainsUseful(){
        String result=ArchiveDiagnostic.clean("GATT 133 AA:BB:CC:DD:EE:FF 0123456789abcdef0123456789abcdef 123456");
        assertEquals("GATT 133 [Geräteadresse] [Schlüssel] [Code]",result);
    }
}
