package de.wattpit.app.core;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/** Synthetic plant and the production QR pixels for browser tests; no private device data. */
public final class PairingQrFixture {
    public static void main(String[] args)throws Exception{
        String code=LanWire.encodeInvitation("{\"id\":\""+LanWire.random(16)+"\",\"name\":\"Beispiel-Wohnwagen\",\"key\":\""+LanWire.random(32)+"\",\"port\":28760,\"addresses\":[\"192.168.43.10\"]}");
        BufferedImage image=new BufferedImage(PairingQr.SIZE,PairingQr.SIZE,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,PairingQr.SIZE,PairingQr.SIZE,PairingQr.pixels(code),0,PairingQr.SIZE);
        try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()){ImageIO.write(image,"png",bytes);System.out.println("{\"code\":\""+code+"\",\"image\":\"data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray())+"\"}");}
    }
}
