package de.wattpit.app.core;

import java.sql.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;

/** Observes unmodified production planning against persistent SQLite. No Android/APK build. */
public final class ArchiveDeltaProof {
    private static final long EPOCH=1700000000000L;
    private static final int[] PERIODS={1,30,300,1800};
    private static final int[] CAPACITIES={121,241,289,1441};
    private static ArchiveCodec.Clock clock(){ArchiveCodec.Clock c=new ArchiveCodec.Clock();byte[] b=new byte[8];System.arraycopy(ArchiveCodec.le(EPOCH/1000,4),0,b,4,4);c.anchors(b);return c;}
    private static int reg(int ch){return ch==0?0xec8a:ch==1?0xed8d:ch==2?0xed8c:0xec88;}
    private static String device(int ch){return ch==0||ch==3?"solar-A":"shunt-B";}
    private static String kind(int ch){return ch==0||ch==3?"solar":"shunt";}
    private static long raw(int ch){return ch==0?60:ch==1?1200:ch==2?-5000:127;}
    private static ArchiveCodec.Field field(int ch){return ArchiveCodec.field(reg(ch),-1);}
    private static List<ArchiveCodec.Level> levels(long end){List<ArchiveCodec.Level> out=new ArrayList<>();for(int i=0;i<PERIODS.length;i++)out.add(new ArchiveCodec.Level(CAPACITIES[i],PERIODS[i],end-end%PERIODS[i]));return out;}
    private static void bind(PreparedStatement q,String[] args)throws Exception{for(int i=0;i<args.length;i++)q.setString(i+1,args[i]);}
    private static ArchiveSyncPlan.Availability load(Connection db,int ch,long end,long now,boolean refresh)throws Exception{
        ArchiveSyncPlan.Availability local=new ArchiveSyncPlan.Availability(Arrays.asList(1,30,300,1800),now,refresh);
        long[] bounds=ArchiveSyncPlan.bounds(levels(end),clock());
        try(PreparedStatement q=db.prepareStatement(ArchiveSyncPlan.readingsSql(field(ch).name))){bind(q,new String[]{device(ch),kind(ch),""+bounds[0],""+bounds[1]});try(ResultSet r=q.executeQuery()){while(r.next())local.reading(r.getLong(1),r.getString(2),r.getInt(3));}}
        try(PreparedStatement q=db.prepareStatement("SELECT period,start,end FROM archive_coverage WHERE device=? AND channel=? AND start<=? AND end>=?")){bind(q,new String[]{device(ch),""+reg(ch),""+bounds[1],""+bounds[0]});try(ResultSet r=q.executeQuery()){while(r.next())local.imported(r.getInt(1),r.getLong(2),r.getLong(3));}}
        return local;
    }
    private static void cover(Connection db,int ch,int period,long from,long to)throws Exception{
        String where="device=? AND channel=? AND period=? AND start<=? AND end>=?";
        String[] args={device(ch),""+reg(ch),""+period,""+(to+period*1000L),""+(from-period*1000L)};
        try(PreparedStatement q=db.prepareStatement("SELECT MIN(start),MAX(end) FROM archive_coverage WHERE "+where)){bind(q,args);try(ResultSet r=q.executeQuery()){if(r.next()&&r.getObject(1)!=null){from=Math.min(from,r.getLong(1));to=Math.max(to,r.getLong(2));}}}
        try(PreparedStatement q=db.prepareStatement("DELETE FROM archive_coverage WHERE "+where)){bind(q,args);q.executeUpdate();}
        try(PreparedStatement q=db.prepareStatement("INSERT INTO archive_coverage(device,channel,period,start,end) VALUES(?,?,?,?,?)")){bind(q,new String[]{device(ch),""+reg(ch),""+period,""+from,""+to});q.executeUpdate();}
    }
    private static void sample(Connection db,int ch,long ts,int period,String origin,double value)throws Exception{
        String name=field(ch).name;
        try(PreparedStatement q=db.prepareStatement("INSERT INTO readings(ts,device,kind,origin,period,"+name+") VALUES(?,?,?,?,?,?) ON CONFLICT(ts,device,origin,period) DO UPDATE SET "+name+"=excluded."+name)){
            q.setLong(1,ts);q.setString(2,device(ch));q.setString(3,kind(ch));q.setString(4,origin);q.setInt(5,period);q.setDouble(6,value);q.executeUpdate();
        }
    }
    private static ArchiveCodec.Block block(ArchiveTransferPlan.Read read){
        int ch=read.channel,n=read.request.count;ArchiveCodec.Field f=field(ch);byte[] data=new byte[8+n*f.width];data[0]=(byte)ch;
        System.arraycopy(ArchiveCodec.le(read.request.cursor,4),0,data,1,4);data[5]=(byte)n;System.arraycopy(ArchiveCodec.le(read.period,2),0,data,6,2);
        for(int i=0;i<n;i++)System.arraycopy(ArchiveCodec.le(raw(ch),f.width),0,data,8+i*f.width,f.width);
        // Round-trip the actual outgoing EC5B request too.
        byte[] command=ArchiveCodec.trendQuery(ch,read.request.cursor,n);
        if((command[command.length-1]&255)!=n)throw new AssertionError("Wrong wire request");
        return new ArchiveCodec.Block(data,f,ch,n);
    }
    private static List<EnergyIntegral.Sample> samples(Connection db,int ch)throws Exception{
        List<EnergyIntegral.Sample> out=new ArrayList<>();try(PreparedStatement q=db.prepareStatement("SELECT ts,"+field(ch).name+",period,origin FROM readings WHERE device=? AND "+field(ch).name+" IS NOT NULL ORDER BY ts")){q.setString(1,device(ch));try(ResultSet r=q.executeQuery()){while(r.next())out.add(new EnergyIntegral.Sample(r.getLong(1),r.getDouble(2),r.getInt(3),device(ch),r.getString(4)));}}return out;
    }
    public static void main(String[] args)throws Exception{
        Path path=Path.of(args[0]),tracePath=Path.of(args[0]+".requests.tsv");long end=Long.parseLong(args[1]),now=EPOCH+Long.parseLong(args[2])*1000;int packetLimit=Integer.parseInt(args[3]);boolean withNA=Boolean.parseBoolean(args[4]),seedLive=Boolean.parseBoolean(args[5]);
        Class.forName("org.sqlite.JDBC");int channels=withNA?4:3;
        try(Connection db=DriverManager.getConnection("jdbc:sqlite:"+path)){
            try(Statement s=db.createStatement()){s.execute(HistorySchema.CREATE.replace("CREATE TABLE ","CREATE TABLE IF NOT EXISTS "));s.execute(HistorySchema.COVERAGE.replace("CREATE TABLE ","CREATE TABLE IF NOT EXISTS "));s.execute("CREATE TABLE IF NOT EXISTS proof_received(channel INTEGER,period INTEGER,ts INTEGER,PRIMARY KEY(channel,period,ts))");}
            if(seedLive){db.setAutoCommit(false);for(int ch=0;ch<3;ch++)for(long t=0;t<=end;t+=60)sample(db,ch,EPOCH+t*1000,60,"live",raw(ch)/field(ch).divisor);db.commit();db.setAutoCommit(true);}
            Map<Integer,ArchiveSyncPlan.Availability> local=new HashMap<>();Map<Integer,Integer>batches=new HashMap<>();ArchiveTransferPlan plan=new ArchiveTransferPlan(clock(),local,batches);
            for(int ch=0;ch<channels;ch++){local.put(ch,load(db,ch,end,now,true));batches.put(ch,ch==2?14:28);}
            for(ArchiveCodec.Level level:levels(end))for(int ch=0;ch<channels;ch++)plan.add(ch,level);
            int neededAtStart=plan.remaining(),requests=0,points=0,repeated=0,na=0;long oldest=Long.MAX_VALUE,newest=Long.MIN_VALUE;
            Map<Integer,List<long[]>> confirmed=new HashMap<>();
            try(BufferedWriter trace=Files.newBufferedWriter(tracePath)){
                trace.write("channel\tperiod\tdevice_time\tpoints\trepeated\n");
                while(packetLimit<0||requests<packetLimit){
                    ArchiveTransferPlan.Read read=plan.next(ch->{});if(read==null)break;
                    ArchiveCodec.Block b=block(read);int duplicates=0;db.setAutoCommit(false);
                    try{
                        for(int i=0;i<b.count;i++){
                            long t=b.time-(long)i*b.period,ts=clock().millis(t);oldest=Math.min(oldest,t);newest=Math.max(newest,t);
                            try(PreparedStatement q=db.prepareStatement("INSERT OR IGNORE INTO proof_received(channel,period,ts) VALUES(?,?,?)")){q.setInt(1,b.channel);q.setInt(2,b.period);q.setLong(3,ts);if(q.executeUpdate()==0)duplicates++;}
                            Double value=b.values[i];if(value==null){na++;continue;}
                            ArchiveMerge merge=ArchiveMerge.existing(field(b.channel).name,device(b.channel),kind(b.channel),ts,b.period,value);
                            boolean exists;try(PreparedStatement q=db.prepareStatement(merge.sql)){bind(q,merge.args);try(ResultSet r=q.executeQuery()){exists=r.next();}}
                            if(!exists)sample(db,b.channel,ts,b.period,"archive",value);
                        }
                        long receiptTime=now+(requests+1)*1000L;
                        for(long[] r:ArchiveSyncPlan.completed(b,clock(),receiptTime)){cover(db,b.channel,b.period,r[0],r[1]);local.get(b.channel).imported(b.period,r[0],r[1]);}
                        db.commit();
                    }catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
                    for(long[] r:ArchiveSyncPlan.completed(b,clock(),now+(requests+1)*1000L,false))confirmed.computeIfAbsent(b.channel,k->new ArrayList<>()).add(new long[]{b.period,r[0],r[1]});
                    if(!plan.accept(b))throw new AssertionError("Fixture did not match planned response");
                    requests++;points+=b.count;repeated+=duplicates;trace.write(b.channel+"\t"+b.period+"\t"+b.time+"\t"+b.count+"\t"+duplicates+"\n");
                    if(requests>10000)throw new AssertionError("No progress");
                }
            }
            int auditMissing=0;for(int ch=0;ch<channels;ch++){
                ArchiveSyncPlan.Availability a=load(db,ch,end,now,false);for(long[] r:confirmed.getOrDefault(ch,Collections.emptyList()))a.imported((int)r[0],r[1],r[2]);for(ArchiveCodec.Level level:levels(end))auditMissing+=ArchiveSyncPlan.plan(level,clock(),a).needed;
            }
            EnergyIntegral.Flow flow=EnergyIntegral.flowIntervals(SampleTimeline.intervals(samples(db,0),EPOCH,EPOCH+end*1000,false),EnergyIntegral.powerIntervals(Collections.emptyList(),samples(db,1),samples(db,2),EPOCH,EPOCH+end*1000),EPOCH,EPOCH+end*1000,EPOCH,EPOCH+end*1000);
            long totalRows;try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM readings")){r.next();totalRows=r.getLong(1);}
            System.out.println("{\"planned\":"+neededAtStart+",\"requests\":"+requests+",\"elapsed_seconds\":"+requests+",\"points\":"+points+",\"repeated\":"+repeated+",\"unavailable\":"+na+",\"queue_remaining\":"+plan.remaining()+",\"audit_missing\":"+auditMissing+",\"rows\":"+totalRows+",\"oldest_requested\":"+(points==0?"null":oldest)+",\"newest_requested\":"+(points==0?"null":newest)+",\"solar_coverage\":"+flow.solar.coverage(EPOCH,EPOCH+end*1000)+",\"load_coverage\":"+flow.consumption.coverage(EPOCH,EPOCH+end*1000)+",\"automatic_due_after_five_minutes\":"+ArchiveSchedule.automaticDue(ArchiveSchedule.nextAutomatic(now,false),now+300001,ArchiveSchedule.REPAIR_REVISION)+"}");
        }
    }
}
