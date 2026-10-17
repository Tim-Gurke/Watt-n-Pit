package de.wattpit.app.core;
import java.sql.*;import org.junit.*;import static org.junit.Assert.*;
public class ArchiveMergeTest {
 private Connection db;
 @Before public void setup()throws Exception{db=DriverManager.getConnection("jdbc:sqlite::memory:");db.createStatement().execute("CREATE TABLE readings(ts INTEGER,device TEXT,kind TEXT,origin TEXT,period INTEGER,voltage REAL,current REAL,UNIQUE(ts,device,origin))");}
 @After public void close()throws Exception{db.close();}
 private void row(String device,String kind,String origin,long t,int period,Double v,Double current)throws Exception{try(PreparedStatement s=db.prepareStatement("INSERT INTO readings VALUES(?,?,?,?,?,?,?)")){s.setLong(1,t);s.setString(2,device);s.setString(3,kind);s.setString(4,origin);s.setInt(5,period);s.setObject(6,v);s.setObject(7,current);s.execute();}}
 private boolean exists(String field,String device,String kind,long t,int period,double value)throws Exception{ArchiveMerge q=ArchiveMerge.existing(field,device,kind,t,period,value);try(PreparedStatement s=db.prepareStatement(q.sql)){for(int i=0;i<q.args.length;i++)s.setString(i+1,q.args[i]);try(ResultSet r=s.executeQuery()){return r.next();}}}
 @Test public void comparesLiveAndImportedFieldsWithoutTreatingAMissingFieldAsCovered()throws Exception{
  row("A","shunt","live",100000,0,13.0,null);assertTrue(exists("voltage","A","shunt",101000,30,12.9));assertFalse(exists("current","A","shunt",101000,30,1));assertFalse(exists("voltage","B","shunt",101000,30,13));assertFalse(exists("voltage","A","solar",101000,30,13));
  row("csv:shunt","shunt","csv",200000,0,12.8,null);assertTrue(exists("voltage","A","shunt",200000,30,12.8));assertFalse(exists("voltage","A","shunt",200000,30,12.9));
 }
 @Test public void finerArchiveIsKnownButCoarserArchiveMustBeImproved()throws Exception{
  row("A","shunt","archive",100000,30,13.0,null);assertTrue(exists("voltage","A","shunt",100000,1800,12.9));assertFalse(exists("voltage","A","shunt",100000,1,13));assertFalse(exists("voltage","A","shunt",300000,1800,13));
 }
 @Test public void sameNumbersAtDifferentTimesAreDifferentReadings()throws Exception{row("A","shunt","live",100000,0,13.0,null);assertFalse(exists("voltage","A","shunt",200000,30,13));}
}
