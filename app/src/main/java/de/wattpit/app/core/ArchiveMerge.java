package de.wattpit.app.core;
import java.util.*;
/** Only an identical stored archive response is redundant after preflight. */
public final class ArchiveMerge {
    public final String sql;public final String[] args;
    private ArchiveMerge(String sql,String[] args){this.sql=sql;this.args=args;}
    public static ArchiveMerge existing(String field,String device,String kind,long time,int period,double value){
        if(!Arrays.asList("voltage","current","soc","solar","temperature","aux","load","pvVoltage","auxRaw").contains(field))throw new IllegalArgumentException("Unbekannter Archivwert.");
        return new ArchiveMerge("SELECT 1 FROM readings WHERE device=? AND kind=? AND origin='archive' AND ts=? AND period=? AND "+field+"=? LIMIT 1",new String[]{device,kind,""+time,""+period,""+value});
    }
}
