package dev.kw66.glass;

import android.content.*;
import android.media.AudioManager;
import android.media.session.*;
import android.view.KeyEvent;
import java.util.List;

public final class WatchActions {
    public static void event(Context c,byte[] b){
        int action=b[1]&255;Store store=Store.get(c);
        if(action==2){Calls.rejectFromWatch(c);return;}
        if(!AppState.prefs(c).getBoolean("media",false)){store.event("Watch D1 event "+action+" received; media controls disabled");return;}
        try{
            AudioManager audio=c.getSystemService(AudioManager.class);
            if(action==0x0d||action==0x0e){audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,action==0x0d?AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER,0);return;}
            int key=action==7?KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:action==8?KeyEvent.KEYCODE_MEDIA_NEXT:action==9?KeyEvent.KEYCODE_MEDIA_PREVIOUS:0;
            if(key==0){store.event("Watch action retained for audit; no phone action assigned");return;}
            List<MediaController> sessions=c.getSystemService(MediaSessionManager.class).getActiveSessions(new ComponentName(c,NotificationBridge.class));
            if(sessions.isEmpty()){store.event("No active phone media session");return;}
            MediaController selected=sessions.get(0);for(MediaController m:sessions)if(m.getPlaybackState()!=null&&m.getPlaybackState().getState()==PlaybackState.STATE_PLAYING){selected=m;break;}
            selected.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));selected.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_UP,key));store.event("Media action dispatched to phone");
        }catch(SecurityException e){store.event("Media controls need Android notification access");}
    }
}
