package dev.kw66.glass;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.time.LocalDateTime;
import java.util.*;

/** A single owner and FIFO for all GATT operations, including CCCD subscriptions. */
public final class WatchService extends Service {
    private final Handler h=new Handler(Looper.getMainLooper());
    private final ArrayDeque<Op> ops=new ArrayDeque<>();
    private BluetoothGatt gatt;private BluetoothGattCharacteristic writer;private Op active;
    private Store store;private SharedPreferences prefs;private final Protocol decoder=new Protocol();
    private boolean ready=false,stopping=false,fetchSingle=false,callEndPending=false;private int failures=0,fetch=-1,pendingFetch=-1;
    private final Runnable opTimeout=()->lost("Bluetooth operation timed out");
    private final Runnable connectTimeout=()->lost("Connection timed out");
    private final Runnable reconnect=this::connect;
    private final Runnable fetchTimeout=()->{if(fetch>=0){store.event(Protocol.FETCH_NAMES[fetch]+" history incomplete: no end marker; sync paused");fetch=-1;decoder.resetSleep();status("Connected · history incomplete");pumpMessages();}};
    private final BroadcastReceiver radio=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(i.getAction())){if(i.getIntExtra(BluetoothAdapter.EXTRA_STATE,0)==BluetoothAdapter.STATE_ON)connect();else if(i.getIntExtra(BluetoothAdapter.EXTRA_STATE,0)==BluetoothAdapter.STATE_OFF)lost("Bluetooth is off");}}};
    private static final class Op {
        BluetoothGattDescriptor descriptor;byte[] bytes;Runnable done;
        Op(BluetoothGattDescriptor d,byte[] b,Runnable r){descriptor=d;bytes=b;done=r;}
    }
    public void onCreate(){super.onCreate();store=Store.get(this);prefs=AppState.prefs(this);AppState.channels(this);
        try{startForeground(66,AppState.notification(this,"connection","KW66 Glass","Starting watch connection",true));}catch(SecurityException e){store.event("Bluetooth foreground permission unavailable");stopping=true;stopSelf();}
        registerReceiver(radio,new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }
    public int onStartCommand(Intent i,int flags,int startId) {
        String a=i==null?"connect":i.getAction();
        if(stopping)return START_NOT_STICKY;
        if("disconnect".equals(a)){prefs.edit().putBoolean("connect",false).apply();stopping=true;close();status("Disconnected by you");stopSelf();return START_NOT_STICKY;}
        if(!allowed() || !prefs.getBoolean("connect",false)){status("Open app to grant Bluetooth permission and connect");stopSelf();return START_NOT_STICKY;}
        if("reconnect".equals(a)){close();stopping=false;}
        connect();
        if("call_end".equals(a)){callEndPending=true;}
        if("sync".equals(a))sync(0,false);
        else if(a!=null && a.startsWith("sync_")){String k=a.substring(5);int n=k.equals("steps")?0:k.equals("hr")?1:k.equals("spo2")?2:k.equals("sleep")?3:-1;if(n>=0)sync(n,true);}
        else if("time".equals(a))command(Protocol.time(LocalDateTime.now()),"Time sync requested");
        else if("find".equals(a))command(new byte[]{(byte)0xab,0,0,0,1,2,7,1},"Find watch requested");
        else if("vibrate".equals(a))command(Protocol.VIBRATE,"Watch vibration requested");
        pumpMessages();return START_STICKY;
    }
    private boolean allowed(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    private void status(String s){prefs.edit().putString("status",s).apply();getSystemService(NotificationManager.class).notify(66,AppState.notification(this,"connection","KW66 Glass",s,true));AppState.update(this);}
    private void connect(){
        h.removeCallbacks(reconnect);if(stopping||gatt!=null||!allowed()||!prefs.getBoolean("connect",false))return;
        BluetoothManager bm=getSystemService(BluetoothManager.class);BluetoothAdapter a=bm==null?null:bm.getAdapter();
        if(a==null||!a.isEnabled()){status("Bluetooth is off");return;}
        String address=prefs.getString("address","");if(!BluetoothAdapter.checkBluetoothAddress(address)){status("Choose your KW66 in Watch settings");return;}
        try{status("Connecting to KW66…");gatt=a.getRemoteDevice(address).connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE);h.postDelayed(connectTimeout,30_000);}
        catch(SecurityException e){lost("Bluetooth connection permission was revoked");}
        catch(RuntimeException e){lost("Connection unavailable; check Bluetooth permissions");}
    }
    private void close(){h.removeCallbacks(opTimeout);h.removeCallbacks(connectTimeout);h.removeCallbacks(fetchTimeout);h.removeCallbacks(reconnect);ops.clear();active=null;ready=false;writer=null;fetch=-1;decoder.resetSleep();
        BluetoothGatt old=gatt;gatt=null;if(old!=null){try{old.disconnect();old.close();}catch(SecurityException ignored){}}
    }
    private void lost(String why){close();store.event(why);status(why+" · will reconnect");if(!stopping && prefs.getBoolean("connect",false)){long delay=Math.min(120_000,2_000L << Math.min(failures++,6));h.postDelayed(reconnect,delay);}}
    private void add(byte[] bytes,Runnable done){ops.add(new Op(null,bytes.clone(),done));next();}
    private void next(){
        if(active!=null||gatt==null)return;active=ops.poll();if(active==null){pumpMessages();return;}
        boolean ok=false;
        try{if(active.descriptor!=null){active.descriptor.setValue(active.bytes);ok=gatt.writeDescriptor(active.descriptor);}
            else if(writer!=null){writer.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);writer.setValue(active.bytes);ok=gatt.writeCharacteristic(writer);}}
        catch(SecurityException e){lost("Bluetooth write permission was revoked");return;}
        catch(RuntimeException ignored){}
        if(!ok){lost("GATT operation could not start; pending message may be incomplete");return;}h.postDelayed(opTimeout,10_000);
    }
    private void completed(int code){h.removeCallbacks(opTimeout);if(active==null)return;
        if(code!=BluetoothGatt.GATT_SUCCESS){lost("GATT operation failed ("+code+"); message may be incomplete");return;}
        Runnable done=active.done;active=null;if(done!=null)done.run();next();
    }
    private void command(byte[] b,String label){if(!ready){store.event(label+": watch not ready; retry from app");return;}if(fetch>=0){store.event(label+": sync in progress; retry after it finishes");return;}store.event(label);add(b,()->store.event("GATT write completed; watch action not independently confirmed"));}
    private void sync(int first,boolean single){if(!ready){pendingFetch=first;fetchSingle=single;store.event("Sync deferred until connection is ready");return;}if(fetch>=0)return;fetchSingle=single;fetch=first;fetchNext();}
    private void fetchNext(){h.removeCallbacks(fetchTimeout);if(fetch>=Protocol.FETCH.length){fetch=-1;status("Connected · sync ended");prefs.edit().putLong("last_sync",System.currentTimeMillis()).apply();pumpMessages();return;}
        decoder.resetSleep();store.event("BEGIN "+Protocol.FETCH_NAMES[fetch]+" history");status("Syncing "+Protocol.FETCH_NAMES[fetch]+"…");
        int expected=fetch;add(Protocol.FETCH[fetch],()->{if(fetch==expected){h.removeCallbacks(fetchTimeout);h.postDelayed(fetchTimeout,45_000);}});
    }
    private void pumpMessages(){
        if(!ready||active!=null||!ops.isEmpty())return;
        if(callEndPending){callEndPending=false;if(Calls.watchMayEnd()){add(new byte[]{(byte)0xc1,4},null);return;}}
        ContentValues m=store.nextMessage();if(m==null)return;
        boolean call=m.getAsString("origin").startsWith("call:");if(fetch>=0&&!call)return;
        if(fetch>=0&&call){h.removeCallbacks(fetchTimeout);fetch=-1;store.event("History paused for a live call alert; data may be incomplete");}
        List<byte[]> frames;try{frames=Protocol.message(m.getAsString("text"),m.getAsInteger("type"));}catch(IllegalArgumentException e){store.messageWritten(m.getAsLong("id"));return;}
        // Claim once before writing. Interrupted multipart messages are never blindly replayed.
        store.messageWritten(m.getAsLong("id"));store.event("BEGIN watch message (content private)");
        for(byte[] f:frames)ops.add(new Op(null,f,null));
        ops.add(new Op(null,Protocol.VIBRATE.clone(),()->{store.event("Message GATT writes completed; check watch display");AppState.update(this);}));next();
    }
    private void packet(BluetoothGatt source,BluetoothGattCharacteristic c,byte[] b){
        if(source!=gatt)return;String channel=c.getUuid().toString().substring(4,8);Protocol.Result r=decoder.decode(channel,b);store.receive(channel,b,r);
        if(r.battery>=0)prefs.edit().putInt("battery",r.battery).apply();if(r.firmware!=null)prefs.edit().putString("firmware",r.firmware).apply();
        if(r.end==fetch && fetch>=0){store.event("END "+Protocol.FETCH_NAMES[fetch]+" history");fetch=fetchSingle?Protocol.FETCH.length:fetch+1;fetchNext();}
        if(b.length>=2 && (b[0]&255)==0xd1)WatchActions.event(this,b);
        AppState.update(this);
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        public void onConnectionStateChange(BluetoothGatt g,int code,int state){h.post(()->{if(g!=gatt)return;if(code==0&&state==BluetoothProfile.STATE_CONNECTED){status("Discovering watch services…");try{if(!g.discoverServices())lost("Service discovery did not start");}catch(SecurityException e){lost("Bluetooth discovery permission was revoked");}}else if(state==BluetoothProfile.STATE_DISCONNECTED||code!=0)lost("Watch disconnected ("+code+")");});}
        public void onServicesDiscovered(BluetoothGatt g,int code){h.post(()->{
            if(g!=gatt)return;if(code!=0){lost("Service discovery failed");return;}
            BluetoothGattService service=g.getService(uuid("55ff"));writer=service==null?null:service.getCharacteristic(uuid("33f1"));
            if(writer==null || (writer.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE)==0){lost("KW66 command service not found");return;}
            boolean required=false;
            for(BluetoothGattService s:g.getServices()){
                if(!s.getUuid().equals(uuid("55ff"))&&!s.getUuid().equals(uuid("56ff"))&&!s.getUuid().equals(uuid("180f")))continue;
                for(BluetoothGattCharacteristic c:s.getCharacteristics()){
                    int properties=c.getProperties();if((properties&(BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_INDICATE))==0)continue;
                    BluetoothGattDescriptor d=c.getDescriptor(uuid("2902"));if(d==null)continue;
                    try{if(!g.setCharacteristicNotification(c,true))continue;}catch(SecurityException e){lost("Bluetooth subscription permission was revoked");return;}
                    if(c.getUuid().equals(uuid("33f2")))required=true;
                    ops.add(new Op(d,(properties&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0?BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE:BluetoothGattDescriptor.ENABLE_INDICATION_VALUE,null));
                }
            }
            if(!required){lost("Required 33F2 notification channel unavailable");return;}
            Op last=ops.peekLast();if(last==null){lost("No subscribed data channels");return;}
            last.done=()->{ready=true;failures=0;h.removeCallbacks(connectTimeout);status("Connected");store.event("KW66 connected; subscription writes completed");add(new byte[]{(byte)0xa1},null);add(new byte[]{(byte)0xa2},()->{if(pendingFetch>=0){int first=pendingFetch;pendingFetch=-1;sync(first,fetchSingle);}else pumpMessages();});};next();
        });}
        public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){h.post(()->{if(g==gatt)completed(status);});}
        public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){h.post(()->{if(g==gatt)completed(status);});}
        public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){byte[] b=c.getValue().clone();h.post(()->packet(g,c,b));}
        public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){byte[] b=value.clone();h.post(()->packet(g,c,b));}
    };
    public static UUID uuid(String shortId){return UUID.fromString("0000"+shortId+"-0000-1000-8000-00805f9b34fb");}
    public IBinder onBind(Intent i){return null;}
    public void onDestroy(){stopping=true;close();unregisterReceiver(radio);super.onDestroy();}
}
