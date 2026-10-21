package com.stemnote.app;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import com.stemnote.core.AudioProject;
import com.stemnote.core.Pitch;
import java.util.Arrays;

/** Foreground-only microphone capture. All terminal paths release AudioRecord. */
final class NativeCapture {
    interface Listener {void started();void level(int samples,float rms,float peak,double pitch);void completed(float[] pcm,boolean discarded,String error);}
    private final Listener listener;
    private volatile AudioRecord device;
    private volatile boolean requestedStop,discard;
    private Thread thread;
    NativeCapture(Listener listener){this.listener=listener;}
    synchronized void start(){
        if(thread!=null)throw new IllegalStateException("이미 캡처 중입니다.");
        requestedStop=false;discard=false;
        thread=new Thread(this::capture,"stemnote-microphone");thread.start();
    }
    void finish(boolean cancel){if(cancel)discard=true;requestedStop=true;AudioRecord current=device;if(current!=null)try{current.stop();}catch(IllegalStateException ignored){}}
    @android.annotation.SuppressLint("MissingPermission") // Activity checks runtime permission; SecurityException is handled below.
    private void capture(){
        float[] pcm=null;int used=0;String error=null;
        try {
            int rate=AudioProject.SAMPLE_RATE;
            int min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IllegalStateException("이 기기는 22.05kHz 마이크 입력을 지원하지 않습니다.");
            AudioRecord record=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min*2,16384));
            device=record;
            if(record.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("마이크 초기화 실패. 다른 통화/녹음 앱을 종료해주세요.");
            pcm=new float[rate*AudioProject.MAX_SECONDS];short[] block=new short[4096];float[] recent=new float[4096];
            if(!requestedStop){record.startRecording();if(record.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("마이크를 시작할 수 없습니다.");listener.started();}
            int lastUpdate=0;long began=SystemClock.elapsedRealtime(),lastRead=began;
            while(!requestedStop&&used<pcm.length){
                if(SystemClock.elapsedRealtime()-began>=AudioProject.MAX_SECONDS*1000L)break;
                int count=record.read(block,0,Math.min(block.length,pcm.length-used),AudioRecord.READ_NON_BLOCKING);
                if(count<0){if(!requestedStop)throw new IllegalStateException("마이크 입력 중단 ("+count+")");break;}
                if(count==0){if(SystemClock.elapsedRealtime()-lastRead>10000)throw new IllegalStateException("마이크 입력이 중단됐습니다. 다시 캡처해주세요.");Thread.sleep(10);continue;}
                lastRead=SystemClock.elapsedRealtime();
                float peak=0;double power=0;
                for(int i=0;i<count;i++){float value=block[i]/32768f;pcm[used+i]=value;peak=Math.max(peak,Math.abs(value));power+=value*value;}
                used+=count;
                if(used-lastUpdate>=rate/8){lastUpdate=used;int n=Math.min(recent.length,used);System.arraycopy(pcm,used-n,recent,0,n);listener.level(used,(float)Math.sqrt(power/count),peak,Pitch.detect(recent,n,rate));}
            }
        } catch(SecurityException e){error="마이크 권한이 필요합니다. 앱 설정에서 마이크를 허용해주세요.";}
        catch(Exception e){error=e.getMessage();}
        catch(OutOfMemoryError e){error="녹음 메모리가 부족합니다. 더 짧게 캡처해주세요.";}
        finally{
            AudioRecord current=device;device=null;
            if(current!=null){try{current.stop();}catch(IllegalStateException ignored){}current.release();}
            boolean canceled=discard;
            if(!canceled&&error==null&&used<AudioProject.SAMPLE_RATE/5)error="0.2초 이상 캡처해주세요.";
            float[] result=!canceled&&error==null?Arrays.copyOf(pcm,used):null;
            synchronized(this){thread=null;}
            listener.completed(result,canceled,error);
        }
    }
}
