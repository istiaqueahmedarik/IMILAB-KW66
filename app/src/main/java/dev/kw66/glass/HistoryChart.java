package dev.kw66.glass;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.time.LocalDateTime;
import java.util.*;

/** One watch-local day. Missing samples stay gaps; sleep stage codes have no invented names. */
public final class HistoryChart extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<float[]> points=new ArrayList<>();
    private final String kind;
    private final float density;
    private float low,high;
    public HistoryChart(Context context){this(context,"hr",Collections.emptyList());}
    public HistoryChart(Context context,String kind,List<ContentValues> records){
        super(context);this.kind=kind;density=getResources().getDisplayMetrics().density;
        float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;
        for(ContentValues row:records){try{
            LocalDateTime t=LocalDateTime.parse(row.getAsString("local_time"));float minute=t.getHour()*60+t.getMinute();float value=row.getAsInteger("value");
            points.add(new float[]{minute,value});min=Math.min(min,value);max=Math.max(max,value);
        }catch(RuntimeException ignored){}}
        points.sort(Comparator.comparingDouble(p->p[0]));
        if(points.isEmpty()){low=0;high=100;}
        else if(kind.equals("steps")){low=0;high=Math.max(100,(float)Math.ceil(max/100)*100);}
        else if(kind.equals("spo2")){low=Math.max(0,(float)Math.floor((min-2)/5)*5);high=100;}
        else if(kind.equals("sleep")){low=Math.max(0,min-1);high=low+Math.max(4,(float)Math.ceil((max+1-low)/4)*4);}
        else {low=Math.max(0,(float)Math.floor((min-10)/10)*10);high=Math.max(low+20,(float)Math.ceil((max+10)/10)*10);}
        setContentDescription(kind+" history chart, "+points.size()+" recorded samples. "+(points.isEmpty()?"No records for this day.":"Range "+(int)min+" to "+(int)max+". Times use the watch local clock."));
    }
    private float d(float value){return value*density;}
    protected void onDraw(Canvas canvas){super.onDraw(canvas);
        float left=d(38),right=getWidth()-d(10),top=d(16),bottom=getHeight()-d(40),width=right-left,height=bottom-top;
        if(width<=0||height<=0)return;
        paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(d(11));paint.setStrokeWidth(d(1));
        for(int i=0;i<=4;i++){
            float y=bottom-height*i/4;paint.setColor(0x244e7978);canvas.drawLine(left,y,right,y,paint);
            paint.setColor(Glass.MUTED);paint.setTextAlign(Paint.Align.RIGHT);float tick=low+(high-low)*i/4;canvas.drawText(String.format(Locale.ROOT,tick==(int)tick?"%.0f":"%.1f",tick),left-d(8),y+d(4),paint);
        }
        String[] ticks={"00","06","12","18","24"};
        for(int i=0;i<5;i++){paint.setTextAlign(i==0?Paint.Align.LEFT:i==4?Paint.Align.RIGHT:Paint.Align.CENTER);paint.setColor(Glass.MUTED);canvas.drawText(ticks[i],left+width*i/4,bottom+d(24),paint);}
        if(points.isEmpty()){paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(d(14));canvas.drawText("No recorded samples",left+width/2,top+height/2,paint);return;}
        float previousX=0,previousY=0,previousMinute=-1000;
        for(float[] point:points){
            float x=left+width*point[0]/1440,y=bottom-height*(point[1]-low)/(high-low);
            paint.setColor(Glass.MINT);
            if(kind.equals("steps")){
                float barWidth=Math.max(d(2),width/24*.68f);if(point[1]==0)canvas.drawCircle(x+barWidth/2,bottom,d(2),paint);else canvas.drawRoundRect(x,y,Math.min(right,x+barWidth),bottom,d(3),d(3),paint);
            }else{
                if(!kind.equals("sleep")&&point[0]-previousMinute<=10){paint.setStrokeWidth(d(2));canvas.drawLine(previousX,previousY,x,y,paint);}
                canvas.drawCircle(x,y,d(kind.equals("sleep")?4:2.5f),paint);
            }
            previousX=x;previousY=y;previousMinute=point[0];
        }
    }
}
