package de.wattpit.app.core;

import java.util.*;

/** Plans device-time requests from this installation's actual per-field data. */
public final class ArchiveSyncPlan {
    private ArchiveSyncPlan() { }
    private static final Set<String> FIELDS = new HashSet<>(Arrays.asList(
            "voltage", "current", "soc", "solar", "temperature", "aux", "load", "pvVoltage", "auxRaw"));

    public static String readingsSql(String field) {
        if (!FIELDS.contains(field)) throw new IllegalArgumentException("Unbekannter Archivwert.");
        return "SELECT ts,origin,period FROM readings WHERE device=? AND kind=? AND " + field +
                " IS NOT NULL AND origin IN ('live','archive','csv') AND ts BETWEEN ? AND ? ORDER BY ts";
    }

    private static final class Intervals {
        private final TreeMap<Long, Long> ranges = new TreeMap<>();
        void add(long from, long to) {
            if (to < from) return;
            Map.Entry<Long, Long> previous = ranges.floorEntry(from);
            if (previous != null && previous.getValue() >= from - 1) {
                from = previous.getKey(); to = Math.max(to, previous.getValue()); ranges.remove(previous.getKey());
            }
            Map.Entry<Long, Long> next;
            while ((next = ranges.ceilingEntry(from)) != null && next.getKey() <= to + 1) {
                to = Math.max(to, next.getValue()); ranges.remove(next.getKey());
            }
            ranges.put(from, to);
        }
        boolean covers(long from,long to){Map.Entry<Long,Long> previous=ranges.floorEntry(from);return previous!=null&&previous.getValue()>=to;}
        boolean contains(long time) {
            Map.Entry<Long, Long> previous = ranges.floorEntry(time);
            return previous != null && previous.getValue() >= time;
        }
    }

    public static final class Availability {
        private final Map<Integer,Intervals> actual=new LinkedHashMap<>(),checked=new LinkedHashMap<>(),exact=new LinkedHashMap<>();
        private final Map<Integer,long[]> windows=new HashMap<>();
        private final Map<Integer,TreeSet<Long>> archived=new HashMap<>();
        private final long now;private long lastLive=Long.MIN_VALUE;private int lastLiveStep;
        public Availability(Collection<Integer> periods,long now){
            this.now=now;for(int p:periods){if(p<1)throw new IllegalArgumentException("Ungültige Archivauflösung.");actual.put(p,new Intervals());checked.put(p,new Intervals());exact.put(p,new Intervals());}
        }
        public void reading(long time,String origin,int resolution){
            if(origin.equals("live")){
                boolean join=lastLive!=Long.MIN_VALUE&&time>lastLive&&time-lastLive<=SampleTimeline.liveLimit(lastLiveStep,resolution);
                for(Intervals ranges:actual.values()){ranges.add(time-500,time+500);if(join)ranges.add(lastLive,time);}
                if(time>lastLive){lastLive=time;lastLiveStep=resolution;}return;
            }
            if(!(origin.equals("archive")||origin.equals("csv")))return;
            if(resolution<=0){if(origin.equals("csv"))for(Intervals ranges:actual.values())ranges.add(time,time);return;}
            TreeSet<Long> times=archived.computeIfAbsent(resolution,k->new TreeSet<>());
            Long lower=times.floor(time),upper=times.ceiling(time);times.add(time);
            for(int p:actual.keySet())if(resolution<=p){
                Intervals ranges=actual.get(p);ranges.add(time,time);
                if(lower!=null&&time-lower<=SampleTimeline.limit(origin,resolution))ranges.add(lower,time);
                if(upper!=null&&upper-time<=SampleTimeline.limit(origin,resolution))ranges.add(time,upper);
            }
            // A request cursor is rounded forward to the device sample grid. The
            // same-resolution sample at t answers cursors in (t-period,t]. This
            // is not permission to bridge a gap using a nearby live reading.
            if(exact.containsKey(resolution))exact.get(resolution).add(time-resolution*1000L+1,time);
        }
        public void imported(int period,long from,long to){Intervals ranges=checked.get(period);if(ranges!=null)ranges.add(from-period*1000L+1,to);}
        void received(ArchiveCodec.Block block,ArchiveCodec.Clock clock){
            for(int i=0;i<block.count;i++)if(block.values[i]!=null){Long time=clock.millis(block.time-(long)i*block.period);if(importable(time))reading(time,"archive",block.period);}
            for(long[] range:completed(block,clock,now))imported(block.period,range[0],range[1]);
        }
        private void window(int period,Long oldest,Long newest){if(oldest!=null&&newest!=null)windows.put(period,new long[]{Math.min(oldest,newest),Math.max(oldest,newest)});}
        public boolean known(Long time,int period){
            if(time==null||!actual.containsKey(period))return false;
            long from=time,to=time;long[] bounds=windows.get(period);
            if(bounds!=null){long half=period*500L;from=Math.max(bounds[0],time-half);to=Math.min(bounds[1],time+half);}
            // A nearby point alone cannot cover a real hole. Its surrounding archive cell
            // must be supported, or this exact resolution must already have been read.
            return actual.get(period).covers(from,to)||exact.get(period).contains(time)||checked.get(period).contains(time);
        }
        public boolean importable(Long time){return time!=null&&time>=0&&time<=now+300000;}
    }

    public static final class Request {
        public final long cursor;
        public final int skipped, count, deferred;
        Request(long cursor, int skipped, int count,int deferred) { this.cursor = cursor; this.skipped = skipped; this.count = count;this.deferred=deferred; }
    }

    /** A contiguous missing range, independent of the transport's packet size. */
    public static final class Window {
        public final long cursor;
        public final int count;
        private Window(long cursor,int count){this.cursor=cursor;this.count=count;}
    }

    /** Immutable preflight result. No measurement request is needed to construct it. */
    public static final class Plan {
        public final List<Window> windows;
        public final int known,deferred,needed;
        private Plan(List<Window> windows,int known,int deferred,int needed){
            this.windows=Collections.unmodifiableList(windows);
            this.known=known;this.deferred=deferred;this.needed=needed;
        }
    }

    public static Plan plan(ArchiveCodec.Level level,ArchiveCodec.Clock clock,Availability local){
        local.window(level.period,clock.millis(level.oldest),clock.millis(level.newest));
        List<Window> windows=new ArrayList<>();int known=0,deferred=0,needed=0,count=0;long start=0;
        for(int i=0;i<level.capacity;i++){
            long cursor=level.newest-(long)i*level.period;
            if(cursor<level.oldest)break;
            Long time=clock.millis(cursor);
            boolean missing=local.importable(time)&&!local.known(time,level.period);
            if(missing){if(count==0)start=cursor;count++;needed++;}
            else{
                if(count>0){windows.add(new Window(start,count));count=0;}
                if(local.importable(time))known++;else deferred++;
            }
        }
        if(count>0)windows.add(new Window(start,count));
        return new Plan(windows,known,deferred,needed);
    }

    /** Predict only candidate times from the last device layout, then use the normal local plan.
     * The live recording gets its normal reception interval before a new point is considered missing.
     * A real connection always reads fresh device metadata before requesting measurements. */
    public static List<ArchiveCodec.Level> advance(List<ArchiveCodec.Level> levels,long deviceNow,ArchiveCodec.Clock clock,long before){
        long captured=0;for(ArchiveCodec.Level level:levels)captured=Math.max(captured,level.newest);
        return advance(levels,captured,deviceNow,clock,before);
    }
    public static List<ArchiveCodec.Level> advance(List<ArchiveCodec.Level> levels,long capturedUptime,long deviceNow,ArchiveCodec.Clock clock,long before){
        if(capturedUptime<0||deviceNow<0||deviceNow>ArchiveCodec.NA)throw new IllegalArgumentException("Ungültige Gerätezeit.");
        long elapsed=Math.max(0,deviceNow-capturedUptime);
        List<ArchiveCodec.Level> result=new ArrayList<>();
        for(ArchiveCodec.Level level:levels){
            // Older/coarser tiers must retain their age. Their heads are not
            // current-time samples merely because the app has been reopened.
            long newest=level.newest+elapsed/level.period*level.period;
            result.add(new ArchiveCodec.Level(level.capacity,level.period,newest));
        }
        return through(result,clock,before);
    }

    /** Preserve the original oldest boundary while keeping one fixed end for this run. */
    public static List<ArchiveCodec.Level> through(List<ArchiveCodec.Level> levels,ArchiveCodec.Clock clock,long before){
        List<ArchiveCodec.Level> result=new ArrayList<>();
        for(ArchiveCodec.Level level:levels){
            long newest=level.newest;
            int count=level.capacity;
            while(count>0){Long time=clock.millis(newest);if(time==null||time<=before)break;newest-=level.period;count--;}
            if(count>0)result.add(new ArchiveCodec.Level(count,level.period,newest));
        }
        return result;
    }

    public static boolean needsData(List<ArchiveCodec.Level> levels,ArchiveCodec.Clock clock,Availability local){
        for(ArchiveCodec.Level level:levels)if(plan(level,clock,local).needed>0)return true;
        return false;
    }

    /** Victron rounds the cursor forward, by strictly less than one returned interval. */
    public static boolean matches(Request request,int period,ArchiveCodec.Block block){
        return block.count>0&&block.count<=request.count&&block.period==period&&
                block.time>=request.cursor&&block.time-request.cursor<period;
    }

    /** Ignore late blocks before they can be stored against another outstanding request. */
    public static boolean related(int channel,Request request,int period,byte[] payload){
        if(payload==null||payload.length<8)return false;
        int count=payload[5]&255,returnedPeriod=(int)ArchiveCodec.u(payload,6,2);
        long time=ArchiveCodec.u(payload,1,4);
        if((payload[0]&255)!=channel||count>request.count)return false;
        int window=count==0?period:returnedPeriod;
        return (count==0||returnedPeriod>=period)&&time>=request.cursor&&time-request.cursor<window;
    }

    /** Map a confirmed unavailable tier tail without joining across a device reboot. */
    public static List<long[]> unavailable(long cursor,int count,int period,ArchiveCodec.Clock clock,Availability local){
        List<long[]> ranges=new ArrayList<>();long from=0,to=0;boolean open=false;
        for(int i=0;i<count;i++){
            Long time=clock.millis(cursor-(long)i*period);
            if(!local.importable(time)){if(open)ranges.add(new long[]{from,to});open=false;}
            else if(open&&time==from-period*1000L)from=time;
            else{if(open)ranges.add(new long[]{from,to});from=to=time;open=true;}
        }
        if(open)ranges.add(new long[]{from,to});return ranges;
    }

    /** Skip known samples and stop a request before the next known sample, even inside a batch. */
    public static Request next(long cursor, int remaining, long oldest, int period, int batch,
                               ArchiveCodec.Clock clock, Availability local) {
        if (remaining < 0 || period < 1 || batch < 1 || batch > 56) throw new IllegalArgumentException("Ungültige Archivabfrage.");
        int skipped = 0,deferred=0;
        while (skipped < remaining && cursor >= oldest) {
            Long time=clock.millis(cursor);
            if(local.importable(time)&&!local.known(time,period))break;
            if(!local.importable(time))deferred++;
            skipped++; cursor -= period;
        }
        int count = 0;
        while (count < batch && count < remaining - skipped && cursor - (long) count * period >= oldest &&
                local.importable(clock.millis(cursor-(long)count*period))&&
                !local.known(clock.millis(cursor - (long) count * period), period)) count++;
        return new Request(cursor, skipped, count,deferred);
    }

    /** Bounds include all reboot epochs rather than assuming device time is Unix time. */
    public static long[] bounds(List<ArchiveCodec.Level> levels, ArchiveCodec.Clock clock) {
        long from = Long.MAX_VALUE, to = Long.MIN_VALUE;
        for (ArchiveCodec.Level level : levels) for (int i = 0; i < level.capacity; i++) {
            long cursor = level.newest - (long) i * level.period;
            if (cursor < level.oldest) break;
            Long time = clock.millis(cursor);
            if (time != null) { from = Math.min(from, time); to = Math.max(to, time); }
        }
        return from == Long.MAX_VALUE ? new long[]{0, 0} : new long[]{Math.max(0, from - 90000), to + 90000};
    }

    /** Persist completed responses, including unavailable values, without bridging unknown clock epochs. */
    public static List<long[]> completed(ArchiveCodec.Block block, ArchiveCodec.Clock clock, long now) {
        List<long[]> windows = new ArrayList<>();
        long from = 0, to = 0; boolean open = false;
        for (int i = 0; i < block.count; i++) {
            Long time = clock.millis(block.time - (long) i * block.period);
            if (time == null || time<0 || time>now+300000) {
                if (open) windows.add(new long[]{from, to});
                open = false;
            } else if (open && time == from - block.period * 1000L) from = time;
            else {
                if (open) windows.add(new long[]{from, to});
                from = to = time; open = true;
            }
        }
        if (open) windows.add(new long[]{from, to});
        return windows;
    }
}
