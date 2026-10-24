package de.wattpit.app.core;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Decoder for Victron manufacturer 0x02e1, product advertisements 0x10.
 * Layout: Victron Energy "Extra manufacturer Data", 2022-12-14.
 * Input excludes the two-byte Bluetooth manufacturer ID, as Android ScanRecord does.
 */
public final class VictronDecoder {
    private VictronDecoder() { }

    public static byte[] parseKey(String value) {
        String key = value.replaceAll("\\s", "");
        if (!key.matches("[0-9a-fA-F]{32}"))
            throw new IllegalArgumentException("Der Schlüssel muss aus 32 Hex-Zeichen bestehen.");
        byte[] out = new byte[16];
        for (int i = 0; i < 16; i++) out[i] = (byte) Integer.parseInt(key.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    public static String normalizeMac(String value) {
        String mac = value.trim().replace('-', ':').toUpperCase(Locale.ROOT);
        if (mac.matches("[0-9A-F]{12}")) mac = mac.replaceAll("(..)(?!$)", "$1:");
        if (!mac.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}"))
            throw new IllegalArgumentException("Bitte eine gültige Bluetooth-MAC-Adresse eingeben.");
        return mac;
    }

    public static int recordType(byte[] data) {
        return data != null && data.length >= 8 && (data[0] & 0xff) == 0x10 ? data[4] & 0xff : -1;
    }

    public static Reading decode(byte[] data, byte[] key) throws GeneralSecurityException {
        int type = recordType(data);
        if (type != 1 && type != 2) throw new IllegalArgumentException("Kein unterstütztes Instant-Readout-Paket.");
        if (key == null || key.length != 16 || key[0] != data[7])
            throw new IllegalArgumentException("Schlüssel passt nicht. Bitte aus VictronConnect neu übernehmen.");
        byte[] iv = new byte[16];
        iv[0] = data[5];
        iv[1] = data[6];
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return decodePayload(type, cipher.doFinal(Arrays.copyOfRange(data, 8, data.length)));
    }

    public static Reading decodePayload(int type, byte[] payload) {
        // The specification counts the 4-byte record header; payload offsets start at 0.
        int minimum = type == 1 ? 10 : type == 2 ? 15 : Integer.MAX_VALUE;
        if (payload.length < minimum) throw new IllegalArgumentException("Unvollständiges Datenpaket.");
        Reading r = new Reading();
        r.kind = type == 1 ? "solar" : "shunt";
        r.voltage = signedValue(payload, 16, 16, 0x7fff, 0.01);
        if (type == 1) {
            r.state = intValue(payload, 0, 8, 0xff);
            r.error = intValue(payload, 8, 8, 0xff);
            r.current = signedValue(payload, 32, 16, 0x7fff, 0.1);
            r.yieldWh = value(payload, 48, 16, 0xffff, 10.0);
            r.solar = value(payload, 64, 16, 0xffff, 1.0);
            if (payload.length * 8 >= 89) r.load = value(payload, 80, 9, 0x1ff, 0.1);
        } else {
            r.ttg = value(payload, 0, 16, 0xffff, 1.0);
            r.alarm = (int) bits(payload, 32, 16);
            int auxType = (int) bits(payload, 64, 2);
            long rawAux = bits(payload, 48, 16);
            if (auxType == 2 && rawAux != 0xffff) r.temperature = rawAux * 0.01 - 273.15;
            else if (auxType == 0 && rawAux != 0x7fff) r.aux = signed(rawAux, 16) * 0.01;
            else if (auxType == 1 && rawAux != 0xffff) r.aux = rawAux * 0.01;
            // Signed 22-bit NA is the maximum positive value. All ones means -1 mA.
            r.current = signedValue(payload, 66, 22, 0x1fffff, 0.001);
            r.consumed = value(payload, 88, 20, 0xfffff, -0.1);
            r.soc = value(payload, 108, 10, 0x3ff, 0.1);
            if (r.soc != null && r.soc > 100.0) throw new IllegalArgumentException("Unplausible Daten. Schlüssel prüfen.");
        }
        r.calculatePower();
        return r;
    }

    public static long bits(byte[] data, int start, int count) {
        if (start < 0 || count < 1 || count > 32 || start + count > data.length * 8)
            throw new IllegalArgumentException("Unvollständiges Datenfeld.");
        long out = 0;
        for (int i = 0; i < count; i++) out |= (long) ((data[(start + i) / 8] >> ((start + i) % 8)) & 1) << i;
        return out;
    }

    private static long signed(long value, int width) {
        return (value & (1L << (width - 1))) != 0 ? value - (1L << width) : value;
    }
    private static Double signedValue(byte[] p, int start, int width, long na, double factor) {
        long v = bits(p, start, width);
        return v == na ? null : signed(v, width) * factor;
    }
    private static Double value(byte[] p, int start, int width, long na, double factor) {
        long v = bits(p, start, width);
        return v == na ? null : v * factor;
    }
    private static Integer intValue(byte[] p, int start, int width, long na) {
        long v = bits(p, start, width);
        return v == na ? null : (int) v;
    }
}
