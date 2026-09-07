package com.test.test.sheetmusic.composer;

import com.test.test.common.dto.PageResponse;
import com.test.test.common.exception.BusinessRuleException;
import com.test.test.common.exception.DuplicateResourceException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.common.exception.FieldValidationException;
import com.test.test.sheetmusic.common.ImslpUrlNormalizer;
import com.test.test.sheetmusic.common.SearchNormalizer;
import com.test.test.sheetmusic.composer.dto.AdminComposerDTO;
import com.test.test.sheetmusic.composer.dto.AdminComposerDetailDTO;
import com.test.test.sheetmusic.composer.dto.ComposerSaveDTO;
import com.test.test.sheetmusic.composer.repository.ComposerRepository;
import com.test.test.sheetmusic.work.repository.WorkRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 작곡가 관리 (02 §4-2 ~ §4-5). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminComposerService {

    private final ComposerRepository composerRepository;
    private final WorkRepository workRepository;

    public PageResponse<AdminComposerDTO> list(String q, boolean missingKo, Pageable pageable) {
        String normalized = SearchNormalizer.normalize(q);
        Page<ComposerEntity> page = composerRepository.searchAdmin(normalized, missingKo, pageable);
        List<AdminComposerDTO> content = new ArrayList<>();
        for (ComposerEntity composer : page.getContent()) {
            content.add(AdminComposerDTO.from(composer, workRepository.countByComposerId(composer.getId())));
        }
        return PageResponse.<AdminComposerDTO>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    public AdminComposerDetailDTO detail(Long id) {
        ComposerEntity composer = findOrThrow(id);
        return AdminComposerDetailDTO.from(composer, workRepository.countByComposerId(id));
    }

    @Transactional
    public AdminComposerDetailDTO create(ComposerSaveDTO request) {
        validate(request);
        String normalized = SearchNormalizer.normalize(request.getNameOriginal());
        composerRepository.findByNameOriginalNormalized(normalized).ifPresent(existing -> {
            throw new DuplicateResourceException("이미 등록된 작곡가예요");
        });

        ComposerEntity composer = ComposerEntity.builder()
                .nameKo(request.getNameKo())
                .nameOriginal(request.getNameOriginal())
                .birthYear(request.getBirthYear())
                .deathYear(request.getDeathYear())
                .nationality(request.getNationality())
                .imslpUrl(request.getImslpUrl())
                .build();
        composer.replaceAliases(request.getAliases());
        composerRepository.save(composer);
        return AdminComposerDetailDTO.from(composer, 0L);
    }

    @Transactional
    public AdminComposerDetailDTO update(Long id, ComposerSaveDTO request) {
        validate(request);
        ComposerEntity composer = findOrThrow(id);
        String normalized = SearchNormalizer.normalize(request.getNameOriginal());
        composerRepository.findByNameOriginalNormalized(normalized).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new DuplicateResourceException("이미 등록된 작곡가예요");
            }
        });

        composer.update(request.getNameKo(), request.getNameOriginal(), request.getBirthYear(),
                request.getDeathYear(), request.getNationality(), request.getImslpUrl());
        composer.replaceAliases(request.getAliases());
        return AdminComposerDetailDTO.from(composer, workRepository.countByComposerId(id));
    }

    @Transactional
    public void delete(Long id) {
        ComposerEntity composer = findOrThrow(id);
        long works = workRepository.countByComposerId(id);
        if (works > 0) {
            throw new BusinessRuleException("곡 " + works + "개가 있어 삭제할 수 없어요");
        }
        composerRepository.delete(composer);
    }

    private ComposerEntity findOrThrow(Long id) {
        return composerRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("작곡가", id));
    }

    private void validate(ComposerSaveDTO request) {
        if (request.getBirthYear() != null && request.getDeathYear() != null
                && request.getDeathYear() < request.getBirthYear()) {
            throw FieldValidationException.of("deathYear", "몰년이 생년보다 앞서요", request.getDeathYear());
        }
        String imslpUrl = request.getImslpUrl();
        if (imslpUrl != null && !imslpUrl.isBlank() && !ImslpUrlNormalizer.isImslpUrl(imslpUrl)) {
            throw FieldValidationException.of("imslpUrl", "IMSLP 주소를 입력해 주세요", imslpUrl);
        }
    }
}
