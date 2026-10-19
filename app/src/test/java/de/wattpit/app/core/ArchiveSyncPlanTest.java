package de.wattpit.app.core;

import java.sql.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveSyncPlanTest {
    private static final long EPOCH = 1700000000000L;
    private ArchiveCodec.Clock clock() {
        ArchiveCodec.Clock c = new ArchiveCodec.Clock();
        byte[] a = new byte[8];System.arraycopy(ArchiveCodec.le(0,4),0,a,0,4);
        System.arraycopy(ArchiveCodec.le(EPOCH/1000,4),0,a,4,4);c.anchors(a);return c;
    }
    private long t(long deviceTime) { return EPOCH + deviceTime*1000; }
    private ArchiveSyncPlan.Availability local(int... periods) {
        List<Integer> p = new ArrayList<>();for(int period:periods)p.add(period);
        return new ArchiveSyncPlan.Availability(p,t(20000));
    }
    private ArchiveSyncPlan.Request next(ArchiveSyncPlan.Availability local,long cursor,int remaining,long oldest,int batch) {
        return ArchiveSyncPlan.next(cursor,remaining,oldest,30,batch,clock(),local);
    }
    @Test public void firstImportIsBoundedByTheDeviceBatch() {
        ArchiveSyncPlan.Request r=next(local(30),1000,8,790,3);
        assertEquals(1000,r.cursor);assertEquals(0,r.skipped);assertEquals(3,r.count);
    }
    @Test public void liveSamplesAreSkippedBeforeAnyRequest() {
        ArchiveSyncPlan.Availability a=local(30);
        for(int i=0;i<8;i++)a.reading(t(1000-i*30)+500,"live",0);
        ArchiveSyncPlan.Request r=next(a,1000,8,790,3);
        assertEquals(8,r.skipped);assertEquals(0,r.count);
    }
    @Test public void olderHolesAreFoundEvenWhenBothEndsAlreadyExist() {
        ArchiveSyncPlan.Availability a=local(30);
        for(long time:new long[]{1000,970,910,880,790})a.reading(t(time),"live",0);
        ArchiveSyncPlan.Request r=next(a,1000,8,790,8);
        assertEquals(2,r.skipped);assertEquals(940,r.cursor);assertEquals(1,r.count);
        r=next(a,910,5,790,8);
        assertEquals(2,r.skipped);assertEquals(850,r.cursor);assertEquals(2,r.count);
    }
    @Test public void partiallyKnownOldBatchesDoNotDownloadKnownValuesAgain() {
        ArchiveSyncPlan.Availability a=local(30);
        a.imported(30,t(790),t(880));a.imported(30,t(970),t(1000));
        ArchiveSyncPlan.Request r=next(a,1000,8,790,8);
        assertEquals(2,r.skipped);assertEquals(940,r.cursor);assertEquals(2,r.count);
    }
    @Test public void recentArchiveTailIsRefreshedButLiveDataNeedNoOverlap() {
        ArchiveSyncPlan.Availability a=new ArchiveSyncPlan.Availability(Arrays.asList(30),t(1000));
        a.imported(30,t(790),t(1000));
        assertEquals(3,next(a,1000,8,790,8).count);
        a.reading(t(1000),"live",0);
        ArchiveSyncPlan.Request r=next(a,1000,8,790,8);
        assertEquals(1,r.skipped);assertEquals(2,r.count);
    }
    @Test public void restartUsesStoredSamplesEvenBeforeCoverageWasCommitted() {
        ArchiveSyncPlan.Availability a=local(30);
        a.reading(t(1000),"archive",30);a.reading(t(970),"archive",30);
        ArchiveSyncPlan.Request r=next(a,1000,8,790,8);
        assertEquals(2,r.skipped);assertEquals(6,r.count);
    }
    @Test public void finerDataCoverCoarserSamplesButNotTheReverse() {
        ArchiveSyncPlan.Availability a=local(30,1800);
        a.reading(t(1000),"archive",1800);
        assertFalse(a.known(t(1000),30));assertTrue(a.known(t(1000),1800));
        a.reading(t(970),"archive",30);
        assertTrue(a.known(t(970),30));assertTrue(a.known(t(970),1800));
    }
    @Test public void sparseLiveDataDoNotCoverAnEntireGap() {
        ArchiveSyncPlan.Availability a=local(30);
        a.reading(t(1000),"live",0);a.reading(t(790),"live",0);
        assertFalse(a.known(t(940),30));
        assertEquals(6,next(a,1000,8,790,8).count);
    }
    @Test public void sameValueAtAnotherTimeDoesNotFillAHole() {
        ArchiveSyncPlan.Availability a=local(30);a.reading(t(1000),"live",0);
        assertFalse(a.known(t(970),30));assertTrue(a.known(t(1000),30));
    }
    @Test public void unknownClockTimesCannotBeMarkedKnown() {
        ArchiveSyncPlan.Availability a=local(30);a.imported(30,0,t(2000));
        ArchiveSyncPlan.Request r=ArchiveSyncPlan.next(1000,8,790,30,8,new ArchiveCodec.Clock(),a);
        assertEquals(0,r.skipped);assertEquals(8,r.count);
    }
    @Test public void knownIntervalsMergeInEitherOrderWithoutBridgingHoles() {
        ArchiveSyncPlan.Availability a=local(30);
        a.imported(30,t(970),t(1000));a.imported(30,t(790),t(850));a.imported(30,t(880),t(910));
        assertTrue(a.known(t(880),30));assertFalse(a.known(t(940),30));
        a.imported(30,t(850),t(970));
        assertEquals(8,next(a,1000,8,790,8).skipped);
    }
    private ArchiveCodec.Block block(long time,int period,int count,boolean missing) {
        byte[] b=new byte[8+count];b[0]=0;System.arraycopy(ArchiveCodec.le(time,4),0,b,1,4);
        b[5]=(byte)count;System.arraycopy(ArchiveCodec.le(period,2),0,b,6,2);
        Arrays.fill(b,8,b.length,(byte)(missing?255:80));
        return new ArchiveCodec.Block(b,ArchiveCodec.field(0xec87,-1),0,count);
    }
    @Test public void completedMissingSensorValuesAreRememberedWithoutBecomingZero() {
        ArchiveCodec.Block b=block(1000,30,8,true);assertNull(b.values[0]);
        ArchiveSyncPlan.Availability a=local(30);
        for(long[] w:ArchiveSyncPlan.completed(b,clock(),t(20000)))a.imported(30,w[0],w[1]);
        assertEquals(8,next(a,1000,8,790,8).skipped);
    }
    @Test public void completedWindowsDoNotBridgeARebootTimeJump() {
        ArchiveCodec.Clock c=clock();byte[] a=new byte[8];
        System.arraycopy(ArchiveCodec.le(1100,4),0,a,0,4);System.arraycopy(ArchiveCodec.le(EPOCH/1000+10000,4),0,a,4,4);
        c.anchors(a);c.reboots(ArchiveCodec.le(1000,4));
        List<long[]> windows=ArchiveSyncPlan.completed(block(1100,100,3,false),c,t(20000));
        assertEquals(2,windows.size());assertEquals(t(900),windows.get(1)[0]);
        assertEquals(t(10000),windows.get(0)[1]);
    }
    @Test public void emptyDeviceResponseCreatesNoCoverage() {
        assertTrue(ArchiveSyncPlan.completed(block(1000,0,0,false),clock(),t(20000)).isEmpty());
    }
    private Connection database() throws Exception {
        Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        db.createStatement().execute("CREATE TABLE readings(ts INTEGER,device TEXT,kind TEXT,origin TEXT,period INTEGER,voltage REAL,current REAL)");return db;
    }
    private ArchiveSyncPlan.Availability load(Connection db,String device,String kind,String field) throws Exception {
        ArchiveSyncPlan.Availability a=local(30);
        try(PreparedStatement q=db.prepareStatement(ArchiveSyncPlan.readingsSql(field))) {
            q.setString(1,device);q.setString(2,kind);q.setLong(3,0);q.setLong(4,t(20000));
            try(ResultSet r=q.executeQuery()){while(r.next())a.reading(r.getLong(1),r.getString(2),r.getInt(3));}
        }return a;
    }
    @Test public void databaseLookupIsPerDeviceAndFieldAndIncludesRealZero() throws Exception {
        try(Connection db=database()) {
            db.createStatement().execute("INSERT INTO readings VALUES("+t(1000)+",'A','shunt','live',0,0,NULL)");
            assertTrue(load(db,"A","shunt","voltage").known(t(1000),30));
            assertFalse(load(db,"A","shunt","current").known(t(1000),30));
            assertFalse(load(db,"B","shunt","voltage").known(t(1000),30));
            assertFalse(load(db,"A","solar","voltage").known(t(1000),30));
        }
    }
    @Test public void secondInstallationHasItsOwnMissingData() throws Exception {
        try(Connection first=database();Connection second=database()) {
            first.createStatement().execute("INSERT INTO readings VALUES("+t(1000)+",'A','shunt','archive',30,13,1)");
            assertTrue(load(first,"A","shunt","current").known(t(1000),30));
            assertFalse(load(second,"A","shunt","current").known(t(1000),30));
        }
    }
    @Test public void csvWithoutAnUnambiguousDeviceCannotSkipAnotherDevicesRead() throws Exception {
        try(Connection db=database()) {
            db.createStatement().execute("INSERT INTO readings VALUES("+t(1000)+",'csv:shunt','shunt','csv',30,13,1)");
            assertFalse(load(db,"A","shunt","current").known(t(1000),30));
        }
    }
    @Test public void randomizedSparseLiveWindowsRequestExactlyTheMissingSamples() {
        ArchiveSyncPlan.Availability a=local(30);Set<Long> missing=new HashSet<>(),requested=new HashSet<>();
        Random random=new Random(131);
        for(int i=0;i<200;i++) {
            long time=10000-i*30;
            if(random.nextBoolean())a.reading(t(time),"live",0);else missing.add(time);
        }
        long cursor=10000;int remaining=200;
        while(remaining>0) {
            ArchiveSyncPlan.Request r=next(a,cursor,remaining,4030,7);remaining-=r.skipped;
            if(r.count==0)break;
            assertTrue(r.count<=7);
            for(int i=0;i<r.count;i++)assertTrue(requested.add(r.cursor-i*30L));
            remaining-=r.count;cursor=r.cursor-r.count*30L;
        }
        assertEquals(missing,requested);
    }
    @Test public void boundsIncludeSeparatedClockEpochs() {
        long[] bounds=ArchiveSyncPlan.bounds(Arrays.asList(new ArchiveCodec.Level(8,30,1000)),clock());
        assertEquals(t(790)-45000,bounds[0]);assertEquals(t(1000)+45000,bounds[1]);
    }
    @Test(expected=IllegalArgumentException.class) public void sqlOnlyAcceptsKnownMeasurements() {
        ArchiveSyncPlan.readingsSql("voltage OR 1=1");
    }
}
