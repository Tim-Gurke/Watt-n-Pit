package de.wattpit.app.core;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EnergyIntegralTest {
    private EnergyIntegral.Sample sample(long seconds,double watts,int period,String origin){return new EnergyIntegral.Sample(seconds*1000,watts,period,"test-device",origin);}
    @Test public void resolvesFractionsBelowTheDeviceTenWhSteps(){
        List<EnergyIntegral.Sample> data=new ArrayList<>();for(int t=0;t<=3600;t+=30)data.add(sample(t,3,30,"archive"));
        EnergyIntegral.Result r=EnergyIntegral.calculate(data,0,3600000,false);
        assertEquals(3,r.wh,1e-9);assertEquals(1,r.coverage(0,3600000),1e-9);
    }
    @Test public void usesElapsedTimeAndClipsBothMidnightBoundaries(){
        EnergyIntegral.Result r=EnergyIntegral.calculate(Arrays.asList(sample(0,0,60,"archive"),sample(60,120,60,"archive")),15000,45000,false);
        assertEquals(.5,r.wh,1e-9);assertEquals(30000,r.coveredMillis);
    }
    @Test public void chargingCannotCancelPreviouslyDischargedEnergy(){
        EnergyIntegral.Result r=EnergyIntegral.calculate(Arrays.asList(sample(0,-100,60,"archive"),sample(60,100,60,"archive")),0,60000,true);
        assertEquals(100.0/240,r.wh,1e-9);
    }
    @Test public void unknownGapsAndUnobservedEdgesAreExcluded(){
        EnergyIntegral.Result r=EnergyIntegral.calculate(Arrays.asList(sample(30,60,0,"live"),sample(90,60,0,"live"),sample(600,60,0,"live"),sample(660,60,0,"live")),0,720000,false);
        assertEquals(2,r.wh,1e-9);assertEquals(120000,r.coveredMillis);assertEquals(1.0/6,r.coverage(0,720000),1e-9);
        assertTrue(r.points.stream().anyMatch(p->p.time==600000&&p.gap));
    }
    @Test public void knownZeroAndNoUsableIntervalsAreDifferent(){
        assertEquals(0,EnergyIntegral.calculate(Arrays.asList(sample(0,0,30,"archive"),sample(30,0,30,"archive")),0,30000,false).wh,0);
        assertNull(EnergyIntegral.calculate(Collections.singletonList(sample(0,100,30,"archive")),0,30000,false).wh);
        assertNull(EnergyIntegral.calculate(Arrays.asList(sample(0,100,30,"archive"),sample(60,100,30,"archive")),0,60000,false).wh);
        assertNull(EnergyIntegral.calculate(Arrays.asList(sample(0,100,0,"live"),sample(300,100,0,"live")),0,300000,false).wh);
    }
    @Test public void liveThenFineArchivesTakePriorityWithoutAddingOverlaps(){
        List<EnergyIntegral.Sample> data=Arrays.asList(sample(0,60,120,"archive"),sample(120,60,120,"archive"),
            sample(0,120,60,"archive"),sample(60,120,60,"archive"),sample(120,120,60,"archive"),
            sample(30,0,0,"live"),sample(90,0,0,"live"),sample(0,900,120,"csv"),sample(120,900,120,"csv"));
        EnergyIntegral.Result r=EnergyIntegral.calculate(data,0,120000,false);
        assertEquals(2,r.wh,1e-9);assertEquals(120000,r.coveredMillis);
    }
    @Test public void cannotInventIntervalsAcrossDifferentDevicesOrResolutions(){
        List<EnergyIntegral.Sample> data=Arrays.asList(sample(0,100,30,"archive"),sample(30,100,60,"archive"),new EnergyIntegral.Sample(60000,100,60,"other-device","archive"));
        assertNull(EnergyIntegral.calculate(data,0,90000,false).wh);
    }
    @Test public void invalidPowerBreaksRatherThanBridgesAnInterval(){
        assertNull(EnergyIntegral.calculate(Arrays.asList(sample(0,30,30,"archive"),sample(30,Double.NaN,30,"archive"),sample(60,30,30,"archive")),0,60000,false).wh);
    }
    @Test public void longRecordingsKeepTheExactTotalWithABoundedCurve(){
        List<EnergyIntegral.Sample> data=new ArrayList<>();for(int t=0;t<=86400;t+=5)data.add(sample(t,10,0,"live"));
        EnergyIntegral.Result r=EnergyIntegral.calculate(data,0,86400000,false);
        assertEquals(240,r.wh,1e-7);assertTrue(r.points.size()<=1202);assertEquals(240,r.points.get(r.points.size()-1).wh,1e-7);
    }

    @Test public void batteryBalanceIntegratesBothDirectionsAndPreservesTheTurningPoint(){
        List<EnergyIntegral.Sample> data=Arrays.asList(sample(0,120,3600,"archive"),sample(3600,-120,3600,"archive"));
        EnergyIntegral.Result r=EnergyIntegral.balance(data,0,3600000);
        assertEquals(0,r.wh,1e-9);assertEquals(30,r.maximumWh,1e-9);assertEquals(0,r.minimumWh,1e-9);
        assertTrue(r.points.stream().anyMatch(p->p.time==1800000&&Math.abs(p.wh-30)<1e-9));
        assertEquals(30,EnergyIntegral.calculate(data,0,3600000,true).wh,1e-9);
    }
    @Test public void batteryBalanceKeepsNegativeTotalsAndIgnoresOverlappingCopies(){
        List<EnergyIntegral.Sample> data=Arrays.asList(sample(0,-60,60,"archive"),sample(60,-60,60,"archive"),sample(0,900,60,"csv"),sample(60,900,60,"csv"));
        EnergyIntegral.Result r=EnergyIntegral.balance(data,0,60000);
        assertEquals(-1,r.wh,1e-9);assertEquals(-1,r.minimumWh,1e-9);assertEquals(0,r.maximumWh,1e-9);assertEquals(60000,r.coveredMillis);
        assertEquals(1,EnergyIntegral.calculate(data,0,60000,true).wh,1e-9);
    }
    @Test public void batteryBalanceHasKnownZeroButNoInventedEnergyInGaps(){
        assertEquals(0,EnergyIntegral.balance(Arrays.asList(sample(0,0,30,"archive"),sample(30,0,30,"archive")),0,30000).wh,0);
        assertNull(EnergyIntegral.balance(Arrays.asList(sample(0,50,0,"live"),sample(300,50,0,"live")),0,300000).wh);
        List<EnergyIntegral.Sample> data=Arrays.asList(sample(30,60,0,"live"),sample(90,60,0,"live"),sample(600,-120,0,"live"),sample(660,-120,0,"live"));
        EnergyIntegral.Result r=EnergyIntegral.balance(data,0,720000);
        assertEquals(-1,r.wh,1e-9);assertEquals(120000,r.coveredMillis);assertEquals(1.0/6,r.coverage(0,720000),1e-9);
        assertTrue(r.points.stream().anyMatch(p->p.time==600000&&p.gap));
    }
    @Test public void batteryBalanceClipsTheSelectedTimeWindow(){
        EnergyIntegral.Result r=EnergyIntegral.balance(Arrays.asList(sample(0,0,60,"archive"),sample(60,120,60,"archive")),15000,45000);
        assertEquals(.5,r.wh,1e-9);assertEquals(30000,r.coveredMillis);
    }
}
