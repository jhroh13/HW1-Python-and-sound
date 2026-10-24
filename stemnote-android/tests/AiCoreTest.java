import com.stemnote.core.*;
import java.util.*;

public final class AiCoreTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static float[] audio(int length,float level){float[] a=new float[length];for(int i=0;i<length;i++)a[i]=(float)(level*Math.sin(2*Math.PI*440*i/22050));return a;}
    static void fill(float[][] a,int lo,int hi,int pitch,float value){for(int t=lo;t<hi;t++)a[t][pitch-21]=value;}
    public static void main(String[] args) {
        int count=220;float[][] frames=new float[count][88],onsets=new float[count][88];
        float[] samples=audio(count*256,.15f);
        require(NoteDecoder.decode(frames,onsets,samples).isEmpty(),"zero posteriors produce no notes");
        fill(frames,10,60,60,.8f);fill(frames,25,65,64,.75f);
        onsets[10][60-21]=.9f;onsets[25][64-21]=.9f;
        // A short false event must be removed; a brief confidence dip must not split a sustained note.
        fill(frames,15,19,72,.9f);onsets[15][72-21]=.9f;
        fill(frames,35,37,60,0);
        List<AudioProject.Note> notes=NoteDecoder.decode(frames,onsets,samples);
        require(notes.size()==2,"polyphony, short-event rejection and gap bridging");
        require(notes.get(0).pitch==60&&Math.abs(notes.get(0).start-10*256.0/22050)<1e-8,"sample-based onset");
        require(Math.abs(notes.get(0).duration-50*256.0/22050)<1e-8,"sample-based offset");
        int loud=notes.get(0).velocity;
        int soft=NoteDecoder.decode(frames,onsets,audio(samples.length,.03f)).get(0).velocity;
        require(loud>soft&&soft>0,"velocity responds to loudness");
        fill(frames,10,100,60,.8f);onsets[60][60-21]=.9f;
        notes=NoteDecoder.decode(frames,onsets,samples);
        require(notes.stream().filter(n->n.pitch==60).count()==2,"repeated onsets split the same pitch");
        // Long tracks keep exact sample timing at every retained-window seam.
        int fullCount=ModelWindow.frameCount(120*22050);float[][] full=new float[fullCount][88];
        for(int first=0;first<fullCount;first+=ModelWindow.KEPT){
            float[][][] window=new float[1][ModelWindow.FRAMES][88];
            for(int local=0;local<ModelWindow.FRAMES;local++)window[0][local][0]=ModelWindow.sampleStart(first)+local*256;
            ModelWindow.copy(window,full,first);
        }
        for(int t=0;t<fullCount;t++)require(full[t][0]==t*256,"no accumulated window timing drift at "+t);
        float[] tail=audio(22050,.15f);frames=new float[ModelWindow.frameCount(tail.length)][88];onsets=new float[frames.length][88];
        fill(frames,50,frames.length,69,.8f);onsets[50][69-21]=.9f;
        notes=NoteDecoder.decode(frames,onsets,tail);
        require(notes.size()==1&&notes.get(0).start+notes.get(0).duration<=1.0,"last note clipped to file duration");
        AudioProject.Channel[] ch=new AudioProject.Channel[Instrument.COUNT];
        for(Instrument i:Instrument.values())ch[i.ordinal()]=new AudioProject.Channel(i,0);
        ch[Instrument.BASS.ordinal()]=new AudioProject.Channel(Instrument.BASS,new float[100]);
        AudioProject old=new AudioProject("song","file",ch,"AI");
        AudioProject.Channel[] added=ch.clone();added[Instrument.PIANO.ordinal()]=new AudioProject.Channel(Instrument.PIANO,new float[200]);
        AudioProject merged=AudioProject.replaceTrack(old,new AudioProject("other","file",added,"AI"),Instrument.PIANO);
        require(merged.length==200&&old.length==100&&merged.channels[4]==old.channels[4],"stem replacement preserves existing channel and project");
        boolean[] selected=new boolean[Instrument.COUNT];Arrays.fill(selected,true);float[] gains=new float[Instrument.COUNT];Arrays.fill(gains,1);
        require(Exports.mix(merged,selected,gains,false).length==200,"mixed stem lengths are zero padded");
        System.out.println("PASS: AI note decoding, polyphony, repeat onsets, duration/noise filter, dynamics, exact 120s window alignment, track replacement and unequal stem lengths.");
    }
}
