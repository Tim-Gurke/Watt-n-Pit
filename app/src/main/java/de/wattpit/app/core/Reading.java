package de.wattpit.app.core;

/** One device sample. Missing readings are null, never substituted with zero. */
public final class Reading {
    public long timestamp;
    public String device = "", kind = "", origin = "live";
    public Double voltage, current, power, soc, solar, yieldWh, ttg, consumed, temperature, aux, load;
    public Double pvVoltage,auxRaw;
    public int period;
    public Integer state, error, alarm;

    public void calculatePower() {
        if (power == null && voltage != null && current != null) power = voltage * current;
    }
}
