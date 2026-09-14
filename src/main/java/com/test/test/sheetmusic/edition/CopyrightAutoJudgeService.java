package com.test.test.sheetmusic.edition;

import com.test.test.sheetmusic.edition.dto.AutoJudgeDTOs;
import com.test.test.sheetmusic.edition.repository.EditionRepository;
import com.test.test.sheetmusic.work.repository.WorkRepository;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 저작권 자동 판정 일괄 실행·되돌리기 (02 §5-11·§5-12, 03 §16).
 *
 * <p><b>동기 요청 하나로 끝낸다</b> — 수집 워커 같은 비동기 잡을 만들지 않는다. 대상이 판본 수천 건이고
 * 외부 I/O 가 전혀 없다(전부 우리 DB 안의 값 비교). 잡을 만들면 상태 테이블·폴링 화면·재시작 복구가
 * 따라오는데 얻는 것이 없다. 다만 <b>트랜잭션은 하나가 아니다</b> — 청크로 끊는다(03 §16-1, {@link #autoJudge}).
 *
 * <p><b>{@code dryRun} 은 저장을 호출하지 않는 방식이다</b> — 트랜잭션 롤백으로 흉내 내지 않는다(03 §16).
 * 롤백 방식은 예외로 성공 응답을 만드는 셈이라 읽기 어렵고 {@code @Transactional} 경계와 싸운다.
 * 여기서는 {@code dryRun} 이면 엔티티 변경 메서드를 <b>아예 부르지 않으므로</b> 더티체킹이 일어날 것이 없다.
 */
@Service
public class CopyrightAutoJudgeService {

    /** 추천 자동 지정 순서 (02 §5-11): IMSLP 다운로드 수 내림차순(없으면 뒤로), 같으면 id 오름차순. */
    private static final Comparator<EditionEntity> RECOMMEND_ORDER = Comparator
            .comparing(EditionEntity::getImslpDownloadCount, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(EditionEntity::getId);

    /** 한 트랜잭션에서 처리하는 판본 수 (03 §16-1) — 수집 워커의 짧은 UPDATE 가 그 사이에 끼어들 수 있어야 한다. */
    private static final int CHUNK_SIZE = 500;

    private final EditionRepository editionRepository;
    private final WorkRepository workRepository;
    private final TransactionTemplate transactionTemplate;
    private final ZoneId timezone;

    public CopyrightAutoJudgeService(EditionRepository editionRepository,
                                     WorkRepository workRepository,
                                     TransactionTemplate transactionTemplate,
                                     @Value("${app.timezone}") String timezone) {
        this.editionRepository = editionRepository;
        this.workRepository = workRepository;
        this.transactionTemplate = transactionTemplate;
        this.timezone = ZoneId.of(timezone);
    }

    // ===== §5-11 일괄 실행 =====

    /**
     * <b>한 트랜잭션이 아니다</b> (03 §16-1) — 대상 id 를 먼저 읽고 {@code CHUNK_SIZE} 건씩 <b>별도 트랜잭션</b>으로
     * 처리한다. 대상이 UNKNOWN 판본 전량(1,799건, 300곡이면 25,000건)인데 수집 워커는 같은 {@code edition} 행을
     * 짧은 트랜잭션으로 UPDATE 하므로, 한 트랜잭션에 다 잡으면 수집 중 자동 판정을 누르는 순간 락 경합으로
     * 워커 항목이 {@code INTERNAL_ERROR} 로 죽는다. 응답 숫자는 청크 합계이고, 판정은 멱등이라
     * 중간에 끊겨도 다시 실행하면 이어진다.
     */
    public AutoJudgeDTOs.AutoJudgeResult autoJudge(AutoJudgeDTOs.AutoJudgeRequest request) {
        boolean dryRun = request.isDryRun();
        int currentYear = currentYear();
        Instant judgedAt = Instant.now();

        Map<CopyrightAutoJudge.Rule, Integer> byRule = new EnumMap<>(CopyrightAutoJudge.Rule.class);
        Map<CopyrightAutoJudge.SkipReason, Integer> skipped = new EnumMap<>(CopyrightAutoJudge.SkipReason.class);
        Set<Long> newlyFreeEditionIds = new HashSet<>();

        List<Long> targetIds = editionRepository.findUnknownCopyrightIdsForAutoJudge();
        int targetCount = 0;
        for (int from = 0; from < targetIds.size(); from += CHUNK_SIZE) {
            List<Long> chunk = List.copyOf(targetIds.subList(from, Math.min(from + CHUNK_SIZE, targetIds.size())));
            Integer judged = transactionTemplate.execute(status ->
                    judgeChunk(chunk, dryRun, currentYear, judgedAt, byRule, skipped, newlyFreeEditionIds));
            targetCount += judged == null ? 0 : judged;
        }

        int judgedFree = newlyFreeEditionIds.size();
        int recommendedAssigned = request.isAssignRecommended()
                ? assignRecommendedInTransaction(newlyFreeEditionIds, dryRun)
                : 0;

        return AutoJudgeDTOs.AutoJudgeResult.builder()
                .dryRun(dryRun)
                .targetCount(targetCount)
                .judgedFree(judgedFree)
                .remainingUnknown(targetCount - judgedFree)
                .recommendedAssigned(recommendedAssigned)
                .byRule(ruleCounts(byRule))
                .skipped(skipCounts(skipped))
                .build();
    }

    /**
     * 청크 1개를 <b>한 트랜잭션</b>에서 판정하고 실제로 읽은 대상 수를 돌려준다.
     * id 를 뽑은 뒤 다른 관리자가 판정해 버린 판본은 조회에서 빠지므로 대상 수에도 들어가지 않는다 —
     * 그래야 {@code targetCount = judgedFree + sum(skipped)} 가 항상 맞고 남의 판정을 덮지도 않는다.
     */
    private int judgeChunk(List<Long> ids, boolean dryRun, int currentYear, Instant judgedAt,
                           Map<CopyrightAutoJudge.Rule, Integer> byRule,
                           Map<CopyrightAutoJudge.SkipReason, Integer> skipped,
                           Set<Long> newlyFreeEditionIds) {
        List<EditionEntity> targets = editionRepository.findUnknownCopyrightForAutoJudge(ids);
        for (EditionEntity edition : targets) {
            CopyrightAutoJudge.Verdict verdict = verdictOf(edition, currentYear);
            if (!verdict.isFree()) {
                skipped.merge(verdict.getSkipReason(), 1, Integer::sum);
                continue;
            }
            byRule.merge(verdict.getRule(), 1, Integer::sum);
            newlyFreeEditionIds.add(edition.getId());
            if (!dryRun) {
                edition.judgeCopyright(KoreaCopyright.FREE, verdict.getNote(),
                        CopyrightAutoJudge.AUTO_JUDGED_BY, judgedAt);
            }
        }
        return targets.size();
    }

    /** 추천 지정도 판정 청크와 분리된 트랜잭션에서 한다 — 후보는 "파일 있는 전곡 악보" 라 수가 적다. */
    private int assignRecommendedInTransaction(Set<Long> newlyFreeEditionIds, boolean dryRun) {
        Integer assigned = transactionTemplate.execute(status -> assignRecommended(newlyFreeEditionIds, dryRun));
        return assigned == null ? 0 : assigned;
    }

    /**
     * 추천이 없는 곡마다 {@code FREE} 이고 파일이 있는 전곡·전체 악보 판본을 하나 골라 추천으로 지정한다.
     * 판정만 하면 곡이 계속 {@code PREPARING} 이라 사용자가 받을 수 있는 곡은 여전히 0개다.
     * <b>이미 추천이 있는 곡은 건드리지 않는다</b>(관리자가 손으로 지정한 것을 덮지 않는다).
     */
    private int assignRecommended(Set<Long> newlyFreeEditionIds, boolean dryRun) {
        Map<Long, List<EditionEntity>> byWork = new LinkedHashMap<>();
        for (EditionEntity edition : editionRepository.findRecommendCandidates()) {
            boolean free = edition.getKoreaCopyright() == KoreaCopyright.FREE
                    || newlyFreeEditionIds.contains(edition.getId());
            if (free) {
                byWork.computeIfAbsent(edition.getWork().getId(), key -> new ArrayList<>()).add(edition);
            }
        }
        int assigned = 0;
        for (List<EditionEntity> candidates : byWork.values()) {
            candidates.sort(RECOMMEND_ORDER);
            EditionEntity best = candidates.get(0);
            if (!dryRun) {
                best.getWork().recommend(best);
            }
            assigned++;
        }
        return assigned;
    }

    // ===== §5-12 되돌리기 =====

    /**
     * 되돌린 판본 수와, 그중 <b>어떤 곡의 추천으로 지정돼 있던 것</b>의 수를 함께 돌려준다 (02 §5-12).
     * 뒤 숫자는 이번 되돌리기로 <b>다운로드가 닫힌 곡 수</b>다 — 추천은 유지하되 판정이 UNKNOWN 이 되므로
     * 그 곡의 상태가 즉시 UNKNOWN 이 된다(01_ERD §4). 되돌리기 전에 세야 한다(되돌린 뒤에도 추천은 남지만,
     * 이 값은 "이번 실행이 무엇을 닫았는가" 를 말하는 숫자라 대상 집합 안에서만 센다).
     */
    @Transactional
    public AutoJudgeDTOs.UndoResult undo() {
        List<EditionEntity> autoJudged = editionRepository.findAutoJudged(CopyrightAutoJudge.AUTO_JUDGED_BY);
        int recommendationKept = 0;
        for (EditionEntity edition : autoJudged) {
            if (edition.isRecommendedByWork()) {
                recommendationKept++;
            }
        }
        autoJudged.forEach(EditionEntity::revertAutoJudgement);
        return AutoJudgeDTOs.UndoResult.builder()
                .reverted(autoJudged.size())
                .recommendationKept(recommendationKept)
                .build();
    }

    // ===== §5-8 대기함이 쓰는 같은 판정 =====

    /**
     * 이 판본이 지금 자동 판정을 돌리면 왜 안 열리는지 (§5-8 {@code autoJudgeSkipReason}).
     * 열릴 수 있으면 {@code null}. <b>계산만 하고 아무것도 바꾸지 않는다.</b>
     * §5-11 과 같은 함수를 쓰므로 대기함 표시와 실제 실행 결과가 어긋날 수 없다.
     */
    public CopyrightAutoJudge.SkipReason skipReasonOf(EditionEntity edition) {
        return verdictOf(edition, currentYear()).getSkipReason();
    }

    /**
     * 지금 §5-12 로 되돌릴 수 있는 것이 얼마나 남았는지 (§5-8-1 {@code autoJudged}).
     * <b>계산만 하고 아무것도 바꾸지 않는다.</b> 되돌릴 것이 없으면 {@code 0/0} 이고 {@code null} 이 아니다.
     *
     * <p>이 값이 {@link #undo()} 와 <b>같은 곳에</b> 사는 이유는 {@link #skipReasonOf} 가 §5-11 과 판정 함수를
     * 공유하는 이유와 같다 — 조건식이 대기함 쪽에 복사돼 있으면 <b>예고와 결과가 어긋날 수 있다</b>.
     * 되돌리기는 수백~수천 판본을 한 번에 공개 재배포로 여는 동작이라 예고가 틀리면 안 된다.
     */
    @Transactional(readOnly = true)
    public AutoJudgeDTOs.RevertibleSummary revertible() {
        return AutoJudgeDTOs.RevertibleSummary.builder()
                .revertibleEditions(editionRepository.countAutoJudged(CopyrightAutoJudge.AUTO_JUDGED_BY))
                .revertibleRecommendedWorks(
                        workRepository.countAutoJudgedRecommendations(CopyrightAutoJudge.AUTO_JUDGED_BY))
                .build();
    }

    // ===== 내부 =====

    /** 엔티티에서 순수 함수의 인자를 뽑아 넘기는 얇은 어댑터. 규칙 자체는 {@link CopyrightAutoJudge} 에만 있다. */
    private CopyrightAutoJudge.Verdict verdictOf(EditionEntity edition, int currentYear) {
        return CopyrightAutoJudge.judge(
                edition.getImslpLicenseCode(),
                edition.getWork().getComposer().getDeathYear(),
                edition.getEditor(),
                edition.getArranger(),
                edition.getKind(),
                edition.getPublishYear(),
                currentYear);
    }

    /** 기준 연도는 서비스가 구해 순수 함수에 넘긴다 (03 §16 — 시간 의존 로직은 시간을 파라미터로). */
    private int currentYear() {
        return Year.now(timezone).getValue();
    }

    /** count 가 0 인 규칙도 빼지 않는다 — 화면이 항목 유무로 분기하지 않게 항상 3개가 온다(02 §5-11). */
    private List<AutoJudgeDTOs.AutoJudgeResult.RuleCount> ruleCounts(Map<CopyrightAutoJudge.Rule, Integer> counts) {
        List<AutoJudgeDTOs.AutoJudgeResult.RuleCount> list = new ArrayList<>();
        for (CopyrightAutoJudge.Rule rule : CopyrightAutoJudge.Rule.values()) {
            list.add(AutoJudgeDTOs.AutoJudgeResult.RuleCount.builder()
                    .rule(rule)
                    .count(counts.getOrDefault(rule, 0))
                    .build());
        }
        return list;
    }

    /** 마찬가지로 사유 5개는 항상 온다(부록 A §A-1 표 순서 = enum 선언 순서). */
    private List<AutoJudgeDTOs.AutoJudgeResult.SkipCount> skipCounts(
            Map<CopyrightAutoJudge.SkipReason, Integer> counts) {
        List<AutoJudgeDTOs.AutoJudgeResult.SkipCount> list = new ArrayList<>();
        for (CopyrightAutoJudge.SkipReason reason : CopyrightAutoJudge.SkipReason.values()) {
            list.add(AutoJudgeDTOs.AutoJudgeResult.SkipCount.builder()
                    .reason(reason)
                    .count(counts.getOrDefault(reason, 0))
                    .build());
        }
        return list;
    }
}
