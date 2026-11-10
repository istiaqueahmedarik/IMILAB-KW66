package dev.kw66.glass;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;

/** Phone loop + watch nudges every 30s; alarm state survives process death. Stop is an in-app action. */
public final class RingingService extends Service {
    private final Handler h=new Handler(Looper.getMainLooper());private MediaPlayer player;private PowerManager.WakeLock wake;
    private final Runnable pulse=new Runnable(){public void run(){if(!AppState.prefs(RingingService.this).getBoolean("ringing",false)){stopSelf();return;}
        Store.get(RingingService.this).enqueue(AppState.prefs(RingingService.this).getString("alarm_title","Alarm")+" · Open KW66 Glass to stop",4,"alarm");
        AppState.startWatch(RingingService.this,"outbox");h.postDelayed(this,30_000);}};
    public void onCreate(){super.onCreate();AppState.channels(this);startForeground(68,AppState.notification(this,"ringing","Alarm active","Open KW66 Glass to stop ringing",true));
        wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"kw66:alarm");wake.acquire();
        try{player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            android.net.Uri sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);if(sound==null)sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player.setDataSource(this,sound);player.setLooping(true);player.prepare();player.start();
        }catch(Exception e){Store.get(this).event("Alarm sound unavailable; check phone alarm volume");if(player!=null){player.release();player=null;}}
    }
    public int onStartCommand(Intent i,int flags,int id){if(!AppState.prefs(this).getBoolean("ringing",false)){stopSelf();return START_NOT_STICKY;}h.removeCallbacks(pulse);h.post(pulse);AppState.update(this);return START_STICKY;}
    public static void stop(Context c){AppState.prefs(c).edit().putBoolean("ringing",false).commit();c.stopService(new Intent(c,RingingService.class));
        // Remove unsent alarm nudges without discarding other queued notifications.
        Store.get(c).clearOrigin("alarm");
        c.getSystemService(NotificationManager.class).cancel(68);Store.get(c).event("Alarm stopped in app");AppState.update(c);}
    public void onDestroy(){h.removeCallbacks(pulse);if(player!=null)player.release();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
    public IBinder onBind(Intent i){return null;}
}
