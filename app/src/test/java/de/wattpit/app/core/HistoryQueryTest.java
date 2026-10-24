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
    @Test public void energyReceivesOriginalPowerSamplesRatherThanChartBucketAverages() throws Exception {
        db.createStatement().execute("INSERT INTO readings(ts,device,kind,origin,power,period) VALUES (100001,'shunt','shunt','archive',-100,30),(100002,'shunt','shunt','archive',100,30)");
        HistoryQuery q=HistoryQuery.rawPower(1,500000,"batteryPower","all");
        List<Double> power=new ArrayList<>();
        try(PreparedStatement s=db.prepareStatement(q.sql)){
            for(int i=0;i<q.args.length;i++)s.setString(i+1,q.args[i]);
            try(ResultSet r=s.executeQuery()){while(r.next())power.add(r.getDouble(2));}
        }
        assertTrue(power.contains(-100.0));assertTrue(power.contains(100.0));assertTrue(power.contains(-26.4));assertFalse(power.contains(80.0));
    }
    @Test public void aNecessaryArchiveEdgeIsNotHiddenByANearbyLiveReading() throws Exception {
        db.createStatement().execute("INSERT INTO readings(ts,device,kind,origin,solar,period) VALUES (200000,'S','solar','live',60,10),(220000,'S','solar','archive',60,30)");
        HistoryQuery q=HistoryQuery.raw(190000,230000,"solar","all");int count=0;
        try(PreparedStatement statement=db.prepareStatement(q.sql)){for(int i=0;i<q.args.length;i++)statement.setString(i+1,q.args[i]);try(ResultSet r=statement.executeQuery()){while(r.next())count++;}}
        assertEquals(2,count);
    }

    @Test public void complementaryShuntFieldsDiscoverAPlotEvenWithoutAStoredPowerColumn() throws Exception {
        db.createStatement().execute("INSERT INTO readings(ts,device,kind,origin,voltage,current,period) VALUES (200000,'complementary','shunt','archive',12,NULL,10),(230000,'complementary','shunt','archive',NULL,-5,30)");
        HistoryQuery q=HistoryQuery.powerDevices(190000,240000,"all");boolean found=false;
        try(PreparedStatement statement=db.prepareStatement(q.sql)){for(int i=0;i<q.args.length;i++)statement.setString(i+1,q.args[i]);try(ResultSet r=statement.executeQuery()){while(r.next())if(r.getString(1).equals("complementary"))found=true;}}
        assertTrue(found);
    }

    private List<String> sunset(long at,long now,String device)throws Exception{
        HistoryQuery q=HistoryQuery.sunsetSoc(at,now,device);List<String> result=new ArrayList<>();
        try(PreparedStatement s=db.prepareStatement(q.sql)){
            for(int i=0;i<q.args.length;i++)s.setString(i+1,q.args[i]);
            try(ResultSet r=s.executeQuery()){while(r.next())result.add(r.getLong(1)+":"+r.getDouble(2)+":"+r.getString(3));}
        }return result;
    }
    @Test public void sunsetUsesTheNearestOriginalSampleAndNeverAveragesTheSoc()throws Exception{
        db.createStatement().execute("INSERT INTO readings(ts,device,kind,origin,soc,period) VALUES "+
                "(9900000,'A','shunt','archive',72.5,300),(10010000,'A','shunt','live',71.2,0),(10100000,'A','shunt','live',70,0)");
        assertEquals(Collections.singletonList("10010000:71.2:live"),sunset(10000000,11000000,"A"));
    }
    @Test public void sunsetDoesNotUseOtherDevicesInvalidValuesOrDistantReadings()throws Exception{
        db.createStatement().execute("INSERT INTO readings(ts,device,kind,origin,soc,period) VALUES "+
                "(10000000,'B','shunt','live',90,0),(10000000,'A','solar','live',80,0),"+
                "(10000000,'A','shunt','live',101,0),(10000001,'A','shunt','archive',-1,30),"+
                "(10000002,'A','shunt','archive',NULL,30),(10900001,'A','shunt','live',77,0)");
        assertTrue(sunset(10000000,12000000,"A").isEmpty());
    }
    @Test public void sunsetCanUseAnOriginalArchiveZeroAndPrefersLiveAtTheSameTime()throws Exception{
        db.createStatement().execute("INSERT INTO readings(ts,device,kind,origin,soc,period) VALUES "+
                "(10000000,'A','shunt','archive',0,1800)");
        assertEquals(Collections.singletonList("10000000:0.0:archive"),sunset(10000000,11000000,"A"));
        db.createStatement().execute("INSERT INTO readings(ts,device,kind,origin,soc,period) VALUES (10000000,'A','shunt','live',.1,0)");
        assertEquals(Collections.singletonList("10000000:0.1:live"),sunset(10000000,11000000,"A"));
    }
    @Test public void sunsetExcludesFutureSamplesEvenIfCloserToTheTarget()throws Exception{
        db.createStatement().execute("INSERT INTO readings(ts,device,kind,origin,soc,period) VALUES "+
                "(9900000,'A','shunt','archive',60,300),(10010000,'A','shunt','live',90,0)");
        assertEquals(Collections.singletonList("9900000:60.0:archive"),sunset(10000000,10000000,"A"));
    }
}
