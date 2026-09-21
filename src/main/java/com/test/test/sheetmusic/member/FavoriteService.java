package com.test.test.sheetmusic.member;

import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.sheetmusic.member.dto.FavoriteDTO;
import com.test.test.sheetmusic.member.repository.WorkFavoriteRepository;
import com.test.test.sheetmusic.work.WorkEntity;
import com.test.test.sheetmusic.work.repository.WorkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 즐겨찾기 켜기·끄기 (02 §10-1).
 *
 * <p><b>토글이 아니라 멱등한 두 문이다.</b> 로그인 복귀 완성(03 §22)이 한 번 더 실행돼도 결과가 같아야 하고,
 * 토글이었다면 두 번째 호출이 즐겨찾기를 <b>꺼버린다</b>(인수 조건 8-B 4). 그래서 켜기는 이미 있으면
 * 아무것도 하지 않는다 — 행을 다시 만들지 않고 {@code created_at} 도 건드리지 않는다.
 *
 * <p><b>멱등은 동시 요청에도 성립해야 한다.</b> 복귀 완성이 자동으로 켜는 동안 사용자가 하트를 한 번 더
 * 누르면 두 요청이 겹치는데, 뒤에 온 요청은 {@code uk_work_favorite} 에 걸린다. 그 답도 200 이다 —
 * 원하던 상태(켜짐)가 이미 이뤄졌기 때문이다.
 *
 * <p>그래서 <b>클래스에 {@code @Transactional} 을 걸지 않는다</b>: 제약 위반은 그 트랜잭션을 rollback-only 로
 * 만들어 안에서 삼켜도 커밋이 다시 터지므로, 넣기는 {@link FavoriteWriter} 가 <b>따로</b> 열고 닫고
 * 실패는 그 트랜잭션이 끝난 뒤 여기서 받는다. 지우기는 {@link #turnOff} 가 자기 트랜잭션을 연다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FavoriteService {

    private final FavoriteWriter favoriteWriter;
    private final WorkFavoriteRepository workFavoriteRepository;
    private final WorkRepository workRepository;

    public FavoriteDTO turnOn(Long userId, Long workId) {
        WorkEntity work = visibleWork(workId);
        try {
            favoriteWriter.insertIfAbsent(userId, work.getId());
        } catch (DataIntegrityViolationException e) {
            confirmAlreadyOn(userId, work.getId(), e);
        }
        return FavoriteDTO.on(work.getId());
    }

    /**
     * 넣기가 제약에 걸렸다 — <b>그 이유가 "이미 켜져 있다" 일 때만</b> 성공으로 친다.
     * 확인 없이 삼키면 다른 이유(예: 그 찰나에 곡이 지워져 FK 가 깨진 경우)까지 "켜졌다" 고 답하게 된다.
     * 행이 실제로 있으면 우리가 원하던 상태이므로 그대로 두고, {@code created_at} 은 건드리지 않는다.
     */
    private void confirmAlreadyOn(Long userId, Long workId, DataIntegrityViolationException e) {
        if (!workFavoriteRepository.existsByUserIdAndWorkId(userId, workId)) {
            throw e;
        }
        log.debug("즐겨찾기 켜기가 동시 요청과 겹쳤다 — 이미 켜져 있어 그대로 둔다 (userId={}, workId={})", userId, workId);
    }

    /** 꺼져 있는 것을 또 꺼도 오류가 아니다 — 없던 것을 껐을 뿐이다(02 §10-1). */
    @Transactional
    public void turnOff(Long userId, Long workId) {
        WorkEntity work = visibleWork(workId);
        workFavoriteRepository.deleteByUserIdAndWorkId(userId, work.getId());
    }

    /**
     * 숨긴 곡은 <b>없는 곡과 같게</b> 404 다(기획 §F2-6) — 사용자 화면 어디에도 나오지 않아야 하므로
     * "숨겨져 있어요" 라는 다른 답을 주지 않는다.
     */
    private WorkEntity visibleWork(Long workId) {
        return workRepository.findById(workId)
                .filter(work -> !work.isHidden())
                .orElseThrow(() -> EntityNotFoundException.of("곡", workId));
    }
}
