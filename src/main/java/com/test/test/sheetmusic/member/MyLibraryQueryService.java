package com.test.test.sheetmusic.member;

import com.test.test.common.dto.PageResponse;
import com.test.test.file.entity.FileEntity;
import com.test.test.sheetmusic.edition.EditionDtoAssembler;
import com.test.test.sheetmusic.edition.EditionEntity;
import com.test.test.sheetmusic.edition.dto.EditionBriefDTO;
import com.test.test.sheetmusic.member.dto.DownloadLibraryDTO;
import com.test.test.sheetmusic.member.dto.FavoriteLibraryDTO;
import com.test.test.sheetmusic.member.dto.LibraryCountsDTO;
import com.test.test.sheetmusic.member.dto.ReceivedWorkDTO;
import com.test.test.sheetmusic.member.repository.UserWorkDownloadRepository;
import com.test.test.sheetmusic.member.repository.WorkFavoriteRepository;
import com.test.test.sheetmusic.work.Section;
import com.test.test.sheetmusic.work.WorkDtoAssembler;
import com.test.test.sheetmusic.work.WorkEntity;
import com.test.test.sheetmusic.work.dto.WorkSummaryDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 악보 — 즐겨찾기 탭(02 §10-2) · 받은 악보 탭(§10-3).
 *
 * <p>주체는 토큰에서만 온다(§10-0) — 이 서비스는 userId 를 파라미터로 받되 그 값을 만든 곳은 컨트롤러의
 * {@code @AuthenticationPrincipal} 뿐이다.
 *
 * <p>두 탭 모두 <b>숫자 2개를 늘 함께</b> 준다(화면정의 09 §6 S3) — 어느 탭에 있든 탭 머리에 두 숫자를 그리기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MyLibraryQueryService {

    private final WorkFavoriteRepository workFavoriteRepository;
    private final UserWorkDownloadRepository userWorkDownloadRepository;
    private final WorkDtoAssembler workDtoAssembler;
    private final EditionDtoAssembler editionDtoAssembler;

    // ===== §10-2 즐겨찾기 탭 =====

    public FavoriteLibraryDTO favorites(Long userId, String section, Pageable pageable) {
        Section scope = Section.from(section);
        LibraryCountsDTO counts = counts(userId, scope);
        Page<WorkEntity> page = pageOf(pageable, counts.getFavorites(),
                request -> workFavoriteRepository.findFavoriteWorks(userId, scope, request));
        return FavoriteLibraryDTO.builder()
                .counts(counts)
                .works(toPageResponse(page, workDtoAssembler.toSummaries(page.getContent())))
                .build();
    }

    // ===== §10-3 받은 악보 탭 =====

    public DownloadLibraryDTO downloads(Long userId, String section, Pageable pageable) {
        Section scope = Section.from(section);
        LibraryCountsDTO counts = counts(userId, scope);
        Page<UserWorkDownloadEntity> page = pageOf(pageable, counts.getDownloads(),
                request -> userWorkDownloadRepository.findReceived(userId, scope, request));
        return DownloadLibraryDTO.builder()
                .counts(counts)
                .items(toPageResponse(page, toReceived(page.getContent())))
                .build();
    }

    /**
     * 줄(다운로드 행)과 곡 카드를 <b>곡 id 로</b> 붙인다 — 순서(index)로 붙이지 않는다.
     * 조립기가 1:1·순서보존이라는 보장은 어디에도 쓰여 있지 않아서, 나중에 한 건이라도 거르면
     * <b>예외도 없이</b> A 곡 제목 아래 B 곡의 판본이 걸린다(조용히 틀린 데이터).
     *
     * <p>짝을 못 찾은 줄은 <b>목록에서 뺀다</b>(그리고 WARN 을 남긴다). 예외로 500 을 내면 줄 하나 때문에
     * 선반 전체가 안 보이고, 자리를 메우려 남의 카드를 끌어다 쓰면 잘못된 데이터가 나간다 —
     * 셋 중 "말할 수 없는 줄은 말하지 않는다" 가 가장 덜 나쁘다. (이때 {@code counts} 와 목록 길이가
     * 어긋날 수 있는데, 그건 로그가 가리키는 <b>고쳐야 할 버그</b>지 감춰야 할 상태가 아니다.)
     */
    private List<ReceivedWorkDTO> toReceived(List<UserWorkDownloadEntity> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<WorkEntity> works = rows.stream().map(UserWorkDownloadEntity::getWork).toList();
        Map<Long, WorkSummaryDTO> cards = workDtoAssembler.toSummaries(works).stream()
                .collect(Collectors.toMap(WorkSummaryDTO::getId, card -> card, (kept, ignored) -> kept));
        Map<Long, FileEntity> files = editionDtoAssembler.loadFiles(editionsOf(rows));

        List<ReceivedWorkDTO> items = new ArrayList<>();
        for (UserWorkDownloadEntity row : rows) {
            WorkSummaryDTO card = cards.get(row.getWork().getId());
            if (card == null) {
                log.warn("받은 악보 줄의 곡 카드를 만들지 못해 목록에서 뺀다 (workId={})", row.getWork().getId());
                continue;
            }
            RedownloadDecision decision = RedownloadDecision.of(row.getLastEdition(),
                    row.getWork().getRecommendedEdition());
            items.add(ReceivedWorkDTO.builder()
                    .work(card)
                    .downloadedAt(row.getLastDownloadedAt())
                    .receivedEdition(receivedEdition(row, files))
                    .redownloadState(decision.getState())
                    .redownloadUrl(decision.downloadUrl())
                    .alternativeEdition(decision.getAlternative() == null
                            ? null : EditionBriefDTO.from(decision.getAlternative(), fileSize(decision.getAlternative(), files)))
                    .build());
        }
        return items;
    }

    /**
     * "받은 판본" 줄 (02 §10-3) — 판본이 살아 있으면 <b>지금 값</b>, 삭제됐으면 <b>스냅샷</b>(id = null).
     * 이 줄은 "누르면 무엇이 오는가" 를 말하므로 파일이 교체돼 쪽수·크기가 달라졌으면 지금 값이 옳고,
     * 스냅샷은 말할 수 없게 됐을 때의 폴백이다. 스냅샷조차 없으면 null 이라 화면이 줄을 생략한다.
     */
    private EditionBriefDTO receivedEdition(UserWorkDownloadEntity row, Map<Long, FileEntity> files) {
        if (row.hasLiveEdition()) {
            return EditionBriefDTO.from(row.getLastEdition(), fileSize(row.getLastEdition(), files));
        }
        if (!row.hasSnapshot()) {
            return null;
        }
        return EditionBriefDTO.builder()
                .id(null)
                .kind(row.getEditionKind())
                .scope(row.getEditionScope())
                .movementNumber(row.getEditionMovementNumber())
                .pageCount(row.getEditionPageCount())
                .fileSize(row.getEditionFileSize())
                .build();
    }

    /** 페이지의 판본(받은 것 · 지금 추천)이 참조하는 files 행을 한 번에 읽는다 — 줄마다 조회하지 않는다. */
    private List<EditionEntity> editionsOf(List<UserWorkDownloadEntity> rows) {
        List<EditionEntity> editions = new ArrayList<>();
        for (UserWorkDownloadEntity row : rows) {
            if (row.getLastEdition() != null) {
                editions.add(row.getLastEdition());
            }
            EditionEntity recommended = row.getWork().getRecommendedEdition();
            if (recommended != null) {
                editions.add(recommended);
            }
        }
        return editions;
    }

    private Long fileSize(EditionEntity edition, Map<Long, FileEntity> files) {
        FileEntity pdf = edition.getPdfFileId() == null ? null : files.get(edition.getPdfFileId());
        return pdf == null ? null : pdf.getFileSize();
    }

    // ===== 공통 =====

    private LibraryCountsDTO counts(Long userId, Section section) {
        return LibraryCountsDTO.builder()
                .favorites(workFavoriteRepository.countVisible(userId, section))
                .downloads(userWorkDownloadRepository.countVisible(userId, section))
                .build();
    }

    /**
     * <b>범위 밖 페이지는 질의하지 않는다</b> — 200 + 빈 목록이 답이다 (02 §0-4, 03 §27).
     *
     * <p>이 두 탭은 문자열 {@code @Query} 로 페이지를 읽어서, 스프링 데이터가 offset 을 {@code int} 로 좁힌다
     * ({@code PageableUtils.getOffsetAsInteger}) — {@code page} 가 커지면 질의가 닿기도 전에 500 이 된다.
     * 이미 세어 둔 {@code counts} 가 그 탭의 총 개수와 <b>항상 같으므로</b>(§10-2), offset 이 총 개수를 넘으면
     * 질의 없이 빈 페이지를 만든다.
     *
     * <p>{@code page} 는 <b>요청한 값을 그대로</b> 되비춘다 — 서버가 몰래 최대 페이지로 깎으면(clamp) 화면의
     * "다음 페이지" 가 눌러도 그 자리가 된다. 400/404 로 바꾸지도 않는다(다른 목록 API 와 답이 갈린다).
     *
     * <p><b>{@code totalElements} 의 원천은 {@code counts} 하나다</b>(03 §27) — 그래서 질의는 한 페이지의
     * 줄만({@code List}) 돌려주고 여기서 감싼다. 리포지토리에 {@code countQuery} 를 두면 같은 수를 세는 곳이
     * 둘이 돼, 숨김·구분 조건을 한쪽만 고치는 날 응답은 200 인 채 숫자만 조용히 갈린다(02 §10-2 는
     * "{@code counts} 와 {@code totalElements} 는 항상 같다" 가 계약이다). 덤으로 두 분기가 같은 모양이 되고
     * 요청당 count 질의가 하나 준다.
     */
    private <E> Page<E> pageOf(Pageable pageable, long total, Function<Pageable, List<E>> query) {
        Pageable request = pageOnly(pageable);
        if (request.getOffset() >= total) {
            return new PageImpl<>(List.of(), request, total);
        }
        return new PageImpl<>(query.apply(request), request, total);
    }

    /** 정렬은 계약이 정한 것 하나뿐이라(§10-2 · §10-3) 요청의 {@code sort} 는 받지 않는다. */
    private Pageable pageOnly(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private <E, T> PageResponse<T> toPageResponse(Page<E> page, List<T> content) {
        return PageResponse.<T>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
