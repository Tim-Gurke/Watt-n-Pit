package de.wattpit.app.core;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PairingQrTest {
    @Test public void actualQrPixelsDecodeToTheExactConnectionCode()throws Exception{
        // Stable public fixture: random QR masks made this unrelated regression nondeterministic.
        String code=LanWire.encodeInvitation("{\"name\":\"Tims Wohnwagen – Südseite\",\"id\":\"dGVzdC1wbGFudC0wMDAwMDA\",\"key\":\"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY\",\"port\":28760,\"addresses\":[\"192.168.146.202\"]}");
        int[] pixels=PairingQr.pixels(code);assertEquals(PairingQr.SIZE*PairingQr.SIZE,pixels.length);
        Result result=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(PairingQr.SIZE,PairingQr.SIZE,pixels))));
        assertEquals(BarcodeFormat.QR_CODE,result.getBarcodeFormat());assertEquals(code,result.getText());
        assertEquals(0xffffffff,pixels[0]);assertEquals(0xffffffff,pixels[pixels.length-1]);
    }
    @Test public void qrCanonicalizesHarmlessClipboardFormatting()throws Exception{
        String code=LanWire.encodeInvitation("{\"name\":\"Wohnwagen\"}");int[] pixels=PairingQr.pixels("WNP1."+code+"\r\n");
        Result result=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(PairingQr.SIZE,PairingQr.SIZE,pixels))));
        assertEquals(code,result.getText());
    }
}
