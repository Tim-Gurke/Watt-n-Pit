package de.wattpit.app.core;

/** JBD basic-info reply. Only complete, checksum-valid frames become measurements. */
public final class BmsPacket {
    public final double voltage, current, remainingAh, fullAh;
    public final int soc;
    public final double currentResolution;
    public final boolean chargeEnabled, dischargeEnabled;
    public final double[] temperatures;
    private BmsPacket(byte[] b) {
        voltage = u16(b, 4) / 100.0;
        int count = b[26] & 255;
        int optionalTail = 27 + count * 2;
        // Newer JBD firmware can append 0x88 to signal /10 current and capacity scaling.
        currentResolution = (b[3] & 255) > 23 + count * 2
                && (b[optionalTail] & 255) == 0x88 ? 0.1 : 0.01;
        current = (short) u16(b, 6) * currentResolution;
        remainingAh = u16(b, 8) * currentResolution;
        fullAh = u16(b, 10) * currentResolution;
        chargeEnabled = (b[24] & 1) != 0;
        dischargeEnabled = (b[24] & 2) != 0;
        soc = (b[23] & 255) <= 100 ? b[23] & 255 : -1;
        temperatures = new double[count];
        for (int i = 0; i < count; i++) temperatures[i] = (u16(b, 27 + i * 2) - 2731) / 10.0;
    }
    public static BmsPacket decode(byte[] b) {
        if (b == null || b.length < 30 || (b[0] & 255) != 0xdd
                || (b[1] & 255) != 3 || b[2] != 0) return null;
        int length = b[3] & 255;
        if (length < 23 || b.length != length + 7 || (b[b.length - 1] & 255) != 0x77) return null;
        int sum = 0;
        for (int i = 2; i < length + 4; i++) sum += b[i] & 255;
        if (((-sum) & 65535) != u16(b, length + 4)) return null;
        if (23 + (b[26] & 255) * 2 > length) return null;
        return new BmsPacket(b);
    }
    private static int u16(byte[] b, int i) { return ((b[i] & 255) << 8) | (b[i + 1] & 255); }
}

