package com.stemnote.app;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.SystemClock;
import com.stemnote.core.AudioProject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

/** Decode supported device audio codecs, downmix to mono, then resample. */
final class AudioDecoder {
    static float[] decode(Context context,Uri uri)throws IOException {
        MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
        try {
            extractor.setDataSource(context,uri,null);int track=-1;
            for(int i=0;i<extractor.getTrackCount();i++){String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/")){track=i;break;}}
            if(track<0)throw new IOException("지원되는 오디오 트랙이 없습니다.");
            extractor.selectTrack(track);MediaFormat format=extractor.getTrackFormat(track);
            if(format.containsKey(MediaFormat.KEY_DURATION)&&format.getLong(MediaFormat.KEY_DURATION)>120_050_000L)throw new IOException("최대 120초 파일을 지원합니다. 원하는 부분을 잘라서 선택해주세요.");
            int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            if(rate<8000||rate>192000||channels<1||channels>32)throw new IOException("지원되지 않는 오디오 형식입니다.");
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));codec.configure(format,null,null,0);codec.start();
            float[] mono=new float[rate*4];int count=0,encoding=AudioFormat.ENCODING_PCM_16BIT;boolean inputDone=false,outputDone=false;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long lastOutput=SystemClock.elapsedRealtime();
            while(!outputDone){
                if(Thread.currentThread().isInterrupted())throw new CancellationException();
                if(!inputDone){int slot=codec.dequeueInputBuffer(10000);if(slot>=0){ByteBuffer input=codec.getInputBuffer(slot);if(input==null)throw new IOException("오디오 입력 버퍼 오류");input.clear();int size=extractor.readSampleData(input,0);if(size<0){codec.queueInputBuffer(slot,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}else{codec.queueInputBuffer(slot,0,size,extractor.getSampleTime(),0);extractor.advance();}}}
                int slot=codec.dequeueOutputBuffer(info,10000);
                if(slot==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat out=codec.getOutputFormat();int newRate=out.getInteger(MediaFormat.KEY_SAMPLE_RATE);if(count>0&&newRate!=rate)throw new IOException("중간에 샘플레이트가 바뀌는 파일은 지원하지 않습니다.");rate=newRate;channels=out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);if(rate<8000||rate>192000||channels<1||channels>32)throw new IOException("지원되지 않는 출력 형식입니다.");encoding=out.containsKey(MediaFormat.KEY_PCM_ENCODING)?out.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;}
                else if(slot>=0){
                    lastOutput=SystemClock.elapsedRealtime();ByteBuffer out=codec.getOutputBuffer(slot);
                    if(encoding!=AudioFormat.ENCODING_PCM_16BIT&&encoding!=AudioFormat.ENCODING_PCM_FLOAT)throw new IOException("16bit 또는 Float PCM으로 변환 가능한 파일을 선택해주세요.");
                    int bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;
                    if(out!=null&&info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
                        out.position(info.offset);out.limit(info.offset+info.size);out.order(ByteOrder.LITTLE_ENDIAN);
                        while(out.remaining()>=bytes*channels){float sum=0;for(int c=0;c<channels;c++)sum+=bytes==4?out.getFloat():out.getShort()/32768f;
                            if(count>=rate*AudioProject.MAX_SECONDS)throw new IOException("최대 120초 파일을 지원합니다.");
                            if(count==mono.length)mono=Arrays.copyOf(mono,Math.min(rate*AudioProject.MAX_SECONDS,Math.max(count+1,mono.length*2)));
                            mono[count++]=Float.isFinite(sum)?sum/channels:0;
                        }
                    }
                    outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(slot,false);
                }
                if(SystemClock.elapsedRealtime()-lastOutput>15000)throw new IOException("오디오 해독 시간이 초과됐습니다. 다른 파일을 선택해주세요.");
            }
            return AudioProject.resample(Arrays.copyOf(mono,count),rate);
        } finally {if(codec!=null){try{codec.stop();}catch(IllegalStateException ignored){}codec.release();}extractor.release();}
    }
}
