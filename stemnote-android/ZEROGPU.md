# Android + Hugging Face ZeroGPU

앱은 Android SDK 네이티브 UI를 유지합니다. 음악 파일 또는 마이크 캡처를 WAV로 변환해 사용자가 지정한 Hugging Face ZeroGPU Space로 전송하고, Demucs가 분리한 트랙을 받습니다. 음표 채보는 각 트랙에 대해 기기의 Basic Pitch 모델로 진행합니다.

## 사용

1. ZeroGPU로 실행 중인 이 프로젝트의 Space 주소 `https://사용자-공간.hf.space`를 앱에 입력합니다. Hugging Face의 `/spaces/사용자/공간` 페이지 주소는 실행 주소가 아닙니다.
2. 비공개 Space라면 읽기 토큰을 입력합니다. 토큰은 APK에 포함하거나 기기에 저장하지 않으며 앱을 켜 둔 동안만 유지합니다. 주소만 기기에 저장합니다.
3. 6트랙 또는 4트랙 모델을 선택합니다. WAV만 필요하면 ‘MIDI 음표 채보도 진행’을 해제합니다.
4. 음악 파일을 선택하거나 캡처 시작/완료로 최대 120초를 입력합니다. 업로드·GPU 대기·분리·다운로드·채보 상태가 표시됩니다.
5. 보컬은 결과의 기본 선택에서 제외됩니다. 원하는 트랙을 선택해 미리듣고 MIDI/WAV를 ZIP으로 저장합니다. 보컬을 다시 선택하면 보컬 트랙도 포함됩니다.

6트랙: 보컬, 드럼, 베이스, 기타(통합), 피아노, 나머지 반주. 4트랙: 보컬, 드럼, 베이스, 나머지 반주. 6트랙의 기타는 어쿠스틱/일렉트릭을 구분하지 않습니다. 관악기와 신스는 나머지 반주에 포함됩니다. 분리 잔여음이 남을 수 있으며 6트랙 피아노 분리는 특히 입력에 따라 품질이 달라집니다.

드럼 MIDI 모델은 아직 포함하지 않습니다. 드럼 및 음표가 검출되지 않은 트랙은 WAV로 들을 수 있으며 빈 MIDI 파일을 만들지 않습니다. 선택한 트랙에 MIDI 음표가 없으면 WAV 저장을 선택해야 합니다. 출력 WAV는 기존 앱과 동일하게 모노 22.05kHz PCM16입니다.

## Space 준비

서버 폴더: `zerogpu-space/`. 해당 폴더의 파일을 Hugging Face Gradio Space 루트에 올리고 하드웨어 설정에서 **ZeroGPU**를 선택하세요. README만 업로드한다고 ZeroGPU가 활성화되지는 않습니다.

또는 본인 컴퓨터에서 Hugging Face SDK 인증을 안전하게 설정한 뒤 실행합니다. 토큰을 채팅이나 GitHub 코드에 넣지 마세요.

```bash
pip install huggingface_hub
python tools/deploy_zerogpu.py USER/stemnote-zerogpu
```

기본은 비공개 Space이며 공개를 원하면 `--public`을 지정합니다. 기존 Space의 공개 범위는 생성 호출로 변경하지 않습니다. 이 도구는 코드를 업로드하며 유료 GPU를 자동 요청하지 않습니다. ZeroGPU 선택과 계정 자격 확인은 Space 설정에서 수행합니다.

현재 공식 문서에는 이메일 인증 및 가입 30일 이상 등 조건을 만족한 무료 계정의 ZeroGPU 호스팅도 안내되어 있습니다. 계정별 자격·공유 GPU 대기·일일 사용량 제한은 Hugging Face에서 결정합니다. 앱은 한도/권한/서버 시작 오류를 표시하고 로컬 추정으로 몰래 전환하지 않습니다.

## 개발과 검증

APK 빌드: `python3 tools/fetch_model.py`, `bash tests/run.sh`, `gradle :app:assembleDebug :app:lintDebug`.

Android 계측 테스트는 기기 내 실제 채보 모델, 모의 Gradio 서버를 통한 HTTP 업로드→큐/SSE→ZIP 다운로드, 보컬 제외 믹스, 할당량 오류, 결과 손상/경로 검증을 확인합니다. Python 검증은 실제 Demucs 모델과 Gradio 서버를 CPU에서 실행해 4/6트랙 결과를 확인합니다. **CPU 및 모의 서버 검증은 실제 ZeroGPU 할당·실제 배포 Space 연결 검증과 구분해야 합니다.** 후자는 사용자 계정에서 ZeroGPU Space가 활성화된 뒤 확인합니다.

입력/결과는 Space의 임시 캐시에 약 1시간 남을 수 있습니다. Gradio 캐시 정리는 Space가 실행되는 동안 수행됩니다. 음원을 GitHub에 업로드하지 않습니다. Android 다운로드 ZIP은 이름/샘플 수/체크섬을 검사하고 파일 경로를 디스크에 그대로 풀지 않습니다.

공식 참고: https://huggingface.co/docs/hub/spaces-zerogpu · https://www.gradio.app/guides/querying-gradio-apps-with-curl · https://github.com/facebookresearch/demucs

## Midigen 원격 전환

대상: https://huggingface.co/spaces/j0ngh0r0h/Midigen . 앱의 기본 실행 주소는 `https://j0ngh0r0h-midigen.hf.space`입니다. 정적 Space 상태에서는 API가 동작하지 않으며 서버 전환 후 활성화됩니다.

GitHub 저장소의 Actions 비밀값 `HF_TOKEN`에 대상 Space 쓰기 권한이 있는 Hugging Face 토큰을 등록하면 배포 작업이 README SDK를 Gradio로 바꾸고 서버 파일을 반영한 뒤 ZeroGPU 하드웨어를 요청합니다. 기존 정적 파일과 공개 범위는 유지합니다. 유료 GPU로 전환하지 않습니다.

수동 실행은 `python tools/deploy_zerogpu.py j0ngh0r0h/Midigen --verify`입니다. `--verify`는 서버 시작 후 실제 배포된 4/6트랙 API를 생성음으로 검사합니다. 토큰이 없으면 원격 쓰기를 수행하지 않습니다.
