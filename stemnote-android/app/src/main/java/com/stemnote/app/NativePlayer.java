package com.stemnote.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import com.stemnote.core.AudioProject;
import java.util.concurrent.atomic.AtomicInteger;

final class NativePlayer {
    private final AtomicInteger generation=new AtomicInteger();
    private volatile AudioTrack track;
    private volatile float volume=.75f;
    void volume(float value){volume=value;AudioTrack current=track;if(current!=null)try{current.setVolume(value);}catch(IllegalStateException ignored){}}
    void stop(){generation.incrementAndGet();AudioTrack current=track;if(current!=null)try{current.pause();current.flush();}catch(IllegalStateException ignored){}}
    void play(float[] pcm,Runnable ended,java.util.function.Consumer<String> error){
        stop();int token=generation.get();
        new Thread(()->{
            AudioTrack audio=null;
            try {
                int min=AudioTrack.getMinBufferSize(AudioProject.SAMPLE_RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
                if(min<=0)throw new IllegalStateException("오디오 출력 형식을 지원하지 않습니다.");
                audio=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(AudioProject.SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(min,16384)).setTransferMode(AudioTrack.MODE_STREAM).build();
                if(generation.get()!=token)return;
                track=audio;audio.setVolume(volume);audio.play();short[] block=new short[4096];int written=0;
                for(int pos=0;pos<pcm.length&&generation.get()==token;){int n=Math.min(block.length,pcm.length-pos);for(int i=0;i<n;i++)block[i]=(short)Math.round(Math.max(-1,Math.min(1,pcm[pos+i]))*32767);int offset=0;while(offset<n&&generation.get()==token){int result=audio.write(block,offset,n-offset,AudioTrack.WRITE_BLOCKING);if(result<=0)throw new IllegalStateException("오디오 재생이 중단됐습니다.");offset+=result;written+=result;}pos+=n;}
                while(generation.get()==token&&audio.getPlaybackHeadPosition()<written)Thread.sleep(15);
            }catch(Exception e){if(generation.get()==token)error.accept(e.getMessage());}
            finally{if(audio!=null){if(track==audio)track=null;try{audio.stop();}catch(IllegalStateException ignored){}audio.release();}if(generation.get()==token)ended.run();}
        },"stemnote-playback").start();
    }
}
