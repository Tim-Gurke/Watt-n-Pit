package de.wattpit.app.core;

/** Packet credits and the session watchdog are independent parts of the BLE transport. */
public final class ArchiveLink {
    public static final int RECEIVE_WINDOW=128,REFILL=65;
    public static final long HEARTBEAT_MS=3000;
    private int sendCredits,pendingReceive,received,sent,grants;
    private long heartbeat=-1;

    public synchronized void control(byte[] packet){
        if(packet.length==2&&(packet[0]&255)==0xf9)sendCredits=Math.min(255,sendCredits+(packet[1]&255));
    }
    public synchronized boolean reserveSend(){
        if(sendCredits==0)return false;
        sendCredits--;sent++;return true;
    }
    /** Both register characteristics consume the shared notification window. */
    public synchronized void receivedData(){pendingReceive++;received++;}
    public synchronized int receiveGrant(){
        if(pendingReceive<REFILL)return 0;
        pendingReceive-=REFILL;grants++;return REFILL;
    }
    public synchronized void heartbeatSent(long now){heartbeat=now;}
    public synchronized boolean heartbeatDue(long now){return heartbeat>=0&&now-heartbeat>=HEARTBEAT_MS;}
    public synchronized int received(){return received;}
    public synchronized int sent(){return sent;}
    public synchronized int grants(){return grants;}
}
