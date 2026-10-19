package de.wattpit.app.core;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EnergyFlowTest {
    private EnergyIntegral.Sample sample(long seconds,double watts,int period,String origin){return new EnergyIntegral.Sample(seconds*1000,watts,period,"device",origin);}
    private List<EnergyIntegral.Sample> constant(double watts){return Arrays.asList(sample(0,watts,3600,"archive"),sample(3600,watts,3600,"archive"));}
    private void totals(EnergyIntegral.Flow f,double solar,double consumption,double balance,long covered){
        assertEquals(solar,f.solar.wh,1e-9);assertEquals(consumption,f.consumption.wh,1e-9);assertEquals(balance,f.balance.wh,1e-9);
        assertEquals(f.solar.wh-f.consumption.wh,f.balance.wh,1e-9);
        for(EnergyIntegral.Result r:Arrays.asList(f.solar,f.consumption,f.balance))assertEquals(covered,r.coveredMillis);
    }
    @Test public void directSolarConsumptionIsIncludedWhileTheBatteryCharges(){
        totals(EnergyIntegral.flow(constant(100),constant(60),0,3600000),100,40,60,3600000);
    }
    @Test public void solarAndBatteryTogetherSupplyTheLoad(){
        totals(EnergyIntegral.flow(constant(20),constant(-80),0,3600000),20,100,-80,3600000);
    }
    @Test public void unmeteredPwmReducesBothKnownYieldAndCalculatedConsumptionEqually(){
        // Real generation is 20 Wh SmartSolar + 30 Wh PWM; loads use 100 Wh.
        totals(EnergyIntegral.flow(constant(20),constant(-50),0,3600000),20,70,-50,3600000);
        // More unmetered input than load must stay visible, not be clamped to zero.
        totals(EnergyIntegral.flow(constant(0),constant(30),0,3600000),0,-30,30,3600000);
    }
    @Test public void differentCoverageUsesTheIntersectionInsteadOfSubtractingUnmatchedTotals(){
        List<EnergyIntegral.Sample> solar=Arrays.asList(sample(0,120,60,"archive"),sample(60,120,60,"archive"),sample(120,120,60,"archive"));
        List<EnergyIntegral.Sample> battery=Arrays.asList(sample(60,-60,60,"archive"),sample(120,-60,60,"archive"),sample(180,-60,60,"archive"));
        EnergyIntegral.Flow f=EnergyIntegral.flow(solar,battery,0,180000);
        totals(f,2,3,-1,60000);assertEquals(1.0/3,f.solar.coverage(0,180000),1e-9);
        assertEquals(60000,f.solar.points.get(0).time);assertEquals(120000,f.solar.points.get(f.solar.points.size()-1).time);
    }
    @Test public void internalGapsNeverBecomeMeasuredZeroOrInventedConsumption(){
        List<EnergyIntegral.Sample> s=Arrays.asList(sample(0,60,0,"live"),sample(60,60,0,"live"),sample(600,60,0,"live"),sample(660,60,0,"live"));
        List<EnergyIntegral.Sample> b=Arrays.asList(sample(0,-60,660,"archive"),sample(660,-60,660,"archive"));
        EnergyIntegral.Flow f=EnergyIntegral.flow(s,b,0,720000);totals(f,2,4,-2,120000);
        for(EnergyIntegral.Result r:Arrays.asList(f.solar,f.consumption,f.balance))assertTrue(r.points.stream().anyMatch(p->p.time==600000&&p.gap));
    }
    @Test public void missingOneSourceKeepsAllThreeUnknownButMeasuredZeroIsZero(){
        EnergyIntegral.Flow missing=EnergyIntegral.flow(constant(20),Collections.emptyList(),0,3600000);
        for(EnergyIntegral.Result r:Arrays.asList(missing.solar,missing.consumption,missing.balance)){assertNull(r.wh);assertTrue(r.points.isEmpty());}
        totals(EnergyIntegral.flow(constant(0),constant(0),0,3600000),0,0,0,3600000);
    }
    @Test public void overlappingCopiesDoNotAddEnergyAndLiveReadingsHavePriority(){
        List<EnergyIntegral.Sample> solar=new ArrayList<>(constant(900));
        solar.add(sample(0,60,0,"live"));solar.add(sample(60,60,0,"live"));
        List<EnergyIntegral.Sample> battery=Arrays.asList(sample(0,-60,60,"archive"),sample(60,-60,60,"archive"),sample(0,-900,60,"csv"),sample(60,-900,60,"csv"));
        totals(EnergyIntegral.flow(solar,battery,0,60000),1,2,-1,60000);
    }
    @Test public void clipsBoundariesAndRetainsSignedBalanceTurningPoints(){
        List<EnergyIntegral.Sample> battery=Arrays.asList(sample(0,120,3600,"archive"),sample(3600,-120,3600,"archive"));
        EnergyIntegral.Flow f=EnergyIntegral.flow(constant(200),battery,900000,2700000);
        totals(f,100,100,0,1800000);assertEquals(7.5,f.balance.maximumWh,1e-9);
        assertTrue(f.balance.points.stream().anyMatch(p->p.time==1800000&&Math.abs(p.wh-7.5)<1e-9));
    }
    @Test public void sunriseYieldStillIncludesThePreviousNightInConsumptionAndBalance(){
        EnergyIntegral.Flow f=EnergyIntegral.flow(Arrays.asList(sample(1800,100,1800,"archive"),sample(3600,100,1800,"archive")),constant(-50),0,3600000,1800000);
        assertEquals(50,f.solar.wh,1e-9);assertEquals(100,f.consumption.wh,1e-9);assertEquals(-50,f.balance.wh,1e-9);
        assertEquals(1800000,f.solar.coveredMillis);assertEquals(3600000,f.consumption.coveredMillis);
        assertEquals(1800000,f.solar.points.get(0).time);
    }
    @Test public void absentDaytimeSolarDoesNotEraseMeasuredNighttimeConsumption(){
        EnergyIntegral.Flow f=EnergyIntegral.flow(Collections.emptyList(),constant(-50),0,3600000,1800000);
        assertNull(f.solar.wh);assertEquals(25,f.consumption.wh,1e-9);assertEquals(-25,f.balance.wh,1e-9);
        assertEquals(1800000,f.consumption.coveredMillis);assertEquals(.5,f.balance.coverage(0,3600000),1e-9);
    }
    @Test public void beforeSunriseYieldIsZeroAndNighttimeConsumptionUsesTheShunt(){
        EnergyIntegral.Flow f=EnergyIntegral.flow(Collections.emptyList(),constant(-60),0,1200000,1800000);
        assertEquals(0,f.solar.wh,0);assertEquals(20,f.consumption.wh,1e-9);assertEquals(-20,f.balance.wh,1e-9);
        assertEquals(1200000,f.balance.coveredMillis);
    }

    @Test public void positiveProductionBeforeSunriseAlwaysCounts(){
        EnergyIntegral.Flow f=EnergyIntegral.flow(constant(100),constant(-50),0,3600000,1800000);
        totals(f,100,150,-50,3600000);assertEquals(0,f.solarFrom);
    }
    @Test public void sunsetStopsSolarButNeverStopsEveningConsumption(){
        List<EnergyIntegral.Sample> s=Arrays.asList(sample(900,100,1800,"archive"),sample(2700,100,1800,"archive"));
        EnergyIntegral.Flow f=EnergyIntegral.flow(s,constant(-50),0,3600000,900000,2700000);
        assertEquals(50,f.solar.wh,1e-9);assertEquals(100,f.consumption.wh,1e-9);assertEquals(-50,f.balance.wh,1e-9);
        assertEquals(1800000,f.solar.coveredMillis);assertEquals(3600000,f.consumption.coveredMillis);assertEquals(2700000,f.solarTo);
        assertEquals(f.solar.wh-f.consumption.wh,f.balance.wh,1e-9);
    }
    @Test public void measuredEarlyAndLateProductionExtendBothBoundsWithoutDoubleCounting(){
        List<EnergyIntegral.Sample> s=Arrays.asList(sample(0,0,900,"archive"),sample(900,100,900,"archive"),sample(1800,100,900,"archive"),sample(2700,0,900,"archive"),sample(3600,0,900,"archive"));
        EnergyIntegral.Flow f=EnergyIntegral.flow(s,constant(-50),0,3600000,1200000,2100000);
        assertEquals(50,f.solar.wh,1e-9);assertEquals(100,f.consumption.wh,1e-9);assertEquals(-50,f.balance.wh,1e-9);
        assertEquals(0,f.solarFrom);assertEquals(2700000,f.solarTo);assertEquals(3600000,f.balance.coveredMillis);
    }
    @Test public void earlyRealProductionBeforeFutureSunriseIsNotDiscarded(){
        EnergyIntegral.Flow f=EnergyIntegral.flow(constant(100),constant(-50),0,1200000,1800000,3000000);
        assertEquals(100.0/3,f.solar.wh,1e-9);assertEquals(50,f.consumption.wh,1e-9);assertEquals(-50.0/3,f.balance.wh,1e-9);
        assertEquals(0,f.solarFrom);assertEquals(1200000,f.solarTo);
    }
    @Test public void internalDaylightGapsStayUnknownWithNightZeroesOnEitherSide(){
        List<EnergyIntegral.Sample> s=Arrays.asList(sample(600,60,0,"live"),sample(660,60,0,"live"),sample(1200,60,0,"live"),sample(1260,60,0,"live"));
        EnergyIntegral.Flow f=EnergyIntegral.flow(s,constant(-60),0,3600000,600000,1260000);
        assertEquals(2,f.solar.wh,1e-9);assertEquals(3060000,f.balance.coveredMillis);
        assertEquals(f.solar.wh-f.consumption.wh,f.balance.wh,1e-9);
        assertTrue(f.solar.points.stream().anyMatch(p->p.time==1200000&&p.gap));
    }
    @Test public void lowerPriorityPositiveArchiveCannotExtendAZeroLiveWindow(){
        List<EnergyIntegral.Sample> s=new ArrayList<>(constant(900));s.add(sample(0,0,3600,"live"));s.add(sample(3600,0,3600,"live"));
        EnergyIntegral.Flow f=EnergyIntegral.flow(s,constant(-50),0,3600000,900000,2700000);
        assertEquals(0,f.solar.wh,1e-9);assertEquals(900000,f.solarFrom);assertEquals(2700000,f.solarTo);
    }
    @Test public void anIsolatedPointOrInvalidPowerCannotInventSolarEnergy(){
        EnergyIntegral.Flow f=EnergyIntegral.flow(Arrays.asList(sample(300,200,0,"live"),sample(600,Double.NaN,0,"live")),constant(-50),0,3600000,900000,2700000);
        assertNull(f.solar.wh);assertEquals(900000,f.solarFrom);assertEquals(2700000,f.solarTo);
    }
    @Test public void aWindowEntirelyAfterSunsetHasZeroSolarAndStillCountsConsumption(){
        EnergyIntegral.Flow f=EnergyIntegral.flow(Collections.emptyList(),constant(-60),1800000,3600000,900000,1200000);
        assertEquals(0,f.solar.wh,0);assertEquals(30,f.consumption.wh,1e-9);assertEquals(-30,f.balance.wh,1e-9);
    }
}
