# Stemnote Android 2.2 · ZeroGPU

Android SDK 네이티브 앱에서 Hugging Face ZeroGPU의 Demucs 모델로 음원을 분리하고, 트랙별로 기기 내 AI 채보를 진행합니다. 파일 입력과 마이크 캡처 시작/완료, 선택 트랙 믹스 미리듣기, 이름 수정 및 MIDI/WAV ZIP 저장을 지원합니다.

[ZeroGPU 설정·사용법·지원 범위](ZEROGPU.md) · [배포용 Space 코드](zerogpu-space/) · [빌드 기록](BUILD_STATUS.md)

ZeroGPU는 보컬/드럼/베이스/나머지의 4트랙 또는 기타(통합)/피아노를 추가한 6트랙으로 분리합니다. 어쿠스틱/일렉트릭 구분과 독립 관악기·신스 분리는 지원하지 않습니다. 드럼은 WAV만 지원합니다. 보컬은 결과의 기본 선택에서 제외합니다.

이 모드는 설정한 Space로 음원을 전송합니다. 실제 사용에는 활성화된 ZeroGPU Space 주소와 계정별 이용 자격/할당량이 필요합니다. 로컬 AI 채보 모드도 유지합니다.

Android 8.0 이상 ARM64용, SDK 35 / Java 17 / Gradle 8.9.

현재 빌드와 Android 테스트 8개 및 실제 Demucs CPU API 검증은 통과했습니다. [빌드/배포 상태](BUILD_STATUS.md)를 확인하세요. 원격 Midigen은 HF_TOKEN 쓰기 인증 부재로 아직 static입니다. `HF_TOKEN` 등록 후 실패한 배포 작업을 재실행하면 Gradio 전환·ZeroGPU 활성화·실제 API 검증을 수행합니다.
