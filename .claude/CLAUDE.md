# 쉬운악보 (가칭) — IMSLP 한국어 악보 검색·다운로드 서비스

> 보일러플레이트(`spring_csr_boiler_react`)에서 출발한 프로젝트. 보일러플레이트 점검 기록: `docs/점검/보일러플레이트_점검_2026-09-05.md`
Flutter 앱 + Spring Boot 백엔드 + React 웹으로 이루어진 풀스택 프로젝트입니다.
**실제 회사 조직처럼** 역할을 나눈 에이전트 팀이 기획 → 설계 → 구현 → 검증 → 배포 파이프라인으로 일합니다.

## 제품 개요

- **무엇을 만드나**: IMSLP(퍼블릭 도메인 악보 아카이브)의 악보를 **한국어 제목·별칭으로 검색해 추천 판본 1개를 바로 PDF로 받는** 서비스. 1차 범위는 **피아노 독주곡**. 상세: `docs/기획/00_제품비전_IMSLP문제정의_핵심컨셉.md`
- **사용자는 누구인가**: 피아노를 배우는 한국인(취미·입시·레슨생). IMSLP 의 원어 제목·수십 개 판본·대기 페이지가 불편한 사람.

## 스택 / 저장소 구조

| 영역 | 스택 | 경로 | 테스트 명령 |
|---|---|---|---|
| 백엔드 | Spring Boot (Gradle) | 저장소 루트 (`src/`, `build.gradle`) | `./gradlew test` |
| 웹 | **React (JavaScript / JSX)** — TypeScript 아님 | `frontend/` (빌드 시 `src/main/resources/static/`로 복사돼 Spring이 SPA 서빙) | `npm run build` (⚠️ 테스트 프레임워크 미도입 — `npm test` 없음) |
| 앱 | Flutter (Dart) — **아직 미도입** (저장소에 앱 코드 없음) | (해당 없음) | `flutter analyze` + `flutter test` |
| 배포 | Railway | 절차: `.claude/skills/railway-deploy/SKILL.md` | — |

> ⚠️ **렌더링 방식: React CSR(SPA) 전용.** 화면은 `frontend/`에서 **React로만** 만든다.
> **서버사이드 렌더링(Thymeleaf/JSP)은 쓰지 않는다** — 백엔드는 JSON REST API만 제공하고, 모든 화면 라우팅은 SPA가 `forward:/index.html`로 받는다.
> 이 저장소는 과거 Thymeleaf → React로 **전환 완료**된 것이라, `설계/` 문서의 Thymeleaf 언급은 **과거 이력**이고 `templates/`에 옛 `.html`이 보이면 **잔재(삭제 대상)** 다. 새 화면 요구가 와도 Thymeleaf/JSP 뷰를 만들지 말 것.
> **SEO가 중요한 요구가 와도 Thymeleaf/JSP로 돌아가지 않는다** — React 기반 SSR/SSG(예: **Next.js**)를 먼저 고려한다. 기본값은 항상 **CSR React**다.

## 팀 구조 (회사 조직)

| 에이전트 | 역할 | 수정 권한 |
|---|---|---|
| `product-planner` | 기획자 — 사용자 관점 동작 흐름·인수 조건 정의, 기획 문서 관리 | 문서만 |
| `designer` | 디자이너 — 화면 정의서(상태별 UI 포함)·디자인 토큰 관리 | 문서·토큰만 |
| `senior-dev` | 선임개발자(테크 리드) — ERD·API 명세서 작성, TDD 테스트 선작성, 개발 중 코드리뷰, 기술 결정 | O |
| `backend-dev` | 백엔드개발자 — Spring Boot 구현 | O (백엔드만) |
| `frontend-dev` | 프론트개발자 — React 웹 구현 | O (웹만) |
| `app-dev` | 앱개발자 — Flutter 앱 구현 | O (앱만) |
| `qa` | QA — 기획서로 테스트 케이스 설계, 실제 제품을 시나리오로 시험(경계·비정상·탐색·회귀) | X |
| `devops` | CI/CD — git·GitHub Actions·Railway 배포 | O (설정·워크플로만) |
| `builder` *(공통)* | 스택에 안 걸리는 잡일(스크립트·설정·문서). 세 개발자 영역 밖의 폴백 | O |

> 공통 planner/reviewer/researcher는 뺐다 — 기획은 product-planner·senior-dev가, 코드 검토는
> senior-dev·qa가 이미 맡아서 겹치기 때문. 필요해지면 최상위 `.claude/agents/`에서 파일만 가져오면 된다.

## 일하는 흐름 (파이프라인)

```
product-planner(사용자 흐름·인수 조건) → designer(화면 정의서)
→ senior-dev(기획서에서 API 명세서 도출 + ERD + 명세 기반 테스트 선작성 = Red)
→ backend-dev ∥ frontend-dev ∥ app-dev   (테스트를 통과시키며 병렬 구현 = Green)
→ senior-dev(개발 중 코드리뷰 — 컨벤션·클린코드·스택 간 정합성, 수정은 개발자가)
→ qa(테스트 케이스 설계·시나리오 시험) — 결함은 해당 개발자로 되돌림
→ devops(커밋·배포)
```

## 작업 방식 (TDD — 이 프로젝트의 핵심 규칙)

**항상 TDD다. 프로덕션 코드보다 테스트 코드가 먼저다. 예외 없음.**

1. **테스트가 명세다** — senior-dev가 API 계약·화면 요구를 테스트 코드로 먼저 작성한다(실패 상태 = Red). 테스트 없이 개발자에게 일을 넘기지 않는다.
2. **개발자는 테스트를 통과시킨다(Green)** — senior-dev의 테스트 수정 금지. 테스트가 틀렸다고 판단되면 senior-dev에게 되돌린다.
3. **테스트에 없는 코드가 필요해지면** — 계약·공개 동작은 senior-dev에게 테스트 추가를 요청하고, 내부 구현 세부는 개발자가 직접 테스트를 먼저 작성(실패 확인)한 뒤 구현한다. 어떤 경우에도 순서는 테스트 → 코드다.
4. **계약 밖 변경 금지** — API 응답 필드를 임의로 추가/변경하면 다른 스택이 깨진다. 계약 변경은 senior-dev를 통해서만.
5. **qa 통과가 완료 조건** — qa는 개발자 테스트를 재실행하는 게 아니라, 기획서로 테스트 케이스를 설계해 실제 제품을 시험하는 독립 관문이다. 테스트 없이 들어온 프로덕션 코드는 qa가 결함으로 리포트한다.

## 문서 위치

- 기획서: `docs/기획/` (product-planner 담당). `00_*` 은 제품 비전·문제 정의·결정·리스크(메인 세션 작성)
- 화면 정의서: `docs/화면정의/` (designer 담당)
- 디자인 토큰: 웹 `frontend/src/styles/tokens.css` (앱 미도입)
- API 명세서·ERD: `docs/설계/` (senior-dev 담당, 세 스택과 qa가 공유하는 단일 기준). 점검 기록: `docs/점검/`. 루트의 `설계/` 폴더는 **보일러플레이트 시절 문서(과거 이력)** 라 새 문서는 쓰지 않는다.

## 이 프로젝트의 규칙

- **커밋·푸시·배포는 사용자가 명시적으로 요청할 때만** — 실행은 devops 담당.
- **배포 전 조건**: 세 스택 테스트 통과 + qa 판정 통과. 절차는 railway-deploy 스킬.
- **새 라이브러리 도입은 senior-dev 결정 사항** — 개발자가 임의로 추가하지 않는다.
- **코드 컨벤션**: `code-convention` 스킬이 기준이다 (DTO class 규칙, 계층 구조, 백엔드 테스트는 컨트롤러 통합테스트만 등). 문서와 코드가 어긋나면 문서에 맞춰 코드를 고친다.
- **화면은 React CSR로만** — 서버사이드 렌더링(Thymeleaf/JSP)을 도입하거나 `templates/`에 뷰를 만들지 않는다. 백엔드는 JSON REST, 화면은 `frontend/` React. (설계 문서의 Thymeleaf 언급은 과거 전환 이력일 뿐이다.)
- **금지**: 요청받지 않은 대규모 리팩터링, 자기 영역 밖 스택의 코드 수정.
