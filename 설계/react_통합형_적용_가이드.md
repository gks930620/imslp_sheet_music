# React 통합형 적용 가이드

## 1. 이번에 반영된 내용
- `frontend/` Vite + React 앱 추가
- 기존 Thymeleaf 화면 라우팅을 SPA 포워딩으로 변경
  - `HomeController`가 `/`, `/community`, `/rooms/**` 등을 `forward:/index.html`로 전달
- 커뮤니티/댓글/파일/채팅/인증 화면을 React로 이식
- Gradle 빌드에 프론트 번들 자동 포함
  - `processResources` 전에 `frontend npm ci -> npm run build -> src/main/resources/static` 복사

## 2. 내가 해야 하는 외부 준비 사항

### 2-1. 로컬 설치
- Node.js LTS 설치 (권장: 20.x 이상)
  - https://nodejs.org/
- 로컬 실행에 **외부 DB 불필요** (개발/테스트는 인메모리 H2 사용). **Railway MySQL**은 **운영(prod) 프로파일에서만** 필요
- `.env` 파일 준비 (기존과 동일)
  - `KAKAO_CLIENT_ID`
  - `KAKAO_CLIENT_SECRET`
  - `GOOGLE_CLIENT_ID`
  - `GOOGLE_CLIENT_SECRET`
  - `JWT_SECRET_KEY`

### 2-2. OAuth 공급자 콘솔 설정 (필수)
- 카카오 개발자 콘솔
  - Redirect URI 등록:
    - `http://localhost:8080/login/oauth2/code/kakao`
- 구글 Cloud Console OAuth 클라이언트
  - Authorized redirect URI 등록:
    - `http://localhost:8080/login/oauth2/code/google`
- 운영 URL이 있다면 운영 도메인 URI도 같이 등록 필요

## 3. 실행 방법

### 3-1. 통합형 실행 (권장 기본)
- 명령:
  - `./gradlew bootRun` (Windows: `.\gradlew.bat bootRun`)
- 동작:
  - Gradle이 React를 빌드해서 Spring 정적 리소스로 포함
  - `http://localhost:8080` 단일 서버에서 화면+API 함께 동작

### 3-2. 프론트 개발 서버 분리 실행 (개발 편의)
- 터미널 1:
  - `./gradlew bootRun`
- 터미널 2:
  - `cd frontend`
  - `npm run dev` (PowerShell 정책 오류 시 `npm.cmd run dev`)
- 동작:
  - `http://localhost:5173`에서 React HMR
  - Vite proxy로 `/api`, `/custom-oauth2`, `/ws-chat` 요청을 8080으로 전달

## 4. 향후 분리형으로 전환할 때
- 백엔드는 현재 REST API를 그대로 유지
- 프론트는 별도 배포 (예: Vercel, Netlify, Nginx)
- 이때 필요한 핵심은 2가지
  - Spring CORS 허용 도메인 추가
  - 프론트 API base URL 환경변수화

## 5. 체크리스트
- [ ] Node.js 설치됨
- [ ] `.env` 값 채움
- [ ] 카카오/구글 Redirect URI 등록됨
- [ ] `./gradlew bootRun` 실행 시 `index.html` 정상 서빙
- [ ] 로그인/회원가입/커뮤니티/댓글/파일/채팅 기능 확인
