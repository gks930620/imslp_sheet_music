package com.test.test.sheetmusic.crawl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.test.sheetmusic.common.ImslpUrlNormalizer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 실제 IMSLP 통신 (00 조사 §1·§2, 03 §3).
 *
 * <ul>
 *   <li>리다이렉트 자동 추적을 끈다 — 봇 게이트 302 를 직접 판별해야 한다.</li>
 *   <li>gzip 을 수동 해제한다 — 요청하지 않아도 squid 가 gzip 으로 준다.</li>
 *   <li>봇 게이트 쿠키 {@code redirectPassed=1} + {@code imslpdisclaimeraccepted=yes} 를 명시 헤더로 보낸다.</li>
 *   <li>최종 파일 URL 은 조립하지 않고 {@code span#sm_dl_wait[data-id]} 를 읽는다(호스트가 요청마다 바뀐다).</li>
 * </ul>
 *
 * <p>통합테스트는 이 구현을 쓰지 않는다({@code FakeImslpClient} 가 {@code @Primary}) — 실제 네트워크를 치는 테스트는 없다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HttpImslpClient implements ImslpClient {

    private static final String SITE = "https://imslp.org";
    private static final String COOKIES = "redirectPassed=1; imslpdisclaimeraccepted=yes";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration FILE_TIMEOUT = Duration.ofSeconds(300);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 파일 다운로드는 HTTP 를 2회 친다(대기 페이지 → 파일 호스트) — 그 사이 간격도 게이트로 지킨다. */
    private final ImslpGate imslpGate;

    @Value("${app.imslp.user-agent:SheetMusicKR/0.1}")
    private String userAgent;

    @Override
    public ImslpWorkPage fetchWorkPage(String canonicalUrl) {
        String title = ImslpUrlNormalizer.titleOf(canonicalUrl);
        if (title == null) {
            throw new ImslpPageNotFoundException(canonicalUrl);
        }
        String html = getText(SITE + "/wiki/" + encodePathSegment(title), READ_TIMEOUT, canonicalUrl);
        return ImslpWorkPage.builder().canonicalUrl(canonicalUrl).html(html).build();
    }

    @Override
    public ImslpWikitextPage fetchWikitext(String canonicalUrl) {
        String title = ImslpUrlNormalizer.titleOf(canonicalUrl);
        if (title == null) {
            throw new ImslpPageNotFoundException(canonicalUrl);
        }
        String url = SITE + "/api.php?action=parse&prop=wikitext%7Ccategories&format=json&page="
                + URLEncoder.encode(title, StandardCharsets.UTF_8);
        String json = getText(url, READ_TIMEOUT, canonicalUrl);
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("error")) {
                throw new ImslpPageNotFoundException(canonicalUrl);
            }
            JsonNode parse = root.path("parse");
            String wikitext = parse.path("wikitext").path("*").asText("");
            List<String> categories = new ArrayList<>();
            for (JsonNode category : parse.path("categories")) {
                String name = category.path("*").asText(null);
                if (name != null && !name.isBlank()) {
                    categories.add(name);
                }
            }
            return ImslpWikitextPage.builder()
                    .canonicalUrl(canonicalUrl)
                    .wikitext(wikitext)
                    .categories(categories)
                    .build();
        } catch (IOException e) {
            throw new ImslpUnavailableException("위키텍스트 응답을 읽지 못했습니다: " + canonicalUrl, e);
        }
    }

    @Override
    public ImslpFileLocation resolveFileUrl(String imslpFileId) {
        String url = SITE + "/wiki/Special:ImagefromIndex/" + imslpFileId;
        HttpResponse<byte[]> response = send(request(url, READ_TIMEOUT), HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status == 404) {
            throw new ImslpPageNotFoundException(imslpFileId);
        }
        if (isRedirectStatus(status)) {
            String location = response.headers().firstValue("location").orElse(null);
            if (isFileHostRedirect(location)) {
                // 대기 페이지를 생략한 정상 파일 리다이렉트 — HTML 파싱도, Location 재요청도 하지 않는다.
                // 실제 파일 GET 은 downloadResolvedFile() 이 1회만 한다(무한 루프 방지, 1홉 고정 설계).
                return ImslpFileLocation.builder().imslpFileId(imslpFileId).fileUrl(location).build();
            }
            logGateRedirect(status, url, response);
            throw new ImslpUnavailableException("리다이렉트로 막혔습니다(" + status + "): " + url);
        }
        String waitPage = decodeStatusOkBody(response, url);
        Document document = Jsoup.parse(waitPage, SITE);
        Element marker = document.selectFirst("span#sm_dl_wait[data-id]");
        if (marker == null) {
            // 200 인데 카운트다운이 없다 = 게이트/점검/차단 안내 페이지일 수 있다. 본문 전체는 남기지 않고
            // 길이와 제목만 남긴다(로그 폭주·불필요한 내용 저장 방지).
            log.warn("파일 대기 페이지에 sm_dl_wait 가 없습니다 - imslpFileId: {}, 길이: {}, title: {}",
                    imslpFileId, waitPage.length(), document.title());
            throw new ImslpUnavailableException("파일 대기 페이지를 읽지 못했습니다: " + imslpFileId);
        }
        String fileUrl = marker.attr("data-id");
        if (fileUrl.isBlank()) {
            throw new ImslpUnavailableException("파일 주소가 비어 있습니다: " + imslpFileId);
        }
        // 여기서 카운트다운이 시작된다 — 15초는 호출부(EditionFileFetcher.downloadAndStore)가
        // 이 메서드가 돌아온 "다음" 에 건다. 여기에 넣으면 호출부 대기와 겹쳐 파일당 30초가 된다.
        return ImslpFileLocation.builder().imslpFileId(imslpFileId).fileUrl(fileUrl).build();
    }

    @Override
    public ImslpDownloadedFile downloadResolvedFile(ImslpFileLocation location, Path targetDirectory) {
        String imslpFileId = location.getImslpFileId();
        String fileUrl = location.getFileUrl();

        // 다음 요청과의 최소 간격(robots Crawl-delay: 2)을 채우고 lastRequestAt 을 이 요청 시각으로 갱신한다.
        // 호출부의 파일 대기(15초)가 앞에 있으므로 여기서 실제로 자는 시간은 0 이다 (03 §3).
        imslpGate.awaitRequestSlot();

        // 100MB 악보를 힙에 올리지 않는다 — 응답을 스트림으로 받아 임시 파일로 흘려보낸다.
        HttpResponse<InputStream> response = send(request(fileUrl, FILE_TIMEOUT),
                HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status != 200) {
            closeQuietly(response.body());
            log.warn("파일 호스트 응답 비정상 - imslpFileId: {}, 상태: {}, host: {}, location: {}",
                    imslpFileId, status, hostOf(fileUrl),
                    response.headers().firstValue("location").orElse("-"));
            if (status == 404) {
                throw new ImslpPageNotFoundException(fileUrl);
            }
            throw new ImslpUnavailableException("파일 응답 상태 " + status + ": " + fileUrl);
        }

        // 저장 파일명은 우리가 만든다 — 원격 값(구분자·`..`·쿼리스트링)이 경로에 섞이면 디렉터리를 벗어날 수 있다.
        boolean gzipped = isGzip(response.headers().firstValue("content-encoding").orElse(null));
        Path target = targetDirectory.resolve("IMSLP" + imslpFileId + ".pdf");
        long written;
        try {
            Files.createDirectories(targetDirectory);
            try (InputStream body = response.body();
                 InputStream in = gzipped ? new GZIPInputStream(body) : body) {
                written = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ImslpUnavailableException("받은 파일을 저장하지 못했습니다: " + fileUrl, e);
        }

        log.info("IMSLP 파일 수신 완료 - imslpFileId: {}, host: {}, {}바이트", imslpFileId, hostOf(fileUrl), written);

        // gzip 이면 Content-Length 는 압축 크기라 실제 바이트 수와 비교할 수 없다 → 받은 크기를 그대로 쓴다.
        long declaredSize = gzipped
                ? written
                : response.headers().firstValueAsLong("content-length").orElse(written);
        return ImslpDownloadedFile.builder()
                .imslpFileId(imslpFileId)
                .fileName(displayFileName(fileUrl, imslpFileId))
                .contentType(response.headers().firstValue("content-type").orElse(null))
                .declaredSize(declaredSize)
                .path(target)
                .build();
    }

    // ===== 내부 =====

    private String getText(String url, Duration timeout, String contextForNotFound) {
        HttpResponse<byte[]> response = send(request(url, timeout), HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status == 404) {
            throw new ImslpPageNotFoundException(contextForNotFound);
        }
        if (isRedirectStatus(status)) {
            // 봇 게이트(friendlyredirect) 로 튕긴 경우 — 쿠키를 보냈는데도 302 면 무응답으로 본다.
            // 어디로 튕겼는지(Location)가 게이트 종류를 가르는 유일한 단서다.
            // (resolveFileUrl() 과 달리 여기서는 3xx 를 무조건 게이트로 본다 — 위키 페이지가 파일
            // 호스트로 튈 이유가 없다.)
            logGateRedirect(status, url, response);
            throw new ImslpUnavailableException("리다이렉트로 막혔습니다(" + status + "): " + url);
        }
        return decodeStatusOkBody(response, url);
    }

    private static boolean isRedirectStatus(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private void logGateRedirect(int status, String url, HttpResponse<byte[]> response) {
        log.warn("IMSLP 리다이렉트 - 상태: {}, url: {}, location: {}",
                status, url, response.headers().firstValue("location").orElse("-"));
    }

    /** 3xx·404 를 이미 걸러낸 뒤의 상태 분기(429/5xx/기타/200) — {@code getText()}·{@code resolveFileUrl()} 공용. */
    private String decodeStatusOkBody(HttpResponse<byte[]> response, String url) {
        int status = response.statusCode();
        if (status == 429 || status >= 500) {
            log.warn("IMSLP 응답 상태 - 상태: {}, url: {}, retry-after: {}",
                    status, url, response.headers().firstValue("retry-after").orElse("-"));
            throw new ImslpUnavailableException("IMSLP 응답 상태 " + status + ": " + url);
        }
        if (status != 200) {
            throw new ImslpUnavailableException("예상치 못한 응답 상태 " + status + ": " + url);
        }
        byte[] body = decode(response.body(), response.headers().firstValue("content-encoding").orElse(null));
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * 3xx 응답의 {@code Location} 이 "따라가야 할 파일 리다이렉트"인지 판별한다(게이트면 {@code false}).
     *
     * <p>{@code resolveFileUrl()} 이 {@code Special:ImagefromIndex/{id}} 를 호출했을 때 오는 3xx 를
     * 이 함수로 가른다. 실측(운영 로그, 2026-09-22)으로는 쿠키 2개를 보냈는데도 302 로 <b>정상 파일
     * 호스트</b>(예: {@code s9.imslp.org/files/...})를 바로 가리키는 응답이 온다 — {@code data-id} 로
     * 얻을 값과 같은 모양의 주소가 대기 페이지 없이 바로 온 것이다. {@code true} 면 그 Location 을 그대로
     * {@link ImslpFileLocation#getFileUrl()} 로 써서 반환한다(HTML 파싱 없음, 재요청 없음 — 실제 파일
     * GET 은 기존처럼 {@code downloadResolvedFile()} 이 1회만 한다). {@code false} 면 경고 로그를 남기고
     * {@code ImslpUnavailableException}.
     *
     * <p>판별 규칙(근거: 운영 로그 + {@code docs/설계/00_IMSLP_수집_조사.md} §2-1 실측):
     * <ul>
     *   <li>{@code Location} 이 {@code http}/{@code https} 절대 URL 이고,</li>
     *   <li>호스트가 {@code imslp.org} 의 서브도메인이다 — 바로 그 {@code imslp.org} 본체는 <b>제외</b>
     *       (본체로의 튕김은 위키 페이지·면책 페이지로의 게이트다). 특정 서브도메인을 나열하지 않는다 —
     *       파일 호스트는 요청마다 바뀐다({@code s9}·{@code s3}·{@code ks15}·{@code vmirror}…, 03 §2-1)
     *       → 접미사 {@code .imslp.org} 검사만으로 가른다(단순 문자열 포함이 아니라 호스트 라벨 경계 기준 —
     *       {@code evil-imslp.org}·{@code s9.imslp.org.evil.com} 같은 위조 호스트는 걸리지 않아야 한다),</li>
     *   <li>경로가 {@code /files/} 로 시작한다(문서화된 파일 URL 패턴 {@code /files/imglnks/usimg/...}).</li>
     * </ul>
     * 셋 다 만족해야 {@code true}. 그 밖(상대경로 {@code /friendlyredirect.html} 류, 다른 도메인, 본체
     * {@code imslp.org}, {@code /files/} 가 아닌 경로, null/빈 문자열, 파싱 불가 문자열)은 {@code false}.
     */
    static boolean isFileHostRedirect(String location) {
        if (location == null || location.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(location);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        if (!host.toLowerCase(Locale.ROOT).endsWith(".imslp.org")) {
            return false;
        }
        String path = uri.getPath();
        return path != null && path.startsWith("/files/");
    }

    private HttpRequest request(String url, Duration timeout) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept-Encoding", "gzip")
                .header("Cookie", COOKIES)
                .GET()
                .build();
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return httpClient.send(request, handler);
        } catch (IOException e) {
            throw new ImslpUnavailableException("IMSLP 요청 실패: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImslpUnavailableException("IMSLP 요청이 중단됐습니다: " + request.uri(), e);
        }
    }

    /** 로그용 — 파일 호스트는 요청마다 바뀐다(s9·ks15…). 전체 URL 은 길어서 호스트만 남긴다. */
    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "-" : host;
        } catch (IllegalArgumentException e) {
            return "-";
        }
    }

    private static boolean isGzip(String contentEncoding) {
        return contentEncoding != null && contentEncoding.toLowerCase().contains("gzip");
    }

    private static void closeQuietly(InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
            // 실패 응답 본문 닫기 실패는 무시
        }
    }

    /** JDK HttpClient 는 gzip 을 자동 해제하지 않는다 (00 조사 §1). */
    private byte[] decode(byte[] body, String contentEncoding) {
        if (!isGzip(contentEncoding)) {
            return body;
        }
        try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(body));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copy(in, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ImslpUnavailableException("gzip 응답을 풀지 못했습니다", e);
        }
    }

    private void copy(InputStream in, ByteArrayOutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static String encodePathSegment(String title) {
        return URLEncoder.encode(title, StandardCharsets.UTF_8)
                .replace("+", "_")
                .replace("%2F", "/")
                .replace("%3A", ":")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%2C", ",");
    }

    /**
     * 표시용 원본 파일명 — <b>경로를 만드는 데 쓰지 않는다</b>(저장 파일명은 {@code IMSLP{id}.pdf}).
     * 원격이 준 값이라 쿼리스트링·프래그먼트를 떼고, 경로 구분자({@code / \})와 {@code ..} 가 있으면 통째로 버린다.
     */
    private static String displayFileName(String url, String imslpFileId) {
        String fallback = "IMSLP" + imslpFileId + ".pdf";
        if (url == null) {
            return fallback;
        }
        String name = url;
        int query = name.indexOf('?');
        if (query >= 0) {
            name = name.substring(0, query);
        }
        int fragment = name.indexOf('#');
        if (fragment >= 0) {
            name = name.substring(0, fragment);
        }
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = separator >= 0 ? name.substring(separator + 1) : name;
        if (name.isBlank() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return fallback;
        }
        return name;
    }
}
