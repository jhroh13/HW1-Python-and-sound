package com.stemnote.core;

public final class Pitch {
    private Pitch(){}
    public static double detect(float[] source,int length,int rate){
        int step=Math.max(1,rate/11025),n=Math.min(1024,length/step);if(n<128)return 0;
        double[] data=new double[n];double mean=0,power=0;for(int i=0;i<n;i++)mean+=source[i*step];mean/=n;
        for(int i=0;i<n;i++){data[i]=source[i*step]-mean;power+=data[i]*data[i];}if(Math.sqrt(power/n)<.008)return 0;
        double sr=rate/(double)step;int min=(int)(sr/1000),max=Math.min((int)(sr/70),n/2);double[] scores=new double[max+1];
        for(int lag=min;lag<=max;lag++){double dot=0,a=0,b=0;for(int i=0;i<n-max;i++){dot+=data[i]*data[i+lag];a+=data[i]*data[i];b+=data[i+lag]*data[i+lag];}scores[lag]=dot/Math.sqrt(a*b+1e-16);}
        for(int lag=min+1;lag<max;lag++)if(scores[lag]>.87&&scores[lag]>=scores[lag-1]&&scores[lag]>scores[lag+1]){double divisor=scores[lag-1]-2*scores[lag]+scores[lag+1],adjust=divisor==0?0:.5*(scores[lag-1]-scores[lag+1])/divisor;return sr/(lag+Math.max(-.5,Math.min(.5,adjust)));}return 0;
    }
    public static String label(double frequency){if(frequency<=0)return "대표 음높이 —";int p=(int)Math.round(69+12*Math.log(frequency/440)/Math.log(2));String[] names={"C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"};return "대표 음높이(추정) "+names[Math.floorMod(p,12)]+(Math.floorDiv(p,12)-1)+" · "+Math.round(frequency)+" Hz";}
}
