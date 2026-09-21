package com.test.test.sheetmusic.edition;

import com.test.test.jwt.model.CustomUserAccount;
import com.test.test.sheetmusic.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공개 다운로드 API (02 §3-4). */
@RestController
@RequestMapping("/api/editions")
@RequiredArgsConstructor
public class EditionController {

    /** RFC 5987 attr-char (영숫자 + 아래 기호)는 그대로, 나머지는 UTF-8 퍼센트 인코딩. */
    private static final String ATTR_CHARS = "!#$&+-.^_`|~";

    private final DownloadService downloadService;

    /**
     * PDF 다운로드. <b>HEAD 로도 부른다</b> — 화면이 "받을 수 있는지" 를 먼저 묻는 경로다 (02 §3-4, 03 §12).
     *
     * <p>{@code <a href download>} 는 실패를 감지할 수 없어서, 화면은 클릭과 함께 같은 주소로 HEAD 를 한 번 보낸다.
     * 계약은 GET 과 <b>같은 상태코드·헤더</b> 이되 <b>다운로드 수를 올리지 않는 것</b>이다
     * (사전 확인이 카운터를 부풀리면 한 번 받을 때 2 가 된다).
     *
     * <p>HEAD 전용 매핑을 따로 두지 않는 이유: 스프링은 HEAD 를 GET 핸들러로 보내므로
     * 별도 {@code @RequestMapping(method = HEAD)} 를 두면 같은 경로에 매핑이 겹친다.
     * 그래서 한 핸들러 안에서 메서드로만 가른다.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, HttpServletRequest request,
                                             @AuthenticationPrincipal CustomUserAccount account) {
        DownloadService.DownloadFile file = downloadService.prepare(id);

        if (!HttpMethod.HEAD.matches(request.getMethod())) {
            // 주체가 있으면 "받은 악보" 에도 남는다(01_ERD §3-12). 비로그인이면 null — 집계에만 든다.
            downloadService.recordDownload(id, CurrentUser.idOrNull(account));
        }

        String disposition = "attachment; filename=\"score-" + id + ".pdf\"; filename*=UTF-8''"
                + rfc5987(file.getFileName());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.getContentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(file.getResource());
    }

    private static String rfc5987(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            boolean attrChar = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || ATTR_CHARS.indexOf(c) >= 0;
            if (attrChar) {
                encoded.append((char) c);
            } else {
                encoded.append('%').append(String.format("%02X", c));
            }
        }
        return encoded.toString();
    }
}
