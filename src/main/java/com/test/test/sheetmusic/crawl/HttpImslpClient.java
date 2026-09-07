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
import java.util.zip.GZIPInputStream;
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
    public ImslpDownloadedFile downloadFile(String imslpFileId, Path targetDirectory) {
        String waitPage = getText(SITE + "/wiki/Special:ImagefromIndex/" + imslpFileId, READ_TIMEOUT, imslpFileId);
        Document document = Jsoup.parse(waitPage, SITE);
        Element marker = document.selectFirst("span#sm_dl_wait[data-id]");
        if (marker == null) {
            throw new ImslpUnavailableException("파일 대기 페이지를 읽지 못했습니다: " + imslpFileId);
        }
        String fileUrl = marker.attr("data-id");
        if (fileUrl.isBlank()) {
            throw new ImslpUnavailableException("파일 주소가 비어 있습니다: " + imslpFileId);
        }

        // 100MB 악보를 힙에 올리지 않는다 — 응답을 스트림으로 받아 임시 파일로 흘려보낸다.
        HttpResponse<InputStream> response = send(request(fileUrl, FILE_TIMEOUT),
                HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status != 200) {
            closeQuietly(response.body());
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
        if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
            // 봇 게이트(friendlyredirect) 로 튕긴 경우 — 쿠키를 보냈는데도 302 면 무응답으로 본다.
            throw new ImslpUnavailableException("리다이렉트로 막혔습니다(" + status + "): " + url);
        }
        if (status == 429 || status >= 500) {
            throw new ImslpUnavailableException("IMSLP 응답 상태 " + status + ": " + url);
        }
        if (status != 200) {
            throw new ImslpUnavailableException("예상치 못한 응답 상태 " + status + ": " + url);
        }
        byte[] body = decode(response.body(), response.headers().firstValue("content-encoding").orElse(null));
        return new String(body, StandardCharsets.UTF_8);
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
