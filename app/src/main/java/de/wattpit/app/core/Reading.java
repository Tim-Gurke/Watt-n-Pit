package de.wattpit.app.core;

/** One device sample. Missing readings are null, never substituted with zero. */
public final class Reading {
    /** Victron battery-monitor Instant Readout: signed 22-bit current NA, in amps. */
    public static final double UNAVAILABLE_SHUNT_CURRENT = 0x1fffff * 0.001;
    public long timestamp;
    public String device = "", kind = "", origin = "live";
    public Double voltage, current, power, soc, solar, yieldWh, ttg, consumed, temperature, aux, load;
    public Double pvVoltage,auxRaw;
    public int period;
    public Integer state, error, alarm;

    /** Also repairs native backups written before the Instant Readout NA fix. */
    public boolean discardUnavailableCurrent() {
        if ("shunt".equals(kind) && "live".equals(origin) && current != null
                && current == UNAVAILABLE_SHUNT_CURRENT) {
            current = null;
            power = null;
            return true;
        }
        return false;
    }

    public void calculatePower() {
        discardUnavailableCurrent();
        if (power == null && voltage != null && current != null) power = voltage * current;
    }
}
