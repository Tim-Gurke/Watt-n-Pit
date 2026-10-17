package de.wattpit.app.core;

import java.sql.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class HistoryQueryTest {
    private Connection db;
    @Before public void create() throws Exception {
        db=DriverManager.getConnection("jdbc:sqlite::memory:");
        db.createStatement().execute("CREATE TABLE readings(ts INTEGER,device TEXT,kind TEXT,origin TEXT,voltage REAL,current REAL,power REAL,soc REAL,solar REAL,yieldWh REAL)");
        db.createStatement().execute("INSERT INTO readings VALUES (100000,'shunt','shunt','live',13.2,-2.0,-26.4,NULL,NULL,NULL),"+
                "(100000,'csv:shunt','shunt','csv',12.8,-3.0,-38.4,72.0,NULL,NULL),"+
                "(100000,'csv:solar','solar','csv',13.3,5.0,66.5,NULL,80.0,600.0),"+
                "(400000,'csv:shunt','shunt','csv',12.7,-2.2,-27.94,71.0,NULL,NULL)");
        db.createStatement().execute("ALTER TABLE readings ADD COLUMN period INTEGER NOT NULL DEFAULT 0");
    }
    @After public void close() throws Exception { db.close(); }
    private List<Double> values(String metric,String source) throws Exception {
        HistoryQuery query=HistoryQuery.build(1,500000,metric,source);
        try(PreparedStatement statement=db.prepareStatement(query.sql)) {
            for(int i=0;i<query.args.length;i++)statement.setString(i+1,query.args[i]);
            List<Double> values=new ArrayList<>();
            try(ResultSet result=statement.executeQuery()){while(result.next())values.add(result.getDouble("v"));}
            return values;
        }
    }
    @Test public void recordingsWinAndImportedValuesFillActualGaps() throws Exception {
        List<Double> v=values("voltage","all");assertEquals(3,v.size());
        assertTrue(v.contains(13.2));assertTrue(v.contains(12.7));assertFalse(v.contains(12.8));
    }
    @Test public void missingRecordedSocDoesNotSuppressImportedSocOrOtherDevice() throws Exception {
        assertEquals(Arrays.asList(72.0,71.0),values("soc","all"));
        List<Double> power=values("power","all");assertTrue(power.contains(80.0));assertTrue(power.contains(-26.4));assertFalse(power.contains(-38.4));
    }
    @Test public void explicitImportSourceKeepsOriginalOverlappingValues() throws Exception {
        List<Double> imports=values("voltage","csv");assertEquals(3,imports.size());assertTrue(imports.contains(12.8));
        assertEquals(Collections.singletonList(13.2),values("voltage","live"));
    }
    @Test public void aFutureDeviceArchiveHasPriorityOverCsvButRecordingsStillWin() throws Exception {
        db.createStatement().execute("INSERT INTO readings(ts,device,kind,origin,voltage,current,power,soc,solar,yieldWh) VALUES (100000,'shunt','shunt','archive',13.0,-2.1,-27.3,NULL,NULL,NULL),"+
                "(400000,'shunt','shunt','archive',12.9,-2.3,-29.67,NULL,NULL,NULL)");
        List<Double> merged=values("voltage","all");
        assertTrue(merged.contains(13.2));assertTrue(merged.contains(12.9));
        assertFalse(merged.contains(13.0));assertFalse(merged.contains(12.7));
        assertEquals(Arrays.asList(13.0,12.9),values("batteryVoltage","archive"));
    }
    @Test public void tileStatisticsUseExactSamplesAndTheActualLastValue() throws Exception {
        HistoryQuery q=HistoryQuery.summary(1,500000,"batteryCurrent","all");
        try(PreparedStatement s=db.prepareStatement(q.sql)) {
            for(int i=0;i<q.args.length;i++)s.setString(i+1,q.args[i]);
            try(ResultSet r=s.executeQuery()){assertTrue(r.next());assertEquals(-2.1,r.getDouble(1),1e-8);assertEquals(-2.2,r.getDouble(2),1e-8);assertEquals(-2,r.getDouble(3),1e-8);assertEquals(2,r.getLong(4));}
        }
        q=HistoryQuery.latest(1,500000,"batteryCurrent","all");
        try(PreparedStatement s=db.prepareStatement(q.sql)) {
            for(int i=0;i<q.args.length;i++)s.setString(i+1,q.args[i]);
            try(ResultSet r=s.executeQuery()){assertTrue(r.next());assertEquals(-2.2,r.getDouble(1),1e-8);assertEquals(400000,r.getLong(2));}
        }
    }
    @Test public void calculatedTileValuesRemainSeparatedByDeviceType() throws Exception {
        db.createStatement().execute("ALTER TABLE readings ADD COLUMN consumed REAL");
        db.createStatement().execute("UPDATE readings SET soc=50,consumed=-23.6 WHERE origin='live'");
        assertTrue(values("storedEnergy","all").contains(6.6));
        assertEquals(Collections.singletonList(23.6),values("consumed","all"));
        assertEquals(Collections.singletonList(80.0),values("solar","all"));
    }
    @Test public void finerArchivesWinWithoutSuppressingAnotherDevice() throws Exception {
        db.createStatement().execute("INSERT INTO readings(ts,device,kind,origin,voltage,period) VALUES (200000,'A','shunt','archive',12.5,1800),(200100,'A','shunt','archive',12.8,30),(200000,'B','shunt','archive',13.5,1800)");
        List<Double> merged=values("batteryVoltage","all");assertTrue(merged.contains(12.8));assertFalse(merged.contains(12.5));assertTrue(merged.contains(13.5));
        assertTrue(values("batteryVoltage","archive").contains(12.65));
    }
}
