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
 *   <li>{@code HttpImslpClient} — JDK HttpClient, 요청 간격 2초(app.imslp.request-interval-ms), 파일 대기 15초(app.imslp.file-wait-ms),
 *       단일 커넥션, gzip 수동 해제, 봇 게이트 쿠키(redirectPassed=1, imslpdisclaimeraccepted=yes), UA 에 서비스명+연락처.</li>
 * </ul>
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
     * IMSLP 파일 1개를 받아 {@code targetDirectory} 아래 임시 파일로 저장한다.
     * ({@code Special:ImagefromIndex/{id}} → 대기 페이지 {@code span#sm_dl_wait[data-id]} → 15초 대기 → 파일 호스트)
     *
     * <p>받은 바이트가 PDF 인지({@code %PDF} 매직바이트, Content-Length 일치)는 <b>호출자가 검증</b>한다 —
     * 테스트 대역이 손상 파일을 흉내 낼 수 있어야 하기 때문. 검증 실패는 항목 FAILED / FILE_DOWNLOAD_FAILED.
     *
     * @param imslpFileId     IMSLP 파일 번호 원문 (앞자리 0 포함, 예: {@code "00014"})
     * @param targetDirectory 임시 파일을 만들 디렉터리 (호출자가 정리)
     */
    ImslpDownloadedFile downloadFile(String imslpFileId, Path targetDirectory);
}
