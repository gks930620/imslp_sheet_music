# CSR 프로젝트 구조 정리

> Spring Boot(REST API) + React(Vite) SPA 단일서버 + JWT + OAuth2 + WebSocket 채팅  
> `HomeController`가 페이지 URL을 `forward:/index.html`로 넘기면 React Router가 렌더링, 데이터는 `lib/http.js`에서 REST API 호출 (CSR 방식)
> ※ `templates/` Thymeleaf 파일은 이전 버전 잔재로 남아 있으나 현재 렌더링 경로는 React SPA. 화면 전환 상세는 `설계/기존대비_react전환_변경요약.md` 참고

---

## 아키텍처 원칙

### Layer Architecture
```
Controller → Service → Repository
```

| 계층 | 역할 | 규칙 |
|------|------|------|
| **Controller** | 요청/응답 처리 | Repository 직접 사용 ❌, Entity 사용 ❌ |
| **Service** | 비즈니스 로직 | Entity ↔ DTO 변환 담당 |
| **Repository** | 데이터 접근 | QueryDSL에서 DTO 직접 반환 가능 |

- Controller에서 Repository 호출 금지
- DB조회나 해당 domain에 관한 로직은 Service, 파일이나 날짜 등 공통기능은 Util로 이름짓기

### API 설계 규칙 (CSR)
- **1 API = 1 Controller 메소드 = 1 Service 메소드**
- 프론트엔드(JS)에서 진행 흐름을 받고 API 여러번 호출하는 방식
- API 여러번 호출이 부담될 때만 Facade 패턴 고려
- Service 메소드가 비슷하면 → private 공통 메소드로 추출
- SELECT만 하는 경우 → QueryDSL에서 DTO 직접 반환 권장

### SSR과의 차이
| 항목 | SSR | CSR (이 프로젝트) |
|------|-----|-----|
| 데이터 전달 | Controller → Model → Thymeleaf | JS → fetch API → JSON 응답 |
| 인증 | Session + `sec:authorize` | JWT (Cookie) + JS에서 `authFetch` |
| 로그인 정보 | `model.addAttribute("user", ...)` | `authFetch('/api/users/me')` |
| 페이지 렌더링 | 서버에서 HTML 완성 | 빈 HTML + JS에서 DOM 조작 |
| 로그인/비로그인 분기 | `sec:authorize="isAuthenticated()"` | JS에서 API 응답으로 판단 |

---

## 📁 패키지 구조

```
com.test.test/
├── DemoApplication.java              # 메인 애플리케이션
├── HomeController.java               # 페이지 라우팅 (빈 HTML 제공)
│
├── common/                           # 공통 모듈
│   ├── config/
│   │   ├── CorsConfig.java           # CORS 설정
│   │   ├── QuerydslConfig.java       # QueryDSL 설정
│   │   └── WebConfig.java            # 리소스 핸들러, Pageable 설정
│   ├── dto/
│   │   ├── ApiResponse.java          # 성공 응답 래퍼
│   │   ├── ErrorResponse.java        # 에러 응답 래퍼
│   │   └── PageResponse.java         # 페이징 응답 래퍼
│   └── exception/
│       ├── GlobalExceptionHandler.java  # 전역 예외 처리
│       ├── BusinessException.java       # 커스텀 예외 부모
│       ├── EntityNotFoundException.java # 404
│       ├── AccessDeniedException.java   # 403
│       ├── DuplicateResourceException.java # 409
│       ├── BusinessRuleException.java   # 400
│       └── RefreshTokenException.java   # 401 (Refresh 토큰 계약: TOKEN_DISCARDED/EXPIRED/REQUIRED)
│
├── jwt/                              # Security (JWT + OAuth2)
│   ├── JwtUtil.java                  # JWT 생성/검증 유틸
│   ├── config/
│   │   └── SecurityConfig.java       # Security 설정 (필터, 경로 권한)
│   ├── filter/
│   │   ├── JwtLoginFilter.java       # 로그인 요청 처리 (POST /api/login)
│   │   └── JwtAccessTokenCheckAndSaveUserInfoFilter.java  # 매 요청 토큰 검증
│   ├── handler/
│   │   ├── OAuth2LoginSuccessHandler.java   # OAuth2 로그인 성공 → JWT 발급
│   │   └── CustomLogoutSuccessHandler.java  # 로그아웃 처리
│   ├── controller/
│   │   ├── JoinController.java       # 회원가입 API
│   │   ├── UserController.java       # 내 정보 API (/api/users/me)
│   │   ├── RefreshController.java    # 토큰 재발급 API
│   │   ├── Oauth2LoginController.java # OAuth2 로그인 시작 (웹)
│   │   └── AppOAuth2Controller.java  # OAuth2 로그인 (앱 네이티브)
│   ├── service/
│   │   ├── CustomUserDetailsService.java  # 일반 로그인 시 DB 조회
│   │   ├── CustomOAuth2UserService.java   # OAuth2 로그인 시 DB 조회/저장
│   │   ├── JoinService.java          # 회원가입 로직
│   │   ├── RefreshService.java       # Refresh Token DB 관리 + rotate()(원자적 토큰 회전)
│   │   └── AppOAuth2Service.java     # 앱(네이티브) OAuth2 로그인 처리 (upsert + JWT 발급)
│   ├── model/
│   │   ├── CustomUserAccount.java    # UserDetails + OAuth2User 통합
│   │   ├── UserDTO.java              # 사용자 DTO
│   │   ├── JoinDTO.java              # 회원가입 요청 DTO
│   │   └── OAuthProvider.java        # OAuth2 제공자별 로직 (ENUM)
│   ├── entity/
│   │   ├── UserEntity.java           # 사용자 엔티티
│   │   └── RefreshEntity.java        # Refresh Token 엔티티
│   └── repository/
│       ├── UserRepository.java
│       ├── RefreshRepository.java
│       └── InMemoryAuthorizationRequestRepository.java  # OAuth2 state 저장
│
├── community/                        # 게시판
│   ├── CommunityController.java      # 게시글 CRUD API
│   ├── CommunityService.java         # 게시글 비즈니스 로직
│   ├── CommunityEntity.java          # 게시글 엔티티
│   ├── dto/
│   │   ├── CommunityDTO.java         # 게시글 응답 DTO
│   │   ├── CommunityCreateDTO.java   # 게시글 작성 요청 DTO
│   │   └── CommunityUpdateDTO.java   # 게시글 수정 요청 DTO
│   ├── repository/
│   │   ├── CommunityRepository.java
│   │   ├── CommunityRepositoryCustom.java   # QueryDSL 인터페이스
│   │   └── CommunityRepositoryImpl.java     # QueryDSL 구현
│   └── comment/                      # 댓글
│       ├── CommentController.java    # 댓글 CRUD API
│       ├── CommentService.java
│       ├── CommentEntity.java
│       ├── dto/
│       └── repository/
│
├── file/                             # 파일 업로드
│   ├── controller/
│   │   └── FileController.java       # 업로드/다운로드/이미지서빙 API
│   ├── service/
│   │   └── FileService.java
│   ├── entity/
│   │   ├── FileEntity.java
│   │   ├── RefType.java             # 참조 대상 enum (COMMUNITY, USER) — 엔티티 밖 최상위
│   │   └── Usage.java               # 용도 enum (THUMBNAIL, IMAGES, ATTACHMENT)
│   ├── strategy/                     # Strategy Pattern
│   │   ├── FileStorageStrategy.java  # 인터페이스
│   │   ├── LocalFileStorage.java     # 로컬 저장
│   │   └── SupabaseFileStorage.java  # 클라우드 저장
│   ├── dto/
│   ├── repository/
│   └── util/
│
└── stomp/                            # 실시간 채팅 (WebSocket)
    ├── config/
    │   ├── WebSocketConfig.java      # STOMP 엔드포인트/브로커 설정
    │   └── WebSocketEventListener.java  # 입장/퇴장 이벤트
    ├── interceptor/
    │   ├── HttpHandshakeInterceptor.java  # HTTP 핸드셰이크 시 쿠키→JWT 추출
    │   └── JwtChannelInterceptor.java     # STOMP CONNECT 시 JWT 인증
    ├── controller/
    │   ├── ChatController.java       # @MessageMapping 메시지 핸들러
    │   └── RoomController.java       # 채팅방 목록/상세 API
    ├── service/
    │   └── RoomService.java
    ├── entity/
    │   └── RoomEntity.java
    ├── model/
    │   ├── ChatMessage.java
    │   └── RoomDTO.java
    └── repository/
        └── RoomRepository.java
```

---

## Entity와 DTO

### 변환 규칙
```java
// DTO → Entity : DTO에서 toEntity()
Entity entity = dto.toEntity();

// Entity → DTO : DTO에서 from(entity)
DTO dto = DTO.from(entity);
```

- **Entity** — DB 매핑 + 비즈니스 편의 메소드 (상태 변경). 편의메소드에서 exception 발생시켜도 공통처리 됨
- **DTO** — 변환 메소드 + API 요청/응답. 기본적으로 공통DTO 쓰다가 새로운 DTO 필요하면 그때그때 만들기

### DTO 분리 (Security)

| DTO | 역할 | 사용 위치 |
|-----|------|-----------|
| `UserDTO` | 사용자 정보 전달 (id, username, nickname, email, roles) | CustomUserAccount 내부 |
| `JoinDTO` | 회원가입 요청 (username, password, nickname, email) | JoinController |
| `CustomUserAccount` | UserDetails + OAuth2User 통합 — **UserDTO를 composition** | Security 전반 |

---

## 📤 응답 표준화

### 성공 응답
```java
ResponseEntity<ApiResponse<T>>
// 사용: return ResponseEntity.ok(ApiResponse.success("조회 성공", data));
```
- 현재 구현은 성공 응답을 `ApiResponse` 형식으로 통일함.

### 페이징 응답
```java
PageResponse<T>  // Page<>의 필요한 필드만 추출
```

### 에러 응답
```java
ErrorResponse  // 모든 에러 상황에서 일관된 JSON 구조
// { success: false, message: "...", errorCode: "...", timestamp: "..." }
```

### HTTP 상태 코드
| 코드 | 사용 | 에외 클래스 |
|------|------|-------------|
| 400 | 비즈니스 규칙 위반 | `BusinessRuleException` |
| 401 | 인증 필요 / 토큰 만료 | SecurityConfig `authenticationEntryPoint` |
| 403 | 권한 없음 (작성자 아님) | `AccessDeniedException` |
| 404 | 리소스 없음 | `EntityNotFoundException` |
| 409 | 중복 리소스 | `DuplicateResourceException` |

---

## 🔐 Security — JWT + OAuth2 (쿠키 방식)

### 인증 구조
```
브라우저: JWT를 HttpOnly 쿠키에 저장 (JS에서 접근 불가 → XSS 안전)
앱:      JWT를 JSON 응답으로 받아 앱 내부 저장소에 저장
```

### JWT 구조
| 토큰 | 만료 | 저장 | 용도 |
|------|------|------|------|
| Access Token | 30분 | 쿠키 `access_token` | API 인증 |
| Refresh Token | 4시간 | 쿠키 `refresh_token` + DB | Access Token 재발급 |

### 로그인 정보 사용
```java
// Controller에서
@AuthenticationPrincipal CustomUserAccount userAccount
userAccount.getUsername();  // 사용자 ID
userAccount.getUserDTO();  // 전체 사용자 정보
```

### 일반 로그인 흐름
```
POST /api/login (JSON: {username, password})
  → JwtLoginFilter가 가로챔
  → AuthenticationManager → CustomUserDetailsService.loadUserByUsername()
  → BCrypt 비밀번호 비교
  → 성공: Access/Refresh Token 생성 → 쿠키 설정 (200 응답)
  → 실패: 401 응답 (JSON)
```

### OAuth2 로그인 흐름 (카카오/구글)
```
1. 브라우저: <a href="/custom-oauth2/login/web/kakao"> 클릭
2. Oauth2LoginController → state 생성 → 카카오 인증 URL로 리다이렉트
3. 카카오 로그인 완료 → /login/oauth2/code/kakao 콜백
4. Spring Security가 자동으로 CustomOAuth2UserService 호출
5. OAuth2LoginSuccessHandler → JWT 발급 → 쿠키 설정 → / 리다이렉트
```

### 매 요청 인증 (JwtAccessTokenCheckAndSaveUserInfoFilter)
```
매 HTTP 요청 → 쿠키/헤더에서 access_token 추출
  → null이면 비인증 상태로 통과
  → refresh 토큰이면 통과 (/api/tokens/refresh로 갈 것)
  → access 토큰 만료면 ERROR_CAUSE="토큰만료" → authenticationEntryPoint에서 401 응답
  → 유효하면 SecurityContext에 인증정보 저장 → 로그인 상태로 통과
```

### 토큰 재발급 (RefreshController)
```
POST /api/tokens/refresh (쿠키에서 refresh_token 자동 전송)
  → 존재 확인 (DB) → 만료 검증 → 기존 삭제 (Rotation) → 새 토큰 발급
```

### SecurityConfig 경로 규칙
```
permitAll()     → 페이지 URL, 정적 리소스, 공개 API, WebSocket
                  GET /api/communities, GET /api/communities/{id}, GET /api/communities/{id}/comments
                  GET /api/files, GET /api/files/paths, GET /api/files/{id}/content  (파일 조회/다운로드는 공개)
authenticated() → /api/logout, /api/users/me, /api/rooms, /api/rooms/**,
                  POST/PUT/DELETE /api/communities/**, /api/comments/**,
                  POST /api/files, DELETE /api/files/**  (파일 업로드/삭제만 인증 필요)
anyRequest()    → authenticated() (화이트리스트 방식: 공개 경로만 위에 명시하고 나머지는 전부 인증 필요)
                  ※ 상세/전환 체크리스트는 `설계/security_참고사항.md` 참고
```

### 보안 개선사항 (적용됨)
- `InMemoryAuthorizationRequestRepository`: `new Thread()` → `ScheduledExecutorService`
- `JwtUtil.getTokenType()`: `JwtException | IllegalArgumentException` catch 추가
- `RefreshService.rotate()`: 토큰 회전(load→검증→삭제→재발급)을 **하나의 트랜잭션**으로 원자화(동시 회전/부분 실패 방지). `RefreshController`는 토큰 추출·응답 분기만 담당(슬림). 401 계약은 `RefreshTokenException`으로 유지
- `CustomOAuth2UserService` / `OAuthProvider`: `{noop}oauth2user` → `PasswordEncoder + UUID`
- `JwtAccessTokenCheckAndSaveUserInfoFilter`: `"refresh".equals(tokenType)` NPE 방지
- 쿠키 `secure` 설정: `app.cookie.secure` 환경변수로 분기 (로컬 false, 운영 true)
  - `OAuth2LoginSuccessHandler`, `JwtLoginFilter`, `CustomLogoutSuccessHandler` 모두 적용

---

## 💬 실시간 채팅 (STOMP WebSocket)

### 구조
```
브라우저 ←→ SockJS(/ws-chat) ←→ STOMP 프로토콜 ←→ SimpleBroker
```

### 인증 흐름 (쿠키 기반 — HttpOnly 대응)
```
1. SockJS 연결 요청 → HTTP 핸드셰이크 (쿠키 자동 전송)
   → HttpHandshakeInterceptor: 쿠키에서 access_token 추출 → 검증 → 세션 속성에 username 저장

2. STOMP CONNECT 프레임 → JwtChannelInterceptor
   → ① Authorization 헤더 확인 (앱용)
   → ② 없으면 세션 속성의 username 확인 (브라우저 쿠키용)
   → 인증 성공 → user, roomId 세션에 저장
```

### 메시지 흐름
```
클라이언트 send → /pub/room/{roomId} → ChatController.sendMessage() → /sub/room/{roomId} → 구독자 전원
```

### 이벤트
- 입장 (SessionConnectEvent): "OOO님이 입장했습니다."
- 퇴장 (SessionDisconnectEvent): "OOO님이 퇴장했습니다." (브라우저 종료/뒤로가기 감지)

---

## 📁 파일 업로드

### 설계 원칙
- 파일 업로드는 **별도 API**로 분리 (게시판 API + 파일 API)
- 파일 공통 관리 → `RefType` Enum(COMMUNITY/USER)으로 참조 대상 구분, `Usage` Enum(THUMBNAIL/IMAGES/ATTACHMENT)으로 용도 구분
- 파일 삭제 API는 DB 레코드 + 물리 파일을 함께 삭제(저장 전략에 위임). 물리 삭제 실패는 로그만 남기고 진행
- 임시 업로드(refId=0 등) 정합성 정리는 별도 배치 정책으로 관리

### 환경별 저장 전략 (Strategy Pattern)
| 환경 | 저장소 | 설정 |
|------|--------|------|
| 개발 | 로컬 파일시스템 (`./uploads/`) | `supabase.enabled: false` |
| 운영(Railway) | 로컬 파일시스템 (컨테이너 `/app/uploads`) | `supabase.enabled: false` |

yml 설정에 따라 `LocalFileStorage` 또는 `SupabaseFileStorage` 빈이 주입됨.
현재 배포 방침은 **Supabase 미사용 → Local 저장**이다(코드에는 Supabase 전략도 남아있어 필요 시 `enabled: true`로 전환 가능).
⚠️ Railway 컨테이너 파일시스템은 재배포/재시작 시 초기화(ephemeral)되므로, 업로드 영속화가 필요하면 **Railway Volume 을 `/app/uploads` 에 마운트**한다.

---

## 🗄️ JPA 설계

### 연관관계 원칙
- **양방향 지양** → 프론트에서 API 분리 요청 (글 API + 댓글 API + 파일 API)
- 연관 데이터 조회:
  - 방법 1: 메인 쿼리 실행 후 ID 모아서 `IN` 쿼리로 한번에 처리
  - 방법 2: 조인 (JPQL, @EntityGraph 등)
  - 둘 다 Repository(QueryDSL)에서 처리. 연관관계가 너무 많을 때만 Service에서 처리 고려

### Repository 규칙
- 연관관계 필요 → `findByEntity()`
- 단순 조회 → `findByEntityId()` (ID만으로 조회)

### Paging
- `Pageable` → Controller 파라미터에서 직접 바인딩 (시작 0/1은 클라이언트가 처리)

---

## 🌐 프론트엔드 (React SPA, CSR 패턴)

> 현재 화면은 React SPA. (아래 표/코드는 실제 코드 기준. 이전 Thymeleaf 방식은 `templates/`에 잔재로만 남음)

### 페이지 서빙 방식
```
HomeController (@Controller)
  → @GetMapping("/community" 등 페이지 URL) → return "forward:/index.html"
  → React Router(App.jsx)가 실제 페이지 컴포넌트 렌더링
  → 컴포넌트가 lib/http.js로 fetch('/api/communities') → JSON → state 렌더링
```
- 서버 forward 경로 ↔ React 라우트 ↔ SecurityConfig permitAll 은 **항상 같이 관리** (`설계/security_참고사항.md`)

### 공통 API 유틸 (`frontend/src/lib/http.js`)

| 함수 | 역할 |
|------|------|
| `authFetch(url, options)` | 인증 fetch 래퍼. `credentials:'include'` + 401(TOKEN_EXPIRED) 시 자동 재발급·재시도(단일 지점) |
| `callApi(url, options)` | `authFetch` + JSON 파싱 + 에러(`toApiError`) 처리 (인증 API) |
| `callPublicApi(url, options)` | 공개 API 호출(재발급 불필요) |
| `toApiError(response)` | 서버 에러 응답을 표준 객체(`{status,message,errorCode,errors}`)로 변환 |
| `uploadFiles(files, refId, usage, refType?)` | `FormData` + `POST /api/files` 공통 업로드 헬퍼 |

- 로그인 상태 전역관리: `frontend/src/context/AuthContext.jsx` (`login`/`logout`/`refreshUser`)
- 날짜/용량/에러 메시지 포맷: `frontend/src/lib/format.js`

### 인증이 필요한 API 호출 패턴 (React)
```javascript
import { callApi } from "../lib/http.js";
// authFetch가 credentials:'include'를 자동 적용 → 쿠키 자동 전송, 401시 재발급·재시도
const result = await callApi('/api/communities', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ title: '...', content: '...' }),
});
```

### 화면 구조
```
frontend/src/App.jsx            # 라우트 정의 (<Layout> 하위 pages)
frontend/src/components/Layout.jsx  # 헤더/네비/푸터 셸 + <Outlet/>
frontend/src/pages/*.jsx        # 페이지 컴포넌트
```

---

## 🔧 환경 분리

| 환경 | 파일 | 특징 |
|------|------|------|
| 개발/테스트 | `application.yml` / test | ddl-auto: create, SQL 초기화, **인메모리 H2(MODE=MySQL)** — 외부 DB 불필요 |
| 운영(Railway) | `application-prod.yml` | ddl-auto: update, SQL 초기화 비활성화, **Railway MySQL**(`com.mysql:mysql-connector-j`, MySQLDialect), `server.port=${PORT}` |

### 환경 변수 (.env / Railway Variables)
```
# 공통(OAuth/JWT)
KAKAO_CLIENT_ID, KAKAO_CLIENT_SECRET
GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET
JWT_SECRET_KEY

# 운영(Railway)만
SPRING_DATASOURCE_URL   # jdbc:mysql://... (Railway MySQL)
SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD
APP_BASE_URL            # OAuth redirect + WebSocket 허용 출처
# 파일 저장은 로컬(컨테이너) 사용 → SUPABASE_* 불필요
```
> 로컬(dev)은 H2라서 DB 환경변수가 필요 없다. 자세한 목록은 프로젝트 루트 `.env.example` 참고.

---

## 📝 로그
- **logback-spring.xml** 사용
- 콘솔 + 파일(일별 롤링) + 에러 분리
- 30일 보관, 용량 제한

---

## ✅ 구현 현황

| 기능 | 상태 | 패키지/파일 |
|------|------|-------------|
| Layer Architecture | ✅ | Controller/Service/Repository 분리 |
| API 표준 응답 | ✅ | `ApiResponse`, `PageResponse`, `ErrorResponse` |
| 전역 예외 처리 | ✅ | `GlobalExceptionHandler` |
| JWT 인증 | ✅ | `jwt/` 패키지 |
| OAuth2 로그인 | ✅ | 카카오, 구글 (보안 개선 적용) |
| 파일 업로드 | ✅ | `file/` 패키지, Strategy Pattern |
| 게시판 (CRUD) | ✅ | `community/` 패키지 |
| 댓글 (CRUD) | ✅ | `community/comment/` 패키지 |
| 실시간 채팅 | ✅ | `stomp/` 패키지 (STOMP + SockJS + JWT 인증) |
| 로그 설정 | ✅ | `logback-spring.xml` |
| 환경 분리 | ✅ | `application.yml` / `application-prod.yml` |
| Docker | ✅ | `Dockerfile` |
| Swagger | ✅ | `/swagger-ui.html` |
| Actuator | ✅ | `/actuator/health` |
| CORS | ✅ | `CorsConfig.java` |
| QueryDSL | ✅ | 게시판 검색/페이징 |

---

## 🚀 추가 고려사항 (선택)

| 기능 | 설명 | 언제 필요? |
|------|------|-----------|
| **Redis** | 세션/캐시/토큰 블랙리스트, WebSocket 세션 공유 | 서버 다중화 시 |
| **채팅 메시지 DB 저장** | 현재 메시지는 실시간 전송만 (저장 안 됨) | 채팅 이력 필요 시 |
| **이메일 발송** | 비밀번호 찾기, 알림 | 사용자 알림 필요 시 |
| **스케줄러** | @Scheduled 배치 작업 | 고아 파일 정리, 통계 집계 |
| **캐싱** | @Cacheable | 자주 조회되는 데이터 |
| **Rate Limiting** | API 호출 제한 | 악용 방지 |
| **Access Token 블랙리스트** | JWT 즉시 무효화 (Redis) | 강제 로그아웃 필요 시 |

### 프론트엔드 분리 시 (React/Vue/Flutter 등)
- `templates/` 폴더, `HomeController.java` 삭제
- CORS 설정 확인 (`CorsConfig.java`에 프론트 도메인 추가)
- 앱은 `Authorization: Bearer {token}` 헤더 방식 사용
- WebSocket: STOMP CONNECT 시 `Authorization` 헤더로 JWT 전달

