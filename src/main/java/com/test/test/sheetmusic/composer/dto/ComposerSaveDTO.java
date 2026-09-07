package com.test.test.sheetmusic.composer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 작곡가 등록·수정 요청 (02 §4-4).
 *
 * <p>문자열 상한은 02 §0-6 표 = 01_ERD 의 컬럼 길이. 넘으면 400 VALIDATION_ERROR 로 막는다
 * (검증이 없으면 DB 컬럼 길이 초과로 500 이 된다).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComposerSaveDTO {

    @NotBlank(message = "한글 표기를 입력해 주세요")
    @Size(max = 100, message = "100자를 넘을 수 없어요")
    private String nameKo;

    @NotBlank(message = "원어 표기를 입력해 주세요")
    @Size(max = 200, message = "200자를 넘을 수 없어요")
    private String nameOriginal;

    /** 항목 하나하나에 상한이 걸린다 — 위반 시 field 는 {@code aliases[0]} 형태(02 §0-6). */
    private List<@Size(max = 200, message = "200자를 넘을 수 없어요") String> aliases;

    @Min(value = 1000, message = "생년이 올바르지 않아요")
    @Max(value = 2100, message = "생년이 올바르지 않아요")
    private Integer birthYear;

    @Min(value = 1000, message = "몰년이 올바르지 않아요")
    @Max(value = 2100, message = "몰년이 올바르지 않아요")
    private Integer deathYear;

    @Size(max = 100, message = "100자를 넘을 수 없어요")
    private String nationality;

    @Size(max = 500, message = "500자를 넘을 수 없어요")
    private String imslpUrl;
}
