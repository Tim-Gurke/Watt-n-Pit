package de.wattpit.app.core;

import java.util.*;

/** Read-only VRegs from Victron's BMV/MPPT protocol documents. No configuration commands. */
public final class VictronSettings {
    private VictronSettings() { }
    public static final long MAX_AGE=6L*3600000;
    public static final class Field {
        public final int register,width;public final String name;private final double scale,min,max;private final boolean signed;
        Field(int r,String n,int w,double s,double min,double max,boolean signed){register=r;name=n;width=w;scale=s;this.min=min;this.max=max;this.signed=signed;}
        public Double decode(byte[] bytes){
            if(bytes==null||bytes.length!=width)return null;long raw=ArchiveCodec.u(bytes,0,width);
            if(signed&&(raw&(1L<<(width*8-1)))!=0)raw-=1L<<(width*8);
            double value=raw*scale;return Double.isFinite(value)&&value>=min&&value<=max?value:null;
        }
    }
    private static Field f(int r,String n,int w,double s,double min,double max){return new Field(r,n,w,s,min,max,false);}
    private static final Field[] SHUNT={
        f(0x1000,"capacity",2,1,1,32500),f(0x1001,"chargedVoltage",2,.1,1,95),
        f(0x1002,"tailCurrent",2,.1,.5,10),f(0x1003,"chargedMinutes",2,1,1,50),
        f(0x1004,"chargeEfficiency",2,1,50,100),f(0x1005,"peukert",2,.01,1,1.5),
        f(0x1006,"currentThreshold",2,.01,0,2),f(0x1008,"dischargeFloor",2,.1,0,99),
        f(0xEEB6,"synchronizationState",1,1,0,1)
    };
    private static final Field[] SOLAR={
        f(0xEDF7,"absorptionVoltage",2,.01,1,100),f(0xEDF6,"floatVoltage",2,.01,1,100),
        f(0xEDF0,"maxChargeCurrent",2,.1,0,1000),f(0xEDFB,"absorptionHours",2,.01,0,24),
        new Field(0xEDF2,"temperatureCompensation",2,.01,-1000,1000,true),
        f(0xEDF1,"batteryType",1,1,0,255),f(0xEDE7,"tailCurrentAmps",2,.1,0,1000),
        f(0xEDFE,"adaptive",1,1,0,1),f(0xED2E,"rebulkOffset",2,.01,0,100)
    };
    public static List<Field> fields(String kind){return Collections.unmodifiableList(Arrays.asList(kind.equals("solar")?SOLAR:kind.equals("shunt")?SHUNT:new Field[0]));}
    public static boolean readable(int reg){for(Field f:SOLAR)if(f.register==reg)return true;for(Field f:SHUNT)if(f.register==reg)return true;return false;}
    /** A conservative training gate, not a meter for energy the panels could have produced. */
    public static String limit(int state,double voltage,double current,Double absorption,Double maxCurrent,Double compensation){
        if(state==4||state==5||state==6||state==7||state==247||state==252)return "voltage";
        if(state!=3)return "unknown";
        if(absorption==null||maxCurrent==null||compensation==null||!Double.isFinite(voltage)||!Double.isFinite(current))return "unknown";
        if(maxCurrent<=0||current>=maxCurrent*.95)return "current";
        // A temperature-dependent target cannot be reconstructed without a battery temperature.
        if(compensation!=0)return "unknown";
        if(voltage>=absorption-.05)return "voltage";
        return "clear";
    }
}
