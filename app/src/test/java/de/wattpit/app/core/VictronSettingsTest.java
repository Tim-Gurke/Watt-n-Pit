package de.wattpit.app.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class VictronSettingsTest {
    private VictronSettings.Field field(String kind,int reg){return VictronSettings.fields(kind).stream().filter(f->f.register==reg).findFirst().get();}
    @Test public void decodesDocumentedUnitsAndSignedCompensation(){
        assertEquals(100,field("shunt",0x1000).decode(ArchiveCodec.le(100,2)),0);
        assertEquals(13.9,field("shunt",0x1001).decode(ArchiveCodec.le(139,2)),1e-8);
        assertEquals(4,field("shunt",0x1002).decode(ArchiveCodec.le(40,2)),1e-8);
        assertEquals(1.05,field("shunt",0x1005).decode(ArchiveCodec.le(105,2)),1e-8);
        assertEquals(13.6,field("solar",0xEDF6).decode(ArchiveCodec.le(1360,2)),1e-8);
        assertEquals(-16,field("solar",0xEDF2).decode(ArchiveCodec.le(-1600,2)),1e-8);
        assertNull(field("solar",0xEDF7).decode(ArchiveCodec.le(65535,2)));
        assertNull(field("shunt",0x1000).decode(new byte[1]));
    }
    @Test public void settingsCanOnlyBeRead(){
        for(String kind:new String[]{"solar","shunt"})for(VictronSettings.Field f:VictronSettings.fields(kind)){
            assertEquals(5,ArchiveCodec.read(f.register)[0]);
            try{ArchiveCodec.request(f.register,new byte[f.width]);fail("Configuration write allowed");}catch(IllegalArgumentException expected){}
        }
        assertFalse(VictronSettings.readable(0x102c));assertFalse(VictronSettings.readable(0xec5c));
    }
    @Test public void lowVoltageBulkDoesNotBecomeCurtailmentBecauseAShuntSaysFull(){
        assertEquals("clear",VictronSettings.limit(3,13.39,2,14.2,15.0,0.0));
        assertEquals("voltage",VictronSettings.limit(5,13.39,.1,13.6,15.0,0.0));
        assertEquals("voltage",VictronSettings.limit(4,13.6,2,13.6,15.0,0.0));
        assertEquals("voltage",VictronSettings.limit(3,13.58,2,13.6,15.0,0.0));
        assertEquals("current",VictronSettings.limit(3,13.0,14.8,14.2,15.0,0.0));
        assertEquals("unknown",VictronSettings.limit(3,13.0,2,null,15.0,0.0));
        assertEquals("unknown",VictronSettings.limit(3,13.0,2,14.2,15.0,-16.0));
    }
}
