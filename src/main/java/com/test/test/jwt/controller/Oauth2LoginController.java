package com.test.test.jwt.controller;

import com.test.test.jwt.repository.InMemoryAuthorizationRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/custom-oauth2/login")
@RequiredArgsConstructor
public class Oauth2LoginController {
    private final InMemoryAuthorizationRequestRepository authorizationRequestRepository;

    // ✅ yml 설정을 불러오기 위한 인터페이스 주입
    private final ClientRegistrationRepository clientRegistrationRepository;


    // CSR방식이어도  브라우저에서 Oauth2 처음 요청만큼은 REST요청이 아닌 그냥 url 요청으로.. 즉 a태그로 직접요청이지 ajax요청이 아님
    @GetMapping("/web/{provider}")
    public void oauth2LoginWeb(
        @PathVariable("provider") String provider,
        HttpServletRequest request,
        HttpServletResponse response) throws IOException {

        // 1. yml 설정 정보 로드 (google, kakao 등)
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(provider);
        if (registration == null) {
            throw new IllegalArgumentException("지원하지 않는 로그인 수단입니다: " + provider);
        }

        String state = UUID.randomUUID().toString();
        String authUri = registration.getProviderDetails().getAuthorizationUri();

        // 2. OAuth2AuthorizationRequest 생성 및 저장
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri(authUri)
            .clientId(registration.getClientId())
            .redirectUri(registration.getRedirectUri())
            .state(state)
            .attributes(attrs -> {
                attrs.put("registration_id", provider);
                attrs.put("target", "web");
            })
            .build();

        authorizationRequestRepository.saveAuthorizationRequest(authorizationRequest, request, response);

        // 3. 인증 URL 조립 및 리다이렉트
        String authorizationUrl = UriComponentsBuilder.fromHttpUrl(authUri)
            .queryParam("client_id", registration.getClientId())
            .queryParam("redirect_uri", registration.getRedirectUri())
            .queryParam("response_type", "code")
            .queryParam("state", state)
            // ✅ 구글은 scope가 필수이므로 추가 (카카오는 설정에 따라 생략 가능하지만 넣어도 무방)
            .queryParam("scope", String.join(" ", registration.getScopes()))
            .build()
            .encode()
            .toUriString();
        response.sendRedirect(authorizationUrl);
    }

    // ✅ 앱용 OAuth2 로그인은 이제 네이티브 SDK 방식을 사용합니다.
    // 앱에서 kakao_flutter_sdk, google_sign_in 등으로 네이티브 로그인 후
    // /api/oauth2/providers/{provider}/tokens 엔드포인트로 사용자 정보를 전송하여 JWT를 발급받습니다.
    // (AppOAuth2Controller 참조)
}

