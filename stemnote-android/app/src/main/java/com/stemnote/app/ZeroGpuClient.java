package com.stemnote.app;

import android.content.Context;
import com.stemnote.core.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CancellationException;

/** Gradio 5 named-call/upload/SSE client. No credentials are embedded or persisted. */
public final class ZeroGpuClient {
    public interface Status {void update(String message);}
    private final URL origin;private final String token;private volatile boolean canceled;private volatile HttpURLConnection current;
    public ZeroGpuClient(String address,String token)throws Exception {this(validate(address),token);}
    // Package-private origin injection is used only by loopback transport instrumentation tests.
    ZeroGpuClient(URL origin,String token){this.origin=origin;this.token=token.trim();}
    static URL validate(String address)throws Exception {
        URI uri=new URI(address.trim());String host=uri.getHost();
        if(!"https".equals(uri.getScheme())||host==null||!host.toLowerCase(java.util.Locale.ROOT).matches("[a-z0-9-]+\\.hf\\.space")||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath().isEmpty()||uri.getPath().equals("/")))
            throw new IOException("Space 실행 주소를 입력해주세요: https://이름-공간.hf.space");
        return new URL("https://"+host+"/");
    }
    public void cancel(){canceled=true;HttpURLConnection connection=current;if(connection!=null)connection.disconnect();}
    private void check(){if(canceled||Thread.currentThread().isInterrupted())throw new CancellationException();}
    private HttpURLConnection open(String path,String method)throws Exception {
        check();HttpURLConnection connection=(HttpURLConnection)new URL(origin,path).openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(60000);connection.setRequestMethod(method);
        if(!token.isEmpty())connection.setRequestProperty("Authorization","Bearer "+token);
        current=connection;return connection;
    }
    private void okay(HttpURLConnection connection)throws IOException {
        int code=connection.getResponseCode();if(code>=200&&code<300)return;
        String reason=code==401||code==403?"Space 접근 권한 또는 HF 토큰을 확인해주세요.":code==429?"ZeroGPU 사용량/대기 한도를 초과했습니다. 나중에 다시 시도해주세요.":code==502||code==503?"Space가 시작 중이거나 중지되어 있습니다. 잠시 후 다시 시도해주세요.":"Space 응답 오류 (HTTP "+code+"). 주소와 서버 버전을 확인해주세요.";
        throw new IOException(reason);
    }
    private String json(HttpURLConnection connection)throws Exception {okay(connection);try(InputStream input=connection.getInputStream()){return new String(StemArchive.readBounded(input,1024*1024),StandardCharsets.UTF_8);}}
    private String upload(File audio)throws Exception {
        HttpURLConnection connection=open("/gradio_api/upload","POST");String boundary="Stemnote"+UUID.randomUUID().toString().replace("-","");
        byte[] start=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"files\"; filename=\"input.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8),end=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);connection.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);connection.setFixedLengthStreamingMode(start.length+audio.length()+end.length);
        try{try(OutputStream out=connection.getOutputStream();InputStream input=new FileInputStream(audio)){out.write(start);byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))!=-1){check();out.write(bytes,0,n);}out.write(end);}
            JSONArray result=new JSONArray(json(connection));if(result.length()!=1)throw new IOException("업로드 결과가 잘못되었습니다.");return result.getString(0);
        }finally{connection.disconnect();}
    }
    private String start(String uploaded,String model)throws Exception {
        JSONObject file=new JSONObject().put("path",uploaded).put("orig_name","input.wav").put("meta",new JSONObject().put("_type","gradio.FileData"));
        byte[] body=new JSONObject().put("data",new JSONArray().put(file).put(model)).toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection=open("/gradio_api/call/separate","POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");connection.setFixedLengthStreamingMode(body.length);
        try{try(OutputStream output=connection.getOutputStream()){output.write(body);}String id=new JSONObject(json(connection)).getString("event_id");if(!id.matches("[A-Za-z0-9_-]{1,128}"))throw new IOException("작업 ID가 잘못되었습니다.");return id;}finally{connection.disconnect();}
    }
    private String waitFor(String id,Status status)throws Exception {
        HttpURLConnection connection=open("/gradio_api/call/separate/"+id,"GET");connection.setRequestProperty("Accept","text/event-stream");
        try{okay(connection);try(BufferedReader input=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8))){
            String event="";StringBuilder data=new StringBuilder();long deadline=System.nanoTime()+600_000_000_000L;String line;
            while((line=input.readLine())!=null){check();if(System.nanoTime()>deadline)throw new IOException("GPU 대기 시간이 길어졌습니다. 나중에 다시 시도해주세요.");
                if(line.isEmpty()){
                    if(event.equals("complete")){JSONArray values=new JSONArray(data.toString());String path=values.getJSONObject(0).getString("path");if(!path.startsWith("/")||path.contains("..")||path.indexOf('\0')>=0)throw new IOException("분리 파일 경로가 잘못되었습니다.");return path;}
                    if(event.equals("error"))throw new IOException("ZeroGPU 분리에 실패했습니다. GPU 할당량 또는 Space 오류를 확인해주세요. "+data.toString().substring(0,Math.min(300,data.length())));
                    if(event.equals("heartbeat")||event.equals("generating"))status.update("ZeroGPU 대기 또는 음원 분리 중");event="";data.setLength(0);
                }else if(line.startsWith("event:"))event=line.substring(6).trim();else if(line.startsWith("data:")){if(data.length()>1024*1024)throw new IOException("이벤트 응답이 너무 큽니다.");if(data.length()>0)data.append('\n');data.append(line.substring(5).trim());}
            }
        }throw new IOException("Space 연결이 완료 전에 끊겼습니다.");}finally{connection.disconnect();}
    }
    private void download(String path,File destination)throws Exception {
        String encoded=new URI(null,null,"/gradio_api/file="+path,null).toASCIIString();HttpURLConnection connection=open(encoded,"GET");
        try{okay(connection);try(InputStream input=connection.getInputStream();OutputStream output=new FileOutputStream(destination)){byte[] block=new byte[8192];int n,total=0;while((n=input.read(block))!=-1){check();total+=n;if(total>StemArchive.MAX_BYTES)throw new IOException("분리 결과가 너무 큽니다.");output.write(block,0,n);}}}finally{connection.disconnect();}
    }
    public AudioProject separate(Context context,float[] samples,String title,String source,String model,Status status)throws Exception {
        File audio=File.createTempFile("stemnote-upload-",".wav",context.getCacheDir()),result=null;
        try{
            try(OutputStream out=new FileOutputStream(audio)){Exports.wav(out,samples);}
            status.update("WAV 업로드 중");String uploaded=upload(audio);status.update("ZeroGPU 작업 요청 중");String id=start(uploaded,model);
            status.update("ZeroGPU 대기 또는 음원 분리 중");String path=waitFor(id,status);status.update("분리된 트랙 다운로드 중");
            result=File.createTempFile("stemnote-stems-",".zip",context.getCacheDir());download(path,result);check();
            return StemArchive.read(result,model,samples.length,title,source);
        }finally{audio.delete();if(result!=null)result.delete();current=null;}
    }
}
