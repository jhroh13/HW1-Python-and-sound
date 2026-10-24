package com.stemnote.app;

import android.test.InstrumentationTestCase;
import com.stemnote.core.*;
import java.util.List;

/** Runs the packaged TFLite model through the same Java pipeline as the real app. */
@SuppressWarnings("deprecation")
public final class BasicPitchDeviceTest extends InstrumentationTestCase {
    public void testNativeScreenStarts()throws Exception {
        android.content.Context context=getInstrumentation().getTargetContext();
        android.content.Intent intent=new android.content.Intent(context,MainActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.Activity activity=getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();assertNotNull(activity);
        getInstrumentation().runOnMainSync(activity::finish);
    }
    private AudioProject transcribe(float[] audio)throws Exception {
        return new BasicPitchTranscriber().analyze(getInstrumentation().getTargetContext(),audio,"test","generated test",Instrument.OTHER,p->{});
    }
    private static float[] pluck(int pitch,double start,double duration,double level,int seconds) {
        float[] audio=new float[seconds*22050];double frequency=440*Math.pow(2,(pitch-69)/12.0);
        for(int i=(int)(start*22050);i<audio.length&&i<(start+duration)*22050;i++) {
            double t=i/22050.0-start,envelope=Math.min(1,t/.015)*Math.min(1,(duration-t)/.06)*Math.exp(-t*.6);
            double value=0;for(int harmonic=1;harmonic<=6;harmonic++)value+=Math.sin(2*Math.PI*frequency*harmonic*t)/(harmonic*harmonic);
            audio[i]=(float)(value*envelope*level);
        }
        return audio;
    }
    public void testSilence()throws Exception {
        AudioProject p=transcribe(new float[3*22050]);
        for(AudioProject.Channel c:p.channels)assertTrue("silence must not produce MIDI notes",c.notes.isEmpty());
    }
    public void testKnownNoteAndWindowSeam()throws Exception {
        // The note crosses the first retained-window boundary (~1.65s).
        AudioProject p=transcribe(pluck(69,.45,2.1,.3,4));
        List<AudioProject.Note> notes=p.channels[Instrument.OTHER.ordinal()].notes;
        for(AudioProject.Note n:notes)System.out.println("MODEL_NOTE pitch="+n.pitch+" start="+n.start+" end="+(n.start+n.duration)+" velocity="+n.velocity);
        assertTrue("known A4 must be detected across the window seam",notes.stream().anyMatch(n->n.pitch==69&&n.duration>1.5&&Math.abs(n.start-.45)<.15));
        assertTrue("no fabricated drum notes",p.channels[Instrument.DRUMS.ordinal()].notes.isEmpty());
        for(AudioProject.Note n:notes)assertTrue("note bounds",n.start>=0&&n.start+n.duration<=4);
    }
    public void testChord()throws Exception {
        float[] audio=pluck(60,.3,1.3,.12,3);
        for(int pitch:new int[]{64,67}){float[] part=pluck(pitch,.3,1.3,.12,3);for(int i=0;i<audio.length;i++)audio[i]+=part[i];}
        List<AudioProject.Note> notes=transcribe(audio).channels[Instrument.OTHER.ordinal()].notes;
        for(int pitch:new int[]{60,64,67})assertTrue("known triad pitch "+pitch,notes.stream().anyMatch(n->n.pitch==pitch&&n.duration>.7));
    }
}
