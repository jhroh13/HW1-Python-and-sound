package com.stemnote.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

final class WaveView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] peaks=new float[100];private final int color;private boolean active;
    WaveView(Context context,float[] samples,int color,boolean active){super(context);this.color=color;this.active=active;float max=.00001f;for(int b=0;b<peaks.length;b++){int lo=(int)(b*(long)samples.length/peaks.length),hi=(int)((b+1L)*samples.length/peaks.length);int stride=Math.max(1,(hi-lo)/80);for(int i=lo;i<hi;i+=stride)peaks[b]=Math.max(peaks[b],Math.abs(samples[i]));max=Math.max(max,peaks[b]);}for(int b=0;b<peaks.length;b++)peaks[b]/=max;setContentDescription("악기 오디오 파형");}
    void active(boolean value){active=value;invalidate();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);paint.setColor(color);paint.setAlpha(active?180:55);float width=getWidth()/(float)peaks.length;for(int i=0;i<peaks.length;i++){float h=Math.max(2,peaks[i]*getHeight()*.9f);canvas.drawRect(i*width,(getHeight()-h)/2,i*width+Math.max(1,width*.45f),(getHeight()+h)/2,paint);}}
}
