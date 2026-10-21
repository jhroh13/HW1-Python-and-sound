import com.stemnote.core.*;
import com.stemnote.core.Instrument;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.sound.midi.*;
import javax.sound.sampled.*;

public final class CoreTest {
    static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
    static float[] sine(int rate,int length,double frequency){float[] a=new float[length];for(int i=0;i<length;i++)a[i]=(float)(.3*Math.sin(2*Math.PI*frequency*i/rate)*Math.min(1,Math.min(i/300.0,(length-i)/300.0)));return a;}
    static AudioProject fixture(){AudioProject.Channel[] channels=new AudioProject.Channel[8];for(int i=0;i<8;i++)channels[i]=new AudioProject.Channel(Instrument.values()[i],22050);channels[0].notes.add(new AudioProject.Note(69,.5,.5,90));channels[3].notes.add(new AudioProject.Note(36,0,.1,100));channels[0].audio[0]=.2f;channels[3].audio[0]=.1f;return new AudioProject("테스트곡","마이크 캡처",channels);}
    public static void main(String[] args)throws Exception {
        Analyzer analyzer=new Analyzer();AudioProject silent=analyzer.analyze(new float[22050],"silence","test",p->{});for(AudioProject.Channel channel:silent.channels){require(channel.notes.isEmpty(),"silence has no notes");for(float x:channel.audio)require(x==0,"silent stem");}
        float[] input=sine(22050,22050,440);AudioProject result=analyzer.analyze(input,"A4","test",p->{});double error=0;boolean a4=false;
        for(int i=0;i<input.length;i++){double sum=0;for(AudioProject.Channel c:result.channels){require(Float.isFinite(c.audio[i]),"finite audio");sum+=c.audio[i];}error+=Math.abs(sum-input[i]);}
        error/=input.length;require(error<1e-5,"spectral reconstruction: "+error);
        for(AudioProject.Channel c:result.channels)for(AudioProject.Note n:c.notes){a4|=n.pitch==69&&n.duration>.5;require(n.start>=0&&n.duration>0&&n.start+n.duration<=1.001,"note bounds");}require(a4,"sustained A4");
        for(int rate:new int[]{22050,44100,48000}){float[] wave=sine(rate,rate,440);require(Math.abs(Pitch.detect(wave,Math.min(wave.length,4096),rate)-440)<3,"live pitch "+rate);float[] resampled=AudioProject.resample(wave,rate);require(resampled.length==22050,"resampling duration");require(Math.abs(Pitch.detect(resampled,4096,22050)-440)<3,"resampled pitch");}
        require(Pitch.detect(new float[2048],2048,22050)==0,"silent pitch");
        Thread.currentThread().interrupt();boolean canceled=false;try{analyzer.analyze(input,"cancel","test",p->{});}catch(java.util.concurrent.CancellationException expected){canceled=true;}finally{Thread.interrupted();}require(canceled,"analysis cancellation");
        AudioProject p=fixture();boolean[] chosen={true,false,false,true,false,false,false,false};float[] gains=new float[8];Arrays.fill(gains,.8f);
        byte[] midi=Exports.midi(p,chosen,gains);Sequence sequence=MidiSystem.getSequence(new ByteArrayInputStream(midi));require(sequence.getResolution()==480&&sequence.getTracks().length==3,"MIDI tracks/PPQ");boolean voice=false,drum=false;int noteCount=0;
        for(Track track:sequence.getTracks())for(int i=0;i<track.size();i++){MidiEvent event=track.get(i);if(event.getMessage() instanceof ShortMessage){ShortMessage m=(ShortMessage)event.getMessage();if(m.getCommand()==ShortMessage.NOTE_ON){noteCount++;voice|=m.getChannel()==0&&m.getData1()==69&&m.getData2()==72&&event.getTick()==480;drum|=m.getChannel()==9&&m.getData1()==36&&m.getData2()==80&&event.getTick()==0;}}}require(voice&&drum&&noteCount==2,"note timing, velocity, GM drums");
        gains[0]=0;Sequence muted=MidiSystem.getSequence(new ByteArrayInputStream(Exports.midi(p,chosen,gains)));for(int i=0;i<muted.getTracks()[1].size();i++)require(!(muted.getTracks()[1].get(i).getMessage() instanceof ShortMessage)||((ShortMessage)muted.getTracks()[1].get(i).getMessage()).getCommand()!=ShortMessage.NOTE_ON,"zero gain mutes MIDI");gains[0]=.8f;
        float[] mix=Exports.mix(p,chosen,gains,false);require(Math.abs(mix[0]-.24)<1e-6,"selection and gain mix");float[] synth=Exports.mix(p,chosen,gains,true);boolean nonzero=false;for(float x:synth){require(Float.isFinite(x)&&Math.abs(x)<=.951,"synth bounds");nonzero|=Math.abs(x)>.01;}require(nonzero,"MIDI preview synthesis");
        ByteArrayOutputStream wav=new ByteArrayOutputStream();Exports.wav(wav,mix);try(AudioInputStream stream=AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav.toByteArray()))){require(stream.getFormat().getSampleRate()==22050&&stream.getFormat().getChannels()==1&&stream.getFormat().getSampleSizeInBits()==16,"WAV format");require(stream.getFrameLength()==22050,"WAV duration");}
        require(Exports.safeName(" ../a:b*? ").equals(".._a_b__"),"safe filename");require(Exports.safeName("...").equals("Untitled"),"empty filename");
        ByteArrayOutputStream zip=new ByteArrayOutputStream();Exports.archive(zip,p,chosen,gains,"저장이름",true);Set<String> names=new HashSet<>();
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(zip.toByteArray()),StandardCharsets.UTF_8)){for(ZipEntry entry;(entry=z.getNextEntry())!=null;){names.add(entry.getName());byte[] data=z.readAllBytes();if(entry.getName().endsWith(".mid"))MidiSystem.getSequence(new ByteArrayInputStream(data));z.closeEntry();}}
        require(names.equals(new HashSet<>(Arrays.asList("저장이름_보이스_01.mid","저장이름_드럼_10.mid","저장이름_선택채널_멀티트랙.mid","저장이름_선택채널_믹스.wav","저장이름_읽어주세요.txt"))),"ZIP selected files and Korean names");
        boolean emptyRejected=false;try{Exports.archive(new ByteArrayOutputStream(),p,new boolean[8],gains,"none",false);}catch(IllegalArgumentException expected){emptyRejected=true;}require(emptyRejected,"empty export rejected");
        System.out.println("PASS: native FFT, silence, A4, reconstruction MAE="+error+", cancellation, resampling, live pitch, MIDI timing/channels/velocity/mute, WAV, synthesis, selected ZIP and Korean filenames.");
    }
}
