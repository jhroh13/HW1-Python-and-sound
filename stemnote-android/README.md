# Stemnote Android

> **2.1 업데이트:** 기본 채보를 기기 내 Basic Pitch 학습 모델로 변경했습니다. 최신 사용법·지원 범위·SDK 빌드는 [AI_ANDROID.md](AI_ANDROID.md)를 보세요. 아래 내용은 2.0 실험적 분석 버전의 기록입니다.

Android SDK로 구현한 **네이티브 앱 개발 프로젝트**입니다. WebView, 웹 프런트엔드, HTTPS 실행 서버, Node.js를 사용하지 않습니다. 기존 웹 프로토타입을 실행하는 래퍼가 아니라 UI, 녹음, 해독, 분석, 재생, 파일 생성을 Java로 옮겼습니다.

GitHub Actions에서 **APK 빌드, Android Lint, 코어 테스트 및 APK v2 서명 검증을 완료했습니다.** 실기기·에뮬레이터 실행은 아직 검증하지 못했습니다. [검증 빌드](https://github.com/jhroh13/HW1-Python-and-sound/actions/runs/39599522329)와 [빌드 기록](BUILD_STATUS.md)을 확인하세요.

## 기능

- 기기 음악 파일 선택: Android Storage Access Framework + `MediaExtractor`/`MediaCodec`.
- 마이크 캡처 시작 / 완료 / 취소: `AudioRecord`, `RECORD_AUDIO` 런타임 권한.
- 다른 휴대폰에서 스피커로 재생하는 음악 또는 목소리/연주 구간 캡처.
- 캡처 중 입력 레벨과 대표 음높이 추정. 완료 후 해당 구간을 분석.
- 최대 120초 자동 완료. 화면 이탈 시 녹음을 완료하고 마이크 해제. 녹음 중 화면 켜짐 유지.
- 보이스, 어쿠스틱 기타, 일렉트릭 기타, 드럼, 베이스, 피아노, 윈드, 전자 사운드의 8개 추정 채널.
- 채널 선택과 레벨 조절, 선택 채널 오디오 믹스 / MIDI 합성음 미리듣기 (`AudioTrack`).
- 악기별 MIDI + 선택 채널 멀티트랙 MIDI ZIP 저장. WAV 믹스 동시 저장 옵션.
- 파일명 기본값: `원곡제목_악기이름_CH.mid`. 저장 전 곡 제목 수정 가능.
- 저장 위치는 Android 시스템 파일 저장창에서 선택. 광범위한 저장소 권한 없음.
- 앱에 네트워크 권한이 없으며 음원을 전송하지 않음.

이 앱은 주변 소리를 마이크로 녹음합니다. 다른 기기에 자동 연결하지 않으며 같은 기기의 다른 앱 내부 오디오를 직접 캡처하는 MediaProjection/AudioPlaybackCapture 기능은 포함하지 않습니다. 같은 기기의 다른 앱 재생과 마이크 동시 사용은 OS/앱 정책에 따라 제한될 수 있습니다.

## 개발 환경과 빌드

- Android 8.0 (API 26) 이상
- compile/target SDK 35
- Android Gradle Plugin 8.7.3, Gradle 8.9
- JDK 17 (권장), Android SDK Platform 35, Build Tools 35.0.0
- UI 및 앱 코드는 Java 17, 외부 런타임 라이브러리 없음

Android Studio에서 프로젝트 루트를 엽니다. SDK 35와 JDK를 설정하고 Gradle 8.9를 설치한 뒤 실행합니다. 최초 저장소는 Gradle Wrapper 바이너리를 포함하지 않습니다. 설치된 Gradle로 다음을 실행하면 Wrapper도 생성할 수 있습니다.

```bash
gradle wrapper --gradle-version 8.9 --distribution-type bin
gradle :app:assembleDebug :app:lintDebug
```

APK 출력: `app/build/outputs/apk/debug/app-debug.apk`.

디버그 APK는 개발용 자동 서명입니다. Play Store 배포용 키와 릴리스 서명은 포함하지 않습니다.

## GitHub에서 개발

GitHub 저장소 `jhroh13/HW1-Python-and-sound`의 `codex/stemnote-android-apk` 브랜치에 `stemnote-android/` 하위 프로젝트로 추가했습니다. 기존 `master` 브랜치와 웹앱은 변경하지 않았습니다. 저장소 루트의 `.github/workflows/android-native.yml`이 해당 하위 프로젝트를 빌드합니다. 이 폴더를 별도 저장소 루트로 옮길 때는 폴더 내부 `.github/workflows/android.yml`을 사용합니다.

이 프로젝트 전체를 GitHub 저장소 루트에 올리면 `.github/workflows/android.yml`이 push / pull request / 수동 실행에서 다음을 수행합니다.

1. JDK 17, Android SDK 35, Gradle 8.9 설정
2. `bash tests/run.sh`로 코어 테스트와 Java 구문 검사
3. `:app:assembleDebug :app:lintDebug` 실행
4. 성공 시 Actions의 **Stemnote-Android-debug** artifact에 APK 업로드

저장소에 올라간 이후에는 기능 브랜치와 PR로 변경을 검토할 수 있습니다. APK 생성, Lint, 코어 테스트, 서명 검증은 위 실행에서 통과했습니다. 실기기 동작 검증은 별도입니다.

## 검증

```bash
bash tests/run.sh
```

실제 Java 분석/파일 생성 코드를 컴파일하고 JDK의 독립 MIDI/WAV 리더로 결과를 읽습니다. 무음, 440Hz, FFT 채널 재합성, 노트 범위, 분석 취소, 리샘플링, 실시간 음높이, MIDI 타이밍·velocity·GM 드럼 채널 10·레벨 0 음소거, 선택 믹스, 합성음, WAV 헤더·길이, 한글 파일명의 선택 채널 ZIP을 검증합니다.

별도 구문 검사는 모든 Android Java 파일을 파싱하지만 **Android SDK 타입 검증과 APK 빌드를 대체하지 않습니다.** `out/`은 테스트 생성물이며 버전 관리에서 제외됩니다.

실기기 검증 체크리스트는 [DEVICE_TESTS.md](DEVICE_TESTS.md)를 확인하세요.

## 분석의 한계

분석기는 주파수 마스크와 피크 검출을 사용하는 실험적 방식입니다. 학습된 악기 분리·다성 채보 모델이 없으므로 같은 음역의 악기를 정확히 식별하지 못하고 채널에 다른 악기가 섞일 수 있습니다. 이 한계는 앱 화면과 저장 안내에도 표시합니다. 전문 품질에는 별도 학습 모델 통합이 필요합니다.

- 분석 및 WAV: 22.05kHz, 모노. WAV 저장은 16bit.
- MIDI: 480 PPQ / 120 BPM을 절대 시간축으로 사용. 원곡 템포 감지값이 아닙니다.
- 드럼은 GM 채널 10, 다른 악기는 각기 다른 GM 채널/program 사용.
- 캡처 원곡명을 자동 식별하지 않으며, 제목 미입력 시 `마이크캡처_yyyyMMdd_HHmmss` 사용.
- 입력 파일 코덱 지원은 Android 기기별로 다릅니다. 16bit 또는 Float PCM 디코더 출력을 지원합니다.
- 리샘플링은 창 함수 기반 저역 통과 sinc 방식입니다. 완성된 전문 오디오 엔진의 품질/성능을 보장하지 않습니다.
- 120초·8채널 분석은 메모리와 CPU를 사용하므로 저사양 기기에서는 더 짧은 구간을 권장합니다.
- 프로젝트는 메모리에 유지됩니다. 프로세스 종료/앱 재시작 시 사라지므로 ZIP으로 저장해주세요.
- 캡처 취소 및 분석 실패 시 기존 프로젝트를 유지합니다.

## 주요 코드

- `MainActivity.java`: 네이티브 화면, 권한, 파일 선택·저장 및 작업 흐름
- `NativeCapture.java`: PCM 마이크 캡처와 해제
- `AudioDecoder.java`: MediaCodec 파일 해독
- `NativePlayer.java`: 선택 믹스 재생
- `core/Analyzer.java`: STFT 마스크와 음표 추정
- `core/Exports.java`: MIDI, WAV, ZIP, 믹스, MIDI 미리듣기 합성

참고한 Android 공식 문서:

- https://developer.android.com/reference/android/media/AudioRecord
- https://developer.android.com/media/platform/mediarecorder (권한 및 백그라운드 마이크 제한)
- https://developer.android.com/training/data-storage/shared/documents-files
- https://developer.android.com/reference/android/media/MediaCodec
