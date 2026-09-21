package com.test.test.sheetmusic.edition.dto;

import com.test.test.sheetmusic.edition.EditionEntity;
import com.test.test.sheetmusic.edition.EditionKind;
import com.test.test.sheetmusic.edition.EditionScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판본 한 줄 설명 (02 §2-4) — 받은 악보의 {@code 받은 판본: 전체 악보 · 전곡 · 5쪽 · 1.1MB} 를 만드는 값만 담는다.
 *
 * <p>{@link EditionDTO} 를 쓰지 않는 이유: 그 DTO 의 절반(미리보기·저작권 표기·IMSLP 링크·{@code downloadable})은
 * 이 줄에 쓰이지 않고, <b>판본이 삭제된 뒤에도 남아야 하는 값</b>(01_ERD §3-12 스냅샷)은 여기 다섯뿐이다.
 * 문장은 화면이 만든다 — 서버는 재료만 준다(§2-2-1 과 같은 원칙).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditionBriefDTO {

    /** 지금도 살아 있는 판본이면 그 id. <b>삭제됐으면 null</b>(설명만 스냅샷으로 남아 있다). */
    private Long id;
    private EditionKind kind;
    private EditionScope scope;
    private Integer movementNumber;
    private Integer pageCount;
    private Long fileSize;

    /** 살아 있는 판본 — 파일이 교체돼 쪽수·크기가 달라졌으면 <b>지금 값</b>이 옳다(02 §10-3). */
    public static EditionBriefDTO from(EditionEntity edition, Long fileSize) {
        return EditionBriefDTO.builder()
                .id(edition.getId())
                .kind(edition.getKind())
                .scope(edition.getScope())
                .movementNumber(edition.getMovementNumber())
                .pageCount(edition.getPageCount())
                .fileSize(fileSize)
                .build();
    }
}
