package dev.kw66.glass;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** App-private durable state. No notification text is included in diagnostics or AI context. */
public final class Store extends SQLiteOpenHelper {
    private static Store instance;
    public static synchronized Store get(Context c) {if(instance==null)instance=new Store(c.getApplicationContext());return instance;}
    private Store(Context c) {super(c,"kw66.db",null,1);setWriteAheadLoggingEnabled(true);}
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE samples (kind TEXT, local_time TEXT, value INTEGER, duration INTEGER, note TEXT, fields TEXT, received INTEGER, PRIMARY KEY(kind,local_time))");
        db.execSQL("CREATE TABLE packets (id INTEGER PRIMARY KEY, received INTEGER, channel TEXT, hex TEXT, note TEXT)");
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY, time INTEGER, text TEXT)");
        db.execSQL("CREATE TABLE outbox (id INTEGER PRIMARY KEY, text TEXT, type INTEGER, expires INTEGER, origin TEXT NOT NULL)");
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY, title TEXT, due INTEGER, repeat_minutes INTEGER, alarm INTEGER, done INTEGER DEFAULT 0, delivered INTEGER DEFAULT 0)");
    }
    public void onUpgrade(SQLiteDatabase db,int old,int next) {throw new IllegalStateException("Migration required");}
    public void event(String text) {
        ContentValues v=new ContentValues();v.put("time",System.currentTimeMillis());v.put("text",text);getWritableDatabase().insert("events",null,v);
        getWritableDatabase().execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT 200)");
    }
    public void receive(String channel,byte[] b,Protocol.Result r) {
        long now=System.currentTimeMillis();SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues p=new ContentValues();p.put("received",now);p.put("channel",channel);
            // Message ACKs/echoes and quick replies can contain private notification text.
            boolean redact=b.length>0 && ((b[0]&255)==0xc5 || (b[0]&255)==0x52);
            p.put("hex",redact?"[private message packet redacted]":Protocol.hex(b));p.put("note",r.status);db.insert("packets",null,p);
            for(Protocol.Sample s:r.samples) {
                ContentValues v=new ContentValues();v.put("kind",s.kind);v.put("local_time",s.localTime.equals("received")?"received:"+now:s.localTime);
                v.put("value",s.value);v.put("duration",s.duration);v.put("note",s.note);v.put("fields",new JSONObject(s.fields).toString());v.put("received",now);
                db.insertWithOnConflict("samples",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.execSQL("DELETE FROM packets WHERE id NOT IN (SELECT id FROM packets ORDER BY id DESC LIMIT 5000)");db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public List<ContentValues> rows(String sql,String...args) {
        List<ContentValues> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery(sql,args)) {
            while(c.moveToNext()) {ContentValues v=new ContentValues();for(int i=0;i<c.getColumnCount();i++)v.put(c.getColumnName(i),c.getString(i));out.add(v);}
        }return out;
    }
    public String latest(String kind) {
        List<ContentValues> r=rows("SELECT * FROM samples WHERE kind=? ORDER BY local_time DESC LIMIT 1",kind);
        return r.isEmpty()?"—":r.get(0).getAsString("value");
    }
    public ContentValues latestRecord(String kind){List<ContentValues> result=rows("SELECT * FROM samples WHERE kind=? ORDER BY local_time DESC LIMIT 1",kind);return result.isEmpty()?null:result.get(0);}
    public ContentValues latestHeart(){
        List<ContentValues> live=rows("SELECT * FROM samples WHERE kind='live_hr' ORDER BY received DESC LIMIT 1");
        if(!live.isEmpty()){long age=System.currentTimeMillis()-live.get(0).getAsLong("received");if(age>=0&&age<300_000)return live.get(0);}
        return latestRecord("hr");
    }
    public void enqueue(String text,int type) {enqueue(text,type,"custom");}
    public synchronized void enqueue(String text,int type,String origin) {enqueue(text,type,origin,15*60_000L);}
    public synchronized void enqueue(String text,int type,String origin,long lifetime) {
        if(origin.equals("alarm")||origin.startsWith("task:")||origin.startsWith("call:"))getWritableDatabase().delete("outbox","origin=?",new String[]{origin});
        ContentValues v=new ContentValues();v.put("text",text);v.put("type",type);v.put("origin",origin);v.put("expires",System.currentTimeMillis()+lifetime);
        getWritableDatabase().insert("outbox",null,v);getWritableDatabase().execSQL("DELETE FROM outbox WHERE id NOT IN (SELECT id FROM outbox ORDER BY id DESC LIMIT 50)");
    }
    public void clearOutbox() {getWritableDatabase().delete("outbox",null,null);}
    public void clearOrigin(String origin) {getWritableDatabase().delete("outbox","origin=?",new String[]{origin});}
    public ContentValues nextMessage() {
        getWritableDatabase().delete("outbox","expires < ?",new String[]{""+System.currentTimeMillis()});
        List<ContentValues> r=rows("SELECT * FROM outbox ORDER BY CASE WHEN origin LIKE 'call:%' THEN 0 ELSE 1 END,id LIMIT 1");return r.isEmpty()?null:r.get(0);
    }
    public void messageWritten(long id) {getWritableDatabase().delete("outbox","id=?",new String[]{""+id});}
    public long task(String title,long due,int repeat,boolean alarm) {
        ContentValues v=new ContentValues();v.put("title",title);v.put("due",due);v.put("repeat_minutes",repeat);v.put("alarm",alarm?1:0);return getWritableDatabase().insert("tasks",null,v);
    }
    public void complete(long id) {ContentValues v=new ContentValues();v.put("done",1);getWritableDatabase().update("tasks",v,"id=?",new String[]{""+id});}
    public void delivered(long id) {ContentValues v=new ContentValues();v.put("delivered",1);getWritableDatabase().update("tasks",v,"id=?",new String[]{""+id});}
    public void advance(long id,long due) {ContentValues v=new ContentValues();v.put("due",due);getWritableDatabase().update("tasks",v,"id=?",new String[]{""+id});}
    public String context(boolean health,boolean todos,boolean watch,SharedPreferences p) {
        JSONObject out=new JSONObject();try {
            if(health) {JSONArray a=new JSONArray();for(ContentValues v:rows("SELECT kind,local_time,value,note FROM samples ORDER BY received DESC LIMIT 24"))a.put(json(v));out.put("watch_reported_health_not_medical",a);}
            if(todos) {JSONArray a=new JSONArray();for(ContentValues v:rows("SELECT id,title,due FROM tasks WHERE done=0 ORDER BY due LIMIT 20"))a.put(json(v));out.put("todos",a);}
            if(watch) {out.put("watch_status",p.getString("status","Disconnected"));out.put("watch_battery",p.getInt("battery",-1));}
        }catch(JSONException e){throw new IllegalStateException(e);}return out.toString();
    }
    private static JSONObject json(ContentValues v) throws JSONException {JSONObject j=new JSONObject();for(String k:v.keySet())j.put(k,v.getAsString(k));return j;}
    public String export() throws JSONException {
        JSONObject o=new JSONObject();o.put("exported_at",System.currentTimeMillis());o.put("timestamp_contract","local_time is watch wall clock; received is phone epoch milliseconds");
        for(String table:new String[]{"samples","packets","events","tasks"}) {JSONArray a=new JSONArray();for(ContentValues v:rows("SELECT * FROM "+table))a.put(json(v));o.put(table,a);}return o.toString(2);
    }
}
