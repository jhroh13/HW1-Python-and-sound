package com.stemnote.core;

import java.util.ArrayList;
import java.util.List;

public final class AudioProject {
    public static final int SAMPLE_RATE=22050, MAX_SECONDS=120;
    public final String title, source;
    public final Channel[] channels;
    public final int length;
    public AudioProject(String title, String source, Channel[] channels) {
        if(channels.length!=8) throw new IllegalArgumentException("8 channels required");
        this.title=title; this.source=source; this.channels=channels; this.length=channels[0].audio.length;
    }
    public static final class Channel {
        public final Instrument instrument;
        public final float[] audio;
        public final List<Note> notes=new ArrayList<>();
        public Channel(Instrument instrument, int length) {this.instrument=instrument;audio=new float[length];}
    }
    public static final class Note {
        public final int pitch, velocity;
        public final double start, duration;
        public Note(int pitch, double start, double duration, int velocity) {
            this.pitch=pitch;this.start=start;this.duration=duration;this.velocity=velocity;
        }
    }
    public static float[] resample(float[] source, int rate) {
        if(rate<=0)throw new IllegalArgumentException("Invalid sample rate");
        if(source.length==0)return new float[0];
        if(rate==SAMPLE_RATE)return source;
        int n=(int)Math.min((long)SAMPLE_RATE*MAX_SECONDS,Math.round(source.length*(double)SAMPLE_RATE/rate));
        float[] result=new float[n];
        // Windowed low-pass sinc prevents aliasing when reducing the sample rate.
        double cutoff=Math.min(1.0,SAMPLE_RATE/(double)rate)*.92;
        for(int i=0;i<n;i++) {
            if((i&8191)==0&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            double at=i*(double)rate/SAMPLE_RATE,sum=0,weight=0;
            int center=(int)at;
            for(int k=center-16;k<=center+16;k++) {
                if(k<0||k>=source.length)continue;
                double d=at-k,window=.5+.5*Math.cos(Math.PI*d/17),x=Math.PI*d*cutoff;
                double w=(Math.abs(x)<1e-9?1:Math.sin(x)/x)*window;
                sum+=source[k]*w;weight+=w;
            }
            result[i]=(float)(sum/Math.max(weight,1e-9));
        }
        return result;
    }
}
