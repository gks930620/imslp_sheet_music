# OAuth2 소셜 로그인(구글·카카오) 설정 방법 — 웹 로그인

> 작성일: 2026-07-22
> 이 문서는 **웹 브라우저 소셜 로그인**(React SPA + Spring Security) 설정만 다룬다.
> (앱/네이티브 SDK 로그인 `POST /api/oauth2/providers/{provider}/tokens`는 범위 밖 — 앱 도입 시 별도 문서.)

---

## 0. 이 프로젝트의 로그인 흐름 (먼저 이해)

```
[로그인 화면] 카카오/구글 버튼(a 태그)
   → GET /custom-oauth2/login/web/{provider}      (Oauth2LoginController: 인증 URL 조립 후 제공자로 리다이렉트)
   → 제공자(카카오/구글) 로그인·동의
   → GET {APP_BASE_URL}/login/oauth2/code/{provider}   ★ 콜백(Redirect URI) — 콘솔에 등록할 값
   → 성공 시 OAuth2LoginSuccessHandler 가 access_token·refresh_token 을 HttpOnly 쿠키로 심고
   → "/" 로 리다이렉트 (React가 쿠키로 로그인 상태 인식)
```

- **콘솔에 등록할 Redirect URI = `{base}/login/oauth2/code/{kakao|google}`** (이 경로가 스프링 시큐리티 표준 콜백). 이게 안 맞으면 로그인 전체가 실패한다.
- `{base}` = 로컬은 `http://localhost:8080`, 운영은 `APP_BASE_URL`(예: `https://<앱>.up.railway.app`).

---

## 1. 등록할 Redirect URI 요약 (가장 중요) ⭐

| 제공자 | 로컬(dev) | 운영(prod) |
|---|---|---|
| 카카오 | `http://localhost:8080/login/oauth2/code/kakao` | `https://<운영도메인>/login/oauth2/code/kakao` |
| 구글 | `http://localhost:8080/login/oauth2/code/google` | `https://<운영도메인>/login/oauth2/code/google` |

> ⚠️ **스킴·호스트·포트·경로가 완전히 일치**해야 한다. 로컬은 포트 `8080` 포함. 로컬·운영 URI를 **둘 다** 콘솔에 등록해 두면 편하다.

---

## 2. 카카오 설정

### 2-1. 카카오 개발자 콘솔 (https://developers.kakao.com)
1. **내 애플리케이션 → 애플리케이션 추가하기** 로 앱 생성.
2. **앱 키 → REST API 키** 값을 복사 → 이게 `KAKAO_CLIENT_ID` 다. (JavaScript 키 아님, **REST API 키**)
3. **카카오 로그인 → 활성화 설정 ON**.
4. **카카오 로그인 → Redirect URI** 에 위 1번 표의 카카오 URI(로컬·운영) 등록.
5. **앱 설정 → 플랫폼 → Web** 에 사이트 도메인 등록 (`http://localhost:8080`, 운영 도메인).
6. **카카오 로그인 → 동의항목**:
   - **닉네임**(`profile_nickname`) — 필수 동의로 설정.
   - **카카오계정(이메일)**(`account_email`) — 동의항목에서 사용 설정. ⚠️ 이메일은 앱 상태(개인/비즈)에 따라 **검수·비즈앱 전환**이 필요할 수 있다. 이메일을 못 받으면 이 설정을 확인.
7. **(선택) 보안 → Client Secret**: 발급 후 "사용함"으로 설정한 경우에만 `KAKAO_CLIENT_SECRET` 에 값을 넣는다. 미사용이면 **빈 값 허용**(로컬 기본).
   - 이 프로젝트는 카카오를 `client_secret_post` 방식으로 보낸다(`application.yml`에 이미 설정됨).

### 2-2. 이 프로젝트가 카카오에 요청하는 것 (참고, 코드에 이미 설정됨)
- scope: `profile_nickname`, `account_email`
- 사용자 식별자(user-name-attribute): `id`

---

## 3. 구글 설정

### 3-1. Google Cloud Console (https://console.cloud.google.com)
1. **프로젝트 생성**(또는 기존 선택).
2. **API 및 서비스 → OAuth 동의 화면** 구성:
   - User Type: **외부(External)**.
   - 앱 이름·지원 이메일 입력, 범위에 `.../auth/userinfo.email`, `.../auth/userinfo.profile` 추가.
   - 게시 상태가 "테스트"면 **테스트 사용자**에 로그인할 구글 계정을 추가(안 하면 접근 차단됨).
3. **API 및 서비스 → 사용자 인증 정보 → 사용자 인증 정보 만들기 → OAuth 클라이언트 ID**:
   - 애플리케이션 유형: **웹 애플리케이션**.
   - **승인된 리디렉션 URI**: 위 1번 표의 구글 URI(로컬·운영) 등록.
   - (선택) **승인된 자바스크립트 원본**: `http://localhost:8080`, 운영 도메인.
4. 생성 후 **클라이언트 ID** = `GOOGLE_CLIENT_ID`, **클라이언트 보안 비밀** = `GOOGLE_CLIENT_SECRET`.
   - ⚠️ 구글은 인가코드→토큰 교환에 **secret이 필수**다(카카오와 달리 생략 불가).

### 3-2. 이 프로젝트가 구글에 요청하는 것 (참고)
- scope: `profile`, `email`
- 사용자 식별자(user-name-attribute): `sub`

---

## 4. 환경변수 채우기

### 로컬(dev) — 프로젝트 루트 `.env` (커밋 안 됨)
```
KAKAO_CLIENT_ID=<카카오 REST API 키>
KAKAO_CLIENT_SECRET=            # 카카오 Client Secret "사용함"일 때만, 아니면 빈 값
GOOGLE_CLIENT_ID=<구글 클라이언트 ID>
GOOGLE_CLIENT_SECRET=<구글 클라이언트 보안 비밀>
JWT_SECRET_KEY=<아무 랜덤 64hex 이상>
```
> `.env.example` 을 복사해서 채우면 된다. 로컬은 `APP_BASE_URL` 없이도 `localhost:8080` 기본값으로 동작한다.

### 운영(prod) — Railway 대시보드 서비스 Variables
- 위 4개(`KAKAO_CLIENT_ID/SECRET`, `GOOGLE_CLIENT_ID/SECRET`) + **`APP_BASE_URL`**(운영 도메인) 등록.
- ⚠️ 운영은 **카카오 secret도 필수**(`application-prod.yml`은 빈 값 허용 안 함).
- 전체 운영 환경변수 목록·주의는 [배포_환경변수_및_환경무관_실행.md](배포_환경변수_및_환경무관_실행.md) 참고.

---

## 5. 자주 겪는 오류 (트러블슈팅)

| 증상 | 원인 / 해결 |
|---|---|
| `redirect_uri_mismatch` (구글) / KOE006·정상적이지 않은 요청 (카카오) | 콘솔의 Redirect URI가 `{base}/login/oauth2/code/{provider}` 와 **정확히 일치**하지 않음. 포트(8080)·스킴(http/https)·끝 슬래시까지 확인. |
| 운영에서 로그인 누르면 localhost로 튐 | 운영에 **`APP_BASE_URL` 미설정** → redirect_uri가 localhost로 조립됨. Railway Variables에 등록. |
| 구글 "액세스 차단됨: 앱이 인증을 완료하지 않았습니다" | OAuth 동의 화면이 "테스트" 상태 + 로그인 계정이 **테스트 사용자에 없음**. 테스트 사용자 추가 또는 앱 게시. |
| 카카오 로그인은 되는데 **이메일이 null** | 동의항목에서 `account_email` 미사용, 또는 비즈앱/검수 미완료. 동의항목·앱 상태 확인. |
| 카카오 `invalid_client` | Client Secret을 "사용함"으로 켰는데 `KAKAO_CLIENT_SECRET` 이 비어 있음(또는 반대). 콘솔 설정과 env를 일치시킨다. |
| 로그인 후 쿠키가 안 붙음(운영) | 운영은 HTTPS + `app.cookie.secure=true`(prod 프로파일). HTTP 도메인에선 secure 쿠키가 저장 안 됨 → HTTPS로 접속. |

---

## 6. 확인 방법

1. 로컬: `./gradlew bootRun` 후 `http://localhost:8080` → 로그인 화면 → "카카오/구글로 시작하기" 클릭.
2. 제공자 로그인·동의 → `/` 로 복귀하며 우상단이 로그인 상태로 바뀌면 성공.
3. 브라우저 개발자도구 → Application → Cookies 에 `access_token`, `refresh_token`(HttpOnly)이 있으면 정상.
