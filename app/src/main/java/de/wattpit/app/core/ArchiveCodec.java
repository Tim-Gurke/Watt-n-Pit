package de.wattpit.app.core;

import java.io.ByteArrayOutputStream;
import java.util.*;

/** Bounded Victron trend and settings reads. No configuration, clock or erase writes. */
public final class ArchiveCodec {
    private ArchiveCodec() { }
    public static final long NA = 0xffffffffL;
    public static long u(byte[] b,int p,int n) {
        if(p<0||p+n>b.length||n<1||n>4)throw new IllegalArgumentException("Unvollständige Archivdaten.");
        long v=0;for(int i=0;i<n;i++)v|=(b[p+i]&255L)<<(8*i);return v;
    }
    public static byte[] le(long value,int n) {byte[] b=new byte[n];for(int i=0;i<n;i++)b[i]=(byte)(value>>(8*i));return b;}
    public static byte[] read(int reg) {
        if(!readable(reg))throw new IllegalArgumentException("Register nicht zum Lesen freigegeben.");
        return new byte[]{5,3,(byte)0x81,0x19,(byte)(reg>>8),(byte)reg};
    }
    private static boolean readable(int r) {return r>=0xec4a&&r<=0xec5f&&r!=0xec5c || r==0xec63||r==0xec64|| r==0xeef8 || r>=0x1050&&r<=0x106e || r>=0x0300&&r<=0x0311 || VictronSettings.readable(r);}
    public static byte[] request(int reg,byte[] payload) {
        if(!(reg==0xec5b&&payload.length==6 || (reg==0xec63||reg==0xec64)&&payload.length==4))
            throw new IllegalArgumentException("Nur Archivabfragen sind freigegeben.");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(6);out.write(3);out.write(0x82);out.write(0x19);out.write(reg>>8);out.write(reg);
        out.write(0x40+payload.length);out.write(payload,0,payload.length);return out.toByteArray();
    }
    public static byte[] trendQuery(int channel,long time,int count) {
        if(channel<0||channel>7||time<0||time>NA||count<1||count>56)throw new IllegalArgumentException("Ungültige Archivabfrage.");
        byte[] b=new byte[6];b[0]=(byte)channel;System.arraycopy(le(time,4),0,b,1,4);b[5]=(byte)count;return request(0xec5b,b);
    }
    public static final class Message {
        public final int code,reg;public final byte[] data;public final long status;
        Message(int c,int r,byte[] d,long s){code=c;reg=r;data=d;status=s;}
    }
    private static final class Incomplete extends RuntimeException {private static final long serialVersionUID=1;}
    private static final class Cursor {
        final byte[] b;int p;
        Cursor(byte[] b,int p){this.b=b;this.p=p;}
        int next(){if(p>=b.length)throw new Incomplete();return b[p++]&255;}
        long size(int initial){int n=initial&31;if(n<24)return n;int width=n==24?1:n==25?2:n==26?4:0;if(width==0)throw new IllegalArgumentException("Unbekanntes Archivformat.");long v=0;for(int i=0;i<width;i++)v=(v<<8)|next();return v;}
        long integer(){int first=next();if(first>>5>1)throw new IllegalArgumentException("Ungültiges Archivregister.");long n=size(first);return first>>5==1?-1-n:n;}
        byte[] bytes(){int first=next();if(first>>5!=2)throw new IllegalArgumentException("Ungültige Archivantwort.");long n=size(first);if(n>4096)throw new IllegalArgumentException("Archivantwort ist zu groß.");if(n>b.length-p)throw new Incomplete();byte[] v=Arrays.copyOfRange(b,p,p+(int)n);p+=(int)n;return v;}
    }
    /** Keeps split messages; consumes byte strings atomically, including header-like samples. */
    public static final class Stream {
        private byte[] pending=new byte[0];
        public List<Message> feed(byte[] part) {
            if(pending.length+part.length>8192)throw new IllegalArgumentException("Archivpuffer überschritten.");
            byte[] b=Arrays.copyOf(pending,pending.length+part.length);System.arraycopy(part,0,b,pending.length,part.length);
            List<Message> out=new ArrayList<>();int p=0;
            while(p<b.length){
                int code=b[p]&255;
                if(code==2||code==7){int n=code==2?9:4;if(b.length-p<n)break;out.add(new Message(code,-1,Arrays.copyOfRange(b,p,p+n),0));p+=n;continue;}
                if(code!=8&&code!=9)throw new IllegalArgumentException("Unbekannte Bluetooth-Antwort.");
                if(b.length-p<2)break;
                if((b[p+1]&255)>3)throw new IllegalArgumentException("Ungültiger Archivkontext.");
                Cursor c=new Cursor(b,p+2);
                try {long reg=c.integer();if(reg<0||reg>65535)throw new IllegalArgumentException("Ungültiges Register.");
                    byte[] data=code==8?c.bytes():null;long status=code==9?c.integer():0;
                    out.add(new Message(code,(int)reg,data,status));p=c.p;
                } catch(Incomplete e){break;}
            }
            pending=Arrays.copyOfRange(b,p,b.length);return out;
        }
    }
    public static final class Field {
        public final String name;public final int width;final boolean signed;final double divisor;final long invalid;
        Field(String n,int w,boolean s,double d,long i){name=n;width=w;signed=s;divisor=d;invalid=i;}
        public Double value(byte[] b,int p){long v=u(b,p,width);if(v==invalid)return null;long sign=1L<<(width*8-1);if(signed&&(v&sign)!=0)v-=1L<<(width*8);return v/divisor;}
    }
    public static Field field(int r,int auxMode){
        switch(r){
            case 0xec87:return new Field("soc",1,false,1,255);
            case 0xec86:return new Field(auxMode==0?"aux":"auxRaw",2,false,auxMode==0?100:1,65535);
            case 0xec88:return new Field("temperature",1,true,1,127);
            case 0xec89:return new Field("load",1,false,10,255);
            case 0xec8a:return new Field("solar",2,false,1,65535);
            case 0xedbb:return new Field("pvVoltage",2,false,100,65535);
            case 0xed8d:return new Field("voltage",2,true,100,32767);
            case 0xed8f:return new Field("current",2,true,10,32767);
            case 0xed8c:return new Field("current",4,true,1000,2147483647L);
            default:return null;
        }
    }
    public static final class Block {
        public final int channel,count,period;public final long time;public final Double[] values;
        public Block(byte[] b,Field f,int expectedChannel,int requestedCount){
            if(b.length<8)throw new IllegalArgumentException("Unvollständiger Archivblock.");
            channel=b[0]&255;time=u(b,1,4);count=b[5]&255;period=(int)u(b,6,2);
            if(channel!=expectedChannel||count>requestedCount||b.length!=8+count*f.width||count>0&&period==0||time-(long)Math.max(0,count-1)*period<0)
                throw new IllegalArgumentException("Archivblock passt nicht zur Abfrage.");
            values=new Double[count];for(int i=0;i<count;i++)values[i]=f.value(b,8+i*f.width);
        }
    }
    public static final class Clock {
        private final TreeMap<Long,Long> anchors=new TreeMap<>(),local=new TreeMap<>();private final TreeSet<Long> reboots=new TreeSet<>();
        private boolean anchorHistoryComplete,rebootHistoryComplete;
        public boolean historyComplete(int reg){if(reg==0xec63)return anchorHistoryComplete;if(reg==0xec64)return rebootHistoryComplete;throw new IllegalArgumentException("Unbekannte Zeitreferenz.");}
        public void completeHistory(int reg){if(reg==0xec63)anchorHistoryComplete=true;else if(reg==0xec64)rebootHistoryComplete=true;else throw new IllegalArgumentException("Unbekannte Zeitreferenz.");}
        public static Clock restore(long savedUptime,long currentUptime,byte[] anchors,byte[] reboots){Clock clock=new Clock();if(savedUptime<0||currentUptime<savedUptime)return clock;clock.anchors(anchors);clock.reboots(reboots);return clock;}
        public int anchors(byte[] b){if(b.length%8!=0)throw new IllegalArgumentException("Ungültige Archivzeit.");int added=0;for(int i=0;i<b.length;i+=8){long u=u(b,i,4),t=u(b,i+4,4);if(u!=NA&&t!=NA&&t>946684800L){Long old=anchors.put(u,t);if(old==null||old!=t)added++;}}return added;}
        public int reboots(byte[] b){if(b.length%4!=0)throw new IllegalArgumentException("Ungültige Neustartliste.");int added=0;for(int i=0;i<b.length;i+=4){long t=u(b,i,4);if(t!=NA&&reboots.add(t))added++;}return added;}
        public boolean hasAnchors(){return !anchors.isEmpty()||!local.isEmpty();}
        public long oldestStoredAnchor(){return anchors.isEmpty()?NA:anchors.firstKey();}
        public long oldestStoredReboot(){return reboots.isEmpty()?NA:reboots.first();}
        public byte[] anchorBytes(){ByteArrayOutputStream b=new ByteArrayOutputStream();for(Map.Entry<Long,Long> a:anchors.entrySet()){byte[] u=le(a.getKey(),4),t=le(a.getValue(),4);b.write(u,0,4);b.write(t,0,4);}return b.toByteArray();}
        public byte[] rebootBytes(){ByteArrayOutputStream b=new ByteArrayOutputStream();for(long t:reboots){byte[] bytes=le(t,4);b.write(bytes,0,4);}return b.toByteArray();}
        private Long originalMillis(long time){Long low=reboots.floor(time),high=reboots.higher(time);long from=low==null?0:low,to=high==null?NA:high;
            Map.Entry<Long,Long> a=anchors.floorEntry(time);if(a==null||a.getKey()<from)a=anchors.ceilingEntry(from);
            return a==null||a.getKey()>=to?null:(a.getValue()+time-a.getKey())*1000;
        }
        public void localBytes(byte[] bytes){
            if(bytes.length%12!=0)throw new IllegalArgumentException("Ungültige lokale Archivzeit.");
            for(int i=0;i<bytes.length;i+=12){long c=u(bytes,i,4),at=u(bytes,i+4,4)|(u(bytes,i+8,4)<<32);if(c>=NA||at<=946684800000L)throw new IllegalArgumentException("Ungültige lokale Archivzeit.");local.put(c,at);}
        }
        public byte[] localBytes(){ByteArrayOutputStream out=new ByteArrayOutputStream();for(Map.Entry<Long,Long> e:local.entrySet()){byte[] c=le(e.getKey(),4),at=le(e.getValue(),8);out.write(c,0,4);out.write(at,0,8);}return out.toByteArray();}
        public Clock copy(){Clock c=new Clock();c.anchors.putAll(anchors);c.reboots.addAll(reboots);c.local.putAll(local);c.anchorHistoryComplete=anchorHistoryComplete;c.rebootHistoryComplete=rebootHistoryComplete;return c;}
        private Map.Entry<Long,Long> localFor(long time){Long low=reboots.floor(time),high=reboots.higher(time);long from=low==null?0:low,to=high==null?NA:high;Map.Entry<Long,Long> e=local.lowerEntry(to);return e!=null&&e.getKey()>=from?e:null;}
        private Map.Entry<Long,Long> driftBase(Map.Entry<Long,Long> ref){
            Long boot=reboots.floor(ref.getKey());long start=boot==null?0:boot;
            Map.Entry<Long,Long> saved=local.lowerEntry(ref.getKey());if(saved!=null&&saved.getKey()>=start&&driftPair(saved,ref))return saved;
            for(Map.Entry<Long,Long> a=anchors.lowerEntry(ref.getKey());a!=null&&a.getKey()>=start;a=anchors.lowerEntry(a.getKey())){
                Map.Entry<Long,Long> candidate=new AbstractMap.SimpleImmutableEntry<>(a.getKey(),a.getValue()*1000);
                if(driftPair(candidate,ref))return candidate;
            }return null;
        }
        private boolean driftPair(Map.Entry<Long,Long> base,Map.Entry<Long,Long> ref){long seconds=ref.getKey()-base.getKey();if(seconds<86400)return false;double rate=(ref.getValue()-base.getValue())/(seconds*1000.0);return rate>=0.998&&rate<=1.002;}
        /** Local observations place their own boot only. Long, consistent references also
         * account for clock drift between the device's original sync and the current read. */
        public Long millis(long time){
            if(time<0||time>=NA)return null;
            Map.Entry<Long,Long> ref=localFor(time);if(ref==null)return originalMillis(time);
            Map.Entry<Long,Long> base=driftBase(ref);
            if(base!=null){
                return Math.round(base.getValue()+(time-base.getKey())*(double)(ref.getValue()-base.getValue())/(ref.getKey()-base.getKey()));
            }
            return ref.getValue()+(time-ref.getKey())*1000;
        }
        /** Avoid replacing a reference for sub-second counter jitter on every import. */
        public boolean observe(ArchiveTimeReference read){
            if(!read.trusted)throw new IllegalArgumentException("Geräteuhr konnte nicht sicher mit der Handyzeit verglichen werden. Bitte erneut nachladen.");
            Map.Entry<Long,Long> previous=localFor(read.counter);Long mapped=millis(read.counter);
            if(previous!=null&&read.observedMillis>=previous.getValue()&&read.observedMillis-previous.getValue()<6*3600000L&&mapped!=null&&Math.abs(mapped-read.observedMillis)<=2500)return false;
            Long boot=reboots.floor(read.counter),next=reboots.higher(read.counter);long from=boot==null?0:boot,to=next==null?NA:next;
            Map.Entry<Long,Long> ref=new AbstractMap.SimpleImmutableEntry<>(read.counter,read.observedMillis),base=previous==null?null:driftBase(previous);
            if(base==null&&previous!=null&&driftPair(previous,ref))base=previous;
            if(base==null)base=driftBase(ref);
            local.subMap(from,true,to,false).clear();if(base!=null)local.put(base.getKey(),base.getValue());local.put(read.counter,read.observedMillis);return true;
        }
        /** Exact inverse used only to recover a missing raw counter from its saved mapping.
         * Ambiguous or foreign timestamps stay unknown; no rounded guess is returned. */
        public Long counter(long millis){
            Set<Long> candidates=new HashSet<>();
            for(Map.Entry<Long,Long> a:anchors.entrySet())candidate(candidates,millis,a.getKey()+(millis-a.getValue()*1000)/1000);
            for(Map.Entry<Long,Long> ref:local.entrySet()){
                Map.Entry<Long,Long> base=driftBase(ref);
                double guess=base==null?ref.getKey()+(millis-ref.getValue())/1000.0:base.getKey()+(millis-base.getValue())*(double)(ref.getKey()-base.getKey())/(ref.getValue()-base.getValue());
                long rounded=Math.round(guess);for(long c=rounded-1;c<=rounded+1;c++)candidate(candidates,millis,c);
            }
            return candidates.size()==1?candidates.iterator().next():null;
        }
        private void candidate(Set<Long> found,long millis,long counter){Long mapped=millis(counter);if(mapped!=null&&mapped==millis)found.add(counter);}
        public long oldestAnchor(byte[] b){long min=NA;for(int i=0;i+8<=b.length;i+=8)if(u(b,i,4)!=NA&&u(b,i+4,4)!=NA)min=Math.min(min,u(b,i,4));return min;}
    }
    public static final class Level {
        public final int capacity,period;public final long newest,oldest;
        public Level(int n,int p,long t){
            if(n<1||n>10000||p<1||p>65535||t<0||t>NA)throw new IllegalArgumentException("Ungültige Archivaufteilung.");
            capacity=n;period=p;newest=t;oldest=Math.max(0,t-(long)(n-1)*p);
        }
    }
    public static List<Level> levels(byte[] config,byte[] refs){
        if(config.length<2||config[0]<1||config[0]>8||config.length<2+4*config[0]||refs.length<4*config[0])throw new IllegalArgumentException("Unbekannte Archivaufteilung.");
        List<Level> out=new ArrayList<>();
        for(int i=0;i<config[0];i++){int n=(int)u(config,2+i*4,2),p=(int)u(config,4+i*4,2);long t=u(refs,i*4,4);
            if(n>10000||n<1||p<1)throw new IllegalArgumentException("Ungültige Archivaufteilung.");if(t!=NA)out.add(new Level(n,p,t));}
        return out;
    }
}
