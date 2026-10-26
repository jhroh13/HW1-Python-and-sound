# Stemnote Android 2.1

Android SDK / Java 네이티브 음악 채보 앱입니다. Spotify Basic Pitch 학습 모델을 TensorFlow Lite로 기기에서 실행합니다. 모델은 APK에 포함되며 앱은 인터넷 권한을 사용하지 않습니다.

음악 파일 입력, 마이크 캡처 시작/완료, 채널 선택과 레벨 조절, 원음/MIDI 합성음 미리듣기, 저장 이름 수정 및 MIDI/WAV ZIP 저장을 지원합니다. 분리된 악기 파일을 채널별로 추가할 수 있습니다.

**자동 악기 분리·보컬 제거·드럼 AI 채보는 아직 포함하지 않습니다.** 혼합곡은 기본적으로 ‘미분류 반주’로 채보합니다. 악기 이름만 지정해도 그 악기만 분리되는 것은 아닙니다. 실제 합주곡의 채보 정확도는 추가 평가가 필요합니다.

- [APK 다운로드 (ZIP)](https://github.com/jhroh13/HW1-Python-and-sound/actions/runs/40269815833/artifacts/12627971036)
- [사용법·지원 범위·SDK 빌드](AI_ANDROID.md)
- [빌드/검증 기록](BUILD_STATUS.md)
- [실기기 확인 항목](DEVICE_TESTS.md)
- [모델 출처와 라이선스](third_party/basic-pitch/README.md)

Android 8.0 이상 ARM64용 디버그 APK입니다. ZIP을 풀고 APK를 설치하세요. 이전 2.0 배포본과 서명이 다르므로 기존 프로젝트를 ZIP으로 저장한 뒤 이전 앱을 삭제하고 설치해야 합니다.

개발 환경: compile/target SDK 35, JDK 17, AGP 8.7.3, Gradle 8.9, TensorFlow Lite 2.16.1.

```bash
python3 tools/fetch_model.py
bash tests/run.sh
gradle --no-daemon :app:assembleDebug :app:lintDebug
```

GitHub Actions에서 APK 빌드, Lint, 서명, 코어 테스트와 Android 11 x86_64 에뮬레이터 테스트 4개를 통과했습니다. 실제 ARM64 휴대폰의 마이크/파일 선택/저장과 실제 합주곡의 정확도는 별도 확인이 필요합니다.

프로젝트는 `stemnote-android/`, 실제 Actions 워크플로는 저장소 루트 `.github/workflows/android-native.yml`입니다. `codex/stemnote-ondevice-ai` 브랜치에서 개발하며 master는 유지합니다.
