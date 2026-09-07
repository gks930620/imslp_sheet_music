package com.test.test.sheetmusic.crawl;

import java.nio.file.Path;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link ImslpClient#downloadFile(String, Path)} 의 결과 — 임시 파일로 받은 바이트의 메타.
 * 검증(매직바이트·크기)과 저장 전략 업로드, 임시 파일 삭제는 호출자 책임.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImslpDownloadedFile {
    /** IMSLP 파일 번호 원문 ({@code "00014"}) */
    private String imslpFileId;
    /**
     * 표시용 원본 파일명 ({@code IMSLP00014-Beethoven,_L.v._-_Piano_Sonata_14.pdf}).
     * 원격이 준 값을 다듬은 것이라 <b>경로 생성에 쓰지 않는다</b> — 임시 파일은 {@code path} 가 가리킨다.
     */
    private String fileName;
    /** 응답 Content-Type (정상이면 {@code application/pdf}) */
    private String contentType;
    /** 응답 Content-Length (없으면 실제 받은 바이트 수) */
    private long declaredSize;
    /** 받은 바이트가 기록된 임시 파일 */
    private Path path;
}
