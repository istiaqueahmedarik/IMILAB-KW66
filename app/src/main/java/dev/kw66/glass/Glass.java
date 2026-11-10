package dev.kw66.glass;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Native vector surfaces; no WebView, raster mockups or compositing dependency. */
public final class Glass {
    public static final int INK=Color.rgb(235,246,241), MUTED=Color.rgb(151,181,177), MINT=Color.rgb(168,230,211), DEEP=Color.rgb(8,25,29);
    public static Drawable panel(float radius,boolean accent){return new Drawable(){final Paint p=new Paint(3);final RectF r=new RectF();
        public void draw(Canvas c){r.set(getBounds());float x=r.left,y=r.top;
            p.setShader(new LinearGradient(x,y,r.right,r.bottom,accent?new int[]{0xc53c6867,0xd31b3e43,0xec122e34}:new int[]{0xc9314c50,0xd219353b,0xec102b31},null,Shader.TileMode.CLAMP));
            c.drawRoundRect(r,radius,radius,p);p.setShader(null);p.setColor(0x35d9fff1);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);r.inset(.7f,.7f);c.drawRoundRect(r,radius,radius,p);p.setStyle(Paint.Style.FILL);
        }public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    };}
    public static final class Backdrop extends View {
        final Paint p=new Paint(3);public Backdrop(Context c){super(c);}
        protected void onDraw(Canvas c){c.drawColor(DEEP);p.setShader(new RadialGradient(getWidth()*.9f,getHeight()*.12f,getWidth()*.95f,new int[]{0x803f7471,0x0045787b},null,Shader.TileMode.CLAMP));c.drawRect(0,0,getWidth(),getHeight(),p);
            p.setShader(new RadialGradient(0,getHeight()*.65f,getWidth()*.85f,new int[]{0x403a5667,0x00364b56},null,Shader.TileMode.CLAMP));c.drawRect(0,0,getWidth(),getHeight(),p);p.setShader(null);}
    }
    public static final class WatchOrb extends View {
        final Paint p=new Paint(3);int battery=-1;boolean connected;
        public WatchOrb(Context c,int b,boolean on){super(c);battery=b;connected=on;setContentDescription("KW66 watch. Battery "+(b<0?"unknown":b+" percent")+". "+(on?"Connected":"Disconnected"));}
        protected void onDraw(Canvas c){float size=Math.min(getWidth(),getHeight()),cx=getWidth()/2f,cy=getHeight()/2f,s=size/180;
            c.save();c.translate(cx,cy);c.scale(s,s);
            p.setShader(new RadialGradient(0,0,85,new int[]{0x404cf2bd,0x0038b893},null,Shader.TileMode.CLAMP));c.drawCircle(0,0,86,p);p.setShader(null);
            p.setColor(0xff17383c);c.drawRoundRect(-20,-85,20,85,13,13,p);
            p.setColor(0xff10292f);c.drawCircle(0,0,59,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0x6cc3f1e6);c.drawCircle(0,0,59,p);
            p.setColor(0x223b7975);p.setStrokeWidth(6);c.drawCircle(0,0,49,p);p.setStrokeCap(Paint.Cap.ROUND);
            if(battery>=0){p.setColor(MINT);c.drawArc(-49,-49,49,49,-90,battery*3.6f,false,p);}p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.BUTT);
            p.setTextAlign(Paint.Align.CENTER);p.setColor(INK);p.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));p.setTextSize(26);c.drawText("KW66",0,4,p);
            p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(9);p.setColor(MUTED);c.drawText(battery<0?"AWAITING WATCH":battery+"% BATTERY",0,22,p);
            p.setColor(connected?MINT:MUTED);c.drawCircle(46,42,5,p);c.restore();}
    }
}
