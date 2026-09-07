package com.test.test.sheetmusic.edition.dto;

import com.test.test.sheetmusic.edition.EditionKind;
import com.test.test.sheetmusic.edition.EditionScope;
import com.test.test.sheetmusic.edition.KoreaCopyright;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 사용자 화면 판본 (02 §2-3). */
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class EditionDTO {

    /** 큰 파일 경고 기준 (02 §2-3 largeFile). */
    public static final long LARGE_FILE_BYTES = 20L * 1024 * 1024;

    private Long id;
    private EditionKind kind;
    private EditionScope scope;
    private Integer movementNumber;
    private String sectionLabel;
    private Integer pageCount;
    private Long fileSize;
    private boolean hasFile;
    private String previewUrl;
    private String publisher;
    private Integer publishYear;
    private String plateNumber;
    private String editor;
    private String arranger;
    private String scanner;
    private KoreaCopyright koreaCopyright;
    private String imslpCopyrightText;
    private String ccLicenseName;
    private String ccAttribution;
    private String imslpFileUrl;
    private boolean downloadable;
    private boolean largeFile;
    private String downloadUrl;
}
