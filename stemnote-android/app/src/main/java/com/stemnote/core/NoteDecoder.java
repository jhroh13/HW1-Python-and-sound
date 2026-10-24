package com.stemnote.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Decode Basic Pitch note/onset probabilities; no instrument classification or drum inference. */
public final class NoteDecoder {
    public static final int HOP=256, PITCHES=88, MIDI_OFFSET=21;
    private static final double FRAME_SECONDS=HOP/(double)AudioProject.SAMPLE_RATE;
    private static final double MIN_SECONDS=.12;
    private NoteDecoder() {}

    public static List<AudioProject.Note> decode(float[][] frames,float[][] onsets,float[] audio) {
        if(frames.length!=onsets.length)throw new IllegalArgumentException("Probability lengths differ");
        List<AudioProject.Note> notes=new ArrayList<>();
        for(int pitch=0;pitch<PITCHES;pitch++) {
            if(Thread.currentThread().isInterrupted())throw new CancellationException();
            int start=-1,last=-1,misses=0;
            for(int time=0;time<frames.length;time++) {
                float probability=frames[time][pitch];
                float before=time==0?0:onsets[time-1][pitch];
                float after=time+1==frames.length?0:onsets[time+1][pitch];
                boolean onset=onsets[time][pitch]>=.5f&&onsets[time][pitch]>before&&onsets[time][pitch]>=after;
                if(start>=0&&onset&&(time-start)*FRAME_SECONDS>=MIN_SECONDS) {
                    emit(notes,pitch,start,Math.min(time,last+1),frames,audio);start=-1;
                }
                if(start<0&&(onset||probability>=.5f)) {start=time;last=time;misses=0;}
                if(start>=0) {
                    if(probability>=.3f) {last=time;misses=0;}
                    else misses++;
                    if(misses>3) {emit(notes,pitch,start,last+1,frames,audio);start=-1;}
                }
            }
            if(start>=0)emit(notes,pitch,start,last+1,frames,audio);
        }
        notes.sort(Comparator.comparingDouble((AudioProject.Note n)->n.start).thenComparingInt(n->n.pitch));
        return notes;
    }

    private static void emit(List<AudioProject.Note> out,int pitch,int start,int end,float[][] frames,float[] audio) {
        double begin=start*FRAME_SECONDS,finish=Math.min(audio.length/(double)AudioProject.SAMPLE_RATE,end*FRAME_SECONDS);
        if(finish-begin<MIN_SECONDS)return;
        double confidence=0;for(int t=start;t<end;t++)confidence+=frames[t][pitch];
        confidence/=Math.max(1,end-start);
        // Loudness contributes to dynamics. Posterior confidence is not treated as calibrated MIDI velocity.
        double energy=0;int count=0;
        int lo=Math.min(audio.length,(int)(begin*AudioProject.SAMPLE_RATE));
        int hi=Math.min(audio.length,(int)(finish*AudioProject.SAMPLE_RATE));
        for(int i=lo;i<hi;i+=16){energy+=audio[i]*audio[i];count++;}
        double rms=Math.sqrt(energy/Math.max(1,count));
        int velocity=(int)Math.round(127*Math.sqrt(Math.min(1,rms/.35))*Math.sqrt(Math.max(0,Math.min(1,confidence))));
        if(velocity>0)out.add(new AudioProject.Note(pitch+MIDI_OFFSET,begin,finish-begin,Math.max(1,Math.min(127,velocity))));
    }
}
