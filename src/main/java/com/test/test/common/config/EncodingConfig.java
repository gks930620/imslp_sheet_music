package com.test.test.common.config;

import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CharacterEncodingFilter;

/**
 * 요청 본문을 항상 UTF-8 로 읽는다.
 *
 * <p>응답 인코딩은 여기서 강제하지 않는다 — 전역으로 강제하면 이미지·PDF 응답의 Content-Type 에도
 * {@code charset} 이 붙는다. JSON 응답의 UTF-8 표기는 {@code WebConfig} 의 Jackson 컨버터가 담당한다.
 */
@Configuration
public class EncodingConfig {

    @Bean
    public CharacterEncodingFilter characterEncodingFilter() {
        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        filter.setEncoding(StandardCharsets.UTF_8.name());
        filter.setForceRequestEncoding(true);
        filter.setForceResponseEncoding(false);
        return filter;
    }
}
