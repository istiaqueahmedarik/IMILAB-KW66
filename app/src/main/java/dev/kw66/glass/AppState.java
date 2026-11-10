package dev.kw66.glass;
import android.content.*;
import android.app.*;
import android.os.Build;

public final class AppState {
    public static SharedPreferences prefs(Context c) {return c.getSharedPreferences("glass",Context.MODE_PRIVATE);}
    public static PendingIntent open(Context c) {return PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
    public static void channels(Context c) {
        NotificationManager n=c.getSystemService(NotificationManager.class);
        n.createNotificationChannel(new NotificationChannel("connection","Watch connection",NotificationManager.IMPORTANCE_LOW));
        n.createNotificationChannel(new NotificationChannel("reminders","Reminders & todos",NotificationManager.IMPORTANCE_HIGH));
        n.createNotificationChannel(new NotificationChannel("ringing","Repeating alarm",NotificationManager.IMPORTANCE_HIGH));
    }
    public static Notification notification(Context c,String channel,String title,String body,boolean ongoing) {
        return new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_watch).setContentTitle(title).setContentText(body)
            .setContentIntent(open(c)).setOngoing(ongoing).setCategory(channel.equals("ringing")?Notification.CATEGORY_ALARM:Notification.CATEGORY_STATUS).build();
    }
    public static void startWatch(Context c,String action) {
        if(Build.VERSION.SDK_INT>=31&&c.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED){Store.get(c).event("Bluetooth permission required; open Watch settings");return;}
        if(!prefs(c).getBoolean("connect",false)&&!"disconnect".equals(action)){Store.get(c).event("Connection is paused; queued messages will wait until reconnect");return;}
        try {c.startForegroundService(new Intent(c,WatchService.class).setAction(action));}
        catch(RuntimeException e) {Store.get(c).event("Android prevented background connection start; open app to reconnect");}
    }
    public static void update(Context c) {c.sendBroadcast(new Intent("dev.kw66.glass.UPDATE").setPackage(c.getPackageName()));}
}
