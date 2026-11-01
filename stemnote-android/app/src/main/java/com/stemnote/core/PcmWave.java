package com.stemnote.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Read only the bounded PCM16 mono 22050 Hz format in the stem archive contract. */
public final class PcmWave {
    private PcmWave() {}
    private static String tag(byte[] bytes,int offset){return new String(bytes,offset,4,StandardCharsets.US_ASCII);}
    public static float[] read(byte[] bytes,int expectedSamples)throws IOException {
        if(bytes.length<44||!tag(bytes,0).equals("RIFF")||!tag(bytes,8).equals("WAVE"))throw new IOException("잘못된 WAV 헤더입니다.");
        ByteBuffer data=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        boolean format=false;int pcmAt=-1,pcmSize=0;
        for(int at=12;at+8<=bytes.length;){
            long size=Integer.toUnsignedLong(data.getInt(at+4));
            if(size>bytes.length-at-8)throw new IOException("잘린 WAV입니다.");
            if(tag(bytes,at).equals("fmt ")) {
                if(size<16||data.getShort(at+8)!=1||data.getShort(at+10)!=1||data.getInt(at+12)!=22050||data.getShort(at+22)!=16)
                    throw new IOException("분리 WAV는 PCM16 / 모노 / 22.05kHz여야 합니다.");
                format=true;
            }else if(tag(bytes,at).equals("data")){if(pcmAt>=0)throw new IOException("중복 PCM 데이터입니다.");pcmAt=at+8;pcmSize=(int)size;}
            at+=8+(int)size+((int)size&1);
        }
        if(!format||pcmAt<0||pcmSize!=expectedSamples*2)throw new IOException("분리 트랙 길이가 입력과 다릅니다.");
        float[] result=new float[expectedSamples];for(int i=0;i<result.length;i++)result[i]=data.getShort(pcmAt+2*i)/32768f;
        return result;
    }
}
