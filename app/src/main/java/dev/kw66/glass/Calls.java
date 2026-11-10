package dev.kw66.glass;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
import android.telecom.*;
import java.util.*;

/** Live call handles remain process-local, never in diagnostics, export or AI context. */
public final class Calls {
    public static final class Entry {
        public final String id,source,caller,state;public final boolean ringing,notification;
        public final LinkedHashMap<String,Runnable> actions=new LinkedHashMap<>();
        Entry(String id,String source,String caller,String state,boolean ringing,boolean notification){this.id=id;this.source=source;this.caller=caller;this.state=state;this.ringing=ringing;this.notification=notification;}
    }
    private static final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
    private static long version;
    public static synchronized long version(){return version;}
    public static synchronized List<Entry> snapshot(){return new ArrayList<>(entries.values());}
    public static boolean enabled(Context c){return AppState.prefs(c).getBoolean("calls",false);}
    public static boolean telecomAccess(Context c){return Build.VERSION.SDK_INT>=31&&c.getSystemService(TelecomManager.class)!=null&&c.getSystemService(TelecomManager.class).hasManageOngoingCallsPermission();}
    private static synchronized void put(Context c,Entry e){
        Entry old=entries.put(e.id,e);version++;
        if(e.ringing&&(old==null||!old.ringing)){
            Store.get(c).enqueue("Incoming "+e.source+" call · "+e.caller,0,"call:"+e.id,10_000);
            AppState.startWatch(c,"outbox");Store.get(c).event("Incoming call alert queued (caller private; watch display unverified)");
        }else if(old==null&&e.notification&&e.state.contains("state unavailable")){
            Store.get(c).enqueue(e.source+" call · "+e.caller,4,"call:"+e.id,10_000);AppState.startWatch(c,"outbox");
        }else if(old!=null&&old.ringing&&!e.ringing){Store.get(c).clearOrigin("call:"+e.id);if(!hasRinging())AppState.startWatch(c,"call_end");}
        AppState.update(c);
    }
    private static boolean hasRinging(){for(Entry e:entries.values())if(e.ringing)return true;return false;}
    public static synchronized void remove(Context c,String id){if(entries.remove(id)!=null){version++;Store.get(c).clearOrigin("call:"+id);if(!hasRinging())AppState.startWatch(c,"call_end");AppState.update(c);}}
    public static synchronized void clear(Context c){for(String id:new ArrayList<>(entries.keySet()))remove(c,id);}
    public static synchronized boolean watchMayEnd(){return !hasRinging();}
    public static synchronized void telecom(Context c,Call call){
        String id="telecom:"+System.identityHashCode(call);int state=call.getState();
        if(!enabled(c)||state==Call.STATE_DISCONNECTED){remove(c,id);return;}
        for(Entry other:new ArrayList<>(entries.values()))if(other.source.equals("Phone notification"))remove(c,other.id);
        Call.Details details=call.getDetails();String caller=details==null?null:details.getCallerDisplayName();
        if((caller==null||caller.isEmpty())&&details!=null&&details.getHandle()!=null)caller=details.getHandle().getSchemeSpecificPart();
        if(caller==null||caller.isEmpty())caller="Private or unavailable caller";
        boolean ringing=state==Call.STATE_RINGING;Entry e=new Entry(id,"Phone",caller,ringing?"Incoming phone call":state==Call.STATE_ACTIVE?"Phone call active":"Phone call state "+state,ringing,false);
        if(ringing){e.actions.put("Answer",()->{requireCall(call,Call.STATE_RINGING);call.answer(VideoProfile.STATE_AUDIO_ONLY);});e.actions.put("Reject",()->{requireCall(call,Call.STATE_RINGING);call.reject(false,null);});}
        else if(state==Call.STATE_ACTIVE||state==Call.STATE_HOLDING)e.actions.put("Hang up",()->{if(call.getState()!=Call.STATE_ACTIVE&&call.getState()!=Call.STATE_HOLDING)throw new IllegalStateException("Call state changed");call.disconnect();});
        put(c,e);
    }
    private static void requireCall(Call call,int state){if(call.getState()!=state)throw new IllegalStateException("Call state changed");}
    public static boolean posted(NotificationBridge bridge,StatusBarNotification sbn){
        if(!enabled(bridge))return false;String pkg=sbn.getPackageName();TelecomManager tm=bridge.getSystemService(TelecomManager.class);String dialer=tm==null?"":tm.getDefaultDialerPackage();if(dialer==null)dialer="";
        Notification n=sbn.getNotification();if(!CallPolicy.supportedPackage(pkg,dialer)||!Notification.CATEGORY_CALL.equals(n.category))return false;
        if(AppState.prefs(bridge).getStringSet("blocked",Collections.emptySet()).contains(pkg)){remove(bridge,"notification:"+sbn.getKey());return true;}
        // Telecom is authoritative for cellular calls when companion access is working; avoid duplicate controls.
        if(pkg.equals(dialer)&&telecomAccess(bridge)&&hasTelecom())return true;
        String id="notification:"+sbn.getKey(),source=pkg.startsWith("com.whatsapp")?"WhatsApp":pkg.startsWith("com.facebook")?"Messenger":"Phone notification";
        Bundle b=n.extras;String caller=String.valueOf(b.getCharSequence(Notification.EXTRA_TITLE,"Caller unavailable"));
        if(Build.VERSION.SDK_INT>=31){Person person=b.getParcelable(Notification.EXTRA_CALL_PERSON);if(person!=null&&person.getName()!=null)caller=person.getName().toString();}
        PendingIntent answer=null,reject=null,hangup=null;
        if(Build.VERSION.SDK_INT>=31){answer=b.getParcelable(Notification.EXTRA_ANSWER_INTENT);reject=b.getParcelable(Notification.EXTRA_DECLINE_INTENT);hangup=b.getParcelable(Notification.EXTRA_HANG_UP_INTENT);}
        boolean ringing=answer!=null;Entry e=new Entry(id,source,caller,ringing?"Incoming call":hangup!=null?"Call in progress":"Call notification · state unavailable",ringing,true);
        if(answer!=null)addIntent(e,"Answer",answer,bridge,sbn.getKey());if(reject!=null)addIntent(e,"Reject",reject,bridge,sbn.getKey());if(hangup!=null)addIntent(e,"Hang up",hangup,bridge,sbn.getKey());
        // Legacy/localized action labels are displayed verbatim. They are never guessed as watch reject/answer.
        if(n.actions!=null)for(int i=0;i<n.actions.length;i++){Notification.Action a=n.actions[i];if(a.actionIntent!=null&&a.title!=null&&!containsIntent(a.actionIntent,answer,reject,hangup)){String label="App action "+(i+1)+": "+a.title;addIntent(e,label,a.actionIntent,bridge,sbn.getKey());}}
        if(sbn.isClearable())e.actions.put("Dismiss notification",()->{if(!bridge.current(sbn.getKey()))throw new IllegalStateException("Notification is no longer active");bridge.cancelNotification(sbn.getKey());});
        put(bridge,e);return true;
    }
    private static synchronized boolean hasTelecom(){for(Entry e:entries.values())if(!e.notification)return true;return false;}
    private static boolean containsIntent(PendingIntent i,PendingIntent...known){for(PendingIntent k:known)if(i.equals(k))return true;return false;}
    private static void addIntent(Entry e,String label,PendingIntent intent,NotificationBridge bridge,String key){e.actions.put(label,()->{
        if(!bridge.current(key))throw new IllegalStateException("Call notification is no longer active");
        if(bridge.getSystemService(KeyguardManager.class).isDeviceLocked())throw new IllegalStateException("Unlock the phone for notification call actions");
        try{intent.send();}catch(PendingIntent.CanceledException ex){throw new IllegalStateException("The calling app withdrew this action");}
    });}
    public static void perform(Context c,String id,String action){
        Entry entry;Runnable run;synchronized(Calls.class){entry=entries.get(id);run=entry==null?null:entry.actions.get(action);}
        if(!enabled(c)||run==null){Store.get(c).event("Call action unavailable; no call changed");return;}
        try{run.run();Store.get(c).event("Call action dispatched; outcome awaits Android/app update");}
        catch(RuntimeException ex){Store.get(c).event("Call action unavailable: "+ex.getClass().getSimpleName());android.widget.Toast.makeText(c,"Call action unavailable: "+ex.getMessage(),android.widget.Toast.LENGTH_LONG).show();}
    }
    public static void rejectFromWatch(Context c){
        if(!enabled(c)||!AppState.prefs(c).getBoolean("watch_call_reject",false)){Store.get(c).event("Watch hang-up received; call control is disabled");return;}
        List<String> ring=new ArrayList<>(),active=new ArrayList<>();for(Entry e:snapshot()){
            if(e.notification&&e.state.contains("state unavailable")){Store.get(c).event("Watch hang-up blocked by an unknown-state call notification");return;}
            if(e.ringing)ring.add(e.id);else active.add(e.id);
        }
        String id=CallPolicy.uniqueRejectTarget(ring,active);if(id==null){Store.get(c).event("Watch hang-up has no unique controllable call; no action taken");return;}
        Entry e; synchronized(Calls.class){e=entries.get(id);}if(e!=null)perform(c,id,e.ringing?"Reject":"Hang up");
    }
}
