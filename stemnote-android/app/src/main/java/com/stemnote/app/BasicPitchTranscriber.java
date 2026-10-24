package com.stemnote.app;

import android.content.Context;
import com.stemnote.core.*;
import org.tensorflow.lite.Interpreter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

/** Native TensorFlow Lite inference. Audio stays on the device; model ships in the APK. */
public final class BasicPitchTranscriber {
    public AudioProject analyze(Context context,float[] audio,String title,String source,Instrument target,Analyzer.Progress progress)throws IOException {
        if(audio.length<AudioProject.SAMPLE_RATE/10||audio.length>AudioProject.SAMPLE_RATE*AudioProject.MAX_SECONDS)
            throw new IllegalArgumentException("0.1초~120초 구간을 선택해주세요.");
        if(target==Instrument.DRUMS)throw new IllegalArgumentException("AI 음높이 채보는 드럼을 지원하지 않습니다.");
        byte[] model;
        try(java.io.InputStream stream=context.getAssets().open("basic_pitch.tflite");
            java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream()) {
            byte[] block=new byte[8192];int n;while((n=stream.read(block))!=-1)data.write(block,0,n);model=data.toByteArray();
        }
        ByteBuffer modelBuffer=ByteBuffer.allocateDirect(model.length).order(ByteOrder.nativeOrder());
        modelBuffer.put(model).rewind();
        int count=ModelWindow.frameCount(audio.length);
        float[][] frames=new float[count][NoteDecoder.PITCHES],onsets=new float[count][NoteDecoder.PITCHES];
        try(Interpreter interpreter=new Interpreter(modelBuffer,new Interpreter.Options().setNumThreads(2))) {
            if(!Arrays.equals(interpreter.getInputTensor(0).shape(),new int[]{1,ModelWindow.SAMPLES,1}))
                throw new IOException("AI 모델의 입력 형식이 다릅니다.");
            ByteBuffer input=ByteBuffer.allocateDirect(ModelWindow.SAMPLES*4).order(ByteOrder.nativeOrder());
            float[][][] note=new float[1][ModelWindow.FRAMES][NoteDecoder.PITCHES];
            float[][][] onset=new float[1][ModelWindow.FRAMES][NoteDecoder.PITCHES];
            float[][][] contour=new float[1][ModelWindow.FRAMES][264];
            Map<String,Object> inputs=new HashMap<>(),outputs=new HashMap<>();
            inputs.put("input_2",input);outputs.put("note",note);outputs.put("onset",onset);outputs.put("contour",contour);
            for(int first=0;first<count;first+=ModelWindow.KEPT) {
                if(Thread.currentThread().isInterrupted())throw new CancellationException();
                input.clear();int start=ModelWindow.sampleStart(first);
                for(int j=0;j<ModelWindow.SAMPLES;j++) {int at=start+j;input.putFloat(at>=0&&at<audio.length?audio[at]:0);}
                input.rewind();interpreter.runSignature(inputs,outputs);
                ModelWindow.copy(note,frames,first);ModelWindow.copy(onset,onsets,first);
                progress.update(Math.min(95,(first+ModelWindow.KEPT)*95/count));
            }
        }
        AudioProject.Channel[] channels=new AudioProject.Channel[Instrument.COUNT];
        for(Instrument instrument:Instrument.values())channels[instrument.ordinal()]=new AudioProject.Channel(instrument,0);
        AudioProject.Channel channel=new AudioProject.Channel(target,audio);
        channel.notes.addAll(NoteDecoder.decode(frames,onsets,audio));channels[target.ordinal()]=channel;
        progress.update(100);
        return new AudioProject(title,source,channels,"Basic Pitch AI 채보 · 악기 직접 지정 · 자동 음원 분리 없음");
    }
}
