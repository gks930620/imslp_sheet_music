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
| section | VARCHAR(30) | N | **2026-09-10 추가.** enum `Section`: `PIANO / VIOLIN / ORCHESTRA`. **곡은 정확히 하나의 구분에 속한다**(기획 04 §5). 기본값 `PIANO`(`@ColumnDefault("'PIANO'")`) — 1차에 값을 바꾸는 경로가 **없다**(§9-2). `hidden_reason = NOT_PIANO_SOLO` 로 숨긴 곡도 `PIANO` 그대로다: 숨김 사유는 부정형(`피아노가 아니다`)이라 그 안에 바이올린·총보·성악이 섞여 있어 **자동으로 다른 구분이 될 수 없다**(기획 04 §2-2). 계약은 02 §0-7 |
| title_ko | VARCHAR(300) | Y | 한국어 대표 제목. 비면 "보완 필요" |
| title_ko_normalized | VARCHAR(300) | Y | |
| title_original | VARCHAR(300) | N | 원어 제목(IMSLP Work Title) |
| title_original_normalized | VARCHAR(300) | N | |
| level | VARCHAR(30) | Y | enum `Level`: `BEGINNER / ELEMENTARY / INTERMEDIATE / ADVANCED`. **NULL = 난이도 미정** |
| composition_year | VARCHAR(20) | Y | IMSLP 원문이 `1830-31` 같은 범위라 문자열 |
| musical_key | VARCHAR(50) | Y | 조성 |
| movements | VARCHAR(500) | Y | 악장 구성(자유 문장) |
| movement_page_guide | VARCHAR(500) | Y | 악장 페이지 안내(관리자 입력) |
| collection_guide | VARCHAR(500) | Y | **2026-09-08 추가.** 수록곡 안내(01 §0-3, §2 F3-2). 곡 번호 기준 완성 문장이며 시드(`works.csv`)로 들어온다 — 출처는 `03_1차_큐레이션_곡목록.md` §10. **값이 비어 있지 않으면 그 곡은 "묶음 악보"** 다(03 §10-1) — 검색 항목의 `scopeNote`(02 §2-2)와 곡 상세의 수록곡 안내가 이 값 하나로 갈린다. `movement_page_guide`(쪽수 기준·추천 판본이 바뀌면 틀려짐)와 **별개 필드**다 |
| imslp_url | VARCHAR(500) | Y | **정규 형태 UNIQUE**(NULL 허용 — MySQL/H2 모두 NULL 중복 허용). 수집·시드 동일성 키. 정규 형태 = `https://imslp.org/wiki/` + 퍼센트 디코딩한 제목(공백→`_`) — `02_API_명세서.md` §6-1 |
| hidden | BOOLEAN | N | 기본 false. 숨김이면 사용자 화면 어디에도 안 나옴 |
| hidden_reason | VARCHAR(30) | Y | enum `HiddenReason`: `NOT_PIANO_SOLO`(수집 자동 숨김). 관리자가 직접 숨기면 NULL |
| recommended_edition_id | BIGINT FK→edition | Y | **추천 판본(곡당 0~1)**. 순환 FK 지만 Hibernate 가 테이블 생성 후 ALTER 로 제약을 건다. 곡 삭제 시 서비스가 먼저 NULL 로 만든 뒤 판본 삭제 |
| recommended_edition_reviewed | BOOLEAN | N | **2026-09-08 추가.** 기본 false. **지금 추천 판본이 사람 눈을 통과했는가**(기획 §F6-4 "확인함", 공개 기준 §8-17). `recommended_edition_id` 가 **바뀌거나 NULL 이 되면 false 로 초기화**한다 — 확인한 것은 "그 판본"이 아니라 "지금 추천"이다. "누가·언제"는 남기지 않는다: 답이 추천 변경으로 곧바로 무효가 되므로 이력이 아니라 상태다. 계약은 02 §5-6-1 |
| download_count | BIGINT | N | 기본 0. 곡 누적 다운로드(판본 합계를 비정규화). 정렬용. 원본은 `download_log` |
| created_at / updated_at | TIMESTAMP(6) | N | |

인덱스: `idx_work_composer(composer_id)`, `uk_work_imslp_url(imslp_url)`, `idx_work_title_ko_normalized`, `idx_work_title_original_normalized`, `idx_work_download_count`, `idx_work_updated_at`(관리 목록 정렬), `idx_work_hidden`.

> `LIKE '%…%'` 는 B-tree 인덱스를 못 타지만 1차 규모(수백 곡)에서는 풀스캔이 밀리초다. 정규화 컬럼 인덱스는 정확·전방 일치(일치도 정렬)와 유니크용이다.
>
> **2026-09-07 재확인(senior-dev).** 실제 수집으로 드러난 규모는 **곡 수백 · 판본 수만**이다(곡 1개당 판본 70개, 20곡 1,792개 → 300곡이면 25,000개).
> 이 비대칭이 중요하다: 검색·목록의 `LIKE` 풀스캔은 여전히 `work`(수백 행) 위에서만 돌고 `edition` 은 검색 경로에 없으므로 **문제가 아니다**.
> 규모가 깨뜨리는 것은 **판본을 곡 단위로 통째로 읽는 자리**뿐이다 — 곡 상세 `otherEditions`(02 §3-3 에서 잘라 냄),
> 관리 곡 목록의 `editionCount`(판본 전량 로드 금지, `count(*) group by work_id` 프로젝션으로), 자동 판정 §5-11(트랜잭션 청크, 03 §16).

**추천 후보(candidate)** 는 컬럼이 아니라 계산값이다: `kind = COMPLETE_SCORE AND scope = COMPLETE AND pdf_file_id IS NOT NULL` 인 판본 중 `imslp_download_count` 최대(동률이면 id 최소). `recommended_edition_id` 가 있으면 후보를 표시하지 않는다. (컬럼으로 두면 "파일 받아오기" 뒤 갱신 누락이 생긴다.)

> **추천 자동 지정(02 §5-11)** 은 이 후보 규칙에 `korea_copyright = FREE` 를 **더한** 것으로 고른다. 화면의 후보 표시(§4-7 `candidateEditionId`)는 판정 전에도 관리자에게 "이걸 추천으로 세우면 된다"를 보여줘야 하므로 판정 조건이 없지만, 자동 지정은 곡을 `READY` 로 만들어 **다운로드를 여는 행위**라 판정된 판본만 고른다.

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
| sort_key | VARCHAR(600) | N | 작품번호 순 정렬용: normalized 값의 숫자 구간을 6자리 0-패딩 (`op000027no000002`, `bwv000846`). 문자열 정렬로 `Op.9 < Op.10` 이 되게. **길이는 원문(100)이 아니라 팽창 후 기준**(2026-09-08 개정 — 아래) |
| sort_order | INT | N | 곡 안 표시 순서(0부터). 0번이 대표 작품번호(다운로드 파일명·정렬에 사용) |
| created_at | TIMESTAMP(6) | N | |

인덱스: `uk_work_catalog(work_id, catalog_value_normalized)`, `idx_work_catalog_normalized`.

> **`sort_key` 는 파생값이라 저장을 깨뜨리면 안 된다 (2026-09-08, senior-dev — 결함 6 테스트 중 발견).**
> `catalog_value` 상한은 100자(02 §0-6)인데 `sort_key` 는 숫자 구간을 **6자리로 0-패딩**하므로 값이 **길어진다**.
> `"Op.1 Op.1 …"`(99자, 숫자 20개)면 `op000001` 20개 = 160자로 부풀어 `VARCHAR(120)` 을 넘고,
> **검증을 통과한 정상 입력이 500**(`DataIntegrityViolationException`)이 된다 — qa 결함 D3 와 같은 모양이다.
> 최대 팽창은 100자 원문이 350자(`a1a1…` 처럼 1자리 숫자 50개 → 각 6자)라 **컬럼을 `VARCHAR(600)`** 으로 두고,
> 계산 결과가 그보다 길면 **잘라서 쓴다**(잘린 정렬 키는 정렬이 뭉개질 뿐 데이터를 잃지 않는다).
> 반대로 사용자에게 보이는 원문 상한을 내부 정렬 구현에 맞춰 줄이는 방향은 버린다 — 설명할 수 없는 제약이 된다.
> 계약 검증: `AdminSaveLengthValidationIntegrationTest#work_catalogNumber_maxLength_andDerivedSortKeyDoesNotOverflow`.

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
| copyright_judged_by | VARCHAR(100) | Y | 판정한 관리자 username. **자동 판정(02 §5-11)은 고정 센티널 `system:auto`** — username 에는 `:` 가 못 들어가므로 사람 판정과 절대 겹치지 않고, 되돌리기(§5-12) 대상을 이 값 하나로 고를 수 있다 |
| cc_license_name | VARCHAR(100) | Y | 사용자 표시용 (`CC BY-SA 4.0`) |
| cc_attribution | VARCHAR(200) | Y | 표기할 저작자 |
| file_fetch_status | VARCHAR(30) | Y | enum `FileFetchStatus`: `QUEUED / FETCHING / FAILED`. NULL = 요청 없음/완료 |
| file_fetch_error | VARCHAR(300) | Y | 마지막 받아오기 실패 사유 |
| file_fetched_at | TIMESTAMP(6) | Y | 수집/받아오기로 파일을 얻은 시각 |
| admin_edited_at | TIMESTAMP(6) | Y | **2026-09-07 추가.** 관리자가 §5-3 으로 이 판본을 저장한 마지막 시각. NULL = 아직 사람이 손대지 않음(수집이 만든 그대로). **재수집이 관리자 편집 필드를 덮을지 판단하는 유일한 근거**(02 §6-12). §5-9 저작권 판정으로는 찍지 않는다 — 판정은 편집이 아니고, 표기 원문은 계속 IMSLP 를 따라가야 라이선스 강등 회수(02 §6-12 (2))가 작동한다 |
| download_count | BIGINT | N | 기본 0. 우리 서비스 다운로드 수 |
| created_at / updated_at | TIMESTAMP(6) | N | |

인덱스: `idx_edition_work(work_id)`, `uk_edition_imslp_file_id(imslp_file_id)`, `idx_edition_korea_copyright(korea_copyright)`(대기함), `idx_edition_pdf_file(pdf_file_id)`.

`files` 와의 관계: **재사용한다**(별도 파일 테이블을 만들지 않음). 이유는 `03_기술결정.md` §2. 정합성 규칙: `pdf_file_id`/`preview_file_id` 가 가리키는 `files` 행은 `ref_type = EDITION`, `ref_id = edition.id`. 업로드 직후(판본 저장 전)에는 `ref_id = 0` 임시 상태이며 판본 저장 시 연결한다(컨벤션 §5-3-1 ①). 판본 삭제·파일 교체 시 `files` 행과 바이트를 함께 지운다(②). 24시간 지난 `ref_id = 0` 파일은 기존 orphan 배치가 정리한다(③).

### 3-7. `download_log` — 다운로드 기록

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| id | BIGINT PK | N | |
| edition_id | BIGINT FK→edition | **Y** | **2026-09-08 개정**: 판본 삭제 시 로그를 지우지 않고 이 값만 `NULL` 로 만든다 |
| work_id | BIGINT FK→work | N | 비정규화. **로그의 주인은 곡이다** — 판본 삭제 후에도 곡 단위 집계를 유지하기 위한 것이며(조회 편의가 아니다), 곡 삭제 시에만 로그를 지운다 |
| downloaded_at | TIMESTAMP(6) | N | |

> **`download_log` 가 원장이고 `work.download_count` 는 그 합계 캐시다 (2026-09-08 확정, senior-dev — qa 3차 결함 8).**
> 4번 받은 판본을 지우면 `work.download_count` 는 4인데 로그는 0행이 되어, **인기곡 정렬(02 §3-2)** 과
> **대시보드 `monthlyDownloads`(02 §4-1)** 가 같은 사건을 다르게 셌다(실측 13 → 9).
> 다운로드는 **곡 단위 사건**이다 — 사용자가 받은 것은 "월광 소나타" 이지 "판본 #301" 이 아니고,
> 판본은 우리가 운영상 교체하는 파일일 뿐이다. 그래서 **판본 삭제로는 로그도 `work.download_count` 도 줄지 않는다**.
> 곡 삭제는 로그도 지운다(`work_id` 가 NOT NULL 이라 가리킬 곳이 없어진다).

> **기존 DB 마이그레이션이 필요하다 (2026-09-08, senior-dev — backend-dev 재현 보고).**
> `edition_id` 의 `NOT NULL` **해제는 비추가형 변경**이라 `ddl-auto: update` 가 반영하지 않는다.
> 이미 스키마가 만들어진 로컬 파일 H2·운영 MySQL 에서는 컬럼이 그대로 `NOT NULL` 이므로, 위 규칙대로 구현해도
> **판본 삭제가 500(`NULL not allowed for column "EDITION_ID"`)** 으로 실패한다. 테스트는 `create-drop` 이라 못 잡는다(03 §17-5).
> 한 줄 적용:
> ```sql
> -- H2 (로컬 파일 DB: data/devdb)
> ALTER TABLE download_log ALTER COLUMN edition_id SET NULL;
> -- MySQL 8 (운영)
> ALTER TABLE download_log MODIFY edition_id BIGINT NULL;
> ```
> 새로 만드는 DB(테스트·초기화 후 로컬)는 엔티티대로 생성되므로 실행할 필요가 없다. 대장은 §9.

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
| edition_count / file_count | INT | Y | 결과 요약. **FAILED 라도 곡·판본이 이미 저장됐다면 채운다** — 파일 단계 실패(`FILE_DOWNLOAD_FAILED`)와 파일 단계 무응답(`IMSLP_UNAVAILABLE`) 모두 해당(2026-09-07 명확화, 02 §6-10) |
| started_at / finished_at | TIMESTAMP(6) | Y | |

인덱스: `idx_crawl_item_job_status(job_id, status)`.

---

## 4. 상태값(enum) 총정리

| enum | 값 | 사용처 |
|---|---|---|
| `Section` (2026-09-10 신설) | PIANO(피아노) / VIOLIN(바이올린) / ORCHESTRA(오케스트라) — **NULL 없음**, 기본 PIANO | work.section, 공개 API 의 `section` 파라미터(02 §0-7) |
| `Level` | BEGINNER(입문) / ELEMENTARY(초급) / INTERMEDIATE(중급) / ADVANCED(고급) — NULL = 미정 | work.level, 필터 |
| `KoreaCopyright` | FREE / RESTRICTED / UNKNOWN | edition, 뱃지 |
| `LicenseCode` | PD / CC0 / CC_BY / CC_BY_SA / CC_BY_NC / CC_BY_NC_SA / CC_BY_NC_ND / OTHER | edition.imslp_license_code |
| `EditionKind` | COMPLETE_SCORE / PARTS / ARRANGEMENT | edition.kind |
| `EditionScope` | COMPLETE / MOVEMENT | edition.scope |
| `WorkStatus` (계산값, 컬럼 아님) | READY(바로 받기 가능) / PREPARING(준비 중) / RESTRICTED(이용 제한) / UNKNOWN(저작권 확인 중) | 검색·상세·관리 목록 |
| `WorkMissing` (계산값) | TITLE_KO / ALIAS / LEVEL / RECOMMENDED_EDITION / **COPYRIGHT_JUDGMENT** | 관리 "보완 필요" |
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

### 보완 필요 계산 (`needsWork`, 관리자 전용) — 2026-09-08 개정 (기획 §11-1)

`missing[]` 은 아래 다섯 가지를 **이 순서로** 담는다. 하나라도 있으면 `needsWork = true`.

| 값 | 조건 |
|---|---|
| `TITLE_KO` | `title_ko` 가 NULL/공백 |
| `ALIAS` | 별칭 0개 |
| `LEVEL` | `level` NULL(난이도 미정) |
| `RECOMMENDED_EDITION` | `recommended_edition_id` NULL |
| `COPYRIGHT_JUDGMENT` | 추천 판본이 **있고** 그 판본의 `korea_copyright = UNKNOWN` |

- `RECOMMENDED_EDITION` 과 `COPYRIGHT_JUDGMENT` 는 **동시에 나올 수 없다**(앞은 추천 없음, 뒤는 추천 있음).
- **`RESTRICTED` 는 세지 않는다** — 사람이 내린 결론이라 할 일이 아니라 끝난 일이다(기획 §11-1).
- **`recommended_edition_reviewed = false`(추천 판본 미검수)도 여기 넣지 않는다** (2026-09-08). 보완 필요가 답하는 질문은
  "이 곡을 사용자에게 **열어 주려면** 뭐가 남았나" 인데, 미검수 곡은 **이미 열려 있다**(바로 받기 가능). 두 목록을 섞으면
  공개 기준 §8-17 의 두 조건("35곡 이상 바로 받기 가능" + "미검수 0곡")이 한 숫자로 뭉개져 진척을 볼 수 없다.
  미검수는 별도 지표·별도 필터다(02 §4-1 `needsRecommendationReviewWorks`, §4-6 `status=NEEDS_RECOMMENDATION_REVIEW`).
- 이 규칙을 쓰는 곳은 **셋뿐이고 전부 같은 함수를 쓴다**: 관리 곡 목록 필터 `status=NEEDS_WORK`(02 §4-6) ·
  관리 홈 `needsWorkWorks`(02 §4-1) · 곡 상세 `missing[]`(02 §4-7). 한 곳에서 사라진 곡이 다른 곳에 남으면 결함이다.

> **왜 추가했나 (기획 §11-1).** '보완 필요'가 답하는 질문은 "필드가 비었나"가 아니라 **"이 곡을 사용자에게 열어 주려면 뭐가 남았나"** 다.
> 자동 판정 되돌리기(02 §5-12)는 추천을 유지한 채 판정만 `UNKNOWN` 으로 되돌리므로, 옛 규칙에서는 그 곡이
> `RECOMMENDED_EDITION` 도 아니어서 **일감 목록에서 사라졌다** — 되돌리기를 누른 이유(다시 보겠다)가 무효가 된다.
> 참고: 되돌리기 후 그 곡의 `WorkStatus` 는 `PREPARING` 이 아니라 `UNKNOWN` 이다(추천이 남아 있으므로).
> 그래서 대시보드의 `preparingWorks` 는 실행 전 값으로 돌아가지 않고, `readyWorks`·`needsWorkWorks`·`unknownCopyrightEditions` 가 돌아간다.

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
| collection_guide | `이 악보에는 왈츠 3곡이 들어 있어요 — …` — **2026-09-08 추가**. 03 §10 표의 문구 그대로(38곡), 단일 곡 12곡은 빈 칸. `work.collection_guide` 로 적재 |

> **`seq`(큐레이션 노출 순서) 열은 읽지 않는다 — 컬럼으로도 두지 않는다 (2026-09-08 결정, senior-dev).**
> CSV 에 남겨 두는 것은 사람이 03 문서와 대조하기 위한 것이고, 엔티티·DB 에는 싣지 않는다.
> 인기곡 tie-breaker 로 쓰자는 요구(기획 §11-3)는 **난이도 오름차순 → 가나다**로 대신한다 — 근거는 `03_기술결정.md` §18.

무소르그스키(2차 후보)는 `composers.csv` 에서 제외한다(03 §2 각주).

### 3-10. `seed_load` — 시드 적재 기록 (2026-09-07 추가)

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| id | BIGINT PK | N | |
| seed_type | VARCHAR(30) | N | enum `SeedType`: `COMPOSER / WORK / COLLECTION_GUIDE`(2026-09-08 추가 — §6 백필) |
| natural_key | VARCHAR(500) | N | 작곡가 `name_original_normalized`, 곡 `imslp_url`(정규 형태) |
| loaded_at | TIMESTAMP(6) | N | |

인덱스: `uk_seed_load(seed_type, natural_key)`.

> **왜 필요한가.** 03 §17 로 로컬 DB 가 **파일 DB**(재시작을 넘어 살아남음)가 되면서, "없으면 INSERT" 만으로는
> **관리자가 §4-9 로 지운 시드 곡이 다음 기동에 새 id 로 되살아난다**. 삭제가 되돌려지고 그 곡에 붙은 작업도 다시 해야 한다.
> 운영(MySQL)도 같은 구조라 배포마다 반복된다. 적재 기록을 남기면 "지웠으니 다시 넣지 않는다" 와
> "CSV 에 새로 추가된 행은 다음 배포에 들어온다" 를 동시에 만족한다. 검증: `SeedDeletionIntegrationTest`.

### 로더 규칙
- 실행 시점: 매 기동. `app.seed.enabled`(기본 true) 로 끌 수 있다. 테스트 프로파일도 true — 검색 인수조건("월광" → 소나타 14번)을 실제 시드로 검증한다.
- **멱등·삽입 전용**: 작곡가는 `name_original_normalized`, 곡은 `imslp_url` 로 존재 여부를 보고 **없을 때만 INSERT**. 있으면 어떤 컬럼도 덮어쓰지 않는다(관리자 수정 보호). 별칭·작품번호는 정규화 값이 없을 때만 추가(`source = SEED`).
- **한 번 적재한 행은 다시 적재하지 않는다**(2026-09-07): CSV 행마다 `seed_load` 에 자연키가 있으면 **건너뛴다**. 없으면 (INSERT 했든, 이미 같은 자연키의 행이 DB 에 있어 건너뛰었든) `seed_load` 에 기록을 남긴다 — 기록이 없는 기존 DB 에서 첫 실행이 중복을 만들지 않게 하기 위해서다.
- 운영(MySQL, `sql.init.mode=never`)에서도 로더는 돈다 → 첫 배포에 1회 적재, 이후 기동은 no-op. 03 문서가 검수로 바뀌면 CSV 갱신 → 다음 배포에서 새 행만 추가된다. 삭제·수정은 관리자 화면에서.
- **`collection_guide` 1회 백필 (2026-09-08 추가)** — 위 "한 번 적재한 행은 다시 적재하지 않는다" 규칙 때문에, 컬럼을 새로 만들면 **이미 적재된 시드 곡 50개는 영원히 NULL** 이다(로컬 파일 DB·운영 모두). 그러면 묶음 악보 38곡의 곡 상세에서 사용자가 검색한 이름이 화면 어디에도 없다(기획 §10-3). 그래서 이 값만 별도 패스로 채운다.
  1. CSV 행의 `collection_guide` 가 비어 있지 않고, `seed_load(COLLECTION_GUIDE, imslp_url)` **기록이 없을 때만** 대상.
  2. `imslp_url` 로 곡을 찾아 **`collection_guide` 가 NULL/공백일 때만** 채운다(관리자가 이미 쓴 값은 덮지 않는다).
  3. 곡이 없든, 값이 이미 있든, 채웠든 — 결과와 무관하게 `seed_load(COLLECTION_GUIDE, …)` 기록을 남긴다. **그래서 다음 기동부터는 아무 일도 하지 않는다**(관리자가 지운 문구가 되살아나지 않는다).
  4. 이 예외는 **`collection_guide` 한 컬럼에만** 적용한다. 다른 컬럼을 같은 방식으로 백필하고 싶어지면 그때 이 절을 다시 연다 — "시드는 삽입 전용" 원칙에 구멍을 여러 개 뚫으면 시드가 관리자 입력을 언제 덮는지 아무도 설명하지 못하게 된다.
- 시드 곡은 `recommended_edition_id = NULL`, 판본 0개

---

## 7. 삭제·정합성 규칙 (서비스 구현 계약)

| 동작 | 순서 |
|---|---|
| 작곡가 삭제 | 곡 1개라도 있으면 거부(400). 없으면 별칭 → 작곡가 |
| 곡 삭제 | `recommended_edition_id = NULL` → 판본마다(§아래 판본 삭제) → download_log(work) → 별칭·작품번호(cascade) → crawl_item.work_id NULL 처리 → 곡 |
| 판본 삭제 | 추천이면 곡의 `recommended_edition_id = NULL` → **download_log(edition) 의 `edition_id` 를 NULL 로**(행은 남긴다 — §3-7) → `files` 행(pdf, preview) 삭제 + 바이트 삭제는 **커밋 후**(기존 `FileService.registerBytesDeletionAfterCommit` 패턴) → 판본 |
| 파일 교체 | 새 files 행 연결 후 옛 files 행 삭제(바이트는 커밋 후) |
| 추천 지정 | 대상 판본이 그 곡의 것이고 `pdf_file_id IS NOT NULL` 일 때만. 이전 추천은 자동 해제(컬럼 하나라 자연히) |
| 다운로드 | 파일 Resource 확보 성공 후 짧은 트랜잭션에서 `edition.download_count+1`, `work.download_count+1`, `download_log` INSERT(원자적 UPDATE 문). 실패(파일 없음)면 아무것도 올리지 않는다 |
| 수집 upsert | `imslp_url` 로 곡 조회 → 없으면 생성(CREATE), 있으면 판본만 붙임(ATTACH/REFRESH). 판본은 `imslp_file_id` 로 조회 → 있으면 메타만 갱신(파일·판정·메모는 보존), 없으면 생성. **갱신 범위·라이선스 강등 회수는 02 §6-12** |
| 수집 판본 조회 | `imslp_file_id` 는 전역 UNIQUE 라 조회도 전역이다. 찾은 판본의 `work_id` 가 **지금 수집 중인 곡이 아니면** 그 판본은 건드리지 않고 건너뛴다(경고 로그) — 다른 곡의 판본을 조용히 갱신하거나 곡 사이를 옮겨 다니면 판본 수 집계와 추천이 어긋난다 (2026-09-07 추가) |
| 파일 받아오기 결과 붙이기 | 대상 판본이 **그 사이에 파일을 갖게 되었으면 붙이지 않는다** — 받아온 `files` 행을 삭제하고 `file_fetch_status` 만 비운다. 덮어쓰면 밀려난 행이 `ref_id ≠ 0` 이라 orphan 배치가 못 지운다 (02 §5-7, 2026-09-07 추가) |
| 재시작 복구 | `file_fetch_status IN (QUEUED, FETCHING)` 인 판본은 `FAILED` + 사유로 되돌린다 — 비동기 작업은 프로세스와 함께 사라졌는데 상태만 남으면 §5-7 이 409 로 영구히 막는다 (2026-09-07 추가) |

---

## 8. H2(MySQL 모드) ↔ MySQL 8 호환 체크

- BOOLEAN: H2 `BOOLEAN`, MySQL `TINYINT(1)` — JPA `boolean` 으로 양쪽 자동.
- `Instant` → `TIMESTAMP(6)` / `DATETIME(6)`, `hibernate.jdbc.time_zone=UTC` 명시.
- 순환 FK(work ↔ edition): Hibernate `ddl-auto` 가 CREATE TABLE 후 ALTER TABLE ADD CONSTRAINT 로 처리 — 양쪽 OK. 곡 삭제 시 서비스가 먼저 NULL 처리(§7).
- NULL 허용 UNIQUE(`work.imslp_url`, `edition.imslp_file_id`): 양쪽 모두 NULL 여러 개 허용.
- 정렬 collation: 한글 가나다순은 utf8mb4 계열/H2 기본 모두 코드포인트 순 = 가나다 순. 대소문자 무시 비교는 collation 에 기대지 않고 정규화 컬럼으로 한다.
- 예약어: §1 표 참고. `@Column(name=…)` 으로 전부 명시해 Hibernate 네이밍 전략 차이에 기대지 않는다.

---

## 9. 마이그레이션 대장 (비추가형 스키마 변경 — 2026-09-08 신설)

`ddl-auto: update` 는 **추가만** 한다. 아래 표에 있는 변경은 이미 스키마가 만들어진 DB(로컬 `data/devdb`, 운영 MySQL)에
저절로 반영되지 않으므로 **손으로 한 번 실행**해야 한다. 규칙은 `03_기술결정.md` §17-5.

| 날짜 | 변경 | H2(로컬) | MySQL 8(운영) | 안 하면 |
|---|---|---|---|---|
| 2026-09-08 | `download_log.edition_id` **NOT NULL 해제**(§3-7) | `ALTER TABLE download_log ALTER COLUMN edition_id SET NULL;` | `ALTER TABLE download_log MODIFY edition_id BIGINT NULL;` | 판본 삭제가 500(`NULL not allowed for column "EDITION_ID"`) |
| 2026-09-08 | `seed_load.seed_type` 에 **enum 값 `COLLECTION_GUIDE` 추가**(§3-10·§6 백필) | `ALTER TABLE seed_load ALTER COLUMN seed_type ENUM('COMPOSER','WORK','COLLECTION_GUIDE') NOT NULL;` | `ALTER TABLE seed_load MODIFY seed_type ENUM('COMPOSER','WORK','COLLECTION_GUIDE') NOT NULL;` | **애플리케이션이 기동하지 못한다.** 기존 DB 의 컬럼은 `ENUM('COMPOSER','WORK')` 라 `SeedLoader` 의 조회가 `Value not permitted for column "('COMPOSER','WORK')": "COLLECTION_GUIDE" [22030-224]` 로 터진다(값을 쓰기 전에 **읽기부터** 깨진다) |
| 2026-09-08 | **enum 컬럼 16개를 네이티브 `ENUM(...)` → `VARCHAR` 로 고정**(§9-1, 근거 `03_기술결정.md` §17-6) | 아래 §9-1 문장 | 아래 §9-1 문장 | 다음에 enum 상수를 하나 추가할 때마다 위와 같은 기동 실패·조회 실패가 반복된다(테스트는 `create-drop` 이라 끝까지 초록) |
| 2026-09-10 | `work.section` **NOT NULL 컬럼 신설 + 기존 50행 백필**(§3-3·§9-2) | `ALTER TABLE work ADD COLUMN section VARCHAR(30) DEFAULT 'PIANO' NOT NULL;` | `ALTER TABLE work ADD COLUMN section VARCHAR(30) NOT NULL DEFAULT 'PIANO';` | 최악의 경우 **모든 곡 API 가 500** 이 된다 — `ddl-auto: update` 는 DDL 실패를 **삼키므로**(로그만), 컬럼 없이 기동한 뒤 첫 조회에서 `Column "SECTION" not found` 가 난다. 자세한 것은 §9-2 |

살아 있는 로컬 DB(`data/devdb`)에는 위 두 건 중 **`seed_load` 건이 2026-09-08 에 적용됐다**(백업 후 실행, 기동 확인). §9-1 은 backend-dev 적용 대기.
새 DB(테스트 `create-drop`, 초기화 후 로컬, 첫 배포 MySQL)는 엔티티대로 생성되므로 실행할 필요가 없다.
**추가형 변경(nullable 컬럼·새 테이블·인덱스)은 이 표에 적지 않는다** — `update` 가 알아서 한다.
이번 함께 들어가는 `work.collection_guide`(§3-3)가 그 예다.

`work.recommended_edition_reviewed`(§3-3, 2026-09-08)도 **표에 넣지 않는다**(senior-dev 판단). `NOT NULL` 컬럼이라
"기존 행은 어쩌나" 가 걸릴 수 있지만, Hibernate `update` 가 `DEFAULT FALSE` 를 붙여 만들고 **실데이터 복사본으로 기동을
확인했다**(backend-dev: 곡 50건 전부 `false`, 추천이 있는 42곡이 미검수로 잡힘). 이 표는 **손으로 실행해야 하는 것**만
담는 목록이라, 실행할 것이 없는 변경을 적으면 표를 볼 때마다 매번 "이건 했나?" 를 다시 판단하게 된다 —
표의 모든 줄이 할 일이어야 표가 쓸모 있다. (기록만 남기면 되는 문장은 이 문단이 대신한다.)

### 9-1. enum 컬럼 → VARCHAR 고정 (2026-09-08)

**무엇을 바꾸나.** 우리는 모든 상태값을 `@Enumerated(EnumType.STRING)` 으로 쓰는데, Hibernate 6 은 H2·MySQL 에서
그 컬럼의 **DDL 타입을 네이티브 `enum('A','B')` 로** 만든다(`H2Dialect.getEnumTypeDeclaration`, `EnumJavaType.getRecommendedJdbcType`
— `@Column(length=30)` 은 무시된다). 실제 생성 DDL 로 확인한 대상은 **16개 컬럼**이다:

| 테이블 | 컬럼 | 현재 DDL |
|---|---|---|
| work | level | `enum ('ADVANCED','BEGINNER','ELEMENTARY','INTERMEDIATE')` |
| work | hidden_reason | `enum ('NOT_PIANO_SOLO')` ← **값이 하나뿐이다** |
| work_alias | source | `enum ('ADMIN','IMSLP','SEED')` |
| edition | kind | `enum ('ARRANGEMENT','COMPLETE_SCORE','PARTS')` |
| edition | scope | `enum ('COMPLETE','MOVEMENT')` |
| edition | korea_copyright | `enum ('FREE','RESTRICTED','UNKNOWN')` |
| edition | imslp_license_code | `enum ('CC0','CC_BY','CC_BY_NC','CC_BY_NC_ND','CC_BY_NC_SA','CC_BY_SA','OTHER','PD')` |
| edition | file_fetch_status | `enum ('FAILED','FETCHING','QUEUED')` |
| crawl_job | status | `enum ('COMPLETED','FAILED','PAUSED','RUNNING','STOPPED')` |
| crawl_job | current_stage | `enum ('DOWNLOADING_FILE','MAKING_PREVIEW','READING_METADATA')` |
| crawl_item | item_mode | `enum ('ATTACH','CREATE','REFRESH','SKIP')` |
| crawl_item | status | `enum ('FAILED','HIDDEN','PENDING','PROCESSING','SKIPPED','SUCCESS')` |
| crawl_item | fail_reason | `enum ('FILE_DOWNLOAD_FAILED','IMSLP_UNAVAILABLE','INTERNAL_ERROR','INTERRUPTED','NO_PDF_EDITION','PAGE_NOT_FOUND')` |
| seed_load | seed_type | `enum ('COLLECTION_GUIDE','COMPOSER','WORK')` |
| files | ref_type | `enum ('COMMUNITY','EDITION','USER')` ← 새 도메인이 파일을 붙일 때마다 늘어난다 |
| files | file_usage | `enum ('ATTACHMENT','IMAGES','THUMBNAIL')` |

**엔티티 변경(backend-dev)** — 위 16개 필드에 `@JdbcTypeCode(SqlTypes.VARCHAR)`(`org.hibernate.annotations` / `org.hibernate.type`)를 붙인다.
`@Enumerated(EnumType.STRING)` 과 `@Column(length = 30)` 은 그대로 둔다(길이는 이제 실제로 DDL 에 쓰인다).
`files.ref_type` / `files.file_usage` 는 `length` 가 없으므로 `@Column(length = 30)` 을 함께 붙여 `varchar(255)` 가 되지 않게 한다.
전역 설정으로는 못 바꾼다 — `hibernate.type.prefer_native_enum_types` 는 `@Enumerated(STRING)` 경로를 타지 않는다(6.5 `EnumJavaType` 확인).

**기존 DB 마이그레이션** — 16개 컬럼 전부. `NOT NULL` 여부는 현재 정의를 그대로 유지한다.

```sql
-- H2 (로컬 data/devdb) : ALTER TABLE t ALTER COLUMN c VARCHAR(30) [NOT NULL];
ALTER TABLE work       ALTER COLUMN level              VARCHAR(30);
ALTER TABLE work       ALTER COLUMN hidden_reason      VARCHAR(30);
ALTER TABLE work_alias ALTER COLUMN source             VARCHAR(30) NOT NULL;
ALTER TABLE edition    ALTER COLUMN kind               VARCHAR(30) NOT NULL;
ALTER TABLE edition    ALTER COLUMN scope              VARCHAR(30) NOT NULL;
ALTER TABLE edition    ALTER COLUMN korea_copyright    VARCHAR(30) NOT NULL;
ALTER TABLE edition    ALTER COLUMN imslp_license_code VARCHAR(30);
ALTER TABLE edition    ALTER COLUMN file_fetch_status  VARCHAR(30);
ALTER TABLE crawl_job  ALTER COLUMN status             VARCHAR(30) NOT NULL;
ALTER TABLE crawl_job  ALTER COLUMN current_stage      VARCHAR(30);
ALTER TABLE crawl_item ALTER COLUMN item_mode          VARCHAR(30) NOT NULL;
ALTER TABLE crawl_item ALTER COLUMN status             VARCHAR(30) NOT NULL;
ALTER TABLE crawl_item ALTER COLUMN fail_reason        VARCHAR(30);
ALTER TABLE seed_load  ALTER COLUMN seed_type          VARCHAR(30) NOT NULL;
ALTER TABLE files      ALTER COLUMN ref_type           VARCHAR(30);
ALTER TABLE files      ALTER COLUMN file_usage         VARCHAR(30);

-- MySQL 8 (운영) : ALTER TABLE t MODIFY c VARCHAR(30) [NOT NULL];
ALTER TABLE work       MODIFY level              VARCHAR(30);
ALTER TABLE work       MODIFY hidden_reason      VARCHAR(30);
ALTER TABLE work_alias MODIFY source             VARCHAR(30) NOT NULL;
ALTER TABLE edition    MODIFY kind               VARCHAR(30) NOT NULL;
ALTER TABLE edition    MODIFY scope              VARCHAR(30) NOT NULL;
ALTER TABLE edition    MODIFY korea_copyright    VARCHAR(30) NOT NULL;
ALTER TABLE edition    MODIFY imslp_license_code VARCHAR(30);
ALTER TABLE edition    MODIFY file_fetch_status  VARCHAR(30);
ALTER TABLE crawl_job  MODIFY status             VARCHAR(30) NOT NULL;
ALTER TABLE crawl_job  MODIFY current_stage      VARCHAR(30);
ALTER TABLE crawl_item MODIFY item_mode          VARCHAR(30) NOT NULL;
ALTER TABLE crawl_item MODIFY status             VARCHAR(30) NOT NULL;
ALTER TABLE crawl_item MODIFY fail_reason        VARCHAR(30);
ALTER TABLE seed_load  MODIFY seed_type          VARCHAR(30) NOT NULL;
ALTER TABLE files      MODIFY ref_type           VARCHAR(30);
ALTER TABLE files      MODIFY file_usage         VARCHAR(30);
```

- **값은 그대로 보존된다** — 네이티브 ENUM 도 저장된 값은 문자열이고 Hibernate 는 이미 VARCHAR 로 바인딩한다(`EnumJdbcType.getJdbcTypeCode() == VARCHAR`).
  MySQL 은 이 ALTER 가 테이블 재작성이라 큰 테이블에서는 느리다 — **운영 DB 가 없는 지금이 가장 싼 시점**이다.
- 이 변경을 하고 나면 **앞으로 enum 상수 추가는 순수 추가형**이 되어 이 대장에 적을 일이 없다.
- 회귀 가드: `SchemaEnumColumnTypeIntegrationTest` — 생성된 스키마에 `DATA_TYPE = 'ENUM'` 인 컬럼이 하나도 없어야 한다.
  (`create-drop` 테스트가 이 사고를 못 잡는다는 §17-5 의 구멍을, "엔티티가 만드는 DDL 자체"를 보게 해서 메운다.)

### 9-2. `work.section` — NOT NULL 컬럼을 실데이터 50곡 위에 얹는 방법 (2026-09-10)

**문제 셋.**

1. `ddl-auto: update` 는 **NOT NULL 을 완화하지 못하고, 기존 행에 값을 채워 주지도 않는다.**
2. 그런데 `update` 는 **DDL 실패를 예외로 올리지 않는다** — 로그 한 줄만 남기고 기동을 계속한다. 그래서 컬럼이 없는 채로 서버가 뜨고, **첫 곡 조회부터 전부 500** 이 된다. `seed_load` 사고(위 표 2번째 줄)보다 나쁘다: 그때는 기동이 멈춰서 바로 알았다.
3. 로컬 `data/devdb` 에 **실데이터 곡 50건**이 이미 있다.

**대책 — 엔티티에 `@ColumnDefault("'PIANO'")` 를 붙이고, 그래도 대장에 등재한다.**

- `@ColumnDefault` 를 붙이면 Hibernate 가 만드는 DDL 이 `... varchar(30) default 'PIANO' not null` 이 되어 **기존 행이 있어도 ALTER 가 성공할 수 있다**(H2·MySQL 모두 DEFAULT 가 있으면 기존 행을 그 값으로 채운다). 새로 만드는 DB(테스트 `create-drop`·초기화 후 로컬·첫 배포 MySQL)와 마이그레이션한 DB의 **스키마가 한 글자도 안 달라진다**(`recommended_edition_reviewed` 의 `@ColumnDefault("false")` 와 같은 패턴).
- **그래도 대장에 넣는 이유**: 위 2번(조용한 실패) 때문이다. "될 것"에 서버 50곡을 걸지 않는다. 손으로 먼저 실행해 두면 Hibernate 가 할 일이 없어져 **어느 쪽이든 결과가 같아진다.**

**backend-dev 실행 절차 (이 순서를 지킨다 — 새 코드 기동이 마지막이다).**

```sql
-- 0) data/ 백업 (기존 data_backup_* 방식 그대로)
-- 1) 컬럼 추가 (H2, 서버를 내린 상태에서)
ALTER TABLE work ADD COLUMN section VARCHAR(30) DEFAULT 'PIANO' NOT NULL;
-- 2) 백필 확인 — DEFAULT 로 이미 채워졌어야 한다. 혹시 비었으면 채운다
UPDATE work SET section = 'PIANO' WHERE section IS NULL OR section = '';
-- 3) 확인: 50 (전부 PIANO 한 줄이어야 한다)
SELECT section, COUNT(*) FROM work GROUP BY section;
-- 4) 새 코드 기동 → GET /api/works/search?q=월광 이 200 인지 확인
```

- **인덱스를 만들지 않는다.** 1차에는 모든 행이 `PIANO` 라 카디널리티가 1이고, 옵티마이저가 쓰지 않는다. `work` 는 수백 행이고 검색은 이미 `LIKE '%…%'` 풀스캔이다(§3-3 주석). 구분이 실제로 둘 이상 열려 행이 갈릴 때 `idx_work_section` 을 그때 추가한다 — 그건 **추가형**이라 이 대장에 적을 일도 없다.
- **`works.csv` 에 `section` 열을 만들지 않는다.** 시드 곡 50개는 전부 피아노이고, 로더는 **삽입 전용**이라 열을 더해도 이미 적재된 행에는 닿지 않는다 — `collection_guide` 처럼 **백필 패스를 하나 더** 만들어야 하는데, §6 이 "시드가 관리자 입력을 언제 덮는지 아무도 설명 못 하게 되므로 구멍을 여러 개 뚫지 않는다" 고 못 박았다. 여기서는 그럴 필요가 아예 없다: 엔티티 기본값이 `PIANO` 라 **로더가 만드는 새 곡도 `PIANO`** 이고, 이미 적재된 곡은 위 SQL 한 줄이 끝낸다. 시드로 다른 구분의 곡을 넣게 되는 날 CSV 에 열을 더한다.
- **1차에 `section` 값을 바꾸는 경로는 없다.** 관리 API(§4-8)에도 넣지 않고(기획 04 §3-6·§9 8-7 — 구분 배정은 구분을 여는 시점의 과제), 수집도 `NOT_PIANO_SOLO` 를 구분으로 바꾸지 않는다(기획 04 §2-2). 그래서 **테스트가 다른 구분의 곡을 만들 때만 리포지토리로 직접 저장한다**(02 §0-7 의 "1차에는 걸러낼 대상이 없다" 문제).
