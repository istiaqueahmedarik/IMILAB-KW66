package dev.kw66.glass;
import java.util.*;
/** Pure policy: never guess which call a watch hang-up event refers to. */
public final class CallPolicy {
    public static boolean supportedPackage(String pkg,String dialer){return pkg.equals("com.whatsapp")||pkg.equals("com.whatsapp.w4b")||pkg.equals("com.facebook.orca")||pkg.equals("com.facebook.mlite")||(!dialer.isEmpty()&&pkg.equals(dialer));}
    public static String uniqueRejectTarget(List<String> ringing,List<String> active){
        if(ringing.size()==1)return ringing.get(0);
        if(ringing.size()>1)return null;
        return active.size()==1?active.get(0):null;
    }
}
