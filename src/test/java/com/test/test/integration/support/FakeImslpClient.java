package com.test.test.integration.support;

import com.test.test.sheetmusic.crawl.ImslpClient;
import com.test.test.sheetmusic.crawl.ImslpDownloadedFile;
import com.test.test.sheetmusic.crawl.ImslpPageNotFoundException;
import com.test.test.sheetmusic.crawl.ImslpUnavailableException;
import com.test.test.sheetmusic.crawl.ImslpWikitextPage;
import com.test.test.sheetmusic.crawl.ImslpWorkPage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.io.ClassPathResource;

/**
 * IMSLP 를 흉내 내는 테스트 대역 (03_기술결정 §3 "테스트 격리"). 실제 네트워크를 절대 치지 않는다.
 *
 * <h3>픽스처 ({@code src/test/resources/imslp/})</h3>
 * <table>
 *   <tr><th>정규 URL</th><th>HTML</th><th>위키텍스트</th><th>카테고리</th><th>기대 결과</th></tr>
 *   <tr><td>{@link #MOONLIGHT_URL}</td><td>moonlight.html (판본 블록 7개 = PDF 8개)</td><td>moonlight.wikitext</td><td>For_piano 포함</td><td>판본 8개, 파일 2개(#32718, #00014)</td></tr>
 *   <tr><td>{@link #ELISE_URL}</td><td>elise.html (블록 7개 = 파일 8개, 그중 .zip 1개·Sketches 탭 1개)</td><td>elise.wikitext (실제 원문)</td><td>For_piano 포함</td><td>판본 6개, 파일 2개(#103834, #05929)</td></tr>
 *   <tr><td>{@link #SYMPHONY5_URL}</td><td>symphony5.html (수작성, Instrumentation=orchestra)</td><td>symphony5.wikitext</td><td>For_orchestra</td><td>곡 숨김(NOT_PIANO_SOLO), 파일 0</td></tr>
 * </table>
 * 등록되지 않은 URL 은 {@link ImslpPageNotFoundException}. 모든 파일 다운로드는 {@code imslp/sample-crawl.pdf}(2쪽, 661바이트)를 복사한다.
 *
 * <h3>시나리오 제어</h3>
 * <ul>
 *   <li>{@link #markNotFound(String)} / {@link #markUnavailable(String)} / {@link #setAllUnavailable(boolean)} — URL 별·전체 장애</li>
 *   <li>{@link #markFileCorrupt(String)} — 해당 파일 ID 는 PDF 가 아닌 바이트를 준다 (→ FILE_DOWNLOAD_FAILED)</li>
 *   <li>{@link #holdFiles()} / {@link #releaseFiles()} — 파일 다운로드를 붙잡아 "처리 중" 상태(DOWNLOADING_FILE·QUEUED)를 관찰</li>
 *   <li>{@link #getDownloadedFileIds()} 등 — 요청 횟수 검증("파일당 1회", REFRESH 는 재요청 없음)</li>
 * </ul>
 * 지연은 전혀 없다. 요청 간격·파일 대기 설정({@code app.imslp.request-interval-ms}, {@code app.imslp.file-wait-ms})은
 * 워커 쪽 게이트에서 읽으므로 테스트 프로퍼티로 0 을 준다 ({@link CrawlTestSupport}).
 */
public class FakeImslpClient implements ImslpClient {

    public static final String MOONLIGHT_URL = "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)";
    public static final String ELISE_URL = "https://imslp.org/wiki/Für_Elise,_WoO_59_(Beethoven,_Ludwig_van)";
    public static final String SYMPHONY5_URL = "https://imslp.org/wiki/Symphony_No.5,_Op.67_(Beethoven,_Ludwig_van)";

    public static final String SAMPLE_PDF_RESOURCE = "imslp/sample-crawl.pdf";
    public static final int SAMPLE_PDF_PAGE_COUNT = 2;

    private record Fixture(String html, String wikitext, List<String> categories) {
    }

    private final Map<String, Fixture> fixtures = new ConcurrentHashMap<>();
    private final Set<String> notFoundUrls = ConcurrentHashMap.newKeySet();
    private final Set<String> unavailableUrls = ConcurrentHashMap.newKeySet();
    private final Set<String> corruptFileIds = ConcurrentHashMap.newKeySet();
    private final Set<String> brokenUrls = ConcurrentHashMap.newKeySet();
    private volatile boolean allUnavailable;

    private final AtomicInteger workPageRequests = new AtomicInteger();
    private final AtomicInteger wikitextRequests = new AtomicInteger();
    private final List<String> downloadedFileIds = Collections.synchronizedList(new ArrayList<>());
    private volatile CountDownLatch fileGate;

    public FakeImslpClient() {
        registerFixture(MOONLIGHT_URL, "moonlight");
        registerFixture(ELISE_URL, "elise");
        registerFixture(SYMPHONY5_URL, "symphony5");
    }

    /** 픽스처 3종을 {@code imslp/{name}.html|wikitext|categories.txt} 에서 읽어 등록한다. */
    public void registerFixture(String canonicalUrl, String name) {
        fixtures.put(canonicalUrl, new Fixture(
                readResource("imslp/" + name + ".html"),
                readResource("imslp/" + name + ".wikitext"),
                readResource("imslp/" + name + ".categories.txt").lines().map(String::trim).filter(s -> !s.isEmpty()).toList()));
    }

    /** 시나리오 설정·카운터를 모두 초기값으로. 테스트마다 {@code @BeforeEach} 에서 호출. */
    public void reset() {
        notFoundUrls.clear();
        unavailableUrls.clear();
        corruptFileIds.clear();
        brokenUrls.clear();
        allUnavailable = false;
        workPageRequests.set(0);
        wikitextRequests.set(0);
        downloadedFileIds.clear();
        releaseFiles();
    }

    public void markNotFound(String canonicalUrl) {
        notFoundUrls.add(canonicalUrl);
    }

    public void unmarkNotFound(String canonicalUrl) {
        notFoundUrls.remove(canonicalUrl);
    }

    public void markUnavailable(String canonicalUrl) {
        unavailableUrls.add(canonicalUrl);
    }

    public void setAllUnavailable(boolean value) {
        this.allUnavailable = value;
    }

    /**
     * 이 URL 의 작품 페이지 요청은 <b>예상 밖의</b> 런타임 예외를 던진다(우리 쪽 버그·파싱 사고 재현).
     * IMSLP 무응답({ #markUnavailable}) 과 구분하기 위한 것 — 02 §6-10 의 INTERNAL_ERROR 계약.
     */
    public void markBroken(String canonicalUrl) {
        brokenUrls.add(canonicalUrl);
    }

    public void markFileCorrupt(String imslpFileId) {
        corruptFileIds.add(imslpFileId);
    }

    /** 이후의 파일 다운로드는 {@link #releaseFiles()} 까지 블록된다. */
    public void holdFiles() {
        fileGate = new CountDownLatch(1);
    }

    public void releaseFiles() {
        CountDownLatch gate = fileGate;
        fileGate = null;
        if (gate != null) {
            gate.countDown();
        }
    }

    public int getWorkPageRequests() {
        return workPageRequests.get();
    }

    public int getWikitextRequests() {
        return wikitextRequests.get();
    }

    /** 요청 순서대로의 파일 ID (중복 포함). */
    public List<String> getDownloadedFileIds() {
        return List.copyOf(downloadedFileIds);
    }

    // ===== ImslpClient =====

    @Override
    public ImslpWorkPage fetchWorkPage(String canonicalUrl) {
        workPageRequests.incrementAndGet();
        if (brokenUrls.contains(canonicalUrl)) {
            throw new IllegalStateException("테스트용 예상 밖 오류");
        }
        Fixture fixture = resolve(canonicalUrl);
        return ImslpWorkPage.builder().canonicalUrl(canonicalUrl).html(fixture.html()).build();
    }

    @Override
    public ImslpWikitextPage fetchWikitext(String canonicalUrl) {
        wikitextRequests.incrementAndGet();
        Fixture fixture = resolve(canonicalUrl);
        return ImslpWikitextPage.builder()
                .canonicalUrl(canonicalUrl)
                .wikitext(fixture.wikitext())
                .categories(fixture.categories())
                .build();
    }

    @Override
    public ImslpDownloadedFile downloadFile(String imslpFileId, Path targetDirectory) {
        if (allUnavailable) {
            throw new ImslpUnavailableException("fake: IMSLP unavailable");
        }
        awaitGate();
        downloadedFileIds.add(imslpFileId);
        try {
            Files.createDirectories(targetDirectory);
            String fileName = "IMSLP" + imslpFileId + "-sample.pdf";
            Path target = targetDirectory.resolve(fileName);
            byte[] bytes = corruptFileIds.contains(imslpFileId)
                    ? "<html><body>Bot gate</body></html>".getBytes(StandardCharsets.UTF_8)
                    : new ClassPathResource(SAMPLE_PDF_RESOURCE).getContentAsByteArray();
            Files.write(target, bytes);
            return ImslpDownloadedFile.builder()
                    .imslpFileId(imslpFileId)
                    .fileName(fileName)
                    .contentType(corruptFileIds.contains(imslpFileId) ? "text/html" : "application/pdf")
                    .declaredSize(bytes.length)
                    .path(target)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ===== internals =====

    private Fixture resolve(String canonicalUrl) {
        String key = normalize(canonicalUrl);
        if (allUnavailable || unavailableUrls.contains(key)) {
            throw new ImslpUnavailableException("fake: IMSLP unavailable for " + key);
        }
        Fixture fixture = fixtures.get(key);
        if (fixture == null || notFoundUrls.contains(key)) {
            throw new ImslpPageNotFoundException(key);
        }
        return fixture;
    }

    /** 퍼센트 인코딩된 형태로 오더라도 같은 픽스처를 찾도록 디코딩해 비교한다. */
    private static String normalize(String url) {
        if (url == null) {
            return "";
        }
        return URLDecoder.decode(url.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private void awaitGate() {
        CountDownLatch gate = fileGate;
        if (gate == null) {
            return;
        }
        try {
            if (!gate.await(30, TimeUnit.SECONDS)) {
                throw new ImslpUnavailableException("fake: file gate held too long");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImslpUnavailableException("fake: interrupted while waiting for file gate", e);
        }
    }

    private static String readResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("픽스처를 읽을 수 없습니다: " + path, e);
        }
    }
}
