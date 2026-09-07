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
| **지원하지 않는 HTTP 메서드**(`DELETE /api/works/21`, `PUT /api/composers/4`, `PATCH /api/rooms/1` …) | 405 | `METHOD_NOT_ALLOWED` | `HttpRequestMethodNotSupportedException` — **핸들러 추가 필요**(현재 500). 응답에 **`Allow` 헤더**를 함께 준다. 2026-09-07 추가 |
| **본문 Content-Type 이 맞지 않음**(`text/plain` 으로 JSON API 호출) | 415 | `UNSUPPORTED_MEDIA_TYPE` | `HttpMediaTypeNotSupportedException` — **핸들러 추가 필요**(현재 500). 2026-09-07 추가 |

**프레임워크 표준 4xx 를 500 으로 뭉개지 않는다**(코드 컨벤션 §2). 위 세 줄은 전부 "클라이언트가 잘못 부른 요청" 인데,
`@ExceptionHandler(Exception.class)` 라는 최후의 보루가 Spring MVC 표준 예외까지 먼저 삼켜 500 이 되고 있다(qa 2차 결함).
500 은 "서버가 깨졌다" 는 뜻이라 모니터링·로그의 신호를 흐리고, 호출자에게 "내 요청이 잘못됐다" 는 정보를 주지 못한다.

- **405 에 `Allow` 헤더를 붙이는 이유**: RFC 9110 이 405 응답에 `Allow` 를 요구한다. 예외가 `getSupportedMethods()` 로 이미
  값을 들고 있어 헤더 한 줄이면 되고, 없으면 405 는 "안 된다" 만 말하고 "무엇이 되는지" 는 말하지 않는다.
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

### 0-4. 표기 규칙

- 필드명 camelCase. 날짜·시각은 **ISO-8601 UTC 문자열**(`"2026-09-06T05:02:00Z"`) — 새 엔티티는 `Instant`. 프론트는 `new Date(v)` 로 지역 시각 표시.
- enum 은 대문자 문자열(`"INTERMEDIATE"`). 화면 문구 변환(중급 등)은 프론트 책임.
- 없는 값은 `null` 로 내려준다(키 생략 안 함). 빈 목록은 `[]`.
- 페이지: `page`(0-base, 기본 0), `size`(기본 20, 최대 100). 화면은 `size` 를 보내지 않는다(20 고정).
- 파일 크기 바이트(`fileSize`), 쪽수 정수(`pageCount`). "2.4MB"/"12쪽" 포맷은 프론트.
- 미리보기 이미지 URL 은 `previewUrl` = `/uploads/{저장파일명}.png` — **기존 `FileServingController` 프록시** 가 서빙(permitAll, 로컬/버킷 공통). 별도 이미지 API 를 두지 않는다(03 §2).

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

```json
{ "id": 21, "titleKo": "월광 소나타", "titleOriginal": "Piano Sonata No.14, Op.27 No.2",
  "composer": { "id": 4, "nameKo": "베토벤", "nameOriginal": "Beethoven, Ludwig van" },
  "catalogNumbers": ["Op.27 No.2"], "level": "INTERMEDIATE", "status": "READY",
  "pageCount": 14, "fileSize": 1059957, "previewUrl": "/uploads/3f2a…c1.png", "matchedAlias": "월광" }
```

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

### 3-2. `GET /api/works/popular?limit=10`
`limit` 기본 10, 최대 20. 공개 곡을 `download_count DESC, created_at DESC, id DESC` 로 — 기록이 없으면 자연히 최근 등록순. 응답 `data: WorkSummaryDTO[]`(matchedAlias null).

### 3-3. `GET /api/works/{id}` — 곡 상세

404: 없는 id, **숨김 곡**.

`WorkDetailDTO`

| 필드 | 타입 | 설명 |
|---|---|---|
| id, titleKo, titleOriginal, composer, catalogNumbers, level, status | (2-2 와 동일) | |
| aliases | string[] | 원문, id 순. 0개면 `[]`(줄 생략은 화면) |
| compositionYear, musicalKey, movements, movementPageGuide | string\|null | |
| imslpUrl | string\|null | "출처: IMSLP — 원본 페이지 보기" |
| composerImslpUrl | string\|null | |
| recommendedEdition | EditionDTO\|null | |
| otherEditions | EditionDTO[] | 추천 제외. 정렬 `imslp_download_count DESC NULLS LAST, id ASC` |
| downloadableOtherCount | int | otherEditions 중 downloadable=true 수 — "바로 받을 수 있는 다른 판본이 N개 있어요" |
| sameComposerWorks | WorkSummaryDTO[] | 같은 작곡가의 다른 공개 곡, download_count DESC, 최대 5 |

```json
{ "success": true, "message": "성공", "data": {
  "id": 21, "titleKo": "월광 소나타", "titleOriginal": "Piano Sonata No.14, Op.27 No.2",
  "composer": { "id": 4, "nameKo": "베토벤", "nameOriginal": "Beethoven, Ludwig van" },
  "catalogNumbers": ["Op.27 No.2"], "level": "INTERMEDIATE", "status": "READY",
  "aliases": ["월광", "월광 소나타", "Moonlight Sonata"],
  "compositionYear": "1801", "musicalKey": "C-sharp minor", "movements": "3 movements", "movementPageGuide": "1악장 1쪽 · 2악장 6쪽 · 3악장 9쪽",
  "imslpUrl": "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)",
  "composerImslpUrl": "https://imslp.org/wiki/Category:Beethoven,_Ludwig_van",
  "recommendedEdition": { "id": 301, "kind": "COMPLETE_SCORE", "scope": "COMPLETE", "movementNumber": null, "sectionLabel": null,
    "pageCount": 14, "fileSize": 1059957, "hasFile": true, "previewUrl": "/uploads/3f2a…c1.png",
    "publisher": "Vienna: Universal Edition, 1921. Plate U.E. 7000.", "publishYear": 1921, "plateNumber": "U.E. 7000", "editor": "Heinrich Schenker", "arranger": null, "scanner": "Unknown",
    "koreaCopyright": "FREE", "imslpCopyrightText": "Public Domain", "ccLicenseName": null, "ccAttribution": null,
    "imslpFileUrl": "https://imslp.org/wiki/Special:ImagefromIndex/00014",
    "downloadable": true, "largeFile": false, "downloadUrl": "/api/editions/301/download" },
  "otherEditions": [ ], "downloadableOtherCount": 0,
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

**파일명 규칙(01 §9 8-9 확정)**: `{작곡가 한글 표기 또는 원어 표기} - {한국어 대표 제목 또는 원어 제목}{ (대표 작품번호)}.pdf`
- 대표 작품번호 = `sort_order = 0`. 작품번호 없으면 괄호째 생략 → `사티 - 짐노페디.pdf`
- 한국어 제목 없으면 원어 제목, 작곡가 한글 없으면 원어 표기.
- 파일명 금지 문자 `\ / : * ? " < > |` 는 `-` 로, 연속 공백은 하나로, 앞뒤 공백 제거. 예: `모차르트 - 피아노 소나타 11번 A장조 (K.331).pdf`, 카탈로그 `K.331/300i` 였다면 `(K.331-300i)`.
- 성공 시 같은 요청 안에서 `edition.download_count`, `work.download_count` 를 1 올리고 `download_log` 1행 INSERT(바이트 확보 뒤, 스트리밍 전 짧은 트랜잭션).
- **이 API 가 판본 바이트를 얻는 유일한 공개 경로다(2026-09-07 확정, senior-dev).** 공용 파일 API(`GET /api/files`, `/api/files/paths`, `/api/files/{id}/content`)는 `ref_type=EDITION` 파일을 다루지 않는다 — 목록/경로에서 제외하고 `/content` 는 404. (순번 fileId 로 저작권 게이트를 우회할 수 있으면 §9-1 재배포 정책이 무의미해진다. 업로드 쪽은 `FileService.verifyOwnership` 이 이미 EDITION 을 막고 있다.) 미리보기 PNG 는 지금처럼 `/uploads/{저장파일명}` 프록시로 공개한다.

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
| status | `READY \| PREPARING \| RESTRICTED \| UNKNOWN \| NEEDS_WORK \| HIDDEN` 중 하나 |
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
  "imslpUrl": "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)",
  "hidden": false, "hiddenReason": null,
  "status": "READY", "needsWork": false, "missing": [],
  "recommendedEditionId": 301, "candidateEditionId": null,
  "downloadCount": 312, "hasDownloadHistory": true,
  "editions": [ AdminEditionDTO… ],
  "createdAt": "2026-09-06T05:02:00Z", "updatedAt": "2026-09-06T05:02:00Z"
} }
```
- `missing`: `TITLE_KO | ALIAS | LEVEL | RECOMMENDED_EDITION` 중 해당 값.
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
  "imslpUrl": "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)", "hidden": false }
```
| 검증 | 결과 |
|---|---|
| composerId 없음/존재하지 않음 | 400 field `composerId` / 404 |
| titleOriginal 공백 | 400 field `titleOriginal` "원어 제목을 입력해 주세요" |
| titleKo 공백 | **허용**(보완 필요가 됨). 저장 시 null 로 정규화 |
| level 은 null 허용 | |
| imslpUrl 이 다른 곡과 같음(정규화 비교) | 409 `DUPLICATE_RESOURCE` |
| aliases/catalogNumbers 안 정규화 중복 | 하나만 저장. 다른 곡과 겹치는 별칭은 허용(경고는 §4-10) |
| hidden=true 로 바꾸면 | hiddenReason 는 null(관리자 숨김). 수집 숨김 곡을 hidden=false 로 바꾸면 hiddenReason 도 null |
응답 `data`: `AdminWorkDetailDTO`. 별칭·작품번호는 **전체 교체**(기존 SEED/IMSLP 별칭도 목록에 없으면 삭제; 목록에 남아 있으면 source 유지).

### 4-9. `DELETE /api/admin/works/{id}` → 204
판본·파일·다운로드 기록 함께 삭제(01_ERD §7). 확인 문구용 정보(판본 수, 다운로드 기록 유무)는 §4-7 응답의 `editions.length`, `hasDownloadHistory` 로 화면이 미리 안다.

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
추천이면 곡 추천 해제. 파일·미리보기·다운로드 기록 삭제.

### 5-6. `PUT /api/admin/works/{workId}/recommended-edition`
요청 `{ "editionId": 302 }`
| 조건 | 결과 |
|---|---|
| 판본이 그 곡의 것이 아님 / 없음 | 404 |
| 판본에 파일 없음 | 400 `BUSINESS_RULE_VIOLATION` "파일이 없어 추천으로 지정할 수 없어요" |
| 정상 | 200 `{ "workId": 21, "previousEditionId": 301, "editionId": 302, "workStatus": "UNKNOWN", "warning": "NOT_DOWNLOADABLE" }` — `warning` 은 판정이 FREE 가 아닐 때 `NOT_DOWNLOADABLE`, 아니면 null |

### 5-7. `POST /api/admin/editions/{id}/fetch-file` — IMSLP 파일 받아오기(비동기)
| 조건 | 결과 |
|---|---|
| 이미 파일 있음 | 400 "이미 파일이 있는 판본이에요" |
| `imslpFileId` 없음 | 400 "IMSLP 파일 정보가 없어 받아올 수 없어요" |
| 라이선스 코드가 PD/CC0/CC_BY/CC_BY_SA 가 아님(OTHER/NC/ND/null) | 400 "재배포가 허용되지 않는 표기라 받아올 수 없어요"(01 §9-1) |
| 이미 QUEUED/FETCHING | 409 `DUPLICATE_RESOURCE` |
| 정상 | **202** `{ "editionId": 305, "fileFetchStatus": "QUEUED" }` |
이후 화면은 `GET /api/admin/editions/{id}` 를 3초 간격으로 폴링해 `fileFetchStatus` 가 null(성공, `hasFile: true`) 또는 `FAILED`(`fileFetchError`) 가 될 때까지 기다린다. 수집 작업이 진행 중이면 그 뒤에 처리된다(03 §3: IMSLP 커넥션은 항상 1개).
- **진행 중 수집 안내(06-A)**: 받아오는 중인 행이 생기면 화면은 `GET /api/admin/crawl/jobs/active` 를 **한 번** 확인한다. `data` 가 있으면(RUNNING/PAUSED) 상태 줄을 `수집이 끝난 뒤 받아와요 — 진행 중인 수집 보기`(→ `/admin/crawl/{id}`)로 바꾸고, 없으면 기본 문구를 쓴다. 폴링 대상은 판본 상세뿐이고 active 는 다시 묻지 않는다(2026-09-07 확정, senior-dev).

### 5-8. `GET /api/admin/copyright/pending?q=&composerId=&page=&size=`
`size` 는 §4-2 와 같은 규칙(기본 20, 최대 200, 1 미만이면 기본 20). `korea_copyright = UNKNOWN` 판본. `q` 는 곡 제목(한/원어)·작곡가 부분 일치. 정렬 `composer.death_year ASC NULLS LAST, edition.id ASC`.
`PageResponse<PendingCopyrightDTO>`:
```json
{ "editionId": 305, "work": { "id": 23, "titleKo": null, "titleOriginal": "Nocturnes, Op.9" },
  "composer": { "id": 9, "nameKo": "쇼팽", "nameOriginal": "Chopin, Frédéric", "deathYear": 1849 },
  "kind": "COMPLETE_SCORE", "scope": "COMPLETE", "movementNumber": null, "editor": "Ignacy Paderewski", "arranger": null,
  "publisher": "Warsaw: Instytut Fryderyka Chopina, 1949.", "publishYear": 1949,
  "imslpCopyrightText": "Public Domain", "imslpFileUrl": "https://imslp.org/wiki/Special:ImagefromIndex/…", "hasFile": true }
```
응답에 `unfilteredTotal`(전체 대기 수)도 포함: `{ "unfilteredTotal": 19, "editions": PageResponse<…> }`.

### 5-9. `PUT /api/admin/editions/{id}/copyright`
요청 `{ "koreaCopyright": "FREE", "copyrightNote": "작곡가 1849 사망, 편집자 1941 사망 → 경과" }`
- FREE/RESTRICTED 에 note 공백 → 400 field `copyrightNote`. UNKNOWN 으로 되돌리기도 허용(note 선택).
- 200 `AdminEditionDTO`(+`workStatus`).

### 5-10. `POST /api/admin/editions/copyright/bulk`
요청 `{ "editionIds": [305, 306, 307], "koreaCopyright": "FREE", "copyrightNote": "…" }` — `editionIds` 1개 이상, note 규칙 동일.
200 `{ "succeeded": [305, 306], "failed": [ { "editionId": 307, "reason": "NOT_FOUND" } ] }`. 없는 id 는 예외 없이 `failed[]` 로 보고하고 나머지는 저장한다(요청 1건 = 트랜잭션 1개 — 2026-09-07 정정, senior-dev: 관찰 가능한 계약이 같고 구현이 단순하다).

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
- 편성이 피아노 독주가 아니면(위키텍스트 `Instrumentation` ≠ `piano` 이고 카테고리에 `For piano` 없음) 곡은 만들되 `hidden=true, hiddenReason=NOT_PIANO_SOLO`, 항목 `HIDDEN`, 파일은 받지 않는다.
- 파일 1개 실패는 항목 `FAILED/FILE_DOWNLOAD_FAILED`(판본 정보는 저장됨, 다른 파일이 성공했으면 fileCount 에 반영).
- **메타 읽기 단계에서 예상 밖의 오류**(우리 쪽 버그·파싱 사고 = IMSLP 예외가 아닌 것)면 항목 `FAILED/INTERNAL_ERROR`, message `"처리 중 오류가 났어요"`, 작업은 계속된다. **연속 무응답으로 세지 않는다**(PAUSED 로 가지 않는다). 2026-09-07 추가 — `IMSLP_UNAVAILABLE` 로 뭉개면 관리자가 IMSLP 탓으로 읽고, 멀쩡한 IMSLP 를 두고 10분 쉰다. 파일 수신 단계의 예상 밖 오류는 지금처럼 `FILE_DOWNLOAD_FAILED`(관리자가 §5-7 로 재시도할 수 있고 곡·판본은 이미 저장돼 있다).
- IMSLP 무응답(타임아웃·5xx·429·봇 게이트 302 반복)이 **연속 3항목** → `PAUSED`, `pausedUntil = now+10분`. 시간이 지나면 자동 재개, 다시 3연속이면 `STOPPED` + `failureReason "IMSLP가 응답하지 않아요"`.
- 서비스 기동 시 RUNNING/PAUSED 작업 → `STOPPED, stoppedByRestart=true`, PROCESSING 항목 → `FAILED/INTERRUPTED`. 이미 받은 파일(`imslp_file_id` 로 판본에 파일이 있음)은 재개해도 다시 받지 않는다.
- 요청 간격 최소 2초, 파일은 15초 대기 준수, 동시 1커넥션, UA 에 서비스명+연락처(03 §3).

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
| 대기함 | §5-8 ~ 5-10 |
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
| product-planner | 인기곡 정렬 "기록 없으면 최근 등록순" 은 `download_count DESC, created_at DESC` 로 자연 구현되며, 기록이 일부만 있으면 0건 곡들 사이에서만 최근순이 된다 | 그대로 구현 |
