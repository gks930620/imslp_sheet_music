package com.test.test;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * SPA 셸 서빙 (02_API_명세서 §0-5, 03_기술결정 §10).
 *
 * <p>경로를 열거하지 않는다 — 점(.)이 없는 세그먼트로만 이뤄진 경로가 API·인프라가 아니면 전부
 * {@code forward:/index.html}. 새 화면을 추가할 때 이 컨트롤러는 손대지 않는다.
 */
@Controller
public class HomeController {

    /** 첫 세그먼트 제외 목록 + 점이 든 세그먼트(정적 파일) 제외. */
    private static final String SPA_SEGMENT =
            "^(?!api|uploads|assets|images|ws-chat|h2-console|swagger-ui|v3|actuator|oauth2|custom-oauth2|healthz|error)[^.]*";

    @GetMapping({
            "/",
            "/{path:" + SPA_SEGMENT + "}",
            "/{path:" + SPA_SEGMENT + "}/**"
    })
    public String forward(HttpServletRequest request) throws NoResourceFoundException {
        String uri = request.getRequestURI();
        String lastSegment = uri.substring(uri.lastIndexOf('/') + 1);
        if (lastSegment.contains(".")) {
            // 점이 든 세그먼트는 정적 파일 — SPA 셸로 넘기지 않고 404 (02 §0-5)
            throw new NoResourceFoundException(HttpMethod.GET, uri);
        }
        return "forward:/index.html";
    }
}
