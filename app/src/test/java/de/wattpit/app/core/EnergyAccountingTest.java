package de.wattpit.app.core;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import de.wattpit.app.core.SampleTimeline.Interval;
import de.wattpit.app.core.EnergyIntegral.ModelInterval;

public final class EnergyAccountingTest {
    private static final long H=3600000L;
    private EnergyIntegral.Flow flow(double solar,double battery,List<ModelInterval> mobile){return EnergyIntegral.flowIntervals(List.of(new Interval(0,H,solar,solar)),List.of(new Interval(0,H,battery,battery)),0,H,0,H,mobile,mobile);}
    @Test public void deviceConsumptionIncludesMobileOnceWhileShuntBalanceStaysMeasured(){
        EnergyIntegral.Flow f=flow(155,49,List.of(new ModelInterval(0,H,60,true)));
        assertEquals(166,f.consumption.wh,1e-8);assertEquals(60,f.mobile.wh,1e-8);assertEquals(49,f.balance.wh,1e-8);assertEquals(155,f.solar.wh,1e-8);
        assertEquals(f.balance.wh,f.solar.wh+f.mobile.wh-f.consumption.wh,1e-8);assertEquals(H,f.consumption.modelledMillis);
    }
    @Test public void switchedOffAndAbsentMobileNeedNoWeatherAndHaveNoModelledDeviceShare(){
        EnergyIntegral.Flow f=flow(155,49,List.of(new ModelInterval(0,H,0,false)));
        assertEquals(106,f.consumption.wh,1e-8);assertEquals(0,f.mobile.wh,0);assertEquals(0,f.consumption.modelledMillis);
    }
    @Test public void switchingOffKeepsPreviouslyProducedEnergy(){
        EnergyIntegral.Flow f=flow(155,49,List.of(new ModelInterval(0,H/2,60,true),new ModelInterval(H/2,H,0,false)));
        assertEquals(30,f.mobile.wh,1e-8);assertEquals(136,f.consumption.wh,1e-8);assertEquals(H/2,f.consumption.modelledMillis);assertEquals(H,f.consumption.coveredMillis);
    }
    @Test public void unknownActiveMobileIsNotMeasuredZeroAndDoesNotInvalidateTheMeasuredBattery(){
        EnergyIntegral.Flow f=flow(155,49,Collections.emptyList());
        assertNull(f.consumption.wh);assertNull(f.mobile.wh);assertEquals(49,f.balance.wh,1e-8);assertEquals(155,f.solar.wh,1e-8);
    }
    @Test public void partialModelCoverageRetainsGapsAndDoesNotInventEnergy(){
        EnergyIntegral.Flow f=flow(155,49,List.of(new ModelInterval(0,H/4,60,true),new ModelInterval(H/2,H,0,false)));
        assertEquals(.75,f.consumption.coverage(0,H),1e-8);assertEquals(15,f.mobile.wh,1e-8);assertTrue(f.consumption.points.stream().anyMatch(p->p.time==H/2&&p.gap));
    }
    @Test public void aNegativeDeviceLoadIsFlaggedAndNotPresentedAsConsumption(){
        EnergyIntegral.Flow f=flow(100,200,List.of(new ModelInterval(0,H,20,true)));
        assertNull(f.consumption.wh);assertEquals(H,f.consumption.inconsistentMillis);assertEquals(200,f.balance.wh,0);assertEquals(20,f.mobile.wh,0);
    }
    @Test public void originalLearningResidualDoesNotReceiveTheDisplayCorrection(){
        EnergyIntegral.Flow f=EnergyIntegral.flowIntervals(List.of(new Interval(0,H,155,155)),List.of(new Interval(0,H,49,49)),0,H,0,H);
        assertEquals(106,f.consumption.wh,0);assertEquals(0,f.consumption.modelledMillis);
    }
}
