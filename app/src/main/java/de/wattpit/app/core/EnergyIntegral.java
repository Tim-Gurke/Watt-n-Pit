package de.wattpit.app.core;

import java.util.*;
import de.wattpit.app.core.SampleTimeline.Interval;

/** Integrates supported samples; bounded live interpolation stays explicitly labelled. */
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
        public long interpolatedMillis;
        public long modelledMillis,inconsistentMillis,adjustedMillis,limitedMillis;
        public final List<Point> points=new ArrayList<>();
        public double coverage(long from,long to){return to>from?Math.min(1,coveredMillis/(double)(to-from)):0;}
        public double measuredCoverage(long from,long to){return to>from?Math.min(1,(coveredMillis-interpolatedMillis)/(double)(to-from)):0;}
    }
    public static final class Flow {
        public final Result solar,consumption,balance,mobile;
        public final long solarFrom,solarTo;
        Flow(Result solar,Result consumption,Result balance,Result mobile,long from,long to){this.solar=solar;this.consumption=consumption;this.balance=balance;this.mobile=mobile;solarFrom=from;solarTo=to;}
    }
    /** A known off state is measured absence of an assigned source, not a weather estimate. */
    public static final class ModelInterval {
        public static final int BALANCED=1,LIMITED=2;
        public final Interval interval;public final boolean estimated,night;
        public final double ratedWatts;public final String site;public final int basis;
        public ModelInterval(long from,long to,double watts,boolean estimated){this(new Interval(from,to,watts,watts),estimated,Double.NaN,false,"",0);}
        public ModelInterval(Interval interval,boolean estimated,double ratedWatts,boolean night,String site,int basis){this.interval=interval;this.estimated=estimated;this.ratedWatts=ratedWatts;this.night=night;this.site=site;this.basis=basis;}
        public ModelInterval slice(long from,long to){return new ModelInterval(new Interval(from,to,interval.value(from),interval.value(to)),estimated,ratedWatts,night,site,basis);}
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
        return flowIntervals(observed,b,from,to,solarFrom,solarTo,null,null);
    }
    /** Display accounting only. Learning continues to consume the original signed residual. */
    public static Flow flowIntervals(List<Interval> observed,List<Interval> b,long from,long to,long solarFrom,long solarTo,List<ModelInterval> extra,List<ModelInterval> mobile){
        solarFrom=Math.max(from,Math.min(to,solarFrom));solarTo=Math.max(solarFrom,Math.min(to,solarTo));
        List<Interval> s=new ArrayList<>();
        for(Interval x:observed)if(x.a>0||x.b>0){solarFrom=Math.min(solarFrom,x.from);solarTo=Math.max(solarTo,x.to);}
        if(solarFrom>from)s.add(new Interval(from,solarFrom,0,0));
        for(Interval x:observed){long start=Math.max(solarFrom,x.from),end=Math.min(solarTo,x.to);if(end>start)s.add(new Interval(start,end,x.value(start),x.value(end),x.interpolated));}
        if(solarTo<to)s.add(new Interval(solarTo,to,0,0));
        List<Interval> generation=new ArrayList<>(),loads=new ArrayList<>(),net=new ArrayList<>();
        int i=0,j=0;
        while(i<s.size()&&j<b.size()){
            Interval x=s.get(i),y=b.get(j);long start=Math.max(x.from,y.from),end=Math.min(x.to,y.to);
            if(end>start){
                double sa=x.value(start),sb=x.value(end),ba=y.value(start),bb=y.value(end);
                long gs=Math.max(start,solarFrom),ge=Math.min(end,solarTo);
                boolean estimated=x.interpolated||y.interpolated;
                if(ge>gs)generation.add(new Interval(gs,ge,x.value(gs),x.value(ge),estimated));
                // Keep signed residuals: an unmetered charger can make calculated consumption negative.
                loads.add(new Interval(start,end,sa-ba,sb-bb,estimated));net.add(new Interval(start,end,ba,bb,estimated));
            }
            if(x.to<=y.to)i++;if(y.to<=x.to)j++;
        }
        Result yield=integrate(generation,solarFrom,solarTo,false,true);
        if(solarFrom==solarTo)yield.wh=0.0;
        Result mobileEnergy=new Result();
        if(mobile!=null){List<Interval> m=new ArrayList<>();long modelled=0,adjusted=0,limited=0;for(ModelInterval x:mobile){m.add(x.interval);long duration=x.interval.to-x.interval.from;if(x.estimated)modelled+=duration;if((x.basis&ModelInterval.BALANCED)!=0)adjusted+=duration;if((x.basis&ModelInterval.LIMITED)!=0)limited+=duration;}
            mobileEnergy=integrate(m,from,to,false,true);mobileEnergy.modelledMillis=modelled;mobileEnergy.adjustedMillis=adjusted;mobileEnergy.limitedMillis=limited;}
        return new Flow(yield,extra==null?integrate(loads,from,to,false,true):devices(loads,extra,from,to),integrate(net,from,to,false,true),mobileEnergy,solarFrom,solarTo);
    }
    private static Result devices(List<Interval> loads,List<ModelInterval> model,long from,long to){
        List<Interval> values=new ArrayList<>();int i=0,j=0;long estimated=0,inconsistent=0,adjusted=0,limited=0;
        while(i<loads.size()&&j<model.size()){
            Interval a=loads.get(i),b=model.get(j).interval;long start=Math.max(a.from,b.from),end=Math.min(a.to,b.to);
            if(end>start){double x=a.value(start)+b.value(start),y=a.value(end)+b.value(end);
                // Retain the supported positive portion; a negative portion remains a real gap.
                long validFrom=start,validTo=end;
                if(x<0&&y<=0||y<0&&x<=0){inconsistent+=end-start;validTo=start;}
                else if(x<0||y<0){long zero=start+Math.round((end-start)*(-x)/(y-x));if(x<0){inconsistent+=zero-start;validFrom=zero;x=0;}else{inconsistent+=end-zero;validTo=zero;y=0;}}
                if(validTo>validFrom){long duration=validTo-validFrom;values.add(new Interval(validFrom,validTo,x,y,a.interpolated));ModelInterval basis=model.get(j);if(basis.estimated)estimated+=duration;if((basis.basis&ModelInterval.BALANCED)!=0)adjusted+=duration;if((basis.basis&ModelInterval.LIMITED)!=0)limited+=duration;}
            }
            if(a.to<=b.to)i++;if(b.to<=a.to)j++;
        }
        Result result=integrate(values,from,to,false,true);result.modelledMillis=estimated;result.inconsistentMillis=inconsistent;result.adjustedMillis=adjusted;result.limitedMillis=limited;return result;
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
        return powerIntervals(power,voltage,current,from,to,true);
    }
    public static List<Interval> powerIntervals(List<Sample> power,List<Sample> voltage,List<Sample> current,long from,long to,boolean interpolate){
        Map<String,List<Sample>> volts=new LinkedHashMap<>(),amps=new LinkedHashMap<>();
        for(Sample s:voltage)volts.computeIfAbsent(s.device,k->new ArrayList<>()).add(s);
        for(Sample s:current)amps.computeIfAbsent(s.device,k->new ArrayList<>()).add(s);
        List<Interval> derived=new ArrayList<>();
        for(String device:volts.keySet()){
            List<Interval> v=SampleTimeline.energyIntervals(volts.get(device),from,to,false,interpolate),c=SampleTimeline.energyIntervals(amps.getOrDefault(device,Collections.emptyList()),from,to,true,interpolate),part=new ArrayList<>();
            int i=0,j=0;while(i<v.size()&&j<c.size()){
                Interval a=v.get(i),b=c.get(j);long start=Math.max(a.from,b.from),end=Math.min(a.to,b.to);
                if(end>start)part.add(new Interval(start,end,a.value(start)*b.value(start),a.value(end)*b.value(end),a.interpolated||b.interpolated));
                if(a.to<=b.to)i++;if(b.to<=a.to)j++;
            }
            derived=SampleTimeline.prefer(derived,part);
        }
        return SampleTimeline.prefer(SampleTimeline.energyIntervals(power,from,to,true,interpolate),derived);
    }
    public static Result calculateIntervals(List<Interval> intervals,long from,long to,boolean discharge,boolean balance){return integrate(intervals,from,to,discharge,balance);}
    private static List<Interval> intervals(List<Sample> samples,long from,long to,boolean signed){
        return SampleTimeline.energyIntervals(samples,from,to,signed);
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
            result.coveredMillis+=end-time;if(interval.interpolated)result.interpolatedMillis+=end-time;raw.add(new Point(end,energy,false));lastEnd=end;
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
