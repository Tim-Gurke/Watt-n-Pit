package de.wattpit.app.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class VictronDecoderTest {
    // Fixtures were encoded independently with Python cryptography, using the
    // published field layout and nonce 0x1234. No production encoder is reused.
    private static final byte[] KEY = VictronDecoder.parseKey("00112233445566778899aabbccddeeff");
    private static byte[] hex(String value) {
        byte[] data=new byte[value.length()/2];
        for(int i=0;i<data.length;i++)data[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);
        return data;
    }
    @Test public void decryptsSolarWithCorrectNonceOrderAndUnits() throws Exception {
        Reading r=VictronDecoder.decode(hex("1001a0a00134120039f8aef0449c08662c8cda58"),KEY);
        assertEquals("solar",r.kind);assertEquals(13.24,r.voltage,1e-8);
        assertEquals(10.5,r.current,1e-8);assertEquals(780,r.yieldWh,1e-8);
        assertEquals(139,r.solar,1e-8);assertEquals(2.1,r.load,1e-8);
        assertEquals(Integer.valueOf(3),r.state);assertEquals(Integer.valueOf(0),r.error);
    }
    @Test public void decryptsUnalignedSignedBatteryFields() throws Exception {
        Reading r=VictronDecoder.decode(hex("1001a0a002341200cef9a2f02d9c55127d4130b639067987"),KEY);
        assertEquals("shunt",r.kind);assertEquals(-3.210,r.current,1e-8);
        assertEquals(76.2,r.soc,1e-8);assertEquals(-23.8,r.consumed,1e-8);
        assertEquals(24.0,r.temperature,1e-8);assertNull(r.aux);
        assertEquals(500,r.ttg,1e-8);assertEquals(-42.1152,r.power,1e-8);
    }
    @Test public void unavailableValuesAreNullAndExtensionIsAccepted() {
        Reading r=VictronDecoder.decodePayload(1,hex("ffff ff7f ff7f ffff ffff ffff ffff".replace(" ","")));
        assertNull(r.state);assertNull(r.error);assertNull(r.voltage);assertNull(r.current);
        assertNull(r.yieldWh);assertNull(r.solar);assertNull(r.load);assertNull(r.power);
        Reading shunt=VictronDecoder.decodePayload(2,hex("ffff ff7f ffff ffff ffff ffff ffff ffff".replace(" ","")));
        assertNull(shunt.ttg);assertNull(shunt.current);assertNull(shunt.soc);assertNull(shunt.consumed);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsChangedKey() throws Exception {
        VictronDecoder.decode(hex("1001a0a00134120139f8aef0449c08662c8cda58"),KEY);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsTruncatedBattery() {
        VictronDecoder.decodePayload(2,new byte[14]);
    }
    @Test public void acceptsSolarWithoutOptionalLoadAndNormalizesMac() {
        Reading r=VictronDecoder.decodePayload(1,hex("03002c0569004e008b00"));assertNull(r.load);
        assertEquals("AA:BB:CC:DD:EE:FF",VictronDecoder.normalizeMac("aabbccddeeff"));
    }
}
