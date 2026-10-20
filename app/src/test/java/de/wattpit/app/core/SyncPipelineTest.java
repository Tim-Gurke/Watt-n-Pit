package de.wattpit.app.core;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Archive preflight -> real protocol blocks -> retained raw sources -> energy and plot. */
public class SyncPipelineTest {
    private static final long EPOCH=1700000000000L;
    private static final class Run {
        final List<EnergyIntegral.Sample> stored=new ArrayList<>();final Set<String> received=new HashSet<>();
        final ArchiveCodec.Level level;final ArchiveCodec.Clock clock=new ArchiveCodec.Clock();int transferred;
        Run(int count,int period,long newest){level=new ArchiveCodec.Level(count,period,newest);byte[] a=new byte[8];System.arraycopy(ArchiveCodec.le(0,4),0,a,0,4);System.arraycopy(ArchiveCodec.le(EPOCH/1000,4),0,a,4,4);clock.anchors(a);}
        void live(long second,int cadence){stored.add(new EnergyIntegral.Sample(EPOCH+second*1000,60,cadence,"solar-A","live"));}
        ArchiveSyncPlan.Availability local(){
            ArchiveSyncPlan.Availability a=new ArchiveSyncPlan.Availability(Arrays.asList(level.period),EPOCH+(level.newest+10000)*1000);
            stored.stream().sorted(Comparator.comparingLong(s->s.time)).forEach(s->a.reading(s.time,s.origin,s.period));return a;
        }
        ArchiveSyncPlan.Plan plan(){return ArchiveSyncPlan.plan(level,clock,local());}
        void transfer(int packetLimit){
            ArchiveSyncPlan.Availability a=local();ArchiveSyncPlan.Plan plan=ArchiveSyncPlan.plan(level,clock,a);int packets=0;
            for(ArchiveSyncPlan.Window w:plan.windows){long cursor=w.cursor;int remaining=w.count;
                while(remaining>0){
                    ArchiveSyncPlan.Request r=ArchiveSyncPlan.next(cursor,remaining,level.oldest,level.period,7,clock,a);
                    remaining-=r.skipped;if(r.count==0)break;
                    if(packets++>=packetLimit)return;
                    ArchiveCodec.Field f=ArchiveCodec.field(0xec8a,-1);byte[] payload=new byte[8+r.count*f.width];
                    System.arraycopy(ArchiveCodec.le(r.cursor,4),0,payload,1,4);payload[5]=(byte)r.count;System.arraycopy(ArchiveCodec.le(level.period,2),0,payload,6,2);
                    for(int i=0;i<r.count;i++)System.arraycopy(ArchiveCodec.le(60,2),0,payload,8+i*2,2);
                    ArchiveCodec.Block block=new ArchiveCodec.Block(payload,f,0,r.count);assertTrue(ArchiveSyncPlan.matches(r,level.period,block));
                    for(int i=0;i<block.count;i++){
                        long time=block.time-i*(long)block.period;String key=time+":"+block.period;
                        assertTrue("Already transferred: "+key,received.add(key));
                        long utc=clock.millis(time);stored.add(new EnergyIntegral.Sample(utc,block.values[i],block.period,"solar-A","archive"));a.reading(utc,"archive",block.period);transferred++;
                    }
                    for(long[] range:ArchiveSyncPlan.completed(block,clock,EPOCH+(level.newest+10000)*1000))a.imported(block.period,range[0],range[1]);
                    remaining-=block.count;cursor=block.time-block.count*(long)block.period;
                }
            }
        }
        EnergyIntegral.Result result(long end){return EnergyIntegral.calculate(stored,EPOCH,EPOCH+end*1000,false);}
        void full(long end){
            transfer(Integer.MAX_VALUE);assertEquals(0,plan().needed);
            EnergyIntegral.Result r=result(end);assertEquals(end*1000,r.coveredMillis);assertEquals(end/60.0,r.wh,1e-7);
            List<SampleTimeline.PlotPoint> points=SampleTimeline.plot(stored,EPOCH,EPOCH+end*1000);
            assertEquals(1,points.stream().filter(p->p.gap).count());assertEquals(EPOCH,points.get(0).time);assertEquals(EPOCH+end*1000,points.get(points.size()-1).time);
            List<EnergyIntegral.Sample> battery=new ArrayList<>();int cadence=(int)Math.min(900,end);
            for(long time=0;time<=end;time+=cadence)battery.add(new EnergyIntegral.Sample(EPOCH+time*1000,-30,cadence,"shunt-B","archive"));
            EnergyIntegral.Flow flow=EnergyIntegral.flow(stored,battery,EPOCH,EPOCH+end*1000);
            assertEquals(end*1000,flow.solar.coveredMillis);assertEquals(end/60.0,flow.solar.wh,1e-7);assertEquals(end/40.0,flow.consumption.wh,1e-7);assertEquals(-end/120.0,flow.balance.wh,1e-7);
        }
    }
    @Test public void theReportedTwentySecondLiveCaseIsActuallyRepairedInsteadOfSkipped(){
        Run run=new Run(21,30,600);for(int t=0;t<=600;t+=20)run.live(t,10);
        assertTrue(run.plan().needed>0);run.full(600);assertTrue(run.transferred>0);
    }
    @Test public void uninterruptedTenSecondRecordingNeedsNoSecondArchiveDownload(){
        Run run=new Run(121,1,120);for(int t=0;t<=120;t+=10)run.live(t,10);run.full(120);assertEquals(0,run.transferred);
    }
    @Test public void legacyRecordingAndItsEnergyUseTheSameHistoricalGapRule(){
        Run run=new Run(21,30,600);for(int t=0;t<=600;t+=20)run.live(t,0);run.full(600);assertEquals(0,run.transferred);
    }
    @Test public void archiveValuesNearLiveEdgesMustSurviveToConnectTheSources(){
        Run run=new Run(5,30,120);for(int t:new int[]{0,10,20,100,110,120})run.live(t,10);
        run.full(120);assertEquals(3,run.transferred);assertTrue(run.received.contains("30:30"));assertTrue(run.received.contains("90:30"));
    }
    @Test public void cancellationResumesOnlyTheRemainingPlanAndKeepsCoverage(){
        Run run=new Run(121,30,3600);run.transfer(1);assertEquals(7,run.transferred);assertEquals(114,run.plan().needed);run.full(3600);assertEquals(121,run.transferred);
    }
    @Test public void threeDaysAndThreeWeeksOfflineFinishWithoutManualTimeSlices(){
        for(int days:new int[]{3,21}){int end=days*86400;Run run=new Run(days*96+1,900,end);run.full(end);assertEquals(days*96+1,run.transferred);}
    }
    @Test public void threeWeeksOfBackgroundDataAreKeptWithoutAnArchiveTransfer(){
        int end=21*86400;Run run=new Run(2017,900,end);for(int t=0;t<=end;t+=60)run.live(t,60);run.full(end);assertEquals(0,run.transferred);
    }
    @Test public void gapsOlderThanDeviceRetentionStayExplicitlyUnknown(){
        int end=3*86400;Run run=new Run(21,30,end);for(int t=0;t<=60;t+=10){run.live(t,10);run.live(end-t,10);}
        run.transfer(Integer.MAX_VALUE);EnergyIntegral.Result r=run.result(end);assertEquals(660000,r.coveredMillis);assertEquals(11,r.wh,1e-9);
        assertEquals(2,SampleTimeline.plot(run.stored,EPOCH,EPOCH+end*1000).stream().filter(p->p.gap).count());
        for(String key:run.received)assertTrue(Long.parseLong(key.split(":")[0])>=end-600);
    }
    @Test public void randomizedReceiverInterruptionsNeverDisappearFromTheFinalEnergyCoverage(){
        for(int seed=0;seed<40;seed++){Run run=new Run(121,30,3600);Random random=new Random(seed);for(int t=0;t<=3600;t+=10)if(random.nextDouble()<.75)run.live(t,10);run.full(3600);}
    }
    @Test public void aSourceBoundaryUsesObservedEndpointsWithoutAddingEnergyTwice(){
        List<EnergyIntegral.Sample> data=new ArrayList<>();int[] times={0,10,20,30,40,50};
        for(int t:times)data.add(new EnergyIntegral.Sample(EPOCH+t*1000,60,t==20||t==30?30:10,"A",t==20||t==30?"archive":"live"));
        EnergyIntegral.Result result=EnergyIntegral.calculate(data,EPOCH,EPOCH+50000,false);assertEquals(50000,result.coveredMillis);assertEquals(5.0/6,result.wh,1e-9);
        assertEquals(1,SampleTimeline.plot(data,EPOCH,EPOCH+50000).stream().filter(p->p.gap).count());
    }
    @Test public void adjacentArchiveResolutionsConnectWithoutInventingUnobservedEdges(){
        List<EnergyIntegral.Sample> data=Arrays.asList(new EnergyIntegral.Sample(EPOCH,60,30,"A","archive"),new EnergyIntegral.Sample(EPOCH+30000,60,60,"A","archive"));
        EnergyIntegral.Result result=EnergyIntegral.calculate(data,EPOCH,EPOCH+120000,false);assertEquals(30000,result.coveredMillis);assertEquals(.5,result.wh,1e-9);
    }
    @Test public void aSmallRealGapDoesNotBlankTheRestOfItsPlotBucket(){
        List<EnergyIntegral.Sample> data=Arrays.asList(new EnergyIntegral.Sample(EPOCH,60,10,"A","live"),new EnergyIntegral.Sample(EPOCH+10000,60,10,"A","live"),new EnergyIntegral.Sample(EPOCH+30000,60,10,"A","live"),new EnergyIntegral.Sample(EPOCH+40000,60,10,"A","live"));
        long to=EPOCH+86400000;EnergyIntegral.Result energy=EnergyIntegral.calculate(data,EPOCH,to,false);assertEquals(20000,energy.coveredMillis);
        assertEquals(Arrays.asList(EPOCH,EPOCH+10000,EPOCH+30000,EPOCH+40000),energy.points.stream().map(p->p.time).collect(java.util.stream.Collectors.toList()));
        assertFalse(energy.points.get(1).gap);assertTrue(energy.points.get(2).gap);assertFalse(energy.points.get(3).gap);
    }
    @Test public void complementaryShuntChannelsAtDifferentResolutionsProduceTheSameCoveredPower(){
        List<EnergyIntegral.Sample> v=new ArrayList<>(),c=new ArrayList<>();
        for(int t=0;t<=120;t+=10)v.add(new EnergyIntegral.Sample(EPOCH+t*1000,12,10,"A","live"));
        for(int t=0;t<=120;t+=30)c.add(new EnergyIntegral.Sample(EPOCH+t*1000,-5,30,"A","archive"));
        List<SampleTimeline.Interval> intervals=EnergyIntegral.powerIntervals(Collections.emptyList(),v,c,EPOCH,EPOCH+120000);
        EnergyIntegral.Result r=EnergyIntegral.calculateIntervals(intervals,EPOCH,EPOCH+120000,false,true);
        assertEquals(120000,r.coveredMillis);assertEquals(-2,r.wh,1e-9);
        assertEquals(1,SampleTimeline.plot(Collections.emptyList(),intervals,EPOCH,EPOCH+120000).stream().filter(p->p.gap).count());
    }
    @Test public void complementaryChannelsCannotMultiplyValuesAcrossAnUnobservedGap(){
        List<EnergyIntegral.Sample> v=Arrays.asList(new EnergyIntegral.Sample(EPOCH,12,10,"A","live"),new EnergyIntegral.Sample(EPOCH+10000,12,10,"A","live"));
        List<EnergyIntegral.Sample> c=Arrays.asList(new EnergyIntegral.Sample(EPOCH+30000,-5,30,"A","archive"),new EnergyIntegral.Sample(EPOCH+60000,-5,30,"A","archive"));
        assertTrue(EnergyIntegral.powerIntervals(Collections.emptyList(),v,c,EPOCH,EPOCH+60000).isEmpty());
    }

    @Test public void complementaryChannelsFromDifferentDevicesCannotProducePower(){
        List<EnergyIntegral.Sample> v=Arrays.asList(new EnergyIntegral.Sample(EPOCH,12,30,"A","archive"),new EnergyIntegral.Sample(EPOCH+30000,12,30,"A","archive"));
        List<EnergyIntegral.Sample> c=Arrays.asList(new EnergyIntegral.Sample(EPOCH,-5,30,"B","archive"),new EnergyIntegral.Sample(EPOCH+30000,-5,30,"B","archive"));
        assertTrue(EnergyIntegral.powerIntervals(Collections.emptyList(),v,c,EPOCH,EPOCH+30000).isEmpty());
    }

}
