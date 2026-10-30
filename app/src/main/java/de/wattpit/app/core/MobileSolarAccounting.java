package de.wattpit.app.core;

import java.util.*;
import de.wattpit.app.core.SampleTimeline.Interval;
import de.wattpit.app.core.EnergyIntegral.ModelInterval;

/** Display estimates only. Raw measurements and the learning ledger are never modified. */
public final class MobileSolarAccounting {
    private MobileSolarAccounting(){}
    private static final long DAY=86400000L;
    public static final class Result {
        public final List<ModelInterval> extra=new ArrayList<>(),mobile=new ArrayList<>();
        public long missingPriorMillis,abnormalMillis,limitedMillis;
    }
    private static final class Series {
        final List<Interval> values;int index;
        Series(List<Interval> values){this.values=values;}
        Interval at(long a,long b){while(index<values.size()&&values.get(index).to<=a)index++;return index<values.size()&&values.get(index).from<=a&&values.get(index).to>=b?values.get(index):null;}
    }
    private static final class PriorSample {
        final long from,to;final double watts,volts;final String site;
        PriorSample(long a,long b,double watts,double volts,String site){from=a;to=b;this.watts=watts;this.volts=volts;this.site=site;}
    }
    private static final class Prior {double watts=Double.NaN,volts=Double.NaN;}
    private static double quantile(List<double[]> values,double fraction){
        values.sort(Comparator.comparingDouble(x->x[0]));double total=0,at=0;for(double[] x:values)total+=x[1];
        for(double[] x:values){at+=x[1];if(at>=total*fraction)return x[0];}return Double.NaN;
    }
    private static Prior prior(List<PriorSample> samples,long at,String site){
        // Use prior days only: later data cannot change an earlier displayed estimate.
        long before=at/DAY*DAY;List<double[]> loads=new ArrayList<>(),volts=new ArrayList<>();long duration=0;
        for(PriorSample x:samples)if(x.to<=before&&x.to>before-3*DAY&&x.site.equals(site)){
            long n=x.to-Math.max(x.from,before-3*DAY);duration+=n;loads.add(new double[]{x.watts,n});if(Double.isFinite(x.volts))volts.add(new double[]{x.volts,n});
        }
        Prior p=new Prior();if(duration>=1800000L){p.watts=quantile(loads,.25);if(!volts.isEmpty())p.volts=quantile(volts,.5);}return p;
    }
    private static void cuts(TreeSet<Long> cuts,List<Interval> values,long from,long to){for(Interval x:values){if(x.from>from&&x.from<to)cuts.add(x.from);if(x.to>from&&x.to<to)cuts.add(x.to);}}
    private static List<Interval> intervals(List<ModelInterval> values){List<Interval> out=new ArrayList<>();for(ModelInterval x:values)out.add(x.interval);return out;}
    public static Result adjust(long from,long to,List<Interval> solar,List<Interval> battery,List<Interval> states,List<Interval> voltage,List<Interval> soc,List<ModelInterval> extra,List<ModelInterval> mobile,List<Interval> roller){
        Result result=new Result();if(to<=from)return result;
        long earliest=from-4*DAY;TreeSet<Long> cuts=new TreeSet<>();cuts.add(from);cuts.add(to);
        for(List<Interval> x:Arrays.asList(solar,battery,states,voltage,soc,intervals(extra),intervals(mobile),roller))cuts(cuts,x,earliest,to);
        Series sun=new Series(solar),net=new Series(battery),phase=new Series(states),volts=new Series(voltage),charge=new Series(soc),special=new Series(roller);
        List<PriorSample> samples=new ArrayList<>();Map<String,Prior> priors=new HashMap<>();int ei=0,mi=0;Long last=null;
        for(long end:cuts){if(last==null){last=end;continue;}long start=last;last=end;
            while(ei<extra.size()&&extra.get(ei).interval.to<=start)ei++;while(mi<mobile.size()&&mobile.get(mi).interval.to<=start)mi++;
            if(ei>=extra.size()||mi>=mobile.size())continue;ModelInterval e=extra.get(ei),m=mobile.get(mi);
            if(e.interval.from>start||e.interval.to<end||m.interval.from>start||m.interval.to<end)continue;
            Interval s=sun.at(start,end),b=net.at(start,end),v=volts.at(start,end),state=phase.at(start,end),c=charge.at(start,end),r=special.at(start,end);
            double specialA=r==null?0:r.value(start),specialB=r==null?0:r.value(end);
            // Only independently observed load, with no mobile production or during confirmed night.
            if(s!=null&&b!=null&&(!e.estimated||e.night)&&Math.max(e.interval.value(start),e.interval.value(end))==0&&specialA==0&&specialB==0){
                double load=(s.value(start)+s.value(end)-b.value(start)-b.value(end))/2;
                if(load>=0&&Double.isFinite(load))samples.add(new PriorSample(start,end,load,v==null?Double.NaN:(v.value(start)+v.value(end))/2,e.site));
            }
            if(end<=from)continue;long a=Math.max(start,from);if(end<=a)continue;
            if(!e.estimated||e.night){result.extra.add(e.slice(a,end));result.mobile.add(m.slice(a,end));continue;}
            if(s==null||b==null)continue;
            double sa=s.value(a),sb=s.value(end),ba=b.value(a),bb=b.value(end),ea=e.interval.value(a),eb=e.interval.value(end);
            double st=state==null?Double.NaN:state.value(a),stEnd=state==null?Double.NaN:state.value(end);
            boolean limited=st==4||st==5||st==6||st==7||st==247||st==252||stEnd==4||stEnd==5||stEnd==6||stEnd==7||stEnd==247||stEnd==252||c!=null&&Math.max(c.value(a),c.value(end))>=99.5;
            boolean negative=sa+ea-ba<0||sb+eb-bb<0;
            String priorKey=(a/DAY)+":"+e.site;Prior p=priors.get(priorKey);if(p==null){p=prior(samples,a,e.site);priors.put(priorKey,p);}
            negative|=Double.isFinite(p.watts)&&(specialA>0&&sa+ea-ba<p.watts+specialA||specialB>0&&sb+eb-bb<p.watts+specialB);
            boolean abnormal=Double.isFinite(p.volts)&&v!=null&&Math.max(v.value(a),v.value(end))>p.volts*1.2;
            if(abnormal||st==2||st==245||stEnd==2||stEnd==245){result.abnormalMillis+=end-a;continue;}
            if(!limited&&!negative){result.extra.add(e.slice(a,end));result.mobile.add(m.slice(a,end));continue;}
            if(limited)result.limitedMillis+=end-a;
            // A measured balance alone cannot separate devices from an unmetered source.
            // Inference therefore needs an independent prior, plausible capacity and a sole mobile source.
            if(!Double.isFinite(p.watts)||p.watts<=.5||!Double.isFinite(e.ratedWatts)||e.ratedWatts<=0||e.ratedWatts!=m.ratedWatts){
                if(limited){result.missingPriorMillis+=end-a;continue;}
                result.extra.add(e.slice(a,end));result.mobile.add(m.slice(a,end));continue;
            }
            double ma=ba-sa+p.watts+specialA,mb=bb-sb+p.watts+specialB;
            // Enough measured fixed production can cover the assumed load by itself.
            ma=Math.max(0,ma);mb=Math.max(0,mb);
            if(Math.max(ma,mb)>e.ratedWatts){result.abnormalMillis+=end-a;continue;}
            int basis=ModelInterval.BALANCED|(limited?ModelInterval.LIMITED:0);
            result.extra.add(new ModelInterval(new Interval(a,end,ma,mb),true,e.ratedWatts,false,e.site,basis));
            result.mobile.add(new ModelInterval(new Interval(a,end,ma,mb),true,m.ratedWatts,false,m.site,basis));
        }
        return result;
    }
}
