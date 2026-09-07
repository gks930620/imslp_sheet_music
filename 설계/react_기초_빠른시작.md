# React 기초 빠른시작 (이 프로젝트 기준)

## 1. React를 한 줄로
- React는 "화면을 컴포넌트 단위로 쪼개서 상태(state)가 바뀌면 자동으로 다시 그려주는" UI 라이브러리입니다.

## 2. 이 프로젝트에서 꼭 알아야 할 최소 개념

### 컴포넌트
- 화면 조각입니다. 예: `Layout`, `CommunityListPage`, `RoomPage`
- 파일 위치: `frontend/src/components`, `frontend/src/pages`

### 상태(State)
- 화면에서 변하는 값입니다.
- 예: 게시글 목록, 로그인 사용자 정보, 입력값
- `useState`로 관리합니다.

### 효과(Effect)
- API 호출처럼 "렌더링 후 해야 하는 일"입니다.
- `useEffect`에서 처리합니다.
- 예: 페이지 진입 시 `/api/communities` 호출

### 라우팅
- URL과 컴포넌트를 연결합니다.
- 이 프로젝트는 `react-router-dom` 사용
- 진입 파일: `frontend/src/App.jsx`

## 3. 이 프로젝트 실행 구조 (단일 서버)
- React를 Vite로 빌드하면 `frontend/dist` 생성
- Gradle이 해당 빌드 산출물을 `src/main/resources/static`으로 복사
- Spring이 정적 파일을 서빙
- 결과: `http://localhost:8080` 하나로 화면+API 모두 동작

## 4. 실제 작업 순서 (Windows PowerShell)

```powershell
# 프로젝트 루트
node -v
npm.cmd -v

# 프론트 의존성 설치/빌드
cd frontend
npm.cmd ci
npm.cmd run build

# 루트로 돌아와서 Spring 정적 리소스 반영
cd ..
.\gradlew.bat processResources
.\gradlew.bat bootRun
```

## 5. API 호출 방식 (현재 표준)
- 공개 API: `callPublicApi`
- 인증 API: `callApi` 또는 `authFetch`
- 파일 업로드: `authFetch` + `FormData`
- 공통 유틸: `frontend/src/lib/http.js`

## 6. 자주 보는 파일
- 라우팅: `frontend/src/App.jsx`
- 인증 상태: `frontend/src/context/AuthContext.jsx`
- API 유틸: `frontend/src/lib/http.js`
- 커뮤니티 목록: `frontend/src/pages/CommunityListPage.jsx`
- 커뮤니티 상세/댓글: `frontend/src/pages/CommunityDetailPage.jsx`
- 채팅: `frontend/src/pages/RoomPage.jsx`
- 백엔드 SPA 포워딩: `src/main/java/com/test/test/HomeController.java`

## 7. 디버깅 시작점
- 화면이 안 뜨면:
  1. `frontend/dist/index.html` 생성 여부 확인
  2. `src/main/resources/static/index.html` 갱신 여부 확인
  3. `HomeController` 포워딩 경로 확인
- API가 실패하면:
  1. 브라우저 Network 탭에서 상태코드 확인
  2. 응답 JSON의 `errorCode` 확인
  3. 인증 API면 쿠키(`access_token`, `refresh_token`) 확인
