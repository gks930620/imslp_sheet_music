package com.test.test.integration.support;

import com.test.test.sheetmusic.crawl.ImslpClient;
import com.test.test.sheetmusic.crawl.ImslpDownloadedFile;
import com.test.test.sheetmusic.crawl.ImslpFileLocation;
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
 *   <tr><td>{@link #BACH_INVENTIONS_URL}</td><td>bach_inventions.html (수작성, Instrumentation=harpsichord, PDF 2개)</td><td>bach_inventions.wikitext</td><td>For_harpsichord + For_piano_(arr)</td><td>판본 2개(#02001 PD·#02002 CC BY-SA), <b>숨기지 않는다</b>(02 §6-11)</td></tr>
 * </table>
 * 픽스처는 {@link #reset()} 마다 기본값으로 되돌아간다 — 테스트가 {@link #registerFixture} 로 바꿔도 다음 테스트에 새지 않는다.
 * 등록되지 않은 URL 은 {@link ImslpPageNotFoundException}. 모든 파일 다운로드는 {@code imslp/sample-crawl.pdf}(2쪽, 661바이트)를 복사한다.
 *
 * <h3>시나리오 제어</h3>
 * <ul>
 *   <li>{@link #markNotFound(String)} / {@link #markUnavailable(String)} / {@link #setAllUnavailable(boolean)} — URL 별·전체 장애</li>
 *   <li>{@link #markFileUnavailable(String)} — 그 파일 ID 의 <b>파일 호스트</b> 요청만 ImslpUnavailableException</li>
 *   <li>{@link #markFileWaitPageUnavailable(String)} — 그 파일 ID 의 <b>대기 페이지</b> 요청이 무응답 (봇 게이트 302)</li>
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
    public static final String BACH_INVENTIONS_URL = "https://imslp.org/wiki/15_Inventions,_BWV_772-786_(Bach,_Johann_Sebastian)";

    public static final String SAMPLE_PDF_RESOURCE = "imslp/sample-crawl.pdf";
    public static final int SAMPLE_PDF_PAGE_COUNT = 2;

    private record Fixture(String html, String wikitext, List<String> categories) {
    }

    private final Map<String, Fixture> fixtures = new ConcurrentHashMap<>();
    private final Set<String> notFoundUrls = ConcurrentHashMap.newKeySet();
    private final Set<String> unavailableUrls = ConcurrentHashMap.newKeySet();
    private final Set<String> corruptFileIds = ConcurrentHashMap.newKeySet();
    private final Set<String> brokenUrls = ConcurrentHashMap.newKeySet();
    private final Set<String> unavailableFileIds = ConcurrentHashMap.newKeySet();
    private final Set<String> waitPageUnavailableFileIds = ConcurrentHashMap.newKeySet();
    private volatile boolean allUnavailable;

    private final AtomicInteger workPageRequests = new AtomicInteger();
    private final AtomicInteger wikitextRequests = new AtomicInteger();
    private final List<String> resolvedFileIds = Collections.synchronizedList(new ArrayList<>());
    private final List<String> downloadedFileIds = Collections.synchronizedList(new ArrayList<>());
    private volatile CountDownLatch fileGate;

    private final ImslpCallLog callLog;

    public FakeImslpClient(ImslpCallLog callLog) {
        this.callLog = callLog;
        registerDefaultFixtures();
    }

    /** 기본 픽스처 4종. {@link #reset()} 이 매번 다시 등록하므로 테스트가 픽스처를 바꿔도 다음 테스트에 새지 않는다. */
    private void registerDefaultFixtures() {
        registerFixture(MOONLIGHT_URL, "moonlight");
        registerFixture(ELISE_URL, "elise");
        registerFixture(SYMPHONY5_URL, "symphony5");
        registerFixture(BACH_INVENTIONS_URL, "bach_inventions");
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
        fixtures.clear();
        registerDefaultFixtures();
        notFoundUrls.clear();
        unavailableUrls.clear();
        corruptFileIds.clear();
        brokenUrls.clear();
        unavailableFileIds.clear();
        waitPageUnavailableFileIds.clear();
        allUnavailable = false;
        workPageRequests.set(0);
        wikitextRequests.set(0);
        resolvedFileIds.clear();
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

    /** 파일 <b>호스트</b> 단계만 무응답 — 대기 페이지는 정상이라 카운트다운까지는 정상으로 흐른다. */
    public void markFileUnavailable(String imslpFileId) {
        unavailableFileIds.add(imslpFileId);
    }

    /**
     * <b>대기 페이지</b> 단계에서 무응답 — 봇 게이트 302·카운트다운 없는 안내 페이지를 흉내 낸다.
     * 이때는 파일 주소 자체가 없으므로 15초를 태우지도, 파일을 치지도 않아야 한다(03 §3).
     */
    public void markFileWaitPageUnavailable(String imslpFileId) {
        waitPageUnavailableFileIds.add(imslpFileId);
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

    /** 대기 페이지에서 주소까지 읽어낸 파일 ID (파일을 실제로 받았는지와는 별개). */
    public List<String> getResolvedFileIds() {
        return List.copyOf(resolvedFileIds);
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

    /**
     * 대기 페이지 단계 — 파일 주소만 돌려주고 <b>기다리지 않는다</b>(15초는 호출부의 게이트가 건다).
     * {@link #markFileWaitPageUnavailable(String)} 로 이 단계만 골라 무응답을 만들 수 있다.
     */
    @Override
    public ImslpFileLocation resolveFileUrl(String imslpFileId) {
        callLog.recordResolve(imslpFileId);
        if (allUnavailable || waitPageUnavailableFileIds.contains(imslpFileId)) {
            throw new ImslpUnavailableException("fake: 대기 페이지 무응답");
        }
        resolvedFileIds.add(imslpFileId);
        return ImslpFileLocation.builder()
                .imslpFileId(imslpFileId)
                .fileUrl("https://fake.imslp.test/files/IMSLP" + imslpFileId + "-sample.pdf")
                .build();
    }

    @Override
    public ImslpDownloadedFile downloadResolvedFile(ImslpFileLocation location, Path targetDirectory) {
        String imslpFileId = location.getImslpFileId();
        callLog.recordDownload(imslpFileId);
        if (allUnavailable || unavailableFileIds.contains(imslpFileId)) {
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
