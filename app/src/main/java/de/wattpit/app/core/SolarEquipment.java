package de.wattpit.app.core;

import java.util.*;

/** User-declared hardware, separate from measured power and charger configuration. */
public final class SolarEquipment {
    /** Declared controller text is enough for old installations; an explicit type wins. */
    public static String controllerType(String controller,String declared){
        String type=declared==null||declared.isEmpty()?"auto":declared;
        if(!Arrays.asList("auto","pwm","mppt","other").contains(type))throw new IllegalArgumentException("Bitte einen gültigen Reglertyp wählen.");
        if(!type.equals("auto"))return type;
        String name=controller==null?"":controller.toLowerCase(Locale.ROOT);
        boolean pwm=java.util.regex.Pattern.compile("(^|[^a-z])pwm([^a-z]|$)").matcher(name).find(),mppt=java.util.regex.Pattern.compile("(^|[^a-z])mppt([^a-z]|$)").matcher(name).find();
        return pwm&&!mppt?"pwm":mppt&&!pwm?"mppt":"other";
    }
    public static String yieldMode(String declared,Double factor){
        if(factor!=null&&(!Double.isFinite(factor)||factor<.1||factor>1))throw new IllegalArgumentException("Ertragsfaktor muss zwischen 0,1 und 1 liegen.");
        // Legacy .8 was also written by the editor for an untouched default.
        String mode=declared==null||declared.isEmpty()?(factor==null||factor==.8?"auto":"manual"):declared;
        if(!Arrays.asList("auto","manual").contains(mode)||mode.equals("manual")&&factor==null)throw new IllegalArgumentException("Bitte den eigenen Gesamtfaktor angeben.");
        return mode;
    }
    /** A provisional total loss model, never a second derating of a custom total. */
    public static double yieldFactor(String controller,String type,String mode,Double factor){
        String resolvedType=controllerType(controller,type),resolvedMode=yieldMode(mode,factor);
        return resolvedMode.equals("manual")?factor:resolvedType.equals("pwm")?.6:.8;
    }
    public static final class Group {
        public final String id,label,controller,placement,measurement;
        public final int count;public final double watts;public final Double tilt;public final boolean defaultActive;
        public Group(String id,String label,int count,double watts,String placement,String controller,String measurement){
            this(id,label,count,watts,placement,controller,measurement,null,!"mobile".equals(placement));
        }
        public Group(String id,String label,int count,double watts,String placement,String controller,String measurement,Double tilt,boolean defaultActive){
            if(id==null||!id.matches("[a-zA-Z0-9_-]{1,48}"))throw new IllegalArgumentException("Ungültige Solargruppe.");
            if(label==null||label.trim().isEmpty()||label.trim().length()>60||controller==null||controller.trim().length()>80)
                throw new IllegalArgumentException("Bitte kurze Bezeichnungen für Solargruppe und Regler eingeben.");
            if(count<1||count>99||!Double.isFinite(watts)||watts<1||watts>5000)throw new IllegalArgumentException("Bitte 1–99 Module mit jeweils 1–5.000 Wp angeben.");
            if(!Arrays.asList("fixed","mobile","other").contains(placement)||!Arrays.asList("solar","shunt","none").contains(measurement))throw new IllegalArgumentException("Bitte Montage und Messwertquelle wählen.");
            if(tilt!=null&&(!Double.isFinite(tilt)||tilt<0||tilt>90))throw new IllegalArgumentException("Der Montagewinkel muss zwischen 0° und 90° liegen.");
            this.id=id;this.label=label.trim();this.count=count;this.watts=watts;this.placement=placement;this.controller=controller.trim();this.measurement=measurement;this.tilt=tilt;this.defaultActive=!placement.equals("mobile")&&defaultActive;
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
