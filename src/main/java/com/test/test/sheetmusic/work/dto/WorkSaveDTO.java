package com.test.test.sheetmusic.work.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 곡 등록·수정 요청 (02 §4-8). 검증은 컨트롤러의 {@code @Valid} + 서비스 규칙.
 *
 * <p>문자열 상한은 02 §0-6 표 = 01_ERD 의 컬럼 길이. 넘으면 400 VALIDATION_ERROR 로 막는다
 * (검증이 없으면 DB 컬럼 길이 초과로 500 이 된다).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkSaveDTO {

    @NotNull(message = "작곡가를 선택해 주세요")
    private Long composerId;

    /** 비어 있어도 허용 — 보완 필요가 된다(02 §4-8). */
    @Size(max = 300, message = "300자를 넘을 수 없어요")
    private String titleKo;

    @Size(max = 300, message = "300자를 넘을 수 없어요")
    private String titleOriginal;

    /** 항목 하나하나에 상한이 걸린다 — 위반 시 field 는 {@code catalogNumbers[0]} 형태(02 §0-6). */
    private List<@Size(max = 100, message = "100자를 넘을 수 없어요") String> catalogNumbers;

    /** 항목 하나하나에 상한이 걸린다 — 위반 시 field 는 {@code aliases[0]} 형태(02 §0-6). */
    private List<@Size(max = 200, message = "200자를 넘을 수 없어요") String> aliases;

    private String level;

    @Size(max = 20, message = "20자를 넘을 수 없어요")
    private String compositionYear;

    @Size(max = 50, message = "50자를 넘을 수 없어요")
    private String musicalKey;

    @Size(max = 500, message = "500자를 넘을 수 없어요")
    private String movements;

    @Size(max = 500, message = "500자를 넘을 수 없어요")
    private String movementPageGuide;

    /**
     * 수록곡 안내 (02 §4-8, 2026-09-08 추가). 시드가 채우는 값이지만 수집으로 들어온 곡·오타 수정을 위해
     * 관리자도 편집할 수 있다. 공백·누락은 허용(null 로 저장).
     */
    @Size(max = 500, message = "500자를 넘을 수 없어요")
    private String collectionGuide;

    @Size(max = 500, message = "500자를 넘을 수 없어요")
    private String imslpUrl;

    private boolean hidden;
}
