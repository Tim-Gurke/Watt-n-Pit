package de.wattpit.app.core;

/** Reject other scooters, stale samples, clock jumps and corrupt shared packets. */
public final class SharedBmsData {
    private SharedBmsData(){}
    public static BmsPacket validate(String selected,String reported,byte[] frame,long at,long elapsed,long wallNow,long elapsedNow){
        if(selected==null||!selected.equalsIgnoreCase(reported)||at<=0||elapsed<0||wallNow<at||wallNow-at>8000||elapsedNow<elapsed||elapsedNow-elapsed>8000)return null;
        return BmsPacket.decode(frame);
    }
}
