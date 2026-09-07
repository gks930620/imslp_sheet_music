package com.test.test.sheetmusic.edition.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.test.test.sheetmusic.edition.FileFetchStatus;
import com.test.test.sheetmusic.edition.LicenseCode;
import com.test.test.sheetmusic.work.WorkStatus;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 관리자 판본 (02 §4-7 AdminEditionDTO) = {@link EditionDTO} + IMSLP 수집값·판정·받아오기 상태.
 *
 * <p>{@code isRecommended}/{@code isCandidate} 는 JSON 에도 그 이름 그대로 나가야 한다(02 §4-7).
 * 필드에 {@link JsonProperty} 를 붙이고 Lombok 게터를 함께 두면, 게터가 {@code recommended} 라는
 * 계약에 없는 두 번째 프로퍼티를 만들어 낸다. 그래서 이 두 개만 Lombok 게터를 끄고
 * 직접 쓴 게터에 이름을 못 박아 프로퍼티가 하나만 나가게 한다.
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AdminEditionDTO extends EditionDTO {

    private String imslpFileId;
    private String imslpOriginalFileName;
    private String imslpDescription;
    private LicenseCode imslpLicenseCode;
    private Integer imslpDownloadCount;
    private Long pdfFileId;
    private Long previewFileId;
    private String copyrightNote;
    private Instant copyrightJudgedAt;
    private String copyrightJudgedBy;
    private FileFetchStatus fileFetchStatus;
    private String fileFetchError;
    private Instant fileFetchedAt;
    private long downloadCount;

    @Getter(AccessLevel.NONE)
    private boolean isRecommended;

    @Getter(AccessLevel.NONE)
    private boolean isCandidate;

    private Instant createdAt;
    private Instant updatedAt;

    /** §5-2 ~ §5-4·§5-9 응답에만 실린다(곡 상세의 editions[] 에는 null). */
    private Long workId;
    private WorkStatus workStatus;

    @JsonProperty("isRecommended")
    public boolean isRecommended() {
        return isRecommended;
    }

    @JsonProperty("isCandidate")
    public boolean isCandidate() {
        return isCandidate;
    }

    /** 단건 응답에 곡 맥락을 덧붙인다 (02 §5-2 workId, §5-4 workStatus). */
    public void setWorkContext(Long workId, WorkStatus workStatus) {
        this.workId = workId;
        this.workStatus = workStatus;
    }
}
