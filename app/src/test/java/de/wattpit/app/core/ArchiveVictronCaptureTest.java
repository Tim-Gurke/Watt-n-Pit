package de.wattpit.app.core;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveVictronCaptureTest {
    private CapturedVictronDevice capture()throws Exception{return new CapturedVictronDevice(getClass().getResourceAsStream("/victron/smartshunt-trends.tsv"));}
    @Test public void allOriginalRegularRepliesAcceptTheActualDeviceRounding()throws Exception{
        CapturedVictronDevice peer=capture();int regular=0,rounded=0;
        for(CapturedVictronDevice.Pair p:peer.pairs){
            int count=p.request[5]&255;if(count<=1)continue;regular++;
            int channel=p.request[0]&255;long cursor=ArchiveCodec.u(p.request,1,4);
            ArchiveCodec.Block b=new ArchiveCodec.Block(p.response,peer.field(channel),channel,count);
            ArchiveSyncPlan.Request q=new ArchiveSyncPlan.Request(cursor,0,count,0);
            assertTrue(ArchiveSyncPlan.related(channel,q,b.period,p.response));
            assertTrue("regular recorded reply rejected at "+cursor,ArchiveSyncPlan.matches(q,b.period,b));
            if(b.time!=cursor)rounded++;
        }
        assertEquals(462,regular);assertEquals(391,rounded);
    }
    @Test public void lateRepliesCannotBeStoredForAnotherCursorChannelOrTier()throws Exception{
        CapturedVictronDevice peer=capture();
        CapturedVictronDevice.Pair p=peer.pairs.stream().filter(x->(x.request[5]&255)>1&&ArchiveCodec.u(x.response,6,2)==30).findFirst().get();
        int ch=p.request[0]&255,count=p.request[5]&255;long cursor=ArchiveCodec.u(p.request,1,4);
        ArchiveSyncPlan.Request q=new ArchiveSyncPlan.Request(cursor,0,count,0);
        assertFalse(ArchiveSyncPlan.related(ch+1,q,30,p.response));
        assertFalse(ArchiveSyncPlan.related(ch,new ArchiveSyncPlan.Request(cursor-30,0,count,0),30,p.response));
        assertFalse(ArchiveSyncPlan.related(ch,new ArchiveSyncPlan.Request(cursor+30,0,count,0),30,p.response));
        assertFalse(ArchiveSyncPlan.related(ch,q,300,p.response));
    }
    @Test public void cachedCoarseTiersKeepTheirCapturedAgeInsteadOfJumpingToNow()throws Exception{
        CapturedVictronDevice peer=capture();long uptime=ArchiveCodec.u(peer.registers.get(0xec5a),0,4),elapsed=3*3600;
        for(List<ArchiveCodec.Level> levels:peer.levels().values()){
            List<ArchiveCodec.Level> unchanged=ArchiveSyncPlan.advance(levels,uptime,uptime,peer.clock(),peer.now()+300000);
            List<ArchiveCodec.Level> advanced=ArchiveSyncPlan.advance(levels,uptime,uptime+elapsed,peer.clock(),peer.now()+(elapsed+300)*1000);
            for(int i=0;i<levels.size();i++){
                assertEquals(levels.get(i).newest,unchanged.get(i).newest);
                assertEquals(elapsed,advanced.get(i).newest-levels.get(i).newest);
                assertEquals(levels.get(i).capacity,advanced.get(i).capacity);
            }
        }
    }
    @Test public void capturedArchiveCompletesWithoutReplansAndARepeatRequestsNothing()throws Exception{
        CapturedVictronDevice peer=capture();Map<Integer,List<ArchiveCodec.Level>> levels=peer.levels();
        Map<Integer,ArchiveSyncPlan.Availability> saved=new HashMap<>();Map<Integer,Integer>batches=new HashMap<>();
        for(int ch:levels.keySet()){List<Integer> periods=new ArrayList<>();for(ArchiveCodec.Level l:levels.get(ch))periods.add(l.period);saved.put(ch,new ArchiveSyncPlan.Availability(periods,peer.now()));batches.put(ch,peer.registers.get(0xec4a+ch)[1]&255);}
        ArchiveTransferPlan plan=new ArchiveTransferPlan(peer.clock(),saved,batches);for(int ch:levels.keySet())for(ArchiveCodec.Level l:levels.get(ch))plan.add(ch,l);
        Set<String> actual=new HashSet<>();ArchiveTransferPlan.Read r;int queries=0,boundaries=0;
        while((r=plan.next(ch->{}))!=null){
            assertTrue("transfer did not finish",++queries<550);byte[] raw=peer.reply(r);
            assertTrue(ArchiveSyncPlan.related(r.channel,r.request,r.period,raw));
            ArchiveCodec.Block b=new ArchiveCodec.Block(raw,peer.field(r.channel),r.channel,r.request.count);
            if(!plan.unavailable(b).isEmpty())boundaries++;
            for(int i=0;i<b.count;i++)if(b.values[i]!=null)actual.add(b.channel+":"+b.period+":"+(b.time-(long)i*b.period));
            assertTrue("normal device reply replanned at "+r.request.cursor,plan.accept(b));
        }
        Set<String> missing=new HashSet<>(peer.validKeys());missing.removeAll(actual);assertTrue("captured measurements missing: "+missing,missing.isEmpty());
        assertTrue("expected rolling fine-tier boundaries",boundaries>0);
        ArchiveTransferPlan again=new ArchiveTransferPlan(peer.clock(),saved,batches);for(int ch:levels.keySet())for(ArchiveCodec.Level l:levels.get(ch))again.add(ch,l);
        assertNull(again.next(ch->{}));assertEquals(0,again.remaining());
        System.out.println("Captured peer: "+queries+" block reads, "+boundaries+" ordinary tier endings, "+actual.size()+" captured measurements, zero replans and zero repeat reads.");
    }
}
