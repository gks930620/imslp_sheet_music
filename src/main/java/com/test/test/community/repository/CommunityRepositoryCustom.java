package com.test.test.community.repository;

import com.test.test.community.dto.CommunityDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommunityRepositoryCustom {

    /**
     * 커뮤니티 목록 조회 / 검색 (QueryDSL 동적 쿼리)
     * @param searchType "title" 또는 "nickname" (null 가능)
     * @param keyword 검색 키워드 (null이면 전체 조회)
     * @param pageable 페이징 정보
     * @return 게시글 목록 (최신순 정렬)
     */
    Page<CommunityDTO> searchCommunity(String searchType, String keyword, Pageable pageable);
}

