# Android 2.1 · 기기 내 AI 채보

Android SDK / Java 네이티브 앱의 기본 분석을 Spotify Basic Pitch 학습 모델 + TensorFlow Lite 2.16.1로 변경했습니다. 모델은 APK에 포함되며 음원을 서버로 보내지 않습니다.

## 사용법

1. 분석 방식은 `AI 음표 채보`를 선택합니다. 전체 믹스는 `미분류 반주`, 분리된 단일 악기 파일은 해당 악기를 지정합니다.
2. 음악 파일 선택 또는 마이크 `캡처 시작` / `캡처 완료`로 최대 120초를 입력합니다.
3. 분리된 다른 악기 파일은 `기존 AI 프로젝트에 트랙 추가/교체`를 체크하고 악기를 지정한 뒤 가져옵니다. 동일한 곡의 동일한 시작 위치여야 하며 모든 파일은 0초를 기준으로 정렬합니다. 같은 악기 채널은 교체됩니다.
4. 채널/레벨을 선택하고 원음 믹스 또는 MIDI 합성음으로 미리 듣습니다.
5. 저장할 제목과 WAV 옵션을 정하고 ZIP을 저장합니다. 채널별 MIDI, 멀티트랙 MIDI, 선택 시 원음 WAV 믹스가 포함됩니다.

파일명은 `곡제목_악기이름_CH.mid`입니다. 기본 채널 레벨은 100%입니다. `미분류 반주`는 MIDI 채널 8로 저장합니다. 기존 8개 채널의 번호는 유지됩니다.

## 지원 범위

이번 버전은 **기기 내 AI 음표 채보**를 추가합니다. **자동 악기 분리·보컬 제거·드럼 채보 모델은 포함하지 않습니다.** 혼합 파일에 악기 이름을 지정해도 해당 악기만 분리되지는 않습니다. WAV는 선택한 입력 트랙의 원음 믹스이며 자동 보컬 제거 결과가 아닙니다.

Basic Pitch는 단일 악기/분리된 트랙에 더 적합하며 복잡한 합주에서는 누락·추가 음표가 생길 수 있습니다. 학습 모델을 넣었다는 사실만으로 실제 음악의 정확도가 검증되었다고 주장하지 않습니다. 기존 8채널 주파수 추정은 비교용 실험 모드로 제공하며 오분류가 많습니다. AI 실패 시 자동으로 전환하지 않습니다.

- 입력·WAV는 모노 22.05kHz, WAV는 PCM 16bit입니다.
- MIDI 480 PPQ / 120 BPM은 절대 시간 저장용이며 원곡 템포 감지값이 아닙니다. 양자화와 pitch bend는 지원하지 않습니다.
- MIDI 미리듣기는 간단한 합성음이며 실제 GM 음원 라이브러리와 다릅니다.
- 다른 휴대폰의 소리는 스피커를 통해 마이크로 녹음합니다. 같은 기기의 다른 앱 내부 오디오는 직접 캡처하지 않습니다.
- 프로젝트는 메모리에 유지됩니다. 종료 전에 ZIP으로 저장해주세요.
- 배포 APK는 Android 8.0 이상 ARM64용입니다. 다른 CPU는 해당 ABI로 별도 빌드합니다.
- 새 CI의 디버그 서명은 기존 배포본과 다를 수 있습니다. 업데이트가 거부되면 프로젝트를 ZIP 저장한 뒤 이전 앱을 삭제하고 설치하세요.

## 개발/빌드

JDK 17, Android SDK 35, Build Tools 35.0.0, AGP 8.7.3, Gradle 8.9를 사용합니다.

```bash
python3 tools/fetch_model.py
bash tests/run.sh
gradle --no-daemon :app:assembleDebug :app:lintDebug
```

모델 다운로드는 개발/빌드 시 수행하고 고정된 공식 Git blob 체크섬을 검사합니다. 실행 시 다운로드하지 않습니다. 모델이 없거나 다르면 빌드를 중단합니다.

```bash
gradle -PstemnoteAbi=x86_64 :app:connectedDebugAndroidTest
```

코어 테스트는 MIDI/WAV/ZIP과 AI 후처리·다성 음표·반복 onset·길이 필터·velocity·120초 윈도 연결 타이밍을 검사합니다. Android 테스트는 앱에 포함된 실제 모델로 무음·단음·화음을 분석하고 네이티브 화면 실행을 확인합니다. 생성음 테스트는 실제 합주곡의 정확도 측정을 대체하지 않습니다.

## GitHub

`jhroh13/HW1-Python-and-sound`의 `codex/stemnote-ondevice-ai` 브랜치에서 개발합니다. 프로젝트는 `stemnote-android/`, 실제 Actions는 루트 `.github/workflows/android-native.yml`입니다. `master`는 변경하지 않습니다. 이 폴더를 독립 저장소로 사용할 때는 내부 `.github/workflows/android.yml`을 사용하세요.

Basic Pitch 라이선스·출처: [third_party/basic-pitch](third_party/basic-pitch/README.md). 모델은 변경하지 않았으며 Java 실행 코드와 note/onset 후처리를 추가했습니다.
