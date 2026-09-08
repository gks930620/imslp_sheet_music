package com.test.test.sheetmusic.crawl;

import java.nio.file.Path;

/**
 * IMSLP 와의 HTTP 통신 경계 (03_기술결정 §3 "테스트 격리").
 *
 * <p>이 인터페이스는 <b>네트워크만</b> 담당한다. 파싱(jsoup·위키텍스트)·검증(매직바이트)·저장은 호출자(워커/파서)의 일이다.
 * 그래야 통합테스트가 {@code FakeImslpClient} 로 픽스처 문자열·샘플 PDF 를 돌려주는 것만으로 워커 흐름 전체를 검증할 수 있다.
 *
 * <p>구현체(백엔드 담당):
 * <ul>
 *   <li>{@code HttpImslpClient} — JDK HttpClient, 요청 간격 2초(app.imslp.request-interval-ms),
 *       단일 커넥션, gzip 수동 해제, 봇 게이트 쿠키(redirectPassed=1, imslpdisclaimeraccepted=yes), UA 에 서비스명+연락처.
 *       <b>파일 대기 15초는 구현체가 걸지 않는다</b> — 호출자({@code EditionFileFetcher})가 두 메서드 사이에 건다.</li>
 * </ul>
 *
 * <p><b>파일 1개를 받는 순서는 계약이다</b> (기획 §9-1, 03 §3) —
 * {@link #resolveFileUrl(String)}(대기 페이지 GET) → {@code ImslpGate.awaitFileWait()}(카운트다운) →
 * {@link #downloadResolvedFile(ImslpFileLocation, Path)}(파일 GET). 브라우저와 같은 순서다.
 * 대기를 대기 페이지 <b>앞</b>에 두면 카운트다운을 띄운 뒤 0초 만에 파일을 치는 셈이라, 서버에는 정확히
 * "카운트다운을 안 기다린 클라이언트"로 보인다. 그래서 이 인터페이스는 두 단계를 굳이 나눠 둔다 —
 * 한 메서드 안에 감추면 대기 자리를 통합테스트로 관찰할 수 없다({@code ImslpFileWaitPolicyIntegrationTest}).
 *
 * <p>예외 계약 (워커가 항목 상태로 옮긴다 — 02 §6-10):
 * <ul>
 *   <li>{@link ImslpPageNotFoundException} — 404. 항목 FAILED / PAGE_NOT_FOUND</li>
 *   <li>{@link ImslpUnavailableException} — 타임아웃·5xx·429·봇 게이트 302 반복. 항목 FAILED / IMSLP_UNAVAILABLE, 연속 3항목이면 작업 PAUSED</li>
 * </ul>
 */
public interface ImslpClient {

    /**
     * 작품 페이지 HTML 을 그대로 돌려준다.
     *
     * @param canonicalUrl 02 §6-1 규칙으로 정규화된 작품 페이지 주소
     *                     (예: {@code https://imslp.org/wiki/Für_Elise,_WoO_59_(Beethoven,_Ludwig_van)} — 비ASCII 는 디코딩된 상태)
     */
    ImslpWorkPage fetchWorkPage(String canonicalUrl);

    /**
     * MediaWiki {@code api.php?action=parse&prop=wikitext|categories} 결과.
     * 위키텍스트 원문과 카테고리 목록(예: {@code For_piano})을 돌려준다.
     *
     * @param canonicalUrl {@link #fetchWorkPage(String)} 와 같은 정규 주소. 페이지 제목 추출은 구현체가 한다.
     */
    ImslpWikitextPage fetchWikitext(String canonicalUrl);

    /**
     * <b>1단계 — 대기 페이지를 열어 파일 주소를 읽는다.</b>
     * {@code GET Special:ImagefromIndex/{id}} → {@code span#sm_dl_wait[data-id]}.
     *
     * <p>여기서 <b>기다리지 않는다</b>. 이 메서드가 돌아온 순간이 카운트다운의 시작점이고,
     * 15초를 재우는 것은 호출자({@code EditionFileFetcher.downloadAndStore})의 일이다.
     * 구현체가 안에서 자면 호출자의 대기와 겹쳐 파일당 30초가 된다.
     *
     * <p>대기 페이지가 200 인데 {@code sm_dl_wait} 가 없으면(봇 게이트·점검 안내) {@link ImslpUnavailableException} —
     * 이때 호출자는 카운트다운을 태우지 않고 그 파일을 포기한다.
     *
     * @param imslpFileId IMSLP 파일 번호 원문 (앞자리 0 포함, 예: {@code "00014"})
     */
    ImslpFileLocation resolveFileUrl(String imslpFileId);

    /**
     * <b>2단계 — 해석된 주소에서 파일을 받아</b> {@code targetDirectory} 아래 임시 파일로 저장한다.
     * 호출자가 {@code awaitFileWait()} 로 카운트다운을 채운 <b>뒤</b> 부른다.
     *
     * <p>받은 바이트가 PDF 인지({@code %PDF} 매직바이트, Content-Length 일치)는 <b>호출자가 검증</b>한다 —
     * 테스트 대역이 손상 파일을 흉내 낼 수 있어야 하기 때문. 검증 실패는 항목 FAILED / FILE_DOWNLOAD_FAILED.
     *
     * @param location        {@link #resolveFileUrl(String)} 가 방금 돌려준 1회용 주소
     * @param targetDirectory 임시 파일을 만들 디렉터리 (호출자가 정리)
     */
    ImslpDownloadedFile downloadResolvedFile(ImslpFileLocation location, Path targetDirectory);
}
