package com.stemnote.core;

import java.util.ArrayList;
import java.util.List;

public final class AudioProject {
    public static final int SAMPLE_RATE=22050, MAX_SECONDS=120;
    public final String title, source, analysis;
    public final Channel[] channels;
    public final int length;
    public AudioProject(String title, String source, Channel[] channels) {
        this(title,source,channels,"주파수 기반 실험적 추정 · 학습 모델 없음");
    }
    public AudioProject(String title,String source,Channel[] channels,String analysis) {
        if(channels.length!=Instrument.COUNT) throw new IllegalArgumentException("Invalid channel count");
        this.title=title; this.source=source; this.channels=channels;this.analysis=analysis;
        int max=0;for(Channel channel:channels)max=Math.max(max,channel.audio.length);this.length=max;
    }
    public static final class Channel {
        public final Instrument instrument;
        public final float[] audio;
        public final List<Note> notes=new ArrayList<>();
        public Channel(Instrument instrument, int length) {this.instrument=instrument;audio=new float[length];}
        public Channel(Instrument instrument,float[] audio) {this.instrument=instrument;this.audio=audio;}
    }
    /** Files added as stems share the same zero-time origin. Existing target is replaced. */
    public static AudioProject replaceTrack(AudioProject previous,AudioProject addition,Instrument target) {
        Channel[] channels=previous.channels.clone();
        channels[target.ordinal()]=addition.channels[target.ordinal()];
        return new AudioProject(previous.title,previous.source+" · 트랙 추가",channels,
                "사용자가 지정한 악기 트랙 · 0초 정렬 · 자동 악기 분리 없음");
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
