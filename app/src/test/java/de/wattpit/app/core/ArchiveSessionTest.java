package de.wattpit.app.core;
import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class ArchiveSessionTest {
    @Test public void opensEveryContextBeforeArchiveTraffic(){
        ArchiveSession.Step[] steps=ArchiveSession.setup();List<Integer> contexts=new ArrayList<>();
        for(ArchiveSession.Step step:steps)if(step.command[0]==3)contexts.add((int)step.command[1]);
        assertEquals(Arrays.asList(0,1,3),contexts);assertArrayEquals(new byte[]{1},steps[0].command);
        // No battery configuration, clock write, key read or archive deletion is part of setup.
        assertEquals(5,steps.length);assertEquals(0,steps[2].reply);
    }
    @Test public void splitContextConfirmationMustFinishBeforeProceeding(){
        ArchiveCodec.Stream stream=new ArchiveCodec.Stream();assertTrue(stream.feed(new byte[]{7,0}).isEmpty());
        List<ArchiveCodec.Message> messages=stream.feed(new byte[]{3,0,7,0,3,0});assertEquals(2,messages.size());
        assertTrue(ArchiveSession.accepted(ArchiveSession.setup()[3],messages.get(0)));assertTrue(ArchiveSession.accepted(ArchiveSession.setup()[4],messages.get(1)));
    }
    @Test(expected=IllegalArgumentException.class)public void rejectedContextIsNotTreatedAsReady(){
        ArchiveCodec.Message m=new ArchiveCodec.Stream().feed(new byte[]{7,0,3,1}).get(0);ArchiveSession.accepted(ArchiveSession.setup()[4],m);
    }
}
