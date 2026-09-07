package com.test.test.sheetmusic.work.repository;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 작곡가별 공개 곡 수 집계 프로젝션 (02 §3-5·§3-6) — 응답 DTO 가 아니라 조회 결과 값이다.
 * 작곡가 엔티티를 로드하지 않으려고 id 만 담는다(컨벤션 §1 "조회는 DTO 프로젝션").
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ComposerWorkCount {

    private Long composerId;
    /** {@code count(w)} 결과 — JPQL 생성자 표현식이 그대로 넘겨줄 수 있게 박싱 타입으로 둔다. */
    private Long workCount;
}
