package com.test.test.sheetmusic.edition;

import com.test.test.common.exception.BusinessRuleException;
import com.test.test.common.exception.PayloadTooLargeException;
import com.test.test.file.entity.FileEntity;
import com.test.test.file.entity.RefType;
import com.test.test.file.entity.Usage;
import com.test.test.file.repository.FileRepository;
import com.test.test.file.service.StoredBytesDeleter;
import com.test.test.file.strategy.FileStorageStrategy;
import com.test.test.file.strategy.FileStorageStrategy.FileUploadResult;
import com.test.test.sheetmusic.common.PdfBytes;
import com.test.test.sheetmusic.edition.dto.EditionFileUploadDTO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 판본 PDF 파일 처리 (02 §5-1, 03 §2).
 *
 * <p>흐름은 항상 <b>임시 파일 → 검증(매직바이트·크기) → PDFBox(쪽수·첫 페이지 PNG) → 저장 전략 → 임시 파일 삭제</b>.
 * 저장 전략 호출(외부 I/O)은 트랜잭션 밖에서 먼저 하고, 그 결과로 {@code files} 행만 짧은 트랜잭션에 쓴다(컨벤션 §1).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EditionFileService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final int PREVIEW_DPI = 72;
    private static final int PREVIEW_MAX_WIDTH = 800;

    private final FileStorageStrategy fileStorageStrategy;
    private final FileRepository fileRepository;
    private final StoredBytesDeleter storedBytesDeleter;

    @Value("${app.edition.max-file-bytes:104857600}")
    private long maxFileBytes;

    /** 02 §5-1 — 업로드된 PDF 를 저장하고 쪽수·미리보기를 만든다. */
    public EditionFileUploadDTO upload(MultipartFile file, String username) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("PDF 파일만 올릴 수 있어요");
        }
        if (file.getSize() > maxFileBytes) {
            throw PayloadTooLargeException.ofLimit(maxFileBytes);
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("edition-", ".pdf");
            try (InputStream in = file.getInputStream()) {
                copy(in, temp);
            }
            if (Files.size(temp) > maxFileBytes) {
                throw PayloadTooLargeException.ofLimit(maxFileBytes);
            }
            if (!PdfBytes.isPdf(temp)) {
                throw new BusinessRuleException("PDF 파일만 올릴 수 있어요");
            }
            String originalName = file.getOriginalFilename() == null ? "score.pdf" : file.getOriginalFilename();
            return storeWithPreview(temp, originalName, username);
        } catch (IOException e) {
            log.error("판본 파일 업로드 실패", e);
            throw new BusinessRuleException("파일을 저장하지 못했어요");
        } finally {
            deleteQuietly(temp);
        }
    }

    /**
     * 이미 검증된 임시 PDF 파일(수집·받아오기)을 저장하고 쪽수·미리보기를 만든다.
     * 임시 파일 삭제는 호출자 책임.
     */
    public EditionFileUploadDTO storeFetchedPdf(Path pdf, String originalName, String username) {
        try {
            if (!PdfBytes.isPdf(pdf)) {
                throw new BusinessRuleException("PDF 파일이 아니에요");
            }
            return storeWithPreview(pdf, originalName, username);
        } catch (IOException e) {
            throw new BusinessRuleException("파일을 저장하지 못했어요");
        }
    }

    private EditionFileUploadDTO storeWithPreview(Path pdf, String originalName, String username) throws IOException {
        Integer pageCount = readPageCount(pdf);
        FileUploadResult pdfResult = fileStorageStrategy.store(originalName, PDF_CONTENT_TYPE, pdf);
        FileEntity pdfFile = saveFileRow(pdfResult, Usage.ATTACHMENT, username);

        Long previewFileId = null;
        String previewUrl = null;
        Path previewTemp = null;
        try {
            previewTemp = renderPreview(pdf);
            if (previewTemp != null) {
                FileUploadResult previewResult = fileStorageStrategy.store(
                        stripExtension(originalName) + ".png", PNG_CONTENT_TYPE, previewTemp);
                FileEntity previewFile = saveFileRow(previewResult, Usage.THUMBNAIL, username);
                previewFileId = previewFile.getId();
                previewUrl = previewFile.getFilePath();
            }
        } catch (Exception e) {
            // 미리보기 실패는 업로드를 막지 않는다 (02 §5-1)
            log.warn("미리보기 생성 실패 - {}", e.getMessage());
        } finally {
            deleteQuietly(previewTemp);
        }

        return EditionFileUploadDTO.builder()
                .fileId(pdfFile.getId())
                .previewFileId(previewFileId)
                .fileName(originalName)
                .fileSize(pdfFile.getFileSize() == null ? 0L : pdfFile.getFileSize())
                .pageCount(pageCount)
                .previewUrl(previewUrl)
                .build();
    }

    private FileEntity saveFileRow(FileUploadResult result, Usage usage, String username) {
        FileEntity entity = FileEntity.builder()
                .originalFileName(result.getOriginalFilename())
                .storedFileName(result.getStoredFilename())
                .filePath(result.getWebPath())
                .fileSize(result.getFileSize())
                .contentType(result.getContentType())
                .refId(0L)
                .refType(RefType.EDITION)
                .fileUsage(usage)
                .uploadedBy(username)
                .build();
        return fileRepository.save(entity);
    }

    /** 업로드된 파일을 판본에 연결한다 (컨벤션 §5-3-1 ①). */
    @Transactional
    public void linkToEdition(Long editionId, Long pdfFileId, Long previewFileId) {
        for (Long fileId : new Long[] {pdfFileId, previewFileId}) {
            if (fileId == null) {
                continue;
            }
            fileRepository.findById(fileId).ifPresent(file -> file.linkTo(editionId));
        }
    }

    /** 판본에 연결된 files 행을 지운다(바이트는 커밋 후). */
    @Transactional
    public void deleteFiles(List<Long> fileIds) {
        List<FileEntity> files = new ArrayList<>();
        for (Long fileId : fileIds) {
            if (fileId != null) {
                fileRepository.findById(fileId).ifPresent(files::add);
            }
        }
        if (files.isEmpty()) {
            return;
        }
        List<String> paths = files.stream().map(FileEntity::getFilePath).toList();
        fileRepository.deleteAll(files);
        storedBytesDeleter.deleteAfterCommit(paths);
    }

    /** 업로드 응답의 fileId 검증 (02 §5-2): 존재·EDITION·미연결. */
    public FileEntity requireUnlinkedEditionFile(Long fileId) {
        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessRuleException("업로드된 파일을 찾을 수 없어요"));
        if (file.getRefType() != RefType.EDITION) {
            throw new BusinessRuleException("판본에 연결할 수 있는 파일이 아니에요");
        }
        if (file.getRefId() != null && file.getRefId() != 0L) {
            throw new BusinessRuleException("이미 다른 판본에 연결된 파일이에요");
        }
        return file;
    }

    /**
     * 업로드된 판본 PDF 의 쪽수 (02 §5-3 — 저장 시 요청에 pageCount 가 없을 때의 폴백). 읽지 못하면 null.
     *
     * <p><b>트랜잭션 밖에서 부른다</b> — 저장소 읽기(운영에선 S3 GetObject)와 PDFBox 파싱이 들어 있어
     * 쓰기 트랜잭션 안에서 돌면 DB 커넥션을 붙잡는다(컨벤션 §1). 파일이 없거나 판본 파일이 아니면
     * 조용히 null 을 돌려주고, 그 검증은 저장 트랜잭션 안의 {@link #requireUnlinkedEditionFile(Long)} 가 맡는다.
     */
    public Integer pageCountOfUploadedFile(Long fileId) {
        if (fileId == null) {
            return null;
        }
        return fileRepository.findById(fileId)
                .filter(file -> file.getRefType() == RefType.EDITION)
                .map(this::pageCountOfStoredFile)
                .orElse(null);
    }

    /** 저장된 PDF 의 쪽수. 읽지 못하면 null. */
    private Integer pageCountOfStoredFile(FileEntity file) {
        if (file == null) {
            return null;
        }
        Resource resource = fileStorageStrategy.loadAsResource(file.getStoredFileName());
        if (resource == null) {
            return null;
        }
        Path temp = null;
        try (InputStream in = resource.getInputStream()) {
            temp = Files.createTempFile("edition-page-", ".pdf");
            copy(in, temp);
            return readPageCount(temp);
        } catch (IOException e) {
            log.warn("저장된 파일의 쪽수를 읽지 못했습니다 - fileId: {}", file.getId());
            return null;
        } finally {
            deleteQuietly(temp);
        }
    }

    private Integer readPageCount(Path pdf) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBufferedFile(pdf.toFile()))) {
            return document.getNumberOfPages();
        } catch (Exception e) {
            log.warn("쪽수를 읽지 못했습니다 - {}", e.getMessage());
            return null;
        }
    }

    private Path renderPreview(Path pdf) {
        Path target = null;
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBufferedFile(pdf.toFile()))) {
            if (document.getNumberOfPages() == 0) {
                return null;
            }
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, PREVIEW_DPI, ImageType.RGB);
            if (image.getWidth() > PREVIEW_MAX_WIDTH) {
                image = scaleToWidth(image);
            }
            target = Files.createTempFile("edition-preview-", ".png");
            ImageIO.write(image, "png", target.toFile());
            return target;
        } catch (Exception e) {
            log.warn("미리보기 렌더링 실패 - {}", e.getMessage());
            deleteQuietly(target);
            return null;
        }
    }

    private BufferedImage scaleToWidth(BufferedImage source) {
        int height = Math.max(1, source.getHeight() * PREVIEW_MAX_WIDTH / source.getWidth());
        BufferedImage scaled = new BufferedImage(PREVIEW_MAX_WIDTH, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = scaled.createGraphics();
        graphics.drawImage(source.getScaledInstance(PREVIEW_MAX_WIDTH, height, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
        graphics.dispose();
        return scaled;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 임시 파일 정리 실패는 무시
        }
    }

    /** MultipartFile.transferTo 는 기존 파일을 덮어쓰지 못하므로 직접 복사한다. */
    private static void copy(InputStream in, Path target) throws IOException {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
