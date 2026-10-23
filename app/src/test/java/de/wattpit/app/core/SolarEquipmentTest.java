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
}
