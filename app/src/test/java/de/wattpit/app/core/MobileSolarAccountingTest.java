package de.wattpit.app.core;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import de.wattpit.app.core.SampleTimeline.Interval;
import de.wattpit.app.core.EnergyIntegral.ModelInterval;

public final class MobileSolarAccountingTest {
    private static final long H=3600000,D=24*H,T=10*D+10*H;
    private static ModelInterval model(long a,long b,double w,boolean night,String site){return new ModelInterval(new Interval(a,b,w,w),!night,100,night,site,0);}
    private MobileSolarAccounting.Result estimate(double fixed,double net,double potential,int state,double voltage,boolean prior,List<Interval> roller,String site){
        List<Interval> s=new ArrayList<>(),b=new ArrayList<>(),v=new ArrayList<>();List<ModelInterval> models=new ArrayList<>();
        if(prior){s.add(new Interval(T-12*H,T-11*H,0,0));b.add(new Interval(T-12*H,T-11*H,-4,-4));v.add(new Interval(T-12*H,T-11*H,13.3,13.3));models.add(model(T-12*H,T-11*H,0,true,"site"));}
        s.add(new Interval(T,T+H,fixed,fixed));b.add(new Interval(T,T+H,net,net));v.add(new Interval(T,T+H,voltage,voltage));models.add(model(T,T+H,potential,false,site));
        return MobileSolarAccounting.adjust(T,T+H,s,b,List.of(new Interval(T,T+H,state,state)),v,Collections.emptyList(),models,models,roller);
    }
    @Test public void observedNightLoadResolvesTheReported155Plus9Minus211Contradiction(){
        MobileSolarAccounting.Result r=estimate(155,211,9,3,13.7,true,List.of(),"site");assertEquals(1,r.mobile.size());assertEquals(60,r.mobile.get(0).interval.a,0);assertEquals(ModelInterval.BALANCED,r.mobile.get(0).basis);
        EnergyIntegral.Flow f=EnergyIntegral.flowIntervals(List.of(new Interval(T,T+H,155,155)),List.of(new Interval(T,T+H,211,211)),T,T+H,T,T+H,r.extra,r.mobile);
        assertEquals(4,f.consumption.wh,1e-8);assertEquals(H,f.consumption.adjustedMillis);assertEquals(H,f.consumption.coveredMillis);assertEquals(211,f.balance.wh,0);
    }
    @Test public void floatUsesPlausibleDemandRatherThanAllAvailableSun(){
        MobileSolarAccounting.Result r=estimate(0,0,70,5,14.1,true,List.of(),"site");assertEquals(4,r.mobile.get(0).interval.a,0);assertTrue((r.mobile.get(0).basis&ModelInterval.LIMITED)!=0);
    }
    @Test public void limitingDoesNotBlindlySwitchOffMobileOrIgnoreKnownRollerLoad(){
        MobileSolarAccounting.Result r=estimate(20,0,70,4,14.2,true,List.of(new Interval(T,T+H,50,50)),"site");assertEquals(34,r.mobile.get(0).interval.a,0);
    }
    @Test public void independentlyRecordedRollerDemandCanExposeAnUnderestimateEvenBeforeTheResidualTurnsNegative(){
        MobileSolarAccounting.Result r=estimate(155,49,9,3,13.7,true,List.of(new Interval(T,T+H,150,150)),"site");assertEquals(48,r.mobile.get(0).interval.a,0);
    }
    @Test public void noPriorIsNotAZeroWattDeviceLoad(){
        MobileSolarAccounting.Result r=estimate(0,0,70,5,14.1,false,List.of(),"site");assertTrue(r.mobile.isEmpty());assertEquals(H,r.missingPriorMillis);
    }
    @Test public void anUnrelatedSiteDoesNotSupplyTheMissingPrior(){
        MobileSolarAccounting.Result r=estimate(0,0,70,5,14.1,true,List.of(),"another-site");assertTrue(r.mobile.isEmpty());
    }
    @Test public void abnormalVoltageDoesNotCreateFictitiousSolarOrDeviceConsumption(){
        MobileSolarAccounting.Result r=estimate(0,0,70,5,21.8,true,List.of(),"site");assertTrue(r.mobile.isEmpty());assertEquals(H,r.abnormalMillis);
    }
    @Test public void impossibleSourcePowerRemainsAnOpenInconsistency(){
        MobileSolarAccounting.Result r=estimate(100,250,9,3,13.7,true,List.of(),"site");assertTrue(r.mobile.isEmpty());assertEquals(H,r.abnormalMillis);
    }
    @Test public void plausibleBulkWeatherEstimateIsNotReplacedByTheNightMinimum(){
        MobileSolarAccounting.Result r=estimate(155,49,60,3,13.7,true,List.of(),"site");assertEquals(60,r.mobile.get(0).interval.a,0);assertEquals(0,r.mobile.get(0).basis);
    }
}
