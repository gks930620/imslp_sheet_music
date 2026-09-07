package com.test.test.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이징 응답용 커스텀 클래스
 * Spring Page의 불필요한 필드를 제거하고 필요한 것만 제공
 */
@Getter
@AllArgsConstructor
@Builder
public class PageResponse<T> {
    private List<T> content;        // 실제 데이터 목록
    private int page;               // 현재 페이지 번호 (0부터 시작)
    private int size;               // 페이지 크기
    private long totalElements;     // 전체 데이터 수
    private int totalPages;         // 전체 페이지 수
    private boolean first;          // 첫 페이지 여부
    private boolean last;           // 마지막 페이지 여부

    /**
     * Spring Page를 PageResponse로 변환
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}

