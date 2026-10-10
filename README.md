# WAVE — 나만의 오디오 스튜디오

음원 업로드, 재생, 파형 탐색을 제공하는 한국어 웹앱입니다. 파일은 서버에 전송하거나 저장하지 않고 브라우저 안에서만 처리합니다. 새로고침하면 불러온 음원이 사라집니다.

## 실행

Node.js 20.19 이상(또는 22.12 이상)을 사용하세요. 현재 개발 환경은 Node.js 24입니다.

```sh
cd /workspace/HW1-Python-and-sound
npm ci
npm run dev -- --port 5173
```

## 기능

- 파일 선택 또는 드래그 앤 드롭으로 음원 불러오기(최대 100 MB)
- 실제 오디오 데이터를 디코딩한 파형, 재생 위치 표시
- 재생/일시정지, 5초 이동, 음량/음소거
- 파형 클릭으로 탐색, 방향키로 5초 이동, Home/End로 처음/끝 이동
- 파형이나 페이지에 포커스가 있을 때 Space로 재생/일시정지
- 직접 생성한 10초 샘플 멜로디, 모바일 화면 지원
- 잘못된 파일이나 디코딩 실패 안내

MP3, WAV, OGG, M4A 등의 지원 여부는 브라우저의 오디오 코덱에 따라 달라집니다. 한 번에 하나의 음원을 불러옵니다. 분석에는 Web Audio API, 재생에는 HTML Audio, 파형에는 Canvas를 사용합니다. 최신 Chrome, Edge, Firefox, Safari를 권장하며 현재 자동화 검증은 Chromium에서 수행합니다. 외부 서비스나 API 키는 필요하지 않습니다.

## 빌드와 검증

```sh
npm run build
npm run preview -- --port 4173
npm test
```

`dist/`는 정적 웹 호스팅에 배포할 수 있는 빌드 결과입니다. Vite 개발 서버와 preview 서버는 개발/빌드 확인용입니다.

브라우저 테스트는 음원 디코딩, 실제 재생, 파형/키보드 탐색, 파일 교체, 오류 복구, 드래그 앤 드롭, 음량, 모바일 화면을 검증합니다. `/usr/bin/chromium`이 있으면 사용하며, 없으면 `npx playwright install --with-deps chromium`으로 브라우저를 준비하세요. 다른 실행 파일은 `CHROMIUM_PATH=/path/to/chromium npm test`로 지정할 수 있습니다.

## 웹 릴리즈

[릴리즈·배포 안내](RELEASE.md)를 참고하세요. GitHub Pages용 자동 배포 워크플로를 포함합니다. `dist/`는 상대 경로로 빌드되어 정적 호스팅의 루트 또는 프로젝트 하위 경로에 배포할 수 있습니다.
