package de.wattpit.app.core;

import java.util.*;

/** Integrates original power samples. Unknown intervals are excluded, never extrapolated. */
public final class EnergyIntegral {
    public static final long LOOKAROUND = 65535L * 1500;
    public static final class Sample {
        public final long time;
        public final double watts;
        public final int period;
        public final String device,origin;
        public Sample(long time,double watts,int period,String device,String origin) {
            this.time=time;this.watts=watts;this.period=period;this.device=device;this.origin=origin;
        }
    }
    public static final class Point {
        public final long time;
        public final double wh;
        public boolean gap;
        Point(long time,double wh,boolean gap){this.time=time;this.wh=wh;this.gap=gap;}
    }
    public static final class Result {
        public Double wh;
        public double minimumWh,maximumWh;
        public long coveredMillis;
        public final List<Point> points=new ArrayList<>();
        public double coverage(long from,long to){return to>from?Math.min(1,coveredMillis/(double)(to-from)):0;}
    }
    private static final class Segment {
        final Sample a,b;
        final long from,to;
        final int id,rank;
        Segment(Sample a,Sample b,long from,long to,int id){this.a=a;this.b=b;this.from=from;this.to=to;this.id=id;rank=a.origin.equals("live")?3:a.origin.equals("archive")?2:1;}
        double value(long t){return a.watts+(b.watts-a.watts)*(t-a.time)/(b.time-a.time);}
    }
    private static final class Event {
        final long time;final Segment segment;final boolean start;
        Event(long time,Segment segment,boolean start){this.time=time;this.segment=segment;this.start=start;}
    }
    private static final class Interval {
        final long from,to;final double a,b;
        Interval(long from,long to,double a,double b){this.from=from;this.to=to;this.a=a;this.b=b;}
        double value(long time){return a+(b-a)*(time-from)/(to-from);}
    }
    public static final class Flow {
        public final Result solar,consumption,balance;
        public final long solarFrom,solarTo;
        Flow(Result solar,Result consumption,Result balance,long from,long to){this.solar=solar;this.consumption=consumption;this.balance=balance;solarFrom=from;solarTo=to;}
    }
    /** All three values use only the intersection of observed Solar and Shunt intervals. */
    public static Flow flow(List<Sample> solar,List<Sample> battery,long from,long to) {
        return flow(solar,battery,from,to,from);
    }
    /** Solar starts at solarFrom; earlier consumption is derived from observed Shunt data alone. */
    public static Flow flow(List<Sample> solar,List<Sample> battery,long from,long to,long solarFrom) {
        return flow(solar,battery,from,to,solarFrom,to);
    }
    /** Sun times bound assumed night-time zeroes; observed production always extends them. */
    public static Flow flow(List<Sample> solar,List<Sample> battery,long from,long to,long solarFrom,long solarTo) {
        if(from<0||to<=from)throw new IllegalArgumentException("Ungültiger Zeitraum.");
        solarFrom=Math.max(from,Math.min(to,solarFrom));solarTo=Math.max(solarFrom,Math.min(to,solarTo));
        List<Interval> observed=intervals(solar,from,to,false),s=new ArrayList<>(),b=intervals(battery,from,to,true);
        for(Interval x:observed)if(x.a>0||x.b>0){solarFrom=Math.min(solarFrom,x.from);solarTo=Math.max(solarTo,x.to);}
        if(solarFrom>from)s.add(new Interval(from,solarFrom,0,0));
        for(Interval x:observed){long start=Math.max(solarFrom,x.from),end=Math.min(solarTo,x.to);if(end>start)s.add(new Interval(start,end,x.value(start),x.value(end)));}
        if(solarTo<to)s.add(new Interval(solarTo,to,0,0));
        List<Interval> generation=new ArrayList<>(),loads=new ArrayList<>(),net=new ArrayList<>();
        int i=0,j=0;
        while(i<s.size()&&j<b.size()){
            Interval x=s.get(i),y=b.get(j);long start=Math.max(x.from,y.from),end=Math.min(x.to,y.to);
            if(end>start){
                double sa=x.value(start),sb=x.value(end),ba=y.value(start),bb=y.value(end);
                long gs=Math.max(start,solarFrom),ge=Math.min(end,solarTo);
                if(ge>gs)generation.add(new Interval(gs,ge,x.value(gs),x.value(ge)));
                // Keep signed residuals: an unmetered charger can make calculated consumption negative.
                loads.add(new Interval(start,end,sa-ba,sb-bb));net.add(new Interval(start,end,ba,bb));
            }
            if(x.to<=y.to)i++;if(y.to<=x.to)j++;
        }
        Result yield=integrate(generation,solarFrom,solarTo,false,true);
        if(solarFrom==solarTo)yield.wh=0.0;
        return new Flow(yield,integrate(loads,from,to,false,true),integrate(net,from,to,false,true),solarFrom,solarTo);
    }
    /** One configured device per kind. Overlapping sources/archives contribute once. */
    public static Result calculate(List<Sample> samples,long from,long to,boolean discharge) {
        return calculate(samples,from,to,discharge,false);
    }
    /** Signed energy at the Shunt: charging minus discharging, without a solar input. */
    public static Result balance(List<Sample> samples,long from,long to) {
        return calculate(samples,from,to,false,true);
    }
    private static Result calculate(List<Sample> samples,long from,long to,boolean discharge,boolean balance) {
        return integrate(intervals(samples,from,to,discharge||balance),from,to,discharge,balance);
    }
    private static List<Interval> intervals(List<Sample> samples,long from,long to,boolean signed) {
        if(from<0||to<=from)throw new IllegalArgumentException("Ungültiger Zeitraum.");
        List<Sample> sorted=new ArrayList<>(samples);sorted.sort(Comparator.comparingLong(s->s.time));
        Map<String,Sample> previous=new HashMap<>();List<Event> events=new ArrayList<>();int id=0;
        for(Sample s:sorted){
            boolean live=s.origin.equals("live");
            String key=s.device+"\n"+s.origin+"\n"+(live?0:s.period);Sample a=previous.put(key,s);
            if(a==null||!Double.isFinite(a.watts)||!Double.isFinite(s.watts)||(!signed&&(a.watts<0||s.watts<0)))continue;
            long dt=s.time-a.time,limit=s.period>0?s.period*1500L:90000;
            if(live&&a.period>0)limit=Math.min(limit,a.period*1500L);
            if(dt<=0||dt>limit)continue;
            long start=Math.max(from,a.time),end=Math.min(to,s.time);if(end<=start)continue;
            Segment segment=new Segment(a,s,start,end,id++);events.add(new Event(start,segment,true));events.add(new Event(end,segment,false));
        }
        events.sort(Comparator.comparingLong(e->e.time));
        TreeSet<Segment> active=new TreeSet<>(Comparator.<Segment>comparingInt(s->s.rank).reversed().thenComparingInt(s->s.a.period).thenComparingInt(s->s.id));
        List<Interval> result=new ArrayList<>();
        for(int i=0;i<events.size();){
            long time=events.get(i).time;
            while(i<events.size()&&events.get(i).time==time){Event e=events.get(i++);if(e.start)active.add(e.segment);else active.remove(e.segment);}
            if(i==events.size()||active.isEmpty())continue;
            long end=events.get(i).time;if(end<=time)continue;
            Segment s=active.first();double a=s.value(time),b=s.value(end);
            result.add(new Interval(time,end,a,b));
        }
        return result;
    }
    private static Result integrate(List<Interval> intervals,long from,long to,boolean discharge,boolean balance) {
        Result result=new Result();List<Point> raw=new ArrayList<>();double energy=0;long lastEnd=-1;
        for(Interval interval:intervals){
            long time=interval.from,end=interval.to;double a=interval.a,b=interval.b;
            raw.add(new Point(time,energy,lastEnd!=time));
            if(balance&&a*b<0){
                long zero=time+Math.round((end-time)*(-a)/(b-a));
                if(zero>time&&zero<end)raw.add(new Point(zero,energy+a*(zero-time)/7200000.0,false));
            }
            energy+=(balance?(a+b)/2:positiveArea(discharge?-a:a,discharge?-b:b))*(end-time)/3600000.0;
            result.coveredMillis+=end-time;raw.add(new Point(end,energy,false));lastEnd=end;
        }
        if(result.coveredMillis==0)return result;
        result.wh=energy;
        // Bound bridge payloads while retaining endpoints and explicit breaks in each bucket.
        long width=Math.max(1,(to-from)/600);Point first=null,last=null,low=null,high=null;long bucket=-1;boolean gap=false;
        for(Point p:raw){long next=(p.time-from)/width;
            result.minimumWh=Math.min(result.minimumWh,p.wh);result.maximumWh=Math.max(result.maximumWh,p.wh);
            if(first!=null&&next!=bucket){append(result.points,first,last,low,high,gap);first=null;gap=false;}
            if(first==null){first=p;low=high=p;bucket=next;}else if(p.gap&&p.time!=first.time)gap=true;
            if(p.wh<low.wh)low=p;if(p.wh>high.wh)high=p;
            last=p;
        }
        if(first!=null)append(result.points,first,last,low,high,gap);
        return result;
    }
    private static void append(List<Point> out,Point first,Point last,Point low,Point high,boolean gap){
        List<Point> selected=new ArrayList<>(Arrays.asList(first,low,high,last));selected.sort(Comparator.comparingLong(p->p.time));
        long previous=-1;for(Point p:selected)if(p.time!=previous){if(p!=first)p.gap|=gap;out.add(p);previous=p.time;}
    }
    private static double positiveArea(double a,double b){
        if(a>=0&&b>=0)return (a+b)/2;
        if(a<=0&&b<=0)return 0;
        return a>0?a*a/(2*(a-b)):b*b/(2*(b-a));
    }
    private EnergyIntegral(){}
}
