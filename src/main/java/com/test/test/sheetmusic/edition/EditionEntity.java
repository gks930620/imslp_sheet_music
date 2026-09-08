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
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "kind", length = 30, nullable = false)
    private EditionKind kind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
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
    @JdbcTypeCode(SqlTypes.VARCHAR)
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
    @JdbcTypeCode(SqlTypes.VARCHAR)
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
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "file_fetch_status", length = 30)
    private FileFetchStatus fileFetchStatus;

    @Column(name = "file_fetch_error", length = 300)
    private String fileFetchError;

    @Column(name = "file_fetched_at")
    private Instant fileFetchedAt;

    /**
     * 관리자가 §5-3 으로 이 판본을 저장한 마지막 시각 (01_ERD §3-6, 2026-09-07 추가).
     * NULL = 아직 사람이 손대지 않음(수집이 만든 그대로). 재수집이 관리자 편집 필드를 덮을지 판단하는 유일한 근거(02 §6-12).
     * <b>§5-9 저작권 판정으로는 찍지 않는다</b> — 판정은 편집이 아니고, 표기 원문은 계속 IMSLP 를 따라가야
     * 라이선스 강등 회수(02 §6-12 (2))가 작동한다.
     */
    @Column(name = "admin_edited_at")
    private Instant adminEditedAt;

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

    /**
     * 관리자 판본 저장(§5-2/5-3)의 편집 가능한 필드만 바꾼다. IMSLP 수집값(파일 id·다운로드 수 등)은 건드리지 않는다.
     *
     * <p>단 {@code imslpLicenseCode} 는 요청 필드가 아니라 <b>원문의 정규화 캐시</b>이므로 원문을 저장하는
     * 이 자리에서 함께 도출한다(02 §5-2). 그러지 않으면 같은 {@code "Public Domain"} 인 두 판본이
     * 출처(수집/수기)에 따라 화면에서 다르게 보이고, 자동 판정(§5-11)이 코드 하나로 판단할 수 없다.
     */
    public void updateFromAdmin(EditionKind kind, EditionScope scope, Integer movementNumber, Integer pageCount,
                                String publisher, Integer publishYear, String plateNumber, String editor,
                                String arranger, String scanner, String imslpFileUrl, String imslpCopyrightText,
                                String ccLicenseName, String ccAttribution, Instant editedAt) {
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
        this.imslpLicenseCode = LicenseCode.fromText(imslpCopyrightText);
        this.ccLicenseName = ccLicenseName;
        this.ccAttribution = ccAttribution;
        // 사람이 손댄 판본이라는 표시 — 재수집이 이 값을 되돌리지 않게 한다 (02 §6-12 (1)).
        this.adminEditedAt = editedAt;
    }

    /**
     * 수집이 다시 읽은 메타데이터로 갱신 (01_ERD §7, 02 §6-12).
     *
     * <ul>
     *   <li><b>IMSLP 거울 필드</b>(section_label·imslp_description·원본 파일명·다운로드 수)는 언제나 갱신한다 —
     *       관리자가 편집할 수 없는 값이라 충돌이 없다.</li>
     *   <li><b>관리자 편집 필드</b>는 {@code adminEditedAt} 이 NULL 일 때만 갱신한다. 한 번이라도 §5-3 으로
     *       저장된 판본을 덮으면 관리 작업이 무의미해지고, 특히 {@code scope}/{@code kind} 가 되돌아가면
     *       추천 판본이 편곡·악장 발췌가 된다.</li>
     *   <li><b>파일·판정</b>은 보존한다 — 단 재배포 불가로 바뀐 표기의 자동 판정만 회수한다(아래).</li>
     * </ul>
     */
    public void refreshFromCrawl(EditionKind kind, EditionScope scope, Integer movementNumber, String sectionLabel,
                                 String imslpDescription, String publisher, Integer publishYear, String plateNumber,
                                 String editor, String arranger, String scanner, String imslpOriginalFileName,
                                 String imslpFileUrl, String imslpCopyrightText, LicenseCode imslpLicenseCode,
                                 Integer imslpDownloadCount, Integer htmlPageCount) {
        this.sectionLabel = sectionLabel;
        this.imslpDescription = imslpDescription;
        this.imslpOriginalFileName = imslpOriginalFileName;
        this.imslpDownloadCount = imslpDownloadCount;

        if (this.adminEditedAt == null) {
            this.kind = kind;
            this.scope = scope;
            this.movementNumber = movementNumber;
            this.publisher = publisher;
            this.publishYear = publishYear;
            this.plateNumber = plateNumber;
            this.editor = editor;
            this.arranger = arranger;
            this.scanner = scanner;
            this.imslpFileUrl = imslpFileUrl;
            this.imslpCopyrightText = imslpCopyrightText;
            this.imslpLicenseCode = imslpLicenseCode;
            // 파일을 받은 판본의 쪽수는 PDFBox 값이 이긴다 — 파일이 없을 때만 HTML 값으로 채운다.
            if (this.pdfFileId == null) {
                this.pageCount = htmlPageCount;
            }
        }
        revokeAutoJudgementIfNotRedistributable();
    }

    /**
     * 재수집 결과가 <b>재배포 불가</b> 표기가 되었을 때 자동 판정(§5-11)으로 열어 둔 판본만 다시 닫는다 (02 §6-12 (2)).
     *
     * <p>표기가 {@code CC BY-SA → CC BY-NC-ND} 로 바뀌었는데 계속 FREE 로 두면 재배포하면 안 되는 파일을
     * 우리 서버가 계속 내려준다(법적 리스크). <b>사람이 내린 판정은 뒤집지 않는다</b> — 사람은 표기 외의
     * 근거(작곡가 사후 연수 등)로 판단했을 수 있다. 파일 바이트는 지우지 않는다: UNKNOWN 이면 §3-4 가 닫힌다.
     *
     * <p>판단 기준은 <b>이 갱신이 끝난 뒤의 {@code imslpLicenseCode}</b> 다. 관리자가 표기를 고친 판본은
     * 그 값이 그대로 유지되므로 관리자 입력이 계속 기준이 된다.
     */
    private void revokeAutoJudgementIfNotRedistributable() {
        if (this.koreaCopyright == KoreaCopyright.FREE
                && CopyrightAutoJudge.AUTO_JUDGED_BY.equals(this.copyrightJudgedBy)
                && !LicenseCode.isRedistributable(this.imslpLicenseCode)) {
            revertAutoJudgement();
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

    /**
     * 자동 판정 되돌리기 (02 §5-12) — <b>판정만</b> 지운다.
     * 추천 판본 지정은 건드리지 않는다: 추천은 유지되지만 판정이 UNKNOWN 이라 곡 상태가 즉시 UNKNOWN 이 되고
     * 다운로드는 그 순간 닫힌다(위험 차단 목적은 그것으로 100% 달성된다). 자동 지정과 사람이 지정한 추천을
     * 구분할 컬럼이 없어서, 추천까지 지우면 사람 작업을 지우게 된다.
     */
    public void revertAutoJudgement() {
        this.koreaCopyright = KoreaCopyright.UNKNOWN;
        this.copyrightNote = null;
        this.copyrightJudgedBy = null;
        this.copyrightJudgedAt = null;
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

    /**
     * 받아오기 요청을 끝난 것으로만 표시한다 (02 §5-7) — 받아온 파일을 붙이지 않고 버린 경우.
     * 관리자가 그 사이에 직접 파일을 붙였으므로 실패가 아니다(FAILED 로 두면 화면에 붉은 오류가 남는다).
     */
    public void clearFetchRequest() {
        this.fileFetchStatus = null;
        this.fileFetchError = null;
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

    /** 이 판본이 자기 곡의 추천으로 지정돼 있는가 — 추천의 단일 기준은 곡 쪽 필드다(02 §4-7). */
    public boolean isRecommendedByWork() {
        EditionEntity recommended = this.work == null ? null : this.work.getRecommendedEdition();
        return recommended != null && recommended.getId() != null && recommended.getId().equals(this.id);
    }

    /**
     * 추천으로 지정할 때 관리자에게 알릴 것 — <b>해당되는 것이 전부</b>, 고정 순서 (02 §5-6, 기획 §11-2 ①).
     * 경고는 안내이지 거부가 아니다(막는 것은 파일 없음 하나뿐이다). 해당 없으면 빈 목록이다.
     */
    public List<RecommendWarning> recommendWarnings() {
        List<RecommendWarning> warnings = new ArrayList<>();
        if (this.koreaCopyright != KoreaCopyright.FREE) {
            warnings.add(RecommendWarning.NOT_DOWNLOADABLE);
        }
        if (this.kind == EditionKind.ARRANGEMENT) {
            warnings.add(RecommendWarning.ARRANGEMENT);
        }
        if (this.scope == EditionScope.MOVEMENT) {
            warnings.add(RecommendWarning.PARTIAL_SCOPE);
        }
        return warnings;
    }

    /**
     * 다운로드 파일명 끝에 붙는 범위·편곡 표시 (02 §3-4, 기획 §11-2 ③) — 예: {@code " 편곡 2악장"}.
     *
     * <p>파일은 사용자 컴퓨터에 남아 몇 주 뒤 레슨 직전에 열린다. 그때 화면은 없고 파일 이름만 있으므로,
     * 전곡·전체 악보가 아니면 그 사실이 이름에 남아야 한다. 붙일 것이 없으면 빈 문자열.
     * {@code PARTS}(파트보)에는 붙이지 않는다 — 계약에 없는 새 문구를 서버가 만들지 않기 위해서다.
     */
    public String downloadNameSuffix() {
        StringBuilder suffix = new StringBuilder();
        if (this.kind == EditionKind.ARRANGEMENT) {
            suffix.append(" 편곡");
        }
        if (this.scope == EditionScope.MOVEMENT) {
            suffix.append(this.movementNumber == null ? " 발췌" : " " + this.movementNumber + "악장");
        }
        return suffix.toString();
    }
}
