package com.stemnote.core;

/** Sample-aligned overlap avoids a cumulative timing shift when joining model windows. */
public final class ModelWindow {
    public static final int SAMPLES=43844, FRAMES=172, CONTEXT=15, KEPT=FRAMES-2*CONTEXT;
    private ModelWindow() {}
    public static int frameCount(int samples){return (samples+NoteDecoder.HOP-1)/NoteDecoder.HOP;}
    public static int sampleStart(int firstFrame){return (firstFrame-CONTEXT)*NoteDecoder.HOP;}
    public static void copy(float[][][] output,float[][] full,int firstFrame) {
        for(int local=CONTEXT;local<FRAMES-CONTEXT&&firstFrame+local-CONTEXT<full.length;local++)
            System.arraycopy(output[0][local],0,full[firstFrame+local-CONTEXT],0,NoteDecoder.PITCHES);
    }
}
