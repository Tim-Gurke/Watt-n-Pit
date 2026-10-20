package de.wattpit.app.core;
import java.sql.*;import org.junit.Test;import static org.junit.Assert.*;
public class HistorySchemaTest {
    private void migrate(Connection db)throws Exception{db.setAutoCommit(false);try{for(String sql:HistorySchema.MIGRATE)db.createStatement().execute(sql);db.commit();}catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}}
    @Test public void upgradingKeepsEveryLiveCsvAndArchiveValueAndAllowsBothResolutions()throws Exception{
        try(Connection db=DriverManager.getConnection("jdbc:sqlite::memory:")){
            db.createStatement().execute(HistorySchema.CREATE.replace("UNIQUE(ts,device,origin,period)","UNIQUE(ts,device,origin)"));
            db.createStatement().execute("INSERT INTO readings(ts,device,kind,origin,period,voltage,current,solar) VALUES(1000,'A','solar','live',10,12,0,0),(1000,'A','solar','archive',30,13,2,26),(2000,'B','shunt','csv',0,14,-3,NULL)");
            db.createStatement().execute(HistorySchema.COVERAGE);db.createStatement().execute("INSERT INTO archive_coverage VALUES('A',1,30,0,10000)");
            db.createStatement().execute("CREATE TABLE untouched(payload TEXT)");db.createStatement().execute("INSERT INTO untouched VALUES('keep')");migrate(db);
            try(ResultSet r=db.createStatement().executeQuery("SELECT COUNT(*),SUM(voltage),SUM(current),SUM(solar) FROM readings")){assertTrue(r.next());assertEquals(3,r.getInt(1));assertEquals(39,r.getDouble(2),0);assertEquals(-1,r.getDouble(3),0);assertEquals(26,r.getDouble(4),0);}
            try(ResultSet r=db.createStatement().executeQuery("SELECT COUNT(*) FROM archive_coverage")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
            db.createStatement().execute("INSERT INTO readings(ts,device,kind,origin,period,solar) VALUES(1000,'A','solar','archive',1,25)");
            try(ResultSet r=db.createStatement().executeQuery("SELECT COUNT(*) FROM readings WHERE ts=1000 AND origin='archive'")){assertTrue(r.next());assertEquals(2,r.getInt(1));}
            try(ResultSet r=db.createStatement().executeQuery("SELECT payload FROM untouched")){assertTrue(r.next());assertEquals("keep",r.getString(1));}
        }
    }
    @Test public void olderUpgradeColumnOrderCannotSwapPeriodAndAuxiliaryValues()throws Exception{
        try(Connection db=DriverManager.getConnection("jdbc:sqlite::memory:")){
            String old=HistorySchema.CREATE.replace("pvVoltage REAL,auxRaw REAL,period INTEGER NOT NULL DEFAULT 0,","").replace("UNIQUE(ts,device,origin,period)","UNIQUE(ts,device,origin)");db.createStatement().execute(old);
            db.createStatement().execute("ALTER TABLE readings ADD COLUMN period INTEGER NOT NULL DEFAULT 0");db.createStatement().execute("ALTER TABLE readings ADD COLUMN pvVoltage REAL");db.createStatement().execute("ALTER TABLE readings ADD COLUMN auxRaw REAL");
            db.createStatement().execute("INSERT INTO readings(ts,device,kind,origin,period,pvVoltage,auxRaw) VALUES(1000,'A','solar','archive',1800,19.5,123)");migrate(db);
            try(ResultSet r=db.createStatement().executeQuery("SELECT period,pvVoltage,auxRaw FROM readings")){assertTrue(r.next());assertEquals(1800,r.getInt(1));assertEquals(19.5,r.getDouble(2),0);assertEquals(123,r.getDouble(3),0);}
        }
    }
    @Test public void identicalResolutionRowsStillCannotBeInsertedTwice()throws Exception{
        try(Connection db=DriverManager.getConnection("jdbc:sqlite::memory:")){
            db.createStatement().execute(HistorySchema.CREATE);String sql="INSERT OR IGNORE INTO readings(ts,device,kind,origin,period) VALUES(1000,'A','solar','archive',30)";
            assertEquals(1,db.createStatement().executeUpdate(sql));assertEquals(0,db.createStatement().executeUpdate(sql));
        }
    }
}
