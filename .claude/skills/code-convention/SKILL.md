---
name: code-convention
description: 풀스택 프로젝트의 코드 컨벤션 — 코드를 작성·리뷰·리팩터링하기 전에 반드시 따르는 기준. Spring 백엔드(DTO class 규칙, 계층 구조, 예외 처리, 트랜잭션, PK 전략, 환경 무관 실행, Railway 버킷, 컨트롤러 통합테스트만) + React 규칙 포함. senior-dev의 코드리뷰·테스트 작성 기준이자 개발자들의 구현 기준. 이 문서와 코드가 어긋나면 문서가 기준이다.
---

> 대부분 백엔드(Spring) 규칙이다. React는 §7, **Flutter 앱 컨벤션은 아직 없음** — 정해지는 대로 §8로 추가한다.

# 코드 컨벤션 (total_module 공통)

> 작성일: 2026-07-08
> 모든 모듈(businesscard_qr, doll_gacha, 향후 프로젝트)이 **일정하게** 따를 규칙.
> 코드 리뷰/리팩터 시 이 문서를 기준으로 한다. 새 프로젝트 편입 시에도 이 컨벤션에 맞춘다.

---

## 0. 최우선 규칙

### DTO는 `record`가 아니라 `class`로 작성한다 ⭐
- **결정(2026-07-08, 사용자 지시)**: DTO는 `record`를 쓰지 않는다. Lombok 어노테이션을 붙인 일반 `class`로 작성한다.
- 이유: 팀 선호 + QueryDSL `Projections.bean(...)`(setter 기반 프로젝션)과 호환. record로 바꾸면 조회 프로젝션이 깨진다.
- **리뷰 시**: "record로 바꾸면 어떠냐"는 제안을 하지 않는다. 기존 record가 있으면 class로 통일하는 방향이 맞다.

```java
// ✅ 이렇게
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityDTO {
    private Long id;
    private String title;
    // 정적 팩토리로 엔티티→DTO 변환
    public static CommunityDTO from(CommunityEntity e) { ... }
}

// ❌ 이렇게 하지 않는다
public record CommunityDTO(Long id, String title) {}
```

- 응답 전용 DTO에서 `@Setter`는 꼭 필요할 때만(예: QueryDSL bean 프로젝션 대상). 불필요하면 생략.
- 엔티티 → DTO 변환은 **DTO의 정적 팩토리 메서드**(`from(...)`)로 모은다.

---

## 1. 계층 구조

- **Controller**: 얇게. 요청 받기 → 서비스 호출 → `ApiResponse`로 감싸 반환만. 비즈니스 로직 금지.
- **Service**: 비즈니스 로직. 클래스에 `@Transactional(readOnly = true)` 기본, **쓰기 메서드에만** `@Transactional` 추가.
- **트랜잭션 안엔 DB 작업만 — 외부 API 호출은 `@Transactional` 밖에서 한다** ⭐
  - 외부 HTTP 호출(소셜 로그인 검증, 결제, 알림, 외부 조회 등)을 트랜잭션 안에 넣지 않는다.
  - 이유: ① 느리고 통제 못 하는 네트워크 대기 동안 **DB 커넥션·락을 붙잡아** 동시 요청 시 커넥션 풀 고갈 위험. ② **HTTP는 롤백이 안 되므로** 트랜잭션에 넣어도 원자성 이득이 없다(비용만 있음).
  - 패턴: **외부 호출로 결과를 먼저 받고 → 그 결과로 짧은 트랜잭션을 열어 DB 저장/갱신만.** (트랜잭션은 짧게, DB 조작만 담는다.)
- **Entity**: 도메인 로직은 엔티티 안에 둔다 (`entity.update(...)`, `entity.softDelete()`, `entity.isWrittenBy(user)`). 서비스에서 필드를 직접 setter로 만지지 않는다.
- **Repository**: 조회는 가능하면 **DTO로 직접 프로젝션**(QueryDSL)해서 N+1을 구조적으로 피한다. 엔티티를 컨트롤러까지 노출하지 않는다.

---

## 2. 예외 처리

- 예외는 `GlobalExceptionHandler`에서 일원 처리한다. 컨트롤러/서비스에서 `try/catch (Exception)`로 삼켜 상태코드를 직접 만들지 않는다.
- "없음"은 `orElseThrow(() -> EntityNotFoundException.of("리소스명", id))` — `null` 반환 금지.
- 권한 위반은 `AccessDeniedException.forUpdate("리소스")` / `forDelete("리소스")` 같은 의미 있는 팩토리 사용.
- 프레임워크 표준 4xx(400/404/405/415/413)를 500으로 뭉개지 않는다 (핸들러가 상태코드 보존).

---

## 3. 의존성 주입 / Lombok

- 생성자 주입 + `@RequiredArgsConstructor` (필드 주입 `@Autowired` 금지).
- 로깅은 `@Slf4j` + `log.info/warn/error`. `System.out`·`printStackTrace` 금지.
- 의존성은 `private final`.

---

## 4. 네이밍 / 정리

- 메인 클래스명은 프로젝트를 드러내는 이름 (`DollGachaApplication`, `BusinessCardApplication`). `DemoApplication` 금지.
- QueryDSL 생성물(`src/main/generated/`), 빌드 산출물은 **git에 커밋하지 않는다**(.gitignore). 빌드 시 자동 생성.
- 죽은 코드(주석처리된 블록, 미사용 import/메서드, 실행되지 않는 워크플로/리소스)는 남기지 않는다.
- 주석/문서 블록 중복 금지.

---

## 4-1. ID · PK 전략 (보안) ⭐

> 핵심: **위험한 건 ID 타입이 아니라 "인가(authorization) 부재"다.** 순번 Long은 열거(enumeration)가 쉬울 뿐, 진짜 방어는 접근제어. UUID는 열거를 줄이는 보조수단이지 인가 대체가 아니다.

- **기본 PK = `Long` + `@GeneratedValue(strategy = IDENTITY)`.** 내부 조인·삽입 빠르고 인덱스 효율 좋음. (랜덤 UUID를 InnoDB 클러스터드 PK로 쓰면 인덱스 단편화로 느려짐 — 굳이 UUID PK가 필요하면 순차형 **UUID v7**.)
- **인증 사용자 식별은 클라이언트가 준 id가 아니라 토큰(SecurityContext/JWT 주체)에서 꺼낸다.** "내 정보"를 클라이언트가 준 userId로 조회/수정하지 않는다 → user PK가 순번이어도 외부 열거 불가.
- **by-id 접근에는 인가 체크 필수.** path로 받은 id로 수정/삭제할 때 소유자 검증(`isWrittenBy` → `AccessDeniedException.forUpdate/forDelete`). 공개 조회는 그대로 두되, 교차 사용자 접근 가능 지점은 반드시 확인.
- **열거·개수 유출이 곤란한 리소스만 UUID/랜덤 토큰**을 쓴다: 공유 링크, 초대, 비밀번호 재설정, 파일 다운로드 토큰 등. (예: businesscard의 다운로드 토큰)
- 정석 패턴: **내부 PK는 Long, 외부 노출이 필요한 리소스만 별도 `public_id`(UUID/slug, unique index)를 두고 URL엔 그걸 노출.**
- 요약: `Long + 제대로 된 인가 = 안전`, `UUID + 인가 없음 = 여전히 취약`. **인가 먼저, UUID는 그 위 한 겹.**

---

## 5. 환경 무관 실행 원칙 ⭐ (로컬/운영, 코드 변경 없이 둘 다 동작)

> **핵심**: total_module은 "로컬에서 다 잘 되는 것을 그대로 배포"하는 저장소다.
> 따라서 **같은 코드가 코드 변경 없이 로컬에서도, 운영에서도 알아서 잘 돌아야 한다.**
> 환경 차이는 **프로파일 + 환경변수로만** 흡수하고, `if (로컬) ... else ...` 같은 코드 분기로 처리하지 않는다.

### 5-1. 프로파일로만 환경을 가른다
- 로컬 = 기본 프로파일(`local`), 운영 = `prod`(`SPRING_PROFILES_ACTIVE=prod`).
- 모든 운영 설정은 환경변수로: `${ENV_NAME:로컬기본값}`. 비밀값은 소스/커밋에 두지 않는다.
- 절대 URL은 요청 헤더 유추가 아니라 공개 base URL 환경변수(`APP_BASE_URL`/`APP_PUBLIC_BASE_URL`) 기준으로 생성. `forward-headers-strategy: framework`.

### 5-2. DB 정책 — **로컬은 휘발, 운영은 영속** ⭐⭐
- **기본값 원칙: 로컬 H2는 특별한 지시가 없으면 "재시작(매 기동 초기화)"이다.** 데이터를 남기려면 그 모듈에 명시적 사유를 적는다(예외).
- **로컬**: **인메모리 H2 + `ddl-auto: create`**. 서버 켤 때마다 **스키마를 새로 만들고 시드(`data-*.sql`)를 다시 로드**한다.
  → **로컬 데이터는 남지 않는다.** 매 기동이 깨끗한 상태 = 재현성 확보, **깨진 데이터/스키마 잔재로 인한 문제를 원천 차단**
    (예: 시드 인코딩을 고쳐도 옛 파일 DB가 남아 안 고쳐지던 문제 — 파일 DB를 손으로 지울 필요가 없어짐).
  → 로컬 DB 설치·관리 불필요.
- **운영**: **관리형 MySQL(Railway) + `ddl-auto: update`(또는 validate)**. **데이터는 반드시 영속**해야 한다(재배포·재시작에도 보존).
  운영에서 인메모리/H2로 부팅되면 **fail-fast**로 죽인다(데이터 소실 방지 — `RailwayDeploymentValidator`).
- ⚠️ 헷갈리지 말 것: "DB가 남아야 한다"는 **운영** 이야기다. **로컬은 반대로 매번 비우는 게 원칙**이다.
- 시드 `.sql`은 UTF-8로 저장하고 `spring.sql.init.encoding: UTF-8`을 명시한다(JDK17+한글 Windows의 CP949 오독 방지).
- **시드 데이터 2종 구분**:
  - **실참조 데이터**(예: API로 받아온 가게 목록): 로컬·운영 **공통 사용**. 로컬은 자동 로드, 운영은 **수동/마이그레이션으로 1회 적재 + 주기적 갱신**(운영 `mode: never` 유지). 데이터 성격·갱신주기를 문서화.
  - **데모/개발용 데이터**(가짜 사용자·게시글, 로컬 경로가 박힌 파일 등): **로컬 전용.** 운영에는 넣지 않는다(실사용자가 채움).

### 5-3. 파일 저장 — **Railway Storage Buckets (S3 호환 object storage) 표준** ⭐
> 2026-07-10 확정. object storage가 파일 서빙 정석. (Supabase→DB BLOB→Volume 시행착오 끝에 Railway Buckets로 확정.)
> **왜 이것들이 아닌가**: DB BLOB=파일을 바이트로 DB에 넣는 건 정석 아님(DB 비대·백업 무거움). Volume=블록스토리지(SSD)로 DB/앱 상태용이지 파일 서빙용 아님. Supabase=외부 의존, 안 씀.

- 업로드 파일(이미지·첨부)은 **Railway Storage Buckets**(private, S3 완전 호환)에 저장한다. **모든 모듈 공통.**
  - 요금: $0.015/GB-월, S3 API 호출·egress 무료. 환경별 버킷 격리.
- **버킷은 프로젝트(모듈)별로 하나씩 만든다** (규칙: `<모듈명>_bucket` — 예: `doll_gacha_bucket`, `businesscard_qr_bucket`).
  - **요금 근거**: Railway Bucket 요금은 **"버킷 개수"가 아니라 "총 저장 용량(GB)"** 기준이고 버킷당 기본요금이 없다.
    → 버킷을 여러 개 만들어도 요금이 안 오른다("1버킷 5GB" = "5버킷 각 1GB"로 동일). **요금 때문에 공통 버킷으로 합칠 이유가 없다.**
  - 기술적으로는 파일명이 UUID라 공통 버킷 1개도 동작하지만, **격리(파일 분리)·독립 자격증명·관리 용이성** 때문에 프로젝트별 분리가 정석.
  - 버킷 서비스명 규칙: `<프로젝트명>_bucket` (참조 `${{<프로젝트명>_bucket.BUCKET_ENDPOINT}}` 등).
- **연동**: 표준 **AWS SDK for Java v2 (S3)**. 주입 env — `BUCKET_ENDPOINT`, `BUCKET_ACCESS_KEY_ID`, `BUCKET_SECRET_ACCESS_KEY`, `BUCKET_NAME` (region 기본 `us-east-1`, **path-style**).
  - 앱 설정 예: `app.bucket.endpoint: ${BUCKET_ENDPOINT:}` 등. S3 SDK라 나중에 provider(R2/S3/MinIO) 교체도 코드 그대로.
- **서빙**: 버킷은 **private → 공개 URL 없음.** **백엔드 프록시** `GET /uploads/{key}` → 버킷 `getObject` 스트리밍.
  - 기존 프론트 계약(`/uploads/저장파일명`)을 그대로 유지 → 프론트 수정 0, 같은 오리진이라 presigned 만료 걱정 없음.
- **저장 전략 선택**: `app.bucket.endpoint`(=BUCKET_ENDPOINT)가 **있으면 BucketStorage(S3)**, **없으면 로컬 디스크 폴백**(`${FILE_UPLOAD_DIR:./uploads}`).
  → **운영 = 버킷, 로컬 개발 = 디스크**(마찰 0). 코드는 하나(전략 패턴), 환경 차이는 설정으로만.
- **배포 검증**: Railway(prod)에서 `BUCKET_*` 미설정이면 `RailwayDeploymentValidator`가 fail-fast.
- 확장: 지금 규모엔 Railway Buckets로 충분. 훗날 필요하면 같은 S3 API로 Cloudflare R2/S3로 이전.

### 5-3-1. 첨부·본문 파일 수명주기 (연결·삭제·정리) ⭐ — 리치에디터/첨부 쓰는 모듈 공통
> 파일은 버킷에 쌓이므로 "글 ↔ 파일 연결"과 "정리"를 안 하면 **orphan(버려진 파일)이 누적**되고 글-파일 관계가 끊긴다. 아래 3가지를 지킨다. (2026-07-14 확정 — doll_gacha 커뮤니티에서 확인된 패턴)

- **① 연결(refId reconcile)**: 에디터 **본문 이미지**는 글 저장 *전에* 업로드되므로 임시 refId(예: `0`)로 저장된다. **글 저장(create/update) 시 본문 HTML의 `/uploads/{저장파일명}`을 파싱해, 실제로 본문에 쓰인 파일들만 `refId=글ID`(usage=IMAGES)로 UPDATE**한다.
  - 본문에서 지운 이미지는 연결 안 됨 → refId=0으로 남아 ③이 정리. (첨부파일은 글 등록 후 올려서 처음부터 실제 refId)
- **② 삭제 연동**: 글 삭제 시 `refId=글ID`인 파일(본문 IMAGES + 첨부 ATTACHMENT)을 **버킷 바이트 + files 메타행 모두 삭제**한다. (글이 soft delete여도 파일은 hard delete — 되살릴 일 없으면 정석)
- **③ orphan 배치**: `refId=0` 이면서 **생성 후 유예시간(예: 24h)이 지난** 파일만 주기적(`@Scheduled`)으로 삭제.
  - ⚠️ **"refId=0 전부 삭제" 금지** — 지금 글 쓰는 중인 초안 이미지도 refId=0이라 지워버린다. 반드시 "오래된 것"만.
- **원칙**: files 메타(refId/refType/usage)와 실제 버킷 바이트는 **항상 함께** 생성/삭제해 정합성 유지. (한쪽만 지우면 깨진 링크/orphan 발생)

### 5-4. 배포 기본기 (상세: 아키텍처_및_편입기준.md / railway_배포_및_비용.md)
- `GET /healthz` 필수(200 + `{status:UP}`), 헬스체크 회귀 방지 스모크 테스트 1건 유지.
- `server.port: ${PORT:...}`. 모듈=서비스, 게이트웨이 없음(자기 공개 URL 직행).

---

## 6. 테스트 — **컨트롤러 통합테스트만** ⭐

> **방침**: 이 프로젝트들은 REST API다. 테스트는 **컨트롤러 레벨 통합테스트로 "요청 → 응답 데이터가 잘 오는지"만** 검증한다.
> **service / repository(DAO) 단위테스트는 하지 않는다** — 계층별로 쪼개 테스트하는 건 비용이 크고,
> 어차피 통합테스트가 컨트롤러부터 DB까지 한 번에 관통하며 응답으로 결과를 확인하기 때문이다.

- 기본 형태: `@SpringBootTest` + `@AutoConfigureMockMvc` + MockMvc로 엔드포인트 호출 → **상태코드 + 응답 바디(JSON)** 검증.
  (인증이 필요 없는 스모크는 `addFilters=false`, 인증 흐름 검증은 `@WithMockCustomUser` 등 사용)
- 커버 대상: **정상 응답 + 주요 에러 경로**(권한 거부 403 / 검증 실패 400 / 없음 404 / 인증 필요 401). service·DAO를 따로 목킹해 단위검증하지 않는다.
- 응답 검증은 상태코드만 보지 말고 **핵심 필드(JSON)까지** 확인한다("200만 오고 데이터는 틀림"을 잡기 위해).
- 시간 의존 로직은 시간을 파라미터로 주입해 결정적으로 테스트(`createReview(user, dto, now)`).
- 순서/랜덤/시간 의존 테스트 금지. 로컬 테스트 DB는 H2(인메모리) — §5-2.
- 예외: 순수 유틸/알고리즘 등 컨트롤러를 거치지 않는 로직은 필요하면 작은 단위테스트 허용(드묾).

---

## 7. 프론트엔드 (React, 해당 모듈에 한함)

- API 호출은 공용 클라이언트(`lib/http.js`)로 일원화, 상대경로 + 쿠키 인증(`credentials: 'include'`).
- 토큰을 localStorage에 저장하지 않는다(쿠키 사용).
- `console.log` 잔재 남기지 않는다.
- 반복되는 fetch/loading/error 패턴은 커스텀 훅으로, 중복 컴포넌트는 통합.

---

## 8. 앱 (Flutter) — *아직 없음, 채우기*

> Flutter 컨벤션이 정해지는 대로 여기에 추가한다 (상태관리 패턴, 폴더 구조, 모델 변환 규칙 등).

---

> 이 문서와 코드가 어긋나면 **이 문서에 맞춰 코드를 고친다**(문서가 기준). 컨벤션을 바꾸려면 이 문서를 먼저 고친다.
