import dev.kw66.glass.Protocol;
import dev.kw66.glass.SchedulePolicy;
import dev.kw66.glass.CallPolicy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class ProtocolTest {
    static int checks=0;
    static void eq(Object a,Object b){checks++;if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
    static void ok(boolean v){checks++;if(!v)throw new AssertionError("Condition failed");}
    static Protocol.Result decode(Protocol p,String hex){return p.decode("33f2",Protocol.unhex(hex));}
    public static void main(String[] args)throws Exception{
        // Public fixtures are synthetic; optional private-capture replay is supplied separately.
        Protocol p=new Protocol();
        Protocol.Sample steps=decode(p,"B2 07 EA 01 02 09 03 E8 00 00 00 00 00 0D 3B 14 03 E8").samples.get(0);
        eq(steps.value,1000);eq(steps.localTime,"2026-01-02T09:00");eq(steps.fields.get("walkSteps"),1000);eq(steps.fields.get("unknownWalk"),20);
        Protocol.Sample unsigned=decode(p,"B2 07 EA 07 15 13 FF FE 00 00 00 00 00 0D 3B 14 FF FE").samples.get(0);eq(unsigned.value,65534);
        List<Protocol.Sample> hrs=decode(p,"F7 07 EA 01 03 00 46 47 48 49 4A 4B 4C 4D 4E 4F 50 51").samples;
        eq(hrs.size(),12);eq(hrs.get(0).localTime,"2026-01-02T22:10");eq(hrs.get(11).localTime,"2026-01-03T00:00");eq(hrs.get(0).value,70);eq(hrs.get(11).value,81);
        List<Protocol.Sample> oxygen=decode(p,"34 FA 07 EA 01 03 00 00 61 61 61 61 61 61 61 61 61 61 61 FF").samples;
        eq(oxygen.size(),11);eq(oxygen.get(0).localTime,"2026-01-02T22:10");eq(oxygen.get(10).localTime,"2026-01-02T23:50");
        eq(decode(p,"E5 11 00 50").samples.get(0).value,80);eq(decode(p,"E5 00 00 57").samples.size(),0);eq(decode(p,"E5 11").samples.size(),0);
        eq(decode(p,"F7 04 07 EA 01 03 0A 1E 64 3C 50").samples.get(0).fields.get("max"),100);
        eq(decode(p,"B2 FD 00").end,0);eq(decode(p,"F7 FD 00").end,1);eq(decode(p,"34 FA FD 08").end,2);eq(decode(p,"31 02").end,3);
        // Synthetic sleep session. No saved KW66 capture contains stage records.
        eq(p.decode("34f2",Protocol.unhex("32 17 1E 02 01 00 3C")).samples.size(),0);
        decode(p,"31 01 07 EA 0B 09 02");
        List<Protocol.Sample> sleep=p.decode("34f2",Protocol.unhex("32 17 1E 02 01 00 3C 00 1E 03 01 00 1E")).samples;
        eq(sleep.size(),2);eq(sleep.get(0).localTime,"2026-11-08T23:30");eq(sleep.get(1).localTime,"2026-11-09T00:30");eq(sleep.get(0).duration,60);
        decode(p,"31 02");eq(p.decode("34f2",Protocol.unhex("32 00 1E 03 01 00 1E")).samples.size(),0);
        eq(decode(p,"B2 07 EA 0D 15 13 08 95 00 00 00 00 00 0D 3B 14 08 95").samples.size(),0);
        eq(p.decode("b004",Protocol.unhex("E5 11 00 50")).samples.size(),0);
        // All short/truncated/random packets must remain bounded and nonthrowing.
        Random random=new Random(6600);for(int i=0;i<10000;i++){byte[] b=new byte[random.nextInt(40)];random.nextBytes(b);p.decode(i%2==0?"33f2":"34f2",b);}ok(true);
        for(String message:new String[]{"Hello KW66","বাংলা বার্তা","A🚀B","x".repeat(119)+"🚀"}){
            List<byte[]> frames=Protocol.message(message,4);eq(frames.get(0)[2],(byte)4);ByteArrayOutputStream out=new ByteArrayOutputStream();int idx=0;
            for(byte[] f:frames){ok(f.length<=20);eq(f[0],(byte)0xc5);if((f[1]&255)==0xfd)break;eq(f[1]&255,idx);int at=idx++==0?4:2;out.write(f,at,f.length-at);}
            eq(out.size(),Protocol.u(frames.get(0)[3]));String rebuilt=out.toString(StandardCharsets.UTF_16BE);ok(!rebuilt.contains("�"));ok(message.startsWith(rebuilt));ok(out.size()<=240);
        }
        try{Protocol.message("  ",4);throw new AssertionError("empty accepted");}catch(IllegalArgumentException expected){ok(true);}
        eq(SchedulePolicy.next(1_000L,1_000L,60),3_601_000L);
        eq(SchedulePolicy.next(1_000L,10_000_000L,60),10_801_000L);
        eq(SchedulePolicy.next(10_000L,1_000L,1440),10_000L);
        try{SchedulePolicy.next(0,0,0);throw new AssertionError("Zero interval accepted");}catch(IllegalArgumentException expected){ok(true);}
        ok(CallPolicy.supportedPackage("com.whatsapp",""));ok(CallPolicy.supportedPackage("com.whatsapp.w4b",""));
        ok(CallPolicy.supportedPackage("com.facebook.orca",""));ok(CallPolicy.supportedPackage("com.phone","com.phone"));
        ok(!CallPolicy.supportedPackage("com.whatsapp.fake",""));ok(!CallPolicy.supportedPackage("com.example","com.phone"));
        eq(CallPolicy.uniqueRejectTarget(Collections.emptyList(),Collections.emptyList()),null);
        eq(CallPolicy.uniqueRejectTarget(Arrays.asList("incoming"),Arrays.asList("active")),"incoming");
        eq(CallPolicy.uniqueRejectTarget(Arrays.asList("one","two"),Collections.emptyList()),null);
        eq(CallPolicy.uniqueRejectTarget(Collections.emptyList(),Arrays.asList("active")),"active");
        eq(CallPolicy.uniqueRejectTarget(Collections.emptyList(),Arrays.asList("one","two")),null);
        if(args.length>0)replay(p,Paths.get(args[0]));System.out.println("PASS "+checks+" protocol assertions + 10,000 malformed-packet trials");
    }
    static void replay(Protocol p,Path packets)throws Exception{
        Map<String,Integer> counts=new TreeMap<>();Map<String,Protocol.Sample> unique=new HashMap<>();int records=0;
        for(String line:Files.readAllLines(packets)){String[] parts=line.split("\\t",-1);Protocol.Result r=p.decode(parts[0],Protocol.unhex(parts[1]));records++;
            for(Protocol.Sample s:r.samples){counts.merge(s.kind,1,Integer::sum);unique.put(s.kind+"/"+s.localTime,s);}}
        eq(records,403);eq(counts.get("steps"),232);eq(counts.get("hr"),767);eq(counts.get("spo2"),736);eq(counts.get("hr_summary"),1);ok(!counts.containsKey("sleep"));
        System.out.println("Actual saved-capture replay: "+records+" packets; decoded sample counts="+counts+"; unique keys="+unique.size());
    }
}
