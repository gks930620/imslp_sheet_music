package com.test.test.jwt.config;

import com.test.test.jwt.JwtUtil;
import com.test.test.jwt.filter.JwtAccessTokenCheckAndSaveUserInfoFilter;
import com.test.test.jwt.filter.JwtLoginFilter;
import com.test.test.jwt.handler.CustomLogoutSuccessHandler;
import com.test.test.jwt.handler.OAuth2LoginSuccessHandler;
import com.test.test.jwt.service.CustomOAuth2UserService;
import com.test.test.jwt.service.CustomUserDetailsService;
import com.test.test.jwt.service.RefreshService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final AuthenticationConfiguration authenticationConfiguration;
    private final RefreshService refreshService;
    private final AuthorizationRequestRepository authorizationRequestRepository;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final CustomLogoutSuccessHandler customLogoutSuccessHandler;

    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                // frameOptions는 sameOrigin으로 제한: h2-console(동일 출처) iframe은 허용되면서
                // 운영 응답에도 X-Frame-Options: SAMEORIGIN 이 유지되어 클릭재킹을 방어한다.
                // (기존 disable()은 전역으로 헤더를 제거해 운영에서도 iframe 삽입을 허용했음)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        http.logout(logout -> logout
                .logoutUrl("/api/logout")
                .logoutSuccessHandler(customLogoutSuccessHandler));

        http.authorizeHttpRequests(auth -> auth
                // 관리 API 는 ADMIN 만 (역할 문자열에 ROLE_ 접두가 없어 hasRole 이 아니라 hasAuthority — 02 §0-3)
                .requestMatchers("/api/admin/**").hasAuthority("ADMIN")
                // 공개 API (02 §0-3)
                .requestMatchers(HttpMethod.GET,
                        "/api/works/**",
                        "/api/composers/**",
                        "/api/editions/*/download"
                ).permitAll()
                // 다운로드 사전 확인 (02 §3-4) — 화면이 받기 전에 HEAD 로 한 번 묻는다.
                // GET 만 열어 두면 같은 주소인데 401 이 떠서 정상 파일에도 실패 안내가 뜬다.
                .requestMatchers(HttpMethod.HEAD, "/api/editions/*/download").permitAll()
                // SPA 화면 셸 GET (02 §0-5) — HomeController catch-all 과 같은 기준
                .requestMatchers(new SpaShellRequestMatcher()).permitAll()
                .requestMatchers(
                        // 정적 리소스 (Vite 번들은 /assets/**, 아이콘/파비콘 포함)
                        "/", "/index.html", "/assets/**",
                        // /images/** 는 열지 않는다 — 그 경로의 서빙 엔드포인트를 제거했다(02 §0-4, qa 4차).
                        //   바이트 프록시는 /uploads/** 하나이고 판본 PDF 는 거기서도 404 다.
                        "/css/**", "/js/**",
                        "/favicon.ico", "/favicon.svg", "/icons.svg", "/uploads/**",
                        "/error", "/healthz",
                        "/h2-console/**",
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**",
                        // actuator는 health/info만 공개(헬스체크용). metrics 등 나머지는 인증 필요(anyRequest로 처리).
                        "/actuator/health", "/actuator/health/**", "/actuator/info",
                        // SPA 페이지 라우트 (HomeController가 index.html로 forward)
                        "/login", "/signup", "/mypage", "/community/**",
                        "/rooms", "/rooms/**",
                        "/ws-chat", "/ws-chat/**",
                        // OAuth2: 커스텀 시작점 + 스프링 표준 인가/콜백 엔드포인트
                        "/custom-oauth2/login/**", "/oauth2/**", "/login/oauth2/**"
                ).permitAll()
                .requestMatchers(
                        "/api/login",
                        "/api/users",
                        "/api/tokens/refresh",
                        "/api/oauth2/**"
                ).permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/api/communities",
                        "/api/communities/*",
                        "/api/communities/*/comments",
                        "/api/files",
                        "/api/files/paths",
                        "/api/files/*/content"
                ).permitAll()
                .requestMatchers(
                        "/api/logout",
                        "/api/users/me",
                        "/api/rooms", "/api/rooms/**"
                ).authenticated()
                .requestMatchers(HttpMethod.POST,
                        "/api/communities",
                        "/api/communities/*/comments",
                        "/api/files"
                ).authenticated()
                .requestMatchers(HttpMethod.PUT,
                        "/api/communities/**",
                        "/api/comments/**"
                ).authenticated()
                .requestMatchers(HttpMethod.DELETE,
                        "/api/communities/**",
                        "/api/comments/**",
                        "/api/files/**"
                ).authenticated()
                // 화이트리스트 방식: 위에서 공개(permitAll) 경로를 명시했고,
                // 그 외 모든 요청은 인증 필요. 신규 엔드포인트가 실수로 공개되지 않도록 하는 안전 기본값.
                // 새 공개 API/페이지를 추가하면 위 permitAll 목록에도 반드시 등록할 것.
                .anyRequest().authenticated());

        http.oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(authEndpoint ->
                        authEndpoint.authorizationRequestRepository(authorizationRequestRepository))
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler(oAuth2LoginSuccessHandler)
                .failureHandler((request, response, exception) -> {
                    log.error("OAuth2 login failed", exception);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                }));

        http.userDetailsService(customUserDetailsService)
                .addFilterAt(
                        new JwtLoginFilter(authenticationConfiguration.getAuthenticationManager(), jwtUtil,
                                refreshService, "/api/login", secureCookie),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new JwtAccessTokenCheckAndSaveUserInfoFilter(jwtUtil, customUserDetailsService),
                        UsernamePasswordAuthenticationFilter.class);

        http.exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");

            String errorCause = request.getAttribute("ERROR_CAUSE") != null
                    ? (String) request.getAttribute("ERROR_CAUSE")
                    : "NOT_AUTHENTICATED";

            String errorMessage;
            String errorCode;
            if ("TOKEN_EXPIRED".equals(errorCause)) {
                errorMessage = "Access token expired";
                errorCode = "TOKEN_EXPIRED";
            } else if ("INVALID_TOKEN".equals(errorCause)) {
                errorMessage = "Invalid token";
                errorCode = "INVALID_TOKEN";
            } else {
                errorMessage = "Authentication required";
                errorCode = "NOT_AUTHENTICATED";
            }

            String jsonResponse = String.format(
                    "{\"success\":false,\"message\":\"%s\",\"errorCode\":\"%s\",\"timestamp\":\"%s\"}",
                    errorMessage, errorCode, java.time.LocalDateTime.now()
            );
            response.getWriter().write(jsonResponse);
        }).accessDeniedHandler((request, response, deniedException) -> {
            // 권한 부족(USER 가 관리 API) → 진입점과 같은 형태의 JSON (02 §0-2)
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(String.format(
                    "{\"success\":false,\"message\":\"%s\",\"errorCode\":\"%s\",\"timestamp\":\"%s\"}",
                    "권한이 없습니다.", "ACCESS_DENIED", java.time.LocalDateTime.now()));
        }));

        return http.build();
    }
}
