package dev.kw66.glass;
import android.telecom.*;
import java.util.*;
/** Non-UI wearable companion; the system dialer retains its UI, ringtone and emergency behavior. */
public final class CellularCalls extends InCallService {
    private static CellularCalls live;
    public void onCreate(){super.onCreate();live=this;}
    public static void refreshCalls(){if(live!=null)for(Call c:live.getCalls())Calls.telecom(live,c);}
    private final Map<Call,Call.Callback> callbacks=new HashMap<>();
    public void onCallAdded(Call call){super.onCallAdded(call);Call.Callback cb=new Call.Callback(){
        public void onStateChanged(Call c,int state){Calls.telecom(CellularCalls.this,c);}
        public void onDetailsChanged(Call c,Call.Details d){Calls.telecom(CellularCalls.this,c);}
    };callbacks.put(call,cb);call.registerCallback(cb);Calls.telecom(this,call);}
    public void onCallRemoved(Call call){Call.Callback cb=callbacks.remove(call);if(cb!=null)call.unregisterCallback(cb);Calls.remove(this,"telecom:"+System.identityHashCode(call));super.onCallRemoved(call);}
    public void onDestroy(){if(live==this)live=null;for(Map.Entry<Call,Call.Callback> e:callbacks.entrySet()){e.getKey().unregisterCallback(e.getValue());Calls.remove(this,"telecom:"+System.identityHashCode(e.getKey()));}callbacks.clear();super.onDestroy();}
}
