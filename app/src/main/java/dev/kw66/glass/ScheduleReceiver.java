package dev.kw66.glass;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.util.*;

public final class ScheduleReceiver extends BroadcastReceiver {
    static PendingIntent pending(Context c,long id){return PendingIntent.getBroadcast(c,(int)id,new Intent(c,ScheduleReceiver.class).setAction("dev.kw66.glass.DUE").setData(android.net.Uri.parse("kw66://task/"+id)).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static boolean exact(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    public static void schedule(Context c,long id,long due){
        AlarmManager a=c.getSystemService(AlarmManager.class);PendingIntent pi=pending(c,id);
        try{if(exact(c))a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,Math.max(System.currentTimeMillis()+1000,due),pi);
            else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,Math.max(System.currentTimeMillis()+1000,due),pi);
        }catch(SecurityException e){a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,Math.max(System.currentTimeMillis()+1000,due),pi);Store.get(c).event("Exact alarm access revoked; reminder may be delayed");}
    }
    public static void cancel(Context c,long id){c.getSystemService(AlarmManager.class).cancel(pending(c,id));}
    public static void restore(Context c){for(ContentValues v:Store.get(c).rows("SELECT id,due FROM tasks WHERE done=0 AND delivered=0"))schedule(c,v.getAsLong("id"),v.getAsLong("due"));}
    public void onReceive(Context c,Intent i){
        AppState.channels(c);String action=i.getAction();
        if(!"dev.kw66.glass.DUE".equals(action)){
            restore(c);
            if(Intent.ACTION_BOOT_COMPLETED.equals(action)){
                if(AppState.prefs(c).getBoolean("connect",false))AppState.startWatch(c,"connect");
                // Android 15+ prohibits starting mediaPlayback FGS at boot. Keep alarm state and ask to reopen.
                if(AppState.prefs(c).getBoolean("ringing",false))c.getSystemService(NotificationManager.class).notify(68,AppState.notification(c,"ringing","Alarm is still active","Open KW66 Glass to resume ringing or stop it",false));
            }
            return;
        }
        Store s=Store.get(c);long id=i.getLongExtra("id",-1);List<ContentValues> list=s.rows("SELECT * FROM tasks WHERE id=? AND done=0 AND delivered=0",""+id);if(list.isEmpty())return;
        ContentValues task=list.get(0);long now=System.currentTimeMillis();
        if(task.getAsLong("due")>now+1000){schedule(c,id,task.getAsLong("due"));return;}
        String title=task.getAsString("title");
        c.getSystemService(NotificationManager.class).notify((int)(1000+id),AppState.notification(c,"reminders",title,"Scheduled reminder · tap to open",false));
        if(task.getAsInteger("alarm")==1){
            AppState.prefs(c).edit().putBoolean("ringing",true).putString("alarm_title",title).commit();
            try{c.startForegroundService(new Intent(c,RingingService.class));}catch(RuntimeException e){s.event("Android blocked alarm service; open app to resume persistent alarm");}
        }else{s.enqueue(title,4,"task:"+id);AppState.startWatch(c,"outbox");}
        int repeat=task.getAsInteger("repeat_minutes");
        if(repeat>0){long next=SchedulePolicy.next(task.getAsLong("due"),now,repeat);s.advance(id,next);schedule(c,id,next);}else if(task.getAsInteger("alarm")==1)s.complete(id);else s.delivered(id);
        s.event("Scheduled "+(task.getAsInteger("alarm")==1?"alarm":"reminder")+" fired (content private)");AppState.update(c);
    }
}
