package de.wattpit.app.core;
import java.sql.*;import org.junit.*;import static org.junit.Assert.*;
public class ArchiveMergeTest {
 private Connection db;
 @Before public void setup()throws Exception{db=DriverManager.getConnection("jdbc:sqlite::memory:");db.createStatement().execute(HistorySchema.CREATE);}
 @After public void close()throws Exception{db.close();}
 private void row(String origin,long t,int period,Double v,Double current)throws Exception{try(PreparedStatement s=db.prepareStatement("INSERT INTO readings(ts,device,kind,origin,period,voltage,current) VALUES(?,'A','shunt',?,?,?,?)")){s.setLong(1,t);s.setString(2,origin);s.setInt(3,period);s.setObject(4,v);s.setObject(5,current);s.execute();}}
 private boolean exists(String field,String device,String kind,long t,int period,double value)throws Exception{ArchiveMerge q=ArchiveMerge.existing(field,device,kind,t,period,value);try(PreparedStatement s=db.prepareStatement(q.sql)){for(int i=0;i<q.args.length;i++)s.setString(i+1,q.args[i]);try(ResultSet r=s.executeQuery()){return r.next();}}}
 @Test public void requestedBoundaryValuesAreNotThrownAwayBecauseOfNearbyLiveOrCsv()throws Exception{
  row("live",100000,10,13.0,null);row("csv",200000,30,12.8,null);
  assertFalse(exists("voltage","A","shunt",101000,30,12.9));assertFalse(exists("current","A","shunt",101000,30,1));assertFalse(exists("voltage","A","shunt",200000,30,12.8));
 }
 @Test public void independentArchiveResolutionsAreKeptAtTheSameInstant()throws Exception{
  row("archive",100000,30,13.0,null);assertFalse(exists("voltage","A","shunt",100000,1800,12.9));row("archive",100000,1800,12.9,null);
  try(ResultSet r=db.createStatement().executeQuery("SELECT COUNT(*) FROM readings")){assertTrue(r.next());assertEquals(2,r.getInt(1));}
 }
 @Test public void onlyAnIdenticalDeviceFieldAndResolutionIsAnAlreadyStoredResponse()throws Exception{
  row("archive",100000,30,13.0,null);assertTrue(exists("voltage","A","shunt",100000,30,13));assertFalse(exists("voltage","B","shunt",100000,30,13));assertFalse(exists("voltage","A","solar",100000,30,13));assertFalse(exists("current","A","shunt",100000,30,13));assertFalse(exists("voltage","A","shunt",100000,30,12.9));
 }
}
