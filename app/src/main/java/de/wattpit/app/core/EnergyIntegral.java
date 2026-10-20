package de.wattpit.app.core;

import java.util.*;
import de.wattpit.app.core.SampleTimeline.Interval;

/** Integrates original power samples. Unknown intervals are excluded, never extrapolated. */
public final class EnergyIntegral {
    public static final long LOOKAROUND = 65535L * 1500;
    public static final class Sample extends SampleTimeline.Sample {
        public Sample(long time,double watts,int period,String device,String origin){super(time,watts,period,device,origin);}
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
        return flowIntervals(intervals(solar,from,to,false),intervals(battery,from,to,true),from,to,solarFrom,solarTo);
    }
    public static Flow flowIntervals(List<Interval> observed,List<Interval> b,long from,long to,long solarFrom,long solarTo){
        solarFrom=Math.max(from,Math.min(to,solarFrom));solarTo=Math.max(solarFrom,Math.min(to,solarTo));
        List<Interval> s=new ArrayList<>();
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
    /** Complementary Voltage/Current channels need no extra BLE fetch to derive power. */
    public static List<Interval> powerIntervals(List<Sample> power,List<Sample> voltage,List<Sample> current,long from,long to){
        Map<String,List<Sample>> volts=new LinkedHashMap<>(),amps=new LinkedHashMap<>();
        for(Sample s:voltage)volts.computeIfAbsent(s.device,k->new ArrayList<>()).add(s);
        for(Sample s:current)amps.computeIfAbsent(s.device,k->new ArrayList<>()).add(s);
        List<Interval> derived=new ArrayList<>();
        for(String device:volts.keySet()){
            List<Interval> v=SampleTimeline.intervals(volts.get(device),from,to,false),c=SampleTimeline.intervals(amps.getOrDefault(device,Collections.emptyList()),from,to,true),part=new ArrayList<>();
            int i=0,j=0;while(i<v.size()&&j<c.size()){
                Interval a=v.get(i),b=c.get(j);long start=Math.max(a.from,b.from),end=Math.min(a.to,b.to);
                if(end>start)part.add(new Interval(start,end,a.value(start)*b.value(start),a.value(end)*b.value(end)));
                if(a.to<=b.to)i++;if(b.to<=a.to)j++;
            }
            derived=SampleTimeline.prefer(derived,part);
        }
        return SampleTimeline.prefer(SampleTimeline.intervals(power,from,to,true),derived);
    }
    public static Result calculateIntervals(List<Interval> intervals,long from,long to,boolean discharge,boolean balance){return integrate(intervals,from,to,discharge,balance);}
    private static List<Interval> intervals(List<Sample> samples,long from,long to,boolean signed){
        return SampleTimeline.intervals(samples,from,to,signed);
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
        long width=Math.max(1,(to-from)/600);List<Point> bucket=new ArrayList<>();long slot=-1;
        for(Point p:raw){long next=(p.time-from)/width;
            result.minimumWh=Math.min(result.minimumWh,p.wh);result.maximumWh=Math.max(result.maximumWh,p.wh);
            if(!bucket.isEmpty()&&(next!=slot||p.gap)){append(result.points,bucket);bucket.clear();}
            bucket.add(p);slot=next;
        }
        append(result.points,bucket);return result;
    }
    private static void append(List<Point> out,List<Point> bucket){
        if(bucket.isEmpty())return;Point first=bucket.get(0),last=bucket.get(bucket.size()-1),low=first,high=first;
        for(Point p:bucket){if(p.wh<low.wh)low=p;if(p.wh>high.wh)high=p;}
        List<Point> selected=new ArrayList<>(Arrays.asList(first,low,high,last));selected.sort(Comparator.comparingLong(p->p.time));
        Point previous=null;for(Point p:selected)if(previous!=p){if(out.isEmpty()||out.get(out.size()-1).time!=p.time||p.gap)out.add(p);previous=p;}
    }
    private static double positiveArea(double a,double b){
        if(a>=0&&b>=0)return (a+b)/2;
        if(a<=0&&b<=0)return 0;
        return a>0?a*a/(2*(a-b)):b*b/(2*(b-a));
    }
    private EnergyIntegral(){}
}
