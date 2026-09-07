package com.test.test.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    // 업로드 파일 서빙은 로컬 디스크 정적 핸들러(/uploads/**) 대신
    // FileServingController 프록시가 담당한다(§5-3, 버킷 private 모드 대응).

    /**
     * JSON 응답의 Content-Type 에 {@code charset=UTF-8} 을 명시한다.
     * (붙이지 않으면 서블릿 기본 인코딩으로 해석돼 한글이 깨진다. 이미지·PDF 응답은 영향받지 않는다.)
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                jackson.setSupportedMediaTypes(List.of(
                        new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8),
                        new MediaType("application", "*+json", StandardCharsets.UTF_8)));
            }
        }
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // Pageable 기본값 설정
        PageableHandlerMethodArgumentResolver resolver = new PageableHandlerMethodArgumentResolver();
        resolver.setFallbackPageable(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")));
        resolver.setMaxPageSize(100); // 최대 페이지 크기 제한
        resolvers.add(resolver);
    }
}
