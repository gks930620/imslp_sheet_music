package com.test.test.sheetmusic.crawl;

import com.test.test.sheetmusic.edition.EditionKind;
import com.test.test.sheetmusic.edition.EditionScope;
import com.test.test.sheetmusic.edition.LicenseCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 작품 페이지에서 읽은 판본 1개(= PDF 파일 1개). 파싱 결과일 뿐 엔티티가 아니다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedEdition {

    private String imslpFileId;
    private String imslpOriginalFileName;
    private String imslpFileUrl;
    private String imslpDescription;
    private EditionKind kind;
    private EditionScope scope;
    private Integer movementNumber;
    private String sectionLabel;
    private Integer pageCount;
    private Integer imslpDownloadCount;
    private String editor;
    private String arranger;
    private String scanner;
    private String publisher;
    private Integer publishYear;
    private String plateNumber;
    private String imslpCopyrightText;
    private LicenseCode imslpLicenseCode;
}
