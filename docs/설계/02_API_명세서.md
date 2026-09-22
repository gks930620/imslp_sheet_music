# 02. API 명세서 — 쉬운악보 1차 (피아노 독주)

> 작성일: 2026-09-06 / 작성: senior-dev
> 기준: `docs/기획/01_MVP_피아노_기획서.md` §2·§3·§5·§9·§9-1, 화면 정의서 `docs/화면정의/00~07`(화면이 요구하는 필드 = 응답 DTO), `01_ERD.md`(상태값·계산 규칙), 기존 코드(`ApiResponse`/`PageResponse`/`ErrorResponse`, `GlobalExceptionHandler`, `SecurityConfig` 화이트리스트)
> 읽는 사람: backend-dev / frontend-dev / qa. **이 문서가 세 스택이 공유하는 단일 계약**이다. 다음 단계의 Red 테스트는 이 문서를 코드로 옮긴 것이며, 테스트 통과 = 명세 준수다.
> 계약 변경은 senior-dev 만 한다(개발자가 필드를 임의로 추가·변경하지 않는다).

---

## 0. 공통 규칙

### 0-1. 응답 래퍼 (기존 그대로)

성공: `ApiResponse<T>`
```json
{ "success": true, "message": "성공", "data": { } }
```
목록(페이지): `data` 가 `PageResponse<T>`
```json
{ "content": [ ], "page": 0, "size": 20, "totalElements": 21, "totalPages": 2, "first": true, "last": false }
```
실패: `ErrorResponse` (필드 없는 값은 생략)
```json
{ "success": false, "message": "사람이 읽을 문구", "errorCode": "NOT_FOUND", "timestamp": "2026-09-06T05:02:00.123", "errors": [ { "field": "copyrightNote", "message": "판정 근거를 적어 주세요", "rejectedValue": "" } ] }
```
파일 응답(PDF)만 래퍼 없이 바이트를 그대로 준다.

### 0-2. 상태코드 · errorCode

| 상황 | 상태 | errorCode | 발생 예외(백엔드) |
|---|---|---|---|
| 없음(곡·작곡가·판본·작업·**숨김 곡**) | 404 | `NOT_FOUND` | `EntityNotFoundException` |
| 인증 필요(비로그인이 관리 API) | 401 | `NOT_AUTHENTICATED` / `TOKEN_EXPIRED` / `INVALID_TOKEN` | Security 진입점(기존) |
| 권한 부족(USER 가 관리 API) | 403 | `ACCESS_DENIED` | **신규 `AccessDeniedHandler`** 가 진입점과 같은 JSON 을 쓴다(현재는 빈 403 → 기본 `/error`) |
| 입력 검증 실패(`@Valid`) | 400 | `VALIDATION_ERROR` + `errors[]` | `MethodArgumentNotValidException` |
| 비즈니스 규칙 위반 | 400 | `BUSINESS_RULE_VIOLATION` | `BusinessRuleException` |
| 중복(작곡가 원어 표기, 곡 IMSLP 주소, 같은 곡 별칭, 진행 중 수집 있음) | 409 | `DUPLICATE_RESOURCE` | `DuplicateResourceException` |
| 저작권 때문에 다운로드 불가 | 403 | `COPYRIGHT_RESTRICTED` | **신규 `CopyrightRestrictedException`**(BusinessException, 403) |
| 파일 메타는 있는데 바이트가 없음 | 503 | `FILE_UNAVAILABLE` | **신규 `FileUnavailableException`**(BusinessException, 503) |
| 업로드 상한 초과 | 413 | `PAYLOAD_TOO_LARGE` | **신규 핸들러** `MaxUploadSizeExceededException` → 413 (현재는 500 으로 뭉개짐) |
| 파라미터 누락/타입 | 400 | `MISSING_PARAMETER` / `TYPE_MISMATCH` | 기존 |
| **multipart 필수 파트 누락**(`file` 없이 §5-1 호출) | 400 | `MISSING_PARAMETER` | `MissingServletRequestPartException` — **핸들러 추가 필요**(현재 500). 2026-09-07 추가 |
| **multipart 요청이 아님**(Content-Type 없이 / `application/json` 으로 §5-1 호출) | 400 | `MISSING_PARAMETER` | `MultipartException` — **핸들러 추가 필요**(현재 500). 2026-09-07 추가 |
| **지원하지 않는 HTTP 메서드**(`DELETE /api/works/21`, `PUT /api/composers/4`, `PATCH /api/rooms/1` …) — **인가를 통과한 요청에 한한다**(아래 참조) | 405 | `METHOD_NOT_ALLOWED` | `HttpRequestMethodNotSupportedException` — **핸들러 추가 필요**(현재 500). 응답에 **`Allow` 헤더**를 함께 준다. 2026-09-07 추가 |
| **본문 Content-Type 이 맞지 않음**(`text/plain` 으로 JSON API 호출) | 415 | `UNSUPPORTED_MEDIA_TYPE` | `HttpMediaTypeNotSupportedException` — **핸들러 추가 필요**(현재 500). 2026-09-07 추가 |

**프레임워크 표준 4xx 를 500 으로 뭉개지 않는다**(코드 컨벤션 §2). 위 세 줄은 전부 "클라이언트가 잘못 부른 요청" 인데,
`@ExceptionHandler(Exception.class)` 라는 최후의 보루가 Spring MVC 표준 예외까지 먼저 삼켜 500 이 되고 있다(qa 2차 결함).
500 은 "서버가 깨졌다" 는 뜻이라 모니터링·로그의 신호를 흐리고, 호출자에게 "내 요청이 잘못됐다" 는 정보를 주지 못한다.

- **405 에 `Allow` 헤더를 붙이는 이유**: RFC 9110 이 405 응답에 `Allow` 를 요구한다. 예외가 `getSupportedMethods()` 로 이미
  값을 들고 있어 헤더 한 줄이면 되고, 없으면 405 는 "안 된다" 만 말하고 "무엇이 되는지" 는 말하지 않는다.
- **405 는 인가를 통과한 뒤의 계약이다 — 비로그인은 401 이다 (2026-09-08 판정, senior-dev — qa 3차 결함 7).**
  위 표의 예시 `DELETE /api/works/21` 이 하필 공개 리소스라 §0-3 과 부딪혔다: 비로그인이면 **401**(`Allow` 없음)이고,
  ADMIN 토큰을 붙여야 405 + `Allow: GET` 이 된다. **§0-3 이 우선한다.**
  - 405 를 만들려면 `HandlerMapping` 까지 가야 하는데 그건 시큐리티 필터**보다 뒤**다. 비로그인에게 405 를 주려면
    공개 경로를 **메서드 무관 permitAll** 로 열어야 하고(지금은 `GET` 만 permitAll), 그러면 나중에 `/api/works/**` 아래에
    쓰기 매핑이 하나라도 생기는 순간 그게 공개된다. **실재하는 안전장치를 상태코드 하나와 바꾸지 않는다.**
  - §0-3 은 이미 "인증 전에는 어떤 API 가 있는지 알려주지 않는다" 를 계약으로 정했다. **메서드도 API 모양의 일부**다.
  - 호출자 손해가 없다: 둘 다 4xx 이고, 401 을 받아 로그인해도 그 요청은 여전히 405 다.
  - 계약 검증: `HttpStandardErrorContractIntegrationTest#anonymous_write_method_on_public_path_returns_401_not_405`.
- **multipart 아님이 415 가 아니라 400 인 이유**: `MultipartException` 은 (a) multipart 요청이 아님, (b) multipart 본문 파싱 실패
  두 가지를 함께 나타낸다. (b) 는 Content-Type 이 맞는데 본문이 깨진 경우라 415 가 틀린 답이 된다. 예외 메시지 문자열로 둘을
  가르는 것(`"Current request is not a multipart request"` 매칭)은 프레임워크 문구에 계약을 매다는 짓이라 쓰지 않는다.
  §5-1 은 필수 입력이 `file` 하나뿐이라, 호출자가 보는 사실은 두 경우 모두 **"파일이 서버에 오지 않았다"** 로 같다 —
  바로 윗줄(`MissingServletRequestPartException`)과 **같은 400 `MISSING_PARAMETER` · 같은 문구**를 쓰는 편이 프론트 분기도 하나로 유지된다.
  415 는 **Spring 이 실제로 미디어 타입 협상을 한 경우**(`HttpMediaTypeNotSupportedException`)에만 쓴다.
- **주의**: `MaxUploadSizeExceededException` 은 `MultipartException` 의 하위 타입이다. Spring 이 더 구체적인 핸들러를 먼저 고르므로
  413(업로드 상한) 은 그대로 남지만, 새 핸들러를 추가할 때 **기존 413 테스트가 계속 초록인지** 확인할 것.
  `MissingServletRequestPartException` 은 `MultipartException` 의 하위 타입이 **아니다**(`ServletException` 계열) — 두 핸들러가 모두 필요하다.

**클라이언트가 먼저 끊은 요청에는 응답이 없다 — 상태코드도 본문도 만들지 않는다** (2026-09-09 확정, 03 §20 · qa 6차 결함).
다운로드·미리보기 도중 사용자가 취소하거나 화면을 떠나면 서버는 `AsyncRequestNotUsableException`/`ClientAbortException`
(원인: `Connection reset by peer`·`Broken pipe`)을 받는다. 이때는 **에러 JSON 을 쓰지 않고 DEBUG 한 줄만 남긴다.**

- **계약 영향 없음** — 받을 상대가 이미 사라진 뒤라 호출자가 관찰할 수 있는 응답이 없다. 프론트·앱이 분기할 것도 없다.
  (`fetch` 를 abort 한 쪽은 원래 자기 `AbortError` 를 본다.)
- **바이트 엔드포인트의 성질**: `GET /api/editions/{id}/download`·`GET /uploads/{저장파일명}` 은 본문 전송이 시작되면
  응답 Content-Type 이 이미 `application/pdf`·`image/png` 다. **전송 중에 생긴 실패는 JSON 에러로 바뀌지 않는다** —
  실패는 "끊긴 다운로드"로만 나타난다. 그래서 §3-4 의 404/403/503 은 **전송이 시작되기 전**(판본·저작권·파일 확인 단계)의 계약이다.
- 화면이 다운로드 가능 여부를 미리 알고 싶으면 지금처럼 같은 주소로 `HEAD` 를 먼저 보낸다(03 §12).

**401 의 세 errorCode 는 뜻이 다르다(프론트 동작이 갈린다).** 2026-09-07 명확화:

| 상황 | errorCode | 프론트 동작 |
|---|---|---|
| 토큰 없음 | `NOT_AUTHENTICATED` | 로그인 화면 |
| 서명은 유효한데 유효기간 지남 | `TOKEN_EXPIRED` | `/api/tokens/refresh` 로 **재발급 시도**(`lib/http.js`) |
| 서명 불일치(위조)·JWT 형식 아님·해독 불가 | `INVALID_TOKEN` | 재발급 시도 없이 로그인 화면 |

위조 토큰에 `TOKEN_EXPIRED` 를 주면 프론트가 무의미한 재발급을 한 번 더 친다. 만료 여부는 **서명 검증을 통과한 뒤에만** 판단한다.

**"찾을 수 없음" 메시지 문구(2026-09-07 확정).** `NOT_FOUND` 의 `message` 는 화면에 그대로 보인다. 지금 문구는 `"작곡가을(를) 찾을 수 없습니다: 999"` 로, 괄호 조사와 내부 id 가 그대로 노출된다.

- 형식: **`"{리소스}{을|를} 찾을 수 없어요"`** — id 를 붙이지 않는다(사용자에게 의미 없고 내부 식별자를 흘린다. 디버깅은 로그에 남긴다).
- 조사는 **받침 계산**으로 고른다: 마지막 글자가 한글이면 `(코드 - 0xAC00) % 28 != 0` 일 때 `을`, 아니면 `를`. → `곡을 / 판본을 / 게시글을 / 파일을 / 수집 작업을`, `작곡가를 / 사용자를`. (`EntityNotFoundException.of` 안의 작은 정적 헬퍼 하나면 된다. 리소스명이 늘어도 문구가 깨지지 않는다.)
- 예외: 이미 문장을 통째로 주는 곳(`"파일이 없는 판본이에요"`)은 그대로 둔다.
- **리소스명은 사용자가 읽는 한국어 낱말**이어야 한다 — 조사 계산이 맞아도 `"Comment를 찾을 수 없어요"` 면 문구가 깨진 것이다.
  같은 규칙이 `AccessDeniedException.forUpdate/forDelete` 의 `"본인의 {리소스}만 수정할 수 있습니다."` 에도 적용된다.
  2026-09-07 기준 남은 곳: `CommentService` 의 `"Comment"`(3곳)·`"Community"`·`"User"` → `"댓글"`·`"게시글"`·`"사용자"`.

### 0-3. 인증·인가

- 인증 방식은 기존과 동일(쿠키 `access_token` 또는 `Authorization: Bearer`).
- **공개 API**(`/api/works/**`, `/api/composers/**`, `/api/editions/*/download`)는 `permitAll`.
- **관리 API** 는 전부 `/api/admin/**` 아래에 두고 `SecurityConfig` 에 `.requestMatchers("/api/admin/**").hasAuthority("ADMIN")` 한 줄로 막는다(역할 문자열이 `ROLE_` 접두 없이 저장되므로 `hasRole` 이 아니라 `hasAuthority`). 이후 `anyRequest().authenticated()` 유지.
- 관리자 판별(프론트): `GET /api/users/me` 의 `data.roles` 에 `"ADMIN"` 포함 여부(기존 응답 그대로).
- **존재하지 않는 `/api/…` 경로는 비로그인에게 404 가 아니라 401 이다**(`anyRequest().authenticated()`). 의도한 동작이다 — 인증 전에는 어떤 API 가 있는지 알려주지 않는다. 계약의 "없으면 404"(§0-2)는 **존재하는 API 의 리소스**에 대한 규칙이다. (2026-09-07 판정, 근거는 03 §14-1)
- **같은 이유로, 공개 경로에 GET 이외 메서드를 보낸 비로그인도 401 이다**(405 아님). 공개 API 는 `GET`(+ §3-4 의 `HEAD`)만 permitAll 이고, 그 밖의 메서드는 `anyRequest().authenticated()` 로 떨어진다. **메서드도 API 모양의 일부**이므로 인증 전에는 알려주지 않는다. 405 는 인가를 통과한 뒤의 계약이다(§0-2). (2026-09-08 판정)

### 0-4. 표기 규칙

- 필드명 camelCase. 날짜·시각은 **ISO-8601 UTC 문자열**(`"2026-09-06T05:02:00Z"`) — 새 엔티티는 `Instant`. 프론트는 `new Date(v)` 로 지역 시각 표시.
- enum 은 대문자 문자열(`"INTERMEDIATE"`). 화면 문구 변환(중급 등)은 프론트 책임.
- 없는 값은 `null` 로 내려준다(키 생략 안 함). 빈 목록은 `[]`.
- 페이지: `page`(0-base, 기본 0), `size`(기본 20, 최대 100). 화면은 `size` 를 보내지 않는다(20 고정).
- **범위 밖 페이지는 200 + 빈 `content` — 목록 API 전부 공통** (2026-09-21 명시, senior-dev — qa 8차 결함 2). `page` 에 어떤 정수가 와도(`2147483647` 까지) 500 이 되지 않는다. `page` 는 **요청한 값을 그대로 되비추고**(서버가 몰래 다른 페이지로 옮기면 화면의 페이지 이동이 "눌러도 그 자리" 가 된다), `totalElements` 는 그 페이지와 무관한 실제 수다. 페이지 번호는 **주소창에 그대로 드러나는 값**이라 사용자가 손으로 고치고 링크로 공유한다 — 목록마다 답이 갈리면(어떤 목록은 200, 어떤 목록은 500) 그건 계약이 아니다. 검증: `WorkSearchApiIntegrationTest`(§3-1), `MyLibraryPageBoundsIntegrationTest`(§10-2·§10-3).
- 파일 크기 바이트(`fileSize`), 쪽수 정수(`pageCount`). "2.4MB"/"12쪽" 포맷은 프론트.
- 미리보기 이미지 URL 은 `previewUrl` = `/uploads/{저장파일명}.png` — **기존 `FileServingController` 프록시** 가 서빙(permitAll, 로컬/버킷 공통). 별도 이미지 API 를 두지 않는다(03 §2).

**`GET /uploads/{저장파일명}` 는 미리보기 PNG 전용이다 (2026-09-08 확정, senior-dev — qa 3차 결함 1).**

| 파일 | `/uploads` |
|---|---|
| 판본 미리보기 PNG — 그 판본이 `koreaCopyright = FREE` **이고** 그 곡이 `hidden = false` | 200 |
| 판본 미리보기 PNG — 그 판본이 `RESTRICTED`/`UNKNOWN` | **404** (단 **ADMIN 인증이면 200**) |
| 판본 미리보기 PNG — **그 곡이 `hidden = true`** (판정이 `FREE` 여도) | **404** (단 **ADMIN 인증이면 200**) |
| 커뮤니티·사용자 파일 (`ref_type=COMMUNITY/USER`, 이미지·첨부) | 200 |
| **판본 PDF** (`ref_type=EDITION` 의 PDF) | **404** — 판정이 `FREE` 여도 |
| `files` 행이 없는 저장 파일명(고아 바이트·없는 이름) | **404** |

**판정이 안 끝난 판본은 미리보기도 감춘다 (2026-09-08 확정, senior-dev — qa 4차 결함 2).**
표의 첫 두 줄이 그것이다. 원래 표는 미리보기를 무조건 200 으로 적었고 기획 §F3-6 · §5 예외표는 감추라고 했다 —
계약이 없어서 코드는 표를 따랐다(실측: UNKNOWN 판본 10건이 공개 응답에 `previewUrl` 을 싣고 직접 GET 도 200).

- **응답과 서빙을 둘 다 막는다.** 응답(`previewUrl = null`, §2-2 · §2-3)만 막으면 **저장 파일명을 아는 사람에게는 그대로 열려 있다.**
  이름은 없어지지 않는다 — FREE 였다가 판정이 뒤집힌 판본의 옛 응답·브라우저 기록, 서버의 `uploads/` 폴더(qa 3차가 실제로 쓴 수단).
  "우리 응답에 안 실렸으니 닫힌 것"이라는 판단이 정확히 `/images` 사고였다. 재배포 책임(기획 `02_저작권_판정_지침` A-4)은
  **우리 도메인이 그 바이트를 주느냐**로 정해지지, 우리 JSON 이 주소를 알려줬느냐로 정해지지 않는다.
  반대로 서빙만 막으면 화면에 깨진 이미지가 뜨고 화면이 문구를 고를 수 없다 — 하나로는 둘 다 못 한다.
- **ADMIN 은 통과한다.** 판정 근거가 미리보기 그 자체다(기획 §F6-4 (B)3). 판정 안 된 판본의 미리보기를 관리자에게 감추면
  판정 자체가 불가능하다. 관리 웹은 쿠키 인증(`credentials: "include"`)이라 `<img src="/uploads/…">` 에도 권한이 실린다.
- 계약 검증: `EditionPreviewExposureIntegrationTest`.

**숨긴 곡의 미리보기도 감춘다 (2026-09-09 확정, senior-dev — qa 5차 결함 3).**
게이트 조건은 `koreaCopyright == FREE` 하나였고 `work.hidden` 을 보지 않았다. 그 곡의 **곡 상세(§3-3)도 다운로드(§3-4)도 404** 인데
미리보기 PNG 만 200 이었다 — 실측으로 확인된 상태다.

- **조건은 AND 다**: `koreaCopyright == FREE` **그리고** `work.hidden == false`. 하나라도 걸리면 404.
- 숨김의 정의가 "사용자 화면 **어디에도** 안 나온다"(기획 §F2-6)이다. 곡 상세·검색·인기곡·다운로드가 전부 닫혀 있는데
  바이트 하나만 열려 있으면 그건 숨긴 것이 아니다. 특히 수집이 **"피아노 독주곡이 아닌 것 같아요"** 로 자동 숨김한 곡
  (`hidden_reason = NOT_PIANO_SOLO`, §6-11)의 1쪽 이미지가 공개로 남는데, 그 곡은 **우리가 아직 무엇인지 판단하지 못한 곡**이다.
  판단이 끝나지 않은 것을 공개로 두지 않는다는 점에서 바로 위 "판정이 안 끝난 판본은 미리보기도 감춘다" 와 같은 결정이다.
- **여기서는 응답을 고칠 것이 없다** — 숨긴 곡은 애초에 공개 응답이 없다(§3-3 404, 검색 결과에서 제외). 그래서 이번 변경은
  **서빙 게이트 한 곳**이다. 뒤집어 말하면 "응답에 안 실리니 닫힌 것" 이라는 판단이 통하지 않는 가장 순수한 사례다:
  이 곡은 **응답이 존재한 적도 없는데** 바이트는 열려 있었다(숨기기 전에 공개였던 곡의 주소는 이미 밖에 나가 있다).
- **ADMIN 은 통과한다.** 숨김을 풀지 말지를 판단하는 근거가 그 1쪽 이미지다(정말 피아노 독주가 아닌가). 판정 근거와 같은 이유다.
- 계약 검증: `HiddenWorkPreviewGateIntegrationTest`.

- 이 프록시는 저작권 게이트를 태우지 않고 다운로드 수도 세지 않는다. 그래서 판본 바이트는 **§3-4 한 문으로만** 나간다.
  (qa 3차 실측: 비로그인·헤더 없이 `uploads/` PDF 80개·515MB 전량이 열려 있었고, 같은 판본의 §3-4 는 403 이었다.)
- **행이 없는 바이트도 안 준다** — 모든 저장 경로가 `files` 행을 함께 쓰므로, 행이 없는 파일은 우리가 책임지는 파일이 아니다(삭제 뒤 남은 고아 바이트 등).
- **404 는 본문 없이** 준다. `/uploads` 는 `<img src>`·PDF 뷰어가 직접 무는 바이트 엔드포인트라 §0-1 의 JSON 래퍼를 쓰지 않는다(바이트가 없을 때의 404 와 같은 모양이어야 호출자가 분기하지 않는다).
- 계약 검증: `UploadsServingGateIntegrationTest`, 바이트 동일성 회귀는 `FileServingContractIntegrationTest`.

### 0-5. SPA 라우트 등록 방식 (확정)

- `HomeController` 는 경로 열거를 버리고 **catch-all** 로 바꾼다: `GET /` 및 `GET /{첫세그먼트:^(?!api|uploads|assets|images|ws-chat|h2-console|swagger-ui|v3|actuator|oauth2|custom-oauth2|healthz|error)[^.]*}/**` → `forward:/index.html`. (점이 든 세그먼트 = 정적 파일은 제외. `/login/oauth2/**` 는 시큐리티 필터가 먼저 처리.) 새 화면을 추가할 때 HomeController 수정 불필요.
- `SecurityConfig` 에는 화면 셸 GET 을 permitAll 로 명시 추가: `"/search", "/works/**", "/composers/**", "/admin", "/admin/**"`. (`/admin/**` 은 HTML 셸만 공개, 데이터는 `/api/admin/**` 이 막는다.) 기존 `/login, /signup, /mypage, /community/**, /rooms/**` 항목은 그대로.
- 확정 라우트 표는 `docs/화면정의/00_공통_레이아웃_토큰.md` §8 에 반영했다.

**2026-09-10 — 악기 구분이 붙은 뒤의 화면 라우트 (확정, 근거 `03_기술결정.md` §21).** 백엔드는 **한 줄도 바뀌지 않는다** — catch-all 과 `SpaShellRequestMatcher` 가 이미 임의의 첫 세그먼트를 셸로 넘긴다. 회귀 가드: `SectionRouteShellIntegrationTest`.

| 화면 | 라우트 | 비고 |
|---|---|---|
| 피아노 홈 | `/piano` | |
| 검색 결과 | `/piano/search?q=&in=&level=&pages=&downloadable=&page=` (+ 화면 전용 `from=`) | |
| 곡 상세 | `/piano/works/:id` | |
| 작곡가 목록 / 상세 | `/piano/composers` / `/piano/composers/:id?sort=&level=&pages=&downloadable=&page=` | |
| 준비 중 안내 | `/violin` · `/orchestra` (+ 화면 전용 `q·in·level·pages·downloadable·page`) | **그 아래 하위 경로는 없다** — `/violin/search` 는 찾을 수 없는 페이지. 검색 상태 6개는 검색 결과 화면에서 준비 중 탭을 눌렀을 때만 실린다(08 §2-3) |
| 관리 | `/admin`, `/admin/**` | 구분 밖 그대로(기획 04 §3-6). 구분 바를 **렌더하지 않는다** |
| 옛 주소 | `/` `/search` `/works/:id` `/composers` `/composers/:id` | 같은 화면의 `/piano/…` 로 **replace 리다이렉트**, **쿼리 그대로 보존**(기획 04 §3-5, 인수 조건 8-B 5) |
| 없는 구분 이름 | `/cello`, `/cello/works/3` | 찾을 수 없는 페이지. 구분 바는 보이되 **선택된 탭 없음**(화면정의 08 §3) |

- **화면 전용 쿼리 2개는 API 로 넘기지 않는다.** ⑴ `from=violin|orchestra` — 준비 중 화면의 헤더 검색으로 왔다는 표시(화면정의 08 §2-4 의 안내 줄). 주소에 담는 이유는 **한 번만 보이게** 하는 규칙을 화면이 상태 없이 지킬 수 있어서다: 재검색·기준 변경·필터 변경은 전부 새 주소를 push 하고 그때 이 값이 빠지면 줄도 사라진다. 알 수 없는 값이면 줄을 만들지 않는다(오류 아님). ⑵ 준비 중 화면의 검색 상태 `q·in·level·pages·downloadable·page`(검색 결과 화면에서 준비 중 탭을 눌렀을 때 그 6개를 이름 그대로 옮겨 싣는다, `in=ALL` 은 생략) — 돌아가기 링크(검색 결과 화면의 필터·페이지까지 복원)와 IMSLP 주소를 만드는 값(08 §2-3). **링크가 보이는지는 `q` 하나로 판단한다** — `q` 없이 다른 쿼리만 있으면 "들고 오지 않은 것"이라 돌아가기 링크를 만들지 않는다.
- **기본값은 주소에 쓰지 않는다**: `in=ALL` 은 생략한다. 그래서 `/piano/search?q=월광` 이 기준 없던 시절 주소와 같은 모양이 되고, 옛 링크가 그대로 열린다(인수 조건 8-F 3).
- **화면 안의 링크 규칙(프론트 계약)** — 구분 안의 화면으로 가는 링크는 전부 `/{현재 구분}/…` 로 만든다. 현재 구분이 없거나(404·관리) 준비 중이면 **기본 구분 `piano`**.
  - **예외 하나: 관리 화면에서 사용자 곡 화면으로 나가는 링크는 `/works/:id`** (구분 없는 주소). 관리자는 곡의 구분을 모르고 알 필요도 없으며(기획 04 §3-6, 그래서 관리 응답에 `section` 을 넣지 않았다), 이 주소는 **리다이렉트 → 곡 상세의 자기 교정**(응답 `section` 이 주소와 다르면 그 구분으로 replace, 기획 04 §1-4)으로 옳은 구분에 도착한다. 즉 옛 주소는 죽은 호환 코드가 아니라 **"구분을 모를 때 쓰는 주소"** 로 계속 산다 — 이것이 §3-5 리다이렉트를 영구 계약으로 두는 두 번째 이유다.

### 0-6. 문자열 길이 상한 (모든 저장 API 공통 — 2026-09-07 추가)

01_ERD 의 컬럼 길이(`VARCHAR(n)`)가 곧 API 상한이다. **상한을 넘으면 400 `VALIDATION_ERROR` + `errors[].field`** 이고,
정확히 상한 길이면 통과한다. (지금은 검증이 없어 DB 까지 내려갔다가 500 이 된다 — qa 결함 D3.)

| API | 필드 | 최대 |
|---|---|---|
| §4-4 작곡가 | `nameKo` | 100 |
| | `nameOriginal` | 200 |
| | `aliases[]` 각 항목 | 200 |
| | `nationality` | 100 |
| | `imslpUrl` | 500 |
| §4-8 곡 | `titleKo` | 300 |
| | `titleOriginal` | 300 |
| | `aliases[]` 각 항목 | 200 |
| | `catalogNumbers[]` 각 항목 | 100 |
| | `compositionYear` | 20 |
| | `musicalKey` | 50 |
| | `movements` | 500 |
| | `movementPageGuide` | 500 |
| | `collectionGuide` | 500 (2026-09-08 추가) |
| | `imslpUrl` | 500 |
| §5-2·§5-3 판본 | `publisher` | 300 |
| | `plateNumber` | 100 |
| | `editor` / `arranger` / `scanner` | 각 200 |
| | `imslpFileUrl` | 500 |
| | `imslpCopyrightText` | 200 |
| | `copyrightNote` | 1000 |
| | `ccLicenseName` | 100 |
| | `ccAttribution` | 200 |
| §5-6 추천 지정 | `note`(고른 이유 메모) | **300** (2026-09-21 추가) |
| §5-9·§5-10 판정 | `copyrightNote` | 1000 |
| §6-1 수집 확인 | `urls[]` 각 항목 | 500 |
| §6-2 수집 시작 | `items[].url` | 500 |

- **정규화 컬럼도 같은 길이**다(`title_ko_normalized` 300 등). 정규화는 길이를 늘리지 않으므로 원문 상한만 검사하면 된다.
- `errors[].field`: 단일 필드는 그 이름(`nameKo`), **목록 항목은 Bean Validation 표준대로 인덱스가 붙는다**(`aliases[0]`).
  화면은 `field` 의 대괄호 앞부분으로 입력을 찾는다(별칭 칩 입력은 컨트롤이 하나이므로 어느 항목인지까지는 표시하지 않아도 된다).
- 수집 API 의 `field` 는 `urls[0]`(§6-1) / `items[0].url`(§6-2) 다. 주소 입력은 여러 줄 텍스트 하나이므로 화면은 대괄호 앞부분(`urls`/`items`)으로 그 입력을 찾는다.
  **`crawl_item.url` 이 `VARCHAR(500)`** 이라 검증이 없으면 D3 와 똑같이 DB 까지 내려가 500 이 된다 — §6-2 는 작업까지 만들어 놓고 항목 저장에서 깨지므로 §6-1 보다 뒷맛이 더 나쁘다.
- 문구는 `"{n}자를 넘을 수 없어요"` 로 통일한다.

---
### 0-7. 악기 구분 `section` (2026-09-10 신설 — 기획 04 §3·§5, 03_기술결정 §21)

**화면 주소는 `/{구분 슬러그}/…` 경로**(03 §21), **API 는 쿼리 파라미터 `section`** 이다. 경로를 바꾸지 않는다.

| 항목 | 값 |
|---|---|
| 파라미터 이름 | `section` |
| 값 | `PIANO` / `VIOLIN` / `ORCHESTRA` (§0-4 대로 대문자 enum). **대소문자는 무시**한다(`piano` = `PIANO`) — 화면 주소의 슬러그가 소문자라 변환 실수를 계약이 흡수한다 |
| 생략했을 때 | **`PIANO`**. 구분이 없던 시절의 호출·옛 링크가 그대로 동작한다(기획 §3-5) |
| 정의되지 않은 값 | **400 `BUSINESS_RULE_VIOLATION`** — `level`·`pages` 와 같은 취급(`BusinessRuleException`). 화면에서 없는 구분 이름은 라우터가 404 화면으로 처리하므로 이 요청은 API 까지 오지 않는다. **검색 기준 `in` 의 관용 처리(§3-1)와 일부러 다르다**: `in` 은 "잘못 붙은 쿼리 한 조각"이라 결과를 통째로 안 주는 게 손해가 크고, `section` 은 "어느 세계를 보고 있는가"라서 조용히 다른 세계를 보여주면 사용자가 잘못된 결과를 옳은 것으로 읽는다 |
| 슬러그 ↔ enum | `piano↔PIANO`, `violin↔VIOLIN`, `orchestra↔ORCHESTRA`. 변환은 프론트 한 곳(`lib/sections.js`) |

**어느 API 에 걸리나**

| API | `section` | 걸리는 방식 |
|---|---|---|
| §3-1 `GET /api/works/search` | 쿼리 | 곡 목록·`unfilteredTotal`·`totalInAll`·작곡가 카드 전부 그 구분 안에서만 |
| §3-2 `GET /api/works/popular` | 쿼리 | 자격·폴백 모두 그 구분 안에서만 |
| §3-5 `GET /api/composers` | 쿼리 | "공개 곡 1개 이상" 의 뜻이 **"그 구분에서 공개 곡 1개 이상"** 으로 좁아진다. `workCount` 도 그 구분 기준 |
| §3-6 `GET /api/composers/featured` | 쿼리 | 같음 |
| §3-7 `GET /api/composers/{id}` | 쿼리 | `workCount` 만 그 구분 기준. **404 조건은 바뀌지 않는다** — 작곡가는 구분에 속하지 않으므로, 그 구분에 곡이 0개여도 200 이다(기존 규칙 유지) |
| §3-8 `GET /api/composers/{id}/works` | 쿼리 | 곡 목록·`unfilteredTotal` |
| §3-3 `GET /api/works/{id}` | **받지 않는다** | 곡이 스스로 구분을 안다. 대신 **응답에 `section` 을 싣는다**(화면이 다른 구분의 곡 주소로 들어온 사용자를 그 구분으로 전환시킨다 — 기획 §1-4). `sameComposerWorks` 는 그 곡의 구분 안에서 고른다 |
| §3-4 다운로드 | 받지 않는다 | 판본 id 로 직접 간다. 구분과 무관 |
| §4 ~ §6 관리 API 전부 | **받지 않는다** | 관리자는 모든 구분을 한 화면에서 본다(기획 §3-6). 관리 응답에 `section` 필드도 넣지 않는다 — **1차에 값을 바꿀 수 없으므로 보여 줄 것이 없다**(01_ERD §9-2) |

**`WorkSummaryDTO` 에는 `section` 을 넣지 않는다.** 목록 응답은 **전부 한 구분 안**이라 화면이 주소에서 이미 안다. 쓰는 곳 없는 필드를 먼저 두지 않는다(§8 의 `collectionPieceCount` 판단과 같은 원칙). `WorkDetailDTO` 만 예외인 이유는 위 표대로 그 값이 **화면 전환의 근거**로 실제로 쓰이기 때문이다.

**1차에는 걸러낼 대상이 실제로 없다 — 이 사실을 계약에 적어 둔다.**

지금 DB 의 모든 곡은 `section = PIANO` 이고, 그 값을 바꾸는 API·수집 경로가 **하나도 없다**(01_ERD §9-2). 그래서 `section=PIANO` 로 부르든 안 부르든 결과가 같고, **필터를 구현하지 않아도 모든 테스트가 초록이 될 수 있다.** 이것이 이번 작업에서 가장 조용한 위험이다.

- **테스트가 이 구멍을 메우는 방법**: 계약 검증 테스트(`SectionScopeIntegrationTest`)는 **다른 구분의 곡을 리포지토리로 직접 저장**한 뒤 위 표의 6개 API 를 부른다. 관리 API 로는 만들 수 없는 값이므로(그게 설계다) given 단계만 리포지토리를 쓰고, 검증은 그대로 MockMvc 상태코드 + 응답 JSON 이다(컨벤션 §6).
- **backend-dev 에게**: `section` 을 "받아서 무시" 하면 테스트가 잡는다. 반대로 그 테스트가 없으면 아무도 못 잡는다.

---


## 1. 엔드포인트 요약

### 공개 (비로그인)

| 메서드 | 경로 | 설명 | 화면 |
|---|---|---|---|
| GET | `/api/works/search` | 검색(작곡가 카드 + 곡 목록 + 필터 + 페이지) | 02 |
| GET | `/api/works/popular` | 인기곡 N개 | 01, 02(0건) |
| GET | `/api/works/{id}` | 곡 상세(추천 판본·다른 판본·같은 작곡가 곡) | 03 |
| GET | `/api/editions/{id}/download` | PDF 다운로드(1클릭) | 03 |
| GET | `/api/composers` | 작곡가 전체 목록(공개 곡 1개 이상, 가나다순) | 04-A |
| GET | `/api/composers/featured` | 홈 작곡가 바로가기(곡 수 순 8명) | 01 |
| GET | `/api/composers/{id}` | 작곡가 상세 | 04-B |
| GET | `/api/composers/{id}/works` | 작곡가의 곡 목록(정렬·필터·페이지) | 04-B |
| GET | `/api/works/recent` | **최근 본 곡의 "지금 정보"**(브라우저가 든 id 목록 → 요약, §3-9) | 01 |

### 회원 (USER — 로그인만 하면 된다, ADMIN 전용 아님) — 2026-09-20 신설 §10

| 메서드 | 경로 | 설명 | 화면 |
|---|---|---|---|
| PUT | `/api/me/favorites/{workId}` | 즐겨찾기 켜기(멱등) | 03 |
| DELETE | `/api/me/favorites/{workId}` | 즐겨찾기 끄기(멱등) | 03, 09 |
| GET | `/api/me/library/favorites` | 내 악보 › 즐겨찾기 탭(최근 추가순·20개 페이지) | 09 |
| GET | `/api/me/library/downloads` | 내 악보 › 받은 악보 탭(곡 단위 한 줄·최근 받은 순) | 09 |

### 관리자 (ADMIN)

| 메서드 | 경로 | 설명 | 화면 |
|---|---|---|---|
| GET | `/api/admin/dashboard` | 숫자 카드 + 최근 수집 작업 | 05-A |
| GET | `/api/admin/composers` | 작곡가 목록(관리) | 05-B |
| GET | `/api/admin/composers/{id}` | 작곡가 상세(관리) | 05-C |
| POST | `/api/admin/composers` | 작곡가 등록 | 05-C |
| PUT | `/api/admin/composers/{id}` | 작곡가 수정 | 05-C |
| DELETE | `/api/admin/composers/{id}` | 작곡가 삭제 | 05-C |
| GET | `/api/admin/works` | 곡 목록(관리, 숨김 포함) | 05-D |
| GET | `/api/admin/works/{id}` | 곡 상세(관리, 판본 포함) | 05-E, 06-A |
| POST | `/api/admin/works` | 곡 등록 | 05-E |
| PUT | `/api/admin/works/{id}` | 곡 수정 | 05-E |
| DELETE | `/api/admin/works/{id}` | 곡 삭제(판본·파일 포함) | 05-E |
| GET | `/api/admin/works/aliases/overlap` | 별칭이 다른 곡 몇 개에 있는지 | 05-E |
| GET | `/api/admin/works/{workId}/recommendation-history` | 바뀐 이력 **전부**("더 보기") (§4-7-1) | 06-A |
| POST | `/api/admin/edition-files` | PDF 업로드(쪽수·크기·미리보기 자동) | 06-B |
| POST | `/api/admin/works/{workId}/editions` | 판본 추가 | 06-B |
| GET | `/api/admin/editions/{id}` | 판본 1개(수정 모달·받아오기 폴링) | 06-A/B |
| PUT | `/api/admin/editions/{id}` | 판본 수정(파일 교체 포함) | 06-B |
| DELETE | `/api/admin/editions/{id}` | 판본 삭제 | 06-A |
| PUT | `/api/admin/works/{workId}/recommended-edition` | 추천 판본 지정 (**사유 필수** — §5-6, 2026-09-21 개정) | 06-A |
| GET | `/api/admin/works/{workId}/recommended-edition/preview` | **바꾸기 전** 경고 6종 예고 (§5-6-2, 2026-09-21 신설) | 06-A |
| PUT | `/api/admin/works/{workId}/recommended-edition/review` | 추천 판본 확인함/해제 (§5-6-1) | 06-A |
| POST | `/api/admin/editions/{id}/fetch-file` | IMSLP 에서 이 판본 파일 받아오기(비동기) | 06-A |
| GET | `/api/admin/copyright/pending` | 저작권 판정 대기함 | 06-C |
| PUT | `/api/admin/editions/{id}/copyright` | 판정 1건 | 06-C |
| POST | `/api/admin/editions/copyright/bulk` | 일괄 판정 | 06-C |
| POST | `/api/admin/copyright/auto-judge` | 자동 저작권 판정 일괄 실행(+추천 판본 자동 지정) | 06-C |
| POST | `/api/admin/copyright/auto-judge/undo` | 자동 판정 전부 되돌리기 | 06-C |
| POST | `/api/admin/crawl/check` | 수집 대상 주소 확인(판정 표) | 07-A |
| POST | `/api/admin/crawl/jobs` | 수집 시작(작업 생성) | 07-A |
| GET | `/api/admin/crawl/jobs` | 수집 작업 목록 | 07-A |
| GET | `/api/admin/crawl/jobs/active` | 진행 중(RUNNING/PAUSED) 작업 — 상단 띠 | 05 공통 |
| GET | `/api/admin/crawl/jobs/{id}` | 진행/결과(항목 포함) | 07-B |
| POST | `/api/admin/crawl/jobs/{id}/stop` | 중지 | 07-B |
| POST | `/api/admin/crawl/jobs/{id}/resume` | 이어서 시작 / 일시 정지 즉시 재개 | 07-B |
| POST | `/api/admin/crawl/jobs/{id}/retry-failed` | 실패한 것만 새 작업으로 | 07-B |

기존 API(`/api/login`, `/api/logout`, `/api/users/me`, `/api/tokens/refresh`, 커뮤니티·채팅·파일)는 변경 없음.

---

## 2. 공통 DTO

### 2-1. `ComposerRefDTO` (곡 응답 안의 작곡가 요약)
```json
{ "id": 4, "nameKo": "베토벤", "nameOriginal": "Beethoven, Ludwig van" }
```
`nameKo` 는 null 가능(수집 직후) — 화면은 원어로 폴백.

### 2-2. `WorkSummaryDTO` (곡 카드 — 검색·인기곡·작곡가 상세·같은 작곡가 곡)

| 필드 | 타입 | 설명 |
|---|---|---|
| id | long | |
| titleKo | string\|null | |
| titleOriginal | string | |
| composer | ComposerRefDTO | |
| catalogNumbers | string[] | 표시 순서(sort_order) |
| level | `BEGINNER\|ELEMENTARY\|INTERMEDIATE\|ADVANCED`\|null | null = 난이도 미정 |
| status | `READY\|PREPARING\|RESTRICTED\|UNKNOWN` | 01_ERD §4 계산 규칙 |
| pageCount | int\|null | 추천 판본 쪽수. 추천 판본 없으면 null |
| fileSize | long\|null | 추천 판본 바이트 |
| previewUrl | string\|null | 추천 판본 첫 페이지. **추천 판본이 `FREE` 일 때만** 값이 있다(§0-4 · §2-3 과 같은 규칙) |
| matchedAlias | string\|null | **검색 응답에서만** 값이 있다(§3-1). 그 외 항상 null |
| scopeNote | object\|null | **2026-09-08 추가(기획 §11-2).** "받게 되는 악보가 찾은 것과 어떻게 다른가" **한 줄**을 만들 재료. 할 말이 없으면 **null** — 대부분의 곡이 여기 해당한다. 아래 2-2-1 |

```json
{ "id": 21, "titleKo": "월광 소나타", "titleOriginal": "Piano Sonata No.14, Op.27 No.2",
  "composer": { "id": 4, "nameKo": "베토벤", "nameOriginal": "Beethoven, Ludwig van" },
  "catalogNumbers": ["Op.27 No.2"], "level": "INTERMEDIATE", "status": "READY",
  "pageCount": 14, "fileSize": 1059957, "previewUrl": "/uploads/3f2a…c1.png", "matchedAlias": "월광", "scopeNote": null }
```

#### 2-2-1. `scopeNote` — 검색 항목의 "범위 한 줄" (2026-09-08 신설, 기획 §11-2 ②)

```json
"scopeNote": { "codes": ["ARRANGEMENT", "MOVEMENT_ONLY"], "movementNumber": 2 }
```

| 코드 | 판정 조건 | 화면 문구(예 — 확정은 designer) |
|---|---|---|
| `COLLECTION` | `work.collection_guide` 가 비어 있지 않다(= **묶음 악보**, 01_ERD §3-3) | "'강아지 왈츠'가 들어 있는 악보" (`matchedAlias` 와 조합) |
| `ARRANGEMENT` | **추천 판본**의 `kind = ARRANGEMENT` | "피아노 편곡 악보예요" |
| `MOVEMENT_ONLY` | **추천 판본**의 `scope = MOVEMENT` | "2악장만 들어 있어요" |

- `codes` 는 **고정 순서** `COLLECTION → ARRANGEMENT → MOVEMENT_ONLY` 이고 **절대 빈 배열이 아니다** — 해당 코드가 하나도 없으면 `scopeNote` 자체가 `null` 이다(화면은 줄을 만들지 않는다). 빈 상태를 두 가지로 만들지 않는다.
- `movementNumber`: `MOVEMENT_ONLY` 일 때 추천 판본의 악장 번호. 수집이 헤딩에서 못 읽었으면 `null`(화면 폴백 문구). `MOVEMENT_ONLY` 가 없으면 항상 `null`.
- **추천 판본이 없는 곡**은 `ARRANGEMENT`/`MOVEMENT_ONLY` 를 판정할 근거가 없다 → `COLLECTION` 만 나올 수 있다.
- `WorkSummaryDTO` 를 쓰는 **모든 응답**(§3-1 검색 · §3-2 인기곡 · §3-3 sameComposerWorks · §3-8 작곡가의 곡)에서 같은 규칙으로 채운다. `matchedAlias` 처럼 한 응답에서만 채우는 값이 아니다.

**`COLLECTION` 의 두 번째 쓰임 — 난이도·쪽수의 "(전곡 기준)" (2026-09-08 추가, 기획 §12-2. 계약 변경 없음)**

- 묶음 악보의 **난이도와 쪽수는 곡 하나가 아니라 묶음 전체의 값**이다. 그대로 두면 "달빛"을 찾은 레슨생이 `고급 · 62쪽`(= 베르가마스크 모음곡 4곡)을 보고 **못 치는 곡이라 판단하고 창을 닫는다.** 실제 달빛 단독은 중급이다.
- 판정 근거는 이미 있는 `scopeNote.codes ∋ COLLECTION` 하나다. **필드를 새로 만들지 않는다** — "N곡 묶음"에 필요하던 `collectionPieceCount`·`collectionUnit` 은 기획 §12-2 가 **2차로 미뤘다.**
- 화면 규칙(곡 카드 `full` — §3-1 검색 결과 · §3-8 작곡가 상세): 난이도 칩과 쪽수가 **같은 줄**에 있으므로 `(전곡 기준)` 은 그 줄 끝에 **한 번만** 붙인다 → `고급 · 62쪽 (전곡 기준)`. 쪽수가 없으면 난이도 뒤에 붙는다(난이도에도 똑같이 걸리는 꼬리표다).
- `COLLECTION` 이 없으면(= 수록곡 안내가 빈 곡) **붙이지 않는다.** 시스템은 그 곡이 묶음인지 모른다 — 아는 사람(관리자)이 §4-8 `collectionGuide` 를 채우면 그 순간 복구된다.
- 곡 카드 `compact`(홈 인기곡·같은 작곡가의 다른 곡)와 곡 상세 화면은 **이번 범위 밖**이다(기획 §12-3 이 검색 결과·작곡가 상세만 지정했다). designer 가 정해 주면 같은 규칙을 확장한다.
- 계약 검증: `WorkCard.test.jsx`.

> **왜 필드가 하나인가 (기획 §11-2).** 사용자에게 이 줄은 **"받는 게 찾은 것과 다르다"** 는 한 가지 정보다 —
> 묶음은 "더 크다"(찾은 곡이 그 안에 들어 있다), 편곡·악장은 "작거나 다르다". 방향만 반대일 뿐 같은 질문의 답이라
> 화면에서 같은 자리·같은 줄을 쓴다(기획 §2 F2-5). 필드를 둘로 쪼개면 세 스택이 "둘 다일 때 어느 줄이 먼저인가"를
> 각자 정하게 되고, 그 순간 화면마다 답이 달라진다.
> **문구는 서버가 만들지 않는다** — 코드만 주고 문장은 화면(designer)이 만든다. 서버가 완성 문장을 내려보내면
> 문구 한 글자 고치는 데 배포가 필요하고, 앱(Flutter)이 붙을 때 같은 문장을 두 벌 관리하게 된다.
> 예외는 `collectionGuide`(§3-3) 하나다 — 그건 곡마다 사람이 쓴 **데이터**이지 UI 문구가 아니다.
>
> **아직 계약에 없는 것**: 기획 §2 F2-5 의 난이도 "(전곡 기준)" 과 쪽수 옆 "N곡 묶음" 은 **곡 수(숫자)** 가 필요한데
> `collection_guide` 문장에서는 뽑을 수 없다. 별도 필드가 필요하므로 이번 계약에 넣지 않았다 — §8 되돌림.

### 2-3. `EditionDTO` (사용자 화면 판본 — 추천 카드·다른 판본 행·라이트박스)

| 필드 | 타입 | 설명 |
|---|---|---|
| id | long | |
| kind | `COMPLETE_SCORE\|PARTS\|ARRANGEMENT` | 화면 문구: 전체 악보/파트보/편곡 |
| scope | `COMPLETE\|MOVEMENT` | |
| movementNumber | int\|null | scope=MOVEMENT 일 때 "N악장만" |
| sectionLabel | string\|null | movementNumber 없을 때 폴백 표시(IMSLP 섹션명) |
| pageCount | int\|null | |
| fileSize | long\|null | 파일 없으면 null |
| hasFile | boolean | `pdfFileId != null` |
| previewUrl | string\|null | **`koreaCopyright == FREE && previewFileId != null` 일 때만** 값이 있다. 그 밖에는 null (2026-09-08, 기획 §F3-6 · §0-4). 관리 응답 `AdminEditionDTO`(§4-7 · §5-4)는 **판정과 무관하게 그대로** — 판정 근거가 미리보기다 |
| publisher, publishYear, plateNumber, editor, arranger, scanner | string\|null (publishYear 는 int\|null) | 있는 것만 표시 |
| koreaCopyright | `FREE\|RESTRICTED\|UNKNOWN` | 뱃지 |
| imslpCopyrightText | string\|null | "IMSLP 표기: …" |
| ccLicenseName, ccAttribution | string\|null | CC 줄 |
| imslpFileUrl | string\|null | "파일 페이지 ↗" / "IMSLP에서 보기" |
| downloadable | boolean | `hasFile && koreaCopyright == FREE` — 버튼 활성 기준 |
| largeFile | boolean | `fileSize >= 20MB`(20 × 1024 × 1024) — 경고 문구 |
| downloadUrl | string\|null | downloadable 일 때 `/api/editions/{id}/download`, 아니면 null |

> **`previewUrl == null` 의 두 가지 이유를 화면은 `koreaCopyright` 로 가른다 (2026-09-08).**
> 기획 §5 예외표가 문구를 다르게 정했다 — 파일이 없으면 **"미리보기 준비 중"**, 판정이 안 끝났으면
> **"저작권을 확인하는 중이라 미리보기도 아직 보여드릴 수 없어요"**. 이유를 알려주는 **별도 필드는 두지 않는다**:
> `koreaCopyright` 가 이미 그 답이고, 필드를 하나 더 두면 두 값이 어긋날 때 어느 쪽이 참인지 아무도 모른다.
> 규칙: `koreaCopyright !== "FREE"` → 저작권 문구, 그 밖 → "미리보기 준비 중".
> "다른 판본" **줄(row)** 에서는 이 문구를 반복하지 않는다 — 같은 줄의 저작권 뱃지가 이미 그 사실을 말하고,
> 긴 문구를 작은 썸네일 자리에 넣으면 줄이 깨진다. 문구는 **추천 판본 카드에서만** 보인다.
> 계약 검증: `WorkDetailPage.previewGate.test.jsx`, `EditionPreviewExposureIntegrationTest`.

### 2-4. `EditionBriefDTO` (판본 한 줄 설명 — 2026-09-20 신설, 화면정의 09 §1-2)

받은 악보 항목의 **`받은 판본: 전체 악보 · 전곡 · 5쪽 · 1.1MB`** 한 줄을 만드는 값만 담는다. `EditionDTO`(§2-3)를 쓰지 않는 이유: 그 DTO 의 절반(미리보기·저작권 표기·IMSLP 링크·`downloadable`)은 이 줄에 쓰이지 않고, **판본이 삭제된 뒤에도 남아야 하는 값**(01_ERD §3-12 스냅샷)은 여기 다섯뿐이다.

| 필드 | 타입 | 설명 |
|---|---|---|
| id | long\|null | 지금도 살아 있는 판본이면 그 id. **삭제됐으면 `null`**(설명만 스냅샷으로 남아 있다) |
| kind | `COMPLETE_SCORE\|PARTS\|ARRANGEMENT` | |
| scope | `COMPLETE\|MOVEMENT` | |
| movementNumber | int\|null | |
| pageCount | int\|null | |
| fileSize | long\|null | |

```json
{ "id": 301, "kind": "COMPLETE_SCORE", "scope": "COMPLETE", "movementNumber": null, "pageCount": 5, "fileSize": 1153434 }
```

- 화면 문구는 기존 `formatEditionKind`·`formatEditionScope`·`formatFileSizeCompact` 로 만든다(서버는 문장을 만들지 않는다 — §2-2-1 과 같은 원칙). `sectionLabel` 은 넣지 않는다: 스냅샷 대상이 아니고(01_ERD §3-12), `scope=MOVEMENT`·`movementNumber=null` 이면 화면이 `발췌` 로 폴백한다(§3-4 접미사 규칙과 같은 말).

---

## 3. 공개 API 상세

### 3-1. `GET /api/works/search` — 검색

**쿼리**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| q | string | Y | 검색어. 공백만이면 400 `VALIDATION_ERROR`(field `q`). 최대 100자 |
| in | `ALL \| TITLE \| COMPOSER` | N | **검색 기준 — 2026-09-10 신설**(기획 04 §4). 생략·빈 값·**알 수 없는 값 전부 `ALL`** 이고 **오류를 내지 않는다**(아래 관용 규칙). 대소문자 무시 |
| section | `PIANO \| VIOLIN \| ORCHESTRA` | N | 악기 구분 — §0-7. 생략 시 `PIANO`, 정의되지 않은 값은 400 |
| level | string | N | 쉼표 구분 복수: `level=ELEMENTARY,INTERMEDIATE`. 지정 시 level NULL(미정) 곡 제외 |
| pages | `LE10 \| 11_20 \| GE21` | N | 추천 판본 쪽수 구간(10 이하 / 11~20 / 21 이상). 지정 시 추천 판본 없는 곡 제외. 잘못된 값 400 |
| downloadable | boolean | N | `true` 면 status=READY 만 |
| page | int | N | 0-base, 기본 0 |
| size | int | N | 기본 20, 최대 100 |

**검색 규칙 (확정)**

1. `q` 를 공백으로 나눠 단어마다 `SearchNormalizer.normalize`(01_ERD §2). 빈 단어 제거. 결과 단어 0개 → 400.
2. 단어마다 아래 **어느 하나에 부분 일치(LIKE %단어%)** 해야 하고, **모든 단어가 각각** 걸려야 한다(AND):
   곡 `title_ko_normalized` / `title_original_normalized` / 별칭 `alias_normalized`(EXISTS) / 작품번호 `catalog_value_normalized`(EXISTS) / 작곡가 `name_ko_normalized` / `name_original_normalized` / 작곡가 별칭 `alias_normalized`(EXISTS).
3. `hidden = true` 곡 제외.
4. **정렬**: 일치도 내림차순 → `download_count` 내림차순 → `id` 오름차순. 일치도(`qn` = 전체 검색어를 공백 없이 normalize 한 값):
   - 3: `title_ko_normalized = qn` 또는 어떤 별칭 `alias_normalized = qn`
   - 2: `title_ko_normalized LIKE qn%` 또는 어떤 별칭 `LIKE qn%`
   - 1: 그 외
5. **작곡가 카드** `composers`: 모든 단어가 작곡가 `name_ko_normalized` / `name_original_normalized` / 작곡가 별칭 중 하나에 각각 부분 일치하고, 공개 곡이 1개 이상인 작곡가. 곡 수 많은 순 최대 3명, 전체 수는 `composerMatchCount`. 필터·페이지와 무관.
   - **`workCount` 는 "그 작곡가에게 등록된 공개 곡 전체 수"다 — 검색 필터·페이지와 무관하고, 같은 응답의 `works.totalElements` 와 일부러 다르다** (2026-09-08 명확화, 기획 §11-4). 두 숫자는 다른 질문의 답이다: 카드는 "작곡가 페이지에 가면 몇 곡이 있나", 목록은 "이 조건에 몇 곡이 걸렸나". 필터로 목록이 0건이 되어도 이 값은 그대로이며, 그때 카드는 결과가 아니라 **출구**다.
   - **계약은 바꾸지 않는다** — 숫자를 필터에 맞추면 카드를 눌러 들어간 작곡가 페이지의 곡 수와 어긋나 더 큰 거짓말이 된다(기획 §11-4-4). 두 숫자가 헷갈리는 문제는 **화면 문구**로 푼다("곡 24개"가 아니라 "등록된 곡 24개 모두 보기") — designer·frontend-dev 몫이고 API 는 그대로다.
6. **`matchedAlias`**(항목별) — 구현 확정 규칙(2026-09-07, backend-dev 보고 반영):
   1. 검색어가 **한 단어**이고 그 단어가 어떤 곡 별칭과 **정확히 같으면** 그 별칭 원문을 내려준다. (예: "월광" → `"월광"` — 곡 제목 "월광 소나타"가 그 단어를 포함해도 별칭 일치가 우선)
   2. 아니면 "주 필드"(곡 제목 2개·작곡가 이름·작곡가 별칭·작품번호)로 설명되지 않는 단어를 포함하는 곡 별칭 중 `id` 최소의 원문. 모든 단어가 주 필드로 설명되면 null. (예: "쇼팽 녹턴" → 녹턴 Op.9 의 제목·작곡가로 두 단어가 모두 설명되므로 null)
7. `unfilteredTotal`: 필터(level/pages/downloadable) 없이 검색어만 적용한 곡 수 — "21곡 중 8곡".
8. **`section`**(§0-7): 위 1~7 이 전부 그 구분 안에서 계산된다 — `works`·`unfilteredTotal`·`totalInAll`·작곡가 카드·`composerMatchCount` 모두.

**검색 기준 `in` — 각 기준이 찾는 칸 (2026-09-10 확정, 기획 04 §4-2)**

위 규칙 2번의 **OR 묶음만** 좁아진다. **정규화·부분 일치·여러 단어 AND·숨김 제외·일치도 정렬·페이지는 전부 그대로다** — 기준이 하는 일은 "어느 칸에서 찾는가" 하나뿐이다(기획 §4-2).

| 찾는 칸 (01_ERD) | `ALL` | `TITLE` | `COMPOSER` |
|---|:---:|:---:|:---:|
| `work.title_ko_normalized` | O | **O** | — |
| `work.title_original_normalized` | O | **O** | — |
| `work_alias.alias_normalized` (EXISTS) | O | **O** | — |
| `work_catalog_number.catalog_value_normalized` (EXISTS) | O | **O** | — |
| `composer.name_ko_normalized` | O | — | **O** |
| `composer.name_original_normalized` | O | — | **O** |
| `composer_alias.alias_normalized` (EXISTS) | O | — | **O** |

- **`ALL` 은 지금 검색과 한 글자도 다르지 않다.** 기획 §7 충돌 4 가 요구한 대로 **`01` §6 의 기존 검색 인수 조건 전부가 `ALL` 에서 그대로 통과해야 한다**(회귀). 계약 검증: `WorkSearchApiIntegrationTest`(기존, 손대지 않는다) + `WorkSearchScopeIntegrationTest`(같은 인수 조건을 `in=ALL` 을 **명시해서** 한 번 더).
- **작품번호는 `TITLE` 이다** — 사용자에게 `Op.27 No.2` 는 곡을 부르는 이름이다(기획 §4-2). 그래서 `in=COMPOSER&q=Op.27 No.2` 는 **0건이 정상**이다(인수 조건 8-D 7).
- **알 수 없는 값은 오류가 아니다.** `in=xyz`·`in=`·`in=TITLE_KO` 전부 `ALL` 로 열고 **200** 을 준다. 근거(기획 §4-5): 사용자는 주소를 손으로 편집하지 않는다 — 링크가 조금 상했다고 결과를 통째로 안 주는 건 손해만 크다. `level`·`pages` 가 400 인 것과 다른 이유는, 그 둘은 **결과를 좁히는 조건**이라 조용히 무시하면 사용자가 안 건 필터가 걸린 줄 알지만, `in` 은 **가장 넓은 기본값으로 떨어지므로 사용자가 잃는 것이 없다.**
- 응답의 **`in` 은 관용 처리가 끝난 확정값**이다(`in=xyz` → `"in": "ALL"`). 화면 세그먼트는 이 값으로 그린다(인수 조건 8-F 4).
- **주소에 쓰는 표기는 대문자 enum 이다 (2026-09-11 확정, senior-dev).** 프론트는 URL 에 `in=TITLE`·`in=COMPOSER` 를 쓰고 `ALL` 은 생략한다(`searchScope.js`, `SearchResultPage`·`HomePage.scope` 테스트가 잠금). 응답·API 요청·URL 이 **한 표기(대문자 enum)** 로 통일되고, 서버는 어차피 대소문자를 무시하므로 URL 케이스는 기능에 영향이 없다(프론트도 `readScopeParam` 이 대소문자 무시로 읽는다). *(화면정의 `00 §8`·`08 §4-1` 의 "소문자로 쓴다" 문구는 이 확정 이전 표기다 — designer 가 대문자로 정정. 슬러그 `piano` 소문자는 경로 어휘라 별개다.)*

**기준에 따라 달라지는 응답 필드**

| 필드 | `ALL` | `TITLE` | `COMPOSER` |
|---|---|---|---|
| `composers[]` · `composerMatchCount` | 규칙 5 그대로 | **`[]` · `0`** | 규칙 5 그대로 |
| `matchedAlias` (항목별) | 규칙 6 그대로 | 규칙 6 을 쓰되 "주 필드" 에서 **작곡가 이름·작곡가 별칭을 뺀다**(= 곡 제목 2개 + 작품번호). 그 기준으로 찾지 않은 칸이 "이 단어를 설명한다" 고 판단하면 별칭이 이유 없이 사라진다 | **항상 `null`** — 곡 별칭이 검색 대상이 아니다 |
| `totalInAll` | **`null`** | 아래 | 아래 |

- **`TITLE` 에서 작곡가 카드를 내리지 않는 이유**(designer 결정, 화면정의 02 §요소표): 곡명 기준은 **작곡가를 찾지 않기로 한 약속**이다(인수 조건 8-D 5). 목록에는 작곡가로 걸린 곡이 하나도 없는데 위에 작곡가 카드만 떠 있으면 그 약속이 깨져 보인다. `COMPOSER` 에서는 당연히 낸다.

**`totalInAll` — 0건 화면 [B] 판정 정보 (2026-09-10 신설, 기획 04 §9 8-6 / 화면정의 08 §4-7)**

| `in` | 값 | 뜻 | 화면(08 §4-7) |
|---|---|---|---|
| `ALL` | **`null`** | 질문 자체가 성립하지 않는다(이미 전체다) | [B] 를 검사하지 않는다 |
| `TITLE`·`COMPOSER` | **정수 N ≥ 0** | **같은 구분 · 같은 검색어 · 기준 `ALL` · 필터 없음** 의 곡 수 | `N > 0` → [B](`전체에서 찾으면 N곡이 있어요`) / `N == 0` → [C] |

- **화면이 "모름"과 "0건"을 절대 같게 취급하지 않는다**(designer 가 못 박은 조건). 그래서 이 계약은 **셋을 구분한다**: 숫자(`N>0`) / 0건(`0`) / 모름(`null`, `in=ALL` 일 때만). 화면이 [B] 를 추측으로 띄우는 경우가 계약상 존재하지 않는다.
- **필터를 빼고 세는 이유**: [B] 는 **필터를 다 푼 뒤에도 0건일 때만** 나온다([A] 가 먼저 — 기획 §4-4). 화면이 [B] 를 그릴 때 필터는 이미 없다. 필터를 넣어 세면 [A] 를 지나온 화면에서만 맞는 숫자가 된다.
- **비용**: `in != ALL` 일 때만 `work` 위의 `count` 쿼리 **1회**가 는다(수백 행, `unfilteredTotal` 과 같은 성격). `in == ALL` 이면 쿼리를 더 돌리지 않는다.
- 숫자를 못 줄 이유가 없으므로 designer 의 "있다/없다 비트" 최소 요구보다 한 단계 위를 준다. 화면은 숫자를 안 써도 계약이 성립한다.


**응답 200**
```json
{ "success": true, "message": "성공", "data": {
  "q": "월광",
  "in": "ALL",
  "composers": [ { "id": 4, "nameKo": "베토벤", "nameOriginal": "Beethoven, Ludwig van", "workCount": 5 } ],
  "composerMatchCount": 1,
  "unfilteredTotal": 1,
  "totalInAll": null,
  "works": { "content": [ WorkSummaryDTO… ], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true }
} }
```
`composers[]` 항목 = `ComposerCardDTO { id, nameKo, nameOriginal, workCount }`. 범위 밖 페이지는 200 + 빈 `content`.

**인수조건 매핑(테스트가 검증할 것)**: "월광"→소나타 14번 / "엘리제를 위하여"·"엘리제"·"Für Elise"·"fur elise" 동일 곡 / "쇼팽 녹턴"→쇼팽 곡만 / "Chopin"="쇼팽" / "Op.27 No.2"="op 27 no 2"="op27no2" / "BWV 846"="BWV846" / "K.545"="K545" / 대소문자 무시 / "녹" 부분 일치 / 숨김 곡 미노출 / 필터 3종 / 20개 페이지.

**검색 기준 인수조건(기획 04 §8-D·8-E·8-F)**: `in=TITLE` 로 "월광"·"엘리제"·"Für Elise"·"Piano Sonata No.14"·"Op.27 No.2"·"op27no2"·"K545"·"BWV 846" → 해당 곡 / `in=TITLE&q=쇼팽` → 제목·별칭에 그 글자가 없는 곡은 안 나옴 / `in=TITLE&q=Chopin 녹턴` → 0건 (`쇼팽 녹턴` 은 시드 별칭 때문에 곡명으로도 2곡이라 0건이 아니다 — §9-1) / `in=COMPOSER` 로 "쇼팽"·"Chopin"·"차이콥스키"·"Beethoven, Ludwig van" → 그 작곡가 곡 / `in=COMPOSER&q=녹턴` → 0건 / `in=COMPOSER&q=Op.27 No.2` → 0건 / `in` 없음·`in=xyz` → `"in":"ALL"` 200 / `in=TITLE` 이면 `composers` 비었음 / `totalInAll` 3상태.

### 3-2. `GET /api/works/popular?limit=10` — 홈 "지금 바로 받을 수 있는 인기곡"
**2026-09-08 전면 개정 (기획 §11-3 — 이 자리는 순위표가 아니라 견본 진열대다).** `limit` 기본 10, 최대 20, 1 미만이면 400.
**2026-09-10 추가**: 쿼리에 `section`(§0-7, 생략 시 `PIANO`)이 붙는다. 자격 곡·폴백 곡 모두 **그 구분 안에서만** 고른다.


1. **자격 (둘 다 만족해야 목록에 오른다)**
   - `status = READY` (01_ERD §4 계산 규칙)
   - `title_ko` 가 비어 있지 않다
   - 숨김 곡 제외는 그대로.
2. **정렬** `download_count DESC` → `level ASC`(`BEGINNER → ELEMENTARY → INTERMEDIATE → ADVANCED`, **NULL 은 맨 뒤**) → `title_ko` 오름차순(가나다) → `id ASC`.
   - **`created_at DESC`(최근 등록순)는 폐기한다** — 방금 만든 미완성 곡이 1위가 된다(기획 §2 F1).
   - 2순위가 난이도인 이유(큐레이션 노출 순서를 쓰지 않는 근거)는 `03_기술결정.md` §18. 기획 §11-3-5 가 제시한 대안이다.
3. **폴백** — 자격 곡이 `limit` 보다 적으면 **모자란 칸만** `status = PREPARING` 이고 `title_ko` 가 있는 곡으로 채운다(정렬 규칙 동일). 폴백 곡은 **항상 자격 곡 뒤**에 온다.
   - `RESTRICTED`·`UNKNOWN` 곡은 폴백에도 쓰지 않는다 — 사용자가 받을 수 없고 뱃지가 부정적이라 견본이 되지 못한다.
4. **자격 곡이 0개면 폴백도 하지 않고 빈 배열 `[]`** 를 준다. 화면은 인기곡 영역 **자체를 표시하지 않는다**(기획 §2 F1 — "빈 채로 자리를 남기지 않는다"). "아직 등록된 곡이 없어요" 같은 빈 상태 문구도 두지 않는다.

응답 `data: WorkSummaryDTO[]`(`matchedAlias` 는 항상 null, `scopeNote` 는 §2-2-1 규칙대로 채운다).

> 왜 필터를 거는데 정렬 근거는 다운로드 수 그대로인가: 우리가 손보는 것은 "누가 이 자리에 설 자격이 있는가"이지 "누가 더 인기 있는가"가 아니다(기획 §11-3-4). 계약 검증: `WorkPopularApiIntegrationTest`.

### 3-3. `GET /api/works/{id}` — 곡 상세

404: 없는 id, **숨김 곡**.

`WorkDetailDTO`

| 필드 | 타입 | 설명 |
|---|---|---|
| id, titleKo, titleOriginal, composer, catalogNumbers, level, status | (2-2 와 동일) | |
| section | `PIANO\|VIOLIN\|ORCHESTRA` | **2026-09-10 추가.** 이 곡이 속한 구분(§0-7). 화면은 주소의 구분과 다르면 **그 구분으로 전환해** 보여준다(기획 04 §1-4) — "찾을 수 없음" 으로 보내지 않는다. *1차에는 전부 `PIANO` 라 실제로 갈리지 않지만, 규칙과 계약은 지금 정한다* |
| aliases | string[] | 원문, id 순. 0개면 `[]`(줄 생략은 화면) |
| compositionYear, musicalKey, movements, movementPageGuide | string\|null | |
| collectionGuide | string\|null | **2026-09-08 추가.** 수록곡 안내(기획 §2 F3-2, 03 §10). 곡 번호 기준 완성 문장을 **그대로** 내려준다(서버가 조립하지 않는다). null/공백이면 화면에서 줄 생략. 이 값이 있는 곡이 **묶음 악보**이고, §2-2-1 `scopeNote.codes` 의 `COLLECTION` 판정 근거다 |
| imslpUrl | string\|null | "출처: IMSLP — 원본 페이지 보기" |
| composerImslpUrl | string\|null | |
| recommendedEdition | EditionDTO\|null | |
| otherEditions | EditionDTO[] | 추천 제외, **파일 있는 판본 전부**(편성 무관). 정렬 `imslp_download_count DESC NULLS LAST, id ASC`. 상한 없음 — 개수는 우리가 통제한다(곡당 최대 2개, 01 §9-1) |
| imslpOnlyCount | int | **2026-09-08 신설.** 추천을 뺀 판본 중 **파일 없는 것의 수**(편성 무관). 화면의 "IMSLP 에는 이 곡의 다른 악보가 N개 더 있어요" 한 줄이 쓰는 값. 0 이면 줄을 만들지 않는다 |
| imslpCandidateEdition | EditionDTO\|null | **2026-09-08 신설(기획 §F3-7 · §10-5).** `recommendedEdition == null` 일 때만 값이 있다. 아래 3-3-2 |
| downloadableOtherCount | int | **전체** 판본 중 downloadable=true 수 — "바로 받을 수 있는 다른 판본이 N개 있어요" |

> **줄로 펼치는 것은 "우리가 파일을 가진 판본" 뿐이다 (2026-09-08 계약 통일, senior-dev — qa 4차 결함 4).**
> 그 전까지 계약(2026-09-07·09-08)은 "파일 있는 판본 전부 + 파일 없는 `COMPLETE_SCORE` 최대 5개 + `otherEditionsTotal`" 이었고,
> 기획 §F3-4 는 "줄은 파일 있는 판본만, 나머지는 개수 한 줄" 이었다 — **계약과 기획이 서로 다른 말을 했다.** 기획을 따른다.
> - 그 5칸의 유일한 용도가 "IMSLP 에 더 있다" 는 **안내**인데, 같은 정보를 **숫자 한 줄이 더 정확히** 전한다.
>   5줄은 "88개 중 5개" 라는 사실을 말할 수 없다.
> - 그 5줄은 **누를 수 없는 줄**이다(파일이 없으니 버튼이 "IMSLP에서 보기"). 행동이 다른 줄을 같은 목록에 섞으면
>   접이식 헤더의 "(N개)" 가 "받을 수 있는 판본 수" 가 아니게 된다 — **헤더 숫자가 거짓말을 한다.**
> - 원래 근거(안내용 5칸)는 이미 2026-09-08 편성 필터에서 반쯤 무너졌다. 5칸을 정확히 만들려고 `kind` 를 걸렀지만
>   걸러도 "파일 없음" 줄인 것은 같다. **규칙을 덧대는 대신 없앤다.**
> - 실측(qa 4차): 프론트는 `otherEditionsTotal` 을 아예 쓰지 않는다. 지금 화면에는 파일 없는 5줄만 있고 안내 줄은 없다 —
>   **두 설계의 나쁜 점만** 남아 있었다.
>
> **바뀐 것**
> - `otherEditions` = 추천 제외 · **파일 있는 판본 전부**(편성 무관). 잘리지 않는다.
> - `otherEditionsTotal` **삭제** — 새 규칙에서는 언제나 `otherEditions.length` 와 같다. 길이와 늘 같은 필드는 잡음이고,
>   없어진 "잘림" 개념을 화면이 다시 계산하게 만든다.
> - `imslpOnlyCount` **신설** = 추천 제외 · 파일 없는 판본 수. **편성으로 거르지 않는다** — 이 숫자는 IMSLP **작품 페이지**로
>   보내는 링크의 개수 안내이고, 그 페이지에 실제로 있는 것은 편곡·파트보를 포함한 전부다. 걸러 세면 사용자가 링크를
>   눌러 보는 것과 어긋난다(목록은 *무엇을 보여줄지*의 문제였고, 숫자는 *거기 몇 개가 있는지*의 문제다).
> - **관리 화면(§4-7)은 그대로 전부 보여 준다** — 관리자는 편곡·파트보도 저작권 판정과 추천 후보 판단을 해야 한다.
> - 화면(기획 §F3-4): 접이식 헤더 개수 = `otherEditions.length`, 접힌 영역 맨 아래 회색 한 줄 =
>   "IMSLP 에는 이 곡의 다른 악보가 `imslpOnlyCount`개 더 있어요 — IMSLP 에서 보기 ↗"(곡의 `imslpUrl`, 새 탭).
>   파일 있는 다른 판본이 0개면 안내 한 줄만, `imslpOnlyCount == 0` 이면 영역 자체를 만들지 않는다.
> - 계약 검증: `WorkDetailEditionVolumeIntegrationTest`, `WorkDetailEditionKindFilterIntegrationTest`,
>   `WorkDetailPage.otherEditions.test.jsx`.
>
> **열린 문구 하나(기획 §10-10)** — "N개"의 숫자를 화면에 그대로 보일지는 designer·product-planner 의 결정이다.
> 계약은 숫자를 **주고**, 쓸지 말지는 화면이 정한다(0 인지 아닌지는 어느 쪽이든 필요하다).
| sameComposerWorks | WorkSummaryDTO[] | 같은 작곡가의 다른 공개 곡, download_count DESC, 최대 5 |
| favorited | boolean | **2026-09-20 신설(기획 05 §1-1, 화면정의 09 §6 S5).** 이 요청의 **로그인 주체**가 이 곡을 즐겨찾기했는가. **비로그인이면 언제나 `false`**(401 이 아니다 — 곡 상세는 공개 API 그대로다). 화면은 이 값으로 버튼을 그린다 |

> **왜 즐겨찾기 상태를 곡 상세에 싣나 (S5).** 따로 물으면 버튼이 **꺼짐 → 켜짐으로 깜빡인다**(화면정의 03 §3-6-2). 곡 상세 응답이 끝난 순간 버튼 상태도 확정돼 있어야 "다시 누를 필요가 없다"가 눈에 보인다(인수 조건 8-B 3).
> 공개 API 에 사용자별 값이 하나 붙는다는 뜻이라, **캐시 헤더를 붙이지 않는 것이 계약이다**(지금도 안 붙인다). 목록 응답(`WorkSummaryDTO`)에는 **넣지 않는다** — 곡 카드에 즐겨찾기 표시를 두지 않기로 확정했고(기획 05 §10 8-6, 인수 조건 8-G 5), 쓰는 곳 없는 필드를 먼저 두지 않는다(§0-7 의 `section` 판단과 같은 원칙).

```json
{ "success": true, "message": "성공", "data": {
  "id": 21, "titleKo": "월광 소나타", "titleOriginal": "Piano Sonata No.14, Op.27 No.2",
  "composer": { "id": 4, "nameKo": "베토벤", "nameOriginal": "Beethoven, Ludwig van" },
  "catalogNumbers": ["Op.27 No.2"], "level": "INTERMEDIATE", "status": "READY",
  "aliases": ["월광", "월광 소나타", "Moonlight Sonata"],
  "compositionYear": "1801", "musicalKey": "C-sharp minor", "movements": "3 movements", "movementPageGuide": "1악장 1쪽 · 2악장 6쪽 · 3악장 9쪽",
  "collectionGuide": "이 악보에는 3개 악장이 들어 있어요 — 흔히 아는 느린 선율은 1악장이에요. 3악장은 훨씬 어려워요(고급)",
  "imslpUrl": "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)",
  "composerImslpUrl": "https://imslp.org/wiki/Category:Beethoven,_Ludwig_van",
  "recommendedEdition": { "id": 301, "kind": "COMPLETE_SCORE", "scope": "COMPLETE", "movementNumber": null, "sectionLabel": null,
    "pageCount": 14, "fileSize": 1059957, "hasFile": true, "previewUrl": "/uploads/3f2a…c1.png",
    "publisher": "Vienna: Universal Edition, 1921. Plate U.E. 7000.", "publishYear": 1921, "plateNumber": "U.E. 7000", "editor": "Heinrich Schenker", "arranger": null, "scanner": "Unknown",
    "koreaCopyright": "FREE", "imslpCopyrightText": "Public Domain", "ccLicenseName": null, "ccAttribution": null,
    "imslpFileUrl": "https://imslp.org/wiki/Special:ImagefromIndex/00014",
    "downloadable": true, "largeFile": false, "downloadUrl": "/api/editions/301/download" },
  "otherEditions": [ ], "imslpOnlyCount": 88, "imslpCandidateEdition": null, "downloadableOtherCount": 0,
  "sameComposerWorks": [ WorkSummaryDTO… ], "favorited": false
} }
```

#### 3-3-2. `imslpCandidateEdition` — 못 주는 곡이 내보내는 IMSLP 링크 (2026-09-08 신설, 기획 §F3-7 · §10-5)

기획 §10-5 는 준비 중·이용 제한·확인 중인 곡의 대안 링크를 IMSLP **작품 페이지**에서 **우리가 고른 판본의 파일 페이지**로
바꿨다 — 작품 페이지로 보내면 사용자를 "판본 70개 중 고르기" 앞에 그대로 내려놓기 때문이다. 그런데 준비 중 곡은 추천이 없고
`otherEditions` 는 파일 있는 판본만 담으므로 **화면이 그 링크를 만들 재료가 없었다**(qa 4차 결함 9).

- **언제 값이 있나** — `recommendedEdition == null` 일 때만. 추천이 있으면(이용 제한·확인 중이어도) 화면은 추천 카드의
  `imslpFileUrl` 을 쓴다. 두 자리에서 같은 링크를 만들 수 있으면 어느 쪽을 쓸지 화면마다 갈린다.
- **무엇을 고르나** — `kind = COMPLETE_SCORE` 이고 `scope = COMPLETE` 인 판본 중 `imslp_download_count DESC NULLS LAST, id ASC`
  첫 번째. **파일 유무는 보지 않는다**(01 §3-3 추천 후보 규칙에서 파일 조건만 뺀 것 — 준비 중 곡에는 파일이 없다).
- **없으면 null** — 전체 악보·전곡 판본이 하나도 없으면(편곡뿐이거나 판본 0개) null 이고, 화면은 **그때만** `imslpUrl`(작품 페이지)로
  폴백한다(기획 §F3-7 "그 판본조차 없으면 작품 페이지"). 편곡을 "우리가 고른 판본" 이라며 내보내지 않는다 — 1차 범위는 피아노 독주다.
- **타입은 `EditionDTO`** — 화면 문구가 "IMSLP 에서 이 판본 보기 ↗ — 전체 악보 · 전곡 · 12쪽 · Breitkopf 1862" 라서
  kind·scope·pageCount·publisher 가 다 필요하다. 파일이 없으므로 `hasFile=false`, `downloadable=false`, `downloadUrl=null` —
  **이걸로 다운로드 버튼을 만들면 안 된다.**
- 계약 검증: `WorkDetailImslpCandidateIntegrationTest`.

### 3-4. `GET /api/editions/{id}/download` — PDF 다운로드

| 조건 | 결과 |
|---|---|
| 판본 없음 / 곡 숨김 | 404 `NOT_FOUND` |
| `pdfFileId == null` | 404 `NOT_FOUND` ("파일이 없는 판본이에요") |
| `koreaCopyright != FREE` | 403 `COPYRIGHT_RESTRICTED` |
| files 행은 있는데 바이트를 못 읽음 | 503 `FILE_UNAVAILABLE` — **다운로드 수 안 올림** |
| 같은 계정이 **같은 곡을 같은 순간에** 두 번 받음 | **둘 다 200** — 받은 악보 줄은 1개, 집계(`download_log`·두 `download_count`)는 **각각 다 든다**(아래 2026-09-21) |
| 정상 | 200, `Content-Type: application/pdf`, `Content-Length`, `Content-Disposition: attachment; filename="score-{editionId}.pdf"; filename*=UTF-8''{퍼센트인코딩 파일명}` |

**파일명 규칙(01 §9 8-9 확정, 2026-09-08 접미사 추가, 2026-09-08 괄호 생략 3조건 확정 — 기획 01 §12-1)**: `{작곡가 한글 표기 또는 원어 표기} - {한국어 대표 제목 또는 원어 제목}{ (대표 작품번호)}{ 편곡}{ N악장}.pdf`
- **빈 값은 원어로 대체한다** — 한국어 제목 없으면 원어 제목, 작곡가 한글 없으면 원어 표기. (수집·직접 등록으로 한글 값이 없는 곡도 다운로드가 열린다.)
- 대표 작품번호 = `sort_order = 0`.
- **괄호는 "제목이 말하지 않은 것"만 말한다 (2026-09-08 확정, 기획 §12-1).** 아래 3조건 중 하나라도 맞으면 **괄호를 통째로 생략**한다. 생략 결과에 **빈 괄호 `()` 는 어떤 경우에도 남지 않는다.**

  | # | 조건 | 예 | 시드 50곡 |
  |---|---|---|---|
  | ① | 작품번호가 **없다** (`work_catalog_number` 0행이거나 값이 공백뿐) | `사티 - 짐노페디.pdf` | 4곡 (#2·#14·#34·#35) |
  | ② | **파일명에 실제로 쓰인 제목**에 그 작품번호가 이미 들어 있다 | `쇼팽 - 녹턴 Op.9.pdf` | 12곡 (#11·#12·#23·#24·#25·#30·#39·#42·#43·#47·#48·#49) |
  | ③ | 작품번호 행이 **2개 이상**이거나, 대표 작품번호 값 안에 **여는 괄호 `(`** 가 있다 | `멘델스존 - 무언가 (전곡).pdf` | 5곡 (#28·#31·#32·#43·#50) |

  **②의 비교 규칙 (계약)**
  - 비교 대상은 **파일명에 실제로 쓰인 제목**이다 — `titleKo` 가 비어 원어 제목을 썼으면 원어 제목과 비교한다.
  - 제목·작품번호를 각각 `SearchNormalizer.normalize`(01_ERD §2 — 소문자화·발음구별기호 제거·문자/숫자 이외 제거)로 정규화한 뒤 **부분 문자열 포함**으로 판정한다. `Op.9` · `Op. 9` · `op9` 는 같다.
  - **경계 검사**: 정규화된 제목에서 찾은 위치의 **바로 뒤 문자가 숫자면 포함으로 보지 않는다.** 제목 `연습곡 Op.10`(`연습곡op10`)은 작품번호 `Op.1`(`op1`)을 담고 있는 것이 아니다. 다른 번호에 걸치면 사용자는 파일 이름만 보고 곡을 잘못 고른다.
  - **앞쪽에는 경계 검사를 두지 않는다.** 정규화가 구분자를 지우므로 매치 앞이 숫자인 것은 정상 상황이다 — #43 `즉흥곡 Op.90 (D.899)`(`즉흥곡op90d899`) + 대표 작품번호 `D.899`(`d899`) 가 그 예이고, 앞을 막으면 지워야 할 괄호가 남는다. 뒤만 막는 이유는 **번호를 늘리는 것은 뒤에 붙는 숫자뿐**이기 때문이다.
  - 정규화는 **비교에만** 쓴다. 출력 문자열은 원문 그대로다(악센트·괄호·대소문자 유지).
  - 금지문자 치환·공백 정리 전후 어느 값으로 비교해도 결과가 같다(정규화가 두 처리 결과를 같은 문자열로 만든다).
  - **생략하지 않는 경계(기획 §12-1-3)**: 제목 `녹턴 Op.9` + 대표 작품번호 `Op.9 No.2` → `쇼팽 - 녹턴 Op.9 (Op.9 No.2).pdf`. 뒤 괄호가 범위를 **좁혀 주므로** 남긴다. "겹치는 부분만 잘라 `(No.2)`" 같은 두 번째 규칙은 두지 않는다.

  **③의 구현 위치**: "여러 개"는 값 하나로 알 수 없으므로 **호출자(`DownloadMetaReader` / `WorkEntity`)** 가 판정해 `DownloadFileName.build(...)` 에 **생략이면 `null`** 을 넘긴다. `DownloadFileName` 의 시그니처는 바뀌지 않는다(①②·경계는 `DownloadFileName` 안에서 판정).

  **접미사와의 관계**: `편곡`·`N악장` 접미사는 **괄호 생략 여부와 무관하게 그대로 붙는다** → `쇼팽 - 녹턴 Op.9 편곡 2악장.pdf`.
  **길이 상한과의 관계**: 아래 204바이트 규칙은 **괄호 생략을 마친 이름**에 적용한다(생략은 이름을 짧게만 하므로 순서가 바뀌어도 결과가 같다).
  계약 검증: `DownloadFileNameTest`(①②·경계), `DownloadCatalogOmissionIntegrationTest`(③과 전 조건의 왕복).
- **범위·편곡 접미사 (2026-09-08 추가, 기획 §11-2 ③)** — 추천 판본이 전곡·전체 악보가 아니면 그 사실을 이름 끝에 붙인다. 파일은 사용자 컴퓨터에 남아 몇 주 뒤에 열리고, **그때 화면은 없고 파일 이름만 있다.**

  | 판본 | 붙이는 것 | 예 |
  |---|---|---|
  | `kind = ARRANGEMENT` | ` 편곡` | `리스트 - 사랑의 꿈 (S.541) 편곡.pdf` |
  | `scope = MOVEMENT` + `movementNumber = 2` | ` 2악장` | `베토벤 - 비창 소나타 (Op.13) 2악장.pdf` |
  | `scope = MOVEMENT` + `movementNumber = null` | ` 발췌` | `베토벤 - 비창 소나타 (Op.13) 발췌.pdf` |
  | 둘 다 | ` 편곡 {N}악장` (편곡이 먼저) | `… (Op.13) 편곡 2악장.pdf` |
  | `kind = COMPLETE_SCORE`/`PARTS` + `scope = COMPLETE` | 없음 (기존 이름 그대로) | `베토벤 - 월광 소나타 (Op.27 No.2).pdf` |

  - `kind = PARTS`(파트보)에는 접미사를 붙이지 않는다 — 기획 §3 F6-3 의 경고 목록에도 없다. 피아노 독주 1차 범위에서 파트보가 추천이 되는 경우가 없고, 새 문구를 계약에 넣으면 세 스택이 각자 번역한다.
  - 접미사는 **금지문자 치환·공백 정리 뒤에 붙인다**(우리가 만드는 문자열이라 치환 대상이 없다).
  - **길이 상한과의 우선순위**: 아래 204바이트 규칙을 적용할 때 **제목이 먼저 잘리고 접미사는 남는다**(작곡가·작품번호와 같은 등급). 접미사는 안내이기 이전에 "이 파일이 무엇인지"의 일부다. 제목을 다 지워도 넘칠 때만(규칙 2) 접미사도 잘릴 수 있다.
  - 계약 검증: `DownloadScopeSuffixIntegrationTest`, `DownloadFileNameTest`.
- 파일명 금지 문자 `\ / : * ? " < > |` 는 `-` 로, 연속 공백은 하나로, 앞뒤 공백 제거. 예: `모차르트 - 피아노 소나타 11번 A장조 (K.331).pdf`, 카탈로그 `K.331/300i` 였다면 `(K.331-300i)`.
- **길이 상한 — 확장자 포함 UTF-8 204바이트 (2026-09-08 추가, senior-dev — qa 3차 결함 6).** 조립·치환이 끝난 뒤 확장자를 뺀 부분이 **200바이트**를 넘으면 줄인다.
  1. **제목 부분만** 뒤에서 코드포인트 단위로 줄인다 — 곡을 식별하는 건 작곡가와 작품번호이므로 그 둘은 남긴다.
  2. 제목을 다 없애도 넘으면 이름 전체를 200바이트 이하가 되는 마지막 **문자 경계**에서 자른다(글자를 반토막 내면 U+FFFD 가 섞인 깨진 파일명이 된다).
  3. 말줄임표 같은 잘림 표시를 붙이지 않는다 — 파일명은 읽을 문장이 아니라 식별자이고, 특수문자를 늘리면 위의 금지문자 규칙과 다시 부딪힌다.
  4. ASCII 대체 파일명 `filename="score-{editionId}.pdf"` 는 그대로다(길이 문제가 없다).

  왜 필요한가: §0-6 의 세 상한(작곡가 100자·제목 300자·작품번호 100자)을 그대로 더하면 500자가 넘고, 한글은 UTF-8 3바이트라 1,000바이트를 넘긴다(qa 실측 413자·1,019바이트, `Content-Disposition` 2,923바이트). **ext4 는 파일명 255바이트, NTFS·APFS 는 255자**가 한계라 브라우저가 저장에 실패하거나 제멋대로 잘라 낸다(잘리는 규칙은 브라우저마다 다르다). 204바이트면 한계 안이면서 브라우저 중복 접미사 `" (1)"` 여유까지 남는다. 실데이터 최장 제목이 41자라 지금은 아무도 다치지 않지만, 수집이 가져오는 원어 제목은 길다. 계약 검증: `DownloadFileNameLimitIntegrationTest`.
- 성공 시 같은 요청 안에서 `edition.download_count`, `work.download_count` 를 1 올리고 `download_log` 1행 INSERT(바이트 확보 뒤, 스트리밍 전 짧은 트랜잭션).
- **2026-09-20 — 그 요청에 로그인 주체가 있으면 같은 트랜잭션에서 `user_work_download` 를 upsert 한다**(01_ERD §3-12, 기획 05 §3-1). 인증은 **선택**이다: 이 API 는 계속 `permitAll` 이고 **비로그인도 200 이며 집계에도 계속 든다**(인수 조건 8-D 3 — 이것이 회귀다). 토큰이 붙어 있으면 그 사람의 받은 악보에 남고, 아니면 아무 데도 남지 않는다. **사용감은 한 글자도 바뀌지 않는다** — 클릭 1번, 확인 창 없음, 로그인 요구 없음(기획 05 §3-1).
  - `HEAD` 는 지금처럼 **아무것도 쓰지 않는다** — `download_log` 도 `user_work_download` 도. 사전 확인이 받은 악보에 줄을 만들면 "받지도 않은 곡"이 선반에 선다.
  - **2026-09-21 — 겹친 요청도 200 이다**(senior-dev, qa 8차 결함 1). 같은 계정이 같은 곡을 **처음** 받는 두 요청이 겹치면 뒤에 온 요청의 선반 넣기가 `uk_user_work_download` 에 걸린다(큰 PDF 를 나란히 받으면 실제로 겹친다 — qa 실측 3/3). 그때의 계약은 **둘 다 200, 줄 1개, `download_log` 2행, `edition.download_count`·`work.download_count` 각 +2** 다. **이 API 의 본체는 파일이고 선반은 부산물이다** — 부산물의 경합이 사용자가 받으려던 파일을 막으면 안 되고, 선반 때문에 **집계까지 함께 롤백되면 안 된다**(그 요청은 실제로 파일을 받아 갔다). 구현 방향은 03 §26. 검증: `MyLibraryDownloadRaceIntegrationTest`.
  - 저작권 게이트가 먼저다: 403·404·503 은 기록 자체가 없다. **"이용 제한" 판본은 받은 악보의 "다시 받기"로도 이 문을 통과하지 못한다**(§10-3 은 그 판본의 `downloadUrl` 을 아예 만들지 않고, 만들어 불러도 여기서 403 이다 — 인수 조건 8-D 8).
- **이 API 가 판본 바이트를 얻는 유일한 공개 경로다(2026-09-07 확정, senior-dev).** 공용 파일 API(`GET /api/files`, `/api/files/paths`, `/api/files/{id}/content`)는 `ref_type=EDITION` 파일을 다루지 않는다 — 목록/경로에서 제외하고 `/content` 는 404. (순번 fileId 로 저작권 게이트를 우회할 수 있으면 §9-1 재배포 정책이 무의미해진다. 업로드 쪽은 `FileService.verifyOwnership` 이 이미 EDITION 을 막고 있다.)
- **`/uploads/{저장파일명}` 프록시도 판본 PDF 를 주지 않는다(2026-09-08 확정 — qa 3차 결함 1).** 그 경로는 **미리보기 PNG 전용**이고 판본 PDF 는 404 다. 표는 §0-4. "유일한 공개 경로"라고 써 놓고 게이트 없는 문을 하나 더 열어 두면 계약이 아니라 문서일 뿐이다.

**`HEAD /api/editions/{id}/download` — 받을 수 있는지 먼저 묻기 (2026-09-07 추가, senior-dev)**

| 항목 | 계약 |
|---|---|
| 상태코드 | GET 과 **완전히 동일**(200 / 403 `COPYRIGHT_RESTRICTED` / 404 `NOT_FOUND` / 503 `FILE_UNAVAILABLE`) |
| 헤더 | 200 이면 GET 과 같은 `Content-Type`·`Content-Length`·`Content-Disposition` |
| 본문 | 없음 |
| 부수효과 | **없음 — `download_count` 를 올리지 않고 `download_log` 도 남기지 않는다** |

왜 여는가: 화면의 `PDF 받기` 는 `<a href download>` 라(§7) 실패를 감지할 수 없다. 화면 정의서 03 §동작·통신 상태가 요구하는
"다운로드 시작 실패 → InlineAlert danger" 를 구현하려면 실패를 알 수단이 하나는 있어야 한다(qa 결함 D5).

왜 카운터를 올리면 안 되는가: Spring 은 HEAD 를 GET 핸들러로 보낸다. 그대로 두면 화면이 한 번 받을 때마다 HEAD+GET 로 **2** 가 올라
인기곡 정렬이 왜곡된다. 구현은 컨트롤러에서 `HEAD` 일 때 `recordDownload` 를 건너뛰면 된다
(별도 `@RequestMapping(method = HEAD)` 를 추가하면 GET 매핑과 겹쳐 모호해질 수 있다).
저작권 게이트·숨김 곡 규칙은 GET 과 같아야 한다 — HEAD 로 존재 여부를 캐낼 수 있으면 게이트가 반만 있는 셈이다.
**`SecurityConfig` 의 공개 화이트리스트가 메서드를 GET 으로 못 박고 있으면 HEAD 도 함께 허용해야 한다**(지금은 HEAD 가 401).

**2026-09-10 추가 — §3-5 ~ §3-8 공통**: 쿼리에 `section`(§0-7, 생략 시 `PIANO`)이 붙는다.
- §3-5·§3-6: **"공개 곡이 1개 이상"의 뜻이 "그 구분에서 공개 곡이 1개 이상"** 으로 좁아지고, `workCount` 도 그 구분 기준이다. 바이올린 곡만 있는 작곡가는 피아노 구분의 목록에 나오지 않는다(기획 04 §5).
- §3-7: `workCount` 만 그 구분 기준. **404 조건은 바뀌지 않는다** — 작곡가 자체는 구분에 속하지 않으므로 그 구분에 곡이 0개여도 200 이다.
- §3-8: 곡 목록과 `unfilteredTotal` 이 그 구분 기준. 같은 작곡가라도 구분마다 다른 곡 목록을 갖는다.
- §3-1 의 작곡가 카드(`composers[]`)도 같은 규칙이라 **카드의 `workCount` 와 작곡가 상세의 곡 수가 계속 일치한다**(기획 04 §5, 01 §11-4 의 뜻 유지).

### 3-5. `GET /api/composers` — 작곡가 전체 목록
공개(숨김 아님) 곡이 1개 이상인 작곡가만. 정렬 `name_ko IS NULL` 뒤로 → `name_ko ASC` → `name_original ASC`. 페이지 없음.
```json
{ "data": { "total": 25, "composers": [ { "id": 1, "nameKo": "그리그", "nameOriginal": "Grieg, Edvard", "birthYear": 1843, "deathYear": 1907, "workCount": 1 } ] } }
```

### 3-6. `GET /api/composers/featured?limit=8`
공개 곡 수 많은 순(동률 name_ko 순) `limit`(기본 8, 최대 20). `data: ComposerCardDTO[]`(`id, nameKo, nameOriginal, workCount`).

### 3-7. `GET /api/composers/{id}` — 작곡가 상세
404 없는 id. 공개 곡 0개여도 200(화면이 빈 상태 표시).
```json
{ "data": { "id": 9, "nameKo": "쇼팽", "nameOriginal": "Chopin, Frédéric", "birthYear": 1810, "deathYear": 1849, "nationality": "폴란드",
  "aliases": ["프레데리크 쇼팽", "쇼팡", "Chopin"], "imslpUrl": "https://imslp.org/wiki/Category:Chopin,_Frédéric", "workCount": 9 } }
```

### 3-8. `GET /api/composers/{id}/works` — 작곡가의 곡

| 쿼리 | 값 |
|---|---|
| sort | `downloads`(기본: download_count DESC, id ASC) / `opus`(대표 작품번호 `sort_key ASC NULLS LAST`, 그 다음 title_original ASC). 그 외 값 400 |
| level / pages / downloadable / page / size | §3-1 과 동일 |

응답: `{ "unfilteredTotal": 24, "works": PageResponse<WorkSummaryDTO> }`. 404 없는 작곡가. 숨김 곡 제외.

### 3-9. `GET /api/works/recent?ids=23,21,22&section=PIANO` — 최근 본 곡의 "지금 정보" (2026-09-20 신설)

브라우저가 저장한 **곡 id 목록**을 주면 **지금의 요약**을 돌려준다. 기획 05 §4-2 의 두 요구가 이 한 문으로 닫힌다: "보이는 정보는 저장한 순간의 것이 아니라 **지금의 것**", "내려간 곡은 **보이지 않는다**(눌러서 404 를 만나지 않는다)".

| 항목 | 계약 |
|---|---|
| 인증 | **없다(공개).** 최근 본 곡은 로그인과 무관하다(기획 05 §0-4). `/api/works/**` GET permitAll 그대로 |
| `ids` | 쉼표로 이은 곡 id. **요청한 순서가 곧 응답 순서**(브라우저가 든 "최근에 본 순"을 서버가 다시 정하지 않는다) |
| 개수 | **최대 10.** 넘치면 **앞 10개만** 쓴다(400 이 아니다 — `limit`·`size` 상한과 같은 관용, §3-2·§0-4) |
| 걸러지는 것 | 없는 곡 · **숨김 곡** · **다른 구분의 곡**. 조용히 빠진다(오류 아님) — 그래서 **응답 길이가 요청보다 짧을 수 있다** |
| 이상한 값 | 숫자가 아닌 토큰·빈 토큰·중복은 **무시**한다(중복은 첫 번째만). 브라우저 저장은 오염될 수 있고 화면 규칙이 "조용히 숨김"이다 |
| `ids` 가 비었거나 전부 걸러짐 | **200 + `[]`** (화면은 영역 자체를 만들지 않는다 — 01_홈 상태표) |
| `ids` 파라미터 자체가 없음 | 400 `MISSING_PARAMETER`(§0-2 표준). 화면은 저장된 곡이 0개면 **요청을 보내지 않는다** |
| `section` | §0-7 그대로(생략 시 `PIANO`, 정의되지 않은 값 400) |

응답 `data: WorkSummaryDTO[]` — `matchedAlias` 는 항상 null, `scopeNote` 는 §2-2-1 규칙대로.

```
GET /api/works/recent?ids=23,21,999&section=PIANO
→ data: [ {id:23 …}, {id:21 …} ]      // 999 는 없는 곡이라 빠졌다. 순서는 요청 그대로
```

- **왜 `POST` 가 아닌가**: 읽기이고, 부수효과가 없고, 10개 id 는 주소에 들어간다. 그래서 캐시·재시도·로그가 전부 평범하게 동작한다.
- **왜 화면이 id 만 저장하나**: 제목·뱃지를 저장하면 "저장한 순간의 정보"가 화면에 남아 관리자가 고친 제목·열린 곡이 반영되지 않는다(인수 조건 8-E 7·8). 저장 규칙은 `03_기술결정.md` §23.
- 계약 검증: `RecentWorksApiIntegrationTest`, `HomePage.recentWorks.test.jsx`.

---

## 4. 관리자 API 상세 — 작곡가·곡

모든 관리 API: 비로그인 401, USER 403(§0-2). 아래 표에는 그 외 에러만 적는다.

### 4-1. `GET /api/admin/dashboard`
```json
{ "data": {
  "totalWorks": 312, "readyWorks": 241, "preparingWorks": 38, "needsWorkWorks": 57,
  "needsRecommendationReviewWorks": 12,
  "unknownCopyrightEditions": 19, "monthlyDownloads": 1204,
  "latestJob": CrawlJobDTO | null,
  "activeJob":  CrawlJobDTO | null
} }
```
- `totalWorks` 는 숨김 포함 전체. `readyWorks/preparingWorks` 는 01_ERD §4 규칙(숨김 포함). `needsWorkWorks` 는 보완 필요 규칙. `monthlyDownloads` 는 Asia/Seoul 기준 이번 달 1일 00:00 이후 `download_log` 수.
- **`needsWorkWorks` 는 01_ERD §4 "보완 필요 계산" 을 그대로 쓴다 — `COPYRIGHT_JUDGMENT` 포함**(2026-09-08, 기획 §11-1). 곡 목록 필터 `status=NEEDS_WORK`(§4-6) · 곡 상세 `missing[]`(§4-7) 과 **반드시 같은 규칙**이다. 세 곳이 한 함수를 공유해야 하고, 한 곳에서 사라진 곡이 다른 곳에 남으면 결함이다.
- **자동 판정 되돌리기(§5-12) 뒤 카드가 어떻게 움직이는가** (2026-09-08 명시): `readyWorks`·`needsWorkWorks`·`unknownCopyrightEditions` 는 **자동 판정 실행 전 값으로 돌아온다**. 다만 `preparingWorks` 는 돌아오지 않는다 — 되돌리기는 추천 지정을 유지하므로 그 곡의 상태가 `PREPARING` 이 아니라 `UNKNOWN` 이기 때문이다. 그래서 그 곡을 관리자 일감에 남기는 일은 `needsWorkWorks` 가 맡는다(그게 `COPYRIGHT_JUDGMENT` 를 추가한 이유다).
- **`monthlyDownloads` 는 판본을 지워도 줄지 않는다**(§5-5, 2026-09-08). 이 숫자는 "이번 달에 몇 번 받아갔나" 라는 **일어난 사건의 수**이고, 같은 달 수치가 나중에 줄어들면 지표가 아니다. 곡 삭제(§4-9)로만 줄어든다.
- **`needsRecommendationReviewWorks`(2026-09-08 신설, 기획 §F6-4 · §8-17 · §10-8)** — 화면 문구 "추천 판본 확인 필요 N곡".
  `recommended_edition_id IS NOT NULL AND recommended_edition_reviewed = false` 인 곡 수, **숨김 곡 제외**.
  숨김을 빼는 이유: 이 값은 공개(출시) 기준 §8-17 "추천 판본 미검수 0곡" 을 재는 지표이고, 숨긴 곡은 공개 대상이 아니다
  (숨김을 포함하는 `totalWorks` 와 다른 이유다). 같은 모집단을 §4-6 `status=NEEDS_RECOMMENDATION_REVIEW` 목록이 쓴다 —
  **카드 숫자와 목록이 어긋나면 관리자는 어느 쪽도 믿지 않는다.** 규칙·API 는 §5-6-1.
  `needsWorkWorks`(보완 필요)와 **섞지 않는다** — 보완 필요는 "열어 주려면 뭐가 남았나"(기획 §11-1)이고 미검수 곡은 이미 열려 있다.
- `latestJob` = 가장 최근 생성 작업, `activeJob` = RUNNING/PAUSED 작업(없으면 null).

### 4-2. `GET /api/admin/composers?q=&missingKo=&page=&size=`
- `q`: 한글·원어·별칭 부분 일치(정규화). `missingKo=true`: name_ko NULL 만. 정렬 `name_original ASC`. 기본 20개 페이지.
- `size`: 선택. 기본 20, **최대 200**(초과 요청은 200 으로 자르고, 1 미만이면 기본 20). 관리 화면의 **작곡가 필터 select**(`/admin/works`, `/admin/copyright`)와 곡 편집의 **작곡가 선택 목록**이 `?size=200` 으로 한 번에 채운다 — 20개 고정 페이지로는 select 를 채울 수 없어 추가했다(2026-09-07, senior-dev). 공개 `GET /api/composers`(§3-5)는 "공개 곡이 1개 이상인 작곡가"만이라 관리 화면 선택지로 쓸 수 없다.
- `PageResponse<AdminComposerDTO>`: `{ id, nameKo, nameOriginal, birthYear, deathYear, workCount, updatedAt }`

### 4-3. `GET /api/admin/composers/{id}`
`AdminComposerDetailDTO`: `{ id, nameKo, nameOriginal, aliases: string[], birthYear, deathYear, nationality, imslpUrl, workCount, createdAt, updatedAt }`. 404.

### 4-4. `POST /api/admin/composers` → 201 / `PUT /api/admin/composers/{id}` → 200
요청 `ComposerSaveDTO`
```json
{ "nameKo": "베토벤", "nameOriginal": "Beethoven, Ludwig van", "aliases": ["루트비히 판 베토벤"], "birthYear": 1770, "deathYear": 1827, "nationality": "독일", "imslpUrl": "https://imslp.org/wiki/Category:Beethoven,_Ludwig_van" }
```
| 검증 | 결과 |
|---|---|
| nameKo 공백 | 400 `VALIDATION_ERROR` field `nameKo` "한글 표기를 입력해 주세요" |
| nameOriginal 공백 | 400 field `nameOriginal` |
| birthYear/deathYear 범위 밖(1000~2100) | 400 |
| deathYear < birthYear | 400 field `deathYear` "몰년이 생년보다 앞서요" |
| imslpUrl 이 `https://imslp.org/` 로 시작하지 않음 | 400 field `imslpUrl` |
| nameOriginal 정규화 값이 다른 작곡가와 같음 | 409 `DUPLICATE_RESOURCE` "이미 등록된 작곡가예요" — 화면의 "보기" 링크는 `GET /api/admin/composers?q={nameOriginal}` 로 찾는다(ErrorResponse 에 data 슬롯이 없으므로) |
| aliases 안 정규화 중복 | 조용히 하나만 저장 |
응답 `data`: `AdminComposerDetailDTO`. `aliases` 는 요청 목록으로 **전체 교체**.

### 4-5. `DELETE /api/admin/composers/{id}` → 204
곡이 1개라도 있으면 400 `BUSINESS_RULE_VIOLATION` message `"곡 12개가 있어 삭제할 수 없어요"`(숫자 포함).

### 4-6. `GET /api/admin/works?q=&status=&composerId=&level=&page=`
| 쿼리 | 값 |
|---|---|
| q | 사용자 검색과 같은 규칙(§3-1 2번), 숨김 포함, 정렬은 아래 |
| status | `READY \| PREPARING \| RESTRICTED \| UNKNOWN \| NEEDS_WORK \| NEEDS_RECOMMENDATION_REVIEW \| HIDDEN` 중 하나. `NEEDS_WORK` 는 01_ERD §4 보완 필요 계산(**`COPYRIGHT_JUDGMENT` 포함**, 2026-09-08) — 다른 값과 달리 `WorkStatus` 가 아니라서, `UNKNOWN` 곡이 `NEEDS_WORK` 목록에도 나오는 것이 정상이다. `NEEDS_RECOMMENDATION_REVIEW`(2026-09-08 신설)도 `WorkStatus` 가 아니며 §4-1 카드와 **같은 모집단**이다(추천 있음 + 미검수 + 숨김 제외) |
| composerId | long |
| level | `BEGINNER \| ELEMENTARY \| INTERMEDIATE \| ADVANCED \| NONE`(=미정) |
| size | 선택. **기본 20, 최대 200**(초과는 200 으로 자름, 1 미만은 기본 20) — §4-2 와 같은 규칙 |
정렬 `updated_at DESC, id DESC`. 응답 `{ "unfilteredTotal": 312, "works": PageResponse<AdminWorkSummaryDTO> }`

`AdminWorkSummaryDTO`: `{ id, titleKo, titleOriginal, composer: ComposerRefDTO, catalogNumbers, level, editionCount, hasRecommended, recommendationReviewed, recommendationSource, status, needsWork, hidden, updatedAt }`
- `recommendationReviewed`(boolean, 2026-09-08 신설) — 목록의 "미검수" 표시. `hasRecommended == false` 인 곡은 항상 `false` 이고 표시하지 않는다(검수할 대상이 없다).
- **`recommendationSource`**(`"AUTO" | "ADMIN" | null`, **2026-09-21 신설** — 기획 06 §2-2, 화면정의 05 화면 D): 지금 추천을 **누가 골랐나**. 곡 목록의 추천 열이 `★` 하나로 말하던 것을 넷으로 나눈다.

  | `hasRecommended` | `recommendationSource` | 화면 |
  |---|---|---|
  | false | **항상 null** | `–` |
  | true | `AUTO` | `★ 자동` |
  | true | `ADMIN` | `★ 사람` |
  | true | **null** | `★ 기록 없음` (실데이터 42곡 — 기획 06 §5) |

  값은 그 곡 `work_recommendation_log` 의 **맨 위 줄이 `ASSIGNED` 일 때 그 줄의 `source`** 다. 줄이 없으면 null.
  - **"기록 없음" 을 enum 상수로 만들지 않는다.** `NO_RECORD` 같은 값을 넣으면 **DB 에 저장될 수 있는 값의 집합과 응답 값의 집합이 갈려**, 다음 사람이 "이 값은 왜 로그 테이블에 없지?" 를 매번 다시 판단한다. 없음은 **null 하나로** 말한다(§5-6-2 의 `warnings` 가 빈 배열 하나로 말하는 것과 같은 원칙).
  - **미검수 표시(`recommendationReviewed`)와 겹치지 않는다** — 다른 질문의 답이다. 미검수 = "아무도 미리보기를 열어 보지 않았다", 자동/사람 = "이 판본을 고른 것이 기계인가 사람인가". 자동 지정된 뒤 관리자가 미리보기만 확인한 곡은 `AUTO` + `reviewed=true` 로 남는다(기획 06 §2-2).
  - **새 필터를 만들지 않는다**(기획 8-E 4). `status` 값에 추가되는 것이 없다.
  - 계약 검증: `RecommendationHistoryApiIntegrationTest`.

### 4-7. `GET /api/admin/works/{id}` — 곡 상세(관리)
`AdminWorkDetailDTO`
```json
{ "data": {
  "id": 21, "titleKo": "월광 소나타", "titleOriginal": "Piano Sonata No.14, Op.27 No.2",
  "composer": { "id": 4, "nameKo": "베토벤", "nameOriginal": "Beethoven, Ludwig van", "deathYear": 1827, "nameKoMissing": false },
  "catalogNumbers": ["Op.27 No.2"], "aliases": ["월광", "Moonlight Sonata"], "level": "INTERMEDIATE",
  "compositionYear": "1801", "musicalKey": "C-sharp minor", "movements": "3 movements", "movementPageGuide": null,
  "collectionGuide": "이 악보에는 3개 악장이 들어 있어요 — 흔히 아는 느린 선율은 1악장이에요",
  "imslpUrl": "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)",
  "hidden": false, "hiddenReason": null,
  "status": "READY", "needsWork": false, "missing": [],
  "recommendedEditionId": 301, "candidateEditionId": null, "recommendationReviewed": false,
  "recommendation": { "current": { … }, "historyCount": 3, "history": [ … ], "hasMore": false },
  "downloadCount": 312, "hasDownloadHistory": true,
  "editions": [ AdminEditionDTO… ],
  "createdAt": "2026-09-06T05:02:00Z", "updatedAt": "2026-09-06T05:02:00Z"
} }
```
- `missing`: `TITLE_KO \| ALIAS \| LEVEL \| RECOMMENDED_EDITION \| COPYRIGHT_JUDGMENT` 중 해당 값. **순서는 이 나열 순서로 고정**(화면이 "빠진 것: …" 으로 그대로 이어 붙인다).
  - `COPYRIGHT_JUDGMENT` = **추천 판본이 있고 그 판본의 `koreaCopyright = UNKNOWN`** (2026-09-08 추가, 기획 §11-1). `RESTRICTED` 는 사람이 내린 결론이라 **세지 않는다**. 규칙 원본은 01_ERD §4.
  - `RECOMMENDED_EDITION` 과 `COPYRIGHT_JUDGMENT` 는 동시에 나올 수 없다(앞은 추천 없음, 뒤는 추천 있음).
- `collectionGuide`(string|null): **2026-09-08 계약 보완(senior-dev, backend-dev 지적).** §4-8 요청에는 있는데 이 응답에 없었다.
  PUT 은 **전체 교체**이고 관리 화면은 이 응답으로 폼을 채워 그대로 되돌려 보내므로, 응답에 없으면 폼이 값을 들고 있을 수 없어
  **관리자가 곡을 한 번 저장하는 것만으로 시드가 넣은 38곡(01_ERD §6)의 수록곡 안내가 조용히 지워진다.**
  지워지면 복구도 안 된다 — 백필은 `seed_load(COLLECTION_GUIDE, imslp_url)` 기록이 있어 다시 채우지 않는다.
  그리고 이 값은 검색 항목 `scopeNote.COLLECTION` 의 판정 근거라(§2-2-1), 사용자 화면의 줄까지 함께 사라진다.
  고치는 방향은 **응답에 필드를 넣는 것**이지 "PUT 에서 빠지면 유지"가 아니다 — 후자는 값을 지울 방법을 없애고
  전체 교체 계약에 필드별 예외를 만든다. 계약 검증: `AdminWorkCollectionGuideIntegrationTest`.
- `candidateEditionId`: 01_ERD §3-3 규칙(추천 없을 때만 값, 있으면 null).
- `recommendationReviewed`(boolean, 2026-09-08 신설): 지금 추천 판본이 사람 눈을 통과했는가. 규칙·API 는 §5-6-1.
- **`recommendation`(객체, 2026-09-21 신설)**: "이 판본을 고른 이유" + "바뀐 이력" 최근 5줄. 전체 모양은 **§4-7-2**. 추천이 없거나 기록이 없는 곡에도 **객체는 항상 있다**(`current: null`, `history: []`) — 빈 상태를 두 가지로 만들지 않는다.
- `editions` 정렬: 추천 → 추천 후보 → 파일 있음(imslp_download_count DESC) → 파일 없음(imslp_download_count DESC) → id.
- 404 없는 id(숨김 곡은 관리자에게 보임).

- **추천·후보의 단일 기준은 곡 쪽 필드(`recommendedEditionId` / `candidateEditionId`)** 다(2026-09-07 senior-dev). 판본의 `isRecommended`/`isCandidate` 는 같은 사실의 파생값이라 **정렬 근거와 단건 응답용**으로만 둔다 — 관리 화면(06-A 판본 목록)은 곡 쪽 id 로 표시를 결정한다. 이유: 추천은 곡의 속성이고(§5-6 응답도 `{workId, previousEditionId, editionId}` 로 곡 기준이다), id 비교면 "한 곡에 추천 하나" 가 구조적으로 지켜진다(배열 플래그는 둘이 true 가 될 수 있다). 두 출처를 섞으면 추천 지정 직후 목록을 다시 받기 전까지 표시가 어긋난다.
- JSON 이름은 **`isRecommended`/`isCandidate` 하나뿐**이다 — `recommended`/`candidate` 를 같이 내보내지 않는다(계약 밖 필드가 있으면 스택마다 다른 이름에 붙는다).

`AdminEditionDTO` = `EditionDTO` + `{ imslpFileId, imslpOriginalFileName, imslpDescription, imslpLicenseCode, imslpDownloadCount, pdfFileId, previewFileId, copyrightNote, copyrightJudgedAt, copyrightJudgedBy, fileFetchStatus, fileFetchError, fileFetchedAt, downloadCount, isRecommended, isCandidate, createdAt, updatedAt }`

#### 4-7-2. `recommendation` — "이 판본을 고른 이유" 와 "바뀐 이력" (2026-09-21 신설)

§4-7 응답에 **객체 하나**로 들어간다. 화면정의 06 A-1 이 그리는 세 모양(자동 / 사람 / 기록 없음)과 접힘 이력이 전부 여기서 나온다.

```json
"recommendation": {
  "current": {
    "id": 91, "decidedAt": "2026-09-20T02:02:00Z",
    "source": "ADMIN", "action": "ASSIGNED", "decidedByNickname": "창희",
    "edition": { "editionId": 302, "kind": "COMPLETE_SCORE", "scope": "COMPLETE", "movementNumber": null,
                 "publisher": "Peters", "editor": "Köhler", "publishYear": 1880 },
    "previousEdition": { "editionId": 301, "kind": "COMPLETE_SCORE", "scope": "COMPLETE", "movementNumber": null,
                 "publisher": "Breitkopf", "editor": "Lebert", "publishYear": 1862 },
    "auto": null,
    "reason": "NOT_THIS_WORK", "note": "앞 추천은 관현악 총보였음", "clearedReason": null
  },
  "historyCount": 3,
  "history": [ /* 최신순 최대 5줄. history[0] 은 current 와 같은 줄이다 */ ],
  "hasMore": false
}
```

자동으로 지정된 줄은 `auto` 가 채워지고 `reason`·`note`·`decidedByNickname` 이 전부 null 이다:
```json
{ "id": 40, "decidedAt": "2026-09-07T05:20:00Z", "source": "AUTO", "action": "ASSIGNED", "decidedByNickname": null,
  "edition": { "editionId": 301, "kind": "COMPLETE_SCORE", "scope": "COMPLETE", "movementNumber": null,
               "publisher": "Breitkopf", "editor": "Lebert", "publishYear": 1862 },
  "previousEdition": null,
  "auto": { "rule": "MOST_IMSLP_DOWNLOADS", "imslpDownloadCount": 1204, "candidateCount": 3, "rank": 1 },
  "reason": null, "note": null, "clearedReason": null }
```

추천이 빠진 줄:
```json
{ "id": 92, "decidedAt": "2026-09-21T01:10:00Z", "source": "ADMIN", "action": "CLEARED", "decidedByNickname": "창희",
  "edition": null,
  "previousEdition": { "editionId": null, "kind": "COMPLETE_SCORE", "scope": "COMPLETE", "movementNumber": null,
                       "publisher": "Peters", "editor": "Köhler", "publishYear": 1880 },
  "auto": null, "reason": null, "note": null, "clearedReason": "EDITION_DELETED" }
```

| 필드 | 타입 | 규칙 |
|---|---|---|
| `current` | `RecommendationLogDTO` \| **null** | **지금 추천을 정한 줄.** 맨 위 줄이 `ASSIGNED` 면 그 줄, `CLEARED` 면 **null**, 줄이 없으면 **null** |
| `historyCount` | int | 전체 줄 수. 화면 접힘 헤더 `▸ 바뀐 이력 (N)`. **2 미만이면 화면이 헤더를 만들지 않는다**(화면정의 06 A-1) |
| `history` | 배열 | 최신순 **최대 5줄**. `historyCount = 0` 이면 **빈 배열**(null 이 아니다) |
| `hasMore` | boolean | `historyCount > history.length`. true 면 화면이 `더 보기`(§4-7-1)를 띄운다 |

`RecommendationLogDTO`

| 필드 | 타입 | 규칙 |
|---|---|---|
| `id` | long | 이력 줄 id(화면 key) |
| `decidedAt` | ISO8601 UTC | 화면은 `2026-09-20 11:02`(§0-4 관리 화면 형식) |
| `source` | `AUTO` \| `ADMIN` | 화면 1단: `자동` / `{닉네임} 님` |
| `action` | `ASSIGNED` \| `CLEARED` | |
| `decidedByNickname` | string \| null | `source = ADMIN` 일 때만. **닉네임이다 — 로그인 아이디를 내려보내지 않는다**(화면정의 06 A-1 "관리자 이름 표기"). 닉네임이 비어 있으면 null → 화면 `관리자` |
| `edition` | `RecommendationEditionRefDTO` \| null | `ASSIGNED` 면 값, `CLEARED` 면 null |
| `previousEdition` | 〃 \| null | **null = 처음 지정** → 화면 `처음 지정한 추천이에요` |
| `auto` | 객체 \| null | `source = AUTO` 일 때만 |
| `reason` | enum \| null | `ADMIN` + `ASSIGNED` 일 때만 |
| `note` | string \| null | 메모. 없으면 null(빈 문자열을 내려보내지 않는다) |
| `clearedReason` | `EDITION_DELETED` \| `EDITION_FILE_REMOVED` \| null | `CLEARED` 일 때만 |

`RecommendationEditionRefDTO` — **그때의 표기 스냅샷**이다. 지금 판본을 다시 읽은 값이 아니다(01_ERD §3-13).

| 필드 | 규칙 |
|---|---|
| `editionId` | long \| **null** — **판본이 삭제됐으면 null.** 나머지 6개는 스냅샷이라 그대로 남는다 |
| `kind` / `scope` / `movementNumber` | 그때의 종류·포함 범위 |
| `publisher` / `editor` / `publishYear` | 그때의 출판사 / 편집자 / 출판 연도. 없던 값은 null(화면이 `–`) |

`auto` 객체 — **지정 시점 값으로 박아 둔 것**이다(기획 06 §1-3). 나중에 IMSLP 다운로드 수가 바뀌어도 **이 값은 바뀌지 않는다.**

| 필드 | 규칙 |
|---|---|
| `rule` | `MOST_IMSLP_DOWNLOADS` — 화면이 규칙 한 줄을 그린다 |
| `imslpDownloadCount` | int \| **null**. **null = "IMSLP 다운로드 수가 적혀 있지 않은 판본이에요"** (화면정의 06 A-1 셋째 줄 둘째 갈래) |
| `candidateCount` | int. **`1` 이면 화면이 "1위" 라고 쓰지 않는다** → `고를 수 있는 판본이 이것 하나뿐이었어요`(8-A 3) |
| `rank` | int. 지금 규칙은 언제나 1이지만 값으로 말한다(8-A 2 가 후보 수와 순위를 함께 요구한다) |

**`candidateCount == 1` 과 `imslpDownloadCount == null` 은 서로 다른 분기다** — 화면이 둘을 따로 그린다.
후보가 1개이면서 다운로드 수도 없는 판본이면 `고를 수 있는 판본이 이것 하나뿐이었어요` 쪽이 이긴다(화면정의 06 A-1 표).

**세 상태를 화면이 가르는 법** (A-1 의 3가지 모양)

| 곡의 상태 | 응답 | 화면 |
|---|---|---|
| 추천 있음 + 근거 있음(자동) | `recommendedEditionId != null`, `current.source = AUTO` | A-1 (A) |
| 추천 있음 + 근거 있음(사람) | 〃 `current.source = ADMIN` | A-1 (B) |
| **추천 있음 + 기록 없음**(실데이터 42곡) | `recommendedEditionId != null`, `current = null`, `historyCount = 0` | A-1 (C) `기록이 없어요` |
| 추천 없음 + 이력 있음 | `recommendedEditionId = null`, `current = null`, `historyCount ≥ 1` | 상자 없음 + `▸ 바뀐 이력 (N)` 줄만(화면정의 06 A-0) |
| 추천 없음 + 이력 없음 | `recommendedEditionId = null`, `current = null`, `historyCount = 0` | 아무것도 없음 |

- **`current` 가 `history[0]` 과 같은 줄인 것은 의도된 중복이다.** 화면이 두 자리(상자 / 이력 맨 위 `지금` pill)에 같은 줄을 그리는데, `history` 가 5줄 상한이라 "현재 줄이 반드시 들어 있다" 를 화면이 계산으로 보장하게 하면 상한을 바꾸는 날 조용히 깨진다. 서버가 한 곳에서 만드는 **같은 객체**라 둘이 어긋날 수 없다.
- **`recommendation` 을 객체로 묶는 이유**: 상세 응답 루트에 필드 넷을 흩뿌리면 "이 넷이 한 덩어리" 라는 사실이 계약에서 사라지고, 화면마다 다른 조합으로 읽게 된다.
- **백필이 없다 (기획 06 §5).** 기능 도입 전에 지정된 곡은 `current = null` · `history = []` 가 **정상**이다. 서버가 지금 값으로 근거를 지어내면 8-E 1 이 결함으로 잡는다.
- 계약 검증: `RecommendationLogIntegrationTest` · `RecommendationAutoEvidenceIntegrationTest` · `RecommendationHistoryApiIntegrationTest`.

### 4-7-1. `GET /api/admin/works/{workId}/recommendation-history` — 바뀐 이력 전부 (2026-09-21 신설)

화면정의 06 A-1 "바뀐 이력" 의 **`더 보기`** 가 부른다. §4-7 은 최근 **5줄**만 싣고, 이 API 가 **전부**를 준다.

**왜 곡 상세를 다시 받지 않나.** 곡 편집(`/admin/works/:id`)은 **작성 중인 폼**이다. 이력 한 줄을 더 보자고 곡 상세를 다시 받으면
사용자가 치고 있던 제목·별칭이 서버 값으로 덮인다. 이력은 곡 상세와 **수명이 다른 읽기**라 자기 주소를 갖는다.

| 조건 | 결과 |
|---|---|
| 비로그인 / USER | 401 / 403 |
| 곡 없음 | 404 `NOT_FOUND` |
| 정상 | 200 (아래) |

```json
{ "data": { "workId": 21, "historyCount": 12, "history": [ RecommendationLogDTO, … ] } }
```

- 정렬 `decidedAt DESC, id DESC` — **최신이 맨 위**(8-D 1).
- **상한 200줄.** 넘으면 최신 200줄만 싣고 `historyCount` 는 **실제 전체 수**를 그대로 말한다(화면이 "전부는 아니다" 를 알 수 있어야 한다).
  근거: 기획 미결 6-3 은 "전부 남기고 화면에는 최근 5줄 + 더 보기" 였고, **남기는 것과 한 번에 보내는 것은 다른 문제**다.
  42곡 규모에서는 200 에 닿지 않지만, 기획 06 §4-2 의 대량 재지정이 열리면 곡당 줄이 늘 수 있다.
  상한 없는 배열은 언젠가 응답 하나가 커지고, 그때는 화면이 아니라 서버가 멈춘다. 200 은 §4-2 페이지 상한과 같은 숫자를 쓴다.
- **보관 기간은 두지 않는다 (미결 6-3 결론 — senior-dev).** 오래된 줄을 지우는 배치를 만들지 않는다.
  근거: ⑴ 근거를 남기겠다고 시작한 일이 **설명 없이 근거를 지우는 배치**로 끝나면 안 된다 ⑵ 42곡 × 드문 변경이라 양이 작다
  ⑶ 보관 기간을 두면 "왜 그 줄이 없지?" 라는, 지금 우리가 없애려는 바로 그 질문이 되돌아온다. 줄이 사라지는 유일한 때는 **곡 삭제**다(01_ERD §7).

### 4-8. `POST /api/admin/works` → 201 / `PUT /api/admin/works/{id}` → 200
요청 `WorkSaveDTO`
```json
{ "composerId": 4, "titleKo": "월광 소나타", "titleOriginal": "Piano Sonata No.14, Op.27 No.2",
  "catalogNumbers": ["Op.27 No.2"], "aliases": ["월광", "Moonlight Sonata"], "level": "INTERMEDIATE",
  "compositionYear": "1801", "musicalKey": "C-sharp minor", "movements": "3 movements", "movementPageGuide": "1악장 1쪽 · 2악장 6쪽",
  "collectionGuide": "이 악보에는 3개 악장이 들어 있어요 — 흔히 아는 느린 선율은 1악장이에요",
  "imslpUrl": "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)", "hidden": false }
```
| 검증 | 결과 |
|---|---|
| composerId 없음/존재하지 않음 | 400 field `composerId` / 404 |
| titleOriginal 공백 | 400 field `titleOriginal` "원어 제목을 입력해 주세요" |
| titleKo 공백 | **허용**(보완 필요가 됨). 저장 시 null 로 정규화 |
| level 은 null 허용 | |
| collectionGuide 공백/누락 | **허용**(null 로 저장). 최대 500자, 넘으면 400 field `collectionGuide`(§0-6 길이 표에 함께 넣는다). 시드가 채우는 값이지만 수집으로 들어온 곡·오타 수정을 위해 관리자도 편집할 수 있다(2026-09-08 추가) |
| imslpUrl 이 다른 곡과 같음(정규화 비교) | 409 `DUPLICATE_RESOURCE` |
| aliases/catalogNumbers 안 정규화 중복 | 하나만 저장. 다른 곡과 겹치는 별칭은 허용(경고는 §4-10) |
| hidden=true 로 바꾸면 | hiddenReason 는 null(관리자 숨김). 수집 숨김 곡을 hidden=false 로 바꾸면 hiddenReason 도 null |
응답 `data`: `AdminWorkDetailDTO`. 별칭·작품번호는 **전체 교체**(기존 SEED/IMSLP 별칭도 목록에 없으면 삭제; 목록에 남아 있으면 source 유지).
`collectionGuide` 도 전체 교체 대상이며 **응답에 그대로 실려 돌아온다**(§4-7) — 화면은 상세 응답으로 폼을 채우고 그 폼을 되돌려 보내면 값이 보존된다.
**규칙(2026-09-08 추가): 이 문서의 저장 요청 DTO 에 있는 필드는 같은 리소스의 상세 응답 DTO 에도 있어야 한다.**
전체 교체 방식에서 응답에 없는 필드는 화면이 왕복시킬 수 없어 **저장할 때마다 지워진다**. §4-3/§4-4(작곡가)·§5-4/§5-2(판본)는 대조 완료 — 빠진 필드 없음.

**대조는 두 겹이다 (2026-09-08 senior-dev 보강).** 응답 DTO 에 필드가 있어도 **화면 폼이 그 값을 들고 있다가 되돌려 보내지 않으면** 결과는 똑같다 —
`collectionGuide` 가 그랬다. 서버는 §4-7 로 값을 주는데 관리 화면(05-E)이 그 필드를 읽지도 싣지도 않아,
관리자가 **제목만 고쳐 저장해도** 시드가 넣은 38곡(01_ERD §6)의 수록곡 안내가 null 로 덮이고
백필(`seed_load(COLLECTION_GUIDE, imslp_url)` 기록)로도 복구되지 않으며, 검색의 `scopeNote.COLLECTION`(§2-2-1) 줄까지 함께 사라진다.
그래서 대조 항목은 **① 요청 DTO ⊆ 상세 응답 DTO, ② 요청 DTO ⊆ 화면 폼이 싣는 키** 둘 다다.
② 의 검증은 화면 테스트가 **저장 본문 전체를 `toEqual` 로 고정**하는 방식으로 한다(키 하나만 골라 보면 새로 생긴 누락을 못 잡는다) —
`frontend/src/pages/admin/WorkFormPage.test.jsx` 의 "수정 저장 → PUT 본문(WorkSaveDTO)".
2026-09-08 전수 대조 결과: **작곡가(§4-4 ↔ ComposerFormPage) · 판본(§5-2·5-3 ↔ EditionFormModal) 은 누락 없음**, 곡(§4-8)의 `collectionGuide` 하나뿐이었다.

`collectionGuide` 는 **화면에 입력 칸을 둔다**(숨은 필드로 왕복만 시키지 않는다). 근거: (1) §4-8 이 공백·누락을 허용해 **지울 수 있는 값**으로 정의했는데
숨은 필드면 그 분기를 유일한 클라이언트에서 실행할 수 없다, (2) 수집으로 들어온 곡은 이 값이 비어 있어 **채울 수단**이 필요하다,
(3) 사용자 화면에 그대로 보이는 **사람이 쓴 문장**이라 오타 수정이 배포·마이그레이션 없이 가능해야 한다(§2-2-1 의 "예외는 `collectionGuide` 하나다").
화면 정의(05-E)에 이 칸이 아직 없다 — designer 확정 대상.

### 4-9. `DELETE /api/admin/works/{id}` → 204
판본·파일·다운로드 기록 함께 삭제(01_ERD §7). 확인 문구용 정보(판본 수, 다운로드 기록 유무)는 §4-7 응답의 `editions.length`, `hasDownloadHistory` 로 화면이 미리 안다.
**로그를 지우는 삭제는 여기뿐이다** — 판본 삭제(§5-5)는 로그를 남긴다. 곡이 사라지면 `download_log.work_id`(NOT NULL)가 가리킬 곳이 없고, "무언가를 누가 받았다" 만 남은 행은 어떤 질문에도 답하지 못한다.

### 4-10. `GET /api/admin/works/aliases/overlap?alias=녹턴&excludeWorkId=23`
`alias` 필수. 정규화 값이 같은 별칭을 가진 **다른 곡** 수. 응답 `{ "alias": "녹턴", "overlapCount": 20 }`. (곡 저장과 별개로 칩 추가 시 즉시 호출)

---

## 5. 관리자 API 상세 — 판본·저작권

### 5-1. `POST /api/admin/edition-files` — PDF 업로드 (multipart, 필드명 `file`)
서버가 하는 일: PDF 검증(Content-Type `application/pdf` 또는 확장자 `.pdf` + 매직바이트 `%PDF`) → 저장 전략으로 저장(files: ref_type=EDITION, ref_id=0, usage=ATTACHMENT) → PDFBox 로 쪽수 + 첫 페이지 PNG(files: THUMBNAIL, ref_id=0) → 응답.

| 결과 | 상태 |
|---|---|
| 정상 | 201 `{ "fileId": 501, "previewFileId": 502, "fileName": "beethoven_op27-2.pdf", "fileSize": 1059957, "pageCount": 14, "previewUrl": "/uploads/…png" }` |
| PDF 아님 | 400 `BUSINESS_RULE_VIOLATION` "PDF 파일만 올릴 수 있어요" (저장 안 함) |
| 100MB 초과 | 413 `PAYLOAD_TOO_LARGE` "100MB 이하만 올릴 수 있어요" |
| 미리보기 생성 실패(손상 PDF 등) | 201 이되 `previewFileId: null, previewUrl: null`, `pageCount` 는 읽힌 값 또는 null — 업로드 자체는 성공 |
| 파일 비어 있음 | 400 |
| **`file` 파트 자체가 없음** | 400 `MISSING_PARAMETER` "파일을 선택해 주세요" (2026-09-07 추가 — 현재 500) |
| **multipart 요청이 아님**(Content-Type·본문 없음, 또는 `application/json` 으로 호출) | 400 `MISSING_PARAMETER` "파일을 선택해 주세요" — 위와 같은 응답(§0-2 의 `MultipartException` 줄. 2026-09-07 추가 — 현재 500) |

업로드된 파일은 판본 저장(§5-2/5-3)에서 `fileId` 로 연결한다. 연결되지 않은 채 24시간 지나면 orphan 배치가 지운다.

### 5-2. `POST /api/admin/works/{workId}/editions` → 201 / 5-3. `PUT /api/admin/editions/{id}` → 200
요청 `EditionSaveDTO`
```json
{ "fileId": 501, "previewFileId": 502,
  "kind": "COMPLETE_SCORE", "scope": "COMPLETE", "movementNumber": null, "pageCount": 14,
  "publisher": "Breitkopf & Härtel", "publishYear": 1862, "plateNumber": "B.&H. 1234", "editor": "Sigmund Lebert", "arranger": null, "scanner": null,
  "imslpFileUrl": "https://imslp.org/wiki/Special:ImagefromIndex/00014", "imslpCopyrightText": "Public Domain",
  "koreaCopyright": "FREE", "copyrightNote": "작곡가 1827 사망, 편집자 Lebert 1884 사망 → 사후 70년 경과",
  "ccLicenseName": null, "ccAttribution": null }
```
| 검증 | 결과 |
|---|---|
| kind / scope / koreaCopyright 누락 | 400 `VALIDATION_ERROR` |
| scope=MOVEMENT 인데 movementNumber 없음(또는 < 1) | 400 field `movementNumber` "악장 번호를 입력해 주세요" |
| koreaCopyright ∈ {FREE, RESTRICTED} 인데 copyrightNote 공백 | 400 field `copyrightNote` "판정 근거를 적어 주세요" |
| fileId 가 없는 파일 / ref_type≠EDITION / 다른 판본에 이미 연결됨 | 400 `BUSINESS_RULE_VIOLATION` |
| fileId 와 previewFileId 는 §5-1 응답을 그대로 넘긴다. `fileId: null` 이면 파일 없는 판본(정보만) | |
| imslpFileUrl 형식 | `https://imslp.org/` 시작 아니면 400 |
| PUT 에서 fileId 가 기존과 다르면 | 교체: 새 파일 연결, 옛 files 행+바이트 삭제, pageCount 는 요청값 우선(없으면 새 파일 값) |
| PUT 에서 koreaCopyright 가 바뀌면 | `copyrightJudgedAt/By` 갱신 |
| **PUT 에서 추천 판본의 `fileId` 를 `null` 로 바꾸면** | **곡의 추천을 함께 해제한다**(`recommendedEditionId = null`) — §5-5 와 같은 정리. 2026-09-07 추가 |
| 404 | workId / editionId 없음 |
응답 `data`: `AdminEditionDTO` + `workStatus`(저장 후 곡 상태 — "다운로드가 열렸어요" 토스트 판단용). 새 판본 기본 판정은 요청값(화면 기본 UNKNOWN).

**`imslpLicenseCode` 는 요청 필드가 아니라 서버가 `imslpCopyrightText` 에서 도출한다** (2026-09-07 senior-dev).
`imslpLicenseCode = LicenseCode.fromText(imslpCopyrightText)`(빈 값이면 null) — 등록·수정 모두. 요청 본문에 `imslpLicenseCode`
가 와도 무시한다. 근거: 지금은 **수집이 만든 판본에만** 코드가 있고 관리자가 손으로 넣은 판본은 늘 null 이라, 같은
`imslpCopyrightText: "Public Domain"` 인 두 판본이 화면(§4-7 `AdminEditionDTO.imslpLicenseCode`)에서 다르게 보인다.
코드는 원문의 **정규화 캐시**일 뿐이므로 원문을 저장하는 그 자리에서 함께 채우는 것이 맞고, 그래야 자동 판정(§5-11)이
`imslpLicenseCode` 하나만 보고 판단할 수 있다(같은 규칙이 판본의 출처에 따라 다르게 동작하면 안 된다).

**추천 판본에서 파일을 떼면 추천도 함께 풀린다** (2026-09-07 senior-dev, qa 2차 결함). 지금은 `fileId: null` 로 저장해도
`recommendedEditionId` 가 그대로 남아 **"파일 없는 추천 판본"** 이라는 상태가 만들어진다. 이 상태는 §5-6 이 애초에 금지한 것이다
(같은 판본을 다시 추천으로 지정하면 400 "파일이 없어 추천으로 지정할 수 없어요" 로 막힌다) — **API 로는 만들 수 없다고 선언한 상태를
다른 API 가 만들어 두는 모순**이고, 관리 화면은 `missing` 에 `RECOMMENDED_EDITION` 이 없으니 "보완 필요" 로도 잡아주지 않아
관리자가 곡이 준비된 줄 안다(공개 쪽은 `WorkStatus` 계산이 `pdf_file_id` 를 보므로 PREPARING·다운로드 404 로 안전하다).

- **고르는 방향은 "정리"** 다. 400 으로 거절하는 방향은 버린다 — 추천을 푸는 별도 API 가 없어서(§5-6 은 지정만 한다)
  관리자가 잘못 올린 파일을 떼려면 다른 판본을 먼저 추천으로 세워야 하고, 판본이 하나뿐이면 빠져나갈 길이 없다.
- **`missing` 계산을 고치는 방향도 아니다.** `missing` 은 곡 필드의 파생 표시일 뿐이고(01_ERD §4), 원인은 저장 로직이
  남긴 잘못된 상태다. 표시만 고치면 DB 에는 여전히 금지된 상태가 남는다.
- 결과: 저장 응답의 `workStatus` 는 `PREPARING`, §4-7 의 `recommendedEditionId` 는 `null`, `missing` 에 `RECOMMENDED_EDITION`,
  `needsWork: true`, 그 판본의 `isRecommended` 는 false, `candidateEditionId` 는 01_ERD §3-3 규칙으로 다시 계산된다.
- 파일을 **다른 파일로 교체**하는 경우(§5-3 의 교체 규칙)는 추천을 유지한다 — 파일 없는 상태가 되지 않는다.

### 5-4. `GET /api/admin/editions/{id}` → `AdminEditionDTO` (+ `workId`, `workStatus`). 404.

### 5-5. `DELETE /api/admin/editions/{id}` → 204
추천이면 곡 추천 해제. 파일·미리보기 삭제. **다운로드 기록(`download_log`)은 지우지 않는다** — `edition_id` 만 비운다(2026-09-08 개정).

> **2026-09-21 — 추천 판본을 지우면 근거 한 줄이 쌓인다 (기획 8-B 7).** 그 판본이 추천이었으면 해제 **전에** `work_recommendation_log` 에
> `action = CLEARED` · `source = ADMIN` · `decided_by_*` = 토큰 주체 · `cleared_reason = EDITION_DELETED` · `previousEdition` = **지워질 판본의 그때 표기 스냅샷** 한 줄을 넣는다(01_ERD §3-13·§7).
> 그 뒤 로그 행의 `edition_id`·`previous_edition_id` 는 `download_log` 와 같은 방식으로 **NULL 로 비우고 행과 스냅샷 6개는 남긴다** —
> 스냅샷이 없으면 이력 줄이 `? → 추천 없음` 이 되어, 관리자가 이 화면에서 던지는 바로 그 질문("어제까지 추천이 있었는데 왜 없지?")에 답하지 못한다.
> 추천이 **아닌** 판본을 지울 때는 로그를 쌓지 않는다(추천이 정해진 순간이 아니다). 다만 **과거 이력 줄이 그 판본을 가리키고 있었다면** 그 줄의 id 도 NULL 이 된다(스냅샷은 남는다).
> 계약 검증: `RecommendationLogIntegrationTest`.

> **§5-3 으로 추천 판본의 파일을 떼도 추천이 풀린다 (이미 하던 동작 — 2026-09-21 계약으로 명시).** 그 경로도 `CLEARED` 한 줄을 쌓고
> `cleared_reason = EDITION_FILE_REMOVED` 다. 기획·화면정의가 언급하지 않은 경로이지만 **코드에 실재한다** — 계약에서 빠뜨리면 그 길로 추천이 빠진 곡만 이력이 비어
> 8-B 7 이 곡마다 다르게 판정된다. 화면 문구는 designer 확인 대상(§8).

> **왜 바꿨나 (qa 3차 결함 8).** 4번 받은 판본을 지우면 `work.download_count` 는 4로 남는데 `download_log` 는 0행이 됐다.
> 그래서 **인기곡 정렬(§3-2, `download_count`)** 과 **대시보드 `monthlyDownloads`(§4-1, 로그 수)** 가 같은 달의 같은 사건을 다르게 셌다(실측 13 → 9).
> **판정: `download_log` 가 원장(사실)이고 `work.download_count` 는 그 합계 캐시다. 그리고 다운로드는 _곡_ 단위 사건이다.**
> 사용자가 받은 것은 "월광 소나타" 이지 "판본 #301" 이 아니다 — 판본은 우리가 운영상 교체하는 파일일 뿐이라, 나쁜 스캔을 지우고 다시 넣었다고
> 곡의 인기가 0 이 되는 것은 사실이 아니다. 운영 지표도 과거를 다시 쓰면 안 된다(이번 달 수치가 나중에 **줄어드는** 지표는 지표가 아니다).
> 그래서 판본 삭제는 로그를 남기고, **곡 삭제(§4-9)만 로그를 지운다** — 로그는 곡에 속한 사실이고 `download_log.work_id` 는 NOT NULL 이다.
> `work.download_count` 도 판본 삭제로 줄이지 않는다. §4-7 의 `hasDownloadHistory` 는 계속 true(그 곡은 실제로 받아진 적이 있다).
> **이 판정이 고치지 _않는_ 것**: "판본이 지워져 못 받는 곡이 인기곡 1위"(결함 4-B)는 인기곡 목록의 **자격 조건** 문제다 —
> 역사를 지워서 순위를 고치면 대시보드가 다시 틀어진다. 계약 검증: `DownloadHistoryRetentionIntegrationTest`.

### 5-6. `PUT /api/admin/works/{workId}/recommended-edition`

> **2026-09-21 전면 개정 (기획 06 §1-4·§3-1·§3-2·§3-3, 화면정의 06 A-3).** 지정이 **사유를 고르는 자리를 한 번 거친다.**
> 바뀐 것 넷: ⑴ `reason` **필수**("기타"면 `note` 필수) ⑵ `reviewed` 체크로 **검수를 그 자리에서 완료** ⑶ `warnings` **3종 → 6종**
> ⑷ 지정이 성공하면 **근거 한 줄이 쌓인다**(01_ERD §3-13). 바뀌지 않은 것: 파일 없는 판본은 400 으로 막히고, **경고는 막지 않는다.**

요청 `RecommendEditionRequest`
```json
{ "editionId": 302, "reason": "NOT_THIS_WORK", "note": "앞 추천은 관현악 총보였음", "reviewed": true }
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `editionId` | long | **필수** | 그 곡의 판본이어야 한다 |
| `reason` | enum `RecommendationReason` | **필수** | 6개 중 하나(아래 표). 없거나 모르는 값이면 저장하지 않는다 |
| `note` | string ≤ **300** | 선택 | **`reason = OTHER` 면 필수.** 공백만 있으면 없는 것으로 본다(null 로 정규화) |
| `reviewed` | boolean | 선택(기본 **false**) | `true` 면 지정과 동시에 검수 완료(§5-6-1 을 따로 부르지 않는다). **기본이 false 인 것이 계약이다** — 기본을 true 로 두면 "추천 판본 확인 필요" 가 뜻 없는 항상 0 이 된다(기획 06 §3-3) |

**`reason` 6가지** — 선언 순서가 곧 화면 나열 순서다(화면정의 06 A-3 ③). **문구는 계약에 없다** — 서버는 코드만 주고
화면이 문구를 갖는다(`RecommendWarning` 과 같은 방식). 근거: 문구는 designer 가 계속 다듬고(기획 06 미결 6-2 는 아직 사람 확정 전),
서버가 문자열을 박으면 문구 한 글자 수정이 배포가 된다.

| 코드 | 화면 라벨(화면정의 06 A-3 ③ — **화면이 갖는다**) |
|---|---|
| `NOT_THIS_WORK` | 앞 추천이 이 곡의 악보가 아니었어요 |
| `BETTER_READABILITY` | 이 판본이 더 읽기 좋아요 |
| `BETTER_FOR_LEARNERS` | 이 판본의 편집·운지가 배우는 사람에게 맞아요 |
| `PREVIOUS_UNAVAILABLE` | 앞 추천은 지금 받을 수 없어요 |
| `COVERS_WHOLE_WORK` | 이 판본이 곡 전체를 담고 있어요 |
| `OTHER` | 기타 — 직접 적기 (**메모 필수**) |

- **첫 지정(그 곡에 지금 추천이 없는 곡)에서 화면은 `NOT_THIS_WORK`·`PREVIOUS_UNAVAILABLE` 두 개를 감춘다**(앞 추천이 없는데 "앞 추천이 …" 를 고르면 근거가 거짓이 된다). **계약은 6개 그대로다** — 서버는 첫 지정에서도 여섯 값을 전부 받는다. 화면만 줄인다(화면정의 06 A-3 ③).

| 조건 | 결과 |
|---|---|
| 비로그인 | 401 `NOT_AUTHENTICATED` |
| USER 토큰 | 403 `ACCESS_DENIED` |
| 곡 없음 / 판본 없음 / **그 곡의 판본이 아님** | 404 `NOT_FOUND` |
| `editionId` 누락 | 400 `VALIDATION_ERROR` field `editionId` `"판본을 선택해 주세요"` |
| **`reason` 누락** | 400 `VALIDATION_ERROR` field `reason` `"왜 이 판본을 골랐는지 골라 주세요"` |
| **`reason = OTHER` 인데 `note` 가 비었다** | 400 `VALIDATION_ERROR` field `note` `"왜 이 판본을 골랐는지 적어 주세요"` |
| `note` 300자 초과 | 400 `VALIDATION_ERROR` field `note` `"300자를 넘을 수 없어요"` (§0-6) |
| 판본에 파일 없음 | 400 `BUSINESS_RULE_VIOLATION` `"파일이 없어 추천으로 지정할 수 없어요"` |
| 정상 | 200 (아래) |

- **검증에 걸리면 추천도 근거도 하나도 바뀌지 않는다** (기획 8-B 2·4). 400 을 받은 뒤 곡 상세를 다시 읽으면 `recommendedEditionId` 가 그대로다.
- **검증 순서는 404 → 400 이다** — 없는 곡/판본에 사유 오류를 말하면 "그 곡이 있다" 는 사실이 새어 나간다(§0-3 과 같은 원칙).

응답 200 `RecommendResult`
```json
{ "workId": 21, "previousEditionId": 301, "editionId": 302, "workStatus": "UNKNOWN",
  "recommendationReviewed": true,
  "warnings": ["WORK_BECOMES_CLOSED", "HAS_DOWNLOAD_HISTORY", "NOT_DOWNLOADABLE", "PARTIAL_SCOPE"] }
```
- `recommendationReviewed`(boolean, **2026-09-21 신설**): 요청의 `reviewed` 가 그대로 반영된 결과. 화면이 A-1 의 미검수 줄을 다시 조회 없이 그린다.
- `warnings`: **6종 전부**(§5-6-2 표). **지정 직전 상태로 계산한다** — `WORK_BECOMES_CLOSED` 는 "바꾸면 닫힌다" 는 말이라 지정한 뒤에 세면 이미 닫혀 있어 영영 뜨지 않는다. 같은 이유로 §5-6-2(예고)와 **같은 함수**를 쓴다.
- 같은 판본을 다시 지정해도 200 이다. 다만 **추천이 실제로 바뀌지 않았으므로 근거 줄은 쌓지 않고**(01_ERD §7) 검수 상태도 유지한다(`reviewed: true` 를 보내면 검수만 켜진다).

**근거 한 줄이 쌓인다 (01_ERD §3-13).** 성공한 지정은 같은 트랜잭션에서 `work_recommendation_log` 에 한 줄을 넣는다:
`source = ADMIN`, `action = ASSIGNED`, `decided_by_*` = **토큰 주체**(요청 본문의 사용자 id 를 쓰지 않는다 — 컨벤션 §4-1),
`edition_*` = 새 판본의 그때 표기 스냅샷, `previous_*` = 바뀌기 직전 추천의 스냅샷(없으면 전부 NULL = 처음 지정), `reason`·`note`.

- **지정의 결과가 사용자에게 드러나는 곳은 그대로 세 군데다**: 검색 항목 `scopeNote`(§2-2-1) · 곡 상세 `recommendedEdition`(§2-3) · 다운로드 파일명 접미사(§3-4). **근거는 그 어디에도 실리지 않는다**(기획 06 §6 — 1차 제외 확정).
- 계약 검증: `RecommendEditionWarningIntegrationTest`(경고) · `RecommendReasonIntegrationTest`(사유·메모·검수) · `RecommendationLogIntegrationTest`(근거 기록).

### 5-6-2. `GET /api/admin/works/{workId}/recommended-edition/preview?editionId=302` — 바꾸기 전 경고 예고 (2026-09-21 신설)

화면정의 06 A-3 의 "이 판본으로 바꾸기" 패널은 **바꾸기 전에** 경고를 읽는다. 그런데 경고 ④⑤는 판본의 성질이 아니라
**이 변경의 결과**라, 판본 행의 값만으로는 판정할 수 없다(곡의 지금 상태 + 그 곡의 다운로드 기록 유무가 필요하다).

**왜 화면이 직접 계산하지 않나.** 그러면 경고 규칙이 서버(§5-6 응답)와 화면 두 곳에 살게 되고, **예고와 결과가 어긋날 수 있다.**
같은 문제를 §5-8 `autoJudgeSkipReason` 에서 이미 겪었고 "예고는 결과와 같은 함수여야 한다" 로 닫았다(03 §16).
추천 변경은 사용자가 받는 파일을 바꾸는 동작이라 예고가 틀리면 안 된다.

| 쿼리 | 값 |
|---|---|
| `editionId` | **필수**. 그 곡의 판본 |

| 조건 | 결과 |
|---|---|
| 비로그인 / USER | 401 / 403 |
| `editionId` 누락 | 400 `MISSING_PARAMETER` |
| 곡·판본 없음 / 그 곡의 판본이 아님 | 404 `NOT_FOUND` |
| 판본에 파일 없음 | **200** — 예고는 읽기다. 막는 것은 §5-6 의 일이고, 읽기를 게이트로 쓰면 "왜 못 보는지" 를 화면이 또 설명해야 한다 |
| 정상 | 200 (아래) |

```json
{ "data": { "workId": 21, "editionId": 302,
  "warnings": ["WORK_BECOMES_CLOSED", "HAS_DOWNLOAD_HISTORY", "NOT_DOWNLOADABLE", "PARTIAL_SCOPE"] } }
```

**`warnings` — 경고 6종 (2026-09-21, 기획 06 §3-2 / 화면정의 06 A-3 ②-1)**

| 순서 | 값 | 조건 | 묶음(화면) | 화면 문구 — **화면이 갖는다** |
|:---:|---|---|---|---|
| ④ | `WORK_BECOMES_CLOSED` | **지금 이 곡이 `READY`** 인데 지정할 판본의 `koreaCopyright != FREE` | 바뀌면 생기는 일 | 지금 받을 수 있는 곡이에요 — 바꾸면 이 곡의 다운로드가 닫혀요 |
| ⑤ | `HAS_DOWNLOAD_HISTORY` | **이 곡에 다운로드 기록이 있다**(§4-7 `hasDownloadHistory`) | 바뀌면 생기는 일 | 이미 이 곡을 받아 간 사람이 있어요 — … |
| ① | `NOT_DOWNLOADABLE` | 지정할 판본의 `koreaCopyright != FREE` | 이 판본은 이런 판본이에요 | 이 판본은 저작권이 '…' 이라 사용자에게 다운로드가 열리지 않아요 |
| ⑥ | `PARTS` | `kind = PARTS` | 〃 | 이 판본은 한 악기 파트만 담고 있어요 — … |
| ② | `ARRANGEMENT` | `kind = ARRANGEMENT` | 〃 | 이 판본은 편곡이에요 — … |
| ③ | `PARTIAL_SCOPE` | `scope = MOVEMENT` | 〃 | 이 판본은 {N}악장만 들어 있어요 — … |

- **고정 순서** `WORK_BECOMES_CLOSED → HAS_DOWNLOAD_HISTORY → NOT_DOWNLOADABLE → PARTS → ARRANGEMENT → PARTIAL_SCOPE`
  (= 화면정의 06 A-3 ② "순서 고정 ④ → ⑤ → ① → ⑥ → ② → ③"). 해당 없으면 **빈 배열 `[]`**, null 이 아니다.
  - 기존 3종의 상대 순서(`NOT_DOWNLOADABLE → ARRANGEMENT → PARTIAL_SCOPE`)는 **바뀌지 않는다**. `PARTS` 가 사이에 끼지만 `PARTS`·`ARRANGEMENT` 는 `kind` 가 하나뿐이라 **동시에 성립할 수 없다**.
- **한 번에 최대 5줄이다. 6줄은 불가능하다** — ②와 ⑥이 상호 배타이기 때문. 화면은 이 상한을 전제로 그린다(화면정의 06 A-3 ②).
- **④가 뜨면 ①은 반드시 뜬다** — ④의 조건이 ①의 조건을 포함한다(④ ⊂ ①). **둘 다 보낸다**(기획 8-C 2): ①은 판본의 성질, ④는 이 변경의 결과이고, 관리자가 판단에 쓰는 것은 후자다.
- **곡이 지금도 닫혀 있으면 ④는 뜨지 않는다** — 닫힌 것을 닫을 수는 없다.
- **⑤에 사람 수·건수를 담지 않는다** — 판단은 0이냐 1 이상이냐에서 갈린다(기획 06 §3-2). 기록이 없는 곡에는 이 값이 **아예 없다**.
- **⑥은 실데이터에 사례가 없을 수 있다**(피아노 독주곡에 파트보는 거의 없다). 그래도 계약에 두는 이유는 ②③과 정확히 같은 종류의 사고이기 때문이다 — 사용자가 기대한 것과 다른 것을 받는다.
- 계약 검증: `RecommendChangePreviewIntegrationTest`.

### 5-6-1. `PUT /api/admin/works/{workId}/recommended-edition/review` — 추천 판본 확인함 (2026-09-08 신설)

요청 `{ "reviewed": true }` → 200 `AdminWorkDetailDTO`(§4-7). `false` 로 되돌리기도 같은 API.

| 조건 | 결과 |
|---|---|
| 곡 없음 | 404 `NOT_FOUND` |
| 추천 판본이 없는 곡에 `reviewed: true` | 400 `VALIDATION_ERROR` — 확인할 대상이 없다 |
| 정상 | 200 `AdminWorkDetailDTO`(화면이 다시 조회하지 않아도 되게 곡 상세 그대로) |

**왜 이 계약이 필요한가 (기획 §F6-4 · §8-17 · §10-8, qa 4차 결함 6).** 자동 추천 지정은 "전체 악보 · 전곡" 만 보므로
**관현악 총보가 추천이 될 수 있다**(파반느·사계·짐노페디·어린이 차지·꽃노래·엔터테이너 — 큐레이션 50곡의 실제 사례).
그래서 기획은 "추천 판본의 미리보기를 열어 피아노 악보가 맞는지 확인 → **확인함**" 을 두고 **공개(출시) 기준**에
"추천 판본 미검수 0곡" 을 넣었는데, 계약·코드·화면정의 어디에도 이 개념이 없었다 — **출시를 막는 조건인데 셀 수 없는 숫자**였다.

- **저장**(01_ERD `work`): `recommended_edition_reviewed BOOLEAN NOT NULL DEFAULT FALSE`. "누가·언제" 는 남기지 않는다 —
  이 값이 답하는 질문은 "지금 추천이 사람 눈을 통과했나" 하나뿐이고, 그 답은 추천이 바뀌는 순간 무효가 되므로 이력이 아니라 상태다.
- **false 로 돌아가는 때**: 추천이 **바뀌거나 해제되면**(§5-6, §5-11 자동 지정 포함) 자동으로 false. 확인한 것은
  "그 판본" 이 아니라 "지금 추천" 이다. 곡의 다른 필드 수정(§4-8)으로는 바뀌지 않는다.
- 보이는 곳: §4-1 카드 `needsRecommendationReviewWorks` · §4-6 필터 `status=NEEDS_RECOMMENDATION_REVIEW` ·
  §4-6 목록/§4-7 상세의 `recommendationReviewed`.
- **2026-09-21 개정 (기획 06 §3-3, `01` §15-3)** — 검수를 켜는 길이 둘이 됐다:
  ⑴ **§5-6 의 `reviewed: true`** — 관리자가 미리보기를 보고 고른 그 자리에서 함께 완료. 체크하지 않으면(기본 false) 지금과 같이 미검수로 남는다.
  ⑵ **이 API** — 자동 지정된 추천을 확인하는 유일한 길(그 곡은 지정 요청 자체가 없었다). **없앨 수 없다.**
  두 길이 같은 컬럼 하나를 쓴다. 지정과 검수를 한 번의 왕복으로 끝내는 것이 ⑴ 의 전부이고, 계약이 갈라지지 않는다.
- **`reviewed: false`(확인 해제) 경로는 유지한다 (2026-09-21 판정 — 화면정의 06 D4 회신, senior-dev).**
  화면정의는 "되돌릴 일은 다시 지정으로 해결된다" 며 버튼을 두지 않으려 했는데, **이번 개정으로 그 근거가 성립하지 않는다**:
  ⓐ 같은 판본을 다시 지정하면 추천이 바뀌지 않아 **검수가 유지된다**(01_ERD §7) — 즉 다시 지정으로는 풀 수 없다.
  ⓑ 다른 판본을 지정해 푸는 것은 **추천을 실제로 바꾸는 일**이고, 이제 **사유가 필수**라 있지도 않은 사유를 남기게 된다 —
  근거를 남기려고 만든 기능이 **거짓 근거**를 만들면 안 된다.
  그래서 계약도 화면의 `확인 해제` 버튼도 **그대로 둔다**. designer 에게 화면정의 A-1 의 그 줄을 되돌려 준다(§8).
- 계약 검증: `RecommendationReviewIntegrationTest`.

### 5-7. `POST /api/admin/editions/{id}/fetch-file` — IMSLP 파일 받아오기(비동기)
| 조건 | 결과 |
|---|---|
| 이미 파일 있음 | 400 "이미 파일이 있는 판본이에요" |
| `imslpFileId` 없음 | 400 "IMSLP 파일 정보가 없어 받아올 수 없어요" |
| 라이선스 코드가 PD/CC0/CC_BY/CC_BY_SA 가 아님(OTHER/NC/ND/null) | 400 "재배포가 허용되지 않는 표기라 받아올 수 없어요"(01 §9-1) |
| 이미 QUEUED/FETCHING | 409 `DUPLICATE_RESOURCE` |
| 정상 | **202** `{ "editionId": 305, "fileFetchStatus": "QUEUED" }` |
이후 화면은 `GET /api/admin/editions/{id}` 를 3초 간격으로 폴링해 `fileFetchStatus` 가 null(성공, `hasFile: true`) 또는 `FAILED`(`fileFetchError`) 가 될 때까지 기다린다. 수집 작업이 진행 중이면 그 뒤에 처리된다(03 §3: IMSLP 커넥션은 항상 1개).

**받아오기의 수명주기 계약 (2026-09-07 추가, senior-dev)** — 요청은 202 로 끝나고 실제 작업은 나중에 다른 스레드에서 끝난다. 그 사이에 벌어지는 두 가지를 계약으로 못 박는다. 검증: `EditionFetchLifecycleIntegrationTest`.

| 상황 | 계약 |
|---|---|
| **서비스가 재시작**했는데 `file_fetch_status` 가 `QUEUED`/`FETCHING` 으로 남아 있다 | 기동 복구(§6-10 `CrawlStartupRecovery`)가 그 판본을 `FAILED` + `fileFetchError` 로 되돌린다. 되돌리지 않으면 위 표의 409 규칙 때문에 **그 판본은 DB 를 손으로 고치기 전까지 영구히 받아올 수 없다**(비동기 작업은 프로세스와 함께 사라졌는데 상태 컬럼만 남는다) |
| 받아오는 **동안 관리자가 §5-3 으로 그 판본에 파일을 붙였다** | 뒤늦게 도착한 수집 파일은 **붙이지 않는다**. 받아온 `files` 행(PDF·미리보기)은 삭제하고 `file_fetch_status`/`file_fetch_error` 는 지운다(요청은 끝난 것으로 본다). 관리자 파일이 그대로 남는다 — 수집이 관리자 입력을 덮지 않는다는 원칙(§6-10·§6-11)과 같은 방향이고, 덮으면 밀려난 `files` 행이 `ref_id = 판본 id` 라 orphan 배치(컨벤션 §5-3-1 ③ — `ref_id = 0` 만 본다)도 못 지워 **바이트가 영구히 남는다** |

- **진행 중 수집 안내(06-A)**: 받아오는 중인 행이 생기면 화면은 `GET /api/admin/crawl/jobs/active` 를 **한 번** 확인한다. `data` 가 있으면(RUNNING/PAUSED) 상태 줄을 `수집이 끝난 뒤 받아와요 — 진행 중인 수집 보기`(→ `/admin/crawl/{id}`)로 바꾸고, 없으면 기본 문구를 쓴다. 폴링 대상은 판본 상세뿐이고 active 는 다시 묻지 않는다(2026-09-07 확정, senior-dev).

### 5-8. `GET /api/admin/copyright/pending?q=&composerId=&page=&size=`
`size` 는 §4-2 와 같은 규칙(기본 20, 최대 200, 1 미만이면 기본 20). `korea_copyright = UNKNOWN` 판본. `q` 는 곡 제목(한/원어)·작곡가 부분 일치. 정렬 `composer.death_year ASC NULLS LAST, edition.id ASC`.
`PageResponse<PendingCopyrightDTO>`:
```json
{ "editionId": 305, "work": { "id": 23, "titleKo": null, "titleOriginal": "Nocturnes, Op.9" },
  "composer": { "id": 9, "nameKo": "쇼팽", "nameOriginal": "Chopin, Frédéric", "deathYear": 1849 },
  "kind": "COMPLETE_SCORE", "scope": "COMPLETE", "movementNumber": null, "editor": "Ignacy Paderewski", "arranger": null,
  "publisher": "Warsaw: Instytut Fryderyka Chopina, 1949.", "publishYear": 1949,
  "imslpCopyrightText": "Public Domain", "imslpFileUrl": "https://imslp.org/wiki/Special:ImagefromIndex/…", "hasFile": true,
  "autoJudgeSkipReason": "EDITOR_UNVERIFIABLE" }
```
응답 전체 모양 — `unfilteredTotal`(필터 무관 전체 대기 수)과 `autoJudged`(§5-8-1)가 목록과 함께 온다:
```json
{ "data": {
  "unfilteredTotal": 19,
  "autoJudged": { "revertibleEditions": 812, "revertibleRecommendedWorks": 17 },
  "editions": PageResponse<PendingCopyrightDTO>
} }
```
- `autoJudgeSkipReason`(2026-09-07 추가): 지금 §5-11 자동 판정을 돌리면 **이 판본이 왜 자동으로 열리지 않는지**(기획 `02_저작권_판정_지침.md` 부록 A §A-1 표의 사유 코드 **5종** — 2026-09-08 정정, "6종" 은 오기였다. §A-1 에서 `UNKNOWN` 을 유지시키는 규칙은 1·2·3·6·8 이고 §5-11 `skipped` 배열도 5개다). 자동 판정으로 `FREE` 가 될 수 있는 판본이면 `null`. 관리자가 "남은 일감이 어떤 종류인지"를 목록에서 바로 보게 하는 값이다. 계산만 하고 아무것도 바꾸지 않는다.
  - **사유는 "그 판본의 모든 문제"가 아니라 "제일 먼저 막은 하나"다** — §A-1 은 위에서부터 먼저 걸리는 곳에서 끝난다. 몰년도 없고 표기도 NC 인 판본의 사유는 `LICENSE_NOT_REDISTRIBUTABLE`(규칙 1)이지 `COMPOSER_DEATH_YEAR_UNKNOWN`(규칙 2)이 아니다.

**화면 문구 확정 (2026-09-08, senior-dev — §7 이 "designer 에게 요청 필요" 로 열어 둔 건).**
designer 로 넘기지 않았다. 새 문구를 짓는 일이 아니라 **이미 제품에 나가 있는 말을 한 벌로 묶는 일**이기 때문이다 —
같은 5개 코드의 한국어 문구가 **같은 화면**(대기함 상단 자동 판정 미리보기 모달, §5-11 `skipped`)에 이미 나가고 있고,
§7 도 예시로 `EDITOR_UNVERIFIABLE → "편집자 생몰 확인 필요"` 를 적어 두었다. 한 화면에서 같은 코드가 두 가지 말로 보이면
관리자는 그 둘이 같은 것인지 알 수 없다. 표시의 목적("왜 이 판본은 자동으로 안 열렸나 → 그래서 내가 뭘 해야 하나")도
문구의 멋이 아니라 **행동 구분**에 있다(몰년이 비었으면 작곡가를 고치면 다음 실행에 자동으로 열린다, §A-2 ②).

| 코드 | 문구 |
|---|---|
| `LICENSE_NOT_REDISTRIBUTABLE` | 재배포 허용 라이선스가 아님 |
| `COMPOSER_DEATH_YEAR_UNKNOWN` | 작곡가 몰년을 모름 |
| `COMPOSER_COPYRIGHT_ACTIVE` | 작곡가 사후 70년 미경과 |
| `PUBLICATION_TOO_RECENT` | 출판 70년 미경과 |
| `EDITOR_UNVERIFIABLE` | 편집자 생몰 확인 필요 |

- `null` 이면 **아무 말도 만들지 않는다**(자동 판정을 돌리면 열릴 판본이다). 모르는 코드가 오면 **코드를 그대로 보인다** —
  빈 문자열로 삼키면 새 사유가 생겼을 때 관리자의 일감 하나가 설명 없이 사라진다.
- **문구 표는 한 벌만 둔다**: `frontend/src/lib/format.js` 의 `formatAutoJudgeSkipReason()`(이 프로젝트에서 enum → 문구
  변환이 사는 자리 — `formatWorkStatus`·`formatCrawlJobStatus`·`formatRecommendWarning` 과 같은 곳). 미리보기 모달
  (`AutoJudgePanel.jsx`)도 자기 안의 `SKIP_LABELS` 를 버리고 이 함수를 쓴다. 표가 두 벌이면 다음에 한쪽만 고쳐진다.
- designer 에게 열려 있는 것은 **문구가 아니라 행 안에서의 표현**(자리·색·아이콘)이다 — §8 되돌림 참고.
- 계약 검증(Red): `frontend/src/lib/format.autoJudge.test.js`, `frontend/src/pages/admin/CopyrightPendingPage.skipReason.test.jsx`.
- `imslpLicenseCode` 는 **이 응답에 넣지 않는다**(2026-09-07 정정, senior-dev — 예시에만 있고 DTO 에는 없던 불일치를 예시를 빼는 쪽으로 맞춘다). 화면정의 06-C 대기함 표의 열은 "IMSLP 표기 **원문** + 파일 페이지 링크" 라 관리자가 보는 건 `imslpCopyrightText` 이고, 코드는 그 원문의 정규화 캐시(§5-2)일 뿐이라 같은 행에서 새로 알려주는 정보가 없다. 코드 기반 판단의 결과는 이미 `autoJudgeSkipReason` 의 `LICENSE_NOT_REDISTRIBUTABLE` 로 사람 말이 되어 나간다. 화면이 실제로 코드를 필요로 하게 되면 그때 계약에 추가한다(쓰는 곳 없는 필드를 계약에 두면 세 스택이 각자 다르게 해석한다).

#### 5-8-1. `autoJudged` — 되돌리기 진입점이 쓰는 잔량 (2026-09-09 신설, senior-dev — qa 5차 결함 1)

```json
"autoJudged": { "revertibleEditions": 812, "revertibleRecommendedWorks": 17 }
```

| 필드 | 타입 | 의미 |
|---|---|---|
| `revertibleEditions` | long | 지금 §5-12 로 되돌릴 수 있는 판본 수 = `copyright_judged_by = 'system:auto' AND korea_copyright = FREE` 인 판본 수 |
| `revertibleRecommendedWorks` | long | 그중 <b>어떤 곡의 `recommended_edition_id`</b> 로 지정돼 있는 것의 수 = 지금 되돌리면 **다운로드가 닫히는 곡 수** |

> **타입은 `long` 이다** (2026-09-09 정정, senior-dev — `int` 였다). **JSON 은 달라지지 않는다**(정수 두 개).
> 두 수의 출처가 `count(*)` 라 리포지터리가 `long` 을 주는데 DTO 가 `int` 면 서비스에 `(int)` **축소 캐스팅**이 남고,
> 같은 응답의 `unfilteredTotal` 은 이미 `long` 이라 **한 화면의 같은 성격의 수가 두 폭으로 갈린다**.
> 반대로 §5-12 `UndoResult` 의 `reverted`·`recommendationKept` 는 **`int` 그대로 둔다** — 그쪽은 이미 메모리에 올린
> 목록의 크기(`List.size()`)라 캐스팅이 없고, 캐스팅 없는 자리를 맞추자고 타입을 넓히면 근거 없는 변경이 된다.

- **객체는 항상 있다**(`null` 아님). 되돌릴 것이 없으면 `{ "revertibleEditions": 0, "revertibleRecommendedWorks": 0 }` 이다. 화면이 키 유무로 분기하지 않게 한다(§5-11 `byRule`/`skipped` 와 같은 원칙).
- **§5-12 와 같은 집합·같은 조건으로 센다.** 불변식: 그 사이 아무도 판정을 바꾸지 않았다면 지금 §5-12 를 부른 결과가
  `reverted == revertibleEditions`, `recommendationKept == revertibleRecommendedWorks` 다. 두 숫자를 다른 규칙으로 계산하면
  **화면이 예고한 것과 실제 결과가 어긋난다** — 되돌리기는 수백~수천 판본을 한 번에 닫는 동작이라 예고가 틀리면 안 된다.
- **필터(`q`·`composerId`)·페이지와 무관하다.** 목록의 부분집합이 아니라 화면 전체의 상태다(`unfilteredTotal` 과 같은 성격).
  특히 **대기 목록이 0건이어도 값은 그대로다** — 자동 판정을 전부 돌린 직후가 정확히 그 상태이고, 그때가 되돌리기가 가장 필요한 때다.
- **숨김 곡을 빼지 않는다.** §5-12 `recommendationKept` 가 빼지 않기 때문이다(§4-1 `needsRecommendationReviewWorks` 는 빼지만
  그건 공개 기준 §8-17 을 재는 지표라 모집단이 다르다). 같은 숫자를 예고와 결과가 다르게 세면 위 불변식이 깨진다.
- **부분 되돌리기 상태를 그대로 말한다.** 자동으로 열린 판본을 관리자가 §5-9 로 다시 판정하면 `copyright_judged_by` 가
  그 관리자 username 이 되어 대상에서 빠지므로, 이 수는 사람이 손댄 만큼 자연히 줄어든다. 남은 것이 하나라도 있으면 여전히 되돌릴 수 있다.

**왜 이 값이 계약에 필요했나.** qa 5차 결함 1: "자동 판정만 되돌리기" 버튼이 **실행 직후의 컴포넌트 로컬 state 로만** 그려져
관리 홈에 갔다 오거나 새로고침하면 사라졌다. 재실행해도 멱등이라 `judgedFree == 0` 이면 다시 나타나지 않았다.
API 는 살아 있는데 **화면 진입점이 없는 상태**이고, 8084 실데이터가 이미 그렇다(`system:auto` 로 열린 판본이 다수 남아 있는데 버튼이 없다).
이 되돌리기는 부록 A §A-2 ④ 가 "출판 후 120년" 이라는 통계적 안전선을 **"한 번의 요청으로 전부 되돌릴 수 있게 한다"** 로 정당화하는
근거이므로, 운영 중 도달 불가면 규칙의 근거가 함께 약해진다. 버튼을 상시 노출하려면 화면이 **"되돌릴 것이 남아 있나"** 를 알아야 하는데
어떤 응답에도 그 값이 없었다(§4-1 `unknownCopyrightEditions` 는 UNKNOWN 수라 무관하다).

**왜 §4-1(대시보드)이 아니라 §5-8 인가.** ⑴ 되돌리기 **버튼이 있는 화면이 대기함**이다. 값이 다른 화면의 응답에 있으면
대기함이 대시보드 API 를 한 번 더 부르게 되고, 화면과 그 화면이 쓰는 데이터가 갈라진다. ⑵ 대시보드 카드는 "공개까지 뭐가 남았나"
(§8-17)를 재는 **지표**들이다. 이 값은 지표가 아니라 **운영 도구의 잔량**이라 그 축에 얹으면 카드의 의미가 흐려진다.
⑶ 대기함이 이미 하는 한 번의 조회에 실으면 **요청이 늘지 않는다**.

**화면 계약 (frontend-dev — 이 값을 쓰는 방식까지가 계약이다).**
1. `autoJudged.revertibleEditions > 0` 이면 "자동 판정만 되돌리기" 버튼이 **항상** 보인다 — 이번 세션에 실행했는지, 새로고침했는지와 무관하다.
2. `== 0` 이면 버튼이 없다. 다만 **이번 세션의 §5-11 실행이 성공했으면**(`judgedFree > 0`) 서버 값이 아직 `0` 이어도 보인다.
3. 버튼 자리에 남은 양을 말한다: `되돌릴 수 있는 자동 판정 N개`. `revertibleRecommendedWorks > 0` 이면 뒤에 ` · 되돌리면 M곡의 다운로드가 닫혀요` 를 잇는다.
   `0` 일 때 "0곡" 을 쓰지 않는다(닫힐 것이 없다는 말을 굳이 하지 않는다 — §5-12 결과 안내와 같은 규칙).
4. 실행·되돌리기 뒤에는 목록을 다시 불러 이 값도 함께 갱신한다(이미 하는 `onDone` 재조회 하나로 끝난다).

**로컬 기억의 유효 범위** (2026-09-09 추가, senior-dev — qa 5차 코드리뷰 채택). 화면은 "내가 방금 무엇을 했나" 를
**서버 값을 보조하는 용도로만** 기억한다. 그 기억은 **내 동작 직전에 받아 둔 값에만 효력이 있고, 새 §5-8 응답이 도착하는 순간 무효**다.

2-1. **되돌리기가 성공하면 그 즉시 버튼과 잔량 줄을 감춘다** — 새 응답이 올 때까지. 그 시점에 화면이 들고 있는 `autoJudged` 는
   **내가 방금 되돌린 것을 세고 있는 철 지난 값**이라, 그대로 두면 화면이 "812개를 되돌렸어요" 와 "되돌릴 수 있는 자동 판정 812개" 를
   **동시에** 말한다. 이 창에서 한 번 더 누르면 `reverted: 0` 응답이 방금 뜬 결과 안내를 "되돌릴 자동 판정이 없었어요" 로 덮는다.
   **연타 대책은 이것 하나로 끝낸다** — 진입점이 사라지므로 두 번째 요청이 애초에 나갈 수 없다(버튼을 남긴 채 안내만 지키는 방식은
   "눌러도 되는 버튼처럼 보이는데 아무 일도 없다" 를 만든다).
2-2. **그 기억은 새 응답 하나로 끝난다.** 재조회가 `revertibleEditions > 0` 을 주면 버튼은 **다시 보인다** — 그 사이 다른 관리자가
   §5-11 을 돌렸을 수 있다. 되돌린 적이 있다는 사실만으로 감추면 **결함 1(진입점 소실)이 그대로 재발한다.**
2-3. **기억은 마지막 동작 하나뿐이다.** 되돌린 직후 다시 실행하면 버튼은 즉시 돌아온다(2 번 규칙).

> **방향이 비대칭인 것은 의도다.** 실행 기억(2)은 진입점을 **더 보이게만** 하고, 되돌리기 기억(2-1)은 감추되 **새 응답에 즉시 진다.**
> 없는데 보이는 오류의 최대 피해는 요청 한 번과 `reverted: 0` 안내지만, 있는데 감추는 오류의 피해는 **안전장치 소실**(결함 1)이다.
> 그래서 "서버가 되돌릴 것이 있다고 말하는데 화면이 감추는" 상태는 어떤 경우에도 만들지 않는다.

**문구 (확정 — 이 문자열이 계약이다).** 잔량 줄과 확인 모달은 **같은 값**(`autoJudged`)에서 만든다. 예고가 두 벌이면 다음에 한쪽만 고쳐진다.

| 자리 | 조건 | 문구 |
|---|---|---|
| 잔량 줄 | `N > 0`, `M > 0` | `되돌릴 수 있는 자동 판정 N개 · 되돌리면 M곡의 다운로드가 닫혀요` |
| 잔량 줄 | `N > 0`, `M == 0` | `되돌릴 수 있는 자동 판정 N개` |
| 잔량 줄 | `N == 0` | (줄 자체가 없다) |
| 확인 모달 제목 | 항상 | `자동 판정을 되돌릴까요?` |
| 확인 모달 본문 | `N > 0`, `M > 0` | `판본 N개를 '확인 중'으로 되돌리고 M곡의 다운로드가 닫혀요. ` + 유지 문장 |
| 확인 모달 본문 | `N > 0`, `M == 0` | `판본 N개를 '확인 중'으로 되돌려요. ` + 유지 문장 |
| 확인 모달 본문 | `N == 0` (실행 직후 창) | `자동으로 매긴 저작권 판정만 '확인 중'으로 되돌려요. ` + 유지 문장 |

`N` = `revertibleEditions`, `M` = `revertibleRecommendedWorks`, 세 자리 구분 쉼표(`1,234`). 유지 문장 =
`자동으로 지정된 추천 판본은 그대로 있어요 — 바꾸려면 곡 편집에서 해제하세요.` (기획 §11-1: 되돌리기가 **되돌리지 않는 것**을 반드시 말한다.)

- **왜 모달에 숫자를 넣나** (2026-09-09 채택): 같은 패널의 실행 경로는 `dryRun` 미리보기 모달로 "수천 건을 여는 버튼이라 숫자를 먼저 보여준다" 는
  원칙을 이미 지킨다. 되돌리기는 같은 규모를 **닫는** 동작(수백~수천 곡의 다운로드가 그 순간 막힌다)인데 모달이 문장뿐이면
  관리자는 무엇이 얼마나 닫히는지 모른 채 확인을 누른다. 두 경로의 기준을 맞춘다.
- **`N == 0` 에서 숫자 절을 통째로 빼는 이유**: 그 창은 서버 잔량이 아직 `0` 인 **실행 직후**뿐이고, 그때 화면에는 셀 값이 없다.
  `판본 0개를 …` 은 "0곡" 을 쓰지 않는 규칙과 같은 이유로 만들지 않는다(모르는 수·0 인 수는 말하지 않는다).

- 계약 검증(Red): `AutoJudgeUndoEntryPointIntegrationTest`, `frontend/src/pages/admin/CopyrightPendingPage.undoEntry.test.jsx`,
  `frontend/src/pages/admin/CopyrightPendingPage.undoWindow.test.jsx`(2-1·2-2·2-3),
  `frontend/src/pages/admin/CopyrightPendingPage.undoConfirm.test.jsx`(문구 표의 모달 4행).

### 5-9. `PUT /api/admin/editions/{id}/copyright`
요청 `{ "koreaCopyright": "FREE", "copyrightNote": "작곡가 1849 사망, 편집자 1941 사망 → 경과" }`
- FREE/RESTRICTED 에 note 공백 → 400 field `copyrightNote`. UNKNOWN 으로 되돌리기도 허용(note 선택).
- 200 `AdminEditionDTO`(+`workStatus`).

### 5-10. `POST /api/admin/editions/copyright/bulk`
요청 `{ "editionIds": [305, 306, 307], "koreaCopyright": "FREE", "copyrightNote": "…" }` — `editionIds` 1개 이상, note 규칙 동일.
200 `{ "succeeded": [305, 306], "failed": [ { "editionId": 307, "reason": "NOT_FOUND" } ] }`. 없는 id 는 예외 없이 `failed[]` 로 보고하고 나머지는 저장한다(요청 1건 = 트랜잭션 1개 — 2026-09-07 정정, senior-dev: 관찰 가능한 계약이 같고 구현이 단순하다).

### 5-11. `POST /api/admin/copyright/auto-judge` — 자동 판정 일괄 실행 (2026-09-07 추가)

규칙은 기획 `02_저작권_판정_지침.md` **부록 A** 가 원본이다. 이 절은 그 규칙을 API 로 옮긴 것이다.

요청 (본문 생략 가능 — 둘 다 선택)
```json
{ "dryRun": false, "assignRecommended": true }
```
| 필드 | 타입 | 기본 | 의미 |
|---|---|---|---|
| `dryRun` | boolean | `false` | `true` 면 **아무것도 저장하지 않고** 같은 응답 숫자만 계산한다(1,792건을 열기 전 미리보기) |
| `assignRecommended` | boolean | `true` | 판정 후 추천 판본이 없는 곡에 추천을 자동 지정한다(아래 규칙). `dryRun` 이면 계산만 |

**판정 대상·규칙**: `korea_copyright = UNKNOWN` 인 **모든** 판본(파일 유무 무관). 부록 A §A-1 표를 위에서부터 적용해
`FREE` 아니면 `UNKNOWN` 유지. **`RESTRICTED` 는 만들지 않는다. 사람이 판정한 값은 절대 덮어쓰지 않는다.**
`FREE` 가 된 판본에는 다음을 함께 쓴다.

| 컬럼 | 값 |
|---|---|
| `korea_copyright` | `FREE` |
| `copyright_judged_by` | `system:auto` (고정 문자열 — 사람 username 과 구분되는 센티널) |
| `copyright_judged_at` | 실행 시각 |
| `copyright_note` | 규칙별 고정 문구(아래) |

`copyright_note` **고정 문구**(qa·프론트 대조용 — 이 문자열이 계약이다):
| 규칙 | 문구 |
|---|---|
| `CC_REDISTRIBUTABLE` | `자동 판정: 재배포 허용 라이선스(CC_BY_SA) + 작곡가 1750년 사망` — 괄호 안은 `imslp_license_code` 의 enum 이름 |
| `PD_NO_EDITOR` | `자동 판정: Public Domain + 작곡가 1827년 사망 + 편집자 표기 없음` |
| `PD_OLD_PUBLICATION` | `자동 판정: Public Domain + 작곡가 1827년 사망 + 1862년 출판(120년 경과)` |

**추천 판본 자동 지정** (`assignRecommended: true`): `recommended_edition_id IS NULL` 인 곡마다,
그 곡의 판본 중 `kind = COMPLETE_SCORE AND scope = COMPLETE AND pdf_file_id IS NOT NULL AND korea_copyright = FREE` 인 것을
`imslp_download_count DESC NULLS LAST, id ASC` 로 정렬해 첫 번째를 추천으로 지정한다(01_ERD §3-3 의 "추천 후보" 정의에
`korea_copyright = FREE` 조건을 더한 것). **이미 추천이 있는 곡은 건드리지 않는다.**
자동으로 지정된 곡은 `recommended_edition_reviewed = false` 다(§5-6-1) — **이 자동 지정이 관현악 총보를 추천으로 앉힐 수 있다는 것이
바로 검수 개념을 만든 이유다**(기획 §10-8). 그래서 자동 판정을 돌리면 §4-1 의 "추천 판본 확인 필요" 숫자가 그만큼 올라간다.
> 왜 같은 API 에 넣는가: 판정만 하면 `recommendedEditionId` 가 없어 곡이 계속 `PREPARING` 이라 **다운로드 가능한 곡은 여전히 0개**다.
> 두 단계를 따로 두면 관리자가 절반만 실행한 상태가 생긴다. 다만 관리자가 추천을 손으로 관리하고 싶을 수 있으니 끌 수 있는 스위치로 둔다.

**자동 지정도 근거 한 줄을 남긴다 (2026-09-21, 기획 06 §1-3 · 8-D 3 · 8-F 5).** 지정한 곡마다 `work_recommendation_log` 에 한 줄(01_ERD §3-13):

| 컬럼 | 값 |
|---|---|
| `source` / `action` | `AUTO` / `ASSIGNED` |
| `decided_by_user_id` · `decided_by_nickname` | **NULL** — 사람이 아니다. 화면 1단은 `자동` |
| `auto_rule` | `MOST_IMSLP_DOWNLOADS` |
| `auto_imslp_download_count` | **지정한 그 판본의 `imslp_download_count` 를 그 순간 읽은 값.** 값이 없던 판본이면 **NULL** |
| `auto_candidate_count` | **그 순간 그 곡의 후보 수**(위 조건을 만족한 판본 수). 정렬 전 집합의 크기 |
| `auto_rank` | `1` (지금 규칙은 언제나 1위를 고른다) |
| `previous_*` | 전부 NULL — 자동 지정은 **추천이 없는 곡에만** 걸리므로 항상 첫 지정이다 |

- **"그때 그 값" 이 계약의 핵심이다 (기획 06 §1-3).** 근거를 보여줄 때 IMSLP 다운로드 수·후보 수를 **다시 읽어 계산하면 안 된다** —
  그건 그때의 판단을 설명하는 것이 아니라 오늘 다시 낸 답이다. 그래서 세 숫자를 지정 시점에 **박아 둔다.**
  검증: `RecommendationAutoEvidenceIntegrationTest` 가 지정 뒤에 판본의 `imslpDownloadCount` 를 바꾸고도 근거가 그대로인지 본다.
- **후보가 1개였던 것과 다운로드 수가 없던 것은 서로 다른 사실이다** — 화면이 별도 분기로 그린다(§4-7-2). `auto_candidate_count = 1` 과 `auto_imslp_download_count = null` 이 각각 구분 가능해야 한다.
- **`dryRun` 이면 줄도 쌓지 않는다** — dryRun 은 엔티티 변경 메서드를 아예 부르지 않는 방식이다(03 §16). 로그도 같은 규칙을 따른다.
- **이 실행으로 지정된 추천에는 근거가 함께 남는다**(기획 8-F 5). 반대로 **이미 추천이 있던 곡(실데이터 42곡)은 후보 조회에서 빠지므로 근거가 생기지 않는다** — 8-E 1 이 그것을 확인한다.

응답 200
```json
{ "data": {
  "dryRun": false,
  "targetCount": 1792,
  "judgedFree": 812,
  "remainingUnknown": 980,
  "recommendedAssigned": 17,
  "byRule": [
    { "rule": "CC_REDISTRIBUTABLE", "count": 12 },
    { "rule": "PD_NO_EDITOR", "count": 300 },
    { "rule": "PD_OLD_PUBLICATION", "count": 500 }
  ],
  "skipped": [
    { "reason": "LICENSE_NOT_REDISTRIBUTABLE", "count": 400 },
    { "reason": "COMPOSER_DEATH_YEAR_UNKNOWN", "count": 20 },
    { "reason": "COMPOSER_COPYRIGHT_ACTIVE", "count": 5 },
    { "reason": "PUBLICATION_TOO_RECENT", "count": 55 },
    { "reason": "EDITOR_UNVERIFIABLE", "count": 500 }
  ]
} }
```
- `targetCount` = 실행 **전** `UNKNOWN` 판본 수. `judgedFree + remainingUnknown = targetCount` 가 항상 성립한다.
- `byRule` 은 `CC_REDISTRIBUTABLE → PD_NO_EDITOR → PD_OLD_PUBLICATION` 고정 순서, `skipped` 는 부록 A §A-1 표 순서
  (`LICENSE_NOT_REDISTRIBUTABLE → COMPOSER_DEATH_YEAR_UNKNOWN → COMPOSER_COPYRIGHT_ACTIVE → PUBLICATION_TOO_RECENT → EDITOR_UNVERIFIABLE`).
  **count 가 0 인 항목도 배열에서 빼지 않는다** — 화면이 항목 유무로 분기하지 않게, 항상 3개·5개가 온다.
- `sum(byRule.count) = judgedFree`, `sum(skipped.count) = remainingUnknown`.
- **재실행해도 안전(멱등)**: 두 번째 실행의 `targetCount` 는 첫 실행의 `remainingUnknown` 과 같고 `judgedFree` 는 0 이다.
- 인증: ADMIN. 비로그인 401, USER 403.
- `dryRun: true` 는 DB 를 전혀 바꾸지 않는다 — 실행 후 §5-8 대기함 수·판본 `koreaCopyright` 가 그대로여야 한다.

### 5-12. `POST /api/admin/copyright/auto-judge/undo` — 자동 판정 되돌리기 (2026-09-07 추가)

요청 본문 없음. 응답 200 `{ "data": { "reverted": 812, "recommendationKept": 17 } }`.

- `recommendationKept`(2026-09-08 추가, 기획 §11-1 결정 4): 되돌린 판본 중 **어떤 곡의 추천 판본으로 지정돼 있는 것**의 수 = 이번 되돌리기로 다운로드가 닫힌 곡 수. 화면은 이 숫자로 되돌리기의 범위를 말한다("추천 지정은 그대로 뒀어요 — 17곡은 다시 판정해야 열려요"). 되돌릴 것이 없으면 `{ "reverted": 0, "recommendationKept": 0 }`.
- 왜 숫자를 계약에 넣나: 기획은 "되돌리기가 무엇을 되돌리지 **않았는지** 결과 안내 문구로 반드시 말한다"고 정했다. 고정 문장만으로는 "몇 곡이 닫혔나"를 말할 수 없고, 화면이 그 수를 스스로 세려면 곡 목록을 다시 받아야 한다.
- 되돌린 곡은 `missing` 에 `COPYRIGHT_JUDGMENT` 가 생겨 **보완 필요 목록으로 돌아온다**(§4-7 · §4-6 · §4-1). 되돌리기의 목적("다시 볼 곡을 찾는다")은 이것으로 완성된다.

- 대상: `copyright_judged_by = 'system:auto' AND korea_copyright = FREE` 인 판본 전부.
- 되돌린 판본: `korea_copyright = UNKNOWN`, `copyright_note = null`, `copyright_judged_at = null`, `copyright_judged_by = null`.
- **사람이 판정한 것은 대상이 아니다.** 자동으로 열린 판본을 관리자가 §5-9 로 다시 판정하면 `copyright_judged_by` 가
  그 관리자 username 이 되므로 자동으로 제외된다.
- **추천 판본 지정은 되돌리지 않는다.** 추천은 유지되지만 그 판본이 `UNKNOWN` 이 되므로 곡 상태는 즉시 `UNKNOWN`
  (01_ERD §4 계산) → **다운로드는 그 순간 닫힌다.** 되돌리기의 목적(위험 차단)은 판정만 되돌려도 100% 달성되고,
  추천까지 지우면 관리자가 손으로 지정한 추천과 자동 지정을 구분할 수 없어(구분용 컬럼이 없다) 사람 작업을 지운다.
- **근거·이력을 하나도 건드리지 않는다 (2026-09-21, 기획 8-D 4 · `01` §15-7).** 이 API 는 추천을 건드리지 않으므로 `work_recommendation_log` 에
  줄을 더하지도 지우지도 고치지도 않는다. 되돌린 뒤 §4-7 의 `recommendation` 은 실행 전과 **한 글자도 같아야** 한다 — 달라지면 결함이다.
  (qa 가 "되돌렸는데 근거가 남아 있다" 를 결함으로 보지 않도록 계약으로 못 박는다. 검증: `RecommendationAutoEvidenceIntegrationTest`.)
- 되돌릴 것이 없어도 200 `{ "reverted": 0 }`(에러 아님).
- **버튼이 언제 보이는가는 이 응답이 아니라 §5-8-1 `autoJudged` 가 정한다** (2026-09-09, qa 5차 결함 1).
  이 API 를 부를 수 있는지를 화면이 "방금 실행했다" 는 기억으로 판단하면 새로고침 한 번에 진입점이 사라진다.
  이 응답의 `reverted`·`recommendationKept` 는 §5-8-1 이 미리 말한 `revertibleEditions`·`revertibleRecommendedWorks` 의 **사후 확인**이고, 둘은 같은 조건으로 센다.
- **이 응답이 온 뒤 재조회가 도착하기 전까지, 화면이 들고 있는 §5-8-1 값은 방금 되돌린 것을 세고 있는 철 지난 값이다**
  (2026-09-09). 그 창에서 버튼·잔량 줄을 감추는 것이 §5-8-1 화면 계약 2-1 이고, 그래서 **연타로 `reverted: 0` 을 받는 일이 없다.**
- 인증: ADMIN. 비로그인 401, USER 403.

---

## 6. 관리자 API 상세 — 수집

### 6-1. `POST /api/admin/crawl/check` — 주소 확인
요청 `{ "urls": ["https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)", "…"], "fetchFiles": true }` (`urls` 1개 이상, 최대 500; `fetchFiles` 기본 true — 예상 시간 계산용)

**주소 정규화·판정 규칙**
1. 줄 앞뒤 공백 제거, 빈 줄 무시.
2. `URI` 파싱 실패 / 스킴이 http·https 아님 / 호스트가 `imslp.org`·`www.imslp.org` 아님 / 경로가 `/wiki/` 로 시작하지 않음 / 제목이 `Special:`·`Category:`·`File:`·`Image:`·`Template:` 으로 시작 → `INVALID_URL`.
3. 정규 URL = `https://imslp.org/wiki/` + (퍼센트 디코딩한 제목, 공백→`_`, `#`·`?` 이후 제거). **비ASCII(é ü ą)·아포스트로피·쉼표·슬래시·괄호는 그대로 허용**(03 §8 미결 1 확정). `Für_Elise…` 와 `F%C3%BCr_Elise…` 는 같은 주소.
4. 같은 정규 URL 이 목록 안에 앞서 있으면 `DUPLICATE`(`duplicateOfSeq` = 앞 항목 번호).
5. `work.imslp_url` 일치하는 곡이 있고 그 곡에 `imslp_file_id` 가 있는 판본이 하나라도 있으면(=이미 수집됨) `EXISTS`(`existingWorkId`). 곡은 있는데 수집된 판본이 없으면(시드·직접 등록) `ATTACH`. 곡이 없으면 `NEW`.

응답 200
```json
{ "data": {
  "items": [
    { "seq": 1, "inputUrl": "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)", "canonicalUrl": "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)", "verdict": "ATTACH", "existingWorkId": 21, "duplicateOfSeq": null },
    { "seq": 2, "inputUrl": "https://example.com/foo", "canonicalUrl": null, "verdict": "INVALID_URL", "existingWorkId": null, "duplicateOfSeq": null },
    { "seq": 3, "inputUrl": "…", "canonicalUrl": "…", "verdict": "DUPLICATE", "existingWorkId": null, "duplicateOfSeq": 1 }
  ],
  "summary": { "newCount": 30, "attachCount": 18, "existsCount": 2, "invalidCount": 1, "duplicateCount": 1, "estimatedSeconds": 3600 }
} }
```
화면 표시: NEW·ATTACH = "수집 예정"(ATTACH 는 "등록된 곡에 판본 붙임" 보조 문구), EXISTS = "이미 있음 — 건너뜀"(+정보만 다시 가져오기 체크), INVALID_URL, DUPLICATE.
`estimatedSeconds` = `(newCount + attachCount) × (fetchFiles ? 75 : 6) + refreshCount × 6` — 항목당 메타데이터 2요청(각 2초 대기) + 파일 2개 × (15초 대기 + 2초 + 전송 약 10초). 체크 단계에선 refresh 선택 전이므로 `existsCount` 는 0초로 본다.

### 6-2. `POST /api/admin/crawl/jobs` — 수집 시작
요청
```json
{ "items": [ { "url": "https://imslp.org/wiki/…", "refresh": false }, { "url": "…", "refresh": true } ], "fetchFiles": true }
```
서버는 §6-1 규칙으로 다시 판정해 항목 모드를 정한다: NEW→`CREATE`, ATTACH→`ATTACH`, EXISTS+refresh→`REFRESH`, EXISTS→`SKIP`(항목은 만들되 즉시 `SKIPPED`), INVALID_URL/DUPLICATE → 항목에서 제외.

| 조건 | 결과 |
|---|---|
| RUNNING/PAUSED 작업이 이미 있음 | 409 `DUPLICATE_RESOURCE` "진행 중인 수집이 있어요" |
| 처리할 항목(CREATE/ATTACH/REFRESH) 0개 | 400 `BUSINESS_RULE_VIOLATION` "수집할 주소가 없어요" |
| 정상 | 201 `CrawlJobDTO`(status RUNNING). 워커가 즉시 시작 |

### 6-3. `CrawlJobDTO`
```json
{ "id": 12, "status": "RUNNING", "stopRequested": false, "stoppedByRestart": false, "fetchFiles": true,
  "totalCount": 50, "processedCount": 12, "successCount": 11, "failCount": 1, "skipCount": 0, "hiddenCount": 0, "pendingCount": 38,
  "currentItem": { "seq": 13, "url": "https://imslp.org/wiki/Nocturnes,_Op.9_(Chopin,_Frédéric)", "title": "Nocturnes, Op.9" },
  "currentStage": "DOWNLOADING_FILE", "currentFileIndex": 2, "currentFileTotal": 2,
  "pausedUntil": null, "failureReason": null, "retryOfJobId": null, "createdBy": "gks930620",
  "createdAt": "2026-09-06T05:02:00Z", "startedAt": "2026-09-06T05:02:01Z", "finishedAt": null,
  "elapsedSeconds": 903, "estimatedRemainingSeconds": 2700 }
```
- `processedCount = success + fail + skip + hidden`, `pendingCount = total − processed`(처리 중 1건 포함).
- `estimatedRemainingSeconds`: 처리된(SKIP 제외) 항목의 평균 소요 × 남은 항목, 처리된 게 없으면 항목당 75초. COMPLETED/STOPPED/FAILED 면 null. `currentItem`·`currentStage` 는 처리 중이 아닐 때 null.
- `elapsedSeconds`: `startedAt` 부터 `finishedAt`(없으면 지금)까지.

### 6-4. `GET /api/admin/crawl/jobs?page=` → `PageResponse<CrawlJobDTO>`(created_at DESC)
### 6-5. `GET /api/admin/crawl/jobs/active` → `data: CrawlJobDTO | null`(RUNNING 또는 PAUSED)
### 6-6. `GET /api/admin/crawl/jobs/{id}` → `CrawlJobDetailDTO` = CrawlJobDTO + `items: CrawlItemDTO[]`(seq 순, 전부). 404.

`CrawlItemDTO`
```json
{ "id": 1201, "seq": 1, "url": "https://imslp.org/wiki/…", "mode": "CREATE", "status": "SUCCESS",
  "failReason": null, "message": "판본 14개, 파일 2개 받음", "workId": 77, "workTitle": "Piano Sonata No.14, Op.27 No.2",
  "editionCount": 14, "fileCount": 2, "startedAt": "…", "finishedAt": "…" }
```
`message` 고정 문구(qa 대조용): 성공 `"판본 {n}개, 파일 {m}개 받음"` / REFRESH 성공 `"판본 정보 갱신 (파일 재요청 없음)"` / SKIPPED `"이미 있음 — 건너뜀"` / HIDDEN `"피아노 독주곡이 아닌 것 같아요"` / FAILED: `PAGE_NOT_FOUND` `"IMSLP에 그 페이지가 없어요"`, `NO_PDF_EDITION` `"작품 페이지에 PDF 판본이 없어요"`, `FILE_DOWNLOAD_FAILED` `"파일을 받다가 끊겼어요"`, `IMSLP_UNAVAILABLE` `"IMSLP가 응답하지 않아요"`, `INTERRUPTED` `"중단됨 — 서비스가 재시작됐어요"`.

### 6-7. `POST /api/admin/crawl/jobs/{id}/stop`
- RUNNING → `stopRequested=true`(처리 중 항목을 마친 뒤 STOPPED). PAUSED → 즉시 STOPPED. 그 외 상태 400 "중지할 수 있는 상태가 아니에요". 200 `CrawlJobDTO`.

### 6-8. `POST /api/admin/crawl/jobs/{id}/resume`
- STOPPED(대기 항목 있음) → RUNNING, PENDING 항목부터 계속. PAUSED → `pausedUntil=null` 즉시 재개. 다른 활성 작업 있으면 409. 대기 항목 0개면 400. 200 `CrawlJobDTO`.

### 6-9. `POST /api/admin/crawl/jobs/{id}/retry-failed`
- 원본 작업의 `FAILED` 항목만으로 새 작업 생성(`retryOfJobId`, `fetchFiles` 는 원본 값). HIDDEN·SKIPPED 는 제외. 실패 0건 400, 활성 작업 있음 409. 201 `CrawlJobDTO`.

### 6-10. 워커 동작 계약 (API 로 관찰되는 것만)
- 항목 처리 순서 = `seq`. 항목마다: 메타 읽기(`READING_METADATA`) → 곡·판본 upsert → `fetchFiles` 면 "전체 악보·전곡·허용 라이선스" 판본 중 IMSLP 다운로드 수 상위 2개 파일 수신(`DOWNLOADING_FILE` i/n) → 미리보기(`MAKING_PREVIEW`) → 항목 상태 확정.
- 편성이 **건반 독주**가 아니면 곡은 만들되 `hidden=true, hiddenReason=NOT_PIANO_SOLO`, 항목 `HIDDEN`, 파일은 받지 않는다. 판정 규칙은 아래 §6-11.
- 파일 1개 실패는 항목 `FAILED/FILE_DOWNLOAD_FAILED`(판본 정보는 저장됨, 다른 파일이 성공했으면 fileCount 에 반영).
- **메타 읽기 단계에서 예상 밖의 오류**(우리 쪽 버그·파싱 사고 = IMSLP 예외가 아닌 것)면 항목 `FAILED/INTERNAL_ERROR`, message `"처리 중 오류가 났어요"`, 작업은 계속된다. **연속 무응답으로 세지 않는다**(PAUSED 로 가지 않는다). 2026-09-07 추가 — `IMSLP_UNAVAILABLE` 로 뭉개면 관리자가 IMSLP 탓으로 읽고, 멀쩡한 IMSLP 를 두고 10분 쉰다. 파일 수신 단계의 예상 밖 오류는 지금처럼 `FILE_DOWNLOAD_FAILED`(관리자가 §5-7 로 재시도할 수 있고 곡·판본은 이미 저장돼 있다).
- IMSLP 무응답(타임아웃·5xx·429·봇 게이트 302 반복)이 **연속 3항목** → `PAUSED`, `pausedUntil = now+10분`. 시간이 지나면 자동 재개, 다시 3연속이면 `STOPPED` + `failureReason "IMSLP가 응답하지 않아요"`.
- 서비스 기동 시 RUNNING/PAUSED 작업 → `STOPPED, stoppedByRestart=true`, PROCESSING 항목 → `FAILED/INTERRUPTED`. 이미 받은 파일(`imslp_file_id` 로 판본에 파일이 있음)은 재개해도 다시 받지 않는다.
- **파일 수신 단계의 IMSLP 무응답**(봇 게이트 302·429·5xx)은 항목 `FAILED/IMSLP_UNAVAILABLE` 이다 — `FILE_DOWNLOAD_FAILED` 로 내리지 않는다. 302/429 는 "물러서라"는 신호라 연속 3항목 백오프(PAUSED)를 타야 하고, 파일 실패로 뭉개면 다음 항목에서 같은 요청을 그대로 반복하게 된다. `FILE_DOWNLOAD_FAILED` 는 **그 파일이 잘못된 경우**(PDF 아님·크기 불일치)다. 단 이때도 항목의 `editionCount`(이미 저장된 판본 수)와 `fileCount`(그때까지 받은 파일 수)를 채운다 — 2026-09-07 추가, 지금은 둘 다 null 이라 관리자가 "곡·판본은 저장됐고 파일만 비었다"는 사실을 화면에서 알 수 없다. 판본은 남아 있으므로 §5-7 로 개별 재시도할 수 있다.
- 요청 간격 최소 2초, **파일 1개를 받을 때마다 15초 대기**(항목당 1회가 아니다 — 곡당 파일 2개면 2회, 개별 받아오기 §5-7 도 1회), 동시 1커넥션, UA 에 서비스명+연락처(03 §3).
- **대기의 자리도 계약이다** — `대기 페이지 GET → 15초 → 파일 GET` 순서다(브라우저와 같다). 대기 페이지를 열기 전에 15초를 쓰면, 카운트다운을 띄운 페이지를 받아 놓고 0.3초 만에 파일을 치는 셈이라 IMSLP 에는 "카운트다운을 안 기다린 클라이언트"로 보인다(기획 §9-1 위반). 대기 페이지가 무응답이면(주소를 못 얻으면) **15초를 태우지 않고** 그 파일을 포기한다 — 못 받을 파일 때문에 워커가 자면 연속 무응답 백오프(PAUSED)로 물러서는 것도 늦어진다.
- 이 대기는 응답 JSON 에 드러나지 않으므로 `ImslpFileWaitPolicyIntegrationTest` 가 게이트·클라이언트 호출 순서 `RESOLVE:{id} → FILE_WAIT → DOWNLOAD:{id}` (파일마다 반복)로 검증한다. 2026-09-07 — 같은 자리에서 두 번 어겼다(1차: 호출이 통째로 빠진 채 수집 테스트 전부 통과 / 2차: 호출은 있으나 대기 페이지 **앞**에 있었다).

### 6-11. 건반 독주 판정 규칙 (`ParsedWorkPage.isPianoSolo`) — 2026-09-07 개정

**허용 악기 집합 `KEYBOARD_SOLO`** = `piano, pianoforte, fortepiano, harpsichord, clavichord, cembalo, keyboard`
(소문자·공백 정규화 후 비교). **`organ` 은 넣지 않는다.**

판정 순서:
1. **카테고리 정확 일치** — 카테고리 중 `For {X}` (X ∈ `KEYBOARD_SOLO`) 와 **정확히 같은** 것이 하나라도 있으면 `true`.
   (`_` 는 공백으로 바꿔 비교. `For piano (arr)`·`For piano 4 hands`·`For violin, harpsichord` 는 정확히 같지 않으므로 걸리지 않는다.)
2. **Instrumentation 전 토큰 일치** — `Instrumentation` 값을 `,` `;` `/` 로 나누고, 각 토큰에서 괄호 부분과 `or …` 뒤를
   떼어낸 뒤(`harpsichord (or piano)` → `harpsichord`), **비어 있지 않고 모든 토큰이 `KEYBOARD_SOLO` 에 속하면** `true`.
   토큰이 하나도 없으면(값이 비었으면) `false`.
3. 그 외 `false`.

**왜 바꾸는가.** 1차 수집에서 `Notebooks for Anna Magdalena Bach`, `Prelude and Fugue in C major, BWV 846`,
`15 Inventions, BWV 772-786` 세 곡이 `HIDDEN` 으로 빠졌다. IMSLP 는 바흐 건반곡을 작곡 당시 악기인
harpsichord/keyboard 로 분류하는데, 인벤션·평균율·안나 막달레나 노트북은 **한국 피아노 학습자의 핵심 레퍼토리**이고
1차 큐레이션 50곡에 일부러 넣은 곡이다. "IMSLP 의 악기 분류"와 "피아노로 치는 곡인가"가 어긋나는 지점이다.

**왜 organ 은 빼는가.** 오르간 악보는 페달 성부가 별도 보표로 적혀 있어 그대로 피아노로 칠 수 없고(손만으로 연주 불가),
레지스트레이션 지시도 피아노에 의미가 없다. 즉 "받아서 바로 피아노로 친다"는 우리 제품의 약속을 지킬 수 없는 악보다.
바흐 오르간 작품을 열면 그 수가 건반 독주곡을 압도해 목록이 오염된다. **피아노 편곡본이 필요하면 그 편곡 페이지를
별도 URL 로 수집하면 된다**(IMSLP 는 편곡을 별도 작품 페이지로 두는 경우가 많고, 아니면 `ARRANGEMENT` 판본으로 들어온다).

**왜 카테고리를 먼저 보고, 걸리면 Instrumentation 을 보지 않는가.** `Notebooks for Anna Magdalena Bach` 처럼
건반 소품과 성악 아리아가 **섞인 모음집**은 `Instrumentation` 에 `keyboard, voice` 가 함께 적히지만 카테고리에는
`For harpsichord` 와 `For voice, continuo` 가 **둘 다** 붙는다. 이때 "단일 건반악기 카테고리가 하나라도 있다"는
**"이 페이지 안에 건반 독주로 칠 곡이 들어 있다"** 는 뜻이고, 그런 페이지는 사용자에게 보여야 한다(미뉴에트 BWV Anh.114 가 여기 있다).
반대로 카테고리에 단일 건반악기 항목이 없으면 그 곡은 반주·앙상블이므로 Instrumentation 전 토큰 검사로 막힌다.

**왜 카테고리 정확 일치가 "독주" 신호로 충분한가.** IMSLP 카테고리는 `For {악기 목록}` 형태라 편성이 늘어나면
카테고리 이름 자체가 길어진다(`For violin, harpsichord`, `For piano 4 hands`, `For voice, piano`).
따라서 단일 악기 카테고리와의 **정확 일치**가 곧 "그 악기 하나로 연주하는 곡"이라는 뜻이다 — 기존 `For piano` 판정이
이미 이 성질에 기대고 있었고, 집합만 넓히는 것이라 새 위험이 없다.

**바뀌는 것 / 안 바뀌는 것**
| 입력 | 전 | 후 |
|---|---|---|
| `Instrumentation: piano`, `For piano` | true | true (변화 없음) |
| `Instrumentation: harpsichord`, `For harpsichord` | **false** | **true** |
| `Instrumentation: keyboard`, `For keyboard` | **false** | **true** |
| `Instrumentation: harpsichord (or piano)` | **false** | **true** |
| `Instrumentation: organ`, `For organ` | false | false (변화 없음 — 의도적) |
| `Instrumentation: voice, harpsichord`, 카테고리 `For voice, harpsichord` | false | false |
| `Instrumentation: keyboard, voice`, 카테고리 `For harpsichord` + `For voice, continuo` | **false** | **true** (모음집 — 위 설명) |
| `Instrumentation: orchestra`, `For orchestra` | false | false (변화 없음) |
| 카테고리에 `For piano (arr)` 만 있음 | false | false (변화 없음) |

**이미 숨겨진 곡은 자동으로 풀리지 않는다.** `hidden` 은 DB 컬럼이고 판정은 수집 시점에만 돈다.
1차 수집에서 숨겨진 3곡은 (a) 관리자가 §4-8 로 `hidden: false` 로 바꾸거나 (b) 그 URL 을 `refresh` 로 다시 수집하면 풀린다.

**(b) 를 위해 재수집(`REFRESH`·`ATTACH`)도 숨김을 다시 계산한다 — 단 한 방향으로만** (2026-09-07 추가):

| 곡의 현재 상태 | 이번 판정 | 결과 |
|---|---|---|
| `hidden=true, hiddenReason=NOT_PIANO_SOLO` (수집이 숨긴 것) | 건반 독주 | **`hidden=false, hiddenReason=null`** — 규칙 개정이 반영된다 |
| `hidden=true, hiddenReason=null` (관리자가 숨긴 것) | 건반 독주 | 그대로 숨김 — **관리자 판단을 수집이 뒤집지 않는다** |
| `hidden=false` (보이는 곡) | 건반 독주 아님 | 그대로 보임 — 수집이 이미 공개된 곡을 갑자기 감추지 않는다 |
| 새 곡 | 건반 독주 아님 | `hidden=true, hiddenReason=NOT_PIANO_SOLO` (기존과 동일) |

규칙 개정이 기존 데이터에 닿지 않으면 개정한 의미가 없고, 반대로 수집이 관리자의 숨김/공개를 덮으면
관리자가 손댈수록 되돌아가는 화면이 된다. 그래서 **"수집이 숨긴 것만 수집이 푼다"** 로 한정한다.

### 6-12. 재수집(`REFRESH`/`ATTACH`)이 판본에 하는 일 — 2026-09-07 추가

수집은 **몇 번이고 다시 도는 작업**이다(판정 규칙 개정·IMSLP 갱신·실패 재시도). 지금까지 이 계약은 01_ERD §7 의
"판본은 `imslp_file_id` 로 조회 → 있으면 메타만 갱신(파일·판정·메모는 보존)" 한 줄이었고, 아래 두 가지가 비어 있었다.
검증: `RecrawlContractIntegrationTest`.

**(1) 관리자가 §5-3 으로 저장한 적 있는 판본은 재수집이 그 값을 덮지 않는다.**

| 필드 묶음 | 재수집의 동작 |
|---|---|
| **관리자 편집 필드** — `kind`, `scope`, `movement_number`, `page_count`, `publisher`, `publish_year`, `plate_number`, `editor`, `arranger`, `scanner`, `imslp_file_url`, `imslp_copyright_text`(+파생 `imslp_license_code`), `cc_license_name`, `cc_attribution` | `edition.admin_edited_at IS NULL` 일 때만 갱신한다. 관리자가 한 번이라도 §5-3 으로 저장했으면(= `admin_edited_at` 이 찍혔으면) **건드리지 않는다** |
| **IMSLP 거울 필드** — `section_label`, `imslp_description`, `imslp_original_file_name`, `imslp_download_count` | 언제나 갱신한다(관리자가 편집할 수 없는 값이라 충돌이 없다) |
| **파일·판정** — `pdf_file_id`, `preview_file_id`, `korea_copyright`, `copyright_note`, `copyright_judged_*` | 기존대로 보존 (아래 (2) 의 예외 하나) |

> 판본이 25,000개가 되는 규모에서 관리자가 손으로 고친 값이 다음 수집에 사라지면 관리 작업 자체가 무의미해진다.
> 특히 `scope`/`kind` 가 되돌아가면 **추천 판본이 갑자기 편곡·악장 발췌**가 되어 §5-6 이 막으려던 상태가
> 수집을 통해 뒷문으로 들어온다. `admin_edited_at` 은 §5-3 저장에서만 찍는다 — §5-9 저작권 판정은 "편집"이 아니다
> (판정은 이미 별도 컬럼으로 보존되고, 표기 원문은 계속 IMSLP 를 따라가야 아래 (2) 가 작동한다).

**(2) IMSLP 표기가 재배포 불가로 바뀌면, 자동 판정으로 열어 둔 판본은 다시 닫는다.**

재수집한 `imslp_license_code` 가 재배포 허용(`PD/CC0/CC_BY/CC_BY_SA`)이 **아니게** 되었고 그 판본이
`korea_copyright = FREE AND copyright_judged_by = 'system:auto'` 이면 → `UNKNOWN` 으로 되돌리고
`copyright_note`/`copyright_judged_by`/`copyright_judged_at` 을 비운다(§5-12 되돌리기와 같은 결과).
그 판본이 추천이면 곡 상태는 즉시 `UNKNOWN` 이 되고 다운로드가 닫힌다.

- **사람이 판정한 값(`copyright_judged_by` 가 관리자 username)은 뒤집지 않는다** — 이 저장소의 일관된 원칙이고,
  사람은 표기 외의 근거(작곡가 사후 연수·판본의 출처)로 판단했을 수 있다. 대신 §5-8 대기함의 `autoJudgeSkipReason`
  로직이 그 판본을 `LICENSE_NOT_REDISTRIBUTABLE` 로 보게 되므로 다음 점검에서 드러난다.
- 파일 바이트는 지우지 않는다. `UNKNOWN` 이면 §3-4 다운로드가 닫히므로 재배포는 그 즉시 멈춘다.
- 반대 방향(재배포 불가 → 허용)으로 자동으로 열지는 않는다. 여는 것은 언제나 §5-11 을 명시적으로 돌릴 때뿐이다.

---

## 7. 화면 ↔ API 매핑 (frontend-dev 참조)

**2026-09-10 — 구분·기준이 붙은 뒤의 호출 (기존 행에 겹쳐 읽는다)**

| 화면 | 호출 |
|---|---|
| 홈 `/piano` | `GET /api/works/popular?section=PIANO&limit=10`, `GET /api/composers/featured?section=PIANO&limit=8` |
| 검색 결과 `/piano/search` | `GET /api/works/search?section=PIANO&q&in&level&pages&downloadable&page` — **URL 쿼리를 그대로 전달하되 `in=ALL` 은 생략하고, 화면 전용 `from` 은 보내지 않는다.** 0건 [C] 의 인기곡 5개는 `popular?section=PIANO&limit=5` |
| 곡 상세 `/piano/works/:id` | `GET /api/works/{id}` — **`section` 을 보내지 않는다.** 응답 `section` 이 주소의 구분과 다르면 그 구분 주소로 **replace 이동**(기획 04 §1-4) |
| 작곡가 목록 / 상세 | `GET /api/composers?section=PIANO` / `GET /api/composers/{id}?section=PIANO` + `GET /api/composers/{id}/works?section=PIANO&sort&…` |
| **준비 중 안내 `/violin`·`/orchestra`** | **호출 없음.** 고정 문구·고정 출구 2개뿐이라 로딩·에러 상태가 존재하지 않는다(화면정의 08 §2 상태표) |
| **찾을 수 없는 페이지** | 호출 없음 |
| 관리 화면 전부 | **변화 없음.** `section` 을 보내지 않는다 |

| 화면 | 호출 |
|---|---|
| 홈 | `GET /api/works/popular?limit=10`, `GET /api/composers/featured?limit=8` (독립 요청, 각각 실패 처리) |
| 검색 결과 | `GET /api/works/search?q&level&pages&downloadable&page` — URL 쿼리 그대로 전달. 0건 화면의 인기곡 5개는 `popular?limit=5` |
| 곡 상세 | `GET /api/works/{id}` 1회(같은 작곡가 곡 포함). 다운로드는 `<a href={downloadUrl} download>` — **fetch 로 blob 받지 않는다**(큰 파일 메모리·진행 표시 없음). **실패 감지: 클릭할 때 같은 주소로 `HEAD` 를 한 번 보내고(§3-4), 응답이 실패거나 네트워크 오류면 버튼 아래 InlineAlert danger** (2026-09-07 변경 — 이전의 "10초 자동 복귀" 로는 실패를 알 수 없어 인수조건이 구현되지 않았다. 03 §12) **2026-09-21 — 이 클릭 처리는 공용 훅 `useDownloadStart`(03 §28) 하나다. 내 악보 "다시 받기" 와 같은 코드를 쓴다: 진행 중 `aria-disabled`, 재클릭은 HEAD 를 더 보내지 않으며, 클릭 후 10초가 지나면 잠금만 풀고(실패로 치지 않는다) 늦게 온 응답은 버린다.** |
| 작곡가 목록 / 상세 | `GET /api/composers` / `GET /api/composers/{id}` + `GET /api/composers/{id}/works?sort&…` |
| 관리 홈 | `GET /api/admin/dashboard` |
| 관리 띠(AdminBanner) | `GET /api/admin/crawl/jobs/active` 를 관리 화면 진입 시 + 30초 간격 |
| 작곡가 관리 | §4-2 ~ 4-5 |
| 곡 관리 / 편집 | §4-6 ~ 4-10, 판본 §5 |
| 판본 모달 | 업로드 §5-1 → 저장 §5-2/5-3(fileId 전달) |
| 대기함 | §5-8 ~ 5-12. 상단에 **"자동 판정 실행"** — 먼저 `dryRun: true` 로 미리보기(규칙별·사유별 숫자 확인 모달) → 확인 누르면 `dryRun: false`. **"자동 판정만 되돌리기"**(§5-12) 버튼은 §5-8-1 `autoJudged.revertibleEditions > 0` 이면 **항상** 보인다(실행 여부·새로고침 무관 — qa 5차 결함 1). 남은 양·닫히는 곡 수 문구도, **되돌리기 확인 모달의 숫자도** 그 값 하나로 만든다(문구는 §5-8-1 문구 표로 확정). 되돌린 직후 재조회가 도착하기 전까지는 버튼·잔량 줄을 감춘다(§5-8-1 2-1 — 옛 잔량을 말하지 않는다·연타 방지). 목록 각 행에는 `autoJudgeSkipReason` 을 사람 말로 바꿔 표시 — **문구는 §5-8 표로 확정**(2026-09-08 senior-dev, `formatAutoJudgeSkipReason()` 한 벌). designer 에게 남은 것은 행 안에서의 자리·색·아이콘뿐이다 |
| 수집 관리 / 진행 | §6-1 → 6-2 → 6-6 을 10초 폴링(RUNNING/PAUSED 일 때만) |

프론트 호출 헬퍼: 공개는 `callPublicApi`, 관리는 `callApi`(토큰 갱신 포함), 업로드는 `authFetch` + FormData(기존 `uploadFiles` 는 `/api/files` 전용이라 `uploadEditionFile` 헬퍼를 `lib/http.js` 에 추가).

---

## 8. 되돌림 (기획·디자인에 확인 요청)

| 대상 | 내용 | 이 문서의 잠정 처리 |
|---|---|---|
| designer (07-A) | 01 §9-1 의 "파일도 함께 받기" 스위치가 화면에 없다 | API 는 `fetchFiles`(기본 true) 필드로 받는다. 07-A 등록 패널에 체크박스 1개 추가 요청 |
| designer (07-A) | 판정 표에 `ATTACH`(등록된 곡에 판본 붙임 — 시드 곡) 표시가 없다 | "수집 예정" 으로 묶되 보조 문구 "등록된 곡에 붙임" 제안 |
| designer (06-A) | "파일 받아오기"는 비동기(202 + 폴링)이며 수집 작업이 돌고 있으면 그 뒤에 처리돼 수 분~수십 분 걸릴 수 있다 | 스피너 유지 + "수집이 끝난 뒤 받아와요" 안내 문구 제안 |
| designer (06-B) | "판정 지침 보기 ↗" — `docs/` 는 배포되지 않는다 | 1차는 링크 생략(정의서에 이미 "없으면 생략" 허용) |
| designer (05-C) | 작곡가 중복 409 의 "보기" 링크 — 에러 응답에 기존 id 를 실을 슬롯이 없다 | 화면이 `GET /api/admin/composers?q=` 로 찾아 링크. 공통 `ErrorResponse` 는 바꾸지 않는다 |
| product-planner | "전체 악보 상위 2개" 파일 수신은 §9-1 의 라이선스 조건(PD/CC0/CC-BY/CC-BY-SA)을 만족하는 판본 중 상위 2개다. NC/ND 판본이 상위여도 건너뛴다 | 그대로 구현 |
| ~~product-planner~~ | ~~인기곡 정렬 "기록 없으면 최근 등록순"~~ | **해소(2026-09-08)** — 기획 §11-3 이 최근 등록순을 폐기했다. 새 규칙은 §3-2 |
| product-planner (2026-09-08) | 기획 §2 F2-5 의 난이도 "(전곡 기준)" · 쪽수 옆 "**N곡 묶음**" 은 **곡 수(숫자)** 를 요구하는데, 시드로 들어오는 값은 문장(`collection_guide`) 하나뿐이라 숫자를 뽑을 수 없다 | 이번 계약은 `scopeNote.codes` 에 `COLLECTION`(묶음 여부) 까지만 넣었다. 숫자가 필요하면 `works.csv` 에 `collection_piece_count` 열을 더하고 `WorkSummaryDTO` 에 필드를 추가해야 한다 — **기획이 "필요하다"고 확인해 주면** 그때 계약에 넣는다(쓰는 곳 없는 필드를 먼저 두지 않는다) |
| ↑ 기술 정리 (senior-dev 2026-09-08) | **둘은 서로 다른 요구다.** ⑴ 난이도 "(전곡 기준)"은 숫자가 필요 없다 — **묶음 여부만** 있으면 되고 그건 이미 `scopeNote.codes ∋ COLLECTION` 으로 계약·구현에 있다(오늘 화면이 바로 붙일 수 있다). ⑵ "N곡 묶음"만 숫자를 요구한다 | **문장 파싱은 불가**하다: 시드 38건의 실제 문구가 `왈츠 3곡`·`연습곡 106곡`·**`3개 악장`(5건 — 곡이 아니라 악장이다)**·`8권 48곡`(숫자 둘)·`프렐류드와 푸가가 이어서`(숫자 없음)로 섞여 있어, 숫자를 뽑아도 **단위가 다르고 어느 숫자인지도 정해지지 않는다**("3곡 묶음"이라고 쓰면 틀린 말이 된다). 필요한 것은 값 **두 개**: `collectionPieceCount:int` + `collectionUnit:PIECE\|MOVEMENT`(서버가 문구를 만들지 않는다는 §2-2 원칙 유지 — 화면이 "3곡 묶음"/"3개 악장"을 조립). 작업량은 `collection_guide` 와 같은 경로 그대로: `works.csv` 열 2개 → `work` 컬럼 2개(nullable=추가형이라 마이그레이션 없음) → 01_ERD §6 백필 1회(이미 적재된 시드 곡은 그냥 두면 영원히 NULL) → `WorkSummaryDTO` 필드 2개. **기획이 정해 줄 것: 38곡의 숫자·단위 표(문장에서 옮겨 적으면 된다)와, 수집·관리자 등록 곡처럼 값이 없는 곡의 표시(줄 생략으로 충분한가)** |
| ↑ **해소 (senior-dev 2026-09-08, 기획 §12-2 결론 반영)** | **계약 변경 없음.** 1차는 "(전곡 기준)" 만 넣고 "N곡 묶음" 은 2차로 미뤄졌으므로 `collectionPieceCount`·`collectionUnit` 은 **만들지 않는다**(쓰는 곳 없는 필드를 먼저 두지 않는다 — 위 판단 유지). "(전곡 기준)" 이 요구하는 값은 "이 곡이 묶음인가" 하나뿐이고 이미 `scopeNote.codes ∋ COLLECTION`(§2-2-1) 으로 내려가고 있다 | 남은 것은 **화면 문구**다: 곡 카드(full)의 난이도·쪽수 줄 끝에 `(전곡 기준)` 을 **한 번만** 붙인다(§2-2-1 아래 화면 규칙). Red: `WorkCard.test.jsx`. compact 변형(홈 인기곡·같은 작곡가의 다른 곡)과 곡 상세는 **이번 범위 밖** — designer 확인 대기 |
| product-planner (2026-09-08) | 기획 §10-6 "한국어 제목에 이미 작품번호가 들어 있으면 괄호째 생략"이 **아직 계약·구현에 없다** — 지금도 `쇼팽 - Nocturnes, Op.9 (Op.9).pdf` 가 나온다(`DownloadFileNameTest` 가 그 값을 기대하고 있다) | 이번 §3-4 개정 범위(편곡·악장 접미사)와 별건이라 손대지 않았다. "제목에 작품번호가 들어 있다"의 판정 기준(정규화 비교? 부분 문자열?)을 정해야 계약이 되므로 별도 건으로 올린다 |
| ↑ 기술 정리 (senior-dev 2026-09-08) | **시드 데이터에도 이미 있다** — `title_ko` 자체에 작품번호가 든 곡이 50곡 중 10곡이다(`녹턴 Op.9`, `왈츠 Op.64`, `소나티네 Op.36`, `즉흥곡 Op.90 (D.899)` …). 즉 titleKo 가 없어 원어 제목을 쓰는 곡(qa 예시)만의 문제가 아니라 **정상 곡에서도 `쇼팽 - 녹턴 Op.9 (Op.9).pdf` 가 된다.** qa 실측 42곡 중 11곡(26%) | 구현은 §3-4 파일명 조립에 조건 한 줄이면 된다(비용 작음). **기획이 정해 줄 것은 판정 규칙뿐**: ⓐ 비교 대상은 "파일명에 실제로 쓰인 제목"(한국어 없으면 원어) ⓑ 비교 방식은 `SearchNormalizer`(01_ERD §2) 정규화 후 **부분 문자열 포함**을 제안한다 — `Op.9`/`Op. 9`/`op9` 를 같게 본다 ⓒ **부분 일치의 경계**를 정해야 한다: 제목 `녹턴 Op.9` + 대표 작품번호 `Op.9 No.2` 는 포함이 아니라 `(Op.9 No.2)` 가 그대로 붙고, 반대로 제목 `Op.9 No.2` + 작품번호 `Op.9` 는 생략된다. **"제목이 작품번호보다 더 자세하면 생략, 덜 자세하면 유지"** 가 이 규칙의 실제 동작이며 이대로 좋은지 확인이 필요하다 ⓓ 확정되면 `DownloadFileNameTest` 기대값 갱신 + Red 추가는 senior-dev 가 한다 |
| ↑ **해소 (senior-dev 2026-09-08, 기획 §12-1 결론 반영)** | 괄호 생략 3조건·빈 값 원어 대체를 §3-4 계약에 넣고 Red 를 박았다(`DownloadFileNameTest` 기대값 갱신 + `DownloadCatalogOmissionIntegrationTest` 신설). 판정은 `SearchNormalizer` 정규화 후 부분 문자열 포함 + **뒤 숫자 경계 검사** | **기획 §12-1 서술과 실데이터가 두 곳에서 어긋난다 — planner·qa 확인 필요.** ⑴ **#6 안나 막달레나**: 기획이 근거로 든 `BWV Anh.113–132 (1725년 수첩)` 는 `03` §1 표기이고, 시드(`works.csv`)에 실제로 들어 있는 값은 괄호 없는 `BWV Anh.113–132` 다. 그래서 ③이 걸리지 않고 `바흐 - 안나 막달레나 바흐를 위한 음악 수첩 (BWV Anh.113–132).pdf` 가 된다 — #16 인벤션 `(BWV 772–786)` 과 같은 모양이라 규칙상 일관되지만, `03` §11 이 적은 결과(괄호 없음)와는 다르다. 곡 데이터에 `(1725년 수첩)` 을 넣기로 하면 그 순간 ③이 걸려 기획대로 된다. ⑵ **#31·#32·#50 (드뷔시 3곡)**: 작품번호가 `CD 74`+`L.66` 처럼 **2행**이라 ③에 걸려 괄호가 사라진다(`드뷔시 - 두 개의 아라베스크.pdf`). `03` §11 은 이 3곡을 "나머지는 그대로" 로 적었다. 규칙 원문("작품번호가 여러 개이거나")을 그대로 구현한 결과이고 결과 문자열도 읽기 좋으므로 **규칙을 따랐다** — 세는 방법을 바꾸려면(예: 3개 이상일 때만) 기획이 되돌려 달라. 규칙을 둘로 만들지 않기 위해 임의 임계값은 두지 않았다 |
| designer (05-E, 2026-09-08) | 화면 E "곡 등록·수정" 폼에 **`수록곡 안내`(`collectionGuide`) 칸이 없다.** §4-7 응답·§4-8 요청에는 있고 시드가 38곡에 채워 두었는데 화면이 다루지 않아, 관리자가 다른 칸만 고쳐 저장해도 **전체 교체 PUT 이 그 값을 null 로 덮는다**(백필도 안 되는 영구 유실 + 검색의 `scopeNote.COLLECTION` 줄 동반 소멸) | senior-dev 가 **입력 칸 있음**으로 잠정 확정하고 Red 를 박았다(`WorkFormPage.test.jsx` — 라벨 `수록곡 안내`, 왕복·비우면 null). 위치는 `악장 페이지 안내` 아래 제안, 컨트롤은 여러 줄 문장이므로 `textarea` 제안, 상한 500자(§0-6). **designer 가 정할 것: 라벨 문구 · 컨트롤(`.form-input` 1줄 vs textarea) · 배치.** 라벨이 바뀌면 senior-dev 가 테스트를 맞춰 고친다 |
| ↑ **도움말 문구 확정 (senior-dev 2026-09-09, qa 5차 결함 2)** | 칸은 붙었는데 **도움말이 없어** 관리자가 무엇을 적는 칸인지 알 수 없었다(바로 위 `악장 페이지 안내` 에는 있어 대비가 뚜렷). 인수 조건 기획 §6 이 요구하는 항목인데 **테스트로 옮겨지지 않은 자리**였다 — 왕복은 7케이스로 촘촘한데 문구 검증이 하나도 없었다 | designer 로 넘기지 않고 확정했다. **새 문구를 짓는 일이 아니라 기획 §3 F5-2 가 이미 따옴표로 적어 둔 문장을 제품 말투로 맞추는 일**이고, 인수 조건 §6 도 "취지의 도움말" 로 열어 뒀다. 확정 문구: `여러 곡·여러 악장이 한 PDF 에 들어 있는 곡이면 적어 주세요 — 이 줄이 있어야 사용자 화면에 "(전곡 기준)"이 붙어요`. 뒷절을 남긴 이유는 이 칸이 **사용자 화면을 바꾼다는 사실**(§2-2-1 `scopeNote.COLLECTION`)이 관리자가 채울 유일한 동기이기 때문이다. Red: `WorkFormPage.test.jsx`(문구 · 그 칸의 `.form-group` 안에 있을 것 · 새 곡 화면에도 있을 것) |
| designer (03·06-A, 2026-09-08) | **"추천 판본 미검수" 가 화면정의 전체에 0건이다.** 기획 §F6-4·§8-17 이 인수 조건("미검수 0곡")으로 요구하는데 관리 홈 카드·곡 목록 뱃지·곡 편집의 "확인함" 버튼이 어느 정의서에도 없다 | 계약은 확정했다(§4-1 카드 `needsRecommendationReviewWorks`, §4-6 필터·목록 필드, §5-6-1 API). **화면 세 자리**(관리 홈 카드 / 곡 목록 "미검수" 뱃지 / 곡 편집 판본 목록의 "확인함" 토글)의 위치·문구를 정의서에 넣어 주면 그대로 붙는다 |
| designer (05-D, 2026-09-08) | 화면정의 05 §D 의 **상태 필터 선택지가 7개(전체 + 6종)** 로 적혀 있어 §4-6 에 신설된 `NEEDS_RECOMMENDATION_REVIEW` 가 빠져 있다. 관리 홈 카드가 `?status=NEEDS_RECOMMENDATION_REVIEW` 로 보내는데 select 에 그 항목이 없으면 **서버 필터는 걸렸는데 화면은 "전체" 로 보인다**(관리자는 목록이 왜 짧은지 알 수 없다) | senior-dev 가 **문구 `추천 판본 확인 필요`** 로 잠정 확정하고 Red 를 박았다(`WorkAdminListPage.test.jsx` — 값·문구·순서를 목록으로 단언). 이 말을 고른 이유는 **관리 홈 카드(§4-1)와 같은 말**이어야 관리자가 자기가 누른 필터가 걸렸음을 알 수 있어서다. 순서는 §4-6 enum 순서(보완 필요 다음, 숨김 앞). 정의서 §D 표를 8개로 갱신해 달라 — 다른 문구를 원하면 되돌려 주면 테스트를 맞춰 고친다 |
| designer (06-C, 2026-09-09) | 대기함 상단 자동 판정 패널에 **"자동 판정만 되돌리기" 버튼이 상시 노출**되고(§5-8-1), 그 옆에 잔량 한 줄이 새로 생긴다 — 정의서에 없는 요소다 | 계약과 문구는 senior-dev 가 확정했다(§5-8-1 화면 계약): 노출 조건 `revertibleEditions > 0`, 문구 `되돌릴 수 있는 자동 판정 N개` + `· 되돌리면 M곡의 다운로드가 닫혀요`(M>0 일 때만). **designer 가 정할 것은 표현뿐**이다: 이 줄이 버튼 옆인지 아래인지, 회색 보조 텍스트인지 경고색인지, 버튼을 위험(danger) 계열로 볼지. Red 는 "그 말이 화면에 있다" 까지만 잠갔으므로(`CopyrightPendingPage.undoEntry.test.jsx`) 어느 배치를 골라도 테스트는 그대로 쓴다. **2026-09-09 추가**: 되돌리기 **확인 모달 본문**도 문장뿐이던 것을 숫자로 바꿔 문구를 확정했다(§5-8-1 문구 표 — 실행 경로의 `dryRun` 미리보기 모달과 기준을 맞춘 것). 여기서도 designer 가 정할 것은 표현(줄바꿈·강조·버튼 색)뿐이다 |
| designer (06-C, 2026-09-08) | 대기함 행별 `autoJudgeSkipReason` 표시가 정의서에 없다(§7 이 요구) | **문구는 §5-8 표로 확정**(근거는 그 절). designer 가 정할 것은 **행 안에서의 표현**뿐이다: 별도 열인가 편집자·IMSLP 표기 아래 보조 줄인가, 회색 텍스트인가 칩인가, 아이콘을 붙일 것인가. Red 는 "그 행 안에 그 말이 있다" 까지만 잠갔으므로(`CopyrightPendingPage.skipReason.test.jsx`) 어느 배치를 골라도 테스트는 그대로 쓴다 |
| designer (03, 2026-09-08) | 곡 상세 "다른 판본" 영역 맨 아래 **"IMSLP 에는 이 곡의 다른 악보가 N개 더 있어요 — IMSLP 에서 보기 ↗"** 한 줄(기획 §F3-4)이 정의서에 없다 | 계약은 `imslpOnlyCount` 로 확정(§3-3). 파일 있는 판본 0개일 때는 접이식 헤더 없이 이 줄만, `imslpOnlyCount == 0` 이면 영역 자체 없음 — 이 두 상태의 시안이 필요하다 |
| designer (03, 2026-09-08) | **판정 안 된 판본의 미리보기 문구를 "다른 판본" 줄에서도 보여줄지.** 기획 §5 예외표는 "곡 상세" 라고만 한다 | 잠정: **추천 판본 카드에서만** 회색 상자 + 문구를 보이고, 줄(row)에서는 썸네일 자리를 비운다. 근거는 같은 줄의 저작권 뱃지가 이미 그 사실을 말하고(중복), 긴 문구가 작은 썸네일 칸을 깨뜨린다는 것. 다르게 원하면 되돌려 달라 |
| product-planner (2026-09-08) | 기획 §10-10 **"IMSLP 에 N개 더" 의 숫자를 그대로 보일지** 가 아직 열려 있다 | 계약은 숫자를 **주고**(`imslpOnlyCount`) 쓸지는 화면이 정한다. 숫자를 감추기로 해도 계약은 그대로면 된다(0 인지 아닌지는 어느 쪽이든 필요하다) |

---

## 9. 악기 구분·검색 기준 — 되돌림 / 확정 기록 (2026-09-10 senior-dev)

### 9-1. product-planner 에게 되돌림 — **인수 조건 8-D 8 의 기대값이 우리 데이터와 어긋난다**

기획 04 §4-2 는 `"곡명"으로 "쇼팽 녹턴"을 치면 0건이 정상`이라 적었고, 인수 조건 8-D 8 이 그대로 검사한다. **우리 시드에서는 0건이 아니다.**

- 근거: `src/main/resources/seed/works.csv` 의 쇼팽 곡 **9곡 전부**가 `쇼팽 녹턴`·`쇼팽 왈츠 6번`·`쇼팽 에튀드` 처럼 **작곡가 이름이 들어간 별칭**을 갖고 있다(seq 13·23·24·25·26·38·39·40·41·42). 별칭은 계약상 **곡명 칸**이다(§3-1 표 — 기획 §4-2 가 그렇게 배분했다).
- 그래서 `in=TITLE&q=쇼팽 녹턴` 은 "쇼팽" 도 "녹턴" 도 **별칭에서** 걸려 녹턴 두 곡이 나온다. **계약대로 동작한 결과이지 버그가 아니다.**
- **계약을 바꾸지 않았다.** 별칭을 곡명에서 빼면 `"월광"`·`"강아지 왈츠"` 같은 이 서비스의 존재 이유가 함께 사라진다.
- **planner 가 정할 것**: ⑴ 인수 조건 8-D 8 의 예시를 **작곡가 원어 표기**로 바꾼다(`"곡명"으로 "Chopin 녹턴" → 0건` — 시드에 그 글자가 든 곡명·별칭이 하나도 없어 안정적이다), 또는 ⑵ 큐레이션에서 "쇼팽 …" 별칭을 걷어낸다(권하지 않는다 — 사용자가 실제로 그렇게 친다).
- **qa 에게**: 그때까지 8-D 8 은 실패로 보이는 것이 정상이다. 같은 이유로 **화면정의 08 §4-7 [B] 의 예시 문구** `'쇼팽 녹턴'을 곡명에서 찾지 못했어요` 는 **시안일 뿐**이고, 그 검색어로는 [B] 화면이 재현되지 않는다.
- 테스트가 잠근 것: `WorkSearchScopeIntegrationTest.AndRuleWithinScope` — AND 규칙을 **"두 단어 결과 = 각 단어 결과의 교집합"** 으로 단언해 큐레이션 문구에 기대지 않게 했고, 0건 예시는 `Chopin 녹턴` 으로 잡았다.

### 9-2. senior-dev 가 확정한 것 (물어보지 않아도 되는 값들)

| 항목 | 확정 | 근거 |
|---|---|---|
| 구분 주소 | 경로 첫 세그먼트 `/piano` `/violin` `/orchestra` | 03 §21 |
| 검색 기준 쿼리 이름 | **`in`**, 값 `ALL/TITLE/COMPOSER` | `scope` 는 이미 판본 필드(`EditionScope`)이자 `scopeNote` 라 응답 안에서 두 뜻이 부딪힌다. `field` 는 `errors[].field` 와 겹치는 말이다. `?q=월광&in=TITLE` 은 주소가 문장으로 읽힌다(값 표기는 대문자 enum — line 482) |
| 알 수 없는 `in` | 200 + `ALL` (관용) | 기획 §4-5 |
| 알 수 없는 `section` | 400 | §0-7 |
| 0건 [B] 재료 | `totalInAll`(정수 또는 null) — designer 의 "있다/없다 비트" 최소 요구보다 한 단계 위 | §3-1 |
| 준비 중 → 피아노 안내 줄 표시 | 화면 전용 쿼리 **`from=violin|orchestra`** | §0-5 |
| 탭 제목 갱신 | 라이브러리 없이 `useDocumentTitle` 훅 | 03 §21-7 |
| `work.section` 을 바꾸는 API | **만들지 않는다**(1차) | 01_ERD §9-2, 기획 §9 8-7 |

### 9-3. 이번 계약에서 **하지 않은** 것

- 관리 API 에 `section` 추가 — 기획 04 §9 8-7 이 "구분을 여는 시점의 과제" 로 미뤘다. 그래서 관리 화면은 **한 픽셀도 바뀌지 않는다**.
- 숨김 곡의 구분 배정·관리자 숨김 해제 경고(기획 §9 8-8) — 범위 밖.
- 구분을 4개 이상으로 늘리는 것 — 기획 §9 8-1 이 3개로 확정.
- `WorkSummaryDTO.section` — 쓰는 곳이 없다(§0-7).
- **`frontend/src/test/fixtures.js` 갱신** — `searchResponse()` 에 `in`·`totalInAll` 이, `workDetail()` 에 `section` 이 아직 없다. 그 파일에 **커밋되지 않은 이전 라운드 변경**이 있어 이번에 손대지 않았고, 새 테스트는 픽스처 위에 필드를 얹어 쓴다(`SearchResultPage.scope.test.jsx` 의 `response()`). **커밋 직후 senior-dev 가 픽스처를 명세에 맞춘다.**

---

## 10. 회원 개인화 API — 즐겨찾기 · 내 악보 (2026-09-20 신설)

> 기준: 기획 `05_즐겨찾기_받은악보_최근본곡_기획서.md` §1·§2·§3·§5·§7·§9(인수 조건 60개), 화면정의 `09_내악보_즐겨찾기_받은악보.md`(§6 S1~S7) · `03_곡상세` §3-6 · `01_홈`.
> 최근 본 곡은 **로그인과 무관**하므로 회원 API 가 아니다 — 공개 §3-9 에 있다.

### 10-0. 이 절 전체의 공통 규칙

| 항목 | 계약 |
|---|---|
| 경로 | 전부 `/api/me/**` |
| **사용자 식별** | **주소·쿼리·본문 어디에도 사용자 id 를 받지 않는다.** 주체는 토큰(SecurityContext)에서만 꺼낸다(컨벤션 §4-1) — 그래서 "남의 것 보기" 는 **시도할 주소가 없다**(인수 조건 8-C 9). 관리 API 에도 "누가 뭘 받았나" 를 주는 문을 만들지 않았다 |
| 인증 | **로그인만 하면 된다**(USER·ADMIN 동일). 비로그인은 **401 `NOT_AUTHENTICATED`** — `SecurityConfig` 의 `anyRequest().authenticated()` 가 그대로 받는다. **`/api/me/**` 를 permitAll 목록에 넣지 않는 것이 계약이다** |
| 숨김 곡 | 목록·숫자·상태 판정 어디에도 나오지 않는다(기획 §F2-6). 행은 지우지 않으므로 숨김이 풀리면 **그 자리로 돌아온다**(인수 조건 8-C 7·8-D 10) |
| `section` | §0-7 그대로 — 생략 시 `PIANO`, 정의되지 않은 값 400. 내 악보는 **구분 안의 화면**이다(기획 05 §5-1) |
| 페이지 | `page`(0-base) · `size`(기본 20, 최대 100) — §0-4 그대로. 화면은 `size` 를 보내지 않는다. **범위 밖 페이지는 두 탭 모두 200 + 빈 목록**(§0-4 공통 규칙, `page=2147483647` 까지) — `counts` 와 `totalElements` 는 그대로 실제 수다 |
| 삭제·비우기 | **없다**(기획 05 §3-4, 인수 조건 8-D 9). 받은 악보를 지우는 API 를 만들지 않는다 |

### 10-1. `PUT /api/me/favorites/{workId}` — 즐겨찾기 켜기 / `DELETE` — 끄기

| 메서드 | 성공 | 본문 |
|---|---|---|
| PUT | **200** | `data: { "workId": 21, "favorited": true }` |
| DELETE | **204** | 없음(관리 DELETE 관례와 같다) |

| 조건 | 결과 |
|---|---|
| 비로그인 | 401 `NOT_AUTHENTICATED` |
| 없는 곡 · **숨김 곡** | 404 `NOT_FOUND` — `"곡을 찾을 수 없어요"`(§0-2 문구 규칙) |
| 이미 켜져 있는데 다시 PUT | **200**. 행을 만들지 않고 **`created_at` 도 갱신하지 않는다**(목록 순서가 바뀌면 "최근에 넣은 순" 이 거짓말이 된다) |
| 꺼져 있는데 DELETE | **204**(없던 것을 껐다 — 오류가 아니다) |
| 준비 중·이용 제한·확인 중 곡 | **된다.** 다운로드 버튼이 없어도 즐겨찾기는 켜진다(기획 05 §1-4, 인수 조건 8-A 4) |
| 요청 본문 | **없다.** 토글이 아니라 **상태를 지정**하는 두 문이다 |

- **왜 토글(`POST /toggle`)이 아닌가 — 이 결정이 인수 조건 8-B 4 를 지탱한다.** 비로그인 → 로그인 → 복귀 완성(03 §22)은 "돌아와서 한 번 켠다" 인데, 새로고침·중복 실행이 **한 번이라도** 더 일어나면 토글은 **꺼진다.** 멱등한 PUT/DELETE 면 몇 번 불려도 결과가 같아서, 화면의 중복 방지(§22 의 take)와 계약의 멱등성이 **이중으로** 같은 것을 지킨다.
- 부수효과는 즐겨찾기뿐이다 — **다운로드 수는 오르지 않는다**(인수 조건 8-A 6).
- 계약 검증: `FavoriteApiIntegrationTest`.

### 10-2. `GET /api/me/library/favorites?section=PIANO&page=0` — 내 악보 › 즐겨찾기 탭

```json
{ "success": true, "message": "성공", "data": {
  "counts": { "favorites": 12, "downloads": 5 },
  "works": { "content": [ WorkSummaryDTO… ], "page": 0, "size": 20, "totalElements": 12, "totalPages": 1, "first": true, "last": true }
} }
```

| 항목 | 계약 |
|---|---|
| 정렬 | **`work_favorite.created_at DESC, work_favorite.id DESC`** — "최근에 넣은 순"(기획 05 §2-2). id 를 2순위로 두는 이유: 같은 밀리초에 두 개를 넣어도 순서가 확정된다(테스트가 순서를 단언할 수 있어야 한다) |
| 담기는 것 | 그 계정 · **그 구분** · 숨김 아닌 곡. `WorkSummaryDTO` 는 다른 목록과 **한 글자도 다르지 않다**(`matchedAlias` 는 null, `scopeNote` 는 §2-2-1) |
| `counts` | **두 탭의 숫자를 늘 함께 준다**(화면정의 09 §6 S3) — 어느 탭에 있든 탭 머리의 숫자 2개를 그려야 하기 때문이다. 값은 **숨김 제외 전체 수**이고 `page`·`size` 와 무관하다. `counts.favorites` 는 이 응답 `works.totalElements` 와 **항상 같다** |
| 페이지 범위 밖 | 200 + 빈 `content`(`totalElements` 는 그대로) — 화면이 "이 페이지에는 곡이 없어요" 를 그린다(09 상태표). **`page` 값의 크기와 무관하다**(§0-4): `2147483647` 도 200 이고 `page` 는 요청한 값을 그대로 되비춘다. 검증 `MyLibraryPageBoundsIntegrationTest` |

### 10-3. `GET /api/me/library/downloads?section=PIANO&page=0` — 내 악보 › 받은 악보 탭

**곡 단위 한 줄**이다(기획 05 §3-2). 같은 곡을 몇 번 받아도 한 줄이고, 줄의 날짜는 **가장 최근에 받은 날**이다.

```json
{ "data": {
  "counts": { "favorites": 12, "downloads": 5 },
  "items": { "content": [
    { "work": { WorkSummaryDTO… },
      "downloadedAt": "2026-09-12T08:12:00Z",
      "receivedEdition": { "id": 301, "kind": "COMPLETE_SCORE", "scope": "COMPLETE", "movementNumber": null, "pageCount": 5, "fileSize": 1153434 },
      "redownloadState": "AVAILABLE",
      "redownloadUrl": "/api/editions/301/download",
      "alternativeEdition": null }
  ], "page": 0, "size": 20, "totalElements": 5, "totalPages": 1, "first": true, "last": true }
} }
```

`ReceivedWorkDTO`

| 필드 | 타입 | 설명 |
|---|---|---|
| work | WorkSummaryDTO | 곡 카드. 다른 목록과 같다 |
| downloadedAt | string | 가장 최근에 받은 시각(ISO UTC). 화면이 `오늘 받음`·`9월 12일 받음` 으로 옮긴다(00 §4 날짜 규칙) |
| receivedEdition | EditionBriefDTO\|null | **"받은 판본: …" 줄의 재료**(§2-4). 아래 "무엇을 담나" |
| redownloadState | `AVAILABLE\|RECOMMENDATION_CHANGED\|UNAVAILABLE` | 화면 3상태(09 §1-2-2 ①②③) |
| redownloadUrl | string\|null | **"다시 받기" 버튼이 실제로 여는 주소.** ①② 는 그때 받은 판본, ③-a 는 **지금 추천 판본**, ③-b 는 `null` |
| alternativeEdition | EditionBriefDTO\|null | **③-a 일 때만** 값이 있다 — "지금 추천 판본: …" 줄. 그 밖에는 `null` |

**상태 판정 (한 함수로 계산한다)**

```
received            = user_work_download.last_edition_id 가 가리키는 판본 (삭제됐으면 null)
receivedDownloadable= received != null && received.pdf_file_id != null && received.korea_copyright == FREE
recommended         = work.recommended_edition
recommendedDownloadable = recommended != null && recommended.pdf_file_id != null && recommended.korea_copyright == FREE

receivedDownloadable && received.id == recommended?.id  → AVAILABLE               (①)
receivedDownloadable                                    → RECOMMENDATION_CHANGED  (②)
그 밖                                                   → UNAVAILABLE             (③)
    └ recommendedDownloadable  → redownloadUrl = 추천 판본, alternativeEdition = 추천 판본 (③-a)
    └ 아니면                    → redownloadUrl = null, alternativeEdition = null        (③-b)
```

- **`RECOMMENDATION_CHANGED` 에는 "추천이 아예 없어진 곡" 도 든다.** 그때 받은 판본은 여전히 줄 수 있으니 버튼은 그대로이고, 화면의 한 줄(`지금 추천 판본은 이것과 달라요 — 곡 보기`)이 "지금 이 곡은 그 판본을 권하지 않는다" 는 사실을 말한다. 상태를 넷으로 쪼개지 않는 이유: 화면이 하는 일이 같다(버튼 + 한 줄 + 곡 보기).
- **`receivedEdition` 이 무엇을 담나** — **판본이 살아 있으면 지금 값, 삭제됐으면 스냅샷**(01_ERD §3-12). 이 줄은 "누르면 무엇이 오는가" 를 말하는 줄이므로(화면정의 09 §1-2 D7), 파일이 교체돼 쪽수·크기가 달라졌으면 **지금 값**이 옳다. 스냅샷은 말할 수 없게 됐을 때의 폴백이고, 그래서 ③에서도 줄이 남는다(09 §6 S4). 스냅샷조차 비어 있으면 `null` → 화면이 줄을 생략한다.
- **"이용 제한" 판본은 어떤 경로로도 주지 않는다**(기획 05 §3-3, 인수 조건 8-D 8): 판정이 `FREE` 가 아니면 `receivedDownloadable` 이 false 라 `redownloadUrl` 이 만들어지지 않고, 주소를 손으로 만들어 불러도 §3-4 가 403 이다. **게이트는 두 겹이되 판정 규칙은 한 곳(§3-4)이다.**
- 정렬 **`last_downloaded_at DESC, id DESC`**. 숨김 곡 제외, 그 구분만. `counts.downloads` 는 이 응답 `items.totalElements` 와 항상 같다.
- **페이지 범위 밖은 200 + 빈 `content`** — §10-2 와 한 글자도 다르지 않다(§0-4 공통 규칙). `page` 가 `2147483647` 이어도 200 이고, `counts`·`items.totalElements` 는 그대로 실제 수다. 검증 `MyLibraryPageBoundsIntegrationTest`.
- **"다시 받기" 성공을 화면이 아는 수단(09 §6 S7)**: 곡 상세와 **같은 방식**이다 — `<a href download>` + 같은 주소로 `HEAD` 한 번(§3-4). 200 이면 화면이 그 줄의 날짜를 `오늘 받음` 으로 바꾸고, 실패면 그 항목 아래 안내를 띄운다(09 §1-2-1). **계약은 한 줄도 늘지 않는다**(전용 "다시 받기" 엔드포인트를 만들지 않는다 — 다운로드 문이 둘이 되면 저작권 게이트도 둘이 된다).
  - **2026-09-21 — 같은 방식이라는 말은 곧 같은 코드다**: 두 화면 모두 공용 훅 `useDownloadStart`(03 §28)를 쓴다. 진행 표시(스피너·`aria-disabled`)·재클릭 차단·10초 잠금 해제가 두 화면에서 같고, **클릭 후 10초가 지나 도착한 200 은 버린다** — 그 시점에 날짜를 `오늘 받음` 으로 바꾸면 사용자가 손을 뗀 목록이 혼자 움직인다.
- 계약 검증: `MyLibraryDownloadApiIntegrationTest`, `MyLibraryPage.downloads.test.jsx`, `MyLibraryPage.downloadProgress.test.jsx`.

### 10-4. 화면 주소 · 로그인 "이유" 쿼리 — **확정** (화면정의 09 §6 S1·S2, 00 §8)

| 항목 | 확정값 |
|---|---|
| 내 악보 | **`/{구분}/library`** → **즐겨찾기 탭으로 `replace` 리다이렉트**(히스토리에 남기지 않는다) |
| 즐겨찾기 탭 | **`/{구분}/library/favorites`** (+ `?page=`) |
| 받은 악보 탭 | **`/{구분}/library/downloads`** (+ `?page=`) |
| 헤더 "내 악보" 링크 | **`/{현재 구분}/library/favorites`** (작곡가 메뉴와 같은 링크 규칙, 00 §8) |
| 헤더 활성 판정 | 현재 경로가 **`/{구분}/library`** 로 시작하면 "내 악보" `.active`(두 탭 모두) |
| 준비 중 구분 아래 | `/violin/library*` 는 **찾을 수 없는 페이지**(00 §2-4 공통 갈래) — 준비 중 구분에 하위 경로가 없다는 기존 규칙 그대로. 라우트를 따로 만들지 않으면 `*` 로 떨어져 저절로 그렇게 된다(인수 조건 8-F 5) |
| 그 밖의 탭 이름 | `/piano/library/xyz` 도 찾을 수 없는 페이지(라우트를 두 개만 등록한다) |
| 로그인 "이유" | 화면 전용 쿼리 **`reason`** — 값 **`favorite`** / **`library`**. 알 수 없는 값·없음은 **기존 부제**(오류 아님) |
| 로그인 복귀 | 기존 **`redirect`** 그대로 — `/login?redirect=%2Fpiano%2Fworks%2F21&reason=favorite` |

- **왜 `/library` 인가**: 주소 어휘는 소문자 영어 슬러그다(`search`·`works`·`composers`). `my` 는 무엇의 "내 것" 인지 말하지 않고, `favorites` 를 최상위로 올리면 받은 악보 탭이 그 아래에 설 자리가 없다. 헤더 아이콘도 이미 `library_music` 이다(00 §2-1).
- **왜 `/library` 가 탭 주소를 겸하지 않고 리다이렉트하나**: 탭마다 자기 주소여야 "주소를 복사해 새 탭에 붙이면 같은 탭이 열리고, 탭을 바꾼 뒤 뒤로 가기를 누르면 이전 탭으로 돌아간다"(인수 조건 8-F 3)가 성립한다. 한 화면에 주소가 둘이면 **뒤로 가기 결과가 진입 경로마다 달라진다.** `replace` 라 히스토리에 흔적이 없다(옛 주소 리다이렉트와 같은 방식, §0-5).
- **`reason` 이 대문자 enum 이 아닌 이유**: API 로 가지 않는 **화면 전용 쿼리**라 `from=violin` 과 같은 규칙을 따른다(§0-5). 서버는 이 값을 본 적이 없다.
- **비로그인이 내 악보 주소로 들어오면**: 화면을 그리지 않고 `/login?redirect={들어오려던 탭 주소}&reason=library` 로 `replace`(관리 딥링크 가드와 같은 방식, `AdminRoute`). 로그인 후 **그 탭으로** 돌아온다(인수 조건 8-C 8).
- **"이 곡을 즐겨찾기하려 했다" 를 들고 갔다 오는 수단**(기획 05 §11 ②, 09 §6 S2)은 **주소가 아니라 `sessionStorage` 한 칸**이다 — 카카오·구글 왕복에서 `redirect` 쿼리가 살아남지 못하기 때문이다(`OAuth2LoginSuccessHandler` 가 `/` 로 보낸다). 규칙 전체는 `03_기술결정.md` §22. **백엔드는 한 줄도 바뀌지 않는다.**

### 10-5. 이번 계약에서 **하지 않은** 것

- 곡 카드(`WorkSummaryDTO`)의 즐겨찾기 표시 — 기획 05 §10 8-6 이 1차 제외로 확정(인수 조건 8-G 5).
- "N명이 즐겨찾기" 집계 — 8-G 6.
- 받은 악보 삭제·즐겨찾기 폴더/메모/정렬 — 기획 05 §0-3 제외.
- 즐겨찾기 개수 상한 — **두지 않는다**(기획 05 §10 8-5 의 senior-dev 몫을 여기서 닫는다. 근거는 01_ERD §3-11).
- 최근 본 곡의 계정 동기화 — 기획 05 §0-3 제외. 저장은 브라우저뿐이다(03 §23).
- 회원가입 경유 복귀·마이페이지 정비 — 기획 05 §10 8-4·8-8(사용자 작업 ④).

---

## 11. 추천 판본 "왜 이 판본인가" — 확정·되돌림 (2026-09-21 senior-dev)

> 기획 `06_추천판본_선정근거와_변경_기획서.md` · 화면정의 `06_관리자_판본관리.md` A-0~A-3 을 계약으로 옮기면서
> 내린 결정과, 다른 담당에게 되돌리는 것을 한 자리에 모았다. 절 본문은 §4-6 · §4-7-2 · §4-7-1 · §5-5 · §5-6 · §5-6-1 · §5-6-2 · §5-11 · §5-12.

### 11-1. senior-dev 가 닫은 미결 (기획 §9 · 화면정의 "이 문서가 남긴 미정")

| # | 항목 | 결론 | 근거가 적힌 곳 |
|---|---|---|---|
| 기획 6-3 / D2 | **이력 보관 기간·분량** | **보관 기간 없음**(지우는 배치를 만들지 않는다. 줄이 사라지는 유일한 때는 곡 삭제). **응답 분량은 곡 상세 5줄 + `hasMore`, "더 보기" 는 전용 API 로 최대 200줄** | §4-7-1 · §4-7-2 |
| D3 | **메모 길이 상한** | **300자.** 저작권 판정 메모(1000)를 따르지 않는다 — 그쪽은 법적 근거를 **문장**으로 남기는 자리라 2줄 textarea 이고, 이 메모는 designer 가 **한 줄 input** 으로 확정한 "꼬리표"다(화면정의 06 A-3 ④). 한 줄 칸에 1000자 상한은 화면과 어긋난 약속이다. 300 은 `imslp_description`·`file_fetch_error` 와 같은 계열 | §0-6 · §5-6 |
| D4 | **`reviewed:false` 경로를 화면이 안 쓴다** | **계약도 버튼도 유지한다.** 이번 개정으로 "다시 지정으로 풀면 된다" 가 성립하지 않게 됐다(같은 판본 재지정은 검수를 유지하고, 다른 판본 지정은 **거짓 사유**를 남기게 된다) | §5-6-1 |
| D5 | **추천 "해제" 전용 API 가 없다 — 8-B 7 의 "해제" 를 판본 삭제로 읽어도 되나** | **읽어도 된다. 해제 전용 API 를 만들지 않는다.** 추천이 빠지는 길은 셋이고 **전부 근거를 남긴다**: ⑴ 다른 판본 지정(§5-6, `ASSIGNED` 줄) ⑵ 추천 판본 삭제(§5-5, `CLEARED`/`EDITION_DELETED`) ⑶ **추천 판본에서 파일 떼기**(§5-3, `CLEARED`/`EDITION_FILE_REMOVED`). 전용 버튼을 새로 만들면 "해제도 판단" 이므로 사유를 묻는 자리를 하나 더 설계해야 하고, 그건 기획 §0-4 의 1차 포함 목록에 없다 | §5-5 · 01_ERD §3-13 |

### 11-2. designer 에게 되돌리는 것 (2건)

| # | 무엇 | 왜 |
|---|---|---|
| S1 | **화면정의 06 A-1 "확인 해제 버튼은 두지 않는다" 줄을 되돌린다** — 버튼을 **유지**해 주세요(지금 코드에도 있다) | 위 D4. 이번 개정이 그 줄의 근거를 무너뜨렸다. 이 줄을 그대로 두면 frontend-dev 가 버튼을 지우고, 잘못 누른 "확인함" 을 푸는 길이 **거짓 사유를 남기는 길 하나만** 남는다 |
| S2 | **추천이 빠지는 세 번째 길에 문구가 없다** — `CLEARED` / `EDITION_FILE_REMOVED`(§5-3 으로 추천 판본의 파일을 떼면 추천이 풀린다. **이미 그렇게 동작한다**). 화면정의 A-1 "바뀐 이력" 표는 `추천을 뺐어요 — 판본 삭제` 한 가지만 정의했다 | 계약에는 값이 있고 화면에는 문구가 없으면 그 줄이 빈칸으로 그려진다. 제안 문구: `추천을 뺐어요 — 판본에서 파일을 뗐어요` |

### 11-3. 이번 계약에서 **하지 않은** 것

- **사용자 응답은 한 필드도 바뀌지 않는다** (기획 06 §6 — 1차 제외 확정). `WorkDetailDTO`·`WorkSummaryDTO`·`EditionDTO`·다운로드·검색·인기곡·작곡가·내 악보 **전부 그대로**다. 근거를 사용자 API 로 흘리지 않는다. 회귀 가드: `RecommendationHistoryApiIntegrationTest` 의 "사용자 응답에 근거가 없다".
- **자동 선택 규칙 자체를 바꾸지 않는다** (기획 §4 · §9 6-1). `MOST_IMSLP_DOWNLOADS` 하나이고 §5-11 의 후보 조건·정렬은 한 글자도 건드리지 않았다 — 바꾸면 실데이터 42곡의 추천이 통째로 흔들린다.
- **관리 홈(§4-1)에 카드·숫자를 더하지 않는다** (기획 §2-3, 8-A 8).
- **"근거 없는 곡" 필터를 만들지 않는다** — `status` 값이 늘지 않는다(8-E 4).
- **백필을 하지 않는다** (기획 §5). 마이그레이션은 "새로 생기는 것을 담을 자리" 하나뿐이고, 그것도 순수 추가형이라 손으로 실행할 것이 없다(01_ERD §9).
- **"이전 추천으로 되돌리기" 전용 API 를 만들지 않는다** (기획 §3-5). 되돌리는 것도 판단이라 §5-6 의 사유를 거쳐야 한다.
