package com.test.test.sheetmusic.edition;

import com.test.test.sheetmusic.work.WorkEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 판본 = IMSLP 파일 1개 = PDF 1개 (01_ERD §3-6). */
@Entity
@Table(name = "edition",
        uniqueConstraints = @UniqueConstraint(name = "uk_edition_imslp_file_id", columnNames = "imslp_file_id"),
        indexes = {
                @Index(name = "idx_edition_work", columnList = "work_id"),
                @Index(name = "idx_edition_korea_copyright", columnList = "korea_copyright"),
                @Index(name = "idx_edition_pdf_file", columnList = "pdf_file_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EditionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_id", nullable = false)
    private WorkEntity work;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 30, nullable = false)
    private EditionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 30, nullable = false)
    private EditionScope scope;

    @Column(name = "movement_number")
    private Integer movementNumber;

    @Column(name = "section_label", length = 200)
    private String sectionLabel;

    @Column(name = "imslp_description", length = 300)
    private String imslpDescription;

    @Column(name = "publisher", length = 300)
    private String publisher;

    @Column(name = "publish_year")
    private Integer publishYear;

    @Column(name = "plate_number", length = 100)
    private String plateNumber;

    @Column(name = "editor", length = 200)
    private String editor;

    @Column(name = "arranger", length = 200)
    private String arranger;

    @Column(name = "scanner", length = 200)
    private String scanner;

    @Column(name = "imslp_file_id", length = 20)
    private String imslpFileId;

    @Column(name = "imslp_original_file_name", length = 300)
    private String imslpOriginalFileName;

    @Column(name = "imslp_file_url", length = 500)
    private String imslpFileUrl;

    @Column(name = "imslp_copyright_text", length = 200)
    private String imslpCopyrightText;

    @Enumerated(EnumType.STRING)
    @Column(name = "imslp_license_code", length = 30)
    private LicenseCode imslpLicenseCode;

    @Column(name = "imslp_download_count")
    private Integer imslpDownloadCount;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "pdf_file_id")
    private Long pdfFileId;

    @Column(name = "preview_file_id")
    private Long previewFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "korea_copyright", length = 30, nullable = false)
    private KoreaCopyright koreaCopyright = KoreaCopyright.UNKNOWN;

    @Column(name = "copyright_note", length = 1000)
    private String copyrightNote;

    @Column(name = "copyright_judged_at")
    private Instant copyrightJudgedAt;

    @Column(name = "copyright_judged_by", length = 100)
    private String copyrightJudgedBy;

    @Column(name = "cc_license_name", length = 100)
    private String ccLicenseName;

    @Column(name = "cc_attribution", length = 200)
    private String ccAttribution;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_fetch_status", length = 30)
    private FileFetchStatus fileFetchStatus;

    @Column(name = "file_fetch_error", length = 300)
    private String fileFetchError;

    @Column(name = "file_fetched_at")
    private Instant fileFetchedAt;

    @Column(name = "download_count", nullable = false)
    private long downloadCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private EditionEntity(WorkEntity work, EditionKind kind, EditionScope scope, Integer movementNumber,
                          String sectionLabel, String imslpDescription, String publisher, Integer publishYear,
                          String plateNumber, String editor, String arranger, String scanner,
                          String imslpFileId, String imslpOriginalFileName, String imslpFileUrl,
                          String imslpCopyrightText, LicenseCode imslpLicenseCode, Integer imslpDownloadCount,
                          Integer pageCount, KoreaCopyright koreaCopyright, String copyrightNote,
                          String ccLicenseName, String ccAttribution) {
        this.work = work;
        this.kind = kind;
        this.scope = scope;
        this.movementNumber = movementNumber;
        this.sectionLabel = sectionLabel;
        this.imslpDescription = imslpDescription;
        this.publisher = publisher;
        this.publishYear = publishYear;
        this.plateNumber = plateNumber;
        this.editor = editor;
        this.arranger = arranger;
        this.scanner = scanner;
        this.imslpFileId = imslpFileId;
        this.imslpOriginalFileName = imslpOriginalFileName;
        this.imslpFileUrl = imslpFileUrl;
        this.imslpCopyrightText = imslpCopyrightText;
        this.imslpLicenseCode = imslpLicenseCode;
        this.imslpDownloadCount = imslpDownloadCount;
        this.pageCount = pageCount;
        this.koreaCopyright = koreaCopyright == null ? KoreaCopyright.UNKNOWN : koreaCopyright;
        this.copyrightNote = copyrightNote;
        this.ccLicenseName = ccLicenseName;
        this.ccAttribution = ccAttribution;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // ===== 도메인 =====

    /** 관리자 판본 저장(§5-2/5-3)의 편집 가능한 필드만 바꾼다. IMSLP 수집값은 건드리지 않는다. */
    public void updateFromAdmin(EditionKind kind, EditionScope scope, Integer movementNumber, Integer pageCount,
                                String publisher, Integer publishYear, String plateNumber, String editor,
                                String arranger, String scanner, String imslpFileUrl, String imslpCopyrightText,
                                String ccLicenseName, String ccAttribution) {
        this.kind = kind;
        this.scope = scope;
        this.movementNumber = scope == EditionScope.MOVEMENT ? movementNumber : null;
        this.pageCount = pageCount;
        this.publisher = publisher;
        this.publishYear = publishYear;
        this.plateNumber = plateNumber;
        this.editor = editor;
        this.arranger = arranger;
        this.scanner = scanner;
        this.imslpFileUrl = imslpFileUrl;
        this.imslpCopyrightText = imslpCopyrightText;
        this.ccLicenseName = ccLicenseName;
        this.ccAttribution = ccAttribution;
    }

    /** 수집이 다시 읽은 메타데이터로 갱신(파일·판정·메모는 보존 — 01_ERD §7). */
    public void refreshFromCrawl(EditionKind kind, EditionScope scope, Integer movementNumber, String sectionLabel,
                                 String imslpDescription, String publisher, Integer publishYear, String plateNumber,
                                 String editor, String arranger, String scanner, String imslpOriginalFileName,
                                 String imslpFileUrl, String imslpCopyrightText, LicenseCode imslpLicenseCode,
                                 Integer imslpDownloadCount, Integer htmlPageCount) {
        this.kind = kind;
        this.scope = scope;
        this.movementNumber = movementNumber;
        this.sectionLabel = sectionLabel;
        this.imslpDescription = imslpDescription;
        this.publisher = publisher;
        this.publishYear = publishYear;
        this.plateNumber = plateNumber;
        this.editor = editor;
        this.arranger = arranger;
        this.scanner = scanner;
        this.imslpOriginalFileName = imslpOriginalFileName;
        this.imslpFileUrl = imslpFileUrl;
        this.imslpCopyrightText = imslpCopyrightText;
        this.imslpLicenseCode = imslpLicenseCode;
        this.imslpDownloadCount = imslpDownloadCount;
        // 파일을 받은 판본의 쪽수는 PDFBox 값이 이긴다 — 파일이 없을 때만 HTML 값으로 채운다.
        if (this.pdfFileId == null) {
            this.pageCount = htmlPageCount;
        }
    }

    public void changeFiles(Long pdfFileId, Long previewFileId) {
        this.pdfFileId = pdfFileId;
        this.previewFileId = previewFileId;
    }

    public void changePageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    /** 수집·받아오기로 파일을 얻었을 때. 쪽수는 PDFBox 값이 우선한다. */
    public void attachFetchedFile(Long pdfFileId, Long previewFileId, Integer pageCount, Instant fetchedAt) {
        this.pdfFileId = pdfFileId;
        this.previewFileId = previewFileId;
        if (pageCount != null) {
            this.pageCount = pageCount;
        }
        this.fileFetchedAt = fetchedAt;
        this.fileFetchStatus = null;
        this.fileFetchError = null;
    }

    /** 대기함 판정(§5-9/§5-10) — 언제나 판정자·시각을 남긴다. */
    public void judgeCopyright(KoreaCopyright koreaCopyright, String copyrightNote, String judgedBy, Instant judgedAt) {
        this.koreaCopyright = koreaCopyright;
        this.copyrightNote = copyrightNote;
        this.copyrightJudgedBy = judgedBy;
        this.copyrightJudgedAt = judgedAt;
    }

    /** 관리자 저장 시: 판정이 바뀐 경우에만 판정자·시각을 갱신한다(02 §5-3). */
    public void applyCopyrightOnSave(KoreaCopyright koreaCopyright, String copyrightNote,
                                     String judgedBy, Instant judgedAt) {
        if (this.koreaCopyright != koreaCopyright) {
            this.copyrightJudgedBy = judgedBy;
            this.copyrightJudgedAt = judgedAt;
        }
        this.koreaCopyright = koreaCopyright;
        this.copyrightNote = copyrightNote;
    }

    public void markFetchQueued() {
        this.fileFetchStatus = FileFetchStatus.QUEUED;
        this.fileFetchError = null;
    }

    public void markFetching() {
        this.fileFetchStatus = FileFetchStatus.FETCHING;
    }

    public void markFetchFailed(String error) {
        this.fileFetchStatus = FileFetchStatus.FAILED;
        this.fileFetchError = error;
    }

    public void increaseDownloadCount() {
        this.downloadCount++;
    }

    public boolean hasFile() {
        return this.pdfFileId != null;
    }

    public boolean isDownloadable() {
        return hasFile() && this.koreaCopyright == KoreaCopyright.FREE;
    }

    /** 추천 후보 자격 (01_ERD §3-3). */
    public boolean isCandidateEligible() {
        return this.kind == EditionKind.COMPLETE_SCORE && this.scope == EditionScope.COMPLETE && hasFile();
    }
}
