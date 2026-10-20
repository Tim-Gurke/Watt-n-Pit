package de.wattpit.app.core;

import java.sql.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Uses the production queue and protocol, a real SQLite store, and production energy. */
public class ArchiveTransferPlanTest {
    private static final long EPOCH=1700000000000L;
    private ArchiveCodec.Clock clock(){ArchiveCodec.Clock c=new ArchiveCodec.Clock();byte[] b=new byte[8];System.arraycopy(ArchiveCodec.le(EPOCH/1000,4),0,b,4,4);c.anchors(b);return c;}
    private ArchiveCodec.Field field(int ch){return ArchiveCodec.field(ch==0?0xec8a:ch==1?0xed8d:0xed8c,-1);}
    private ArchiveCodec.Block block(int ch,long time,int period,int count){
        ArchiveCodec.Field f=field(ch);byte[] b=new byte[8+f.width*count];b[0]=(byte)ch;System.arraycopy(ArchiveCodec.le(time,4),0,b,1,4);b[5]=(byte)count;System.arraycopy(ArchiveCodec.le(period,2),0,b,6,2);
        for(int i=0;i<count;i++)System.arraycopy(ArchiveCodec.le(ch==0?60:ch==1?1200:-5000,f.width),0,b,8+i*f.width,f.width);
        return new ArchiveCodec.Block(b,f,ch,Math.max(1,count));
    }
    private final class Store implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");final long now;
        Store(long now)throws Exception{this.now=EPOCH+now*1000;db.createStatement().execute(HistorySchema.CREATE);db.createStatement().execute(HistorySchema.COVERAGE);}
        String device(int ch){return ch==0?"solar-A":"shunt-B";}
        String kind(int ch){return ch==0?"solar":"shunt";}
        void put(ArchiveCodec.Block b)throws Exception{
            String name=field(b.channel).name;
            db.setAutoCommit(false);
            try{
                for(int i=0;i<b.count;i++){
                    long t=clock().millis(b.time-(long)i*b.period);Double value=b.values[i];if(value==null)continue;
                    ArchiveMerge merge=ArchiveMerge.existing(name,device(b.channel),kind(b.channel),t,b.period,value);
                    try(PreparedStatement q=db.prepareStatement(merge.sql)){for(int j=0;j<merge.args.length;j++)q.setString(j+1,merge.args[j]);try(ResultSet r=q.executeQuery()){if(r.next())continue;}}
                    try(PreparedStatement q=db.prepareStatement("INSERT INTO readings(ts,device,kind,origin,period,"+name+") VALUES(?,?,?,'archive',?,?) ON CONFLICT(ts,device,origin,period) DO UPDATE SET "+name+"=excluded."+name)){
                        q.setLong(1,t);q.setString(2,device(b.channel));q.setString(3,kind(b.channel));q.setInt(4,b.period);q.setDouble(5,value);q.executeUpdate();
                    }
                }
                for(long[] r:ArchiveSyncPlan.completed(b,clock(),now))try(PreparedStatement q=db.prepareStatement("INSERT INTO archive_coverage(device,channel,period,start,end) VALUES(?,?,?,?,?)")){
                    q.setString(1,device(b.channel));q.setInt(2,b.channel);q.setInt(3,b.period);q.setLong(4,r[0]);q.setLong(5,r[1]);q.executeUpdate();
                }
                db.commit();
            }catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
        }
        ArchiveSyncPlan.Availability load(int ch,List<ArchiveCodec.Level> levels,boolean refresh)throws Exception{
            List<Integer> p=new ArrayList<>();for(ArchiveCodec.Level level:levels)p.add(level.period);
            ArchiveSyncPlan.Availability a=new ArchiveSyncPlan.Availability(p,now,refresh);
            try(PreparedStatement q=db.prepareStatement(ArchiveSyncPlan.readingsSql(field(ch).name))){q.setString(1,device(ch));q.setString(2,kind(ch));q.setLong(3,0);q.setLong(4,Long.MAX_VALUE);try(ResultSet r=q.executeQuery()){while(r.next())a.reading(r.getLong(1),r.getString(2),r.getInt(3));}}
            try(PreparedStatement q=db.prepareStatement("SELECT period,start,end FROM archive_coverage WHERE device=? AND channel=?")){q.setString(1,device(ch));q.setInt(2,ch);try(ResultSet r=q.executeQuery()){while(r.next())a.imported(r.getInt(1),r.getLong(2),r.getLong(3));}}
            return a;
        }
        List<EnergyIntegral.Sample> samples(int ch)throws Exception{
            List<EnergyIntegral.Sample> out=new ArrayList<>();try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT ts,"+field(ch).name+",period FROM readings WHERE device='"+device(ch)+"' AND "+field(ch).name+" IS NOT NULL ORDER BY ts")){while(r.next())out.add(new EnergyIntegral.Sample(r.getLong(1),r.getDouble(2),r.getInt(3),device(ch),"archive"));}return out;
        }
        public void close()throws Exception{db.close();}
    }
    private ArchiveTransferPlan plan(Store s,Map<Integer,List<ArchiveCodec.Level>> levels)throws Exception{
        Map<Integer,ArchiveSyncPlan.Availability> local=new HashMap<>();Map<Integer,Integer>batches=new HashMap<>();
        ArchiveTransferPlan p=new ArchiveTransferPlan(clock(),local,batches);
        for(int ch:levels.keySet()){local.put(ch,s.load(ch,levels.get(ch),true));batches.put(ch,ch==2?14:28);}
        for(int ch:levels.keySet())for(ArchiveCodec.Level level:levels.get(ch))p.add(ch,level);return p;
    }
    private void finish(Store s,ArchiveTransferPlan p)throws Exception{
        ArchiveTransferPlan.Read r;int packets=0;
        while((r=p.next(ch->{}))!=null){assertTrue(++packets<10000);ArchiveCodec.Block b=block(r.channel,r.request.cursor,r.period,r.request.count);s.put(b);assertTrue(p.accept(b));}
        assertEquals(0,p.remaining());
    }
    @Test public void allThreeChannelsAndAllFourRealisticTiersCoverThreeWeeksInSQLiteAndEnergy()throws Exception{
        int end=21*86400;List<ArchiveCodec.Level> tier=Arrays.asList(new ArchiveCodec.Level(121,1,end),new ArchiveCodec.Level(241,30,end),new ArchiveCodec.Level(289,300,end),new ArchiveCodec.Level(1441,1800,end));
        Map<Integer,List<ArchiveCodec.Level>> levels=new LinkedHashMap<>();for(int ch=0;ch<3;ch++)levels.put(ch,tier);
        try(Store s=new Store(end+10000)){
            finish(s,plan(s,levels));
            for(int ch=0;ch<3;ch++)for(ArchiveCodec.Level l:tier)assertEquals(0,ArchiveSyncPlan.plan(l,clock(),s.load(ch,tier,false)).needed);
            assertEquals(0,plan(s,levels).remaining());
            List<SampleTimeline.Interval> battery=EnergyIntegral.powerIntervals(Collections.emptyList(),s.samples(1),s.samples(2),EPOCH,EPOCH+end*1000L);
            EnergyIntegral.Flow flow=EnergyIntegral.flowIntervals(SampleTimeline.intervals(s.samples(0),EPOCH,EPOCH+end*1000L,false),battery,EPOCH,EPOCH+end*1000L,EPOCH,EPOCH+end*1000L);
            assertEquals(end*1000L,flow.solar.coveredMillis);assertEquals(end*1000L,flow.balance.coveredMillis);assertEquals(end*1000L,flow.consumption.coveredMillis);
            assertEquals(end/60.0,flow.solar.wh,1e-6);assertEquals(end/30.0,flow.consumption.wh,1e-6);assertEquals(-end/60.0,flow.balance.wh,1e-6);
            assertEquals(1,flow.solar.points.stream().filter(p->p.gap).count());
        }
    }
    @Test public void aChangedResolutionKeepsReturnedDataAndTheOtherChannelsQueued()throws Exception{
        Map<Integer,List<ArchiveCodec.Level>> levels=new LinkedHashMap<>();levels.put(0,Arrays.asList(new ArchiveCodec.Level(11,30,300)));levels.put(1,Arrays.asList(new ArchiveCodec.Level(11,30,300)));
        try(Store s=new Store(10000)){
            ArchiveTransferPlan p=plan(s,levels);ArchiveTransferPlan.Read read=p.next(ch->{});assertEquals(0,read.channel);
            ArchiveCodec.Block actual=block(0,300,300,2);s.put(actual);assertFalse(p.accept(actual));assertTrue(p.remaining()>11);
            List<ArchiveCodec.Level> current=Arrays.asList(new ArchiveCodec.Level(2,300,300));p.refresh(0,current,s.load(0,current,true),28);
            finish(s,p);assertEquals(2,s.samples(0).size());assertEquals(11,s.samples(1).size());
        }
    }
    @Test public void anEmptyReplyDoesNotDiscardTheRestOfItsRange()throws Exception{
        Map<Integer,List<ArchiveCodec.Level>> levels=Collections.singletonMap(0,Arrays.asList(new ArchiveCodec.Level(101,30,3000)));
        try(Store s=new Store(20000)){ArchiveTransferPlan p=plan(s,levels);ArchiveTransferPlan.Read r=p.next(ch->{});assertEquals(101,p.remaining());assertFalse(p.accept(block(0,r.request.cursor,0,0)));assertEquals(101,p.remaining());p.refresh(0,levels.get(0),s.load(0,levels.get(0),true),28);finish(s,p);assertEquals(101,s.samples(0).size());}
    }
    @Test public void partialRepliesConsumeOnlyTheirConfirmedSamples()throws Exception{
        Map<Integer,List<ArchiveCodec.Level>> levels=Collections.singletonMap(0,Arrays.asList(new ArchiveCodec.Level(101,30,3000)));
        try(Store s=new Store(20000)){ArchiveTransferPlan p=plan(s,levels);ArchiveTransferPlan.Read r=p.next(ch->{});ArchiveCodec.Block b=block(0,r.request.cursor,30,3);s.put(b);assertTrue(p.accept(b));assertEquals(98,p.remaining());finish(s,p);assertEquals(101,s.samples(0).size());}
    }
    @Test public void aPersistentWrongReplyFailsWithTheOutstandingWorkInsteadOfReportingCompletion()throws Exception{
        Map<Integer,List<ArchiveCodec.Level>> levels=Collections.singletonMap(0,Arrays.asList(new ArchiveCodec.Level(101,30,3000)));
        try(Store s=new Store(20000)){ArchiveTransferPlan p=plan(s,levels);for(int i=0;i<4;i++){ArchiveTransferPlan.Read r=p.next(ch->{});assertFalse(p.accept(block(0,r.request.cursor,0,0)));try{p.refresh(0,levels.get(0),s.load(0,levels.get(0),true),28);assertTrue(i<3);}catch(IllegalStateException e){assertEquals(3,i);assertTrue(e.getMessage().contains("101"));assertEquals(101,p.remaining());}}}
    }
    @Test public void finalAuditRecognizesFreshSavedValuesWithoutRefetchingTheEntireRecentTail()throws Exception{
        List<ArchiveCodec.Level> levels=Arrays.asList(new ArchiveCodec.Level(121,1,120));
        try(Store s=new Store(120)){ArchiveTransferPlan p=plan(s,Collections.singletonMap(0,levels));finish(s,p);assertTrue(ArchiveSyncPlan.plan(levels.get(0),clock(),s.load(0,levels,true)).needed>0);assertEquals(0,ArchiveSyncPlan.plan(levels.get(0),clock(),s.load(0,levels,false)).needed);}
    }
    @Test public void aDatabaseAuditCanRequeueAnActuallyMissingSavedRange()throws Exception{
        Map<Integer,List<ArchiveCodec.Level>> levels=Collections.singletonMap(0,Arrays.asList(new ArchiveCodec.Level(101,30,3000)));
        try(Store s=new Store(20000)){ArchiveTransferPlan p=plan(s,levels);finish(s,p);s.db.createStatement().execute("DELETE FROM readings WHERE ts="+(EPOCH+1500000));s.db.createStatement().execute("DELETE FROM archive_coverage");ArchiveSyncPlan.Availability a=s.load(0,levels.get(0),false);assertEquals(1,ArchiveSyncPlan.plan(levels.get(0).get(0),clock(),a).needed);p.replace(0,levels.get(0),a,28);assertEquals(1,p.remaining());finish(s,p);assertEquals(101,s.samples(0).size());}
    }
}
