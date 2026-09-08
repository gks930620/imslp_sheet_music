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
- 파일 크기 바이트(`fileSize`), 쪽수 정수(`pageCount`). "2.4MB"/"12쪽" 포맷은 프론트.
- 미리보기 이미지 URL 은 `previewUrl` = `/uploads/{저장파일명}.png` — **기존 `FileServingController` 프록시** 가 서빙(permitAll, 로컬/버킷 공통). 별도 이미지 API 를 두지 않는다(03 §2).

**`GET /uploads/{저장파일명}` 는 미리보기 PNG 전용이다 (2026-09-08 확정, senior-dev — qa 3차 결함 1).**

| 파일 | `/uploads` |
|---|---|
| 판본 미리보기 PNG (`ref_type=EDITION`, `file_usage=THUMBNAIL`) | 200 |
| 커뮤니티·사용자 파일 (`ref_type=COMMUNITY/USER`, 이미지·첨부) | 200 |
| **판본 PDF** (`ref_type=EDITION` 의 PDF) | **404** — 판정이 `FREE` 여도 |
| `files` 행이 없는 저장 파일명(고아 바이트·없는 이름) | **404** |

- 이 프록시는 저작권 게이트를 태우지 않고 다운로드 수도 세지 않는다. 그래서 판본 바이트는 **§3-4 한 문으로만** 나간다.
  (qa 3차 실측: 비로그인·헤더 없이 `uploads/` PDF 80개·515MB 전량이 열려 있었고, 같은 판본의 §3-4 는 403 이었다.)
- **행이 없는 바이트도 안 준다** — 모든 저장 경로가 `files` 행을 함께 쓰므로, 행이 없는 파일은 우리가 책임지는 파일이 아니다(삭제 뒤 남은 고아 바이트 등).
- **404 는 본문 없이** 준다. `/uploads` 는 `<img src>`·PDF 뷰어가 직접 무는 바이트 엔드포인트라 §0-1 의 JSON 래퍼를 쓰지 않는다(바이트가 없을 때의 404 와 같은 모양이어야 호출자가 분기하지 않는다).
- 계약 검증: `UploadsServingGateIntegrationTest`, 바이트 동일성 회귀는 `FileServingContractIntegrationTest`.

### 0-5. SPA 라우트 등록 방식 (확정)

- `HomeController` 는 경로 열거를 버리고 **catch-all** 로 바꾼다: `GET /` 및 `GET /{첫세그먼트:^(?!api|uploads|assets|images|ws-chat|h2-console|swagger-ui|v3|actuator|oauth2|custom-oauth2|healthz|error)[^.]*}/**` → `forward:/index.html`. (점이 든 세그먼트 = 정적 파일은 제외. `/login/oauth2/**` 는 시큐리티 필터가 먼저 처리.) 새 화면을 추가할 때 HomeController 수정 불필요.
- `SecurityConfig` 에는 화면 셸 GET 을 permitAll 로 명시 추가: `"/search", "/works/**", "/composers/**", "/admin", "/admin/**"`. (`/admin/**` 은 HTML 셸만 공개, 데이터는 `/api/admin/**` 이 막는다.) 기존 `/login, /signup, /mypage, /community/**, /rooms/**` 항목은 그대로.
- 확정 라우트 표는 `docs/화면정의/00_공통_레이아웃_토큰.md` §8 에 반영했다.

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
| POST | `/api/admin/edition-files` | PDF 업로드(쪽수·크기·미리보기 자동) | 06-B |
| POST | `/api/admin/works/{workId}/editions` | 판본 추가 | 06-B |
| GET | `/api/admin/editions/{id}` | 판본 1개(수정 모달·받아오기 폴링) | 06-A/B |
| PUT | `/api/admin/editions/{id}` | 판본 수정(파일 교체 포함) | 06-B |
| DELETE | `/api/admin/editions/{id}` | 판본 삭제 | 06-A |
| PUT | `/api/admin/works/{workId}/recommended-edition` | 추천 판본 지정 | 06-A |
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
| previewUrl | string\|null | 추천 판본 첫 페이지 |
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
| previewUrl | string\|null | |
| publisher, publishYear, plateNumber, editor, arranger, scanner | string\|null (publishYear 는 int\|null) | 있는 것만 표시 |
| koreaCopyright | `FREE\|RESTRICTED\|UNKNOWN` | 뱃지 |
| imslpCopyrightText | string\|null | "IMSLP 표기: …" |
| ccLicenseName, ccAttribution | string\|null | CC 줄 |
| imslpFileUrl | string\|null | "파일 페이지 ↗" / "IMSLP에서 보기" |
| downloadable | boolean | `hasFile && koreaCopyright == FREE` — 버튼 활성 기준 |
| largeFile | boolean | `fileSize >= 20MB`(20 × 1024 × 1024) — 경고 문구 |
| downloadUrl | string\|null | downloadable 일 때 `/api/editions/{id}/download`, 아니면 null |

---

## 3. 공개 API 상세

### 3-1. `GET /api/works/search` — 검색

**쿼리**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| q | string | Y | 검색어. 공백만이면 400 `VALIDATION_ERROR`(field `q`). 최대 100자 |
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

**응답 200**
```json
{ "success": true, "message": "성공", "data": {
  "q": "월광",
  "composers": [ { "id": 4, "nameKo": "베토벤", "nameOriginal": "Beethoven, Ludwig van", "workCount": 5 } ],
  "composerMatchCount": 1,
  "unfilteredTotal": 1,
  "works": { "content": [ WorkSummaryDTO… ], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true }
} }
```
`composers[]` 항목 = `ComposerCardDTO { id, nameKo, nameOriginal, workCount }`. 범위 밖 페이지는 200 + 빈 `content`.

**인수조건 매핑(테스트가 검증할 것)**: "월광"→소나타 14번 / "엘리제를 위하여"·"엘리제"·"Für Elise"·"fur elise" 동일 곡 / "쇼팽 녹턴"→쇼팽 곡만 / "Chopin"="쇼팽" / "Op.27 No.2"="op 27 no 2"="op27no2" / "BWV 846"="BWV846" / "K.545"="K545" / 대소문자 무시 / "녹" 부분 일치 / 숨김 곡 미노출 / 필터 3종 / 20개 페이지.

### 3-2. `GET /api/works/popular?limit=10` — 홈 "지금 바로 받을 수 있는 인기곡"
**2026-09-08 전면 개정 (기획 §11-3 — 이 자리는 순위표가 아니라 견본 진열대다).** `limit` 기본 10, 최대 20, 1 미만이면 400.

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
| aliases | string[] | 원문, id 순. 0개면 `[]`(줄 생략은 화면) |
| compositionYear, musicalKey, movements, movementPageGuide | string\|null | |
| collectionGuide | string\|null | **2026-09-08 추가.** 수록곡 안내(기획 §2 F3-2, 03 §10). 곡 번호 기준 완성 문장을 **그대로** 내려준다(서버가 조립하지 않는다). null/공백이면 화면에서 줄 생략. 이 값이 있는 곡이 **묶음 악보**이고, §2-2-1 `scopeNote.codes` 의 `COLLECTION` 판정 근거다 |
| imslpUrl | string\|null | "출처: IMSLP — 원본 페이지 보기" |
| composerImslpUrl | string\|null | |
| recommendedEdition | EditionDTO\|null | |
| otherEditions | EditionDTO[] | 추천 제외. **파일 있는 판본 전부(편성 무관) + 파일 없는 `kind=COMPLETE_SCORE` 최대 5개**(2026-09-07 개정 → 2026-09-08 편성 조건 추가 — 아래). 각 구간 안 정렬 `imslp_download_count DESC NULLS LAST, id ASC`, 파일 있는 구간이 앞 |
| otherEditionsTotal | int | **2026-09-07 추가.** 추천을 뺀 판본 중 **목록과 같은 모집단**의 수 = 파일 있는 것 전부 + 파일 없는 `COMPLETE_SCORE`. `otherEditions.length` 보다 크면 목록이 잘린 것이다 |
| downloadableOtherCount | int | **전체** 판본 중 downloadable=true 수(잘린 목록 기준이 아니다) — "바로 받을 수 있는 다른 판본이 N개 있어요" |

> **왜 전량을 안 주는가 (2026-09-07 개정, senior-dev).** 설계는 "곡당 판본 몇 개"를 전제로 전량을 내려보냈는데,
> 실제 수집 결과는 **곡 1개당 판본 70개**(20곡 1,792개)다. 그중 우리가 파일을 받는 것은 곡당 최대 2개(01 §9-1)이므로
> 전량을 주면 사용자 화면에 `파일 없음 · IMSLP에서 보기` 행이 68줄 깔린다 — 응답이 수십 KB 로 부풀 뿐 아니라
> **"판본 고민 없이 1개"** 라는 제품 약속과 정면으로 어긋난다. 그래서 (1) 우리가 실제로 줄 수 있는 판본(파일 있음)은
> 전부 내려보내고 — 개수는 우리가 통제하므로 상한이 필요 없다 —, (2) "IMSLP 에 가면 더 있다"는 정보는
> 파일 없는 판본 상위 5개 + `otherEditionsTotal` 숫자로 대신한다.
> 화면(03 §3-3)의 접이식 헤더 개수는 `otherEditions.length`, 잘렸을 때의 안내(`… 외 N개는 IMSLP에서`)는
> `otherEditionsTotal - otherEditions.length` 로 만든다. 계약 검증: `WorkDetailEditionVolumeIntegrationTest`.
>
> **파일 없는 구간은 `kind = COMPLETE_SCORE` 만 담는다 (2026-09-08 개정, senior-dev — qa 3차 결함 5).**
> 실측이 위 가정보다 컸다: 곡당 판본 **최대 207개·평균 79.8개**(응답 127KB)이고 그중 **73~83%가 `ARRANGEMENT`(편곡)** 다.
> 이 목록의 유일한 용도는 "IMSLP 에 가면 더 있다" 는 **안내**인데, 그 5칸이 기타·성악·2대 피아노 편곡 스캔으로 채워지면
> 1차 범위가 **피아노 독주**(기획 §0-2)인 제품에서 **안내 자체가 틀린 정보**가 된다. `PARTS`(파트보)도 뺀다 —
> 파트보가 있다는 건 애초에 앙상블 곡이라는 뜻이다.
> - **파일 있는 판본은 편성과 무관하게 전부 남긴다** — 우리가 실제로 줄 수 있는 것이고, 편곡에 파일이 붙어 있다면
>   관리자가 의도해 붙인 것이다(수집 자동 지정은 `COMPLETE_SCORE` 만 고른다 — 01_ERD §3-3).
> - **`otherEditionsTotal` 도 같은 모집단**으로 센다. 화면은 `total - length` 로 "… 외 N개" 를 만드는데(위),
>   모집단이 다르면 그 뺄셈이 뜻을 잃는다. `downloadableOtherCount` 는 파일 있는 판본 기준이라 영향이 없다.
> - **관리 화면(§4-7)은 그대로 전부 보여 준다** — 관리자는 편곡·파트보도 저작권 판정과 추천 후보 판단을 해야 한다.
> - 계약 검증: `WorkDetailEditionKindFilterIntegrationTest`.
| sameComposerWorks | WorkSummaryDTO[] | 같은 작곡가의 다른 공개 곡, download_count DESC, 최대 5 |

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
  "otherEditions": [ ], "otherEditionsTotal": 0, "downloadableOtherCount": 0,
  "sameComposerWorks": [ WorkSummaryDTO… ]
} }
```

### 3-4. `GET /api/editions/{id}/download` — PDF 다운로드

| 조건 | 결과 |
|---|---|
| 판본 없음 / 곡 숨김 | 404 `NOT_FOUND` |
| `pdfFileId == null` | 404 `NOT_FOUND` ("파일이 없는 판본이에요") |
| `koreaCopyright != FREE` | 403 `COPYRIGHT_RESTRICTED` |
| files 행은 있는데 바이트를 못 읽음 | 503 `FILE_UNAVAILABLE` — **다운로드 수 안 올림** |
| 정상 | 200, `Content-Type: application/pdf`, `Content-Length`, `Content-Disposition: attachment; filename="score-{editionId}.pdf"; filename*=UTF-8''{퍼센트인코딩 파일명}` |

**파일명 규칙(01 §9 8-9 확정, 2026-09-08 접미사 추가)**: `{작곡가 한글 표기 또는 원어 표기} - {한국어 대표 제목 또는 원어 제목}{ (대표 작품번호)}{ 편곡}{ N악장}.pdf`
- 대표 작품번호 = `sort_order = 0`. 작품번호 없으면 괄호째 생략 → `사티 - 짐노페디.pdf`
- 한국어 제목 없으면 원어 제목, 작곡가 한글 없으면 원어 표기.
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

---

## 4. 관리자 API 상세 — 작곡가·곡

모든 관리 API: 비로그인 401, USER 403(§0-2). 아래 표에는 그 외 에러만 적는다.

### 4-1. `GET /api/admin/dashboard`
```json
{ "data": {
  "totalWorks": 312, "readyWorks": 241, "preparingWorks": 38, "needsWorkWorks": 57,
  "unknownCopyrightEditions": 19, "monthlyDownloads": 1204,
  "latestJob": CrawlJobDTO | null,
  "activeJob":  CrawlJobDTO | null
} }
```
- `totalWorks` 는 숨김 포함 전체. `readyWorks/preparingWorks` 는 01_ERD §4 규칙(숨김 포함). `needsWorkWorks` 는 보완 필요 규칙. `monthlyDownloads` 는 Asia/Seoul 기준 이번 달 1일 00:00 이후 `download_log` 수.
- **`needsWorkWorks` 는 01_ERD §4 "보완 필요 계산" 을 그대로 쓴다 — `COPYRIGHT_JUDGMENT` 포함**(2026-09-08, 기획 §11-1). 곡 목록 필터 `status=NEEDS_WORK`(§4-6) · 곡 상세 `missing[]`(§4-7) 과 **반드시 같은 규칙**이다. 세 곳이 한 함수를 공유해야 하고, 한 곳에서 사라진 곡이 다른 곳에 남으면 결함이다.
- **자동 판정 되돌리기(§5-12) 뒤 카드가 어떻게 움직이는가** (2026-09-08 명시): `readyWorks`·`needsWorkWorks`·`unknownCopyrightEditions` 는 **자동 판정 실행 전 값으로 돌아온다**. 다만 `preparingWorks` 는 돌아오지 않는다 — 되돌리기는 추천 지정을 유지하므로 그 곡의 상태가 `PREPARING` 이 아니라 `UNKNOWN` 이기 때문이다. 그래서 그 곡을 관리자 일감에 남기는 일은 `needsWorkWorks` 가 맡는다(그게 `COPYRIGHT_JUDGMENT` 를 추가한 이유다).
- **`monthlyDownloads` 는 판본을 지워도 줄지 않는다**(§5-5, 2026-09-08). 이 숫자는 "이번 달에 몇 번 받아갔나" 라는 **일어난 사건의 수**이고, 같은 달 수치가 나중에 줄어들면 지표가 아니다. 곡 삭제(§4-9)로만 줄어든다.
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
| status | `READY \| PREPARING \| RESTRICTED \| UNKNOWN \| NEEDS_WORK \| HIDDEN` 중 하나. `NEEDS_WORK` 는 01_ERD §4 보완 필요 계산(**`COPYRIGHT_JUDGMENT` 포함**, 2026-09-08) — 다른 값과 달리 `WorkStatus` 가 아니라서, `UNKNOWN` 곡이 `NEEDS_WORK` 목록에도 나오는 것이 정상이다 |
| composerId | long |
| level | `BEGINNER \| ELEMENTARY \| INTERMEDIATE \| ADVANCED \| NONE`(=미정) |
| size | 선택. **기본 20, 최대 200**(초과는 200 으로 자름, 1 미만은 기본 20) — §4-2 와 같은 규칙 |
정렬 `updated_at DESC, id DESC`. 응답 `{ "unfilteredTotal": 312, "works": PageResponse<AdminWorkSummaryDTO> }`

`AdminWorkSummaryDTO`: `{ id, titleKo, titleOriginal, composer: ComposerRefDTO, catalogNumbers, level, editionCount, hasRecommended, status, needsWork, hidden, updatedAt }`

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
  "recommendedEditionId": 301, "candidateEditionId": null,
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
- `editions` 정렬: 추천 → 추천 후보 → 파일 있음(imslp_download_count DESC) → 파일 없음(imslp_download_count DESC) → id.
- 404 없는 id(숨김 곡은 관리자에게 보임).

- **추천·후보의 단일 기준은 곡 쪽 필드(`recommendedEditionId` / `candidateEditionId`)** 다(2026-09-07 senior-dev). 판본의 `isRecommended`/`isCandidate` 는 같은 사실의 파생값이라 **정렬 근거와 단건 응답용**으로만 둔다 — 관리 화면(06-A 판본 목록)은 곡 쪽 id 로 표시를 결정한다. 이유: 추천은 곡의 속성이고(§5-6 응답도 `{workId, previousEditionId, editionId}` 로 곡 기준이다), id 비교면 "한 곡에 추천 하나" 가 구조적으로 지켜진다(배열 플래그는 둘이 true 가 될 수 있다). 두 출처를 섞으면 추천 지정 직후 목록을 다시 받기 전까지 표시가 어긋난다.
- JSON 이름은 **`isRecommended`/`isCandidate` 하나뿐**이다 — `recommended`/`candidate` 를 같이 내보내지 않는다(계약 밖 필드가 있으면 스택마다 다른 이름에 붙는다).

`AdminEditionDTO` = `EditionDTO` + `{ imslpFileId, imslpOriginalFileName, imslpDescription, imslpLicenseCode, imslpDownloadCount, pdfFileId, previewFileId, copyrightNote, copyrightJudgedAt, copyrightJudgedBy, fileFetchStatus, fileFetchError, fileFetchedAt, downloadCount, isRecommended, isCandidate, createdAt, updatedAt }`

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
요청 `{ "editionId": 302 }`
| 조건 | 결과 |
|---|---|
| 판본이 그 곡의 것이 아님 / 없음 | 404 |
| 판본에 파일 없음 | 400 `BUSINESS_RULE_VIOLATION` "파일이 없어 추천으로 지정할 수 없어요" |
| 정상 | 200 `{ "workId": 21, "previousEditionId": 301, "editionId": 302, "workStatus": "UNKNOWN", "warnings": ["NOT_DOWNLOADABLE", "ARRANGEMENT", "PARTIAL_SCOPE"] }` — `warnings` 는 **배열**(2026-09-08 개정) |

**`warnings` — 해당되는 것이 전부 온다 (2026-09-08 개정, 기획 §11-2 ①)**

| 값 | 조건 | 화면 문구(기획 §3 F6-3) |
|---|---|---|
| `NOT_DOWNLOADABLE` | 지정한 판본의 `koreaCopyright != FREE`(UNKNOWN/RESTRICTED) | "이 판본은 사용자에게 다운로드가 열리지 않아요" |
| `ARRANGEMENT` | `kind = ARRANGEMENT` | "이 판본은 편곡이에요. 사용자가 원곡 악보를 기대하고 받을 수 있어요" |
| `PARTIAL_SCOPE` | `scope = MOVEMENT` | "이 판본은 2악장만 들어 있어요. 곡 전체가 아니에요" (악장 번호는 화면이 이미 가진 판본 데이터에서 쓴다) |

- **고정 순서** `NOT_DOWNLOADABLE → ARRANGEMENT → PARTIAL_SCOPE`. 해당 없으면 **빈 배열 `[]`** — null 을 내려보내지 않는다(빈 상태는 하나만 둔다).
- 옛 필드 `warning`(string|null)은 **삭제한다**. 같은 뜻의 필드를 둘 두면 화면마다 다른 걸 읽는다. frontend-dev 는 `data.warning === "NOT_DOWNLOADABLE"` 분기를 `data.warnings.includes(…)` 로 바꾼다.
- **경고는 막지 않는다** — 200 으로 지정은 끝나 있고 경고는 안내다(기획 §11-2 "경고를 보고도 지정하면 그건 사람의 결정"). 파일 없는 판본만 400 으로 막는다(위 표).
- 지정의 결과가 사용자에게 드러나는 곳은 세 군데다: 검색 항목 `scopeNote`(§2-2-1) · 곡 상세 `recommendedEdition.kind/scope`(§2-3, 이미 있다) · 다운로드 파일명 접미사(§3-4).
- 계약 검증: `RecommendEditionWarningIntegrationTest`.

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
응답에 `unfilteredTotal`(전체 대기 수)도 포함: `{ "unfilteredTotal": 19, "editions": PageResponse<…> }`.
- `autoJudgeSkipReason`(2026-09-07 추가): 지금 §5-11 자동 판정을 돌리면 **이 판본이 왜 자동으로 열리지 않는지**(기획 `02_저작권_판정_지침.md` 부록 A §A-1 표의 사유 코드 6종). 자동 판정으로 `FREE` 가 될 수 있는 판본이면 `null`. 관리자가 "남은 일감이 어떤 종류인지"를 목록에서 바로 보게 하는 값이다. 계산만 하고 아무것도 바꾸지 않는다.
- `imslpLicenseCode` 는 **이 응답에 넣지 않는다**(2026-09-07 정정, senior-dev — 예시에만 있고 DTO 에는 없던 불일치를 예시를 빼는 쪽으로 맞춘다). 화면정의 06-C 대기함 표의 열은 "IMSLP 표기 **원문** + 파일 페이지 링크" 라 관리자가 보는 건 `imslpCopyrightText` 이고, 코드는 그 원문의 정규화 캐시(§5-2)일 뿐이라 같은 행에서 새로 알려주는 정보가 없다. 코드 기반 판단의 결과는 이미 `autoJudgeSkipReason` 의 `LICENSE_NOT_REDISTRIBUTABLE` 로 사람 말이 되어 나간다. 화면이 실제로 코드를 필요로 하게 되면 그때 계약에 추가한다(쓰는 곳 없는 필드를 계약에 두면 세 스택이 각자 다르게 해석한다).

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
> 왜 같은 API 에 넣는가: 판정만 하면 `recommendedEditionId` 가 없어 곡이 계속 `PREPARING` 이라 **다운로드 가능한 곡은 여전히 0개**다.
> 두 단계를 따로 두면 관리자가 절반만 실행한 상태가 생긴다. 다만 관리자가 추천을 손으로 관리하고 싶을 수 있으니 끌 수 있는 스위치로 둔다.

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
- 되돌릴 것이 없어도 200 `{ "reverted": 0 }`(에러 아님).
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

| 화면 | 호출 |
|---|---|
| 홈 | `GET /api/works/popular?limit=10`, `GET /api/composers/featured?limit=8` (독립 요청, 각각 실패 처리) |
| 검색 결과 | `GET /api/works/search?q&level&pages&downloadable&page` — URL 쿼리 그대로 전달. 0건 화면의 인기곡 5개는 `popular?limit=5` |
| 곡 상세 | `GET /api/works/{id}` 1회(같은 작곡가 곡 포함). 다운로드는 `<a href={downloadUrl} download>` — **fetch 로 blob 받지 않는다**(큰 파일 메모리·진행 표시 없음). **실패 감지: 클릭할 때 같은 주소로 `HEAD` 를 한 번 보내고(§3-4), 응답이 실패거나 네트워크 오류면 버튼 아래 InlineAlert danger** (2026-09-07 변경 — 이전의 "10초 자동 복귀" 로는 실패를 알 수 없어 인수조건이 구현되지 않았다. 03 §13) |
| 작곡가 목록 / 상세 | `GET /api/composers` / `GET /api/composers/{id}` + `GET /api/composers/{id}/works?sort&…` |
| 관리 홈 | `GET /api/admin/dashboard` |
| 관리 띠(AdminBanner) | `GET /api/admin/crawl/jobs/active` 를 관리 화면 진입 시 + 30초 간격 |
| 작곡가 관리 | §4-2 ~ 4-5 |
| 곡 관리 / 편집 | §4-6 ~ 4-10, 판본 §5 |
| 판본 모달 | 업로드 §5-1 → 저장 §5-2/5-3(fileId 전달) |
| 대기함 | §5-8 ~ 5-12. 상단에 **"자동 판정 실행"** — 먼저 `dryRun: true` 로 미리보기(규칙별·사유별 숫자 확인 모달) → 확인 누르면 `dryRun: false`. 실행 뒤 **"자동 판정 되돌리기"**(§5-12) 버튼이 보인다. 목록 각 행에는 `autoJudgeSkipReason` 을 사람 말로 바꿔 표시(예: `EDITOR_UNVERIFIABLE` → "편집자 생몰 확인 필요") — designer 에게 문구 요청 필요 |
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
| product-planner (2026-09-08) | 기획 §10-6 "한국어 제목에 이미 작품번호가 들어 있으면 괄호째 생략"이 **아직 계약·구현에 없다** — 지금도 `쇼팽 - Nocturnes, Op.9 (Op.9).pdf` 가 나온다(`DownloadFileNameTest` 가 그 값을 기대하고 있다) | 이번 §3-4 개정 범위(편곡·악장 접미사)와 별건이라 손대지 않았다. "제목에 작품번호가 들어 있다"의 판정 기준(정규화 비교? 부분 문자열?)을 정해야 계약이 되므로 별도 건으로 올린다 |
| ↑ 기술 정리 (senior-dev 2026-09-08) | **시드 데이터에도 이미 있다** — `title_ko` 자체에 작품번호가 든 곡이 50곡 중 10곡이다(`녹턴 Op.9`, `왈츠 Op.64`, `소나티네 Op.36`, `즉흥곡 Op.90 (D.899)` …). 즉 titleKo 가 없어 원어 제목을 쓰는 곡(qa 예시)만의 문제가 아니라 **정상 곡에서도 `쇼팽 - 녹턴 Op.9 (Op.9).pdf` 가 된다.** qa 실측 42곡 중 11곡(26%) | 구현은 §3-4 파일명 조립에 조건 한 줄이면 된다(비용 작음). **기획이 정해 줄 것은 판정 규칙뿐**: ⓐ 비교 대상은 "파일명에 실제로 쓰인 제목"(한국어 없으면 원어) ⓑ 비교 방식은 `SearchNormalizer`(01_ERD §2) 정규화 후 **부분 문자열 포함**을 제안한다 — `Op.9`/`Op. 9`/`op9` 를 같게 본다 ⓒ **부분 일치의 경계**를 정해야 한다: 제목 `녹턴 Op.9` + 대표 작품번호 `Op.9 No.2` 는 포함이 아니라 `(Op.9 No.2)` 가 그대로 붙고, 반대로 제목 `Op.9 No.2` + 작품번호 `Op.9` 는 생략된다. **"제목이 작품번호보다 더 자세하면 생략, 덜 자세하면 유지"** 가 이 규칙의 실제 동작이며 이대로 좋은지 확인이 필요하다 ⓓ 확정되면 `DownloadFileNameTest` 기대값 갱신 + Red 추가는 senior-dev 가 한다 |
| designer (05-E, 2026-09-08) | 화면 E "곡 등록·수정" 폼에 **`수록곡 안내`(`collectionGuide`) 칸이 없다.** §4-7 응답·§4-8 요청에는 있고 시드가 38곡에 채워 두었는데 화면이 다루지 않아, 관리자가 다른 칸만 고쳐 저장해도 **전체 교체 PUT 이 그 값을 null 로 덮는다**(백필도 안 되는 영구 유실 + 검색의 `scopeNote.COLLECTION` 줄 동반 소멸) | senior-dev 가 **입력 칸 있음**으로 잠정 확정하고 Red 를 박았다(`WorkFormPage.test.jsx` — 라벨 `수록곡 안내`, 왕복·비우면 null). 위치는 `악장 페이지 안내` 아래 제안, 컨트롤은 여러 줄 문장이므로 `textarea` 제안, 상한 500자(§0-6). **designer 가 정할 것: 라벨 문구 · 도움말 문구 · 컨트롤(`.form-input` 1줄 vs textarea) · 배치.** 라벨이 바뀌면 senior-dev 가 테스트를 맞춰 고친다 |
