package com.test.test.sheetmusic.crawl;

import com.test.test.common.dto.PageResponse;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.sheetmusic.crawl.dto.CrawlJobDTO;
import com.test.test.sheetmusic.crawl.dto.CrawlJobDetailDTO;
import com.test.test.sheetmusic.crawl.repository.CrawlJobRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수집 작업 조회 (02 §6-4 ~ §6-6). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrawlQueryService {

    private final CrawlJobRepository crawlJobRepository;
    private final CrawlJobDtoAssembler crawlJobDtoAssembler;

    public PageResponse<CrawlJobDTO> list(Pageable pageable) {
        Page<CrawlJobEntity> page = crawlJobRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);
        List<CrawlJobDTO> content = new ArrayList<>();
        for (CrawlJobEntity job : page.getContent()) {
            content.add(crawlJobDtoAssembler.toDto(job));
        }
        return PageResponse.<CrawlJobDTO>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    /** RUNNING 또는 PAUSED 인 작업(없으면 null) — 관리 화면 상단 띠. */
    public CrawlJobDTO active() {
        return crawlJobRepository.findByStatuses(List.of(CrawlJobStatus.RUNNING, CrawlJobStatus.PAUSED))
                .stream().findFirst()
                .map(crawlJobDtoAssembler::toDto)
                .orElse(null);
    }

    public CrawlJobDetailDTO detail(Long jobId) {
        CrawlJobEntity job = crawlJobRepository.findById(jobId)
                .orElseThrow(() -> EntityNotFoundException.of("수집 작업", jobId));
        return crawlJobDtoAssembler.toDetailDto(job);
    }
}
