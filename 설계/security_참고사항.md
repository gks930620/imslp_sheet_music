# Security 참고사항 (SecurityConfig 화이트리스트 전환)

> 작성일: 2026-07-06
> 항목 6 결정("화이트리스트 방식이 맞다") 반영 기록 + 운영/유지보수 주의점.

---

## 1. 무엇이 바뀌었나

`SecurityConfig`의 마지막 규칙이 **기본 개방 → 기본 차단**으로 바뀌었습니다.

```java
// 변경 전 (블랙리스트: 나열 안 하면 공개)
.anyRequest().permitAll();

// 변경 후 (화이트리스트: 나열한 것만 공개, 나머지 인증 필요)
.anyRequest().authenticated();
```

### 핵심 개념 (오해 정정)
- "로그인 필요한 곳을 하나씩 추가"하는 방식(블랙리스트)이 아니라,
- **"공개할 곳만 명시하고, 나머지는 전부 인증 필요"** 로 뒤집은 것(화이트리스트).
- 장점: 새 엔드포인트를 깜빡해도 **자동으로 보호**됨(실수로 열리지 않음). 이게 보안상 안전한 기본값.
- 비용: **공개해야 할 경로를 빠뜨리면 즉시 깨짐**(화면 안 뜸/로그인 불가). 그래서 아래 목록 관리가 중요.

---

## 2. 현재 공개(permitAll)로 열어둔 경로

| 분류 | 경로 | 이유 |
|------|------|------|
| 정적 리소스 | `/`, `/index.html`, `/assets/**`, `/css/**`, `/js/**`, `/images/**`, `/favicon.ico`, `/favicon.svg`, `/icons.svg`, `/uploads/**` | React(Vite) 번들·아이콘·업로드 파일. **로그아웃 상태에서도 화면이 떠야 함** |
| 에러 | `/error` | 스프링 에러 디스패치 경로(막으면 에러 응답이 꼬임) |
| 문서/모니터링 | `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/swagger-resources/**`, `/actuator/**`, `/h2-console/**` | 개발 편의·헬스체크 |
| SPA 페이지 라우트 | `/login`, `/signup`, `/mypage`, `/community/**`, `/rooms`, `/rooms/**` | `HomeController`가 `index.html`로 forward하는 페이지 URL (실제 데이터 접근은 API에서 인증) |
| WebSocket | `/ws-chat`, `/ws-chat/**` | SockJS 핸드셰이크(인증은 STOMP CONNECT에서) |
| OAuth2 | `/custom-oauth2/login/**`, `/oauth2/**`, `/login/oauth2/**` | 커스텀 시작점 + **스프링 표준 인가/콜백**(막으면 소셜 로그인 불가) |
| 공개 API | `POST /api/login`, `POST /api/users`(회원가입), `POST /api/tokens/refresh`, `/api/oauth2/**`(앱 OAuth) | 로그인 전에 호출돼야 함 |
| 공개 조회 API | `GET /api/communities`, `/api/communities/*`, `/api/communities/*/comments`, `/api/files`, `/api/files/paths`, `/api/files/*/content` | 비로그인도 열람 가능한 조회 |

그 외 모든 요청 → **인증 필요**. (게시글/댓글/파일 쓰기·삭제, `/api/users/me`, `/api/rooms/**` 등)

---

## 3. ⚠️ 신규 개발 시 필수 체크리스트

새 기능을 추가할 때 **"공개여야 하는데 인증 걸려서 안 되는" 실수**가 가장 흔합니다.

- [ ] **새 공개 API**를 만들었나? → `SecurityConfig`의 permitAll 목록(메서드+경로)에 **명시적으로 추가**.
- [ ] **새 SPA 페이지 라우트**(비로그인 접근 가능)를 추가했나? → permitAll에 경로 추가 **+ `HomeController` forward에도 추가**.
- [ ] 새 **정적 경로**(폰트/이미지 디렉토리 등)를 쓰나? → permitAll에 추가.
- [ ] 로그인 안 한 상태로 **화면 진입 → 정적 자산 200인지** 확인(로그아웃 후 새로고침).
- [ ] **OAuth 로그인**(카카오/구글) 실제로 되는지 확인 — `/login/oauth2/**` 콜백이 막히면 실패함.

> 반대로, 인증이 필요한 API는 **아무것도 안 해도 자동 보호**됨(화이트리스트의 이점). 별도 등록 불필요.

---

## 4. 배포 전 수동 스모크 테스트 (권장)

자동화 테스트가 커버 못 하는 부분(정적 서빙, OAuth 웹 흐름)이 있어 **1회 수동 확인** 권장:

1. 로그아웃 상태에서 `/` 접속 → 화면(CSS/JS) 정상 로드.
2. 게시글 목록/상세(비로그인) 열람 → 200.
3. 로그인 → `/api/users/me` 200, 헤더 로그인 상태 전환.
4. 카카오/구글 OAuth 로그인 → 콜백 후 정상 로그인.
5. 게시글 작성/삭제(로그인) → 정상, **비로그인 시 401**.
6. 채팅방 입장/메시지 송수신.

---

## 5. 관련 파일
- `src/main/java/com/test/test/jwt/config/SecurityConfig.java` — 경로 권한 규칙(이 문서의 근거)
- `src/main/java/com/test/test/HomeController.java` — SPA forward 경로(permitAll 목록과 동기화 필요)
- `frontend/src/App.jsx` — React 라우트(위 둘과 3자 동기화)

> 세 곳(SecurityConfig permitAll ↔ HomeController forward ↔ App.jsx route)은 **항상 같이 관리**.

---

## 6. 인증(Authentication) vs 인가(Authorization) — 반드시 구분

> 추가일: 2026-07-07 (검수 반영). SecurityConfig는 **"로그인 했는가(인증)"** 만 판정한다.
> **"로그인한 그 사람이 이 리소스를 건드릴 권한이 있는가(인가)"** 는 SecurityConfig가 막아주지 않으므로
> 서비스 계층에서 **작성자/소유자 검증**을 반드시 해야 한다. (이 부분을 빠뜨리면 IDOR 취약점이 된다.)

### 6-1. 원칙: 남의 리소스 수정/삭제 차단 (소유권 검증)
- 리소스를 수정/삭제하는 API는 `authenticated()` 만으로 충분하지 않다.
- 서비스에서 대상 리소스를 조회한 뒤 **작성자 == 요청자** 를 확인하고, 불일치 시 `AccessDeniedException`(403).
- 참고 구현: `CommunityService.updateCommunity/deleteCommunity` → `CommunityEntity.isWrittenBy(username)`.
- **파일 API도 동일**: 파일은 FK 없이 `refId + refType` 로 느슨하게 연결되므로, 업로드/삭제 시
  대상 리소스(예: `refType=COMMUNITY` → 해당 게시글)의 소유권을 되짚어 검증한다.
  참고 구현: `FileService.verifyOwnership()` (업로드·삭제 공통). `refId=0`(임시 업로드)은 미연결로 간주해 통과.

### 6-2. 원칙: 앱(네이티브) OAuth는 provider 토큰을 **서버가 검증**
- 앱 OAuth 로그인 엔드포인트(`POST /api/oauth2/providers/{provider}/tokens`)는 permitAll(인증 전 호출)이다.
- 따라서 **클라이언트가 보낸 사용자 id를 절대 신뢰하지 않는다.** 앱은 provider가 발급한 access token을 보내고,
  서버가 provider API(google userinfo / kakao user/me)로 검증하여 식별자를 **직접** 획득한 뒤에만 JWT를 발급한다.
  (검증 없이 id를 신뢰하면 누구나 임의 계정으로 로그인 가능 = 인증 우회)
- 참고 구현: `ProviderTokenVerifier` / `HttpProviderTokenVerifier`, `AppOAuth2Service.login`.

### 6-3. 신규 개발 체크리스트 (인가)
- [ ] 이 API가 **특정 사용자 소유 리소스**를 수정/삭제하나? → 서비스에서 소유권 검증(불일치 403) 추가.
- [ ] 리소스가 **id로 직접 지정**되나(`/{id}`, `refId` 등)? → 다른 사용자 id를 넣어도 막히는지 확인(IDOR).
- [ ] 통합 테스트에 **"다른 사용자가 남의 리소스에 접근 → 403"** 음성 케이스를 포함했나?
  (참고: `FileApiIntegrationTest.other_user_cannot_upload_or_delete_files_on_someones_post`)
- [ ] 인증 전 호출되는 엔드포인트가 **외부에서 받은 신원 정보**를 신뢰하나? → 서버검증으로 대체.
