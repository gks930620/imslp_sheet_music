package com.test.test.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 설정
 * - Spring Security의 cors(Customizer.withDefaults())가 이 Bean을 사용함
 * - 현재: 같은 도메인이라 불필요하지만 미리 적용
 * - 나중에: 프론트엔드 분리 시 (Nginx + Spring API) 필요
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 허용할 Origin 목록
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",   // React 개발 서버
                "http://localhost:5173",   // Vite 개발 서버
                "http://localhost:8080"   // 로컬 개발
                // 운영 도메인 추가 예시:
                // "https://your-domain.com",
                // "https://www.your-domain.com"
        ));

        // 허용할 HTTP 메서드
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // 허용할 헤더
        configuration.setAllowedHeaders(List.of("*"));

        // 노출할 헤더 (쿠키 관련)
        configuration.setExposedHeaders(Arrays.asList("Set-Cookie", "Authorization"));

        // 쿠키 허용 (JWT 쿠키용)
        configuration.setAllowCredentials(true);

        // preflight 캐시 1시간
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 모든 경로에 CORS 설정 적용 (Security 필터가 처리)
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
