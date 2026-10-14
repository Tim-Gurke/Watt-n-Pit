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
}
