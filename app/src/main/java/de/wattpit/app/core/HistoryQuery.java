package de.wattpit.app.core;

import java.util.*;

/** Shared, executable SQL plan: imported data fills gaps; recordings win per metric. */
public final class HistoryQuery {
    public final String sql;
    public final String[] args;
    private HistoryQuery(String sql, List<String> args) { this.sql=sql;this.args=args.toArray(new String[0]); }
    public static HistoryQuery build(long from,long to,String metric,String source) {
        if(from<0||to<=from)throw new IllegalArgumentException("Ungültiger Zeitraum.");
        if(!Arrays.asList("all","live","csv").contains(source))throw new IllegalArgumentException("Unbekannte Datenquelle.");
        String field,kind="";
        switch(metric) {
            case "power":field="CASE WHEN kind='solar' THEN solar ELSE power END";break;
            case "soc":field="soc";kind="shunt";break;
            case "voltage":field="voltage";break;
            case "current":field="current";break;
            case "yieldWh":field="yieldWh";kind="solar";break;
            default:throw new IllegalArgumentException("Unbekannte Messgröße.");
        }
        long bucket=Math.max(1,(to-from)/600);
        String where="ts>=? AND ts<=? AND ("+field+") IS NOT NULL";
        List<String> args=new ArrayList<>(Arrays.asList(String.valueOf(from),String.valueOf(to)));
        if(!kind.isEmpty()){where+=" AND kind=?";args.add(kind);}
        if(!source.equals("all")){where+=" AND origin=?";args.add(source);}
        else {
            String liveField=metric.equals("power")?"CASE WHEN l.kind='solar' THEN l.solar ELSE l.power END":"l."+field;
            where+=" AND (origin='live' OR NOT EXISTS (SELECT 1 FROM readings l WHERE l.kind=readings.kind AND l.origin='live' "+
                    "AND l.ts BETWEEN readings.ts-45000 AND readings.ts+45000 AND ("+liveField+") IS NOT NULL))";
        }
        args.add(String.valueOf(bucket));
        String sql="SELECT MIN(ts) t,MAX(ts) last,device,kind,origin,AVG("+field+") v,MIN("+field+") lo,MAX("+field+") hi,COUNT(*) n "+
                "FROM readings WHERE "+where+" GROUP BY (ts / ?),device,kind,origin ORDER BY t";
        return new HistoryQuery(sql,args);
    }
}
