package com.stemnote.app;

import com.stemnote.core.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Parse in memory, never extract remote ZIP entry paths to the device filesystem. */
final class StemArchive {
    static final int MAX_BYTES=64*1024*1024;
    static Instrument instrument(String source)throws IOException {
        switch(source){case "vocals":return Instrument.VOICE;case "drums":return Instrument.DRUMS;
            case "bass":return Instrument.BASS;case "other":return Instrument.OTHER;
            case "guitar":return Instrument.GUITAR;case "piano":return Instrument.PIANO;
            default:throw new IOException("지원하지 않는 분리 트랙: "+source);}
    }
    static byte[] readBounded(InputStream input,int limit)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;
        while((n=input.read(block))!=-1){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();if(out.size()+n>limit)throw new IOException("서버 응답이 허용 크기를 초과했습니다.");out.write(block,0,n);}return out.toByteArray();
    }
    static AudioProject read(File zip,String model,int samples,String title,String source)throws Exception {
        if(samples<2205||samples>22050*120)throw new IOException("입력 길이가 잘못되었습니다.");
        try(ZipInputStream in=new ZipInputStream(new FileInputStream(zip),StandardCharsets.UTF_8)){
            ZipEntry entry=in.getNextEntry();if(entry==null||!entry.getName().equals("manifest.json"))throw new IOException("분리 결과 manifest가 없습니다.");
            JSONObject manifest=new JSONObject(new String(readBounded(in,65536),StandardCharsets.UTF_8));
            if(!"stemnote.stems.v1".equals(manifest.getString("schema"))||!model.equals(manifest.getString("model"))||manifest.getInt("sample_rate")!=22050||manifest.getInt("channels")!=1||manifest.getInt("sample_count")!=samples)throw new IOException("분리 결과 형식이나 길이가 다릅니다.");
            Set<String> required=new HashSet<>(Arrays.asList("vocals","drums","bass","other"));
            if(model.equals("htdemucs_6s")){required.add("guitar");required.add("piano");}else if(!model.equals("htdemucs_ft"))throw new IOException("알 수 없는 모델입니다.");
            Map<String,JSONObject> files=new HashMap<>();Set<String> seenSources=new HashSet<>();JSONArray list=manifest.getJSONArray("stems");
            for(int i=0;i<list.length();i++){JSONObject stem=list.getJSONObject(i);String label=stem.getString("source"),name=stem.getString("file");if(!required.contains(label)||!seenSources.add(label)||!name.equals(label+".wav")||files.put(name,stem)!=null)throw new IOException("중복되거나 잘못된 분리 트랙입니다.");}
            if(!seenSources.equals(required))throw new IOException("필수 분리 트랙이 누락됐습니다.");
            AudioProject.Channel[] channels=new AudioProject.Channel[Instrument.COUNT];for(Instrument ins:Instrument.values())channels[ins.ordinal()]=new AudioProject.Channel(ins,0);
            Set<String> seenFiles=new HashSet<>();int total=0;
            while((entry=in.getNextEntry())!=null){String name=entry.getName();JSONObject stem=files.get(name);if(stem==null||!seenFiles.add(name)||entry.isDirectory())throw new IOException("예상하지 못한 ZIP 항목입니다.");
                byte[] bytes=readBounded(in,samples*2+4096);total+=bytes.length;if(total>MAX_BYTES)throw new IOException("분리 결과가 너무 큽니다.");
                byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder hex=new StringBuilder();for(byte b:hash)hex.append(String.format(Locale.ROOT,"%02x",b&255));
                if(!hex.toString().equals(stem.getString("sha256")))throw new IOException("분리 WAV 체크섬이 일치하지 않습니다.");
                Instrument ins=instrument(stem.getString("source"));channels[ins.ordinal()]=new AudioProject.Channel(ins,PcmWave.read(bytes,samples));
            }
            if(!seenFiles.equals(files.keySet()))throw new IOException("분리 WAV가 누락됐습니다.");
            return new AudioProject(title,source+" · ZeroGPU",channels,"ZeroGPU Demucs "+model+" 음원 분리 · 트랙별 AI 채보 · 드럼은 WAV만 지원");
        }
    }
}
