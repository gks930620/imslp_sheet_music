package com.test.test.sheetmusic.member.dto;

import com.test.test.sheetmusic.edition.dto.EditionBriefDTO;
import com.test.test.sheetmusic.member.RedownloadState;
import com.test.test.sheetmusic.work.dto.WorkSummaryDTO;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 받은 악보 한 줄 (02 §10-3) — 곡 단위다. 같은 곡을 몇 번 받아도 한 줄이고 날짜는 가장 최근에 받은 날이다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivedWorkDTO {

    private WorkSummaryDTO work;

    /** 가장 최근에 받은 시각. 화면이 {@code 오늘 받음}·{@code 9월 12일 받음} 으로 옮긴다(00 §4). */
    private Instant downloadedAt;

    /**
     * "받은 판본: …" 줄의 재료 (02 §2-4). 판본이 살아 있으면 <b>지금 값</b>, 삭제됐으면 <b>스냅샷</b>(id = null).
     * 스냅샷조차 없으면 null 이고 화면이 줄을 생략한다.
     */
    private EditionBriefDTO receivedEdition;

    private RedownloadState redownloadState;

    /** "다시 받기" 버튼이 실제로 여는 주소. ③-b 면 null 이라 버튼이 없다. */
    private String redownloadUrl;

    /** "지금 추천 판본: …" 줄 — ③-a 일 때만 값이 있다. */
    private EditionBriefDTO alternativeEdition;
}
