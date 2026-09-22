package com.test.test.sheetmusic.recommendation;

import com.test.test.sheetmusic.edition.EditionEntity;
import com.test.test.sheetmusic.edition.EditionKind;
import com.test.test.sheetmusic.edition.EditionScope;
import com.test.test.sheetmusic.recommendation.dto.RecommendationAutoDTO;
import com.test.test.sheetmusic.recommendation.dto.RecommendationEditionRefDTO;
import com.test.test.sheetmusic.recommendation.dto.RecommendationHistoryDTO;
import com.test.test.sheetmusic.recommendation.dto.RecommendationLogDTO;
import com.test.test.sheetmusic.recommendation.dto.RecommendationSummaryDTO;
import com.test.test.sheetmusic.recommendation.repository.WorkRecommendationLogRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 근거·이력 (01_ERD §3-13, 02 §4-6·§4-7-1·§4-7-2·§5-6·§5-11). "고른 이유" 와 "바뀐 이력" 은 같은 표 하나다
 * (03 §30-1) — 그래서 쓰기·읽기도 이 서비스 한 곳에 모은다.
 */
@Service
@RequiredArgsConstructor
public class RecommendationLogService {

    /** 곡 상세(§4-7-2)에 싣는 최근 줄 수. */
    private static final int SUMMARY_LIMIT = 5;

    /** 전용 이력 API(§4-7-1)의 상한 — 응답 하나가 무한히 커지지 않게 한다. */
    private static final int HISTORY_LIMIT = 200;

    private final WorkRecommendationLogRepository repository;

    // ===== 쓰기 =====

    /** 관리자 지정 (02 §5-6). 추천이 실제로 바뀔 때만 부른다 — 같은 판본 재지정은 줄을 쌓지 않는다. */
    @Transactional
    public void recordAdminAssigned(Long workId, Long decidedByUserId, String decidedByNickname,
                                    EditionEntity edition, EditionEntity previousEdition,
                                    RecommendationReason reason, String note, Instant decidedAt) {
        repository.save(WorkRecommendationLogEntity.adminAssigned(
                workId, decidedAt, decidedByUserId, decidedByNickname, edition, previousEdition, reason, note));
    }

    /** 자동 지정 (02 §5-11). {@code dryRun} 이면 아예 부르지 않는 것이 계약이다 — 호출자가 그 판단을 한다. */
    @Transactional
    public void recordAutoAssigned(Long workId, EditionEntity edition, int candidateCount, int rank,
                                   Instant decidedAt) {
        repository.save(WorkRecommendationLogEntity.autoAssigned(workId, decidedAt, edition, candidateCount, rank));
    }

    /** 추천이 빠짐 (02 §5-3·§5-5). 지워지거나 파일이 떨어지는 판본을 <b>바뀌기 전에</b> 스냅샷한다. */
    @Transactional
    public void recordCleared(Long workId, Long decidedByUserId, String decidedByNickname,
                              EditionEntity clearedEdition, RecommendationClearedReason clearedReason,
                              Instant decidedAt) {
        repository.save(WorkRecommendationLogEntity.cleared(
                workId, decidedAt, decidedByUserId, decidedByNickname, clearedEdition, clearedReason));
    }

    /**
     * 판본 삭제(§7) — 이 판본을 가리키던 <b>참조만</b> 끊는다(행과 스냅샷 6개는 남긴다).
     * 방금 쌓은 {@code CLEARED} 줄 자신도 대상이다 — {@code previousEdition.editionId} 가 그렇게 null 이 된다.
     */
    @Transactional
    public void detachEditionReferences(List<Long> editionIds) {
        if (editionIds == null || editionIds.isEmpty()) {
            return;
        }
        repository.detachEditionRefs(editionIds);
        repository.detachPreviousEditionRefs(editionIds);
    }

    /** 곡 삭제(§7) — 곡이 없으면 설명할 대상이 없다. */
    @Transactional
    public void deleteByWorkId(Long workId) {
        repository.deleteByWorkId(workId);
    }

    // ===== 읽기 =====

    /** 곡 상세(§4-7-2) 안의 {@code recommendation} 객체 — 추천·기록이 없어도 객체는 항상 있다. */
    @Transactional(readOnly = true)
    public RecommendationSummaryDTO summaryFor(Long workId) {
        long historyCount = repository.countByWorkId(workId);
        List<WorkRecommendationLogEntity> top = repository.findByWorkIdOrderByDecidedAtDescIdDesc(
                workId, PageRequest.of(0, SUMMARY_LIMIT));
        List<RecommendationLogDTO> history = top.stream().map(this::toDto).toList();
        RecommendationLogDTO current = currentOf(history);
        return RecommendationSummaryDTO.builder()
                .current(current)
                .historyCount(historyCount)
                .history(history)
                .hasMore(historyCount > history.size())
                .build();
    }

    /** 전용 이력 API(§4-7-1) — 최신순 전부, 상한 200줄. */
    @Transactional(readOnly = true)
    public RecommendationHistoryDTO historyFor(Long workId) {
        long historyCount = repository.countByWorkId(workId);
        List<WorkRecommendationLogEntity> rows = repository.findByWorkIdOrderByDecidedAtDescIdDesc(
                workId, PageRequest.of(0, HISTORY_LIMIT));
        return RecommendationHistoryDTO.builder()
                .workId(workId)
                .historyCount(historyCount)
                .history(rows.stream().map(this::toDto).toList())
                .build();
    }

    /**
     * 곡 목록(§4-6) {@code recommendationSource} — 그 곡 로그의 맨 위 줄이 {@code ASSIGNED} 일 때 그 줄의
     * {@code source}, 아니면(줄이 없거나 맨 위가 {@code CLEARED}) null. 결과에 없는 workId 는 호출자가 null 로 읽는다.
     */
    @Transactional(readOnly = true)
    public Map<Long, RecommendationSource> latestAssignedSourceByWork(List<Long> workIds) {
        Map<Long, RecommendationSource> result = new LinkedHashMap<>();
        if (workIds == null || workIds.isEmpty()) {
            return result;
        }
        // 맨 위(=가장 최신) 줄이 CLEARED 면 결과가 null 이어야 하는데, Map#computeIfAbsent 는 null 을
        // 기록하지 않고 넘어가 버려 다음(더 옛) 줄로 자리를 채우는 버그가 난다 — seen 으로 직접 가른다.
        Set<Long> seen = new HashSet<>();
        for (WorkRecommendationLogEntity row : repository.findAllByWorkIdInOrderedForLatest(workIds)) {
            // 정렬이 work 별 최신순이라 그 work 의 첫 등장 행이 맨 위 줄이다.
            if (!seen.add(row.getWorkId())) {
                continue;
            }
            result.put(row.getWorkId(), row.getAction() == RecommendationAction.ASSIGNED ? row.getSource() : null);
        }
        return result;
    }

    // ===== 내부 =====

    private RecommendationLogDTO currentOf(List<RecommendationLogDTO> history) {
        if (history.isEmpty()) {
            return null;
        }
        RecommendationLogDTO top = history.get(0);
        return top.getAction() == RecommendationAction.ASSIGNED ? top : null;
    }

    private RecommendationLogDTO toDto(WorkRecommendationLogEntity e) {
        return RecommendationLogDTO.builder()
                .id(e.getId())
                .decidedAt(e.getDecidedAt())
                .source(e.getSource())
                .action(e.getAction())
                .decidedByNickname(e.getDecidedByNickname())
                .edition(editionRefOf(e.getEditionKind(), e.getEditionId(), e.getEditionScope(),
                        e.getEditionMovementNumber(), e.getEditionPublisher(), e.getEditionEditor(),
                        e.getEditionPublishYear()))
                .previousEdition(editionRefOf(e.getPreviousKind(), e.getPreviousEditionId(), e.getPreviousScope(),
                        e.getPreviousMovementNumber(), e.getPreviousPublisher(), e.getPreviousEditor(),
                        e.getPreviousPublishYear()))
                .auto(autoOf(e))
                .reason(e.getReason())
                .note(e.getNote())
                .clearedReason(e.getClearedReason())
                .build();
    }

    /**
     * 스냅샷이 있었는지는 {@code kind} 유무로 판정한다 — {@code kind} 는 판본에서 NOT NULL 이라
     * 스냅샷을 뜬 적이 있으면 항상 채워져 있고, 아예 없었으면(CLEARED 의 edition, 첫 지정의 previous) null 이다.
     * {@code editionId} 만으로 판정하지 않는 이유: 판본이 삭제된 뒤에도 나머지 6개는 남아 객체 자체는 있어야 한다.
     */
    private RecommendationEditionRefDTO editionRefOf(EditionKind kind, Long editionId, EditionScope scope,
                                                      Integer movementNumber, String publisher, String editor,
                                                      Integer publishYear) {
        if (kind == null) {
            return null;
        }
        return RecommendationEditionRefDTO.builder()
                .editionId(editionId)
                .kind(kind)
                .scope(scope)
                .movementNumber(movementNumber)
                .publisher(publisher)
                .editor(editor)
                .publishYear(publishYear)
                .build();
    }

    private RecommendationAutoDTO autoOf(WorkRecommendationLogEntity e) {
        if (e.getAutoRule() == null) {
            return null;
        }
        return RecommendationAutoDTO.builder()
                .rule(e.getAutoRule())
                .imslpDownloadCount(e.getAutoImslpDownloadCount())
                .candidateCount(e.getAutoCandidateCount())
                .rank(e.getAutoRank())
                .build();
    }
}
