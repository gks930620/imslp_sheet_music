# React 단일서버 개발원칙 (CSR 프로젝트 적용 규칙)

> 대상: Spring Boot + React 통합 배포(단일 서버)  
> 목적: 기존 jQuery/ajax/fetch 화면을 React로 바꿔도 기능/동작/보안 일관성을 유지

---

## 1. 아키텍처 원칙
- 백엔드: `Controller -> Service -> Repository`를 유지한다.
- 프론트: `Page -> Component -> lib/http` 흐름으로 API를 호출한다.
- 화면 로직(분기/조합)은 React에서 처리하고, 백엔드는 REST API 책임만 가진다.

## 2. URL/라우팅 원칙
- 브라우저 진입 URL은 Spring `HomeController`에서 `forward:/index.html`로 처리한다.
- React Router 경로와 Spring 포워딩 경로는 항상 같이 관리한다.
- 기존 사용자 북마크 URL(예: `/community/list`)은 가능한 유지한다.

## 3. API 계약 원칙
- 백엔드 응답 포맷(`ApiResponse`, `PageResponse`, `ErrorResponse`)을 변경하지 않는다.
- 프론트는 공통 유틸(`callApi`, `callPublicApi`, `authFetch`)만 사용한다.
- 페이지마다 `fetch`를 직접 새로 구현하지 않는다.

## 4. 인증/보안 원칙
- JWT는 HttpOnly 쿠키 기반을 유지한다. (로컬스토리지 저장 금지)
- 인증 필요한 요청은 반드시 `credentials: include`가 포함되어야 한다.
- 401 + `TOKEN_EXPIRED` 재시도 로직은 `http.js` 한 곳에서만 처리한다.
- 페이지 컴포넌트에서 토큰 재발급 로직을 중복 구현하지 않는다.

## 5. 화면 전환 원칙 (jQuery -> React)
- `innerHTML` 기반 직접 DOM 조작 대신 React state 기반 렌더링으로 전환한다.
- 이벤트 바인딩은 `addEventListener` 대신 JSX 이벤트 핸들러로 처리한다.
- 전역 함수(window.*)를 만들지 않는다.

## 6. 파일/에디터 이미지 원칙
- 첨부파일/본문이미지 업로드는 `FormData + /api/files` 정책을 유지한다.
- 게시글 삭제/수정 시 파일 정합성을 같이 고려한다.
- "임시(refId=0) 업로드 파일" 정리 정책(배치 또는 저장 시 재매핑)을 별도 관리한다.

## 7. 실시간 채팅 원칙
- STOMP 연결 전 `AuthContext`에서 인증 상태를 확인한다.
- 방 ID, 메시지 송수신 구조(`/pub`, `/sub`)는 백엔드 계약을 그대로 따른다.
- 연결/해제/오류 메시지 처리는 페이지에서 사용자에게 명확히 보여준다.

## 8. 상태관리 원칙
- 전역 상태는 최소화: 인증만 `AuthContext` 사용.
- 페이지 데이터(목록, 상세, 댓글, 폼)는 페이지 로컬 state로 관리.
- 같은 데이터를 여러 컴포넌트가 공유할 때만 context 확장 고려.

## 9. 예외처리 원칙
- API 실패는 `toApiError` 표준 객체로 처리한다.
- 서버 메시지를 우선 사용하고, 없으면 프론트 기본 메시지를 사용한다.
- `alert` 남발 대신 점진적으로 공통 에러 UI 컴포넌트로 수렴한다.

## 10. 빌드/배포 원칙 (단일 서버)
- 배포 산출물은 항상 Spring `static`에 포함되어야 한다.
- 기준 명령:
  1. `npm.cmd ci`
  2. `npm.cmd run build`
  3. `.\gradlew.bat processResources` 또는 `bootJar/bootRun`
- 운영 배포 전, "React 빌드 결과가 static에 반영되었는지"를 확인한다.

## 11. 테스트 원칙
- 백엔드: 기존 통합 테스트를 유지/확장한다.
- 프론트: 최소한 핵심 플로우 수동 점검 체크리스트를 유지한다.
  - 로그인/로그아웃
  - 게시글 CRUD
  - 댓글 CRUD
  - 파일 업로드/다운로드
  - 채팅방 목록/입장/메시지 송신

## 12. 변경 시 체크리스트
- React 라우트 추가/변경 시 Spring 포워딩 경로도 반영했는가?
- 인증 API 호출이 `callApi/authFetch`를 타는가?
- API 응답 포맷이 기존과 동일한가?
- 단일 서버 빌드(`processResources`) 후 화면이 정상 동작하는가?
