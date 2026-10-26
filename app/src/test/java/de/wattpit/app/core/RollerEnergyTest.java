package de.wattpit.app.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class RollerEnergyTest {
    @Test public void measuredCalibration(){assertEquals(138,RollerEnergy.inputWatts(96,21,96.0/117),1e-9);assertEquals(0,RollerEnergy.inputWatts(0,21,.8),0);}
    @Test public void noEnergyAcrossMissingTime(){RollerEnergy.Session s=new RollerEnergy.Session();s.start(1000);s.sample(1000,96,21,96.0/117);s.sample(11000,96,21,96.0/117);s.sample(31000,96,21,96.0/117);assertEquals(96.0/360,s.batteryWh,1e-9);assertEquals(138.0/360,s.inputWh,1e-9);assertEquals(20000,s.gapMillis);assertEquals(10000,s.observedMillis);}
    @Test public void disconnectDoesNotCountGapTwice(){RollerEnergy.Session s=new RollerEnergy.Session();s.start(1000);s.sample(1000,96,21,.8);s.sample(3000,96,21,.8);s.interrupt(4000);s.sample(9000,96,21,.8);s.sample(11000,96,21,.8);s.stop(12000);assertEquals(7000,s.gapMillis);assertEquals(4000,s.observedMillis);assertEquals(11000,s.gapMillis+s.observedMillis);}
    @Test public void dischargingDoesNotBecomeCharge(){RollerEnergy.Session s=new RollerEnergy.Session();s.start(1000);s.sample(1000,-50,21,.8);s.sample(3000,-50,21,.8);assertEquals(0,s.batteryWh,0);assertEquals(0,s.inputWh,0);}
    @Test public void ignoresOutOfOrder(){RollerEnergy.Session s=new RollerEnergy.Session();s.start(1000);s.sample(2000,96,21,.8);s.sample(1500,96,21,.8);assertEquals(2000,s.lastAt);}
    @Test public void stopsWithoutInventingTail(){RollerEnergy.Session s=new RollerEnergy.Session();s.start(1000);s.sample(1000,96,21,.8);s.stop(10000);assertEquals(9000,s.gapMillis);assertEquals(0,s.batteryWh,0);}
    @Test public void cachedSharedPacketCannotReopenTimeBeforeHandover(){RollerEnergy.Session s=new RollerEnergy.Session();s.start(1000);s.sample(1000,96,21,.8);s.sample(3000,96,21,.8);s.interrupt(4000);s.sample(3500,96,21,.8);assertEquals(0,s.lastAt);assertEquals(1000,s.gapMillis);s.sample(5000,96,21,.8);s.sample(7000,96,21,.8);s.stop(8000);assertEquals(4000,s.observedMillis);assertEquals(3000,s.gapMillis);assertEquals(7000,s.observedMillis+s.gapMillis);}
    private byte[] frame(){byte[] b=new byte[34];b[0]=(byte)0xdd;b[1]=3;b[3]=27;b[4]=0x13;b[5]=(byte)0xd6;b[6]=0;b[7]=(byte)189;b[8]=7;b[9]=38;b[10]=10;b[11]=40;b[23]=70;b[24]=1;b[26]=2;b[27]=11;b[28]=36;b[29]=11;b[30]=0;checksum(b);return b;}
    private void checksum(byte[] b){int n=b[3]&255,sum=0;for(int i=2;i<n+4;i++)sum+=b[i]&255;int value=(-sum)&65535;b[n+4]=(byte)(value>>8);b[n+5]=(byte)value;b[n+6]=0x77;}
    @Test public void decodesActualUnitsAndSigns(){byte[] b=frame();BmsPacket p=BmsPacket.decode(b);assertNotNull(p);assertEquals(50.78,p.voltage,1e-9);assertEquals(1.89,p.current,1e-9);assertEquals(26,p.fullAh,1e-9);assertEquals(70,p.soc);assertTrue(p.chargeEnabled);assertFalse(p.dischargeEnabled);b[6]=(byte)0xff;b[7]=(byte)0x43;checksum(b);assertEquals(-1.89,BmsPacket.decode(b).current,1e-9);}
    @Test public void rejectsCorruptionAndTruncation(){byte[] b=frame();b[7]++;assertNull(BmsPacket.decode(b));assertNull(BmsPacket.decode(new byte[20]));}
}
