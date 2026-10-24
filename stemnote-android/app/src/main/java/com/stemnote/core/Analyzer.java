package com.stemnote.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Experimental spectral masks; this is not a trained source separation model. */
public final class Analyzer {
    private static final int N=2048,H=512,SR=AudioProject.SAMPLE_RATE;
    public interface Progress {void update(int percent);}
    private static final class Active {double start,last,amplitude;Active(double t,double amp){start=last=t;amplitude=amp;}}
    private static final class Peak {int bin,pitch;double frequency,amplitude;}
    private static double band(double f,double lo,double hi){return Math.exp(-Math.pow(2*Math.log(Math.max(f,1)/Math.sqrt(lo*hi))/Math.log(hi/lo),4));}

    public AudioProject analyze(float[] samples,String title,String source,Progress progress) {
        if(samples.length<SR/10||samples.length>SR*AudioProject.MAX_SECONDS)throw new IllegalArgumentException("0.1초~120초 구간을 선택해주세요.");
        AudioProject.Channel[] channels=new AudioProject.Channel[Instrument.COUNT];
        for(int id=0;id<8;id++)channels[id]=new AudioProject.Channel(Instrument.values()[id],samples.length);
        channels[Instrument.OTHER.ordinal()]=new AudioProject.Channel(Instrument.OTHER,0);
        double[] window=new double[N],previous=new double[N/2+1],re=new double[N],im=new double[N],r=new double[N],ii=new double[N],mag=new double[N/2+1];
        float[] norm=new float[samples.length];double[][] weights=new double[8][N/2+1],base=new double[8][N/2+1];
        double[][] ranges={{180,2600},{110,3800},{140,6500},{60,10500},{30,240},{65,4200},{450,5500},{900,10000}};
        for(int j=0;j<N;j++)window[j]=.5-.5*Math.cos(2*Math.PI*j/N);
        for(int id=0;id<8;id++)for(int k=0;k<=N/2;k++)base[id][k]=band(k*(double)SR/N,ranges[id][0],ranges[id][1]);
        Active[][] active=new Active[8][128];double previousEnergy=0,lastDrum=-1;
        FFT fft=new FFT(N);int frames=0;
        for(int pos=-N+H;pos<samples.length;pos+=H) {
            if(Thread.currentThread().isInterrupted())throw new CancellationException();
            for(int j=0;j<N;j++){int at=pos+j;re[j]=(at>=0&&at<samples.length?samples[at]:0)*window[j];im[j]=0;}
            fft.run(re,im,false);double energy=0,flux=0,max=0;
            for(int k=0;k<=N/2;k++){mag[k]=Math.hypot(re[k],im[k]);max=Math.max(max,mag[k]);if(k>0){energy+=mag[k];flux+=Math.max(0,mag[k]-previous[k]);previous[k]=mag[k];}}
            double transientValue=Math.min(1,flux/(energy+.001)*2);
            double[] factors={1.4-transientValue,.35+transientValue*.7,.5,.1+transientValue*2,2,.3+transientValue,1.5-transientValue,.5};
            for(int k=0;k<=N/2;k++){double sum=1e-20;for(int id=0;id<8;id++){weights[id][k]=base[id][k]*factors[id];sum+=weights[id][k];}for(int id=0;id<8;id++)weights[id][k]/=sum;}
            double t=Math.max(0,(pos+N/2)/(double)SR);List<Peak> peaks=new ArrayList<>();
            for(int k=3;k<N/2-1;k++)if(mag[k]>max*.12&&mag[k]>.5&&mag[k]>mag[k-1]&&mag[k]>mag[k+1]) {
                double fraction=.5*(mag[k-1]-mag[k+1])/(mag[k-1]-2*mag[k]+mag[k+1]);
                double f=(k+Math.max(-.5,Math.min(.5,fraction)))*SR/N;
                int pitch=(int)Math.round(69+12*Math.log(f/440)/Math.log(2));
                if(pitch>=28&&pitch<=96){Peak peak=new Peak();peak.bin=k;peak.pitch=pitch;peak.frequency=f;peak.amplitude=mag[k]/N;peaks.add(peak);}
            }
            peaks.sort(Comparator.comparingDouble((Peak p)->p.amplitude).reversed());
            List<Peak> accepted=new ArrayList<>();double[][] found=new double[8][128];
            for(Peak p:peaks){boolean harmonic=false;for(Peak a:accepted){double ratio=p.frequency/a.frequency;if(ratio>1.8&&Math.abs(ratio-Math.round(ratio))<.045){harmonic=true;break;}}if(harmonic)continue;accepted.add(p);int bestId=0;double best=0;for(int id=0;id<8;id++)if(id!=3&&weights[id][p.bin]>best){best=weights[id][p.bin];bestId=id;}found[bestId][p.pitch]=p.amplitude;if(accepted.size()>=8)break;}
            if(transientValue>.65&&energy>previousEnergy*1.08&&t-lastDrum>.11&&max>2&&t<samples.length/(double)SR){double low=0,high=0;for(int k=1;k<16;k++)low+=mag[k];for(int k=400;k<900;k++)high+=mag[k];channels[3].notes.add(new AudioProject.Note(low>high?36:high>energy*.15?42:38,t,Math.min(.1,samples.length/(double)SR-t),90));lastDrum=t;}
            previousEnergy=energy;
            for(int id=0;id<8;id++) {
                for(int p=28;p<=96;p++){Active a=active[id][p];if(a!=null){if(found[id][p]>0){a.last=t;a.amplitude=Math.max(a.amplitude,found[id][p]);}else if(t-a.last>.07){finish(channels[id],p,a,samples.length);active[id][p]=null;}}if(found[id][p]>0&&active[id][p]==null)active[id][p]=new Active(t,found[id][p]);}
                for(int k=0;k<N;k++){double w=weights[id][k<=N/2?k:N-k];r[k]=re[k]*w;ii[k]=im[k]*w;}
                fft.run(r,ii,true);
                for(int j=0;j<N;j++)if(pos+j>=0&&pos+j<samples.length)channels[id].audio[pos+j]+=r[j]*window[j];
            }
            for(int j=0;j<N;j++)if(pos+j>=0&&pos+j<samples.length)norm[pos+j]+=window[j]*window[j];
            if(++frames%12==0)progress.update((int)Math.max(0,Math.min(99,pos*100L/samples.length)));
        }
        for(int id=0;id<8;id++){for(int p=28;p<=96;p++)if(active[id][p]!=null)finish(channels[id],p,active[id][p],samples.length);for(int j=0;j<samples.length;j++)channels[id].audio[j]/=Math.max(norm[j],.01f);}
        progress.update(100);return new AudioProject(title,source,channels);
    }
    private static void finish(AudioProject.Channel channel,int pitch,Active a,int length){double end=Math.min(length/(double)SR,a.last+H/(double)SR);if(end-a.start>=.065)channel.notes.add(new AudioProject.Note(pitch,a.start,end-a.start,Math.min(110,Math.max(35,(int)Math.round(a.amplitude*350)))));}

    private static final class FFT {
        final int n;final int[] reverse;final double[] cos,sin;
        FFT(int n){this.n=n;reverse=new int[n];int bits=Integer.numberOfTrailingZeros(n);for(int i=0;i<n;i++)reverse[i]=Integer.reverse(i) >>> (32-bits);cos=new double[n/2];sin=new double[n/2];for(int i=0;i<n/2;i++){cos[i]=Math.cos(2*Math.PI*i/n);sin[i]=Math.sin(2*Math.PI*i/n);}}
        void run(double[] re,double[] im,boolean inverse){for(int i=0;i<n;i++){int j=reverse[i];if(i<j){double x=re[i];re[i]=re[j];re[j]=x;x=im[i];im[i]=im[j];im[j]=x;}}for(int len=2;len<=n;len*=2){int half=len/2,step=n/len;for(int i=0;i<n;i+=len)for(int j=0;j<half;j++){double wr=cos[j*step],wi=sin[j*step]*(inverse?1:-1);int k=i+j,l=k+half;double tr=re[l]*wr-im[l]*wi,ti=re[l]*wi+im[l]*wr;re[l]=re[k]-tr;im[l]=im[k]-ti;re[k]+=tr;im[k]+=ti;}}if(inverse)for(int i=0;i<n;i++){re[i]/=n;im[i]/=n;}}
    }
}
