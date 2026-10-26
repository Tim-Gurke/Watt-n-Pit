package de.wattpit.app.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class SharedBmsDataTest {
 private static final String MAC="AA:BB:CC:DD:EE:FF";
 private byte[] packet(){byte[] b=new byte[30];b[0]=(byte)0xdd;b[1]=3;b[3]=23;b[4]=0x13;b[5]=(byte)0xd6;b[7]=(byte)189;b[23]=70;int sum=0;for(int i=2;i<27;i++)sum+=b[i]&255;int c=(-sum)&65535;b[27]=(byte)(c>>8);b[28]=(byte)c;b[29]=0x77;return b;}
 @Test public void validSharedFramePreservesMeasurement(){BmsPacket p=SharedBmsData.validate(MAC,MAC.toLowerCase(java.util.Locale.ROOT),packet(),10000,1000,10500,1500);assertNotNull(p);assertEquals(50.78,p.voltage,1e-9);assertEquals(1.89,p.current,1e-9);}
 @Test public void noOtherScooterOrStaleOrClockJump(){assertNull(SharedBmsData.validate(MAC,"02:00:00:00:00:02",packet(),10000,1000,10500,1500));assertNull(SharedBmsData.validate(MAC,MAC,packet(),10000,1000,19000,10000));assertNull(SharedBmsData.validate(MAC,MAC,packet(),11000,1000,10000,1500));assertNull(SharedBmsData.validate(MAC,MAC,packet(),10000,2000,10500,1500));}
 @Test public void corruptAndMissingFramesRejected(){byte[] b=packet();b[7]++;assertNull(SharedBmsData.validate(MAC,MAC,b,10000,1000,10500,1500));assertNull(SharedBmsData.validate(MAC,MAC,null,10000,1000,10500,1500));}
}
