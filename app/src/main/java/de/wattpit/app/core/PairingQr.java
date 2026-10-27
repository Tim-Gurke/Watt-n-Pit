package de.wattpit.app.core;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.EnumMap;
import java.util.Map;

/** Exact, offline QR pixels. Theme colors never change the scan contrast or quiet zone. */
public final class PairingQr {
    public static final int SIZE=640;
    private PairingQr(){}
    public static int[] pixels(String invitation)throws WriterException{
        String code=LanWire.encodeInvitation(LanWire.decodeInvitation(invitation));
        Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");hints.put(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M);hints.put(EncodeHintType.MARGIN,4);
        BitMatrix matrix=new QRCodeWriter().encode(code,BarcodeFormat.QR_CODE,SIZE,SIZE,hints);int[] pixels=new int[SIZE*SIZE];
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)pixels[y*SIZE+x]=matrix.get(x,y)?0xff000000:0xffffffff;
        return pixels;
    }
}
