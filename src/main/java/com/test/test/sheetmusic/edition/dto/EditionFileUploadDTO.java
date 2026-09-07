package com.test.test.sheetmusic.edition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PDF 업로드 응답 (02 §5-1). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditionFileUploadDTO {

    private Long fileId;
    private Long previewFileId;
    private String fileName;
    private long fileSize;
    private Integer pageCount;
    private String previewUrl;
}
