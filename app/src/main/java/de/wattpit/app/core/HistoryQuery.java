package de.wattpit.app.core;

import java.util.*;

/** Shared, executable SQL plan: imported data fills gaps; recordings win per metric. */
public final class HistoryQuery {
    public final String sql;
    public final String[] args;
    private HistoryQuery(String sql, List<String> args) { this.sql=sql;this.args=args.toArray(new String[0]); }
    public static HistoryQuery build(long from,long to,String metric,String source) {
        Selection selection = select(from,to,metric,source);
        long bucket=Math.max(1,(to-from)/600);
        List<String> args=new ArrayList<>(selection.args);
        args.add(String.valueOf(bucket));
        String field=selection.field;
        String sql="SELECT MIN(ts) t,MAX(ts) last,device,kind,origin,AVG("+field+") v,MIN("+field+") lo,MAX("+field+") hi,COUNT(*) n,MAX(period) resolution "+
                "FROM readings WHERE "+selection.where+" GROUP BY (ts / ?),device,kind,origin ORDER BY t";
        return new HistoryQuery(sql,args);
    }
    public static HistoryQuery summary(long from,long to,String metric,String source) {
        Selection s=select(from,to,metric,source);
        return new HistoryQuery("SELECT AVG("+s.field+"),MIN("+s.field+"),MAX("+s.field+"),COUNT(*),MAX(ts) FROM readings WHERE "+s.where,s.args);
    }
    public static HistoryQuery latest(long from,long to,String metric,String source) {
        Selection s=select(from,to,metric,source);
        return new HistoryQuery("SELECT "+s.field+",ts FROM readings WHERE "+s.where+" ORDER BY ts DESC LIMIT 1",s.args);
    }
    public static HistoryQuery rawPower(long from,long to,String metric,String source) {
        if(!metric.equals("solar")&&!metric.equals("batteryPower"))throw new IllegalArgumentException("Keine Leistungsmessgröße.");
        Selection s=select(from,to,metric,source,false);
        return new HistoryQuery("SELECT ts,"+s.field+",period,device,origin FROM readings WHERE "+s.where+" ORDER BY ts",s.args);
    }
    private static final class Selection {
        final String field,where; final List<String> args;
        Selection(String field,String where,List<String> args) { this.field=field;this.where=where;this.args=args; }
    }
    private static Selection select(long from,long to,String metric,String source) {
        return select(from,to,metric,source,true);
    }
    private static Selection select(long from,long to,String metric,String source,boolean merge) {
        if(from<0||to<=from)throw new IllegalArgumentException("Ungültiger Zeitraum.");
        if(!Arrays.asList("all","live","csv","archive").contains(source))throw new IllegalArgumentException("Unbekannte Datenquelle.");
        String field,kind="";
        switch(metric) {
            case "power":field="CASE WHEN kind='solar' THEN solar ELSE power END";break;
            case "batteryPower":field="power";kind="shunt";break;
            case "solar":field="solar";kind="solar";break;
            case "soc":field="soc";kind="shunt";break;
            case "voltage":field="voltage";break;
            case "batteryVoltage":field="voltage";kind="shunt";break;
            case "pvVoltage":field="pvVoltage";kind="solar";break;
            case "solarTemperature":field="temperature";kind="solar";break;
            case "solarVoltage":field="voltage";kind="solar";break;
            case "current":field="current";break;
            case "batteryCurrent":field="current";kind="shunt";break;
            case "solarCurrent":field="current";kind="solar";break;
            case "yieldWh":field="yieldWh";kind="solar";break;
            case "ttg":field="ttg";kind="shunt";break;
            case "temperature":field="temperature";kind="shunt";break;
            case "aux":field="aux";kind="shunt";break;
            case "consumed":field="ABS(consumed)";kind="shunt";break;
            case "storedEnergy":field="soc*voltage/100.0";kind="shunt";break;
            case "load":field="load";kind="solar";break;
            case "state":field="state";kind="solar";break;
            case "error":field="error";kind="solar";break;
            case "alarm":field="alarm";kind="shunt";break;
            default:throw new IllegalArgumentException("Unbekannte Messgröße.");
        }
        String where="ts>=? AND ts<=? AND ("+field+") IS NOT NULL";
        List<String> args=new ArrayList<>(Arrays.asList(String.valueOf(from),String.valueOf(to)));
        if(!kind.isEmpty()){where+=" AND kind=?";args.add(kind);}
        if(!source.equals("all")){where+=" AND origin=?";args.add(source);}
        else if(merge) {
            String liveField;
            if (metric.equals("power")) liveField="CASE WHEN l.kind='solar' THEN l.solar ELSE l.power END";
            else if (metric.equals("consumed")) liveField="ABS(l.consumed)";
            else if (metric.equals("storedEnergy")) liveField="l.soc*l.voltage/100.0";
            else liveField="l."+field;
            where+=" AND (origin='live' OR NOT EXISTS (SELECT 1 FROM readings l WHERE l.kind=readings.kind AND (l.device=readings.device OR readings.origin='csv') "+
                    "AND (l.origin='live' OR (readings.origin='csv' AND l.origin='archive') OR (readings.origin='archive' AND l.origin='archive' AND l.period<readings.period AND l.period>0)) "+
                    "AND l.ts BETWEEN readings.ts-MAX(45000,readings.period*500) AND readings.ts+MAX(45000,readings.period*500) AND ("+liveField+") IS NOT NULL))";
        }
        return new Selection(field,where,args);
    }
}
