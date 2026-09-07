# 기존 코드 대비 React 전환 변경요약

## 한 줄 요약
- **HTML만 바뀐 게 아닙니다.**
- 화면 렌더링 방식(Thymeleaf+페이지별 JS)에서 **React SPA 구조**로 바뀌었고, 빌드/배포 흐름도 함께 바뀌었습니다.

---

## 1. 안 바뀐 것 (기능/백엔드 관점)
- REST API URL/계약은 기존 구조를 최대한 유지
  - 예: `/api/communities`, `/api/comments/**`, `/api/files/**`, `/api/rooms/**`
- 보안 방식 유지
  - JWT HttpOnly 쿠키, refresh 재발급 흐름 유지
- 도메인/서비스 계층 유지
  - community, comment, file, jwt, stomp 패키지 구조 유지
- 즉, **기능 자체(커뮤니티/댓글/파일/채팅/인증)는 동일**하게 동작하도록 유지

## 2. 바뀐 것 (핵심)

### 2-1. 화면 구성 방식
- 이전:
  - `templates/*.html` + `templates/layout/header.html` 내 공통 JS
  - 페이지마다 직접 DOM 조작, `fetch/ajax` 호출
- 현재:
  - `frontend/src`의 React 컴포넌트(`pages`, `components`)에서 렌더링
  - 라우팅은 `react-router-dom`으로 처리 (`frontend/src/App.jsx`)

### 2-2. 서버 라우팅 방식
- 이전:
  - URL마다 각 Thymeleaf 템플릿 반환
- 현재:
  - `HomeController`가 주요 URL을 `forward:/index.html`로 전달
  - React Router가 실제 페이지를 결정

### 2-3. API 호출 방식(프론트 내부)
- 이전:
  - 템플릿 JS에서 `authFetch`, `callApi` 등을 전역 함수로 사용
- 현재:
  - `frontend/src/lib/http.js`로 공통화
  - `callApi`, `callPublicApi`, `authFetch`, `toApiError`를 모듈 방식으로 사용
  - 인증 상태는 `AuthContext`로 관리

### 2-4. 빌드/배포 방식 (단일 서버)
- 이전:
  - Spring 템플릿 중심 서빙
- 현재:
  - Gradle이 프론트 빌드까지 포함
  1. `frontend npm ci`
  2. `frontend npm run build`
  3. `frontend/dist`를 `src/main/resources/static`으로 복사
  - 결과적으로 여전히 `localhost:8080` 단일 서버로 동작

---

## 3. 페이지별 변경 매핑 (기존 -> 현재)

| URL | 기존(Thymeleaf) | 현재(React) |
|---|---|---|
| `/` | `templates/index.html` | `pages/HomePage.jsx` |
| `/login` | `templates/login.html` | `pages/LoginPage.jsx` |
| `/signup` | `templates/signup.html` | `pages/SignupPage.jsx` |
| `/mypage` | `templates/mypage.html` | `pages/MyPage.jsx` |
| `/community` | `templates/community/list.html` | `pages/CommunityListPage.jsx` |
| `/community/detail` | `templates/community/detail.html` | `pages/CommunityDetailPage.jsx` |
| `/community/write` | `templates/community/write.html` | `pages/CommunityWritePage.jsx` |
| `/community/edit` | `templates/community/edit.html` | `pages/CommunityEditPage.jsx` |
| `/rooms` | `templates/stomp/rooms.html` | `pages/RoomListPage.jsx` |
| `/rooms/{roomId}` | `templates/stomp/room.html` | `pages/RoomPage.jsx` |

추가 호환:
- `/community/list`는 React에서 `/community`로 리다이렉트되도록 처리

---

## 4. 현재 시점 정리
- **사용자 입장:** 같은 기능을 같은 서버에서 사용
- **개발자 입장:** 화면 코드가 React로 통합되어 재사용/유지보수성이 높아짐
- 템플릿 파일(`src/main/resources/templates`)은 아직 남아 있지만, 현재 메인 렌더링 경로는 React SPA

---

## 5. 왜 이렇게 바꿨는가
- 페이지별 JS 분산/중복 감소
- 인증 상태, API 에러 처리, 라우팅을 한 구조로 통합
- 이후 기능 추가 시 템플릿+스크립트 분산보다 React 컴포넌트 기반이 변경 추적이 쉬움
