package de.wattpit.app.core;

import java.util.*;

/** Per-field comparison with all local sources, executed before storing an archive value. */
public final class ArchiveMerge {
    public final String sql;
    public final String[] args;
    private ArchiveMerge(String sql,String[] args){this.sql=sql;this.args=args;}
    public static ArchiveMerge existing(String field,String device,String kind,long time,int period,double value){
        if(!Arrays.asList("voltage","current","soc","solar","temperature","aux","load","pvVoltage","auxRaw").contains(field))throw new IllegalArgumentException("Unbekannter Archivwert.");
        // CSV without a declared interval matches only an effectively identical instant/value.
        // A finer stored archive can replace the coarser sample, but never a missing channel.
        String sql="SELECT 1 FROM readings WHERE kind=? AND (device=? OR (origin='csv' AND device=?)) AND "+field+" IS NOT NULL AND ts BETWEEN ? AND ? AND ("+
                "(origin='live' AND ABS(ts-?)<=?) OR "+
                "(origin='archive' AND period>0 AND period<? AND ABS(ts-?)<=?) OR "+
                "(origin='csv' AND ABS(ts-?)<=1000 AND ((period>0 AND period<=?) OR (period=0 AND ABS("+field+"-?)<0.000001)))) LIMIT 1";
        long near=Math.min(45000,Math.max(500,period*500L));
        return new ArchiveMerge(sql,new String[]{kind,device,"csv:"+kind,""+(time-Math.max(near,1000)),""+(time+Math.max(near,1000)),""+time,""+near,""+period,""+time,""+near,""+time,""+period,""+value});
    }
}
