package com.stemnote.core;

public enum Instrument {
    VOICE("보이스", "보이스", 0, 53, 0xffd4b4e9),
    ACOUSTIC("어쿠스틱 기타", "어쿠스틱기타", 1, 25, 0xffe4be8c),
    ELECTRIC("일렉트릭 기타", "일렉트릭기타", 2, 29, 0xffe5a39b),
    DRUMS("드럼", "드럼", 9, 0, 0xff9dbddd),
    BASS("베이스", "베이스", 3, 33, 0xffb2c989),
    PIANO("피아노", "피아노", 4, 0, 0xffddcc8e),
    WINDS("윈드 계열", "윈드", 5, 73, 0xff95c8bf),
    SYNTH("전자 사운드", "전자사운드", 6, 81, 0xffafa7e7),
    OTHER("미분류 반주", "미분류반주", 7, 0, 0xffb8c6db);

    public static final int COUNT=values().length;

    public final String title, fileName;
    public final int channel, program, color;
    Instrument(String title, String fileName, int channel, int program, int color) {
        this.title=title; this.fileName=fileName; this.channel=channel; this.program=program; this.color=color;
    }
}
