# IMSLP 수집 조사 (스파이크) — 2026-09-05

> 작성: senior-dev. 목적: 큐레이션된 피아노 인기곡의 **메타데이터와 PDF 를 IMSLP 에서 수집**하는 크롤러를 설계하기 전에,
> 실제 HTTP 응답으로 확인한 사실만 정리한다. 설계·테스트·구현은 다음 단계. 모든 응답은 curl 로 직접 받았다
> (요청 간격 2초 이상, UA `SheetMusicKR-spike/0.1 (contact: gks9306202@gmail.com)`, PDF 실파일은 1개만 수신).
> 기획서 `docs/기획/00_제품비전_IMSLP문제정의_핵심컨셉.md` §5·§6·§8 의 전제 중 **정정할 사실**은 §0 에 모았다.

---

## 0. 기획서 §8 대비 정정·보강 사항 (먼저 읽을 것)

| 기획서 기술 | 실제 확인 결과 |
|---|---|
| API 페이지당 약 334건 | **1,000건**. 키 `"0"`~`"999"` + `"metadata"`. `start=1000` 으로 다음 페이지. |
| `Special:ImagefromIndex/{id}` → 면책 페이지 → `/images/{a}/{ab}/{파일명}.pdf` | 실제 흐름은 **4단계**: 봇 게이트(302) → 면책 페이지 → `IMSLPDisclaimerAccept`(쿠키 발급) → `IMSLPImageHandler` **15초 대기 페이지** → 최종 URL 은 **별도 파일 호스트** `https://{ks15|vmirror}.imslp.org/files/imglnks/usimg/{a}/{ab}/IMSLP{id}-{파일명}.pdf`. 작품 페이지 안의 `/images/...` 링크는 **직접 다운로드가 안 된다**(HEAD 결과 `text/html`). |
| 파일 URL 은 작품 페이지에서 얻어야 한다 | 맞다. 다만 **파일 ID·크기·쪽수는 HTML 에만** 있고, 편집자·출판사·저작권 등 필드는 **`api.php` 위키텍스트**가 훨씬 깨끗하다(§3-3). |
| robots: `/images/`, `/wiki/Special:` Disallow | 맞다. 추가로 **`/index.php` Disallow** — 카테고리 페이지의 "next 200" 링크가 `/index.php?...pagefrom=` 이라 페이징을 따라가면 위반. 대신 카테고리 페이지 1회 응답에 **전체 목록(54,623건)이 JSON 으로 내장**돼 있다(§4-1). `api.php` 는 Disallow 목록에 **없다**. |
| 평점 수집 | **불가**. 정적 HTML 에는 전 파일이 `0.0/10`, 평가수 `-`(JS 가 나중에 채움). 다운로드 수는 HTML 에 있다. |

---

## 1. 작품 페이지 HTML 구조

조사 페이지 3개(모두 200 OK, 크기 0.4~1.0MB):
- `https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)` — 1,057,624 B, 파일 170개
- `https://imslp.org/wiki/F%C3%BCr_Elise,_WoO_59_(Beethoven,_Ludwig_van)` — 408,202 B, 파일 63개
- `https://imslp.org/wiki/Nocturnes,_Op.9_(Chopin,_Fr%C3%A9d%C3%A9ric)` — 854,891 B, 파일 134개

응답 헤더 특이점: `Vary: Cookie`, `Set-Cookie: imslp_wikiLanguageSelectorLanguage=en`. 앞단에 squid 캐시(`X-Cache: HIT/MISS from mysquid`)가 있어 정적 파일(robots.txt, friendlyredirect.html)은 **Accept-Encoding 을 안 보내도 `Content-Encoding: gzip`** 으로 온다. HTTP 클라이언트가 gzip 을 자동 해제하지 않으면(JDK HttpClient 가 그렇다) 직접 풀어야 한다.

### 1-1. 페이지 전체 골격

```
#wpaudiosection                         <- 오디오(Performances). 파일 블록 마크업이 악보와 동일하므로 반드시 제외
  h2 .mw-headline#Performances ... div.we > div.we_file.we_audio_top ...
#wpscoresection                         <- 여기부터가 악보
  h2 .mw-headline#Sheet_Music
  ul#wpscore_tabs > li#tabScore1_tab | li#tabScore3_tab | li#tabArrTrans_tab | li#tabExcerpts1_tab ...
  div.jq-ui-tabs#tabScore1              <- "Scores" 탭 (span.na-marker[data-name="Scores"] 로 확정)
    h3 .mw-headline "Scores"
    h4 .mw-headline "Complete"          <- 전곡
      div.we > div#IMSLP{id}.we_file_first.we_fileblock_{N} ...   <- 파일 블록(=판본)
    h4 .mw-headline "Adagio sostenuto (No.1)" / "Selections"     <- 악장별
      div.we ...
  div.jq-ui-tabs#tabScore3              <- "Parts" (있을 때)
  div.jq-ui-tabs#tabArrTrans            <- "Arrangements and Transcriptions"
    h3 -> h4 "Complete" / "Selections (Nos.1-2)" / 악장명 -> h5 "For Strings (Choe)" -> div.we ...
  (Elise 에는 h3 "Sketches and Drafts", h3 "Braille Scores" 도 있음)
h2 .mw-headline#General_Information
  div.wi_body > table > tr > th / td   <- 일반 정보 (§1-3)
div#catlinks                            <- 카테고리 링크 (For piano 등)
```

헤딩 계층: `h2`(Sheet Music) > `h3`(탭 이름: Scores / Parts / Arrangements and Transcriptions / Sketches and Drafts / Braille Scores) > `h4`(Complete / Selections / 악장명) > `h5`(편곡 편성 "For X (편곡자)"). 탭 컨테이너 `div.jq-ui-tabs` 끝의 `span.na-marker[data-name]` 이 탭 표시명이다.

**섹션 판정 규칙(후보)**: 파일 블록의 조상 중 가장 가까운 `div.jq-ui-tabs` 의 id 가 `tabScore1` 이면 원곡 악보, `tabArrTrans` 면 편곡. 그 블록 **앞에 있는 가장 가까운 `h4.mw-headline`** 텍스트가 `Complete` 면 전곡, 아니면 악장/발췌명. 1차 범위(전곡 PDF 만)는 `tabScore1` + `h4 == "Complete"` 만 취하면 된다. 3페이지 파일 설명 텍스트 분포: `Complete Score` 141건, 악장별(`1. Adagio sostenuto` 등), 파트보(`Violins I`, `Cello Part`), `Complete Score and Parts`, `Complete Score (a4)` 등.

### 1-2. 파일 블록(판본) DOM — `div.we`

`div.we` 하나 = 판본(edition) 하나. 그 안에 파일이 1개 이상(`div#IMSLP{id}`)과 판본 정보 표(`table.we_edition_info`) **1개**가 있다(파일 여러 개가 표 하나를 공유: 3페이지에서 `div.we` 85개 = 악보 표 62개 + 오디오 표 23개). 오디오 블록은 `div.we_audio_top` / `table.we_audio_info` 라 구분 가능.

```html
<div class="we">
  <div id="IMSLP00014" class="we_file_first we_fileblock_37">   <!-- 첫 파일: we_file_first, 2번째부터: we_file we_file_hideentry -->
    <div class="we_file_download plainlinks"><p>
      <b><a class="external text" href="https://imslp.org/wiki/Special:ImagefromIndex/00014">
        <span title="Download this file"><span class="we_file_dlarrwrap">...</span>Complete Score</span></a></b>  <!-- 파일 설명 -->
      <br/>
      <span class="we_file_info2">
        <span class="hidden"><a href="/images/a/a8/Beethoven%2C_L.v._-_Piano_Sonata_14.pdf" class="internal">*</a></span>  <!-- 해시경로 a/a8 + 원본 파일명. 직접 다운로드 불가 -->
        <a href="/wiki/File:Beethoven,_L.v._-_Piano_Sonata_14.pdf">#00014</a> - 1.01MB, 14 pp.        <!-- 파일ID, 크기, 쪽수 -->
        <span class="mobilehide625">- <span id='current-rating-14'>0.0/10</span> (<span id='num-of-ratings-14'>-</span>)</span>  <!-- 평점: JS 로드, 정적 HTML 에선 항상 0.0 / - -->
        <span class="uctagonly mh900">- <a href="/wiki/Special:IMSLPEditCTag/00014/...">V/V/V</a></span>  <!-- 저작권 검토 태그(CA/US/EU) -->
        - <span title="Total number of downloads: 56391"><a href="/wiki/Special:GetFCtrStats/@14">56391</a>x</span>  <!-- 다운로드 수 -->
        <span class="ms555"> - <a href="/wiki/User:Feldmahler">Feldmahler</a></span>                 <!-- 업로더 -->
      </span></p></div>
    <div class="we_file_info mhs"><p><span class="mh555">
        <a href="/wiki/IMSLP:File_formats">PDF</a> scanned by <i>Unknown</i><br/>                    <!-- 포맷 + 스캔 제공자 -->
        <a href="/wiki/User:Feldmahler">Feldmahler</a> (<span title="Updated: 2020/5/26" class="hovertext">2006/2/16</span>)  <!-- 업로더, 등록일(갱신일은 title) -->
    </span></p></div>
    <div class="we_clear"></div>
  </div>
  <!-- 같은 판본의 2번째 파일부터 <div id="IMSLP..." class="we_file we_fileblock_37 we_file_hideentry"> 반복.
       앞에 <div class="we_file_more" id="we_file_more_37" data-block="37">"5 more"</div> 는 UI 접기용이고 DOM 은 이미 전부 존재 -->
  <table class="we_edition_info gainlayout"><tr><td class="we_edition_info_i gainlayout">
    <table>
      <tr><th>Editor</th><td><a href="/wiki/Category:Schenker,_Heinrich">Heinrich Schenker</a> (1868-1935)</td></tr>
      <tr><th>Pub<span class="mh555">lisher</span><span class="ms555">.</span> Info.</th><td>Vienna: <a href="/wiki/Universal_Edition">Universal Edition</a><!--ASL14-->, 1921.  Plate U.E. 7000.</td></tr>
      <tr><th>Reprinted</th><td>New York: Dover Publications, 1975.</td></tr>
      <tr><th>Copyright</th><td><div class="plainlinks"><a href="/wiki/IMSLP:Public_Domain" title="IMSLP:Public Domain">Public Domain</a> ...</div></td></tr>
      <tr><th>Misc. Notes</th><td>...</td></tr>                 <!-- 있을 때만 -->
      <tr><th>Arranger</th>...                                  <!-- 편곡 블록 -->
      <tr><th><span class="mh555">Purchase</span></th><td><div class="imslpd_purchase">...</div></td></tr>  <!-- 무시 -->
    </table>
  </td><td><div class="we_thumb"><a href="/wiki/File:..." class="image"><img data-src="//cdn.imslp.org/images/thumb/pdfs/a7/....png"></a></div></td></tr></table>
</div>
```

**셀렉터·정규식 후보** (jsoup CSS 기준. `th` 텍스트는 `mh555/ms555` 스팬 때문에 "Pub"+"lisher"+"." 로 쪼개져 있으니 `th` 의 `text()` 를 공백 제거 후 startsWith 로 비교):

| 항목 | 셀렉터 / 정규식 | 비고 |
|---|---|---|
| 악보 영역 | `#wpscoresection` | 오디오(`#wpaudiosection`) 제외 필수 |
| 판본 블록 | `#wpscoresection div.we` (`div.we_audio_top` 포함 블록 제외) | |
| 파일 | `div.we > div[id^=IMSLP]` -> id 에서 `IMSLP(\d+)` | 앞자리 0 포함(`00014`). URL 에는 원문 그대로 쓴다 |
| 파일 설명 | `div.we_file_download a.external span[title="Download this file"]` 텍스트 | `Complete Score`, `1. Adagio sostenuto`, `Complete Score and Parts` 등 |
| ImagefromIndex 링크 | `div.we_file_download a.external[href*=Special:ImagefromIndex/]` -> `Special:ImagefromIndex/(\d+)` | |
| 원본 파일명·해시경로 | `span.we_file_info2 span.hidden a.internal[href]` -> `^/images/([0-9a-f])/([0-9a-f]{2})/(.+)$` | 파일명은 URL 인코딩됨(`%2C`). 최종 URL 조립엔 쓰지 말고 §2 의 `data-id` 를 신뢰 |
| 파일ID·크기·쪽수 | `span.we_file_info2 > a[href^=/wiki/File:]` 텍스트 `#(\d+)`, 그 **다음 텍스트 노드**에 `-\s*([\d.]+)\s*(MB|KB),\s*(\d+|\?)\s*pp\.` | 관측 변형: `- 1.01MB, 14 pp.`(273건) / `- 3.2MB, ? pp.`(6건, `&#160;?`) / 텍스트 없음(22건) / 오디오 `- 5.6MB - 4:32`(59건, 제외) |
| 다운로드 수 | `span[title^="Total number of downloads:"]` -> `(\d+)` | |
| 평점 | (수집 불가 — JS) | `#current-rating-{id}`, `#num-of-ratings-{id}` 는 항상 `0.0/10`, `-` |
| 업로더 | `div.we_file_info a[href^=/wiki/User:]` 첫 번째 | |
| 스캔 제공자/포맷 | `div.we_file_info span.mh555` 텍스트 -> `^(PDF|MP3 file|ZIP|...)\s+(scanned by|typeset by)\s+(.+?)$` | "scanned by Unknown"(57), "typeset by arranger"(126), "typeset by editor"(18), "scanned by afp0316" 등 |
| 등록일 | `div.we_file_info span.hovertext` 텍스트 `(\d{4}/\d{1,2}/\d{1,2})`, 갱신일은 `[title^=Updated:]` | |
| 편집자 | `table.we_edition_info th` 텍스트 == `Editor` 인 행의 `td` | 이름 링크 `a[href^=/wiki/Category:]`, 생몰년 텍스트 뒤따름 |
| 편곡자 | th == `Arranger` | |
| 출판사 정보 | th startsWith `Pub` 인 행의 `td` 전체 텍스트 | 형식 자유. 연도 `(\d{4})`, 플레이트 `Plate\s+(.+?)\.` 정도만 정규식 |
| 재판 | th == `Reprinted` | |
| 저작권 | th == `Copyright` 인 행의 `td a[title^=IMSLP:]` 의 `title` 에서 `IMSLP:` 제거 | 관측 값 18종(§1-4). `noanon` 스팬(tag/del 링크)은 무시 |
| 기타 메모 | th == `Misc. Notes` | |
| 썸네일 | `div.we_thumb img[data-src]` | 지연 로딩용 `data-src`(`src` 아님) |

**JS 없이 정적 HTML 만으로 파일 목록 전체가 나온다** (`we_file_hideentry` 는 CSS 숨김일 뿐).

### 1-3. General Information 블록

`h2 .mw-headline#General_Information` 다음 `div.wi_body > table` 의 `tr > th / td`. 라벨은 `span.mh555`(긴 이름)+`span.ms555`(모바일 축약)로 쪼개진 경우가 있어 **`th` 안에 `span.mh555` 가 있으면 그 텍스트, 없으면 `th` 텍스트**로 라벨을 잡는다.

| 라벨(mh555 기준) | 값 형식(관측) | 메모 |
|---|---|---|
| Work Title | `Piano Sonata No.14` / `Für Elise` / `Nocturnes` | `th.wi_head` + `td.wi_head` |
| Alternative Title | `Sonata quasi una fantasia ; Moonlight Sonata` / `Bagatelle No.25 in A minor ; Für Therese` / (빈 값) | 렌더 텍스트는 "Alt"+"ernative"+".". `;` 구분 |
| Name Translations | `span[title="ko"]` 등 언어코드별 스팬 | **`span[title=ko]` = 한국어 제목("피아노 소나타 14번")** — 별칭 사전 초기값 후보. `span.expandline` 은 "N more..." 표시용 |
| Name Aliases | 언어별 별칭(`Moonlight Sonata`, `月光ソナタ`...) | 같은 구조 |
| Composer | `td a[href^=/wiki/Category:]` | `Beethoven, Ludwig van` |
| Opus/Catalogue Number | `Op.27 No.2` / `WoO 59` / `Op.9` | |
| Internal Reference Number | `ILB 175` / `ILB 73` / `IFC 65` | API 의 `icatno` 와 동일 |
| Key | `C-sharp minor` / `A minor` / 없음(곡집) | |
| Movements/Sections | `3 movements:` + `<ol><li>` / `3 nocturnes:` + `<dl>` / `1. Poco moto` | 악장명 안에 `span.music-symbol`(♯♭) 이 섞여 있어 텍스트 붙여야 함 |
| Year/Date of Composition | `1802` / `1810` / `1830-31` | 문자열 그대로 저장 |
| First Publication | `1802` / `1867 - in <i>Neue Briefe Beethovens</i>` | |
| Dedication, Average Duration, Composer Time Period, Piece Style | 자유 텍스트/링크 | |
| Instrumentation | **`piano`** (3곡 모두 소문자 한 단어) | 피아노 독주 판정 1차 기준 |
| Authorities / External Links / Extra Information | 링크 모음 | 미사용 |

작품 페이지 `div#catlinks` 및 `api.php prop=categories` 에는 **`For piano`, `Scores featuring the piano`, `For 1 player`** 가 있고 편곡은 `... (arr)` 접미가 붙는다(§4).

### 1-4. 관측된 저작권 표기 값 (3페이지 208개 파일 블록)

`Public Domain`(117) / `Public Domain (dedicated)`(4) / `Creative Commons Zero 1.0`(3) / `Creative Commons Attribution 4.0`(16), `3.0`(5) / `Creative Commons Attribution-ShareAlike 4.0`(29), `3.0`(1), `Attribution Share Alike 3.0`(2) / `Creative Commons Attribution-NonCommercial 4.0`(5), `3.0`(3), `Attribution Non-commercial 3.0`(3) / `Creative Commons Attribution-NonCommercial-ShareAlike 4.0`(7), `3.0`(1), `Non-commercial Share Alike 3.0`(4) / `Creative Commons Attribution-NonCommercial-NoDerivs 4.0`(5), `3.0`(1), `Non-commercial No Derivatives 3.0`(1) / `Performance Restricted Attribution-NoDerivs 3.0`(1).

표기 철자가 버전별로 다르므로 **원문 그대로 저장 + 정규화 코드(PD / CC0 / CC-BY / CC-BY-SA / CC-BY-NC / CC-BY-NC-SA / CC-BY-NC-ND / OTHER)** 두 컬럼이 필요하다. 위키텍스트(§3-3)의 `|Copyright=` 값도 동일 문자열이다.

---

## 2. 다운로드 흐름 (실측)

`Special:ImagefromIndex/00014`(베토벤 소나타 14번, Schenker 판, 1.01MB)로 확인. 최종적으로 PDF 1개를 수신해 검증했다.

### 2-1. 단계별 응답

| # | 요청 | 응답 | 의미 |
|---|---|---|---|
| 1 | `GET /wiki/Special:ImagefromIndex/00014` (쿠키 없음) | **302** `Location: /friendlyredirect.html#/wiki/Special:ImagefromIndex/00014`, 본문은 nginx 302 페이지, Set-Cookie 없음 | **nginx 봇 게이트**. 브라우저(JS)가 아니면 여기서 막힌다 |
| 1a | `GET /friendlyredirect.html` | 200 정적 HTML. JS 가 `friendlyredirect=<난수>` 를 `POST /friendlyredirect2.html#dest` 로 제출 | |
| 1b | `POST /friendlyredirect2.html` | 200 정적 HTML. **JS 가 `document.cookie='redirectPassed=1; path=/; Max-Age=400일; SameSite=Lax; Secure'` 를 심고** `location.href=dest` | 서버는 Set-Cookie 를 보내지 않는다. 클라이언트가 스스로 쿠키를 만드는 구조 |
| 2 | `GET /wiki/Special:ImagefromIndex/00014` + `Cookie: redirectPassed=1` | **200** "Disclaimer - IMSLP"(24.6KB). 본문에 `<a href="/wiki/Special:IMSLPDisclaimerAccept/00014">I understand</a>` | 면책 페이지. 폼·타이머 없음 |
| 3 | `GET /wiki/Special:IMSLPDisclaimerAccept/00014` | **302** `Location: http://imslp.org/wiki/Special:IMSLPImageHandler/00014`, **`Set-Cookie: imslpdisclaimeraccepted=yes; Max-Age=2592000(30일); path=/`** | 수락 쿠키 발급 |
| 4 | `GET /wiki/Special:IMSLPImageHandler/00014` + 두 쿠키 | **200** "Subscribe" 페이지(27.5KB). `Set-Cookie: userHash=<32hex>`(400일). 본문 `<span id="sm_dl_wait" data-id="https://ks15.imslp.org/files/imglnks/usimg/a/a8/IMSLP00014-Beethoven,_L.v._-_Piano_Sonata_14.pdf">` | **최종 URL 은 `data-id` 속성**. `IMSLPJS.D.js` 가 `this.timer=15` 로 1초마다 카운트다운 후 `data-id` 를 링크로 렌더 → **15초 대기는 순수 클라이언트 JS**(서버 시간 검증 없음). "Are you a member? Please sign in" — 회원(유료 구독)은 대기 없음 |
| 5 | `GET https://ks15.imslp.org/files/imglnks/usimg/a/a8/IMSLP00014-Beethoven,_L.v._-_Piano_Sonata_14.pdf` (쿠키·Referer 포함) | **200 `Content-Type: application/pdf`, `Content-Length: 1059957`, `Accept-Ranges: bytes`, `ETag`, `Last-Modified`** | 수신 파일 `%PDF-1.3`, 14쪽 확인(스크래치 `IMSLP00014.pdf`). 쿠키·Referer 필수 여부는 미검증(추가 수신 금지 조건) |
| 2' | `GET /wiki/Special:ImagefromIndex/00014` + `Cookie: redirectPassed=1; imslpdisclaimeraccepted=yes` | **200** 이 바로 4번 대기 페이지. `data-id` 호스트가 이번엔 **`vmirror.imslp.org`** | 쿠키 2개면 면책 단계 생략. **파일 호스트는 요청마다 바뀐다(ks15 / vmirror)** → URL 을 조립하지 말고 `data-id` 를 읽어야 한다 |
| - | `HEAD https://imslp.org/images/a/a8/Beethoven%2C_L.v._-_Piano_Sonata_14.pdf` | 200 이지만 **`Content-Type: text/html`** | 작품 페이지의 `/images/` 링크는 위키 핸들러로 라우팅되는 HTML 이지 PDF 가 아니다 |
| - | `GET https://ks15.imslp.org/robots.txt` | 404 | 파일 호스트엔 robots 없음(본 도메인 robots 의 `/imglnks/` Disallow 는 imslp.org 경로 기준) |

**2026-09-22 실측 보충 — 302 자체가 아니라 `Location` 의 모양이 게이트인지 파일인지를 가른다.**

위 표(2번·2'번 행)는 "쿠키 2개(`redirectPassed=1; imslpdisclaimeraccepted=yes`)를 보내면 `Special:ImagefromIndex/{id}` 는
**항상 200** 이고, 본문의 `span#sm_dl_wait[data-id]` 가 최종 파일 URL" 이라는 전제로 읽힐 수 있는데, **그 전제가 전부 맞지는 않는다는 것을
2026-09-22 운영 로그로 확인했다.** 같은 쿠키 2개 조합으로도 `Special:ImagefromIndex/{id}` 가 **302 + `Location:
https://s9.imslp.org/files/imglnks/...`** 를 바로 준 사례가 있었다. 이건 봇 게이트가 아니라 **대기 페이지(4번 행)를 생략한 정상 파일
리다이렉트**다 — `Location` 의 모양이 원래 `span#sm_dl_wait[data-id]` 로 오던 값과 같은 패턴(`{서브도메인}.imslp.org` + `/files/...`)이기
때문이다. 이때는 15초 대기 페이지 자체가 없으므로 **`Location` 이 곧 최종 파일 URL** 이고, 이어지는 파일 GET(5번 행)만 그대로 하면 된다.

즉 3xx 응답을 봇 게이트로 볼지 정상 파일 리다이렉트로 볼지는 **"302 인가"가 아니라 "`Location` 이 `*.imslp.org` 서브도메인 +
`/files/` 경로인가"** 로 가른다(본체 `imslp.org` 로의 302 나 `/friendlyredirect.html` 류는 여전히 게이트 — 1번·3번 행 그대로 유효하다).
위 표의 관측(그 순간엔 매번 200 대기 페이지였던 것)을 지우지 않는 이유는 그때 관측은 그때대로 사실이기 때문이다 — 새로 확인된 것은
"200 대기 페이지 / 302 즉시 리다이렉트" **둘 다 일어날 수 있다**는 것이다. 판별 규칙은 `HttpImslpClient.isFileHostRedirect()` 와
그 규칙을 20건으로 고정한 `HttpImslpClientFileRedirectTest` 로 코드에 옮겼다(정정 배경은 `03_기술결정.md` §3 「파일 대기 값(15초) 판단」
2026-09-22 정정 참고).

### 2-2. 크롤러 관점 요약

```
필요 쿠키:  redirectPassed=1              (클라이언트 생성 쿠키. 그냥 보내면 됨)
            imslpdisclaimeraccepted=yes   (IMSLPDisclaimerAccept/{id} 1회 호출로 발급, 30일. 직접 보내도 동작 확인)
최소 흐름:  GET /wiki/Special:ImagefromIndex/{id}  --쿠키 2개-->  200 대기 페이지
            파싱: span#sm_dl_wait[data-id]  (값이 `https&#58;//...` 처럼 엔티티라 디코딩 필요 — jsoup attr() 는 자동)
            15초 sleep (사이트 의도 존중, §5)
            GET {data-id}  ->  application/pdf. Content-Length 일치 + 매직바이트 %PDF 검증
파일명:     IMSLP{id}-{원본파일명} (data-id 마지막 세그먼트). 우리 저장 키는 파일 ID 기준으로 새로 짓는다
```

---

## 3. 공개 API 와 MediaWiki api.php

### 3-1. `API.ISCR.php` 작품 목록 (`type=2`)

`GET https://imslp.org/imslpscripts/API.ISCR.php?account=worklist/disclaimer=accepted/sort=id/type=2/start=0/retformat=json` → 200, 314KB.

```json
{
  "0": {
    "id": "\"A\" (Ferrari, Carlotta)",
    "type": "2",
    "parent": "Category:Ferrari, Carlotta",
    "intvals": {
      "composer": "Ferrari, Carlotta",
      "worktitle": "\"A\"",
      "icatno": "ICF 1237",
      "pageid": "1637322"
    },
    "permlink": "https://imslp.org/wiki/\"A\"_(Ferrari,_Carlotta)"
  },
  "999": { "...": "..." },
  "metadata": {
    "start": 0, "limit": 1000, "sortby": "id", "sortdirection": "ASC",
    "moreresultsavailable": true, "timestamp": 1788619970, "apiversion": 10
  }
}
```

- `id` = 작품 페이지 제목("제목 (성, 이름)"). `type`·`pageid` 는 **문자열**. `parent` = 작곡가 카테고리. `intvals` 키는 정확히 `composer`, `worktitle`, `icatno`(Internal Reference Number), `pageid` 4개(1,000건 합집합 기준).
- 페이지 크기 **1,000**(`limit`). 다음 페이지는 `start=1000`(확인: `metadata.start=1000`, 첫 항목 `100 Preludyi (Various)`). 종료 조건: `metadata.moreresultsavailable === false`.
- **파일·편성·조성 정보 없음**. 용도는 "제목 -> permlink/pageid 매핑" 뿐이며, 큐레이션 목록이 정확한 페이지 제목을 갖고 있으면 이 API 는 필요 없다.

### 3-2. `type=1` (인물)

같은 스키마. `id` 가 `Category:성, 이름`, `parent: ""`, **`intvals: []`(빈 배열 — 객체가 아님)**, `permlink` 는 카테고리 URL. 작곡가·연주자·기관 카테고리가 섞여 있고 생몰년 등 부가 정보 없음 → 사용 가치 낮음.

### 3-3. MediaWiki `api.php` (robots Disallow 아님, 200 확인)

| 요청 | 결과 |
|---|---|
| `api.php?action=parse&page={제목}&prop=sections\|categories&format=json` | 12.9KB. `parse.categories[].*` 에 `For_piano`, `Scores_featuring_the_piano`, `For_1_player`, `..._(arr)` 등. `parse.sections[]` 에 `{toclevel, line}`(Sheet Music / Scores / Arrangements ...) |
| `api.php?action=parse&page={제목}&prop=wikitext&format=json` | 24KB. `parse.wikitext.*` 에 **원본 위키텍스트**(아래) |
| `api.php?action=query&titles=File:{파일명}&prop=imageinfo&iiprop=url\|size\|sha1\|mime\|timestamp\|user&format=json` | `size`(바이트, 예 8397816), `sha1`, `mime`, `url`(`//imslp.org/images/e/ed/...` — 직접 다운로드 불가), `timestamp`, `user`. **IMSLP 파일 ID(#NNNNN)·쪽수는 없음** |

위키텍스트 구조(Für Elise):

```
{{#fte:imslppage
| *****AUDIO***** =
{{#fte:imslpaudio ... }}           <- 오디오. 무시
| *****FILES***** =
{{#fte:imslpfile                    <- 첫 헤딩 전 블록 = "Scores / Complete"
|File Name 1=PMLP14377-Beethoven-WoO.059nohl1867.pdf
|File Description 1=Complete Score
|Editor={{LinkEd|Ludwig|Nohl|1831|1885}}<br>{{FE}}
|Image Type=Normal Scan
|Scanner={{BeethBonn}}
|Uploader=[[User:Feduol|Feduol]]
|Date Submitted=2011/6/1
|Publisher Information=''Neue Briefe Beethovens'' (pp.28-33)<br>{{P|Cotta|J.G. Cotta|Stuttgart||1867||}}
|Copyright=Public Domain
|Misc. Notes=
}}
{{#fte:imslpfile
|File Name 1=...pdf
|File Name 2=...pdf                 <- 한 판본에 파일 여러 개: 접미 숫자 1,2,... (Scanner 1 / Uploader 2 / Date Submitted 2 도)
|File Description 1=Complete Score
|File Description 2=Complete Score
...
}}
===Arrangements and Transcriptions===
=====For Orchestra (Rondeau)=====
{{#fte:imslpfile |Arranger={{LinkArr|...}} ... }}
===Sketches and Drafts===
===Braille Scores===
| *****WORK INFO*****
|Work Title=Für Elise
|Alternative Title=Bagatelle No.25 in A minor ; Für Therese
|Opus/Catalogue Number=WoO 59
|Key={{Key|a}}
|Number of Movements/Sections=1. Poco moto
|Year/Date of Composition=1810
|Year of First Publication=1867 - in ''Neue Briefe Beethovens'' ...
|Piece Style=Classical
|Instrumentation=piano
|Tags=pieces ; bagatelles ; pf
| *****END OF TEMPLATE***** }}
```

관측된 파일 블록 필드(빈도): `Publisher Information`(47) `Copyright`(47) `Uploader`(46) `Date Submitted`(46) `Image Type`(37) `Scanner`(36) `Editor`(34) `Arranger`(17) `Thumb Filename`(12) `Amazon`(1) `Misc. Notes`. 작품 정보 필드: `Work Title, Alternative Title, Opus/Catalogue Number, Key, Number of Movements/Sections, Average Duration, Dedication, Year/Date of Composition, Year of First Publication, Librettist, Language, Piece Style, Incipit, External Links, Extra Information, Instrumentation, Tags`.

**HTML 파싱 vs 위키텍스트 파싱**

| | HTML (`/wiki/{제목}`) | 위키텍스트 (`api.php prop=wikitext`) |
|---|---|---|
| robots | 허용 | 허용(`/api.php` 는 Disallow 목록에 없음) |
| 크기 | 0.4~1.0MB | 24KB(Elise) — 20~40배 작음 |
| 파일 ID(#NNNNN)·크기·쪽수·다운로드 수 | **있음** | **없음**(imageinfo 로 크기·sha1 은 보완 가능, ID·쪽수는 불가) |
| 섹션(전곡/악장/편곡) | 헤딩 DOM 으로 판정 | `===...===` 헤딩 줄로 판정(첫 헤딩 전 = Scores/Complete). 악장별 페이지의 헤딩 레벨은 미확인(Elise 만 수신) |
| 편집자·출판사·저작권·스캐너·등록일 | 렌더 텍스트(사람이 읽기 좋음, 라벨이 스팬으로 쪼개짐) | `|키=값` 한 줄. 정확하지만 `{{P|...}}`, `{{LinkEd|...}}`, `{{Key|a}}` 같은 템플릿이 섞여 있어 표시용 문자열은 별도 정리 필요 |
| 한국어 제목(Name Translations ko) | 있음 | 없음(Wikidata 에서 렌더 시 주입) |
| 파싱 라이브러리 | jsoup 필요 | 불필요(줄 단위 정규식) |

**권고(설계 단계 결정)**: 작품 1건당 **HTML 1회 + 위키텍스트 1회**. HTML 에서 `파일ID <-> 원본 파일명 <-> 크기/쪽수/다운로드수/섹션/한국어 제목` 을, 위키텍스트에서 `판본 필드(편집자·출판사·저작권·스캐너·등록일)` 를 얻어 **원본 파일명(`File Name N`)** 으로 조인한다. HTML 만으로도 전부 가능하므로 요청 수를 줄이려면 HTML 단독도 선택지다.

---

## 4. 피아노곡만 고르는 방법

### 4-1. `Category:For_piano` 페이지 (1회 GET, 6.66MB)

- 눈에 보이는 목록은 200건(`a.categorypagelink`)이고 "next 200" 링크는 `/index.php?title=Category:For_piano&pagefrom=...` → **robots Disallow 경로**라 따라가면 안 된다.
- 그러나 페이지 안 `<script>` 에 **`$.extend(catpagejs,{"p1":{"A":[...],"B":[...]}})`** 로 **카테고리 전체 54,623건**이 내장돼 있다(`origcatmap.p1="For_piano"`, `#catttlmsgp1` = `54,623`). 파싱: `$.extend(catpagejs,` 다음부터 `);if(typeof origcatmap` 전까지가 JSON(3.3MB). 첫 글자 키 42개.
- 항목 문자열 형식: `"제목 (작곡가)|<탭코드><탭이름>\<파일수>|..."`
  - `Piano Sonata No.14, Op.27 No.2 (Beethoven, Ludwig van)|RRecordings\31|NNaxos\0|SScores\28|AArrangements and Transcriptions\104`
  - `Für Elise, WoO 59 (Beethoven, Ludwig van)|RRecordings\6|NNaxos\0|SScores\20|AArrangements and Transcriptions\31|DSketches and Drafts\1`
  - 탭코드: `S`=Scores(변형 `SPiano Scores`, `SFull Scores`, `SScores and Parts`), `P`=Parts, `A`=Arrangements, `R`=Recordings, `N`=Naxos, `D`=Sketches, `O`=Other, `B`=Books.
- 이 JSON 은 `For_piano` 정회원(원곡이 피아노)만 담고, 편곡은 별도 카테고리 `For_piano_(arr)` 다.

### 4-2. 작품 단위 판정

- `api.php?action=parse&prop=categories` 의 `For_piano` 포함 여부(편곡 `For_piano_(arr)` 는 제외) — 정확하고 가벼움(13KB).
- 위키텍스트 `|Instrumentation=piano` / HTML General Information `Instrumentation: piano` — 3곡 모두 소문자 `piano` 한 단어. `piano 4 hands`, `2 pianos`, `piano, orchestra` 등 변형이 있을 수 있으니 **정확히 `piano` 와 같은지**로 판정한다.

### 4-3. 실용적 결론

1차 범위는 **큐레이션 목록(200~300곡)** 이므로 "카테고리를 훑어서 피아노곡을 찾는" 작업 자체가 없다. 실용적 순서:
1. 큐레이션 목록에 IMSLP 페이지 제목(예: `Nocturnes, Op.9 (Chopin, Frédéric)`)을 관리자가 적는다.
2. 수집 시 작품별로 `api.php categories` 또는 `Instrumentation` 으로 **피아노 독주인지 검증**하고 아니면 건너뛴다(오타·편곡 페이지 방지).
3. `Category:For_piano` 내장 JSON 은 (a) 큐레이션 제목이 실제 존재하는지 일괄 검증, (b) 관리자 화면 제목 자동완성 사전(5만 건)으로 쓸 수 있다. 하루 1회 이상 받을 이유는 없다.

---

## 5. robots.txt 준수 범위 — 기술 리드 의견 (결정은 사용자)

### 5-1. 사실

- `robots.txt`(gzip 으로 수신, 원문 356B): `User-agent: *` 에 `Disallow: /index.php, /wiki/Special:, /images/, /imglnks/, /wiki/File:, /wiki/Image:, /instruments, /works, /work/, /instrument/, /library/`, `Crawl-delay: 2`, `Sitemap: https://imslp.org/sitemap/sitemap-index-imslp_wiki.xml`(NS_0 사이트맵 45개). `MyriadBot` 만 전부 허용. `/wiki/{작품}`, `/wiki/Category:`, `/api.php`, `/imslpscripts/` 는 허용.
- 허용 경로만으로 얻을 수 있는 것: **메타데이터 전부**(작품 정보, 판본, 파일 ID, 크기, 쪽수, 저작권, 다운로드 수) + 카테고리 목록 + 한국어 제목.
- PDF 를 받으려면 `/wiki/Special:ImagefromIndex`(Disallow) → 봇 게이트(JS 쿠키) → 면책 수락 → 15초 JS 대기 → 파일 호스트. 즉 IMSLP 는 **robots 로 한 번, JS 게이트로 두 번, 대기 시간으로 세 번** "사람이 브라우저로 받으라"는 신호를 보내고 있고, 대기 없는 다운로드는 유료 회원 혜택으로 팔고 있다. 기술적으로 우회는 쉽지만(쿠키 2개), **쉽다는 것과 의도에 맞는다는 것은 다르다.**
- 파일 자체는 대부분 PD/CC 라 재배포 자체는 라이선스상 가능(§1-4 정규화 후 PD/CC0/CC-BY/CC-BY-SA 만 1차 대상. NC 는 우리 서비스가 비영리일 때만, ND 는 원본 그대로일 때만). 문제는 저작권이 아니라 **IMSLP 서버 자원과 정책 존중**이다.

### 5-2. 선택지

| 안 | 내용 | 장점 | 단점/리스크 |
|---|---|---|---|
| A. 전자동 | 메타데이터 + PDF 모두 크롤러가 자동(2초 간격, 15초 대기 준수, 식별 UA, 동시 1, 재요청 없음) | 구현 단순, 관리자 손 안 감 | robots 명시 위반. 200~300건이면 트래픽은 미미하지만 "알고 위반했다"는 사실은 남음. IP 차단 시 서비스 핵심 가치 상실 |
| **B. 메타 자동 + PDF 관리자 1회 대리** | 메타데이터는 허용 경로로 자동 수집. PDF 는 관리자가 곡 상세에서 "가져오기" 버튼을 눌러 **그때 1건** 서버가 §2 흐름으로 받아 버킷에 저장(15초 대기 그대로 둠). 한 파일은 두 번 받지 않음 | 사람이 한 건씩 개시 → 브라우저 사용자와 같은 빈도·같은 절차. 코드 경로가 A 와 같아 정책이 바뀌면 자동화 전환 쉬움 | 여전히 Disallow 경로를 프로그램이 호출. 300곡이면 관리자가 300번 클릭(약 2시간) |
| C. 메타 자동 + PDF 완전 수동 | 관리자가 브라우저에서 IMSLP 로 직접 받아 우리 관리자 화면에 업로드. 크롤러는 Disallow 경로를 전혀 안 건드림 | robots 완전 준수. 파일 검수(스캔 품질)를 겸함 | 관리자 노동 최대. 업로드 UI 필요(기존 파일 모듈 재사용 가능) |
| D. IMSLP 에 알리고/허가 받고 진행 | 연락(`User:Feldmahler#Contact`)해 목적·규모·간격을 알리고 A 또는 B 진행. 필요하면 유료 회원 가입(대기 면제) | 가장 떳떳함. 미러 자격 가능성 | 답이 늦거나 없을 수 있음 |

### 5-3. 권고

**B 를 기본으로, D 를 병행**. 근거: (1) 1차 범위 300곡은 "사람이 300번 받는" 규모라 B 는 실질적으로 브라우저 이용과 구별되지 않는다. (2) 메타데이터 수집은 전부 허용 경로라 자동화해도 문제없다. (3) C 는 B 와 결과가 같으면서 관리자 UI 가 하나 더 필요하고, 파일 무결성(원본 파일명·크기 대조)은 B 가 낫다. (4) A 로 시작해 차단당하면 되돌릴 방법이 없지만, B 로 시작해 D 의 허가를 받으면 같은 코드로 A 로 확장할 수 있다.

B 의 구현 규칙: 큐레이션 목록 외 파일은 절대 받지 않음 / 파일 ID 단위 1회 수신, 성공 기록이 있으면 재요청 금지 / 15초 대기 유지(우회하지 않음) / 동시 1커넥션 / `User-Agent` 에 서비스명+연락처 / 실패(4xx/5xx/차단) 시 자동 재시도 없이 관리자에게 표시 / 저작권 정규화가 PD·CC0·CC-BY·CC-BY-SA 가 아니면 버튼 자체를 비활성(IMSLP 링크만 제공).

---

## 6. 크롤러 구현 위치 권고 (도입 결정은 설계 단계에서)

### 6-1. Spring 앱 내부 vs 별도 스크립트

| | Spring 앱 내부(관리자 API 로 개시하는 비동기 잡) | 별도 스크립트/프로세스 |
|---|---|---|
| 런타임 | Java 17 그대로. Python 없음 | Node 24 는 이 PC 에 있으나(frontend 빌드용) Railway 서비스에 없음. Python 미설치 |
| 저장 | 기존 파일 모듈(로컬 `uploads/` <-> S3 호환 버킷, 컨벤션 §5-3)·JPA 엔티티·관리자 인증 **재사용** | 저장·DB 접근·인증을 따로 구현하거나 앱 API 를 다시 호출해야 함 |
| 운영 | Railway 단일 서비스, 배포 절차 그대로. B 안의 "관리자 버튼 → 서버가 1건 수신" 과 자연스럽게 맞음 | 서비스 하나 더(비용·배포 절차 추가) 또는 개발 PC 에서만 실행 |
| 주의 | Railway 디스크는 휘발 → prod 에선 버킷에 바로 저장. 재시작 대비 **진행 상태를 DB 에**(작품별·파일별 상태). 인스턴스 2개 이상이면 중복 실행 방지(락 또는 단일 인스턴스 전제). 요청 스레드에서 15초 기다리지 말고 `@Async` 워커 1개 + 상태 폴링 | |

**권고: Spring 앱 내부**, 단 `@Scheduled` 상시 크롤이 아니라 **관리자가 개시하는 잡**(메타데이터 갱신 잡 + 파일 1건 수신 잡)으로. 근거는 위 표의 "재사용"과 "B 안과의 궁합". 상시 스케줄이 필요해지는 시점(D 허가 이후)에 `@Scheduled` 를 얹으면 된다.

### 6-2. 필요한 라이브러리 후보

| 용도 | 후보 | 근거 |
|---|---|---|
| HTML 파싱 | **jsoup**(`org.jsoup:jsoup`, MIT) | 작품 페이지가 1MB·3만 태그 규모라 정규식 파싱은 깨지기 쉽다(§1-2 의 라벨 쪼개짐, 엔티티, hidden 스팬). CSS 셀렉터·`attr()` 엔티티 디코딩·관대한 파서가 필요. 의존성 없음. htmlunit/Selenium 은 JS 실행이 필요할 때만인데 **JS 없이 정적 HTML 로 전부 얻을 수 있음**을 확인했으므로 불필요 |
| 위키텍스트 파싱 | 없음(직접 구현) | `|키=값` 줄 단위 + `{{#fte:imslpfile ... }}` 블록 경계만 처리하면 됨 |
| HTTP | 기존 스택의 Spring `RestClient` 또는 JDK `HttpClient` | 새 도입 없음. 단 **gzip 수동 해제**(요청 없이도 `Content-Encoding: gzip` 을 보냄), **리다이렉트 자동 추적 끄기**(302 의 `Location` 을 직접 봐야 봇 게이트 판별), 쿠키 2개 수동 첨부, 타임아웃·Content-Length 검증 필요 |
| JSON | 기존 Jackson | API.ISCR / api.php / catpagejs 모두 표준 JSON |
| PDF 검증 | 없음 | 매직 바이트 `%PDF` + Content-Length 일치만. 쪽수는 HTML 의 `N pp.` 를 신뢰(다르면 관리자에게 표시) |

### 6-3. 설계 단계로 넘길 미확인 항목

1. 파일 호스트 요청에 쿠키/Referer 가 필수인지(이번엔 붙여서 성공. 빼고는 안 해봄 — 추가 수신 금지 조건 때문).
2. 악장별 헤딩이 있는 작품의 위키텍스트 헤딩 레벨(`====Complete====` 등) — HTML 로만 섹션을 판정하면 불필요.
3. 두 쿠키 없이 파일 호스트 URL 을 직접 치는 경우의 응답(미검증, 검증할 계획도 없음 — 우회 목적이 되므로).
4. 한국 저작권 판정(사후 70년 + 편집자 권리)은 §1-4 정규화 코드와 별개 컬럼으로 관리자가 입력.

---

## 부록. 이번 조사에서 보낸 요청 목록 (모두 200 또는 의도된 302)

robots.txt / 작품 페이지 3 / `Special:ImagefromIndex/00014` x3(쿠키 조합별) / friendlyredirect.html / friendlyredirect2.html(POST) / `IMSLPDisclaimerAccept/00014` / `IMSLPImageHandler/00014` / `IMSLPJS.D.js` / `/images/...pdf` HEAD / **PDF 1건(ks15, 1,059,957 B)** / `API.ISCR.php` type=2 start=0·1000, type=1 start=0 / `Category:For_piano` / sitemap index / `api.php` parse(sections·categories), parse(wikitext), query(imageinfo) / `ks15.imslp.org/robots.txt`. 응답 원문은 스크래치 폴더에만 저장했고 저장소에는 넣지 않았다.
