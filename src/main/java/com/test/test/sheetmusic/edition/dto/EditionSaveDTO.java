package com.test.test.sheetmusic.edition.dto;

import com.test.test.sheetmusic.edition.EditionKind;
import com.test.test.sheetmusic.edition.EditionScope;
import com.test.test.sheetmusic.edition.KoreaCopyright;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 판본 등록·수정 요청 (02 §5-2/§5-3).
 *
 * <p>문자열 상한은 02 §0-6 표 = 01_ERD 의 컬럼 길이. 넘으면 400 VALIDATION_ERROR 로 막는다
 * (검증이 없으면 DB 컬럼 길이 초과로 500 이 된다).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditionSaveDTO {

    /** {@code null} 이면 파일 없는 판본(정보만). */
    private Long fileId;
    private Long previewFileId;

    @NotNull(message = "판본 종류를 선택해 주세요")
    private EditionKind kind;

    @NotNull(message = "판본 범위를 선택해 주세요")
    private EditionScope scope;

    private Integer movementNumber;
    private Integer pageCount;

    @Size(max = 300, message = "300자를 넘을 수 없어요")
    private String publisher;

    private Integer publishYear;

    @Size(max = 100, message = "100자를 넘을 수 없어요")
    private String plateNumber;

    @Size(max = 200, message = "200자를 넘을 수 없어요")
    private String editor;

    @Size(max = 200, message = "200자를 넘을 수 없어요")
    private String arranger;

    @Size(max = 200, message = "200자를 넘을 수 없어요")
    private String scanner;

    @Size(max = 500, message = "500자를 넘을 수 없어요")
    private String imslpFileUrl;

    @Size(max = 200, message = "200자를 넘을 수 없어요")
    private String imslpCopyrightText;

    @NotNull(message = "저작권 판정을 선택해 주세요")
    private KoreaCopyright koreaCopyright;

    @Size(max = 1000, message = "1000자를 넘을 수 없어요")
    private String copyrightNote;

    @Size(max = 100, message = "100자를 넘을 수 없어요")
    private String ccLicenseName;

    @Size(max = 200, message = "200자를 넘을 수 없어요")
    private String ccAttribution;
}
