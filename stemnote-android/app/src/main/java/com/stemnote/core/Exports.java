package com.stemnote.core;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class Exports {
    private Exports(){}
    public static String safeName(String input){String s=input.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").replaceAll("[. ]+$","");if(s.isEmpty())return "Untitled";return s.substring(0,Math.min(80,s.length()));}
    private static final class Event {int tick,status,pitch,velocity;Event(int tick,int status,int pitch,int velocity){this.tick=tick;this.status=status;this.pitch=pitch;this.velocity=velocity;}}
    private static void vlq(OutputStream out,int value)throws IOException {int[] b=new int[4];int n=0;b[n++]=value&127;while((value>>>=7)>0)b[n++]=(value&127)|128;while(n>0)out.write(b[--n]);}
    private static void track(DataOutputStream out,byte[] bytes)throws IOException{out.writeBytes("MTrk");out.writeInt(bytes.length);out.write(bytes);}
    public static byte[] midi(AudioProject project,boolean[] selected,float[] gains)throws IOException {
        int count=0;for(boolean s:selected)if(s)count++;
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(buffer);
        out.writeBytes("MThd");out.writeInt(6);out.writeShort(1);out.writeShort(count+1);out.writeShort(480);
        track(out,new byte[]{0,(byte)255,81,3,7,(byte)161,32,0,(byte)255,47,0});
        for(int id=0;id<8;id++)if(selected[id]) {
            AudioProject.Channel channel=project.channels[id];Instrument ins=channel.instrument;
            ByteArrayOutputStream t=new ByteArrayOutputStream();byte[] name=ins.title.getBytes(StandardCharsets.UTF_8);
            t.write(0);t.write(255);t.write(3);vlq(t,name.length);t.write(name);t.write(0);t.write(192+ins.channel);t.write(ins.program);
            List<Event> events=new ArrayList<>();
            if(gains[id]>0)for(AudioProject.Note note:channel.notes){int velocity=Math.max(1,Math.min(127,Math.round(note.velocity*gains[id])));events.add(new Event((int)Math.round(note.start*960),144+ins.channel,note.pitch,velocity));events.add(new Event((int)Math.round((note.start+note.duration)*960),128+ins.channel,note.pitch,0));}
            events.sort(Comparator.comparingInt((Event e)->e.tick).thenComparingInt(e->e.status&240));
            int last=0;for(Event event:events){vlq(t,event.tick-last);t.write(event.status);t.write(event.pitch);t.write(event.velocity);last=event.tick;}
            t.write(new byte[]{0,(byte)255,47,0});track(out,t.toByteArray());
        }
        out.flush();return buffer.toByteArray();
    }
    public static float[] mix(AudioProject project,boolean[] selected,float[] gains,boolean synthesized) {
        float[] result=new float[project.length];
        for(int id=0;id<8;id++)if(selected[id]&&gains[id]>0){float[] audio=synthesized?synthesize(project.channels[id],project.length):project.channels[id].audio;for(int i=0;i<result.length;i++)result[i]+=audio[i]*gains[id];}
        float peak=0;for(float sample:result)peak=Math.max(peak,Math.abs(sample));if(peak>.95f)for(int i=0;i<result.length;i++)result[i]*=.95f/peak;return result;
    }
    private static float[] synthesize(AudioProject.Channel channel,int length){float[] result=new float[length];int id=channel.instrument.ordinal();for(AudioProject.Note note:channel.notes){int start=(int)(note.start*AudioProject.SAMPLE_RATE),count=(int)(note.duration*AudioProject.SAMPLE_RATE);double frequency=440*Math.pow(2,(note.pitch-69)/12.0);long random=note.pitch+start;for(int j=0;j<count&&start+j<length;j++){double t=j/(double)AudioProject.SAMPLE_RATE,envelope=Math.min(1,t/.012)*Math.min(1,(count-j)/(.04*AudioProject.SAMPLE_RATE)),sample;if(id==3){envelope*=Math.exp(-t*20);random=random*1664525+1013904223;sample=note.pitch==36?Math.sin(2*Math.PI*(80*t-40*t*t)):((random>>>16&65535)/32768.0-1)*.7;}else{sample=Math.sin(2*Math.PI*frequency*t);if(id==1||id==5)envelope*=Math.exp(-t*(id==1?4:2));if(id==2||id==7)sample+=Math.sin(4*Math.PI*frequency*t)*.3+Math.sin(6*Math.PI*frequency*t)*.15;if(id==0||id==6)sample+=Math.sin(4*Math.PI*frequency*t)*.15;}result[start+j]+=sample*envelope*(note.velocity/127.0)*.16;}}return result;}
    private static void little(OutputStream out,int value,int bytes)throws IOException{for(int i=0;i<bytes;i++)out.write(value>>>(8*i)&255);}
    public static void wav(OutputStream out,float[] samples)throws IOException {
        out.write("RIFF".getBytes(StandardCharsets.US_ASCII));little(out,36+samples.length*2,4);out.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));little(out,16,4);little(out,1,2);little(out,1,2);little(out,AudioProject.SAMPLE_RATE,4);little(out,AudioProject.SAMPLE_RATE*2,4);little(out,2,2);little(out,16,2);out.write("data".getBytes(StandardCharsets.US_ASCII));little(out,samples.length*2,4);
        byte[] block=new byte[8192];int used=0;for(float sample:samples){int v=Math.round(Math.max(-1,Math.min(1,sample))*32767);block[used++]=(byte)v;block[used++]=(byte)(v>>8);if(used==block.length){out.write(block);used=0;}}if(used>0)out.write(block,0,used);
    }
    public static void archive(OutputStream output,AudioProject project,boolean[] selected,float[] gains,String title,boolean includeWav)throws IOException {
        boolean any=false;for(boolean s:selected)any|=s;if(!any)throw new IllegalArgumentException("채널을 선택해주세요.");
        String base=safeName(title);
        try(ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8)) {
            for(int id=0;id<8;id++)if(selected[id]){boolean[] single=new boolean[8];single[id]=true;Instrument ins=project.channels[id].instrument;zip.putNextEntry(new ZipEntry(String.format(Locale.ROOT,"%s_%s_%02d.mid",base,ins.fileName,ins.channel+1)));zip.write(midi(project,single,gains));zip.closeEntry();}
            zip.putNextEntry(new ZipEntry(base+"_선택채널_멀티트랙.mid"));zip.write(midi(project,selected,gains));zip.closeEntry();
            if(includeWav){zip.putNextEntry(new ZipEntry(base+"_선택채널_믹스.wav"));wav(zip,mix(project,selected,gains,false));zip.closeEntry();}
            zip.putNextEntry(new ZipEntry(base+"_읽어주세요.txt"));String text="Stemnote Android\n입력: "+project.source+"\n주파수 기반 실험적 추정이며 정확한 악기 분리·채보를 보장하지 않습니다.\nMIDI 480 PPQ, 120 BPM 시간축(원곡 템포 아님), GM 드럼 채널 10.\nWAV: 모노, 22.05kHz, 16bit. 채널 레벨 적용, 필요시 피크 정규화.\n미리듣기 전체 볼륨은 저장에 적용되지 않습니다.\n";zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
    }
}
