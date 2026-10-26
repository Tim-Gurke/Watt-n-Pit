package de.wattpit.app.core;

import java.util.*;

/** One definition of supported sample intervals for plots, energy and archive coverage. */
public final class SampleTimeline {
    private SampleTimeline() { }
    public static long limit(String origin,int period){return period>0?period*1500L:90000L;}
    public static long liveLimit(int a,int b){return Math.min(limit("live",a),limit("live",b));}
    public static class Sample {
        public final long time;public final double value;public final int period;public final String device,origin;
        public Sample(long time,double value,int period,String device,String origin){this.time=time;this.value=value;this.period=period;this.device=device;this.origin=origin;}
    }
    public static final class Interval {
        public final long from,to;public final double a,b;public final boolean interpolated;
        public Interval(long from,long to,double a,double b){this(from,to,a,b,false);}
        public Interval(long from,long to,double a,double b,boolean interpolated){this.from=from;this.to=to;this.a=a;this.b=b;this.interpolated=interpolated;}
        public double value(long time){return a+(b-a)*(time-from)/(to-from);}
    }
    private static int rank(Sample s){return s.origin.equals("live")?3:s.origin.equals("archive")?2:1;}
    private static boolean valid(Sample s,boolean signed){return Double.isFinite(s.value)&&(signed||s.value>=0);}
    private static boolean preferred(Sample a,Sample b){return rank(a)>rank(b)||rank(a)==rank(b)&&a.period<b.period;}
    private static final class Segment {
        final Sample a,b;final long from,to,resolution;final int id,rank;final boolean boundary,interpolated;
        Segment(Sample a,Sample b,long from,long to,int id,boolean boundary,boolean interpolated){this.a=a;this.b=b;this.from=from;this.to=to;this.id=id;this.boundary=boundary;this.interpolated=interpolated;rank=boundary?0:rank(a);resolution=Math.max(b.time-a.time,Math.max(a.period,b.period)*1000L);}
        double value(long t){return a.value+(b.value-a.value)*(t-a.time)/(b.time-a.time);}
    }
    private static final class Event {
        final long time;final Segment segment;final boolean start;
        Event(long t,Segment s,boolean start){time=t;segment=s;this.start=start;}
    }
    private static void add(List<Event> events,Sample a,Sample b,long from,long to,boolean boundary,boolean interpolated){
        long start=Math.max(from,a.time),end=Math.min(to,b.time);if(end<=start)return;
        Segment s=new Segment(a,b,start,end,events.size(),boundary,interpolated);events.add(new Event(start,s,true));events.add(new Event(end,s,false));
    }
    public static List<Interval> intervals(List<? extends Sample> samples,long from,long to,boolean signed){
        return intervals(samples,from,to,signed,false,false);
    }
    /** Energy prefers finer original data. Short live delays may be explicitly estimated. */
    public static List<Interval> energyIntervals(List<? extends Sample> samples,long from,long to,boolean signed){
        return energyIntervals(samples,from,to,signed,true);
    }
    public static List<Interval> energyIntervals(List<? extends Sample> samples,long from,long to,boolean signed,boolean interpolate){
        return intervals(samples,from,to,signed,true,interpolate);
    }
    private static List<Interval> intervals(List<? extends Sample> samples,long from,long to,boolean signed,boolean quality,boolean interpolate){
        if(from<0||to<=from)throw new IllegalArgumentException("Ungültiger Zeitraum.");
        List<Sample> sorted=new ArrayList<>(samples);sorted.sort(Comparator.comparingLong(s->s.time));
        Map<String,Sample> previous=new HashMap<>();List<Event> events=new ArrayList<>();
        for(Sample s:sorted){
            boolean live=s.origin.equals("live");String key=s.device+"\n"+s.origin+"\n"+(live?0:s.period);Sample a=previous.put(key,s);
            if(a==null||!valid(a,signed)||!valid(s,signed))continue;
            long dt=s.time-a.time,maximum=live?liveLimit(a.period,s.period):limit(s.origin,s.period);
            boolean estimated=dt>maximum;
            long bridge=live&&a.period>0&&s.period>0?Math.max(maximum,Math.min(30000L,Math.min(a.period,s.period)*3000L)):maximum;
            if(dt>0&&(dt<=maximum||interpolate&&dt<=bridge))add(events,a,s,from,to,false,estimated);
        }
        // Only join adjacent, actually observed endpoints at a source/resolution boundary.
        // These joins never replace any supported same-stream interval.
        Map<String,Sample> last=new HashMap<>();
        for(int i=0;i<sorted.size();){
            long time=sorted.get(i).time;Map<String,Sample> at=new LinkedHashMap<>();
            while(i<sorted.size()&&sorted.get(i).time==time){Sample s=sorted.get(i++),old=at.get(s.device);if(old==null||preferred(s,old))at.put(s.device,s);}
            for(Sample s:at.values()){
                Sample a=last.put(s.device,s);if(a==null||!valid(a,signed)||!valid(s,signed))continue;
                boolean same=a.origin.equals(s.origin)&&(a.origin.equals("live")||a.period==s.period);
                if(same)continue;
                long dt=s.time-a.time,maximum=Math.max(limit(a.origin,a.period),limit(s.origin,s.period));
                if(dt>0&&dt<=maximum)add(events,a,s,from,to,true,false);
            }
        }
        events.sort(Comparator.comparingLong(e->e.time));
        Comparator<Segment> order=quality?Comparator.<Segment>comparingInt(s->s.interpolated?2:s.boundary?1:0)
            .thenComparingInt(s->s.a.origin.equals("csv")?1:0).thenComparingLong(s->s.resolution)
            .thenComparing(Comparator.<Segment>comparingInt(s->s.rank).reversed()).thenComparingInt(s->s.id)
            :Comparator.<Segment>comparingInt(s->s.rank).reversed().thenComparingInt(s->s.a.period).thenComparingInt(s->s.id);
        TreeSet<Segment> active=new TreeSet<>(order);
        List<Interval> result=new ArrayList<>();
        for(int i=0;i<events.size();){
            long time=events.get(i).time;
            while(i<events.size()&&events.get(i).time==time){Event e=events.get(i++);if(e.start)active.add(e.segment);else active.remove(e.segment);}
            if(i==events.size()||active.isEmpty())continue;
            long end=events.get(i).time;if(end>time){Segment s=active.first();result.add(new Interval(time,end,s.value(time),s.value(end),s.interpolated));}
        }
        return result;
    }
    /** A secondary series fills only actual holes in the primary interval union. */
    public static List<Interval> prefer(List<Interval> primary,List<Interval> secondary){
        if(primary.stream().anyMatch(x->x.interpolated)||secondary.stream().anyMatch(x->x.interpolated)){
            List<Interval> p=new ArrayList<>(),s=new ArrayList<>(),pe=new ArrayList<>(),se=new ArrayList<>();
            for(Interval x:primary)(x.interpolated?pe:p).add(x);for(Interval x:secondary)(x.interpolated?se:s).add(x);
            return preferPlain(preferPlain(p,s),preferPlain(pe,se));
        }
        return preferPlain(primary,secondary);
    }
    private static List<Interval> preferPlain(List<Interval> primary,List<Interval> secondary){
        List<Interval> out=new ArrayList<>(primary);int index=0;
        for(Interval x:secondary){long cursor=x.from;
            while(index<primary.size()&&primary.get(index).to<=cursor)index++;
            int j=index;
            while(j<primary.size()&&primary.get(j).from<x.to){Interval p=primary.get(j++);
                if(cursor<p.from){long end=Math.min(x.to,p.from);out.add(new Interval(cursor,end,x.value(cursor),x.value(end),x.interpolated));}
                cursor=Math.max(cursor,p.to);if(cursor>=x.to)break;
            }
            if(cursor<x.to)out.add(new Interval(cursor,x.to,x.value(cursor),x.b,x.interpolated));
        }
        out.sort(Comparator.comparingLong(x->x.from));return out;
    }
    public static final class PlotPoint {
        public final long time;public final double value;public final boolean gap;
        PlotPoint(long t,double v,boolean gap){time=t;value=v;this.gap=gap;}
    }
    /** Keep extrema and every supported segment boundary; a small gap cannot erase a whole bucket. */
    public static List<PlotPoint> plot(List<? extends Sample> samples,long from,long to){
        return plot(samples,intervals(samples,from,to,true),from,to);
    }
    public static List<PlotPoint> plot(List<? extends Sample> samples,List<Interval> intervals,long from,long to){
        List<PlotPoint> raw=new ArrayList<>();long end=-1;
        for(Interval x:intervals){raw.add(new PlotPoint(x.from,x.a,end!=x.from));raw.add(new PlotPoint(x.to,x.b,false));end=x.to;}
        int j=0;
        List<Sample> sorted=new ArrayList<>(samples);sorted.sort(Comparator.comparingLong(s->s.time));
        for(Sample s:sorted){
            if(s.time<from||s.time>to||!Double.isFinite(s.value))continue;
            while(j<intervals.size()&&intervals.get(j).to<s.time)j++;
            if(j==intervals.size()||intervals.get(j).from>s.time)raw.add(new PlotPoint(s.time,s.value,true));
        }
        raw.sort(Comparator.comparingLong(p->p.time));
        List<PlotPoint> out=new ArrayList<>(),bucket=new ArrayList<>();long width=Math.max(1,(to-from)/600),slot=-1;
        for(PlotPoint p:raw){long next=(p.time-from)/width;
            if(!bucket.isEmpty()&&(next!=slot||p.gap)){flush(out,bucket);bucket.clear();}
            bucket.add(p);slot=next;
        }
        flush(out,bucket);return out;
    }
    private static void flush(List<PlotPoint> out,List<PlotPoint> bucket){
        if(bucket.isEmpty())return;PlotPoint first=bucket.get(0),last=bucket.get(bucket.size()-1),low=first,high=first;
        for(PlotPoint p:bucket){if(p.value<low.value)low=p;if(p.value>high.value)high=p;}
        List<PlotPoint> selected=new ArrayList<>(Arrays.asList(first,low,high,last));selected.sort(Comparator.comparingLong(p->p.time));
        PlotPoint previous=null;for(PlotPoint p:selected)if(previous!=p){if(out.isEmpty()||out.get(out.size()-1).time!=p.time||Double.compare(out.get(out.size()-1).value,p.value)!=0||p.gap)out.add(p);previous=p;}
    }
}
