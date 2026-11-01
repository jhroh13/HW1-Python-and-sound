package com.stemnote.app;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import com.stemnote.core.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

@RunWith(AndroidJUnit4.class)
public final class ZeroGpuDeviceTest {
    private static final int SAMPLES=22050;
    private static final String[] LABELS={"drums","bass","other","vocals"};
    private static byte[] archive(boolean badChecksum,boolean extra)throws Exception {
        List<byte[]> audio=new ArrayList<>();JSONArray entries=new JSONArray();
        for(int i=0;i<LABELS.length;i++){
            float[] samples=new float[SAMPLES];Arrays.fill(samples,(i+1)*.04f);ByteArrayOutputStream out=new ByteArrayOutputStream();Exports.wav(out,samples);byte[] wav=out.toByteArray();audio.add(wav);
            StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(wav))hex.append(String.format(Locale.ROOT,"%02x",b&255));
            entries.put(new JSONObject().put("source",LABELS[i]).put("file",LABELS[i]+".wav").put("sha256",badChecksum?"bad":hex.toString()));
        }
        JSONObject manifest=new JSONObject().put("schema","stemnote.stems.v1").put("model","htdemucs_ft").put("sample_rate",22050).put("channels",1).put("sample_count",SAMPLES).put("stems",entries);
        ByteArrayOutputStream result=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(result)){
            zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            for(int i=0;i<LABELS.length;i++){zip.putNextEntry(new ZipEntry(LABELS[i]+".wav"));zip.write(audio.get(i));zip.closeEntry();}
            if(extra){zip.putNextEntry(new ZipEntry("../unexpected.wav"));zip.write(1);zip.closeEntry();}
        }return result.toByteArray();
    }
    private static final class Space implements AutoCloseable {
        final ServerSocket socket;final Thread server;final byte[] zip;final boolean quota;volatile Throwable error;volatile int requests;
        Space(byte[] zip,boolean quota)throws Exception {
            this.zip=zip;this.quota=quota;socket=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));
            server=new Thread(()->{try{while(!socket.isClosed()&&requests<(quota?1:4)){try(Socket client=socket.accept()){serve(client);requests++;}}}catch(Throwable e){if(!socket.isClosed())error=e;}});server.start();
        }
        URL url()throws Exception{return new URL("http://127.0.0.1:"+socket.getLocalPort()+"/");}
        static String line(InputStream in)throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();int b;while((b=in.read())!=-1&&b!='\n'){if(b!='\r')out.write(b);if(out.size()>8192)throw new IOException("header too large");}return out.toString("UTF-8");}
        void serve(Socket client)throws Exception {
            client.setSoTimeout(10000);InputStream input=client.getInputStream();String first=line(input);Map<String,String> headers=new HashMap<>();String header;
            while(!(header=line(input)).isEmpty()){int at=header.indexOf(':');if(at>0)headers.put(header.substring(0,at).toLowerCase(Locale.ROOT),header.substring(at+1).trim());}
            assertEquals("Bearer hf_test_read_only",headers.get("authorization"));
            int length=Integer.parseInt(headers.getOrDefault("content-length","0"));byte[] body=new byte[length];int offset=0;while(offset<length){int n=input.read(body,offset,length-offset);if(n<0)throw new EOFException();offset+=n;}
            byte[] response;String type="application/json";int code=200;
            if(quota){code=429;response="{}".getBytes(StandardCharsets.UTF_8);}
            else if(first.startsWith("POST /gradio_api/upload ")){
                String content=new String(body,StandardCharsets.ISO_8859_1);assertTrue(content.contains("name=\"files\""));assertTrue(content.contains("RIFF"));response="[\"/tmp/input.wav\"]".getBytes(StandardCharsets.UTF_8);
            }else if(first.startsWith("POST /gradio_api/call/separate ")){
                JSONArray data=new JSONObject(new String(body,StandardCharsets.UTF_8)).getJSONArray("data");assertEquals("htdemucs_ft",data.getString(1));assertEquals("gradio.FileData",data.getJSONObject(0).getJSONObject("meta").getString("_type"));response="{\"event_id\":\"event123\"}".getBytes(StandardCharsets.UTF_8);
            }else if(first.startsWith("GET /gradio_api/call/separate/event123 ")){
                type="text/event-stream";
                // The external URL must be ignored: the client downloads the path from its own origin.
                response="event: heartbeat\ndata: null\n\nevent: complete\ndata: [{\"path\":\"/tmp/stems.zip\",\"url\":\"https://invalid.example/leak\"}]\n\n".getBytes(StandardCharsets.UTF_8);
            }else if(first.startsWith("GET /gradio_api/file=/tmp/stems.zip ")){response=zip;type="application/zip";}
            else throw new AssertionError(first);
            OutputStream out=client.getOutputStream();out.write(("HTTP/1.1 "+code+" OK\r\nContent-Type: "+type+"\r\nContent-Length: "+response.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(response);out.flush();
        }
        public void close()throws Exception{socket.close();server.join(3000);if(error!=null)throw new AssertionError(error);}
    }
    @Test public void testUploadQueuedCallDownloadAndVocalExclusion()throws Exception {
        try(Space space=new Space(archive(false,false),false)){
            AudioProject result=new ZeroGpuClient(space.url(),"hf_test_read_only").separate(InstrumentationRegistry.getInstrumentation().getTargetContext(),new float[SAMPLES],"song","test","htdemucs_ft",s->{});
            space.server.join(1000);assertEquals(4,space.requests);assertEquals(SAMPLES,result.length);
            boolean[] selected=new boolean[Instrument.COUNT];float[] gains=new float[Instrument.COUNT];Arrays.fill(gains,1);
            for(Instrument ins:new Instrument[]{Instrument.DRUMS,Instrument.BASS,Instrument.OTHER})selected[ins.ordinal()]=true;
            float[] mix=Exports.mix(result,selected,gains,false);assertEquals(.24,mix[100],.0001);
            ByteArrayOutputStream zip=new ByteArrayOutputStream();Exports.archive(zip,result,selected,gains,"backing",true);
            try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(zip.toByteArray()))){ZipEntry entry;boolean wav=false;while((entry=in.getNextEntry())!=null){assertFalse("do not export fabricated drum MIDI",entry.getName().endsWith(".mid"));wav|=entry.getName().endsWith(".wav");}assertTrue(wav);}
        }
    }
    @Test public void testQuotaFailure()throws Exception {
        try(Space space=new Space(new byte[0],true)){
            try{new ZeroGpuClient(space.url(),"hf_test_read_only").separate(InstrumentationRegistry.getInstrumentation().getTargetContext(),new float[SAMPLES],"x","test","htdemucs_ft",s->{});fail("quota must fail");}
            catch(IOException expected){assertTrue(expected.getMessage().contains("한도"));}
        }
    }
    @Test public void testArchiveRejectsCorruptionAndUnexpectedPaths()throws Exception {
        for(boolean corrupt:new boolean[]{true,false}){
            File file=File.createTempFile("archive-test-",".zip",InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
            try{try(OutputStream out=new FileOutputStream(file)){out.write(archive(corrupt,!corrupt));}
                try{StemArchive.read(file,"htdemucs_ft",SAMPLES,"x","test");fail("invalid archive must fail");}catch(IOException expected){assertNotNull(expected.getMessage());}
            }finally{file.delete();}
        }
    }
    @Test public void testSpaceAddressValidation()throws Exception {
        assertEquals("https://owner-space.hf.space/",ZeroGpuClient.validate("https://owner-space.hf.space").toString());
        for(String invalid:new String[]{"http://owner-space.hf.space","https://owner-space.hf.space.evil.com","https://huggingface.co/spaces/owner/space","https://token@owner-space.hf.space","https://owner-space.hf.space/path"}){
            try{ZeroGpuClient.validate(invalid);fail(invalid);}catch(IOException expected){assertNotNull(expected.getMessage());}
        }
    }
}
