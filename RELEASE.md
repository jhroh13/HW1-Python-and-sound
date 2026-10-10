# WAVE 1.0.0 웹 릴리즈

## 제공 기능

음원 파일 선택·드래그 앤 드롭, 실제 파형 표시, 재생·일시정지, 5초 이동, 파형/키보드 탐색, 음량·음소거, 10초 샘플 멜로디, 모바일 화면을 제공합니다. 선택한 파일은 브라우저에서만 처리합니다.

## 배포 파일

`npm ci && npm run build`로 생성한 `dist/`의 **내용물 전체**를 정적 호스팅에 배포합니다. 상대 경로로 빌드하므로 루트와 `/HW1-Python-and-sound/` 같은 하위 경로에서 동일한 파일을 사용할 수 있습니다. JavaScript 모듈을 사용하는 앱이므로 HTML 파일을 직접 더블클릭하는 대신 HTTP(S)로 제공하세요.

## GitHub Pages 자동 배포

1. GitHub 저장소의 **Settings → Pages → Build and deployment → Source**를 **GitHub Actions**로 선택합니다. 저장소와 계정의 Pages 사용 권한이 필요합니다.
2. 앱 소스와 `.github/workflows/deploy-pages.yml`을 `master`에 푸시합니다.
3. **Actions → Release WAVE to GitHub Pages**에서 실행 결과를 확인합니다. `workflow_dispatch`로 수동 실행할 수도 있습니다.
4. Node.js 24에서 고정 의존성을 설치하고, Chromium 브라우저 테스트를 통과한 뒤 `dist/`를 배포합니다. 기존 사이트가 있다면 새 배포가 교체합니다.
5. 배포 작업이 출력한 URL을 열어 샘플 불러오기·실제 재생·파형 이동과 WAV 업로드를 확인합니다.

워크플로 파일이 준비되거나 로컬 빌드가 성공했다는 것만으로 공개 배포 완료를 의미하지 않습니다. GitHub Actions 실행 성공과 실제 공개 URL 검증이 모두 필요합니다.

## 다른 정적 호스팅

Vercel 또는 Netlify에서도 build command를 `npm run build`, output/publish directory를 `dist`, Node.js 버전을 24로 지정합니다. 또는 빌드 압축 파일의 내용을 직접 정적 사이트로 업로드할 수 있습니다. 서버 측 API나 비밀 키는 필요하지 않습니다.

## 알려진 범위

한 번에 한 파일을 불러오며 최대 크기는 100 MB입니다. 새로고침하면 선택한 음원은 사라집니다. 코덱 지원은 브라우저마다 다릅니다. 자동화 검증은 Chromium에서 수행하며 다른 브라우저 지원은 별도 확인이 필요합니다.
