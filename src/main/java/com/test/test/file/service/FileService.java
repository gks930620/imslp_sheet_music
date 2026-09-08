package com.test.test.file.service;

import com.test.test.common.exception.AccessDeniedException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.community.CommunityEntity;
import com.test.test.community.repository.CommunityRepository;
import com.test.test.file.dto.FileDetailDTO;
import com.test.test.file.entity.FileEntity;
import com.test.test.file.entity.RefType;
import com.test.test.file.entity.Usage;
import com.test.test.file.repository.FileRepository;
import com.test.test.file.strategy.FileStorageStrategy.FileUploadResult;
import com.test.test.file.util.FileUtil;
import com.test.test.jwt.entity.UserEntity;
import com.test.test.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FileService {

    // 본문 HTML에서 /uploads/{저장파일명} 참조를 추출하는 패턴 (§5-3-1 ① reconcile)
    private static final Pattern UPLOADS_REF = Pattern.compile("/uploads/([^\\s\"'<>()\\\\]+)");
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String PDF_EXTENSION = ".pdf";
    /** 역할 문자열에 ROLE_ 접두가 없다 (02 §0-3 — SecurityConfig 도 hasAuthority 로 건다). */
    private static final String ADMIN_AUTHORITY = "ADMIN";

    private final FileRepository fileRepository;
    private final FileUtil fileUtil;
    private final StoredBytesDeleter storedBytesDeleter;
    private final CommunityRepository communityRepository;
    private final UserRepository userRepository;
    private final EditionPreviewGate editionPreviewGate;

    /** orphan(미연결 임시) 파일 유예시간(시간). 이 시간이 지난 refId=0 파일만 배치가 정리한다(§5-3-1 ③). */
    @Value("${app.file.orphan-grace-hours:24}")
    private long orphanGraceHours;

    /**
     * 파일 경로 조회 (통합 검색 - QueryDSL 동적 쿼리)
     * @param refId 참조 ID
     * @param refType COMMUNITY, USER
     * @param usage THUMBNAIL, IMAGES, ATTACHMENT (선택, null 가능)
     * @return 파일 웹 경로 리스트 (/uploads/{저장파일명})
     */
    public List<String> getFilePaths(Long refId, String refType, String usage) {
        RefType type = RefType.valueOf(refType);
        if (isEditionScoped(type)) {
            return List.of();
        }
        Usage usageType = usage != null && !usage.isEmpty()
                ? Usage.valueOf(usage)
                : null;

        // QueryDSL 동적 쿼리 실행
        List<FileEntity> files = fileRepository.searchFiles(refId, type, usageType);

        // DB에 저장된 filePath 그대로 반환 (/uploads/{저장파일명})
        return files.stream()
                .map(FileEntity::getFilePath)
                .toList();
    }

    /**
     * 파일 업로드 (물리 저장 + DB 저장 통합, 1 API = 1 Service 메서드)
     * - 물리 저장 이전에 소유권을 먼저 검증하여 미인가 요청 시 고아 파일이 생기지 않도록 한다.
     * @param files    업로드할 파일들
     * @param refId    참조 ID (임시 업로드는 0)
     * @param refType  참조 타입
     * @param usage    파일 용도
     * @param username 요청자(인증 주체) — 소유권 검증에 사용
     * @return 저장된 파일 웹 경로 리스트 (/uploads/{저장파일명})
     */
    @Transactional
    public List<String> upload(List<MultipartFile> files, Long refId,
                               RefType refType, Usage usage, String username) {
        // 인가(Authorization) 검증: 남의 리소스에 파일을 붙이는 IDOR 방지
        verifyOwnership(refType, refId, username);

        // 물리적 파일 저장 (검증 통과 후)
        List<FileUploadResult> uploadResults = files.stream()
                .filter(file -> !file.isEmpty())
                .map(fileUtil::saveFile)
                .toList();

        log.info("파일 물리적 저장 완료 - 파일 수: {}", uploadResults.size());

        return uploadResults.stream()
                .map(result -> {
                    FileEntity fileEntity = FileEntity.builder()
                            .originalFileName(result.getOriginalFilename())
                            .storedFileName(result.getStoredFilename())
                            .filePath(result.getWebPath())  // 웹 경로 저장 (/uploads/{저장파일명})
                            .fileSize(result.getFileSize())
                            .contentType(result.getContentType())
                            .refId(refId)
                            .refType(refType)
                            .fileUsage(usage)
                            .uploadedBy(username)  // 임시(refId=0) 파일의 소유권 판별용
                            .build();

                    fileRepository.save(fileEntity);

                    log.info("파일 정보 DB 저장 완료 - refId: {}, refType: {}, 파일명: {}, 경로: {}",
                            refId, refType, result.getStoredFilename(), result.getWebPath());

                    return result.getWebPath();  // /uploads/{저장파일명} 반환
                })
                .toList();
    }

    /**
     * 본문 이미지 reconcile (§5-3-1 ①) — 글 저장(create/update) 직후 호출.
     * <p>본문 HTML의 {@code /uploads/{저장파일명}} 을 파싱해, 실제로 본문에 쓰인 임시 이미지(refId=0)만
     * {@code refId=글ID}(usage=IMAGES)로 연결하고, 본문에서 빠진 기존 연결 이미지는 refId=0으로 되돌린다.
     * (되돌려진 초안은 유예시간 경과 후 orphan 배치가 정리 — ③)
     * <p>순수 DB 작업이므로 글 저장 트랜잭션에 합류한다(§1).
     * @param postId  글 ID
     * @param content 본문 HTML
     */
    @Transactional
    public void reconcileBodyImages(Long postId, String content) {
        Set<String> referenced = extractStoredNames(content);

        // ① 본문에서 빠진 기존 연결 이미지 → 연결 해제(refId=0)
        List<FileEntity> currentlyLinked =
                fileRepository.searchFiles(postId, RefType.COMMUNITY, Usage.IMAGES);
        for (FileEntity file : currentlyLinked) {
            if (!referenced.contains(file.getStoredFileName())) {
                file.unlink();
            }
        }

        // ② 본문에 쓰인 임시 이미지(refId=0) → 글ID로 연결
        if (!referenced.isEmpty()) {
            List<FileEntity> drafts = fileRepository
                    .findByFileUsageAndRefIdAndStoredFileNameIn(Usage.IMAGES, 0L, referenced);
            for (FileEntity draft : drafts) {
                draft.linkTo(postId);
            }
        }
    }

    /** 본문 HTML에서 참조된 {@code /uploads/{저장파일명}} 의 저장파일명 집합을 추출한다. */
    private Set<String> extractStoredNames(String content) {
        Set<String> names = new LinkedHashSet<>();
        if (content == null || content.isEmpty()) {
            return names;
        }
        Matcher matcher = UPLOADS_REF.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * 글 삭제 연동 (§5-3-1 ②) — 글에 연결된 파일(IMAGES+ATTACHMENT)의 메타행을 hard delete 하고,
     * 저장 바이트(외부 I/O)는 트랜잭션 커밋 후 삭제한다(§1).
     * @param refId   글 ID
     * @param refType 참조 타입 (COMMUNITY)
     */
    @Transactional
    public void deleteFilesByRef(Long refId, RefType refType) {
        List<FileEntity> files = fileRepository.findByRefIdAndRefType(refId, refType);
        if (files.isEmpty()) {
            return;
        }
        List<String> storedPaths = files.stream().map(FileEntity::getFilePath).toList();
        fileRepository.deleteAll(files);
        storedBytesDeleter.deleteAfterCommit(storedPaths);
        log.info("글 연결 파일 삭제 - refId: {}, 파일 수: {}", refId, files.size());
    }

    /**
     * orphan 파일 정리 (§5-3-1 ③) — refId=0/null 이면서 (now - 유예시간) 이전에 생성된 파일만
     * 메타행 삭제 + 저장 바이트(커밋 후) 삭제. 시간은 파라미터 주입으로 결정적 테스트(§6).
     * @param now 기준 시각 (테스트에서 주입)
     * @return 삭제된 파일 수
     */
    @Transactional
    public int deleteOrphanFiles(LocalDateTime now) {
        LocalDateTime threshold = now.minusHours(orphanGraceHours);
        List<FileEntity> orphans = fileRepository.findOrphansOlderThan(threshold);
        if (orphans.isEmpty()) {
            return 0;
        }
        List<String> storedPaths = orphans.stream().map(FileEntity::getFilePath).toList();
        fileRepository.deleteAll(orphans);
        storedBytesDeleter.deleteAfterCommit(storedPaths);
        return orphans.size();
    }

    /**
     * 파일 ID로 파일 정보 조회 (공용 첨부 다운로드용).
     * <p>판본(EDITION) 파일은 여기서 다루지 않는다 — 없는 것으로 본다(호출자는 404).
     * 판본 PDF 의 유일한 공개 경로는 {@code GET /api/editions/{id}/download} 이고,
     * 그 경로만 숨김 곡·저작권 판정·다운로드 집계(02 §3-4)를 거친다.
     * @param fileId 파일 ID
     * @return 파일 엔티티 (없거나 판본 파일이면 null)
     */
    public FileEntity getFileById(Long fileId) {
        return fileRepository.findById(fileId)
                .filter(file -> !isEditionScoped(file.getRefType()))
                .orElse(null);
    }

    /**
     * 판본 파일인지 — 공용 파일 API(/api/files/**)는 커뮤니티·사용자 파일 전용이다.
     * 업로드({@link #verifyOwnership})가 EDITION 을 막는 것과 같은 기준을 조회에도 적용한다(03 §11).
     */
    private static boolean isEditionScoped(RefType refType) {
        return refType == RefType.EDITION;
    }

    /**
     * {@code /uploads/{저장파일명}} 으로 바이트를 그대로 내줘도 되는 파일인가 (02 §3-4).
     *
     * <p><b>판본 PDF 는 안 된다.</b> §3-4 는 {@code GET /api/editions/{id}/download} 가 판본 바이트를 얻는
     * <b>유일한</b> 공개 경로라고 못 박는다. 그 API 는 숨김 404 → 파일 없음 404 → 비 FREE 403 → 바이트 없음 503
     * 게이트를 태우고 다운로드 수·기록도 남긴다. 저장 파일명만 알면 되는 이 프록시가 같은 바이트를 내주면
     * 저작권 게이트가 통째로 우회되어 기획 §9-1 재배포 정책이 무의미해진다(qa 3차 실측).
     *
     * <p><b>판본 미리보기 PNG 는 판정이 {@code FREE} 인 것만 나간다</b>(§0-4 표, 2026-09-08 —
     * {@link #isPreviewServable}). 커뮤니티·프로필 파일은 그대로 서빙된다.
     * <b>{@code files} 행이 없는 저장 파일명도 막는다</b> — 판본 삭제·고아 정리 실패로
     * 업로드 폴더에 바이트만 남는 일이 실제로 있고, 행이 없으면 그 바이트가 무엇인지 우리가 알 수 없다.
     */
    public boolean isPubliclyServable(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return false;
        }
        return fileRepository.findFirstByStoredFileName(storedFileName)
                .map(this::isServable)
                .orElse(false);
    }

    private boolean isServable(FileEntity file) {
        if (!isEditionScoped(file.getRefType())) {
            return true;
        }
        if (isEditionPdf(file)) {
            return false;
        }
        return isPreviewServable(file);
    }

    /**
     * 판본 미리보기 PNG 의 노출 게이트 (02 §0-4 표, 2026-09-08 — qa 4차 결함 2).
     *
     * <p><b>비 FREE 판본의 미리보기는 비관리자에게 404</b> 다. 공개 응답에서 {@code previewUrl} 을 비우는 것만으로는
     * 저장 파일명을 아는 사람에게 그대로 열려 있다 — 이름은 없어지지 않는다(판정이 뒤집힌 판본의 옛 응답·브라우저 기록,
     * 서버의 {@code uploads/} 폴더). "우리 응답에 안 실렸으니 닫힌 것" 이라는 판단이 정확히 {@code /images} 사고였다.
     *
     * <p><b>ADMIN 은 통과한다</b> — 판정 근거가 미리보기 그 자체라(기획 §F6-4 (B)3), 판정 안 된 판본의 미리보기를
     * 관리자에게 감추면 판정 자체가 불가능해진다. 관리 웹은 쿠키 인증이라 {@code <img src>} 에도 권한이 실린다.
     *
     * <p><b>아직 판본에 붙지 않은 업로드(refId 0)는 그대로 연다</b>(02 §5-1) — 판정할 대상 자체가 없고,
     * 관리자가 판본을 저장하기 전에 폼에서 그 이미지를 보는 것이 업로드 응답의 용도다.
     * 붙지 않은 채 유예시간이 지나면 orphan 배치가 지운다(컨벤션 §5-3-1 ③).
     */
    private boolean isPreviewServable(FileEntity preview) {
        Long editionId = preview.getRefId();
        if (editionId == null || editionId == 0L) {
            return true;
        }
        return isAdmin() || editionPreviewGate.isPreviewOpenToPublic(editionId);
    }

    /**
     * 요청자가 관리자인가 — 인가 판단은 서비스에 두고 컨트롤러는 얇게 유지한다(컨벤션 §1).
     * {@code /uploads} 는 permitAll 이라 시큐리티가 걸러 주지 않지만, JWT 필터는 토큰·쿠키가 있으면
     * 경로와 무관하게 인증을 채워 둔다.
     */
    private static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }

    /** 판본 PDF 판정 — 미리보기 PNG(THUMBNAIL)와 갈라야 하므로 용도·Content-Type·확장자를 함께 본다. */
    private static boolean isEditionPdf(FileEntity file) {
        if (!isEditionScoped(file.getRefType())) {
            return false;
        }
        return file.getFileUsage() == Usage.ATTACHMENT
                || PDF_CONTENT_TYPE.equalsIgnoreCase(file.getContentType())
                || (file.getStoredFileName() != null
                        && file.getStoredFileName().toLowerCase().endsWith(PDF_EXTENSION));
    }

    /**
     * 파일 삭제 (DB 레코드 + 물리 파일 함께 삭제)
     * - 물리 삭제는 저장 전략(FileStorageStrategy)에 위임 (로컬/버킷 공통 처리)
     * - 물리 삭제 실패는 로그만 남기고 DB 삭제는 진행 (고아 파일은 별도 정리 대상)
     * @param fileId 삭제할 파일 ID
     * @param username 요청자(인증 주체) — 소유권 검증에 사용
     */
    @Transactional
    public void deleteFile(Long fileId, String username) {
        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> EntityNotFoundException.of("파일", fileId));

        // 인가(Authorization) 검증: 남의 파일 삭제(IDOR) 방지
        verifyDeletable(fileEntity, username);

        fileRepository.delete(fileEntity);

        try {
            fileUtil.deleteFile(fileEntity.getFilePath());
        } catch (Exception e) {
            log.warn("물리 파일 삭제 실패 - fileId: {}, path: {}, 사유: {}",
                    fileId, fileEntity.getFilePath(), e.getMessage());
        }

        log.info("파일 삭제 완료 - fileId: {}, 파일명: {}", fileId, fileEntity.getOriginalFileName());
    }

    /**
     * 파일 상세 정보 조회 (원본 파일명 포함)
     * @param refId 참조 ID
     * @param refType COMMUNITY, USER
     * @param usage THUMBNAIL, IMAGES, ATTACHMENT (선택, null 가능)
     * @return 파일 상세 정보 리스트
     */
    public List<FileDetailDTO> getFileDetails(Long refId, String refType, String usage) {
        RefType type = RefType.valueOf(refType);
        if (isEditionScoped(type)) {
            return List.of();
        }
        Usage usageType = usage != null && !usage.isEmpty()
                ? Usage.valueOf(usage)
                : null;

        List<FileEntity> files = fileRepository.searchFiles(refId, type, usageType);

        return files.stream()
                .map(FileDetailDTO::from)
                .toList();
    }

    /**
     * 파일 삭제 인가 검증. (IDOR 방지)
     * - 정식 refId 를 가진 파일: 참조 리소스(게시글/사용자)의 소유자만 허용 → {@link #verifyOwnership}.
     * - 임시(refId=0/null) 파일: 참조 리소스가 없으므로 업로더 본인만 삭제 허용.
     *   (uploadedBy 가 null 인 레거시 파일은 보정 불가하므로 통과 — 신규 업로드분부터 보호됨)
     * @throws AccessDeniedException 소유자/업로더가 아닐 때 (403)
     */
    private void verifyDeletable(FileEntity file, String username) {
        Long refId = file.getRefId();
        if (file.getRefType() == null || refId == null || refId == 0L) {
            if (file.getUploadedBy() != null && !file.getUploadedBy().equals(username)) {
                throw AccessDeniedException.forFile();
            }
            return; // 업로더 본인이거나, 소유자 판별 불가한 레거시 파일
        }
        verifyOwnership(file.getRefType(), refId, username);
    }

    /**
     * 파일이 참조하는 리소스의 소유자인지 검증한다. (IDOR 방지 — 파일 업로드에서 사용)
     * - refId 가 null/0 이면 아직 어떤 리소스에도 연결되지 않은 "임시 업로드"로 보고 통과시킨다.
     *   (업로드 시점엔 요청자가 곧 업로더이므로 통과. 삭제 시점 보호는 {@link #verifyDeletable} 가 담당)
     * - COMMUNITY: 해당 게시글의 작성자만 허용
     * - USER: refId 가 요청자 본인의 user id 일 때만 허용
     * @throws AccessDeniedException 소유자가 아닐 때 (403)
     */
    private void verifyOwnership(RefType refType, Long refId, String username) {
        if (refType == null || refId == null || refId == 0L) {
            return; // 미연결 임시 업로드 — 소유 리소스 없음
        }

        switch (refType) {
            case COMMUNITY -> {
                CommunityEntity community = communityRepository.findById(refId)
                        .orElseThrow(() -> EntityNotFoundException.of("게시글", refId));
                if (!community.isWrittenBy(username)) {
                    throw AccessDeniedException.forFile();
                }
            }
            case USER -> {
                UserEntity user = userRepository.findByUsername(username)
                        .orElseThrow(() -> EntityNotFoundException.of("사용자", username));
                if (!user.getId().equals(refId)) {
                    throw AccessDeniedException.forFile();
                }
            }
            // 판본 파일은 소유자 개념이 없다 — 전용 관리 API(/api/admin/edition-files)로만 다룬다 (03 §11).
            case EDITION -> throw AccessDeniedException.forFile();
        }
    }
}
