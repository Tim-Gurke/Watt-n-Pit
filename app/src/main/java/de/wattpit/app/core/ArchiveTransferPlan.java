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
        final int channel,period;final long oldest;final List<ArchiveSyncPlan.Window> windows;
        int index,remaining;long cursor;boolean rounded,answered;
        Task(int channel,ArchiveCodec.Level level,ArchiveSyncPlan.Plan plan){this.channel=channel;this.period=level.period;oldest=level.oldest;windows=plan.windows;load();}
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
        if(plan.needed>0)pending.addLast(new Task(channel,level,plan));return plan;
    }
    public Read next(Before before)throws Exception{
        if(active!=null)throw new IllegalStateException("Archivantwort noch nicht bestätigt.");
        while(!pending.isEmpty()){
            Task t=pending.removeFirst();before.check(t.channel);
            long oldest=t.cursor-(long)(t.remaining-1)*t.period;
            ArchiveSyncPlan.Request r=ArchiveSyncPlan.next(t.cursor,t.remaining,oldest,t.period,batches.get(t.channel),clock,local.get(t.channel));
            skipped+=r.skipped-r.deferred;deferred+=r.deferred;processed+=r.skipped;t.remaining-=r.skipped;t.cursor=r.cursor;
            if(r.count==0){if(t.more())pending.addLast(t);continue;}
            // Rounded tier metadata can omit its oldest boundary sample from
            // the nominal count. Let the device finish that final packet, as
            // VictronConnect does, rather than truncating it to a guessed grid.
            if(t.rounded&&r.count==t.remaining&&t.index==t.windows.size()-1&&
                    t.cursor-(long)(t.remaining-1)*t.period<t.oldest+t.period){
                int count=(int)Math.min(batches.get(t.channel),t.cursor/t.period+1);
                if(count>r.count)r=new ArchiveSyncPlan.Request(r.cursor,r.skipped,count,r.deferred);
            }
            active=t;read=new Read(t.channel,t.period,r);return read;
        }
        return null;
    }
    /** Call only after the returned samples and completion markers have been committed. */
    public boolean accept(ArchiveCodec.Block block){
        if(active==null)throw new IllegalStateException("Keine offene Archivabfrage.");
        local.get(active.channel).received(block,clock);
        if(tierEnd(block)){
            Task t=active;for(long[] range:unavailable(block))local.get(t.channel).imported(t.period,range[0],range[1]);
            processed+=t.remaining();retries.remove(t.channel);active=null;read=null;return true;
        }
        if(!ArchiveSyncPlan.matches(read.request,read.period,block))return false;
        Task t=active;t.answered=true;t.rounded|=block.time!=read.request.cursor;
        int consumed=Math.min(block.count,t.remaining);processed+=consumed;t.remaining-=consumed;t.cursor=block.time-(long)block.count*block.period;
        retries.remove(t.channel);active=null;read=null;if(t.more())pending.addFirst(t);return true;
    }
    /** An empty/coarser reply is normal only at the old end of the expected tier.
     * A missing reply or an empty block in its middle still requires a refresh. */
    private boolean tierEnd(ArchiveCodec.Block block){
        if(active==null||!active.answered||block.channel!=active.channel||block.count>read.request.count)return false;
        long delta=block.time-read.request.cursor;
        int returned=block.count==0?read.period:block.period;
        return (block.count==0||block.period>read.period)&&delta>=0&&delta<returned&&
                read.request.cursor-active.oldest<(long)batches.get(active.channel)*active.period;
    }
    /** Commit these unavailable fine-tier positions before accepting its boundary reply. */
    public List<long[]> unavailable(ArchiveCodec.Block block){
        List<long[]> result=new ArrayList<>();if(!tierEnd(block))return result;
        for(int i=active.index;i<active.windows.size();i++){
            ArchiveSyncPlan.Window w=active.windows.get(i);
            result.addAll(ArchiveSyncPlan.unavailable(i==active.index?active.cursor:w.cursor,
                    i==active.index?active.remaining:w.count,active.period,clock,local.get(active.channel)));
        }
        return result;
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
