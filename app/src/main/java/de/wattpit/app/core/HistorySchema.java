package de.wattpit.app.core;
public final class HistorySchema {
    private HistorySchema() { }
    public static final String COLUMNS="ts,device,kind,origin,voltage,current,power,soc,solar,yieldWh,ttg,consumed,temperature,aux,load,state,error,alarm,pvVoltage,auxRaw,period";
    public static final String CREATE="CREATE TABLE readings (ts INTEGER NOT NULL,device TEXT NOT NULL,kind TEXT NOT NULL,origin TEXT NOT NULL,voltage REAL,current REAL,power REAL,soc REAL,solar REAL,yieldWh REAL,ttg REAL,consumed REAL,temperature REAL,aux REAL,load REAL,state INTEGER,error INTEGER,alarm INTEGER,pvVoltage REAL,auxRaw REAL,period INTEGER NOT NULL DEFAULT 0,UNIQUE(ts,device,origin,period))";
    public static final String COVERAGE="CREATE TABLE archive_coverage(device TEXT NOT NULL,channel INTEGER NOT NULL,period INTEGER NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,PRIMARY KEY(device,channel,period,start))";
    public static final String[] MIGRATE={"DROP INDEX IF EXISTS history_window","DROP INDEX IF EXISTS history_device","ALTER TABLE readings RENAME TO readings_v4",CREATE,"INSERT INTO readings ("+COLUMNS+") SELECT "+COLUMNS+" FROM readings_v4","DROP TABLE readings_v4","CREATE INDEX history_window ON readings(ts,kind,origin)","CREATE INDEX history_device ON readings(device,ts)","DROP TABLE IF EXISTS archive_coverage",COVERAGE};
}
