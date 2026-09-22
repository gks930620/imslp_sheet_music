package com.test.test.sheetmusic.crawl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HttpImslpClient#isFileHostRedirect(String)} — 302 봇 게이트 대 정상 파일 리다이렉트 판별 (TDD Red).
 *
 * <p><b>실측 결함</b>(운영 로그 {@code server-8104.log}, 2026-09-22): {@code Special:ImagefromIndex/{id}}
 * 를 쿠키 2개(정상 조합)로 호출했는데도 302 가 오고, {@code Location} 은 정상 파일 호스트
 * ({@code https://s9.imslp.org/files/imglnks/...}) 다. 지금 {@code HttpImslpClient.getText()} 는
 * 이런 3xx 도 전부 봇 게이트로 보고 {@code ImslpUnavailableException} 을 던진다 — 받을 수 있는 파일을
 * 계속 놓치는 원인. 이 테스트는 {@code isFileHostRedirect()} 가 아직 항상 {@code false} 인 스텁이라
 * "파일 리다이렉트" 케이스에서 실패한다({@code false} 여야 할 게이트 케이스는 우연히 통과한다 — 스텁 상태의
 * 정상적인 모습이다). backend-dev 가 판별 로직을 채우면 전부 통과해야 한다.
 *
 * <p>실 네트워크를 치지 않는다(컨벤션 §6) — {@code Location} 문자열만으로 판별하는 순수 함수 테스트다.
 */
class HttpImslpClientFileRedirectTest {

    @Test
    @DisplayName("운영 로그 실측: s9.imslp.org/files/... 302 는 파일 리다이렉트다 — 따라가야 한다")
    void observedProductionRedirect_isFileHostRedirect() {
        assertThat(HttpImslpClient.isFileHostRedirect(
                "https://s9.imslp.org/files/imglnks/usimg/1/1a/IMSLP06644-Some_Work.pdf"))
                .isTrue();
    }

    @ParameterizedTest(name = "[{index}] {0} → 파일 리다이렉트(true)")
    @DisplayName("00_IMSLP_수집_조사 §2-1 실측: 파일 호스트는 요청마다 바뀐다(ks15/vmirror/s3…) — 접미사로만 가른다")
    @ValueSource(strings = {
            "https://ks15.imslp.org/files/imglnks/usimg/a/a8/IMSLP00014-Beethoven,_L.v._-_Piano_Sonata_14.pdf",
            "https://vmirror.imslp.org/files/imglnks/usimg/a/a8/IMSLP00014-Beethoven.pdf",
            "https://s3.imslp.org/files/imglnks/usimg/2/2b/IMSLP12345-Foo.pdf",
            "http://ks3.imslp.org/files/imglnks/usimg/x/xy/IMSLP1.pdf",
            "HTTPS://S9.IMSLP.ORG/files/imglnks/usimg/1/1a/IMSLP1.pdf",
    })
    void knownFileHostPatterns_areFileHostRedirects(String location) {
        assertThat(HttpImslpClient.isFileHostRedirect(location)).isTrue();
    }

    @ParameterizedTest(name = "[{index}] {0} → 게이트(false)")
    @DisplayName("봇 게이트·면책 흐름 302 는 여전히 게이트다 (00_IMSLP_수집_조사 §2-1 1번·3번·4번 행)")
    @ValueSource(strings = {
            // 1번 행: 쿠키 없이 치면 상대경로 friendlyredirect.html 로 튕긴다
            "/friendlyredirect.html#/wiki/Special:ImagefromIndex/00014",
            // 3번 행: 면책 수락 302 는 imslp.org 본체(위키)로 간다 — 파일 호스트가 아니다
            "http://imslp.org/wiki/Special:IMSLPImageHandler/00014",
            "https://imslp.org/wiki/Special:ImagefromIndex/00014",
    })
    void gateAndDisclaimerRedirects_areNotFileHostRedirects(String location) {
        assertThat(HttpImslpClient.isFileHostRedirect(location)).isFalse();
    }

    @ParameterizedTest(name = "[{index}] {0} → 게이트(false), 예외 없이")
    @DisplayName("위조·경계 케이스: 호스트 접미사만 보는 순진한 구현이 속으면 안 된다")
    @ValueSource(strings = {
            // 본체 imslp.org 에 /files/ 경로가 와도 파일 호스트로 보지 않는다 (파일 호스트는 항상 서브도메인)
            "https://imslp.org/files/imglnks/usimg/a/a8/IMSLP00014.pdf",
            // 올바른 서브도메인이지만 경로가 /files/ 가 아니다
            "https://s9.imslp.org/wiki/Special:ImagefromIndex/00014",
            // 문자열 포함(contains)만으로 가르면 속는 위조 호스트 — 접미사 경계를 봐야 한다
            "https://s9.imslp.org.evil.com/files/imglnks/usimg/a/a8/IMSLP1.pdf",
            "https://evil-imslp.org/files/imglnks/usimg/a/a8/IMSLP1.pdf",
            "https://not-imslp.org/files/imglnks/usimg/a/a8/IMSLP1.pdf",
            // http/https 가 아닌 스킴
            "ftp://s9.imslp.org/files/imglnks/usimg/a/a8/IMSLP1.pdf",
            // 파싱 자체가 불가능한 문자열 — 예외를 던지면 안 된다(호출부가 그 예외를 못 받는다)
            "not a url at all",
            "https://",
    })
    void spoofedOrMalformedHosts_areNotFileHostRedirects(String location) {
        assertThat(HttpImslpClient.isFileHostRedirect(location)).isFalse();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" → 게이트(false)")
    @DisplayName("null / 빈 문자열 / 공백만 → false (무응답·헤더 없음과 동일 취급)")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nullOrBlank_isNotFileHostRedirect(String location) {
        assertThat(HttpImslpClient.isFileHostRedirect(location)).isFalse();
    }
}
