package de.wattpit.app.core;

import java.util.*;

/** User-declared hardware, separate from measured power and charger configuration. */
public final class SolarEquipment {
    public static final class Group {
        public final String id,label,controller,placement,measurement;
        public final int count;public final double watts;
        public Group(String id,String label,int count,double watts,String placement,String controller,String measurement){
            if(id==null||!id.matches("[a-zA-Z0-9_-]{1,48}"))throw new IllegalArgumentException("Ungültige Solargruppe.");
            if(label==null||label.trim().isEmpty()||label.trim().length()>60||controller==null||controller.trim().length()>80)
                throw new IllegalArgumentException("Bitte kurze Bezeichnungen für Solargruppe und Regler eingeben.");
            if(count<1||count>99||!Double.isFinite(watts)||watts<1||watts>5000)throw new IllegalArgumentException("Bitte 1–99 Module mit jeweils 1–5.000 Wp angeben.");
            if(!Arrays.asList("fixed","mobile","other").contains(placement)||!Arrays.asList("solar","shunt","none").contains(measurement))throw new IllegalArgumentException("Bitte Montage und Messwertquelle wählen.");
            this.id=id;this.label=label.trim();this.count=count;this.watts=watts;this.placement=placement;this.controller=controller.trim();this.measurement=measurement;
        }
        public double ratedWatts(){return count*watts;}
    }
    public final List<Group> groups;
    public SolarEquipment(List<Group> groups){
        if(groups==null||groups.size()>32)throw new IllegalArgumentException("Bis zu 32 Solargruppen sind möglich.");
        Set<String> ids=new HashSet<>();for(Group g:groups)if(g==null||!ids.add(g.id))throw new IllegalArgumentException("Doppelte Solargruppe.");
        this.groups=Collections.unmodifiableList(new ArrayList<>(groups));
    }
    public double ratedWatts(){double total=0;for(Group g:groups)total+=g.ratedWatts();return total;}
}
