package dev.kw66.glass;

import android.app.Notification;
import android.content.*;
import android.os.Bundle;
import android.service.notification.*;
import java.util.*;

public final class NotificationBridge extends NotificationListenerService {
    private static NotificationBridge live;
    public void onCreate(){super.onCreate();live=this;}
    public static void refreshCalls(){if(live!=null)try{for(StatusBarNotification n:live.getActiveNotifications())Calls.posted(live,n);}catch(RuntimeException ignored){}}
    public void onDestroy(){if(live==this)live=null;for(Calls.Entry e:Calls.snapshot())if(e.notification)Calls.remove(this,e.id);super.onDestroy();}
    private final LinkedHashMap<String,String> recent=new LinkedHashMap<>();
    public void onListenerConnected(){Store.get(this).event("Android notification access connected");try{for(StatusBarNotification n:getActiveNotifications())Calls.posted(this,n);}catch(RuntimeException ignored){}AppState.update(this);}
    public boolean current(String key){try{StatusBarNotification[] active=getActiveNotifications(new String[]{key});return active!=null&&active.length==1;}catch(RuntimeException e){return false;}}
    public void onNotificationPosted(StatusBarNotification sbn){
        SharedPreferences p=AppState.prefs(this);
        if(sbn.getPackageName().equals(getPackageName()))return;
        Set<String> known=new HashSet<>(p.getStringSet("known_apps",Collections.emptySet()));
        if(known.size()<300&&known.add(sbn.getPackageName()))p.edit().putStringSet("known_apps",known).apply();
        if(Calls.posted(this,sbn))return;
        Calls.remove(this,"notification:"+sbn.getKey());
        if(!p.getBoolean("forward",false))return;
        Set<String> blocked=p.getStringSet("blocked",Collections.emptySet());if(blocked.contains(sbn.getPackageName()))return;
        Notification n=sbn.getNotification();
        if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return;
        Bundle e=n.extras;CharSequence title=e.getCharSequence(Notification.EXTRA_TITLE),body=e.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if(body==null)body=e.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence[] lines=e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if((body==null||body.length()==0)&&lines!=null&&lines.length>0)body=lines[lines.length-1];
        if((title==null||title.length()==0)&&(body==null||body.length()==0))return;
        String app=sbn.getPackageName();try{app=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(app,0)).toString();}catch(Exception ignored){}
        String text=app+" · "+(title==null?"":title)+((body==null||body.length()==0)?"":": "+body);
        // Content is kept only for this deduplication window and the expiring private outbox.
        if(text.equals(recent.get(sbn.getKey())))return;recent.put(sbn.getKey(),text);if(recent.size()>100)recent.remove(recent.keySet().iterator().next());
        int type=sbn.getPackageName().contains("whatsapp")?7:sbn.getPackageName().contains("telegram")?24:4;
        Store.get(this).enqueue(text,type,"notification");AppState.startWatch(this,"outbox");
    }
    public void onNotificationRemoved(StatusBarNotification sbn){recent.remove(sbn.getKey());Calls.remove(this,"notification:"+sbn.getKey());}
    public void onListenerDisconnected(){for(Calls.Entry e:Calls.snapshot())if(e.notification)Calls.remove(this,e.id);}
}
