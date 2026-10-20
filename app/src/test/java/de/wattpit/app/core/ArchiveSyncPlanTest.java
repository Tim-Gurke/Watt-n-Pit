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
    @Test public void unknownClockTimesAreDeferredBeforeTransferWithoutBeingMarkedKnown() {
        ArchiveSyncPlan.Availability a=local(30);a.imported(30,0,t(2000));
        ArchiveSyncPlan.Request r=ArchiveSyncPlan.next(1000,8,790,30,8,new ArchiveCodec.Clock(),a);
        assertEquals(8,r.skipped);assertEquals(8,r.deferred);assertEquals(0,r.count);assertFalse(a.known(null,30));
    }
    @Test public void deferredWindowsCanBeRequestedAsSoonAsAnAnchorExists() {
        ArchiveSyncPlan.Availability a=local(30);ArchiveCodec.Clock clock=new ArchiveCodec.Clock();
        assertEquals(8,ArchiveSyncPlan.next(1000,8,790,30,8,clock,a).deferred);
        clock.anchors(clock().anchorBytes());
        ArchiveSyncPlan.Request r=ArchiveSyncPlan.next(1000,8,790,30,8,clock,a);
        assertEquals(0,r.deferred);assertEquals(0,r.skipped);assertEquals(8,r.count);
    }
    @Test public void anUnmappedBootEpochDoesNotPreventReadingTheOtherEpoch() {
        ArchiveSyncPlan.Availability a=local(100);ArchiveCodec.Clock c=clock();c.reboots(ArchiveCodec.le(1000,4));
        ArchiveSyncPlan.Request r=ArchiveSyncPlan.next(1100,5,700,100,8,c,a);
        assertEquals(2,r.deferred);assertEquals(900,r.cursor);assertEquals(3,r.count);
    }
    @Test public void futureClockErrorsCannotTriggerRepeatedUnusableTransfers() {
        ArchiveSyncPlan.Availability a=new ArchiveSyncPlan.Availability(Arrays.asList(30),t(0));
        ArchiveSyncPlan.Request r=next(a,1000,8,790,8);
        assertEquals(8,r.deferred);assertEquals(0,r.count);
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
        assertEquals(t(790)-90000,bounds[0]);assertEquals(t(1000)+90000,bounds[1]);
    }
    @Test public void tenSecondRecordingAlreadyCoversAOneSecondDeviceArchive(){
        ArchiveSyncPlan.Availability a=local(1);
        for(long time=1000;time<=4600;time+=10)a.reading(t(time),"live",10);
        ArchiveSyncPlan.Plan plan=ArchiveSyncPlan.plan(new ArchiveCodec.Level(3601,1,4600),clock(),a);
        assertEquals(3601,plan.known);assertEquals(0,plan.needed);assertTrue(plan.windows.isEmpty());
    }
    @Test public void legacyRecordingsUseThePreviousTenSecondDefault(){
        ArchiveSyncPlan.Availability a=local(1);
        for(long time=1000;time<=1060;time+=10)a.reading(t(time),"live",0);
        assertEquals(0,ArchiveSyncPlan.plan(new ArchiveCodec.Level(61,1,1060),clock(),a).needed);
    }
    @Test public void theSelectedOneMinuteCadenceDoesNotCreateArtificialSecondGaps(){
        ArchiveSyncPlan.Availability a=local(1);
        a.reading(t(1000),"live",60);a.reading(t(1061),"live",60);
        assertEquals(0,ArchiveSyncPlan.plan(new ArchiveCodec.Level(62,1,1061),clock(),a).needed);
    }
    @Test public void aMissingLiveSampleLeavesItsActualReceptionGapInThePlan(){
        ArchiveSyncPlan.Availability a=local(1);
        for(long time:new long[]{1000,1010,1030,1040})a.reading(t(time),"live",10);
        ArchiveSyncPlan.Plan plan=ArchiveSyncPlan.plan(new ArchiveCodec.Level(41,1,1040),clock(),a);
        assertEquals(19,plan.needed);assertEquals(22,plan.known);assertEquals(1,plan.windows.size());
        assertEquals(1029,plan.windows.get(0).cursor);assertEquals(19,plan.windows.get(0).count);
    }
    @Test public void anArchiveOrCsvPointCannotJoinTwoLiveSpans(){
        ArchiveSyncPlan.Availability a=local(1);
        a.reading(t(1000),"live",10);a.reading(t(1010),"csv",1);a.reading(t(1020),"live",10);
        assertFalse(a.known(t(1005),1));assertFalse(a.known(t(1015),1));
    }
    @Test public void aRecordingCadenceChangeCannotCoverALongReceptionGap(){
        ArchiveSyncPlan.Availability a=local(1);
        a.reading(t(1000),"live",10);a.reading(t(1060),"live",60);
        assertFalse(a.known(t(1030),1));
    }
    @Test public void threeWeeksWithoutOpeningTheUiNeedNoTrendTransferIfRecordingContinued(){
        long end=21L*86400;
        ArchiveSyncPlan.Availability a=new ArchiveSyncPlan.Availability(Arrays.asList(1,30,900),t(end+100000));
        for(long time=0;time<=end;time+=10)a.reading(t(time),"live",0);
        for(ArchiveCodec.Level level:Arrays.asList(new ArchiveCodec.Level(120,1,end),new ArchiveCodec.Level(10000,30,end),new ArchiveCodec.Level(2017,900,end))){
            ArchiveSyncPlan.Plan plan=ArchiveSyncPlan.plan(level,clock(),a);
            assertEquals(0,plan.needed);assertEquals(level.capacity,plan.known);assertTrue(plan.windows.isEmpty());
        }
    }
    @Test public void threeDaysAndThreeWeeksOfRealOfflineDataArePlannedWithoutATimeCutoff(){
        for(int days:new int[]{3,21}){
            long end=days*86400L;int samples=days*96+1;
            ArchiveSyncPlan.Availability a=new ArchiveSyncPlan.Availability(Arrays.asList(900),t(end+100000));
            ArchiveSyncPlan.Plan plan=ArchiveSyncPlan.plan(new ArchiveCodec.Level(samples,900,end),clock(),a);
            assertEquals(samples,plan.needed);assertEquals(0,plan.known);assertEquals(1,plan.windows.size());
            assertEquals(end,plan.windows.get(0).cursor);assertEquals(samples,plan.windows.get(0).count);
        }
    }
    @Test public void preflightFindsAllSeparateHolesBeforeTheFirstMeasurementRequest(){
        ArchiveSyncPlan.Availability a=local(30);
        for(long time:new long[]{790,800,810,860,870,880,890,900,910,920,930,950,960,970,980,990,1000})a.reading(t(time),"live",10);
        ArchiveSyncPlan.Plan plan=ArchiveSyncPlan.plan(new ArchiveCodec.Level(8,30,1000),clock(),a);
        assertEquals(3,plan.needed);assertEquals(5,plan.known);assertEquals(2,plan.windows.size());
        assertEquals(940,plan.windows.get(0).cursor);assertEquals(1,plan.windows.get(0).count);
        assertEquals(850,plan.windows.get(1).cursor);assertEquals(2,plan.windows.get(1).count);
    }
    @Test public void liveDataArrivingAfterPreflightArePrunedBeforeTheirTransfer(){
        ArchiveSyncPlan.Availability a=local(30);
        ArchiveSyncPlan.Plan plan=ArchiveSyncPlan.plan(new ArchiveCodec.Level(8,30,1000),clock(),a);
        for(long time=970;time<=1000;time+=10)a.reading(t(time),"live",10);
        ArchiveSyncPlan.Window w=plan.windows.get(0);
        ArchiveSyncPlan.Request request=next(a,w.cursor,w.count,790,8);
        assertEquals(8,plan.needed);assertEquals(1,request.skipped);assertEquals(7,request.count);assertEquals(970,request.cursor);
    }
    @Test public void aPartialMatchingReplyCanBeStoredAndTheRemainderStaysMissing(){
        ArchiveSyncPlan.Availability a=local(30);ArchiveSyncPlan.Request request=next(a,1000,8,790,8);
        ArchiveCodec.Block partial=block(1000,30,2,false);
        assertTrue(ArchiveSyncPlan.matches(request,30,partial));
        for(long[] w:ArchiveSyncPlan.completed(partial,clock(),t(20000)))a.imported(30,w[0],w[1]);
        assertEquals(6,ArchiveSyncPlan.plan(new ArchiveCodec.Level(8,30,1000),clock(),a).needed);
    }
    @Test public void changedOrEmptyDeviceRepliesCannotMarkAPlannedRangeComplete(){
        ArchiveSyncPlan.Request request=next(local(30),1000,8,790,8);
        assertFalse(ArchiveSyncPlan.matches(request,30,block(970,30,2,false)));
        assertFalse(ArchiveSyncPlan.matches(request,30,block(1000,60,2,false)));
        assertFalse(ArchiveSyncPlan.matches(request,30,block(1000,0,0,false)));
    }
    @Test public void unknownTimeReferencesAreCountedInPreflightWithoutADataRequest(){
        ArchiveSyncPlan.Plan plan=ArchiveSyncPlan.plan(new ArchiveCodec.Level(8,30,1000),new ArchiveCodec.Clock(),local(30));
        assertEquals(8,plan.deferred);assertEquals(0,plan.needed);assertTrue(plan.windows.isEmpty());
    }
    @Test(expected=IllegalArgumentException.class) public void sqlOnlyAcceptsKnownMeasurements() {
        ArchiveSyncPlan.readingsSql("voltage OR 1=1");
    }
}
