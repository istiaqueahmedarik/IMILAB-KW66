package dev.kw66.glass;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Conservative KW66 decoder. Watch wall-clock timestamps carry no invented UTC offset. */
public final class Protocol {
    public static final byte[] VIBRATE = {(byte)0xab,0,0,0,1,1,0,0};
    public static final byte[][] FETCH = {{(byte)0xb2,(byte)0xfa},{(byte)0xf7,(byte)0xfa},{0x34,(byte)0xfa},{0x31,1}};
    public static final String[] FETCH_NAMES = {"Steps","Heart rate","SpO₂","Sleep"};
    public static final class Sample {
        public final String kind, localTime, note;
        public final int value, duration;
        public final Map<String,Integer> fields;
        Sample(String k, String t, int v, int d, String n, Map<String,Integer> f) { kind=k;localTime=t;value=v;duration=d;note=n;fields=f; }
        Sample(String k, LocalDateTime t, int v) { this(k,t.toString(),v,0,"Source-derived format; watch local time",Collections.emptyMap()); }
    }
    public static final class Result {
        public final List<Sample> samples = new ArrayList<>();
        public String status="Unknown packet retained", firmware;
        public int battery=-1, end=-1;
    }
    private LocalDate sleepDate;
    private int sleepExpected, sleepReceived, lastSleepHour=-1;
    private LocalDate stageDate;
    public void resetSleep() { sleepDate=null;stageDate=null;lastSleepHour=-1;sleepReceived=0;sleepExpected=0; }
    public Result decode(String channel, byte[] b) {
        Result r=new Result();
        if (b.length==0) return r;
        int op=u(b[0]);
        if (channel.equals("2a19")) { if(b.length==1 && op<=100) r.battery=op; return r; }
        try {
            if(channel.equals("34f2") && op==0x32) {
                if(sleepDate==null || (b.length-1)%6!=0) { r.status="Sleep stages without valid session/length"; return r; }
                for(int i=1;i<b.length;i+=6) {
                    int h=u(b[i]),m=u(b[i+1]),s=u(b[i+2]),d=be(b,i+4);
                    if(h>23||m>59) throw new IllegalArgumentException("Invalid sleep time");
                    // Header is wake-up date in reviewed source; this convention remains unverified on KW66.
                    if(stageDate==null) stageDate=h>12?sleepDate.minusDays(1):sleepDate;
                    if(lastSleepHour>=0 && h<lastSleepHour) stageDate=stageDate.plusDays(1);
                    lastSleepHour=h;sleepReceived++;
                    r.samples.add(new Sample("sleep",stageDate.atTime(h,m).toString(),s,d,
                        "Tentative session-date/midnight interpretation; raw stage and duration retained",fields("unknown",u(b[i+3]),"expectedStages",sleepExpected)));
                }
                r.status="Sleep stages decoded (date convention unverified)"; return r;
            }
            if(!channel.equals("33f2")) return r;
            if(op==0xa1 && b.length>1) {r.firmware=new String(b,1,b.length-1,StandardCharsets.US_ASCII);r.status="Firmware";}
            else if(op==0xa2 && b.length>=2 && u(b[1])<=100) {r.battery=u(b[1]);r.status="Battery";}
            else if(op==0xe5 && b.length==4 && u(b[1])==0x11 && be(b,2)>0 && be(b,2)<255) {
                r.samples.add(new Sample("live_hr","received",be(b,2),0,"Observed E5 11 format; timestamp is receipt time",Collections.emptyMap()));
            }
            else if(op==0xf7 && b.length==11 && u(b[1])==4) {
                LocalDateTime t=date(b,2).atTime(u(b[6]),u(b[7]));
                r.samples.add(new Sample("hr_summary",t.toString(),u(b[10]),0,"Observed max/min/average; not history samples",fields("max",u(b[8]),"min",u(b[9]))));
            }
            else if(op==0xb2 && b.length==18) {
                LocalDateTime t=date(b,1).atTime(u(b[5]),0);
                r.samples.add(new Sample("steps",t.toString(),be(b,6),0,"Hourly record; unsigned big-endian counts",fields(
                    "runStart",u(b[8]),"runEnd",u(b[9]),"unknownRun",u(b[10]),"runSteps",be(b,11),
                    "walkStart",u(b[13]),"walkEnd",u(b[14]),"unknownWalk",u(b[15]),"walkSteps",be(b,16))));
            }
            else if(op==0xf7 && b.length==18 && u(b[1])==7) {
                LocalDateTime end=date(b,1).atTime(u(b[5]),0);
                for(int i=6;i<18;i++) if(valid(b[i])) r.samples.add(new Sample("hr",end.minusMinutes((17-i)*10L),u(b[i])));
            }
            else if(op==0x34 && b.length==20 && u(b[1])==0xfa && u(b[2])==7) {
                LocalDateTime end=date(b,2).atTime(u(b[6]),u(b[7]));
                for(int i=8;i<20;i++) if(valid(b[i]) && u(b[i])<=100) r.samples.add(new Sample("spo2",end.minusMinutes((19-i)*10L),u(b[i])));
            }
            else if(op==0x31 && b.length==7 && b[1]==1) {
                resetSleep();sleepDate=date(b,2);sleepExpected=u(b[6]);r.status="Sleep session header: "+sleepExpected+" stages";
            }
            else if(op==0xb2 && b.length==3 && u(b[1])==0xfd) r.end=0;
            else if(op==0xf7 && b.length==3 && u(b[1])==0xfd) r.end=1;
            else if(op==0x34 && b.length>=3 && u(b[1])==0xfa && u(b[2])==0xfd) r.end=2;
            else if(op==0x31 && b.length==2 && b[1]==2) {
                r.end=3;r.status=sleepDate==null?"Sleep ended; no session received":"Sleep ended: "+sleepReceived+" / "+sleepExpected+" stages";resetSleep();
            }
            if(!r.samples.isEmpty()) r.status="Decoded "+r.samples.size()+" "+r.samples.get(0).kind+" sample(s)";
            if(r.end>=0 && r.end!=3) r.status=FETCH_NAMES[r.end]+" transfer ended";
        } catch(DateTimeException|IllegalArgumentException e) {
            r.samples.clear();r.status="Malformed/unsupported date or fields; raw packet retained";
        }
        return r;
    }
    private static Map<String,Integer> fields(Object... pairs) { Map<String,Integer> out=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],(Integer)pairs[i+1]);return out; }
    private static LocalDate date(byte[] b,int at) { int y=be(b,at); if(y<2000||y>2100) throw new IllegalArgumentException("year"); return LocalDate.of(y,u(b[at+2]),u(b[at+3])); }
    private static boolean valid(byte b) { return b!=0 && u(b)!=255; }
    public static int u(byte b) { return b&255; }
    public static int be(byte[] b,int i) { return u(b[i])*256+u(b[i+1]); }
    public static String hex(byte[] b) { StringBuilder s=new StringBuilder();for(byte v:b) {if(s.length()>0)s.append(' ');s.append(String.format(Locale.ROOT,"%02X",u(v)));}return s.toString(); }
    public static byte[] unhex(String s) { String x=s.replace(" ","");if((x.length()&1)!=0)throw new IllegalArgumentException("hex");byte[] a=new byte[x.length()/2];for(int i=0;i<a.length;i++)a[i]=(byte)Integer.parseInt(x.substring(i*2,i*2+2),16);return a; }
    public static List<byte[]> message(String text,int type) {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        text=text.trim();
        for(int i=0;i<text.length();) {
            int cp=text.codePointAt(i);byte[] unit=new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_16BE);
            if(out.size()+unit.length>240)break;out.write(unit,0,unit.length);i+=Character.charCount(cp);
        }
        byte[] data=out.toByteArray();if(data.length==0)throw new IllegalArgumentException("Enter a message");
        List<byte[]> frames=new ArrayList<>();int offset=0;
        while(offset<data.length) {
            int header=offset==0?4:2,n=Math.min(20-header,data.length-offset);byte[] f=new byte[header+n];
            f[0]=(byte)0xc5;f[1]=(byte)frames.size();if(header==4){f[2]=(byte)type;f[3]=(byte)data.length;}
            System.arraycopy(data,offset,f,header,n);frames.add(f);offset+=n;
        }
        frames.add(new byte[]{(byte)0xc5,(byte)0xfd});return frames;
    }
    public static byte[] time(LocalDateTime t) {return new byte[]{(byte)0xa3,(byte)(t.getYear()>>8),(byte)t.getYear(),(byte)t.getMonthValue(),(byte)t.getDayOfMonth(),(byte)t.getHour(),(byte)t.getMinute(),(byte)t.getSecond()};}
}
