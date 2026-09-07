# 01. ERD / DB 스키마 — 쉬운악보 1차 (피아노 독주)

> 작성일: 2026-09-06 / 작성: senior-dev
> 기준: `docs/기획/01_MVP_피아노_기획서.md` (§0-3 용어·상태, §2·§3 흐름, §9·§9-1), `02_저작권_판정_지침.md`, `03_1차_큐레이션_곡목록.md` (§0 표 규칙, §9 시드 가정), `docs/설계/00_IMSLP_수집_조사.md`, 화면 정의서 00~07, `code-convention` 스킬(§4-1 PK Long, §5-2 로컬 H2 휘발/운영 MySQL, §5-3 파일 버킷)
> 짝 문서: `02_API_명세서.md`(응답 DTO), `03_기술결정.md`(구현 방식)
> 대상 DB: 로컬/테스트 **H2(MySQL 모드, ddl-auto create)**, 운영 **MySQL 8(ddl-auto update)**. 스키마는 JPA 엔티티 어노테이션이 원본이며, 이 문서는 그 엔티티가 만들어야 할 결과를 적은 것이다.

---

## 0. 한눈에 보는 관계

```
composer 1 ──< composer_alias
composer 1 ──< work 1 ──< work_alias
                  │  1 ──< work_catalog_number
                  │  1 ──< edition >── 0..1 files (pdf_file_id)
                  │         │      >── 0..1 files (preview_file_id)
                  │         │  1 ──< download_log
                  │  0..1 ─── edition (work.recommended_edition_id)   ← 곡당 추천 판본 1개
                  └──< download_log
crawl_job 1 ──< crawl_item >── 0..1 work
users (기존)  — user_roles 에 'ADMIN' 역할 추가(시드)
files (기존)  — ref_type 에 EDITION 추가, file_usage 는 ATTACHMENT(PDF)/THUMBNAIL(미리보기) 재사용
```

- 신규 테이블 9개: `composer`, `composer_alias`, `work`, `work_alias`, `work_catalog_number`, `edition`, `download_log`, `crawl_job`, `crawl_item`
- 기존 테이블 변경 2개: `files`(enum 값 추가만, 컬럼 변경 없음), `user_roles`(시드에 ADMIN 1건)
- 커뮤니티·채팅 테이블은 손대지 않는다(01 §9 8-2).

---

## 1. 공통 규칙

| 항목 | 규칙 |
|---|---|
| PK | 전부 `id BIGINT` + `IDENTITY` (컨벤션 §4-1). 외부 노출 id 도 Long — 공개 리소스(곡·작곡가·판본)는 열거돼도 무해하고, 관리 API 는 ADMIN 인가로 막는다 |
| FK | JPA `@ManyToOne(fetch = LAZY)` + DB FK 제약. 삭제 순서는 서비스가 책임진다(cascade 는 `work → work_alias / work_catalog_number` 만 JPA cascade, 나머지는 명시 삭제) |
| 시각 | 새 테이블은 전부 `Instant`(UTC) — 컬럼 `TIMESTAMP(6)`(MySQL `DATETIME(6)`). `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` 로 H2/MySQL 동일하게 저장. 기존 모듈의 `LocalDateTime` 은 손대지 않는다 |
| 문자열 | `VARCHAR(n)` 만 쓴다(`@Column(length = n)`). `TEXT`/`@Lob` 없음 — 1,000자 이내로 전부 충분하다 |
| enum | 전부 `@Enumerated(EnumType.STRING)` + `VARCHAR(30)` |
| 예약어 회피 | `value`(H2 예약어) → `catalog_value`, `key` → `musical_key`, `mode` → `item_mode`, `usage` → 기존과 같이 `file_usage` |
| 인덱스 이름 | `idx_{테이블}_{컬럼}`, 유니크 `uk_{테이블}_{컬럼}` (`@Table(indexes=…, uniqueConstraints=…)`) |
| 정규화 컬럼 | 검색 대상 문자열마다 `*_normalized` 짝 컬럼을 둔다(§2). 엔티티가 원문을 바꿀 때 항상 함께 갱신(엔티티 도메인 메서드 안에서 `SearchNormalizer` 호출) |
| 생성/수정 시각 | `created_at`, `updated_at` — `@PrePersist/@PreUpdate` (기존 패턴) |

---

## 2. 검색 정규화 규칙 (`SearchNormalizer.normalize`)

기획 F2-4(대소문자·공백·구두점 무시)와 03 §9 가정(악센트 무시)을 한 함수로 고정한다. **저장할 때와 검색할 때 같은 함수**를 쓴다.

```
normalize(s):
  1. null/blank → ""
  2. 유니코드 NFD 분해 → 결합 문자(\p{M}) 제거   → "Für" → "Fur", "Frédéric" → "Frederic"
     (한글 음절은 NFD 로 자모 분해되므로) 3. NFC 재결합       → "월광" 이 다시 음절로
  4. NFD 로 분해되지 않는 문자 치환표: ß→ss, ø→o, Ø→o, ł→l, Ł→l, æ→ae, œ→oe, đ→d, ð→d, þ→th, ı→i
  5. 소문자(Locale.ROOT)
  6. 문자(\p{L})·숫자(\p{N}) 이외 전부 제거   → 공백, 마침표, 쉼표, 하이픈, 아포스트로피, 슬래시, ♯♭#, 괄호 삭제
```

예: `Op. 27, No. 2` → `op27no2` / `BWV 846` → `bwv846` / `K.545` → `k545` / `Beethoven, Ludwig van` → `beethovenludwigvan` / `엘리제를 위하여` → `엘리제를위하여` / `Fantaisie-impromptu` → `fantaisieimpromptu` / `C-sharp minor` → `csharpminor`

검색어도 같은 함수로 **단어별** 정규화한다(공백으로 나눈 뒤 각 단어 normalize, 빈 단어 제거). 매칭 규칙은 `02_API_명세서.md` §3-1.

초성 검색은 2차(01 §9 8-4) — 정규화 컬럼에 초성 컬럼을 추가하면 되도록 구조는 열어 둔다.

---

## 3. 테이블 정의

### 3-1. `composer` — 작곡가

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| id | BIGINT PK | N | |
| name_ko | VARCHAR(100) | Y | 한글 표기. **수집이 만든 작곡가는 비어 있을 수 있다**(관리자가 채움). 관리자 등록/수정 API 에서는 필수(검증은 API 계층) |
| name_ko_normalized | VARCHAR(100) | Y | |
| name_original | VARCHAR(200) | N | IMSLP 원어 표기 "성, 이름" (`Beethoven, Ludwig van`) |
| name_original_normalized | VARCHAR(200) | N | **UNIQUE** — 중복 등록 판정 키(`Chopin, Frederic` 과 `Chopin, Frédéric` 을 같은 작곡가로) |
| birth_year | INT | Y | |
| death_year | INT | Y | 저작권 판정 근거(02 규칙 1) |
| nationality | VARCHAR(100) | Y | |
| imslp_url | VARCHAR(500) | Y | `https://imslp.org/wiki/Category:성,_이름` |
| created_at / updated_at | TIMESTAMP(6) | N | |

인덱스: `uk_composer_name_original_normalized`, `idx_composer_name_ko`(가나다 정렬), `idx_composer_name_ko_normalized`.

### 3-2. `composer_alias` — 작곡가 검색용 별칭

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| id | BIGINT PK | N | |
| composer_id | BIGINT FK→composer | N | |
| alias | VARCHAR(200) | N | 원문 표기 (`차이코프스키`, `Tchaikovski`) |
| alias_normalized | VARCHAR(200) | N | |
| created_at | TIMESTAMP(6) | N | |

인덱스: `uk_composer_alias(composer_id, alias_normalized)` — 같은 작곡가 안 중복 금지, `idx_composer_alias_normalized`.

### 3-3. `work` — 곡

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| id | BIGINT PK | N | |
| composer_id | BIGINT FK→composer | N | |
| title_ko | VARCHAR(300) | Y | 한국어 대표 제목. 비면 "보완 필요" |
| title_ko_normalized | VARCHAR(300) | Y | |
| title_original | VARCHAR(300) | N | 원어 제목(IMSLP Work Title) |
| title_original_normalized | VARCHAR(300) | N | |
| level | VARCHAR(30) | Y | enum `Level`: `BEGINNER / ELEMENTARY / INTERMEDIATE / ADVANCED`. **NULL = 난이도 미정** |
| composition_year | VARCHAR(20) | Y | IMSLP 원문이 `1830-31` 같은 범위라 문자열 |
| musical_key | VARCHAR(50) | Y | 조성 |
| movements | VARCHAR(500) | Y | 악장 구성(자유 문장) |
| movement_page_guide | VARCHAR(500) | Y | 악장 페이지 안내(관리자 입력) |
| imslp_url | VARCHAR(500) | Y | **정규 형태 UNIQUE**(NULL 허용 — MySQL/H2 모두 NULL 중복 허용). 수집·시드 동일성 키. 정규 형태 = `https://imslp.org/wiki/` + 퍼센트 디코딩한 제목(공백→`_`) — `02_API_명세서.md` §6-1 |
| hidden | BOOLEAN | N | 기본 false. 숨김이면 사용자 화면 어디에도 안 나옴 |
| hidden_reason | VARCHAR(30) | Y | enum `HiddenReason`: `NOT_PIANO_SOLO`(수집 자동 숨김). 관리자가 직접 숨기면 NULL |
| recommended_edition_id | BIGINT FK→edition | Y | **추천 판본(곡당 0~1)**. 순환 FK 지만 Hibernate 가 테이블 생성 후 ALTER 로 제약을 건다. 곡 삭제 시 서비스가 먼저 NULL 로 만든 뒤 판본 삭제 |
| download_count | BIGINT | N | 기본 0. 곡 누적 다운로드(판본 합계를 비정규화). 정렬용. 원본은 `download_log` |
| created_at / updated_at | TIMESTAMP(6) | N | |

인덱스: `idx_work_composer(composer_id)`, `uk_work_imslp_url(imslp_url)`, `idx_work_title_ko_normalized`, `idx_work_title_original_normalized`, `idx_work_download_count`, `idx_work_updated_at`(관리 목록 정렬), `idx_work_hidden`.

> `LIKE '%…%'` 는 B-tree 인덱스를 못 타지만 1차 규모(수백 곡)에서는 풀스캔이 밀리초다. 정규화 컬럼 인덱스는 정확·전방 일치(일치도 정렬)와 유니크용이다.

**추천 후보(candidate)** 는 컬럼이 아니라 계산값이다: `kind = COMPLETE_SCORE AND scope = COMPLETE AND pdf_file_id IS NOT NULL` 인 판본 중 `imslp_download_count` 최대(동률이면 id 최소). `recommended_edition_id` 가 있으면 후보를 표시하지 않는다. (컬럼으로 두면 "파일 받아오기" 뒤 갱신 누락이 생긴다.)

### 3-4. `work_alias` — 곡 별칭

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| id | BIGINT PK | N | |
| work_id | BIGINT FK→work | N | |
| alias | VARCHAR(200) | N | 원문 (`월광`, `Moonlight Sonata`) |
| alias_normalized | VARCHAR(200) | N | |
| source | VARCHAR(30) | N | enum `AliasSource`: `ADMIN / SEED / IMSLP`(수집이 Alternative Title·Name Aliases·`span[title=ko]` 에서 가져온 것) |
| created_at | TIMESTAMP(6) | N | |

인덱스: `uk_work_alias(work_id, alias_normalized)` — 같은 곡 안 중복은 DB 가 막고(→409), **다른 곡과의 겹침은 허용**(01 F5-2). `idx_work_alias_normalized`.

### 3-5. `work_catalog_number` — 작품번호

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| id | BIGINT PK | N | |
| work_id | BIGINT FK→work | N | |
| catalog_value | VARCHAR(100) | N | 표시 원문 (`Op.27 No.2`, `WoO 59`, `K.331/300i`) — `value` 는 H2 예약어라 이 이름 |
| catalog_value_normalized | VARCHAR(100) | N | `op27no2` |
| sort_key | VARCHAR(120) | N | 작품번호 순 정렬용: normalized 값의 숫자 구간을 6자리 0-패딩 (`op000027no000002`, `bwv000846`). 문자열 정렬로 `Op.9 < Op.10` 이 되게 |
| sort_order | INT | N | 곡 안 표시 순서(0부터). 0번이 대표 작품번호(다운로드 파일명·정렬에 사용) |
| created_at | TIMESTAMP(6) | N | |

인덱스: `uk_work_catalog(work_id, catalog_value_normalized)`, `idx_work_catalog_normalized`.

### 3-6. `edition` — 판본 (= IMSLP 파일 1개 = PDF 1개)

IMSLP 의 `div.we` 블록 하나에 파일이 여러 개면 파일마다 행을 만든다(01 §0-3: 판본 = PDF 파일 하나). 편집자·출판사 정보는 행마다 복사한다.

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| id | BIGINT PK | N | |
| work_id | BIGINT FK→work | N | |
| kind | VARCHAR(30) | N | enum `EditionKind`: `COMPLETE_SCORE`(전체 악보) / `PARTS`(파트보) / `ARRANGEMENT`(편곡). 수집 매핑: `tabScore1`→COMPLETE_SCORE, `tabScore3`(Parts)→PARTS, `tabArrTrans`→ARRANGEMENT |
| scope | VARCHAR(30) | N | enum `EditionScope`: `COMPLETE`(전곡) / `MOVEMENT`(특정 악장·발췌). 수집: `h4 == "Complete"` → COMPLETE, 그 외 MOVEMENT |
| movement_number | INT | Y | scope=MOVEMENT 일 때 악장 번호. **관리자 입력은 필수**, 수집은 헤딩에서 `(No.N)`/`N.` 을 못 읽으면 NULL |
| section_label | VARCHAR(200) | Y | 수집이 읽은 IMSLP 섹션 헤딩 원문(`Adagio sostenuto (No.1)`, `Selections`). movement_number 가 없을 때 표시 폴백 |
| imslp_description | VARCHAR(300) | Y | 파일 설명 원문(`Complete Score`, `1. Adagio sostenuto`) |
| publisher | VARCHAR(300) | Y | 출판사 정보(수집은 Pub. Info 원문 전체) |
| publish_year | INT | Y | |
| plate_number | VARCHAR(100) | Y | |
| editor | VARCHAR(200) | Y | 편집자 |
| arranger | VARCHAR(200) | Y | 편곡자(02 규칙 2 — 판정 근거). 편곡 판본에서 수집 |
| scanner | VARCHAR(200) | Y | 스캔 제공자(`scanned by X` / `typeset by editor`) |
| imslp_file_id | VARCHAR(20) | Y | IMSLP 파일 번호 원문(`00014`, 앞자리 0 포함). **UNIQUE(NULL 허용)** — "파일당 평생 1회" 키. 관리자 직접 등록 판본은 NULL |
| imslp_original_file_name | VARCHAR(300) | Y | `Beethoven, L.v. - Piano Sonata 14.pdf` — HTML↔위키텍스트 조인 키 |
| imslp_file_url | VARCHAR(500) | Y | 사용자에게 보여줄 IMSLP 파일 페이지 = `https://imslp.org/wiki/Special:ImagefromIndex/{id}` (IMSLP 가 파일명에 거는 링크와 동일) |
| imslp_copyright_text | VARCHAR(200) | Y | IMSLP 표기 원문 (`Public Domain`, `Creative Commons Attribution-ShareAlike 4.0`) |
| imslp_license_code | VARCHAR(30) | Y | enum `LicenseCode`: `PD / CC0 / CC_BY / CC_BY_SA / CC_BY_NC / CC_BY_NC_SA / CC_BY_NC_ND / OTHER` (00 조사 §1-4 정규화). 수집 파일 수신 허용 = `PD, CC0, CC_BY, CC_BY_SA` |
| imslp_download_count | INT | Y | IMSLP 누적 다운로드 수(수집값). 추천 후보 산정·다른 판본 정렬 |
| page_count | INT | Y | 쪽수. 업로드 시 PDFBox 로 자동, 수집 시 HTML `N pp.`, 관리자 수정 가능 |
| pdf_file_id | BIGINT FK→files | Y | PDF 바이트 메타(기존 `files`, ref_type=EDITION, file_usage=ATTACHMENT). NULL = 파일 없음 |
| preview_file_id | BIGINT FK→files | Y | 첫 페이지 PNG(ref_type=EDITION, file_usage=THUMBNAIL). NULL = 미리보기 준비 중 |
| korea_copyright | VARCHAR(30) | N | enum `KoreaCopyright`: `FREE / RESTRICTED / UNKNOWN`. 기본 **UNKNOWN** |
| copyright_note | VARCHAR(1000) | Y | 판정 메모. FREE/RESTRICTED 일 때 필수(API 검증) |
| copyright_judged_at | TIMESTAMP(6) | Y | 마지막 판정 시각 |
| copyright_judged_by | VARCHAR(100) | Y | 판정한 관리자 username |
| cc_license_name | VARCHAR(100) | Y | 사용자 표시용 (`CC BY-SA 4.0`) |
| cc_attribution | VARCHAR(200) | Y | 표기할 저작자 |
| file_fetch_status | VARCHAR(30) | Y | enum `FileFetchStatus`: `QUEUED / FETCHING / FAILED`. NULL = 요청 없음/완료 |
| file_fetch_error | VARCHAR(300) | Y | 마지막 받아오기 실패 사유 |
| file_fetched_at | TIMESTAMP(6) | Y | 수집/받아오기로 파일을 얻은 시각 |
| download_count | BIGINT | N | 기본 0. 우리 서비스 다운로드 수 |
| created_at / updated_at | TIMESTAMP(6) | N | |

인덱스: `idx_edition_work(work_id)`, `uk_edition_imslp_file_id(imslp_file_id)`, `idx_edition_korea_copyright(korea_copyright)`(대기함), `idx_edition_pdf_file(pdf_file_id)`.

`files` 와의 관계: **재사용한다**(별도 파일 테이블을 만들지 않음). 이유는 `03_기술결정.md` §2. 정합성 규칙: `pdf_file_id`/`preview_file_id` 가 가리키는 `files` 행은 `ref_type = EDITION`, `ref_id = edition.id`. 업로드 직후(판본 저장 전)에는 `ref_id = 0` 임시 상태이며 판본 저장 시 연결한다(컨벤션 §5-3-1 ①). 판본 삭제·파일 교체 시 `files` 행과 바이트를 함께 지운다(②). 24시간 지난 `ref_id = 0` 파일은 기존 orphan 배치가 정리한다(③).

### 3-7. `download_log` — 다운로드 기록

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| id | BIGINT PK | N | |
| edition_id | BIGINT FK→edition | N | |
| work_id | BIGINT FK→work | N | 비정규화(판본 삭제 후에도 곡 집계 유지 목적이 아니라 조회 편의. 판본 삭제 시 로그도 삭제) |
| downloaded_at | TIMESTAMP(6) | N | |

인덱스: `idx_download_log_downloaded_at`, `idx_download_log_work`. 개인정보(IP·UA)는 저장하지 않는다. "이번 달 다운로드 수" = `downloaded_at >= Asia/Seoul 이번 달 1일 00:00 (UTC 환산)`.

### 3-8. `crawl_job` — 수집 작업

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| id | BIGINT PK | N | 화면의 "수집 작업 #12" |
| status | VARCHAR(30) | N | enum `CrawlJobStatus`: `RUNNING / PAUSED / STOPPED / COMPLETED / FAILED` |
| stop_requested | BOOLEAN | N | 중지 요청됨(처리 중 항목 마친 뒤 STOPPED 로). 화면의 "중지하는 중…" |
| stopped_by_restart | BOOLEAN | N | 서비스 재시작으로 STOPPED 된 작업(화면 안내용) |
| fetch_files | BOOLEAN | N | "파일도 함께 받기"(01 §9-1 스위치) 작업 생성 시 스냅샷 |
| total_count | INT | N | 항목 수 |
| success_count / fail_count / skip_count / hidden_count | INT | N | 항목 상태별 집계(워커만 갱신) |
| current_item_id | BIGINT | Y | 지금 처리 중 항목 |
| current_stage | VARCHAR(30) | Y | enum `CrawlStage`: `READING_METADATA / DOWNLOADING_FILE / MAKING_PREVIEW` |
| current_file_index / current_file_total | INT | Y | "파일 받는 중 (2/2)" |
| consecutive_unavailable | INT | N | IMSLP 무응답 연속 횟수(3 → PAUSED) |
| paused_until | TIMESTAMP(6) | Y | 자동 재시도 시각(PAUSED 일 때) |
| pause_count | INT | N | 자동 재시도 횟수. 2번째 PAUSED 도 실패하면 STOPPED |
| failure_reason | VARCHAR(300) | Y | 작업 자체 실패/중지 사유 문구 |
| retry_of_job_id | BIGINT | Y | "실패한 것만 다시 시도"로 만든 작업의 원본 작업 id |
| created_by | VARCHAR(100) | N | 관리자 username |
| started_at / finished_at | TIMESTAMP(6) | Y | |
| created_at / updated_at | TIMESTAMP(6) | N | |

인덱스: `idx_crawl_job_status`, `idx_crawl_job_created_at`. **동시에 RUNNING/PAUSED 인 작업은 1개** — 서비스가 생성·재개 시 검사(409). DB 제약은 두지 않는다(MySQL 부분 유니크 불가).

### 3-9. `crawl_item` — 수집 항목

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| id | BIGINT PK | N | |
| job_id | BIGINT FK→crawl_job | N | |
| seq | INT | N | 입력 순서(1부터). `uk_crawl_item(job_id, seq)` |
| imslp_url | VARCHAR(500) | N | 정규 형태 URL |
| item_mode | VARCHAR(30) | N | enum `CrawlItemMode`: `CREATE`(새 곡) / `ATTACH`(시드·직접 등록된 곡에 판본 붙임) / `REFRESH`(정보만 갱신, 파일 재요청 없음) / `SKIP`(이미 있음·갱신 안 함) |
| status | VARCHAR(30) | N | enum `CrawlItemStatus`: `PENDING / PROCESSING / SUCCESS / FAILED / SKIPPED / HIDDEN`(비피아노로 등록되되 숨김) |
| fail_reason | VARCHAR(30) | Y | enum `CrawlFailReason`: `PAGE_NOT_FOUND / NO_PDF_EDITION / FILE_DOWNLOAD_FAILED / IMSLP_UNAVAILABLE / INTERRUPTED / INTERNAL_ERROR`(메타 읽기 중 예상 밖 오류 = 우리 쪽 버그, 2026-09-07 추가) |
| message | VARCHAR(500) | Y | 사람이 읽을 결과 문구(성공: "판본 14개, 파일 2개 받음") |
| work_id | BIGINT FK→work | Y | 만들었거나 붙인 곡 |
| edition_count / file_count | INT | Y | 결과 요약 |
| started_at / finished_at | TIMESTAMP(6) | Y | |

인덱스: `idx_crawl_item_job_status(job_id, status)`.

---

## 4. 상태값(enum) 총정리

| enum | 값 | 사용처 |
|---|---|---|
| `Level` | BEGINNER(입문) / ELEMENTARY(초급) / INTERMEDIATE(중급) / ADVANCED(고급) — NULL = 미정 | work.level, 필터 |
| `KoreaCopyright` | FREE / RESTRICTED / UNKNOWN | edition, 뱃지 |
| `LicenseCode` | PD / CC0 / CC_BY / CC_BY_SA / CC_BY_NC / CC_BY_NC_SA / CC_BY_NC_ND / OTHER | edition.imslp_license_code |
| `EditionKind` | COMPLETE_SCORE / PARTS / ARRANGEMENT | edition.kind |
| `EditionScope` | COMPLETE / MOVEMENT | edition.scope |
| `WorkStatus` (계산값, 컬럼 아님) | READY(바로 받기 가능) / PREPARING(준비 중) / RESTRICTED(이용 제한) / UNKNOWN(저작권 확인 중) | 검색·상세·관리 목록 |
| `WorkMissing` (계산값) | TITLE_KO / ALIAS / LEVEL / RECOMMENDED_EDITION | 관리 "보완 필요" |
| `HiddenReason` | NOT_PIANO_SOLO | work.hidden_reason |
| `AliasSource` | ADMIN / SEED / IMSLP | work_alias.source |
| `FileFetchStatus` | QUEUED / FETCHING / FAILED | edition.file_fetch_status |
| `CrawlJobStatus` | RUNNING / PAUSED / STOPPED / COMPLETED / FAILED | crawl_job |
| `CrawlStage` | READING_METADATA / DOWNLOADING_FILE / MAKING_PREVIEW | crawl_job.current_stage |
| `CrawlItemMode` | CREATE / ATTACH / REFRESH / SKIP | crawl_item |
| `CrawlItemStatus` | PENDING / PROCESSING / SUCCESS / FAILED / SKIPPED / HIDDEN | crawl_item |
| `CrawlFailReason` | PAGE_NOT_FOUND / NO_PDF_EDITION / FILE_DOWNLOAD_FAILED / IMSLP_UNAVAILABLE / INTERRUPTED / INTERNAL_ERROR | crawl_item |
| `RefType` (기존, 값 추가) | COMMUNITY / USER / **EDITION** | files.ref_type |
| `Usage` (기존, 변경 없음) | THUMBNAIL(=미리보기 PNG) / IMAGES / ATTACHMENT(=악보 PDF) | files.file_usage |

### 곡 준비 상태 계산 (`WorkStatus`) — 검색·상세·관리·대시보드가 전부 같은 규칙

```
recommended = work.recommended_edition
if recommended == null or recommended.pdf_file_id == null  → PREPARING
else if recommended.korea_copyright == FREE                 → READY
else if recommended.korea_copyright == RESTRICTED           → RESTRICTED
else                                                        → UNKNOWN
```
"바로 받기 가능" 필터 = `READY`. 판본 단위 "바로 받기 가능" = `pdf_file_id IS NOT NULL AND korea_copyright = FREE`.

### 보완 필요 계산 (`needsWork`, 관리자 전용)
`title_ko` 가 NULL/공백 **또는** 별칭 0개 **또는** level NULL **또는** recommended_edition_id NULL → 보완 필요. `missing[]` 에 해당 항목을 나열한다.

---

## 5. 기존 테이블 변경

| 테이블 | 변경 | 영향 |
|---|---|---|
| `files` | `RefType` 에 `EDITION` 추가. `Usage` 는 그대로(PDF=ATTACHMENT, 미리보기=THUMBNAIL) | enum STRING 저장이라 DDL 변화 없음. `FileService.verifyOwnership` 의 switch 에 EDITION 분기 추가(ADMIN 만 허용) |
| `user_roles` | 시드 `data-user-roles.sql` 에 `(3,'ADMIN')` 1건 추가(로컬·테스트용 관리자 = `gks930620`) | 운영은 시드가 안 돌므로 관리자 역할은 DB 에서 직접 1회 부여 |
| `data-h2-reset-identity.sql` | 변경 없음 — 새 테이블은 시드가 명시 id 를 쓰지 않는다(§6) | |

---

## 6. 시드 적재 — 03 문서 곡 50 + 작곡가 25

**결정: `data-*.sql` 이 아니라 CSV + 자바 로더(`SeedLoader`, `ApplicationRunner`)** 로 적재한다. 근거는 `03_기술결정.md` §8. 요약: 정규화 컬럼을 SQL 로 손으로 계산할 수 없고, 별칭 500여 건을 SQL 로 옮기면 오타를 잡을 수 없으며, 운영에도 같은 코드로 1회 적재(멱등)가 된다.

### 파일 (`src/main/resources/seed/`)

`composers.csv` — 03 §2 표 1:1 (헤더 고정, UTF-8, RFC 4180 큰따옴표 인용)

| 열 | 예 |
|---|---|
| name_ko | 쇼팽 |
| name_original | Chopin, Frédéric |
| birth_year | 1810 |
| death_year | 1849 |
| aliases | `프레데리크 쇼팽\|프레데릭 쇼팽\|쇼팡\|Chopin\|Fryderyk Chopin` — 구분자 `\|` |
| nationality | 폴란드 |

`works.csv` — 03 §1 표 1:1

| 열 | 예 |
|---|---|
| seq | 21 |
| composer_original | Beethoven, Ludwig van |
| title_ko | 월광 소나타 |
| title_original | Piano Sonata No.14, Op.27 No.2 |
| catalog_numbers | `Op.27 No.2` — 여러 개면 `\|`. 03 의 "(없음)" 은 빈 칸. `D.899 (Op.90)` 처럼 괄호 병기는 `D.899\|Op.90` 두 개로 |
| aliases | `월광\|월광 소나타\|Moonlight Sonata\|…` — 03 은 쉼표 구분이지만 CSV 에서는 `\|` 로 옮긴다(제목 열의 쉼표와 헷갈리지 않게). 띄어쓰기만 다른 변형은 로더가 정규화 중복으로 자동 제거(03 §5-1) |
| level | INTERMEDIATE |
| imslp_url | `https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)` — 로더가 정규화 |

무소르그스키(2차 후보)는 `composers.csv` 에서 제외한다(03 §2 각주).

### 로더 규칙
- 실행 시점: 매 기동(로컬은 DB 가 휘발이므로 매번 새로 들어감 = 컨벤션 §5-2 와 동일 효과). `app.seed.enabled`(기본 true) 로 끌 수 있다. 테스트 프로파일도 true — 검색 인수조건("월광" → 소나타 14번)을 실제 시드로 검증한다.
- **멱등·삽입 전용**: 작곡가는 `name_original_normalized`, 곡은 `imslp_url` 로 존재 여부를 보고 **없을 때만 INSERT**. 있으면 어떤 컬럼도 덮어쓰지 않는다(관리자 수정 보호). 별칭·작품번호는 정규화 값이 없을 때만 추가(`source = SEED`).
- 운영(MySQL, `sql.init.mode=never`)에서도 로더는 돈다 → 첫 배포에 1회 적재, 이후 기동은 no-op. 03 문서가 검수로 바뀌면 CSV 갱신 → 다음 배포에서 새 행만 추가된다. 삭제·수정은 관리자 화면에서.
- 시드 곡은 `recommended_edition_id = NULL`, 판본 0개 → 사용자에게 "준비 중"으로 보이며 별칭 검색은 즉시 된다. 수집이 같은 `imslp_url` 을 처리하면 `ATTACH` 모드로 판본을 붙인다(03 §9-3 가정).

---

## 7. 삭제·정합성 규칙 (서비스 구현 계약)

| 동작 | 순서 |
|---|---|
| 작곡가 삭제 | 곡 1개라도 있으면 거부(400). 없으면 별칭 → 작곡가 |
| 곡 삭제 | `recommended_edition_id = NULL` → 판본마다(§아래 판본 삭제) → download_log(work) → 별칭·작품번호(cascade) → crawl_item.work_id NULL 처리 → 곡 |
| 판본 삭제 | 추천이면 곡의 `recommended_edition_id = NULL` → download_log(edition) 삭제 → `files` 행(pdf, preview) 삭제 + 바이트 삭제는 **커밋 후**(기존 `FileService.registerBytesDeletionAfterCommit` 패턴) → 판본 |
| 파일 교체 | 새 files 행 연결 후 옛 files 행 삭제(바이트는 커밋 후) |
| 추천 지정 | 대상 판본이 그 곡의 것이고 `pdf_file_id IS NOT NULL` 일 때만. 이전 추천은 자동 해제(컬럼 하나라 자연히) |
| 다운로드 | 파일 Resource 확보 성공 후 짧은 트랜잭션에서 `edition.download_count+1`, `work.download_count+1`, `download_log` INSERT(원자적 UPDATE 문). 실패(파일 없음)면 아무것도 올리지 않는다 |
| 수집 upsert | `imslp_url` 로 곡 조회 → 없으면 생성(CREATE), 있으면 판본만 붙임(ATTACH/REFRESH). 판본은 `imslp_file_id` 로 조회 → 있으면 메타만 갱신(파일·판정·메모는 보존), 없으면 생성 |

---

## 8. H2(MySQL 모드) ↔ MySQL 8 호환 체크

- BOOLEAN: H2 `BOOLEAN`, MySQL `TINYINT(1)` — JPA `boolean` 으로 양쪽 자동.
- `Instant` → `TIMESTAMP(6)` / `DATETIME(6)`, `hibernate.jdbc.time_zone=UTC` 명시.
- 순환 FK(work ↔ edition): Hibernate `ddl-auto` 가 CREATE TABLE 후 ALTER TABLE ADD CONSTRAINT 로 처리 — 양쪽 OK. 곡 삭제 시 서비스가 먼저 NULL 처리(§7).
- NULL 허용 UNIQUE(`work.imslp_url`, `edition.imslp_file_id`): 양쪽 모두 NULL 여러 개 허용.
- 정렬 collation: 한글 가나다순은 utf8mb4 계열/H2 기본 모두 코드포인트 순 = 가나다 순. 대소문자 무시 비교는 collation 에 기대지 않고 정규화 컬럼으로 한다.
- 예약어: §1 표 참고. `@Column(name=…)` 으로 전부 명시해 Hibernate 네이밍 전략 차이에 기대지 않는다.
