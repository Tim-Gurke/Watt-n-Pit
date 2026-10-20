package de.wattpit.app.core;

import java.util.*;

/** The actual import queue: a rejected reply never consumes or drops planned work. */
public final class ArchiveTransferPlan {
    public interface Before { void check(int channel) throws Exception; }
    public static final class Read {
        public final int channel,period;
        public final ArchiveSyncPlan.Request request;
        private Read(int channel,int period,ArchiveSyncPlan.Request request){this.channel=channel;this.period=period;this.request=request;}
    }
    private static final class Task {
        final int channel,period;final List<ArchiveSyncPlan.Window> windows;
        int index,remaining;long cursor;
        Task(int channel,int period,ArchiveSyncPlan.Plan plan){this.channel=channel;this.period=period;windows=plan.windows;load();}
        void load(){ArchiveSyncPlan.Window w=windows.get(index);cursor=w.cursor;remaining=w.count;}
        boolean more(){if(remaining>0)return true;if(++index>=windows.size())return false;load();return true;}
        int remaining(){int n=remaining;for(int i=index+1;i<windows.size();i++)n+=windows.get(i).count;return n;}
    }
    private final ArchiveCodec.Clock clock;
    private final Map<Integer,ArchiveSyncPlan.Availability> local;
    private final Map<Integer,Integer> batches,retries=new HashMap<>();
    private final ArrayDeque<Task> pending=new ArrayDeque<>();
    private Task active;private Read read;
    public int skipped,deferred,processed,needed;
    public ArchiveTransferPlan(ArchiveCodec.Clock clock,Map<Integer,ArchiveSyncPlan.Availability> local,Map<Integer,Integer> batches){this.clock=clock;this.local=local;this.batches=batches;}
    public ArchiveSyncPlan.Plan add(int channel,ArchiveCodec.Level level){
        ArchiveSyncPlan.Plan plan=ArchiveSyncPlan.plan(level,clock,local.get(channel));
        skipped+=plan.known;deferred+=plan.deferred;needed+=plan.needed;
        if(plan.needed>0)pending.addLast(new Task(channel,level.period,plan));return plan;
    }
    public Read next(Before before)throws Exception{
        if(active!=null)throw new IllegalStateException("Archivantwort noch nicht bestätigt.");
        while(!pending.isEmpty()){
            Task t=pending.removeFirst();before.check(t.channel);
            long oldest=t.cursor-(long)(t.remaining-1)*t.period;
            ArchiveSyncPlan.Request r=ArchiveSyncPlan.next(t.cursor,t.remaining,oldest,t.period,batches.get(t.channel),clock,local.get(t.channel));
            skipped+=r.skipped-r.deferred;deferred+=r.deferred;processed+=r.skipped;t.remaining-=r.skipped;t.cursor=r.cursor;
            if(r.count==0){if(t.more())pending.addLast(t);continue;}
            active=t;read=new Read(t.channel,t.period,r);return read;
        }
        return null;
    }
    /** Call only after the returned samples and completion markers have been committed. */
    public boolean accept(ArchiveCodec.Block block){
        if(active==null)throw new IllegalStateException("Keine offene Archivabfrage.");
        local.get(active.channel).received(block,clock);
        if(!ArchiveSyncPlan.matches(read.request,read.period,block))return false;
        Task t=active;processed+=block.count;t.remaining-=block.count;t.cursor=block.time-(long)block.count*block.period;
        retries.remove(t.channel);active=null;read=null;if(t.more())pending.addLast(t);return true;
    }
    /** Refresh only this channel; preserve all other channels and all confirmed data. */
    public void refresh(int channel,List<ArchiveCodec.Level> levels,ArchiveSyncPlan.Availability availability,int batch){
        int attempt=retries.getOrDefault(channel,0)+1;
        if(attempt>3)throw new IllegalStateException("Archivantwort bleibt unvollständig. "+remaining()+" geplante Werte sind noch offen.");
        retries.put(channel,attempt);replace(channel,levels,availability,batch);
    }
    /** A final database audit can requeue missing work without replaying completed ranges. */
    public void replace(int channel,List<ArchiveCodec.Level> levels,ArchiveSyncPlan.Availability availability,int batch){
        if(active!=null&&active.channel==channel){active=null;read=null;}
        pending.removeIf(t->t.channel==channel);local.put(channel,availability);batches.put(channel,batch);
        for(ArchiveCodec.Level level:levels)add(channel,level);
    }
    public int remaining(){int n=active==null?0:active.remaining();for(Task t:pending)n+=t.remaining();return n;}
}
