package de.wattpit.app.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Test peer built only from the public sanitized VictronConnect response bytes.
 * Re-slices captured samples for a new cursor/count; it never invents values or timestamps.
 * It is not a Bluetooth speed benchmark. */
public final class CapturedVictronDevice {
    public static final class Pair {
        public final byte[] request,response;
        Pair(byte[] request,byte[] response){this.request=request;this.response=response;}
    }
    public final List<Pair> pairs=new ArrayList<>();
    public final Map<Integer,byte[]> registers=new LinkedHashMap<>();
    private final Map<Integer,SortedMap<Integer,NavigableMap<Long,byte[]>>> samples=new TreeMap<>();
    public static final long EPOCH=1700000000000L;
    public CapturedVictronDevice(Path path)throws IOException{this(Files.newInputStream(path));}
    public CapturedVictronDevice(InputStream input)throws IOException{
        if(input==null)throw new FileNotFoundException("Victron capture fixture");
        try(BufferedReader r=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){
            String line;while((line=r.readLine())!=null){
                if(line.startsWith("#")||line.isEmpty())continue;
                String[] p=line.split("\t");
                if(p[0].equals("V"))registers.putIfAbsent(Integer.parseInt(p[1],16),hex(p[2]));
                else if(p[0].equals("B"))pairs.add(new Pair(hex(p[1]),hex(p[2])));
            }
        }
        for(Pair pair:pairs){
            if((pair.request[5]&255)<=1)continue; // omit the app's 16 single-point probes
            byte[] b=pair.response;int ch=b[0]&255,count=b[5]&255,period=(int)ArchiveCodec.u(b,6,2);
            if(count==0)continue;
            int width=field(ch).width;long newest=ArchiveCodec.u(b,1,4);
            NavigableMap<Long,byte[]> tier=samples.computeIfAbsent(ch,k->new TreeMap<>()).computeIfAbsent(period,k->new TreeMap<>());
            for(int i=0;i<count;i++)tier.put(newest-(long)i*period,Arrays.copyOfRange(b,8+i*width,8+(i+1)*width));
        }
    }
    private static byte[] hex(String s){byte[] out=new byte[s.length()/2];for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return out;}
    public int register(int channel){return (int)ArchiveCodec.u(registers.get(0xec5d),channel*2,2);}
    public ArchiveCodec.Field field(int channel){return ArchiveCodec.field(register(channel),-1);}
    public Map<Integer,List<ArchiveCodec.Level>> levels(){
        Map<Integer,List<ArchiveCodec.Level>> out=new LinkedHashMap<>();
        for(int ch:samples.keySet())out.put(ch,ArchiveCodec.levels(registers.get(0xec4a+ch),registers.get(0xec52+ch)));
        return out;
    }
    public ArchiveCodec.Clock clock(){ArchiveCodec.Clock c=new ArchiveCodec.Clock();byte[] b=new byte[8];System.arraycopy(ArchiveCodec.le(EPOCH/1000,4),0,b,4,4);c.anchors(b);return c;}
    public long now(){return EPOCH+ArchiveCodec.u(registers.get(0xec5a),0,4)*1000;}
    public byte[] reply(ArchiveTransferPlan.Read read){
        int ch=read.channel;long cursor=read.request.cursor;
        for(Map.Entry<Integer,NavigableMap<Long,byte[]>> entry:samples.get(ch).entrySet()){
            int period=entry.getKey();NavigableMap<Long,byte[]> tier=entry.getValue();Long time=tier.ceilingKey(cursor);
            if(time==null||time-cursor>=period)continue;
            List<byte[]> values=new ArrayList<>();
            for(int i=0;i<read.request.count;i++){byte[] v=tier.get(time-(long)i*period);if(v==null)break;values.add(v);}
            byte[] block=new byte[8+values.size()*field(ch).width];block[0]=(byte)ch;System.arraycopy(ArchiveCodec.le(time,4),0,block,1,4);block[5]=(byte)values.size();System.arraycopy(ArchiveCodec.le(period,2),0,block,6,2);
            for(int i=0;i<values.size();i++)System.arraycopy(values.get(i),0,block,8+i*field(ch).width,field(ch).width);
            return block;
        }
        byte[] empty=new byte[8];empty[0]=(byte)ch;System.arraycopy(ArchiveCodec.le(cursor,4),0,empty,1,4);return empty;
    }
    public Map<String,Double> validValues(){
        Map<String,Double> keys=new HashMap<>();
        for(int ch:samples.keySet())for(Map.Entry<Integer,NavigableMap<Long,byte[]>> tier:samples.get(ch).entrySet())for(Map.Entry<Long,byte[]> sample:tier.getValue().entrySet())if(field(ch).value(sample.getValue(),0)!=null)keys.put(ch+":"+tier.getKey()+":"+sample.getKey(),field(ch).value(sample.getValue(),0));
        return keys;
    }
    public Set<String> validKeys(){return validValues().keySet();}
}
