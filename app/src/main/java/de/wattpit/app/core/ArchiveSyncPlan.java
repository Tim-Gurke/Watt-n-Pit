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
        boolean contains(long time) {
            Map.Entry<Long, Long> previous = ranges.floorEntry(time);
            return previous != null && previous.getValue() >= time;
        }
    }

    public static final class Availability {
        private final Map<Integer, Intervals> live = new LinkedHashMap<>(), archive = new LinkedHashMap<>();
        private final long now;
        public Availability(Collection<Integer> periods, long now) {
            this.now = now;
            for (int period : periods) {
                if (period < 1) throw new IllegalArgumentException("Ungültige Archivauflösung.");
                live.put(period, new Intervals()); archive.put(period, new Intervals());
            }
        }
        public void reading(long time, String origin, int resolution) {
            for (int period : live.keySet()) {
                long tolerance = Math.min(45000L, Math.max(500L, period * 500L));
                if (origin.equals("live")) live.get(period).add(time - tolerance, time + tolerance);
                else if ((origin.equals("archive") || origin.equals("csv")) && resolution > 0 && resolution <= period) {
                    // Equal-resolution samples cover their own instant; finer samples have the same
                    // bounded matching tolerance as the existing archive/live merge.
                    long near = resolution < period ? tolerance : 0;
                    archive.get(period).add(time - near, time + near);
                } else if (origin.equals("csv") && resolution == 0) archive.get(period).add(time, time);
            }
        }
        public void imported(int period, long from, long to) {
            Intervals intervals = archive.get(period);
            if (intervals != null) intervals.add(from, to);
        }
        public boolean known(Long time, int period) {
            if (time == null || !live.containsKey(period)) return false;
            // Live data are final. The newest two archive intervals can still change on the device.
            return live.get(period).contains(time) ||
                    time < now - 2L * period * 1000 && archive.get(period).contains(time);
        }
        public boolean importable(Long time){return time!=null&&time>=0&&time<=now+300000;}
    }

    public static final class Request {
        public final long cursor;
        public final int skipped, count, deferred;
        Request(long cursor, int skipped, int count,int deferred) { this.cursor = cursor; this.skipped = skipped; this.count = count;this.deferred=deferred; }
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
        return from == Long.MAX_VALUE ? new long[]{0, 0} : new long[]{Math.max(0, from - 45000), to + 45000};
    }

    /** Persist completed responses, including unavailable values, without bridging unknown clock epochs. */
    public static List<long[]> completed(ArchiveCodec.Block block, ArchiveCodec.Clock clock, long now) {
        List<long[]> windows = new ArrayList<>();
        long from = 0, to = 0; boolean open = false;
        for (int i = 0; i < block.count; i++) {
            Long time = clock.millis(block.time - (long) i * block.period);
            if (time == null || time >= now - 2L * block.period * 1000) {
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
