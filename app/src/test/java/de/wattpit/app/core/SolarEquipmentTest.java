package de.wattpit.app.core;
import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class SolarEquipmentTest {
    @Test public void totalsDeclaredEquipmentWithoutInventingMeasuredWatts(){
        SolarEquipment equipment=new SolarEquipment(Arrays.asList(new SolarEquipment.Group("roof","Dach",3,90,"fixed","Regler A","solar"),new SolarEquipment.Group("case","Koffer",1,120,"mobile","PWM","shunt")));
        assertEquals(390,equipment.ratedWatts(),1e-8);assertEquals(270,equipment.groups.get(0).ratedWatts(),1e-8);assertEquals("shunt",equipment.groups.get(1).measurement);
        assertEquals(0,new SolarEquipment(Collections.emptyList()).ratedWatts(),1e-8);
    }
    @Test(expected=IllegalArgumentException.class)public void rejectsNonfiniteRating(){new SolarEquipment.Group("x","Module",1,Double.NaN,"mobile","","none");}
    @Test(expected=IllegalArgumentException.class)public void rejectsMissingModules(){new SolarEquipment.Group("x","Module",0,100,"mobile","","none");}
    @Test(expected=IllegalArgumentException.class)public void rejectsDuplicateGroups(){SolarEquipment.Group g=new SolarEquipment.Group("x","Module",1,100,"mobile","","none");new SolarEquipment(Arrays.asList(g,g));}
    @Test public void legacyAnglesStayUnknownAndMobileCircuitsRequireAnExplicitSetup(){
        assertNull(new SolarEquipment.Group("roof","Dach",1,100,"fixed","","solar").tilt);
        SolarEquipment.Group flat=new SolarEquipment.Group("roof","Dach",1,100,"fixed","","solar",0.0,true),mobile=new SolarEquipment.Group("case","Koffer",1,100,"mobile","","shunt",90.0,true);
        assertEquals(0,flat.tilt,0);assertTrue(flat.defaultActive);assertEquals(90,mobile.tilt,0);assertFalse(mobile.defaultActive);
    }
    @Test public void rejectsInvalidMountingAngles(){for(double angle:new double[]{-1,91,Double.NaN,Double.POSITIVE_INFINITY})try{new SolarEquipment.Group("roof","Dach",1,100,"fixed","","solar",angle,true);fail("Invalid angle");}catch(IllegalArgumentException expected){}}
    @Test public void legacyPwmNamesAndUntouchedDefaultsUseThePwmTotalOnce(){
        for(String name:new String[]{"PWM","Dokio pwm-Regler","PWM20A"}){
            assertEquals("pwm",SolarEquipment.controllerType(name,"auto"));
            assertEquals(.6,SolarEquipment.yieldFactor(name,"auto","",null),1e-9);
            assertEquals(.6,SolarEquipment.yieldFactor(name,"auto","",.8),1e-9);
        }
        assertEquals(.65,SolarEquipment.yieldFactor("PWM","auto","",.65),1e-9);
        assertEquals(.8,SolarEquipment.yieldFactor("PWM","auto","manual",.8),1e-9);
        assertEquals(.6,SolarEquipment.yieldFactor("PWM","auto","auto",.65),1e-9);
    }
    @Test public void explicitTypesWinAndAmbiguousControllerNamesStayUnclassified(){
        assertEquals(.8,SolarEquipment.yieldFactor("PWM","mppt","auto",null),1e-9);
        assertEquals(.6,SolarEquipment.yieldFactor("Regler","pwm","auto",null),1e-9);
        for(String name:new String[]{"MPPT75/15","MPPT / PWM","mypwm",""})assertEquals(.8,SolarEquipment.yieldFactor(name,"auto","",null),1e-9);
    }
    @Test public void invalidLossModelsAreRejected(){
        for(String type:new String[]{"bad","PWM"})try{SolarEquipment.yieldFactor("PWM",type,"auto",null);fail("Invalid type");}catch(IllegalArgumentException expected){}
        for(Double factor:new Double[]{Double.NaN,Double.POSITIVE_INFINITY,0.0,1.1})try{SolarEquipment.yieldFactor("PWM","auto","manual",factor);fail("Invalid factor");}catch(IllegalArgumentException expected){}
        try{SolarEquipment.yieldFactor("PWM","auto","manual",null);fail("Missing total");}catch(IllegalArgumentException expected){}
    }
}
