package de.wattpit.app.core;
import java.util.*;

/** Feeds the real Java plot/energy results to the WebView rendering regression test. */
public final class SyncCoverageFixture {
    private static final long FROM=1792473600000L,TO=FROM+120000;
    private static String point(long time,double value,boolean gap,String kind,String origin){return "{\"t\":"+time+",\"last\":"+time+",\"v\":"+value+",\"min\":"+value+",\"max\":"+value+",\"n\":1,\"period\":0,\"device\":\""+(origin.equals("calculated")?"calculated-"+kind:"A")+"\",\"kind\":\""+kind+"\",\"origin\":\""+origin+"\",\"gap\":"+gap+"}";}
    private static String energy(EnergyIntegral.Result result,String kind){List<String> points=new ArrayList<>();for(EnergyIntegral.Point p:result.points)points.add(point(p.time,p.wh,p.gap,kind,"calculated"));return "{\"points\":["+String.join(",",points)+"],\"summary\":{\"from\":"+FROM+",\"to\":"+TO+",\"last\":"+result.wh+",\"coverage\":"+result.coverage(FROM,TO)+",\"measuredCoverage\":"+result.measuredCoverage(FROM,TO)+",\"interpolatedMillis\":"+result.interpolatedMillis+"}}";}
    private static String scenario(int mode){
        List<EnergyIntegral.Sample> samples=new ArrayList<>();
        for(int time=0;time<=120;time+=10)if(mode==0?time<=20||time>=100:mode==1?time<20||time>40:time!=20)samples.add(new EnergyIntegral.Sample(FROM+time*1000,60,10,"A","live"));
        if(mode==0)for(int time=30;time<=90;time+=30)samples.add(new EnergyIntegral.Sample(FROM+time*1000,60,30,"A","archive"));
        List<String> points=new ArrayList<>();for(SampleTimeline.PlotPoint p:SampleTimeline.plot(samples,FROM,TO))points.add(point(p.time,p.value,p.gap,"solar","merged"));
        List<EnergyIntegral.Sample> battery=Arrays.asList(new EnergyIntegral.Sample(FROM,0,120,"B","archive"),new EnergyIntegral.Sample(TO,0,120,"B","archive"));
        EnergyIntegral.Flow flow=EnergyIntegral.flow(samples,battery,FROM,TO);
        return "{\"power\":{\"points\":["+String.join(",",points)+"],\"summary\":{\"last\":60,\"min\":60,\"max\":60,\"avg\":60}},\"flow\":{\"solar\":"+energy(flow.solar,"solar")+",\"consumption\":"+energy(flow.consumption,"consumption")+",\"balance\":"+energy(flow.balance,"balance")+"}}";
    }
    public static void main(String[] args){System.out.println("{\"from\":"+FROM+",\"to\":"+TO+",\"complete\":"+scenario(0)+",\"gap\":"+scenario(1)+",\"short\":"+scenario(2)+"}");}
}
