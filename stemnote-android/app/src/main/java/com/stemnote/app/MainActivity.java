package com.stemnote.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import com.stemnote.core.*;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_AUDIO=10,SAVE_ZIP=11,MIC_PERMISSION=12;
    private static final int BG=0xff101514,PANEL=0xff1b241d,TEXT=0xffedf1e9,MUTED=0xff99a591,LIME=0xffc4ef8b;
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private final NativePlayer player=new NativePlayer();
    private AudioProject project;
    private final boolean[] selected=new boolean[8];private final float[] gains=new float[8];
    private volatile boolean destroyed;private boolean foreground,busy,capturing,playing,pendingPermission;
    private NativeCapture microphone;
    private LinearLayout content,channelList;
    private TextView state,projectName,projectMeta,captureState,captureTime,pitchLabel,levelLabel,selectionLabel,filePreview;
    private EditText captureName,fileName;
    private Button importButton,startButton,finishButton,cancelButton,selectButton,playButton,exportButton,guideButton;
    private Spinner sourceSpinner,previewSpinner;
    private CheckBox includeWav;
    private ProgressBar inputMeter,analysisProgress;
    private final CheckBox[] checks=new CheckBox[8];private final SeekBar[] gainSliders=new SeekBar[8];
    private ExportRequest pendingExport;
    private String capturedTitle,capturedSource;

    @Override public void onCreate(Bundle saved){super.onCreate(saved);Arrays.fill(gains,.8f);buildScreen();sync();}
    @Override protected void onResume(){super.onResume();foreground=true;}
    @Override protected void onStop(){foreground=false;stopPlayer();if(capturing){captureState.setText("화면을 벗어나 캡처를 완료합니다.");microphone.finish(false);}super.onStop();}
    @Override protected void onDestroy(){destroyed=true;if(microphone!=null)microphone.finish(true);player.stop();jobs.shutdownNow();super.onDestroy();}
    private void ui(Runnable action){runOnUiThread(()->{if(!destroyed)action.run();});}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setLineSpacing(dp(3),1);return view;}
    private LinearLayout vertical(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private LinearLayout row(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.HORIZONTAL);layout.setGravity(Gravity.CENTER_VERTICAL);return layout;}
    private GradientDrawable background(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));d.setStroke(dp(1),0xff3c4937);return d;}
    private void space(LinearLayout parent,int height){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    private LinearLayout card(){LinearLayout layout=vertical();layout.setPadding(dp(16),dp(18),dp(16),dp(18));layout.setBackground(background(PANEL));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(16);content.addView(layout,p);return layout;}
    private Button button(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(primary?BG:LIME);b.setBackgroundTintList(ColorStateList.valueOf(primary?LIME:0xff303d29));b.setMinHeight(dp(48));return b;}
    private EditText input(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(TEXT);e.setTextSize(14);e.setHintTextColor(MUTED);e.setHint(hint);e.setPadding(dp(10),dp(8),dp(10),dp(8));e.setBackground(background(0xff222c22));e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});return e;}
    private Spinner spinner(String[] values){Spinner s=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);s.setAdapter(adapter);return s;}
    private void buildScreen(){
        LinearLayout root=vertical();root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=vertical();content.setPadding(dp(20),dp(22),dp(20),dp(30));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));setContentView(root);
        TextView brand=text("≋  stemnote",28,LIME);brand.setTypeface(null,Typeface.BOLD);content.addView(brand);space(content,8);
        content.addView(text("ANDROID NATIVE  /  AUDIO → MIDI",10,MUTED));space(content,20);
        content.addView(text("한 곡에서, 새로운 가능성으로.",22,TEXT));space(content,8);
        content.addView(text("음악 파일이나 마이크 구간을 악기별로 탐색하세요.",12,MUTED));space(content,20);
        LinearLayout upload=card();upload.addView(text("음악 파일 가져오기",16,TEXT));space(upload,8);upload.addView(text("MP3 · WAV · M4A · OGG · FLAC (기기 코덱에 따라 다름)\n최대 120초. 파일은 기기 안에서 처리합니다.",11,MUTED));space(upload,10);
        importButton=button("음악 파일 선택",false);upload.addView(importButton);importButton.setOnClickListener(v->pickAudio());
        LinearLayout capture=card();capture.addView(text("● 마이크로 음악 캡처",16,LIME));space(capture,8);capture.addView(text("다른 휴대폰은 스피커로 재생하거나, 직접 연주해주세요. 같은 기기의 다른 앱 내부 오디오를 직접 녹음하지 않습니다.",11,MUTED));space(capture,12);
        sourceSpinner=spinner(new String[]{"다른 휴대폰에서 재생하는 음악","현재 기기 마이크 · 목소리 / 연주"});capture.addView(sourceSpinner);space(capture,8);
        captureName=input("곡 제목 (선택 · 비우면 캡처 날짜/시간)");capture.addView(captureName);space(capture,14);
        startButton=button("● 캡처 시작",true);finishButton=button("■ 캡처 완료 · 분석",true);cancelButton=button("캡처 취소",false);capture.addView(startButton);capture.addView(finishButton);capture.addView(cancelButton);
        startButton.setOnClickListener(v->requestMicrophone());finishButton.setOnClickListener(v->microphone.finish(false));cancelButton.setOnClickListener(v->microphone.finish(true));space(capture,10);
        captureTime=text("0:00 / 2:00",19,TEXT);capture.addView(captureTime);captureState=text("마이크 준비됨",12,LIME);capture.addView(captureState);space(capture,10);
        inputMeter=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);inputMeter.setMax(100);inputMeter.setProgressTintList(ColorStateList.valueOf(LIME));capture.addView(inputMeter,new LinearLayout.LayoutParams(-1,dp(9)));
        levelLabel=text("입력 대기",11,MUTED);pitchLabel=text("대표 음높이 —",11,MUTED);capture.addView(levelLabel);capture.addView(pitchLabel);space(capture,10);
        capture.addView(text("캡처 중에는 입력 레벨과 대표 음높이를 추정합니다. 완료 후 악기별 분석을 진행합니다. 최대 120초 자동 완료. 화면을 떠나면 캡처가 완료됩니다.",10,MUTED));
        LinearLayout warning=card();warning.addView(text("ⓘ 실험적 로컬 분석",13,LIME));warning.addView(text("주파수 기반 추정입니다. 보이스·기타처럼 음역이 겹치는 악기를 정확히 분리하거나 채보하지 못할 수 있습니다. 학습된 AI 분리 모델은 포함되지 않습니다.",11,MUTED));
        LinearLayout tracks=card();projectName=text("프로젝트를 시작하세요",18,TEXT);tracks.addView(projectName);projectMeta=text("파일을 선택하거나 마이크를 캡처해주세요.",11,MUTED);tracks.addView(projectMeta);space(tracks,10);
        state=text("준비됨",12,LIME);tracks.addView(state);analysisProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);analysisProgress.setMax(100);analysisProgress.setVisibility(View.GONE);tracks.addView(analysisProgress);space(tracks,10);
        LinearLayout heading=row();heading.addView(text("악기 채널",15,TEXT),new LinearLayout.LayoutParams(0,-2,1));selectButton=button("전체 선택",false);heading.addView(selectButton);selectButton.setOnClickListener(v->{boolean all=selectedCount()==8;Arrays.fill(selected,!all);stopPlayer();renderChannels();sync();});tracks.addView(heading);
        channelList=vertical();tracks.addView(channelList);renderChannels();space(tracks,12);
        selectionLabel=text("0 / 8 채널 선택",12,LIME);tracks.addView(selectionLabel);previewSpinner=spinner(new String[]{"선택 채널 오디오 믹스","선택 채널 MIDI 합성음"});tracks.addView(previewSpinner);
        previewSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int i,long id){stopPlayer();}public void onNothingSelected(android.widget.AdapterView<?> p){}});
        playButton=button("▶ 선택 채널 미리듣기",true);tracks.addView(playButton);playButton.setOnClickListener(v->preview());space(tracks,8);tracks.addView(text("미리듣기 전체 볼륨 (저장에는 적용하지 않음)",10,MUTED));
        SeekBar master=new SeekBar(this);master.setMax(100);master.setProgress(75);master.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){player.volume(p/100f);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});tracks.addView(master);
        LinearLayout export=card();export.addView(text("내보내기",17,TEXT));space(export,10);fileName=input("저장할 곡 제목");export.addView(fileName);filePreview=text("원곡제목_악기이름_CH.mid",10,MUTED);export.addView(filePreview);fileName.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){updateFilePreview();}public void afterTextChanged(android.text.Editable e){}});space(export,12);
        includeWav=new CheckBox(this);includeWav.setText("WAV도 함께 저장 · 선택 채널 믹스");includeWav.setTextColor(TEXT);includeWav.setTextSize(12);export.addView(includeWav);export.addView(text("악기별 MIDI + 멀티트랙 MIDI를 ZIP으로 저장합니다. WAV: 모노 22.05kHz / 16bit.",11,MUTED));space(export,12);
        exportButton=button("↓ 선택 채널 내보내기",true);export.addView(exportButton);exportButton.setOnClickListener(v->chooseExport());
        guideButton=button("사용 가이드",false);content.addView(guideButton);guideButton.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Stemnote 사용법").setMessage("1. 음악 파일을 고르거나 마이크 캡처를 시작합니다.\n2. 원하는 지점에서 캡처 완료를 누릅니다.\n3. 분석 후 채널을 선택하고 레벨을 조절합니다.\n4. 오디오 믹스 또는 MIDI 합성음으로 들어봅니다.\n5. 저장 이름과 WAV 옵션을 정하고 ZIP 저장 위치를 고릅니다.\n\n녹음에는 마이크 권한만 필요합니다. HTTPS나 웹 브라우저는 필요하지 않습니다. 모든 분석은 기기에서 처리합니다.\n\n분석은 실험적 주파수 추정입니다. MIDI 120 BPM은 시간 저장용이며 원곡 템포 감지가 아닙니다. 드럼은 GM 채널 10입니다.\n\n프로젝트는 메모리에만 유지됩니다. 앱 종료 전 ZIP으로 저장해주세요.").setPositiveButton("확인",null).show());space(content,16);content.addView(text("STEMNOTE  /  기기 내 처리 · 음원 업로드 없음",10,MUTED));
    }
    private void renderChannels(){
        channelList.removeAllViews();
        for(int index=0;index<8;index++){final int id=index;Instrument ins=Instrument.values()[id];LinearLayout item=vertical();item.setPadding(0,dp(12),0,dp(8));
            CheckBox check=new CheckBox(this);checks[id]=check;check.setText(ins.title+"  ·  CH "+String.format(Locale.ROOT,"%02d",ins.channel+1));check.setTextColor(ins.color);check.setTextSize(14);check.setChecked(selected[id]);item.addView(check);
            TextView count=text(project==null?"아직 분석되지 않음":project.channels[id].notes.size()+" NOTES",10,MUTED);item.addView(count);
            WaveView wave=null;if(project!=null){wave=new WaveView(this,project.channels[id].audio,ins.color,selected[id]);item.addView(wave,new LinearLayout.LayoutParams(-1,dp(34)));}final WaveView waveView=wave;
            LinearLayout level=row();TextView number=text(Math.round(gains[id]*100)+"%",11,MUTED);SeekBar slider=new SeekBar(this);gainSliders[id]=slider;slider.setMax(100);slider.setProgress(Math.round(gains[id]*100));level.addView(slider,new LinearLayout.LayoutParams(0,dp(36),1));level.addView(number);item.addView(level);
            check.setOnCheckedChangeListener((v,checked)->{selected[id]=checked;if(waveView!=null)waveView.active(checked);stopPlayer();sync();});
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){gains[id]=p/100f;number.setText(p+"%");if(user)stopPlayer();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
            channelList.addView(item);
        }
    }
    private int selectedCount(){int n=0;for(boolean s:selected)if(s)n++;return n;}
    private void updateFilePreview(){if(filePreview==null||fileName==null)return;int first=-1;for(int i=0;i<8;i++)if(selected[i]){first=i;break;}if(first<0){filePreview.setText("채널을 선택해주세요.");return;}Instrument ins=Instrument.values()[first];filePreview.setText(Exports.safeName(fileName.getText().toString())+"_"+ins.fileName+"_"+String.format(Locale.ROOT,"%02d",ins.channel+1)+".mid");}
    private void sync(){
        boolean locked=busy||capturing;importButton.setEnabled(!locked);startButton.setEnabled(!locked);finishButton.setEnabled(capturing);cancelButton.setEnabled(capturing);captureName.setEnabled(!locked);sourceSpinner.setEnabled(!locked);selectButton.setEnabled(!locked&&project!=null);fileName.setEnabled(!locked&&project!=null);includeWav.setEnabled(!locked);previewSpinner.setEnabled(!locked);
        for(int i=0;i<8;i++){if(checks[i]!=null)checks[i].setEnabled(!locked&&project!=null);if(gainSliders[i]!=null)gainSliders[i].setEnabled(!locked&&project!=null);}
        playButton.setEnabled(!locked&&selectedCount()>0&&project!=null);exportButton.setEnabled(!locked&&selectedCount()>0&&project!=null);selectionLabel.setText(selectedCount()+" / 8 채널 선택");selectButton.setText(selectedCount()==8?"전체 해제":"전체 선택");analysisProgress.setVisibility(busy?View.VISIBLE:View.GONE);updateFilePreview();
    }
    private void requestMicrophone(){if(busy||capturing)return;if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){pendingPermission=true;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_PERMISSION);}else startCapture();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==MIC_PERMISSION&&pendingPermission){pendingPermission=false;if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&foreground)startCapture();else{captureState.setText("마이크 권한을 허용해주세요.");new AlertDialog.Builder(this).setMessage("캡처에는 마이크 권한이 필요합니다. 권한 요청이 표시되지 않으면 설정 → 앱 → Stemnote → 권한에서 마이크를 허용해주세요.").setPositiveButton("확인",null).show();}}}
    private void startCapture(){
        if(busy||capturing||destroyed)return;stopPlayer();capturedTitle=captureName.getText().toString().trim();if(capturedTitle.isEmpty())capturedTitle="마이크캡처_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date());capturedSource=sourceSpinner.getSelectedItemPosition()==0?"다른 휴대폰 음악 · 마이크":"목소리/연주 · 마이크";
        capturing=true;captureTime.setText("0:00 / 2:00");captureState.setText("마이크를 준비하는 중");inputMeter.setProgress(0);levelLabel.setText("입력 대기");pitchLabel.setText("대표 음높이 —");getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);sync();
        microphone=new NativeCapture(new NativeCapture.Listener(){
            public void started(){ui(()->captureState.setText("● 캡처 중 · 원하는 지점에서 완료"));}
            public void level(int samples,float rms,float peak,double frequency){ui(()->{captureTime.setText(clock(samples/(double)AudioProject.SAMPLE_RATE)+" / 2:00");inputMeter.setProgress(Math.min(100,Math.round(rms*500)));levelLabel.setText(peak>.98?"소리가 너무 큼":rms<.008?"입력이 작음":"입력 수신 중");pitchLabel.setText(Pitch.label(frequency));});}
            public void completed(float[] pcm,boolean discarded,String error){ui(()->{capturing=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);inputMeter.setProgress(0);sync();if(discarded){captureState.setText("캡처 취소 · 마이크 해제됨");return;}if(error!=null){captureState.setText("캡처 실패 · 마이크 해제됨");message(error);return;}captureTime.setText(clock(pcm.length/(double)AudioProject.SAMPLE_RATE)+" 캡처됨");captureState.setText("캡처 완료 · 마이크 해제됨 · 분석 중");analyze(()->pcm,capturedTitle,capturedSource);});}
        });microphone.start();
    }
    private void pickAudio(){if(busy||capturing)return;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("audio/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,PICK_AUDIO);}
    private String displayName(Uri uri){try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0).replaceFirst("\\.[^.]+$","");}catch(Exception ignored){}return "음악";}
    interface AudioInput {float[] read()throws Exception;}
    private void analyze(AudioInput input,String title,String source){
        if(busy)return;busy=true;stopPlayer();state.setText("음악을 읽고 분석하는 중");analysisProgress.setProgress(0);sync();
        jobs.execute(()->{try{float[] samples=input.read();AudioProject result=new Analyzer().analyze(samples,title,source,p->ui(()->{state.setText("악기·음표 추정 중 "+p+"%");analysisProgress.setProgress(p);}));ui(()->{project=result;Arrays.fill(selected,true);Arrays.fill(gains,.8f);fileName.setText(result.title);projectName.setText(result.title);projectMeta.setText(clock(result.length/(double)AudioProject.SAMPLE_RATE)+" · 모노 22.05kHz · "+result.source);state.setText("분석 완료 · 주파수 기반 추정");if(source.contains("마이크"))captureState.setText("캡처 분석 완료 · MIDI 저장 가능");busy=false;renderChannels();sync();});}
            catch(Exception e){ui(()->{busy=false;state.setText("분석 실패 · 기존 프로젝트 유지");if(source.contains("마이크"))captureState.setText("캡처 분석 실패 · 마이크 해제됨");sync();message(e.getMessage());});}
            catch(OutOfMemoryError e){ui(()->{busy=false;state.setText("메모리 부족");sync();message("메모리가 부족합니다. 더 짧은 구간으로 다시 시도해주세요.");});}});
    }
    private void preview(){
        if(playing){stopPlayer();return;}if(project==null||busy||capturing||selectedCount()==0)return;
        busy=true;stopPlayer();state.setText("선택 채널 믹스를 준비하는 중");sync();AudioProject snapshot=project;boolean[] chosen=selected.clone();float[] volumes=gains.clone();boolean midi=previewSpinner.getSelectedItemPosition()==1;
        jobs.execute(()->{try{float[] mix=Exports.mix(snapshot,chosen,volumes,midi);ui(()->{busy=false;state.setText("분석 완료 · 주파수 기반 추정");sync();if(!foreground)return;playing=true;playButton.setText("■ 미리듣기 정지");player.play(mix,()->ui(this::stopPlayer),error->ui(()->message(error)));});}catch(OutOfMemoryError e){ui(()->{busy=false;sync();message("재생 메모리가 부족합니다.");});}});
    }
    private void stopPlayer(){player.stop();playing=false;if(playButton!=null)playButton.setText("▶ 선택 채널 미리듣기");}
    private static final class ExportRequest {AudioProject project;boolean[] selected;float[] gains;String name;boolean wav;}
    private void chooseExport(){
        if(project==null||busy||capturing||selectedCount()==0)return;stopPlayer();ExportRequest request=new ExportRequest();request.project=project;request.selected=selected.clone();request.gains=gains.clone();request.name=Exports.safeName(fileName.getText().toString());request.wav=includeWav.isChecked();pendingExport=request;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/zip");intent.putExtra(Intent.EXTRA_TITLE,request.name+"_선택채널.zip");startActivityForResult(intent,SAVE_ZIP);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==PICK_AUDIO&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();analyze(()->AudioDecoder.decode(this,uri),displayName(uri),"음악 파일");}
        if(request==SAVE_ZIP){ExportRequest snapshot=pendingExport;pendingExport=null;if(result!=RESULT_OK||data==null||data.getData()==null||snapshot==null)return;Uri uri=data.getData();busy=true;state.setText("선택 채널을 저장하는 중");sync();jobs.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new java.io.IOException("저장 위치를 열 수 없습니다.");Exports.archive(out,snapshot.project,snapshot.selected,snapshot.gains,snapshot.name,snapshot.wav);ui(()->{busy=false;state.setText("저장 완료");sync();message("악기별 MIDI와 멀티트랙 MIDI"+(snapshot.wav?", WAV 믹스":"")+"를 ZIP으로 저장했습니다.");});}catch(Exception e){ui(()->{busy=false;state.setText("저장 실패 · 다시 시도 가능");sync();message(e.getMessage());});}catch(OutOfMemoryError e){ui(()->{busy=false;sync();message("저장 메모리가 부족합니다. 더 짧게 캡처해주세요.");});}});}
    }
    private static String clock(double seconds){return String.format(Locale.ROOT,"%d:%02d",(int)seconds/60,(int)seconds%60);}
    private void message(String text){Toast.makeText(this,text==null?"처리에 실패했습니다.":text,Toast.LENGTH_LONG).show();}
}
