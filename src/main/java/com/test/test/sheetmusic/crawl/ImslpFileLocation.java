package com.test.test.sheetmusic.crawl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link ImslpClient#resolveFileUrl(String)} 의 결과 — 대기 페이지에서 읽어낸 "이제부터 카운트다운이 도는" 파일 주소.
 *
 * <p>이 값이 생긴 시점이 곧 <b>브라우저가 15초 카운트다운을 시작하는 시점</b>이다.
 * 그래서 대기({@code ImslpGate.awaitFileWait()})는 이 값을 받은 <b>뒤에</b> 걸고,
 * 그 다음 {@link ImslpClient#downloadResolvedFile(ImslpFileLocation, java.nio.file.Path)} 로 파일을 친다(03 §3, 기획 §9-1).
 *
 * <p>URL 은 요청마다 호스트가 바뀌는 1회용 주소라 <b>저장하거나 재사용하지 않는다</b> — 받은 즉시 쓰고 버린다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImslpFileLocation {
    /** IMSLP 파일 번호 원문 (앞자리 0 포함, 예: {@code "00014"}) */
    private String imslpFileId;
    /**
     * 대기 페이지 {@code span#sm_dl_wait[data-id]} 가 준 파일 호스트 절대 주소
     * (예: {@code https://ks15.imslp.org/files/imglnks/usimg/.../IMSLP00014-....pdf}).
     * 호스트가 요청마다 바뀌므로 우리가 조립하지 않는다.
     */
    private String fileUrl;
}
